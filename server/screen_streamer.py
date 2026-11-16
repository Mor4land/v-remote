import ctypes
import io
import time
from ctypes import wintypes
import mss
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
    """Ensures current thread is bound to user's interactive 'Default' desktop."""
    try:
        hdesk = user32.OpenDesktopW("Default", 0, False, DESKTOP_ALL_ACCESS)
        if hdesk:
            user32.SetThreadDesktop(hdesk)
            return hdesk
    except Exception:
        pass
    return None

class ScreenCapturer:
    def __init__(self, target_width=1280, target_height=720, quality=60):
        self.target_width = target_width
        self.target_height = target_height
        self.quality = quality

        attach_to_default_desktop()
        self.sct = mss.MSS()
        self.monitor = self.sct.monitors[1]
        self.screen_width = self.monitor["width"]
        self.screen_height = self.monitor["height"]

    def capture_frame(self) -> bytes:
        attach_to_default_desktop()

        # Grab from MSS
        sct_img = self.sct.grab(self.monitor)
        im = Image.frombytes("RGB", sct_img.size, sct_img.bgra, "raw", "BGRX")

        # Resize to streaming resolution
        if im.size != (self.target_width, self.target_height):
            im = im.resize((self.target_width, self.target_height), Image.BILINEAR)

        # Draw red dot cursor indicator so user sees where cursor is
        ci = CURSORINFO()
        ci.cbSize = ctypes.sizeof(CURSORINFO)
        if user32.GetCursorInfo(ctypes.byref(ci)):
            if ci.flags & CURSOR_SHOWING:
                scale_x = self.target_width / self.screen_width
                scale_y = self.target_height / self.screen_height
                cx = int(ci.ptScreenPos.x * scale_x)
                cy = int(ci.ptScreenPos.y * scale_y)
                # Draw simple crosshair / dot on frame
                from PIL import ImageDraw
                draw = ImageDraw.Draw(im)
                r = 4
                draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(239, 68, 68), outline=(255, 255, 255))

        out = io.BytesIO()
        im.save(out, format="JPEG", quality=self.quality, optimize=False)
        return out.getvalue()

    def close(self):
        try:
            self.sct.close()
        except Exception:
            pass
