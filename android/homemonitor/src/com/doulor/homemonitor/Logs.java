package com.doulor.homemonitor;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 日志：同时写 logcat 和应用私有目录下的 monitor.log（方便 adb 抓） */
class Logs {
    private static final String TAG = "HomeMonitor";
    private static final long MAX_BYTES = 256 * 1024;
    private static File logFile;
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    static void init(File dir) {
        if (dir != null) logFile = new File(dir, "monitor.log");
    }

    static void i(String msg) { write("I", msg); }
    static void w(String msg) { write("W", msg); }
    static void e(String msg) { write("E", msg); }

    private static void write(String level, String msg) {
        String line = FMT.format(new Date()) + " " + level + " " + msg;
        if ("E".equals(level)) Log.e(TAG, msg);
        else if ("W".equals(level)) Log.w(TAG, msg);
        else Log.i(TAG, msg);

        if (logFile == null) return;
        try {
            if (logFile.length() > MAX_BYTES) {
                File old = new File(logFile.getParentFile(), "monitor.log.1");
                if (old.exists()) old.delete();
                logFile.renameTo(old);
            }
            FileWriter w = new FileWriter(logFile, true);
            w.write(line + "\n");
            w.close();
        } catch (Exception ignored) {
        }
    }
}
