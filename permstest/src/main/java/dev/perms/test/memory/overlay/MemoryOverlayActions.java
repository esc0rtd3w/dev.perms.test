package dev.perms.test.memory.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import dev.perms.test.vr.PermsTestVrOverlayCompat;

/**
 * Activity-side opener for Memory overlay windows.
 *
 * MainActivity supplies the current target/process and output sink. This helper keeps overlay
 * permission handling, notification-permission prompting, service launching, and stable Memory
 * toast/log text together without owning Memory tab widgets.
 */
public final class MemoryOverlayActions {
    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    public interface SelectedPidProvider {
        String getSelectedPid();
    }

    public interface OutputAppender {
        void append(String text);
    }

    private static final int REQUEST_POST_NOTIFICATIONS = 10051;

    private final Activity activity;
    private final TargetPackageProvider targetPackageProvider;
    private final SelectedPidProvider selectedPidProvider;
    private final OutputAppender outputAppender;

    public MemoryOverlayActions(Activity activity,
                                TargetPackageProvider targetPackageProvider,
                                SelectedPidProvider selectedPidProvider,
                                OutputAppender outputAppender) {
        this.activity = activity;
        this.targetPackageProvider = targetPackageProvider;
        this.selectedPidProvider = selectedPidProvider;
        this.outputAppender = outputAppender;
    }

    public void openMainOverlay() {
        open(MemoryOverlayService.ACTION_SHOW_OVERLAY,
                "Memory overlay opened",
                "[Memory] Failed to open overlay: ");
    }

    public void openOverlayAction(String action, String toastText) {
        open(action,
                TextUtils.isEmpty(toastText) ? "Memory overlay opened" : toastText,
                "[Memory] Failed to open overlay window: ");
    }

    private void open(String action, String toastText, String failurePrefix) {
        try {
            if (!MemoryOverlayLauncher.hasOverlayPermission(activity)) {
                Toast.makeText(activity, "Allow overlay permission to use Memory overlay controls.", Toast.LENGTH_LONG).show();
                MemoryOverlayLauncher.openOverlayPermissionSettings(activity);
                return;
            }
            requestNotificationPermissionIfNeeded();
            MemoryOverlayLauncher.startOverlayService(
                    activity,
                    action,
                    getTargetPackage(),
                    getSelectedPid());
            if (PermsTestVrOverlayCompat.isEnabled(activity)) {
                appendOutput("[Memory] " + toastText + "\n");
            } else {
                Toast.makeText(activity, toastText, Toast.LENGTH_SHORT).show();
            }
            PermsTestVrOverlayCompat.moveHostTaskBehindMemoryToolIfNeeded(activity);
        } catch (Throwable t) {
            appendOutput((TextUtils.isEmpty(failurePrefix) ? "[Memory] Failed to open overlay window: " : failurePrefix) + t + "\n");
        }
    }

    private String getTargetPackage() {
        try {
            return targetPackageProvider == null ? "" : targetPackageProvider.getTargetPackage();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getSelectedPid() {
        try {
            return selectedPidProvider == null ? null : selectedPidProvider.getSelectedPid();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        requestNotificationPermissionIfNeeded(activity);
    }

    private static void requestNotificationPermissionIfNeeded(Activity activity) {
        try {
            if (activity == null || Build.VERSION.SDK_INT < 33) return;
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                return;
            }
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS);
            Toast.makeText(activity, "Allow notifications so the minimized Memory overlay can be restored from the notification shade.", Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }

    public static boolean handleNotificationPermissionResult(Activity activity, int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return false;
        try {
            boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted && activity != null) {
                Toast.makeText(activity, "Memory overlay can still run, but Android may hide the minimized restore notification.", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private void appendOutput(String text) {
        if (TextUtils.isEmpty(text)) return;
        try {
            if (outputAppender != null) outputAppender.append(text);
        } catch (Throwable ignored) {
        }
    }
}
