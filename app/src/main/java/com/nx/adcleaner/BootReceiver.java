package com.nx.adcleaner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机 / 应用更新后自动把前台服务拉起来，省得每次手动点。
 * 注意：无障碍服务本身是系统托管的，开了就一直生效；这个只是把常驻通知和进程优先级也恢复。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        Prefs prefs = Prefs.get(context);
        if (!prefs.enabled()) {
            return;
        }
        AccessibilityKeeper.ensureEnabled(context);
        try {
            MonitorService.start(context);
            LogStore.log(context, "boot", "auto start after " + action);
        } catch (RuntimeException e) {
            LogStore.log(context, "boot", "start failed: " + e.getMessage());
        }
    }
}
