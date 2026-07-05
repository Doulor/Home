import psutil
import subprocess
import time


def _get_real_boot_time():
    """通过 Windows 事件日志获取真实的系统启动时间"""
    try:
        # Event ID 12 = Kernel-General（系统启动事件）
        result = subprocess.run(
            ['powershell', '-NoProfile', '-NonInteractive', '-Command',
             "Get-WinEvent -FilterHashtable @{LogName='System'; Id=12} -MaxEvents 1 | Select-Object -ExpandProperty TimeCreated"],
            capture_output=True, text=True, timeout=10, creationflags=0x08000000
        )
        if result.returncode == 0 and result.stdout.strip():
            # 格式: "2026年7月5日 10:47:56" 或 "7/5/2026 10:47:56 AM"
            from datetime import datetime
            raw = result.stdout.strip()
            # 尝试中文格式
            for fmt in ("%Y年%m月%d日 %H:%M:%S", "%m/%d/%Y %I:%M:%S %p", "%Y/%m/%d %H:%M:%S"):
                try:
                    dt = datetime.strptime(raw, fmt)
                    return dt.timestamp()
                except ValueError:
                    continue
    except Exception:
        pass

    # 降级到 psutil
    return psutil.boot_time()


def get_system_metrics():
    """采集 CPU、内存、电池信息"""
    battery = psutil.sensors_battery()
    mem = psutil.virtual_memory()
    boot_time = _get_real_boot_time()
    uptime = int(time.time() - boot_time)

    return {
        "cpu": psutil.cpu_percent(interval=1),
        "memory_percent": mem.percent,
        "memory_total_mb": round(mem.total / (1024 ** 2)),
        "memory_used_mb": round(mem.used / (1024 ** 2)),
        "battery": battery.percent if battery else None,
        "battery_charging": battery.power_plugged if battery else False,
        "uptime": uptime,
    }
