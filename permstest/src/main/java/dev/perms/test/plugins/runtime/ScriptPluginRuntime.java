package dev.perms.test.plugins.runtime;

import android.app.Activity;
import android.text.TextUtils;

import java.io.File;

import dev.perms.test.plugins.PluginAction;
import dev.perms.test.plugins.PluginActionRegistry;
import dev.perms.test.plugins.PluginManifest;
import dev.perms.test.ui.dialog.GenericViewerDialog;

/** Controlled shell/script plugin action runtime. */
public final class ScriptPluginRuntime {
    private ScriptPluginRuntime() {
    }

    public static boolean run(PluginActionRegistry.Host host, PluginManifest plugin, PluginAction action) {
        if (host == null || plugin == null || action == null) return false;
        String command = action.command;
        if (TextUtils.isEmpty(command) && !TextUtils.isEmpty(action.script)) {
            File script = new File(plugin.homeDir, action.script);
            try {
                String root = plugin.homeDir.getCanonicalPath() + File.separator;
                String path = script.getCanonicalPath();
                if (!path.startsWith(root) || !script.isFile()) throw new IllegalArgumentException("Invalid script path");
                command = "sh '" + script.getAbsolutePath().replace("'", "'\\''") + "'";
            } catch (Throwable t) {
                host.appendOutput("[plugins] Script path rejected: " + t.getMessage() + "\n");
                return false;
            }
        }
        if (TextUtils.isEmpty(command)) {
            host.appendOutput("[plugins] Script action has no command or script file: " + plugin.id + "/" + action.id + "\n");
            return false;
        }
        final String cmd = command;
        host.appendOutput("[plugins] Running script action " + plugin.id + "/" + action.id + "\n");
        host.runShellCommandCapture(cmd, (exitCode, stdout, stderr) -> {
            StringBuilder out = new StringBuilder();
            out.append("[plugins] script ").append(plugin.id).append("/").append(action.id)
                    .append(" exit=").append(exitCode).append("\n");
            if (!TextUtils.isEmpty(stdout)) out.append(stdout).append("\n");
            if (!TextUtils.isEmpty(stderr)) out.append("[stderr]\n").append(stderr).append("\n");
            host.appendOutput(out.toString());
            boolean largeOverride = host.shouldRunPluginInPanel(plugin.id);
            boolean requestWindow = action.isWindowPresentation() || (!action.isLogOnlyPresentation() && !action.isDialogPresentation() && largeOverride);
            String syntax = firstNonEmpty(action.syntax, "shell");
            if (requestWindow) {
                String windowStyle = largeOverride ? "full" : firstNonEmpty(action.windowStyle, plugin.windowStyle, "full");
                String windowFit = largeOverride ? "current" : firstNonEmpty(action.windowFit, plugin.windowFit, "current");
                host.showPluginTextPanel(plugin.id + "." + action.id + ".script",
                        plugin.name + " / " + action.title,
                        "Shell/script plugin output",
                        out.toString(),
                        syntax,
                        windowStyle,
                        windowFit);
            } else if (action.isDialogPresentation()) {
                Activity activity = host.getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> GenericViewerDialog.showHighlightedText(activity,
                            plugin.name + " / " + action.title,
                            "Shell/script plugin output",
                            out.toString(),
                            syntax));
                }
            }
        });
        return true;
    }
    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }
}
