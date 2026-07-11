package rikka.shizuku.server;

import rikka.shizuku.common.ShizukuConfig;

public class ServerConstants {

    /**
     * Permission checked by the server/provider.
     *
     * This is a field (not only a getter) because upstream code uses a static import.
     * The host app is expected to set {@link rikka.shizuku.common.ShizukuConfig#setPermission(String)}
     * early in process startup and restart after changing modes.
     */
    public static volatile String PERMISSION = ShizukuConfig.getPermission();

    public static final int MANAGER_APP_NOT_FOUND = 50;

    // Use runtime-configurable permission so the host app can embed without colliding
    // with an installed Shizuku manager.
    public static String getPermission() {
        return PERMISSION;
    }

    /**
     * The package name that hosts the "manager" side for the current server instance.
     * <p>
     * Upstream Shizuku uses a standalone manager app (moe.shizuku.privileged.api). For the
     * embedded variant we point this at the host application's package.
     */
    private static volatile String managerApplicationId = ShizukuConfig.MANAGER_APPLICATION_ID_EXTERNAL;

    public static String getManagerApplicationId() {
        return managerApplicationId;
    }

    public static void setManagerApplicationId(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) return;
        managerApplicationId = applicationId.trim();
    }

    public static String getRequestPermissionAction() {
        return getManagerApplicationId() + ".intent.action.REQUEST_PERMISSION";
    }

    public static final int BINDER_TRANSACTION_getApplications = 10001;
}
