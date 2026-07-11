package dev.perms.test.network;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;

import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * External services supplied by MainActivity for Activity-side Network controllers.
 *
 * The Network package owns tab wiring, FTP runtime holder ownership, preference
 * keys, and FTP client action/UI refresh coordination while MainActivity keeps
 * shared app state, shell execution, and lifecycle ownership. FTP-specific shell
 * conversion stays in FTP server/client bridge helpers instead of this
 * dependency container.
 */
public final class NetworkActivityDependencies {
    private final Activity activity;
    private final BindingProvider bindingProvider;
    private final SharedPreferencesProvider prefsProvider;
    private final Handler mainHandler;
    private final ExecutorService ioExecutor;
    private final ShellCaptureRunner ftpServerShellCaptureRunner;
    private final ShizukuProcessStarter ftpServerProcessStarter;
    private final DebugOutputProvider debugOutputProvider;
    private final OutputAppender outputAppender;
    private final ShizukuReadyProvider fileShizukuReadyProvider;
    private final ShizukuCommandRunner ftpClientLocalRefreshRunner;
    private final OutputTagSetter outputTagSetter;
    private final ShellCommandRunner shellCommandRunner;

    public NetworkActivityDependencies(Activity activity,
                                       BindingProvider bindingProvider,
                                       SharedPreferencesProvider prefsProvider,
                                       Handler mainHandler,
                                       ExecutorService ioExecutor,
                                       ShellCaptureRunner ftpServerShellCaptureRunner,
                                       ShizukuProcessStarter ftpServerProcessStarter,
                                       DebugOutputProvider debugOutputProvider,
                                       OutputAppender outputAppender,
                                       ShizukuReadyProvider fileShizukuReadyProvider,
                                       ShizukuCommandRunner ftpClientLocalRefreshRunner,
                                       OutputTagSetter outputTagSetter,
                                       ShellCommandRunner shellCommandRunner) {
        this.activity = activity;
        this.bindingProvider = bindingProvider;
        this.prefsProvider = prefsProvider;
        this.mainHandler = mainHandler;
        this.ioExecutor = ioExecutor;
        this.ftpServerShellCaptureRunner = ftpServerShellCaptureRunner;
        this.ftpServerProcessStarter = ftpServerProcessStarter;
        this.debugOutputProvider = debugOutputProvider;
        this.outputAppender = outputAppender;
        this.fileShizukuReadyProvider = fileShizukuReadyProvider;
        this.ftpClientLocalRefreshRunner = ftpClientLocalRefreshRunner;
        this.outputTagSetter = outputTagSetter;
        this.shellCommandRunner = shellCommandRunner;
    }

    public Activity getActivity() {
        return activity;
    }

    public ActivityMainBinding getBinding() {
        return bindingProvider == null ? null : bindingProvider.getBinding();
    }

    public SharedPreferences getPreferences() {
        return prefsProvider == null ? null : prefsProvider.getPreferences();
    }

    public Handler getMainHandler() {
        return mainHandler;
    }

    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    public ShellResult runFtpServerShellCapture(String command) {
        return ftpServerShellCaptureRunner == null
                ? new ShellResult(1, "", "Shizuku not ready or permission not granted.")
                : ftpServerShellCaptureRunner.run(command);
    }

    public Process startFtpServerProcess(String command) throws java.io.IOException {
        if (ftpServerProcessStarter == null) throw new java.io.IOException("Shizuku not ready or permission not granted.");
        return ftpServerProcessStarter.start(command);
    }

    public boolean isDebugOutputEnabled() {
        return debugOutputProvider != null && debugOutputProvider.isDebugOutputEnabled();
    }

    public void appendOutput(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    public boolean filesCanUseShizuku() {
        return fileShizukuReadyProvider != null && fileShizukuReadyProvider.isReady();
    }

    public void runFtpClientLocalRefreshCommand(String command, CaptureCallback callback) {
        if (ftpClientLocalRefreshRunner != null) ftpClientLocalRefreshRunner.run(command, callback);
    }

    public void setOutputTag(String tag) {
        if (outputTagSetter != null) outputTagSetter.setTag(tag);
    }

    public void runShellCommand(String command) {
        if (shellCommandRunner != null) shellCommandRunner.run(command);
    }


    public static final class ShellResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    public interface BindingProvider {
        public ActivityMainBinding getBinding();
    }

    public interface SharedPreferencesProvider {
        public SharedPreferences getPreferences();
    }

    public interface ShellCaptureRunner {
        ShellResult run(String command);
    }

    public interface ShizukuProcessStarter {
        Process start(String command) throws java.io.IOException;
    }

    public interface DebugOutputProvider {
        public boolean isDebugOutputEnabled();
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface ShizukuReadyProvider {
        boolean isReady();
    }

    public interface CaptureCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface ShizukuCommandRunner {
        void run(String command, CaptureCallback callback);
    }

    public interface OutputTagSetter {
        void setTag(String tag);
    }

    public interface ShellCommandRunner {
        void run(String command);
    }
}
