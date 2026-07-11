package dev.perms.test.plugins.editor;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.perms.test.plugins.PluginManifest;

/** Validation shared by Plugin Editor preview, save, validate, and package paths. */
public final class PluginEditorDeclarativeUiValidator {
    private static final Set<String> CONTROL_TYPES = setOf(
            "label", "text", "input", "multiline", "dropdown", "checkbox", "output",
            "button", "divider", "group", "section", "buttons");
    private static final Set<String> ACTION_TYPES = setOf(
            "toast", "settext", "appendtext", "clear", "backspace", "shell", "api", "sequence");
    private static final Set<String> API_NAMES = setOf(
            "calculator.evaluateinteger", "converter.texttobytes", "converter.bytestotext",
            "text.uppercase", "text.lowercase", "text.trim", "text.reverse", "text.length", "text.wordcount", "text.isblank", "text.linecount",
            "text.contains", "text.replace", "json.pretty", "json.minify", "url.encode", "url.decode",
            "hash.sha256", "encoding.base64encode", "encoding.base64decode", "encoding.hexencode", "encoding.hexdecode");

    private PluginEditorDeclarativeUiValidator() {
    }

    public static String runtimeProblem(String uiJson) {
        if (TextUtils.isEmpty(uiJson) || TextUtils.isEmpty(uiJson.trim())) return "ui.json is empty.";
        try {
            JSONObject root = new JSONObject(uiJson);
            JSONArray controls = root.optJSONArray("controls");
            if (controls == null) return "ui.json needs a controls array before preview, save, or packaging.";
            ControlIndex index = new ControlIndex();
            String problem = indexControls(controls, "controls", 0, index);
            if (!TextUtils.isEmpty(problem)) return problem;
            return validateControls(controls, "controls", 0, index);
        } catch (Throwable t) {
            return "ui.json is invalid: " + safeMessage(t);
        }
    }

    private static String indexControls(JSONArray controls, String path, int depth, ControlIndex index) {
        if (controls == null) return path + " must be an array.";
        if (depth > 8) return path + " is nested too deeply.";
        for (int i = 0; i < controls.length(); i++) {
            JSONObject control = controls.optJSONObject(i);
            String label = path + "[" + i + "]";
            if (control == null) return label + " must be an object.";
            String type = normalizedControlType(control);
            if (!CONTROL_TYPES.contains(type)) return label + " has unsupported control type: " + type;
            String id = control.optString("id", "").trim();
            if (!TextUtils.isEmpty(id)) {
                if (!PluginManifest.isSafeId(id)) return label + " has unsafe control id: " + id;
                String previous = index.pathsById.put(id, label);
                if (!TextUtils.isEmpty(previous)) return label + " duplicates control id " + id + " already used by " + previous + ".";
                index.typesById.put(id, type);
            }
            if ("group".equals(type) || "section".equals(type)) {
                if (control.has("controls")) {
                    JSONArray nested = control.optJSONArray("controls");
                    if (nested == null) return label + ".controls must be an array.";
                    String problem = indexControls(nested, label + ".controls", depth + 1, index);
                    if (!TextUtils.isEmpty(problem)) return problem;
                }
            } else if ("buttons".equals(type)) {
                JSONArray buttons = control.optJSONArray("buttons");
                if (buttons == null) return label + " needs a buttons array.";
                for (int b = 0; b < buttons.length(); b++) {
                    JSONObject item = buttons.optJSONObject(b);
                    String itemLabel = label + ".buttons[" + b + "]";
                    if (item == null) return itemLabel + " must be an object.";
                }
            }
        }
        return "";
    }

    private static String validateControls(JSONArray controls, String path, int depth, ControlIndex index) {
        if (controls == null) return path + " must be an array.";
        if (depth > 8) return path + " is nested too deeply.";
        for (int i = 0; i < controls.length(); i++) {
            JSONObject control = controls.optJSONObject(i);
            String label = path + "[" + i + "]";
            if (control == null) return label + " must be an object.";
            String type = normalizedControlType(control);
            if (!CONTROL_TYPES.contains(type)) return label + " has unsupported control type: " + type;
            if ("dropdown".equals(type) && control.has("values")) {
                JSONArray values = control.optJSONArray("values");
                if (values == null) return label + ".values must be an array.";
                for (int v = 0; v < values.length(); v++) {
                    if (values.opt(v) == null || values.opt(v) == JSONObject.NULL) return label + ".values[" + v + "] must be text.";
                }
            }
            if ("group".equals(type) || "section".equals(type)) {
                if (control.has("controls")) {
                    String problem = validateControls(control.optJSONArray("controls"), label + ".controls", depth + 1, index);
                    if (!TextUtils.isEmpty(problem)) return problem;
                }
            } else if ("buttons".equals(type)) {
                JSONArray buttons = control.optJSONArray("buttons");
                if (buttons == null) return label + " needs a buttons array.";
                for (int b = 0; b < buttons.length(); b++) {
                    JSONObject item = buttons.optJSONObject(b);
                    String itemLabel = label + ".buttons[" + b + "]";
                    if (item == null) return itemLabel + " must be an object.";
                    if (TextUtils.isEmpty(item.optString("text", "").trim())) return itemLabel + " needs text.";
                    String problem = actionProblem(item.opt("action"), itemLabel + ".action", index, 0);
                    if (!TextUtils.isEmpty(problem)) return problem;
                }
            } else {
                String problem = actionProblem(control.opt("action"), label + ".action", index, 0);
                if (!TextUtils.isEmpty(problem)) return problem;
                problem = actionProblem(control.opt("onChange"), label + ".onChange", index, 0);
                if (!TextUtils.isEmpty(problem)) return problem;
            }
        }
        return "";
    }

    private static String actionProblem(Object rawAction, String label, ControlIndex index, int depth) {
        if (rawAction == null || rawAction == JSONObject.NULL) return "";
        if (!(rawAction instanceof JSONObject)) return label + " must be an object.";
        if (depth > 8) return label + " is nested too deeply.";
        JSONObject action = (JSONObject) rawAction;
        String type = action.optString("type", "").trim().toLowerCase(Locale.US);
        if (TextUtils.isEmpty(type) || "none".equals(type)) return "";
        if (!ACTION_TYPES.contains(type)) return label + " uses an unsupported action type: " + action.optString("type", "");
        if ("toast".equals(type)) {
            if (TextUtils.isEmpty(action.optString("message", "").trim())) return label + " requires message.";
        } else if ("settext".equals(type) || "appendtext".equals(type)) {
            String target = action.optString("target", "").trim();
            if (TextUtils.isEmpty(target) || TextUtils.isEmpty(action.optString("value", "").trim())) {
                return label + " requires target and value.";
            }
            String problem = targetProblem(target, label + " target", index, true);
            if (!TextUtils.isEmpty(problem)) return problem;
        } else if ("clear".equals(type) || "backspace".equals(type)) {
            String problem = targetProblem(action.optString("target", "").trim(), label + " target", index, true);
            if (!TextUtils.isEmpty(problem)) return problem;
        } else if ("shell".equals(type)) {
            if (TextUtils.isEmpty(action.optString("command", "").trim())) return label + " requires command.";
            String output = action.optString("output", "").trim();
            if (!TextUtils.isEmpty(output)) {
                String problem = targetProblem(output, label + " output", index, true);
                if (!TextUtils.isEmpty(problem)) return problem;
            }
        } else if ("api".equals(type)) {
            String problem = apiProblem(action, label, index);
            if (!TextUtils.isEmpty(problem)) return problem;
        } else if ("sequence".equals(type)) {
            JSONArray steps = action.optJSONArray("steps");
            if (steps == null || steps.length() == 0) return label + " requires a non-empty steps array.";
            for (int i = 0; i < steps.length(); i++) {
                String problem = actionProblem(steps.opt(i), label + " step " + (i + 1), index, depth + 1);
                if (!TextUtils.isEmpty(problem)) return problem;
            }
        }
        if (action.has("then")) {
            if (!supportsThen(type)) return label + " uses then on an action type that does not run nested then actions.";
            String problem = actionProblem(action.opt("then"), label + " then", index, depth + 1);
            if (!TextUtils.isEmpty(problem)) return problem;
        }
        return "";
    }

    private static String apiProblem(JSONObject action, String label, ControlIndex index) {
        String name = action.optString("name", "").trim().toLowerCase(Locale.US);
        if (!API_NAMES.contains(name)) return label + " requires a supported API name.";
        if ("calculator.evaluateinteger".equals(name)) {
            String problem = targetProblem(action.optString("input", "input"), label + " input", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            JSONObject outputs = action.optJSONObject("outputs");
            String[] keys = {"hex", "dec", "oct", "bin"};
            for (String key : keys) {
                problem = targetProblem(outputs == null ? key : outputs.optString(key, key), label + " output " + key, index, true);
                if (!TextUtils.isEmpty(problem)) return problem;
            }
            if (action.has("status")) {
                problem = targetProblem(action.optString("status", "status"), label + " status", index, true);
                if (!TextUtils.isEmpty(problem)) return problem;
            }
            return "";
        }
        if ("converter.texttobytes".equals(name)) {
            String problem = targetProblem(action.optString("input", "text"), label + " input", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            return targetProblem(action.optString("output", "values"), label + " output", index, true);
        }
        if ("converter.bytestotext".equals(name)) {
            String problem = targetProblem(action.optString("input", "values"), label + " input", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            return targetProblem(action.optString("output", "text"), label + " output", index, true);
        }
        if ("text.contains".equals(name)) {
            String problem = targetProblem(action.optString("input", "input"), label + " input", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            problem = targetProblem(action.optString("query", "query"), label + " query", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            return targetProblem(action.optString("output", "output"), label + " output", index, true);
        }
        if ("text.replace".equals(name)) {
            String problem = targetProblem(action.optString("input", "input"), label + " input", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            problem = targetProblem(action.optString("search", "search"), label + " search", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            problem = targetProblem(action.optString("replacement", "replacement"), label + " replacement", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            return targetProblem(action.optString("output", "output"), label + " output", index, true);
        }
        if (isSimpleInputOutputApi(name)) {
            String problem = targetProblem(action.optString("input", "input"), label + " input", index, false);
            if (!TextUtils.isEmpty(problem)) return problem;
            return targetProblem(action.optString("output", "output"), label + " output", index, true);
        }
        return "";
    }

    private static boolean isSimpleInputOutputApi(String name) {
        return "text.uppercase".equals(name) || "text.lowercase".equals(name)
                || "text.trim".equals(name) || "text.reverse".equals(name)
                || "text.length".equals(name) || "text.wordcount".equals(name)
                || "text.isblank".equals(name) || "text.linecount".equals(name)
                || "json.pretty".equals(name) || "json.minify".equals(name)
                || "url.encode".equals(name) || "url.decode".equals(name)
                || "hash.sha256".equals(name)
                || "encoding.base64encode".equals(name) || "encoding.base64decode".equals(name)
                || "encoding.hexencode".equals(name) || "encoding.hexdecode".equals(name);
    }

    private static String targetProblem(String target, String label, ControlIndex index, boolean textTarget) {
        String safeTarget = target == null ? "" : target.trim();
        if (TextUtils.isEmpty(safeTarget)) return label + " is required.";
        if (!PluginManifest.isSafeId(safeTarget)) return label + " has unsafe control id: " + safeTarget;
        String type = index.typesById.get(safeTarget);
        if (TextUtils.isEmpty(type)) return label + " does not match any control ID: " + safeTarget;
        if (textTarget && !isTextTargetType(type)) return label + " points to non-text control " + safeTarget + " (" + type + ").";
        return "";
    }

    private static boolean isTextTargetType(String type) {
        return "label".equals(type) || "text".equals(type) || "input".equals(type)
                || "multiline".equals(type) || "dropdown".equals(type) || "checkbox".equals(type)
                || "output".equals(type) || "button".equals(type);
    }

    private static boolean supportsThen(String type) {
        return "appendtext".equals(type) || "clear".equals(type)
                || "backspace".equals(type) || "api".equals(type);
    }

    private static String normalizedControlType(JSONObject control) {
        return control == null ? "label" : control.optString("type", "label").trim().toLowerCase(Locale.US);
    }

    private static Set<String> setOf(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? "" : t.getMessage();
        return TextUtils.isEmpty(msg) ? String.valueOf(t) : msg;
    }

    private static final class ControlIndex {
        final Map<String, String> pathsById = new LinkedHashMap<>();
        final Map<String, String> typesById = new LinkedHashMap<>();
    }
}
