package dev.perms.test.memory;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Small Activity-side helpers for the Memory running-process dropdown.
 *
 * The Activity keeps ownership of widgets and threading; this class only centralizes the
 * process-list model setup and stable status/selection text used by those widgets.
 */
public final class MemoryProcesses {
    public static final String AUTO_SELECT_LABEL = "Auto-select";

    private MemoryProcesses() {
    }

    public static ArrayList<MemoryProcessEntry> buildInitialProcessDropdownItems() {
        ArrayList<MemoryProcessEntry> items = new ArrayList<>();
        items.add(new MemoryProcessEntry("", AUTO_SELECT_LABEL));
        return items;
    }

    public static ArrayList<MemoryProcessEntry> buildProcessDropdownItems(List<MemoryProcessEntry> processes) {
        ArrayList<MemoryProcessEntry> items = buildInitialProcessDropdownItems();
        if (processes != null) items.addAll(processes);
        return items;
    }

    public static String resolveSelectedProcessText(String currentText, String restoredText) {
        if (!TextUtils.isEmpty(currentText)) return currentText.trim();
        if (!TextUtils.isEmpty(restoredText)) return restoredText.trim();
        return AUTO_SELECT_LABEL;
    }

    public static String formatProcessRefreshStatus(List<MemoryProcessEntry> processes) {
        int count = processes == null ? 0 : processes.size();
        if (count <= 0) return "[Memory] No running processes found for the target package.\n";
        return "[Memory] Found " + count + " running process" + (count == 1 ? "" : "es") + ".\n";
    }
}
