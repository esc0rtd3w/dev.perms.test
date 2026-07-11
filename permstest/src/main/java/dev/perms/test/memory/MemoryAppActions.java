package dev.perms.test.memory;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.widget.Toast;

/**
 * Activity-side Memory app/session actions.
 *
 * MainActivity owns the widgets, target selection source, background executor, shell backend,
 * and output area. This helper keeps the target validation, launch/stop/clear commands,
 * and stable Memory log text together.
 */
public final class MemoryAppActions {
    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    public interface BackgroundExecutor {
        void execute(Runnable task);
    }

    public interface ShellCommandRunner {
        CommandResult run(String command);
    }

    public interface OutputAppender {
        void append(String text);
    }

    public static final class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private final Activity activity;
    private final TargetPackageProvider targetPackageProvider;
    private final BackgroundExecutor backgroundExecutor;
    private final ShellCommandRunner shellCommandRunner;
    private final OutputAppender outputAppender;

    public MemoryAppActions(Activity activity,
                            TargetPackageProvider targetPackageProvider,
                            BackgroundExecutor backgroundExecutor,
                            ShellCommandRunner shellCommandRunner,
                            OutputAppender outputAppender) {
        this.activity = activity;
        this.targetPackageProvider = targetPackageProvider;
        this.backgroundExecutor = backgroundExecutor;
        this.shellCommandRunner = shellCommandRunner;
        this.outputAppender = outputAppender;
    }

    public void launchTargetPackage() {
        try {
            final String pkg = getTargetPackage();
            if (TextUtils.isEmpty(pkg)) {
                Toast.makeText(activity, "Select a target package first.", Toast.LENGTH_SHORT).show();
                return;
            }
            final PackageManager packageManager = activity.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(pkg);
            if (intent == null) {
                Toast.makeText(activity, "No launchable activity for target package.", Toast.LENGTH_SHORT).show();
                appendOutput("[Memory] No launchable activity for: " + pkg + "\n");
                return;
            }
            activity.startActivity(intent);
            appendOutput("[Memory] Launched: " + pkg + "\n");
        } catch (Throwable t) {
            appendOutput("[Memory] Launch failed: " + t + "\n");
        }
    }

    public void stopTargetPackage() {
        try {
            final String pkg = getTargetPackage();
            if (TextUtils.isEmpty(pkg)) {
                Toast.makeText(activity, "Select a target package first.", Toast.LENGTH_SHORT).show();
                return;
            }
            backgroundExecutor.execute(() -> {
                final CommandResult result = runShellCommand("am force-stop " + MemoryToolRuntime.shQuote(pkg));
                activity.runOnUiThread(() -> {
                    appendOutput("[Memory] Stopped: " + pkg + "\n");
                    appendCommandOutput(result);
                });
            });
        } catch (Throwable t) {
            appendOutput("[Memory] Stop failed: " + t + "\n");
        }
    }

    public void clearSession() {
        try {
            final String pkg = getTargetPackage();
            if (TextUtils.isEmpty(pkg)) {
                Toast.makeText(activity, "Enter/select a target package first.", Toast.LENGTH_SHORT).show();
                return;
            }
            backgroundExecutor.execute(() -> {
                final String shellCommand = MemoryToolHelper.buildClearStateCommand(pkg);
                final CommandResult result = runShellCommand(shellCommand);
                activity.runOnUiThread(() -> {
                    appendOutput("[Memory] Cleared session state for " + pkg + "\n");
                    appendCommandOutput(result);
                    Toast.makeText(activity,
                            result != null && result.exitCode == 0 ? "Memory session cleared" : "Memory session clear attempted",
                            Toast.LENGTH_SHORT).show();
                });
            });
        } catch (Throwable t) {
            appendOutput("[Memory] Failed to clear session: " + t + "\n");
        }
    }

    private String getTargetPackage() {
        return targetPackageProvider == null ? null : targetPackageProvider.getTargetPackage();
    }

    private CommandResult runShellCommand(String command) {
        return shellCommandRunner == null ? null : shellCommandRunner.run(command);
    }

    private void appendCommandOutput(CommandResult result) {
        if (result == null) return;
        if (!TextUtils.isEmpty(result.stdout)) appendOutput(result.stdout.endsWith("\n") ? result.stdout : (result.stdout + "\n"));
        if (!TextUtils.isEmpty(result.stderr)) appendOutput(result.stderr.endsWith("\n") ? result.stderr : (result.stderr + "\n"));
    }

    private void appendOutput(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }
}
