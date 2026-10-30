package com.doulor.homemonitor;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通知监听服务。三个作用：
 *   1. 让本应用成为「已启用的通知监听器」—— 同进程才有资格读 MediaSession
 *   2. 定时（每 8 秒）刷新一次媒体信息，结果缓存在 latest
 *   3. 收到媒体类通知时立刻刷新，并用通知里的标题/副标题兜底
 *
 * 为什么改成定时刷新：
 *   之前只在收到「媒体通知」时刷新，判据是 category==TRANSPORT 或带
 *   EXTRA_MEDIA_SESSION。但酷狗这类国产播放器两条都不满足，于是从来没有触发过
 *   刷新 —— 网页上就永远看不到歌名。定时主动轮询最可靠。
 *
 * 为什么不在采集循环里直接调 getActiveSessions：
 *   这个 binder 调用在国产 ROM 上会**永久挂住**（实测线程不再返回，采集循环随之
 *   停摆）。所以它只存在于本类的独立守护线程里，并用时间戳守卫 + 在飞数量上限，
 *   挂住了也不会拖累采集循环，也不会被无限堆叠。
 */
public class MediaWatcher extends NotificationListenerService {

    private static volatile String[] latest = new String[]{null, null};

    /** 上次发起刷新的时刻（毫秒）；用作节流，挂死的线程不会永久阻塞后续刷新 */
    private static final AtomicLong lastRefreshAt = new AtomicLong(0);
    /** 正在飞的刷新线程数，防止挂死堆积 */
    private static final AtomicInteger inflight = new AtomicInteger(0);

    private static final long MIN_INTERVAL_MS = 4000L;
    private static final int MAX_INFLIGHT = 3;

    private Timer timer;

    @Override
    public void onCreate() {
        super.onCreate();
        Logs.init(getFilesDir());
        // 每 8 秒主动刷一次：不依赖任何通知，避免国产播放器不按标准发媒体通知
        timer = new Timer("media-poll", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                refreshAsync(getApplicationContext(), false);
            }
        }, 4000L, 8000L);
        Logs.i("媒体监听服务已创建，开始每 8 秒轮询");
    }

    @Override
    public void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        Logs.i("通知监听已连接");
        refreshAsync(getApplicationContext(), true);
    }

    @Override
    public void onListenerDisconnected() {
        Logs.w("通知监听已断开（媒体信息会停在最后一次的值）");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (looksMedia(sbn)) {
            // 先用通知里的文字兜底（不调 binder，马上生效），再异步拉 MediaSession 覆盖
            String[] fromNotif = fromNotification(sbn);
            if (fromNotif[0] != null) latest = fromNotif;
            refreshAsync(getApplicationContext(), true);
        } else {
            refreshAsync(getApplicationContext(), false);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && looksMedia(sbn)) refreshAsync(getApplicationContext(), true);
    }

    /** 采集侧调用：只读缓存，不触发任何 binder 调用 */
    static String[] peek() {
        return latest;
    }

    private static boolean looksMedia(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return true;
        Bundle e = n.extras;
        if (e == null) return false;
        return e.containsKey(Notification.EXTRA_MEDIA_SESSION)
                || e.getCharSequence(Notification.EXTRA_MEDIA_SESSION) != null;
    }

    /** 从通知里取标题/副标题（国产播放器最稳的来源） */
    private static String[] fromNotification(StatusBarNotification sbn) {
        String[] r = new String[]{null, null};
        try {
            Bundle e = sbn.getNotification().extras;
            if (e == null) return r;
            CharSequence title = e.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = e.getCharSequence(Notification.EXTRA_TEXT);
            CharSequence sub = e.getCharSequence(Notification.EXTRA_SUB_TEXT);
            if (title != null && title.length() > 0) r[0] = title.toString();
            CharSequence detail = (sub != null && sub.length() > 0) ? sub : text;
            if (detail != null && detail.length() > 0) r[1] = detail.toString();
        } catch (Throwable ignored) {
        }
        return r;
    }

    /** 异步刷新；节流 + 在飞上限，挂死的线程不会阻塞以后所有刷新 */
    static void refreshAsync(Context ctx, boolean force) {
        if (ctx == null) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastRefreshAt.get() < MIN_INTERVAL_MS) return;
        if (inflight.get() >= MAX_INFLIGHT) return;
        lastRefreshAt.set(now);
        inflight.incrementAndGet();
        final Context app = ctx.getApplicationContext() == null ? ctx : ctx.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] r = read(app);
                    if (r[0] != null) latest = r;
                    else if (inflight.get() <= 1) latest = new String[]{null, null};
                } catch (Throwable ignored) {
                } finally {
                    inflight.decrementAndGet();
                }
            }
        }, "media-read");
        t.setDaemon(true);
        t.start();
    }

    /** 取应用显示名（用于把「酷狗音乐 - 歌名」拼起来）；失败返回 null */
    private static String labelOf(Context ctx, String pkg) {
        if (pkg == null) return null;
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return label == null ? null : label.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String[] read(Context ctx) {
        String[] empty = new String[]{null, null};
        try {
            MediaSessionManager msm =
                    (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return empty;
            List<MediaController> list =
                    msm.getActiveSessions(new ComponentName(ctx, MediaWatcher.class));
            if (list == null) return empty;
            for (MediaController c : list) {
                MediaMetadata md = c.getMetadata();
                if (md == null) continue;
                CharSequence title = md.getText(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.length() == 0) {
                    title = md.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                }
                if (title == null || title.length() == 0) continue;

                CharSequence artist = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null || artist.length() == 0) {
                    artist = md.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }
                if (artist == null || artist.length() == 0) {
                    artist = md.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                }
                if (artist == null || artist.length() == 0) {
                    artist = md.getText(MediaMetadata.METADATA_KEY_ALBUM);
                }

                // 前面补上播放器的应用名，拼成「酷狗音乐 - 歌名」
                String titleText = title.toString();
                String appLabel = labelOf(ctx, c.getPackageName());
                if (appLabel != null && appLabel.length() > 0) {
                    titleText = appLabel + " - " + titleText;
                }
                // 只认「正在播放」的会话：暂停的、播放完的都不算，
                // 否则页面上会一直挂着上一次的歌名（多个播放器同时存在时还会串台）
                android.media.session.PlaybackState st = c.getPlaybackState();
                boolean playing = st != null
                        && st.getState() == android.media.session.PlaybackState.STATE_PLAYING;
                if (playing) {
                    return new String[]{titleText, artist == null ? null : artist.toString()};
                }
            }
            return empty;
        } catch (SecurityException e) {
            Logs.w("读不到媒体信息（通知使用权没给）");
            return empty;
        } catch (Throwable e) {
            Logs.w("读媒体失败: " + e);
            return empty;
        }
    }
}
