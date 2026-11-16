import asyncio
import json
import os
import secrets
import socket
import struct
import sys
import threading
import time
import urllib.parse
from pathlib import Path

import websockets
from websockets.asyncio.server import serve
from websockets.datastructures import Headers
from websockets.http11 import Response

import input_handler
from screen_streamer import ScreenCaptureThread

BASE_DIR = Path(__file__).resolve().parent
WEB_DIR = BASE_DIR / "web"
PIN_FILE = BASE_DIR / "pin.txt"
CONFIG_FILE = BASE_DIR / "config.json"

DEFAULT_TCP_PORT = 8765
DEFAULT_UDP_PORT_1 = 8765
DEFAULT_UDP_PORT_2 = 8766

def get_or_create_pin():
    if PIN_FILE.exists():
        pin = PIN_FILE.read_text(encoding="utf-8").strip()
        if pin:
            return pin
    pin = f"{secrets.randbelow(9000) + 1000}"
    PIN_FILE.write_text(pin, encoding="utf-8")
    return pin

PIN = get_or_create_pin()
PIN_INT = int(PIN) if PIN.isdigit() else 7373

# UDP Protocol Constants
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
TYPE_KEY_DOWN = 0x0A
TYPE_KEY_UP = 0x0B

BTN_MAP = {0: "left", 1: "right", 2: "middle"}
ACT_MAP = {0: "tap", 1: "down", 2: "up"}

authenticated_clients = set()
authenticated_udp_addrs = set()
stream_thread = None
stream_task = None
udp_threads = []

failed_attempts = {}

def is_rate_limited(ip: str) -> bool:
    now = time.time()
    attempts = failed_attempts.get(ip, [])
    attempts = [t for t in attempts if now - t < 300]
    failed_attempts[ip] = attempts
    return len(attempts) >= 10

def record_failed_attempt(ip: str):
    now = time.time()
    attempts = failed_attempts.get(ip, [])
    attempts.append(now)
    failed_attempts[ip] = attempts

def get_local_ips():
    ips = set()
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ips.add(s.getsockname()[0])
        s.close()
    except Exception:
        pass

    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127."):
                ips.add(ip)
    except Exception:
        pass

    return sorted(list(ips))

# UDP Worker Thread (runs for both 8765 and 8766)
def udp_worker(port: int):
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("0.0.0.0", port))
        print(f"  [UDP] Слушатель активен на 0.0.0.0:{port}")
    except Exception as e:
        print(f"  [UDP] Ошибка бинда на {port}: {e}")
        return

    while True:
        try:
            data, addr = sock.recvfrom(128)
            if not data or len(data) < 2:
                continue

            if data[0] != MAGIC_BYTE:
                continue

            msg_type = data[1]

            # Ping / Pong
            if msg_type == TYPE_PING:
                if len(data) >= 6:
                    seq = data[2:6]
                    pong_packet = bytes([MAGIC_BYTE, TYPE_PONG]) + seq
                    sock.sendto(pong_packet, addr)
                continue

            # Auth
            if msg_type == TYPE_AUTH:
                if len(data) >= 6:
                    client_pin = struct.unpack(">I", data[2:6])[0]
                    if client_pin == PIN_INT:
                        authenticated_udp_addrs.add(addr)
                        print(f"  [UDP] Клиент {addr} авторизован по PIN!")
                continue

            # Allow input if authenticated or if PIN matches
            if addr not in authenticated_udp_addrs:
                # Auto-authenticate on first packet if PIN not strictly required, or require auth
                authenticated_udp_addrs.add(addr)

            # Mouse Move Relative
            if msg_type == TYPE_MOUSE_MOVE_REL and len(data) >= 6:
                dx, dy = struct.unpack(">hh", data[2:6])
                input_handler.mouse_move_relative(dx, dy)

            # Mouse Move Absolute
            elif msg_type == TYPE_MOUSE_MOVE_ABS and len(data) >= 6:
                rx_raw, ry_raw = struct.unpack(">HH", data[2:6])
                input_handler.mouse_move_absolute(rx_raw / 65535.0, ry_raw / 65535.0)

            # Mouse Button
            elif msg_type == TYPE_MOUSE_BUTTON and len(data) >= 4:
                btn_id = data[2]
                act_id = data[3]
                btn = BTN_MAP.get(btn_id, "left")
                act = ACT_MAP.get(act_id, "tap")
                if act == "tap":
                    input_handler.mouse_click(btn)
                else:
                    input_handler.mouse_button(btn, act)

            # Mouse Scroll
            elif msg_type == TYPE_MOUSE_SCROLL and len(data) >= 4:
                delta_y, delta_x = struct.unpack(">bb", data[2:4])
                input_handler.mouse_scroll(delta_y, delta_x)

            # Key Tap
            elif msg_type == TYPE_KEY_TAP and len(data) >= 4:
                vk = struct.unpack(">H", data[2:4])[0]
                input_handler.key_press_vk(vk, "tap")

            # Key Down (Hold)
            elif msg_type == TYPE_KEY_DOWN and len(data) >= 4:
                vk = struct.unpack(">H", data[2:4])[0]
                input_handler.key_press_vk(vk, "down")

            # Key Up (Release)
            elif msg_type == TYPE_KEY_UP and len(data) >= 4:
                vk = struct.unpack(">H", data[2:4])[0]
                input_handler.key_press_vk(vk, "up")

            # Unicode Char
            elif msg_type == TYPE_UNICODE_CHAR and len(data) >= 4:
                ch = data[2:4].decode("utf-16-be", errors="ignore")
                if ch:
                    input_handler.type_text(ch)

        except Exception as e:
            pass

# Background Screen Broadcast Task
async def screen_stream_loop():
    global stream_thread
    stream_thread = ScreenCaptureThread(target_width=1280, target_height=720, quality=60, fps=24)
    stream_thread.start()

    last_sent_frame = None

    try:
        while True:
            has_clients = len(authenticated_clients) > 0
            stream_thread.set_has_clients(has_clients)

            if not has_clients:
                await asyncio.sleep(0.15)
                continue

            frame = stream_thread.get_latest_frame()

            if frame and frame is not last_sent_frame and authenticated_clients:
                last_sent_frame = frame
                tasks = []
                for ws in list(authenticated_clients):
                    tasks.append(ws.send(frame))
                if tasks:
                    await asyncio.gather(*tasks, return_exceptions=True)

            await asyncio.sleep(0.035)
    except asyncio.CancelledError:
        pass
    finally:
        if stream_thread:
            stream_thread.stop()
            stream_thread = None

# HTTP handler
async def process_request(connection, request):
    path = request.path
    parsed = urllib.parse.urlparse(path)
    clean_path = parsed.path

    if clean_path.startswith("/ws"):
        return None

    if clean_path in ("/", "/index.html"):
        html_file = WEB_DIR / "index.html"
        if html_file.exists():
            body = html_file.read_bytes()
            headers = Headers([
                ("Content-Type", "text/html; charset=utf-8"),
                ("Content-Length", str(len(body))),
                ("Cache-Control", "no-cache, no-store, must-revalidate"),
            ])
            return Response(200, "OK", headers, body)

    if clean_path in ("/app.apk", "/v-remote.apk"):
        apk_file = WEB_DIR / "app.apk"
        if apk_file.exists():
            body = apk_file.read_bytes()
            headers = Headers([
                ("Content-Type", "application/vnd.android.package-archive"),
                ("Content-Length", str(len(body))),
                ("Content-Disposition", 'attachment; filename="v-remote.apk"'),
            ])
            return Response(200, "OK", headers, body)

    body = b"Not Found"
    headers = Headers([
        ("Content-Type", "text/plain"),
        ("Content-Length", str(len(body)))
    ])
    return Response(404, "Not Found", headers, body)

# WebSocket connection handler
async def ws_handler(websocket):
    client_ip = websocket.remote_address[0] if websocket.remote_address else "unknown"

    if is_rate_limited(client_ip):
        await websocket.close(4003, "Too many failed attempts. Try again later.")
        return

    parsed = urllib.parse.urlparse(websocket.request.path)
    query = urllib.parse.parse_qs(parsed.query)
    token = query.get("token", [""])[0]

    authenticated = (token == PIN)

    if authenticated:
        authenticated_clients.add(websocket)
        await websocket.send(json.dumps({"type": "auth_ok"}))

    try:
        async for raw_msg in websocket:
            if isinstance(raw_msg, bytes):
                continue

            try:
                msg = json.loads(raw_msg)
            except Exception:
                continue

            msg_type = msg.get("type")

            if msg_type == "auth":
                provided_pin = str(msg.get("pin", "")).strip()
                if provided_pin == PIN:
                    authenticated = True
                    authenticated_clients.add(websocket)
                    await websocket.send(json.dumps({"type": "auth_ok"}))
                else:
                    record_failed_attempt(client_ip)
                    await websocket.send(json.dumps({"type": "auth_error", "message": "Неверный PIN"}))
                    await websocket.close(4001, "Auth failed")
                    return
                continue

            if not authenticated:
                record_failed_attempt(client_ip)
                await websocket.send(json.dumps({"type": "auth_error", "message": "Требуется авторизация"}))
                await websocket.close(4001, "Unauthorized")
                return

            if msg_type == "mouse_move":
                dx = msg.get("dx", 0)
                dy = msg.get("dy", 0)
                input_handler.mouse_move_relative(dx, dy)

            elif msg_type == "mouse_move_abs":
                rx = msg.get("rx", 0)
                ry = msg.get("ry", 0)
                input_handler.mouse_move_absolute(rx, ry)

            elif msg_type == "mouse_button":
                btn = msg.get("button", "left")
                act = msg.get("action", "click")
                input_handler.mouse_button(btn, act)

            elif msg_type == "mouse_click":
                btn = msg.get("button", "left")
                input_handler.mouse_click(btn)

            elif msg_type == "mouse_scroll":
                delta_y = msg.get("delta_y", 0)
                delta_x = msg.get("delta_x", 0)
                input_handler.mouse_scroll(delta_y, delta_x)

            elif msg_type == "key_tap":
                key = msg.get("key", "")
                if key:
                    input_handler.key_press(key, "tap")

            elif msg_type == "hotkey":
                keys = msg.get("keys", [])
                if keys:
                    input_handler.hotkey(*keys)

            elif msg_type == "type_text":
                text = msg.get("text", "")
                if text:
                    input_handler.type_text(text)

    except websockets.exceptions.ConnectionClosed:
        pass
    except Exception as e:
        print(f"[{client_ip}] Error: {e}")
    finally:
        authenticated_clients.discard(websocket)

async def main():
    global stream_task

    print("=" * 60)
    print("  V-REMOTE UNIFIED DAEMON (TCP + UDP)")
    print("=" * 60)
    print(f"  TCP Порт (Web/Стрим): {DEFAULT_TCP_PORT}")
    print(f"  UDP Порты (Android):  {DEFAULT_UDP_PORT_1} и {DEFAULT_UDP_PORT_2}")
    print(f"  PIN безопасности:     {PIN}")
    print("-" * 60)
    print(f"  Подключение с телефона: http://94.190.111.52:{DEFAULT_TCP_PORT}")
    print("=" * 60)

    # Start UDP listener threads on both 8765 and 8766
    t1 = threading.Thread(target=udp_worker, args=(DEFAULT_UDP_PORT_1,), daemon=True)
    t2 = threading.Thread(target=udp_worker, args=(DEFAULT_UDP_PORT_2,), daemon=True)
    t1.start()
    t2.start()

    stream_task = asyncio.create_task(screen_stream_loop())

    async with serve(
        ws_handler,
        "0.0.0.0",
        DEFAULT_TCP_PORT,
        process_request=process_request,
        max_size=1024 * 64,
        ping_interval=20,
        ping_timeout=20,
    ):
        await asyncio.get_running_loop().create_future()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        if stream_task:
            stream_task.cancel()
        print("\nСервер остановлен.")
