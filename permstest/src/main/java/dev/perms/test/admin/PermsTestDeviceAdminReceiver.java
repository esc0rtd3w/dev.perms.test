package dev.perms.test.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Device-admin receiver so PermsTest can be enabled from Android's Device Admin Apps screen. */
public final class PermsTestDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "PermsTestDeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "Device admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.i(TAG, "Device admin disabled");
    }
}
