package dev.perms.test.shell;

import android.app.Activity;

import dev.perms.test.databinding.ActivityMainBinding;

public final class ShellQuickActionsController {
    public interface Host {
        void runShellCommand(String command);
        void runAndFillCommand(String command);
        void toast(String message);
        String getSelectedPackageName();
        String getSelfPackageName();
        void showManageBundledBinsDialog();
    }

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final Host host;

    public ShellQuickActionsController(Activity activity, ActivityMainBinding binding, Host host) {
        this.activity = activity;
        this.binding = binding;
        this.host = host;
    }

    public void bind() {
        if (activity == null || binding == null || host == null) return;
        new ShellQuickActions(activity, binding, new ShellQuickActions.Callbacks() {
            @Override
            public void runShellCommand(String command) {
                host.runShellCommand(command);
            }

            @Override
            public void runAndFillCommand(String command) {
                host.runAndFillCommand(command);
            }

            @Override
            public void showToast(String message) {
                host.toast(message);
            }

            @Override
            public String getSelectedPackageName() {
                return host.getSelectedPackageName();
            }

            @Override
            public String getSelfPackageName() {
                return host.getSelfPackageName();
            }

            @Override
            public void showManageBundledBinsDialog() {
                host.showManageBundledBinsDialog();
            }
        }).bind();
    }
}
