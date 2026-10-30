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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 通知监听服务。两个作用：
 *   1. 让本应用成为「已启用的通知监听器」，同进程才有资格读 MediaSession
 *   2. 只在收到媒体类通知时去读一次媒体信息，结果缓存到 latest
 *
 * 为什么不在采集循环里直接调 getActiveSessions：
 *   这个 binder 调用在国产 ROM 上会**永久挂住**（实测：调用后线程状态变 S 不再
 *   返回，整个采集循环随之停摆，表现就是"再也不上报了"）。所以：
 *     - 只放在这里、由事件驱动，不在定时循环里碰
 *     - 用独立线程 + AtomicBoolean 守卫，挂住了也不会拖累别处
 *     - 采集侧只读 latest 这个静态字段，零 binder 调用
 *   它不解析任何通知内容，只看是否媒体通知。
 */
public class MediaWatcher extends NotificationListenerService {

    private static volatile String[] latest = new String[]{null, null};
    private static final AtomicBoolean refreshing = new AtomicBoolean(false);

    @Override
    public void onListenerConnected() {
        Logs.i("通知监听已连接");
        refreshAsync(getApplicationContext());
    }

    @Override
    public void onListenerDisconnected() {
        Logs.w("通知监听已断开");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (!isMedia(sbn)) return;
        refreshAsync(getApplicationContext());
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (isMedia(sbn)) refreshAsync(getApplicationContext());
    }

    private static boolean isMedia(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return true;
        Bundle e = n.extras;
        return e != null && e.containsKey(Notification.EXTRA_MEDIA_SESSION);
    }

    /** 采集侧调用：只读缓存，不触发任何 binder 调用 */
    static String[] peek() {
        return latest;
    }

    /** 异步刷新；已有一次在飞就跳过（挂住的那次不会越堆越多） */
    static void refreshAsync(final Context ctx) {
        if (ctx == null) return;
        if (!refreshing.compareAndSet(false, true)) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] r = read(ctx);
                    latest = r;
                } catch (Throwable ignored) {
                } finally {
                    refreshing.set(false);
                }
            }
        }, "media-read");
        t.setDaemon(true);
        t.start();
    }

    private static String[] read(Context ctx) {
        String[] empty = new String[]{null, null};
        try {
            MediaSessionManager msm =
                    (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return empty;
            List<MediaController> list = msm.getActiveSessions(new ComponentName(ctx, MediaWatcher.class));
            if (list == null) return empty;
            for (MediaController c : list) {
                MediaMetadata md = c.getMetadata();
                if (md == null) continue;
                CharSequence title = md.getText(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.length() == 0) continue;
                CharSequence artist = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
                return new String[]{title.toString(), artist == null ? null : artist.toString()};
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
