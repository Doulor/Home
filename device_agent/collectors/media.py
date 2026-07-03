"""媒体信息采集 - 通过窗口标题检测"""

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


def _get_window_title(proc_name):
    """获取指定进程名的窗口标题"""
    title = None

    def callback(hwnd, _):
        nonlocal title
        try:
            if not win32gui.IsWindowVisible(hwnd):
                return True
            _, pid = win32process.GetWindowThreadProcessId(hwnd)
            if pid == 0:
                return True
            process = psutil.Process(pid)
            name = process.name().replace(".exe", "").lower()
            if name == proc_name:
                t = win32gui.GetWindowText(hwnd)
                if t and t not in ("", "Default IME", "MSCTFIME UI"):
                    title = t
        except Exception:
            pass
        return True

    win32gui.EnumWindows(callback, None)
    return title


def _parse_player_title(title, player_name):
    """解析播放器窗口标题"""
    if not title or len(title) < 2:
        return None

    title = title.strip()
    display_name = MEDIA_PLAYERS.get(player_name, "")

    # 标题只有播放器名 → 未播放
    if title == display_name:
        return None

    # 去掉末尾播放器名
    if display_name and title.endswith(display_name):
        title = title[: -len(display_name)].rstrip(" -")

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
    """解析浏览器标题，提取标签页标题和数量"""
    if not title or len(title) < 3:
        return None

    title = title.strip()
    tab_count = None

    # 提取标签页数量
    if " 和另外 " in title:
        try:
            count_part = title[title.index(" 和另外 ") + 5:]
            count_str = count_part.split(" 个页面")[0].strip()
            tab_count = int(count_str) + 1
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

    if " 和另外 " in clean_title:
        clean_title = clean_title[: clean_title.index(" 和另外 ")]

    clean_title = clean_title.strip()
    if not clean_title:
        return None

    player = BROWSER_NAMES.get(browser_name, browser_name)
    count_text = f"{tab_count} 个标签页" if tab_count else None

    return {
        "type": "browser",
        "title": clean_title,
        "artist": count_text,
        "player": player,
    }


def get_media_info():
    """检测当前播放的媒体信息"""
    try:
        playing_procs = _get_playing_processes()
        foreground = _get_foreground_process()

        # 优先检查专用播放器（需要正在播放音频）
        for proc_name in MEDIA_PLAYERS:
            title = _get_window_title(proc_name)
            if not title:
                continue
            if proc_name not in playing_procs:
                continue
            result = _parse_player_title(title, proc_name)
            if result:
                return result

        # 浏览器在前台时，始终显示标签页标题
        for proc_name in BROWSER_NAMES:
            if proc_name == foreground:
                title = _get_window_title(proc_name)
                if title:
                    result = _parse_browser_title(title, proc_name)
                    if result:
                        return result

        return {}
    except Exception:
        return {}
