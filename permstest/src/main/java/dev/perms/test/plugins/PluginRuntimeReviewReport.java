package dev.perms.test.plugins;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Builds user-visible runtime-policy review text for staged plugin packages. */
public final class PluginRuntimeReviewReport {
    private static final int MAX_COMMAND_CHARS = 1600;
    private static final int MAX_SCRIPT_PREVIEW_CHARS = 2400;
    private static final int MAX_SCRIPT_PREVIEW_BYTES = 16 * 1024;

    private PluginRuntimeReviewReport() {
    }

    public static String build(SharedPreferences prefs, PluginManifest plugin, boolean disabled) {
        return build(prefs, plugin, disabled, plugin == null ? null : plugin.homeDir);
    }

    public static String build(SharedPreferences prefs, PluginManifest plugin, boolean disabled, File pluginDir) {
        StringBuilder sb = new StringBuilder();
        final char nl = (char) 10;
        sb.append(PluginRuntimePolicy.reviewText(prefs, plugin, disabled));
        if (plugin == null) {
            return sb.toString();
        }

        if (PluginRuntimePolicy.pluginHasScriptActions(plugin)) {
            appendScriptReviewDetails(sb, plugin, pluginDir, nl);
        }
        if (PluginRuntimePolicy.pluginHasTrustedDexActions(plugin)) {
            appendTrustedDexReviewDetails(sb, prefs, plugin, pluginDir, nl);
        }
        return sb.toString();
    }

    private static void appendScriptReviewDetails(StringBuilder sb, PluginManifest plugin, File pluginDir, char nl) {
        sb.append(nl).append(nl).append("Script review details:").append(nl);
        sb.append("Approval fingerprint: ").append(PluginRuntimePolicy.scriptApprovalFingerprint(plugin)).append(nl);
        sb.append("Review inline command text, plugin-local script file summaries, and shell-capable declarative UI file summaries before approving. The approval fingerprint includes controlled shell action metadata, plugin-local script file hash state, and shell-capable declarative UI target hash state, so edited staged scripts or UI files require re-approval. File previews are truncated and are only for review; scripts still run through the existing explicit user-tapped shell capture path.").append(nl);

        if (plugin.actions == null || plugin.actions.isEmpty()) {
            sb.append("• No script actions declared.").append(nl);
            return;
        }

        for (PluginAction action : plugin.actions) {
            if (!PluginRuntimePolicy.actionUsesControlledShell(action)) continue;
            sb.append(nl).append("• ").append(safe(action.title)).append(" [").append(safe(action.id)).append("]").append(nl);
            sb.append("  Type: ").append(TextUtils.isEmpty(action.type) ? "shell" : action.type).append(nl);
            if (action.requires != null && !action.requires.isEmpty()) {
                sb.append("  Requires: ").append(joinActionValues(action.requires)).append(nl);
            }
            if (!TextUtils.isEmpty(action.command)) {
                sb.append("  Inline command:").append(nl);
                sb.append(indent(truncate(action.command, MAX_COMMAND_CHARS))).append(nl);
            }
            if (!TextUtils.isEmpty(action.script)) {
                appendScriptDetails(sb, pluginDir, action.script, nl);
            }
            appendControlledDeclarativeUiTargetDetails(sb, plugin, pluginDir, action, nl);
            if (!action.isScriptAction() && TextUtils.isEmpty(action.command) && TextUtils.isEmpty(action.script)) {
                sb.append("  Controlled shell capability is declared by this action's requires list. The actual shell command may live inside the declarative UI target and is reviewed at per-run confirmation time.").append(nl);
            } else if (TextUtils.isEmpty(action.command) && TextUtils.isEmpty(action.script)) {
                sb.append("  No inline command or plugin-local script file is declared.").append(nl);
            }
        }
    }

    private static void appendTrustedDexReviewDetails(StringBuilder sb, SharedPreferences prefs, PluginManifest plugin, File pluginDir, char nl) {
        sb.append(nl).append(nl).append("Trusted-Dex review details:").append(nl);
        sb.append("trusted_dex actions are policy-gated for explicit user-tapped trusted-code dispatch. Dispatch requires the runtime gate, exact-payload trust, declared SHA-256 match, capability checks, and class/method metadata. Plugin APK components are not launched.").append(nl);
        sb.append("Trusted code approval: ").append(PluginRuntimePolicy.trustedDexApprovalStatus(prefs, plugin)).append(nl);
        sb.append("Trusted payload fingerprint: ").append(PluginRuntimePolicy.trustedDexApprovalFingerprint(plugin)).append(nl);
        sb.append("For trusted_dex actions, target or handler should point to a plugin-local .dex, .jar, or .apk payload. Advanced JSON fields can include className, methodName, sha256, and entryClass for review and explicit trusted-code dispatch.").append(nl);

        if (plugin.actions == null || plugin.actions.isEmpty()) {
            sb.append("• No trusted_dex actions declared.").append(nl);
            return;
        }

        for (PluginAction action : plugin.actions) {
            if (action == null || !action.isTrustedDexAction()) continue;
            JSONObject raw = action.raw == null ? new JSONObject() : action.raw;
            String entryPath = firstNonEmpty(action.target, action.handler, raw.optString("dex", ""), raw.optString("path", ""));
            String className = firstNonEmpty(raw.optString("className", ""), raw.optString("entryClass", ""), raw.optString("class", ""));
            String methodName = firstNonEmpty(raw.optString("methodName", ""), raw.optString("method", ""));
            String expectedSha256 = raw.optString("sha256", "").trim();
            sb.append(nl).append("• ").append(safe(action.title)).append(" [").append(safe(action.id)).append("]").append(nl);
            sb.append("  Type: ").append(TextUtils.isEmpty(action.type) ? "trusted_dex" : action.type).append(nl);
            if (action.requires != null && !action.requires.isEmpty()) {
                sb.append("  Requires: ").append(joinActionValues(action.requires)).append(nl);
            }
            sb.append("  Entry payload: ").append(TextUtils.isEmpty(entryPath) ? "not declared" : entryPath).append(nl);
            if (!TextUtils.isEmpty(className)) sb.append("  Class: ").append(className).append(nl);
            if (!TextUtils.isEmpty(methodName)) sb.append("  Method: ").append(methodName).append(nl);
            if (!TextUtils.isEmpty(expectedSha256)) sb.append("  Expected sha256: ").append(expectedSha256).append(nl);
            String approvalProblem = PluginRuntimePolicy.trustedDexApprovalProblem(prefs, plugin, action);
            if (!TextUtils.isEmpty(approvalProblem)) sb.append("  Trust status: blocked; ").append(approvalProblem).append(nl);
            else sb.append("  Trust status: trusted for the current exact payload fingerprint; runtime gate and payload checks still apply.").append(nl);
            appendTrustedDexPayloadDetails(sb, pluginDir, entryPath, expectedSha256, nl);
        }
    }

    public static String buildScriptReadiness(SharedPreferences prefs,
                                              PluginManifest plugin,
                                              boolean disabled,
                                              File pluginDir) {
        StringBuilder sb = new StringBuilder();
        final char nl = (char) 10;
        if (plugin == null) {
            return "Plugin manifest is unavailable.";
        }
        sb.append("Script Action Readiness").append(nl);
        sb.append(plugin.name).append(nl);
        sb.append("ID: ").append(safe(plugin.id)).append(nl);
        sb.append("Version: ").append(safe(plugin.version)).append(nl);
        sb.append("Runtime: ").append(safe(plugin.runtime)).append(nl);
        sb.append("Disabled: ").append(disabled ? "yes" : "no").append(nl);
        sb.append("Script runtime gate: ")
                .append(PluginRuntimePolicy.isScriptRuntimeEnabled(prefs) ? "enabled" : "blocked")
                .append(nl);
        sb.append("Script approval required: ")
                .append(PluginRuntimePolicy.isScriptApprovalRequired(prefs) ? "yes" : "no")
                .append(nl);
        sb.append("Script approval: ")
                .append(PluginRuntimePolicy.scriptApprovalStatus(prefs, plugin))
                .append(nl);
        sb.append("Script approval fingerprint: ")
                .append(PluginRuntimePolicy.scriptApprovalFingerprint(plugin))
                .append(nl);
        sb.append("Per-run confirmation: ")
                .append(PluginRuntimePolicy.isScriptRunConfirmationRequired(prefs) ? "required" : "optional")
                .append(nl).append(nl);

        if (!PluginRuntimePolicy.pluginHasScriptActions(plugin)) {
            sb.append("No controlled shell/script actions are declared.").append(nl);
            return sb.toString();
        }

        sb.append("Readiness checklist:").append(nl);
        sb.append("• This check never runs shell commands, launches plugin APK components, approves scripts, or schedules background work.").append(nl);
        sb.append("• Dispatch remains explicit user-tapped only and still rechecks runtime gate, approval, capabilities, and plugin disabled state.").append(nl);
        sb.append("• Declarative UI shell buttons are blocked in Plugin Editor Preview UI and share these same shell/script gates when staged.").append(nl);

        if (plugin.actions != null) {
            for (PluginAction action : plugin.actions) {
                if (!PluginRuntimePolicy.actionUsesControlledShell(action)) continue;
                sb.append(nl).append("• ").append(safe(action.title)).append(" [").append(safe(action.id)).append("]").append(nl);
                sb.append("  Type: ").append(TextUtils.isEmpty(action.type) ? "shell" : action.type).append(nl);
                if (action.requires != null && !action.requires.isEmpty()) {
                    sb.append("  Requires: ").append(joinActionValues(action.requires)).append(nl);
                }
                String capabilityProblem = PluginRuntimePolicy.capabilityDispatchProblem(plugin, action);
                String approvalProblem = PluginRuntimePolicy.scriptApprovalProblem(prefs, plugin, action);
                if (disabled) {
                    sb.append("  Dispatch readiness: blocked because the plugin is disabled.").append(nl);
                } else if (!TextUtils.isEmpty(capabilityProblem)) {
                    sb.append("  Dispatch readiness: blocked; ").append(capabilityProblem).append(nl);
                } else if (!PluginRuntimePolicy.isScriptRuntimeEnabled(prefs)) {
                    sb.append("  Dispatch readiness: blocked; controlled shell/script actions are disabled.").append(nl);
                } else if (!TextUtils.isEmpty(approvalProblem)) {
                    sb.append("  Dispatch readiness: blocked; ").append(approvalProblem).append(nl);
                } else {
                    sb.append("  Dispatch readiness: ready for explicit user-tapped shell/script dispatch.").append(nl);
                }

                if (!TextUtils.isEmpty(action.command)) {
                    sb.append("  Inline command:").append(nl);
                    sb.append(indent(truncate(action.command, MAX_COMMAND_CHARS))).append(nl);
                }
                if (!TextUtils.isEmpty(action.script)) {
                    appendScriptDetails(sb, pluginDir == null ? plugin.homeDir : pluginDir, action.script, nl);
                }
                appendControlledDeclarativeUiTargetDetails(sb, plugin, pluginDir == null ? plugin.homeDir : pluginDir, action, nl);
                if (!action.isScriptAction() && TextUtils.isEmpty(action.command) && TextUtils.isEmpty(action.script)) {
                    sb.append("  Shell/script capability is declared by this action's requires list. The concrete shell commands may live inside declarative UI actions and remain blocked in Plugin Editor Preview UI.").append(nl);
                } else if (TextUtils.isEmpty(action.command) && TextUtils.isEmpty(action.script)) {
                    sb.append("  No inline command or plugin-local script file is declared.").append(nl);
                }
            }
        }

        sb.append(nl).append("Result: ");
        if (disabled) {
            sb.append("not dispatch-ready because the plugin is disabled.");
        } else if (!PluginRuntimePolicy.isScriptRuntimeEnabled(prefs)) {
            sb.append("not dispatch-ready because the controlled shell/script runtime gate is off.");
        } else if (PluginRuntimePolicy.isScriptApprovalRequired(prefs) && !PluginRuntimePolicy.isScriptPluginApproved(prefs, plugin)) {
            sb.append("not dispatch-ready until script actions are approved for this exact script/UI fingerprint.");
        } else {
            sb.append("ready for explicit user-tapped script actions; each action still passes policy checks again at dispatch time.");
        }
        return sb.toString();
    }

    public static String buildTrustedCodeReadiness(SharedPreferences prefs,
                                                   PluginManifest plugin,
                                                   boolean disabled,
                                                   File pluginDir) {
        StringBuilder sb = new StringBuilder();
        final char nl = (char) 10;
        if (plugin == null) {
            return "Plugin manifest is unavailable.";
        }
        sb.append("Trusted Code Loader Readiness").append(nl);
        sb.append(plugin.name).append(nl);
        sb.append("ID: ").append(safe(plugin.id)).append(nl);
        sb.append("Version: ").append(safe(plugin.version)).append(nl);
        sb.append("Runtime: ").append(safe(plugin.runtime)).append(nl);
        sb.append("Disabled: ").append(disabled ? "yes" : "no").append(nl);
        sb.append("Trusted-Dex runtime requested: ")
                .append(PluginRuntimePolicy.isTrustedDexRuntimeRequested(prefs) ? "yes" : "no")
                .append(nl);
        sb.append("Trusted-Dex loader compiled/enabled: ")
                .append(PluginRuntimePolicy.isTrustedDexRuntimeCompiledEnabled() ? "yes" : "no")
                .append(nl);
        sb.append("Trusted-Dex effective runtime gate: ")
                .append(PluginRuntimePolicy.isTrustedDexRuntimeEnabled(prefs) ? "enabled" : "blocked")
                .append(nl);
        sb.append("Trusted payload fingerprint: ")
                .append(PluginRuntimePolicy.trustedDexApprovalFingerprint(plugin))
                .append(nl);
        sb.append("Trusted code approval: ")
                .append(PluginRuntimePolicy.trustedDexApprovalStatus(prefs, plugin))
                .append(nl).append(nl);

        if (!PluginRuntimePolicy.pluginHasTrustedDexActions(plugin)) {
            sb.append("No trusted_dex actions are declared.").append(nl);
            return sb.toString();
        }

        sb.append("Readiness checklist:").append(nl);
        sb.append("• This check never loads code, opens a ClassLoader, launches plugin APK components, or calls plugin methods.").append(nl);
        sb.append("• Current build loader state: ")
                .append(PluginRuntimePolicy.isTrustedDexRuntimeCompiledEnabled()
                        ? "compiled policy gate exists"
                        : "loader intentionally not enabled")
                .append(".").append(nl);
        sb.append("• Only explicit user-tapped dispatch can run trusted code after every item below is clean and the runtime gate is enabled.").append(nl);

        if (plugin.actions != null) {
            for (PluginAction action : plugin.actions) {
                if (action == null || !action.isTrustedDexAction()) continue;
                JSONObject raw = action.raw == null ? new JSONObject() : action.raw;
                String entryPath = firstNonEmpty(action.target, action.handler, raw.optString("dex", ""), raw.optString("path", ""));
                String className = firstNonEmpty(raw.optString("className", ""), raw.optString("entryClass", ""), raw.optString("class", ""));
                String methodName = firstNonEmpty(raw.optString("methodName", ""), raw.optString("method", ""));
                String expectedSha256 = raw.optString("sha256", "").trim();
                sb.append(nl).append("• ").append(safe(action.title)).append(" [").append(safe(action.id)).append("]").append(nl);
                sb.append("  Payload: ").append(TextUtils.isEmpty(entryPath) ? "not declared" : entryPath).append(nl);
                sb.append("  Expected sha256: ").append(TextUtils.isEmpty(expectedSha256) ? "not declared" : expectedSha256).append(nl);
                sb.append("  Class metadata: ").append(TextUtils.isEmpty(className) ? "not declared" : className).append(nl);
                sb.append("  Method metadata: ").append(TextUtils.isEmpty(methodName) ? "not declared" : methodName).append(nl);
                String capabilityProblem = PluginRuntimePolicy.capabilityDispatchProblem(plugin, action);
                String approvalProblem = PluginRuntimePolicy.trustedDexApprovalProblem(prefs, plugin, action);
                if (disabled) {
                    sb.append("  Dispatch readiness: blocked because the plugin is disabled.").append(nl);
                } else if (!TextUtils.isEmpty(capabilityProblem)) {
                    sb.append("  Dispatch readiness: blocked; ").append(capabilityProblem).append(nl);
                } else if (!TextUtils.isEmpty(approvalProblem)) {
                    sb.append("  Dispatch readiness: blocked; ").append(approvalProblem).append(nl);
                } else if (!PluginRuntimePolicy.isTrustedDexRuntimeCompiledEnabled()) {
                    sb.append("  Dispatch readiness: blocked; in-process trusted-code loader is not enabled.").append(nl);
                } else if (!PluginRuntimePolicy.isTrustedDexRuntimeEnabled(prefs)) {
                    sb.append("  Dispatch readiness: blocked; trusted-Dex runtime gate is off.").append(nl);
                } else {
                    sb.append("  Dispatch readiness: ready for explicit user-tapped trusted-code dispatch.").append(nl);
                }
                appendTrustedDexPayloadDetails(sb, pluginDir == null ? plugin.homeDir : pluginDir, entryPath, expectedSha256, nl);
            }
        }

        sb.append(nl).append("Result: ");
        if (!PluginRuntimePolicy.isTrustedDexRuntimeCompiledEnabled()) {
            sb.append("not loader-ready because the trusted-code loader gate is unavailable.");
        } else if (!PluginRuntimePolicy.isTrustedDexRuntimeEnabled(prefs)) {
            sb.append("not loader-ready because the runtime gate is off.");
        } else {
            sb.append("policy gate is open; each action still passes the per-action checks again at dispatch time.");
        }
        return sb.toString();
    }

    public static String buildScriptActionRunReview(SharedPreferences prefs,
                                                    PluginManifest plugin,
                                                    PluginAction action,
                                                    boolean disabled,
                                                    File pluginDir) {
        StringBuilder sb = new StringBuilder();
        final char nl = (char) 10;
        if (plugin == null || action == null) {
            return "Plugin action is unavailable.";
        }
        sb.append("Script action run review:").append(nl);
        sb.append(plugin.name).append(" / ").append(safe(action.title)).append(nl);
        sb.append("ID: ").append(safe(plugin.id)).append("/").append(safe(action.id)).append(nl);
        sb.append("Runtime: ").append(safe(plugin.runtime)).append(nl);
        sb.append("Disabled: ").append(disabled ? "yes" : "no").append(nl);
        sb.append("Script runtime: ").append(PluginRuntimePolicy.isScriptRuntimeEnabled(prefs) ? "enabled" : "disabled").append(nl);
        sb.append("Script approval: ").append(PluginRuntimePolicy.scriptApprovalStatus(prefs, plugin)).append(nl);
        sb.append("Approval fingerprint: ").append(PluginRuntimePolicy.scriptApprovalFingerprint(plugin)).append(nl);
        sb.append("Per-run confirmation: ").append(PluginRuntimePolicy.isScriptRunConfirmationRequired(prefs) ? "required" : "optional").append(nl);
        sb.append(nl);

        String capabilityProblem = PluginRuntimePolicy.capabilityDispatchProblem(plugin, action);
        String approvalProblem = PluginRuntimePolicy.scriptApprovalProblem(prefs, plugin, action);
        if (disabled) {
            sb.append("Status: blocked because the plugin is disabled.").append(nl);
        } else if (!TextUtils.isEmpty(capabilityProblem)) {
            sb.append("Status: blocked; ").append(capabilityProblem).append(nl);
        } else if (!PluginRuntimePolicy.isScriptRuntimeEnabled(prefs)) {
            sb.append("Status: blocked; controlled shell/script actions are disabled.").append(nl);
        } else if (!TextUtils.isEmpty(approvalProblem)) {
            sb.append("Status: blocked; ").append(approvalProblem).append(nl);
        } else {
            sb.append("Status: ready for explicit user-tapped dispatch.").append(nl);
        }

        sb.append(nl).append("Action details:").append(nl);
        sb.append("Type: ").append(TextUtils.isEmpty(action.type) ? "shell" : action.type).append(nl);
        if (action.requires != null && !action.requires.isEmpty()) {
            sb.append("Requires: ").append(joinActionValues(action.requires)).append(nl);
        }
        if (!TextUtils.isEmpty(action.command)) {
            sb.append("Inline command:").append(nl);
            sb.append(indent(truncate(action.command, MAX_COMMAND_CHARS))).append(nl);
        }
        if (!TextUtils.isEmpty(action.script)) {
            appendScriptDetails(sb, pluginDir == null ? plugin.homeDir : pluginDir, action.script, nl);
        }
        appendControlledDeclarativeUiTargetDetails(sb, plugin, pluginDir == null ? plugin.homeDir : pluginDir, action, nl);
        if (TextUtils.isEmpty(action.command) && TextUtils.isEmpty(action.script)) {
            sb.append("No inline command or plugin-local script file is declared.").append(nl);
        }
        sb.append(nl).append("This confirmation only applies to the current explicit tap. It does not enable startup execution, background dispatch, services, receivers, boot handling, trusted-Dex loading, or plugin APK loading.");
        return sb.toString();
    }

    public static String buildTrustedCodeActionRunReview(SharedPreferences prefs,
                                                         PluginManifest plugin,
                                                         PluginAction action,
                                                         boolean disabled,
                                                         File pluginDir) {
        StringBuilder sb = new StringBuilder();
        final char nl = (char) 10;
        if (plugin == null || action == null) {
            return "Plugin action is unavailable.";
        }
        JSONObject raw = action.raw == null ? new JSONObject() : action.raw;
        String entryPath = firstNonEmpty(action.target, action.handler, raw.optString("dex", ""), raw.optString("path", ""));
        String className = firstNonEmpty(raw.optString("className", ""), raw.optString("entryClass", ""), raw.optString("class", ""));
        String methodName = firstNonEmpty(raw.optString("methodName", ""), raw.optString("method", ""), "run");
        String expectedSha256 = raw.optString("sha256", "").trim();

        sb.append("Trusted-code action run review:").append(nl);
        sb.append(plugin.name).append(" / ").append(safe(action.title)).append(nl);
        sb.append("ID: ").append(safe(plugin.id)).append("/").append(safe(action.id)).append(nl);
        sb.append("Runtime: ").append(safe(plugin.runtime)).append(nl);
        sb.append("Disabled: ").append(disabled ? "yes" : "no").append(nl);
        sb.append("Trusted-Dex runtime gate: ").append(PluginRuntimePolicy.isTrustedDexRuntimeEnabled(prefs) ? "enabled" : "blocked").append(nl);
        sb.append("Trusted-code approval: ").append(PluginRuntimePolicy.trustedDexApprovalStatus(prefs, plugin)).append(nl);
        sb.append("Trusted payload fingerprint: ").append(PluginRuntimePolicy.trustedDexApprovalFingerprint(plugin)).append(nl);
        sb.append("Per-run confirmation: ").append(PluginRuntimePolicy.isTrustedDexRunConfirmationRequired(prefs) ? "required" : "optional").append(nl);
        sb.append(nl);

        String capabilityProblem = PluginRuntimePolicy.capabilityDispatchProblem(plugin, action);
        String approvalProblem = PluginRuntimePolicy.trustedDexApprovalProblem(prefs, plugin, action);
        if (disabled) {
            sb.append("Status: blocked because the plugin is disabled.").append(nl);
        } else if (!TextUtils.isEmpty(capabilityProblem)) {
            sb.append("Status: blocked; ").append(capabilityProblem).append(nl);
        } else if (!PluginRuntimePolicy.isTrustedDexRuntimeCompiledEnabled()) {
            sb.append("Status: blocked; trusted-code loader is not compiled/enabled.").append(nl);
        } else if (!PluginRuntimePolicy.isTrustedDexRuntimeEnabled(prefs)) {
            sb.append("Status: blocked; trusted-Dex runtime gate is off.").append(nl);
        } else if (!TextUtils.isEmpty(approvalProblem)) {
            sb.append("Status: blocked; ").append(approvalProblem).append(nl);
        } else {
            sb.append("Status: ready for explicit user-tapped trusted-code dispatch after payload checks pass again at runtime.").append(nl);
        }

        sb.append(nl).append("Action details:").append(nl);
        sb.append("Type: ").append(TextUtils.isEmpty(action.type) ? "trusted_dex" : action.type).append(nl);
        if (action.requires != null && !action.requires.isEmpty()) {
            sb.append("Requires: ").append(joinActionValues(action.requires)).append(nl);
        }
        sb.append("Entry payload: ").append(TextUtils.isEmpty(entryPath) ? "not declared" : entryPath).append(nl);
        sb.append("Expected sha256: ").append(TextUtils.isEmpty(expectedSha256) ? "not declared" : expectedSha256).append(nl);
        sb.append("Class: ").append(TextUtils.isEmpty(className) ? "not declared" : className).append(nl);
        sb.append("Method: ").append(TextUtils.isEmpty(methodName) ? "run" : methodName).append(nl);
        appendTrustedDexPayloadDetails(sb, pluginDir == null ? plugin.homeDir : pluginDir, entryPath, expectedSha256, nl);
        sb.append(nl).append("This confirmation only applies to the current explicit tap. It does not enable startup execution, background dispatch, services, receivers, boot handling, or plugin APK component launching. Dispatch rechecks the payload hash, exact trust record, capability policy, and runtime gate before loading code.");
        return sb.toString();
    }

    private static void appendScriptDetails(StringBuilder sb, File pluginDir, String scriptPath, char nl) {
        sb.append("  Script file: ").append(scriptPath).append(nl);
        if (pluginDir == null || !pluginDir.isDirectory()) {
            sb.append("  Script status: plugin directory unavailable.").append(nl);
            return;
        }
        if (!isSafeRelativePluginPath(scriptPath)) {
            sb.append("  Script status: blocked unsafe plugin-relative path.").append(nl);
            return;
        }
        try {
            File script = new File(pluginDir, scriptPath);
            File root = pluginDir.getCanonicalFile();
            File canonical = script.getCanonicalFile();
            if (!canonical.getPath().startsWith(root.getPath() + File.separator)) {
                sb.append("  Script status: blocked path outside plugin directory.").append(nl);
                return;
            }
            if (!canonical.isFile()) {
                sb.append("  Script status: missing.").append(nl);
                return;
            }
            sb.append("  Script status: present, ").append(canonical.length()).append(" bytes, sha256=").append(sha256Short(canonical)).append(nl);
            String preview = readPreview(canonical);
            if (!TextUtils.isEmpty(preview)) {
                sb.append("  Script preview:").append(nl);
                sb.append(indent(preview)).append(nl);
                if (canonical.length() > MAX_SCRIPT_PREVIEW_BYTES || preview.length() >= MAX_SCRIPT_PREVIEW_CHARS) {
                    sb.append("  ... preview truncated ...").append(nl);
                }
            } else {
                sb.append("  Script preview: empty or binary-looking file.").append(nl);
            }
        } catch (Throwable t) {
            sb.append("  Script status: review failed: ").append(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()).append(nl);
        }
    }

    private static void appendControlledDeclarativeUiTargetDetails(StringBuilder sb,
                                                                  PluginManifest plugin,
                                                                  File pluginDir,
                                                                  PluginAction action,
                                                                  char nl) {
        if (sb == null || action == null || !action.isDeclarativeAction()
                || !PluginRuntimePolicy.actionUsesControlledShell(action)) {
            return;
        }
        String path = firstNonEmpty(action.target, action.handler,
                plugin == null ? "" : plugin.entry,
                "declarative".equals(plugin == null ? "" : plugin.runtime) ? "ui.json" : "");
        if (TextUtils.isEmpty(path)) {
            sb.append("  Declarative UI target: not declared; shell-capable UI actions cannot be reviewed until a UI file is declared.").append(nl);
            return;
        }
        sb.append("  Declarative UI target: ").append(path).append(nl);
        sb.append("  Declarative UI approval note: this UI file participates in the script approval fingerprint because the manifest action declares shell/script capability.").append(nl);
        if (pluginDir == null || !pluginDir.isDirectory()) {
            sb.append("  Declarative UI status: plugin directory unavailable.").append(nl);
            return;
        }
        if (!isSafeRelativePluginPath(path)) {
            sb.append("  Declarative UI status: blocked unsafe plugin-relative path.").append(nl);
            return;
        }
        try {
            File target = new File(pluginDir, path);
            File root = pluginDir.getCanonicalFile();
            File canonical = target.getCanonicalFile();
            if (!canonical.getPath().startsWith(root.getPath() + File.separator)) {
                sb.append("  Declarative UI status: blocked path outside plugin directory.").append(nl);
                return;
            }
            if (!canonical.isFile()) {
                sb.append("  Declarative UI status: missing.").append(nl);
                return;
            }
            sb.append("  Declarative UI status: present, ").append(canonical.length())
                    .append(" bytes, sha256=").append(sha256Short(canonical)).append(nl);
        } catch (Throwable t) {
            sb.append("  Declarative UI status: review failed: ")
                    .append(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage())
                    .append(nl);
        }
    }

    private static void appendTrustedDexPayloadDetails(StringBuilder sb, File pluginDir, String path, String expectedSha256, char nl) {
        if (TextUtils.isEmpty(path)) {
            sb.append("  Payload status: blocked; no plugin-local payload path is declared.").append(nl);
            return;
        }
        if (pluginDir == null || !pluginDir.isDirectory()) {
            sb.append("  Payload status: plugin directory unavailable.").append(nl);
            return;
        }
        if (!isSafeRelativePluginPath(path)) {
            sb.append("  Payload status: blocked unsafe plugin-relative path.").append(nl);
            return;
        }
        try {
            File payload = new File(pluginDir, path);
            File root = pluginDir.getCanonicalFile();
            File canonical = payload.getCanonicalFile();
            if (!canonical.getPath().startsWith(root.getPath() + File.separator)) {
                sb.append("  Payload status: blocked path outside plugin directory.").append(nl);
                return;
            }
            if (!canonical.isFile()) {
                sb.append("  Payload status: missing.").append(nl);
                return;
            }
            String actual = sha256Full(canonical);
            sb.append("  Payload status: present, ").append(canonical.length()).append(" bytes, sha256=").append(actual).append(nl);
            if (!TextUtils.isEmpty(expectedSha256)) {
                sb.append("  SHA-256 match: ").append(expectedSha256.equalsIgnoreCase(actual) ? "yes" : "no").append(nl);
            }
        } catch (Throwable t) {
            sb.append("  Payload status: review failed: ").append(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()).append(nl);
        }
    }

    private static String readPreview(File file) throws Exception {
        int limit = (int) Math.min(Math.max(0L, file.length()), MAX_SCRIPT_PREVIEW_BYTES);
        if (limit <= 0) return "";
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream(limit)) {
            byte[] buffer = new byte[2048];
            int remaining = limit;
            while (remaining > 0) {
                int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read <= 0) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8).replace('\0', ' ');
            text = text.replace("\r\n", "\n").replace('\r', '\n').trim();
            return truncate(text, MAX_SCRIPT_PREVIEW_CHARS);
        }
    }

    private static String sha256Short(File file) throws Exception {
        String full = sha256Full(file);
        return full.length() <= 24 ? full : full.substring(0, 24);
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

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars)) + "\n... truncated ...";
    }

    private static String indent(String text) {
        if (TextUtils.isEmpty(text)) return "    ";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return "    " + normalized.replace("\n", "\n    ");
    }

    private static boolean isSafeRelativePluginPath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String p = path.trim().replace('\\', '/');
        if (p.startsWith("/") || p.startsWith("../") || p.contains("/../") || p.equals("..")) return false;
        if (p.contains(":")) return false;
        return true;
    }

    private static String joinActionValues(Iterable<String> values) {
        StringBuilder sb = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (TextUtils.isEmpty(value)) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(value);
            }
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "unknown" : value;
    }
}
