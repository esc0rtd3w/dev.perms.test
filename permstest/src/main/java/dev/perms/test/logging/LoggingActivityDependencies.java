package dev.perms.test.logging;

import android.app.Activity;
import android.content.SharedPreferences;

import java.io.File;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * External services supplied by MainActivity for Activity-side Logging controllers.
 *
 * The Logging package owns feature wiring while MainActivity keeps shared app state,
 * backend readiness, shell execution, and lifecycle ownership.
 */
public final class LoggingActivityDependencies {
    private final Activity activity;
    private final BindingProvider bindingProvider;
    private final SharedPreferencesProvider prefsProvider;
    static final String PREF_KEY_LIFETIME_LOG_ENABLED = "lifetime_log_enabled";
    private final BackendReadyCheck backendReadyCheck;
    private final Runnable refreshStatus;
    private final OutputAppender outputAppender;
    private final OutputTagSetter outputTagSetter;
    private final OutputTagProvider outputTagProvider;
    private final LastSavedFileProvider lastSavedFileProvider;
    private final LastSavedFileSetter lastSavedFileSetter;
    private final ShellCommandRunner shellCommandRunner;
    private final CommandCaptureRunner commandCaptureRunner;
    private final ShellSuccessRunner shellSuccessRunner;

    public LoggingActivityDependencies(Activity activity,
                                       BindingProvider bindingProvider,
                                       SharedPreferencesProvider prefsProvider,
                                       BackendReadyCheck backendReadyCheck,
                                       Runnable refreshStatus,
                                       OutputAppender outputAppender,
                                       OutputTagSetter outputTagSetter,
                                       OutputTagProvider outputTagProvider,
                                       LastSavedFileProvider lastSavedFileProvider,
                                       LastSavedFileSetter lastSavedFileSetter,
                                       ShellCommandRunner shellCommandRunner,
                                       CommandCaptureRunner commandCaptureRunner,
                                       ShellSuccessRunner shellSuccessRunner) {
        this.activity = activity;
        this.bindingProvider = bindingProvider;
        this.prefsProvider = prefsProvider;
        this.backendReadyCheck = backendReadyCheck;
        this.refreshStatus = refreshStatus;
        this.outputAppender = outputAppender;
        this.outputTagSetter = outputTagSetter;
        this.outputTagProvider = outputTagProvider;
        this.lastSavedFileProvider = lastSavedFileProvider;
        this.lastSavedFileSetter = lastSavedFileSetter;
        this.shellCommandRunner = shellCommandRunner;
        this.commandCaptureRunner = commandCaptureRunner;
        this.shellSuccessRunner = shellSuccessRunner;
    }

    Activity getActivity() {
        return activity;
    }

    ActivityMainBinding getBinding() {
        return bindingProvider == null ? null : bindingProvider.getBinding();
    }

    SharedPreferences getPreferences() {
        return prefsProvider == null ? null : prefsProvider.getPreferences();
    }

    String getLifetimeLogKey() {
        return PREF_KEY_LIFETIME_LOG_ENABLED;
    }

    boolean getInitialLifetimeLogEnabled() {
        SharedPreferences prefs = getPreferences();
        return prefs == null || prefs.getBoolean(PREF_KEY_LIFETIME_LOG_ENABLED, true);
    }

    boolean isBackendReady() {
        return backendReadyCheck != null && backendReadyCheck.isReady();
    }

    void refreshStatus() {
        if (refreshStatus != null) refreshStatus.run();
    }

    void appendOutput(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    void setOutputTag(String tag) {
        if (outputTagSetter != null) outputTagSetter.setTag(tag);
    }

    String getOutputTag() {
        return outputTagProvider == null ? null : outputTagProvider.getTag();
    }

    File getLastSavedFile() {
        return lastSavedFileProvider == null ? null : lastSavedFileProvider.getFile();
    }

    void setLastSavedFile(File file) {
        if (lastSavedFileSetter != null) lastSavedFileSetter.setFile(file);
    }

    void runShellCommand(String command) {
        if (shellCommandRunner != null) shellCommandRunner.run(command);
    }

    void runCapture(String command, LoggingLogcatActions.CaptureResultCallback callback) {
        if (commandCaptureRunner != null) commandCaptureRunner.run(command, callback);
    }

    boolean runShellSuccess(String command) {
        return shellSuccessRunner != null && shellSuccessRunner.run(command);
    }

    public interface BindingProvider {
        ActivityMainBinding getBinding();
    }

    public interface SharedPreferencesProvider {
        SharedPreferences getPreferences();
    }

    public interface BackendReadyCheck {
        boolean isReady();
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface OutputTagSetter {
        void setTag(String tag);
    }

    public interface OutputTagProvider {
        String getTag();
    }

    public interface LastSavedFileProvider {
        File getFile();
    }

    public interface LastSavedFileSetter {
        void setFile(File file);
    }

    public interface ShellCommandRunner {
        void run(String command);
    }

    public interface CommandCaptureRunner {
        void run(String command, LoggingLogcatActions.CaptureResultCallback callback);
    }

    public interface ShellSuccessRunner {
        boolean run(String command);
    }
}
