package dev.perms.test.plugins;

import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import dev.perms.test.plugins.editor.PluginEditorDeclarativeUiValidator;

/** Package-level validation for staged/imported PermsTest plugin folders. */
public final class PluginPackageValidator {
    private static final int MAX_TEXT_FILE_BYTES = 512 * 1024;

    private PluginPackageValidator() {
    }

    public static void requireReadyForStaging(PluginManifest manifest) {
        String problem = requiredFileProblem(manifest, true);
        if (!TextUtils.isEmpty(problem)) throw new IllegalArgumentException(problem);
    }


    public static String buildReadinessReport(PluginManifest manifest) {
        StringBuilder report = new StringBuilder();
        report.append("Plugin Package Readiness\n");
        if (manifest == null) {
            report.append("\nResult: failed\n• Plugin manifest is unavailable.\n");
            return report.toString();
        }
        appendLine(report, "Name", manifest.name);
        appendLine(report, "ID", manifest.id);
        appendLine(report, "Version", manifest.version);
        appendLine(report, "Runtime", manifest.runtime);
        appendLine(report, "API version", String.valueOf(manifest.apiVersion));
        appendLine(report, "Window", firstNonEmpty(manifest.windowStyle, "compact") + "/" + firstNonEmpty(manifest.windowFit, "current"));
        if (manifest.homeDir != null) appendLine(report, "Folder", manifest.homeDir.getAbsolutePath());

        String modelProblem = manifestModelProblem(manifest);
        if (!TextUtils.isEmpty(modelProblem)) {
            report.append("\nResult: failed\n• ").append(modelProblem).append('\n');
            return report.toString();
        }
        if (manifest.homeDir == null || !manifest.homeDir.isDirectory()) {
            report.append("\nResult: failed\n• Staged plugin folder is missing.\n");
            return report.toString();
        }

        File home = manifest.homeDir;
        report.append("\nDeclared package files:\n");
        if (TextUtils.isEmpty(manifest.icon)) {
            report.append("• Icon: default app icon will be used.\n");
        } else {
            appendFileStatus(report, home, manifest.icon, "Icon", false, false, "");
        }

        Set<String> uiPaths = declaredDeclarativeUiPaths(manifest);
        if (uiPaths.isEmpty()) {
            report.append("• Declarative UI: none declared.\n");
        } else {
            for (String path : uiPaths) {
                appendFileStatus(report, home, path, "Declarative UI", true, false, "");
            }
        }

        boolean anyScript = false;
        boolean anyTrusted = false;
        if (manifest.actions != null) {
            for (PluginAction action : manifest.actions) {
                if (action == null) continue;
                if (!TextUtils.isEmpty(action.script)) {
                    anyScript = true;
                    appendFileStatus(report, home, action.script, "Script for " + action.id, false, false, "");
                }
            }
            for (PluginAction action : manifest.actions) {
                if (action == null || !action.isTrustedDexAction()) continue;
                anyTrusted = true;
                String payload = trustedDexPayloadPath(action);
                String expected = action.raw == null ? "" : action.raw.optString("sha256", "").trim();
                appendFileStatus(report, home, payload, "Trusted-Dex payload for " + action.id, false, true, expected);
            }
        }
        if (!anyScript) report.append("• Script files: none declared.\n");
        if (!anyTrusted) report.append("• Trusted-Dex payloads: none declared.\n");

        report.append("\nActions:\n");
        if (manifest.actions == null || manifest.actions.isEmpty()) {
            report.append("• none\n");
        } else {
            for (PluginAction action : manifest.actions) {
                if (action == null) {
                    report.append("• invalid action entry\n");
                    continue;
                }
                report.append("• ").append(action.title).append(" [").append(action.id).append("]")
                        .append(" type=").append(firstNonEmpty(action.type, "default"));
                String entry = firstNonEmpty(action.target, action.handler, action.script);
                if (!TextUtils.isEmpty(entry)) report.append(" entry=").append(entry);
                if (!TextUtils.isEmpty(action.command)) report.append(" command=inline");
                if (action.requires != null && !action.requires.isEmpty()) {
                    report.append(" requires=");
                    boolean first = true;
                    for (String require : action.requires) {
                        if (!first) report.append(',');
                        report.append(require);
                        first = false;
                    }
                }
                report.append('\n');
            }
        }

        String problem = requiredFileProblem(manifest, true);
        report.append("\nResult: ").append(TextUtils.isEmpty(problem) ? "ready" : "failed").append('\n');
        if (TextUtils.isEmpty(problem)) {
            report.append("This staged plugin is ready for export/import round-trip checks. This report does not run plugin code, launch plugin APK components, approve script actions, or trust trusted-code payloads.\n");
        } else {
            report.append("• ").append(problem).append('\n');
        }
        return report.toString();
    }

    public static String requiredFileProblem(PluginManifest manifest) {
        return requiredFileProblem(manifest, true);
    }

    public static String requiredFileProblem(PluginManifest manifest, boolean validateContents) {
        String manifestProblem = manifestModelProblem(manifest);
        if (!TextUtils.isEmpty(manifestProblem)) return manifestProblem;
        if (manifest == null || manifest.homeDir == null) return "Staged plugin directory is unavailable.";
        File home = manifest.homeDir;
        if (!home.isDirectory()) return "Staged plugin folder is missing: " + manifest.id;

        if (!TextUtils.isEmpty(manifest.icon)) {
            String problem = requireFile(home, manifest.icon, "Declared icon file");
            if (!TextUtils.isEmpty(problem)) return problem;
        }

        for (String entry : declaredDeclarativeUiPaths(manifest)) {
            String problem = requireFile(home, entry, "Declarative UI file");
            if (!TextUtils.isEmpty(problem)) return problem;
            if (validateContents) {
                problem = validateDeclarativeUiFile(home, entry);
                if (!TextUtils.isEmpty(problem)) return problem;
            }
        }

        for (PluginAction action : manifest.actions) {
            if (action == null || TextUtils.isEmpty(action.script)) continue;
            String problem = requireFile(home, action.script, "Script file for action " + action.id);
            if (!TextUtils.isEmpty(problem)) return problem;
        }

        for (PluginAction action : manifest.actions) {
            if (action == null || !action.isTrustedDexAction()) continue;
            String payload = trustedDexPayloadPath(action);
            if (TextUtils.isEmpty(payload)) return "Trusted-Dex payload is not declared for action " + action.id + ".";
            String problem = requireFile(home, payload, "Trusted-Dex payload file for action " + action.id);
            if (!TextUtils.isEmpty(problem)) return problem;
            if (!looksLikeTrustedDexPayloadPath(payload)) {
                return "Trusted-Dex payload must be a .dex, .jar, or .apk file for action " + action.id + ": " + payload;
            }
            String expected = action.raw == null ? "" : action.raw.optString("sha256", "").trim();
            if (!TextUtils.isEmpty(expected)) {
                if (!isFullSha256(expected)) return "Trusted-Dex sha256 must be 64 hex characters for action " + action.id + ".";
                try {
                    String actual = sha256Full(resolveFile(home, payload));
                    if (!expected.equalsIgnoreCase(actual)) {
                        return "Trusted-Dex payload hash mismatch for action " + action.id
                                + ". expected=" + expected + " actual=" + actual;
                    }
                } catch (Throwable t) {
                    return "Unable to hash Trusted-Dex payload for action " + action.id + ": " + safeMessage(t);
                }
            }
        }
        return "";
    }


    private static void appendLine(StringBuilder sb, String label, String value) {
        if (sb == null || TextUtils.isEmpty(label) || TextUtils.isEmpty(value)) return;
        sb.append(label).append(": ").append(value).append('\n');
    }

    private static void appendFileStatus(StringBuilder sb, File home, String relativePath, String label,
                                         boolean validateDeclarativeUi, boolean hashFile, String expectedSha256) {
        if (sb == null) return;
        sb.append("• ").append(TextUtils.isEmpty(label) ? "File" : label).append(": ");
        if (TextUtils.isEmpty(relativePath)) {
            sb.append("missing path\n");
            return;
        }
        sb.append(relativePath);
        if (!isSafeRelativePluginPath(relativePath)) {
            sb.append(" — unsafe path\n");
            return;
        }
        try {
            File file = resolveFile(home, relativePath);
            if (!file.isFile()) {
                sb.append(" — missing\n");
                return;
            }
            sb.append(" — present, ").append(file.length()).append(" bytes");
            if (validateDeclarativeUi) {
                String uiProblem = validateDeclarativeUiFile(home, relativePath);
                sb.append(TextUtils.isEmpty(uiProblem) ? ", ui valid" : ", ui invalid: " + uiProblem);
            }
            if (hashFile) {
                String actual = sha256Full(file);
                sb.append(", sha256=").append(actual);
                if (!TextUtils.isEmpty(expectedSha256)) {
                    sb.append(expectedSha256.equalsIgnoreCase(actual) ? ", declared hash matches" : ", declared hash mismatch");
                } else {
                    sb.append(", no declared hash");
                }
            }
            sb.append('\n');
        } catch (Throwable t) {
            sb.append(" — check failed: ").append(safeMessage(t)).append('\n');
        }
    }

    private static String manifestModelProblem(PluginManifest manifest) {
        if (manifest == null) return "Plugin manifest is unavailable.";
        if (!PluginManifest.SCHEMA.equals(manifest.schema)) {
            return "Unsupported plugin schema: " + manifest.schema;
        }
        if (manifest.apiVersion <= 0 || manifest.apiVersion > PluginManifest.SUPPORTED_API_VERSION) {
            return "Unsupported plugin apiVersion: " + manifest.apiVersion
                    + ". PermsTest currently supports apiVersion " + PluginManifest.SUPPORTED_API_VERSION + ".";
        }
        if (!PluginManifest.isSafeId(manifest.id)) return "Plugin ID is invalid: " + manifest.id;
        if (TextUtils.isEmpty(manifest.name)) return "Plugin name is required.";
        if (TextUtils.isEmpty(manifest.version)) return "Plugin version is required.";
        if (!isSupportedRuntime(manifest.runtime)) return "Unsupported plugin runtime: " + manifest.runtime;
        if (!TextUtils.isEmpty(manifest.icon) && !isSafeRelativePluginPath(manifest.icon)) {
            return "Unsafe declared icon path: " + manifest.icon;
        }
        if (!isSupportedWindowStyle(firstNonEmpty(manifest.windowStyle, "compact"))) {
            return "windowStyle must be compact or full.";
        }
        if (!isSupportedWindowFit(firstNonEmpty(manifest.windowFit, "current"))) {
            return "windowFit must be current or fit.";
        }
        Set<String> capabilities = new LinkedHashSet<>();
        if (manifest.capabilities != null) {
            for (String capability : manifest.capabilities) {
                String value = normalizedToken(capability);
                if (TextUtils.isEmpty(value)) continue;
                if (!PluginRuntimePolicy.isSupportedCapability(value)) return "Unsupported plugin capability: " + capability;
                capabilities.add(value);
            }
        }
        if (manifest.actions == null || manifest.actions.isEmpty()) return "At least one plugin action is required.";
        Set<String> actionIds = new LinkedHashSet<>();
        for (PluginAction action : manifest.actions) {
            if (action == null) return "Plugin action entry is invalid.";
            if (!PluginManifest.isSafeId(action.id)) return "Plugin action has invalid id: " + action.id;
            if (!actionIds.add(action.id)) return "Duplicate plugin action id: " + action.id;
            if (TextUtils.isEmpty(action.title)) return "Plugin action " + action.id + " needs a title.";
            if (!isSupportedActionType(action.type)) return "Action " + action.id + " has unsupported type: " + action.type;
            if (!isSupportedPresentation(firstNonEmpty(action.presentation, "default"))) {
                return "Action " + action.id + " has unsupported presentation: " + action.presentation;
            }
            if (!isSupportedSyntax(firstNonEmpty(action.syntax, "default"))) {
                return "Action " + action.id + " has unsupported syntax: " + action.syntax;
            }
            if (!isSupportedActionWindowStyle(firstNonEmpty(action.windowStyle, "inherit"))) {
                return "Action " + action.id + " windowStyle must be inherit, compact, or full.";
            }
            if (!isSupportedActionWindowFit(firstNonEmpty(action.windowFit, "inherit"))) {
                return "Action " + action.id + " windowFit must be inherit, current, or fit.";
            }
            if (action.requires != null) {
                for (String require : action.requires) {
                    String value = normalizedToken(require);
                    if (TextUtils.isEmpty(value)) continue;
                    if (!PluginRuntimePolicy.isSupportedCapability(value)) {
                        return "Action " + action.id + " requires unsupported capability: " + require;
                    }
                    if (!capabilities.isEmpty() && !capabilities.contains(value)) {
                        return "Action " + action.id + " requires capability '" + value
                                + "' but plugin capabilities does not declare it.";
                    }
                }
            }
            if (action.isNativeAction()) {
                if (TextUtils.isEmpty(action.handler)) return "Native action " + action.id + " needs a handler.";
            } else if (action.isDeclarativeAction()) {
                String entry = firstNonEmpty(action.target, action.handler, manifest.entry, "ui.json");
                if (!isSafeRelativePluginPath(entry)) return "Declarative action " + action.id + " has unsafe UI path: " + entry;
            } else if (action.isScriptAction()) {
                if (TextUtils.isEmpty(action.command) && TextUtils.isEmpty(action.script)) {
                    return "Script action " + action.id + " needs a command or script file.";
                }
                if (!TextUtils.isEmpty(action.script) && !isSafeRelativePluginPath(action.script)) {
                    return "Script action " + action.id + " has unsafe script path: " + action.script;
                }
            } else if (action.isTrustedDexAction()) {
                String payload = trustedDexPayloadPath(action);
                if (TextUtils.isEmpty(payload)) return "Trusted-Dex action " + action.id + " needs a payload path.";
                if (!isSafeRelativePluginPath(payload)) return "Trusted-Dex action " + action.id + " has unsafe payload path: " + payload;
                if (!looksLikeTrustedDexPayloadPath(payload)) {
                    return "Trusted-Dex payload must be a .dex, .jar, or .apk file for action " + action.id + ": " + payload;
                }
                String expected = action.raw == null ? "" : action.raw.optString("sha256", "").trim();
                if (!TextUtils.isEmpty(expected) && !isFullSha256(expected)) {
                    return "Trusted-Dex sha256 must be 64 hex characters for action " + action.id + ".";
                }
                String className = trustedDexClassName(action);
                String methodName = trustedDexMethodName(action);
                if (TextUtils.isEmpty(className) || !looksLikeJavaClassName(className)) {
                    return "Trusted-Dex action " + action.id + " needs a safe className/entryClass.";
                }
                if (TextUtils.isEmpty(methodName) || !looksLikeJavaIdentifier(methodName)) {
                    return "Trusted-Dex action " + action.id + " needs a safe methodName.";
                }
            }
        }
        return "";
    }

    public static Set<String> declaredDeclarativeUiPaths(PluginManifest manifest) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (manifest == null) return paths;
        if ("declarative".equals(manifest.runtime)) {
            if (!TextUtils.isEmpty(manifest.entry)) paths.add(normalizedPath(manifest.entry));
        }
        if (manifest.actions != null) {
            for (PluginAction action : manifest.actions) {
                if (action == null || !action.isDeclarativeAction()) continue;
                String path = firstNonEmpty(action.target, action.handler);
                if (!TextUtils.isEmpty(path)) paths.add(normalizedPath(path));
            }
        }
        if ("declarative".equals(manifest.runtime) && paths.isEmpty()) paths.add("ui.json");
        return paths;
    }

    private static String requireFile(File home, String relativePath, String label) {
        if (!isSafeRelativePluginPath(relativePath)) return "Unsafe " + label.toLowerCase(Locale.US) + " path: " + relativePath;
        try {
            File file = resolveFile(home, relativePath);
            if (!file.isFile()) return label + " is missing: " + relativePath;
        } catch (Throwable t) {
            return "Unable to resolve " + label.toLowerCase(Locale.US) + " " + relativePath + ": " + safeMessage(t);
        }
        return "";
    }

    private static String validateDeclarativeUiFile(File home, String relativePath) {
        try {
            File file = resolveFile(home, relativePath);
            String uiJson = readUtf8Limited(file, MAX_TEXT_FILE_BYTES);
            String problem = PluginEditorDeclarativeUiValidator.runtimeProblem(uiJson);
            if (!TextUtils.isEmpty(problem)) return "Invalid declarative UI file " + relativePath + ": " + problem;
        } catch (Throwable t) {
            return "Unable to validate declarative UI file " + relativePath + ": " + safeMessage(t);
        }
        return "";
    }

    private static File resolveFile(File home, String relativePath) throws Exception {
        File root = home.getCanonicalFile();
        File file = new File(root, normalizedPath(relativePath)).getCanonicalFile();
        String rootPath = root.getPath() + File.separator;
        if (!file.getPath().startsWith(rootPath)) {
            throw new IllegalArgumentException("path escapes plugin directory");
        }
        return file;
    }

    private static boolean isSafeRelativePluginPath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String p = normalizedPath(path);
        if (TextUtils.isEmpty(p) || p.startsWith("/") || p.contains(":")) return false;
        String[] parts = p.split("/", -1);
        for (String part : parts) {
            if (TextUtils.isEmpty(part) || ".".equals(part) || "..".equals(part)) return false;
        }
        return true;
    }

    private static boolean isSupportedRuntime(String runtime) {
        String value = normalizedToken(runtime);
        return "declarative".equals(value)
                || "script".equals(value)
                || "trusted_native".equals(value)
                || "native".equals(value)
                || "trusted_dex".equals(value);
    }

    private static boolean isSupportedActionType(String type) {
        String value = normalizedToken(type);
        return TextUtils.isEmpty(value)
                || "native".equals(value)
                || "trusted_native".equals(value)
                || "declarative".equals(value)
                || "declarative_ui".equals(value)
                || "ui".equals(value)
                || "shell".equals(value)
                || "script".equals(value)
                || "trusted_dex".equals(value)
                || "dex".equals(value);
    }

    private static boolean isSupportedWindowStyle(String style) {
        String value = normalizedToken(style);
        return "compact".equals(value) || "full".equals(value);
    }

    private static boolean isSupportedWindowFit(String fit) {
        String value = normalizedToken(fit);
        return "current".equals(value) || "fit".equals(value);
    }

    private static boolean isSupportedActionWindowStyle(String style) {
        String value = normalizedToken(style);
        return TextUtils.isEmpty(value) || "inherit".equals(value) || "compact".equals(value) || "full".equals(value);
    }

    private static boolean isSupportedActionWindowFit(String fit) {
        String value = normalizedToken(fit);
        return TextUtils.isEmpty(value) || "inherit".equals(value) || "current".equals(value) || "fit".equals(value);
    }

    private static boolean isSupportedPresentation(String presentation) {
        String value = normalizedToken(presentation);
        return TextUtils.isEmpty(value)
                || "default".equals(value)
                || "dialog".equals(value)
                || "viewer".equals(value)
                || "window".equals(value)
                || "panel".equals(value)
                || "large".equals(value)
                || "log".equals(value)
                || "output".equals(value)
                || "main_output".equals(value);
    }

    private static boolean isSupportedSyntax(String syntax) {
        String value = normalizedToken(syntax);
        return TextUtils.isEmpty(value)
                || "default".equals(value)
                || "plain".equals(value)
                || "text".equals(value)
                || "json".equals(value)
                || "properties".equals(value)
                || "prop".equals(value)
                || "ini".equals(value)
                || "shell".equals(value)
                || "bash".equals(value)
                || "sh".equals(value)
                || "smali".equals(value)
                || "web".equals(value)
                || "html".equals(value)
                || "css".equals(value)
                || "js".equals(value);
    }

    private static String trustedDexClassName(PluginAction action) {
        if (action == null || action.raw == null) return "";
        return firstNonEmpty(action.raw.optString("className", ""),
                action.raw.optString("entryClass", ""),
                action.raw.optString("class", ""));
    }

    private static String trustedDexMethodName(PluginAction action) {
        if (action == null || action.raw == null) return "";
        return firstNonEmpty(action.raw.optString("methodName", ""), action.raw.optString("method", ""));
    }

    private static boolean looksLikeJavaClassName(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String[] parts = value.trim().split("\\.");
        if (parts.length == 0) return false;
        for (String part : parts) {
            if (!looksLikeJavaIdentifier(part)) return false;
        }
        return true;
    }

    private static boolean looksLikeJavaIdentifier(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String v = value.trim();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = (i == 0)
                    ? (c == '_' || c == '$' || Character.isLetter(c))
                    : (c == '_' || c == '$' || Character.isLetterOrDigit(c));
            if (!ok) return false;
        }
        return true;
    }

    private static String normalizedToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static String trustedDexPayloadPath(PluginAction action) {
        if (action == null) return "";
        String value = firstNonEmpty(action.target, action.handler);
        if (!TextUtils.isEmpty(value)) return value;
        JSONObject raw = action.raw;
        if (raw == null) return "";
        return firstNonEmpty(raw.optString("dex", ""), raw.optString("path", ""));
    }

    private static boolean looksLikeTrustedDexPayloadPath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String p = path.trim().toLowerCase(Locale.US);
        return p.endsWith(".dex") || p.endsWith(".jar") || p.endsWith(".apk");
    }

    private static String normalizedPath(String path) {
        return path == null ? "" : path.trim().replace('\\', '/');
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private static boolean isFullSha256(String value) {
        if (TextUtils.isEmpty(value) || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
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

    private static String readUtf8Limited(File file, int maxBytes) throws Exception {
        int limit = Math.max(1, maxBytes);
        long length = file.length();
        if (length > limit) throw new IllegalArgumentException("file exceeds " + limit + " bytes");
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(length, limit))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? "" : t.getMessage();
        return TextUtils.isEmpty(msg) ? String.valueOf(t) : msg;
    }
}
