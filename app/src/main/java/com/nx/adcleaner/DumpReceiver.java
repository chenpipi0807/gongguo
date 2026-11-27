package com.nx.adcleaner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 调试用：手动抓一次当前界面。
 * adb shell am broadcast -a com.nx.adcleaner.DUMP
 */
public class DumpReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.nx.adcleaner.DUMP";

    @Override
    public void onReceive(Context context, Intent intent) {
        String dump = AdSkipService.dumpActiveWindow();
        LogStore.log(context, "manual", dump == null ? "dump=null（无障碍服务没连上）" : dump);
    }
}
