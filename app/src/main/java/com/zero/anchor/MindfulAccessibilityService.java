package com.zero.anchor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MindfulAccessibilityService extends AccessibilityService {

    private static final String TAG = "MindfulAccessibilityService";
    private static final long CHECK_INTERVAL_MS = 500;

    private static MindfulAccessibilityService instance;
    private static InterventionOverlay overlay;
    private static PrefsManager prefsManager;
    private static AppConfigManager appConfigManager;

    private final Set<String> targetPackages = new HashSet<>();
    private volatile boolean isShowingBlocker = false;
    private volatile String lastForegroundPackage = "";

    private Handler mainHandler;
    private Handler checkHandler;
    private Runnable checkForegroundRunnable;

    public static MindfulAccessibilityService getInstance() {
        return instance;
    }

    public static InterventionOverlay getOverlay() {
        return overlay;
    }

    public static PrefsManager getPrefsManager() {
        return prefsManager;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "无障碍服务已连接");

        prefsManager = new PrefsManager(this);
        appConfigManager = new AppConfigManager(this);
        mainHandler = new Handler(Looper.getMainLooper());
        checkHandler = new Handler(Looper.getMainLooper());

        loadTargetPackages();

        KeepAliveNotificationService.start(this);

        // 立即启动检测，但在前台轮询中处理
        startForegroundCheck();

        Log.i(TAG, "已加载目标应用: " + targetPackages);
    }

    private void loadTargetPackages() {
        targetPackages.clear();
        targetPackages.addAll(appConfigManager.getSelectedPackages());
        Log.i(TAG, "目标包列表已更新: " + targetPackages);
    }

    private void startForegroundCheck() {
        if (checkForegroundRunnable != null) {
            checkHandler.removeCallbacks(checkForegroundRunnable);
        }

        checkForegroundRunnable = new Runnable() {
            @Override
            public void run() {
                checkCurrentForeground();
                if (checkForegroundRunnable != null) {
                    checkHandler.postDelayed(this, CHECK_INTERVAL_MS);
                }
            }
        };

        checkHandler.post(checkForegroundRunnable);
        Log.i(TAG, "前台包名检测已启动");
    }

    private void checkCurrentForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null && !windows.isEmpty()) {
                    AccessibilityWindowInfo topWindow = null;
                    for (AccessibilityWindowInfo window : windows) {
                        if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                            topWindow = window;
                            break;
                        }
                    }

                    if (topWindow != null && topWindow.getRoot() != null) {
                        CharSequence pkg = topWindow.getRoot().getPackageName();
                        if (pkg != null) {
                            checkAndIntercept(pkg.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "前台检测异常", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        try {
            CharSequence packageNameSeq = event.getPackageName();
            if (packageNameSeq == null) {
                return;
            }

            String packageName = packageNameSeq.toString();
            checkAndIntercept(packageName);
        } catch (Exception e) {
            Log.e(TAG, "事件处理异常", e);
        }
    }

    private void checkAndIntercept(String packageName) {
        // 如果是当前应用本身，跳过
        if (packageName.equals(getPackageName())) {
            return;
        }

        // 检查是否和上次相同，如果不同则重新加载列表
        if (!packageName.equals(lastForegroundPackage)) {
            lastForegroundPackage = packageName;
            loadTargetPackages();
            Log.d(TAG, "前台应用已变更: " + packageName);
        }

        // 检查是否是目标应用
        if (!targetPackages.contains(packageName)) {
            return;
        }

        // 检查是否正在显示拦截
        if (isShowingBlocker) {
            return;
        }

        // 检查是否有任务或娱乐正在运行
        if (prefsManager.isTimerRunning() || prefsManager.isEntertainRunning()) {
            return;
        }

        // 注意：不再在这里检查冷却，让弹窗显示但在弹窗中让娱乐按钮变灰
        // 冷却期间依然可以弹出拦截窗口，用户可以选择任务模式

        Log.i(TAG, "检测到目标应用: " + packageName + "，正在显示拦截窗口");
        mainHandler.post(() -> showBlockerOverlay(packageName));
    }

    private void showBlockerOverlay(String packageName) {
        if (isShowingBlocker) {
            return;
        }

        isShowingBlocker = true;
        prefsManager.setBlockingActive(true);
        prefsManager.setLastBlockedPackage(packageName);

        String appName = getAppDisplayName(packageName);

        if (overlay == null) {
            overlay = new InterventionOverlay(this, this::performGoHome);
        }

        overlay.showBlockerScreen(appName, packageName, taskDesc -> {
            onTaskModeSelected(packageName, taskDesc);
        }, mins -> {
            onEntertainModeSelected(packageName, mins);
        }, () -> {
            onBlockerRejected(packageName);
        });
    }

    private void onTaskModeSelected(String packageName, String taskDescription) {
        isShowingBlocker = false;
        prefsManager.setBlockingActive(false);
        prefsManager.setTimerRunning(true, taskDescription);

        if (overlay != null) {
            overlay.showTaskScreen(taskDescription);
        }

        mainHandler.postDelayed(() -> bringTargetAppToFront(packageName), 300);
    }

    private void onEntertainModeSelected(String packageName, int minutes) {
        isShowingBlocker = false;
        prefsManager.setBlockingActive(false);
        prefsManager.startEntertainmentCooldown();
        prefsManager.setEntertainRunning(true, minutes * 60 * 1000L);

        if (overlay != null) {
            overlay.showEntertainScreen(minutes, packageName);
        }
    }

    private void onBlockerRejected(String packageName) {
        Log.i(TAG, "用户拒绝，选择返回桌面");

        isShowingBlocker = false;
        prefsManager.setBlockingActive(false);

        performGoHome();
    }

    private void performGoHome() {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME);
        } catch (Exception e) {
            Log.e(TAG, "返回桌面失败", e);
        }
    }

    private void bringTargetAppToFront(String packageName) {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launchIntent);
                Log.i(TAG, "已启动目标应用: " + packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "启动目标应用失败", e);
        }
    }

    private void killTargetApp(String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭目标应用失败", e);
        }
    }

    private String getAppDisplayName(String packageName) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    public void refreshTargetPackages() {
        loadTargetPackages();
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
        // 不重置 isShowingBlocker，避免弹窗混乱
        // 尝试重启检测
        mainHandler.postDelayed(() -> {
            if (checkForegroundRunnable != null) {
                startForegroundCheck();
            }
        }, 1000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;

        if (checkForegroundRunnable != null) {
            checkHandler.removeCallbacks(checkForegroundRunnable);
            checkForegroundRunnable = null;
        }

        if (overlay != null) {
            overlay.cleanup();
            overlay = null;
        }
        Log.i(TAG, "无障碍服务已销毁");
    }
}
