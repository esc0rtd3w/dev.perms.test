package dev.perms.test.shizuku.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.ExecMode;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ladb.LadbClient;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

public final class InternalShizukuController {
    private static final String PREFS = "perms_test";
    public static final String PREF_KEY_INTERNAL_SHIZUKU_PAIR_PORT = InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_PAIR_PORT;
    public static final String PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT = InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_CONNECT_PORT;
    public static final String PREF_KEY_INTERNAL_SHIZUKU_HOST = "internal_shizuku_host";
    public static final String PREF_KEY_INTERNAL_SHIZUKU_CONNECTED = "internal_shizuku_connected";
    public static final String PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING = "internal_shizuku_server_running";
    public static final String PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID = "internal_shizuku_server_pid";
    public static final String PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED = "internal_shizuku_connect_port_used";
    public static final String PREF_KEY_INTERNAL_SHIZUKU_MDNS_CONNECT_PORT = "internal_shizuku_mdns_connect_port";
    private static final String PREF_KEY_PAIR_ARMED = "internal_shizuku_pair_armed";

    public interface Host {
        ExecMode getExecMode();
        void appendOutput(String text);
        void openWirelessDebuggingSettings();
        void applyExecModeUi();
        void refreshStatus();
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final SharedPreferences prefs;
    private final Host host;

    public InternalShizukuController(AppCompatActivity activity,
                                    ActivityMainBinding binding,
                                    SharedPreferences prefs,
                                    Host host) {
        this.activity = activity;
        this.binding = binding;
        this.prefs = prefs;
        this.host = host;
    }

    public static boolean isSelected(SharedPreferences prefs) {
        try {
            return prefs != null && ExecMode.get(prefs).isInternalShizuku();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean isSelected() {
        return isSelected(prefs);
    }

    public void bind() {
        try {
            if (binding == null || binding.tabMain == null) return;
            if (binding.tabMain.btnInternalShizukuPairStart != null) {
                binding.tabMain.btnInternalShizukuPairStart.setOnClickListener(v -> startPairFlow());
            }
            if (binding.tabMain.btnInternalShizukuStartServer != null) {
                binding.tabMain.btnInternalShizukuStartServer.setOnClickListener(v -> requestStartServer());
            }
            if (binding.tabMain.btnInternalShizukuStopServer != null) {
                binding.tabMain.btnInternalShizukuStopServer.setOnClickListener(v -> requestStopServer());
            }
            if (binding.tabMain.btnInternalShizukuWirelessSettings != null) {
                binding.tabMain.btnInternalShizukuWirelessSettings.setOnClickListener(v -> openWirelessSettingsForDiscovery());
            }
        } catch (Throwable ignored) {
        }
    }

    public static int getBestConnectPort(SharedPreferences sp) {
        try {
            if (sp == null) return 0;
            int port = 0;
            try { port = sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT_USED, 0); } catch (Throwable ignored) {}
            if (port <= 0) {
                try { port = sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_MDNS_CONNECT_PORT, 0); } catch (Throwable ignored) {}
            }
            if (port <= 0) {
                try { port = sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT, 0); } catch (Throwable ignored) {}
            }
            return Math.max(port, 0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean hasEndpoint(SharedPreferences sp) {
        try {
            if (sp == null) return false;
            String value = null;
            try { value = sp.getString(PREF_KEY_INTERNAL_SHIZUKU_HOST, null); } catch (Throwable ignored) {}
            if (TextUtils.isEmpty(value)) value = "127.0.0.1";
            return !TextUtils.isEmpty(value) && getBestConnectPort(sp) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }


    public void resetServerStateAndStartDiscovery() {
        clearClientBinder();
        boolean wasRunning = false;
        try {
            SharedPreferences sp = getPrefs();
            if (sp != null) {
                wasRunning = sp.getBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false);
                sp.edit()
                        .putBoolean(PREF_KEY_PAIR_ARMED, false)
                        .apply();
            }
        } catch (Throwable ignored) {
        }
        host.appendOutput("[i] Internal Shizuku enabled - looking for the current Wireless debugging connect port.\n");
        try {
            InternalShizukuAdbMdnsService.startConnectDiscovery(activity.getApplicationContext());
        } catch (Throwable t) {
            host.appendOutput("[!] Failed to start internal discovery helper: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
        }
        if (wasRunning) {
            boolean embeddedBinderAlive = false;
            try { embeddedBinderAlive = ShizukuProvider.hasEmbeddedBinder(); } catch (Throwable ignored) {}
            if (embeddedBinderAlive) {
                host.appendOutput("[internal-shizuku] Internal server is already running and binder is attached.\n");
            } else if (hasEndpoint(getPrefs())) {
                host.appendOutput("[internal-shizuku] Internal server is marked running, but binder is not attached. Restarting the internal server.\n");
                try { InternalAdbService.enqueueStartServer(activity, true); } catch (Throwable ignored) {}
            } else {
                host.appendOutput("[internal-shizuku] Internal server is marked running, but no endpoint is available. Pair & Start is required.\n");
            }
        } else if (hasEndpoint(getPrefs())) {
            host.appendOutput("[internal-shizuku] Existing endpoint found. Starting server.\n");
            try { InternalAdbService.enqueueStartServer(activity, false); } catch (Throwable ignored) {}
        } else {
            host.appendOutput("[internal-shizuku] Pair & Start is required once if no paired profile exists.\n");
        }
        try { host.refreshStatus(); } catch (Throwable ignored) {}
    }

    public void startPairFlow() {
        try {
            SharedPreferences sp = getPrefs();
            if (sp != null) {
                sp.edit().putBoolean(PREF_KEY_PAIR_ARMED, true).apply();
            }
        } catch (Throwable ignored) {}
        try {
            InternalShizukuAdbMdnsService.start(activity);
        } catch (Throwable ignored) {
        }
        host.openWirelessDebuggingSettings();
    }

    public void openWirelessSettingsForDiscovery() {
        try {
            if (!isSelected()) {
                host.appendOutput("[internal-shizuku] Select Internal Shizuku exec mode before opening Wireless debugging for Internal Shizuku.\n");
                host.applyExecModeUi();
                return;
            }
            host.appendOutput("[internal-shizuku] Opening Wireless debugging settings and watching for the current connect port.\n");
            try {
                InternalShizukuAdbMdnsService.startConnectDiscovery(activity.getApplicationContext());
            } catch (Throwable t) {
                host.appendOutput("[!] Failed to start internal discovery helper: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            }
            host.openWirelessDebuggingSettings();
        } catch (Throwable ignored) {
        }
    }

    public void requestStartServer() {
        try {
            clearClientBinder();
            final boolean shiz = host.getExecMode() != null && host.getExecMode().isInternalShizuku();
            if (!shiz) {
                host.appendOutput("[internal-shizuku] Select Internal Shizuku exec mode first.\n");
                host.applyExecModeUi();
                return;
            }
            if (!hasEndpoint(getPrefs())) {
                host.appendOutput("[internal-shizuku] No connect endpoint yet. Opening Wireless debugging so the app can discover the current port.\n");
                try { InternalShizukuAdbMdnsService.startConnectDiscovery(activity.getApplicationContext()); } catch (Throwable ignored) {}
                try { host.openWirelessDebuggingSettings(); } catch (Throwable ignored) {}
                host.applyExecModeUi();
                return;
            }
            boolean wasRunning = false;
            try {
                SharedPreferences sp = getPrefs();
                if (sp != null) {
                    wasRunning = sp.getBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false);
                    sp.edit()
                            .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false)
                            .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0)
                            .apply();
                }
            } catch (Throwable ignored) {}
            host.appendOutput(wasRunning
                    ? "[internal-shizuku] Restart server requested.\n"
                    : "[internal-shizuku] Start server requested.\n");
            InternalAdbService.enqueueStartServer(activity, wasRunning);
            activity.runOnUiThread(() -> { try { host.applyExecModeUi(); } catch (Throwable ignored) {} });
            scheduleUiRefresh();
        } catch (Throwable ignored) {
        }
    }

    public void requestStopServer() {
        try {
            final boolean shiz = host.getExecMode() != null && host.getExecMode().isInternalShizuku();
            if (!shiz) {
                host.appendOutput("[internal-shizuku] Select Internal Shizuku exec mode first.\n");
                host.applyExecModeUi();
                return;
            }
            host.appendOutput("[internal-shizuku] Stop server requested.\n");
            clearClientBinder();
            try {
                SharedPreferences sp = getPrefs();
                if (sp != null) {
                    sp.edit()
                            .putBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false)
                            .putInt(PREF_KEY_INTERNAL_SHIZUKU_SERVER_PID, 0)
                            .apply();
                }
            } catch (Throwable ignored) {}
            InternalAdbService.enqueueStopServer(activity);
            activity.runOnUiThread(() -> { try { host.applyExecModeUi(); } catch (Throwable ignored) {} });
            scheduleUiRefresh();
        } catch (Throwable ignored) {
        }
    }

    public void showPairDialog() {
        try {
            final EditText edt = new EditText(activity);
            edt.setHint("6-digit pairing code");
            edt.setInputType(InputType.TYPE_CLASS_NUMBER);

            final SharedPreferences sp = getPrefs();
            final int pairPort = sp == null ? 0 : sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_PAIR_PORT, 0);
            final int connectPort = sp == null
                    ? ExecMode.LADB_DEFAULT_CONNECT_PORT
                    : sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT);

            final String msg = "Open: Settings → Developer options → Wireless debugging → Pair device with pairing code.\n\n"
                    + "Detected pairing port: " + (pairPort > 0 ? pairPort : "(not detected yet)") + "\n"
                    + "Connect port: " + connectPort + "\n\n"
                    + "If the pairing port shows as not detected, enable the PermsTest Accessibility helper and reopen the pairing screen.";

            android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(activity)
                    .setTitle("Internal Shizuku: Pair & Start")
                    .setMessage(msg)
                    .setView(edt)
                    .setNegativeButton("Close", null)
                    .setNeutralButton("Open Wireless debugging", (d, w) -> host.openWirelessDebuggingSettings())
                    .setPositiveButton("Pair & Start", (d, w) -> {
                        final String code = edt.getText() == null ? "" : edt.getText().toString().trim();
                        pairAndStart(code);
                    })
                    .create();
            dlg.setOnShowListener(di -> {
                try {
                    android.widget.Button p = dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                    if (p != null) p.setEnabled(true);
                } catch (Throwable ignored) {}
            });
            dlg.show();

            if (!isAccessibilityEnabled()) {
                host.appendOutput("[internal-shizuku] Accessibility helper not enabled. Enable it if you want auto-detect pairing port.\n");
            }
        } catch (Throwable ignored) {
        }
    }

    private void clearClientBinder() {
        try {
            if (ShizukuProvider.isCurrentBinderEmbedded()) {
                Shizuku.onBinderReceived(null, activity.getPackageName());
                ShizukuProvider.clearBinderSource();
            }
        } catch (Throwable ignored) {
        }
    }

    private void scheduleUiRefresh() {
        try {
            Handler h = new Handler(Looper.getMainLooper());
            h.postDelayed(() -> {
                try { host.applyExecModeUi(); } catch (Throwable ignored) {}
                try { host.refreshStatus(); } catch (Throwable ignored) {}
            }, 1500L);
            h.postDelayed(() -> {
                try { host.applyExecModeUi(); } catch (Throwable ignored) {}
                try { host.refreshStatus(); } catch (Throwable ignored) {}
            }, 4000L);
        } catch (Throwable ignored) {
        }
    }

    private SharedPreferences getPrefs() {
        try {
            return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        } catch (Throwable ignored) {
            return prefs;
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            String expected = activity.getPackageName() + "/" + InternalShizukuPairingAccessibilityService.class.getName();
            String enabled = android.provider.Settings.Secure.getString(
                    activity.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(expected);
        } catch (Throwable t) {
            return false;
        }
    }

    private void pairAndStart(final String code) {
        final SharedPreferences sp = getPrefs();
        final int pairPort = sp == null ? 0 : sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_PAIR_PORT, 0);
        final int connectPort = sp == null
                ? ExecMode.LADB_DEFAULT_CONNECT_PORT
                : sp.getInt(PREF_KEY_INTERNAL_SHIZUKU_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT);

        if (TextUtils.isEmpty(code) || code.length() < 4) {
            host.appendOutput("[internal-shizuku] Enter pairing code.\n");
            return;
        }
        if (pairPort <= 0) {
            host.appendOutput("[internal-shizuku] Pairing port not detected yet. Open the pairing screen so PermsTest can read the port.\n");
            return;
        }

        host.appendOutput("[internal-shizuku] Pairing (127.0.0.1:" + pairPort + ")...\n");
        runOnBackgroundThread(() -> {
            try {
                LadbClient client = new LadbClient(activity.getApplicationContext());
                LadbClient.CmdResult r1 = client.pair(LadbClient.DEFAULT_HOST, pairPort, code);
                if (r1 == null || r1.exitCode != 0) {
                    host.appendOutput("[internal-shizuku] Pair failed: " + (r1 == null ? "unknown" : r1.toString()) + "\n");
                    return;
                }
                LadbClient.CmdResult r2 = client.connect(LadbClient.DEFAULT_HOST, connectPort);
                if (r2 == null || r2.exitCode != 0) {
                    host.appendOutput("[internal-shizuku] Connect failed: " + (r2 == null ? "unknown" : r2.toString()) + "\n");
                    return;
                }

                host.appendOutput("[internal-shizuku] Connected. Starting internal server...\n");
                LadbClient.CmdResult r3 = client.shellShC(buildAppProcessCmd());
                // app_process may keep running; exit code may vary. Log output for debugging.
                host.appendOutput("[internal-shizuku] Start result: " + (r3 == null ? "unknown" : r3.toString()) + "\n");

                try { Thread.sleep(800); } catch (Throwable ignored) {}
                activity.runOnUiThread(() -> { try { host.refreshStatus(); } catch (Throwable ignored) {} });
            } catch (Throwable t) {
                host.appendOutput("[internal-shizuku] Error: " + t + "\n");
            }
        });
    }

    public String buildAdbCommand() {
        try {
            final String pkg = activity.getPackageName();
            ApplicationInfo info = activity.getApplicationInfo();
            final String apkPath = info == null ? null : info.sourceDir;
            final String libPath = info == null ? null : info.nativeLibraryDir;
            if (TextUtils.isEmpty(apkPath) || TextUtils.isEmpty(libPath)) {
                return "(unable to resolve APK/lib path)";
            }

            // This is the same core startup mechanism as upstream Shizuku (app_process under shell).
            // Do not try to run this through installed-mode Shizuku.
            final String sh = "APK=\"" + apkPath + "\"; LIB=\"" + libPath + "\"; "
                    + "CLASSPATH=\"$APK\" /system/bin/app_process "
                    + "-Dshizuku.manager.package=" + pkg + " "
                    + "-Dshizuku.permission=" + pkg + ".permission.SHIZUKU_API_V23 "
                    + "-Dshizuku.library.path=\"$LIB\" "
                    + "/system/bin --nice-name=shizuku_server "
                    + "rikka.shizuku.server.ShizukuService "
                    + "--manager-package=" + pkg + " "
                    + "--permission=" + pkg + ".permission.SHIZUKU_API_V23 "
                    + "--library-path=\"$LIB\"";

            return "adb shell \"" + sh.replace("\"", "\\\"") + "\"";
        } catch (Throwable t) {
            return "(error building command: " + t + ")";
        }
    }

    private String buildAppProcessCmd() {
        try {
            final String pkg = activity.getPackageName();
            ApplicationInfo info = activity.getApplicationInfo();
            final String apkPath = info == null ? null : info.sourceDir;
            final String libPath = info == null ? null : info.nativeLibraryDir;
            if (TextUtils.isEmpty(apkPath) || TextUtils.isEmpty(libPath)) {
                return "echo internal-shizuku: unable to resolve APK/lib path";
            }

            return "APK=\"" + apkPath + "\"; LIB=\"" + libPath + "\"; "
                    + "CLASSPATH=\"$APK\" /system/bin/app_process "
                    + "-Dshizuku.manager.package=" + pkg + " "
                    + "-Dshizuku.permission=" + pkg + ".permission.SHIZUKU_API_V23 "
                    + "-Dshizuku.library.path=\"$LIB\" "
                    + "/system/bin --nice-name=shizuku_server "
                    + "rikka.shizuku.server.ShizukuService "
                    + "--manager-package=" + pkg + " "
                    + "--permission=" + pkg + ".permission.SHIZUKU_API_V23 "
                    + "--library-path=\"$LIB\"";
        } catch (Throwable t) {
            return "echo internal-shizuku: error building cmd";
        }
    }

    private void runOnBackgroundThread(Runnable r) {
        try {
            new Thread(r, "PermsTest-internal-shizuku").start();
        } catch (Throwable ignored) {
        }
    }
}
