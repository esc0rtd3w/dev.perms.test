package dev.perms.test.plugins;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.perms.test.plugins.editor.PluginEditorDeclarativeUiValidator;

/** Builds read-only readiness reports for declarative plugin UI files. */
public final class PluginDeclarativeReadinessReport {
    private static final int MAX_UI_BYTES = 256 * 1024;
    private static final int MAX_LISTED_IDS = 32;

    private PluginDeclarativeReadinessReport() {
    }

    public static boolean pluginHasDeclarativeUi(PluginManifest plugin) {
        if (plugin == null) return false;
        if ("declarative".equalsIgnoreCase(plugin.runtime)) return true;
        if (plugin.actions != null) {
            for (PluginAction action : plugin.actions) {
                if (action != null && action.isDeclarativeAction()) return true;
            }
        }
        return false;
    }

    public static String build(PluginManifest plugin, boolean disabled) {
        StringBuilder sb = new StringBuilder();
        final char nl = (char) 10;
        if (plugin == null) return "Plugin manifest is unavailable.";

        sb.append("Declarative UI Readiness").append(nl);
        sb.append(safe(plugin.name)).append(nl);
        sb.append("ID: ").append(safe(plugin.id)).append(nl);
        sb.append("Version: ").append(safe(plugin.version)).append(nl);
        sb.append("Runtime: ").append(safe(plugin.runtime)).append(nl);
        sb.append("Disabled: ").append(disabled ? "yes" : "no").append(nl);
        sb.append("Default entry: ").append(TextUtils.isEmpty(plugin.entry) ? "ui.json" : plugin.entry).append(nl);
        if (plugin.homeDir != null) sb.append("Folder: ").append(plugin.homeDir.getAbsolutePath()).append(nl);
        sb.append(nl);

        if (!pluginHasDeclarativeUi(plugin)) {
            sb.append("No declarative UI actions are declared.").append(nl);
            sb.append("This check is read-only and does not run plugin actions or open plugin UI.").append(nl);
            return sb.toString();
        }

        if (plugin.homeDir == null || !plugin.homeDir.isDirectory()) {
            sb.append("Result: failed").append(nl)
                    .append("• Staged plugin folder is missing.").append(nl);
            return sb.toString();
        }

        Set<String> paths = declaredUiPaths(plugin);
        if (paths.isEmpty()) paths.add("ui.json");

        boolean ready = true;
        sb.append("Readiness checklist:").append(nl);
        sb.append("• This check reads plugin.json and declarative UI JSON only.").append(nl);
        sb.append("• It does not run plugin code, execute shell actions, call host APIs, approve scripts, trust code payloads, or launch plugin APK components.").append(nl);
        sb.append("• Shell actions inside declarative UI remain blocked in Plugin Editor Preview UI and gated by script policy when the staged plugin is explicitly run.").append(nl);

        for (String path : paths) {
            UiSummary summary = inspectUiFile(plugin.homeDir, path);
            if (!TextUtils.isEmpty(summary.problem)) ready = false;
            appendUiSummary(sb, summary, nl);
        }

        sb.append(nl).append("Launch actions:").append(nl);
        if (plugin.actions == null || plugin.actions.isEmpty()) {
            sb.append("• none").append(nl);
        } else {
            for (PluginAction action : plugin.actions) {
                if (action == null || !action.isDeclarativeAction()) continue;
                String path = firstNonEmpty(action.target, action.handler, plugin.entry, "ui.json");
                sb.append("• ").append(safe(action.title)).append(" [").append(safe(action.id)).append("]")
                        .append(" -> ").append(path).append(nl);
                if (action.requires != null && !action.requires.isEmpty()) {
                    sb.append("  Requires: ");
                    for (int i = 0; i < action.requires.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(action.requires.get(i));
                    }
                    sb.append(nl);
                }
            }
        }

        sb.append(nl).append("Result: ").append(ready ? "ready" : "failed").append(nl);
        if (ready) {
            sb.append("Declarative UI files passed structure/action-target validation and are ready for preview, export, and explicit user-tapped launch.").append(nl);
        } else {
            sb.append("Fix the failed UI file checks before packaging or relying on this plugin.").append(nl);
        }
        return sb.toString();
    }

    private static Set<String> declaredUiPaths(PluginManifest plugin) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (plugin == null) return paths;
        if ("declarative".equalsIgnoreCase(plugin.runtime)) {
            paths.add(firstNonEmpty(plugin.entry, "ui.json"));
        }
        if (plugin.actions != null) {
            for (PluginAction action : plugin.actions) {
                if (action == null || !action.isDeclarativeAction()) continue;
                paths.add(firstNonEmpty(action.target, action.handler, plugin.entry, "ui.json"));
            }
        }
        return paths;
    }

    private static UiSummary inspectUiFile(File home, String relativePath) {
        UiSummary summary = new UiSummary(relativePath);
        if (TextUtils.isEmpty(relativePath)) {
            summary.problem = "UI path is empty.";
            return summary;
        }
        if (!isSafeRelativePluginPath(relativePath)) {
            summary.problem = "Unsafe UI path.";
            return summary;
        }
        try {
            File file = resolveFile(home, relativePath);
            if (!file.isFile()) {
                summary.problem = "UI file is missing.";
                return summary;
            }
            summary.bytes = file.length();
            String text = readUtf8(file);
            summary.validationProblem = PluginEditorDeclarativeUiValidator.runtimeProblem(text);
            JSONObject root = new JSONObject(text);
            summary.title = root.optString("title", "");
            summary.description = root.optString("description", "");
            summary.windowStyle = root.optString("windowStyle", "");
            summary.windowFit = root.optString("windowFit", "");
            JSONArray controls = root.optJSONArray("controls");
            if (controls == null) {
                summary.problem = "controls array is missing.";
                return summary;
            }
            inspectControls(controls, "controls", 0, summary);
            if (!TextUtils.isEmpty(summary.validationProblem)) summary.problem = summary.validationProblem;
        } catch (Throwable t) {
            summary.problem = "Unable to inspect UI file: " + safeMessage(t);
        }
        return summary;
    }

    private static void inspectControls(JSONArray controls, String path, int depth, UiSummary summary) {
        if (controls == null || summary == null || depth > 12) return;
        for (int i = 0; i < controls.length(); i++) {
            JSONObject control = controls.optJSONObject(i);
            if (control == null) continue;
            String type = control.optString("type", "label").trim().toLowerCase(Locale.US);
            summary.controlCount++;
            increment(summary.controlTypes, type);
            String id = control.optString("id", "").trim();
            if (!TextUtils.isEmpty(id) && summary.controlIds.size() < MAX_LISTED_IDS) {
                summary.controlIds.add(id);
            }
            inspectAction(control.optJSONObject("action"), summary);
            inspectAction(control.optJSONObject("onChange"), summary);
            if ("group".equals(type) || "section".equals(type)) {
                inspectControls(control.optJSONArray("controls"), path + "[" + i + "].controls", depth + 1, summary);
            } else if ("buttons".equals(type)) {
                JSONArray buttons = control.optJSONArray("buttons");
                if (buttons != null) {
                    for (int b = 0; b < buttons.length(); b++) {
                        JSONObject item = buttons.optJSONObject(b);
                        if (item != null) inspectAction(item.optJSONObject("action"), summary);
                    }
                }
            }
        }
    }

    private static void inspectAction(JSONObject action, UiSummary summary) {
        if (action == null || summary == null) return;
        String type = action.optString("type", "").trim().toLowerCase(Locale.US);
        if (TextUtils.isEmpty(type) || "none".equals(type)) return;
        summary.actionCount++;
        increment(summary.actionTypes, type);
        if ("shell".equals(type)) summary.shellActionCount++;
        if ("api".equals(type)) {
            String api = action.optString("name", "").trim();
            if (!TextUtils.isEmpty(api)) summary.apiNames.add(api);
        }
        if ("sequence".equals(type)) {
            JSONArray steps = action.optJSONArray("steps");
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) inspectAction(steps.optJSONObject(i), summary);
            }
        }
        inspectAction(action.optJSONObject("then"), summary);
    }

    private static void appendUiSummary(StringBuilder sb, UiSummary summary, char nl) {
        sb.append(nl).append("UI file: ").append(summary.relativePath).append(nl);
        if (!TextUtils.isEmpty(summary.problem)) {
            sb.append("• Status: failed — ").append(summary.problem).append(nl);
            return;
        }
        sb.append("• Status: ready").append(nl);
        sb.append("• Size: ").append(summary.bytes).append(" bytes").append(nl);
        if (!TextUtils.isEmpty(summary.title)) sb.append("• Title: ").append(summary.title).append(nl);
        if (!TextUtils.isEmpty(summary.description)) sb.append("• Description: ").append(summary.description).append(nl);
        if (!TextUtils.isEmpty(summary.windowStyle) || !TextUtils.isEmpty(summary.windowFit)) {
            sb.append("• UI window override: ")
                    .append(TextUtils.isEmpty(summary.windowStyle) ? "inherit" : summary.windowStyle)
                    .append("/")
                    .append(TextUtils.isEmpty(summary.windowFit) ? "inherit" : summary.windowFit)
                    .append(nl);
        }
        sb.append("• Controls: ").append(summary.controlCount).append(formatMap(summary.controlTypes)).append(nl);
        if (!summary.controlIds.isEmpty()) sb.append("• Control IDs: ").append(join(summary.controlIds)).append(nl);
        sb.append("• Actions: ").append(summary.actionCount).append(formatMap(summary.actionTypes)).append(nl);
        if (summary.shellActionCount > 0) {
            sb.append("• Shell actions: ").append(summary.shellActionCount)
                    .append(" gated by script runtime policy; blocked in Plugin Editor Preview UI.").append(nl);
        }
        if (!summary.apiNames.isEmpty()) sb.append("• Host APIs: ").append(join(summary.apiNames)).append(nl);
    }

    private static void increment(Map<String, Integer> map, String key) {
        if (map == null || TextUtils.isEmpty(key)) return;
        Integer old = map.get(key);
        map.put(key, old == null ? 1 : old + 1);
    }

    private static String formatMap(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" (");
        boolean first = true;
        for (Map.Entry<String, Integer> e : values.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append("=").append(e.getValue());
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    private static String join(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String value : values) {
            if (!first) sb.append(", ");
            sb.append(value);
            first = false;
        }
        return sb.toString();
    }

    private static boolean isSafeRelativePluginPath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String p = path.replace('\\', '/');
        if (p.startsWith("/") || p.contains(":") || p.contains("//")) return false;
        String[] parts = p.split("/");
        for (String part : parts) {
            if (TextUtils.isEmpty(part) || ".".equals(part) || "..".equals(part)) return false;
        }
        return true;
    }

    private static File resolveFile(File home, String relativePath) throws Exception {
        File file = new File(home, relativePath);
        String root = home.getCanonicalPath() + File.separator;
        String path = file.getCanonicalPath();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Path escapes plugin folder: " + relativePath);
        return file;
    }

    private static String readUtf8(File file) throws Exception {
        if (file.length() > MAX_UI_BYTES) {
            throw new IllegalArgumentException("UI file is too large for readiness inspection: " + file.length() + " bytes");
        }
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), MAX_UI_BYTES))) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.toString() : msg;
    }

    private static final class UiSummary {
        final String relativePath;
        final Map<String, Integer> controlTypes = new LinkedHashMap<>();
        final Map<String, Integer> actionTypes = new LinkedHashMap<>();
        final Set<String> controlIds = new LinkedHashSet<>();
        final Set<String> apiNames = new LinkedHashSet<>();
        long bytes;
        String title = "";
        String description = "";
        String windowStyle = "";
        String windowFit = "";
        String validationProblem = "";
        String problem = "";
        int controlCount;
        int actionCount;
        int shellActionCount;

        UiSummary(String relativePath) {
            this.relativePath = TextUtils.isEmpty(relativePath) ? "ui.json" : relativePath;
        }
    }
}
