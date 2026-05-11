package com.zero.anchor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

/**
 * ============================================
 * 前台保活服务
 * ============================================
 *
 * 核心职责：
 * 1. 通过前台服务提升应用优先级，防止被系统杀死
 * 2. 显示持久通知，告知用户服务运行状态
 * 3. 在无障碍服务被销毁时自动重启
 *
 * 注意：Android 12+ 对前台服务有严格限制，需要声明相应权限
 */
public class KeepAliveService extends Service {

    private static final String TAG = "KeepAliveService";
    private static final String CHANNEL_ID = "mindful_keep_alive_channel";
    private static final int NOTIFICATION_ID = 1001;

    // 服务运行状态
    private static volatile boolean isRunning = false;

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "保活服务已创建");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "保活服务启动");
        
        // 启动为前台服务
        startForeground(NOTIFICATION_ID, buildNotification());
        isRunning = true;
        
        // 检查无障碍服务是否运行，如果没有则尝试重启
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "无障碍服务未运行，尝试引导用户开启");
            // 这里可以发送广播通知 MainActivity 引导用户
        }
        
        // START_STICKY: 服务被杀死后会自动重启
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
        Log.i(TAG, "保活服务已销毁");
        
        // 尝试重新启动服务（如果被系统杀死）
        Intent restartIntent = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
    }

    /**
     * 创建通知渠道（Android 8.0+ 需要）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mindful Intervention 保活服务",
                    NotificationManager.IMPORTANCE_LOW  // 低重要性，不打扰用户
            );
            channel.setDescription("保持应用后台运行，确保抖音拦截功能正常工作");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建前台服务通知
     */
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

        builder.setContentTitle("Mindful Intervention 运行中")
                .setContentText("正在保护您的专注时间...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)  // 使用系统图标
                .setContentIntent(pendingIntent)
                .setOngoing(true)  // 持续通知，不能滑动删除
                .setAutoCancel(false);

        // Android 12+ 需要设置前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }

    /**
     * 检查无障碍服务是否启用
     */
    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + MindfulAccessibilityService.class.getName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Exception e) {
            Log.e(TAG, "检查无障碍服务状态失败: " + e.getMessage());
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (settingValue != null) {
                return settingValue.toLowerCase().contains(serviceName.toLowerCase());
            }
        }
        return false;
    }

    /**
     * 启动保活服务（供外部调用）
     */
    public static void start(Context context) {
        if (isRunning) {
            Log.d(TAG, "保活服务已在运行");
            return;
        }
        
        Intent intent = new Intent(context, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        Log.i(TAG, "启动保活服务");
    }

    /**
     * 停止保活服务（供外部调用）
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, KeepAliveService.class);
        context.stopService(intent);
        Log.i(TAG, "停止保活服务");
    }
}
