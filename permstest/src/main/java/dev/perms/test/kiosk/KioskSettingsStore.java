package dev.perms.test.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.perms.test.settings.SettingsPreferenceKeys;

/** Persistent storage for optional Kiosk Mode settings. */
public final class KioskSettingsStore {
    private final SharedPreferences prefs;

    public KioskSettingsStore(Context context) {
        this(context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE));
    }

    public KioskSettingsStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public boolean isKioskEnabled() {
        return prefs.getBoolean(KioskPrefs.KIOSK_MODE, false) && !KioskSafety.isKioskForceDisabled();
    }

    public boolean isKioskModeRequested() {
        return prefs.getBoolean(KioskPrefs.KIOSK_MODE, false);
    }

    public boolean isKioskForceDisabled() {
        return KioskSafety.isKioskForceDisabled();
    }

    public boolean isLauncherForceDisabled() {
        return KioskSafety.isLauncherForceDisabled();
    }

    public void setKioskEnabled(boolean enabled) {
        prefs.edit().putBoolean(KioskPrefs.KIOSK_MODE, enabled && !KioskSafety.isKioskForceDisabled()).apply();
    }

    public String password() {
        return prefs.getString(KioskPrefs.PASSWORD, "");
    }

    public void setPassword(String password) {
        prefs.edit().putString(KioskPrefs.PASSWORD, password == null ? "" : password).apply();
    }

    public int iconSizeDp() {
        return clampIconSize(prefs.getInt(KioskPrefs.ICON_SIZE_DP, KioskPrefs.DEFAULT_ICON_SIZE_DP));
    }

    public void setIconSizeDp(int sizeDp) {
        prefs.edit().putInt(KioskPrefs.ICON_SIZE_DP, clampIconSize(sizeDp)).apply();
    }

    public String exitPattern() {
        String value = prefs.getString(KioskPrefs.EXIT_PATTERN, KioskPrefs.DEFAULT_EXIT_PATTERN);
        return TextUtils.isEmpty(value) ? KioskPrefs.DEFAULT_EXIT_PATTERN : value;
    }

    public void setExitPattern(String pattern) {
        prefs.edit().putString(KioskPrefs.EXIT_PATTERN, TextUtils.isEmpty(pattern) ? KioskPrefs.DEFAULT_EXIT_PATTERN : pattern.trim()).apply();
    }

    public boolean timerRefreshEnabled() {
        return prefs.getBoolean(KioskPrefs.TIMER_REFRESH_ENABLED, false);
    }

    public void setTimerRefreshEnabled(boolean enabled) {
        prefs.edit().putBoolean(KioskPrefs.TIMER_REFRESH_ENABLED, enabled).apply();
    }

    public int timerRefreshMinutes() {
        int v = prefs.getInt(KioskPrefs.TIMER_REFRESH_MINUTES, KioskPrefs.DEFAULT_TIMER_REFRESH_MINUTES);
        return Math.max(1, Math.min(240, v));
    }

    public void setTimerRefreshMinutes(int minutes) {
        prefs.edit().putInt(KioskPrefs.TIMER_REFRESH_MINUTES, Math.max(1, Math.min(240, minutes))).apply();
    }

    public boolean hideStatusBar() {
        return prefs.getBoolean(KioskPrefs.HIDE_STATUS_BAR, true);
    }

    public void setHideStatusBar(boolean enabled) {
        prefs.edit().putBoolean(KioskPrefs.HIDE_STATUS_BAR, enabled).apply();
    }

    public boolean lockTaskEnabled() {
        return prefs.getBoolean(KioskPrefs.LOCK_TASK_MODE, true);
    }

    public void setLockTaskEnabled(boolean enabled) {
        prefs.edit().putBoolean(KioskPrefs.LOCK_TASK_MODE, enabled).apply();
    }

    public boolean hardwareButtonBypassEnabled() {
        return prefs.getBoolean(KioskPrefs.HARDWARE_BUTTON_BYPASS, false);
    }

    public void setHardwareButtonBypassEnabled(boolean enabled) {
        prefs.edit().putBoolean(KioskPrefs.HARDWARE_BUTTON_BYPASS, enabled).apply();
    }

    public boolean autoSizeIcons() {
        return prefs.getBoolean(KioskPrefs.AUTO_SIZE_ICONS, false);
    }

    public void setAutoSizeIcons(boolean enabled) {
        prefs.edit().putBoolean(KioskPrefs.AUTO_SIZE_ICONS, enabled).apply();
    }

    public boolean showLabels() {
        return prefs.getBoolean(KioskPrefs.SHOW_LABELS, true);
    }

    public void setShowLabels(boolean enabled) {
        prefs.edit().putBoolean(KioskPrefs.SHOW_LABELS, enabled).apply();
    }

    public List<KioskAllowedItem> loadAllowedItems() {
        ArrayList<KioskAllowedItem> out = new ArrayList<>();
        String raw = prefs.getString(KioskPrefs.ALLOWED_ITEMS_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(TextUtils.isEmpty(raw) ? "[]" : raw);
            Map<String, KioskAllowedItem> dedupe = new LinkedHashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                KioskAllowedItem item = KioskAllowedItem.fromJson(arr.optJSONObject(i));
                if (item == null || TextUtils.isEmpty(item.id)) continue;
                dedupe.put(item.stableKey(), item);
            }
            out.addAll(dedupe.values());
        } catch (Throwable ignored) {
        }
        return out;
    }

    public void saveAllowedItems(List<KioskAllowedItem> items) {
        JSONArray arr = new JSONArray();
        if (items != null) {
            Map<String, KioskAllowedItem> dedupe = new LinkedHashMap<>();
            for (KioskAllowedItem item : items) {
                if (item == null || TextUtils.isEmpty(item.id)) continue;
                dedupe.put(item.stableKey(), item);
            }
            for (KioskAllowedItem item : dedupe.values()) {
                try {
                    arr.put(item.toJson());
                } catch (Throwable ignored) {
                }
            }
        }
        prefs.edit().putString(KioskPrefs.ALLOWED_ITEMS_JSON, arr.toString()).apply();
    }

    public void addOrUpdate(KioskAllowedItem item) {
        if (item == null || TextUtils.isEmpty(item.id)) return;
        List<KioskAllowedItem> items = loadAllowedItems();
        for (KioskAllowedItem existing : items) {
            if (existing.stableKey().equals(item.stableKey())) {
                existing.label = item.label;
                existing.enabled = item.enabled;
                saveAllowedItems(items);
                return;
            }
        }
        items.add(item);
        saveAllowedItems(items);
    }

    public int enabledAllowedItemCount() {
        int count = 0;
        for (KioskAllowedItem item : loadAllowedItems()) {
            if (item != null && item.enabled) count++;
        }
        return count;
    }

    public boolean hasEnabledAllowedItems() {
        return enabledAllowedItemCount() > 0;
    }

    public void disableKioskIfNoEnabledItems() {
        if (!hasEnabledAllowedItems()) {
            setKioskEnabled(false);
        }
    }

    private static int clampIconSize(int value) {
        return Math.max(KioskPrefs.MIN_ICON_SIZE_DP, Math.min(KioskPrefs.MAX_ICON_SIZE_DP, value));
    }
}
