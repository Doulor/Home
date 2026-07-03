import win32gui
import win32process
import psutil


def get_active_window():
    """获取当前前台窗口的进程名"""
    try:
        hwnd = win32gui.GetForegroundWindow()
        if not hwnd:
            return None
        _, pid = win32process.GetWindowThreadProcessId(hwnd)
        if pid == 0:
            return None
        process = psutil.Process(pid)
        name = process.name().replace(".exe", "")
        return name
    except Exception:
        return None
