package dev.perms.test.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.settings.SettingsDropdownFix;
import dev.perms.test.ui.device.DeviceUiAdjustmentController;

/**
 * Owns activity-wide visual chrome that is shared by tabs.
 *
 * Keep this focused on layout/chrome wiring so MainActivity does not grow
 * one-off UI helpers for every global view adjustment.
 */
public final class MainActivityChromeController {
    private final Activity activity;
    private final String prefsName;
    private final String keepBottomLogAboveNavKey;

    public MainActivityChromeController(Activity activity, String prefsName, String keepBottomLogAboveNavKey) {
        this.activity = activity;
        this.prefsName = prefsName;
        this.keepBottomLogAboveNavKey = keepBottomLogAboveNavKey;
    }

    public void applyDeviceUiProfile(ActivityMainBinding binding) {
        try {
            PermsTestUiCompat.applyActivityUiProfile(activity, binding == null ? null : binding.getRoot());
            DeviceUiAdjustmentController.apply(activity, binding);
        } catch (Throwable ignored) {
        }
    }

    public void applyCollapsibleGroupboxes(ActivityMainBinding binding) {
        try {
            CollapsibleGroupboxController.apply(binding == null ? null : binding.getRoot(), prefs());
        } catch (Throwable ignored) {
        }
    }

    public void applySamsungDropdownFix(ActivityMainBinding binding,
                                        boolean enabled,
                                        SettingsDropdownFix.PermissionHintUpdater permissionHintUpdater) {
        SettingsDropdownFix.apply(binding, enabled, permissionHintUpdater);
    }

    public void applySystemBarPadding(ActivityMainBinding binding) {
        try {
            if (binding == null) return;
            final View root = binding.getRoot();
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                try {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    int top = sys.top + dp(4);
                    int bottom = prefs().getBoolean(keepBottomLogAboveNavKey, true) ? sys.bottom : 0;
                    v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
                } catch (Throwable ignored) {
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(root);
        } catch (Throwable ignored) {
        }
    }

    public void attachFastScrollOverlays(ActivityMainBinding binding) {
        MainActivityFastScrollBinder.attach(binding);
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    private int dp(int dip) {
        try {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (float) dip,
                    activity.getResources().getDisplayMetrics()
            );
        } catch (Throwable ignored) {
            return dip;
        }
    }
}
