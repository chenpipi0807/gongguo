package com.nx.adcleaner;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * 自愈：华为/荣耀在「强行停止」或覆盖安装后会把这个无障碍服务关掉。
 * 如果用户用 adb 给过一次 WRITE_SECURE_SETTINGS，这里就能自己把自己重新打开，不用每次手点。
 */
public final class AccessibilityKeeper {

    private static final String KEY_SERVICES = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
    private static final String KEY_ENABLED = Settings.Secure.ACCESSIBILITY_ENABLED;

    private AccessibilityKeeper() {
    }

    public static boolean canWrite(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isEnabled(Context context) {
        try {
            String flat = Settings.Secure.getString(context.getContentResolver(), KEY_SERVICES);
            if (flat == null) {
                return false;
            }
            String target = target(context);
            for (String part : flat.split(":")) {
                if (part.equalsIgnoreCase(target)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // 读不到就当没开
        }
        return false;
    }

    /** 已经开就只补一次总开关；没开且有权限就自己写回去。 */
    public static boolean ensureEnabled(Context context) {
        if (!isEnabled(context) && !canWrite(context)) {
            return false;
        }
        try {
            if (!isEnabled(context)) {
                String flat = Settings.Secure.getString(context.getContentResolver(), KEY_SERVICES);
                String target = target(context);
                String next = (flat == null || flat.trim().isEmpty()) ? target : flat + ":" + target;
                Settings.Secure.putString(context.getContentResolver(), KEY_SERVICES, next);
                LogStore.log(context, "keeper", "re-enabled accessibility: " + next);
            }
            Settings.Secure.putInt(context.getContentResolver(), KEY_ENABLED, 1);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String target(Context context) {
        return context.getPackageName() + "/" + AdSkipService.class.getName();
    }
}
