package dev.perms.test.shell;

import java.util.concurrent.ExecutorService;

import dev.perms.test.ExecMode;
import dev.perms.test.ladb.LadbClient;
import dev.perms.test.ladb.LadbController;

/**
 * Owns the Shell tab command runners so MainActivity only adapts app state and UI callbacks.
 */
public final class ShellRuntimeController {
    public interface Host {
        boolean isBackendReadyAndGranted();
        void refreshStatus();
        void appendOutput(String text);
        void executeIo(Runnable task);
        void runOnUiThread(Runnable task);
        ExecMode getExecMode();
        LadbController getLadbController();
        LadbClient getLadbClient();
        void lifetimeLogActionForCommand(String command);
        void lifetimeLog(String tag, String message);
        void setInteractiveRunningUi(boolean running);
        void setOutputTag(String tag);
        String rewriteCommandWithLocalBins(String command);
    }

    public interface Callback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public static final class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final ExecutorService interactiveExecutor;
    private final Host host;
    private ShellCommandCaptureRunner captureRunner;
    private ShellInteractiveCommandRunner interactiveRunner;

    public ShellRuntimeController(ExecutorService interactiveExecutor, Host host) {
        this.interactiveExecutor = interactiveExecutor;
        this.host = host;
    }

    public CommandResult runCaptureBlocking(String command) {
        ShellCommandCaptureRunner.Result result = getCaptureRunner().runBlocking(command);
        return new CommandResult(result.exitCode, result.stdout, result.stderr);
    }

    public void runCaptureAsync(String command, Callback callback) {
        getCaptureRunner().runAsync(command, (exit, out, err) -> {
            if (callback != null) callback.onComplete(exit, out, err);
        });
    }

    public void runCaptureAndAppend(String command, Callback callback) {
        if (host != null) host.setOutputTag("shell");
        getCaptureRunner().runAndAppend(command, (exit, out, err) -> {
            if (callback != null) callback.onComplete(exit, out, err);
        });
    }

    public void runInteractiveCommand(String command) {
        getInteractiveRunner().runCommand(command);
    }

    public void stopInteractiveCommand() {
        getInteractiveRunner().stopCommand();
    }

    private ShellCommandCaptureRunner getCaptureRunner() {
        if (captureRunner == null) {
            captureRunner = new ShellCommandCaptureRunner(new ShellCommandCaptureRunner.Host() {
                @Override
                public boolean isBackendReadyAndGranted() {
                    return host != null && host.isBackendReadyAndGranted();
                }

                @Override
                public void refreshStatus() {
                    if (host != null) host.refreshStatus();
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void executeIo(Runnable task) {
                    if (host != null) host.executeIo(task);
                }

                @Override
                public void runOnUiThread(Runnable task) {
                    if (host != null) host.runOnUiThread(task);
                }

                @Override
                public ExecMode getExecMode() {
                    return host == null ? ExecMode.SHIZUKU : host.getExecMode();
                }

                @Override
                public LadbController getLadbController() {
                    return host == null ? null : host.getLadbController();
                }

                @Override
                public void lifetimeLogActionForCommand(String command) {
                    if (host != null) host.lifetimeLogActionForCommand(command);
                }

                @Override
                public void lifetimeLog(String tag, String message) {
                    if (host != null) host.lifetimeLog(tag, message);
                }
            });
        }
        return captureRunner;
    }

    private ShellInteractiveCommandRunner getInteractiveRunner() {
        if (interactiveRunner == null) {
            interactiveRunner = new ShellInteractiveCommandRunner(interactiveExecutor, new ShellInteractiveCommandRunner.Host() {
                @Override
                public boolean isBackendReady() {
                    return host != null && host.isBackendReadyAndGranted();
                }

                @Override
                public void refreshStatus() {
                    if (host != null) host.refreshStatus();
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void runOnUiThread(Runnable action) {
                    if (host != null) host.runOnUiThread(action);
                }

                @Override
                public void setRunningUi(boolean running) {
                    if (host != null) host.setInteractiveRunningUi(running);
                }

                @Override
                public void lifetimeLogActionForCommand(String command) {
                    if (host != null) host.lifetimeLogActionForCommand(command);
                }

                @Override
                public void lifetimeLog(String tag, String message) {
                    if (host != null) host.lifetimeLog(tag, message);
                }

                @Override
                public void setOutputTag(String tag) {
                    if (host != null) host.setOutputTag(tag);
                }

                @Override
                public String rewriteCommandWithLocalBins(String command) {
                    return host == null ? command : host.rewriteCommandWithLocalBins(command);
                }

                @Override
                public ExecMode getExecMode() {
                    return host == null ? ExecMode.SHIZUKU : host.getExecMode();
                }

                @Override
                public LadbClient getLadbClient() {
                    return host == null ? null : host.getLadbClient();
                }
            });
        }
        return interactiveRunner;
    }
}
