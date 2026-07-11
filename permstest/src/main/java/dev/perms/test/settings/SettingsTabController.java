package dev.perms.test.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;


import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.device.DeviceDetection;
import dev.perms.test.memory.MemoryOptionsBinder;
import dev.perms.test.memory.MemoryToolHelper;
import dev.perms.test.startup.StartupLoadingTextPool;
import dev.perms.test.settings.backup.SettingsBackupController;
import dev.perms.test.tutorial.TutorialController;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.GroupboxCollapseProfiles;
import dev.perms.test.ui.ThemeColorController;
import dev.perms.test.ui.PermsTestUiCompat;
import dev.perms.test.ui.panel.PermsTestPanelSettings;

/** Binds the simple Settings tab preferences to their checkbox controls. */
public final class SettingsTabController {
    private SettingsTabController() {
    }

    public interface Host {
        int getCurrentOutputHeightPx();
        void applyDeviceUiProfile();
        void applySamsungDropdownFix(boolean enabled);
        void applyThemeColors();
        void applyBottomLogNavigationBarPadding();
        void applyCollapsibleGroupboxes();
        void onRootFeaturesChanged(boolean enabled);
        void resolveDeviceSerial(SerialResultCallback callback);
    }

    public interface SerialResultCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    private static boolean serialProbeInFlight;
    private static long lastSerialProbeMs;
    private static boolean syncingDeviceDefaultSettings;

    public static void bind(ActivityMainBinding binding, SharedPreferences sp, Host host) {
        Context context = null;
        try { context = binding == null || binding.getRoot() == null ? null : binding.getRoot().getContext(); } catch (Throwable ignored) {}
        bind(context, binding, sp, host);
    }

    public static void bind(Context context, ActivityMainBinding binding, SharedPreferences sp, Host host) {
        if (binding == null || sp == null) return;

        try {
            updateDeviceProfileInfo(context, binding, sp, host);
            bindSettingsBackupControls(context, binding, sp, host);

            if (binding.tabSettings.chkRememberOutputHeight != null) {
                binding.tabSettings.chkRememberOutputHeight.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.REMEMBER_OUTPUT_HEIGHT, true));
                binding.tabSettings.chkRememberOutputHeight.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.REMEMBER_OUTPUT_HEIGHT, checked).apply();
                    if (!checked) {
                        sp.edit()
                                .remove(SettingsPreferenceKeys.OUTPUT_HEIGHT_PX)
                                .remove(SettingsPreferenceKeys.OUTPUT_RESTORE_HEIGHT_PX)
                                .remove(SettingsPreferenceKeys.OUTPUT_MINIMIZED)
                                .apply();
                    } else if (host != null && host.getCurrentOutputHeightPx() > 0) {
                        sp.edit()
                                .putInt(SettingsPreferenceKeys.OUTPUT_HEIGHT_PX, host.getCurrentOutputHeightPx())
                                .putBoolean(SettingsPreferenceKeys.OUTPUT_MINIMIZED, false)
                                .apply();
                    }
                });
            }

            if (binding.tabSettings.chkKeepBottomLogAboveNavBar != null) {
                binding.tabSettings.chkKeepBottomLogAboveNavBar.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.KEEP_BOTTOM_LOG_ABOVE_NAV_BAR, true));
                binding.tabSettings.chkKeepBottomLogAboveNavBar.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.KEEP_BOTTOM_LOG_ABOVE_NAV_BAR, checked).apply();
                    if (host != null) host.applyBottomLogNavigationBarPadding();
                });
            }

            if (binding.tabSettings.chkAutomaticDeviceDetection != null) {
                binding.tabSettings.chkAutomaticDeviceDetection.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.AUTOMATIC_DEVICE_DETECTION, true));
                binding.tabSettings.chkAutomaticDeviceDetection.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.AUTOMATIC_DEVICE_DETECTION, checked).apply();
                    if (checked && context != null) {
                        DeviceDetection.applyAutomaticProfile(context, true);
                        syncingDeviceDefaultSettings = true;
                        try {
                            syncAutomaticMemoryDefaults(binding, sp);
                            if (binding.tabSettings.chkDetectVrMode != null) {
                                binding.tabSettings.chkDetectVrMode.setChecked(
                                        sp.getBoolean(SettingsPreferenceKeys.UI_DETECT_VR_MODE, false));
                            }
                            updatePanelHostCheckbox(context, binding, sp);
                        } finally {
                            syncingDeviceDefaultSettings = false;
                        }
                    }
                    updateDeviceProfileInfo(context, binding, sp, host);
                    if (host != null) host.applyDeviceUiProfile();
                });
            }

            if (binding.tabSettings.chkDetectVrMode != null) {
                binding.tabSettings.chkDetectVrMode.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.UI_DETECT_VR_MODE, false));
                binding.tabSettings.chkDetectVrMode.setOnCheckedChangeListener((btn, checked) -> {
                    SharedPreferences.Editor editor = sp.edit()
                            .putBoolean(SettingsPreferenceKeys.UI_DETECT_VR_MODE, checked);
                    if (!syncingDeviceDefaultSettings) {
                        editor.putBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_VR_MODE_USER_SET, true);
                    }
                    editor.apply();
                    updateDeviceProfileInfo(context, binding, sp, host);
                    updatePanelHostCheckbox(context, binding, sp);
                    if (host != null) host.applyDeviceUiProfile();
                });
            }

            bindPanelHostCheckbox(context, binding, sp);

            if (binding.tabSettings.chkDebugMode != null) {
                binding.tabSettings.chkDebugMode.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.DEBUG_OUTPUT, false));
                binding.tabSettings.chkDebugMode.setOnCheckedChangeListener((btn, checked) ->
                        sp.edit().putBoolean(SettingsPreferenceKeys.DEBUG_OUTPUT, checked).apply());
            }

            if (binding.tabSettings.chkClearCacheOnStartup != null) {
                binding.tabSettings.chkClearCacheOnStartup.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.CLEAR_CACHE_ON_STARTUP, true));
                binding.tabSettings.chkClearCacheOnStartup.setOnCheckedChangeListener((btn, checked) ->
                        sp.edit().putBoolean(SettingsPreferenceKeys.CLEAR_CACHE_ON_STARTUP, checked).apply());
            }

            if (binding.tabSettings.chkTruncateShellOutput != null) {
                binding.tabSettings.chkTruncateShellOutput.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.TRUNCATE_SHELL_OUTPUT, true));
                binding.tabSettings.chkTruncateShellOutput.setOnCheckedChangeListener((btn, checked) ->
                        sp.edit().putBoolean(SettingsPreferenceKeys.TRUNCATE_SHELL_OUTPUT, checked).apply());
            }

            if (binding.tabSettings.chkDisableTutorial != null) {
                binding.tabSettings.chkDisableTutorial.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.DISABLE_TUTORIAL, false));
                binding.tabSettings.chkDisableTutorial.setOnCheckedChangeListener((btn, checked) ->
                        sp.edit().putBoolean(SettingsPreferenceKeys.DISABLE_TUTORIAL, checked).apply());
            }
            bindTutorialResetControl(context, binding, sp);

            if (binding.tabSettings.chkEnableMultiplayerLink != null) {
                binding.tabSettings.chkEnableMultiplayerLink.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.ENABLE_MULTIPLAYER_LINK, false));
                binding.tabSettings.chkEnableMultiplayerLink.setOnCheckedChangeListener((btn, checked) ->
                        sp.edit().putBoolean(SettingsPreferenceKeys.ENABLE_MULTIPLAYER_LINK, checked).apply());
            }

            if (binding.tabSettings.chkEnableRootFeatures != null) {
                boolean enabled = sp.getBoolean(SettingsPreferenceKeys.ENABLE_ROOT_FEATURES, false);
                binding.tabSettings.chkEnableRootFeatures.setChecked(enabled);
                if (host != null) host.onRootFeaturesChanged(enabled);
                binding.tabSettings.chkEnableRootFeatures.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.ENABLE_ROOT_FEATURES, checked).apply();
                    if (host != null) host.onRootFeaturesChanged(checked);
                    if (context != null) {
                        Toast.makeText(context, checked
                                ? "Root features enabled. Root is requested only when a root action is tapped."
                                : "Root features disabled.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (binding.tabSettings.chkFunnyAnimationTooltips != null) {
                binding.tabSettings.chkFunnyAnimationTooltips.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.FUNNY_ANIMATION_TOOLTIPS, false));
                binding.tabSettings.chkFunnyAnimationTooltips.setOnCheckedChangeListener((btn, checked) ->
                        sp.edit().putBoolean(SettingsPreferenceKeys.FUNNY_ANIMATION_TOOLTIPS, checked).apply());
            }

            bindAnimationCategoryControls(binding, sp);

            if (binding.tabSettings.chkAutoDeviceUi != null) {
                binding.tabSettings.chkAutoDeviceUi.setChecked(
                        PermsTestUiCompat.isAdjustUiBasedOnDeviceEnabled(context));
                binding.tabSettings.chkAutoDeviceUi.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.ADJUST_UI_BASED_ON_DEVICE, checked).apply();
                    if (host != null) host.applyDeviceUiProfile();
                });
            }

            applyMainBannerVisibility(binding, sp);
            if (binding.tabSettings.chkHideMainPageBanner != null) {
                binding.tabSettings.chkHideMainPageBanner.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.HIDE_MAIN_PAGE_BANNER, true));
                binding.tabSettings.chkHideMainPageBanner.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.HIDE_MAIN_PAGE_BANNER, checked).apply();
                    applyMainBannerVisibility(binding, sp);
                });
            }

            if (binding.tabSettings.chkFatDropdownScrollbar != null) {
                binding.tabSettings.chkFatDropdownScrollbar.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.FAT_DROPDOWN_SCROLLBAR, true));
                binding.tabSettings.chkFatDropdownScrollbar.setOnCheckedChangeListener((btn, checked) ->
                        sp.edit().putBoolean(SettingsPreferenceKeys.FAT_DROPDOWN_SCROLLBAR, checked).apply());
            }

            if (binding.tabSettings.chkSamsungDropdownFix != null) {
                boolean enabled = sp.getBoolean(SettingsPreferenceKeys.SAMSUNG_DROPDOWN_FIX, true);
                binding.tabSettings.chkSamsungDropdownFix.setChecked(enabled);
                if (host != null) host.applySamsungDropdownFix(enabled);
                binding.tabSettings.chkSamsungDropdownFix.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.SAMSUNG_DROPDOWN_FIX, checked).apply();
                    if (host != null) host.applySamsungDropdownFix(checked);
                });
            }

            if (binding.tabSettings.chkEnableCollapsibleGroupboxes != null) {
                boolean enabled = sp.getBoolean(SettingsPreferenceKeys.ENABLE_COLLAPSIBLE_GROUPBOXES, true);
                binding.tabSettings.chkEnableCollapsibleGroupboxes.setChecked(enabled);
                binding.tabSettings.chkEnableCollapsibleGroupboxes.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.ENABLE_COLLAPSIBLE_GROUPBOXES, checked).apply();
                    if (host != null) host.applyCollapsibleGroupboxes();
                });
            }

            bindGroupboxAutoCollapseControls(binding, sp, host);

            bindThemeColors(context, binding, sp, host);
        } catch (Throwable ignored) {
        }
    }

    private static void bindSettingsBackupControls(Context context, ActivityMainBinding binding, SharedPreferences sp, Host host) {
        try {
            SettingsBackupController.bind(context, binding, sp, () -> {
                try {
                    bind(context, binding, sp, host);
                    if (host != null) {
                        host.applyDeviceUiProfile();
                        host.applySamsungDropdownFix(sp.getBoolean(SettingsPreferenceKeys.SAMSUNG_DROPDOWN_FIX, true));
                        host.applyThemeColors();
                        host.applyBottomLogNavigationBarPadding();
                        host.applyCollapsibleGroupboxes();
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void bindTutorialResetControl(Context context, ActivityMainBinding binding, SharedPreferences sp) {
        try {
            if (binding == null || binding.tabSettings == null || binding.tabSettings.btnResetTutorial == null || sp == null) return;
            binding.tabSettings.btnResetTutorial.setOnClickListener(v -> {
                Runnable reset = () -> {
                    TutorialController.resetProgress(sp);
                    try {
                        if (binding.tabSettings.chkDisableTutorial != null) {
                            binding.tabSettings.chkDisableTutorial.setChecked(false);
                        }
                    } catch (Throwable ignored) {
                    }
                    try { Toast.makeText(context, "Tutorial reset", Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
                };
                if (context != null) {
                    new AlertDialog.Builder(context)
                            .setTitle("Reset Tutorial?")
                            .setMessage("This clears the tutorial seen flags and re-enables Tutorial Mode so tips can show again on next startup or first tab visit.")
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton("Reset", (dialog, which) -> reset.run())
                            .show();
                } else {
                    reset.run();
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void bindGroupboxAutoCollapseControls(ActivityMainBinding binding, SharedPreferences sp, Host host) {
        try {
            if (binding == null || binding.tabSettings == null || sp == null) return;
            boolean enabled = sp.getBoolean(SettingsPreferenceKeys.AUTO_COLLAPSE_GROUPBOXES, false);
            String profile = GroupboxCollapseProfiles.normalizeProfile(
                    sp.getString(SettingsPreferenceKeys.AUTO_COLLAPSE_GROUPBOX_PROFILE,
                            GroupboxCollapseProfiles.defaultProfile()));

            if (binding.tabSettings.chkAutoCollapseGroupboxes != null) {
                binding.tabSettings.chkAutoCollapseGroupboxes.setOnCheckedChangeListener(null);
                binding.tabSettings.chkAutoCollapseGroupboxes.setChecked(enabled);
                binding.tabSettings.chkAutoCollapseGroupboxes.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.AUTO_COLLAPSE_GROUPBOXES, checked).apply();
                    applyGroupboxAutoCollapseDropdownState(binding, checked);
                    if (host != null) host.applyCollapsibleGroupboxes();
                });
            }

            if (binding.tabSettings.ddAutoCollapseGroupboxProfile != null) {
                String[] labels = GroupboxCollapseProfiles.labels();
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        binding.tabSettings.ddAutoCollapseGroupboxProfile.getContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        labels) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        return padAutoCollapseProfileRow(super.getView(position, convertView, parent));
                    }

                    @Override
                    public View getDropDownView(int position, View convertView, ViewGroup parent) {
                        return padAutoCollapseProfileRow(super.getDropDownView(position, convertView, parent));
                    }
                };
                binding.tabSettings.ddAutoCollapseGroupboxProfile.setAdapter(adapter);
                configureAutoCollapseProfileDropdown(binding, labels);
                binding.tabSettings.ddAutoCollapseGroupboxProfile.setText(
                        GroupboxCollapseProfiles.labelForKey(profile), false);
                binding.tabSettings.ddAutoCollapseGroupboxProfile.setOnItemClickListener((parent, view, position, id) -> {
                    Object item = parent == null ? null : parent.getItemAtPosition(position);
                    String selected = GroupboxCollapseProfiles.keyForLabel(item == null ? null : item.toString());
                    sp.edit().putString(SettingsPreferenceKeys.AUTO_COLLAPSE_GROUPBOX_PROFILE, selected).apply();
                    if (host != null && sp.getBoolean(SettingsPreferenceKeys.AUTO_COLLAPSE_GROUPBOXES, false)) {
                        host.applyCollapsibleGroupboxes();
                    }
                });
                binding.tabSettings.ddAutoCollapseGroupboxProfile.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        String selected = GroupboxCollapseProfiles.keyForLabel(
                                binding.tabSettings.ddAutoCollapseGroupboxProfile.getText() == null
                                        ? null : binding.tabSettings.ddAutoCollapseGroupboxProfile.getText().toString());
                        binding.tabSettings.ddAutoCollapseGroupboxProfile.setText(
                                GroupboxCollapseProfiles.labelForKey(selected), false);
                        sp.edit().putString(SettingsPreferenceKeys.AUTO_COLLAPSE_GROUPBOX_PROFILE, selected).apply();
                    }
                });
                applyGroupboxAutoCollapseDropdownState(binding, enabled);
            }
        } catch (Throwable ignored) {
        }
    }

    private static View padAutoCollapseProfileRow(View row) {
        try {
            if (row instanceof TextView) {
                TextView text = (TextView) row;
                int start = dp(text.getContext(), 18);
                int end = dp(text.getContext(), 24);
                text.setPadding(start, text.getPaddingTop(), end, text.getPaddingBottom());
            }
        } catch (Throwable ignored) {
        }
        return row;
    }

    private static void configureAutoCollapseProfileDropdown(ActivityMainBinding binding, String[] labels) {
        try {
            if (binding == null || binding.tabSettings == null
                    || binding.tabSettings.ddAutoCollapseGroupboxProfile == null) return;
            Context context = binding.tabSettings.ddAutoCollapseGroupboxProfile.getContext();
            DropdownUi.prepareExposedDropdown(binding.tabSettings.layoutAutoCollapseGroupboxProfile,
                    binding.tabSettings.ddAutoCollapseGroupboxProfile);
            int start = dp(context, 18);
            int end = dp(context, 54);
            binding.tabSettings.ddAutoCollapseGroupboxProfile.setPadding(
                    start,
                    binding.tabSettings.ddAutoCollapseGroupboxProfile.getPaddingTop(),
                    end,
                    binding.tabSettings.ddAutoCollapseGroupboxProfile.getPaddingBottom());
            binding.tabSettings.ddAutoCollapseGroupboxProfile.setDropDownHorizontalOffset(0);
            binding.tabSettings.ddAutoCollapseGroupboxProfile.post(() -> {
                try {
                    int widest = 0;
                    if (labels != null) {
                        for (String label : labels) {
                            if (label == null) continue;
                            widest = Math.max(widest, (int) Math.ceil(
                                    binding.tabSettings.ddAutoCollapseGroupboxProfile.getPaint().measureText(label)));
                        }
                    }
                    int desiredWidth = Math.max(dp(context, 176), widest + dp(context, 132));
                    applyViewWidth(binding.tabSettings.layoutAutoCollapseGroupboxProfile, desiredWidth);
                    applyViewWidth(binding.tabSettings.ddAutoCollapseGroupboxProfile, desiredWidth);
                    binding.tabSettings.ddAutoCollapseGroupboxProfile.setDropDownWidth(desiredWidth);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void applyGroupboxAutoCollapseDropdownState(ActivityMainBinding binding, boolean enabled) {
        try {
            if (binding == null || binding.tabSettings == null
                    || binding.tabSettings.ddAutoCollapseGroupboxProfile == null) return;
            binding.tabSettings.ddAutoCollapseGroupboxProfile.setEnabled(enabled);
            binding.tabSettings.ddAutoCollapseGroupboxProfile.setAlpha(enabled ? 1.0f : 0.55f);
            if (binding.tabSettings.layoutAutoCollapseGroupboxProfile != null) {
                binding.tabSettings.layoutAutoCollapseGroupboxProfile.setEnabled(enabled);
                binding.tabSettings.layoutAutoCollapseGroupboxProfile.setAlpha(enabled ? 1.0f : 0.55f);
            }
        } catch (Throwable ignored) {
        }
    }



    private static void bindThemeColors(Context context, ActivityMainBinding binding, SharedPreferences sp, Host host) {
        try {
            if (binding == null || sp == null) return;
            String theme = sp.getString(SettingsPreferenceKeys.THEME_COLORS, ThemeColorController.THEME_BLACK);
            boolean custom = ThemeColorController.THEME_CUSTOM.equals(theme);
            int selectedColor = sp.getInt(SettingsPreferenceKeys.THEME_CUSTOM_COLOR, ThemeColorController.DEFAULT_CUSTOM_COLOR);
            boolean gradient = sp.getBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT, false);

            if (binding.tabSettings.radioThemeBlack != null) {
                binding.tabSettings.radioThemeBlack.setChecked(!custom);
                binding.tabSettings.radioThemeBlack.setOnClickListener(v -> {
                    sp.edit().putString(SettingsPreferenceKeys.THEME_COLORS, ThemeColorController.THEME_BLACK).apply();
                    bindThemeColors(context, binding, sp, host);
                    if (host != null) host.applyThemeColors();
                });
            }
            if (binding.tabSettings.radioThemeCustom != null) {
                binding.tabSettings.radioThemeCustom.setChecked(custom);
                binding.tabSettings.radioThemeCustom.setOnClickListener(v -> {
                    sp.edit()
                            .putString(SettingsPreferenceKeys.THEME_COLORS, ThemeColorController.THEME_CUSTOM)
                            .putBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT,
                                    sp.getBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT, false))
                            .apply();
                    bindThemeColors(context, binding, sp, host);
                    if (host != null) host.applyThemeColors();
                });
            }
            if (binding.tabSettings.chkThemeGradient != null) {
                binding.tabSettings.chkThemeGradient.setOnCheckedChangeListener(null);
                binding.tabSettings.chkThemeGradient.setEnabled(custom);
                binding.tabSettings.chkThemeGradient.setChecked(custom && gradient);
                binding.tabSettings.chkThemeGradient.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit()
                            .putString(SettingsPreferenceKeys.THEME_COLORS, ThemeColorController.THEME_CUSTOM)
                            .putBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT, checked)
                            .apply();
                    bindThemeColors(context, binding, sp, host);
                    if (host != null) host.applyThemeColors();
                });
            }

            if (binding.tabSettings.chkThemeColorNavigationTabs != null) {
                binding.tabSettings.chkThemeColorNavigationTabs.setOnCheckedChangeListener(null);
                binding.tabSettings.chkThemeColorNavigationTabs.setChecked(
                        sp.getBoolean(SettingsPreferenceKeys.THEME_COLOR_NAVIGATION_TABS, true));
                binding.tabSettings.chkThemeColorNavigationTabs.setOnCheckedChangeListener((btn, checked) -> {
                    sp.edit().putBoolean(SettingsPreferenceKeys.THEME_COLOR_NAVIGATION_TABS, checked).apply();
                    if (host != null) host.applyThemeColors();
                });
            }

            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchBlack, sp, host, selectedColor, ThemeColorController.COLOR_SOLID_BLACK);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchBlue, sp, host, selectedColor, ThemeColorController.COLOR_BLUE);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchCyan, sp, host, selectedColor, ThemeColorController.COLOR_CYAN);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchTeal, sp, host, selectedColor, ThemeColorController.COLOR_TEAL);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchPurple, sp, host, selectedColor, ThemeColorController.COLOR_PURPLE);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchPink, sp, host, selectedColor, ThemeColorController.COLOR_PINK);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchGreen, sp, host, selectedColor, ThemeColorController.COLOR_GREEN);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchAmber, sp, host, selectedColor, ThemeColorController.COLOR_AMBER);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchOrange, sp, host, selectedColor, ThemeColorController.COLOR_ORANGE);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchRed, sp, host, selectedColor, ThemeColorController.COLOR_RED);
            bindThemeSwatch(context, binding, binding.tabSettings.viewThemeSwatchSlate, sp, host, selectedColor, ThemeColorController.COLOR_SLATE);
        } catch (Throwable ignored) {
        }
    }

    private static void bindThemeSwatch(Context context, ActivityMainBinding binding, View swatch, SharedPreferences sp, Host host,
                                        int selectedColor, int color) {
        if (swatch == null || sp == null) return;
        boolean selected = colorsEqual(selectedColor, color)
                && ThemeColorController.THEME_CUSTOM.equals(sp.getString(SettingsPreferenceKeys.THEME_COLORS, ThemeColorController.THEME_BLACK));
        try { swatch.setBackground(ThemeColorController.swatchDrawable(context, color, selected)); } catch (Throwable ignored) {}
        swatch.setOnClickListener(v -> {
            sp.edit()
                    .putString(SettingsPreferenceKeys.THEME_COLORS, ThemeColorController.THEME_CUSTOM)
                    .putInt(SettingsPreferenceKeys.THEME_CUSTOM_COLOR, color)
                    .putBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT,
                            sp.getBoolean(SettingsPreferenceKeys.THEME_CUSTOM_GRADIENT, false))
                    .apply();
            bindThemeColors(context, binding, sp, host);
            if (host != null) host.applyThemeColors();
        });
    }

    private static boolean colorsEqual(int a, int b) {
        return (a & 0x00FFFFFF) == (b & 0x00FFFFFF);
    }


    private static void syncAutomaticMemoryDefaults(ActivityMainBinding binding, SharedPreferences sp) {
        try {
            if (binding == null || binding.tabMemory == null || sp == null) return;
            if (DeviceDetection.shouldUseWithoutPtraceForOs()
                    && binding.tabMemory.chkMemoryWithoutPtrace != null) {
                binding.tabMemory.chkMemoryWithoutPtrace.setChecked(
                        sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, true));
            }
            MemoryOptionsBinder.syncDisableOverlaysForDeviceDefaults(
                    binding.getRoot() == null ? null : binding.getRoot().getContext(),
                    binding.tabMemory,
                    sp);
        } catch (Throwable ignored) {
        }
    }

    private static void bindAnimationCategoryControls(ActivityMainBinding binding, SharedPreferences sp) {
        try {
            boolean allTooltips = sp.getBoolean(SettingsPreferenceKeys.RANDOMIZE_ALL_TOOLTIPS, false);
            if (binding.tabSettings.radioStartupAnimationTooltipMode != null) {
                binding.tabSettings.radioStartupAnimationTooltipMode.setOnCheckedChangeListener(null);
            }
            String key = StartupLoadingTextPool.normalizeCategoryKey(
                    sp.getString(SettingsPreferenceKeys.ANIMATION_CATEGORY, StartupLoadingTextPool.CATEGORY_DEFAULT));
            if (binding.tabSettings.radioStartupAnimationCurrentTooltips != null) {
                binding.tabSettings.radioStartupAnimationCurrentTooltips.setChecked(!allTooltips);
            }
            if (binding.tabSettings.radioStartupAnimationAllTooltips != null) {
                binding.tabSettings.radioStartupAnimationAllTooltips.setChecked(allTooltips);
            }
            if (binding.tabSettings.radioStartupAnimationTooltipMode != null) {
                binding.tabSettings.radioStartupAnimationTooltipMode.setOnCheckedChangeListener((group, checkedId) -> {
                    boolean useAll = checkedId == R.id.radioStartupAnimationAllTooltips;
                    sp.edit()
                            .putBoolean(SettingsPreferenceKeys.RANDOMIZE_CURRENT_TOOLTIPS, !useAll)
                            .putBoolean(SettingsPreferenceKeys.RANDOMIZE_ALL_TOOLTIPS, useAll)
                            .putBoolean(SettingsPreferenceKeys.RANDOMIZE_ANIMATION_CATEGORY, false)
                            .apply();
                    applyAnimationCategoryDropdownState(binding, useAll);
                });
            }
            if (binding.tabSettings.ddAnimationCategory != null) {
                String[] categoryLabels = StartupLoadingTextPool.categoryLabels();
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        binding.tabSettings.ddAnimationCategory.getContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        categoryLabels) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        return padAnimationCategoryRow(super.getView(position, convertView, parent));
                    }

                    @Override
                    public View getDropDownView(int position, View convertView, ViewGroup parent) {
                        return padAnimationCategoryRow(super.getDropDownView(position, convertView, parent));
                    }
                };
                binding.tabSettings.ddAnimationCategory.setAdapter(adapter);
                configureAnimationCategoryDropdown(binding, categoryLabels);
                binding.tabSettings.ddAnimationCategory.setText(StartupLoadingTextPool.labelForKey(key), false);
                binding.tabSettings.ddAnimationCategory.setOnItemClickListener((parent, view, position, id) -> {
                    Object item = parent == null ? null : parent.getItemAtPosition(position);
                    String selectedKey = StartupLoadingTextPool.keyForLabel(item == null ? null : item.toString());
                    sp.edit().putString(SettingsPreferenceKeys.ANIMATION_CATEGORY, selectedKey).apply();
                });
                binding.tabSettings.ddAnimationCategory.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        String selectedKey = StartupLoadingTextPool.keyForLabel(
                                binding.tabSettings.ddAnimationCategory.getText() == null
                                        ? null : binding.tabSettings.ddAnimationCategory.getText().toString());
                        binding.tabSettings.ddAnimationCategory.setText(StartupLoadingTextPool.labelForKey(selectedKey), false);
                        sp.edit().putString(SettingsPreferenceKeys.ANIMATION_CATEGORY, selectedKey).apply();
                    }
                });
                applyAnimationCategoryDropdownState(binding, allTooltips);
            }
        } catch (Throwable ignored) {
        }
    }


    private static View padAnimationCategoryRow(View row) {
        try {
            if (row instanceof TextView) {
                TextView text = (TextView) row;
                int start = dp(text.getContext(), 18);
                int end = dp(text.getContext(), 24);
                text.setPadding(start, text.getPaddingTop(), end, text.getPaddingBottom());
            }
        } catch (Throwable ignored) {
        }
        return row;
    }

    private static void configureAnimationCategoryDropdown(ActivityMainBinding binding, String[] labels) {
        try {
            if (binding == null || binding.tabSettings == null || binding.tabSettings.ddAnimationCategory == null) return;
            Context context = binding.tabSettings.ddAnimationCategory.getContext();
            DropdownUi.prepareExposedDropdown(binding.tabSettings.layoutAnimationCategory,
                    binding.tabSettings.ddAnimationCategory);
            int start = dp(context, 18);
            int end = dp(context, 54);
            binding.tabSettings.ddAnimationCategory.setPadding(
                    start,
                    binding.tabSettings.ddAnimationCategory.getPaddingTop(),
                    end,
                    binding.tabSettings.ddAnimationCategory.getPaddingBottom());
            binding.tabSettings.ddAnimationCategory.setDropDownHorizontalOffset(0);
            binding.tabSettings.ddAnimationCategory.post(() -> {
                try {
                    int widest = 0;
                    if (labels != null) {
                        for (String label : labels) {
                            if (label == null) continue;
                            widest = Math.max(widest, (int) Math.ceil(binding.tabSettings.ddAnimationCategory.getPaint().measureText(label)));
                        }
                    }
                    int paddedWidth = widest + dp(context, 132);
                    int minWidth = dp(context, 240);
                    int desiredWidth = Math.max(minWidth, paddedWidth);
                    applyViewWidth(binding.tabSettings.layoutAnimationCategory, desiredWidth);
                    applyViewWidth(binding.tabSettings.ddAnimationCategory, desiredWidth);
                    binding.tabSettings.ddAnimationCategory.setDropDownWidth(desiredWidth);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void applyViewWidth(View view, int widthPx) {
        try {
            if (view == null || widthPx <= 0) return;
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp == null) return;
            if (lp.width == widthPx) return;
            lp.width = widthPx;
            view.setLayoutParams(lp);
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void applyAnimationCategoryDropdownState(ActivityMainBinding binding, boolean allTooltips) {
        try {
            if (binding == null || binding.tabSettings == null || binding.tabSettings.ddAnimationCategory == null) return;
            binding.tabSettings.ddAnimationCategory.setEnabled(!allTooltips);
            binding.tabSettings.ddAnimationCategory.setAlpha(allTooltips ? 0.55f : 1.0f);
        } catch (Throwable ignored) {
        }
    }

    private static void applyMainBannerVisibility(ActivityMainBinding binding, SharedPreferences sp) {
        try {
            if (binding == null || binding.tabMain == null || binding.tabMain.imgMainBanner == null || sp == null) return;
            boolean hide = sp.getBoolean(SettingsPreferenceKeys.HIDE_MAIN_PAGE_BANNER, true);
            binding.tabMain.imgMainBanner.setVisibility(hide ? View.GONE : View.VISIBLE);
        } catch (Throwable ignored) {
        }
    }

    private static void bindPanelHostCheckbox(Context context, ActivityMainBinding binding, SharedPreferences sp) {
        updatePanelHostCheckbox(context, binding, sp);
    }

    private static void updatePanelHostCheckbox(Context context, ActivityMainBinding binding, SharedPreferences sp) {
        try {
            if (binding == null || binding.tabSettings == null || binding.tabSettings.chkEnableFloatingPanels == null || sp == null) return;
            boolean saved = PermsTestPanelSettings.isPanelHostSavedEnabled(context);
            binding.tabSettings.chkEnableFloatingPanels.setOnCheckedChangeListener(null);
            binding.tabSettings.chkEnableFloatingPanels.setChecked(saved);
            binding.tabSettings.chkEnableFloatingPanels.setEnabled(true);
            binding.tabSettings.chkEnableFloatingPanels.setText("Enable Popout Panels");
            binding.tabSettings.chkEnableFloatingPanels.setOnCheckedChangeListener((btn, checked) -> {
                SharedPreferences.Editor editor = sp.edit()
                        .putBoolean(SettingsPreferenceKeys.ENABLE_FLOATING_PANELS, checked);
                if (!syncingDeviceDefaultSettings) {
                    editor.putBoolean(SettingsPreferenceKeys.DEVICE_DEFAULT_FLOATING_PANELS_USER_SET, true);
                }
                editor.apply();
                updatePanelHostCheckbox(context, binding, sp);
            });
        } catch (Throwable ignored) {
        }
    }

    private static void updateDeviceProfileInfo(Context context, ActivityMainBinding binding, SharedPreferences sp, Host host) {
        try {
            if (context == null || binding == null || binding.tabMain.txtDeviceProfileInfo == null) return;
            DeviceDetection.Info info = DeviceDetection.detect(context);
            binding.tabMain.txtDeviceProfileInfo.setText(info.settingsRow());
            if (info.hasUsableSerial() || host == null || sp == null) return;

            long now = SystemClock.uptimeMillis();
            if (serialProbeInFlight || now - lastSerialProbeMs < 8000L) return;
            serialProbeInFlight = true;
            lastSerialProbeMs = now;
            host.resolveDeviceSerial((exitCode, stdout, stderr) -> {
                serialProbeInFlight = false;
                String serial = DeviceDetection.parseSerialProbeOutput(stdout);
                if (TextUtils.isEmpty(serial)) return;
                try {
                    sp.edit().putString(SettingsPreferenceKeys.DEVICE_SERIAL_LAST, serial).apply();
                } catch (Throwable ignored) {
                }
                try {
                    if (binding.tabMain.txtDeviceProfileInfo != null) {
                        binding.tabMain.txtDeviceProfileInfo.setText(info.withSerial(serial).settingsRow());
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
            serialProbeInFlight = false;
        }
    }
}
