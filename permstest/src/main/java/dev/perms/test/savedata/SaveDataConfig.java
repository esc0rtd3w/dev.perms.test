package dev.perms.test.savedata;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * JSON-backed save-data edit definition.
 *
 * Configs live under /sdcard/dev.perms.test/save_data/[package_name] and describe the
 * target save path plus section-scoped byte replacements. The schema is intentionally
 * generic so the Tools tab can support other app save formats without adding app-specific
 * logic to MainActivity.
 */
public final class SaveDataConfig {
    public static final String SCHEMA = "perms_test_save_data_editor";
    public static final int VERSION = 1;

    public final String name;
    public final String packageName;
    public final String saveDataPathTemplate;
    public final String playerIdGlob;
    public final String profileParameterName;
    public final boolean showProfileField;
    public final boolean autoDetectProfileId;
    public final boolean defaultApplyToAllInstances;
    public final List<Preset> presets;

    private SaveDataConfig(String name,
                           String packageName,
                           String saveDataPathTemplate,
                           String playerIdGlob,
                           String profileParameterName,
                           boolean showProfileField,
                           boolean autoDetectProfileId,
                           boolean defaultApplyToAllInstances,
                           List<Preset> presets) {
        this.name = name == null ? "" : name;
        this.packageName = packageName == null ? "" : packageName;
        this.saveDataPathTemplate = saveDataPathTemplate == null ? "" : saveDataPathTemplate;
        this.playerIdGlob = playerIdGlob == null ? "" : playerIdGlob;
        this.profileParameterName = TextUtils.isEmpty(profileParameterName) ? "Profile parameter" : profileParameterName;
        this.showProfileField = showProfileField;
        this.autoDetectProfileId = autoDetectProfileId;
        this.defaultApplyToAllInstances = defaultApplyToAllInstances;
        this.presets = presets == null ? Collections.emptyList() : Collections.unmodifiableList(presets);
    }

    public static SaveDataConfig parse(String json) throws Exception {
        JSONObject root = new JSONObject(json == null ? "{}" : json);
        String packageName = root.optString("package_name", "");
        String name = root.optString("name", packageName);
        String pathTemplate = root.optString("save_data_path_template", root.optString("save_data_path", ""));
        String playerGlob = root.optString("player_id_glob", "");
        if (TextUtils.isEmpty(playerGlob)) {
            JSONObject player = root.optJSONObject("player_id_source");
            if (player != null) playerGlob = player.optString("glob", "");
        }
        String profileLabel = root.optString("profile_parameter_name", root.optString("player_id_label", "Profile parameter"));
        boolean showProfileField = root.optBoolean("show_profile_field", false);
        boolean autoDetectProfile = root.optBoolean("auto_detect_profile_id", !TextUtils.isEmpty(playerGlob));
        boolean applyAll = root.optBoolean("default_apply_to_all_instances",
                root.optBoolean("apply_to_all_instances", true));

        ArrayList<Preset> presets = new ArrayList<>();
        JSONArray arr = root.optJSONArray("presets");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p == null) continue;
                Preset preset = Preset.parse(p, i, applyAll);
                if (preset != null) presets.add(preset);
            }
        }
        return new SaveDataConfig(name, packageName, pathTemplate, playerGlob, profileLabel, showProfileField, autoDetectProfile, applyAll, presets);
    }

    public String resolveSavePath(String playerId) {
        String path = saveDataPathTemplate == null ? "" : saveDataPathTemplate;
        String id = playerId == null ? "" : playerId.trim();
        path = path.replace("{player_id}", id)
                .replace("{profile_id}", id)
                .replace("${PLAYER_ID}", id)
                .replace("${PROFILE_ID}", id)
                .replace("[PLAYER_ID]", id)
                .replace("[PROFILE_ID]", id);
        return path;
    }

    public boolean requiresProfileId() {
        String path = saveDataPathTemplate == null ? "" : saveDataPathTemplate;
        return path.contains("{player_id}")
                || path.contains("{profile_id}")
                || path.contains("${PLAYER_ID}")
                || path.contains("${PROFILE_ID}")
                || path.contains("[PLAYER_ID]")
                || path.contains("[PROFILE_ID]");
    }

    public List<String> presetNames() {
        ArrayList<String> names = new ArrayList<>();
        for (Preset p : presets) {
            if (p != null && !TextUtils.isEmpty(p.name)) names.add(p.name);
        }
        return names;
    }

    public Preset findPreset(String name) {
        if (TextUtils.isEmpty(name)) return null;
        for (Preset p : presets) {
            if (p != null && name.equals(p.name)) return p;
        }
        return null;
    }

    public static final class Preset {
        public final int index;
        public final String name;
        public final boolean enabled;
        public final boolean applyToAllInstances;
        public final List<Edit> edits;

        private Preset(int index, String name, boolean enabled, boolean applyToAllInstances, List<Edit> edits) {
            this.index = index;
            this.name = name == null ? "" : name;
            this.enabled = enabled;
            this.applyToAllInstances = applyToAllInstances;
            this.edits = edits == null ? Collections.emptyList() : Collections.unmodifiableList(edits);
        }

        static Preset parse(JSONObject obj, int presetIndex, boolean defaultApplyAll) {
            if (obj == null) return null;
            boolean applyAll = obj.optBoolean("apply_to_all_instances", defaultApplyAll);
            ArrayList<Edit> edits = new ArrayList<>();
            JSONArray arr = obj.optJSONArray("edits");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.optJSONObject(i);
                    Edit edit = Edit.parse(e, i, applyAll);
                    if (edit != null) edits.add(edit);
                }
            }
            return new Preset(presetIndex, obj.optString("name", "Preset"), obj.optBoolean("enabled", true), applyAll, edits);
        }
    }

    public static final class Edit {
        public final int index;
        public final String name;
        public final String sectionStartHex;
        public final String sectionEndHex;
        public final String sectionStartLabel;
        public final String sectionEndLabel;
        public final String originalHex;
        public final String patchedHex;
        public final int maxChanges;
        public final boolean applyToAllInstances;

        private Edit(int index,
                     String name,
                     String sectionStartHex,
                     String sectionEndHex,
                     String sectionStartLabel,
                     String sectionEndLabel,
                     String originalHex,
                     String patchedHex,
                     int maxChanges,
                     boolean applyToAllInstances) {
            this.index = index;
            this.name = name == null ? "" : name;
            this.sectionStartHex = cleanHex(sectionStartHex);
            this.sectionEndHex = cleanHex(sectionEndHex);
            this.sectionStartLabel = sectionStartLabel == null ? "" : sectionStartLabel;
            this.sectionEndLabel = sectionEndLabel == null ? "" : sectionEndLabel;
            this.originalHex = cleanHex(originalHex);
            this.patchedHex = cleanHex(patchedHex);
            this.maxChanges = maxChanges <= 0 ? 99999 : maxChanges;
            this.applyToAllInstances = applyToAllInstances;
        }

        static Edit parse(JSONObject obj, int editIndex, boolean defaultApplyAll) {
            if (obj == null) return null;
            String startAscii = obj.optString("section_start_ascii", "");
            String endAscii = obj.optString("section_end_ascii", "");
            String startHex = obj.optString("section_start_hex", "");
            String endHex = obj.optString("section_end_hex", "");
            if (TextUtils.isEmpty(startHex)) startHex = asciiToHex(startAscii);
            if (TextUtils.isEmpty(endHex)) endHex = asciiToHex(endAscii);
            String original = obj.optString("original_hex", "");
            String patched = obj.optString("patched_hex", "");
            if (TextUtils.isEmpty(original) || TextUtils.isEmpty(patched)) return null;
            return new Edit(editIndex,
                    obj.optString("name", "Edit " + (editIndex + 1)),
                    startHex,
                    endHex,
                    startAscii,
                    endAscii,
                    original,
                    patched,
                    obj.optInt("max_changes", 99999),
                    obj.optBoolean("apply_to_all_instances", defaultApplyAll));
        }
    }

    static String cleanHex(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
    }

    private static String asciiToHex(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xff));
        return sb.toString();
    }
}
