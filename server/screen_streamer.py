import ctypes
import io
import queue
import threading
import time
from ctypes import wintypes
import numpy as np
from PIL import Image

user32 = ctypes.windll.user32
gdi32 = ctypes.windll.gdi32

class POINT(ctypes.Structure):
    _fields_ = [("x", wintypes.LONG), ("y", wintypes.LONG)]

class CURSORINFO(ctypes.Structure):
    _fields_ = [
        ("cbSize", wintypes.DWORD),
        ("flags", wintypes.DWORD),
        ("hCursor", wintypes.HICON),
        ("ptScreenPos", POINT),
    ]

CURSOR_SHOWING = 0x00000001
DESKTOP_ALL_ACCESS = 0x01FF

def attach_to_default_desktop():
    try:
        user32.OpenDesktopW.restype = ctypes.c_void_p
        user32.OpenDesktopW.argtypes = [ctypes.c_void_p, wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        user32.SetThreadDesktop.argtypes = [ctypes.c_void_p]

        hdesk = user32.OpenDesktopW("Default", 0, False, DESKTOP_ALL_ACCESS)
        if hdesk:
            user32.SetThreadDesktop(hdesk)
            return hdesk
    except Exception:
        pass
    return None

class ScreenCaptureThread(threading.Thread):
    def __init__(self, target_width=1280, target_height=720, quality=45, fps=60):
        super().__init__(daemon=True)
        self.target_width = target_width
        self.target_height = target_height
        self.quality = quality
        self.fps = fps
        self.interval = 1.0 / fps

        self.latest_frame = None
        self.lock = threading.Lock()
        self.running = True
        self.has_clients = False

        self.frame_queue = queue.Queue(maxsize=3)
        self.encoder_threads = []

    def set_has_clients(self, has_clients: bool):
        self.has_clients = has_clients

    def get_latest_frame(self):
        with self.lock:
            return self.latest_frame

    def _encoder_worker(self):
        while self.running:
            try:
                item = self.frame_queue.get(timeout=0.08)
            except queue.Empty:
                continue

            if item is None:
                self.frame_queue.task_done()
                break

            rgb, cx, cy = item
            try:
                # Draw cursor in numpy (fast 0.01ms dot)
                if cx >= 0 and cy >= 0:
                    y1 = max(0, cy - 3)
                    y2 = min(rgb.shape[0], cy + 4)
                    x1 = max(0, cx - 3)
                    x2 = min(rgb.shape[1], cx + 4)
                    rgb[y1:y2, x1:x2] = [239, 68, 68] # Red cursor dot

                im = Image.fromarray(rgb, "RGB")
                out = io.BytesIO()
                im.save(out, format="JPEG", quality=self.quality, optimize=False)
                frame_bytes = out.getvalue()

                with self.lock:
                    self.latest_frame = frame_bytes
            except Exception:
                pass
            finally:
                self.frame_queue.task_done()

    def run(self):
        # 1. Attach this dedicated thread directly to the interactive Default desktop
        attach_to_default_desktop()

        # Start 2 parallel JPEG encoder workers
        for _ in range(2):
            t = threading.Thread(target=self._encoder_worker, daemon=True)
            t.start()
            self.encoder_threads.append(t)

        # 2. Try initializing DXCam (DirectX 11 Desktop Duplication)
        cam = None
        try:
            import dxcam
            cam = dxcam.create(output_color="BGRA")
        except Exception:
            cam = None

        sct = None
        if cam is None:
            import mss
            sct = mss.MSS()

        screen_w = 2560
        screen_h = 1440

        while self.running:
            if not self.has_clients:
                time.sleep(0.08)
                continue

            t0 = time.time()
            try:
                attach_to_default_desktop()

                # Get hardware cursor position
                cx, cy = -1, -1
                ci = CURSORINFO()
                ci.cbSize = ctypes.sizeof(CURSORINFO)
                if user32.GetCursorInfo(ctypes.byref(ci)) and (ci.flags & CURSOR_SHOWING):
                    scale_x = self.target_width / screen_w
                    scale_y = self.target_height / screen_h
                    cx = int(ci.ptScreenPos.x * scale_x)
                    cy = int(ci.ptScreenPos.y * scale_y)

                if cam is not None:
                    # DirectX 11 capture (1-2ms)
                    raw_bgra = cam.grab()
                    if raw_bgra is not None:
                        # Stride downsample (2560x1440 -> 1280x720) + BGRA to RGB (0.3ms)
                        step_y = max(1, raw_bgra.shape[0] // self.target_height)
                        step_x = max(1, raw_bgra.shape[1] // self.target_width)
                        rgb = np.ascontiguousarray(raw_bgra[::step_y, ::step_x, [2, 1, 0]])
                        try:
                            self.frame_queue.put_nowait((rgb, cx, cy))
                        except queue.Full:
                            pass
                elif sct is not None:
                    # Fallback MSS capture
                    mon = sct.monitors[1]
                    screen_w, screen_h = mon["width"], mon["height"]
                    sct_img = sct.grab(mon)
                    arr = np.frombuffer(sct_img.bgra, dtype=np.uint8).reshape((screen_h, screen_w, 4))
                    step_y = max(1, screen_h // self.target_height)
                    step_x = max(1, screen_w // self.target_width)
                    rgb = np.ascontiguousarray(arr[::step_y, ::step_x, [2, 1, 0]])
                    try:
                        self.frame_queue.put_nowait((rgb, cx, cy))
                    except queue.Full:
                        pass

            except Exception:
                time.sleep(0.02)

            elapsed = time.time() - t0
            sleep_time = max(0.001, self.interval - elapsed)
            time.sleep(sleep_time)

        if cam is not None:
            try:
                cam.release()
            except Exception:
                pass
        if sct is not None:
            try:
                sct.close()
            except Exception:
                pass

    def stop(self):
        self.running = False
        for _ in self.encoder_threads:
            try:
                self.frame_queue.put_nowait(None)
            except Exception:
                pass
