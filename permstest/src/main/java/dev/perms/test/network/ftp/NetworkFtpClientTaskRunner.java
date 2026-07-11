package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.os.Handler;

import java.util.concurrent.ExecutorService;

/**
 * Runs FTP client background tasks while preserving the single-task UI gate.
 */
public final class NetworkFtpClientTaskRunner {
    public interface Task {
        void run() throws Exception;
    }

    public interface BusyNotifier {
        void onBusy();
    }

    public interface StatusUpdater {
        void updateStatus(String text, boolean error);
    }

    public interface UiUpdater {
        void updateUi();
    }

    public interface OutputAppender {
        void appendOutput(String text);
    }

    private final ExecutorService executor;
    private final Handler mainHandler;
    private final BusyNotifier busyNotifier;
    private final StatusUpdater statusUpdater;
    private final UiUpdater uiUpdater;
    private final OutputAppender outputAppender;
    private volatile boolean busy;

    public NetworkFtpClientTaskRunner(ExecutorService executor,
                                      Handler mainHandler,
                                      BusyNotifier busyNotifier,
                                      StatusUpdater statusUpdater,
                                      UiUpdater uiUpdater,
                                      OutputAppender outputAppender) {
        this.executor = executor;
        this.mainHandler = mainHandler;
        this.busyNotifier = busyNotifier;
        this.statusUpdater = statusUpdater;
        this.uiUpdater = uiUpdater;
        this.outputAppender = outputAppender;
    }

    public boolean isBusy() {
        return busy;
    }

    public void run(String busyText, Task task) {
        if (busy) {
            if (busyNotifier != null) busyNotifier.onBusy();
            return;
        }
        busy = true;
        if (statusUpdater != null) statusUpdater.updateStatus(busyText, false);
        if (uiUpdater != null) uiUpdater.updateUi();
        executor.execute(() -> {
            try {
                if (task != null) task.run();
            } catch (Throwable e) {
                postToMain(() -> {
                    String message = e.getMessage();
                    if (statusUpdater != null) statusUpdater.updateStatus("FTP client error: " + message, true);
                    if (outputAppender != null) outputAppender.appendOutput("[FTP Client] Error: " + message + "\n");
                });
            } finally {
                busy = false;
                postToMain(() -> {
                    if (uiUpdater != null) uiUpdater.updateUi();
                });
            }
        });
    }

    private void postToMain(Runnable runnable) {
        if (mainHandler != null) {
            mainHandler.post(runnable);
        } else if (runnable != null) {
            runnable.run();
        }
    }
}
