import ctypes
import tkinter as tk

user32 = ctypes.windll.user32
hdesk = user32.OpenDesktopW("Default", 0, False, 0x01FF)
if hdesk:
    user32.SetThreadDesktop(hdesk)

root = tk.Tk()
root.title("ВНИМАНИЕ")
root.attributes("-topmost", True)
root.configure(bg="#0f1117")

# Fullscreen-like large banner
root.overrideredirect(True) # borderless
screen_w = root.winfo_screenwidth()
screen_h = root.winfo_screenheight()

w = int(screen_w * 0.75)
h = int(screen_h * 0.45)
x = (screen_w - w) // 2
y = (screen_h - h) // 2

root.geometry(f"{w}x{h}+{x}+{y}")

frame = tk.Frame(root, bg="#1a1d27", highlightthickness=6, highlightbackground="#ef4444")
frame.pack(fill="both", expand=True, padx=8, pady=8)

lbl_warn = tk.Label(frame, text="⚠️ ВНИМАНИЕ! ⚠️", font=("Segoe UI", 34, "bold"), fg="#f59e0b", bg="#1a1d27")
lbl_warn.pack(pady=(35, 10))

lbl_msg = tk.Label(frame, text="МАМА, НЕ ТРОГАЙ ПК!", font=("Segoe UI", 48, "bold"), fg="#ef4444", bg="#1a1d27")
lbl_msg.pack(pady=15)

lbl_sub = tk.Label(frame, text="Компьютер управляется удалённо", font=("Segoe UI", 20), fg="#94a3b8", bg="#1a1d27")
lbl_sub.pack(pady=(5, 25))

root.mainloop()
