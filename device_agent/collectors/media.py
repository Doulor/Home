"""媒体信息采集 - 通过 Windows SMTC API 获取媒体播放信息"""

import subprocess
import json


# PowerShell 脚本：通过 SMTC API 获取当前播放的媒体信息
_PS_SCRIPT = r'''
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSession, Windows.Media.Control, ContentType=WindowsRuntime]

    $asyncOp = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()
    $manager = $asyncOp.GetAwaiter().GetResult()
    $session = $manager.GetCurrentSession()
    if (-not $session) { exit }

    $info = $session.TryGetMediaPropertiesAsync().GetAwaiter().GetResult()
    $timeline = $session.GetTimelineProperties()
    $title = $info.Title
    if (-not $title) { exit }

    $pos = [math]::Floor($timeline.Position.TotalSeconds)
    $dur = [math]::Floor($timeline.EndTime.TotalSeconds)

    $result = @{
        type = "music"
        title = $title
        artist = $info.Artist
        elapsed = $pos
        duration = $dur
    }
    $result | ConvertTo-Json -Compress
} catch {
    # 静默失败
}
'''


def get_media_info():
    """通过 SMTC API 获取当前播放的媒体信息"""
    try:
        result = subprocess.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command", _PS_SCRIPT],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0 and result.stdout.strip():
            data = json.loads(result.stdout.strip())
            if data.get("title"):
                return {
                    "type": data.get("type", "music"),
                    "title": data["title"],
                    "artist": data.get("artist"),
                    "elapsed": data.get("elapsed", 0),
                    "duration": data.get("duration", 0),
                }
    except Exception:
        pass
    return {}
