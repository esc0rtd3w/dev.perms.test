package dev.perms.test.savedata.builtin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

import dev.perms.test.savedata.SaveDataConfig;

/** Built-in Save Data Editor config for Walkabout Mini Golf profile unlock patches. */
public final class WalkaboutMiniGolfSaveDataConfig {
    public static final String PACKAGE_NAME = "com.mightyCoconut.walkaboutMiniGolf";
    public static final String CONFIG_FILE_NAME = "walkabout_mini_golf_unlocks.json";
    public static final String SAVE_PATH_TEMPLATE = "/sdcard/Android/data/com.MightyCoconut.WalkaboutMiniGolf/files/Profiles/Oculus/{player_id}/Profile_Default.data";
    public static final String PLAYER_ID_GLOB = "/sdcard/Android/data/com.MightyCoconut.WalkaboutMiniGolf/files/Profiles/Oculus/*";

    private WalkaboutMiniGolfSaveDataConfig() {}

    public static String defaultJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SaveDataConfig.SCHEMA);
            root.put("version", SaveDataConfig.VERSION);
            root.put("package_name", PACKAGE_NAME);
            root.put("package_folder", PACKAGE_NAME);
            root.put("name", "Walkabout Mini Golf - Save Unlocks");
            root.put("save_data_path_template", SAVE_PATH_TEMPLATE);
            root.put("default_apply_to_all_instances", true);
            JSONObject player = new JSONObject();
            player.put("type", "directory_glob");
            player.put("glob", PLAYER_ID_GLOB);
            root.put("player_id_source", player);
            root.put("notes", "Section-scoped byte edits converted from the original WMG unlocker/payloads. Scan matches before applying if you want to patch selected instances only.");

            JSONArray presets = new JSONArray();
            presets.put(preset("Unlock Balls", true,
                    edit("Balls HasValue -> true", "BallsFound", "BallPositions", "48617356616C7565000009", "48617356616C7565000109"),
                    edit("Balls Value clear FF", "BallsFound", "BallPositions", "56616C756500FFFFFFFFFFFFFFFF08", "56616C756500000000000000000008")));
            presets.put(preset("Unlock Putters", true,
                    edit("Putters Value -> true", "PuttersUnlocked", "CourseData", "56616C7565000008", "56616C7565000108"),
                    edit("Putters HasValue -> true", "PuttersUnlocked", "CourseData", "48617356616C7565000009", "48617356616C7565000109")));
            root.put("presets", presets);
            return root.toString(2);
        } catch (Throwable ignored) {
            return "{\n"
                    + "  \"schema\": \"" + SaveDataConfig.SCHEMA + "\",\n"
                    + "  \"version\": 1,\n"
                    + "  \"package_name\": \"" + PACKAGE_NAME + "\",\n"
                    + "  \"save_data_path_template\": \"" + SAVE_PATH_TEMPLATE + "\",\n"
                    + "  \"default_apply_to_all_instances\": true,\n"
                    + "  \"presets\": []\n"
                    + "}\n";
        }
    }

    private static JSONObject preset(String name, boolean enabled, JSONObject... edits) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("enabled", enabled);
        obj.put("apply_to_all_instances", true);
        JSONArray arr = new JSONArray();
        if (edits != null) {
            for (JSONObject edit : edits) arr.put(edit);
        }
        obj.put("edits", arr);
        return obj;
    }

    private static JSONObject edit(String name, String start, String end, String original, String patched) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("section_start_ascii", start);
        obj.put("section_end_ascii", end);
        obj.put("section_start_hex", asciiToHex(start));
        obj.put("section_end_hex", asciiToHex(end));
        obj.put("original_hex", original);
        obj.put("patched_hex", patched);
        obj.put("apply_to_all_instances", true);
        obj.put("max_changes", 99999);
        return obj;
    }

    private static String asciiToHex(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xff));
        return sb.toString();
    }
}
