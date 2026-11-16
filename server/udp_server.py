import socket
import struct
import sys
import time
from pathlib import Path

import input_handler

BASE_DIR = Path(__file__).resolve().parent
PIN_FILE = BASE_DIR / "pin.txt"
DEFAULT_PORT = 8766

def get_pin():
    if PIN_FILE.exists():
        try:
            return int(PIN_FILE.read_text(encoding="utf-8").strip())
        except Exception:
            pass
    return 7373

PIN = get_pin()

MAGIC_BYTE = 0x56
TYPE_MOUSE_MOVE_REL = 0x01
TYPE_MOUSE_MOVE_ABS = 0x02
TYPE_MOUSE_BUTTON = 0x03
TYPE_MOUSE_SCROLL = 0x04
TYPE_KEY_TAP = 0x05
TYPE_PING = 0x06
TYPE_PONG = 0x07
TYPE_AUTH = 0x08
TYPE_UNICODE_CHAR = 0x09

BTN_MAP = {0: "left", 1: "right", 2: "middle"}
ACT_MAP = {0: "tap", 1: "down", 2: "up"}

def run_udp_server(port=DEFAULT_PORT):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    # Enable SO_REUSEADDR
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", port))

    print("=" * 60)
    print(f"  V-REMOTE HIGH-SPEED UDP DAEMON (Port {port})")
    print("=" * 60)
    print(f"  Ожидание бинарных UDP дейтаграмм на 0.0.0.0:{port}...")
    print(f"  PIN авторизации: {PIN}")
    print("=" * 60)

    authenticated_clients = set()

    while True:
        try:
            data, addr = sock.recvfrom(128)
            if not data or len(data) < 2:
                continue

            if data[0] != MAGIC_BYTE:
                continue

            msg_type = data[1]

            # 1. Ping / Pong (Available even before full auth to test connection latency)
            if msg_type == TYPE_PING:
                if len(data) >= 6:
                    seq = data[2:6]
                    pong_packet = bytes([MAGIC_BYTE, TYPE_PONG]) + seq
                    sock.sendto(pong_packet, addr)
                continue

            # 2. Auth
            if msg_type == TYPE_AUTH:
                if len(data) >= 6:
                    client_pin = struct.unpack(">I", data[2:6])[0]
                    if client_pin == PIN:
                        authenticated_clients.add(addr)
                        print(f"[{addr}] UDP клиент успешно авторизован!")
                continue

            # Check auth
            if addr not in authenticated_clients:
                continue

            # 3. Mouse Move Relative (dx: int16, dy: int16)
            if msg_type == TYPE_MOUSE_MOVE_REL and len(data) >= 6:
                dx, dy = struct.unpack(">hh", data[2:6])
                input_handler.mouse_move_relative(dx, dy)

            # 4. Mouse Move Absolute (rx: uint16, ry: uint16)
            elif msg_type == TYPE_MOUSE_MOVE_ABS and len(data) >= 6:
                rx_raw, ry_raw = struct.unpack(">HH", data[2:6])
                rx = rx_raw / 65535.0
                ry = ry_raw / 65535.0
                input_handler.mouse_move_absolute(rx, ry)

            # 5. Mouse Button (button: uint8, action: uint8)
            elif msg_type == TYPE_MOUSE_BUTTON and len(data) >= 4:
                btn_id = data[2]
                act_id = data[3]
                btn = BTN_MAP.get(btn_id, "left")
                act = ACT_MAP.get(act_id, "tap")
                if act == "tap":
                    input_handler.mouse_click(btn)
                else:
                    input_handler.mouse_button(btn, act)

            # 6. Mouse Scroll (delta_y: int8, delta_x: int8)
            elif msg_type == TYPE_MOUSE_SCROLL and len(data) >= 4:
                delta_y, delta_x = struct.unpack(">bb", data[2:4])
                input_handler.mouse_scroll(delta_y, delta_x)

            # 7. Key Tap (vk: uint16)
            elif msg_type == TYPE_KEY_TAP and len(data) >= 4:
                vk = struct.unpack(">H", data[2:4])[0]
                # Synthesize keypress directly
                inp_down = input_handler.INPUT(type=input_handler.INPUT_KEYBOARD)
                inp_down.u.ki.wVk = vk
                inp_down.u.ki.dwFlags = 0
                inp_up = input_handler.INPUT(type=input_handler.INPUT_KEYBOARD)
                inp_up.u.ki.wVk = vk
                inp_up.u.ki.dwFlags = input_handler.KEYEVENTF_KEYUP
                input_handler._send_inputs([inp_down])
                time.sleep(0.01)
                input_handler._send_inputs([inp_up])

            # 8. Unicode character
            elif msg_type == TYPE_UNICODE_CHAR and len(data) >= 4:
                ch = data[2:4].decode("utf-16-be", errors="ignore")
                if ch:
                    input_handler.type_text(ch)

        except Exception as e:
            print(f"UDP Error: {e}")

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    try:
        run_udp_server(port)
    except KeyboardInterrupt:
        print("\nUDP сервер остановлен.")
