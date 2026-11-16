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

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x1000

KEYEVENTF_EXTENDEDKEY = 0x0001
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_SCANCODE = 0x0008

EXTENDED_VKS = {
    0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28,  # PageUp, PageDown, End, Home, Arrows
    0x2D, 0x2E,                                      # Insert, Delete
    0x5B, 0x5C,                                      # Win keys
    0xA3, 0xA5,                                      # Right Ctrl, Right Alt
    0x6F,                                            # Divide
}

class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_size_t),
    ]

class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_size_t),
    ]

class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [
        ("uMsg", wintypes.DWORD),
        ("wParamL", wintypes.WORD),
        ("wParamH", wintypes.WORD),
    ]

class INPUT_UNION(ctypes.Union):
    _fields_ = [
        ("mi", MOUSEINPUT),
        ("ki", KEYBDINPUT),
        ("hi", HARDWAREINPUT),
    ]

class INPUT(ctypes.Structure):
    _fields_ = [
        ("type", wintypes.DWORD),
        ("u", INPUT_UNION),
    ]

user32.SendInput.argtypes = [wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int]
user32.SendInput.restype = wintypes.UINT

def _send_hardware_key(vk: int, is_up: bool):
    ensure_default_desktop()
    sc = user32.MapVirtualKeyW(vk, 0) # MAPVK_VK_TO_VSC

    flags = 0
    if is_up:
        flags |= KEYEVENTF_KEYUP
    if vk in EXTENDED_VKS:
        flags |= KEYEVENTF_EXTENDEDKEY

    # Method 1: SendInput with Virtual Key + Scan Code for DirectInput/RawInput games
    inp = INPUT()
    inp.type = 1 # INPUT_KEYBOARD
    inp.u.ki.wVk = vk
    inp.u.ki.wScan = sc
    inp.u.ki.dwFlags = flags
    inp.u.ki.time = 0
    inp.u.ki.dwExtraInfo = 0

    sent = user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
    # Method 2: Fallback to keybd_event with scan code
    if sent == 0:
        user32.keybd_event(vk, sc, flags, 0)

VK_MAP = {
    "enter": 0x0D,
    "backspace": 0x08,
    "tab": 0x09,
    "escape": 0x1B,
    "esc": 0x1B,
    "space": 0x20,
    "delete": 0x2E,
    "del": 0x2E,
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
    "ctrl": 0x11,
    "alt": 0x12,
    "meta": 0x5B,       # Win key
    "win": 0x5B,
    "capslock": 0x14,
    "volumeup": 0xAF,
    "volumedown": 0xAE,
    "volumemute": 0xAD,
    "mediaplaypause": 0xB3,
    "medianext": 0xB0,
    "mediaprev": 0xB1,
    "-": 0xBD,
    "minus": 0xBD,
    "=": 0xBB,
    "equal": 0xBB,
    "[": 0xDB,
    "]": 0xDD,
    "\\": 0xDC,
    ";": 0xBA,
    "'": 0xDE,
    ",": 0xBC,
    ".": 0xBE,
    "/": 0xBF,
    "`": 0xC0,
}

def mouse_move_relative(dx: int, dy: int):
    ensure_default_desktop()
    # Generates hardware relative motion packets (DirectInput/RawInput for 3D games)
    user32.mouse_event(MOUSEEVENTF_MOVE, int(dx), int(dy), 0, 0)

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
    time.sleep(0.04) # 40ms hold duration for 30/60 FPS game tick detection
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
        _send_hardware_key(vk, is_up=False)
    elif action == "up":
        _send_hardware_key(vk, is_up=True)
    else: # tap
        _send_hardware_key(vk, is_up=False)
        time.sleep(0.04)
        _send_hardware_key(vk, is_up=True)

def key_press(key_code_or_name: str, action: str = "tap"):
    ensure_default_desktop()
    key_lower = key_code_or_name.lower()
    vk = VK_MAP.get(key_lower)

    if vk is None:
        if len(key_code_or_name) == 1:
            ch = key_code_or_name.upper()
            if ('A' <= ch <= 'Z') or ('0' <= ch <= '9'):
                vk = ord(ch)
            else:
                type_text(key_code_or_name)
                return
        elif key_lower.startswith("f") and key_lower[1:].isdigit():
            f_num = int(key_lower[1:])
            if 1 <= f_num <= 12:
                vk = 0x6F + f_num
        else:
            return

    if action == "down":
        _send_hardware_key(vk, is_up=False)
    elif action == "up":
        _send_hardware_key(vk, is_up=True)
    else: # tap
        _send_hardware_key(vk, is_up=False)
        time.sleep(0.04)
        _send_hardware_key(vk, is_up=True)

def hotkey(*keys):
    ensure_default_desktop()
    vks = [VK_MAP.get(k.lower()) for k in keys if VK_MAP.get(k.lower()) is not None]
    for vk in vks:
        _send_hardware_key(vk, is_up=False)
    time.sleep(0.04)
    for vk in reversed(vks):
        _send_hardware_key(vk, is_up=True)

def type_text(text: str):
    ensure_default_desktop()
    for ch in text:
        code = ord(ch)
        user32.keybd_event(0, code, KEYEVENTF_UNICODE, 0)
        user32.keybd_event(0, code, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP, 0)
