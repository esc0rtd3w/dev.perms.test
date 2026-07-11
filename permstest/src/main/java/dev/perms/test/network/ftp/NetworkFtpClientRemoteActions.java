package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.os.Handler;

import java.util.List;

/**
 * Remote-side FTP client actions shared by the Network tab Activity wiring.
 */
public final class NetworkFtpClientRemoteActions {
    private NetworkFtpClientRemoteActions() {
    }

    public interface RemoteEntriesSetter {
        void setEntries(List<PermsTestFtpClient.RemoteEntry> entries);
    }

    public interface StatusSetter {
        void setStatus(String text, boolean error);
    }

    public static void refreshList(PermsTestFtpClient client,
                                   NetworkFtpClientTaskRunner taskRunner,
                                   Handler mainHandler,
                                   Runnable clearRemoteList,
                                   Runnable updateClientUi,
                                   RemoteEntriesSetter entriesSetter) {
        if (client == null || !client.isConnected()) {
            if (clearRemoteList != null) clearRemoteList.run();
            if (updateClientUi != null) updateClientUi.run();
            return;
        }
        runTask(taskRunner, "Refreshing remote files...", () -> {
            List<PermsTestFtpClient.RemoteEntry> remote = client.listCurrentDirectory();
            post(mainHandler, () -> setEntries(entriesSetter, remote));
        });
    }

    public static void changeDirectory(PermsTestFtpClient client,
                                       NetworkFtpClientTaskRunner taskRunner,
                                       Handler mainHandler,
                                       String path,
                                       RemoteEntriesSetter entriesSetter) {
        if (client == null || !client.isConnected()) return;
        runTask(taskRunner, "Opening remote folder...", () -> {
            client.changeDirectory(path);
            List<PermsTestFtpClient.RemoteEntry> remote = client.listCurrentDirectory();
            post(mainHandler, () -> setEntries(entriesSetter, remote));
        });
    }

    public static void changeToParent(PermsTestFtpClient client,
                                      NetworkFtpClientTaskRunner taskRunner,
                                      Handler mainHandler,
                                      RemoteEntriesSetter entriesSetter) {
        if (client == null || !client.isConnected()) return;
        runTask(taskRunner, "Opening parent folder...", () -> {
            client.changeToParentDirectory();
            List<PermsTestFtpClient.RemoteEntry> remote = client.listCurrentDirectory();
            post(mainHandler, () -> setEntries(entriesSetter, remote));
        });
    }

    public static void createFolder(PermsTestFtpClient client,
                                    NetworkFtpClientTaskRunner taskRunner,
                                    Handler mainHandler,
                                    String name,
                                    RemoteEntriesSetter entriesSetter,
                                    StatusSetter statusSetter) {
        runTask(taskRunner, "Creating remote folder...", () -> {
            boolean ok = client.mkdir(name);
            List<PermsTestFtpClient.RemoteEntry> list = client.listCurrentDirectory();
            post(mainHandler, () -> {
                setEntries(entriesSetter, list);
                if (statusSetter != null) {
                    statusSetter.setStatus(ok ? "Created remote folder." : "Remote folder create failed.", !ok);
                }
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

    private static void post(Handler mainHandler, Runnable runnable) {
        if (mainHandler != null) {
            mainHandler.post(runnable);
        } else if (runnable != null) {
            runnable.run();
        }
    }
}
