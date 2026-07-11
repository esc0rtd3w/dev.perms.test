package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.os.Handler;

import java.io.File;
import java.util.List;

/**
 * FTP client upload/download task bodies for the Network tab.
 */
public final class NetworkFtpClientTransferActions {
    private NetworkFtpClientTransferActions() {
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

    public static void uploadSelected(PermsTestFtpClient client,
                                      NetworkFtpClientTaskRunner taskRunner,
                                      Handler mainHandler,
                                      List<FtpClientLocalEntry> locals,
                                      boolean useShizuku,
                                      NetworkFtpClientLocalFileOps.ShizukuReadyProvider shizukuReady,
                                      Runnable clearLocalSelection,
                                      Runnable refreshLocalDisplay,
                                      RemoteEntriesSetter remoteEntriesSetter,
                                      StatusSetter statusSetter,
                                      OutputAppender outputAppender) {
        if (locals == null || locals.isEmpty()) return;
        final int total = locals.size();
        runTask(taskRunner, total == 1 ? "Uploading " + locals.get(0).name + "..." : "Uploading " + total + " files...", () -> {
            int okCount = 0;
            for (FtpClientLocalEntry local : locals) {
                if (local == null || !local.file) continue;
                boolean ok = useShizuku
                        ? NetworkFtpClientLocalFileOps.uploadViaShizuku(client, local, shizukuReady)
                        : client.upload(new File(local.path));
                if (ok) okCount++;
            }
            List<PermsTestFtpClient.RemoteEntry> remote = client.listCurrentDirectory();
            final int finalOkCount = okCount;
            post(mainHandler, () -> {
                if (clearLocalSelection != null) clearLocalSelection.run();
                if (refreshLocalDisplay != null) refreshLocalDisplay.run();
                setEntries(remoteEntriesSetter, remote);
                String msg = total == 1
                        ? (finalOkCount == 1 ? "Uploaded " + locals.get(0).name : "Upload failed: " + locals.get(0).name)
                        : "Uploaded " + finalOkCount + "/" + total + " files.";
                setStatus(statusSetter, msg, finalOkCount != total);
                appendOutput(outputAppender, "[FTP Client] " + msg + "\n");
            });
        });
    }

    public static void downloadSelected(PermsTestFtpClient client,
                                        NetworkFtpClientTaskRunner taskRunner,
                                        Handler mainHandler,
                                        List<PermsTestFtpClient.RemoteEntry> remotes,
                                        String localDir,
                                        boolean useShizuku,
                                        NetworkFtpClientLocalFileOps.ShizukuReadyProvider shizukuReady,
                                        Runnable clearRemoteSelection,
                                        Runnable refreshRemoteDisplay,
                                        Runnable refreshLocalList,
                                        StatusSetter statusSetter,
                                        OutputAppender outputAppender) {
        if (remotes == null || remotes.isEmpty()) return;
        final int total = remotes.size();
        runTask(taskRunner, total == 1 ? "Downloading " + remotes.get(0).name + "..." : "Downloading " + total + " files...", () -> {
            int okCount = 0;
            for (PermsTestFtpClient.RemoteEntry remote : remotes) {
                if (remote == null || !remote.file) continue;
                boolean ok = useShizuku
                        ? NetworkFtpClientLocalFileOps.downloadViaShizuku(client, remote, localDir, shizukuReady)
                        : client.download(remote, new File(localDir));
                if (ok) okCount++;
            }
            final int finalOkCount = okCount;
            post(mainHandler, () -> {
                if (clearRemoteSelection != null) clearRemoteSelection.run();
                if (refreshRemoteDisplay != null) refreshRemoteDisplay.run();
                if (refreshLocalList != null) refreshLocalList.run();
                String msg = total == 1
                        ? (finalOkCount == 1 ? "Downloaded " + remotes.get(0).name : "Download failed: " + remotes.get(0).name)
                        : "Downloaded " + finalOkCount + "/" + total + " files.";
                setStatus(statusSetter, msg, finalOkCount != total);
                appendOutput(outputAppender, "[FTP Client] " + msg + "\n");
            });
        });
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
}
