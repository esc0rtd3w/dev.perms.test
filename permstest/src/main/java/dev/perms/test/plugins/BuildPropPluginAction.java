package dev.perms.test.plugins;

import android.app.Activity;
import android.text.TextUtils;

import dev.perms.test.ui.dialog.GenericViewerDialog;

/** Native sample plugin action that displays build.prop and getprop diagnostics. */
final class BuildPropPluginAction {
    static final String HANDLER = "build_prop_dialog";

    private BuildPropPluginAction() {
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
            final String body = normalizePropertyOutput(text.toString());
            activity.runOnUiThread(() -> {
                String title = plugin.name + " - build.prop";
                String subtitle = TextUtils.isEmpty(plugin.description) ? plugin.id : plugin.description;
                String shown = TextUtils.isEmpty(body) ? "No build properties returned." : body;
                if (action.isLogOnlyPresentation()) {
                    host.appendOutput(shown.endsWith("\n") ? shown : shown + "\n");
                    return;
                }
                boolean largeOverride = host.shouldRunPluginInPanel(plugin.id);
                boolean requestWindow = action.isWindowPresentation() || (!action.isDialogPresentation() && largeOverride);
                String windowStyle = largeOverride ? "full" : firstNonEmpty(action.windowStyle, plugin.windowStyle, "full");
                String windowFit = largeOverride ? "current" : firstNonEmpty(action.windowFit, plugin.windowFit, "current");
                String syntax = firstNonEmpty(action.syntax, "properties");
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
        return "echo '# PermsTest Build Property Report'; "
                + "for f in /system/build.prop /system_ext/build.prop /product/build.prop /vendor/build.prop /odm/build.prop /default.prop; do "
                + "if [ -f \"$f\" ]; then printf '\\n[%s]\\n' \"$f\"; cat \"$f\"; fi; "
                + "done; printf '\\n[getprop]\\n'; getprop 2>/dev/null | sort | sed 's/^\\[//; s/\\]: \\[/:/; s/\\]$//'";
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private static String normalizePropertyOutput(String raw) {
        if (raw == null) return "";
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replace("] [", "]\n[");
        return text;
    }
}
