package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

/**
 * Local filesystem entry used by the Network tab FTP client pane.
 */
public final class FtpClientLocalEntry {
    public final String name;
    public final String path;
    public final long size;
    public final boolean directory;
    public final boolean file;
    public final boolean link;

    public FtpClientLocalEntry(String name, String path, long size, boolean directory, boolean file, boolean link) {
        this.name = name == null ? "" : name;
        this.path = normalizePath(path);
        this.size = Math.max(0L, size);
        this.directory = directory;
        this.file = file;
        this.link = link;
    }

    public static String normalizePath(String path) {
        String p = path == null ? "" : path.trim().replace('\\', '/');
        if (p.isEmpty()) p = "/storage/emulated/0/dev.perms.test";
        if (!p.startsWith("/")) p = "/" + p;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
