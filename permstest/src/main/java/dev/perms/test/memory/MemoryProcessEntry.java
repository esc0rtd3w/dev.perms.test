package dev.perms.test.memory;

public final class MemoryProcessEntry {
    public final String pid;
    public final String name;

    public MemoryProcessEntry(String pid, String name) {
        this.pid = pid == null ? "" : pid.trim();
        this.name = name == null ? "" : name.trim();
    }

    public String displayLabel() {
        if (name.isEmpty()) {
            return pid.isEmpty() ? "Auto-select" : (pid + " · process");
        }
        if (pid.isEmpty()) {
            return name;
        }
        return pid + " · " + name;
    }

    @Override
    public String toString() {
        return displayLabel();
    }
}
