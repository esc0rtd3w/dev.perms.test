package dev.perms.test.vr;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;

import dev.perms.test.memory.overlay.MemoryOverlayService;

/**
 * VR-only Memory overlay restore actions.
 *
 * These helpers keep Quest/Horizon restore service launches out of MainActivity and
 * separate from the normal phone/tablet overlay path. All entry points remain gated by
 * the existing VR compatibility state supplied by PermsTestVrOverlayCompat callers.
 */
public final class MemoryOverlayVrRestoreActions {
    private static final long MAIN_LAUNCH_BACK_DELAY_MS = 120L;

    private MemoryOverlayVrRestoreActions() {
    }

    public static void restoreHiddenOverlayFromMainLaunch(Activity activity,
                                                         Handler mainHandler,
                                                         OutputAppender outputAppender) {
        if (activity == null || !PermsTestVrOverlayCompat.consumeMainLaunchOverlayRestore(activity)) return;
        try {
            Intent svc = buildOverlayIntent(
                    activity,
                    MemoryOverlayService.ACTION_VR_RESTORE_OVERLAY_FOR_TARGET,
                    PermsTestVrOverlayCompat.hiddenVrTargetPackage(activity),
                    PermsTestVrOverlayCompat.hiddenVrTargetPid(activity));
            startOverlayService(activity, svc);
            if (mainHandler != null) {
                mainHandler.postDelayed(() -> moveTaskToBackQuietly(activity), MAIN_LAUNCH_BACK_DELAY_MS);
            } else {
                moveTaskToBackQuietly(activity);
            }
        } catch (Throwable t) {
            if (outputAppender != null) {
                outputAppender.append("[Memory] VR overlay restore request failed: " + t + "\n");
            }
        }
    }

    public static void restoreOverlayOverTarget(Activity activity, String targetPackage, String targetPid) {
        startOverlayAndReturnToTarget(
                activity,
                MemoryOverlayService.ACTION_VR_RESTORE_OVERLAY_FOR_TARGET,
                targetPackage,
                targetPid);
    }

    public static void showOverlayOnly(Activity activity, String targetPackage, String targetPid) {
        startOverlayAndReturnToTarget(
                activity,
                MemoryOverlayService.ACTION_SHOW_OVERLAY,
                targetPackage,
                targetPid);
    }

    private static void startOverlayAndReturnToTarget(Activity activity,
                                                      String action,
                                                      String targetPackage,
                                                      String targetPid) {
        if (activity == null) return;
        try {
            startOverlayService(activity, buildOverlayIntent(activity, action, targetPackage, targetPid));
        } catch (Throwable ignored) {
        }
        moveTaskToBackQuietly(activity);
    }

    private static Intent buildOverlayIntent(Activity activity,
                                             String action,
                                             String targetPackage,
                                             String targetPid) {
        Intent svc = new Intent(activity, MemoryOverlayService.class);
        svc.setAction(action);
        if (!TextUtils.isEmpty(targetPackage)) {
            svc.putExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE, targetPackage);
        }
        if (!TextUtils.isEmpty(targetPid)) {
            svc.putExtra(MemoryOverlayService.EXTRA_TARGET_PID, targetPid);
        }
        return svc;
    }

    private static void startOverlayService(Activity activity, Intent svc) {
        if (activity == null || svc == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            activity.startForegroundService(svc);
        } else {
            activity.startService(svc);
        }
    }

    private static void moveTaskToBackQuietly(Activity activity) {
        try {
            if (activity != null) activity.moveTaskToBack(true);
        } catch (Throwable ignored) {
        }
    }

    public interface OutputAppender {
        void append(String text);
    }
}
