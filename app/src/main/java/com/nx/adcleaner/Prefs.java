package com.nx.adcleaner;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** 轻量配置存储（SharedPreferences 包装）。 */
public final class Prefs {

    private static final String FILE = "adcleaner";
    private static final String K_ENABLED = "enabled";
    private static final String K_SKIP = "skip_splash";
    private static final String K_CLOSE = "close_popup";
    private static final String K_SWIPE = "swipe_up";
    private static final String K_LIVE = "live_ad";
    private static final String K_BADGE = "require_ad_badge";
    private static final String K_ALL_APPS = "all_apps";
    private static final String K_KEYWORDS = "keywords";
    private static final String K_TARGETS = "targets";
    private static final String K_VERSION = "prefs_version";
    private static final int CURRENT_VERSION = 5;

    private static volatile Prefs instance;

    private final SharedPreferences sp;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        migrate();
    }

    /**
     * 老版本遗留的默认值修正：早期把「中插上滑」默认打开，升级后用户会被跳集，
     * 这里一次性关掉；同时把界面里会误伤系统设置的关键词清空。
     */
    private void migrate() {
        int version = sp.getInt(K_VERSION, 0);
        if (version >= CURRENT_VERSION) {
            return;
        }
        SharedPreferences.Editor editor = sp.edit();
        if (version < 2) {
            editor.putBoolean(K_SWIPE, false);
        }
        if (version < 3) {
            // 早期版本默认要求「广告」角标，角标不是文字时会完全不动手
            editor.putBoolean(K_BADGE, false);
        }
        if (version < 4) {
            // 实测：all_apps + 关键词「广告」会在系统设置里乱点，上滑又会跳集，恢复安全值
            editor.putBoolean(K_ALL_APPS, false);
            editor.putBoolean(K_SWIPE, false);
            editor.putString(K_KEYWORDS, "");
        }
        if (version < 5) {
            // 目标应用改为可配置，默认空列表（在界面里添加）
            editor.putBoolean(K_ALL_APPS, false);
        }
        editor.putInt(K_VERSION, CURRENT_VERSION);
        editor.apply();
    }

    public static Prefs get(Context context) {
        Prefs local = instance;
        if (local == null) {
            synchronized (Prefs.class) {
                local = instance;
                if (local == null) {
                    local = new Prefs(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public boolean enabled() {
        return sp.getBoolean(K_ENABLED, true);
    }

    public void setEnabled(boolean value) {
        sp.edit().putBoolean(K_ENABLED, value).apply();
    }

    public boolean skipSplash() {
        return sp.getBoolean(K_SKIP, true);
    }

    public void setSkipSplash(boolean value) {
        sp.edit().putBoolean(K_SKIP, value).apply();
    }

    public boolean closePopup() {
        return sp.getBoolean(K_CLOSE, true);
    }

    public void setClosePopup(boolean value) {
        sp.edit().putBoolean(K_CLOSE, value).apply();
    }

    public boolean swipeUp() {
        return sp.getBoolean(K_SWIPE, false);
    }

    public void setSwipeUp(boolean value) {
        sp.edit().putBoolean(K_SWIPE, value).apply();
    }

    /** 直播广告：没有跳过/关闭，只能上滑退出。 */
    public boolean liveAd() {
        return sp.getBoolean(K_LIVE, true);
    }

    public void setLiveAd(boolean value) {
        sp.edit().putBoolean(K_LIVE, value).apply();
    }

    /** 只在屏幕上出现「广告」角标时才出手，避免把正常界面当成广告。 */
    public boolean requireAdBadge() {
        return sp.getBoolean(K_BADGE, false);
    }

    public void setRequireAdBadge(boolean value) {
        sp.edit().putBoolean(K_BADGE, value).apply();
    }

    public boolean allApps() {
        return sp.getBoolean(K_ALL_APPS, false);
    }

    public void setAllApps(boolean value) {
        sp.edit().putBoolean(K_ALL_APPS, value).apply();
    }

    public String keywordsRaw() {
        return sp.getString(K_KEYWORDS, "");
    }

    public void setKeywordsRaw(String value) {
        sp.edit().putString(K_KEYWORDS, value == null ? "" : value).apply();
    }

    /** 自定义关键词（英文逗号或中文逗号分隔），命中即点。 */
    public List<String> keywords() {
        List<String> out = new ArrayList<>();
        String raw = keywordsRaw();
        if (raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split("[,，]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public String targetsRaw() {
        return sp.getString(K_TARGETS, "");
    }

    public void setTargetsRaw(String value) {
        sp.edit().putString(K_TARGETS, value == null ? "" : value).apply();
    }

    /** 目标应用包名列表（逗号/换行分隔）。 */
    public List<String> targets() {
        List<String> out = new ArrayList<>();
        String raw = targetsRaw();
        if (raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split("[,，\n]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public void addTarget(String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) {
            return;
        }
        String value = pkg.trim();
        if (targets().contains(value)) {
            return;
        }
        String raw = targetsRaw();
        setTargetsRaw(raw.isEmpty() ? value : raw + "," + value);
    }

    /** 当前前台包名是否属于处理范围。 */
    public boolean matchesPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        return allApps() || targets().contains(pkg);
    }
}
