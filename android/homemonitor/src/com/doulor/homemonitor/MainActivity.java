package com.doulor.homemonitor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 主界面：显示运行状态和权限情况，可进入设置 */
public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logs.init(getFilesDir());

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            } catch (Exception ignored) {
            }
        }

        startMonitor();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56, 120, 56, 56);

        TextView title = new TextView(this);
        title.setText("设备监控");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, 40, 0, 0);
        root.addView(status);

        Button settings = new Button(this);
        settings.setText("设置上报内容");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 48;
        root.addView(settings, lp);

        TextView hint = new TextView(this);
        hint.setTextSize(12);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, 56, 0, 0);
        hint.setText("权限用 adb 授予：\n"
                + "  appops set com.doulor.homemonitor GET_USAGE_STATS allow\n"
                + "  cmd notification allow_listener com.doulor.homemonitor/.MediaWatcher\n"
                + "  pm grant com.doulor.homemonitor android.permission.ACCESS_FINE_LOCATION");
        root.addView(hint);

        scroll.addView(root);
        setContentView(scroll);
        applyRecentsSetting();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyRecentsSetting();
        refresh();
    }

    /** 按设置决定本 App 是否出现在最近任务（后台页面）里 */
    private void applyRecentsSetting() {
        try {
            boolean hide = Config.load(this).hideFromRecents;
            android.app.ActivityManager am =
                    (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return;
            for (android.app.ActivityManager.AppTask t : am.getAppTasks()) {
                t.setExcludeFromRecents(hide);
            }
            Logs.i("最近任务隐藏 = " + hide);
        } catch (Throwable e) {
            Logs.w("设置最近任务隐藏失败: " + e);
        }
    }

    private void startMonitor() {
        try {
            Intent svc = new Intent(this, MonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        } catch (Exception e) {
            Logs.e("启动服务失败: " + e);
        }
    }

    private void refresh() {
        if (status == null) return;
        Config c = Config.load(this);
        StringBuilder sb = new StringBuilder();
        sb.append("上报到：").append(c.url).append('\n');
        sb.append("设备：").append(c.deviceName).append(" (").append(c.deviceId).append(")\n\n");
        sb.append("上报内容：");
        sb.append(c.sendStatus ? "在线 " : "");
        sb.append(c.sendBattery ? "电量 " : "");
        sb.append(c.sendCharging ? "充电 " : "");
        sb.append(c.sendApp ? "应用 " : "");
        sb.append(c.sendLocation ? "位置 " : "");
        sb.append(c.sendMedia ? "音乐 " : "");
        sb.append(c.sendDeviceInfo ? "设备名" : "");
        sb.append("\n\n");
        sb.append(Config.permissionSummary(this));
        status.setText(sb.toString());
    }
}
