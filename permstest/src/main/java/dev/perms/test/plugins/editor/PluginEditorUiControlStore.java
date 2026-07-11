package dev.perms.test.plugins.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps a Plugin Editor structured ui.json control/item array synchronized without discarding unknown fields. */
public final class PluginEditorUiControlStore {
    private final ArrayList<JSONObject> controls = new ArrayList<>();
    private int selectedIndex = -1;

    public void clear() {
        controls.clear();
        selectedIndex = -1;
    }

    public void load(JSONArray source) {
        clear();
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject control = source.optJSONObject(i);
                if (control != null) controls.add(copy(control));
            }
        }
        if (!controls.isEmpty()) selectedIndex = 0;
    }

    public int size() {
        return controls.size();
    }

    public boolean isEmpty() {
        return controls.isEmpty();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public boolean hasSelection() {
        return selectedIndex >= 0 && selectedIndex < controls.size();
    }

    public JSONObject selectedCopy() {
        return hasSelection() ? copy(controls.get(selectedIndex)) : null;
    }

    public List<JSONObject> snapshot() {
        ArrayList<JSONObject> result = new ArrayList<>();
        for (JSONObject control : controls) result.add(copy(control));
        return Collections.unmodifiableList(result);
    }

    public void select(int index) {
        selectedIndex = index >= 0 && index < controls.size() ? index : -1;
    }

    public int add(JSONObject control) {
        if (control == null) return selectedIndex;
        controls.add(copy(control));
        selectedIndex = controls.size() - 1;
        return selectedIndex;
    }

    public boolean updateSelected(JSONObject control) {
        if (!hasSelection() || control == null) return false;
        controls.set(selectedIndex, copy(control));
        return true;
    }

    public boolean removeSelected() {
        if (!hasSelection()) return false;
        controls.remove(selectedIndex);
        if (controls.isEmpty()) selectedIndex = -1;
        else if (selectedIndex >= controls.size()) selectedIndex = controls.size() - 1;
        return true;
    }

    public boolean moveSelected(int delta) {
        if (!hasSelection() || delta == 0) return false;
        int target = selectedIndex + delta;
        if (target < 0 || target >= controls.size()) return false;
        Collections.swap(controls, selectedIndex, target);
        selectedIndex = target;
        return true;
    }

    public JSONArray toJsonArray() {
        JSONArray array = new JSONArray();
        for (JSONObject control : controls) array.put(copy(control));
        return array;
    }

    private static JSONObject copy(JSONObject source) {
        if (source == null) return new JSONObject();
        try {
            return new JSONObject(source.toString());
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }
}
