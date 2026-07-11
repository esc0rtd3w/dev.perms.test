package dev.perms.test.shell;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON import/export helpers for Shell tab custom commands.
 */
public final class CustomCommandJson {
    private CustomCommandJson() {
    }

    public static ArrayList<CustomCommand> parse(String text) {
        ArrayList<CustomCommand> out = new ArrayList<>();
        try {
            if (text == null) return out;
            String t = text.trim();
            if (t.isEmpty()) return out;

            JSONArray arr = null;
            if (t.startsWith("[")) {
                arr = new JSONArray(t);
            } else if (t.startsWith("{")) {
                JSONObject o = new JSONObject(t);
                arr = o.optJSONArray("commands");
                if (arr == null) arr = o.optJSONArray("custom_commands");
                if (arr == null) arr = o.optJSONArray("items");
            }

            if (arr == null) return out;

            int order = 0;
            for (int i = 0; i < arr.length(); i++) {
                Object it = arr.opt(i);
                if (it instanceof JSONObject) {
                    CustomCommand cc = CustomCommand.fromJson((JSONObject) it, order++);
                    if (cc.cmd != null) cc.cmd = cc.cmd.trim();
                    if (cc.cmd != null && !cc.cmd.isEmpty() && !containsCommand(out, cc.cmd)) {
                        out.add(cc);
                    }
                } else {
                    String s = arr.optString(i, "");
                    if (s != null) {
                        s = s.trim();
                        if (!s.isEmpty() && !containsCommand(out, s)) {
                            out.add(new CustomCommand("", s, false, order++));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static String toPrefsJson(List<CustomCommand> commands) {
        try {
            JSONArray arr = new JSONArray();
            if (commands != null) {
                for (CustomCommand cc : commands) {
                    if (cc == null) continue;
                    String cmd = (cc.cmd == null) ? "" : cc.cmd.trim();
                    if (cmd.isEmpty()) continue;
                    arr.put(cc.toPrefsJson());
                }
            }
            return arr.toString();
        } catch (Throwable ignored) {
            return "[]";
        }
    }

    public static String toExportJson(List<CustomCommand> commands) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("exported_at", System.currentTimeMillis());

            JSONArray arr = new JSONArray();
            if (commands != null) {
                for (CustomCommand cc : commands) {
                    if (cc == null) continue;
                    String cmd = (cc.cmd == null) ? "" : cc.cmd.trim();
                    if (cmd.isEmpty()) continue;
                    arr.put(cc.toPrefsJson());
                }
            }
            root.put("commands", arr);
            return root.toString(2);
        } catch (Throwable ignored) {
            return toRawCommandArray(commands);
        }
    }

    public static boolean containsCommand(List<CustomCommand> list, String cmd) {
        try {
            if (list == null || cmd == null) return false;
            String c = cmd.trim();
            for (CustomCommand cc : list) {
                if (cc == null) continue;
                if (cc.cmd != null && cc.cmd.trim().equals(c)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String toRawCommandArray(List<CustomCommand> commands) {
        try {
            JSONArray arr = new JSONArray();
            if (commands != null) {
                for (CustomCommand cc : commands) {
                    if (cc == null) continue;
                    String value = cc.cmd == null ? "" : cc.cmd.trim();
                    if (!value.isEmpty()) arr.put(value);
                }
            }
            return arr.toString(2);
        } catch (Throwable ignored) {
            return "[]";
        }
    }
}
