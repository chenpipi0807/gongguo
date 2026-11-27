package com.nx.adcleaner;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 诊断日志：JSONL 一行一条，写在应用私有目录，可一键导出到「下载」。
 * 记录内容包括：心跳（当前界面文字）、广告角标、命中动作、界面快照。
 */
public final class LogStore {

    private static final String NAME = "events.jsonl";
    private static final long MAX_BYTES = 1024 * 1024;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault());

    private LogStore() {
    }

    public static File file(Context context) {
        return new File(context.getFilesDir(), NAME);
    }

    public static void log(Context context, String kind, String detail) {
        String line = "{\"t\":" + System.currentTimeMillis()
                + ",\"k\":\"" + esc(kind) + "\""
                + ",\"d\":\"" + esc(detail) + "\"}";
        synchronized (LOCK) {
            try {
                File f = file(context);
                if (f.length() > MAX_BYTES) {
                    // 超了就直接重开一份，避免无限增长
                    FileWriter reset = new FileWriter(f, false);
                    reset.close();
                }
                FileWriter writer = new FileWriter(f, true);
                writer.write(line);
                writer.write("\n");
                writer.close();
            } catch (Exception ignored) {
                // 日志写不进去不影响主流程
            }
        }
    }

    public static String readAll(Context context) {
        synchronized (LOCK) {
            try {
                File f = file(context);
                if (!f.exists()) {
                    return "";
                }
                FileInputStream in = new FileInputStream(f);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                in.close();
                return out.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            File f = file(context);
            if (f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** 导出成 txt 到「下载」，返回给用户看的位置描述。 */
    public static String export(Context context) throws Exception {
        String content = readAll(context);
        String name = "adcleaner-log-" + STAMP.format(new Date()) + ".txt";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("无法在下载目录创建文件");
            }
            OutputStream out = context.getContentResolver().openOutputStream(uri);
            if (out == null) {
                throw new IllegalStateException("无法写入下载目录");
            }
            out.write(content.getBytes("UTF-8"));
            out.close();
            return "下载/" + name;
        }
        File dir = context.getExternalFilesDir(null);
        File out = new File(dir == null ? context.getFilesDir() : dir, name);
        FileOutputStream stream = new FileOutputStream(out);
        stream.write(content.getBytes("UTF-8"));
        stream.close();
        return out.getAbsolutePath();
    }

    public static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
