"""
Android 设备状态采集 Agent (Termux)
使用 Termux:API 采集电量、前台应用等信息，推送到 Supabase。
"""

import time
import sys
import os
import json
import subprocess
import requests

# 配置
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    from config import SUPABASE_URL, SERVICE_ROLE_KEY, DEVICE_ID, DEVICE_NAME
except ImportError:
    print("错误: 找不到 config.py，请先运行 setup.sh")
    sys.exit(1)

PUSH_INTERVAL = 15  # 秒，手机端可以稍长一些


def get_battery():
    """通过 Termux:API 获取电量信息"""
    try:
        result = subprocess.run(
            ["termux-battery-status"],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0:
            data = json.loads(result.stdout)
            level = data.get("percentage", 0)
            # status: CHARGING, DISCHARGING, FULL, NOT_CHARGING
            charging = data.get("status", "") in ("CHARGING", "FULL")
            return level, charging
    except Exception:
        pass
    return None, False


def get_foreground_app():
    """获取当前前台应用包名"""
    try:
        # 方法1: dumpsys（大部分 Android 可用）
        result = subprocess.run(
            ["dumpsys", "activity", "activities"],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0:
            for line in result.stdout.split("\n"):
                if "mResumedActivity" in line or "topResumedActivity" in line:
                    # 格式: mResumedActivity: ActivityRecord{... com.example/.MainActivity ...}
                    parts = line.strip()
                    # 提取包名
                    if "/" in parts:
                        pkg = parts.split("/")[-2].split()[-1]
                        # 清理包名
                        pkg = pkg.strip("{}").split(".")[-1] if "." in pkg else pkg
                        return _friendly_name(parts)
                    return parts.split()[-1] if parts else None
    except Exception:
        pass

    try:
        # 方法2: dumpsys window（备用）
        result = subprocess.run(
            ["dumpsys", "window", "windows"],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0:
            for line in result.stdout.split("\n"):
                if "mCurrentFocus" in line or "mFocusedApp" in line:
                    if "/" in line:
                        return _friendly_name(line)
    except Exception:
        pass

    return None


def _friendly_name(raw):
    """把 dumpsys 输出转成友好的应用名"""
    # 常见应用包名映射
    app_names = {
        "com.tencent.mm": "微信",
        "com.tencent.mobileqq": "QQ",
        "com.ss.android.ugc.aweme": "抖音",
        "com.ss.android.article.news": "今日头条",
        "tv.danmaku.bili": "哔哩哔哩",
        "com.netease.cloudmusic": "网易云音乐",
        "com.kugou.android": "酷狗音乐",
        "com.tencent.qqmusic": "QQ音乐",
        "com.spotify.music": "Spotify",
        "com.google.android.youtube": "YouTube",
        "com.android.chrome": "Chrome",
        "org.mozilla.firefox": "Firefox",
        "com.microsoft.emmx": "Edge",
        "com.taobao.taobao": "淘宝",
        "com.jingdong.app.mall": "京东",
        "com.eg.android.AlipayGphone": "支付宝",
        "com.android.settings": "设置",
        "com.android.launcher": "桌面",
        "com.vivo.launcher": "桌面",
        "com.iqoo.launcher": "桌面",
        "com.termux": "Termux",
        "mark.via": "Via浏览器",
        "com.coolapk.market": "酷安",
        "com.zhihu.android": "知乎",
        "com.sina.weibo": "微博",
        "com.tencent.wework": "企业微信",
        "com.microsoft.teams": "Teams",
        "com.google.android.gm": "Gmail",
        "com.android.email": "邮件",
        "com.android.mms": "短信",
        "com.android.dialer": "电话",
        "com.android.camera": "相机",
        "com.android.gallery3d": "相册",
        "com.android.vending": "Play Store",
    }

    for pkg, name in app_names.items():
        if pkg in raw:
            return name

    # 尝试提取包名作为应用名
    try:
        if "/" in raw:
            pkg_part = raw.split("/")[-2].split()[-1].strip("{}")
            # 取最后一段作为名称
            return pkg_part.split(".")[-1]
    except Exception:
        pass

    return None


def build_payload(battery_level, charging, foreground_app, prev_payload):
    """构造推送数据"""
    status = "online" if foreground_app else "away"

    payload = {
        "device_id": DEVICE_ID,
        "device_name": DEVICE_NAME,
        "device_type": "phone",
        "status": status,
        "battery_level": battery_level,
        "battery_charging": charging,
        "active_app": foreground_app,
        "cpu_usage": None,
        "memory_usage": None,
        "media_type": None,
        "media_title": None,
        "media_detail": None,
        "media_elapsed": None,
        "media_duration": None,
    }

    # 变化检测
    if prev_payload:
        keys_to_check = ["status", "battery_level", "battery_charging", "active_app"]
        changed = any(payload.get(k) != prev_payload.get(k) for k in keys_to_check)
        return payload, changed

    return payload, True


def push_to_supabase(payload):
    """通过 REST API 推送到 Supabase"""
    url = f"{SUPABASE_URL}/rest/v1/device_status"
    headers = {
        "apikey": SERVICE_ROLE_KEY,
        "Authorization": f"Bearer {SERVICE_ROLE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates",
    }
    resp = requests.post(url, json=payload, headers=headers, timeout=10)
    resp.raise_for_status()


def main():
    print(f"Android 设备状态 Agent 启动")
    print(f"设备: {DEVICE_NAME} ({DEVICE_ID})")
    print(f"推送间隔: {PUSH_INTERVAL}s")
    print("按 Ctrl+C 停止")
    print("")

    prev_payload = None

    while True:
        try:
            battery_level, charging = get_battery()
            foreground_app = get_foreground_app()
            payload, changed = build_payload(
                battery_level, charging, foreground_app, prev_payload
            )

            if changed:
                push_to_supabase(payload)
                prev_payload = payload
                print(f"[{time.strftime('%H:%M:%S')}] 推送成功 | "
                      f"电量:{battery_level}% {'充电中' if charging else ''} | "
                      f"应用:{foreground_app or '未知'}")

        except Exception as e:
            print(f"[{time.strftime('%H:%M:%S')}] 错误: {e}")

        time.sleep(PUSH_INTERVAL)


if __name__ == "__main__":
    main()
