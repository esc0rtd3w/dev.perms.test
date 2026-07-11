package dev.perms.test.ui.device;

import android.app.Activity;
import android.content.Context;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.device.DeviceDetection;
import dev.perms.test.ui.PermsTestUiCompat;

/**
 * Applies optional per-device UI polish on top of the default tab layouts.
 *
 * The default UI remains the source layout. This class only dispatches small,
 * category-specific adjustments when Settings > Adjust UI Based On Device is
 * enabled. Keep device-specific tweaks grouped here so phone, tablet, and VR
 * behavior do not drift through scattered one-off patches.
 */
public final class DeviceUiAdjustmentController {
    private DeviceUiAdjustmentController() {
    }

    public static void apply(Activity activity, ActivityMainBinding binding) {
        if (activity == null || binding == null || binding.getRoot() == null) return;
        if (!isEnabled(activity)) return;

        DeviceDetection.Info info = DeviceDetection.detect(activity);
        DeviceDetection.Profile profile = info == null ? DeviceDetection.Profile.TABLET : info.profile;
        if (PermsTestUiCompat.shouldUseVrProfile(activity) || profile == DeviceDetection.Profile.VR) {
            // VR compatibility is intentionally handled by the existing VR-gated path.
            return;
        }

        switch (profile) {
            case PHONE:
                PhoneLayoutTuner.apply(activity, binding);
                break;
            default:
                // Default/tablet layout currently needs no category-specific override.
                break;
        }
    }

    public static boolean isEnabled(Context context) {
        if (context == null) return false;
        return PermsTestUiCompat.isAdjustUiBasedOnDeviceEnabled(context);
    }
}
