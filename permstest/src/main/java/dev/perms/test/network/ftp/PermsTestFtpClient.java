package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPCmd;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Small FTP-only client wrapper for the Network tab.
 *
 * The implementation intentionally exposes only the operations used by PermsTest's simple
 * split-view FTP client UI. Keep SFTP/FTPS/bookmarks out of this layer so it remains easy to
 * audit and does not pull unrelated UI/data-model code into the main app.
 */
public final class PermsTestFtpClient {
    public static final class RemoteEntry {
        public final String name;
        public final long size;
        public final boolean directory;
        public final boolean file;
        public final boolean link;

        RemoteEntry(String name, long size, boolean directory, boolean file, boolean link) {
            this.name = name == null ? "" : name;
            this.size = Math.max(0L, size);
            this.directory = directory;
            this.file = file;
            this.link = link;
        }

        public String displayName() {
            if (directory) return "[D] " + name;
            if (link) return "[L] " + name;
            return "[F] " + name + "  " + humanSize(size);
        }
    }

    private final FTPClient client = new FTPClient();
    private boolean supportsMlsCommands;
    private String currentDirectory = "/";

    public PermsTestFtpClient() {
        client.setAutodetectUTF8(true);
        client.setConnectTimeout(12000);
        client.setDefaultTimeout(12000);
        client.setDataTimeout(20000);
    }

    public synchronized void connect(String host, int port, String user, String password) throws IOException {
        disconnectQuietly();
        client.connect(host, port);
        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            disconnectQuietly();
            throw new IOException("FTP server rejected connection: " + reply);
        }
        if (!client.login(emptyToDefault(user, "anonymous"), password == null ? "" : password)) {
            int loginReply = client.getReplyCode();
            disconnectQuietly();
            throw new IOException("FTP login failed: " + loginReply);
        }
        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.enterLocalPassiveMode();
        client.setControlEncoding("UTF-8");
        supportsMlsCommands = client.hasFeature(FTPCmd.MLST);
        String pwd = client.printWorkingDirectory();
        currentDirectory = normalizeRemotePath(pwd == null ? "/" : pwd);
    }

    public synchronized boolean isConnected() {
        return client.isConnected();
    }

    public synchronized String currentDirectory() {
        return currentDirectory;
    }

    public synchronized List<RemoteEntry> listCurrentDirectory() throws IOException {
        ensureConnected();
        FTPFile[] files = supportsMlsCommands ? client.mlistDir(currentDirectory) : client.listFiles(currentDirectory);
        ArrayList<RemoteEntry> out = new ArrayList<>();
        if (files != null) {
            for (FTPFile f : files) {
                if (f == null) continue;
                String name = f.getName();
                if (".".equals(name) || "..".equals(name)) continue;
                out.add(new RemoteEntry(name, f.getSize(), f.isDirectory(), f.isFile(), f.isSymbolicLink()));
            }
        }
        Collections.sort(out, new Comparator<RemoteEntry>() {
            @Override
            public int compare(RemoteEntry a, RemoteEntry b) {
                if (a.directory != b.directory) return a.directory ? -1 : 1;
                return a.name.toLowerCase(Locale.US).compareTo(b.name.toLowerCase(Locale.US));
            }
        });
        return out;
    }

    public synchronized void changeDirectory(String path) throws IOException {
        ensureConnected();
        String target = normalizeRemotePath(path);
        if (!client.changeWorkingDirectory(target)) {
            throw new IOException("Could not open remote folder: " + target);
        }
        String pwd = client.printWorkingDirectory();
        currentDirectory = normalizeRemotePath(pwd == null ? target : pwd);
    }

    public synchronized void changeToParentDirectory() throws IOException {
        ensureConnected();
        if ("/".equals(currentDirectory)) return;
        changeDirectory(parentPath(currentDirectory));
    }

    public synchronized boolean upload(File localFile) throws IOException {
        ensureConnected();
        if (localFile == null || !localFile.isFile()) throw new IOException("Select a local file first.");
        try (InputStream in = new FileInputStream(localFile)) {
            return upload(localFile.getName(), in);
        }
    }

    public synchronized boolean upload(String remoteName, InputStream in) throws IOException {
        ensureConnected();
        String safeName = sanitizeName(remoteName);
        if (safeName.isEmpty()) throw new IOException("Remote file name is empty.");
        if (in == null) throw new IOException("Local input stream is not available.");
        return client.storeFile(remoteChild(currentDirectory, safeName), in);
    }

    public synchronized boolean download(RemoteEntry remoteEntry, File localDirectory) throws IOException {
        ensureConnected();
        if (remoteEntry == null || !remoteEntry.file) throw new IOException("Select a remote file first.");
        if (localDirectory == null || !localDirectory.isDirectory()) throw new IOException("Local folder is not available.");
        File outFile = new File(localDirectory, remoteEntry.name);
        try (OutputStream out = new FileOutputStream(outFile)) {
            return download(remoteEntry, out);
        }
    }

    public synchronized boolean download(RemoteEntry remoteEntry, OutputStream out) throws IOException {
        ensureConnected();
        if (remoteEntry == null || !remoteEntry.file) throw new IOException("Select a remote file first.");
        if (out == null) throw new IOException("Local output stream is not available.");
        return client.retrieveFile(remoteChild(currentDirectory, remoteEntry.name), out);
    }

    public synchronized boolean delete(RemoteEntry remoteEntry) throws IOException {
        ensureConnected();
        if (remoteEntry == null) throw new IOException("Select a remote item first.");
        String path = remoteChild(currentDirectory, remoteEntry.name);
        if (remoteEntry.directory) return client.removeDirectory(path);
        return client.deleteFile(path);
    }

    public synchronized boolean mkdir(String name) throws IOException {
        ensureConnected();
        String safe = sanitizeName(name);
        if (safe.isEmpty()) throw new IOException("Folder name is empty.");
        return client.makeDirectory(remoteChild(currentDirectory, safe));
    }

    public synchronized void disconnect() throws IOException {
        if (!client.isConnected()) return;
        try { client.logout(); } catch (Throwable ignored) {}
        client.disconnect();
        currentDirectory = "/";
    }

    public synchronized void disconnectQuietly() {
        try { disconnect(); } catch (Throwable ignored) {}
    }

    private void ensureConnected() throws IOException {
        if (!client.isConnected()) throw new IOException("FTP client is not connected.");
    }

    private static String emptyToDefault(String value, String fallback) {
        String s = value == null ? "" : value.trim();
        return s.isEmpty() ? fallback : s;
    }

    public static String normalizeRemotePath(String path) {
        String p = path == null ? "/" : path.trim().replace('\\', '/');
        if (p.isEmpty()) p = "/";
        if (!p.startsWith("/")) p = "/" + p;
        while (p.contains("//")) p = p.replace("//", "/");
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    public static String parentPath(String path) {
        String p = normalizeRemotePath(path);
        if ("/".equals(p)) return "/";
        int idx = p.lastIndexOf('/');
        return idx <= 0 ? "/" : p.substring(0, idx);
    }

    public static String remoteChild(String parent, String name) {
        String p = normalizeRemotePath(parent);
        String n = name == null ? "" : name.trim();
        if (p.endsWith("/")) return p + n;
        return p + "/" + n;
    }

    public static String sanitizeName(String name) {
        String s = name == null ? "" : name.trim();
        s = s.replace("/", "").replace("\\", "");
        return s;
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        for (String unit : units) {
            value /= 1024.0;
            if (value < 1024.0) return String.format(Locale.US, "%.1f %s", value, unit);
        }
        return String.format(Locale.US, "%.1f PB", value / 1024.0);
    }
}
