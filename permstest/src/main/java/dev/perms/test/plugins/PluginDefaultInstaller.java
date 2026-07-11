package dev.perms.test.plugins;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** First-run installer for the small set of bundled plugins that should be immediately available. */
final class PluginDefaultInstaller {
    static final String PREF_DEFAULT_TOOL_PLUGINS_INSTALLED = "plugins_default_tool_plugins_installed_v1";

    private static final List<String> DEFAULT_TOOL_PLUGIN_ASSETS = Arrays.asList(
            "calculator_plugin.ptp",
            "ascii_hex_converter_plugin.ptp",
            "alarms_timers_plugin.ptp"
    );

    static final class Result {
        final boolean attempted;
        final boolean skippedExisting;
        final List<PluginManifest> installed;

        Result(boolean attempted, boolean skippedExisting, List<PluginManifest> installed) {
            this.attempted = attempted;
            this.skippedExisting = skippedExisting;
            this.installed = installed == null ? new ArrayList<>() : installed;
        }
    }

    private PluginDefaultInstaller() {
    }

    static Result ensureFirstRunToolPlugins(SharedPreferences prefs, PluginRepository repository) throws Exception {
        if (repository == null) return new Result(false, false, new ArrayList<>());
        repository.ensurePublicHomeDirectory();
        if (prefs != null && prefs.getBoolean(PREF_DEFAULT_TOOL_PLUGINS_INSTALLED, false)) {
            return new Result(false, false, new ArrayList<>());
        }
        if (repository.hasStagedPluginDirectories()) {
            markComplete(prefs, null);
            return new Result(false, true, new ArrayList<>());
        }
        List<PluginManifest> installed = repository.installBundledPlugins(DEFAULT_TOOL_PLUGIN_ASSETS);
        markComplete(prefs, installed);
        return new Result(true, false, installed);
    }

    private static void markComplete(SharedPreferences prefs, List<PluginManifest> installed) {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(PREF_DEFAULT_TOOL_PLUGINS_INSTALLED, true);
        if (installed != null) {
            for (PluginManifest manifest : installed) {
                if (manifest == null || TextUtils.isEmpty(manifest.id)) continue;
                editor.remove("plugin_disabled_" + manifest.id);
            }
        }
        editor.apply();
    }
}
