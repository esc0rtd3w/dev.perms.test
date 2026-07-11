package dev.perms.test.files;

/** Immutable row model for the Files tab browser panes. */
public final class FileEntry {
    public final String name;
    public final boolean isDir;
    public final boolean isLink;
    public final String fullPath;
    public final long size;
    public final long modifiedEpoch;
    public final String meta;

    public FileEntry(String name, boolean isDir, boolean isLink, String fullPath, long size, long modifiedEpoch, String meta) {
        this.name = name;
        this.isDir = isDir;
        this.isLink = isLink;
        this.fullPath = fullPath;
        this.size = size;
        this.modifiedEpoch = modifiedEpoch;
        this.meta = meta;
    }

    @Override
    public String toString() {
        return isDir ? (name + "/") : name;
    }
}
