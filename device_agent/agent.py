"""
设备状态采集 Agent
每 10 秒采集一次系统信息并推送到 Supabase。

使用方法:
  1. pip install -r requirements.txt
  2. 复制 config.example.py 为 config.py，填入你的 service_role key
  3. python agent.py
  4. (可选) 用 pythonw agent.py 无窗口后台运行
"""

import time
import sys
import os

# 确保能导入同目录模块
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from supabase import create_client
from collectors.system import get_system_metrics
from collectors.window import get_active_window
from collectors.media import get_media_info

# ── 配置 ──────────────────────────────────────────────
# 优先从 config.py 读取，否则从环境变量读取
try:
    from config import SUPABASE_URL, SERVICE_ROLE_KEY
except ImportError:
    SUPABASE_URL = os.environ.get("SUPABASE_URL", "")
    SERVICE_ROLE_KEY = os.environ.get("SERVICE_ROLE_KEY", "")

DEVICE_ID = "windows-pc"
DEVICE_NAME = "Desktop"
PUSH_INTERVAL = 5  # 秒
# ──────────────────────────────────────────────────────


def detect_status(cpu, active_window):
    """根据 CPU 和活动窗口推断设备状态"""
    if active_window is None:
        return "away"
    if cpu and cpu > 90:
        return "online"
    return "online"


def build_payload(metrics, window, media, prev_payload):
    """构造推送数据，返回 (payload, changed)"""
    status = detect_status(metrics["cpu"], window)

    payload = {
        "device_id": DEVICE_ID,
        "device_name": DEVICE_NAME,
        "device_type": "desktop",
        "status": status,
        "cpu_usage": metrics["cpu"],
        "memory_usage": metrics["memory_percent"],
        "memory_total": metrics["memory_total_mb"],
        "memory_used": metrics["memory_used_mb"],
        "battery_level": metrics.get("battery"),
        "battery_charging": metrics.get("battery_charging", False),
        "active_app": window,
        "media_type": media.get("type"),
        "media_title": f"{media.get('player', '')} - {media.get('title', '')}" if media.get("title") and media.get("player") else media.get("title"),
        "media_detail": media.get("artist"),
        "media_elapsed": media.get("elapsed"),
        "media_duration": media.get("duration"),
        "extra": {"uptime": metrics.get("uptime", 0)},
        "updated_at": "now()",
    }

    # 变化检测：仅在关键字段变化时才推送
    if prev_payload:
        keys_to_check = [
            "status", "active_app", "cpu_usage", "memory_usage",
            "battery_level", "battery_charging",
            "media_title", "media_detail", "media_elapsed",
            "extra",
        ]
        changed = any(
            payload.get(k) != prev_payload.get(k) for k in keys_to_check
        )
        return payload, changed

    return payload, True


def main():
    if not SUPABASE_URL or not SERVICE_ROLE_KEY:
        print("错误: 请在 config.py 或环境变量中设置 SUPABASE_URL 和 SERVICE_ROLE_KEY")
        sys.exit(1)

    supabase = create_client(SUPABASE_URL, SERVICE_ROLE_KEY)
    prev_payload = None

    # 启动时强制推送一次（确保 uptime 等字段更新）
    try:
        metrics = get_system_metrics()
        window = get_active_window()
        media = get_media_info()
        payload, _ = build_payload(metrics, window, media, None)
        supabase.table("device_status").upsert(payload).execute()
        prev_payload = payload
        print(f"设备状态 Agent 启动 (设备: {DEVICE_ID}, 间隔: {PUSH_INTERVAL}s)")
    except Exception as e:
        print(f"首次推送失败: {e}")

    while True:
        try:
            metrics = get_system_metrics()
            window = get_active_window()
            media = get_media_info()
            payload, changed = build_payload(metrics, window, media, prev_payload)

            if changed:
                supabase.table("device_status").upsert(payload).execute()
                prev_payload = payload

        except Exception as e:
            print(f"[{time.strftime('%H:%M:%S')}] 推送失败: {e}")

        time.sleep(PUSH_INTERVAL)


if __name__ == "__main__":
    main()
