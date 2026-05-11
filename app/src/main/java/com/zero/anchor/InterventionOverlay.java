package com.zero.anchor;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class InterventionOverlay {

    private static final String TAG = "InterventionOverlay";

    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler;
    private final int windowType;

    private View blockerView;
    private View taskView;
    private View entertainView;

    private EditText taskInput;
    private TextView taskTimerDisplay;
    private TextView entertainTimerDisplay;
    private Runnable taskTimerRunnable;
    private CountDownTimer entertainTimer;
    private long taskStartTimeMs = 0;
    private Runnable onGoHomeCallback;

    private String currentPackageName = "";
    private String currentAppName = "";

    public InterventionOverlay(Context context, Runnable goHomeCallback) {
        this.context = context;
        this.onGoHomeCallback = goHomeCallback;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        if (context instanceof AccessibilityService) {
            this.windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            this.windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
    }

    public interface TaskCallback {
        void onTaskSelected(String taskDescription);
    }

    public interface EntertainCallback {
        void onEntertainSelected(int minutes);
    }

    public interface RejectCallback {
        void onRejected();
    }

    public void showBlockerScreen(String appName, String packageName, TaskCallback onTaskChosen, EntertainCallback onEntertainChosen, RejectCallback onRejected) {
        cleanup();
        currentPackageName = packageName;
        currentAppName = appName;

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xF0000000);

        LinearLayout card = createCard();
        card.setGravity(Gravity.CENTER);

        TextView title = new TextView(context);
        title.setText("使用模式");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        addSpace(card, 16);

        TextView subtitle = new TextView(context);
        subtitle.setText("检测到您正在打开：" + appName);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setTextColor(0xFFAAAAAA);
        subtitle.setGravity(Gravity.CENTER);
        card.addView(subtitle);

        addSpace(card, 12);

        TextView hint = new TextView(context);
        hint.setText("请选择使用模式");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setTextColor(0xFF888888);
        hint.setGravity(Gravity.CENTER);
        card.addView(hint);

        addSpace(card, 32);

        Button btnTask = createPrimaryButton("任务模式", "记录任务，正向计时");
        btnTask.setOnClickListener(v -> {
            showTaskInputScreen(appName, packageName, onTaskChosen, onEntertainChosen, onRejected);
        });
        card.addView(btnTask);

        addSpace(card, 16);

        Button btnEntertain = createPrimaryButton("娱乐模式", "");
        PrefsManager prefs = MindfulAccessibilityService.getPrefsManager();
        if (prefs != null && prefs.isInEntertainmentCooldown()) {
            int remaining = prefs.getEntertainmentCooldownRemainingMinutes();
            btnEntertain.setText("娱乐模式");
            btnEntertain.setEnabled(false);
            btnEntertain.setAlpha(0.5f);
        }
        final boolean finalInCooldown = (prefs != null && prefs.isInEntertainmentCooldown());
        btnEntertain.setOnClickListener(v -> {
            if (!finalInCooldown) {
                showEntertainDurationPicker(appName, packageName, onTaskChosen, onEntertainChosen, onRejected);
            }
        });
        card.addView(btnEntertain);

        addSpace(card, 16);

        Button btnReject = createDangerButton("返回桌面");
        btnReject.setOnClickListener(v -> {
            cleanup();
            if (onRejected != null) {
                onRejected.onRejected();
            }
        });
        card.addView(btnReject);

        root.addView(card);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(root, params);
            blockerView = root;
        } catch (Exception e) {
            Log.e(TAG, "显示拦截弹窗失败", e);
        }
    }

    private void showTaskInputScreen(String appName, String packageName, TaskCallback onTaskChosen, EntertainCallback onEntertainChosen, RejectCallback onRejected) {
        cleanup();

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xF0000000);

        LinearLayout card = createCard();
        card.setGravity(Gravity.CENTER);

        TextView title = new TextView(context);
        title.setText("任务模式");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        addSpace(card, 16);

        TextView subtitle = new TextView(context);
        subtitle.setText("为当前任务命名，开始计时");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(0xFFAAAAAA);
        subtitle.setGravity(Gravity.CENTER);
        card.addView(subtitle);

        addSpace(card, 24);

        taskInput = new EditText(context);
        taskInput.setHint("例如：学习Android开发");
        taskInput.setHintTextColor(0xFF777777);
        taskInput.setTextColor(0xFFFFFFFF);
        taskInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        taskInput.setPadding(dp(16), dp(14), dp(16), dp(14));
        taskInput.setBackgroundColor(0xFF333333);
        taskInput.setFocusable(true);
        taskInput.setFocusableInTouchMode(true);
        taskInput.setClickable(true);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        taskInput.setLayoutParams(inputLp);
        card.addView(taskInput);

        addSpace(card, 20);

        Button btnStart = createPrimaryButton("开始计时", "点击开始任务计时");
        btnStart.setOnClickListener(v -> {
            String task = taskInput.getText().toString().trim();
            if (task.isEmpty()) {
                taskInput.setError("请输入任务名称");
                return;
            }
            cleanup();
            if (onTaskChosen != null) {
                onTaskChosen.onTaskSelected(task);
            }
        });
        card.addView(btnStart);

        addSpace(card, 12);

        Button btnBack = createDangerButton("返回");
        btnBack.setOnClickListener(v -> {
            showBlockerScreen(appName, packageName, onTaskChosen, onEntertainChosen, onRejected);
        });
        card.addView(btnBack);

        root.addView(card);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(root, params);
            blockerView = root;

            mainHandler.postDelayed(() -> {
                if (taskInput != null) {
                    taskInput.requestFocus();
                    taskInput.setClickable(true);
                }
            }, 300);
        } catch (Exception e) {
            Log.e(TAG, "显示任务输入失败", e);
        }
    }

    private void showEntertainDurationPicker(String appName, String packageName, TaskCallback onTaskChosen, EntertainCallback onEntertainChosen, RejectCallback onRejected) {
        cleanup();

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xF0000000);

        LinearLayout card = createCard();
        card.setGravity(Gravity.CENTER);

        TextView title = new TextView(context);
        title.setText("娱乐模式");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        addSpace(card, 16);

        TextView subtitle = new TextView(context);
        subtitle.setText("想玩多久？");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setTextColor(0xFFAAAAAA);
        subtitle.setGravity(Gravity.CENTER);
        card.addView(subtitle);

        addSpace(card, 24);

        int[] minutesOptions = {5, 10, 15, 30, 60};
        for (int minutes : minutesOptions) {
            Button btnDuration = createDurationButton(minutes + " 分钟");
            int finalMinutes = minutes;
            btnDuration.setOnClickListener(v -> {
                cleanup();
                if (onEntertainChosen != null) {
                    onEntertainChosen.onEntertainSelected(finalMinutes);
                }
            });
            card.addView(btnDuration);
            addSpace(card, 12);
        }

        addSpace(card, 8);

        Button btnBack = createDangerButton("返回");
        btnBack.setOnClickListener(v -> {
            showBlockerScreen(appName, packageName, onTaskChosen, onEntertainChosen, onRejected);
        });
        card.addView(btnBack);

        root.addView(card);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(root, params);
            blockerView = root;
        } catch (Exception e) {
            Log.e(TAG, "显示娱乐时长选择失败", e);
        }
    }

    public void showTaskScreen(String taskDescription) {
        cleanup();

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x00000000);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xE0333333);
        cardBg.setCornerRadius(dp(8));
        card.setBackground(cardBg);

        TextView taskLabel = new TextView(context);
        taskLabel.setText("任务：" + taskDescription);
        taskLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        taskLabel.setTextColor(0xFFFFFFFF);
        taskLabel.setGravity(Gravity.END);
        taskLabel.setMaxLines(1);
        taskLabel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(taskLabel);

        addSpace(card, 4);

        taskTimerDisplay = new TextView(context);
        taskTimerDisplay.setText("00:00:00");
        taskTimerDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        taskTimerDisplay.setTextColor(0xFF4CAF50);
        taskTimerDisplay.setTypeface(android.graphics.Typeface.MONOSPACE);
        taskTimerDisplay.setGravity(Gravity.END);
        taskTimerDisplay.setClickable(true);
        taskTimerDisplay.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(taskTimerDisplay);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(8);
        params.y = dp(48);

        card.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(root, params);
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            card.performClick();
                        }
                        return true;
                }
                return false;
            }
        });

        card.setOnClickListener(v -> {
            Log.i(TAG, "用户点击关闭任务计时");
            stopTaskTimer();
            cleanup();
            if (MindfulAccessibilityService.getPrefsManager() != null) {
                MindfulAccessibilityService.getPrefsManager().setTimerRunning(false, "");
            }
            if (onGoHomeCallback != null) {
                onGoHomeCallback.run();
            }
        });

        root.addView(card);

        try {
            windowManager.addView(root, params);
            taskView = root;
            taskStartTimeMs = System.currentTimeMillis();
            startTaskTimer();
        } catch (Exception e) {
            Log.e(TAG, "显示任务气泡失败", e);
        }
    }

    public void showEntertainScreen(int minutes, String packageName) {
        cleanup();

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x00000000);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xE0333333);
        cardBg.setCornerRadius(dp(12));
        card.setBackground(cardBg);

        TextView closeHint = new TextView(context);
        closeHint.setText("点击可退出");
        closeHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        closeHint.setTextColor(0x80FFFFFF);
        closeHint.setGravity(Gravity.CENTER);
        closeHint.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(closeHint);

        addSpace(card, 4);

        entertainTimerDisplay = new TextView(context);
        entertainTimerDisplay.setText(formatTime(minutes * 60 * 1000L));
        entertainTimerDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        entertainTimerDisplay.setTextColor(0xFFFF4444);
        entertainTimerDisplay.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        entertainTimerDisplay.setGravity(Gravity.CENTER);
        entertainTimerDisplay.setClickable(true);
        entertainTimerDisplay.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(entertainTimerDisplay);

        addSpace(card, 4);

        TextView appLabel = new TextView(context);
        appLabel.setText(packageName);
        appLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        appLabel.setTextColor(0x60FFFFFF);
        appLabel.setGravity(Gravity.CENTER);
        appLabel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(appLabel);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(8);
        params.y = dp(48);

        card.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(root, params);
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            card.performClick();
                        }
                        return true;
                }
                return false;
            }
        });

        card.setOnClickListener(v -> {
            Log.i(TAG, "用户点击关闭娱乐倒计时");
            stopEntertainTimer();
            cleanup();
            if (MindfulAccessibilityService.getPrefsManager() != null) {
                MindfulAccessibilityService.getPrefsManager().setEntertainRunning(false, 0);
            }
            if (onGoHomeCallback != null) {
                onGoHomeCallback.run();
            }
        });

        root.addView(card);

        try {
            windowManager.addView(root, params);
            entertainView = root;

            if (MindfulAccessibilityService.getPrefsManager() != null) {
                MindfulAccessibilityService.getPrefsManager().setEntertainRunning(true, minutes * 60 * 1000L);
            }

            startEntertainTimer(minutes * 60 * 1000L);
        } catch (Exception e) {
            Log.e(TAG, "显示娱乐倒计时失败", e);
        }
    }

    private void startTaskTimer() {
        stopTaskTimer();
        taskTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (taskTimerDisplay != null) {
                    long elapsed = System.currentTimeMillis() - taskStartTimeMs;
                    taskTimerDisplay.setText(formatTime(elapsed));
                }
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.post(taskTimerRunnable);
    }

    private void stopTaskTimer() {
        if (taskTimerRunnable != null) {
            mainHandler.removeCallbacks(taskTimerRunnable);
            taskTimerRunnable = null;
        }
    }

    private void startEntertainTimer(long totalMs) {
        stopEntertainTimer();
        entertainTimer = new CountDownTimer(totalMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (entertainTimerDisplay != null) {
                    entertainTimerDisplay.setText(formatTime(millisUntilFinished));
                }
            }

            @Override
            public void onFinish() {
                Log.i(TAG, "娱乐时间结束，强制返回桌面");
                stopEntertainTimer();
                cleanup();
                if (MindfulAccessibilityService.getPrefsManager() != null) {
                    MindfulAccessibilityService.getPrefsManager().setEntertainRunning(false, 0);
                }
                if (onGoHomeCallback != null) {
                    onGoHomeCallback.run();
                }
            }
        }.start();
    }

    private void stopEntertainTimer() {
        if (entertainTimer != null) {
            entertainTimer.cancel();
            entertainTimer = null;
        }
    }

    public void cleanup() {
        stopTaskTimer();
        stopEntertainTimer();

        removeView(blockerView);
        removeView(taskView);
        removeView(entertainView);

        blockerView = null;
        taskView = null;
        entertainView = null;
    }

    private void removeView(View view) {
        if (view != null && windowManager != null) {
            try {
                windowManager.removeView(view);
            } catch (Exception e) {
                Log.e(TAG, "移除视图失败", e);
            }
        }
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(32), dp(40), dp(32), dp(40));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF2D2D2D);
        cardBg.setCornerRadius(dp(20));
        card.setBackground(cardBg);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;
        params.setMargins(dp(32), 0, dp(32), 0);
        card.setLayoutParams(params);

        return card;
    }

    private Button createPrimaryButton(String text, String hint) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundColor(0xFF4CAF50);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF4CAF50);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(params);

        return btn;
    }

    private Button createSecondaryButton(String text, String hint) {
        Button btn = new Button(context);
        btn.setText(text + "\n" + hint);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2196F3);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(params);

        return btn;
    }

    private Button createDurationButton(String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btn.setPadding(dp(20), dp(12), dp(20), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF607D8B);
        bg.setCornerRadius(dp(10));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(params);

        return btn;
    }

    private Button createDangerButton(String text) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF555555);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(params);

        return btn;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        parent.addView(space);
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    private String formatTime(long ms) {
        long totalSec = ms / 1000;
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
