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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 常驻服务：把手机状态 POST 到 update-device 云函数。
 *
 * 关键设计（都是为了让"切应用 → 网页更新"稳定在 5 秒内）：
 *
 * 1. 采集全部跑在独立的 HandlerThread 上，不占用主线程。
 *    getActiveSessions / getLastKnownLocation 都是 binder 跨进程调用，
 *    国产 ROM 上偶尔几百毫秒甚至更久，放主线程会把下一轮定时一起拖后。
 *
 * 2. 上报加并发锁（pushing）。失败重试时如果已有请求在飞就跳过本轮，
 *    否则每 3 秒起一个新线程，网络一慢就越堆越多互相抢带宽。
 *
 * 3. 定位 / 媒体 / 应用名都做短时缓存，避免每轮都去跨进程查。
 *    纯轮询那一轮的开销降到几毫秒，轮询间隔才敢压到 2 秒。
 */
public class MonitorService extends Service {

    private static final String CHANNEL_ID = "monitor";
    private static final int NOTIF_ID = 1001;

    /** 定位缓存 30 秒（定位不会那么快变，没必要每轮都查） */
    private static final long LOCATION_TTL_MS = 30_000L;
    /** 媒体会话缓存 5 秒 */
    private static final long MEDIA_TTL_MS = 5_000L;

    private HandlerThread bgThread;
    private Handler bg;
    private Config cfg;
    private Runnable ticker;

    private final AtomicBoolean pushing = new AtomicBoolean(false);
    private volatile long lastPushOkAt = 0;
    private volatile boolean pendingRetry = false;

    // 上一轮快照
    private boolean lastScreenOn = true;
    private boolean lastCharging = false;
    private int lastBattery = -1;
    private String lastApp;
    private String lastTitle;
    private String lastArtist;

    // 缓存
    private long lastLocationAt = 0;
    private String cachedLocation;
    private long lastMediaAt = 0;
    private String[] cachedMedia = new String[]{null, null};
    private final Map<String, String> labelCache = new HashMap<>();

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logs.i("广播: " + intent.getAction());
            // 亮灭屏 / 插拔电源立刻触发一轮（顺手把媒体缓存作废）
            lastMediaAt = 0;
            scheduleTick(300, true);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Logs.init(getFilesDir());
        cfg = Config.load(this);
        Logs.i("服务启动: " + cfg.url);

        bgThread = new HandlerThread("monitor");
        bgThread.start();
        bg = new Handler(bgThread.getLooper());

        createChannel();
        startForeground(NOTIF_ID, buildNotification("运行中"));
        registerReceivers();

        ticker = new Runnable() {
            @Override
            public void run() {
                tick();
            }
        };
        scheduleTick(800, false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Logs.w("服务被销毁，尝试重启");
        if (bg != null && ticker != null) bg.removeCallbacks(ticker);
        if (bgThread != null) bgThread.quitSafely();
        try {
            unregisterReceiver(powerReceiver);
        } catch (Exception ignored) {
        }
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

    private void scheduleTick(int delayMs, boolean replacePending) {
        if (bg == null || ticker == null) return;
        if (replacePending) bg.removeCallbacks(ticker);
        bg.postDelayed(ticker, Math.max(0, delayMs));
    }

    // ─────────────────────────── 主循环 ───────────────────────────

    private void tick() {
        long t0 = SystemClock.elapsedRealtime();
        int pollMs = 2000;
        try {
            cfg = Config.load(this);
            pollMs = Math.max(1000, cfg.pollSeconds * 1000);

            boolean screenOn = isScreenOn();
            int battery = batteryPercent();
            boolean charging = isCharging();
            String app = foregroundAppLabel();
            String[] media = mediaCached();
            String location = locationCached();

            boolean changed =
                    screenOn != lastScreenOn
                            || charging != lastCharging
                            || battery != lastBattery
                            || !eq(app, lastApp)
                            || !eq(media[0], lastTitle)
                            || !eq(media[1], lastArtist);
            boolean heartbeat = System.currentTimeMillis() - lastPushOkAt >= cfg.heartbeatSeconds * 1000L;

            if (changed || heartbeat || pendingRetry) {
                if (pushing.compareAndSet(false, true)) {
                    JSONObject body = buildBody(screenOn, battery, charging, app, media, location);
                    String reason = pendingRetry ? "重试" : changed ? "变化" : "心跳";
                    Logs.i("上报(" + reason + "): app=" + app + " screen=" + (screenOn ? "on" : "off")
                            + " batt=" + battery + (charging ? " 充电" : "") + " loc=" + (location != null));
                    pushAsync(body);
                } else {
                    Logs.w("上一轮上报还在进行，本轮跳过");
                }
                lastScreenOn = screenOn;
                lastCharging = charging;
                lastBattery = battery;
                lastApp = app;
                lastTitle = media[0];
                lastArtist = media[1];
            }

            updateNotification(app == null ? "运行中" : app);
        } catch (Throwable t) {
            Logs.e("tick 异常: " + t);
            pollMs = 5000;
        }

        long cost = SystemClock.elapsedRealtime() - t0;
        if (cost > 300) Logs.w("tick 耗时 " + cost + "ms（偏慢）");
        // 扣掉本轮自身耗时，保证节奏稳定
        scheduleTick((int) Math.max(500L, pollMs - cost), false);
    }

    private JSONObject buildBody(boolean screenOn, int battery, boolean charging,
                                 String app, String[] media, String location) throws Exception {
        JSONObject body = new JSONObject();
        body.put("device_id", cfg.deviceId);
        if (cfg.sendDeviceInfo) {
            body.put("device_name", cfg.deviceName);
            body.put("device_type", "phone");
        }
        if (cfg.sendStatus) body.put("status", screenOn ? "online" : "offline");
        if (cfg.sendBattery && battery >= 0) body.put("battery_level", battery);
        if (cfg.sendCharging) body.put("battery_charging", charging);
        // 有值才发：取不到就保留服务端上一次的值，避免页面闪没
        if (cfg.sendApp && app != null) body.put("active_app", app);
        if (cfg.sendLocation && location != null) body.put("location", location);
        if (cfg.sendMedia) {
            if (media[0] != null) body.put("media_title", media[0]);
            if (media[1] != null) body.put("media_detail", media[1]);
        }
        return body;
    }

    // ─────────────────────────── 采集（都在 bg 线程）───────────────────────────

    private boolean isScreenOn() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Exception e) {
            return true;
        }
    }

    private Intent batteryIntent() {
        try {
            return registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception e) {
            return null;
        }
    }

    private int batteryPercent() {
        try {
            Intent b = batteryIntent();
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
            Intent b = batteryIntent();
            if (b == null) return false;
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Exception e) {
            return false;
        }
    }

    /** 最后一个切到前台的可见应用的中文名；取不到返回 null */
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
                // MOVE_TO_FOREGROUND = 1（API 29 起同值也叫 ACTIVITY_RESUMED）
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND || type == 1) {
                    pkg = e.getPackageName();
                }
            }
            if (pkg == null || pkg.equals(getPackageName())) return null;

            String cached = labelCache.get(pkg);
            if (cached != null) {
                return cfg.isIgnoredApp(cached) ? null : (cached.isEmpty() ? null : cached);
            }
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = getPackageManager().getApplicationLabel(ai);
            String name = label == null ? "" : label.toString();
            labelCache.put(pkg, name);
            if (name.isEmpty() || cfg.isIgnoredApp(name)) return null;
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    private String locationCached() {
        long now = System.currentTimeMillis();
        if (now - lastLocationAt < LOCATION_TTL_MS) return cachedLocation;
        lastLocationAt = now;
        cachedLocation = readLocation();
        return cachedLocation;
    }

    private String readLocation() {
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

    private String[] mediaCached() {
        long now = System.currentTimeMillis();
        if (now - lastMediaAt < MEDIA_TTL_MS) return cachedMedia;
        lastMediaAt = now;
        cachedMedia = MediaWatcher.current(this);
        return cachedMedia;
    }

    // ─────────────────────────── 上报 ───────────────────────────

    private void pushAsync(final JSONObject body) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (post(body)) {
                        lastPushOkAt = System.currentTimeMillis();
                        pendingRetry = false;
                    } else {
                        pendingRetry = true;
                    }
                } finally {
                    pushing.set(false);
                }
            }
        }, "push").start();
    }

    private boolean post(JSONObject body) {
        HttpURLConnection conn = null;
        long t0 = SystemClock.elapsedRealtime();
        try {
            byte[] data = body.toString().getBytes("UTF-8");
            conn = (HttpURLConnection) new URL(cfg.url).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(data.length);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + cfg.key);
            OutputStream os = conn.getOutputStream();
            os.write(data);
            os.close();
            int code = conn.getResponseCode();
            long cost = SystemClock.elapsedRealtime() - t0;
            if (code >= 200 && code < 300) {
                if (cost > 1500) Logs.w("上报偏慢 " + cost + "ms");
                return true;
            }
            Logs.w("上报失败 HTTP " + code + "（" + cost + "ms）");
            return false;
        } catch (Exception e) {
            Logs.w("上报异常: " + e + "（" + (SystemClock.elapsedRealtime() - t0) + "ms）");
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

    private void registerReceivers() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(powerReceiver, f);
        } catch (Exception e) {
            Logs.e("注册广播失败: " + e);
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
