package dev.perms.test.plugins;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed action entry from a staged PermsTest plugin manifest. */
public final class PluginAction {
    public final String id;
    public final String title;
    public final String description;
    public final String type;
    public final String handler;
    public final String syntax;
    public final String target;
    public final String command;
    public final String script;
    public final String presentation;
    public final String windowStyle;
    public final String windowFit;
    public final List<String> requires;
    public final JSONObject raw;

    private PluginAction(String id,
                         String title,
                         String description,
                         String type,
                         String handler,
                         String syntax,
                         String target,
                         String command,
                         String script,
                         String presentation,
                         String windowStyle,
                         String windowFit,
                         List<String> requires,
                         JSONObject raw) {
        this.id = id == null ? "" : id.trim();
        this.title = TextUtils.isEmpty(title) ? this.id : title.trim();
        this.description = description == null ? "" : description.trim();
        this.type = TextUtils.isEmpty(type) ? "native" : type.trim();
        this.handler = handler == null ? "" : handler.trim();
        this.syntax = optionalOverride(syntax, "default", "inherit");
        this.target = target == null ? "" : target.trim();
        this.command = command == null ? "" : command;
        this.script = script == null ? "" : script.trim();
        this.presentation = optionalOverride(presentation, "default", "inherit");
        this.windowStyle = optionalOverride(windowStyle, "inherit", "default");
        this.windowFit = optionalOverride(windowFit, "inherit", "default");
        this.requires = requires == null ? Collections.emptyList() : Collections.unmodifiableList(requires);
        this.raw = raw;
    }


    private static String optionalOverride(String value, String... defaults) {
        String normalized = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(normalized)) return "";
        if (defaults != null) {
            for (String fallback : defaults) {
                if (!TextUtils.isEmpty(fallback) && fallback.equalsIgnoreCase(normalized)) return "";
            }
        }
        return normalized;
    }

    static PluginAction fromJson(JSONObject object) {
        return fromJson(object, "native");
    }

    static PluginAction fromJson(JSONObject object, String defaultType) {
        if (object == null) return null;
        String id = object.optString("id", "").trim();
        String normalizedDefaultType = TextUtils.isEmpty(defaultType) ? "native" : defaultType.trim();
        String type = object.optString("type", normalizedDefaultType).trim();
        if (TextUtils.isEmpty(type)) type = normalizedDefaultType;
        String handler = object.optString("handler", "").trim();
        String target = object.optString("target", "").trim();
        String command = object.optString("command", "");
        String script = object.optString("script", "").trim();
        String presentation = object.optString("presentation", object.optString("display", "")).trim();
        String windowStyle = object.optString("windowStyle", "").trim();
        String windowFit = object.optString("windowFit", "").trim();
        ArrayList<String> requires = readStringArray(object.optJSONArray("requires"));

        if (!PluginManifest.isSafeId(id)) return null;
        if (TextUtils.isEmpty(handler) && TextUtils.isEmpty(target)
                && TextUtils.isEmpty(command) && TextUtils.isEmpty(script)) {
            return null;
        }

        return new PluginAction(
                id,
                object.optString("title", id),
                object.optString("description", ""),
                type,
                handler,
                object.optString("syntax", ""),
                target,
                command,
                script,
                presentation,
                windowStyle,
                windowFit,
                requires,
                object);
    }

    private static ArrayList<String> readStringArray(JSONArray array) {
        ArrayList<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!TextUtils.isEmpty(value) && !values.contains(value)) values.add(value);
        }
        return values;
    }

    public boolean isNativeAction() {
        return TextUtils.isEmpty(type)
                || "native".equalsIgnoreCase(type)
                || "trusted_native".equalsIgnoreCase(type);
    }

    public boolean isDeclarativeAction() {
        return "declarative".equalsIgnoreCase(type)
                || "declarative_ui".equalsIgnoreCase(type)
                || "ui".equalsIgnoreCase(type);
    }

    public boolean isScriptAction() {
        return "script".equalsIgnoreCase(type)
                || "shell".equalsIgnoreCase(type);
    }

    public boolean isTrustedDexAction() {
        return "trusted_dex".equalsIgnoreCase(type)
                || "dex".equalsIgnoreCase(type);
    }

    public boolean isLogOnlyPresentation() {
        return "log".equalsIgnoreCase(presentation)
                || "output".equalsIgnoreCase(presentation)
                || "main_output".equalsIgnoreCase(presentation);
    }

    public boolean isWindowPresentation() {
        return "window".equalsIgnoreCase(presentation)
                || "panel".equalsIgnoreCase(presentation)
                || "large".equalsIgnoreCase(presentation);
    }

    public boolean isDialogPresentation() {
        return "dialog".equalsIgnoreCase(presentation)
                || "viewer".equalsIgnoreCase(presentation);
    }
}
