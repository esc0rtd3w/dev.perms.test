package dev.perms.test.shell;

import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Owns Shell custom-command UI wiring so MainActivity only provides app-level callbacks.
 */
public final class ShellCustomCommandsController {
    public interface Host {
        String getTargetPackage();
        boolean isSafeToken(String token);
        void runShellCommand(String command);
        void toast(String message);
        void appendOutput(String text);
        int dp(int value);
    }

    private final CustomCommandController controller;

    public ShellCustomCommandsController(AppCompatActivity activity, String prefsName, String prefsKey, Host host) {
        if (host == null) throw new IllegalArgumentException("host == null");
        controller = new CustomCommandController(activity, prefsName, prefsKey, new CustomCommandController.Host() {
            @Override
            public String getTargetPackage() {
                return host.getTargetPackage();
            }

            @Override
            public boolean isSafeToken(String token) {
                return host.isSafeToken(token);
            }

            @Override
            public void runShellCommand(String command) {
                host.runShellCommand(command);
            }

            @Override
            public void toast(String message) {
                host.toast(message);
            }

            @Override
            public void appendOutput(String text) {
                host.appendOutput(text);
            }

            @Override
            public int dp(int value) {
                return host.dp(value);
            }
        });
    }

    public void registerActivityResults() {
        controller.registerActivityResults();
    }

    public void bind(ActivityMainBinding binding) {
        try {
            controller.bind(binding);
        } catch (Throwable ignored) {
        }
    }

    public void runAndFill(String command) {
        controller.runAndFill(command);
    }
}
