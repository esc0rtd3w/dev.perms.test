package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;

import dev.perms.test.databinding.TabNetworkBinding;

/**
 * Runtime holder for the Network tab FTP server.
 *
 * Keeps the live FTP server instance and lifetime operations together so the
 * server controller can stay focused on UI control routing and state refresh.
 */
public final class NetworkFtpServerRuntime {
    private PermsTestFtpServer server;

    public PermsTestFtpServer server() {
        return server;
    }

    public PermsTestFtpServer getOrCreateServer() {
        if (server == null) server = new PermsTestFtpServer();
        return server;
    }

    public void ensureServer() {
        getOrCreateServer();
    }

    public void start(Activity activity,
                      TabNetworkBinding network,
                      SharedPreferences prefs,
                      NetworkFtpServerRuntimeActions.ShellCaptureRunner shellCaptureRunner,
                      NetworkFtpServerRuntimeActions.ShizukuProcessStarter processStarter,
                      NetworkFtpServerRuntimeActions.DebugOutputProvider debugOutputProvider,
                      NetworkFtpServerRuntimeActions.OutputAppender outputAppender,
                      Runnable updateUi,
                      Handler mainHandler) {
        server = NetworkFtpServerRuntimeActions.startFromUi(
                activity,
                network,
                prefs,
                getOrCreateServer(),
                shellCaptureRunner,
                processStarter,
                debugOutputProvider,
                outputAppender,
                updateUi,
                mainHandler);
    }

    public void stop(Activity activity, Runnable updateUi, Handler mainHandler) {
        NetworkFtpServerRuntimeActions.stop(activity, server, updateUi, mainHandler);
    }

    public void shutdown() {
        try { if (server != null) server.shutdown(); } catch (Throwable ignored) {}
    }
}
