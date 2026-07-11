package dev.perms.test.shell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User-defined Shell tab command model and JSON serializer.
 */
public class CustomCommand {
    public String name;
    public String cmd;
    public boolean pinned;
    public int order;
    public LinkedHashMap<String, String> variants = new LinkedHashMap<>();

    public CustomCommand(String name, String cmd, boolean pinned, int order) {
        this.name = (name == null) ? "" : name;
        this.cmd = (cmd == null) ? "" : cmd;
        this.pinned = pinned;
        this.order = order;
    }

    public String displayName() {
        String n = (name == null) ? "" : name.trim();
        if (n.isEmpty()) n = (cmd == null) ? "" : cmd.trim();
        if (pinned) return "★ " + n;
        return n;
    }

    public JSONObject toPrefsJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name == null ? "" : name);
        o.put("cmd", cmd == null ? "" : cmd);
        o.put("pinned", pinned);
        o.put("order", order);

        if (variants != null && !variants.isEmpty()) {
            JSONArray v = new JSONArray();
            for (Map.Entry<String, String> e : variants.entrySet()) {
                if (e == null) continue;
                String k = e.getKey() == null ? "" : e.getKey();
                String c = e.getValue() == null ? "" : e.getValue();
                JSONObject it = new JSONObject();
                it.put("name", k);
                it.put("cmd", c);
                v.put(it);
            }
            o.put("variants", v);
        }

        return o;
    }

    public static CustomCommand fromJson(JSONObject o, int fallbackOrder) {
        if (o == null) return new CustomCommand("", "", false, fallbackOrder);
        String n = o.optString("name", "");
        String c = o.optString("cmd", o.optString("command", ""));
        boolean p = o.optBoolean("pinned", o.optBoolean("favorite", false));
        int ord = o.has("order") ? o.optInt("order", fallbackOrder) : fallbackOrder;
        CustomCommand cc = new CustomCommand(n, c, p, ord);

        try {
            Object vObj = o.opt("variants");
            if (vObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) vObj;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.optJSONObject(i);
                    if (it == null) continue;
                    String vn = it.optString("name", "");
                    if (vn == null) vn = "";
                    vn = vn.trim();
                    String vc = it.optString("cmd", it.optString("command", ""));
                    if (vc == null) vc = "";
                    vc = vc.trim();
                    if (!vn.isEmpty() && !vc.isEmpty()) {
                        cc.variants.put(vn, vc);
                    }
                }
            } else if (vObj instanceof JSONObject) {
                // Back-compat for older object style.
                JSONObject vo = (JSONObject) vObj;
                Iterator<String> it = vo.keys();
                while (it != null && it.hasNext()) {
                    String k = it.next();
                    if (k == null) continue;
                    String vc = vo.optString(k, "");
                    if (vc == null) vc = "";
                    k = k.trim();
                    vc = vc.trim();
                    if (!k.isEmpty() && !vc.isEmpty()) {
                        cc.variants.put(k, vc);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return cc;
    }
}
