package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * FTP client delete confirmation and background task bodies for the Network tab.
 */
public final class NetworkFtpClientDeleteActions {
    private NetworkFtpClientDeleteActions() {
    }

    public interface LocalEntryDeleter {
        boolean delete(FtpClientLocalEntry entry, boolean useShizuku) throws Exception;
    }

    public interface RemoteEntriesSetter {
        void setEntries(List<PermsTestFtpClient.RemoteEntry> entries);
    }

    public interface StatusSetter {
        void setStatus(String text, boolean error);
    }

    public static void confirmDeleteLocal(Context context,
                                          List<FtpClientLocalEntry> targets,
                                          boolean useShizuku,
                                          NetworkFtpClientTaskRunner taskRunner,
                                          Handler mainHandler,
                                          LocalEntryDeleter deleter,
                                          Runnable clearLocalSelection,
                                          Runnable refreshLocalList,
                                          StatusSetter statusSetter) {
        if (targets == null || targets.isEmpty()) {
            showToast(context, "Select one or more local items first.");
            return;
        }
        final int total = targets.size();
        String message = total == 1
                ? "Delete " + targetName(targets.get(0)) + "? Folders must be empty."
                : "Delete " + total + " selected local items? Folders must be empty.";
        new MaterialAlertDialogBuilder(context)
                .setTitle(total == 1 ? "Delete local item" : "Delete local items")
                .setMessage(message)
                .setPositiveButton("Delete", (d, w) -> runTask(taskRunner, total == 1 ? "Deleting local item..." : "Deleting local items...", () -> {
                    int okCount = 0;
                    for (FtpClientLocalEntry target : targets) {
                        try {
                            if (deleter != null && deleter.delete(target, useShizuku)) okCount++;
                        } catch (Throwable ignored) {
                        }
                    }
                    final int finalOkCount = okCount;
                    post(mainHandler, () -> {
                        if (clearLocalSelection != null) clearLocalSelection.run();
                        if (refreshLocalList != null) refreshLocalList.run();
                        String msg = total == 1
                                ? (finalOkCount == 1 ? "Deleted local item." : "Local delete failed.")
                                : "Deleted " + finalOkCount + "/" + total + " local items.";
                        setStatus(statusSetter, msg, finalOkCount != total);
                    });
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void confirmDeleteRemote(Context context,
                                           PermsTestFtpClient client,
                                           List<PermsTestFtpClient.RemoteEntry> targets,
                                           NetworkFtpClientTaskRunner taskRunner,
                                           Handler mainHandler,
                                           Runnable clearRemoteSelection,
                                           RemoteEntriesSetter entriesSetter,
                                           StatusSetter statusSetter) {
        if (targets == null || targets.isEmpty()) {
            showToast(context, "Select one or more remote items first.");
            return;
        }
        if (client == null || !client.isConnected()) {
            showToast(context, "Connect FTP client first.");
            return;
        }
        final int total = targets.size();
        String message = total == 1
                ? "Delete " + targetName(targets.get(0)) + "? Folders must be empty."
                : "Delete " + total + " selected remote items? Folders must be empty.";
        new MaterialAlertDialogBuilder(context)
                .setTitle(total == 1 ? "Delete remote item" : "Delete remote items")
                .setMessage(message)
                .setPositiveButton("Delete", (d, w) -> runTask(taskRunner, total == 1 ? "Deleting remote item..." : "Deleting remote items...", () -> {
                    int okCount = 0;
                    for (PermsTestFtpClient.RemoteEntry target : targets) {
                        if (target != null && client.delete(target)) okCount++;
                    }
                    List<PermsTestFtpClient.RemoteEntry> list = client.listCurrentDirectory();
                    final int finalOkCount = okCount;
                    post(mainHandler, () -> {
                        if (clearRemoteSelection != null) clearRemoteSelection.run();
                        setEntries(entriesSetter, list);
                        String msg = total == 1
                                ? (finalOkCount == 1 ? "Deleted remote item." : "Remote delete failed.")
                                : "Deleted " + finalOkCount + "/" + total + " remote items.";
                        setStatus(statusSetter, msg, finalOkCount != total);
                    });
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void runTask(NetworkFtpClientTaskRunner taskRunner, String busyText, NetworkFtpClientTaskRunner.Task task) {
        if (taskRunner != null) taskRunner.run(busyText, task);
    }

    private static void post(Handler mainHandler, Runnable runnable) {
        if (mainHandler != null) {
            mainHandler.post(runnable);
        } else if (runnable != null) {
            runnable.run();
        }
    }

    private static void setEntries(RemoteEntriesSetter setter, List<PermsTestFtpClient.RemoteEntry> entries) {
        if (setter != null) setter.setEntries(entries);
    }

    private static void setStatus(StatusSetter setter, String text, boolean error) {
        if (setter != null) setter.setStatus(text, error);
    }

    private static String targetName(FtpClientLocalEntry entry) {
        return entry == null || TextUtils.isEmpty(entry.name) ? "item" : entry.name;
    }

    private static String targetName(PermsTestFtpClient.RemoteEntry entry) {
        return entry == null || TextUtils.isEmpty(entry.name) ? "item" : entry.name;
    }

    private static void showToast(Context context, String text) {
        if (context != null) Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
