package com.zero.anchor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * ============================================
 * 独立保活服务 - 与无障碍服务完全分离
 * ============================================
 *
 * 核心职责：
 * 1. 独立的常驻通知，不依赖无障碍服务
 * 2. 监控无障碍服务状态，必要时提醒用户
 * 3. 确保应用即使在后台也能保持运行
 *
 * 这个服务应该一直运行，除非用户手动卸载应用
 */
public class KeepAliveNotificationService extends Service {

    private static final String TAG = "KeepAliveNotification";
    private static final String CHANNEL_ID = "mindful_keep_alive_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long CHECK_INTERVAL_MS = 10000; // 每10秒检查一次

    private static volatile boolean isRunning = false;
    private Handler checkHandler;
    private Runnable checkRunnable;

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "独立保活通知服务已创建");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "独立保活通知服务启动");

        if (!isRunning) {
            startForeground(NOTIFICATION_ID, buildNotification());
            isRunning = true;
            startAccessibilityCheck();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        stopAccessibilityCheck();
        Log.i(TAG, "独立保活通知服务已销毁");

        Intent restartIntent = new Intent(this, KeepAliveNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "正念守护服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持应用后台运行，确保拦截功能正常工作");
            channel.setShowBadge(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("Anchor 核心监控")
                .setContentText("前额叶强制接管中…")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(Notification.PRIORITY_LOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }

    private void startAccessibilityCheck() {
        // 暂时禁用自动检查，避免在设置时干扰用户
        Log.i(TAG, "已禁用无障碍服务自动检查，避免干扰用户设置");
    }

    private void stopAccessibilityCheck() {
        if (checkHandler != null && checkRunnable != null) {
            checkHandler.removeCallbacks(checkRunnable);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + MindfulAccessibilityService.class.getName();
        try {
            int accessibilityEnabled = android.provider.Settings.Secure.getInt(
                    getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
            );

            if (accessibilityEnabled == 1) {
                String settingValue = android.provider.Settings.Secure.getString(
                        getContentResolver(),
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );
                if (settingValue != null) {
                    return settingValue.toLowerCase().contains(serviceName.toLowerCase());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查无障碍服务状态失败: " + e.getMessage());
        }
        return false;
    }

    public static void start(Context context) {
        if (isRunning) {
            Log.d(TAG, "独立保活通知服务已在运行");
            return;
        }

        Intent intent = new Intent(context, KeepAliveNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        Log.i(TAG, "启动独立保活通知服务");
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, KeepAliveNotificationService.class);
        context.stopService(intent);
        Log.i(TAG, "停止独立保活通知服务");
    }
}
