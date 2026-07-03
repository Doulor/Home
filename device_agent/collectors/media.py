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


def _get_playing_processes():
    """通过 pycaw 获取当前正在播放音频的进程名集合"""
    playing = set()
    try:
        from pycaw.pycaw import AudioUtilities
        for session in AudioUtilities.GetAllSessions():
            try:
                if session.Process and session.State == 1:  # 1 = AudioSessionStateActive
                    name = session.Process.name().replace(".exe", "").lower()
                    playing.add(name)
            except Exception:
                pass
    except Exception:
        pass
    return playing


def _enum_windows():
    """枚举所有窗口，找到播放器的窗口标题"""
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
            if name in MEDIA_PLAYERS:
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


def get_media_info():
    """通过窗口标题 + pycaw 音频会话状态检测当前播放的媒体"""
    try:
        # 先获取正在播放音频的进程
        playing_procs = _get_playing_processes()

        windows = _enum_windows()
        if not windows:
            return {}

        for proc_name in MEDIA_PLAYERS:
            if proc_name in windows:
                # 必须同时满足：窗口存在 + 音频会话正在播放
                if proc_name not in playing_procs:
                    continue
                result = _parse_title(windows[proc_name], proc_name)
                if result:
                    return result

        return {}
    except Exception:
        return {}
