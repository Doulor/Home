import psutil
import time


def get_system_metrics():
    """采集 CPU、内存、电池信息"""
    battery = psutil.sensors_battery()
    mem = psutil.virtual_memory()
    uptime = int(time.time() - psutil.boot_time())

    return {
        "cpu": psutil.cpu_percent(interval=1),
        "memory_percent": mem.percent,
        "memory_total_mb": round(mem.total / (1024 ** 2)),
        "memory_used_mb": round(mem.used / (1024 ** 2)),
        "battery": battery.percent if battery else None,
        "battery_charging": battery.power_plugged if battery else False,
        "uptime": uptime,
    }
