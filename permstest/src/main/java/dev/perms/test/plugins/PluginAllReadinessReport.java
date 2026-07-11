package dev.perms.test.plugins;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.List;
import java.util.Set;

/** Builds a read-only readiness report for every currently staged plugin. */
public final class PluginAllReadinessReport {
    private PluginAllReadinessReport() {
    }

    public static String build(SharedPreferences prefs, List<PluginManifest> plugins, Set<String> disabledPluginIds) {
        StringBuilder sb = new StringBuilder();
        final char nl = (char) 10;
        int total = plugins == null ? 0 : plugins.size();
        int disabled = 0;
        int packageBlocked = 0;
        int declarative = 0;
        int script = 0;
        int trustedDex = 0;

        if (plugins != null) {
            for (PluginManifest plugin : plugins) {
                if (plugin == null) continue;
                boolean isDisabled = isDisabled(disabledPluginIds, plugin);
                if (isDisabled) disabled++;
                if (!TextUtils.isEmpty(PluginPackageValidator.requiredFileProblem(plugin))) packageBlocked++;
                if (PluginDeclarativeReadinessReport.pluginHasDeclarativeUi(plugin)) declarative++;
                if (PluginRuntimePolicy.pluginHasScriptActions(plugin)) script++;
                if (PluginRuntimePolicy.pluginHasTrustedDexActions(plugin)) trustedDex++;
            }
        }

        sb.append("All Plugin Readiness").append(nl);
        sb.append("Staged plugins: ").append(total).append(nl);
        sb.append("Enabled plugins: ").append(Math.max(0, total - disabled)).append(nl);
        sb.append("Disabled plugins: ").append(disabled).append(nl);
        sb.append("Package/file blocked: ").append(packageBlocked).append(nl);
        sb.append("Declarative UI plugins: ").append(declarative).append(nl);
        sb.append("Controlled shell/script plugins: ").append(script).append(nl);
        sb.append("Trusted-Dex plugins: ").append(trustedDex).append(nl);
        sb.append("Controlled shell/script runtime gate: ")
                .append(PluginRuntimePolicy.isScriptRuntimeEnabled(prefs) ? "enabled" : "disabled").append(nl);
        sb.append("Script approval required: ")
                .append(PluginRuntimePolicy.isScriptApprovalRequired(prefs) ? "yes" : "no").append(nl);
        sb.append("Per-run script confirmation: ")
                .append(PluginRuntimePolicy.isScriptRunConfirmationRequired(prefs) ? "yes" : "no").append(nl);
        sb.append("Trusted-Dex runtime gate: ")
                .append(PluginRuntimePolicy.isTrustedDexRuntimeEnabled(prefs) ? "enabled" : "disabled").append(nl);
        sb.append("Per-run trusted-code confirmation: ")
                .append(PluginRuntimePolicy.isTrustedDexRunConfirmationRequired(prefs) ? "yes" : "no").append(nl);

        sb.append(nl).append("Safety notes:").append(nl);
        sb.append("• This report reloads staged plugin manifests and reads declared plugin files only.").append(nl);
        sb.append("• It does not open plugin UI, run actions, execute shell commands, approve scripts, trust code payloads, load DEX/JAR/APK payloads, launch plugin APK components, or schedule startup/background work.").append(nl);
        sb.append("• Action dispatch still performs the normal per-action runtime checks when the user explicitly taps an action.").append(nl);

        if (total == 0) {
            sb.append(nl).append("No staged plugins found.").append(nl);
            return sb.toString();
        }

        if (plugins != null) {
            int index = 1;
            for (PluginManifest plugin : plugins) {
                sb.append(nl).append("================ Plugin ").append(index++).append(" of ").append(total)
                        .append(" ================").append(nl);
                if (plugin == null) {
                    sb.append("Plugin manifest is unavailable.").append(nl);
                    continue;
                }
                try {
                    sb.append(PluginCompleteReadinessReport.build(prefs, plugin, isDisabled(disabledPluginIds, plugin))).append(nl);
                } catch (Throwable t) {
                    sb.append("Complete readiness failed for ").append(safe(plugin.id)).append(": ")
                            .append(safeMessage(t)).append(nl);
                }
            }
        }
        return sb.toString();
    }

    private static boolean isDisabled(Set<String> disabledPluginIds, PluginManifest plugin) {
        return plugin != null && disabledPluginIds != null && disabledPluginIds.contains(plugin.id);
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "—" : value;
    }

    private static String safeMessage(Throwable t) {
        String message = t == null ? null : t.getMessage();
        if (!TextUtils.isEmpty(message)) return message;
        return t == null ? "unknown error" : t.getClass().getSimpleName();
    }
}
