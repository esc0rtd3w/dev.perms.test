package dev.perms.test;

import android.content.SharedPreferences;

/**
 * Execution backend selector.
 *
 * SHIZUKU         : privileged shell via an installed Shizuku manager.
 * INTERNAL_SHIZUKU: privileged shell via PermsTest's embedded Shizuku server path.
 * SYSTEM          : app UID shell (limited).
 * LADB            : local ADB client talking to on-device adbd via Wireless Debugging.
 */
public enum ExecMode {
    SHIZUKU("shizuku", "Shizuku"),
    INTERNAL_SHIZUKU("internal_shizuku", "Internal Shizuku"),
    SYSTEM("system", "System"),
    LADB("ladb", "LADB");

    public static final String PREF_KEY_MODE = "exec_mode";

    public static final String PREF_KEY_LADB_AUTOCONNECT = "ladb_autoconnect";
    public static final String PREF_KEY_LADB_CONNECT_PORT = "ladb_connect_port";
    public static final String PREF_KEY_LADB_PAIR_PORT = "ladb_pair_port";
    public static final String PREF_KEY_LADB_PAIR_CODE = "ladb_pair_code";

    public static final int LADB_DEFAULT_CONNECT_PORT = 5555;
	// Pair port is shown by Android's Wireless debugging UI and varies per session.
	// Use 0 as "unset" so the user must input the correct pair port.
	public static final int LADB_DEFAULT_PAIR_PORT = 0;

    private final String prefValue;
    private final String display;

    ExecMode(String prefValue, String display) {
        this.prefValue = prefValue;
        this.display = display;
    }

    public String prefValue() {
        return prefValue;
    }

    public String displayName() {
        return display;
    }

    public boolean isShizukuLike() {
        return this == SHIZUKU || this == INTERNAL_SHIZUKU;
    }

    public boolean isInternalShizuku() {
        return this == INTERNAL_SHIZUKU;
    }

    public static boolean isShizukuLike(ExecMode mode) {
        return mode != null && mode.isShizukuLike();
    }

    public static ExecMode fromPref(String v) {
        if (v == null) return SHIZUKU;
        for (ExecMode m : values()) {
            if (m.prefValue.equalsIgnoreCase(v)) return m;
        }
        return SHIZUKU;
    }

    public static ExecMode fromDisplayName(String s) {
        if (s == null) return null;
        final String t = s.trim();
        if (t.isEmpty()) return null;

        for (ExecMode m : values()) {
            if (m.displayName().equalsIgnoreCase(t) || m.prefValue.equalsIgnoreCase(t)) {
                return m;
            }
        }
        return null;
    }

    public static ExecMode get(SharedPreferences sp) {
        return fromPref(sp.getString(PREF_KEY_MODE, SHIZUKU.prefValue));
    }

    public static void set(SharedPreferences sp, ExecMode mode) {
        sp.edit().putString(PREF_KEY_MODE, mode == null ? SHIZUKU.prefValue : mode.prefValue).apply();
    }
}
