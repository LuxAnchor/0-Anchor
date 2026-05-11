package com.zero.anchor;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppListActivity extends Activity {

    private AppConfigManager configManager;
    private ListView appList;
    private AppAdapter adapter;
    private EditText searchText;
    private List<AppConfigManager.AppInfo> allApps = new ArrayList<>();
    private List<AppConfigManager.AppInfo> filteredApps = new ArrayList<>();
    private Set<String> selectedPackages = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        configManager = new AppConfigManager(this);
        selectedPackages = new HashSet<>(configManager.getSelectedPackages());
        
        appList = findViewById(R.id.app_list);
        searchText = findViewById(R.id.search_text);
        
        loadApps();
        
        searchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }
            
            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            saveAndFinish();
        });
        
        findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            finish();
        });
    }

    private void loadApps() {
        new Thread(() -> {
            allApps = AppConfigManager.getAllInstalledApps(this);
            filteredApps = new ArrayList<>(allApps);
            runOnUiThread(() -> {
                adapter = new AppAdapter(this, filteredApps);
                appList.setAdapter(adapter);
            });
        }).start();
    }

    private void filterApps(String query) {
        filteredApps.clear();
        if (query.isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            String lowerQuery = query.toLowerCase();
            for (AppConfigManager.AppInfo app : allApps) {
                if (app.appName.toLowerCase().contains(lowerQuery)) {
                    filteredApps.add(app);
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void saveAndFinish() {
        Set<String> newSelected = new HashSet<>();
        for (AppConfigManager.AppInfo app : allApps) {
            if (app.isSelected) {
                newSelected.add(app.packageName);
            }
        }
        configManager.setSelectedPackages(newSelected);

        // 刷新服务中的目标应用列表
        MindfulAccessibilityService service = MindfulAccessibilityService.getInstance();
        if (service != null) {
            service.refreshTargetPackages();
        }

        finish();
    }

    private static class AppAdapter extends BaseAdapter {
        private Context context;
        private List<AppConfigManager.AppInfo> apps;

        public AppAdapter(Context context, List<AppConfigManager.AppInfo> apps) {
            this.context = context;
            this.apps = apps;
        }

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public AppConfigManager.AppInfo getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
            }
            
            AppConfigManager.AppInfo app = getItem(position);
            
            ImageView icon = convertView.findViewById(R.id.app_icon);
            TextView name = convertView.findViewById(R.id.app_name);
            CheckBox checkBox = convertView.findViewById(R.id.checkbox);
            
            name.setText(app.appName);
            if (app.appIcon != null) {
                icon.setImageDrawable(app.appIcon);
            } else {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            
            checkBox.setChecked(app.isSelected);
            checkBox.setOnCheckedChangeListener(null);
            
            checkBox.setOnClickListener(v -> {
                app.isSelected = checkBox.isChecked();
            });
            
            convertView.setOnClickListener(v -> {
                app.isSelected = !app.isSelected;
                checkBox.setChecked(app.isSelected);
            });
            
            return convertView;
        }
    }
}
