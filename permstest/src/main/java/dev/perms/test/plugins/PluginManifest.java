package dev.perms.test.plugins;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated metadata for one staged PermsTest plugin directory. */
public final class PluginManifest {
    public static final String FILE_NAME = "plugin.json";
    public static final String SCHEMA = "dev.perms.test.plugin";
    public static final int SUPPORTED_API_VERSION = 1;

    public final String schema;
    public final int apiVersion;
    public final String id;
    public final String name;
    public final String version;
    public final String author;
    public final String description;
    public final String comments;
    public final String icon;
    public final String runtime;
    public final String entry;
    public final String windowStyle;
    public final String windowFit;
    public final List<String> capabilities;
    public final File homeDir;
    public final List<PluginAction> actions;

    private PluginManifest(String schema,
                           int apiVersion,
                           String id,
                           String name,
                           String version,
                           String author,
                           String description,
                           String comments,
                           String icon,
                           String runtime,
                           String entry,
                           String windowStyle,
                           String windowFit,
                           List<String> capabilities,
                           File homeDir,
                           List<PluginAction> actions) {
        this.schema = schema == null ? "" : schema;
        this.apiVersion = apiVersion;
        this.id = id == null ? "" : id;
        this.name = TextUtils.isEmpty(name) ? this.id : name;
        this.version = TextUtils.isEmpty(version) ? "unknown" : version;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.comments = comments == null ? "" : comments;
        this.icon = icon == null ? "" : icon.trim();
        this.runtime = TextUtils.isEmpty(runtime) ? "trusted_native" : runtime.trim();
        this.entry = entry == null ? "" : entry.trim();
        this.windowStyle = TextUtils.isEmpty(windowStyle) ? "compact" : windowStyle.trim();
        this.windowFit = TextUtils.isEmpty(windowFit) ? "current" : windowFit.trim();
        this.capabilities = capabilities == null ? Collections.emptyList() : Collections.unmodifiableList(capabilities);
        this.homeDir = homeDir;
        this.actions = actions == null ? Collections.emptyList() : Collections.unmodifiableList(actions);
    }

    public static PluginManifest fromDirectory(File directory) throws Exception {
        if (directory == null || !directory.isDirectory()) {
            throw new IllegalArgumentException("Plugin directory is missing");
        }
        File manifestFile = new File(directory, FILE_NAME);
        if (!manifestFile.isFile()) {
            throw new IllegalArgumentException("Missing " + FILE_NAME);
        }
        return fromFile(manifestFile, directory);
    }

    public static PluginManifest fromFile(File manifestFile, File homeDir) throws Exception {
        if (manifestFile == null || !manifestFile.isFile()) {
            throw new IllegalArgumentException("Missing plugin manifest");
        }
        JSONObject root = new JSONObject(readUtf8(manifestFile));
        String schema = root.optString("schema", SCHEMA);
        int apiVersion = root.optInt("apiVersion", 1);
        if (!SCHEMA.equals(schema)) {
            throw new IllegalArgumentException("Unsupported plugin schema: " + schema);
        }
        String id = root.optString("id", "").trim();
        if (!isSafeId(id)) {
            throw new IllegalArgumentException("Invalid plugin id: " + id);
        }
        if (apiVersion <= 0 || apiVersion > SUPPORTED_API_VERSION) {
            throw new IllegalArgumentException("Unsupported plugin apiVersion: " + apiVersion);
        }

        String runtime = root.optString("runtime", "trusted_native").trim();
        if (TextUtils.isEmpty(runtime)) runtime = "trusted_native";
        ArrayList<PluginAction> actions = new ArrayList<>();
        JSONArray array = root.optJSONArray("actions");
        if (array != null) {
            String defaultActionType = actionTypeForRuntime(runtime);
            for (int i = 0; i < array.length(); i++) {
                JSONObject actionJson = array.optJSONObject(i);
                if (actionJson == null) {
                    throw new IllegalArgumentException("Plugin action " + (i + 1) + " is not an object");
                }
                PluginAction action = PluginAction.fromJson(actionJson, defaultActionType);
                if (action == null) {
                    throw new IllegalArgumentException("Plugin action " + (i + 1)
                            + " is missing a safe id or dispatch target");
                }
                actions.add(action);
            }
        }
        return new PluginManifest(
                schema,
                apiVersion,
                id,
                root.optString("name", id),
                root.optString("version", "unknown"),
                root.optString("author", ""),
                root.optString("description", ""),
                root.optString("comments", ""),
                root.optString("icon", ""),
                runtime,
                root.optString("entry", ""),
                root.optString("windowStyle", "compact"),
                root.optString("windowFit", "current"),
                readStringArray(root.optJSONArray("capabilities")),
                homeDir,
                actions);
    }

    private static String actionTypeForRuntime(String runtime) {
        if ("declarative".equals(runtime)) return "declarative_ui";
        if ("script".equals(runtime)) return "shell";
        if ("trusted_dex".equals(runtime)) return "trusted_dex";
        return "native";
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

    public static boolean isSafeId(String value) {
        if (TextUtils.isEmpty(value)) return false;
        if (value.length() > 80) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!ok) return false;
        }
        return true;
    }

    private static String readUtf8(File file) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), 256 * 1024))) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
