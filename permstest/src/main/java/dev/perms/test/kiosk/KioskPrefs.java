package dev.perms.test.kiosk;

/** Preference keys and defaults for the optional Kiosk Mode launcher surface. */
public final class KioskPrefs {
    private KioskPrefs() {
    }

    public static final String KIOSK_MODE = "kiosk_mode_enabled";
    public static final String PASSWORD = "kiosk_password";
    public static final String ICON_SIZE_DP = "kiosk_icon_size_dp";
    public static final String ALLOWED_ITEMS_JSON = "kiosk_allowed_items_json";
    public static final String EXIT_PATTERN = "kiosk_exit_pattern";
    public static final String TIMER_REFRESH_ENABLED = "kiosk_timer_refresh_enabled";
    public static final String TIMER_REFRESH_MINUTES = "kiosk_timer_refresh_minutes";
    public static final String HIDE_STATUS_BAR = "kiosk_hide_status_bar";
    public static final String LOCK_TASK_MODE = "kiosk_lock_task_mode";
    public static final String HARDWARE_BUTTON_BYPASS = "kiosk_hardware_button_bypass";
    public static final String AUTO_SIZE_ICONS = "kiosk_auto_size_icons";
    public static final String SHOW_LABELS = "kiosk_show_labels";

    public static final int DEFAULT_ICON_SIZE_DP = 96;
    public static final int MIN_ICON_SIZE_DP = 48;
    public static final int MAX_ICON_SIZE_DP = 192;
    public static final int MAX_AUTO_ICON_SIZE_DP = 320;
    public static final String DEFAULT_EXIT_PATTERN = "TL:3,TR:1,BL:4,BR:2";
    public static final int DEFAULT_TIMER_REFRESH_MINUTES = 5;
}
