package dev.perms.test.shell;

import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import dev.perms.test.ExecMode;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ladb.LadbClient;
import dev.perms.test.ladb.LadbController;

/**
 * Activity-facing owner for Shell-tab controllers and shared shell binary/runtime helpers.
 *
 * MainActivity remains the provider for app-level state and cross-tab callbacks, while this
 * class owns Shell feature controller construction and small routing decisions.
 */
public final class ShellActivityControllers {
    public interface Host {
        ActivityMainBinding binding();
        SharedPreferences prefs();
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
        void setInteractiveShellRunningUi(boolean running);
        void setOutputTag(String tag);
        void runShellCommand(String command);
        void stopInteractiveShellCommand();
        void copyOutputToClipboard();
        void clearOutput();
        void resetOutputPanelHeight();
        String getTargetPackage();
        String getSelfPackageName();
        boolean isSafeToken(String token);
        void toast(String message);
        int dp(int value);
    }

    public interface CaptureCallback {
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

    private final AppCompatActivity activity;
    private final ExecutorService io;
    private final ExecutorService shellIo;
    private final String prefsName;
    private final String customCommandsKey;
    private final Host host;

    private ShellBinaryAssets shellBinaryAssets;
    private ShellBinaryController shellBinaryController;
    private ShellBundledBinsDialog shellBundledBinsDialog;
    private ShellCommandBarController shellCommandBarController;
    private ShellRuntimeController shellRuntimeController;
    private ShellQuickActionsController shellQuickActionsController;
    private ShellCustomCommandsController shellCustomCommandsController;

    public ShellActivityControllers(AppCompatActivity activity,
                                    ExecutorService io,
                                    ExecutorService shellIo,
                                    String prefsName,
                                    String customCommandsKey,
                                    Host host) {
        this.activity = activity;
        this.io = io;
        this.shellIo = shellIo;
        this.prefsName = prefsName;
        this.customCommandsKey = customCommandsKey;
        this.host = host;
    }

    public ShellBinaryAssets getBinaryAssets() {
        if (shellBinaryAssets == null) {
            shellBinaryAssets = new ShellBinaryAssets(activity, () -> host != null && host.isBackendReadyAndGranted());
        }
        return shellBinaryAssets;
    }

    public boolean hasBundledAsset(String name) {
        return getBinaryAssets().hasBundledAsset(name);
    }

    public InputStream openBundledAsset(String name) throws IOException {
        return getBinaryAssets().openBundledAsset(name);
    }

    public void ensureBundledBinaryPublic(String name) {
        getBinaryAssets().ensureBundledBinaryPublic(name);
    }

    public File getAppBinDir() {
        return getBinaryAssets().getAppBinDir();
    }

    public boolean ensureBundledBinary(String name) {
        return getBinaryAssets().ensureBundledBinary(name);
    }

    public boolean isBinaryAvailableSystemOnly(String name) {
        return getBinaryAssets().isBinaryAvailableSystemOnly(name);
    }

    public boolean isBinaryInstalledPublic(String name) {
        return getBinaryAssets().isBinaryInstalledPublic(name);
    }

    public String rewriteCommandWithLocalBins(String command) {
        return getBinaryAssets().rewriteCommandWithLocalBins(command);
    }

    public File getBundledStageDir() {
        return getBinaryAssets().getBundledStageDir();
    }

    public String resolveBundledAbiDir() {
        return getBinaryAssets().resolveBundledAbiDir();
    }

    public ShellBinaryController getBinaryController() {
        if (shellBinaryController == null) {
            shellBinaryController = new ShellBinaryController(getBinaryAssets());
        }
        return shellBinaryController;
    }

    public void refreshBinaryAvailabilityUi() {
        try {
            ActivityMainBinding binding = host == null ? null : host.binding();
            if (binding != null) getBinaryController().refresh(binding);
        } catch (Throwable ignored) {
        }
    }

    public void bindCommandBar() {
        try {
            getCommandBarController().bind();
        } catch (Throwable ignored) {
        }
    }

    public void bindQuickActions() {
        try {
            getQuickActionsController().bind();
        } catch (Throwable ignored) {
        }
    }

    public void registerCustomCommandActivityResults() {
        try {
            getCustomCommandsController().registerActivityResults();
        } catch (Throwable ignored) {
        }
    }

    public void bindCustomCommands() {
        try {
            getCustomCommandsController().bind(host == null ? null : host.binding());
        } catch (Throwable ignored) {
        }
    }

    public void runAndFillCommand(String command) {
        getCustomCommandsController().runAndFill(command);
    }

    public void showManageBundledBinsDialog() {
        getBundledBinsDialog().show();
    }

    public CommandResult runCaptureBlocking(String command) {
        ShellRuntimeController.CommandResult result = getRuntimeController().runCaptureBlocking(command);
        return new CommandResult(result.exitCode, result.stdout, result.stderr);
    }

    public void runCaptureAsync(String command, CaptureCallback callback) {
        getRuntimeController().runCaptureAsync(command, (exit, out, err) -> {
            if (callback != null) callback.onComplete(exit, out, err);
        });
    }

    public void runCaptureAndAppend(String command, CaptureCallback callback) {
        getRuntimeController().runCaptureAndAppend(command, (exit, out, err) -> {
            if (callback != null) callback.onComplete(exit, out, err);
        });
    }

    public void runInteractiveCommand(String command) {
        getRuntimeController().runInteractiveCommand(command);
    }

    public void stopInteractiveCommand() {
        getRuntimeController().stopInteractiveCommand();
    }

    public ShellRuntimeController getRuntimeController() {
        if (shellRuntimeController == null) {
            shellRuntimeController = new ShellRuntimeController(shellIo, new ShellRuntimeController.Host() {
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
                public LadbClient getLadbClient() {
                    return host == null ? null : host.getLadbClient();
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
                public void setInteractiveRunningUi(boolean running) {
                    if (host != null) host.setInteractiveShellRunningUi(running);
                }

                @Override
                public void setOutputTag(String tag) {
                    if (host != null) host.setOutputTag(tag);
                }

                @Override
                public String rewriteCommandWithLocalBins(String command) {
                    return ShellActivityControllers.this.rewriteCommandWithLocalBins(command);
                }
            });
        }
        return shellRuntimeController;
    }

    private ShellCommandBarController getCommandBarController() {
        if (shellCommandBarController == null) {
            shellCommandBarController = new ShellCommandBarController(host == null ? null : host.binding(), new ShellCommandBarController.Host() {
                @Override
                public void runCommand(String command) {
                    if (host != null) host.runShellCommand(command);
                }

                @Override
                public void stopInteractiveCommand() {
                    if (host != null) host.stopInteractiveShellCommand();
                }

                @Override
                public void copyOutput() {
                    if (host != null) host.copyOutputToClipboard();
                }

                @Override
                public void clearOutput() {
                    if (host != null) host.clearOutput();
                }

                @Override
                public void resetOutputPanelHeight() {
                    if (host != null) host.resetOutputPanelHeight();
                }

                @Override
                public void setInteractiveRunning(boolean running) {
                    if (host != null) host.setInteractiveShellRunningUi(running);
                }
            });
        }
        return shellCommandBarController;
    }

    private ShellQuickActionsController getQuickActionsController() {
        if (shellQuickActionsController == null) {
            shellQuickActionsController = new ShellQuickActionsController(activity, host == null ? null : host.binding(), new ShellQuickActionsController.Host() {
                @Override
                public void runShellCommand(String command) {
                    if (host != null) host.runShellCommand(command);
                }

                @Override
                public void runAndFillCommand(String command) {
                    ShellActivityControllers.this.runAndFillCommand(command);
                }

                @Override
                public void toast(String message) {
                    if (host != null) host.toast(message);
                }

                @Override
                public String getSelectedPackageName() {
                    return host == null ? "" : host.getTargetPackage();
                }

                @Override
                public String getSelfPackageName() {
                    return host == null ? "" : host.getSelfPackageName();
                }

                @Override
                public void showManageBundledBinsDialog() {
                    ShellActivityControllers.this.showManageBundledBinsDialog();
                }
            });
        }
        return shellQuickActionsController;
    }

    private ShellBundledBinsDialog getBundledBinsDialog() {
        if (shellBundledBinsDialog == null) {
            shellBundledBinsDialog = new ShellBundledBinsDialog(activity, getBinaryAssets(), io, new ShellBundledBinsDialog.Host() {
                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void refreshAvailability() {
                    refreshBinaryAvailabilityUi();
                    if (host != null) host.refreshStatus();
                }
            });
        }
        return shellBundledBinsDialog;
    }

    private ShellCustomCommandsController getCustomCommandsController() {
        if (shellCustomCommandsController == null) {
            shellCustomCommandsController = new ShellCustomCommandsController(activity, prefsName, customCommandsKey, new ShellCustomCommandsController.Host() {
                @Override
                public String getTargetPackage() {
                    return host == null ? "" : host.getTargetPackage();
                }

                @Override
                public boolean isSafeToken(String token) {
                    return host != null && host.isSafeToken(token);
                }

                @Override
                public void runShellCommand(String command) {
                    if (host != null) host.runShellCommand(command);
                }

                @Override
                public void toast(String message) {
                    if (host != null) host.toast(message);
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public int dp(int value) {
                    return host == null ? value : host.dp(value);
                }
            });
        }
        return shellCustomCommandsController;
    }
}
