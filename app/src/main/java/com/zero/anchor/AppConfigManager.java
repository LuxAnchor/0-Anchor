package com.zero.anchor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppConfigManager {

    private static final String PREFS_NAME = "mindful_app_config";
    private static final String KEY_TARGET_PACKAGES = "target_packages";

    public static class AppInfo {
        public String packageName;
        public String appName;
        public Drawable appIcon;
        public boolean isSelected;

        public AppInfo(String packageName, String appName, Drawable appIcon, boolean isSelected) {
            this.packageName = packageName;
            this.appName = appName;
            this.appIcon = appIcon;
            this.isSelected = isSelected;
        }
    }

    private final SharedPreferences prefs;
    private final Context context;

    public AppConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<AppInfo> getAllInstalledApps(Context context) {
        List<AppInfo> apps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        
        Set<String> selectedPackages = new AppConfigManager(context).getSelectedPackages();
        
        try {
            List<ApplicationInfo> packages = pm.getInstalledApplications(0);
            for (ApplicationInfo appInfo : packages) {
                // 跳过系统应用和本应用
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }
                if (appInfo.packageName.equals(context.getPackageName())) {
                    continue;
                }
                
                String appName = pm.getApplicationLabel(appInfo).toString();
                Drawable icon = appInfo.loadIcon(pm);
                boolean isSelected = selectedPackages.contains(appInfo.packageName);
                
                apps.add(new AppInfo(appInfo.packageName, appName, icon, isSelected));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 按应用名称排序
        apps.sort((a, b) -> a.appName.compareToIgnoreCase(b.appName));
        
        return apps;
    }

    public Set<String> getSelectedPackages() {
        return prefs.getStringSet(KEY_TARGET_PACKAGES, new HashSet<>());
    }

    public void setSelectedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_TARGET_PACKAGES, packages).apply();
    }

    public boolean isPackageSelected(String packageName) {
        return getSelectedPackages().contains(packageName);
    }
}
