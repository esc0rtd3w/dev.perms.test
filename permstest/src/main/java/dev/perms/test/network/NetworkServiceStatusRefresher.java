package dev.perms.test.network;

import dev.perms.test.network.ftp.*;
import dev.perms.test.network.http.PermsTestHttpService;
import dev.perms.test.network.ssh.PermsTestSshService;

import android.os.Handler;
import android.text.TextUtils;

/**
 * Lightweight Network tab status refresher for service-owned FTP/HTTP/SSH changes.
 */
final class NetworkServiceStatusRefresher {
    private static final long REFRESH_INTERVAL_MS = 1500L;

    private final Handler handler;
    private final Runnable refreshCallback;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            refreshIfChanged();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private boolean running;
    private String lastKey;

    NetworkServiceStatusRefresher(Handler handler, Runnable refreshCallback) {
        this.handler = handler;
        this.refreshCallback = refreshCallback;
    }

    void start() {
        if (handler == null) return;
        if (running) return;
        running = true;
        lastKey = null;
        handler.post(tick);
    }

    void stop() {
        running = false;
        lastKey = null;
        if (handler != null) handler.removeCallbacks(tick);
    }

    void refreshIfChanged() {
        String key = buildStatusKey();
        if (TextUtils.equals(key, lastKey)) return;
        lastKey = key;
        if (refreshCallback != null) refreshCallback.run();
    }

    private static String buildStatusKey() {
        PermsTestFtpService.Status ftp = PermsTestFtpService.snapshot();
        PermsTestHttpService.Status http = PermsTestHttpService.snapshot();
        PermsTestSshService.Status ssh = PermsTestSshService.snapshot();
        return "ftp=" + ftp.starting
                + ',' + ftp.running
                + ',' + ftp.port
                + ',' + ftp.root
                + ',' + ftp.usingShell
                + ',' + ftp.keepAliveSleep
                + ',' + ftp.lastError
                + ";http=" + http.starting
                + ',' + http.running
                + ',' + http.port
                + ',' + http.root
                + ',' + http.tls
                + ',' + http.directoryListing
                + ',' + http.webInterface
                + ',' + http.keepAliveSleep
                + ',' + http.lastError
                + ";ssh=" + ssh.starting
                + ',' + ssh.running
                + ',' + ssh.port
                + ',' + ssh.root
                + ',' + ssh.username
                + ',' + ssh.sftpEnabled
                + ',' + ssh.shellEnabled
                + ',' + ssh.keepAliveSleep
                + ',' + ssh.lastError;
    }
}
