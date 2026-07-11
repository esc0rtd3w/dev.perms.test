package dev.perms.test.plugins;

import android.app.Activity;
import android.text.TextUtils;

import dev.perms.test.ui.dialog.GenericViewerDialog;

/** Native bundled plugin action that displays a compact device and build report. */
final class DeviceInfoPluginAction {
    static final String HANDLER = "device_info_dialog";

    private DeviceInfoPluginAction() {
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
                String title = plugin.name + " - Device Info";
                String subtitle = TextUtils.isEmpty(plugin.description) ? plugin.id : plugin.description;
                String shown = TextUtils.isEmpty(body) ? "No device information returned." : body;
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
        return "echo '# PermsTest Device Info'; "
                + "echo; echo '[identity]'; "
                + "echo user:$(id 2>/dev/null); "
                + "echo kernel:$(uname -a 2>/dev/null); "
                + "echo abi:$(getprop ro.product.cpu.abi); "
                + "echo abi_list:$(getprop ro.product.cpu.abilist); "
                + "echo; echo '[android]'; "
                + "for k in ro.build.version.release ro.build.version.sdk ro.build.version.security_patch ro.build.fingerprint ro.build.type ro.build.tags ro.product.brand ro.product.manufacturer ro.product.model ro.product.device ro.product.name ro.hardware ro.boot.hardware; do "
                + "printf '%s:%s\\n' \"$k\" \"$(getprop $k 2>/dev/null)\"; "
                + "done; "
                + "echo; echo '[display]'; "
                + "wm size 2>/dev/null | sed 's/^/wm_size:/'; "
                + "wm density 2>/dev/null | sed 's/^/wm_density:/'; "
                + "echo; echo '[storage]'; "
                + "df -h /sdcard 2>/dev/null | sed 's/^/sdcard:/'; "
                + "df -h /data 2>/dev/null | sed 's/^/data:/';";
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
