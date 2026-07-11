package dev.perms.test;

import android.app.Application;
import android.content.SharedPreferences;

import dev.perms.test.assets.AssetDefaultsInstaller;

import rikka.shizuku.common.ShizukuConfig;
import rikka.shizuku.server.ServerConstants;

/**
 * App-level initialization.
 *
 * This is intentionally tiny: it applies early Shizuku embedded-mode configuration and
 * performs best-effort creation of the public PermsTest home folder.
 */
public final class PermsTestApp extends Application {

    private static final String PREFS = "perms_test";

    @Override
    public void onCreate() {
        super.onCreate();

        ExecMode mode = ExecMode.SHIZUKU;
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            mode = ExecMode.get(sp);
        } catch (Throwable ignored) {
        }
        applyShizukuRuntimeConfig(this, mode);
        AssetDefaultsInstaller.ensurePublicHomeDirectory();
    }

    /**
     * Apply the Shizuku runtime identity used by the provider and embedded server code.
     *
     * MainActivity can switch exec modes without killing the process; this keeps the
     * provider's binder key aligned with the selected backend before a new binder arrives.
     */
    public static void applyShizukuRuntimeConfig(android.content.Context context, ExecMode mode) {
        boolean internal = mode != null && mode.isInternalShizuku();
        String packageName = null;
        try {
            packageName = context == null ? null : context.getPackageName();
        } catch (Throwable ignored) {
        }

        if (internal && packageName != null && !packageName.trim().isEmpty()) {
            // Embedded/Internal Shizuku: avoid colliding with installed Shizuku by using an
            // app-scoped permission name and pointing "manager" to the host package.
            ShizukuConfig.setEmbedded(true);
            ShizukuConfig.setManagerApplicationId(packageName);
            ShizukuConfig.setPermission(packageName + ".permission.SHIZUKU_API_V23");
            try {
                ServerConstants.setManagerApplicationId(packageName);
            } catch (Throwable ignored) {
            }
        } else {
            // External Shizuku (default): upstream values.
            ShizukuConfig.setEmbedded(false);
            ShizukuConfig.setManagerApplicationId(ShizukuConfig.MANAGER_APPLICATION_ID_EXTERNAL);
            ShizukuConfig.setPermission(ShizukuConfig.PERMISSION_EXTERNAL);
            try {
                ServerConstants.setManagerApplicationId(ShizukuConfig.MANAGER_APPLICATION_ID_EXTERNAL);
            } catch (Throwable ignored) {
            }
        }
    }
}
