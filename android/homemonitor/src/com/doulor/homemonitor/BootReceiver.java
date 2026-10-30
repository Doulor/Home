package com.doulor.homemonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机 / 应用更新后自动把服务拉起来 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        Logs.init(context.getFilesDir());
        Logs.i("收到 " + action + "，启动监控服务");
        try {
            Intent svc = new Intent(context, MonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
        } catch (Exception e) {
            Logs.e("启动服务失败: " + e);
        }
    }
}
