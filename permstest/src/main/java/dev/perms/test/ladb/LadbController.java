package dev.perms.test.ladb;

import dev.perms.test.ExecMode;
import dev.perms.test.MainActivity;
import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.wireless.WirelessDebuggingSettingsLauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Owns the LADB controls on the Main tab.
 * Runtime behavior intentionally matches the old MainActivity implementation.
 */
public final class LadbController {
    public static final String DEFAULT_HOST = LadbClient.DEFAULT_HOST;

    private static final String NOTIF_CHANNEL_LADB_PAIR = "ladb_pair";
    private static final int NOTIF_ID_LADB_PAIR = 10021;
    private static final int REQ_POST_NOTIFICATIONS_LADB = 10050;
    private static final String PREF_KEY_LADB_PAIR_HELPER_NOTIFICATION = "ladb_pair_helper_notification";

    public interface Host {
        ExecMode getExecMode();
        void refreshStatus();
        void toast(String message);
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding b;
    private final SharedPreferences prefs;
    private final ExecutorService io;
    private final Host host;

    private LadbClient client;
    private volatile boolean connected;
    private boolean pendingPairNotification;

    public LadbController(AppCompatActivity activity,
                          ActivityMainBinding binding,
                          SharedPreferences prefs,
                          ExecutorService io,
                          Host host) {
        this.activity = activity;
        this.b = binding;
        this.prefs = prefs;
        this.io = io;
        this.host = host;
    }

    public void bind() {
        try {
            View ladbRoot = b.tabMain.includeExecModeLadb.getRoot();
            View btnWirelessDebug = ladbRoot.findViewById(R.id.btnOpenWirelessDebug);
            if (btnWirelessDebug != null) {
                btnWirelessDebug.setOnClickListener(v -> openWirelessDebuggingSettings());
            }

            try {
                b.tabMain.includeExecModeLadb.chkPairHelperNotification.setChecked(
                        prefs.getBoolean(PREF_KEY_LADB_PAIR_HELPER_NOTIFICATION, true));
                b.tabMain.includeExecModeLadb.chkPairHelperNotification.setOnCheckedChangeListener((btnView, checked) ->
                        prefs.edit().putBoolean(PREF_KEY_LADB_PAIR_HELPER_NOTIFICATION, checked).apply());
            } catch (Throwable ignored) {}

            View btnPairHelper = ladbRoot.findViewById(R.id.btnOpenPairHelper);
            if (btnPairHelper != null) {
                btnPairHelper.setOnClickListener(v -> {
                    boolean useNotification = true;
                    try {
                        useNotification = b.tabMain.includeExecModeLadb.chkPairHelperNotification.isChecked();
                    } catch (Throwable ignored) {}
                    if (useNotification) {
                        showPairNotification();
                    } else {
                        openPairOverlay();
                    }
                });
            }

            try { refreshWirelessDebugInfo(); } catch (Throwable ignored) {}

            b.tabMain.includeExecModeLadb.chkLadbAutoConnect.setChecked(
                    prefs.getBoolean(ExecMode.PREF_KEY_LADB_AUTOCONNECT, true));
            b.tabMain.includeExecModeLadb.chkLadbAutoConnect.setOnCheckedChangeListener((btn, checked) ->
                    prefs.edit().putBoolean(ExecMode.PREF_KEY_LADB_AUTOCONNECT, checked).apply());

            b.tabMain.includeExecModeLadb.edtLadbConnectPort.setText(String.valueOf(
                    prefs.getInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT)));
            b.tabMain.includeExecModeLadb.edtLadbPairPort.setText(String.valueOf(
                    prefs.getInt(ExecMode.PREF_KEY_LADB_PAIR_PORT, 0)));
            b.tabMain.includeExecModeLadb.edtLadbPairCode.setText(
                    prefs.getString(ExecMode.PREF_KEY_LADB_PAIR_CODE, ""));

            b.tabMain.includeExecModeLadb.btnLadbPair.setOnClickListener(v -> pairFromFields());
            b.tabMain.includeExecModeLadb.btnLadbPairNotif.setOnClickListener(v -> pairFromSavedNotificationInput());
            b.tabMain.includeExecModeLadb.btnLadbConnect.setOnClickListener(v -> connect());
            b.tabMain.includeExecModeLadb.btnLadbDisconnect.setOnClickListener(v -> disconnect());
        } catch (Throwable ignored) {
        }
    }

    public void applyExecModeUi(ExecMode mode) {
        try {
            int ladbVis = (mode == ExecMode.LADB ? View.VISIBLE : View.GONE);
            b.tabMain.includeExecModeLadb.getRoot().setVisibility(ladbVis);
            try { b.tabMain.txtLadbOptionsHeader.setVisibility(ladbVis); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    public void maybeAutoConnect() {
        try {
            if (host.getExecMode() != ExecMode.LADB) return;
            if (!prefs.getBoolean(ExecMode.PREF_KEY_LADB_AUTOCONNECT, true)) return;
            if (connected) return;
            connect();
        } catch (Throwable ignored) {
        }
    }

    public boolean handleRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode != REQ_POST_NOTIFICATIONS_LADB) return false;
        try {
            boolean granted = grantResults != null
                    && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingPairNotification) {
                pendingPairNotification = false;
                showPairNotification();
            } else {
                pendingPairNotification = false;
            }
        } catch (Throwable ignored) {
            pendingPairNotification = false;
        }
        return true;
    }

    public boolean isConnected() {
        return connected;
    }

    public LadbClient.CmdResult rawAdb(List<String> args) {
        return getClient().rawAdb(args);
    }

    public LadbClient.CmdResult shellShC(String shCmd) {
        return getClient().shellShC(shCmd);
    }

    public LadbClient getClientForShell() {
        return getClient();
    }

    public void openWirelessDebuggingSettings() {
        WirelessDebuggingSettingsLauncher.open(activity);
    }

    private LadbClient getClient() {
        if (client == null) client = new LadbClient(activity);
        return client;
    }

    private void setStatus(String s) {
        try {
            b.tabMain.includeExecModeLadb.txtLadbStatus.setText(s == null ? "" : s);
        } catch (Throwable ignored) {
        }
    }

    private void pairFromFields() {
        int pairPort = safeParseInt(textOf(b.tabMain.includeExecModeLadb.edtLadbPairPort), 0);
        String code = textOf(b.tabMain.includeExecModeLadb.edtLadbPairCode);
        pair(pairPort, code);
    }

    private void pairFromSavedNotificationInput() {
        try {
            int pairPort = prefs.getInt(ExecMode.PREF_KEY_LADB_PAIR_PORT, 0);
            String code = prefs.getString(ExecMode.PREF_KEY_LADB_PAIR_CODE, "");
            if (pairPort <= 0 || TextUtils.isEmpty(code)) {
                host.toast("No saved Pair port/code. Use Pair Helper (Notification) first.");
                return;
            }
            try { b.tabMain.includeExecModeLadb.edtLadbPairPort.setText(String.valueOf(pairPort)); } catch (Throwable ignored) {}
            try { b.tabMain.includeExecModeLadb.edtLadbPairCode.setText(code); } catch (Throwable ignored) {}
            pair(pairPort, code);
        } catch (Throwable t) {
            host.toast("Unable to read saved Pair input.");
        }
    }

    private void pair(int pairPort, String code) {
        if (pairPort <= 0 || TextUtils.isEmpty(code)) {
            host.toast("Enter Pair port and Pair code.");
            return;
        }

        String pairCode = code.trim();
        prefs.edit()
                .putInt(ExecMode.PREF_KEY_LADB_PAIR_PORT, pairPort)
                .putString(ExecMode.PREF_KEY_LADB_PAIR_CODE, pairCode)
                .apply();

        setStatus("Pairing...");
        io.execute(() -> {
            LadbClient.CmdResult r = getClient().pair(LadbClient.DEFAULT_HOST, pairPort, pairCode);
            activity.runOnUiThread(() -> {
                String msg = "pair: exit=" + r.exitCode;
                String out = trimOneLine(r.stdout);
                String err = trimOneLine(r.stderr);
                if (!TextUtils.isEmpty(out)) msg += "\n" + out;
                if (!TextUtils.isEmpty(err)) msg += "\n" + err;
                setStatus(msg);
            });
        });
    }

    private void connect() {
        final int port = safeParseInt(textOf(b.tabMain.includeExecModeLadb.edtLadbConnectPort), ExecMode.LADB_DEFAULT_CONNECT_PORT);
        prefs.edit().putInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, port).apply();

        setStatus("Connecting...");
        io.execute(() -> {
            LadbClient.CmdResult r = getClient().connect(LadbClient.DEFAULT_HOST, port);
            boolean ok = r.exitCode == 0
                    && (r.stdout.toLowerCase(Locale.US).contains("connected")
                    || r.stderr.toLowerCase(Locale.US).contains("connected"));
            connected = ok;
            activity.runOnUiThread(() -> {
                setStatus("connect: exit=" + r.exitCode + "\n" + trimOneLine(r.stdout)
                        + (TextUtils.isEmpty(r.stderr) ? "" : ("\n" + trimOneLine(r.stderr))));
                host.refreshStatus();
            });
        });
    }

    private void disconnect() {
        final int port = prefs.getInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT);

        setStatus("Disconnecting...");
        io.execute(() -> {
            LadbClient.CmdResult r = getClient().disconnect(LadbClient.DEFAULT_HOST, port);
            connected = false;
            activity.runOnUiThread(() -> {
                setStatus("disconnect: exit=" + r.exitCode + "\n" + trimOneLine(r.stdout)
                        + (TextUtils.isEmpty(r.stderr) ? "" : ("\n" + trimOneLine(r.stderr))));
                host.refreshStatus();
            });
        });
    }

    private void showPairNotification() {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                try {
                    pendingPairNotification = true;
                    ActivityCompat.requestPermissions(activity,
                            new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                            REQ_POST_NOTIFICATIONS_LADB);
                } catch (Throwable ignored) {}
                host.toast("Notifications permission is required for Pair Helper notification.");
                return;
            }

            ensurePairNotificationChannel();

            Intent open = new Intent(activity, MainActivity.class);
            int contentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) contentFlags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent contentPi = PendingIntent.getActivity(activity, 1000, open, contentFlags);

            Intent intent = new Intent(activity, LadbPairInputReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= PendingIntent.FLAG_MUTABLE;
            } else if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(activity, 1001, intent, flags);

            RemoteInput ri = new RemoteInput.Builder(LadbPairInputReceiver.KEY_LADB_PAIR_INPUT)
                    .setLabel("Enter: <pairPort> <pairCode>")
                    .build();

            Notification.Action action = null;
            if (Build.VERSION.SDK_INT >= 23) {
                Icon icon = Icon.createWithResource(activity, android.R.drawable.ic_input_add);
                action = new Notification.Action.Builder(icon, "Submit Pair", pi)
                        .addRemoteInput(ri)
                        .build();
            }

            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nb = new Notification.Builder(activity, NOTIF_CHANNEL_LADB_PAIR);
            } else {
                nb = new Notification.Builder(activity);
                nb.setPriority(Notification.PRIORITY_HIGH);
            }

            nb.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("LADB Pair Helper")
                    .setContentText("Enter: <pairPort> <pairCode>  (optional: <connectPort>)")
                    .setContentIntent(contentPi)
                    .setStyle(new Notification.BigTextStyle().bigText(
                            "Enter the *pairing* port and code from the Wireless debugging pairing dialog.\n"
                                    + "Optional: add the connect port at the end (shown on the main Wireless debugging screen).\n\n"
                                    + "Examples:\n  37123 123456\n  37123 123456 5555"))
                    .setOngoing(true)
                    .setAutoCancel(false);

            if (action != null) nb.addAction(action);

            NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIF_ID_LADB_PAIR, nb.build());
                host.toast("Pair Helper notification posted.");
            } else {
                host.toast("Notification service unavailable.");
            }
        } catch (Throwable t) {
            try { host.toast("Failed to post Pair Helper notification: " + t.getClass().getSimpleName()); } catch (Throwable ignored) {}
        }
    }

    private void ensurePairNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = nm.getNotificationChannel(NOTIF_CHANNEL_LADB_PAIR);
            if (ch != null) return;
            ch = new NotificationChannel(NOTIF_CHANNEL_LADB_PAIR, "LADB Pair", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Notification channel for LADB inline pairing input");
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
        }
    }

    private void openPairOverlay() {
        try {
            showPairOverlayHelper();
        } catch (Throwable ignored) {
        }
    }

    private void showPairOverlayHelper() {
        try {
            if (!Settings.canDrawOverlays(activity)) {
                host.toast("Enable 'Display over other apps' for Pair Helper (one-time)");
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(i);
                } catch (Throwable ignored) {
                }
                return;
            }

            Intent svc = new Intent(activity, LadbPairOverlayService.class);
            svc.putExtra(LadbPairOverlayService.EXTRA_PAIR_PORT,
                    safeParseInt(textOf(b.tabMain.includeExecModeLadb.edtLadbPairPort), ExecMode.LADB_DEFAULT_PAIR_PORT));
            svc.putExtra(LadbPairOverlayService.EXTRA_PAIR_CODE,
                    textOf(b.tabMain.includeExecModeLadb.edtLadbPairCode));

            try {
                activity.startService(svc);
            } catch (Throwable t) {
                try { activity.startService(svc); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    private void refreshWirelessDebugInfo() {
        try {
            String ip = getWifiIpString();
            String tlsPort = readProp("service.adb.tls.port");
            String tcpPort = readProp("service.adb.tcp.port");
            if (tcpPort.isEmpty()) tcpPort = readProp("service.adb.tcp.port");

            StringBuilder sb = new StringBuilder();
            sb.append("Wireless Debugging: ");
            boolean any = false;
            if (!ip.isEmpty()) {
                sb.append("IP ").append(ip);
                any = true;
            }
            if (!tlsPort.isEmpty()) {
                sb.append(any ? " | " : "").append("Pair port ").append(tlsPort);
                any = true;
            }
            if (!tcpPort.isEmpty()) {
                sb.append(any ? " | " : "").append("Connect port ").append(tcpPort);
                any = true;
            }
            if (!any) sb.append("(unknown / disabled)");

            try {
                View ladbRoot = b.tabMain.includeExecModeLadb.getRoot();
                TextView tv = ladbRoot.findViewById(R.id.txtWirelessDebugInfo);
                if (tv != null) tv.setText(sb.toString());
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    private String readProp(String key) {
        if (key == null) return "";
        BufferedReader br = null;
        try {
            Process p = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            try { p.waitFor(); } catch (Throwable ignored) {}
            return line == null ? "" : line.trim();
        } catch (Throwable ignored) {
            return "";
        } finally {
            try { if (br != null) br.close(); } catch (Throwable ignored) {}
        }
    }

    private String getWifiIpString() {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "";
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            if (info == null) return "";
            int ip = info.getIpAddress();
            if (ip == 0) return "";
            return String.format(Locale.US, "%d.%d.%d.%d",
                    (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String textOf(TextView tv) {
        try {
            CharSequence s = tv == null ? null : tv.getText();
            return s == null ? "" : s.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int safeParseInt(String s, int def) {
        try {
            if (s == null) return def;
            s = s.trim();
            if (s.isEmpty()) return def;
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return def;
        }
    }

    private static String trimOneLine(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() > 300) s = s.substring(0, 300) + "...";
        return s;
    }
}
