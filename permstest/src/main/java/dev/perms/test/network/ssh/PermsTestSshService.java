package dev.perms.test.network.ssh;

import dev.perms.test.MainActivity;
import dev.perms.test.network.NetworkAddressFormatter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.File;
import java.util.Collections;

/** Foreground Network-tab SSH/SFTP server with an ongoing status notification. */
public final class PermsTestSshService extends Service {
    public static final String ACTION_START = "dev.perms.test.action.START_SSH_SERVICE";
    public static final String ACTION_STOP = "dev.perms.test.action.STOP_SSH_SERVICE";
    private static final String ACTION_RESTORE_NOTIFICATION = "dev.perms.test.action.RESTORE_SSH_NOTIFICATION";

    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_ROOT = "root";
    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_PASSWORD = "password";
    public static final String EXTRA_ENABLE_SFTP = "enableSftp";
    public static final String EXTRA_ENABLE_SHELL = "enableShell";
    public static final String EXTRA_KEEP_ALIVE_SLEEP = "keepAliveSleep";
    public static final String EXTRA_DEBUG = "debug";

    private static final String CHANNEL_ID = "ssh_server_status_v1";
    private static final int NOTIFICATION_ID = 70425;
    private static final Object STATUS_LOCK = new Object();
    private static final long NOTIFICATION_WATCHDOG_MS = 30000L;

    private static boolean statusStarting;
    private static boolean statusRunning;
    private static int statusPort;
    private static String statusRoot = "";
    private static String statusUsername = "";
    private static boolean statusSftpEnabled;
    private static boolean statusShellEnabled;
    private static boolean statusKeepAliveSleep;
    private static String statusLastError = "";

    private SshServer sshServer;
    private Thread startThread;
    private volatile boolean stopRequested;
    private boolean keepLastErrorOnDestroy;
    private boolean debugOutput;
    private PowerManager.WakeLock sshWakeLock;
    private WifiManager.WifiLock sshWifiLock;
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
            setStoppedWithError("SSH service restart ignored: no active start request.");
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

        int port = intent.getIntExtra(EXTRA_PORT, 2222);
        String root = intent.getStringExtra(EXTRA_ROOT);
        String username = intent.getStringExtra(EXTRA_USERNAME);
        String password = intent.getStringExtra(EXTRA_PASSWORD);
        boolean enableSftp = intent.getBooleanExtra(EXTRA_ENABLE_SFTP, true);
        boolean enableShell = intent.getBooleanExtra(EXTRA_ENABLE_SHELL, false);
        boolean keepAliveSleep = intent.getBooleanExtra(EXTRA_KEEP_ALIVE_SLEEP, false);
        debugOutput = intent.getBooleanExtra(EXTRA_DEBUG, false);

        if (!startForegroundCompat(buildNotification("Starting SSH server..."))) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startNotificationWatchdog();
        startServerAsync(port, root, username, password, enableSftp, enableShell, keepAliveSleep);
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
        try { if (sshServer != null) sshServer.stop(true); } catch (Throwable ignored) {}
        stopNotificationWatchdog();
        releaseSshSleepLocks();
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
        setStoppedWithError("Android stopped the background SSH service after its foreground-service time limit"
                + (TextUtils.isEmpty(detail) ? "." : " (" + detail + ")."));
        try { if (startThread != null) startThread.interrupt(); } catch (Throwable ignored) {}
        try { if (sshServer != null) sshServer.stop(true); } catch (Throwable ignored) {}
        sshServer = null;
        stopNotificationWatchdog();
        releaseSshSleepLocks();
        try { stopForegroundCompat(); } catch (Throwable ignored) {}
        stopSelf();
    }

    private synchronized void startServerAsync(int port,
                                               String root,
                                               String username,
                                               String password,
                                               boolean enableSftp,
                                               boolean enableShell,
                                               boolean keepAliveSleep) {
        if (sshServer != null && sshServer.isOpen()) {
            updateNotification();
            return;
        }
        if (startThread != null && startThread.isAlive()) return;

        stopRequested = false;
        keepLastErrorOnDestroy = false;
        final int requestedPort = port;
        final String requestedRoot = TextUtils.isEmpty(root) ? "/storage/emulated/0" : root;
        final String requestedUser = TextUtils.isEmpty(username) ? "perms" : username.trim();
        final String requestedPass = password == null ? "" : password;
        final boolean requestedSftp = enableSftp;
        final boolean requestedShell = enableShell;
        final boolean requestedKeepAlive = keepAliveSleep;
        setStarting(requestedPort, requestedRoot, requestedUser, requestedSftp, requestedShell, requestedKeepAlive);

        startThread = new Thread(() -> {
            try {
                if (TextUtils.isEmpty(requestedPass)) throw new IllegalArgumentException("Password is required.");
                if (!requestedSftp && !requestedShell) throw new IllegalArgumentException("Enable SFTP or shell access first.");
                setSshSleepLocksEnabled(requestedKeepAlive);
                SshAndroidRuntimeCompat.prepareServer(this);

                File rootDir = new File(requestedRoot);
                if (!rootDir.exists() && !rootDir.mkdirs()) {
                    throw new IllegalStateException("Root folder does not exist and could not be created: " + requestedRoot);
                }
                File hostKey = hostKeyFile(this);
                File hostKeyParent = hostKey.getParentFile();
                if (hostKeyParent != null && !hostKeyParent.exists()) hostKeyParent.mkdirs();

                SshServer server = SshServer.setUpDefaultServer();
                server.setHost("0.0.0.0");
                server.setPort(requestedPort);
                server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey.toPath()));
                server.setPasswordAuthenticator((user, pass, session) -> requestedUser.equals(user) && requestedPass.equals(pass));
                server.setFileSystemFactory(new VirtualFileSystemFactory(rootDir.toPath()));
                if (requestedSftp) {
                    server.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory.Builder().build()));
                } else {
                    server.setSubsystemFactories(Collections.emptyList());
                }
                if (requestedShell) {
                    server.setShellFactory(new ProcessShellFactory("/system/bin/sh", "-i"));
                }

                server.start();
                if (stopRequested) {
                    try { server.stop(true); } catch (Throwable ignored) {}
                    releaseSshSleepLocks();
                    clearStatus();
                    stopSelf();
                    return;
                }
                sshServer = server;
                setRunning(requestedPort, requestedRoot, requestedUser, requestedSftp, requestedShell, requestedKeepAlive);
                updateNotification();
            } catch (Throwable t) {
                releaseSshSleepLocks();
                if (stopRequested) {
                    clearStatus();
                    stopSelf();
                    return;
                }
                setStoppedWithError(SshAndroidRuntimeCompat.describeThrowable(t));
                keepLastErrorOnDestroy = true;
                updateNotification();
                stopSelf();
            }
        }, "PermsTest-SSH-Service-Start");
        startThread.start();
    }

    private void stopServerAndSelf() {
        stopRequested = true;
        try { if (sshServer != null) sshServer.stop(true); } catch (Throwable ignored) {}
        sshServer = null;
        releaseSshSleepLocks();
        clearStatus();
        stopNotificationWatchdog();
        try { stopForegroundCompat(); } catch (Throwable ignored) {}
        stopSelf();
    }

    private boolean startForegroundCompat(Notification notification) {
        try {
            ensureNotificationChannel();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
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
            return "ssh " + s.username + "@" + host + " -p " + s.port
                    + (s.sftpEnabled ? " • SFTP" : "")
                    + (s.shellEnabled ? " • shell" : "")
                    + (s.keepAliveSleep ? " • sleep keep-alive" : "");
        }
        if (s.starting) return "Starting SSH server...";
        return TextUtils.isEmpty(s.lastError) ? "SSH server stopped." : "SSH failed: " + s.lastError;
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Intent stop = new Intent(this, PermsTestSshService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Intent restore = new Intent(this, PermsTestSshService.class).setAction(ACTION_RESTORE_NOTIFICATION);
        PendingIntent restorePi = PendingIntent.getService(this, 2, restore, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("PermsTest SSH Server")
                .setContentText(text == null ? "SSH server running" : text)
                .setOngoing(true)
                .setAutoCancel(false)
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SSH server", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("PermsTest SSH/SFTP server status");
        nm.createNotificationChannel(channel);
    }

    private void setSshSleepLocksEnabled(boolean enabled) {
        if (!enabled) {
            releaseSshSleepLocks();
            return;
        }
        try {
            if (sshWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    sshWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PermsTest:SshServer");
                    sshWakeLock.setReferenceCounted(false);
                }
            }
            if (sshWakeLock != null && !sshWakeLock.isHeld()) sshWakeLock.acquire();
        } catch (Throwable ignored) {
        }
        try {
            if (sshWifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wm != null) {
                    sshWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PermsTest:SshServerWifi");
                    sshWifiLock.setReferenceCounted(false);
                }
            }
            if (sshWifiLock != null && !sshWifiLock.isHeld()) sshWifiLock.acquire();
        } catch (Throwable ignored) {
        }
    }

    private void releaseSshSleepLocks() {
        try { if (sshWakeLock != null && sshWakeLock.isHeld()) sshWakeLock.release(); } catch (Throwable ignored) {}
        try { if (sshWifiLock != null && sshWifiLock.isHeld()) sshWifiLock.release(); } catch (Throwable ignored) {}
    }

    private static void setStarting(int port, String root, String username, boolean sftp, boolean shell, boolean keepAlive) {
        synchronized (STATUS_LOCK) {
            statusStarting = true;
            statusRunning = false;
            statusPort = port;
            statusRoot = root == null ? "" : root;
            statusUsername = username == null ? "" : username;
            statusSftpEnabled = sftp;
            statusShellEnabled = shell;
            statusKeepAliveSleep = keepAlive;
            statusLastError = "";
        }
    }

    private static void setRunning(int port, String root, String username, boolean sftp, boolean shell, boolean keepAlive) {
        synchronized (STATUS_LOCK) {
            statusStarting = false;
            statusRunning = true;
            statusPort = port;
            statusRoot = root == null ? "" : root;
            statusUsername = username == null ? "" : username;
            statusSftpEnabled = sftp;
            statusShellEnabled = shell;
            statusKeepAliveSleep = keepAlive;
            statusLastError = "";
        }
    }

    private static void setStoppedWithError(String error) {
        synchronized (STATUS_LOCK) {
            statusStarting = false;
            statusRunning = false;
            statusLastError = error == null ? "" : error;
        }
    }

    public static void clearStatus() {
        synchronized (STATUS_LOCK) {
            statusStarting = false;
            statusRunning = false;
            statusPort = 0;
            statusRoot = "";
            statusUsername = "";
            statusSftpEnabled = false;
            statusShellEnabled = false;
            statusKeepAliveSleep = false;
            statusLastError = "";
        }
    }

    public static void markStartRequested(int port, String root, String username, boolean sftp, boolean shell, boolean keepAlive) {
        setStarting(port, root, username, sftp, shell, keepAlive);
    }

    public static void markStopRequested() {
        clearStatus();
    }

    public static File hostKeyFile(android.content.Context context) {
        File base = context == null ? new File(".") : new File(context.getFilesDir(), "network/ssh");
        return new File(base, "hostkey.ser");
    }

    public static Status snapshot() {
        synchronized (STATUS_LOCK) {
            return new Status(statusStarting, statusRunning, statusPort, statusRoot, statusUsername,
                    statusSftpEnabled, statusShellEnabled, statusKeepAliveSleep, statusLastError);
        }
    }

    public static final class Status {
        public final boolean starting;
        public final boolean running;
        public final int port;
        public final String root;
        public final String username;
        public final boolean sftpEnabled;
        public final boolean shellEnabled;
        public final boolean keepAliveSleep;
        public final String lastError;

        Status(boolean starting,
               boolean running,
               int port,
               String root,
               String username,
               boolean sftpEnabled,
               boolean shellEnabled,
               boolean keepAliveSleep,
               String lastError) {
            this.starting = starting;
            this.running = running;
            this.port = port;
            this.root = root == null ? "" : root;
            this.username = username == null ? "" : username;
            this.sftpEnabled = sftpEnabled;
            this.shellEnabled = shellEnabled;
            this.keepAliveSleep = keepAliveSleep;
            this.lastError = lastError == null ? "" : lastError;
        }
    }
}
