import ctypes
import time
from ctypes import wintypes

user32 = ctypes.windll.user32

class POINT(ctypes.Structure):
    _fields_ = [("x", wintypes.LONG), ("y", wintypes.LONG)]

DESKTOP_ALL_ACCESS = 0x01FF

def ensure_default_desktop():
    """Binds the current thread to the user's interactive 'Default' desktop."""
    try:
        hdesk = user32.OpenDesktopW("Default", 0, False, DESKTOP_ALL_ACCESS)
        if hdesk:
            user32.SetThreadDesktop(hdesk)
            return hdesk
    except Exception:
        pass
    return None

MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x1000

KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004

VK_MAP = {
    "enter": 0x0D,
    "backspace": 0x08,
    "tab": 0x09,
    "escape": 0x1B,
    "space": 0x20,
    "delete": 0x2E,
    "home": 0x24,
    "end": 0x23,
    "pageup": 0x21,
    "pagedown": 0x22,
    "arrowleft": 0x25,
    "arrowup": 0x26,
    "arrowright": 0x27,
    "arrowdown": 0x28,
    "shift": 0x10,
    "control": 0x11,
    "alt": 0x12,
    "meta": 0x5B,       # Win key
    "capslock": 0x14,
    "volumeup": 0xAF,
    "volumedown": 0xAE,
    "volumemute": 0xAD,
    "mediaplaypause": 0xB3,
    "medianext": 0xB0,
    "mediaprev": 0xB1,
}

def mouse_move_relative(dx: int, dy: int):
    ensure_default_desktop()
    pt = POINT()
    if user32.GetCursorPos(ctypes.byref(pt)):
        new_x = max(0, pt.x + int(dx))
        new_y = max(0, pt.y + int(dy))
        user32.SetCursorPos(new_x, new_y)

def mouse_move_absolute(rx: float, ry: float):
    ensure_default_desktop()
    sw = user32.GetSystemMetrics(0)
    sh = user32.GetSystemMetrics(1)
    target_x = int(max(0.0, min(1.0, rx)) * sw)
    target_y = int(max(0.0, min(1.0, ry)) * sh)
    user32.SetCursorPos(target_x, target_y)

def mouse_button(button: str, action: str):
    ensure_default_desktop()
    btn = button.lower()
    act = action.lower()

    flag = 0
    if btn == "left":
        flag = MOUSEEVENTF_LEFTDOWN if act == "down" else MOUSEEVENTF_LEFTUP
    elif btn == "right":
        flag = MOUSEEVENTF_RIGHTDOWN if act == "down" else MOUSEEVENTF_RIGHTUP
    elif btn == "middle":
        flag = MOUSEEVENTF_MIDDLEDOWN if act == "down" else MOUSEEVENTF_MIDDLEUP

    if flag:
        user32.mouse_event(flag, 0, 0, 0, 0)

def mouse_click(button: str = "left"):
    ensure_default_desktop()
    mouse_button(button, "down")
    time.sleep(0.015)
    mouse_button(button, "up")

def mouse_scroll(delta_y: int, delta_x: int = 0):
    ensure_default_desktop()
    if delta_y != 0:
        amount = int(delta_y * 120)
        user32.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, amount, 0)
    if delta_x != 0:
        amount = int(delta_x * 120)
        user32.mouse_event(MOUSEEVENTF_HWHEEL, 0, 0, amount, 0)

def key_press_vk(vk: int, action: str = "tap"):
    ensure_default_desktop()
    if action == "down":
        user32.keybd_event(vk, 0, 0, 0)
    elif action == "up":
        user32.keybd_event(vk, 0, KEYEVENTF_KEYUP, 0)
    else: # tap
        user32.keybd_event(vk, 0, 0, 0)
        time.sleep(0.015)
        user32.keybd_event(vk, 0, KEYEVENTF_KEYUP, 0)

def key_press(key_code_or_name: str, action: str = "tap"):
    ensure_default_desktop()
    key_lower = key_code_or_name.lower()
    vk = VK_MAP.get(key_lower)

    if vk is None:
        if len(key_code_or_name) == 1:
            type_text(key_code_or_name)
        return

    if action == "down":
        user32.keybd_event(vk, 0, 0, 0)
    elif action == "up":
        user32.keybd_event(vk, 0, KEYEVENTF_KEYUP, 0)
    else: # tap
        user32.keybd_event(vk, 0, 0, 0)
        time.sleep(0.015)
        user32.keybd_event(vk, 0, KEYEVENTF_KEYUP, 0)

def hotkey(*keys):
    ensure_default_desktop()
    vks = [VK_MAP.get(k.lower()) for k in keys if VK_MAP.get(k.lower()) is not None]
    for vk in vks:
        user32.keybd_event(vk, 0, 0, 0)
    time.sleep(0.02)
    for vk in reversed(vks):
        user32.keybd_event(vk, 0, KEYEVENTF_KEYUP, 0)

def type_text(text: str):
    ensure_default_desktop()
    for ch in text:
        code = ord(ch)
        user32.keybd_event(0, code, KEYEVENTF_UNICODE, 0)
        user32.keybd_event(0, code, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP, 0)
