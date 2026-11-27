package com.nx.adcleaner;

/** 运行状态，界面和服务同进程共享。 */
public final class Status {

    public static volatile boolean serviceRunning;
    public static volatile int scans;
    public static volatile int badgeHits;
    public static volatile int actions;
    public static volatile long lastActionAt;
    public static volatile String lastReason = "还没有动作";

    private Status() {
    }

    public static void resetCounters() {
        scans = 0;
        badgeHits = 0;
        actions = 0;
        lastActionAt = 0;
        lastReason = "还没有动作";
    }
}
