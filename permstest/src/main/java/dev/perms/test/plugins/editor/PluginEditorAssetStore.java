package dev.perms.test.plugins.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps the Plugin Editor managed assets list and selection independent from Android picker state. */
public final class PluginEditorAssetStore {
    private final ArrayList<String> paths = new ArrayList<>();
    private int selectedIndex = -1;

    public void clear() {
        paths.clear();
        selectedIndex = -1;
    }

    public void load(List<String> source) {
        clear();
        if (source != null) {
            for (String path : source) addInternal(path);
        }
        if (!paths.isEmpty()) selectedIndex = 0;
    }

    public int size() {
        return paths.size();
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public boolean hasSelection() {
        return selectedIndex >= 0 && selectedIndex < paths.size();
    }

    public String selectedPath() {
        return hasSelection() ? paths.get(selectedIndex) : "";
    }

    public List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(paths));
    }

    public void select(int index) {
        selectedIndex = index >= 0 && index < paths.size() ? index : -1;
    }

    public int addOrSelect(String path) {
        String normalized = normalize(path);
        if (normalized.isEmpty()) return selectedIndex;
        int index = paths.indexOf(normalized);
        if (index < 0) {
            paths.add(normalized);
            index = paths.size() - 1;
        }
        selectedIndex = index;
        return selectedIndex;
    }

    public boolean removeSelected() {
        if (!hasSelection()) return false;
        paths.remove(selectedIndex);
        if (paths.isEmpty()) selectedIndex = -1;
        else if (selectedIndex >= paths.size()) selectedIndex = paths.size() - 1;
        return true;
    }

    private void addInternal(String path) {
        String normalized = normalize(path);
        if (!normalized.isEmpty() && !paths.contains(normalized)) paths.add(normalized);
    }

    private static String normalize(String path) {
        return path == null ? "" : path.trim().replace('\\', '/');
    }
}
