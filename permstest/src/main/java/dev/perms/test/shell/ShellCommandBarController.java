package dev.perms.test.shell;

import android.text.TextUtils;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Owns the small Shell/output command bar wiring from MainActivity.
 *
 * This controller only binds existing buttons to existing host actions. Shell
 * execution, output storage, and interactive-process state remain in their
 * existing controllers so this refactor does not change runtime behavior.
 */
public final class ShellCommandBarController {
    public interface Host {
        void runCommand(String command);
        void stopInteractiveCommand();
        void copyOutput();
        void clearOutput();
        void resetOutputPanelHeight();
        void setInteractiveRunning(boolean running);
    }

    private final ActivityMainBinding binding;
    private final Host host;

    public ShellCommandBarController(ActivityMainBinding binding, Host host) {
        this.binding = binding;
        this.host = host;
    }

    public void bind() {
        if (binding == null || host == null) return;

        try {
            binding.tabShell.btnRunCmd.setOnClickListener(v -> host.runCommand(currentCommandText()));
        } catch (Throwable ignored) {
        }

        try {
            binding.tabShell.btnStopShell.setOnClickListener(v -> host.stopInteractiveCommand());
        } catch (Throwable ignored) {
        }

        try {
            binding.tabShell.btnCopy.setOnClickListener(v -> host.copyOutput());
        } catch (Throwable ignored) {
        }

        try {
            binding.btnClearOutputGlobal.setOnClickListener(v -> host.clearOutput());
        } catch (Throwable ignored) {
        }

        try {
            binding.btnResetOutputGlobal.setOnClickListener(v -> host.resetOutputPanelHeight());
        } catch (Throwable ignored) {
        }

        try {
            host.setInteractiveRunning(false);
        } catch (Throwable ignored) {
        }
    }

    private String currentCommandText() {
        try {
            CharSequence text = binding.tabShell.edtCmd.getText();
            return TextUtils.isEmpty(text) ? "" : text.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
