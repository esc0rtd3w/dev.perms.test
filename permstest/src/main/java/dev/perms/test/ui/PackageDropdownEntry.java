package dev.perms.test.ui;

import android.text.TextUtils;

/** Shared model for package-name dropdowns used across app tabs. */
public final class PackageDropdownEntry {
    public final String label;
    public final String pkg;
    public boolean enabled;
    public boolean debuggable;

    public PackageDropdownEntry(String label, String pkg, boolean enabled) {
        this(label, pkg, enabled, false);
    }

    public PackageDropdownEntry(String label, String pkg, boolean enabled, boolean debuggable) {
        this.label = label;
        this.pkg = pkg;
        this.enabled = enabled;
        this.debuggable = debuggable;
    }

    @Override
    public String toString() {
        return TextUtils.isEmpty(label) ? pkg : label;
    }
}
