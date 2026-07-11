package dev.perms.test.network.http;

import dev.perms.test.network.NetworkPreferenceKeys;
import dev.perms.test.network.NetworkAddressFormatter;
import dev.perms.test.network.ftp.*;

import dev.perms.test.MainActivity;
import dev.perms.test.network.web.PermsTestWebInterface;
import dev.perms.test.network.web.PermsTestWebMemoryApi;
import dev.perms.test.settings.SettingsPreferenceKeys;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Foreground HTTP/Web Interface host used when Network HTTP background or
 * sleep keep-alive mode is enabled.
 */
public final class PermsTestHttpService extends Service {
    public static final String ACTION_START = "dev.perms.test.action.START_HTTP_SERVICE";
    public static final String ACTION_STOP = "dev.perms.test.action.STOP_HTTP_SERVICE";
    private static final String ACTION_RESTORE_NOTIFICATION = "dev.perms.test.action.RESTORE_HTTP_NOTIFICATION";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_ROOT = "root";
    public static final String EXTRA_TLS = "tls";
    public static final String EXTRA_TLS_KEYSTORE = "tlsKeystore";
    public static final String EXTRA_TLS_PASSWORD = "tlsPassword";
    public static final String EXTRA_DIRECTORY_LISTING = "directoryListing";
    public static final String EXTRA_WEB_INTERFACE = "webInterface";
    public static final String EXTRA_WEB_TOKEN = "webToken";
    public static final String EXTRA_KEEP_ALIVE_SLEEP = "keepAliveSleep";
    public static final String EXTRA_DEBUG = "debug";

    private static final String CHANNEL_ID = "http_server_status_v1";
    private static final int NOTIFICATION_ID = 70424;
    private static final Object STATUS_LOCK = new Object();
    private static final long NOTIFICATION_WATCHDOG_MS = 30000L;
    private static final int MAX_SERVICE_OUTPUT = 12000;
    private static final String DEFAULT_ROOT = "/storage/emulated/0/dev.perms.test/http";
    private static final String DEFAULT_INDEX = "<!doctype html>\n"
            + "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>PermsTest HTTP Server</title></head><body><h1>PermsTest HTTP Server</h1>"
            + "<p>Default background HTTP root page.</p></body></html>\n";

    private static boolean statusStarting;
    private static boolean statusRunning;
    private static int statusPort;
    private static String statusRoot = "";
    private static boolean statusTls;
    private static boolean statusDirectoryListing;
    private static boolean statusWebInterface;
    private static boolean statusKeepAliveSleep;
    private static String statusLastError = "";
    private static final StringBuilder serviceOutput = new StringBuilder();

    private PermsTestHttpServer server;
    private Thread startThread;
    private volatile boolean stopRequested;
    private boolean keepLastErrorOnDestroy;
    private boolean debugOutput;
    private PowerManager.WakeLock httpWakeLock;
    private WifiManager.WifiLock httpWifiLock;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable notificationWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (stopRequested) return;
            updateNotification();
            mainHandler.postDelayed(this, NOTIFICATION_WATCHDOG_MS);
        }
    };
    private boolean notificationWatchdogRunning;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            setStoppedWithError("HTTP service restart ignored: no active start request.");
            keepLastErrorOnDestroy = true;
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServerAndSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RESTORE_NOTIFICATION.equals(action)) {
            updateNotification();
            startNotificationWatchdog();
            return START_NOT_STICKY;
        }

        int port = intent.getIntExtra(EXTRA_PORT, 8080);
        String root = intent.getStringExtra(EXTRA_ROOT);
        boolean tls = intent.getBooleanExtra(EXTRA_TLS, false);
        String tlsKeyStore = intent.getStringExtra(EXTRA_TLS_KEYSTORE);
        String tlsPassword = intent.getStringExtra(EXTRA_TLS_PASSWORD);
        boolean directoryListing = intent.getBooleanExtra(EXTRA_DIRECTORY_LISTING, false);
        boolean webInterface = intent.getBooleanExtra(EXTRA_WEB_INTERFACE, false);
        String webToken = intent.getStringExtra(EXTRA_WEB_TOKEN);
        boolean keepAliveSleep = intent.getBooleanExtra(EXTRA_KEEP_ALIVE_SLEEP, false);
        debugOutput = intent.getBooleanExtra(EXTRA_DEBUG, false);

        if (!startForegroundCompat(buildNotification("Starting HTTP server..."))) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startNotificationWatchdog();
        startServerAsync(port, root, tls, tlsKeyStore, tlsPassword, directoryListing, webInterface, webToken, keepAliveSleep);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
        try { if (server != null) server.stop(); } catch (Throwable ignored) {}
        stopNotificationWatchdog();
        releaseHttpSleepLocks();
        if (!keepLastErrorOnDestroy) clearStatus();
        super.onDestroy();
    }

    public void onTimeout(int startId) {
        handleForegroundServiceTimeout("startId=" + startId);
    }

    public void onTimeout(int type, int reason) {
        handleForegroundServiceTimeout("type=" + type + "; reason=" + reason);
    }

    private void handleForegroundServiceTimeout(String detail) {
        stopRequested = true;
        keepLastErrorOnDestroy = true;
        setStoppedWithError("Android stopped the background HTTP service after its foreground-service time limit"
                + (TextUtils.isEmpty(detail) ? "." : " (" + detail + ")."));
        appendServiceOutput("[HTTP] Background service stopped by Android foreground-service timeout"
                + (TextUtils.isEmpty(detail) ? "." : " (" + detail + ").") + "\n");
        try { if (startThread != null) startThread.interrupt(); } catch (Throwable ignored) {}
        try { if (server != null) server.stop(); } catch (Throwable ignored) {}
        server = null;
        stopNotificationWatchdog();
        releaseHttpSleepLocks();
        try { stopForegroundCompat(); } catch (Throwable ignored) {}
        stopSelf();
    }

    private synchronized void startServerAsync(int port,
                                               String root,
                                               boolean tls,
                                               String tlsKeyStore,
                                               String tlsPassword,
                                               boolean directoryListing,
                                               boolean webInterface,
                                               String webToken,
                                               boolean keepAliveSleep) {
        if (server != null && server.isRunning()) {
            updateNotification();
            return;
        }
        stopRequested = false;
        keepLastErrorOnDestroy = false;
        final int requestedPort = port;
        final String requestedRoot = TextUtils.isEmpty(root) ? DEFAULT_ROOT : root;
        final boolean requestedTls = tls;
        final String requestedTlsKeyStore = tlsKeyStore == null ? "" : tlsKeyStore;
        final String requestedTlsPassword = tlsPassword == null ? "" : tlsPassword;
        final boolean requestedDirectoryListing = directoryListing;
        final boolean requestedWebInterface = webInterface;
        final String requestedWebToken = webToken == null ? "" : webToken;
        final boolean requestedKeepAliveSleep = keepAliveSleep;
        setStarting(requestedPort, requestedRoot, requestedTls, requestedDirectoryListing, requestedWebInterface, requestedKeepAliveSleep);
        appendServiceOutput("[HTTP] Background HTTP service starting on port " + requestedPort + "; root=" + requestedRoot
                + (requestedTls ? "; https=on" : "")
                + (requestedWebInterface ? "; web-interface=/permstest" : "")
                + (requestedKeepAliveSleep ? "; sleep-keepalive=on" : "") + "\n");
        if (debugOutput) {
            appendServiceOutput("[HTTP] debug: service start request tls=" + requestedTls
                    + "; directoryListing=" + requestedDirectoryListing
                    + "; webInterface=" + requestedWebInterface
                    + "; keepAliveSleep=" + requestedKeepAliveSleep + "\n");
        }

        startThread = new Thread(() -> {
            try {
                setHttpSleepLocksEnabled(requestedKeepAliveSleep);
                File rootDir = new File(requestedRoot);
                ensureRootAndIndex(rootDir);
                PermsTestHttpServer.Config config = new PermsTestHttpServer.Config();
                config.port = requestedPort;
                config.rootDirectory = rootDir;
                config.tls = requestedTls;
                config.tlsKeyStoreFile = TextUtils.isEmpty(requestedTlsKeyStore) ? null : new File(requestedTlsKeyStore);
                config.tlsKeyStorePassword = requestedTlsPassword;
                config.directoryListingEnabled = requestedDirectoryListing;
                config.webInterfaceEnabled = requestedWebInterface;
                config.webInterfaceToken = requestedWebToken;
                PermsTestHttpServer next = new PermsTestHttpServer();
                next.start(config, message -> {
                    if (!TextUtils.isEmpty(message)) appendServiceOutput("[HTTP] " + message + "\n");
                    refreshStatusFromServer(next);
                    updateNotification();
                }, createWebInterface(requestedWebToken));
                server = next;
                refreshStatusFromServer(next);
                updateNotification();
            } catch (Throwable e) {
                String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                updateLastError(error);
                setStoppedWithError(error);
                appendServiceOutput("[HTTP] Background service failed: " + error + "\n");
                updateNotification();
                keepLastErrorOnDestroy = true;
                releaseHttpSleepLocks();
                stopSelf();
            }
        }, "PermsTestHttpServiceStart");
        startThread.setDaemon(true);
        startThread.start();
    }

    private PermsTestWebInterface createWebInterface(String token) {
        return new PermsTestWebInterface(new PermsTestWebInterface.Bridge() {
            @Override
            public String statusJson() {
                return buildStatusJson();
            }

            @Override
            public String outputText() {
                return serviceOutputSnapshot();
            }

            @Override
            public String accessJson() {
                return PermsTestWebMemoryApi.accessJson(webAccessMap());
            }

            @Override
            public boolean isWebSectionEnabled(String section) {
                return PermsTestHttpService.this.isWebSectionEnabled(section);
            }

            @Override
            public String memoryApiJson(String path, String query) {
                if (debugOutput) appendServiceOutput("[Web] Memory API " + path + "?" + (query == null ? "" : query) + "\n");
                return PermsTestWebMemoryApi.actionJson(PermsTestHttpService.this, getPermsPrefs(), path, query);
            }

            @Override
            public boolean isFtpRunning() {
                PermsTestFtpService.Status ftp = PermsTestFtpService.snapshot();
                return ftp.running || ftp.starting;
            }

            @Override
            public void startFtp() {
                if (isFtpRunning()) {
                    appendServiceOutput("[Web] FTP is already running; start ignored.\n");
                    return;
                }
                appendServiceOutput("[Web] Start FTP requested\n");
                startFtpFromPreferences();
            }

            @Override
            public void stopFtp() {
                appendServiceOutput("[Web] Stop FTP requested\n");
                stopFtpService();
            }
        }, token);
    }

    private SharedPreferences getPermsPrefs() {
        return getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
    }

    private Map<String, Boolean> webAccessMap() {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        map.put("global", isWebSectionEnabled("global"));
        map.put("main", isWebSectionEnabled("main"));
        map.put("shell", isWebSectionEnabled("shell"));
        map.put("packages", isWebSectionEnabled("packages"));
        map.put("memory", isWebSectionEnabled("memory"));
        map.put("files", isWebSectionEnabled("files"));
        map.put("network", isWebSectionEnabled("network"));
        map.put("scripts", isWebSectionEnabled("scripts"));
        map.put("debugging", isWebSectionEnabled("debugging"));
        map.put("tools", isWebSectionEnabled("tools"));
        map.put("logging", isWebSectionEnabled("logging"));
        map.put("settings", isWebSectionEnabled("settings"));
        map.put("about", isWebSectionEnabled("about"));
        return map;
    }

    private boolean isWebSectionEnabled(String section) {
        SharedPreferences prefs = getPermsPrefs();
        if (TextUtils.isEmpty(section)) return false;
        switch (section.trim().toLowerCase(Locale.US)) {
            case "global": return prefs == null || prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_GLOBAL, true);
            case "main": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_MAIN, false);
            case "shell": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SHELL, false);
            case "packages": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_PACKAGES, false);
            case "memory": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_MEMORY, false);
            case "files": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_FILES, false);
            case "network": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_NETWORK, false);
            case "scripts": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SCRIPTS, false);
            case "debugging": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_DEBUGGING, false);
            case "tools": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_TOOLS, false);
            case "logging": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_LOGGING, false);
            case "settings": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_SETTINGS, false);
            case "about": return prefs != null && prefs.getBoolean(NetworkPreferenceKeys.WEB_ACCESS_ABOUT, false);
            default: return false;
        }
    }

    private void startFtpFromPreferences() {
        try {
            SharedPreferences prefs = getPermsPrefs();
            int port = prefs.getInt(NetworkPreferenceKeys.FTP_PORT, 2221);
            boolean useShizuku = prefs.getBoolean(NetworkPreferenceKeys.FTP_USE_SHIZUKU, false);
            boolean keepAliveSleep = prefs.getBoolean(NetworkPreferenceKeys.FTP_KEEP_ALIVE_SLEEP, false);
            String root = prefs.getString(NetworkPreferenceKeys.FTP_ROOT,
                    useShizuku ? "/" : "/storage/emulated/0");
            PermsTestFtpService.markStartRequested(port, root, keepAliveSleep);
            Intent intent = new Intent(this, PermsTestFtpService.class)
                    .setAction(PermsTestFtpService.ACTION_START)
                    .putExtra(PermsTestFtpService.EXTRA_PORT, port)
                    .putExtra(PermsTestFtpService.EXTRA_ROOT, root)
                    .putExtra(PermsTestFtpService.EXTRA_USE_SHIZUKU, useShizuku)
                    .putExtra(PermsTestFtpService.EXTRA_KEEP_ALIVE_SLEEP, keepAliveSleep)
                    .putExtra(PermsTestFtpService.EXTRA_DEBUG, debugOutput);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        } catch (Throwable e) {
            appendServiceOutput("[Web] Start FTP failed: " + e.getMessage() + "\n");
        }
    }

    private void stopFtpService() {
        try { PermsTestFtpService.markStopRequested(); } catch (Throwable ignored) {}
        try { startService(new Intent(this, PermsTestFtpService.class).setAction(PermsTestFtpService.ACTION_STOP)); } catch (Throwable ignored) {}
        try { stopService(new Intent(this, PermsTestFtpService.class)); } catch (Throwable ignored) {}
    }

    private void stopServerAndSelf() {
        stopRequested = true;
        keepLastErrorOnDestroy = false;
        try { if (server != null) server.stop(); } catch (Throwable ignored) {}
        server = null;
        releaseHttpSleepLocks();
        clearStatus();
        stopNotificationWatchdog();
        try { stopForegroundCompat(); } catch (Throwable ignored) {}
        stopSelf();
    }

    private void refreshStatusFromServer(PermsTestHttpServer http) {
        synchronized (STATUS_LOCK) {
            statusStarting = false;
            statusRunning = http != null && http.isRunning();
            statusPort = statusRunning ? http.getPort() : statusPort;
            File root = statusRunning ? http.getRootDirectory() : null;
            statusRoot = root == null ? statusRoot : root.getAbsolutePath();
            statusTls = statusRunning && http.isTls();
            statusWebInterface = statusRunning && http.isWebInterfaceEnabled();
            statusKeepAliveSleep = statusRunning && isHttpSleepLockHeld();
            if (statusRunning) statusLastError = "";
        }
    }

    public static void markStartRequested(int port,
                                          String root,
                                          boolean tls,
                                          boolean directoryListing,
                                          boolean webInterface,
                                          boolean keepAliveSleep) {
        setStarting(port, root, tls, directoryListing, webInterface, keepAliveSleep);
    }

    public static void markStopRequested() {
        clearStatus();
    }

    private static void setStarting(int port,
                                    String root,
                                    boolean tls,
                                    boolean directoryListing,
                                    boolean webInterface,
                                    boolean keepAliveSleep) {
        synchronized (STATUS_LOCK) {
            statusStarting = true;
            statusRunning = false;
            statusPort = port;
            statusRoot = root == null ? "" : root;
            statusTls = tls;
            statusDirectoryListing = directoryListing;
            statusWebInterface = webInterface;
            statusKeepAliveSleep = keepAliveSleep;
            statusLastError = "";
        }
    }

    private static void setStoppedWithError(String error) {
        synchronized (STATUS_LOCK) {
            statusStarting = false;
            statusRunning = false;
            statusKeepAliveSleep = false;
            statusLastError = error == null ? "" : error;
        }
    }

    private static void updateLastError(String error) {
        synchronized (STATUS_LOCK) {
            statusLastError = error == null ? "" : error;
        }
    }

    private static void clearStatus() {
        synchronized (STATUS_LOCK) {
            statusStarting = false;
            statusRunning = false;
            statusPort = 0;
            statusRoot = "";
            statusTls = false;
            statusDirectoryListing = false;
            statusWebInterface = false;
            statusKeepAliveSleep = false;
            statusLastError = "";
        }
    }

    public static Status snapshot() {
        synchronized (STATUS_LOCK) {
            return new Status(statusStarting, statusRunning, statusPort, statusRoot, statusTls,
                    statusDirectoryListing, statusWebInterface, statusKeepAliveSleep, statusLastError);
        }
    }

    public static String serviceOutputSnapshot() {
        synchronized (serviceOutput) {
            return serviceOutput.toString();
        }
    }

    private static void appendServiceOutput(String text) {
        synchronized (serviceOutput) {
            if (text != null) serviceOutput.append(text);
            if (serviceOutput.length() > MAX_SERVICE_OUTPUT) {
                serviceOutput.delete(0, serviceOutput.length() - MAX_SERVICE_OUTPUT);
            }
        }
    }

    public static final class Status {
        public final boolean starting;
        public final boolean running;
        public final int port;
        public final String root;
        public final boolean tls;
        public final boolean directoryListing;
        public final boolean webInterface;
        public final boolean keepAliveSleep;
        public final String lastError;

        Status(boolean starting,
               boolean running,
               int port,
               String root,
               boolean tls,
               boolean directoryListing,
               boolean webInterface,
               boolean keepAliveSleep,
               String lastError) {
            this.starting = starting;
            this.running = running;
            this.port = port;
            this.root = root == null ? "" : root;
            this.tls = tls;
            this.directoryListing = directoryListing;
            this.webInterface = webInterface;
            this.keepAliveSleep = keepAliveSleep;
            this.lastError = lastError == null ? "" : lastError;
        }
    }

    private String buildStatusJson() {
        NetworkAddressFormatter.Status address = NetworkAddressFormatter.currentStatus();
        PermsTestFtpService.Status ftp = PermsTestFtpService.snapshot();
        Status http = snapshot();
        return "{"
                + "\"ok\":true,"
                + "\"mode\":\"background\","
                + "\"network\":{\"connected\":" + address.connected + ",\"ipv4\":\"" + jsonEscape(address.firstIpv4) + "\",\"text\":\"" + jsonEscape(address.text) + "\"},"
                + "\"ftp\":{\"running\":" + ftp.running + ",\"starting\":" + ftp.starting + ",\"port\":" + ftp.port + ",\"root\":\"" + jsonEscape(ftp.root) + "\",\"sleepKeepAlive\":" + ftp.keepAliveSleep + "},"
                + "\"http\":{\"running\":" + http.running + ",\"starting\":" + http.starting + ",\"tls\":" + http.tls + ",\"port\":" + http.port + ",\"root\":\"" + jsonEscape(http.root) + "\",\"directoryListing\":" + http.directoryListing + ",\"webInterface\":" + http.webInterface + ",\"sleepKeepAlive\":" + http.keepAliveSleep + "}"
                + "}";
    }

    private void setHttpSleepLocksEnabled(boolean enabled) {
        if (!enabled) {
            releaseHttpSleepLocks();
            return;
        }
        acquireHttpSleepLocks();
    }

    private void acquireHttpSleepLocks() {
        try {
            if (httpWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    httpWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PermsTest:httpSleep");
                    httpWakeLock.setReferenceCounted(false);
                }
            }
            if (httpWakeLock != null && !httpWakeLock.isHeld()) httpWakeLock.acquire();
        } catch (Throwable ignored) {
        }

        try {
            if (httpWifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wm != null) {
                    int mode = Build.VERSION.SDK_INT >= 12 ? WifiManager.WIFI_MODE_FULL_HIGH_PERF : WifiManager.WIFI_MODE_FULL;
                    httpWifiLock = wm.createWifiLock(mode, "PermsTest:httpWifi");
                    httpWifiLock.setReferenceCounted(false);
                }
            }
            if (httpWifiLock != null && !httpWifiLock.isHeld()) httpWifiLock.acquire();
        } catch (Throwable ignored) {
        }
    }

    private void releaseHttpSleepLocks() {
        try { if (httpWifiLock != null && httpWifiLock.isHeld()) httpWifiLock.release(); } catch (Throwable ignored) {}
        httpWifiLock = null;
        try { if (httpWakeLock != null && httpWakeLock.isHeld()) httpWakeLock.release(); } catch (Throwable ignored) {}
        httpWakeLock = null;
    }

    private boolean isHttpSleepLockHeld() {
        try {
            return httpWakeLock != null && httpWakeLock.isHeld();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureRootAndIndex(File root) throws java.io.IOException {
        if (root == null) throw new java.io.IOException("Missing root");
        if (!root.exists() && !root.mkdirs()) throw new java.io.IOException("Unable to create " + root.getAbsolutePath());
        File index = new File(root, "index.html");
        if (!index.exists()) {
            try (FileOutputStream out = new FileOutputStream(index)) {
                out.write(DEFAULT_INDEX.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private boolean startForegroundCompat(Notification notification) {
        try {
            ensureNotificationChannel();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (TextUtils.isEmpty(msg)) msg = t.getClass().getSimpleName();
            String error = "HTTP foreground service start blocked: " + msg;
            setStoppedWithError(error);
            appendServiceOutput("[HTTP] " + error + "\n");
            keepLastErrorOnDestroy = true;
            stopNotificationWatchdog();
            releaseHttpSleepLocks();
            return false;
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(notificationText()));
        } catch (Throwable ignored) {
        }
    }

    private String notificationText() {
        Status s = snapshot();
        if (s.running) {
            NetworkAddressFormatter.Status net = NetworkAddressFormatter.currentStatus();
            String host = TextUtils.isEmpty(net.firstIpv4) ? "127.0.0.1" : net.firstIpv4;
            return (s.tls ? "https" : "http") + "://" + host + ":" + s.port + "/"
                    + (s.webInterface ? " • /permstest" : "")
                    + (s.keepAliveSleep ? " • sleep keep-alive" : "");
        }
        if (s.starting) return "Starting HTTP server...";
        return TextUtils.isEmpty(s.lastError) ? "HTTP server stopped." : "HTTP failed: " + s.lastError;
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Intent stop = new Intent(this, PermsTestHttpService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Intent restore = new Intent(this, PermsTestHttpService.class).setAction(ACTION_RESTORE_NOTIFICATION);
        PendingIntent restorePi = PendingIntent.getService(this, 2, restore, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("PermsTest HTTP Server")
                .setContentText(text == null ? "HTTP server running" : text)
                .setOngoing(true)
                .setDeleteIntent(restorePi)
                .setContentIntent(pi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void startNotificationWatchdog() {
        if (notificationWatchdogRunning) return;
        notificationWatchdogRunning = true;
        mainHandler.removeCallbacks(notificationWatchdogRunnable);
        mainHandler.postDelayed(notificationWatchdogRunnable, NOTIFICATION_WATCHDOG_MS);
    }

    private void stopNotificationWatchdog() {
        notificationWatchdogRunning = false;
        mainHandler.removeCallbacks(notificationWatchdogRunnable);
    }

    private int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "HTTP server", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("PermsTest background HTTP server status");
        nm.createNotificationChannel(channel);
    }

    private static String jsonEscape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
