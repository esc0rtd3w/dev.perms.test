package dev.perms.test.settings;

import dev.perms.test.ui.PermsTestUiCompat;

/** Shared preference names and keys used by the Settings tab and related UI. */
public final class SettingsPreferenceKeys {
    private SettingsPreferenceKeys() {
    }

    public static final String PREFS = "perms_test";

    public static final String RUN_AS_LAUNCHER = "run_as_launcher";
    public static final String AUTO_RESTART_LAUNCHER = "auto_restart_launcher_toggle";
    public static final String PENDING_OPEN_HOME_SETTINGS = "pending_open_home_settings";

    public static final String ENABLE_FILE_OPEN_HANDLER = "enable_file_open_handler";
    public static final String HIDE_FILE_OPEN_UI = "hide_file_open_ui";
    public static final String SHOW_FILE_OPEN_DONE_OPEN = "show_file_open_done_open";
    public static final String CONFIRM_FILE_OPEN_INSTALL = "confirm_file_open_install";

    public static final String INSTALL_USE_ANDROID_DATA_PATH = "install_use_android_data_path";
    public static final String INSTALL_USE_STAGING_FOLDER = "install_use_staging_folder";
    public static final String INSTALL_SKIP_STAGING_LARGE_FILES = "install_skip_staging_large_files";
    public static final String INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK = "install_bypass_low_target_sdk_block";
    public static final String INSTALL_IGNORE_DEXOPT_PROFILE = "install_ignore_dexopt_profile";
    public static final String INSTALL_ALLOW_DOWNGRADE = "install_allow_downgrade";
    public static final String INSTALL_USE_INSTALLER_SCRIPT = "install_use_installer_script";

    public static final String FILES_OPEN_KNOWN_ON_TAP = "files_open_known_on_tap";
    public static final String FILES_INTERNAL_APK_INSTALL = "files_internal_apk_install";
    public static final String FILES_USE_SHIZUKU = "files_use_shizuku";
    public static final String SPLIT_APK_SHOW_WARNING_DIALOG = "split_apk_show_warning_dialog";
    public static final String CUSTOM_SPLIT_OPTIONS = "custom_split_options";
    public static final String USE_APP_PERMS_IN_DROPDOWN = "use_app_perms_in_dropdown";

    public static final String REMEMBER_OUTPUT_HEIGHT = "remember_output_height";
    public static final String OUTPUT_HEIGHT_PX = "output_height_px";
    public static final String OUTPUT_RESTORE_HEIGHT_PX = "output_restore_height_px";
    public static final String OUTPUT_MINIMIZED = "output_minimized";
    public static final String KEEP_BOTTOM_LOG_ABOVE_NAV_BAR = "keep_bottom_log_above_nav_bar";
    public static final String FAT_DROPDOWN_SCROLLBAR = "fat_dropdown_scrollbar";
    public static final String SAMSUNG_DROPDOWN_FIX = "samsung_dropdown_fix";
    public static final String ENABLE_COLLAPSIBLE_GROUPBOXES = "enable_collapsible_groupboxes";
    public static final String AUTO_COLLAPSE_GROUPBOXES = "auto_collapse_groupboxes";
    public static final String AUTO_COLLAPSE_GROUPBOX_PROFILE = "auto_collapse_groupbox_profile";
    public static final String DISABLE_TUTORIAL = "disable_tutorial";
    public static final String TUTORIAL_TAB_SEEN_PREFIX = "tutorial_seen_tab_";
    public static final String UI_DETECT_VR_MODE = PermsTestUiCompat.PREF_UI_DETECT_VR_MODE;
    public static final String ADJUST_UI_BASED_ON_DEVICE = PermsTestUiCompat.PREF_ADJUST_UI_BASED_ON_DEVICE;
    /** Legacy code alias; stored preference uses ADJUST_UI_BASED_ON_DEVICE. */
    public static final String UI_AUTO_DEVICE_LAYOUT = ADJUST_UI_BASED_ON_DEVICE;
    public static final String DEBUG_OUTPUT = "debug_mode";
    public static final String CLEAR_CACHE_ON_STARTUP = "clear_cache_on_startup";
    public static final String TRUNCATE_SHELL_OUTPUT = "truncate_shell_output";
    public static final String ENABLE_MULTIPLAYER_LINK = "enable_multiplayer_link";
    public static final String ENABLE_ROOT_FEATURES = "enable_root_features";
    public static final String ENABLE_FLOATING_PANELS = "enable_floating_panels";
    public static final String HOME_POPOUT_FULL_WINDOW = "home_popout_full_window";
    public static final String AUTOMATIC_DEVICE_DETECTION = "automatic_device_detection";
    public static final String DEVICE_DEFAULT_VR_MODE_USER_SET = "device_default_vr_mode_user_set";
    public static final String DEVICE_DEFAULT_DISABLE_OVERLAYS_USER_SET = "device_default_disable_overlays_user_set";
    public static final String DEVICE_DEFAULT_FLOATING_PANELS_USER_SET = "device_default_floating_panels_user_set";
    public static final String DEVICE_PROFILE_LAST = "device_profile_last";
    public static final String DEVICE_MODEL_LAST = "device_model_last";
    public static final String DEVICE_SERIAL_LAST = "device_serial_last";
    public static final String FUNNY_ANIMATION_TOOLTIPS = "funny_animation_tooltips";
    public static final String RANDOMIZE_ANIMATION_CATEGORY = "randomize_animation_category";
    public static final String RANDOMIZE_CURRENT_TOOLTIPS = "randomize_current_tooltips";
    public static final String RANDOMIZE_ALL_TOOLTIPS = "randomize_all_tooltips";
    public static final String ANIMATION_CATEGORY = "animation_category";
    public static final String HIDE_MAIN_PAGE_BANNER = "hide_main_page_banner";
    public static final String THEME_COLORS = "theme_colors";
    public static final String THEME_CUSTOM_COLOR = "theme_custom_color";
    public static final String THEME_CUSTOM_GRADIENT = "theme_custom_gradient";
    public static final String THEME_COLOR_NAVIGATION_TABS = "theme_color_navigation_tabs";
    public static final String THEME_GRADIENT_DEFAULT_RESET_APPLIED = "theme_gradient_default_reset_20260526_05";
    public static final String UPDATE_CUSTOM_SERVER_ENABLED = "update_custom_server_enabled";
    public static final String UPDATE_CUSTOM_SERVER_URL = "update_custom_server_url";
    public static final String UPDATE_INCLUDE_PRERELEASES = "update_include_prereleases";
    public static final String UPDATE_ALLOW_DOWNGRADE = "update_allow_downgrade";
    public static final String UPDATE_AUTO_ENABLED = "update_auto_enabled";
    public static final String UPDATE_AUTO_CHANNEL_PRERELEASE = "update_auto_channel_prerelease";
    public static final String UPDATE_SILENT = "update_silent";
    public static final String UPDATE_LAST_AUTO_CHECK_MS = "update_last_auto_check_ms";
    public static final String UI_VR_DEFAULT_RESET_APPLIED = "ui_vr_default_reset_20260510_02";

    public static final String COLORIZE_APP_DROPDOWN = "colorize_app_dropdown";

    public static final String FILES_LAST_LEFT_CWD = "files_last_left_cwd";
    public static final String FILES_LAST_RIGHT_CWD = "files_last_right_cwd";
    public static final String FILES_LAST_SPLIT = "files_last_split";
    public static final String FILES_LAST_ACTIVE_RIGHT = "files_last_active_right";
}
