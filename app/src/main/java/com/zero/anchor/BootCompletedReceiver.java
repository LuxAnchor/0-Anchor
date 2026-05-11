package com.zero.anchor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * ============================================
 * Boot Completed Receiver - 开机自启动接收器
 * ============================================
 *
 * 设备重启后自动启动独立保活服务和无障碍服务检查，
 * 确保用户无需每次手动开启服务。
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null &&
            intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {

            Log.i(TAG, "设备已重启，准备启动服务");

            // 立即启动独立保活服务（独立于无障碍服务）
            try {
                KeepAliveNotificationService.start(context);
                Log.i(TAG, "已启动独立保活服务");
            } catch (Exception e) {
                Log.e(TAG, "启动保活服务失败: " + e.getMessage());
            }

            // 启动主 Activity 来触发无障碍服务检查
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                context.startActivity(mainIntent);
                Log.i(TAG, "已启动主界面");
            } catch (Exception e) {
                Log.e(TAG, "启动主界面失败: " + e.getMessage());
            }
        }
    }
}
