package dev.perms.test.plugins;

import android.content.SharedPreferences;
import android.text.TextUtils;

/** Builds a single read-only readiness report that combines package, runtime, script, declarative UI, and trusted-code checks. */
public final class PluginCompleteReadinessReport {
    private PluginCompleteReadinessReport() {
    }

    public static String build(SharedPreferences prefs, PluginManifest plugin, boolean disabled) {
        StringBuilder sb = new StringBuilder();
        final char nl = (char) 10;
        if (plugin == null) return "Plugin manifest is unavailable.";

        sb.append("Complete Plugin Readiness").append(nl);
        sb.append(safe(plugin.name)).append(nl);
        sb.append("ID: ").append(safe(plugin.id)).append(nl);
        sb.append("Version: ").append(safe(plugin.version)).append(nl);
        sb.append("Runtime: ").append(safe(plugin.runtime)).append(nl);
        sb.append("Disabled: ").append(disabled ? "yes" : "no").append(nl);
        if (plugin.homeDir != null) sb.append("Folder: ").append(plugin.homeDir.getAbsolutePath()).append(nl);
        sb.append("Capabilities: ").append(PluginRuntimePolicy.capabilitySummary(plugin)).append(nl);

        String packageProblem = PluginPackageValidator.requiredFileProblem(plugin);
        sb.append(nl).append("Summary:").append(nl);
        appendSummaryLine(sb, "Package files", TextUtils.isEmpty(packageProblem) ? "ready" : "blocked: " + packageProblem, nl);
        appendSummaryLine(sb, "Runtime policy", runtimeSummary(prefs, plugin, disabled), nl);
        if (PluginRuntimePolicy.pluginHasScriptActions(plugin)) {
            appendSummaryLine(sb, "Script actions", scriptSummary(prefs, plugin, disabled), nl);
        } else {
            appendSummaryLine(sb, "Script actions", "none declared", nl);
        }
        if (PluginDeclarativeReadinessReport.pluginHasDeclarativeUi(plugin)) {
            appendSummaryLine(sb, "Declarative UI", TextUtils.isEmpty(packageProblem) ? "see declarative section" : "blocked until package files are fixed", nl);
        } else {
            appendSummaryLine(sb, "Declarative UI", "none declared", nl);
        }
        if (PluginRuntimePolicy.pluginHasTrustedDexActions(plugin)) {
            appendSummaryLine(sb, "Trusted code", trustedSummary(prefs, plugin, disabled), nl);
        } else {
            appendSummaryLine(sb, "Trusted code", "none declared", nl);
        }

        sb.append(nl).append("Safety notes:").append(nl);
        sb.append("• This combined check reads staged plugin files and policy state only.").append(nl);
        sb.append("• It does not open plugin UI, run plugin actions, execute shell commands, approve scripts, trust code payloads, load DEX/JAR/APK payloads, launch plugin APK components, or schedule startup/background work.").append(nl);
        sb.append("• Actual dispatch still rechecks plugin disabled state, package files, declared capabilities, runtime gates, approvals, payload hashes, and explicit user-tapped action metadata.").append(nl);

        sb.append(nl).append("================ Package Readiness ================").append(nl);
        sb.append(PluginPackageValidator.buildReadinessReport(plugin)).append(nl);

        sb.append(nl).append("================ Runtime Policy Review ================").append(nl);
        sb.append(PluginRuntimeReviewReport.build(prefs, plugin, disabled, plugin.homeDir)).append(nl);

        if (PluginDeclarativeReadinessReport.pluginHasDeclarativeUi(plugin)) {
            sb.append(nl).append("================ Declarative UI Readiness ================").append(nl);
            sb.append(PluginDeclarativeReadinessReport.build(plugin, disabled)).append(nl);
        }

        if (PluginRuntimePolicy.pluginHasScriptActions(plugin)) {
            sb.append(nl).append("================ Script Action Readiness ================").append(nl);
            sb.append(PluginRuntimeReviewReport.buildScriptReadiness(prefs, plugin, disabled, plugin.homeDir)).append(nl);
        }

        if (PluginRuntimePolicy.pluginHasTrustedDexActions(plugin)) {
            sb.append(nl).append("================ Trusted Code Readiness ================").append(nl);
            sb.append(PluginRuntimeReviewReport.buildTrustedCodeReadiness(prefs, plugin, disabled, plugin.homeDir)).append(nl);
        }

        sb.append(nl).append("Result: ").append(overallResult(prefs, plugin, disabled, packageProblem)).append(nl);
        return sb.toString();
    }

    private static String runtimeSummary(SharedPreferences prefs, PluginManifest plugin, boolean disabled) {
        if (disabled) return "blocked because plugin is disabled";
        if (plugin == null || plugin.actions == null || plugin.actions.isEmpty()) return "no actions declared";
        int blocked = 0;
        int total = 0;
        for (PluginAction action : plugin.actions) {
            if (action == null) continue;
            total++;
            String problem = PluginRuntimePolicy.capabilityDispatchProblem(plugin, action);
            if (TextUtils.isEmpty(problem) && PluginRuntimePolicy.actionUsesControlledShell(action)) {
                problem = PluginRuntimePolicy.scriptApprovalProblem(prefs, plugin, action);
            }
            if (TextUtils.isEmpty(problem) && action.isTrustedDexAction()) {
                problem = PluginRuntimePolicy.trustedDexApprovalProblem(prefs, plugin, action);
            }
            if (!TextUtils.isEmpty(problem)) blocked++;
        }
        if (total == 0) return "no actions declared";
        if (blocked == 0) return "ready for explicit user-tapped dispatch checks";
        return blocked + " of " + total + " action(s) blocked by policy";
    }

    private static String scriptSummary(SharedPreferences prefs, PluginManifest plugin, boolean disabled) {
        if (disabled) return "blocked because plugin is disabled";
        if (!PluginRuntimePolicy.isScriptRuntimeEnabled(prefs)) return "blocked because controlled shell/script runtime gate is off";
        if (PluginRuntimePolicy.isScriptApprovalRequired(prefs) && !PluginRuntimePolicy.isScriptPluginApproved(prefs, plugin)) {
            return "approval required for this exact script/UI fingerprint";
        }
        return "ready for explicit user-tapped script dispatch checks";
    }

    private static String trustedSummary(SharedPreferences prefs, PluginManifest plugin, boolean disabled) {
        if (disabled) return "blocked because plugin is disabled";
        if (!PluginRuntimePolicy.isTrustedDexRuntimeCompiledEnabled()) return "blocked because trusted-code loader is not compiled/enabled";
        if (!PluginRuntimePolicy.isTrustedDexRuntimeEnabled(prefs)) return "blocked because trusted-code runtime gate is off";
        if (!PluginRuntimePolicy.isTrustedDexPluginApproved(prefs, plugin)) return "exact payload trust is not recorded";
        return "ready for explicit user-tapped trusted-code dispatch checks";
    }

    private static String overallResult(SharedPreferences prefs, PluginManifest plugin, boolean disabled, String packageProblem) {
        if (!TextUtils.isEmpty(packageProblem)) return "blocked until package/file validation passes.";
        if (disabled) return "not dispatch-ready because the plugin is disabled; package checks still passed.";
        if (PluginRuntimePolicy.pluginHasScriptActions(plugin)) {
            if (!PluginRuntimePolicy.isScriptRuntimeEnabled(prefs)) {
                return "package-ready, but script actions are blocked by the controlled shell/script runtime gate.";
            }
            if (PluginRuntimePolicy.isScriptApprovalRequired(prefs) && !PluginRuntimePolicy.isScriptPluginApproved(prefs, plugin)) {
                return "package-ready, but script actions require approval for this exact script/UI fingerprint.";
            }
        }
        if (PluginRuntimePolicy.pluginHasTrustedDexActions(plugin)) {
            if (!PluginRuntimePolicy.isTrustedDexRuntimeEnabled(prefs)) {
                return "package-ready, but trusted-code actions are blocked by the trusted-code runtime gate.";
            }
            if (!PluginRuntimePolicy.isTrustedDexPluginApproved(prefs, plugin)) {
                return "package-ready, but trusted-code actions require exact-payload trust.";
            }
        }
        return "ready for explicit user-tapped action dispatch; runtime still rechecks every gate when an action is tapped.";
    }

    private static void appendSummaryLine(StringBuilder sb, String label, String value, char nl) {
        sb.append("• ").append(label).append(": ").append(TextUtils.isEmpty(value) ? "—" : value).append(nl);
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "—" : value;
    }
}
