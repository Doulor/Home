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
import json

# 确保能导入同目录模块
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from collectors.system import get_system_metrics
from collectors.window import get_active_window
from collectors.media import get_media_info

# 注意：supabase 客户端是**按需**创建的（见 get_supabase），
# 走连接池直连时根本不会碰它 —— 免得代理环境异常时在启动阶段就崩掉。
# from supabase import create_client  <-- 见 get_supabase()

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


def write_heartbeat(last_push_ok, last_error, last_via=""):
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
            f.write(f"last_via={last_via}\n")
            f.write(f"last_error={last_error}\n")
    except Exception:
        pass
# ──────────────────────────────────────────────────────

# ── 写库 ──────────────────────────────────────────────
# 优先走 Postgres 连接池直连（aws-1-ap-southeast-2.pooler.supabase.com:6543）。
#
# 为什么：*.supabase.co（REST API）在国内直连是被阻断的（TCP 能连上，TLS 握手
# 被 RST），只能走代理；而 *.pooler.supabase.com 直连是通的。走连接池之后，
# 电脑端监控就完全不受 Clash 代理模式影响 —— 实测直连可读写。
#
# 连接池写失败时自动退回 REST（走代理那条路），两条路都失败才算失败。
try:
    import pg8000.native
    HAS_PG = True
except ImportError:
    HAS_PG = False

try:
    from config import DB_PASSWORD
except ImportError:
    DB_PASSWORD = os.environ.get("DB_PASSWORD", "")

DB_HOST = "aws-1-ap-southeast-2.pooler.supabase.com"
DB_PORT = 6543  # 事务模式；实测同一密码在 5432 会报 28P01
DB_USER = "postgres.kphhahpdaqdwcbvhvseo"
DB_NAME = "postgres"

UPSERT_SQL = """
INSERT INTO device_status (
    device_id, device_name, device_type, status,
    cpu_usage, memory_usage, memory_total, memory_used,
    battery_level, battery_charging, active_app,
    media_type, media_title, media_detail, media_elapsed, media_duration,
    extra, updated_at
) VALUES (
    :device_id, :device_name, :device_type, :status,
    :cpu_usage, :memory_usage, :memory_total, :memory_used,
    :battery_level, :battery_charging, :active_app,
    :media_type, :media_title, :media_detail, :media_elapsed, :media_duration,
    :extra::jsonb, now()
)
ON CONFLICT (device_id) DO UPDATE SET
    device_name = EXCLUDED.device_name,
    device_type = EXCLUDED.device_type,
    status = EXCLUDED.status,
    cpu_usage = EXCLUDED.cpu_usage,
    memory_usage = EXCLUDED.memory_usage,
    memory_total = EXCLUDED.memory_total,
    memory_used = EXCLUDED.memory_used,
    battery_level = EXCLUDED.battery_level,
    battery_charging = EXCLUDED.battery_charging,
    active_app = EXCLUDED.active_app,
    media_type = EXCLUDED.media_type,
    media_title = EXCLUDED.media_title,
    media_detail = EXCLUDED.media_detail,
    media_elapsed = EXCLUDED.media_elapsed,
    media_duration = EXCLUDED.media_duration,
    extra = EXCLUDED.extra,
    updated_at = now()
"""

# SQL 需要的字段（多余的一律不传，避免 pg8000 报参数不匹配）
SQL_FIELDS = [
    "device_id", "device_name", "device_type", "status",
    "cpu_usage", "memory_usage", "memory_total", "memory_used",
    "battery_level", "battery_charging", "active_app",
    "media_type", "media_title", "media_detail", "media_elapsed", "media_duration",
    "extra",
]

_pg_conn = None
_supabase = None


def get_supabase():
    """REST 兜底通道的客户端，按需创建。

    连接池直连能用时根本不会走到这里，所以代理环境再异常也不影响主流程。
    """
    global _supabase
    if _supabase is None:
        from supabase import create_client

        _supabase = create_client(SUPABASE_URL, SERVICE_ROLE_KEY)
    return _supabase


def push_via_pooler(payload):
    """走 Postgres 连接池直连写库。"""
    global _pg_conn
    row = {k: payload.get(k) for k in SQL_FIELDS}
    row["extra"] = json.dumps(row.get("extra") or {}, ensure_ascii=False)
    if _pg_conn is None:
        _pg_conn = pg8000.native.Connection(
            user=DB_USER, password=DB_PASSWORD, host=DB_HOST, port=DB_PORT,
            database=DB_NAME, timeout=15,
        )
    _pg_conn.run(UPSERT_SQL, **row)


def push(payload):
    """写库。返回实际走的通道名。"""
    global _pg_conn
    if HAS_PG and DB_PASSWORD:
        try:
            push_via_pooler(payload)
            return "pooler"
        except Exception as e:
            _pg_conn = None  # 连接可能已坏，下次重建
            print(f"[{time.strftime('%H:%M:%S')}] 连接池写入失败，改用 REST: {e}")
    get_supabase().table("device_status").upsert(payload).execute()
    return "rest"
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
    # 两条通道至少要有条能走：连接池直连（首选），或 REST（需要代理）
    if not (HAS_PG and DB_PASSWORD) and (not SUPABASE_URL or not SERVICE_ROLE_KEY):
        print("错误: 连接池不可用（缺 pg8000 或 DB_PASSWORD），且没有配置 SUPABASE_URL / SERVICE_ROLE_KEY")
        sys.exit(1)

    prev_payload = None
    last_push_ok = 0
    last_error = ""
    last_via = ""

    print(f"写库通道: {'连接池直连(不走代理)' if (HAS_PG and DB_PASSWORD) else 'REST(需要代理)'}")

    # 启动时强制推送一次（确保 uptime 等字段更新）
    try:
        metrics = get_system_metrics()
        window = get_active_window()
        media = get_media_info()
        payload, _ = build_payload(metrics, window, media, None)
        last_via = push(payload)
        prev_payload = payload
        last_push_ok = time.time()
        print(f"设备状态 Agent 启动 (设备: {DEVICE_ID}, 间隔: {PUSH_INTERVAL}s, 通道: {last_via})")
    except Exception as e:
        last_error = f"{type(e).__name__}: {e}"
        print(f"首次推送失败: {e}")

    write_heartbeat(last_push_ok, last_error, last_via)

    while True:
        try:
            metrics = get_system_metrics()
            window = get_active_window()
            media = get_media_info()
            payload, changed = build_payload(metrics, window, media, prev_payload)

            if changed:
                last_via = push(payload)
                prev_payload = payload
                last_push_ok = time.time()
                last_error = ""

        except Exception as e:
            last_error = f"{type(e).__name__}: {e}"
            print(f"[{time.strftime('%H:%M:%S')}] 推送失败: {e}")

        # 每轮都写心跳：看门狗靠它的修改时间判断这个循环是否还在推进，
        # 而不是靠 CPU 占用（网络不通时 CPU 会掉到接近 0，无法与真卡死区分）
        write_heartbeat(last_push_ok, last_error, last_via)
        time.sleep(PUSH_INTERVAL)


if __name__ == "__main__":
    main()
