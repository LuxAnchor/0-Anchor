package com.zero.anchor;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {

    private static final String PREFS_NAME = "mindful_intervention_prefs";

    private static final String KEY_COOLDOWN_DURATION = "cooldown_duration_minutes";
    private static final String KEY_COOLDOWN_START_TIME = "cooldown_start_time_ms";
    private static final String KEY_ENTERTAINMENT_COOLDOWN_START = "entertainment_cooldown_start_ms";

    private static final String KEY_TIMER_RUNNING = "timer_running";
    private static final String KEY_TIMER_START_TIME = "timer_start_time_ms";
    private static final String KEY_TASK_DESCRIPTION = "task_description";

    private static final String KEY_ENTERTAIN_RUNNING = "entertain_running";
    private static final String KEY_ENTERTAIN_END_TIME = "entertain_end_time_ms";

    private static final String KEY_BLOCKING_ACTIVE = "blocking_active";
    private static final String KEY_LAST_BLOCKED_PACKAGE = "last_blocked_package";
    private static final String KEY_TASK_AUTO_CENTER_SECONDS = "task_auto_center_seconds";
    private static final String KEY_SHOWING_BLOCKER = "showing_blocker";
    private static final String KEY_APP_KILLED = "app_killed";

    private static final int DEFAULT_COOLDOWN_MINUTES = 60;
    private static final int ENTERTAINMENT_COOLDOWN_MINUTES = 60;
    private static final int MIN_COOLDOWN_MINUTES = 60;
    private static final int MAX_COOLDOWN_MINUTES = 180;
    private static final int COOLDOWN_STEP_MINUTES = 30;

    private static final int DEFAULT_AUTO_CENTER_SECONDS = 60;
    private static final int MIN_AUTO_CENTER_SECONDS = 30;
    private static final int MAX_AUTO_CENTER_SECONDS = 600;
    private static final int AUTO_CENTER_STEP_SECONDS = 30;

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getCooldownDurationMinutes() {
        return prefs.getInt(KEY_COOLDOWN_DURATION, DEFAULT_COOLDOWN_MINUTES);
    }

    public void setCooldownDurationMinutes(int minutes) {
        int clampedMinutes = Math.max(MIN_COOLDOWN_MINUTES, Math.min(MAX_COOLDOWN_MINUTES, minutes));
        prefs.edit().putInt(KEY_COOLDOWN_DURATION, clampedMinutes).apply();
    }

    public boolean increaseCooldownDuration() {
        int current = getCooldownDurationMinutes();
        if (current >= MAX_COOLDOWN_MINUTES) return false;
        int newValue = Math.min(MAX_COOLDOWN_MINUTES, current + COOLDOWN_STEP_MINUTES);
        setCooldownDurationMinutes(newValue);
        return true;
    }

    public boolean decreaseCooldownDuration() {
        int current = getCooldownDurationMinutes();
        if (current <= MIN_COOLDOWN_MINUTES) return false;
        int newValue = Math.max(MIN_COOLDOWN_MINUTES, current - COOLDOWN_STEP_MINUTES);
        setCooldownDurationMinutes(newValue);
        return true;
    }

    public int getMinCooldownMinutes() {
        return MIN_COOLDOWN_MINUTES;
    }

    public int getMaxCooldownMinutes() {
        return MAX_COOLDOWN_MINUTES;
    }

    public void startCooldown() {
        prefs.edit().putLong(KEY_COOLDOWN_START_TIME, System.currentTimeMillis()).apply();
    }

    public void clearCooldown() {
        prefs.edit().remove(KEY_COOLDOWN_START_TIME).apply();
    }

    public boolean isInCooldown() {
        long startTime = prefs.getLong(KEY_COOLDOWN_START_TIME, 0);
        if (startTime == 0) return false;
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed < getCooldownDurationMinutes() * 60 * 1000L;
    }

    public int getCooldownRemainingMinutes() {
        long startTime = prefs.getLong(KEY_COOLDOWN_START_TIME, 0);
        if (startTime == 0) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        long totalMs = getCooldownDurationMinutes() * 60 * 1000L;
        long remaining = totalMs - elapsed;
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 60000.0);
    }

    // 娱乐模式冷却时间
    public void startEntertainmentCooldown() {
        prefs.edit().putLong(KEY_ENTERTAINMENT_COOLDOWN_START, System.currentTimeMillis()).apply();
    }

    public boolean isInEntertainmentCooldown() {
        long startTime = prefs.getLong(KEY_ENTERTAINMENT_COOLDOWN_START, 0);
        if (startTime == 0) return false;
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed < getCooldownDurationMinutes() * 60 * 1000L;
    }
    
    public void clearEntertainmentCooldown() {
        prefs.edit().remove(KEY_ENTERTAINMENT_COOLDOWN_START).apply();
    }

    public int getEntertainmentCooldownRemainingMinutes() {
        long startTime = prefs.getLong(KEY_ENTERTAINMENT_COOLDOWN_START, 0);
        if (startTime == 0) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        long totalMs = getCooldownDurationMinutes() * 60 * 1000L;
        long remaining = totalMs - elapsed;
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 60000.0);
    }

    public void setTimerRunning(boolean running, String taskDesc) {
        prefs.edit()
            .putBoolean(KEY_TIMER_RUNNING, running)
            .putString(KEY_TASK_DESCRIPTION, running ? taskDesc : "")
            .putLong(KEY_TIMER_START_TIME, running ? System.currentTimeMillis() : 0)
            .apply();
    }

    public boolean isTimerRunning() {
        boolean running = prefs.getBoolean(KEY_TIMER_RUNNING, false);
        if (!running) return false;
        // 检查时间是否合理，如果时间已过很久，自动清理
        long startTime = prefs.getLong(KEY_TIMER_START_TIME, 0);
        // 如果开始时间是 0，说明是异常状态
        if (startTime == 0) {
            setTimerRunning(false, "");
            return false;
        }
        return true;
    }

    public String getTaskDescription() {
        return prefs.getString(KEY_TASK_DESCRIPTION, "");
    }

    public long getTimerStartTime() {
        return prefs.getLong(KEY_TIMER_START_TIME, 0);
    }

    public void setEntertainRunning(boolean running, long durationMs) {
        long endTime = running ? (System.currentTimeMillis() + durationMs) : 0;
        prefs.edit()
            .putBoolean(KEY_ENTERTAIN_RUNNING, running)
            .putLong(KEY_ENTERTAIN_END_TIME, endTime)
            .apply();
    }

    public boolean isEntertainRunning() {
        long endTime = prefs.getLong(KEY_ENTERTAIN_END_TIME, 0);
        if (!prefs.getBoolean(KEY_ENTERTAIN_RUNNING, false)) return false;
        if (System.currentTimeMillis() >= endTime) {
            setEntertainRunning(false, 0);
            return false;
        }
        return true;
    }

    public long getEntertainEndTime() {
        return prefs.getLong(KEY_ENTERTAIN_END_TIME, 0);
    }

    public void setBlockingActive(boolean active) {
        prefs.edit().putBoolean(KEY_BLOCKING_ACTIVE, active).apply();
    }

    public boolean isBlockingActive() {
        return prefs.getBoolean(KEY_BLOCKING_ACTIVE, false);
    }

    public void setLastBlockedPackage(String packageName) {
        prefs.edit().putString(KEY_LAST_BLOCKED_PACKAGE, packageName).apply();
    }

    public String getLastBlockedPackage() {
        return prefs.getString(KEY_LAST_BLOCKED_PACKAGE, "");
    }

    public void setShowingBlocker(boolean showing) {
        prefs.edit().putBoolean(KEY_SHOWING_BLOCKER, showing).apply();
    }

    public boolean isShowingBlocker() {
        return prefs.getBoolean(KEY_SHOWING_BLOCKER, false);
    }

    public void markAppKilled() {
        prefs.edit().putBoolean(KEY_APP_KILLED, true).apply();
    }

    public boolean wasAppKilled() {
        return prefs.getBoolean(KEY_APP_KILLED, false);
    }

    public void clearAppKilledFlag() {
        prefs.edit().putBoolean(KEY_APP_KILLED, false).apply();
    }

    public void resetAllStates() {
        prefs.edit()
            .putBoolean(KEY_TIMER_RUNNING, false)
            .putBoolean(KEY_ENTERTAIN_RUNNING, false)
            .putBoolean(KEY_BLOCKING_ACTIVE, false)
            .putBoolean(KEY_SHOWING_BLOCKER, false)
            .putString(KEY_TASK_DESCRIPTION, "")
            .putLong(KEY_TIMER_START_TIME, 0)
            .putLong(KEY_ENTERTAIN_END_TIME, 0)
            .apply();
    }

    public int getAutoCenterSeconds() {
        return prefs.getInt(KEY_TASK_AUTO_CENTER_SECONDS, DEFAULT_AUTO_CENTER_SECONDS);
    }

    public void setAutoCenterSeconds(int seconds) {
        int clampedSeconds = Math.max(MIN_AUTO_CENTER_SECONDS, Math.min(MAX_AUTO_CENTER_SECONDS, seconds));
        prefs.edit().putInt(KEY_TASK_AUTO_CENTER_SECONDS, clampedSeconds).apply();
    }

    public boolean increaseAutoCenterSeconds() {
        int current = getAutoCenterSeconds();
        if (current >= MAX_AUTO_CENTER_SECONDS) return false;
        int newValue = Math.min(MAX_AUTO_CENTER_SECONDS, current + AUTO_CENTER_STEP_SECONDS);
        setAutoCenterSeconds(newValue);
        return true;
    }

    public boolean decreaseAutoCenterSeconds() {
        int current = getAutoCenterSeconds();
        if (current <= MIN_AUTO_CENTER_SECONDS) return false;
        int newValue = Math.max(MIN_AUTO_CENTER_SECONDS, current - AUTO_CENTER_STEP_SECONDS);
        setAutoCenterSeconds(newValue);
        return true;
    }
}
