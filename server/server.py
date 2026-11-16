import asyncio
import json
import os
import secrets
import socket
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
from screen_streamer import ScreenCapturer

BASE_DIR = Path(__file__).resolve().parent
WEB_DIR = BASE_DIR / "web"
PIN_FILE = BASE_DIR / "pin.txt"
CONFIG_FILE = BASE_DIR / "config.json"

DEFAULT_PORT = 8765

# Load or generate PIN
def get_or_create_pin():
    if PIN_FILE.exists():
        pin = PIN_FILE.read_text(encoding="utf-8").strip()
        if pin:
            return pin
    pin = f"{secrets.randbelow(9000) + 1000}"
    PIN_FILE.write_text(pin, encoding="utf-8")
    return pin

# Load or save port
def get_port():
    if len(sys.argv) > 1:
        try:
            return int(sys.argv[1])
        except ValueError:
            pass
    if CONFIG_FILE.exists():
        try:
            data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
            return int(data.get("port", DEFAULT_PORT))
        except Exception:
            pass
    return DEFAULT_PORT

PIN = get_or_create_pin()
PORT = get_port()

# Active authenticated clients
authenticated_clients = set()
capturer = None
stream_task = None

# Brute force protection: ip -> [timestamps]
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

# Background Screen Streamer
async def screen_stream_loop():
    global capturer
    capturer = ScreenCapturer(target_width=1280, target_height=720, quality=60)
    loop = asyncio.get_running_loop()

    try:
        while True:
            if not authenticated_clients:
                await asyncio.sleep(0.2)
                continue

            # Capture in thread to avoid blocking asyncio event loop
            frame = await loop.run_in_executor(None, capturer.capture_frame)

            # Send to all authenticated clients
            if frame and authenticated_clients:
                tasks = []
                for ws in list(authenticated_clients):
                    tasks.append(ws.send(frame))
                if tasks:
                    await asyncio.gather(*tasks, return_exceptions=True)

            # Cap at ~22-25 FPS for optimal bandwidth & smooth display
            await asyncio.sleep(0.04)
    except asyncio.CancelledError:
        pass
    finally:
        if capturer:
            capturer.close()
            capturer = None

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
                ("Cache-Control", "no-cache"),
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
            # Binary messages are ignored from client (client only sends text commands)
            if isinstance(raw_msg, bytes):
                continue

            try:
                msg = json.loads(raw_msg)
            except Exception:
                continue

            msg_type = msg.get("type")

            # Auth message
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

            # Input actions
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
    print("  V-REMOTE CONTROL + SCREEN STREAMER")
    print("=" * 60)
    print(f"  Порт сервера:    {PORT}")
    print(f"  PIN безопасности:{PIN}")
    print("-" * 60)
    print("  Адреса для подключения:")
    print(f"    Локально на ПК: http://localhost:{PORT}")
    for ip in get_local_ips():
        print(f"    В локальной сети (Wi-Fi): http://{ip}:{PORT}")
    print("-" * 60)
    print("  Для подключения из любой точки мира:")
    print(f"    1. Пробрось порт {PORT} (TCP) на 192.168.0.108 в роутере.")
    print(f"    2. С телефона открывай: http://94.190.111.52:{PORT}")
    print(f"    3. Введи PIN: {PIN}")
    print("=" * 60)
    print("Сервер запущен. Нажмите Ctrl+C для остановки.\n")

    # Start screen streamer task
    stream_task = asyncio.create_task(screen_stream_loop())

    async with serve(
        ws_handler,
        "0.0.0.0",
        PORT,
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
