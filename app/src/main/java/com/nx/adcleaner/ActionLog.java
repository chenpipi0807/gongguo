package com.nx.adcleaner;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** 内存中的命中日志，界面与服务同进程共享。最多保留 80 条。 */
public final class ActionLog {

    public interface Listener {
        void onChanged();
    }

    private static final int MAX = 80;
    private static final List<String> ITEMS = new ArrayList<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private ActionLog() {
    }

    public static void add(String message) {
        String line;
        synchronized (ITEMS) {
            line = FMT.format(new Date()) + "  " + message;
            ITEMS.add(0, line);
            while (ITEMS.size() > MAX) {
                ITEMS.remove(ITEMS.size() - 1);
            }
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.onChanged();
            } catch (RuntimeException ignored) {
                // 界面已销毁时忽略
            }
        }
    }

    public static List<String> snapshot() {
        synchronized (ITEMS) {
            return new ArrayList<>(ITEMS);
        }
    }

    public static void clear() {
        synchronized (ITEMS) {
            ITEMS.clear();
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.onChanged();
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
