package dev.perms.test.link;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/** Preference keys and snapshot helpers for Multiplayer Link. */
public final class MultiplayerLinkPreferences {
    private MultiplayerLinkPreferences() {
    }

    public static final int DEFAULT_PORT = 39081;

    public static final String KEY_HOST_PORT = "link_host_port";
    public static final String KEY_LAST_GUEST_ADDRESS = "link_last_guest_address";

    public static final String KEY_ALLOW_STATUS_VIEW = "link_allow_status_view";
    public static final String KEY_ALLOW_GUEST_PING = "link_allow_guest_ping";
    public static final String KEY_ALLOW_GUEST_MESSAGES = "link_allow_guest_messages";
    public static final String KEY_ALLOW_SHARED_OBJECTS = "link_allow_shared_objects";
    public static final String KEY_ALLOW_SERVER_CONTROL = "link_allow_server_control";
    public static final String KEY_ALLOW_MEMORY_READ = "link_allow_memory_read";
    public static final String KEY_ALLOW_MEMORY_WRITE = "link_allow_memory_write";
    public static final String KEY_ALLOW_PAYLOAD_APPLY = "link_allow_payload_apply";

    public static final String KEY_SHARE_MEMORY_EDITOR = "link_share_memory_editor";
    public static final String KEY_SHARE_DISASSEMBLER = "link_share_disassembler";
    public static final String KEY_SHARE_PAYLOAD_EDITOR = "link_share_payload_editor";
    public static final String KEY_SHARE_FTP_SERVER = "link_share_ftp_server";
    public static final String KEY_SHARE_HTTP_SERVER = "link_share_http_server";
    public static final String KEY_SHARE_WEB_INTERFACE = "link_share_web_interface";
    public static final String KEY_SHARE_SAVE_DATA_EDITOR = "link_share_save_data_editor";
    public static final String KEY_SHARE_PACKAGES = "link_share_packages";
    public static final String KEY_SHARE_SHELL = "link_share_shell";

    public static int getPort(SharedPreferences sp) {
        if (sp == null) return DEFAULT_PORT;
        return clampPort(sp.getInt(KEY_HOST_PORT, DEFAULT_PORT));
    }

    public static int clampPort(int port) {
        return port < 1024 || port > 65535 ? DEFAULT_PORT : port;
    }

    public static LinkSnapshot snapshot(SharedPreferences sp, boolean globalEnabled, boolean hostRunning, int port, String hostUrl) {
        LinkSnapshot s = new LinkSnapshot();
        s.enabled = globalEnabled;
        s.hostRunning = hostRunning;
        s.port = clampPort(port);
        s.hostUrl = hostUrl == null ? "" : hostUrl;
        if (sp != null) {
            s.allowStatusView = sp.getBoolean(KEY_ALLOW_STATUS_VIEW, false);
            s.allowGuestPing = sp.getBoolean(KEY_ALLOW_GUEST_PING, true);
            s.allowGuestMessages = sp.getBoolean(KEY_ALLOW_GUEST_MESSAGES, true);
            s.allowSharedObjects = sp.getBoolean(KEY_ALLOW_SHARED_OBJECTS, false);
            s.allowServerControl = sp.getBoolean(KEY_ALLOW_SERVER_CONTROL, false);
            s.allowMemoryRead = sp.getBoolean(KEY_ALLOW_MEMORY_READ, false);
            s.allowMemoryWrite = sp.getBoolean(KEY_ALLOW_MEMORY_WRITE, false);
            s.allowPayloadApply = sp.getBoolean(KEY_ALLOW_PAYLOAD_APPLY, false);

            s.shareMemoryEditor = sp.getBoolean(KEY_SHARE_MEMORY_EDITOR, false);
            s.shareDisassembler = sp.getBoolean(KEY_SHARE_DISASSEMBLER, false);
            s.sharePayloadEditor = sp.getBoolean(KEY_SHARE_PAYLOAD_EDITOR, false);
            s.shareFtpServer = sp.getBoolean(KEY_SHARE_FTP_SERVER, false);
            s.shareHttpServer = sp.getBoolean(KEY_SHARE_HTTP_SERVER, false);
            s.shareWebInterface = sp.getBoolean(KEY_SHARE_WEB_INTERFACE, false);
            s.shareSaveDataEditor = sp.getBoolean(KEY_SHARE_SAVE_DATA_EDITOR, false);
            s.sharePackages = sp.getBoolean(KEY_SHARE_PACKAGES, false);
            s.shareShell = sp.getBoolean(KEY_SHARE_SHELL, false);
        }
        return s;
    }

    public static final class LinkSnapshot {
        public boolean enabled;
        public boolean hostRunning;
        public int port;
        public String hostUrl;
        public boolean allowStatusView;
        public boolean allowGuestPing;
        public boolean allowGuestMessages;
        public boolean allowSharedObjects;
        public boolean allowServerControl;
        public boolean allowMemoryRead;
        public boolean allowMemoryWrite;
        public boolean allowPayloadApply;
        public boolean shareMemoryEditor;
        public boolean shareDisassembler;
        public boolean sharePayloadEditor;
        public boolean shareFtpServer;
        public boolean shareHttpServer;
        public boolean shareWebInterface;
        public boolean shareSaveDataEditor;
        public boolean sharePackages;
        public boolean shareShell;

        public JSONObject toJson() {
            JSONObject root = new JSONObject();
            try {
                root.put("app", "PermsTest");
                root.put("feature", "multiplayer_link");
                root.put("enabled", enabled);
                root.put("host_running", hostRunning);
                root.put("port", port);
                root.put("host_url", hostUrl == null ? "" : hostUrl);

                JSONObject actions = new JSONObject();
                actions.put("status_view", allowStatusView);
                actions.put("ping", allowGuestPing);
                actions.put("messages", allowGuestMessages);
                actions.put("shared_objects", allowSharedObjects);
                actions.put("server_control", allowServerControl);
                actions.put("memory_read", allowMemoryRead);
                actions.put("memory_write", allowMemoryWrite);
                actions.put("payload_apply", allowPayloadApply);
                root.put("allowed_guest_actions", actions);

                JSONArray safeGuestActions = new JSONArray();
                if (allowGuestPing) safeGuestActions.put("ping");
                if (allowGuestMessages) {
                    safeGuestActions.put("test_message");
                    safeGuestActions.put("message");
                    safeGuestActions.put("client_info");
                }
                if (allowSharedObjects) safeGuestActions.put("shared_objects");
                root.put("safe_guest_actions", safeGuestActions);

                JSONArray shared = new JSONArray();
                putShared(shared, "memory", "Memory Editor", shareMemoryEditor);
                putShared(shared, "memory", "Assembler/Disassembler", shareDisassembler);
                putShared(shared, "memory", "Payload Editor", sharePayloadEditor);
                putShared(shared, "network", "FTP Server", shareFtpServer);
                putShared(shared, "network", "HTTP Server", shareHttpServer);
                putShared(shared, "network", "Web Interface", shareWebInterface);
                putShared(shared, "tools", "Save Data Editor", shareSaveDataEditor);
                putShared(shared, "packages", "Package Tools", sharePackages);
                putShared(shared, "shell", "Shell/Scripts", shareShell);
                root.put("shared_objects", (allowStatusView || allowSharedObjects) ? shared : new JSONArray());
            } catch (Throwable ignored) {
            }
            return root;
        }

        private static void putShared(JSONArray array, String group, String name, boolean enabled) {
            if (array == null || TextUtils.isEmpty(name)) return;
            try {
                JSONObject o = new JSONObject();
                o.put("group", group == null ? "" : group);
                o.put("name", name);
                o.put("enabled", enabled);
                array.put(o);
            } catch (Throwable ignored) {
            }
        }
    }
}
