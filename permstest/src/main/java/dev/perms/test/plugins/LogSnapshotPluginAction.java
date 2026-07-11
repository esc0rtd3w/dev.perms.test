package dev.perms.test.plugins;

import android.app.Activity;
import android.text.TextUtils;

import dev.perms.test.ui.dialog.GenericViewerDialog;

/** Native bundled plugin action that summarizes PermsTest log folders. */
final class LogSnapshotPluginAction {
    static final String HANDLER = "log_snapshot_dialog";

    private LogSnapshotPluginAction() {
    }

    static void run(PluginActionRegistry.Host host, PluginManifest plugin, PluginAction action) {
        if (host == null) return;
        Activity activity = host.getActivity();
        if (activity == null) return;

        host.appendOutput("[plugins] Running " + plugin.name + " / " + action.title + "\n");
        host.runShellCommandCapture(buildCommand(), (exitCode, stdout, stderr) -> {
            StringBuilder text = new StringBuilder();
            text.append("# PermsTest plugin: ").append(plugin.name).append('\n');
            text.append("# Action: ").append(action.title).append('\n');
            text.append("# Exit: ").append(exitCode).append("\n\n");
            if (!TextUtils.isEmpty(stdout)) text.append(stdout);
            if (!TextUtils.isEmpty(stderr)) {
                if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') text.append('\n');
                text.append("\n# stderr\n").append(stderr);
            }
            final String body = normalize(text.toString());
            activity.runOnUiThread(() -> {
                String title = plugin.name + " - Logs";
                String subtitle = TextUtils.isEmpty(plugin.description) ? plugin.id : plugin.description;
                String shown = TextUtils.isEmpty(body) ? "No log information returned." : body;
                if (action.isLogOnlyPresentation()) {
                    host.appendOutput(shown.endsWith("\n") ? shown : shown + "\n");
                    return;
                }
                boolean largeOverride = host.shouldRunPluginInPanel(plugin.id);
                boolean requestWindow = action.isWindowPresentation() || (!action.isDialogPresentation() && largeOverride);
                String windowStyle = largeOverride ? "full" : firstNonEmpty(action.windowStyle, plugin.windowStyle, "full");
                String windowFit = largeOverride ? "current" : firstNonEmpty(action.windowFit, plugin.windowFit, "current");
                String syntax = firstNonEmpty(action.syntax, "shell");
                if (requestWindow
                        && host.showPluginTextPanel(plugin.id + "." + action.id, title, subtitle, shown, syntax, windowStyle, windowFit)) {
                    host.appendOutput("[plugins] Opened " + plugin.id + " in a managed plugin text window.\n");
                    return;
                }
                GenericViewerDialog.showHighlightedText(activity, title, subtitle, shown, syntax);
            });
        });
    }

    private static String buildCommand() {
        return "ROOT=/sdcard/dev.perms.test; "
                + "echo '# PermsTest Log Snapshot'; "
                + "echo root:$ROOT; "
                + "echo time:$(date 2>/dev/null); "
                + "for d in \"$ROOT/logs\" \"$ROOT/log_archives\" \"$ROOT/permstest_logs\"; do "
                + "echo; echo \"[$d]\"; "
                + "if [ -d \"$d\" ]; then "
                + "du -sh \"$d\" 2>/dev/null | sed 's/^/size:/'; "
                + "ls -la \"$d\" 2>/dev/null | head -n 80; "
                + "echo; echo 'files:'; "
                + "find \"$d\" -maxdepth 2 -type f 2>/dev/null | sort | head -n 200; "
                + "else echo 'missing'; fi; "
                + "done";
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replace("\r\n", "\n").replace('\r', '\n');
    }
}
