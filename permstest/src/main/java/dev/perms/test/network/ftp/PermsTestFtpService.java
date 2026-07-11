package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import dev.perms.test.MainActivity;
import dev.perms.test.ShizukuCompat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import rikka.shizuku.Shizuku;

/**
 * Foreground FTP host used only when the Network tab's background-use option is enabled.
 * The default in-activity FTP path remains available for short foreground-only sessions.
 */
public final class PermsTestFtpService extends Service {
    public static final String ACTION_START = "dev.perms.test.action.START_FTP_SERVICE";
    public static final String ACTION_STOP = "dev.perms.test.action.STOP_FTP_SERVICE";
    private static final String ACTION_RESTORE_NOTIFICATION = "dev.perms.test.action.RESTORE_FTP_NOTIFICATION";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_ROOT = "root";
    public static final String EXTRA_USE_SHIZUKU = "useShizuku";
    public static final String EXTRA_KEEP_ALIVE_SLEEP = "keepAliveSleep";
    public static final String EXTRA_DEBUG = "debug";

    private static final String CHANNEL_ID = "ftp_server_status_v1";
    private static final int NOTIFICATION_ID = 70423;
    private static final Object STATUS_LOCK = new Object();
    private static final long NOTIFICATION_WATCHDOG_MS = 30000L;

    private static boolean statusStarting;
    private static boolean statusRunning;
    private static int statusPort;
    private static String statusRoot = "";
    private static boolean statusUsingShell;
    private static boolean statusKeepAliveSleep;
    private static String statusLastError = "";

    private PermsTestFtpServer server;
    private Thread startThread;
    private volatile boolean stopRequested;
    private boolean keepLastErrorOnDestroy;
    private PowerManager.WakeLock ftpWakeLock;
    private WifiManager.WifiLock ftpWifiLock;
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
            setStoppedWithError("FTP service restart ignored: no active start request.");
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

        int port = intent.getIntExtra(EXTRA_PORT, 2221);
        String root = intent.getStringExtra(EXTRA_ROOT);
        boolean useShizuku = intent.getBooleanExtra(EXTRA_USE_SHIZUKU, false);
        boolean keepAliveSleep = intent.getBooleanExtra(EXTRA_KEEP_ALIVE_SLEEP, false);
        boolean debug = intent.getBooleanExtra(EXTRA_DEBUG, false);
        if (!startForegroundCompat(buildNotification("Starting FTP server..."))) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startNotificationWatchdog();
        startServerAsync(port, root, useShizuku, keepAliveSleep, debug);
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
        try { if (server != null) server.shutdown(); } catch (Throwable ignored) {}
        stopNotificationWatchdog();
        releaseFtpSleepLocks();
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
        setStoppedWithError("Android stopped the background FTP service after its foreground-service time limit"
                + (TextUtils.isEmpty(detail) ? "." : " (" + detail + ")."));
        try { if (startThread != null) startThread.interrupt(); } catch (Throwable ignored) {}
        try { if (server != null) server.shutdown(); } catch (Throwable ignored) {}
        server = null;
        stopNotificationWatchdog();
        releaseFtpSleepLocks();
        try { stopForegroundCompat(); } catch (Throwable ignored) {}
        stopSelf();
    }

    private synchronized void startServerAsync(int port, String root, boolean useShizuku, boolean keepAliveSleep, boolean debug) {
        if (server != null && server.isRunning()) {
            updateNotification();
            return;
        }
        if (startThread != null && startThread.isAlive()) return;

        stopRequested = false;
        keepLastErrorOnDestroy = false;
        final int requestedPort = port;
        final String requestedRoot = TextUtils.isEmpty(root) ? "/storage/emulated/0" : root;
        final boolean requestedUseShizuku = useShizuku;
        final boolean requestedKeepAliveSleep = keepAliveSleep;
        final boolean requestedDebug = debug;
        setStarting(requestedPort, requestedRoot, requestedKeepAliveSleep);

        startThread = new Thread(() -> {
            try {
                setFtpSleepLocksEnabled(requestedKeepAliveSleep);
                PermsTestFtpServer ftp = new PermsTestFtpServer();
                PermsTestFtpServer.ShellAccess shellAccess = requestedUseShizuku ? new PermsTestFtpServer.ShellAccess() {
                    @Override
                    public PermsTestFtpServer.ShellResult run(String command) {
                        return runShizukuShell(command);
                    }

                    @Override
                    public Process start(String command) throws IOException {
                        if (!isShizukuReady()) throw new IOException("Shizuku not ready or permission not granted.");
                        try {
                            return ShizukuCompat.newProcess(new String[]{"sh", "-c", command}, null, null);
                        } catch (Exception e) {
                            IOException io = new IOException("Shizuku process start failed: " + e.getMessage());
                            io.initCause(e);
                            throw io;
                        }
                    }
                } : null;

                ftp.start(requestedPort, new File(requestedRoot), requestedUseShizuku, shellAccess, requestedDebug, new PermsTestFtpServer.Listener() {
                    @Override
                    public void onFtpLog(String message) {
                        if (!TextUtils.isEmpty(message) && requestedDebug) {
                            updateLastError("");
                        }
                    }

                    @Override
                    public void onFtpStateChanged() {
                        updateRunningStatus();
                        updateNotification();
                    }
                });
                if (stopRequested) {
                    ftp.shutdown();
                    releaseFtpSleepLocks();
                    clearStatus();
                    stopSelf();
                    return;
                }
                server = ftp;
                updateRunningStatus();
                updateNotification();
            } catch (Throwable t) {
                releaseFtpSleepLocks();
                if (stopRequested) {
                    clearStatus();
                    stopSelf();
                    return;
                }
                String msg = t.getMessage();
                if (TextUtils.isEmpty(msg)) msg = t.getClass().getSimpleName();
                setStoppedWithError(msg);
                keepLastErrorOnDestroy = true;
                updateNotification();
                stopSelf();
            }
        }, "PermsTestFtpServiceStart");
        startThread.start();
    }

    private synchronized void stopServerAndSelf() {
        stopRequested = true;
        try { if (startThread != null) startThread.interrupt(); } catch (Throwable ignored) {}
        try { if (server != null) server.shutdown(); } catch (Throwable ignored) {}
        server = null;
        releaseFtpSleepLocks();
        clearStatus();
        stopForegroundCompat();
        stopSelf();
    }

    private void updateRunningStatus() {
        PermsTestFtpServer ftp = server;
        synchronized (STATUS_LOCK) {
            statusStarting = false;
            statusRunning = ftp != null && ftp.isRunning();
            statusPort = statusRunning ? ftp.getPort() : statusPort;
            File root = statusRunning ? ftp.getRootDir() : null;
            statusRoot = root == null ? statusRoot : root.getAbsolutePath();
            statusUsingShell = statusRunning && ftp.isUsingShellAccess();
            statusKeepAliveSleep = statusRunning && isFtpSleepLockHeld();
            if (statusRunning) statusLastError = "";
        }
    }

    public static void markStartRequested(int port, String root, boolean keepAliveSleep) {
        setStarting(port, root, keepAliveSleep);
    }

    public static void markStopRequested() {
        clearStatus();
    }

    private static void setStarting(int port, String root, boolean keepAliveSleep) {
        synchronized (STATUS_LOCK) {
            statusStarting = true;
            statusRunning = false;
            statusPort = port;
            statusRoot = root == null ? "" : root;
            statusUsingShell = false;
            statusKeepAliveSleep = keepAliveSleep;
            statusLastError = "";
        }
    }

    private static void setStoppedWithError(String error) {
        synchronized (STATUS_LOCK) {
            statusStarting = false;
            statusRunning = false;
            statusUsingShell = false;
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
            statusUsingShell = false;
            statusKeepAliveSleep = false;
            statusLastError = "";
        }
    }

    public static Status snapshot() {
        synchronized (STATUS_LOCK) {
            return new Status(statusStarting, statusRunning, statusPort, statusRoot, statusUsingShell, statusKeepAliveSleep, statusLastError);
        }
    }

    public static final class Status {
        public final boolean starting;
        public final boolean running;
        public final int port;
        public final String root;
        public final boolean usingShell;
        public final boolean keepAliveSleep;
        public final String lastError;

        Status(boolean starting, boolean running, int port, String root, boolean usingShell, boolean keepAliveSleep, String lastError) {
            this.starting = starting;
            this.running = running;
            this.port = port;
            this.root = root == null ? "" : root;
            this.usingShell = usingShell;
            this.keepAliveSleep = keepAliveSleep;
            this.lastError = lastError == null ? "" : lastError;
        }
    }

    private void setFtpSleepLocksEnabled(boolean enabled) {
        if (!enabled) {
            releaseFtpSleepLocks();
            return;
        }
        acquireFtpSleepLocks();
    }

    private void acquireFtpSleepLocks() {
        try {
            if (ftpWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    ftpWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PermsTest:ftpSleep");
                    ftpWakeLock.setReferenceCounted(false);
                }
            }
            if (ftpWakeLock != null && !ftpWakeLock.isHeld()) ftpWakeLock.acquire();
        } catch (Throwable ignored) {
        }

        try {
            if (ftpWifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wm != null) {
                    int mode = Build.VERSION.SDK_INT >= 12 ? WifiManager.WIFI_MODE_FULL_HIGH_PERF : WifiManager.WIFI_MODE_FULL;
                    ftpWifiLock = wm.createWifiLock(mode, "PermsTest:ftpWifi");
                    ftpWifiLock.setReferenceCounted(false);
                }
            }
            if (ftpWifiLock != null && !ftpWifiLock.isHeld()) ftpWifiLock.acquire();
        } catch (Throwable ignored) {
        }
    }

    private void releaseFtpSleepLocks() {
        try { if (ftpWifiLock != null && ftpWifiLock.isHeld()) ftpWifiLock.release(); } catch (Throwable ignored) {}
        ftpWifiLock = null;
        try { if (ftpWakeLock != null && ftpWakeLock.isHeld()) ftpWakeLock.release(); } catch (Throwable ignored) {}
        ftpWakeLock = null;
    }

    private boolean isFtpSleepLockHeld() {
        try {
            return ftpWakeLock != null && ftpWakeLock.isHeld();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private PermsTestFtpServer.ShellResult runShizukuShell(String command) {
        if (!isShizukuReady()) return new PermsTestFtpServer.ShellResult(1, "", "Shizuku not ready or permission not granted.");
        try {
            Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c", command}, null, null);
            String out = readStream(p.getInputStream());
            String err = readStream(p.getErrorStream());
            int code = 1;
            try { code = p.waitFor(); } catch (Throwable ignored) { code = 1; }
            return new PermsTestFtpServer.ShellResult(code, out, err);
        } catch (Throwable t) {
            return new PermsTestFtpServer.ShellResult(1, "", "Shell failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private boolean isShizukuReady() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
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
            setStoppedWithError("FTP foreground service start blocked: " + msg);
            keepLastErrorOnDestroy = true;
            stopNotificationWatchdog();
            releaseFtpSleepLocks();
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
            return "ftp://anonymous@" + host + ":" + s.port + "/" + (s.keepAliveSleep ? " • sleep keep-alive" : "");
        }
        if (s.starting) return "Starting FTP server...";
        return TextUtils.isEmpty(s.lastError) ? "FTP server stopped." : "FTP failed: " + s.lastError;
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Intent stop = new Intent(this, PermsTestFtpService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Intent restore = new Intent(this, PermsTestFtpService.class).setAction(ACTION_RESTORE_NOTIFICATION);
        PendingIntent restorePi = PendingIntent.getService(this, 2, restore, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("PermsTest FTP Server")
                .setContentText(text == null ? "FTP server running" : text)
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "FTP server", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("PermsTest background FTP server status");
        nm.createNotificationChannel(channel);
    }
}
