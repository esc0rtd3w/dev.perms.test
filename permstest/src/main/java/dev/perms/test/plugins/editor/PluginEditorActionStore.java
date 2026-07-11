package dev.perms.test.plugins.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps the Plugin Editor structured action list synchronized without discarding unknown JSON fields. */
public final class PluginEditorActionStore {
    private final ArrayList<JSONObject> actions = new ArrayList<>();
    private int selectedIndex = -1;

    public void clear() {
        actions.clear();
        selectedIndex = -1;
    }

    public void load(JSONArray source) {
        clear();
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject action = source.optJSONObject(i);
                if (action != null) actions.add(copy(action));
            }
        }
        if (!actions.isEmpty()) selectedIndex = 0;
    }

    public int size() {
        return actions.size();
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public boolean hasSelection() {
        return selectedIndex >= 0 && selectedIndex < actions.size();
    }

    public JSONObject selectedCopy() {
        return hasSelection() ? copy(actions.get(selectedIndex)) : null;
    }

    public JSONObject actionCopy(int index) {
        return index >= 0 && index < actions.size() ? copy(actions.get(index)) : null;
    }

    public List<JSONObject> snapshot() {
        ArrayList<JSONObject> result = new ArrayList<>();
        for (JSONObject action : actions) result.add(copy(action));
        return Collections.unmodifiableList(result);
    }

    public void select(int index) {
        selectedIndex = index >= 0 && index < actions.size() ? index : -1;
    }

    public int add(JSONObject action) {
        if (action == null) return selectedIndex;
        actions.add(copy(action));
        selectedIndex = actions.size() - 1;
        return selectedIndex;
    }

    public boolean updateSelected(JSONObject action) {
        if (!hasSelection() || action == null) return false;
        actions.set(selectedIndex, copy(action));
        return true;
    }

    public boolean removeSelected() {
        if (!hasSelection()) return false;
        actions.remove(selectedIndex);
        if (actions.isEmpty()) selectedIndex = -1;
        else if (selectedIndex >= actions.size()) selectedIndex = actions.size() - 1;
        return true;
    }

    public boolean moveSelected(int delta) {
        if (!hasSelection() || delta == 0) return false;
        int target = selectedIndex + delta;
        if (target < 0 || target >= actions.size()) return false;
        Collections.swap(actions, selectedIndex, target);
        selectedIndex = target;
        return true;
    }

    public JSONArray toJsonArray() {
        JSONArray array = new JSONArray();
        for (JSONObject action : actions) array.put(copy(action));
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
