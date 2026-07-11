package dev.perms.test.kiosk;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

/** One user-whitelisted kiosk launcher target. */
public final class KioskAllowedItem {
    public static final String TYPE_APP = "app";
    public static final String TYPE_SHORTCUT = "shortcut";

    public final String type;
    public final String id;
    public String label;
    public boolean enabled;

    public KioskAllowedItem(String type, String id, String label, boolean enabled) {
        this.type = TextUtils.isEmpty(type) ? TYPE_APP : type;
        this.id = id == null ? "" : id;
        this.label = label == null ? this.id : label;
        this.enabled = enabled;
    }

    public boolean isApp() {
        return TYPE_APP.equals(type);
    }

    public boolean isShortcut() {
        return TYPE_SHORTCUT.equals(type);
    }

    public String stableKey() {
        return type + ":" + id;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", type);
        o.put("id", id);
        o.put("label", label);
        o.put("enabled", enabled);
        return o;
    }

    public static KioskAllowedItem fromJson(JSONObject o) {
        if (o == null) return null;
        String type = o.optString("type", TYPE_APP);
        String id = o.optString("id", "");
        if (TextUtils.isEmpty(id)) return null;
        String label = o.optString("label", id);
        boolean enabled = o.optBoolean("enabled", true);
        return new KioskAllowedItem(type, id, label, enabled);
    }
}
