package dev.perms.test.wireless;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Opens Android developer settings for Wireless debugging setup.
 *
 * Wireless debugging does not have a reliable public deep-link across OEM Settings
 * builds. The stable route is Developer options; from there the user can tap
 * Wireless debugging and PermsTest can keep watching for the current ADB port.
 */
public final class WirelessDebuggingSettingsLauncher {
    private static final String TAG = "PermsTestWireless";

    // Hidden/non-public on many builds. Kept as a last-resort candidate only;
    // Developer options is preferred because it is the public, reliable route.
    private static final String ACTION_WIRELESS_DEBUGGING_SETTINGS = "android.settings.WIRELESS_DEBUGGING_SETTINGS";

    private WirelessDebuggingSettingsLauncher() {
    }

    public static boolean open(Context context) {
        if (context == null) return false;

        Intent[] candidates = createCandidateIntents();
        for (Intent intent : candidates) {
            if (!canResolve(context, intent)) continue;
            try {
                context.startActivity(intent);
                return true;
            } catch (Throwable t) {
                Log.w(TAG, "Settings launch failed for " + intent.getAction(), t);
            }
        }
        return false;
    }

    public static PendingIntent pendingIntent(Context context, int requestCode) {
        if (context == null) return null;

        Intent intent = createIntent(context);
        if (intent == null) return null;

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        try {
            return PendingIntent.getActivity(context, requestCode, intent, flags);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to create Wireless debugging settings PendingIntent", t);
            return null;
        }
    }

    public static Intent createIntent(Context context) {
        if (context == null) return null;
        Intent[] candidates = createCandidateIntents();
        for (Intent intent : candidates) {
            if (canResolve(context, intent)) return intent;
        }
        return null;
    }

    private static Intent[] createCandidateIntents() {
        return new Intent[] {
                newSettingsIntent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                newSettingsIntent(Settings.ACTION_SETTINGS),
                newSettingsIntent(ACTION_WIRELESS_DEBUGGING_SETTINGS)
        };
    }

    private static Intent newSettingsIntent(String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static boolean canResolve(Context context, Intent intent) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm != null && intent.resolveActivity(pm) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
