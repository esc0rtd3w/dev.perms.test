package dev.perms.test.ui;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

/** Keeps slow startup phases from receiving taps before the UI is ready. */
public final class StartupInputGuard {
    private StartupInputGuard() {
    }

    public static void block(Activity activity) {
        setBlocked(activity, true);
    }

    public static void unblock(Activity activity) {
        setBlocked(activity, false);
    }

    private static void setBlocked(Activity activity, boolean blocked) {
        if (activity == null) return;
        try {
            Window window = activity.getWindow();
            if (window == null) return;
            if (blocked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            }
        } catch (Throwable ignored) {
        }
    }
}
