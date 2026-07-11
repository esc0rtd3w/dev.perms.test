package dev.perms.test.link;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debug.DebugLog;
import dev.perms.test.network.NetworkAddressFormatter;
import dev.perms.test.settings.SettingsPreferenceKeys;

/** Network-tab controller for Multiplayer Link LAN session controls. */
public final class MultiplayerLinkController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        SharedPreferences getSharedPreferences();
        void appendOutput(String message);
    }

    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MultiplayerLinkServer server = new MultiplayerLinkServer();
    private SharedPreferences registeredPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private boolean guestConnected;
    private String guestEndpointLabel = "";
    private boolean guestAllowStatusView;
    private boolean guestAllowGuestPing;
    private boolean guestAllowGuestMessages;
    private boolean guestAllowSharedObjects;
    private boolean guestAllowServerControl;
    private boolean guestAllowMemoryRead;
    private boolean guestAllowMemoryWrite;
    private boolean guestAllowPayloadApply;

    public MultiplayerLinkController(Host host) {
        this.host = host;
    }

    public void bind() {
        ActivityMainBinding b = binding();
        SharedPreferences sp = prefs();
        if (b == null || sp == null || b.tabNetwork == null) return;
        ensurePreferenceListener(sp);
        debug("bind", "binding Multiplayer Link controls; enabled=" + isFeatureEnabled(sp) + ", running=" + server.isRunning());

        bindPortAndAddress(b, sp);
        bindCheckBox(b.tabNetwork.chkLinkAllowStatusView, sp, MultiplayerLinkPreferences.KEY_ALLOW_STATUS_VIEW, false);
        bindCheckBox(b.tabNetwork.chkLinkAllowGuestPing, sp, MultiplayerLinkPreferences.KEY_ALLOW_GUEST_PING, true);
        bindCheckBox(b.tabNetwork.chkLinkAllowGuestMessages, sp, MultiplayerLinkPreferences.KEY_ALLOW_GUEST_MESSAGES, true);
        bindCheckBox(b.tabNetwork.chkLinkAllowSharedObjects, sp, MultiplayerLinkPreferences.KEY_ALLOW_SHARED_OBJECTS, false);
        bindCheckBox(b.tabNetwork.chkLinkAllowServerControl, sp, MultiplayerLinkPreferences.KEY_ALLOW_SERVER_CONTROL, false);
        bindCheckBox(b.tabNetwork.chkLinkAllowMemoryRead, sp, MultiplayerLinkPreferences.KEY_ALLOW_MEMORY_READ, false);
        bindCheckBox(b.tabNetwork.chkLinkAllowMemoryWrite, sp, MultiplayerLinkPreferences.KEY_ALLOW_MEMORY_WRITE, false);
        bindCheckBox(b.tabNetwork.chkLinkAllowPayloadApply, sp, MultiplayerLinkPreferences.KEY_ALLOW_PAYLOAD_APPLY, false);

        bindCheckBox(b.tabNetwork.chkLinkShareMemoryEditor, sp, MultiplayerLinkPreferences.KEY_SHARE_MEMORY_EDITOR, false);
        bindCheckBox(b.tabNetwork.chkLinkShareDisassembler, sp, MultiplayerLinkPreferences.KEY_SHARE_DISASSEMBLER, false);
        bindCheckBox(b.tabNetwork.chkLinkSharePayloadEditor, sp, MultiplayerLinkPreferences.KEY_SHARE_PAYLOAD_EDITOR, false);
        bindCheckBox(b.tabNetwork.chkLinkShareFtpServer, sp, MultiplayerLinkPreferences.KEY_SHARE_FTP_SERVER, false);
        bindCheckBox(b.tabNetwork.chkLinkShareHttpServer, sp, MultiplayerLinkPreferences.KEY_SHARE_HTTP_SERVER, false);
        bindCheckBox(b.tabNetwork.chkLinkShareWebInterface, sp, MultiplayerLinkPreferences.KEY_SHARE_WEB_INTERFACE, false);
        bindCheckBox(b.tabNetwork.chkLinkShareSaveDataEditor, sp, MultiplayerLinkPreferences.KEY_SHARE_SAVE_DATA_EDITOR, false);
        bindCheckBox(b.tabNetwork.chkLinkSharePackages, sp, MultiplayerLinkPreferences.KEY_SHARE_PACKAGES, false);
        bindCheckBox(b.tabNetwork.chkLinkShareShell, sp, MultiplayerLinkPreferences.KEY_SHARE_SHELL, false);

        if (b.tabNetwork.btnLinkStartHost != null) b.tabNetwork.btnLinkStartHost.setOnClickListener(v -> startHost());
        if (b.tabNetwork.btnLinkStopHost != null) b.tabNetwork.btnLinkStopHost.setOnClickListener(v -> stopHost());
        if (b.tabNetwork.btnLinkConnectGuest != null) b.tabNetwork.btnLinkConnectGuest.setOnClickListener(v -> connectGuest());
        if (b.tabNetwork.btnLinkDisconnectGuest != null) b.tabNetwork.btnLinkDisconnectGuest.setOnClickListener(v -> disconnectGuest());
        if (b.tabNetwork.btnLinkRefreshHostInfo != null) b.tabNetwork.btnLinkRefreshHostInfo.setOnClickListener(v -> connectGuest());
        if (b.tabNetwork.btnLinkPingHost != null) b.tabNetwork.btnLinkPingHost.setOnClickListener(v -> sendGuestAction("ping", ""));
        if (b.tabNetwork.btnLinkRequestSharedObjects != null) b.tabNetwork.btnLinkRequestSharedObjects.setOnClickListener(v -> sendGuestAction("shared_objects", ""));
        if (b.tabNetwork.btnLinkSendDeviceInfo != null) b.tabNetwork.btnLinkSendDeviceInfo.setOnClickListener(v -> sendGuestAction("client_info", deviceInfoMessage()));
        if (b.tabNetwork.btnLinkSendTestMessage != null) b.tabNetwork.btnLinkSendTestMessage.setOnClickListener(v -> sendGuestAction("test_message", "Test message from guest"));
        if (b.tabNetwork.btnLinkSendMessage != null) b.tabNetwork.btnLinkSendMessage.setOnClickListener(v -> sendGuestTextMessage());

        refreshUi();
    }

    public void refreshVisibleState() {
        refreshUi();
    }

    public void stop() {
        debug("lifecycle", "stop requested; running=" + server.isRunning());
        unregisterPreferenceListener();
        server.stop();
        clearGuestState();
        refreshUi();
    }

    private void bindPortAndAddress(ActivityMainBinding b, SharedPreferences sp) {
        try {
            if (b.tabNetwork.edtLinkHostPort != null) {
                b.tabNetwork.edtLinkHostPort.setText(String.valueOf(MultiplayerLinkPreferences.getPort(sp)));
                b.tabNetwork.edtLinkHostPort.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) saveHostPort();
                });
            }
            if (b.tabNetwork.edtLinkGuestAddress != null) {
                b.tabNetwork.edtLinkGuestAddress.setText(sp.getString(MultiplayerLinkPreferences.KEY_LAST_GUEST_ADDRESS, ""));
                b.tabNetwork.edtLinkGuestAddress.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) saveGuestAddress();
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private void bindCheckBox(CompoundButton checkBox, SharedPreferences sp, String key, boolean defaultValue) {
        if (checkBox == null || sp == null || TextUtils.isEmpty(key)) return;
        checkBox.setChecked(sp.getBoolean(key, defaultValue));
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean(key, isChecked).apply();
            debug("pref", key + "=" + isChecked);
            refreshUi();
        });
    }

    private void startHost() {
        SharedPreferences sp = prefs();
        if (sp == null) return;
        if (!isFeatureEnabled(sp)) {
            debug("host", "start rejected; feature disabled");
            setStatus("Enable Multiplayer Link in Settings > Main first.");
            appendOutput("[!] Multiplayer Link is disabled in Settings.");
            refreshUi();
            return;
        }
        if (server.isRunning()) {
            String url = hostUrl(server.getPort());
            setStatus("Host already running: " + url);
            toast("Multiplayer Link host already running");
            refreshUi();
            return;
        }
        saveHostPort();
        int port = MultiplayerLinkPreferences.getPort(sp);
        debug("host", "start requested port=" + port + ", url=" + hostUrl(port));
        try {
            server.start(port, this::snapshot, this::postServerEvent);
            setStatus("Host running: " + hostUrl(port));
            appendOutput("[+] Multiplayer Link host started: " + hostUrl(port));
            toast("Multiplayer Link host started: " + endpointLabel(hostUrl(port)));
            debug("host", "started port=" + server.getPort() + ", running=" + server.isRunning());
        } catch (Throwable t) {
            debugWarn("host", "start failed: " + t.getClass().getSimpleName() + ": " + summarize(t));
            setStatus("Host failed: " + summarize(t));
            appendOutput("[!] Multiplayer Link host failed: " + summarize(t));
            toast("Multiplayer Link host failed: " + summarize(t));
        }
        refreshUi();
    }

    private void stopHost() {
        debug("host", "stop requested; running=" + server.isRunning());
        int oldPort = server.getPort();
        server.stop();
        setStatus("Host stopped.");
        appendOutput("[-] Multiplayer Link host stopped.");
        toast("Multiplayer Link host stopped" + (oldPort > 0 ? ": port " + oldPort : ""));
        refreshUi();
    }

    private void connectGuest() {
        SharedPreferences sp = prefs();
        if (sp == null) return;
        if (!isFeatureEnabled(sp)) {
            debug("guest", "connect rejected; feature disabled");
            clearGuestState();
            setStatus("Enable Multiplayer Link in Settings > Main first.");
            appendOutput("[!] Multiplayer Link is disabled in Settings.");
            refreshUi();
            return;
        }
        saveGuestAddress();
        String address = textOf(binding() == null || binding().tabNetwork == null ? null : binding().tabNetwork.edtLinkGuestAddress);
        if (TextUtils.isEmpty(address)) {
            debugWarn("guest", "connect rejected; empty address");
            clearGuestState();
            setStatus("Enter a host URL or host:port first.");
            refreshUi();
            return;
        }
        String url = normalizeGuestStatusUrl(address);
        String label = endpointLabel(url);
        debug("guest", "connect requested address=" + address + ", normalized=" + url);
        setStatus("Connecting to " + label + "...");
        toast("Multiplayer Link connecting to " + label);
        new Thread(() -> {
            String result = fetch(url);
            mainHandler.post(() -> {
                if (result == null || !isHttpSuccess(result)) {
                    clearGuestState();
                    debugWarn("guest", "connect failed url=" + url + ", result=" + compact(result));
                    setStatus("Guest connect failed: " + (result == null ? "no response from " : "rejected by ") + label);
                    appendOutput("[!] Multiplayer Link guest connect failed: " + url + (TextUtils.isEmpty(result) ? "" : "\n" + result));
                    toast("Multiplayer Link connect failed: " + label);
                } else {
                    applyGuestSnapshot(result, label);
                    debug("guest", "connect complete url=" + url + ", responseLen=" + result.length()
                            + ", ping=" + guestAllowGuestPing + ", messages=" + guestAllowGuestMessages
                            + ", sharedObjects=" + guestAllowSharedObjects);
                    setStatus("Guest connected to " + label + actionSummary());
                    appendOutput("[+] Multiplayer Link guest status from " + url + ":\n" + formatGuestStatus(result));
                    toast("Multiplayer Link connected to " + label);
                }
                refreshUi();
            });
        }, "PermsTestLinkGuestConnect").start();
    }

    private void disconnectGuest() {
        String label = TextUtils.isEmpty(guestEndpointLabel) ? "host" : guestEndpointLabel;
        clearGuestState();
        setStatus("Guest disconnected from " + label + ".");
        appendOutput("[-] Multiplayer Link guest disconnected from " + label + ".");
        toast("Multiplayer Link disconnected from " + label);
        refreshUi();
    }

    private void sendGuestTextMessage() {
        ActivityMainBinding b = binding();
        String message = textOf(b == null || b.tabNetwork == null ? null : b.tabNetwork.edtLinkGuestMessage);
        if (TextUtils.isEmpty(message)) {
            setStatus("Enter a guest message first.");
            return;
        }
        sendGuestAction("message", message);
    }

    private void sendGuestAction(String action, String message) {
        SharedPreferences sp = prefs();
        if (sp == null) return;
        if (!isFeatureEnabled(sp)) {
            debug("guest-action", "send rejected; feature disabled");
            clearGuestState();
            setStatus("Enable Multiplayer Link in Settings > Main first.");
            appendOutput("[!] Multiplayer Link is disabled in Settings.");
            refreshUi();
            return;
        }
        if (!isGuestActionEnabled(action)) {
            setStatus(readableAction(action) + " is disabled by the host or no host is connected.");
            toast("Multiplayer Link action disabled");
            refreshUi();
            return;
        }
        saveGuestAddress();
        String address = textOf(binding() == null || binding().tabNetwork == null ? null : binding().tabNetwork.edtLinkGuestAddress);
        if (TextUtils.isEmpty(address)) {
            debugWarn("guest-action", "send rejected; empty address");
            setStatus("Enter a host URL or host:port first.");
            return;
        }
        String url = normalizeGuestActionUrl(address);
        JSONObject body = new JSONObject();
        try {
            body.put("action", action == null ? "" : action);
            body.put("message", message == null ? "" : message);
            body.put("client", localClientLabel());
        } catch (Throwable ignored) {
        }
        String label = endpointLabel(url);
        debug("guest-action", "send action=" + action + ", url=" + url);
        setStatus("Sending " + readableAction(action) + " to " + label + "...");
        toast("Multiplayer Link sending " + readableAction(action) + " to " + label);
        new Thread(() -> {
            String result = postJson(url, body.toString());
            mainHandler.post(() -> {
                if (result == null) {
                    debugWarn("guest-action", "send failed action=" + action + ", url=" + url);
                    setStatus("Guest action failed: no response from " + label);
                    appendOutput("[!] Multiplayer Link guest action failed: " + readableAction(action) + " -> " + url);
                    toast("Multiplayer Link action failed: " + label);
                } else if (!isHttpSuccess(result)) {
                    debugWarn("guest-action", "send rejected action=" + action + ", result=" + compact(result));
                    setStatus("Guest action rejected by " + label + ": " + compact(result));
                    appendOutput("[!] Multiplayer Link guest action rejected " + readableAction(action) + " -> " + url + ":\n" + result);
                    toast("Multiplayer Link action rejected: " + label);
                } else {
                    debug("guest-action", "send complete action=" + action + ", responseLen=" + result.length());
                    String formatted = "shared_objects".equals(action) ? formatSharedObjectsResponse(result) : result;
                    setStatus("Guest action sent to " + label + ": " + compact(formatted));
                    appendOutput("[+] Multiplayer Link guest action " + readableAction(action) + " -> " + url + ":\n" + formatted);
                    toast("Multiplayer Link action sent to " + label);
                }
                refreshUi();
            });
        }, "PermsTestLinkGuestAction").start();
    }

    private MultiplayerLinkPreferences.LinkSnapshot snapshot() {
        SharedPreferences sp = prefs();
        int port = server.isRunning() ? server.getPort() : MultiplayerLinkPreferences.getPort(sp);
        return MultiplayerLinkPreferences.snapshot(sp, isFeatureEnabled(sp), server.isRunning(), port, hostUrl(port));
    }

    private void refreshUi() {
        ActivityMainBinding b = binding();
        SharedPreferences sp = prefs();
        if (b == null || sp == null || b.tabNetwork == null) return;
        boolean enabled = isFeatureEnabled(sp);
        boolean running = server.isRunning();
        if (!enabled) clearGuestState();
        boolean connected = enabled && guestConnected;
        setEnabledSafe(b.tabNetwork.edtLinkHostPort, enabled && !running);
        setEnabledSafe(b.tabNetwork.btnLinkStartHost, enabled && !running);
        setEnabledSafe(b.tabNetwork.btnLinkStopHost, enabled && running);
        setEnabledSafe(b.tabNetwork.edtLinkGuestAddress, enabled);
        setEnabledSafe(b.tabNetwork.btnLinkConnectGuest, enabled);
        setEnabledSafe(b.tabNetwork.btnLinkDisconnectGuest, connected);
        setEnabledSafe(b.tabNetwork.btnLinkRefreshHostInfo, connected);
        setEnabledSafe(b.tabNetwork.btnLinkPingHost, connected && guestAllowGuestPing);
        setEnabledSafe(b.tabNetwork.btnLinkRequestSharedObjects, connected && guestAllowSharedObjects);
        setEnabledSafe(b.tabNetwork.btnLinkSendDeviceInfo, connected && guestAllowGuestMessages);
        setEnabledSafe(b.tabNetwork.btnLinkSendTestMessage, connected && guestAllowGuestMessages);
        setEnabledSafe(b.tabNetwork.edtLinkGuestMessage, connected && guestAllowGuestMessages);
        setEnabledSafe(b.tabNetwork.btnLinkSendMessage, connected && guestAllowGuestMessages);

        if (!enabled) {
            setStatus("Multiplayer Link disabled. Enable it in Settings > Main.");
        } else if (running) {
            String current = currentStatusText();
            if (TextUtils.isEmpty(current)
                    || current.startsWith("Multiplayer Link disabled")
                    || current.startsWith("Ready")
                    || current.startsWith("Host stopped")
                    || current.startsWith("Host running")) {
                setStatus("Host running: " + hostUrl(server.getPort()));
            }
        } else {
            String current = currentStatusText();
            if (TextUtils.isEmpty(current) || current.startsWith("Multiplayer Link disabled")) {
                setStatus("Ready. Start Host or connect as guest. Guest actions enable after Connect.");
            }
        }
    }

    private boolean isGuestActionEnabled(String action) {
        if (!guestConnected) return false;
        if ("ping".equals(action)) return guestAllowGuestPing;
        if ("shared_objects".equals(action)) return guestAllowSharedObjects;
        if ("test_message".equals(action) || "message".equals(action) || "client_info".equals(action)) {
            return guestAllowGuestMessages;
        }
        return false;
    }

    private void clearGuestState() {
        guestConnected = false;
        guestEndpointLabel = "";
        guestAllowStatusView = false;
        guestAllowGuestPing = false;
        guestAllowGuestMessages = false;
        guestAllowSharedObjects = false;
        guestAllowServerControl = false;
        guestAllowMemoryRead = false;
        guestAllowMemoryWrite = false;
        guestAllowPayloadApply = false;
    }

    private void applyGuestSnapshot(String response, String label) {
        clearGuestState();
        guestConnected = true;
        guestEndpointLabel = label == null ? "" : label;
        JSONObject root = responseJson(response);
        if (root == null) return;
        JSONObject allowed = root.optJSONObject("allowed_guest_actions");
        JSONArray safe = root.optJSONArray("safe_guest_actions");
        if (allowed != null) {
            guestAllowStatusView = allowed.optBoolean("status_view", false);
            guestAllowGuestPing = allowed.optBoolean("ping", arrayContains(safe, "ping"));
            guestAllowGuestMessages = allowed.optBoolean("messages", arrayContains(safe, "message") || arrayContains(safe, "test_message"));
            guestAllowSharedObjects = allowed.optBoolean("shared_objects", arrayContains(safe, "shared_objects"));
            guestAllowServerControl = allowed.optBoolean("server_control", false);
            guestAllowMemoryRead = allowed.optBoolean("memory_read", false);
            guestAllowMemoryWrite = allowed.optBoolean("memory_write", false);
            guestAllowPayloadApply = allowed.optBoolean("payload_apply", false);
        } else if (safe != null) {
            guestAllowGuestPing = arrayContains(safe, "ping");
            guestAllowGuestMessages = arrayContains(safe, "message") || arrayContains(safe, "test_message") || arrayContains(safe, "client_info");
            guestAllowSharedObjects = arrayContains(safe, "shared_objects");
        }
    }

    private String actionSummary() {
        if (!guestConnected) return "";
        return " (ping " + onOff(guestAllowGuestPing)
                + ", messages " + onOff(guestAllowGuestMessages)
                + ", Shared Objects " + onOff(guestAllowSharedObjects) + ")";
    }

    private String formatGuestStatus(String response) {
        JSONObject root = responseJson(response);
        if (root == null) return response == null ? "" : response;
        StringBuilder sb = new StringBuilder();
        sb.append("Host: ").append(TextUtils.isEmpty(guestEndpointLabel) ? root.optString("host_url", "unknown") : guestEndpointLabel).append('\n');
        sb.append("Running: ").append(root.optBoolean("host_running", false)).append('\n');
        sb.append("Guest actions: ping ").append(onOff(guestAllowGuestPing))
                .append(", messages ").append(onOff(guestAllowGuestMessages))
                .append(", Shared Objects ").append(onOff(guestAllowSharedObjects)).append('\n');
        sb.append("Capability flags: status ").append(onOff(guestAllowStatusView))
                .append(", server ").append(onOff(guestAllowServerControl))
                .append(", memory read ").append(onOff(guestAllowMemoryRead))
                .append(", memory write ").append(onOff(guestAllowMemoryWrite))
                .append(", payload ").append(onOff(guestAllowPayloadApply)).append('\n');
        if (guestAllowSharedObjects || guestAllowStatusView) {
            String objects = formatSharedObjects(root.optJSONArray("shared_objects"));
            if (!TextUtils.isEmpty(objects)) sb.append(objects);
        }
        return sb.toString().trim();
    }

    private String formatSharedObjectsResponse(String response) {
        JSONObject root = responseJson(response);
        if (root == null) return response == null ? "" : response;
        StringBuilder sb = new StringBuilder();
        String hostUrl = root.optString("host_url", "");
        if (!TextUtils.isEmpty(hostUrl)) sb.append("Host URL: ").append(hostUrl).append('\n');
        String objects = formatSharedObjects(root.optJSONArray("shared_objects"));
        if (TextUtils.isEmpty(objects)) sb.append("No Shared Objects are enabled on the host.");
        else sb.append(objects);
        return sb.toString().trim();
    }

    private String formatSharedObjects(JSONArray shared) {
        if (shared == null || shared.length() == 0) return "";
        StringBuilder sb = new StringBuilder("Shared Objects:\n");
        for (int i = 0; i < shared.length(); i++) {
            JSONObject item = shared.optJSONObject(i);
            if (item == null || !item.optBoolean("enabled", false)) continue;
            String group = item.optString("group", "");
            String name = item.optString("name", "");
            if (TextUtils.isEmpty(name)) continue;
            sb.append("- ");
            if (!TextUtils.isEmpty(group)) sb.append(group).append(": ");
            sb.append(name).append('\n');
        }
        String text = sb.toString().trim();
        return "Shared Objects:".equals(text) ? "" : text;
    }

    private String deviceInfoMessage() {
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String device = (maker + " " + model).trim();
        if (TextUtils.isEmpty(device)) device = "Android device";
        return "Device info: " + device + ", Android SDK " + Build.VERSION.SDK_INT + ", IP " + localClientLabel();
    }

    private static boolean isHttpSuccess(String response) {
        return response != null && (response.startsWith("HTTP 200 ") || response.startsWith("HTTP 202 "));
    }

    private static JSONObject responseJson(String response) {
        if (TextUtils.isEmpty(response)) return null;
        int start = response.indexOf('{');
        if (start < 0) return null;
        try {
            return new JSONObject(response.substring(start));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean arrayContains(JSONArray array, String value) {
        if (array == null || TextUtils.isEmpty(value)) return false;
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) return true;
        }
        return false;
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private String fetch(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestMethod("GET");
            debug("guest-fetch", "fetch begin url=" + url);
            int code = conn.getResponseCode();
            BufferedInputStream in = new BufferedInputStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
                if (out.size() > 128 * 1024) break;
            }
            String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            debug("guest-fetch", "fetch complete code=" + code + ", bytes=" + body.length());
            return "HTTP " + code + " " + body;
        } catch (Throwable t) {
            debugWarn("guest-fetch", "fetch failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        } finally {
            try { if (conn != null) conn.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private String postJson(String url, String json) {
        HttpURLConnection conn = null;
        try {
            byte[] data = (json == null ? "{}" : json).getBytes(StandardCharsets.UTF_8);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json, text/plain");
            conn.getOutputStream().write(data);
            debug("guest-post", "post begin url=" + url + ", bytes=" + data.length);
            int code = conn.getResponseCode();
            BufferedInputStream in = new BufferedInputStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
                if (out.size() > 128 * 1024) break;
            }
            String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            debug("guest-post", "post complete code=" + code + ", bytes=" + body.length());
            return "HTTP " + code + " " + body;
        } catch (Throwable t) {
            debugWarn("guest-post", "post failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        } finally {
            try { if (conn != null) conn.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private String normalizeGuestStatusUrl(String address) {
        return normalizeGuestBaseUrl(address) + "/link/status";
    }

    private String normalizeGuestActionUrl(String address) {
        return normalizeGuestBaseUrl(address) + "/link/action";
    }

    private String normalizeGuestBaseUrl(String address) {
        String a = address == null ? "" : address.trim();
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://" + a;
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        if (a.endsWith("/link/status")) a = a.substring(0, a.length() - "/link/status".length());
        if (a.endsWith("/link/action")) a = a.substring(0, a.length() - "/link/action".length());
        if (a.endsWith("/link")) a = a.substring(0, a.length() - "/link".length());
        return a;
    }

    private void saveHostPort() {
        SharedPreferences sp = prefs();
        ActivityMainBinding b = binding();
        if (sp == null || b == null || b.tabNetwork == null) return;
        int port = parseInt(textOf(b.tabNetwork.edtLinkHostPort), MultiplayerLinkPreferences.DEFAULT_PORT);
        port = MultiplayerLinkPreferences.clampPort(port);
        sp.edit().putInt(MultiplayerLinkPreferences.KEY_HOST_PORT, port).apply();
        debug("pref", "hostPort=" + port);
        try { b.tabNetwork.edtLinkHostPort.setText(String.valueOf(port)); } catch (Throwable ignored) {}
    }

    private void saveGuestAddress() {
        SharedPreferences sp = prefs();
        ActivityMainBinding b = binding();
        if (sp == null || b == null || b.tabNetwork == null) return;
        String value = textOf(b.tabNetwork.edtLinkGuestAddress);
        sp.edit().putString(MultiplayerLinkPreferences.KEY_LAST_GUEST_ADDRESS, value).apply();
        debug("pref", "lastGuestAddress=" + value);
    }

    private String localClientLabel() {
        try {
            NetworkAddressFormatter.Status status = NetworkAddressFormatter.currentStatus();
            return status == null || TextUtils.isEmpty(status.firstIpv4) ? "unknown" : status.firstIpv4;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private String hostUrl(int port) {
        NetworkAddressFormatter.Status status = NetworkAddressFormatter.currentStatus();
        String hostAddress = status == null || TextUtils.isEmpty(status.firstIpv4) ? "127.0.0.1" : status.firstIpv4;
        return "http://" + hostAddress + ":" + MultiplayerLinkPreferences.clampPort(port) + "/link/status";
    }

    private boolean isFeatureEnabled(SharedPreferences sp) {
        return sp != null && sp.getBoolean(SettingsPreferenceKeys.ENABLE_MULTIPLAYER_LINK, false);
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.getBinding();
    }

    private SharedPreferences prefs() {
        SharedPreferences sp = host == null ? null : host.getSharedPreferences();
        if (sp != null) return sp;
        Activity activity = host == null ? null : host.getActivity();
        return activity == null ? null : activity.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
    }

    private void ensurePreferenceListener(SharedPreferences sp) {
        if (sp == null || sp == registeredPrefs) return;
        unregisterPreferenceListener();
        preferenceChangeListener = (sharedPreferences, key) -> {
            if (SettingsPreferenceKeys.ENABLE_MULTIPLAYER_LINK.equals(key)) {
                mainHandler.post(this::refreshUi);
            }
        };
        try {
            sp.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
            registeredPrefs = sp;
        } catch (Throwable ignored) {
            registeredPrefs = null;
            preferenceChangeListener = null;
        }
    }

    private void unregisterPreferenceListener() {
        try {
            if (registeredPrefs != null && preferenceChangeListener != null) {
                registeredPrefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
            }
        } catch (Throwable ignored) {
        }
        registeredPrefs = null;
        preferenceChangeListener = null;
    }

    private void appendOutput(String message) {
        if (host != null) host.appendOutput(message);
    }

    private void postServerEvent(String message) {
        mainHandler.post(() -> {
            setStatus(message);
            appendOutput("[i] " + message);
            if (message != null && message.startsWith("Guest connected from ")) {
                toast("Multiplayer Link guest connected: " + message.substring("Guest connected from ".length()));
            } else if (message != null && (message.startsWith("Guest ping from ")
                    || message.startsWith("Guest test message from ")
                    || message.startsWith("Guest message from ")
                    || message.startsWith("Guest device info from ")
                    || message.startsWith("Guest requested Shared Objects from ")
                    || message.startsWith("Rejected guest action from ")
                    || message.startsWith("Unsupported guest action from "))) {
                toast("Multiplayer Link: " + compact(message));
            }
        });
    }

    private void setStatus(String text) {
        ActivityMainBinding b = binding();
        if (b == null || b.tabNetwork == null || b.tabNetwork.txtLinkStatus == null) return;
        b.tabNetwork.txtLinkStatus.setText(text == null ? "" : text);
    }

    private String currentStatusText() {
        ActivityMainBinding b = binding();
        if (b == null || b.tabNetwork == null || b.tabNetwork.txtLinkStatus == null) return "";
        CharSequence cs = b.tabNetwork.txtLinkStatus.getText();
        return cs == null ? "" : cs.toString();
    }

    private boolean isDebugOutputEnabled() {
        SharedPreferences sp = prefs();
        return sp != null && sp.getBoolean(SettingsPreferenceKeys.DEBUG_OUTPUT, false);
    }

    private void debug(String area, String message) {
        if (!isDebugOutputEnabled()) return;
        DebugLog.log(DebugLog.DEFAULT_TAG, "link", area, message);
        appendOutput(DebugLog.line("link", area, message) + "\n");
    }

    private void debugWarn(String area, String message) {
        if (!isDebugOutputEnabled()) return;
        DebugLog.warn(DebugLog.DEFAULT_TAG, "link", area, message);
        appendOutput(DebugLog.line("link", area, message) + "\n");
    }

    private static String textOf(TextView view) {
        if (view == null || view.getText() == null) return "";
        return view.getText().toString().trim();
    }

    private static void setEnabledSafe(View view, boolean enabled) {
        try { if (view != null) view.setEnabled(enabled); } catch (Throwable ignored) {}
    }

    private static int parseInt(String value, int def) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); } catch (Throwable t) { return def; }
    }

    private static String summarize(Throwable t) {
        if (t == null) return "unknown";
        String s = t.getMessage();
        if (TextUtils.isEmpty(s)) s = t.toString();
        return s == null ? "unknown" : s;
    }

    private void toast(String text) {
        Activity activity = host == null ? null : host.getActivity();
        if (activity == null || TextUtils.isEmpty(text)) return;
        try {
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private static String endpointLabel(String url) {
        try {
            URL u = new URL(url == null ? "" : url);
            String host = u.getHost();
            int port = u.getPort();
            if (TextUtils.isEmpty(host)) return url == null ? "" : url;
            return port > 0 ? host + ":" + port : host;
        } catch (Throwable ignored) {
            String s = url == null ? "" : url;
            s = s.replace("http://", "").replace("https://", "");
            int slash = s.indexOf('/');
            return slash >= 0 ? s.substring(0, slash) : s;
        }
    }

    private static String readableAction(String action) {
        if ("ping".equals(action)) return "ping";
        if ("test_message".equals(action)) return "test message";
        if ("message".equals(action)) return "text message";
        if ("client_info".equals(action)) return "device info";
        if ("shared_objects".equals(action)) return "Shared Objects";
        return TextUtils.isEmpty(action) ? "action" : action.replace('_', ' ');
    }

    private static String compact(String value) {
        String s = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > 180) s = s.substring(0, 180) + "...";
        return s;
    }
}
