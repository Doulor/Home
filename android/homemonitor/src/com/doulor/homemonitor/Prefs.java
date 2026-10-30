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

    static String str(Context c, String k, String def) {
        String v = p(c).getString(k, def);
        return v == null ? def : v;
    }

    static int num(Context c, String k, int def) {
        return p(c).getInt(k, def);
    }

    static boolean on(Context c, String k) {
        return p(c).getBoolean(k, true);
    }

    static boolean on(Context c, String k, boolean def) {
        return p(c).getBoolean(k, def);
    }
}
