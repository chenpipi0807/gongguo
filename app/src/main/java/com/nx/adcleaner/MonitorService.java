package com.nx.adcleaner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * 前台服务：常驻通知「广告监控中」。
 * 作用有两个：一是让你随时看到它活着，二是把进程优先级抬高，别被系统随手回收。
 */
public class MonitorService extends Service {

    public static final String ACTION_START = "com.nx.adcleaner.action.START";
    public static final String ACTION_STOP = "com.nx.adcleaner.action.STOP";

    private static final String CHANNEL_ID = "monitor";
    private static final int NOTIF_ID = 1001;

    private static MonitorService instance;
    private MonitorOverlay overlay;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel(this);
        Status.serviceRunning = true;
        startForeground(NOTIF_ID, build(this));
        overlay = new MonitorOverlay(this);
        overlay.show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Status.serviceRunning = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        Status.serviceRunning = true;
        startForeground(NOTIF_ID, build(this));
        if (overlay != null) {
            overlay.show();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Status.serviceRunning = false;
        if (overlay != null) {
            overlay.hide();
        }
        instance = null;
        super.onDestroy();
    }

    /** 刚授权完悬浮窗权限时调用，把胶囊补上。 */
    public static void refreshOverlay() {
        MonitorService service = instance;
        if (service != null && service.overlay != null) {
            service.overlay.show();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, MonitorService.class);
        intent.setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, MonitorService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    /** 每次出手后刷新通知里的计数。 */
    public static void refresh(Context context) {
        if (!Status.serviceRunning) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIF_ID, build(context));
        }
    }

    private static void createChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "广告监控", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("常驻通知，显示广告监控状态");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private static Notification build(Context context) {
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = "已处理 " + Status.actions + " 次 · 扫描 " + Status.scans + " 次";
        return new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("广告监控中")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pending)
                .build();
    }
}
