import ctypes
import time
from ctypes import wintypes

# Win32 Constants
INPUT_MOUSE = 0
INPUT_KEYBOARD = 1

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x1000
MOUSEEVENTF_ABSOLUTE = 0x8000

KEYEVENTF_EXTENDEDKEY = 0x0001
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_SCANCODE = 0x0008

WHEEL_DELTA = 120

# C Structs
class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_ulonglong)
    ]

class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_ulonglong)
    ]

class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [
        ("uMsg", wintypes.DWORD),
        ("wParamL", wintypes.WORD),
        ("wParamH", wintypes.WORD)
    ]

class _INPUT_UNION(ctypes.Union):
    _fields_ = [
        ("mi", MOUSEINPUT),
        ("ki", KEYBDINPUT),
        ("hi", HARDWAREINPUT)
    ]

class INPUT(ctypes.Structure):
    _fields_ = [
        ("type", wintypes.DWORD),
        ("u", _INPUT_UNION)
    ]

user32 = ctypes.windll.user32

# Virtual key map for common control keys
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
    "f1": 0x70,
    "f2": 0x71,
    "f3": 0x72,
    "f4": 0x73,
    "f5": 0x74,
    "f6": 0x75,
    "f7": 0x76,
    "f8": 0x77,
    "f9": 0x78,
    "f10": 0x79,
    "f11": 0x7A,
    "f12": 0x7B,
    "volumeup": 0xAF,
    "volumedown": 0xAE,
    "volumemute": 0xAD,
    "mediaplaypause": 0xB3,
    "medianext": 0xB0,
    "mediaprev": 0xB1,
}

def _send_inputs(inputs):
    n = len(inputs)
    arr = (INPUT * n)(*inputs)
    user32.SendInput(n, ctypes.byref(arr), ctypes.sizeof(INPUT))

def mouse_move_relative(dx: int, dy: int):
    inp = INPUT(type=INPUT_MOUSE)
    inp.u.mi.dx = int(dx)
    inp.u.mi.dy = int(dy)
    inp.u.mi.mouseData = 0
    inp.u.mi.dwFlags = MOUSEEVENTF_MOVE
    inp.u.mi.time = 0
    inp.u.mi.dwExtraInfo = 0
    _send_inputs([inp])

def mouse_move_absolute(rx: float, ry: float):
    rx = max(0.0, min(1.0, float(rx)))
    ry = max(0.0, min(1.0, float(ry)))
    inp = INPUT(type=INPUT_MOUSE)
    inp.u.mi.dx = int(rx * 65535)
    inp.u.mi.dy = int(ry * 65535)
    inp.u.mi.mouseData = 0
    inp.u.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE
    inp.u.mi.time = 0
    inp.u.mi.dwExtraInfo = 0
    _send_inputs([inp])

def mouse_button(button: str, action: str):
    flags = 0
    btn = button.lower()
    act = action.lower()

    if btn == "left":
        flags = MOUSEEVENTF_LEFTDOWN if act == "down" else MOUSEEVENTF_LEFTUP
    elif btn == "right":
        flags = MOUSEEVENTF_RIGHTDOWN if act == "down" else MOUSEEVENTF_RIGHTUP
    elif btn == "middle":
        flags = MOUSEEVENTF_MIDDLEDOWN if act == "down" else MOUSEEVENTF_MIDDLEUP

    if flags:
        inp = INPUT(type=INPUT_MOUSE)
        inp.u.mi.dwFlags = flags
        _send_inputs([inp])

def mouse_click(button: str = "left"):
    mouse_button(button, "down")
    time.sleep(0.015)
    mouse_button(button, "up")

def mouse_scroll(delta_y: int, delta_x: int = 0):
    inputs = []
    if delta_y != 0:
        inp_y = INPUT(type=INPUT_MOUSE)
        inp_y.u.mi.dwFlags = MOUSEEVENTF_WHEEL
        inp_y.u.mi.mouseData = int(delta_y * 120)
        inputs.append(inp_y)
    if delta_x != 0:
        inp_x = INPUT(type=INPUT_MOUSE)
        inp_x.u.mi.dwFlags = MOUSEEVENTF_HWHEEL
        inp_x.u.mi.mouseData = int(delta_x * 120)
        inputs.append(inp_x)
    if inputs:
        _send_inputs(inputs)

def key_press(key_code_or_name: str, action: str = "tap"):
    key_lower = key_code_or_name.lower()
    vk = VK_MAP.get(key_lower)

    if vk is None:
        if len(key_code_or_name) == 1:
            char_code = ord(key_code_or_name)
            vk = user32.VkKeyScanW(wintypes.WCHAR(key_code_or_name)) & 0xFF
            if vk == 0xFF:
                # Type as unicode directly
                type_text(key_code_or_name)
                return
        else:
            return

    def make_ki(dwFlags):
        inp = INPUT(type=INPUT_KEYBOARD)
        inp.u.ki.wVk = vk
        inp.u.ki.wScan = 0
        inp.u.ki.dwFlags = dwFlags
        return inp

    if action == "down":
        _send_inputs([make_ki(0)])
    elif action == "up":
        _send_inputs([make_ki(KEYEVENTF_KEYUP)])
    else:  # tap
        _send_inputs([make_ki(0)])
        time.sleep(0.015)
        _send_inputs([make_ki(KEYEVENTF_KEYUP)])

def type_text(text: str):
    """Sends text via UTF-16 Unicode events (handles any language/Cyrillic regardless of active Windows layout)."""
    inputs = []
    for ch in text:
        code = ord(ch)
        # Down
        inp_down = INPUT(type=INPUT_KEYBOARD)
        inp_down.u.ki.wVk = 0
        inp_down.u.ki.wScan = code
        inp_down.u.ki.dwFlags = KEYEVENTF_UNICODE
        inputs.append(inp_down)
        # Up
        inp_up = INPUT(type=INPUT_KEYBOARD)
        inp_up.u.ki.wVk = 0
        inp_up.u.ki.wScan = code
        inp_up.u.ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP
        inputs.append(inp_up)

    if inputs:
        _send_inputs(inputs)

def hotkey(*keys):
    """Executes a hotkey combination (e.g. ['control', 'c'] or ['meta', 'd'])."""
    down_inputs = []
    up_inputs = []
    for k in keys:
        vk = VK_MAP.get(k.lower())
        if vk is not None:
            d = INPUT(type=INPUT_KEYBOARD)
            d.u.ki.wVk = vk
            d.u.ki.dwFlags = 0
            down_inputs.append(d)

            u = INPUT(type=INPUT_KEYBOARD)
            u.u.ki.wVk = vk
            u.u.ki.dwFlags = KEYEVENTF_KEYUP
            up_inputs.insert(0, u)  # release in reverse order

    if down_inputs:
        _send_inputs(down_inputs)
        time.sleep(0.03)
        _send_inputs(up_inputs)
