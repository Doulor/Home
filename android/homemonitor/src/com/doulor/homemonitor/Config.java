package com.doulor.homemonitor;

import android.content.Context;
import android.provider.Settings;

/** 运行期配置：从 SharedPreferences 读，默认值见 Settings 里的 DEF_* */
class Config {
    String url;
    String key;
    String deviceId;
    String deviceName;

    int heartbeatSeconds = 300;
    int pollSeconds = 3;

    // 要上报哪些内容（界面上的勾选项）
    boolean sendStatus = true;
    boolean sendBattery = true;
    boolean sendCharging = true;
    boolean sendApp = true;
    boolean sendLocation = true;
    boolean sendMedia = true;
    boolean sendDeviceInfo = true;

    /** 是否在最近任务（后台页面）里隐藏；默认开启（划掉卡片会连带停掉上报） */
    boolean hideFromRecents = true;

    /** 这些"前台应用"名不算真正的应用，不上报 */
    private static final String[] IGNORED = {"系统桌面", "桌面", "Launcher", "桌面启动器"};

    static Config load(Context ctx) {
        Config c = new Config();
        c.url = Prefs.str(ctx, Prefs.K_URL, Prefs.DEF_URL).trim();
        c.key = Prefs.str(ctx, Prefs.K_KEY, Prefs.DEF_KEY).trim();
        c.deviceId = Prefs.str(ctx, Prefs.K_ID, Prefs.DEF_ID).trim();
        c.deviceName = Prefs.str(ctx, Prefs.K_NAME, Prefs.DEF_NAME);
        c.heartbeatSeconds = Math.max(30, Prefs.num(ctx, Prefs.K_HB, 300));
        c.pollSeconds = Math.max(1, Prefs.num(ctx, Prefs.K_POLL, 2));

        c.sendStatus = Prefs.on(ctx, Prefs.K_STATUS);
        c.sendBattery = Prefs.on(ctx, Prefs.K_BATTERY);
        c.sendCharging = Prefs.on(ctx, Prefs.K_CHARGING);
        c.sendApp = Prefs.on(ctx, Prefs.K_APP);
        c.sendLocation = Prefs.on(ctx, Prefs.K_LOCATION);
        c.sendMedia = Prefs.on(ctx, Prefs.K_MEDIA);
        c.sendDeviceInfo = Prefs.on(ctx, Prefs.K_DEVINFO);
        c.hideFromRecents = Prefs.on(ctx, Prefs.K_HIDE_RECENTS, true);
        return c;
    }

    boolean isIgnoredApp(String name) {
        if (name == null) return true;
        for (String s : IGNORED) {
            if (name.equals(s)) return true;
        }
        return false;
    }

    /** 设置界面底部显示：各项运行期权限是否就绪 */
    static String permissionSummary(Context ctx) {
        StringBuilder sb = new StringBuilder();

        boolean usageOk = false;
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            usageOk = usm != null && usm.queryEvents(now - 60000, now) != null;
        } catch (Exception ignored) {
        }
        sb.append("使用情况访问：").append(usageOk ? "✅ 已授予" : "❌ 未授予（前台应用取不到）").append('\n');

        String listeners = Settings.Secure.getString(
                ctx.getContentResolver(), "enabled_notification_listeners");
        boolean notifOk = listeners != null && listeners.contains(ctx.getPackageName());
        sb.append("通知使用权：").append(notifOk ? "✅ 已授予" : "❌ 未授予（音乐信息取不到）").append('\n');

        boolean locOk = false;
        try {
            locOk = ctx.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
        }
        sb.append("定位权限：").append(locOk ? "✅ 已授予" : "❌ 未授予（位置取不到）");
        return sb.toString();
    }
}
