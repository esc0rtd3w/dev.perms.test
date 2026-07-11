package dev.perms.test.memory.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * Activity-side launcher plumbing for the Memory floating overlay service.
 */
public final class MemoryOverlayLauncher {
    private MemoryOverlayLauncher() {
    }

    public static boolean hasOverlayPermission(Context context) {
        if (context == null) return false;
        try {
            return Settings.canDrawOverlays(context);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void openOverlayPermissionSettings(Activity activity) {
        if (activity == null) return;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    public static Intent buildOverlayServiceIntent(Context context, String action, String targetPackage, String targetPid) {
        Intent svc = new Intent(context, MemoryOverlayService.class);
        svc.setAction(action);
        if (!TextUtils.isEmpty(targetPackage)) {
            svc.putExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE, targetPackage);
        }
        if (!TextUtils.isEmpty(targetPid)) {
            svc.putExtra(MemoryOverlayService.EXTRA_TARGET_PID, targetPid);
        }
        return svc;
    }

    public static void startOverlayService(Context context, String action, String targetPackage, String targetPid) {
        if (context == null) return;
        Intent svc = buildOverlayServiceIntent(context, action, targetPackage, targetPid);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
