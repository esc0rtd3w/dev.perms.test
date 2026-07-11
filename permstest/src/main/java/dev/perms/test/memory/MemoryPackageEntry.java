package dev.perms.test.memory;

import android.text.TextUtils;

public final class MemoryPackageEntry {
    public final String label;
    public final String pkg;
    public final boolean running;
    public final boolean debuggable;

    public MemoryPackageEntry(String label, String pkg, boolean running) {
        this(label, pkg, running, false);
    }

    public MemoryPackageEntry(String label, String pkg, boolean running, boolean debuggable) {
        this.label = label == null ? "" : label;
        this.pkg = pkg == null ? "" : pkg;
        this.running = running;
        this.debuggable = debuggable;
    }

    @Override
    public String toString() {
        return TextUtils.isEmpty(pkg) ? label : pkg;
    }
}
