package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.List;

/**
 * Handles FTP client pane navigation and selection updates.
 */
public final class NetworkFtpClientNavigationActions {
    private NetworkFtpClientNavigationActions() {}

    public static void onUseShizukuChanged(SharedPreferences prefs,
                                           boolean checked,
                                           NetworkFtpClientSelectionState selection,
                                           Runnable clearLocalSelection,
                                           Runnable refreshLocalList,
                                           ErrorSink errorSink) {
        try {
            if (prefs != null) {
                prefs.edit().putBoolean(NetworkPreferenceKeys.FTP_CLIENT_USE_SHIZUKU, checked).apply();
            }
            if (selection != null) selection.setSelectedLocal(null);
            run(clearLocalSelection);
            run(refreshLocalList);
        } catch (Throwable e) {
            if (errorSink != null) errorSink.onError("[FTP Client] mode change failed: " + e.getMessage() + "\n");
        }
    }

    public static void onLocalItemClick(int position,
                                        List<FtpClientLocalEntry> entries,
                                        NetworkFtpClientSelectionState selection,
                                        StringSetter localDirectorySetter,
                                        Runnable clearLocalSelection,
                                        Runnable saveLocalDirectory,
                                        Runnable refreshLocalList,
                                        Runnable refreshLocalAdapter,
                                        Runnable updateUi) {
        if (entries == null || position < 0 || position >= entries.size()) return;
        FtpClientLocalEntry entry = entries.get(position);
        if (selection != null && selection.localMultiSelectActive()) {
            selection.toggleLocal(entry);
            run(refreshLocalAdapter);
            run(updateUi);
            return;
        }
        if (entry != null && entry.directory) {
            if (localDirectorySetter != null) localDirectorySetter.set(NetworkFtpClientPaths.normalize(entry.path));
            if (selection != null) selection.setSelectedLocal(null);
            run(clearLocalSelection);
            run(saveLocalDirectory);
            run(refreshLocalList);
        } else {
            if (selection != null) selection.setSelectedLocal(entry);
            run(clearLocalSelection);
            run(updateUi);
        }
    }

    public static boolean onLocalItemLongClick(int position,
                                               List<FtpClientLocalEntry> entries,
                                               NetworkFtpClientSelectionState selection,
                                               Runnable refreshLocalAdapter,
                                               Runnable updateUi) {
        if (entries == null || position < 0 || position >= entries.size()) return false;
        if (selection != null) selection.toggleLocal(entries.get(position));
        run(refreshLocalAdapter);
        run(updateUi);
        return true;
    }

    public static void onRemoteItemClick(int position,
                                         List<PermsTestFtpClient.RemoteEntry> entries,
                                         PermsTestFtpClient client,
                                         NetworkFtpClientSelectionState selection,
                                         StringConsumer changeRemoteDirectory,
                                         Runnable clearRemoteSelection,
                                         Runnable refreshRemoteAdapter,
                                         Runnable updateUi) {
        if (entries == null || position < 0 || position >= entries.size()) return;
        PermsTestFtpClient.RemoteEntry entry = entries.get(position);
        if (selection != null && selection.remoteMultiSelectActive()) {
            selection.toggleRemote(entry);
            run(refreshRemoteAdapter);
            run(updateUi);
            return;
        }
        if (entry != null && entry.directory) {
            run(clearRemoteSelection);
            if (changeRemoteDirectory != null) {
                changeRemoteDirectory.accept(PermsTestFtpClient.remoteChild(client.currentDirectory(), entry.name));
            }
        } else {
            if (selection != null) selection.setSelectedRemote(entry);
            run(clearRemoteSelection);
            run(updateUi);
        }
    }

    public static boolean onRemoteItemLongClick(int position,
                                                List<PermsTestFtpClient.RemoteEntry> entries,
                                                NetworkFtpClientSelectionState selection,
                                                Runnable refreshRemoteAdapter,
                                                Runnable updateUi) {
        if (entries == null || position < 0 || position >= entries.size()) return false;
        if (selection != null) selection.toggleRemote(entries.get(position));
        run(refreshRemoteAdapter);
        run(updateUi);
        return true;
    }

    public static void openLocalParent(String currentDirectory,
                                       StringSetter localDirectorySetter,
                                       NetworkFtpClientSelectionState selection,
                                       Runnable clearLocalSelection,
                                       Runnable saveLocalDirectory,
                                       Runnable refreshLocalList) {
        String parent = NetworkFtpClientPaths.parentOf(currentDirectory);
        if (!TextUtils.isEmpty(parent) && !TextUtils.equals(parent, currentDirectory)) {
            if (localDirectorySetter != null) localDirectorySetter.set(parent);
            if (selection != null) selection.setSelectedLocal(null);
            run(clearLocalSelection);
            run(saveLocalDirectory);
            run(refreshLocalList);
        }
    }

    public static void openLocalHome(StringSetter localDirectorySetter,
                                     NetworkFtpClientSelectionState selection,
                                     Runnable clearLocalSelection,
                                     Runnable saveLocalDirectory,
                                     Runnable refreshLocalList) {
        String home = NetworkFtpClientPaths.ensureLocalDirectory(NetworkFtpClientPaths.defaultLocalPath());
        if (localDirectorySetter != null) localDirectorySetter.set(home);
        if (selection != null) selection.setSelectedLocal(null);
        run(clearLocalSelection);
        run(saveLocalDirectory);
        run(refreshLocalList);
    }

    private static void run(Runnable runnable) {
        if (runnable != null) runnable.run();
    }

    public interface StringSetter {
        void set(String value);
    }

    public interface StringConsumer {
        void accept(String value);
    }

    public interface ErrorSink {
        void onError(String message);
    }
}
