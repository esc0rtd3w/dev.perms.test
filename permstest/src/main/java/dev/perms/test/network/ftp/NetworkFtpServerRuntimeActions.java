package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import dev.perms.test.databinding.TabNetworkBinding;

/**
 * FTP server start/stop runtime actions for the Network tab.
 */
public final class NetworkFtpServerRuntimeActions {
    private NetworkFtpServerRuntimeActions() {
    }

    public static PermsTestFtpServer startFromUi(Activity activity,
                                                 TabNetworkBinding network,
                                                 SharedPreferences prefs,
                                                 PermsTestFtpServer server,
                                                 ShellCaptureRunner shellCaptureRunner,
                                                 ShizukuProcessStarter processStarter,
                                                 DebugOutputProvider debugOutputProvider,
                                                 OutputAppender outputAppender,
                                                 Runnable updateUi,
                                                 Handler mainHandler) {
        PermsTestFtpServer ftpServer = server;
        try {
            if (network == null) return ftpServer;
            if (ftpServer == null) ftpServer = new PermsTestFtpServer();
            int port = NetworkFtpServerPaths.parsePort(networkText(network.edtFtpPort, "2221"));
            boolean useShizuku = network.chkFtpUseShizuku.isChecked();
            boolean keepAliveSleep = network.chkFtpKeepAliveSleep.isChecked();
            boolean backgroundUse = network.chkFtpBackgroundUse.isChecked();
            String root = NetworkFtpServerPaths.normalizeRootForMode(useShizuku,
                    NetworkFtpServerPaths.normalizeAbsoluteRoot(
                            networkText(network.edtFtpRoot, NetworkFtpServerPaths.defaultRootForMode(useShizuku)),
                            NetworkFtpServerPaths.defaultRootForMode(useShizuku)));
            try {
                if (network.edtFtpRoot != null) network.edtFtpRoot.setText(root);
            } catch (Throwable ignored) {
            }
            if (prefs != null) {
                NetworkFtpServerPaths.saveRootForMode(prefs, useShizuku, root);
                prefs.edit()
                        .putInt(NetworkPreferenceKeys.FTP_PORT, port)
                        .putString(NetworkPreferenceKeys.FTP_ROOT, root)
                        .putBoolean(NetworkPreferenceKeys.FTP_USE_SHIZUKU, useShizuku)
                        .putBoolean(NetworkPreferenceKeys.FTP_KEEP_ALIVE_SLEEP, keepAliveSleep)
                        .putBoolean(NetworkPreferenceKeys.FTP_BACKGROUND_USE, backgroundUse)
                        .apply();
            }

            boolean debugOutput = debugOutputProvider != null && debugOutputProvider.isDebugOutputEnabled();
            if (debugOutput) {
                append(outputAppender, "[FTP] debug: start UI port=" + port
                        + "; root=" + root
                        + "; shizuku=" + useShizuku
                        + "; sleep-keepalive=" + keepAliveSleep
                        + "; background=" + backgroundUse
                        + "; all-files=" + NetworkFtpServerStorageAccess.hasAllFilesAccess(activity) + "\n");
            }

            if (!useShizuku && NetworkFtpServerPaths.needsAllFilesAccess(root) && !NetworkFtpServerStorageAccess.hasAllFilesAccess(activity)) {
                if (debugOutput) {
                    append(outputAppender, "[FTP] debug: normal mode blocked because All files access is missing for root=" + root + "\n");
                }
                NetworkFtpServerStorageAccess.showNormalStorageAccessDialog(activity, network, root,
                        outputAppender == null ? null : outputAppender::append);
                run(updateUi);
                return ftpServer;
            }

            if (backgroundUse || keepAliveSleep) {
                startBackgroundService(activity, ftpServer, port, root, useShizuku, keepAliveSleep, debugOutput,
                        outputAppender, updateUi, mainHandler);
                run(updateUi);
                return ftpServer;
            }

            File rootDir = new File(root);
            PermsTestFtpServer.ShellAccess shellAccess = useShizuku ? createShellAccess(shellCaptureRunner, processStarter) : null;
            ftpServer.start(port, rootDir, useShizuku, shellAccess, debugOutput, new PermsTestFtpServer.Listener() {
                @Override
                public void onFtpLog(String message) {
                    post(mainHandler, () -> append(outputAppender, "[FTP] " + message + "\n"));
                }

                @Override
                public void onFtpStateChanged() {
                    post(mainHandler, updateUi);
                }
            });
            run(updateUi);
            return ftpServer;
        } catch (Throwable e) {
            if (activity != null) {
                Toast.makeText(activity, "FTP start failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            append(outputAppender, "[FTP] Start failed: " + e.getMessage() + "\n");
            run(updateUi);
            return ftpServer;
        }
    }

    public static void stop(Activity activity,
                            PermsTestFtpServer server,
                            Runnable updateUi,
                            Handler mainHandler) {
        try { if (server != null) server.stop(); } catch (Throwable ignored) {}
        try { PermsTestFtpService.markStopRequested(); } catch (Throwable ignored) {}
        try {
            if (activity != null) {
                Intent intent = new Intent(activity, PermsTestFtpService.class).setAction(PermsTestFtpService.ACTION_STOP);
                activity.startService(intent);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (activity != null) activity.stopService(new Intent(activity, PermsTestFtpService.class));
        } catch (Throwable ignored) {}
        run(updateUi);
        if (mainHandler != null) {
            mainHandler.postDelayed(() -> run(updateUi), 500L);
            mainHandler.postDelayed(() -> run(updateUi), 1500L);
        }
    }

    private static void startBackgroundService(Activity activity,
                                               PermsTestFtpServer server,
                                               int port,
                                               String root,
                                               boolean useShizuku,
                                               boolean keepAliveSleep,
                                               boolean debugOutput,
                                               OutputAppender outputAppender,
                                               Runnable updateUi,
                                               Handler mainHandler) {
        try { if (server != null) server.stop(); } catch (Throwable ignored) {}
        PermsTestFtpService.markStartRequested(port, root, keepAliveSleep);
        if (activity != null) {
            Intent intent = new Intent(activity, PermsTestFtpService.class)
                    .setAction(PermsTestFtpService.ACTION_START)
                    .putExtra(PermsTestFtpService.EXTRA_PORT, port)
                    .putExtra(PermsTestFtpService.EXTRA_ROOT, root)
                    .putExtra(PermsTestFtpService.EXTRA_USE_SHIZUKU, useShizuku)
                    .putExtra(PermsTestFtpService.EXTRA_KEEP_ALIVE_SLEEP, keepAliveSleep)
                    .putExtra(PermsTestFtpService.EXTRA_DEBUG, debugOutput);
            if (Build.VERSION.SDK_INT >= 26) {
                activity.startForegroundService(intent);
            } else {
                activity.startService(intent);
            }
        }
        append(outputAppender, "[FTP] Background FTP service starting on port " + port + "; root=" + root
                + (useShizuku ? "; shizuku-files=on" : "")
                + (keepAliveSleep ? "; sleep-keepalive=on" : "") + "\n");
        if (mainHandler != null) {
            mainHandler.postDelayed(() -> run(updateUi), 600L);
            mainHandler.postDelayed(() -> run(updateUi), 1800L);
        }
    }

    private static PermsTestFtpServer.ShellAccess createShellAccess(ShellCaptureRunner shellCaptureRunner,
                                                                    ShizukuProcessStarter processStarter) {
        return new PermsTestFtpServer.ShellAccess() {
            @Override
            public PermsTestFtpServer.ShellResult run(String command) {
                ShellResult result = shellCaptureRunner == null
                        ? new ShellResult(1, "", "Shizuku not ready or permission not granted.")
                        : shellCaptureRunner.run(command);
                return new PermsTestFtpServer.ShellResult(result.exitCode, result.stdout, result.stderr);
            }

            @Override
            public Process start(String command) throws IOException {
                if (processStarter == null) throw new IOException("Shizuku not ready or permission not granted.");
                return processStarter.start(command);
            }
        };
    }

    private static String networkText(EditText editText, String fallback) {
        try {
            String value = editText == null || editText.getText() == null ? "" : editText.getText().toString().trim();
            return TextUtils.isEmpty(value) ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void append(OutputAppender outputAppender, String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    private static void run(Runnable runnable) {
        if (runnable != null) runnable.run();
    }

    private static void post(Handler handler, Runnable runnable) {
        if (handler != null) {
            handler.post(() -> run(runnable));
        } else {
            run(runnable);
        }
    }

    public interface DebugOutputProvider {
        boolean isDebugOutputEnabled();
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface ShellCaptureRunner {
        ShellResult run(String command);
    }

    public interface ShizukuProcessStarter {
        Process start(String command) throws IOException;
    }

    public static final class ShellResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }
}
