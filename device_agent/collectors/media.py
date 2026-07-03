"""媒体信息采集 - 通过检测音乐播放器窗口标题获取"""

import win32gui
import win32process
import psutil

# 常见媒体播放器进程名（小写）→ 显示名
MEDIA_PLAYERS = {
    "cloudmusic": "网易云音乐",
    "neteasecloudmusic": "网易云音乐",
    "qqmusic": "QQ音乐",
    "kugou": "酷狗音乐",
    "kugouwxmini": "酷狗音乐",
    "spotify": "Spotify",
    "vlc": "VLC",
    "potplayer": "PotPlayer",
    "potplayermini64": "PotPlayer",
    "foobar2000": "foobar2000",
    "musicbee": "MusicBee",
    "aimp": "AIMP",
}

# 浏览器进程名
BROWSERS = {"msedge", "chrome", "firefox", "brave"}


def _enum_windows():
    """枚举所有窗口，找到播放器和浏览器的窗口标题"""
    result = {}

    def callback(hwnd, _):
        try:
            if not win32gui.IsWindowVisible(hwnd):
                return True
            _, pid = win32process.GetWindowThreadProcessId(hwnd)
            if pid == 0:
                return True
            process = psutil.Process(pid)
            name = process.name().replace(".exe", "").lower()
            if name in MEDIA_PLAYERS or name in BROWSERS:
                title = win32gui.GetWindowText(hwnd)
                if title and title not in ("", "Default IME", "MSCTFIME UI"):
                    result[name] = title
        except Exception:
            pass
        return True

    win32gui.EnumWindows(callback, None)
    return result


def _parse_player_title(title, player_name):
    """解析播放器窗口标题，提取歌名和艺术家。暂停时返回 None。"""
    if not title or len(title) < 2:
        return None

    title = title.strip()
    display_name = MEDIA_PLAYERS.get(player_name, "")

    # 如果标题就是播放器名本身（没有 " - "），说明暂停或未播放
    if title == display_name:
        return None

    # 去掉末尾的播放器名称（如 "艺术家 - 歌名 - 酷狗音乐"）
    if display_name and title.endswith(display_name):
        title = title[: -len(display_name)].rstrip(" -")

    # 需要至少有一个 " - " 才算在播放（酷狗格式: "艺术家 - 歌名"）
    if " - " in title:
        parts = title.split(" - ", 1)
        artist = parts[0].strip()
        song = parts[1].strip()
        return {
            "type": "music",
            "title": song,
            "artist": artist,
            "player": display_name,
        }

    # 只有一个词，可能是歌名但不确定，忽略
    return None


def _parse_browser_title(title):
    """解析浏览器标题，检测 B站 视频播放"""
    if not title or len(title) < 3:
        return None

    title = title.strip()
    low = title.lower()

    # B站视频标题格式: "视频标题 - 哔哩哔哩" 或 "视频标题_bilibili"
    if "哔哩哔哩" in low or "bilibili" in low:
        # 去掉网站后缀
        for suffix in (" - 哔哩哔哩", " - bilibili", "_bilibili", "-哔哩哔哩"):
            if title.endswith(suffix):
                video_title = title[: -len(suffix)].strip()
                if video_title:
                    return {
                        "type": "video",
                        "title": video_title,
                        "artist": None,
                        "player": "哔哩哔哩",
                    }
        # 没有匹配到后缀但标题包含 B站标识
        return None

    return None


def get_media_info():
    """通过窗口标题检测当前播放的媒体信息"""
    try:
        windows = _enum_windows()
        if not windows:
            return {}

        # 优先检查专用播放器
        for proc_name in MEDIA_PLAYERS:
            if proc_name in windows:
                result = _parse_player_title(windows[proc_name], proc_name)
                if result:
                    return result

        # 再检查浏览器（B站等）
        for proc_name in BROWSERS:
            if proc_name in windows:
                result = _parse_browser_title(windows[proc_name])
                if result:
                    return result

        return {}
    except Exception:
        return {}
