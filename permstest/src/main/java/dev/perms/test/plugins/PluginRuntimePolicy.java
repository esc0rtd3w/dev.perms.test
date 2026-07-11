package dev.perms.test.plugins;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Central user/runtime policy for plugin action dispatch. */
public final class PluginRuntimePolicy {
    public static final String PREF_ENABLE_SCRIPT_RUNTIME = "plugins_runtime_enable_script_actions";
    public static final String PREF_ENABLE_TRUSTED_DEX_RUNTIME = "plugins_runtime_enable_trusted_dex";
    public static final String PREF_REQUIRE_SCRIPT_APPROVAL = "plugins_runtime_require_script_approval";
    public static final String PREF_CONFIRM_SCRIPT_EACH_RUN = "plugins_runtime_confirm_script_each_run";
    public static final String PREF_CONFIRM_TRUSTED_DEX_EACH_RUN = "plugins_runtime_confirm_trusted_dex_each_run";
    private static final String PREF_SCRIPT_APPROVED_PREFIX = "plugins_runtime_script_approved_";
    private static final String PREF_TRUSTED_DEX_APPROVED_PREFIX = "plugins_runtime_trusted_dex_approved_";

    public static final String CAP_DECLARATIVE_UI = "declarative_ui";
    public static final String CAP_HOST_API = "host_api";
    public static final String CAP_SHELL_COMMAND = "shell_command";
    public static final String CAP_SHELL_SCRIPT = "shell_script";
    public static final String CAP_TRUSTED_NATIVE = "trusted_native";
    public static final String CAP_TRUSTED_DEX = "trusted_dex";

    private static final boolean DEFAULT_SCRIPT_RUNTIME_ENABLED = true;
    private static final boolean DEFAULT_REQUIRE_SCRIPT_APPROVAL = false;
    private static final boolean DEFAULT_CONFIRM_SCRIPT_EACH_RUN = false;
    private static final boolean DEFAULT_CONFIRM_TRUSTED_DEX_EACH_RUN = false;
    private static final boolean TRUSTED_DEX_RUNTIME_COMPILED_ENABLED = true;

    private PluginRuntimePolicy() {
    }

    public static boolean isScriptRuntimeEnabled(SharedPreferences prefs) {
        return prefs == null || prefs.getBoolean(PREF_ENABLE_SCRIPT_RUNTIME, DEFAULT_SCRIPT_RUNTIME_ENABLED);
    }

    public static void setScriptRuntimeEnabled(SharedPreferences prefs, boolean enabled) {
        if (prefs != null) prefs.edit().putBoolean(PREF_ENABLE_SCRIPT_RUNTIME, enabled).apply();
    }

    public static boolean isScriptApprovalRequired(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_REQUIRE_SCRIPT_APPROVAL, DEFAULT_REQUIRE_SCRIPT_APPROVAL);
    }

    public static void setScriptApprovalRequired(SharedPreferences prefs, boolean enabled) {
        if (prefs != null) prefs.edit().putBoolean(PREF_REQUIRE_SCRIPT_APPROVAL, enabled).apply();
    }

    public static boolean isScriptRunConfirmationRequired(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_CONFIRM_SCRIPT_EACH_RUN, DEFAULT_CONFIRM_SCRIPT_EACH_RUN);
    }

    public static void setScriptRunConfirmationRequired(SharedPreferences prefs, boolean enabled) {
        if (prefs != null) prefs.edit().putBoolean(PREF_CONFIRM_SCRIPT_EACH_RUN, enabled).apply();
    }

    public static boolean isTrustedDexRunConfirmationRequired(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_CONFIRM_TRUSTED_DEX_EACH_RUN, DEFAULT_CONFIRM_TRUSTED_DEX_EACH_RUN);
    }

    public static void setTrustedDexRunConfirmationRequired(SharedPreferences prefs, boolean enabled) {
        if (prefs != null) prefs.edit().putBoolean(PREF_CONFIRM_TRUSTED_DEX_EACH_RUN, enabled).apply();
    }

    public static boolean pluginHasScriptActions(PluginManifest plugin) {
        if (plugin == null || plugin.actions == null) return false;
        for (PluginAction action : plugin.actions) {
            if (actionUsesControlledShell(action)) return true;
        }
        return false;
    }

    public static boolean pluginHasTrustedDexActions(PluginManifest plugin) {
        if (plugin == null || plugin.actions == null) return false;
        for (PluginAction action : plugin.actions) {
            if (action != null && action.isTrustedDexAction()) return true;
        }
        return false;
    }

    public static boolean isTrustedDexPluginApproved(SharedPreferences prefs, PluginManifest plugin) {
        if (prefs == null || !pluginHasTrustedDexActions(plugin)) return false;
        return prefs.getBoolean(trustedDexApprovalKey(plugin), false);
    }

    public static void setTrustedDexPluginApproved(SharedPreferences prefs, PluginManifest plugin, boolean approved) {
        if (prefs == null || plugin == null) return;
        String key = trustedDexApprovalKey(plugin);
        if (approved) {
            prefs.edit().putBoolean(key, true).apply();
        } else {
            prefs.edit().remove(key).apply();
        }
    }

    public static String trustedDexApprovalStatus(SharedPreferences prefs, PluginManifest plugin) {
        if (!pluginHasTrustedDexActions(plugin)) return "not needed";
        return isTrustedDexPluginApproved(prefs, plugin)
                ? "trusted for this exact payload fingerprint"
                : "review/trust required before future loading";
    }

    public static String trustedDexApprovalFingerprint(PluginManifest plugin) {
        if (!pluginHasTrustedDexActions(plugin)) return "none";
        return trustedDexApprovalToken(plugin);
    }

    public static int clearAllScriptApprovals(SharedPreferences prefs) {
        return clearApprovalsWithPrefix(prefs, PREF_SCRIPT_APPROVED_PREFIX);
    }

    public static int clearAllTrustedDexApprovals(SharedPreferences prefs) {
        return clearApprovalsWithPrefix(prefs, PREF_TRUSTED_DEX_APPROVED_PREFIX);
    }

    private static int clearApprovalsWithPrefix(SharedPreferences prefs, String prefix) {
        if (prefs == null || TextUtils.isEmpty(prefix)) return 0;
        int count = 0;
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> all = prefs.getAll();
        if (all != null) {
            for (String key : all.keySet()) {
                if (!TextUtils.isEmpty(key) && key.startsWith(prefix)) {
                    editor.remove(key);
                    count++;
                }
            }
        }
        editor.apply();
        return count;
    }

    public static String trustedDexApprovalProblem(SharedPreferences prefs, PluginManifest plugin, PluginAction action) {
        if (action == null || !action.isTrustedDexAction()) return "";
        if (isTrustedDexPluginApproved(prefs, plugin)) return "";
        return "Trusted-Dex action requires Trusted Code Review approval for exact payload fingerprint "
                + trustedDexApprovalFingerprint(plugin) + " before in-process trusted-code dispatch "
                + safePluginAction(plugin == null ? "" : plugin.id, action.id) + ".";
    }

    public static boolean actionUsesControlledShell(PluginAction action) {
        if (action == null) return false;
        if (action.isScriptAction()) return true;
        return containsCapability(action.requires, CAP_SHELL_COMMAND)
                || containsCapability(action.requires, CAP_SHELL_SCRIPT);
    }

    public static boolean isScriptPluginApproved(SharedPreferences prefs, PluginManifest plugin) {
        if (prefs == null || !pluginHasScriptActions(plugin)) return false;
        return prefs.getBoolean(scriptApprovalKey(plugin), false);
    }

    public static void setScriptPluginApproved(SharedPreferences prefs, PluginManifest plugin, boolean approved) {
        if (prefs == null || plugin == null) return;
        String key = scriptApprovalKey(plugin);
        if (approved) {
            prefs.edit().putBoolean(key, true).apply();
        } else {
            prefs.edit().remove(key).apply();
        }
    }

    public static String scriptApprovalProblem(SharedPreferences prefs, PluginManifest plugin, PluginAction action) {
        if (!actionUsesControlledShell(action)) return "";
        if (!isScriptApprovalRequired(prefs)) return "";
        if (isScriptPluginApproved(prefs, plugin)) return "";
        return "Script action requires Review Runtime Policy approval for "
                + safePluginAction(plugin == null ? "" : plugin.id, action.id)
                + ". Open the plugin card menu, review the runtime policy, then approve script actions for this exact manifest/script/UI fingerprint.";
    }

    public static String scriptApprovalStatus(SharedPreferences prefs, PluginManifest plugin) {
        if (!pluginHasScriptActions(plugin)) return "not needed";
        if (!isScriptApprovalRequired(prefs)) return "not required";
        return isScriptPluginApproved(prefs, plugin)
                ? "approved for this manifest/script/UI fingerprint"
                : "review approval required";
    }

    public static String scriptApprovalFingerprint(PluginManifest plugin) {
        if (!pluginHasScriptActions(plugin)) return "none";
        return scriptApprovalToken(plugin);
    }

    public static boolean isTrustedDexRuntimeCompiledEnabled() {
        return TRUSTED_DEX_RUNTIME_COMPILED_ENABLED;
    }

    public static boolean isTrustedDexRuntimeRequested(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_ENABLE_TRUSTED_DEX_RUNTIME, false);
    }

    public static boolean isTrustedDexRuntimeEnabled(SharedPreferences prefs) {
        return TRUSTED_DEX_RUNTIME_COMPILED_ENABLED
                && isTrustedDexRuntimeRequested(prefs);
    }

    public static void setTrustedDexRuntimeRequested(SharedPreferences prefs, boolean enabled) {
        if (prefs != null) prefs.edit().putBoolean(PREF_ENABLE_TRUSTED_DEX_RUNTIME, enabled).apply();
    }

    public static List<String> inferredCapabilities(PluginManifest plugin) {
        ArrayList<String> result = new ArrayList<>();
        if (plugin != null && plugin.actions != null) {
            for (PluginAction action : plugin.actions) addCapabilities(result, inferredCapabilities(plugin, action));
        }
        return result;
    }

    public static List<String> inferredCapabilities(PluginManifest plugin, PluginAction action) {
        ArrayList<String> result = new ArrayList<>();
        if (action == null) return result;
        if (action.isDeclarativeAction()) {
            addCapability(result, CAP_DECLARATIVE_UI);
            addCapability(result, CAP_HOST_API);
        } else if (action.isScriptAction()) {
            if (!TextUtils.isEmpty(action.command)) addCapability(result, CAP_SHELL_COMMAND);
            if (!TextUtils.isEmpty(action.script)) addCapability(result, CAP_SHELL_SCRIPT);
            if (TextUtils.isEmpty(action.command) && TextUtils.isEmpty(action.script)) addCapability(result, CAP_SHELL_COMMAND);
        } else if (action.isTrustedDexAction()) {
            addCapability(result, CAP_TRUSTED_DEX);
        } else if (action.isNativeAction()) {
            addCapability(result, CAP_TRUSTED_NATIVE);
        }
        if (action.requires != null) addCapabilities(result, action.requires);
        return result;
    }

    public static String capabilityDispatchProblem(PluginManifest plugin, PluginAction action) {
        if (plugin == null || action == null) return "Plugin action is unavailable.";
        if (plugin.capabilities == null || plugin.capabilities.isEmpty()) return "";
        List<String> required = inferredCapabilities(plugin, action);
        for (String capability : required) {
            if (TextUtils.isEmpty(capability)) continue;
            if (!containsCapability(plugin.capabilities, capability)) {
                return "Plugin manifest capabilities do not include required action capability '"
                        + capability + "' for " + safePluginAction(plugin.id, action.id) + ".";
            }
        }
        return "";
    }

    public static String capabilityBlockedMessage(String problem) {
        return "[plugins] Action blocked by Plugin Runtime Policy: " + safeMessage(problem) + "\n";
    }

    public static String scriptRuntimeBlockedMessage(String pluginId, String actionId) {
        return "[plugins] Controlled shell/script plugin actions are disabled by Plugin Runtime Policy: "
                + safePluginAction(pluginId, actionId) + "\n";
    }

    public static String scriptApprovalBlockedMessage(String problem) {
        return "[plugins] Script action blocked by Plugin Runtime Policy: " + safeMessage(problem) + "\n";
    }

    public static String trustedDexBlockedMessage(String pluginId, String actionId) {
        return "[plugins] trusted_dex action blocked by Plugin Runtime Policy. "
                + "The in-process trusted-code loader only runs after the runtime gate is enabled, exact payload trust is recorded, and SHA-256 checks pass: "
                + safePluginAction(pluginId, actionId) + "\n";
    }

    public static String trustedDexApprovalBlockedMessage(String problem) {
        return "[plugins] Trusted-Dex action blocked by Plugin Runtime Policy: " + safeMessage(problem) + "\n";
    }

    public static String statusText(SharedPreferences prefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Controlled shell/script actions: ")
                .append(isScriptRuntimeEnabled(prefs) ? "enabled" : "disabled")
                .append(" for user-tapped actions through the existing shell capture backend. ");
        sb.append("Script review approval: ")
                .append(isScriptApprovalRequired(prefs) ? "required" : "optional")
                .append(" for controlled script plugins. ");
        sb.append("Per-run script confirmation: ")
                .append(isScriptRunConfirmationRequired(prefs) ? "required" : "optional")
                .append(" before user-tapped script action dispatch, including declarative UI shell actions. ");
        sb.append("Trusted-Dex runtime: ")
                .append(isTrustedDexRuntimeEnabled(prefs) ? "enabled" : "blocked")
                .append(isTrustedDexRuntimeRequested(prefs) ? "." : " because the user runtime gate is off.");
        sb.append(" Per-run trusted-code confirmation: ")
                .append(isTrustedDexRunConfirmationRequired(prefs) ? "required" : "optional")
                .append(" before user-tapped trusted-code dispatch. ");
        sb.append("Trusted-Dex loading is limited to explicit user-tapped actions after declared SHA-256, exact-payload trust, capabilities, and class/method metadata pass policy checks. Review tools can inspect declared DEX/JAR/APK payload paths, expected hashes, save exact-payload trust records, run a loader-readiness checklist, and optionally confirm the exact action before dispatch.");
        sb.append(" Capabilities metadata now supports declarative_ui, host_api, shell_command, shell_script, trusted_native, and trusted_dex.");
        sb.append(" Declarative UI shell actions share these shell/script gates, can participate in review approval when their launch action requires shell_command or shell_script, and are blocked inside Plugin Editor Preview UI.");
        sb.append(" No plugin startup execution or hidden background dispatch is enabled here.");
        return sb.toString();
    }

    public static String reviewText(SharedPreferences prefs, PluginManifest plugin, boolean disabled) {
        StringBuilder sb = new StringBuilder();
        if (plugin == null) {
            return "Plugin manifest is unavailable.";
        }
        final char nl = (char) 10;
        sb.append(plugin.name).append(nl);
        sb.append("ID: ").append(plugin.id).append(nl);
        sb.append("Version: ").append(plugin.version).append(nl);
        sb.append("Runtime: ").append(plugin.runtime).append(nl);
        sb.append("Disabled: ").append(disabled ? "yes" : "no").append(nl).append(nl);

        sb.append("Declared capabilities: ").append(joinCapabilities(plugin.capabilities)).append(nl);
        sb.append("Inferred capabilities: ").append(joinCapabilities(inferredCapabilities(plugin))).append(nl);
        if (pluginHasScriptActions(plugin)) {
            sb.append("Script approval: ").append(scriptApprovalStatus(prefs, plugin)).append(nl);
            sb.append("Per-run confirmation: ")
                    .append(isScriptRunConfirmationRequired(prefs) ? "required" : "optional")
                    .append(nl);
        }
        if (pluginHasTrustedDexActions(plugin)) {
            sb.append("Trusted-Dex review: available; explicit loading requires the runtime gate, exact payload trust, and SHA-256 match").append(nl);
            sb.append("Trusted code approval: ").append(trustedDexApprovalStatus(prefs, plugin)).append(nl);
            sb.append("Trusted payload fingerprint: ").append(trustedDexApprovalFingerprint(plugin)).append(nl);
            sb.append("Per-run trusted-code confirmation: ")
                    .append(isTrustedDexRunConfirmationRequired(prefs) ? "required" : "optional")
                    .append(nl);
        }
        sb.append(nl);

        sb.append(statusText(prefs)).append(nl).append(nl);
        sb.append("Action review:").append(nl);
        if (plugin.actions == null || plugin.actions.isEmpty()) {
            sb.append("• No actions declared.").append(nl);
        } else {
            for (PluginAction action : plugin.actions) {
                sb.append("• ").append(safePluginAction(plugin.id, action.id)).append(nl);
                sb.append("  Type: ").append(TextUtils.isEmpty(action.type) ? "native" : action.type).append(nl);
                List<String> required = inferredCapabilities(plugin, action);
                sb.append("  Requires: ").append(joinCapabilities(required)).append(nl);
                String problem = capabilityDispatchProblem(plugin, action);
                if (disabled) {
                    sb.append("  Status: blocked because the plugin is disabled.").append(nl);
                } else if (!TextUtils.isEmpty(problem)) {
                    sb.append("  Status: blocked; ").append(problem).append(nl);
                } else if (action.isTrustedDexAction()) {
                    String trustedProblem = trustedDexApprovalProblem(prefs, plugin, action);
                    if (!TextUtils.isEmpty(trustedProblem)) {
                        sb.append("  Status: blocked; ").append(trustedProblem).append(nl);
                    } else if (!isTrustedDexRuntimeCompiledEnabled()) {
                        sb.append("  Status: blocked; trusted_dex loader is not compiled/enabled.").append(nl);
                    } else if (!isTrustedDexRuntimeEnabled(prefs)) {
                        sb.append("  Status: blocked; trusted_dex runtime gate is off.").append(nl);
                    } else {
                        sb.append("  Status: allowed for explicit user-tapped trusted-code dispatch after runtime payload checks.").append(nl);
                    }
                } else if (action.isScriptAction() && !isScriptRuntimeEnabled(prefs)) {
                    sb.append("  Status: blocked; controlled shell/script actions are disabled.").append(nl);
                } else if (!TextUtils.isEmpty(scriptApprovalProblem(prefs, plugin, action))) {
                    sb.append("  Status: blocked; ").append(scriptApprovalProblem(prefs, plugin, action)).append(nl);
                } else {
                    sb.append("  Status: allowed for explicit user-tapped dispatch.").append(nl);
                }
            }
        }
        sb.append(nl).append("No plugin startup execution, hidden background dispatch, service/receiver/boot dispatch, or plugin APK component launching is enabled by this review. Trusted-Dex dispatch is explicit-tap only and still rechecks payload hash/trust at runtime.");
        return sb.toString();
    }
    public static boolean isSupportedCapability(String value) {
        return CAP_DECLARATIVE_UI.equals(value)
                || CAP_HOST_API.equals(value)
                || CAP_SHELL_COMMAND.equals(value)
                || CAP_SHELL_SCRIPT.equals(value)
                || CAP_TRUSTED_NATIVE.equals(value)
                || CAP_TRUSTED_DEX.equals(value);
    }

    public static String capabilitySummary(PluginManifest plugin) {
        List<String> values = plugin == null ? null : plugin.capabilities;
        if (values == null || values.isEmpty()) values = inferredCapabilities(plugin);
        return joinCapabilities(values);
    }

    private static String scriptApprovalKey(PluginManifest plugin) {
        String id = plugin == null || TextUtils.isEmpty(plugin.id) ? "unknown" : plugin.id;
        return PREF_SCRIPT_APPROVED_PREFIX + id + "_" + scriptApprovalToken(plugin);
    }

    private static String scriptApprovalToken(PluginManifest plugin) {
        StringBuilder sb = new StringBuilder();
        if (plugin != null) {
            sb.append(plugin.id).append('\n')
                    .append(plugin.version).append('\n')
                    .append(plugin.runtime).append('\n')
                    .append(joinCapabilities(plugin.capabilities)).append('\n');
            if (plugin.actions != null) {
                for (PluginAction action : plugin.actions) {
                    if (action == null || !actionUsesControlledShell(action)) continue;
                    String declarativeTarget = action.isDeclarativeAction()
                            ? firstNonEmpty(action.target, action.handler, plugin.entry)
                            : "";
                    sb.append(action.id).append('|')
                            .append(action.type).append('|')
                            .append(action.handler).append('|')
                            .append(action.target).append('|')
                            .append(action.command).append('|')
                            .append(action.script).append('|')
                            .append(joinCapabilities(action.requires)).append('|')
                            .append(TextUtils.isEmpty(action.script)
                                    ? "no-script-file"
                                    : scriptFileHashState(plugin.homeDir, action.script)).append('|')
                            .append(TextUtils.isEmpty(declarativeTarget)
                                    ? "no-controlled-ui-target"
                                    : scriptFileHashState(plugin.homeDir, declarativeTarget))
                            .append('\n');
                }
            }
        }
        return sha256Short(sb.toString());
    }

    private static String scriptFileHashState(File pluginDir, String path) {
        if (TextUtils.isEmpty(path)) return "missing-path";
        if (pluginDir == null || !pluginDir.isDirectory()) return "missing-plugin-dir";
        if (!isSafeRelativePluginPath(path)) return "unsafe-path:" + path;
        try {
            File root = pluginDir.getCanonicalFile();
            File script = new File(pluginDir, path).getCanonicalFile();
            if (!script.getPath().startsWith(root.getPath() + File.separator)) return "outside-plugin-dir:" + path;
            if (!script.isFile()) return "missing-file:" + path;
            return script.length() + ":" + sha256Full(script);
        } catch (Throwable t) {
            return "hash-error:" + safeMessage(t.getMessage());
        }
    }

    private static String trustedDexApprovalKey(PluginManifest plugin) {
        String id = plugin == null || TextUtils.isEmpty(plugin.id) ? "unknown" : plugin.id;
        return PREF_TRUSTED_DEX_APPROVED_PREFIX + id + "_" + trustedDexApprovalToken(plugin);
    }

    private static String trustedDexApprovalToken(PluginManifest plugin) {
        StringBuilder sb = new StringBuilder();
        if (plugin != null) {
            sb.append(plugin.id).append('\n')
                    .append(plugin.version).append('\n')
                    .append(plugin.runtime).append('\n')
                    .append(joinCapabilities(plugin.capabilities)).append('\n');
            if (plugin.actions != null) {
                for (PluginAction action : plugin.actions) {
                    if (action == null || !action.isTrustedDexAction()) continue;
                    String payload = trustedDexPayloadPath(action);
                    sb.append(action.id).append('|')
                            .append(action.type).append('|')
                            .append(payload).append('|')
                            .append(action.handler).append('|')
                            .append(action.target).append('|')
                            .append(joinCapabilities(action.requires)).append('|')
                            .append(rawValue(action, "sha256")).append('|')
                            .append(firstNonEmpty(rawValue(action, "className"), rawValue(action, "entryClass"), rawValue(action, "class"))).append('|')
                            .append(firstNonEmpty(rawValue(action, "methodName"), rawValue(action, "method"))).append('|')
                            .append(trustedDexPayloadHashState(plugin.homeDir, payload))
                            .append('\n');
                }
            }
        }
        return sha256Short(sb.toString());
    }

    private static String trustedDexPayloadPath(PluginAction action) {
        if (action == null) return "";
        return firstNonEmpty(action.target, action.handler, rawValue(action, "dex"), rawValue(action, "path"));
    }

    private static String trustedDexPayloadHashState(File pluginDir, String path) {
        if (TextUtils.isEmpty(path)) return "missing-path";
        if (pluginDir == null || !pluginDir.isDirectory()) return "missing-plugin-dir";
        if (!isSafeRelativePluginPath(path)) return "unsafe-path:" + path;
        try {
            File root = pluginDir.getCanonicalFile();
            File payload = new File(pluginDir, path).getCanonicalFile();
            if (!payload.getPath().startsWith(root.getPath() + File.separator)) return "outside-plugin-dir:" + path;
            if (!payload.isFile()) return "missing-file:" + path;
            return payload.length() + ":" + sha256Full(payload);
        } catch (Throwable t) {
            return "hash-error:" + safeMessage(t.getMessage());
        }
    }

    private static String rawValue(PluginAction action, String key) {
        if (action == null || action.raw == null || TextUtils.isEmpty(key)) return "";
        return action.raw.optString(key, "").trim();
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private static boolean isSafeRelativePluginPath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String p = path.trim().replace('\\', '/');
        if (p.startsWith("/") || p.startsWith("../") || p.contains("/../") || p.equals("..")) return false;
        if (p.contains(":")) return false;
        return true;
    }

    private static String sha256Full(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    private static String sha256Short(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hash.length && i < 8; i++) {
                String hex = Integer.toHexString(hash[i] & 0xFF);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }

    private static String joinCapabilities(List<String> values) {
        if (values == null || values.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(value);
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static void addCapabilities(ArrayList<String> result, List<String> values) {
        if (result == null || values == null) return;
        for (String value : values) addCapability(result, value);
    }

    private static void addCapability(ArrayList<String> result, String value) {
        if (result == null) return;
        String safe = value == null ? "" : value.trim();
        if (!TextUtils.isEmpty(safe) && !result.contains(safe)) result.add(safe);
    }

    private static boolean containsCapability(List<String> values, String capability) {
        if (values == null || TextUtils.isEmpty(capability)) return false;
        for (String value : values) {
            if (capability.equals(value)) return true;
        }
        return false;
    }

    private static String safePluginAction(String pluginId, String actionId) {
        String plugin = TextUtils.isEmpty(pluginId) ? "unknown" : pluginId;
        String action = TextUtils.isEmpty(actionId) ? "unknown" : actionId;
        return plugin + "/" + action;
    }

    private static String safeMessage(String message) {
        return TextUtils.isEmpty(message) ? "unknown policy problem" : message;
    }
}
