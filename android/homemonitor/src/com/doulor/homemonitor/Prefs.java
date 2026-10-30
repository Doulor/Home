package com.doulor.homemonitor;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 所有可配置项的读写（SharedPreferences）。
 * key 和设置界面一一对应。
 *
 * 注意：类名不能叫 Settings —— 会和 android.provider.Settings 撞名。
 */
class Prefs {
    static final String P = "monitor";

    static final String DEF_URL = "https://supabaseproxy.doulor.cn/functions/v1/update-device";
    static final String DEF_KEY = "sb_publishable_anG3gK_wUEEE6TtYEoLV8w_ts-yhCIU";
    static final String DEF_ID = "android-phone";
    static final String DEF_NAME = "Android  -  IQOO Neo 10";

    // ── 上报内容开关 ──
    static final String K_STATUS = "send_status";
    static final String K_BATTERY = "send_battery";
    static final String K_CHARGING = "send_charging";
    static final String K_APP = "send_app";
    static final String K_LOCATION = "send_location";
    static final String K_MEDIA = "send_media";
    static final String K_DEVINFO = "send_devinfo";

    // ── 界面 ──
    /** 在最近任务（后台页面）里隐藏，默认关闭 */
    static final String K_HIDE_RECENTS = "hide_recents";

    // ── 连接与设备 ──
    static final String K_URL = "url";
    static final String K_KEY = "key";
    static final String K_ID = "device_id";
    static final String K_NAME = "device_name";
    static final String K_HB = "heartbeat_sec";
    static final String K_POLL = "poll_sec";

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    /**
     * 读字符串。用 getAll() 取值而不是 getString()：
     * 心跳/轮询间隔是以 int 存的，直接 getString() 会抛
     * ClassCastException（Integer cannot be cast to String）导致设置页闪退。
     */
    static String str(Context c, String k, String def) {
        Object o;
        try {
            o = p(c).getAll().get(k);
        } catch (Throwable t) {
            return def;
        }
        return o == null ? def : String.valueOf(o);
    }

    /** 读整数，兼容被存成 String 的情况 */
    static int num(Context c, String k, int def) {
        Object o;
        try {
            o = p(c).getAll().get(k);
        } catch (Throwable t) {
            return def;
        }
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof Long) return ((Long) o).intValue();
        if (o instanceof String) {
            try {
                return Integer.parseInt(((String) o).trim());
            } catch (Throwable ignored) {
                return def;
            }
        }
        return def;
    }

    static boolean on(Context c, String k) {
        return p(c).getBoolean(k, true);
    }

    static boolean on(Context c, String k, boolean def) {
        return p(c).getBoolean(k, def);
    }
}
