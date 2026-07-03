import win32gui
import win32process
import psutil

# 浏览器进程名 → 简短显示名
BROWSER_NAMES = {
    "msedge": "Edge",
    "chrome": "Chrome",
    "firefox": "Firefox",
    "brave": "Brave",
}


def get_active_window():
    """获取当前前台窗口的显示名称"""
    try:
        hwnd = win32gui.GetForegroundWindow()
        if not hwnd:
            return None
        _, pid = win32process.GetWindowThreadProcessId(hwnd)
        if pid == 0:
            return None
        process = psutil.Process(pid)
        name = process.name().replace(".exe", "")

        # 浏览器：显示完整标签页标题
        if name.lower() in BROWSER_NAMES:
            title = win32gui.GetWindowText(hwnd)
            if title and title.strip():
                return title.strip()
            return BROWSER_NAMES[name.lower()]

        return name
    except Exception:
        return None
