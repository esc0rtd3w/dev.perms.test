package dev.perms.test.ui;

import java.util.Locale;

/**
 * Startup collapse profiles for normal tab-page groupboxes.
 *
 * Manual expand/collapse changes are always saved through the normal user-state
 * keys. These profiles only decide which state is applied when Auto Collapse is
 * enabled for app startup/profile application.
 */
public final class GroupboxCollapseProfiles {
    public static final String PROFILE_DEFAULT = "default";
    public static final String PROFILE_USER = "user";
    public static final String PROFILE_MINIMAL = "minimal";
    public static final String PROFILE_GAMING = "gaming";
    public static final String PROFILE_SERVER = "server";
    public static final String PROFILE_DEBUGGING = "debugging";

    private static final String LABEL_DEFAULT = "Default";
    private static final String LABEL_USER = "User";
    private static final String LABEL_MINIMAL = "Minimal";
    private static final String LABEL_GAMING = "Gaming";
    private static final String LABEL_SERVER = "Server";
    private static final String LABEL_DEBUGGING = "Debugging";

    private static final String[] LABELS = {
            LABEL_DEFAULT,
            LABEL_USER,
            LABEL_MINIMAL,
            LABEL_GAMING,
            LABEL_SERVER,
            LABEL_DEBUGGING
    };

    private static final String[] MINIMAL_COLLAPSED = {
            "system_binaries",
            "run_payloads",
            "payload_editor",
            "host_tests",
            "http_request",
            "tcp_port_check",
            "permissions_state",
            "multiplayer_link",
            "lifetime_log"
    };

    private static final String[] GAMING_KEEP_OPEN = {
            "memory",
            "processes",
            "overlay_options",
            "payload_editor",
            "run_payloads",
            "hex_editor",
            "text_editor",
            "assembler_disassembler",
            "smali_editor",
            "apk_editor",
            "create_debuggable_package"
    };

    private static final String[] SERVER_KEEP_OPEN = {
            "backend",
            "custom_commands",
            "shell_commands",
            "ftp_server",
            "ftp_client",
            "http_server",
            "web_interface",
            "multiplayer_link",
            "network_info",
            "host_tests",
            "http_request",
            "tcp_port_check",
            "text_editor",
            "logcat",
            "lifetime_log"
    };

    private static final String[] DEBUGGING_KEEP_OPEN = {
            "backend",
            "custom_commands",
            "shell_commands",
            "system_binaries",
            "apk_installer",
            "permissions_state",
            "network_info",
            "host_tests",
            "http_request",
            "tcp_port_check",
            "assembler_disassembler",
            "network_inspection_mitm_patch",
            "smali_editor",
            "hex_editor",
            "text_editor",
            "logcat",
            "lifetime_log",
            "memory",
            "processes"
    };

    private GroupboxCollapseProfiles() {
    }

    public static String[] labels() {
        return LABELS.clone();
    }

    public static String defaultProfile() {
        return PROFILE_USER;
    }

    public static String normalizeProfile(String value) {
        String normalized = normalize(value);
        if (PROFILE_DEFAULT.equals(normalized)) return PROFILE_DEFAULT;
        if (PROFILE_MINIMAL.equals(normalized)) return PROFILE_MINIMAL;
        if (PROFILE_GAMING.equals(normalized)) return PROFILE_GAMING;
        if (PROFILE_SERVER.equals(normalized)) return PROFILE_SERVER;
        if (PROFILE_DEBUGGING.equals(normalized)) return PROFILE_DEBUGGING;
        return PROFILE_USER;
    }

    public static String keyForLabel(String label) {
        String normalized = normalize(label);
        if (PROFILE_DEFAULT.equals(normalized)) return PROFILE_DEFAULT;
        if (PROFILE_MINIMAL.equals(normalized)) return PROFILE_MINIMAL;
        if (PROFILE_GAMING.equals(normalized)) return PROFILE_GAMING;
        if (PROFILE_SERVER.equals(normalized)) return PROFILE_SERVER;
        if (PROFILE_DEBUGGING.equals(normalized)) return PROFILE_DEBUGGING;
        return PROFILE_USER;
    }

    public static String labelForKey(String key) {
        String normalized = normalizeProfile(key);
        if (PROFILE_DEFAULT.equals(normalized)) return LABEL_DEFAULT;
        if (PROFILE_MINIMAL.equals(normalized)) return LABEL_MINIMAL;
        if (PROFILE_GAMING.equals(normalized)) return LABEL_GAMING;
        if (PROFILE_SERVER.equals(normalized)) return LABEL_SERVER;
        if (PROFILE_DEBUGGING.equals(normalized)) return LABEL_DEBUGGING;
        return LABEL_USER;
    }

    public static boolean shouldCollapse(String profile, String cardId, String titleKey, boolean userCollapsed) {
        String normalizedProfile = normalizeProfile(profile);
        if (PROFILE_USER.equals(normalizedProfile)) return userCollapsed;
        if (PROFILE_DEFAULT.equals(normalizedProfile)) return false;

        String id = normalize(cardId);
        String title = normalize(titleKey);
        if (PROFILE_MINIMAL.equals(normalizedProfile)) {
            return matches(MINIMAL_COLLAPSED, id, title);
        }
        if (PROFILE_GAMING.equals(normalizedProfile)) {
            return !matches(GAMING_KEEP_OPEN, id, title);
        }
        if (PROFILE_SERVER.equals(normalizedProfile)) {
            return !matches(SERVER_KEEP_OPEN, id, title);
        }
        if (PROFILE_DEBUGGING.equals(normalizedProfile)) {
            return !matches(DEBUGGING_KEEP_OPEN, id, title);
        }
        return userCollapsed;
    }

    private static boolean matches(String[] values, String id, String title) {
        if (values == null) return false;
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized.length() == 0) continue;
            if (normalized.equals(id) || normalized.equals(title)) return true;
        }
        return false;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                sb.append(ch);
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
        }
        String out = sb.toString().toLowerCase(Locale.US);
        while (out.endsWith("_")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
