package com.buswake.app;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppLog {
    private static final Object LOCK = new Object();
    private static final String DIR = "logs";
    private static final String FILE = "app.log";
    private static final String OLD_FILE = "app.log.1";
    private static final long MAX_BYTES = 512L * 1024L;
    private static volatile boolean initialized;
    private static Context appContext;

    private AppLog() {
    }

    public static void init(Context context) {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            File dir = new File(context.getFilesDir(), DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            appContext = context.getApplicationContext();
            initialized = true;
        }
    }

    public static void i(String tag, String message) {
        write("I", tag, message);
    }

    public static void w(String tag, String message) {
        write("W", tag, message);
    }

    public static void e(String tag, String message) {
        write("E", tag, message);
    }

    public static void e(String tag, String message, Throwable error) {
        String detail = error == null ? "" : " | " + error.getClass().getSimpleName()
                + ": " + String.valueOf(error.getMessage());
        write("E", tag, message + detail);
    }

    private static void write(String level, String tag, String message) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        init(context);
        synchronized (LOCK) {
            try {
                File dir = new File(context.getFilesDir(), DIR);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, FILE);
                if (file.length() > MAX_BYTES) {
                    File old = new File(dir, OLD_FILE);
                    if (old.exists()) {
                        old.delete();
                    }
                    file.renameTo(old);
                }
                String time = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                        .format(new Date());
                String line = time + " " + level + "/" + safe(tag) + ": " + safe(message) + "\n";
                try (FileOutputStream out = new FileOutputStream(file, true);
                     OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                    writer.write(line);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    public static String readAll(Context context) {
        return readAll(context, true);
    }

    public static String readAllForExport(Context context) {
        return readAll(context, false);
    }

    private static String readAll(Context context, boolean limit) {
        init(context);
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            File dir = new File(context.getFilesDir(), DIR);
            readFile(new File(dir, OLD_FILE), sb);
            readFile(new File(dir, FILE), sb);
            if (limit && sb.length() > 400_000) {
                return "…（仅显示最近部分日志）\n" + sb.substring(sb.length() - 400_000);
            }
            return sb.toString();
        }
    }

    private static void readFile(File file, StringBuilder sb) {
        if (!file.isFile() || file.length() == 0) {
            return;
        }
        try (FileInputStream in = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        } catch (Exception ignored) {
        }
    }

    public static long totalBytes(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        long total = 0;
        File f = new File(dir, FILE);
        if (f.isFile()) {
            total += f.length();
        }
        File old = new File(dir, OLD_FILE);
        if (old.isFile()) {
            total += old.length();
        }
        return total;
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            File dir = new File(context.getFilesDir(), DIR);
            new File(dir, FILE).delete();
            new File(dir, OLD_FILE).delete();
        }
    }
}
