package dev.perms.test.tools.intent;

import android.text.TextUtils;

/** Lightweight display row for saved Intent Launcher presets. */
final class SavedIntentDropdownEntry {
    final String name;
    final String summary;
    final boolean hasPackage;
    final boolean packageEnabled;

    SavedIntentDropdownEntry(String name, String summary, boolean hasPackage, boolean packageEnabled) {
        this.name = name == null ? "" : name;
        this.summary = summary == null ? "" : summary;
        this.hasPackage = hasPackage;
        this.packageEnabled = packageEnabled;
    }

    @Override
    public String toString() {
        return TextUtils.isEmpty(name) ? summary : name;
    }
}
