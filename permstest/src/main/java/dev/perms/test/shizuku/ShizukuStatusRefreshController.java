package dev.perms.test.shizuku;

import android.app.Activity;

import rikka.shizuku.Shizuku;

/**
 * Bridges Shizuku binder/permission callbacks back to the activity status refresh path.
 */
public final class ShizukuStatusRefreshController {
    private final Activity activity;
    private final int requestCode;
    private final Runnable refreshAction;
    private boolean registered;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = new Shizuku.OnBinderReceivedListener() {
        @Override
        public void onBinderReceived() {
            refreshOnUiThread();
        }
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = new Shizuku.OnBinderDeadListener() {
        @Override
        public void onBinderDead() {
            refreshOnUiThread();
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int callbackRequestCode, int grantResult) {
                    if (callbackRequestCode == requestCode) {
                        refreshOnUiThread();
                    }
                }
            };

    public ShizukuStatusRefreshController(Activity activity, int requestCode, Runnable refreshAction) {
        this.activity = activity;
        this.requestCode = requestCode;
        this.refreshAction = refreshAction;
    }

    public void register() {
        if (registered) return;
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        registered = true;
    }

    public void unregisterQuietly() {
        try { Shizuku.removeBinderReceivedListener(binderReceivedListener); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderDeadListener(binderDeadListener); } catch (Throwable ignored) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResultListener); } catch (Throwable ignored) {}
        registered = false;
    }

    private void refreshOnUiThread() {
        if (refreshAction == null) return;
        activity.runOnUiThread(refreshAction);
    }
}
