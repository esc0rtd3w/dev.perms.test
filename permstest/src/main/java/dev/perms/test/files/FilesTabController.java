package dev.perms.test.files;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.ui.TextPromptDialog;

import android.content.SharedPreferences;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;

/**
 * Owns MainActivity's Files-tab wiring so the activity only coordinates high-level lifecycle calls.
 */
public final class FilesTabController {

    public interface Host {
        int dp(int value);
        void appendOutput(String text);
        void toast(String message);
        String shellQuote(String value);
        void runShellCommandCapture(String command, FilesBrowserController.ShellCallback callback);
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final ExecutorService io;
    private final Handler mainHandler;
    private final SharedPreferences prefs;
    private final Host host;

    private FilesBrowserController browserController;

    public FilesTabController(AppCompatActivity activity,
                              ActivityMainBinding binding,
                              ExecutorService io,
                              Handler mainHandler,
                              SharedPreferences prefs,
                              Host host) {
        this.activity = activity;
        this.binding = binding;
        this.io = io;
        this.mainHandler = mainHandler;
        this.prefs = prefs;
        this.host = host;
    }

    public void bind(String restoreLeftCwd,
                     String restoreRightCwd,
                     Boolean restoreSplit,
                     Boolean restoreActiveRight) {
        if (binding == null || binding.tabFiles == null) return;
        browserController = new FilesBrowserController(
                activity,
                binding,
                io,
                mainHandler,
                prefs,
                new FilesBrowserController.Host() {
                    @Override
                    public int dp(int value) {
                        return host.dp(value);
                    }

                    @Override
                    public void appendOutput(String text) {
                        host.appendOutput(text);
                    }

                    @Override
                    public void toast(String message) {
                        host.toast(message);
                    }

                    @Override
                    public String shellQuote(String value) {
                        return host.shellQuote(value);
                    }

                    @Override
                    public boolean canUseShizuku() {
                        return FilesTabController.this.canUseShizuku();
                    }

                    @Override
                    public void runShizukuCommandCapture(String command, FilesBrowserController.ShellCallback callback) {
                        FilesTabController.this.runShizukuCommandCapture(command, callback);
                    }

                    @Override
                    public void runShellCommandCapture(String command, FilesBrowserController.ShellCallback callback) {
                        host.runShellCommandCapture(command, callback);
                    }

                    @Override
                    public void showTextPromptDialog(String title,
                                                     String hint,
                                                     String preset,
                                                     FilesBrowserController.PromptCallback callback) {
                        FilesTabController.this.showTextPromptDialog(title, hint, preset, callback);
                    }
                },
                SettingsPreferenceKeys.FILES_USE_SHIZUKU,
                SettingsPreferenceKeys.FILES_OPEN_KNOWN_ON_TAP,
                SettingsPreferenceKeys.FILES_INTERNAL_APK_INSTALL,
                SettingsPreferenceKeys.FILES_LAST_LEFT_CWD,
                SettingsPreferenceKeys.FILES_LAST_RIGHT_CWD,
                SettingsPreferenceKeys.FILES_LAST_SPLIT,
                SettingsPreferenceKeys.FILES_LAST_ACTIVE_RIGHT);
        browserController.bind(restoreLeftCwd, restoreRightCwd, restoreSplit, restoreActiveRight);
    }

    public FilesBrowserController getBrowserController() {
        return browserController;
    }

    public void invalidatePackageIconCaches() {
        if (browserController != null) browserController.invalidatePackageIconCaches();
    }

    public boolean canUseShizuku() {
        return FilesShizukuCommandRunner.canUseShizuku();
    }

    public void runShizukuCommandCapture(String command, FilesBrowserController.ShellCallback callback) {
        FilesShizukuCommandRunner.run(activity, io, command, (exit, out, err) -> {
            if (callback != null) callback.onComplete(exit, out, err);
        });
    }

    private void showTextPromptDialog(String title,
                                      String hint,
                                      String preset,
                                      FilesBrowserController.PromptCallback callback) {
        TextPromptDialog.show(
                activity,
                title,
                hint,
                preset,
                value -> { if (callback != null) callback.onText(value); },
                t -> host.appendOutput("[Files] dialog failed: " + t + "\n"));
    }
}
