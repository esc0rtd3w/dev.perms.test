package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.List;

import dev.perms.test.databinding.TabNetworkBinding;

/**
 * FTP client connect/disconnect actions for the Network tab.
 */
public final class NetworkFtpClientConnectionActions {
    private NetworkFtpClientConnectionActions() {
    }

    public interface ClientFactory {
        PermsTestFtpClient getOrCreateClient();
    }

    public interface RemoteEntriesSetter {
        void setEntries(List<PermsTestFtpClient.RemoteEntry> entries);
    }

    public interface StatusSetter {
        void setStatus(String text, boolean error);
    }

    public interface OutputAppender {
        void appendOutput(String text);
    }

    public static void connectFromUi(Activity activity,
                                     TabNetworkBinding binding,
                                     SharedPreferences prefs,
                                     NetworkFtpClientTaskRunner taskRunner,
                                     Handler mainHandler,
                                     ClientFactory clientFactory,
                                     RemoteEntriesSetter remoteEntriesSetter,
                                     StatusSetter statusSetter,
                                     OutputAppender outputAppender) {
        if (isBusy(taskRunner) || activity == null || binding == null) return;
        String host = text(binding.edtFtpClientHost, "").trim();
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(activity, "FTP host is required.", Toast.LENGTH_SHORT).show();
            return;
        }
        int port;
        try {
            port = NetworkFtpClientPaths.parsePort(text(binding.edtFtpClientPort, "21"));
        } catch (Throwable e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        String user = text(binding.edtFtpClientUser, "anonymous");
        String pass = text(binding.edtFtpClientPass, "");
        if (prefs != null) {
            prefs.edit()
                    .putString(NetworkPreferenceKeys.FTP_CLIENT_HOST, host)
                    .putInt(NetworkPreferenceKeys.FTP_CLIENT_PORT, port)
                    .putString(NetworkPreferenceKeys.FTP_CLIENT_USER, TextUtils.isEmpty(user) ? "anonymous" : user)
                    .apply();
        }
        runTask(taskRunner, "Connecting...", () -> {
            PermsTestFtpClient client = clientFactory == null ? null : clientFactory.getOrCreateClient();
            if (client == null) return;
            client.connect(host, port, user, pass);
            List<PermsTestFtpClient.RemoteEntry> remote = client.listCurrentDirectory();
            post(mainHandler, () -> {
                setEntries(remoteEntriesSetter, remote);
                setStatus(statusSetter, "Connected to " + host + ":" + port, false);
                appendOutput(outputAppender, "[FTP Client] Connected to " + host + ":" + port + "\n");
            });
        });
    }

    public static void disconnect(PermsTestFtpClient client,
                                  NetworkFtpClientTaskRunner taskRunner,
                                  Handler mainHandler,
                                  Runnable clearRemoteList,
                                  StatusSetter statusSetter,
                                  OutputAppender outputAppender) {
        runTask(taskRunner, "Disconnecting...", () -> {
            if (client != null) client.disconnectQuietly();
            post(mainHandler, () -> {
                if (clearRemoteList != null) clearRemoteList.run();
                setStatus(statusSetter, "FTP client disconnected", false);
                appendOutput(outputAppender, "[FTP Client] Disconnected.\n");
            });
        });
    }

    private static boolean isBusy(NetworkFtpClientTaskRunner taskRunner) {
        return taskRunner != null && taskRunner.isBusy();
    }

    private static void runTask(NetworkFtpClientTaskRunner taskRunner,
                                String busyText,
                                NetworkFtpClientTaskRunner.Task task) {
        if (taskRunner != null) taskRunner.run(busyText, task);
    }

    private static void setEntries(RemoteEntriesSetter entriesSetter,
                                   List<PermsTestFtpClient.RemoteEntry> entries) {
        if (entriesSetter != null) entriesSetter.setEntries(entries);
    }

    private static void setStatus(StatusSetter statusSetter, String text, boolean error) {
        if (statusSetter != null) statusSetter.setStatus(text, error);
    }

    private static void appendOutput(OutputAppender outputAppender, String text) {
        if (outputAppender != null) outputAppender.appendOutput(text);
    }

    private static void post(Handler mainHandler, Runnable runnable) {
        if (mainHandler != null) {
            mainHandler.post(runnable);
        } else if (runnable != null) {
            runnable.run();
        }
    }

    private static String text(android.widget.TextView view, String fallback) {
        try {
            CharSequence value = view == null ? null : view.getText();
            String text = value == null ? "" : value.toString();
            return TextUtils.isEmpty(text) ? fallback : text;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
