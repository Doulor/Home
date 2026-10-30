package com.doulor.homemonitor;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 选择上报哪些信息 + 改连接参数 */
public class SettingsActivity extends Activity {

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logs.init(getFilesDir());

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 96);
        scroll.addView(root);
        setContentView(scroll);

        try {
            buildUi();
        } catch (Throwable t) {
            // 任何异常都不让它闪退，直接显示在界面上，方便排查
            Logs.e("设置页构建失败: " + t);
            root.removeAllViews();
            TextView err = new TextView(this);
            err.setText("设置页加载出错（已避免崩溃）：\n\n" + t);
            err.setTextSize(14);
            err.setTextColor(Color.RED);
            err.setPadding(0, 40, 0, 0);
            root.addView(err);
        }
    }

    private void buildUi() {
        header("上报内容");
        note("勾选的项会随每次上报一起发送；取消勾选就不再发这一项。");
        addSwitch("在线状态（亮屏=在线，熄屏=离线）", Prefs.K_STATUS);
        addSwitch("电量百分比", Prefs.K_BATTERY);
        addSwitch("充电状态", Prefs.K_CHARGING);
        addSwitch("前台应用名", Prefs.K_APP);
        addSwitch("位置（坐标由服务端反查成地址）", Prefs.K_LOCATION);
        addSwitch("音乐信息（歌名 / 歌手）", Prefs.K_MEDIA);
        addSwitch("设备名称与类型", Prefs.K_DEVINFO);

        header("界面");
        addSwitch("在最近任务中隐藏（不出现在后台页面）", Prefs.K_HIDE_RECENTS, false);
        note("隐藏后：按 Home 划掉卡片不会停止上报，想关掉请从通知栏进入本 App。");

        header("连接");
        addEdit("上报地址", Prefs.K_URL, Prefs.DEF_URL, InputType.TYPE_TEXT_VARIATION_URI);
        addEdit("密钥（Bearer）", Prefs.K_KEY, Prefs.DEF_KEY, InputType.TYPE_CLASS_TEXT);
        addEdit("设备 ID", Prefs.K_ID, Prefs.DEF_ID, InputType.TYPE_CLASS_TEXT);
        addEdit("设备名称", Prefs.K_NAME, Prefs.DEF_NAME, InputType.TYPE_CLASS_TEXT);
        addEdit("心跳间隔（秒）", Prefs.K_HB, "300", InputType.TYPE_CLASS_NUMBER);
        addEdit("轮询间隔（秒）", Prefs.K_POLL, "2", InputType.TYPE_CLASS_NUMBER);

        header("当前权限");
        TextView perm = new TextView(this);
        perm.setText(Config.permissionSummary(this));
        perm.setTextSize(13);
        perm.setTextColor(Color.DKGRAY);
        perm.setPadding(0, 12, 0, 24);
        root.addView(perm);

        Button save = new Button(this);
        save.setText("保存");
        save.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                saveAll();
            }
        });
        root.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void header(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(19);
        tv.setTextColor(Color.BLACK);
        tv.setPadding(0, 36, 0, 8);
        root.addView(tv);
    }

    private void note(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.GRAY);
        tv.setPadding(0, 0, 0, 12);
        root.addView(tv);
    }

    private void addSwitch(String label, String key) {
        addSwitch(label, key, true);
    }

    private void addSwitch(String label, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(15);
        sw.setChecked(Prefs.on(this, key, def));
        sw.setTag(key);
        sw.setPadding(0, 18, 0, 18);
        root.addView(sw);
    }

    private void addEdit(String label, String key, String def, int inputType) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(Color.GRAY);
        tv.setPadding(0, 18, 0, 4);
        root.addView(tv);

        EditText et = new EditText(this);
        et.setText(Prefs.str(this, key, def));
        et.setTextSize(14);
        et.setInputType(inputType);
        et.setTag("edit:" + key);
        root.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void saveAll() {
        android.content.SharedPreferences.Editor e = Prefs.p(this).edit();
        for (int i = 0; i < root.getChildCount(); i++) {
            Object tag = root.getChildAt(i).getTag();
            if (tag == null) continue;
            String t = tag.toString();
            if (root.getChildAt(i) instanceof Switch) {
                e.putBoolean(t, ((Switch) root.getChildAt(i)).isChecked());
            } else if (t.startsWith("edit:") && root.getChildAt(i) instanceof EditText) {
                String key = t.substring(5);
                String val = ((EditText) root.getChildAt(i)).getText().toString().trim();
                if (key.equals(Prefs.K_HB) || key.equals(Prefs.K_POLL)) {
                    int n;
                    try {
                        n = Integer.parseInt(val);
                    } catch (Exception ex) {
                        n = key.equals(Prefs.K_HB) ? 300 : 3;
                    }
                    e.putInt(key, n);
                } else if (val.length() > 0) {
                    e.putString(key, val);
                }
            }
        }
        e.apply();
        Logs.i("设置已保存");
        Toast.makeText(this, "已保存，下一轮上报即生效", Toast.LENGTH_SHORT).show();
        finish();
    }
}
