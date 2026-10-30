# 设备监控（homemonitor）

替代 MacroDroid 的专用小工具：把手机状态上报到 `update-device` 云函数。

## 为什么不用 MacroDroid

- 破解版会被 Play Protect / vivo 安全中心反复删除，每次删了重装，宏会从旧备份恢复、权限全部重置
- MacroDroid 取前台应用依赖**无障碍服务**，而这个服务在国产 ROM 上会被反复杀掉
- 这个 App **不需要无障碍** —— 前台应用改用 `UsageStatsManager`（「使用情况访问」授权一次就很稳定）

## 上报什么

界面上可逐项勾选（设置 → 上报内容）：

| 字段 | 来源 | 默认 |
|---|---|---|
| `status` | 亮屏=online / 熄屏=offline | 开 |
| `battery_level` | 电池广播 | 开 |
| `battery_charging` | 电池广播 | 开 |
| `active_app` | UsageStatsManager 取包名再查显示名 | 开 |
| `location` | `getLastKnownLocation` 取坐标，服务端反查成地址 | 开 |
| `media_title` / `media_detail` | MediaSession（需通知使用权） | 开 |
| `device_name` / `device_type` | 设置里填 | 开 |

行为细节：

- **有值才发**：取不到前台应用或定位时，不发送该字段，服务端保留上一次的值（避免页面闪没）
- 系统桌面/启动器不算前台应用
- 状态变化立即上报；无变化时按「心跳间隔」（默认 300 秒）保活
- 开机自启（`BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`），前台服务常驻

## 构建

需要 JDK 17+ 和 Android SDK 的 `build-tools;35.0.0` + `platforms;android-34`：

```powershell
sdkmanager "build-tools;35.0.0" "platforms;android-34"
# 默认从 D:\build\android-sdk 找 SDK，可传 -Sdk 覆盖
powershell -ExecutionPolicy Bypass -File build.ps1
```

不打 Gradle，直接走 `aapt2 → javac → d8 → zipalign → apksigner`。

> 注意：必须用 **build-tools 35+** 的 d8。34.0.0 里的 d8 8.2.2 会在内部类上报
> `NullPointerException: Cannot invoke "String.length()"`，直接崩。

签名密钥 `monitor.keystore`（口令 `monitor123`）不提交到仓库，**请自己备份** ——
丢了之后就无法覆盖安装升级，只能卸载重装。

## 安装与授权

```powershell
# 安装（/data/local/tmp 是必须的，system_server 读不了 /sdcard）
adb push homemonitor.apk /data/local/tmp/
adb shell pm install -r -g /data/local/tmp/homemonitor.apk

# 三项权限 adb 授予
adb shell appops set com.doulor.homemonitor GET_USAGE_STATS allow
adb shell cmd notification allow_listener com.doulor.homemonitor/.MediaWatcher
adb shell pm grant com.doulor.homemonitor android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.doulor.homemonitor android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.doulor.homemonitor android.permission.POST_NOTIFICATIONS
```

`appops set ... GET_USAGE_STATS` 在部分 ROM 上需要重启 App 才生效。

## 排错

```powershell
adb logcat -d | Select-String HomeMonitor          # 运行日志
adb shell dumpsys activity services com.doulor.homemonitor   # 前台服务是否存活
adb shell appops get com.doulor.homemonitor GET_USAGE_STATS  # 授权状态
```

## 源码

| 文件 | 作用 |
|---|---|
| `MonitorService.java` | 常驻前台服务：采集 + 判断变化 + POST |
| `MainActivity.java` | 主界面：显示上报内容和权限状态 |
| `SettingsActivity.java` | 勾选上报项、改地址/密钥/设备名/间隔 |
| `Config.java` | 运行期配置（从 Prefs 读） |
| `Prefs.java` | SharedPreferences 读写（类名不能叫 Settings，会和 `android.provider.Settings` 撞） |
| `MediaWatcher.java` | 通知监听服务，仅为拿到 MediaSession 读取资格，不解析通知内容 |
| `BootReceiver.java` | 开机 / 应用更新后拉起服务 |
