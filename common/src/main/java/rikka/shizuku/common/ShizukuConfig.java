package rikka.shizuku.common;

/**
 * Small configuration shim so the host app can switch between:
 *  - External Shizuku (installed manager app)
 *  - Embedded/Internal Shizuku (server lives in the host APK)
 *
 * Default values match upstream Shizuku.
 *
 * IMPORTANT:
 * This is intentionally tiny and dependency-free so both client/provider and
 * server code can reference the same values without introducing new module
 * dependencies.
 */
public final class ShizukuConfig {

    private ShizukuConfig() {
    }

    /** Upstream permission name (owned by the installed Shizuku manager app). */
    public static final String PERMISSION_EXTERNAL = "moe.shizuku.manager.permission.API_V23";

    /** Upstream manager package. */
    public static final String MANAGER_APPLICATION_ID_EXTERNAL = "moe.shizuku.privileged.api";

    private static volatile boolean embedded = false;
    private static volatile String permission = PERMISSION_EXTERNAL;
    private static volatile String managerApplicationId = MANAGER_APPLICATION_ID_EXTERNAL;

    /**
     * Enable/disable embedded mode.
     * <p>
     * The host app should call this as early as possible. It may also be updated before
     * replacing a backend binder when switching between installed and embedded modes.
     */
    public static void setEmbedded(boolean enabled) {
        embedded = enabled;
    }

    public static boolean isEmbedded() {
        return embedded;
    }

    /**
     * Permission checked by the provider/server.
     * For embedded mode, the host app should set this to a non-conflicting name
     * (e.g. "${applicationId}.permission.SHIZUKU_API_V23").
     */
    public static void setPermission(String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty()) return;
        permission = v;
    }

    public static String getPermission() {
        return permission;
    }

    /**
     * Package name hosting the manager-side UI/permission flow.
     * Upstream is the standalone Shizuku manager app; embedded mode points to the host.
     */
    public static void setManagerApplicationId(String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty()) return;
        managerApplicationId = v;
    }

    public static String getManagerApplicationId() {
        return managerApplicationId;
    }
}
