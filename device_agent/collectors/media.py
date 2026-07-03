"""媒体信息采集 - SMTC API 优先，降级到窗口标题 + pycaw"""

import win32gui
import win32process
import psutil
import subprocess
import json
import tempfile
import os
import time

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

# SMTC 播放状态
SMTC_PLAYING = 4
SMTC_PAUSED = 5

# 进度追踪（SMTC position 不可靠时用时间推算）
_track = {"title": None, "start_time": 0, "paused": True, "last_elapsed": 0}

# PowerShell SMTC 脚本
_PS_SMTC = r'''
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
    $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]
    function Await($WinRtTask, $ResultType) {
        $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
        $netTask = $asTask.Invoke($null, @($WinRtTask))
        $netTask.Wait(-1) | Out-Null
        $netTask.Result
    }
    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    $session = $manager.GetCurrentSession()
    if (-not $session) { Write-Output '{}'; exit }
    $info = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $playback = $session.GetPlaybackInfo()
    $timeline = $session.GetTimelineProperties()
    $r = @{
        title = $info.Title
        artist = $info.Artist
        album = $info.AlbumTitle
        status = [int]$playback.PlaybackStatus
        position = [long]($timeline.Position.TotalMilliseconds)
        duration = [long]($timeline.EndTime.TotalMilliseconds)
    }
    $r | ConvertTo-Json -Compress
} catch {
    Write-Output '{}'
}
'''


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


def _get_smtc_info():
    """通过 PowerShell SMTC API 获取当前播放的媒体信息"""
    try:
        with tempfile.NamedTemporaryFile(mode='w', suffix='.ps1', delete=False, encoding='utf-8') as f:
            f.write(_PS_SMTC)
            script_path = f.name

        result = subprocess.run(
            ['powershell', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script_path],
            capture_output=True, text=True, timeout=10
        )
        os.unlink(script_path)

        output = result.stdout.strip()
        if output and output != '{}':
            data = json.loads(output)
            if data.get('title'):
                return data
    except Exception:
        pass
    return None


def _clean_title(title):
    """清理媒体标题（去掉网站后缀），同时返回检测到的平台名"""
    if not title:
        return title, None

    platform = None

    if '_哔哩哔哩_bilibili' in title or ' - 哔哩哔哩' in title or '_bilibili' in title:
        platform = '哔哩哔哩'
        for suffix in ("_哔哩哔哩_bilibili", " - 哔哩哔哩", "_bilibili"):
            if title.endswith(suffix):
                title = title[: -len(suffix)]
                break

    if title.endswith(" - YouTube"):
        platform = 'YouTube'
        title = title[: -10]

    return title.strip(), platform


def _get_elapsed(position_ms):
    """获取当前播放进度（秒），优先用 SMTC position，不准时用时间推算"""
    global _track

    # SMTC position 可用时直接用（大于 1 秒说明是有效值）
    if position_ms and position_ms > 1000:
        elapsed = position_ms // 1000
        # 同步时间追踪基准
        _track["last_elapsed"] = elapsed
        _track["start_time"] = time.time() - elapsed
        return elapsed

    # SMTC position 不可靠（0 或很小），用时间推算
    if _track["paused"]:
        return _track["last_elapsed"]
    if _track["start_time"]:
        return int(time.time() - _track["start_time"])
    return 0


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

    if title == display_name:
        return None

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
    """解析浏览器标题"""
    if not title or len(title) < 3:
        return None

    title = title.strip()
    tab_count = None

    if " 和另外 " in title:
        try:
            count_part = title[title.index(" 和另外 ") + 5:]
            count_str = count_part.split(" 个页面")[0].strip()
            tab_count = int(count_str) + 1
        except (ValueError, IndexError):
            pass

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
    global _track
    try:
        # 1. SMTC API
        smtc = _get_smtc_info()
        if smtc:
            status = smtc.get('status', 0)
            raw_title = smtc.get('title', '')
            title, detected_platform = _clean_title(raw_title)
            position_ms = smtc.get('position', 0)
            duration_ms = smtc.get('duration', 0)

            if title and status == SMTC_PLAYING:
                # 切歌检测
                if _track["title"] != title:
                    _track["title"] = title
                    _track["start_time"] = time.time()
                    _track["paused"] = False
                    _track["last_elapsed"] = 0
                elif _track["paused"]:
                    # 从暂停恢复
                    _track["paused"] = False
                    _track["start_time"] = time.time() - _track["last_elapsed"]

                elapsed = _get_elapsed(position_ms)
                duration = duration_ms // 1000 if duration_ms else 0

                # 确定平台名和艺术家
                artist = smtc.get('artist') or None
                album = smtc.get('album') or None

                # 平台名：从标题检测到的 > SMTC album > None
                player = detected_platform or album or None

                return {
                    "type": "music",
                    "title": title,
                    "artist": artist,
                    "player": player,
                    "elapsed": elapsed,
                    "duration": duration,
                }

            # 暂停状态
            if status == SMTC_PAUSED and title:
                if not _track["paused"]:
                    _track["last_elapsed"] = _get_elapsed(position_ms)
                    _track["paused"] = True

        # 2. 降级：窗口标题 + pycaw
        playing_procs = _get_playing_processes()
        foreground = _get_foreground_process()

        for proc_name in MEDIA_PLAYERS:
            if proc_name in playing_procs:
                title = _get_window_title(proc_name)
                if title:
                    result = _parse_player_title(title, proc_name)
                    if result:
                        return result

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
