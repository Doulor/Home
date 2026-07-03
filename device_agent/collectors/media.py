"""媒体信息采集 - 通过窗口标题 + 音频会话状态检测"""

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
BROWSER_NAMES = {
    "msedge": "Edge",
    "chrome": "Chrome",
    "firefox": "Firefox",
    "brave": "Brave",
}


def _get_playing_processes():
    """通过 pycaw 获取当前正在播放音频的进程名集合"""
    playing = set()
    try:
        from pycaw.pycaw import AudioUtilities
        for session in AudioUtilities.GetAllSessions():
            try:
                if session.Process and session.State == 1:
                    name = session.Process.name().replace(".exe", "").lower()
                    playing.add(name)
            except Exception:
                pass
    except Exception:
        pass
    return playing


def _get_foreground_process():
    """获取当前前台窗口的进程名（小写）"""
    try:
        hwnd = win32gui.GetForegroundWindow()
        if not hwnd:
            return None
        _, pid = win32process.GetWindowThreadProcessId(hwnd)
        if pid == 0:
            return None
        process = psutil.Process(pid)
        return process.name().replace(".exe", "").lower()
    except Exception:
        return None


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
            if name in MEDIA_PLAYERS or name in BROWSER_NAMES:
                title = win32gui.GetWindowText(hwnd)
                if title and title not in ("", "Default IME", "MSCTFIME UI"):
                    result[name] = title
        except Exception:
            pass
        return True

    win32gui.EnumWindows(callback, None)
    return result


def _parse_title(title, player_name):
    """解析窗口标题，提取歌名和艺术家"""
    if not title or len(title) < 2:
        return None

    title = title.strip()
    display_name = MEDIA_PLAYERS.get(player_name, "")

    # 标题只有播放器名本身（如 "酷狗音乐"）→ 未播放
    if title == display_name:
        return None

    # 去掉末尾的播放器名称（如 "艺术家 - 歌名 - 酷狗音乐"）
    if display_name and title.endswith(display_name):
        title = title[: -len(display_name)].rstrip(" -")

    # 需要至少一个 " - "（酷狗播放格式: "艺术家 - 歌名"）
    if " - " not in title:
        return None

    parts = title.split(" - ", 1)
    artist = parts[0].strip()
    song = parts[1].strip()

    if not song:
        return None

    return {
        "type": "music",
        "title": song,
        "artist": artist,
        "player": display_name,
    }


def _parse_browser_title(title, browser_name):
    """解析浏览器标题，提取标签页标题和标签页数量"""
    if not title or len(title) < 3:
        return None

    title = title.strip()
    tab_count = None

    # 提取标签页数量：格式 "xxx 和另外 N 个页面"
    if " 和另外 " in title:
        try:
            count_part = title[title.index(" 和另外 ") + 5:]
            count_str = count_part.split(" 个页面")[0].strip()
            tab_count = int(count_str) + 1  # +1 是当前标签页
        except (ValueError, IndexError):
            pass

    # 去掉浏览器后缀
    clean_title = title
    for suffix in (" - 个人 - Microsoft\u200b Edge", " - Microsoft\u200b Edge",
                   " - Personal - Microsoft Edge", " - Microsoft Edge",
                   " - Google Chrome", " - Firefox", " - Brave"):
        if clean_title.endswith(suffix):
            clean_title = clean_title[: -len(suffix)]
            break

    # 去掉 "和另外 N 个页面"
    if " 和另外 " in clean_title:
        clean_title = clean_title[: clean_title.index(" 和另外 ")]

    clean_title = clean_title.strip()
    if not clean_title:
        return None

    detail = clean_title
    if tab_count:
        detail = f"{clean_title} ({tab_count} 个标签页)"

    return {
        "type": "browser",
        "title": detail,
        "artist": None,
        "player": BROWSER_NAMES.get(browser_name, browser_name),
    }


def get_media_info():
    """通过窗口标题 + pycaw 音频会话状态检测当前播放的媒体"""
    try:
        playing_procs = _get_playing_processes()
        foreground = _get_foreground_process()
        windows = _enum_windows()
        if not windows:
            return {}

        # 优先检查专用播放器（需要音频会话正在播放）
        for proc_name in MEDIA_PLAYERS:
            if proc_name in windows:
                if proc_name not in playing_procs:
                    continue
                result = _parse_title(windows[proc_name], proc_name)
                if result:
                    return result

        # 浏览器在前台时，始终显示标签页标题（不需要正在播放音频）
        for proc_name in BROWSER_NAMES:
            if proc_name in windows and proc_name == foreground:
                result = _parse_browser_title(windows[proc_name], proc_name)
                if result:
                    return result

        return {}
    except Exception:
        return {}
