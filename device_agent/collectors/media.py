"""媒体信息采集 - 通过检测媒体播放器窗口标题获取"""

import win32gui
import win32process
import psutil

# 常见媒体播放器进程名（小写）
MEDIA_PLAYERS = {
    "spotify", "cloudmusic", "neteasecloudmusic", "qqmusic",
    "vlc", "potplayer", "potplayermini64", "foobar2000",
    "musicbee", "aimp", "wmplayer", "msedge", "chrome",
    "firefox", "brave",
}


def _get_window_info(hwnd):
    """获取窗口的进程名和标题"""
    try:
        _, pid = win32process.GetWindowThreadProcessId(hwnd)
        if pid == 0:
            return None, None
        process = psutil.Process(pid)
        name = process.name().replace(".exe", "").lower()
        title = win32gui.GetWindowText(hwnd)
        return name, title
    except Exception:
        return None, None


def get_media_info():
    """通过窗口标题检测当前播放的媒体信息"""
    try:
        hwnd = win32gui.GetForegroundWindow()
        if not hwnd:
            return {}

        name, title = _get_window_info(hwnd)
        if not name or not title:
            return {}

        # 只对已知媒体播放器提取信息
        if name not in MEDIA_PLAYERS:
            return {}

        # 过滤掉浏览器的通用标题
        if name in ("msedge", "chrome", "firefox", "brave"):
            # 浏览器标题通常是 "歌名 - 艺术家 - 网站名" 或类似格式
            # 但如果标题不像是在播放媒体，就跳过
            if " - " not in title:
                return {}

        # 尝试解析 "艺术家 - 歌名" 或 "歌名 - 艺术家" 格式
        parts = title.split(" - ", 1)
        if len(parts) == 2:
            return {
                "type": "music",
                "title": parts[1].strip(),
                "artist": parts[0].strip(),
                "elapsed": 0,
                "duration": 0,
            }
        else:
            return {
                "type": "music",
                "title": title.strip(),
                "artist": None,
                "elapsed": 0,
                "duration": 0,
            }

    except Exception:
        return {}
