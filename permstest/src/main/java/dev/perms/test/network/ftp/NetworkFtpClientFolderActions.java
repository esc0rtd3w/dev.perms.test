package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.content.Context;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

/**
 * FTP client folder-create dialog and task bodies for the Network tab.
 */
public final class NetworkFtpClientFolderActions {
    private NetworkFtpClientFolderActions() {
    }

    public interface LocalDirectoryProvider {
        String getLocalDirectory();
    }

    public interface UseShizukuProvider {
        boolean useShizuku();
    }

    public interface RemoteEntriesSetter {
        void setEntries(java.util.List<PermsTestFtpClient.RemoteEntry> entries);
    }

    public interface StatusSetter {
        void setStatus(String text, boolean error);
    }

    public static void showNewFolderDialog(Context context,
                                           boolean remote,
                                           PermsTestFtpClient client,
                                           NetworkFtpClientTaskRunner taskRunner,
                                           Handler mainHandler,
                                           LocalDirectoryProvider localDirectoryProvider,
                                           UseShizukuProvider useShizukuProvider,
                                           NetworkFtpClientLocalFileOps.ShizukuReadyProvider shizukuReadyProvider,
                                           Runnable refreshLocalList,
                                           RemoteEntriesSetter remoteEntriesSetter,
                                           StatusSetter statusSetter) {
        if (remote && (client == null || !client.isConnected())) {
            showToast(context, "Connect FTP client first.");
            return;
        }
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int pad = dp(context, 16);
        FrameLayout box = new FrameLayout(context);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(context)
                .setTitle(remote ? "New remote folder" : "New local folder")
                .setView(box)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = PermsTestFtpClient.sanitizeName(input.getText() == null ? "" : input.getText().toString());
                    if (TextUtils.isEmpty(name)) {
                        showToast(context, "Folder name is empty.");
                        return;
                    }
                    if (remote) {
                        createRemoteFolder(client, taskRunner, mainHandler, name, remoteEntriesSetter, statusSetter);
                    } else {
                        createLocalFolder(context, name, localDirectoryProvider, useShizukuProvider, shizukuReadyProvider, taskRunner, mainHandler, refreshLocalList, statusSetter);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void createLocalFolder(Context context,
                                          String name,
                                          LocalDirectoryProvider localDirectoryProvider,
                                          UseShizukuProvider useShizukuProvider,
                                          NetworkFtpClientLocalFileOps.ShizukuReadyProvider shizukuReadyProvider,
                                          NetworkFtpClientTaskRunner taskRunner,
                                          Handler mainHandler,
                                          Runnable refreshLocalList,
                                          StatusSetter statusSetter) {
        final String safeName = PermsTestFtpClient.sanitizeName(name);
        if (TextUtils.isEmpty(safeName)) {
            showToast(context, "Folder name is empty.");
            return;
        }
        final boolean useShizuku = useShizukuProvider != null && useShizukuProvider.useShizuku();
        if (useShizuku) {
            runTask(taskRunner, "Creating local folder...", () -> {
                String localDir = localDirectoryProvider == null ? "" : localDirectoryProvider.getLocalDirectory();
                boolean ok = NetworkFtpClientLocalFileOps.createFolderViaShizuku(localDir, safeName, shizukuReadyProvider);
                post(mainHandler, () -> {
                    if (refreshLocalList != null) refreshLocalList.run();
                    setStatus(statusSetter, ok ? "Created local folder." : "Local folder create failed.", !ok);
                });
            });
            return;
        }
        String localDir = localDirectoryProvider == null ? "" : localDirectoryProvider.getLocalDirectory();
        File dir = new File(NetworkFtpClientPaths.normalize(localDir), safeName);
        boolean ok = dir.isDirectory() || dir.mkdirs();
        if (refreshLocalList != null) refreshLocalList.run();
        setStatus(statusSetter, ok ? "Created local folder." : "Local folder create failed.", !ok);
    }

    private static void createRemoteFolder(PermsTestFtpClient client,
                                           NetworkFtpClientTaskRunner taskRunner,
                                           Handler mainHandler,
                                           String name,
                                           RemoteEntriesSetter entriesSetter,
                                           StatusSetter statusSetter) {
        NetworkFtpClientRemoteActions.RemoteEntriesSetter remoteEntriesSetter =
                entriesSetter == null ? null : entriesSetter::setEntries;
        NetworkFtpClientRemoteActions.StatusSetter remoteStatusSetter =
                statusSetter == null ? null : statusSetter::setStatus;
        NetworkFtpClientRemoteActions.createFolder(
                client,
                taskRunner,
                mainHandler,
                name,
                remoteEntriesSetter,
                remoteStatusSetter);
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

    private static void setStatus(StatusSetter statusSetter, String text, boolean error) {
        if (statusSetter != null) statusSetter.setStatus(text, error);
    }

    private static void showToast(Context context, String text) {
        if (context != null) Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
