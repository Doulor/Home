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
DEVICE_NAME = "Desktop  -  TX 6 Pro"
PUSH_INTERVAL = 5  # 秒
# ──────────────────────────────────────────────────────

# ── 心跳 ──────────────────────────────────────────────
# 每轮循环都更新这个文件，看门狗靠它的修改时间和里面的 pid 判断 agent 是否还在推进。
#
# 为什么不用 CPU 占用判断：网络不通时 agent 的 CPU 会掉到接近 0，和"真的卡死"
# 无法区分（实测健康但推不出去时约 0.001，真卡死约 0.0018 CPU秒/秒，区间重叠）。
HEARTBEAT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "agent.heartbeat")


def write_heartbeat(last_push_ok, last_error):
    """把当前状态写进心跳文件；写失败也不影响主循环。"""
    try:
        with open(HEARTBEAT_FILE, "w", encoding="utf-8") as f:
            f.write(f"pid={os.getpid()}\n")
            f.write(f"last_loop={time.strftime('%Y-%m-%d %H:%M:%S')}\n")
            pushed = (
                time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(last_push_ok))
                if last_push_ok
                else "never"
            )
            f.write(f"last_push_ok={pushed}\n")
            f.write(f"last_error={last_error}\n")
    except Exception:
        pass
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
    last_push_ok = 0
    last_error = ""

    # 启动时强制推送一次（确保 uptime 等字段更新）
    try:
        metrics = get_system_metrics()
        window = get_active_window()
        media = get_media_info()
        payload, _ = build_payload(metrics, window, media, None)
        supabase.table("device_status").upsert(payload).execute()
        prev_payload = payload
        last_push_ok = time.time()
        print(f"设备状态 Agent 启动 (设备: {DEVICE_ID}, 间隔: {PUSH_INTERVAL}s)")
    except Exception as e:
        last_error = f"{type(e).__name__}: {e}"
        print(f"首次推送失败: {e}")

    write_heartbeat(last_push_ok, last_error)

    while True:
        try:
            metrics = get_system_metrics()
            window = get_active_window()
            media = get_media_info()
            payload, changed = build_payload(metrics, window, media, prev_payload)

            if changed:
                supabase.table("device_status").upsert(payload).execute()
                prev_payload = payload
                last_push_ok = time.time()
                last_error = ""

        except Exception as e:
            last_error = f"{type(e).__name__}: {e}"
            print(f"[{time.strftime('%H:%M:%S')}] 推送失败: {e}")

        # 每轮都写心跳：看门狗靠它的修改时间判断这个循环是否还在推进，
        # 而不是靠 CPU 占用（网络不通时 CPU 会掉到接近 0，无法与真卡死区分）
        write_heartbeat(last_push_ok, last_error)
        time.sleep(PUSH_INTERVAL)


if __name__ == "__main__":
    main()
