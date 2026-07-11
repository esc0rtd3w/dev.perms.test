package dev.perms.test.shizuku.internal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Enumeration;

import dev.perms.test.wireless.WirelessDebuggingSettingsLauncher;

/**
 * Internal Shizuku: discover Wireless debugging pairing/connect ports via mDNS,
 * the same approach upstream Shizuku uses.
 *
 * This avoids OEM-specific Settings UI scraping.
 */
public final class InternalShizukuAdbMdnsService extends Service {

    private static final String ACTION_START = "dev.perms.test.action.INTERNAL_SHIZUKU_MDNS_START";
    private static final String ACTION_START_CONNECT_ONLY = "dev.perms.test.action.INTERNAL_SHIZUKU_MDNS_CONNECT_ONLY";
    private static final String ACTION_STOP = "dev.perms.test.action.INTERNAL_SHIZUKU_MDNS_STOP";
    private static final String ACTION_REPLY = "dev.perms.test.action.INTERNAL_SHIZUKU_MDNS_REPLY";
    private static final String ACTION_RESULT = "dev.perms.test.action.INTERNAL_SHIZUKU_MDNS_RESULT";

    private static final String EXTRA_PAIR_PORT = "pair_port";
    private static final String EXTRA_OK = "ok";
    private static final String EXTRA_MSG = "msg";

    // Match upstream Shizuku's RemoteInput key (note the typo).
    private static final String REMOTE_INPUT_RESULT_KEY = "paring_code";

    // Must match MainActivity prefs.
    private static final String PREFS = "perms_test";
    // Host address resolved from mDNS (used for adb pair/connect on OEMs where adbd is not bound to 127.0.0.1).
    private static final String PREF_KEY_INTERNAL_HOST = "internal_shizuku_host";
    // Raw mDNS connect port (best-effort). We only use this as a fallback if UI-based detection didn't populate connect port.
    private static final String PREF_KEY_INTERNAL_MDNS_CONNECT_PORT = "internal_shizuku_mdns_connect_port";

    private static final String NOTIF_CHANNEL = "internal_shizuku_pair";
    // Single notification (1:1 with upstream Shizuku): searching -> found -> working -> result.
    private static final int NOTIF_ID = 11000;

    // PendingIntent request IDs.
    private static final int REQ_REPLY = 1201;
    private static final int REQ_STOP = 1202;
    private static final int REQ_WIRELESS_SETTINGS = 1203;

    // Same service types as upstream Shizuku.
    private static final String TLS_PAIRING = "_adb-tls-pairing._tcp";
    private static final String TLS_CONNECT = "_adb-tls-connect._tcp";


private final Handler mainHandler = new Handler(Looper.getMainLooper());
private final Runnable stopRunnable = () -> {
    try { cleanup(); } catch (Throwable ignored) {}
    try { stopSelf(); } catch (Throwable ignored) {}
};

    private NsdManager nsd;
    private boolean discovering;
    private String lastPairServiceName;
    private String lastConnectServiceName;
    private volatile boolean inputInProgress;

    private final NsdManager.DiscoveryListener pairingListener = new NsdManager.DiscoveryListener() {
        @Override public void onDiscoveryStarted(String serviceType) {
            discovering = true;
            InternalShizukuDiagnostics.debug(InternalShizukuAdbMdnsService.this,
                    "mDNS pairing discovery started serviceType=" + serviceType);
        }

        @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            InternalShizukuDiagnostics.warn("mDNS pairing discovery failed serviceType=" + serviceType
                    + " error=" + errorCode);
            stopSelf();
        }

        @Override public void onDiscoveryStopped(String serviceType) {
            discovering = false;
        }

        @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
            InternalShizukuDiagnostics.debug(InternalShizukuAdbMdnsService.this,
                    "mDNS pairing service found name=" + (serviceInfo == null ? "null" : serviceInfo.getServiceName()));
            if (nsd == null) return;
            nsd.resolveService(serviceInfo, new ResolveListener(true));
        }

        @Override public void onServiceLost(NsdServiceInfo serviceInfo) {
        }
    };

    private final NsdManager.DiscoveryListener connectListener = new NsdManager.DiscoveryListener() {
        @Override public void onDiscoveryStarted(String serviceType) {
        }

        @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            InternalShizukuDiagnostics.warn("mDNS connect discovery failed serviceType=" + serviceType
                    + " error=" + errorCode);
        }

        @Override public void onDiscoveryStopped(String serviceType) {
        }

        @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
            InternalShizukuDiagnostics.debug(InternalShizukuAdbMdnsService.this,
                    "mDNS connect service found name=" + (serviceInfo == null ? "null" : serviceInfo.getServiceName()));
            if (nsd == null) return;
            nsd.resolveService(serviceInfo, new ResolveListener(false));
        }

        @Override public void onServiceLost(NsdServiceInfo serviceInfo) {
        }
    };

    private final class ResolveListener implements NsdManager.ResolveListener {
        private final boolean isPairing;
        ResolveListener(boolean isPairing) {
            this.isPairing = isPairing;
        }

        @Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            InternalShizukuDiagnostics.warn("mDNS resolve failed kind=" + (isPairing ? "pair" : "connect")
                    + " name=" + (serviceInfo == null ? "null" : serviceInfo.getServiceName())
                    + " error=" + errorCode);
        }

        @Override public void onServiceResolved(NsdServiceInfo serviceInfo) {
            try {
                if (serviceInfo == null || serviceInfo.getHost() == null) return;
                final String host = serviceInfo.getHost().getHostAddress();
                final int port = serviceInfo.getPort();
                InternalShizukuDiagnostics.debug(InternalShizukuAdbMdnsService.this,
                        "mDNS resolved kind=" + (isPairing ? "pair" : "connect")
                                + " host=" + host + " port=" + port
                                + " name=" + serviceInfo.getServiceName());
                if (port <= 0) return;
                // Only accept services bound to the local device (match upstream Shizuku).
                if (!isLocalAddress(host)) {
                    InternalShizukuDiagnostics.debug(InternalShizukuAdbMdnsService.this,
                            "mDNS ignored non-local host=" + host + " port=" + port);
                    return;
                }

                // Match upstream Shizuku's practical filter: only keep services whose port
                // is actually bound on this device.  Some OEM/NSD cycles can surface stale
                // connect advertisements shortly after Wireless debugging rotates ports.
                if (!isPortInUse("127.0.0.1", port) && !isPortInUse(host, port)) {
                    InternalShizukuDiagnostics.debug(InternalShizukuAdbMdnsService.this,
                            "mDNS ignored inactive port kind=" + (isPairing ? "pair" : "connect")
                                    + " host=" + host + " port=" + port);
                    return;
                }

                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE | MODE_MULTI_PROCESS);
                if (!InternalShizukuController.isSelected(sp)) return;

                // Some OEMs do not bind wireless debugging to 127.0.0.1; prefer the resolved local host.
                // Fall back to 127.0.0.1 if resolution fails elsewhere.
                try { sp.edit().putString(PREF_KEY_INTERNAL_HOST, host).apply(); } catch (Throwable ignored) {}

                if (isPairing) {
                    // Once user has submitted a code, don't overwrite the working/result notification.
                    if (inputInProgress) return;
                    lastPairServiceName = serviceInfo.getServiceName();
                    sp.edit().putInt(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_PAIR_PORT, port).apply();
                    InternalShizukuDiagnostics.debug(thisService(), "stored pairing port=" + port + " host=" + host);
                    notifyInput(port);
                } else {
                    lastConnectServiceName = serviceInfo.getServiceName();
                    try {
                        SharedPreferences.Editor ed = sp.edit()
                                .putInt(PREF_KEY_INTERNAL_MDNS_CONNECT_PORT, port)
                                .putInt(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_CONNECT_PORT, port);
                        int previousUsed = 0;
                        try { previousUsed = sp.getInt("internal_shizuku_connect_port_used", 0); } catch (Throwable ignored) {}
                        if (previousUsed > 0 && previousUsed != port) {
                            ed.remove("internal_shizuku_connect_port_used");
                        }
                        ed.apply();
                    } catch (Throwable ignored) {}
                    InternalShizukuDiagnostics.debug(thisService(), "stored current mDNS connect port=" + port + " host=" + host);
                }
            } catch (Throwable t) {
                InternalShizukuDiagnostics.warnVerbose(InternalShizukuAdbMdnsService.this,
                        "mDNS resolved-service handling failed", t);
            }
        }

        private Context thisService() {
            return InternalShizukuAdbMdnsService.this;
        }
    }

    public static void start(Context context) {
        if (context == null) return;
        Intent i = new Intent(context, InternalShizukuAdbMdnsService.class);
        i.setAction(ACTION_START);
        try {
            // Start as a normal service; we will elevate to FGS inside onStartCommand if allowed.
            context.startService(i);
        } catch (Throwable ignored) {
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            } catch (Throwable ignored2) {}
        }
    }

    public static void startConnectDiscovery(Context context) {
        if (context == null) return;
        Intent i = new Intent(context, InternalShizukuAdbMdnsService.class);
        i.setAction(ACTION_START_CONNECT_ONLY);
        try {
            context.startService(i);
        } catch (Throwable ignored) {
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            } catch (Throwable ignored2) {}
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        Intent i = new Intent(context, InternalShizukuAdbMdnsService.class);
        i.setAction(ACTION_STOP);
        context.startService(i);
    }

    /**
     * Terminal result from the internal adb process.
     * Updates the single pairing notification and stops the pairing service.
     */
    public static void postResult(Context context, String msg, boolean ok) {
        if (context == null) return;
        Intent i = new Intent(context, InternalShizukuAdbMdnsService.class);
        i.setAction(ACTION_RESULT);
        i.putExtra(EXTRA_OK, ok);
        i.putExtra(EXTRA_MSG, msg);
        try {
            context.startService(i);
        } catch (Throwable ignored) {
            // If service start is blocked, fall back to a direct notification.
            try {
                InternalShizukuAdbMdnsService.showResultNotification(context, msg, ok);
            } catch (Throwable ignored2) {}
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();

        ensureChannel();
        if (ACTION_STOP.equals(action)) {
            stopSearch();
            try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(NOTIF_ID);
            } catch (Throwable ignored) {}
            cleanup();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_RESULT.equals(action)) {
            final boolean ok = intent != null && intent.getBooleanExtra(EXTRA_OK, false);
            final String msg = intent == null ? null : intent.getStringExtra(EXTRA_MSG);
            inputInProgress = false;
            stopSearch();
            try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) {}
            showResultNotification(this, msg, ok);
            cleanup();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_REPLY.equals(action)) {
            final int port = intent == null ? -1 : intent.getIntExtra(EXTRA_PAIR_PORT, -1);
            InternalShizukuDiagnostics.debug(this, "mDNS notification reply received pairPort=" + port);
            String code = "";
            try {
                android.os.Bundle results = RemoteInput.getResultsFromIntent(intent);
                if (results != null) {
                    CharSequence cs = results.getCharSequence(REMOTE_INPUT_RESULT_KEY);
                    if (cs != null) code = cs.toString();
                }
            } catch (Throwable ignored) {}
            onInput(code, port);
            return START_NOT_STICKY;
        }

        final boolean pairMode = ACTION_START.equals(action);

        // Default: start/refresh search.
        InternalShizukuDiagnostics.debug(this, "mDNS service start/refresh action=" + action
                + " pairMode=" + pairMode);
        inputInProgress = false;

        // Match upstream Shizuku for explicit pairing.  Connect-only discovery preserves
        // the last known endpoint so an already paired profile can be started without
        // showing another pairing notification.
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE | MODE_MULTI_PROCESS);
            SharedPreferences.Editor ed = sp.edit()
                    .putInt(PREF_KEY_INTERNAL_MDNS_CONNECT_PORT, 0);
            if (pairMode) {
                ed.putInt(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_PAIR_PORT, 0)
                        .putInt(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_CONNECT_PORT, 0);
            }
            ed.apply();
        } catch (Throwable ignored) {}

        // Mirror upstream Shizuku: show a single foreground notification that will be updated in-place.
        final Notification searching = pairMode ? buildSearchingNotif() : buildConnectSearchingNotif();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, searching, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
            } else {
                startForeground(NOTIF_ID, searching);
            }
        } catch (Throwable t) {
            // Android 12+ may block starting FGS from background; fall back to a normal notification.
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIF_ID, searching);
            } catch (Throwable ignored) {}
        }

        if (Build.VERSION.SDK_INT < 30) {
            // Wireless debugging pairing is Android 11+.
            stopSelf();
            return START_NOT_STICKY;
        }

        if (nsd == null) nsd = getSystemService(NsdManager.class);
        if (nsd == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Restart discovery.
        cleanupDiscoveryOnly();
        if (pairMode) {
            try { nsd.discoverServices(TLS_PAIRING, NsdManager.PROTOCOL_DNS_SD, pairingListener); } catch (Throwable ignored) {}
        }
        try { nsd.discoverServices(TLS_CONNECT, NsdManager.PROTOCOL_DNS_SD, connectListener); } catch (Throwable ignored) {}

        scheduleStop();

        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

private void scheduleStop() {
    try { mainHandler.removeCallbacks(stopRunnable); } catch (Throwable ignored) {}
    // Stop after 60s to avoid background limits/restarts.
    try { mainHandler.postDelayed(stopRunnable, 60_000L); } catch (Throwable ignored) {}
}

    private void cleanupDiscoveryOnly() {
        try { if (nsd != null) nsd.stopServiceDiscovery(pairingListener); } catch (Throwable ignored) {}
        try { if (nsd != null) nsd.stopServiceDiscovery(connectListener); } catch (Throwable ignored) {}
    }

    private void stopSearch() {
        try { cleanupDiscoveryOnly(); } catch (Throwable ignored) {}
    }

    private void cleanup() {
        cleanupDiscoveryOnly();
        discovering = false;
        nsd = null;
        try { mainHandler.removeCallbacks(stopRunnable); } catch (Throwable ignored) {}
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildSearchingNotif() {
        PendingIntent piStop = PendingIntent.getService(
                this,
                REQ_STOP,
                new Intent(this, InternalShizukuAdbMdnsService.class).setAction(ACTION_STOP),
                pendingIntentFlags(false)
        );

        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(
                0,
                "Stop",
                piStop
        ).build();

        PendingIntent piSettings = WirelessDebuggingSettingsLauncher.pendingIntent(this, REQ_WIRELESS_SETTINGS);

        String title = "Internal Shizuku pairing";
        String text = "Searching for Wireless debugging pairing service…";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .addAction(stopAction)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (piSettings != null) {
            builder.setContentIntent(piSettings)
                    .addAction(android.R.drawable.ic_menu_manage, "Wireless Settings", piSettings);
        }
        return builder.build();
    }

    private Notification buildConnectSearchingNotif() {
        PendingIntent piStop = PendingIntent.getService(
                this,
                REQ_STOP,
                new Intent(this, InternalShizukuAdbMdnsService.class).setAction(ACTION_STOP),
                pendingIntentFlags(false)
        );

        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(
                0,
                "Stop",
                piStop
        ).build();

        PendingIntent piSettings = WirelessDebuggingSettingsLauncher.pendingIntent(this, REQ_WIRELESS_SETTINGS);

        String title = "Internal Shizuku discovery";
        String text = "Looking for the current Wireless debugging connect port…";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .addAction(stopAction)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (piSettings != null) {
            builder.setContentIntent(piSettings)
                    .addAction(android.R.drawable.ic_menu_manage, "Wireless Settings", piSettings);
        }
        return builder.build();
    }

    private Notification buildInputNotif(int pairPort) {
        RemoteInput ri = new RemoteInput.Builder(REMOTE_INPUT_RESULT_KEY)
                .setLabel("Pairing code (6 digits)")
                .build();

        Intent replyIntent = new Intent(this, InternalShizukuAdbMdnsService.class)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_PAIR_PORT, pairPort);

        PendingIntent piReply = PendingIntent.getForegroundService(
                this,
                REQ_REPLY,
                replyIntent,
                pendingIntentFlags(true)
        );

        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_input_add,
                "Enter code",
                piReply
        ).addRemoteInput(ri).build();

        PendingIntent piSettings = WirelessDebuggingSettingsLauncher.pendingIntent(this, REQ_WIRELESS_SETTINGS);

        String title = "Internal Shizuku pairing";
        String text = "Pairing port detected: " + pairPort + ". Enter code here (notification reply).";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(true)
                .setOnlyAlertOnce(false)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .addAction(replyAction)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (piSettings != null) {
            builder.setContentIntent(piSettings)
                    .addAction(android.R.drawable.ic_menu_manage, "Wireless Settings", piSettings);
        }
        return builder.build();
    }

    private Notification buildWorkingNotif() {
        PendingIntent piSettings = WirelessDebuggingSettingsLauncher.pendingIntent(this, REQ_WIRELESS_SETTINGS);
        String title = "Internal Shizuku pairing";
        String text = "Pairing/connecting…";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (piSettings != null) {
            builder.setContentIntent(piSettings)
                    .addAction(android.R.drawable.ic_menu_manage, "Wireless Settings", piSettings);
        }
        return builder.build();
    }

    private static void showResultNotification(Context context, String msg, boolean ok) {
        try {
            if (context == null) return;
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            String title = ok ? "Internal Shizuku: Success" : "Internal Shizuku: Failed";
            String text = (msg == null || msg.trim().isEmpty()) ? (ok ? "OK" : "Failed") : msg;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIF_CHANNEL)
                    .setSmallIcon(ok ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            PendingIntent settingsIntent = buildWirelessDebuggingSettingsPendingIntent(context, text);
            if (settingsIntent != null) {
                builder.setContentIntent(settingsIntent)
                        .addAction(android.R.drawable.ic_menu_manage, "Open Wireless debugging", settingsIntent);
            }

            Notification n = builder.build();

            nm.notify(NOTIF_ID, n);
        } catch (Throwable ignored) {
        }
    }


    private static PendingIntent buildWirelessDebuggingSettingsPendingIntent(Context context, String message) {
        if (context == null || TextUtils.isEmpty(message)) return null;
        String lower = message.toLowerCase(java.util.Locale.US);
        if (!lower.contains("wireless debugging") && !lower.contains("connect port")) return null;
        return WirelessDebuggingSettingsLauncher.pendingIntent(context, REQ_WIRELESS_SETTINGS);
    }

    private void notifyInput(int pairPort) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.notify(NOTIF_ID, buildInputNotif(pairPort));
        } catch (Throwable ignored) {
        }
    }

    private void onInput(String rawCode, int pairPort) {
        // If port wasn't provided (shouldn't happen), fall back to prefs.
        if (pairPort <= 0) {
            try {
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE | MODE_MULTI_PROCESS);
                pairPort = sp.getInt(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_PAIR_PORT, 0);
            } catch (Throwable ignored) {
            }
        }

        String code = rawCode == null ? "" : rawCode.trim();
        // Keep permissive but stable across keyboards.
        code = code.replaceAll("[^0-9]", "");

        if (pairPort <= 0) {
            // Still searching; re-show searching notification.
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIF_ID, buildSearchingNotif());
            } catch (Throwable ignored) {}
            return;
        }

        if (code.isEmpty()) {
            notifyInput(pairPort);
            return;
        }

        inputInProgress = true;

        // Update notification to "working".
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildWorkingNotif());
        } catch (Throwable ignored) {}

        // Gather ports from prefs (connect port may be filled by mDNS or other helper).
        int connectPort = 0;
        int mdnsConnectPort = 0;
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE | MODE_MULTI_PROCESS);
            try { connectPort = sp.getInt(InternalShizukuPairingAccessibilityService.PREF_KEY_INTERNAL_CONNECT_PORT, 0); } catch (Throwable ignored) {}
            try { mdnsConnectPort = sp.getInt(PREF_KEY_INTERNAL_MDNS_CONNECT_PORT, 0); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        // Prefer the resolved local host (some OEMs do not bind wireless debugging to 127.0.0.1).
        String host = "127.0.0.1";
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE | MODE_MULTI_PROCESS);
            String h = sp.getString(PREF_KEY_INTERNAL_HOST, null);
            if (h != null && !h.trim().isEmpty()) host = h.trim();
        } catch (Throwable ignored) {}

        // Hand off to the isolated internal adb process.
        InternalAdbService.enqueuePairConnectStart(getApplicationContext(), host, pairPort, connectPort, mdnsConnectPort, code);
    }

    private static int pendingIntentFlags(boolean mutable) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= (mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE);
        } else if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = nm.getNotificationChannel(NOTIF_CHANNEL);
            if (ch != null) return;
            nm.createNotificationChannel(new NotificationChannel(
                    NOTIF_CHANNEL,
                    "Internal Shizuku Pairing",
                    NotificationManager.IMPORTANCE_HIGH
            ));
        } catch (Throwable ignored) {}
    }

    private static boolean isLocalAddress(String addr) {
        if (addr == null) return false;
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs != null && addrs.hasMoreElements()) {
                    java.net.InetAddress ia = addrs.nextElement();
                    if (addr.equals(ia.getHostAddress())) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isPortInUse(String host, int port) {
        if (TextUtils.isEmpty(host) || port <= 0) return false;
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 350);
            return true;
        } catch (IOException ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        }
    }
}