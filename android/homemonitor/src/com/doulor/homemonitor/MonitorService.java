package com.doulor.homemonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 常驻服务：把手机状态 POST 到 update-device 云函数。
 *
 * 为什么用 UsageStatsManager 而不是无障碍服务取前台应用：
 * 无障碍服务在各家 ROM 上会被反复杀掉（之前 MacroDroid 就是这么失效的），
 * 而「使用情况访问」授权一次之后很稳定，不需要常驻的无障碍连接。
 */
public class MonitorService extends Service {

    private static final String CHANNEL_ID = "monitor";
    private static final int NOTIF_ID = 1001;

    private Handler handler;
    private Config cfg;

    // 上一轮的状态快照，用来判断"有没有变化"
    private boolean lastScreenOn = true;
    private boolean lastCharging = false;
    private int lastBattery = -1;
    private String lastApp;
    private String lastTitle;
    private String lastArtist;
    private long lastPushOkAt = 0;
    private boolean pendingRetry = false;

    private Runnable ticker;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 屏幕亮灭 / 插拔电源都立即上报，不用等轮询
            Logs.i("收到广播: " + intent.getAction());
            if (ticker != null) handler.removeCallbacks(ticker);
            scheduleTick(500);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Logs.init(getFilesDir());
        handler = new Handler(Looper.getMainLooper());
        cfg = Config.load(this);
        Logs.i("服务启动，目标地址: " + cfg.url);

        createChannel();
        startForeground(NOTIF_ID, buildNotification("运行中"));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(powerReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(powerReceiver, f);
            }
        } catch (Exception e) {
            Logs.e("注册广播失败: " + e);
        }

        ticker = new Runnable() {
            @Override
            public void run() {
                tick();
            }
        };
        scheduleTick(1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 被系统杀掉后自动重启
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Logs.w("服务被销毁，尝试重启");
        if (handler != null && ticker != null) handler.removeCallbacks(ticker);
        try {
            unregisterReceiver(powerReceiver);
        } catch (Exception ignored) {
        }
        // 尽量自愈
        try {
            Intent restart = new Intent(this, MonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(restart);
            else startService(restart);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void scheduleTick(int delayMs) {
        if (handler != null && ticker != null) handler.postDelayed(ticker, delayMs);
    }

    // ─────────────────────────── 主循环 ───────────────────────────

    private void tick() {
        int nextDelay;
        try {
            cfg = Config.load(this); // 每轮重读配置，adb push 新 config.json 后无需重启

            boolean screenOn = isScreenOn();
            int battery = batteryPercent();
            boolean charging = isCharging();
            String app = foregroundAppLabel();
            String[] media = MediaWatcher.current(this);
            String location = lastKnownLocation();

            boolean changed =
                    screenOn != lastScreenOn
                            || charging != lastCharging
                            || battery != lastBattery
                            || !eq(app, lastApp)
                            || !eq(media[0], lastTitle)
                            || !eq(media[1], lastArtist);

            boolean heartbeat = System.currentTimeMillis() - lastPushOkAt >= cfg.heartbeatSeconds * 1000L;

            if (changed || heartbeat || pendingRetry) {
                JSONObject body = new JSONObject();
                body.put("device_id", cfg.deviceId);
                if (cfg.sendDeviceInfo) {
                    body.put("device_name", cfg.deviceName);
                    body.put("device_type", "phone");
                }
                if (cfg.sendStatus) {
                    body.put("status", screenOn ? "online" : "offline");
                }
                if (cfg.sendBattery && battery >= 0) {
                    body.put("battery_level", battery);
                }
                if (cfg.sendCharging) {
                    body.put("battery_charging", charging);
                }
                if (cfg.sendApp && app != null) {
                    // 只在有真实前台应用时才发；取不到就保留服务端上一次的值，避免闪没
                    body.put("active_app", app);
                }
                if (cfg.sendLocation && location != null) {
                    // 同理：拿不到定位就保留上次的地址，别把已有地址抹掉
                    body.put("location", location);
                }
                if (cfg.sendMedia) {
                    if (media[0] != null) body.put("media_title", media[0]);
                    if (media[1] != null) body.put("media_detail", media[1]);
                }

                final String reason = pendingRetry ? "重试"
                        : changed ? "状态变化" : "心跳";
                final JSONObject payload = body;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean ok = post(payload);
                        if (ok) {
                            lastPushOkAt = System.currentTimeMillis();
                            pendingRetry = false;
                        } else {
                            pendingRetry = true;
                        }
                    }
                }, "push").start();

                Logs.i("上报(" + reason + "): app=" + app + " screen=" + (screenOn ? "on" : "off")
                        + " batt=" + battery + (charging ? " 充电中" : "") + " media=" + media[0]);

                lastScreenOn = screenOn;
                lastCharging = charging;
                lastBattery = battery;
                lastApp = app;
                lastTitle = media[0];
                lastArtist = media[1];
            }

            nextDelay = Math.max(2000, cfg.pollSeconds * 1000);
            updateNotification(app == null ? "运行中" : app);
        } catch (Throwable t) {
            Logs.e("tick 异常: " + t);
            nextDelay = 5000;
        }
        scheduleTick(nextDelay);
    }

    // ─────────────────────────── 状态采集 ───────────────────────────

    private boolean isScreenOn() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Exception e) {
            return true;
        }
    }

    private int batteryPercent() {
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return -1;
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level < 0 || scale <= 0) return -1;
            return Math.round(level * 100f / scale);
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean isCharging() {
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return false;
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Exception e) {
            return false;
        }
    }

    /** 取"最后一个切到前台的可见应用"的中文名；取不到返回 null（此时不上报该字段） */
    private String foregroundAppLabel() {
        try {
            UsageStatsManager usm =
                    (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 60000L, now);
            if (events == null) return null;

            UsageEvents.Event e = new UsageEvents.Event();
            String pkg = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                int type = e.getEventType();
                // MOVE_TO_FOREGROUND = 1，API 29 起同值也叫 ACTIVITY_RESUMED
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND || type == 1) {
                    pkg = e.getPackageName();
                }
            }
            if (pkg == null || pkg.equals(getPackageName())) return null;

            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = getPackageManager().getApplicationLabel(ai);
            String name = label == null ? null : label.toString();
            if (cfg != null && cfg.isIgnoredApp(name)) return null;
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    private String lastKnownLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            String[] providers = new String[]{
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
            };
            for (String p : providers) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception ignored) {
                }
            }
            if (best == null) return null;
            return best.getLatitude() + "," + best.getLongitude();
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────── 上报 ───────────────────────────

    private boolean post(JSONObject body) {
        HttpURLConnection conn = null;
        try {
            byte[] data = body.toString().getBytes("UTF-8");
            conn = (HttpURLConnection) new URL(cfg.url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(data.length);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + cfg.key);
            OutputStream os = conn.getOutputStream();
            os.write(data);
            os.close();
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) return true;
            Logs.w("上报失败 HTTP " + code);
            return false;
        } catch (Exception e) {
            Logs.w("上报异常: " + e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ─────────────────────────── 通知 ───────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "设备监控", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("设备监控")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true);
        return b.build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
        } catch (Exception ignored) {
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
