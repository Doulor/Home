package com.doulor.homemonitor;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;

/**
 * 只干一件事：让本应用成为「已启用的通知监听器」。
 *
 * 有了这个身份，同一进程才能调用 MediaSessionManager.getActiveSessions()
 * 读到正在播放的音乐标题/歌手 —— 这就是为什么需要「通知使用权」。
 * 它不读取任何通知内容。
 */
public class MediaWatcher extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        Logs.i("通知监听已连接（可以读取媒体信息了）");
    }

    @Override
    public void onListenerDisconnected() {
        Logs.w("通知监听已断开");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 刻意什么都不做：不解析通知内容
    }

    /** 返回 {标题, 歌手}，取不到时元素为 null */
    static String[] current(Context ctx) {
        String[] empty = new String[]{null, null};
        try {
            MediaSessionManager msm =
                    (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return empty;
            ComponentName cn = new ComponentName(ctx, MediaWatcher.class);
            List<MediaController> list = msm.getActiveSessions(cn);
            if (list == null) return empty;
            for (MediaController c : list) {
                MediaMetadata md = c.getMetadata();
                if (md == null) continue;
                CharSequence title = md.getText(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.length() == 0) continue;
                CharSequence artist = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
                return new String[]{title.toString(), artist == null ? null : artist.toString()};
            }
        } catch (SecurityException e) {
            Logs.w("读不到媒体信息（通知使用权没给）: " + e.getMessage());
        } catch (Exception e) {
            Logs.w("读媒体信息失败: " + e);
        }
        return empty;
    }
}
