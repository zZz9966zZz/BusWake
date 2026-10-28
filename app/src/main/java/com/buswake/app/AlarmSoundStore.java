package com.buswake.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class AlarmSoundStore {
    private static final String PREFS = "alarm_sound";
    private static final String KEY_NAME = "display_name";
    private static final String FILE_NAME = "alarm_sound_audio";
    private static final long MAX_BYTES = 30L * 1024 * 1024;

    private AlarmSoundStore() {
    }

    public static File getCustomFile(Context c) {
        File f = new File(c.getFilesDir(), FILE_NAME);
        return f.isFile() && f.length() > 0 ? f : null;
    }

    public static boolean hasCustom(Context c) {
        return getCustomFile(c) != null;
    }

    public static String getDisplayName(Context c) {
        if (!hasCustom(c)) {
            return "系统默认闹钟";
        }
        String name = prefs(c).getString(KEY_NAME, "");
        return name == null || name.trim().isEmpty() ? "自定义闹钟音乐" : name;
    }

    public static boolean importFromUri(Context c, Uri uri, String displayName) {
        if (uri == null) {
            return false;
        }
        File target = new File(c.getFilesDir(), FILE_NAME);
        File tmp = new File(c.getFilesDir(), FILE_NAME + ".tmp");
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new IllegalArgumentException("audio too large");
                }
                out.write(buffer, 0, n);
            }
            out.flush();
            if (target.exists() && !target.delete()) {
                return false;
            }
            if (!tmp.renameTo(target)) {
                return false;
            }
            prefs(c).edit().putString(KEY_NAME,
                    displayName == null || displayName.trim().isEmpty()
                            ? "自定义闹钟音乐" : displayName.trim()).apply();
            return true;
        } catch (Exception e) {
            if (tmp.exists()) {
                tmp.delete();
            }
            return false;
        }
    }

    public static void clear(Context c) {
        File f = new File(c.getFilesDir(), FILE_NAME);
        if (f.exists()) {
            f.delete();
        }
        File tmp = new File(c.getFilesDir(), FILE_NAME + ".tmp");
        if (tmp.exists()) {
            tmp.delete();
        }
        prefs(c).edit().remove(KEY_NAME).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
