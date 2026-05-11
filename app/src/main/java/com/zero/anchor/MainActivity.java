package com.zero.anchor;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OVERLAY = 1001;
    private static final int REQUEST_CODE_NOTIFICATION = 1002;
    private static final int REQUEST_CODE_QUERY_PACKAGES = 1003;
    private static final int REQUEST_CODE_BATTERY_OPTIMIZATION = 1004;

    private TextView tvAccessibilityStatus;
    private TextView tvOverlayStatus;
    private TextView tvNotificationStatus;
    private TextView tvQueryPackagesStatus;
    private TextView tvBatteryStatus;
    private TextView tvAutoStartStatus;
    private TextView tvOverallStatus;
    private TextView tvCooldownValue;

    private Button btnAccessibility;
    private Button btnOverlay;
    private Button btnNotification;
    private Button btnQueryPackages;
    private Button btnBattery;
    private Button btnAutoStart;
    private Button btnTest;
    private Button btnManageApps;
    private Button btnDecreaseCooldown;
    private Button btnIncreaseCooldown;

    private AppConfigManager appConfigManager;
    private PrefsManager prefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appConfigManager = new AppConfigManager(this);
        prefsManager = new PrefsManager(this);

        KeepAliveNotificationService.start(this);

        String action = getIntent().getAction();
        if ("ACTION_ACCESSIBILITY_DISABLED".equals(action)) {
            showAccessibilityDisabledDialog();
        }

        initViews();
        setupListeners();
        updateAllStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAllStatus();
        KeepAliveNotificationService.start(this);

        if (MindfulAccessibilityService.getInstance() != null) {
            MindfulAccessibilityService.getInstance().refreshTargetPackages();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        KeepAliveNotificationService.start(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        KeepAliveNotificationService.start(this);
    }

    private void initViews() {
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        tvOverlayStatus = findViewById(R.id.tv_overlay_status);
        tvNotificationStatus = findViewById(R.id.tv_notification_status);
        tvQueryPackagesStatus = findViewById(R.id.tv_query_packages_status);
        tvBatteryStatus = findViewById(R.id.tv_battery_status);
        tvAutoStartStatus = findViewById(R.id.tv_auto_start_status);
        tvOverallStatus = findViewById(R.id.tv_overall_status);

        btnAccessibility = findViewById(R.id.btn_enable_accessibility);
        btnOverlay = findViewById(R.id.btn_enable_overlay);
        btnNotification = findViewById(R.id.btn_enable_notification);
        btnQueryPackages = findViewById(R.id.btn_enable_query_packages);
        btnBattery = findViewById(R.id.btn_enable_battery);
        btnAutoStart = findViewById(R.id.btn_enable_auto_start);
        btnTest = findViewById(R.id.btn_test);
        btnManageApps = findViewById(R.id.btn_manage_apps);
        btnDecreaseCooldown = findViewById(R.id.btn_decrease_cooldown);
        btnIncreaseCooldown = findViewById(R.id.btn_increase_cooldown);
        tvCooldownValue = findViewById(R.id.tv_cooldown_value);

        updateCooldownValue();
    }

    private void setupListeners() {
        btnAccessibility.setOnClickListener(v -> openAccessibilitySettingsWithTip());
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnNotification.setOnClickListener(v -> requestNotificationPermission());
        btnQueryPackages.setOnClickListener(v -> requestQueryPackagesPermission());
        btnBattery.setOnClickListener(v -> requestBatteryOptimization());
        btnAutoStart.setOnClickListener(v -> requestAutoStart());
        btnTest.setOnClickListener(v -> testOverlay());
        btnManageApps.setOnClickListener(v -> openAppList());

        btnDecreaseCooldown.setOnClickListener(v -> {
            int current = prefsManager.getCooldownDurationMinutes();
            if (current > 5) {
                prefsManager.setCooldownDurationMinutes(current - 5);
                updateCooldownValue();
            }
        });

        btnIncreaseCooldown.setOnClickListener(v -> {
            int current = prefsManager.getCooldownDurationMinutes();
            if (current < 180) {
                prefsManager.setCooldownDurationMinutes(current + 5);
                updateCooldownValue();
            }
        });
    }

    private void updateCooldownValue() {
        int minutes = prefsManager.getCooldownDurationMinutes();
        tvCooldownValue.setText(minutes + " 分钟");
    }

    private void openAppList() {
        Intent intent = new Intent(this, AppListActivity.class);
        startActivity(intent);
    }

    private void updateAllStatus() {
        updateAccessibilityStatus();
        updateOverlayStatus();
        updateNotificationStatus();
        updateQueryPackagesStatus();
        updateBatteryStatus();
        updateAutoStartStatus();
        updateOverallStatus();
    }

    private void updateAccessibilityStatus() {
        boolean enabled = isAccessibilityServiceEnabled();

        if (enabled) {
            tvAccessibilityStatus.setText("✓ 已启用");
            tvAccessibilityStatus.setTextColor(0xFF4CAF50);
            btnAccessibility.setText("无障碍服务已开启");
            btnAccessibility.setEnabled(false);
        } else {
            tvAccessibilityStatus.setText("✗ 未启用");
            tvAccessibilityStatus.setTextColor(0xFFE53935);
            btnAccessibility.setText("去开启无障碍服务");
            btnAccessibility.setEnabled(true);
        }
    }

    private void updateOverlayStatus() {
        boolean hasPermission = checkOverlayPermission();

        if (hasPermission) {
            tvOverlayStatus.setText("✓ 已授权");
            tvOverlayStatus.setTextColor(0xFF4CAF50);
            btnOverlay.setText("悬浮窗权限已授权");
            btnOverlay.setEnabled(false);
        } else {
            tvOverlayStatus.setText("✗ 未授权");
            tvOverlayStatus.setTextColor(0xFFE53935);
            btnOverlay.setText("去开启悬浮窗权限");
            btnOverlay.setEnabled(true);
        }
    }

    private void updateNotificationStatus() {
        boolean hasPermission = checkNotificationPermission();

        if (hasPermission) {
            tvNotificationStatus.setText("✓ 已授权（常驻通知保活）");
            tvNotificationStatus.setTextColor(0xFF4CAF50);
            btnNotification.setText("通知权限已开启");
            btnNotification.setEnabled(false);
        } else {
            tvNotificationStatus.setText("✗ 未授权（常驻通知保活）");
            tvNotificationStatus.setTextColor(0xFFE53935);
            btnNotification.setText("去开启通知权限");
            btnNotification.setEnabled(true);
        }
    }

    private void updateQueryPackagesStatus() {
        boolean hasPermission = checkQueryPackagesPermission();

        if (hasPermission) {
            tvQueryPackagesStatus.setText("✓ 已授权");
            tvQueryPackagesStatus.setTextColor(0xFF4CAF50);
            btnQueryPackages.setText("读取应用列表权限已授权");
            btnQueryPackages.setEnabled(false);
        } else {
            tvQueryPackagesStatus.setText("✗ 未授权");
            tvQueryPackagesStatus.setTextColor(0xFFE53935);
            btnQueryPackages.setText("去开启读取应用列表权限");
            btnQueryPackages.setEnabled(true);
        }
    }

    private void updateBatteryStatus() {
        boolean isIgnoring = isIgnoringBatteryOptimizations();

        if (isIgnoring) {
            tvBatteryStatus.setText("✓ 已优化（后台无限制）");
            tvBatteryStatus.setTextColor(0xFF4CAF50);
            btnBattery.setText("电池优化已关闭");
            btnBattery.setEnabled(false);
        } else {
            tvBatteryStatus.setText("✗ 未优化（会被后台清理）");
            tvBatteryStatus.setTextColor(0xFFE53935);
            btnBattery.setText("去关闭电池优化");
            btnBattery.setEnabled(true);
        }
    }

    private void updateAutoStartStatus() {
        boolean canAutoStart = canCheckAutoStart();

        if (canAutoStart) {
            // 跳转到自启动页面后，用户需要手动返回
            tvAutoStartStatus.setText("⚠️ 请手动设置自启动权限");
            tvAutoStartStatus.setTextColor(0xFFFF9800);
            btnAutoStart.setText("去设置自启动");
            btnAutoStart.setEnabled(true);
        } else {
            tvAutoStartStatus.setText("✓ 无需设置（可能已支持）");
            tvAutoStartStatus.setTextColor(0xFF4CAF50);
            btnAutoStart.setText("无需设置");
            btnAutoStart.setEnabled(false);
        }
    }

    private void updateOverallStatus() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean overlayEnabled = checkOverlayPermission();
        boolean notificationEnabled = checkNotificationPermission();
        boolean queryPackagesEnabled = checkQueryPackagesPermission();
        boolean batteryEnabled = isIgnoringBatteryOptimizations();

        int readyCount = 0;
        int totalCount = 5;

        if (accessibilityEnabled) readyCount++;
        if (overlayEnabled) readyCount++;
        if (notificationEnabled) readyCount++;
        if (queryPackagesEnabled) readyCount++;
        if (batteryEnabled) readyCount++;

        if (readyCount == totalCount) {
            tvOverallStatus.setText("所有权限已就绪，正在守护您的专注");
            tvOverallStatus.setTextColor(0xFF4CAF50);
        } else {
            tvOverallStatus.setText("已完成 " + readyCount + "/" + totalCount + " 项权限设置");
            tvOverallStatus.setTextColor(0xFFFF9800);
        }

        btnTest.setEnabled(overlayEnabled);
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + MindfulAccessibilityService.class.getName();
        int enabled = 0;
        try {
            enabled = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }

        if (enabled == 1) {
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

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private boolean checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean checkQueryPackagesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                getPackageManager().getInstalledApplications(0);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private boolean canCheckAutoStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            
            // 检查是否是支持的机型
            return manufacturer.contains("xiaomi") || 
                   manufacturer.contains("oppo") || 
                   manufacturer.contains("vivo") || 
                   manufacturer.contains("huawei") || 
                   manufacturer.contains("honor") ||
                   manufacturer.contains("samsung") || 
                   manufacturer.contains("meizu") || 
                   manufacturer.contains("oneplus");
        }
        return false;
    }

    private void requestAutoStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            
            try {
                if (manufacturer.contains("xiaomi")) {
                    intent.setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                } else if (manufacturer.contains("oppo")) {
                    intent.setComponent(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                } else if (manufacturer.contains("vivo")) {
                    intent.setComponent(new ComponentName("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
                } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                    intent.setComponent(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                } else if (manufacturer.contains("samsung")) {
                    intent.setComponent(new ComponentName("com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"));
                } else if (manufacturer.contains("meizu")) {
                    intent.setComponent(new ComponentName("com.meizu.safe",
                        "com.meizu.safe.permission.SmartBGActivity"));
                } else if (manufacturer.contains("oneplus")) {
                    intent.setComponent(new ComponentName("com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                }
                
                startActivity(intent);
            } catch (Exception e) {
                new AlertDialog.Builder(this)
                        .setTitle("无法自动跳转")
                        .setMessage("请手动设置：\n\n设置 → 应用管理 → 找到「正念守护」→ 允许自启动")
                        .setPositiveButton("好的", null)
                        .show();
            }
        }
    }

    private void openAccessibilitySettingsWithTip() {
        new AlertDialog.Builder(this)
                .setTitle("开启无障碍服务")
                .setMessage("请按以下步骤操作：\n\n1. 在无障碍设置中找到「正念守护」\n2. 点击进入后，系统会有10秒倒计时\n3. 等待倒计时结束后，点击「确认开启」\n\n⚠️ 开启后请返回此应用")
                .setPositiveButton("我知道了，去设置", (dialog, which) -> {
                    openAccessibilitySettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, REQUEST_CODE_OVERLAY);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIFICATION);
            }
        }
    }

    private void requestQueryPackagesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION);
        }
    }

    private void testOverlay() {
        MindfulAccessibilityService service = MindfulAccessibilityService.getInstance();
        if (service != null) {
            InterventionOverlay overlay = service.getOverlay();
            if (overlay == null) {
                overlay = new InterventionOverlay(this, () -> {});
            }

            overlay.showBlockerScreen("测试应用", "com.test.app",
                taskDesc -> {
                },
                minutes -> {
                },
                () -> {
                });
        } else {
            showAccessibilityDisabledDialog();
        }
    }

    private void showAccessibilityDisabledDialog() {
        new AlertDialog.Builder(this)
                .setTitle("无障碍服务已关闭")
                .setMessage("无障碍服务已停止，拦截功能将无法正常工作。请重新开启无障碍服务。")
                .setPositiveButton("去开启", (dialog, which) -> {
                    openAccessibilitySettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY || requestCode == REQUEST_CODE_BATTERY_OPTIMIZATION) {
            updateAllStatus();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_NOTIFICATION || requestCode == REQUEST_CODE_QUERY_PACKAGES) {
            updateAllStatus();
        }
    }
}
