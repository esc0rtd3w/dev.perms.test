package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates FTP client local-pane refreshes while Activity-supplied Shizuku
 * command execution stays behind a narrow runner callback.
 */
public final class NetworkFtpClientLocalRefreshActions {
    private int refreshSeq;

    public String refresh(String currentDirectory,
                          boolean useShizuku,
                          ShizukuReadyChecker shizukuReadyChecker,
                          ShizukuCommandRunner shizukuCommandRunner,
                          LocalEntriesReceiver localEntriesReceiver,
                          StatusSetter statusSetter) {
        String directory = currentDirectory;
        if (TextUtils.isEmpty(directory)) {
            directory = NetworkFtpClientPaths.ensureLocalDirectory(NetworkFtpClientPaths.defaultLocalPath());
        }
        directory = NetworkFtpClientPaths.normalize(directory);
        int seq = ++refreshSeq;
        if (useShizuku) {
            refreshViaShizuku(seq, directory, shizukuReadyChecker, shizukuCommandRunner, localEntriesReceiver, statusSetter);
        } else {
            refreshViaAndroid(seq, directory, localEntriesReceiver, statusSetter);
        }
        return directory;
    }

    private void refreshViaAndroid(int seq,
                                   String requestedPath,
                                   LocalEntriesReceiver localEntriesReceiver,
                                   StatusSetter statusSetter) {
        try {
            NetworkFtpClientLocalListLoader.AndroidResult result = NetworkFtpClientLocalListLoader.listWithAndroid(
                    requestedPath,
                    NetworkFtpClientPaths.ensureLocalDirectory(NetworkFtpClientPaths.defaultLocalPath()));
            if (!TextUtils.isEmpty(result.warning) && statusSetter != null) {
                statusSetter.setStatus(result.warning, true);
            }
            if (seq == refreshSeq && localEntriesReceiver != null) {
                localEntriesReceiver.setLocalEntries(NetworkFtpClientPaths.normalize(result.directory), result.entries);
            }
        } catch (Throwable e) {
            if (statusSetter != null) statusSetter.setStatus("Local list failed: " + e.getMessage(), true);
        }
    }

    private void refreshViaShizuku(int seq,
                                   String requestedPath,
                                   ShizukuReadyChecker shizukuReadyChecker,
                                   ShizukuCommandRunner shizukuCommandRunner,
                                   LocalEntriesReceiver localEntriesReceiver,
                                   StatusSetter statusSetter) {
        if (shizukuReadyChecker == null || !shizukuReadyChecker.isReady()) {
            if (statusSetter != null) {
                statusSetter.setStatus("Shizuku file access is not ready; using Android local listing.", true);
            }
            refreshViaAndroid(seq, requestedPath, localEntriesReceiver, statusSetter);
            return;
        }
        if (shizukuCommandRunner == null) {
            if (statusSetter != null) statusSetter.setStatus("Shizuku local list failed: runner unavailable", true);
            refreshViaAndroid(seq, requestedPath, localEntriesReceiver, statusSetter);
            return;
        }
        String normalized = NetworkFtpClientPaths.normalize(requestedPath);
        String cmd = NetworkFtpClientLocalListLoader.buildShizukuListCommand(normalized);
        shizukuCommandRunner.run(cmd, (exit, out, err) -> {
            if (seq != refreshSeq) return;
            ArrayList<FtpClientLocalEntry> entries = NetworkFtpClientLocalListLoader.parseShizukuStatListing(normalized, out);
            if (exit != 0 && entries.isEmpty()) {
                if (statusSetter != null) {
                    statusSetter.setStatus("Shizuku local list failed (" + exit + "): " + NetworkFtpClientPaths.emptyToMessage(err), true);
                }
                refreshViaAndroid(seq, normalized, localEntriesReceiver, statusSetter);
                return;
            }
            if (localEntriesReceiver != null) localEntriesReceiver.setLocalEntries(normalized, entries);
        });
    }

    public interface ShizukuReadyChecker {
        boolean isReady();
    }

    public interface ShizukuCommandRunner {
        void run(String command, CaptureCallback callback);
    }

    public interface CaptureCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface LocalEntriesReceiver {
        void setLocalEntries(String directory, List<FtpClientLocalEntry> entries);
    }

    public interface StatusSetter {
        void setStatus(String text, boolean error);
    }
}
