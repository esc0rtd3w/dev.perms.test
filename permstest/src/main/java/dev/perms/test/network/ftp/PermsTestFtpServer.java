package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PermsTestFtpServer {
    public interface Listener {
        void onFtpLog(String message);
        void onFtpStateChanged();
    }

    public interface ShellAccess {
        ShellResult run(String command);
        Process start(String command) throws IOException;
    }

    public static final class ShellResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private static final int CONTROL_TIMEOUT_MS = 120_000;
    private static final int DATA_TIMEOUT_MS = 30_000;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService clients = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private File rootDir;
    private File canonicalRootDir;
    private int port;
    private boolean shellEnabled;
    private boolean debugEnabled;
    private ShellAccess shellAccess;
    private Listener listener;
    private String lastError = "";

    public synchronized void start(int requestedPort, File requestedRoot, boolean requestedShellEnabled,
                            ShellAccess requestedShellAccess, boolean requestedDebugEnabled,
                            Listener requestedListener) throws IOException {
        if (running.get()) return;
        if (requestedPort < 1 || requestedPort > 65535) throw new IOException("Invalid FTP port: " + requestedPort);
        if (requestedRoot == null) throw new IOException("FTP root is empty.");

        debugEnabled = requestedDebugEnabled;
        shellEnabled = requestedShellEnabled && requestedShellAccess != null;
        shellAccess = shellEnabled ? requestedShellAccess : null;
        listener = requestedListener;
        debug("start requested port=" + requestedPort
                + "; root=" + requestedRoot.getAbsolutePath()
                + "; shizuku-requested=" + requestedShellEnabled
                + "; shizuku-available=" + (requestedShellAccess != null));

        if (shellEnabled) {
            ShellResult ready = shell("mkdir -p " + shQuote(requestedRoot.getAbsolutePath())
                    + " 2>/dev/null; [ -d " + shQuote(requestedRoot.getAbsolutePath()) + " ]");
            if (ready == null || ready.exitCode != 0) {
                shellEnabled = false;
                shellAccess = null;
                log("Shizuku file access unavailable for FTP root; using Android file APIs.");
            }
        }

        if (!shellEnabled) {
            if (!requestedRoot.exists() && !requestedRoot.mkdirs()) {
                throw new IOException("Could not create FTP root: " + requestedRoot.getAbsolutePath());
            }
            if (!requestedRoot.isDirectory()) throw new IOException("FTP root is not a directory: " + requestedRoot.getAbsolutePath());
        }

        rootDir = requestedRoot;
        canonicalRootDir = requestedRoot.getCanonicalFile();
        lastError = "";
        debug("canonical root=" + canonicalRootDir.getAbsolutePath()
                + "; shell-enabled=" + shellEnabled);

        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(requestedPort));
        serverSocket = socket;
        port = socket.getLocalPort();
        running.set(true);

        acceptThread = new Thread(this::acceptLoop, "PermsTestFtpAccept");
        acceptThread.start();
        notifyState();
        log("FTP server started on port " + port + "; root=" + canonicalRootDir.getAbsolutePath()
                + (shellEnabled ? "; shizuku-files=on" : "")
                + (debugEnabled ? "; debug=on" : ""));
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) return;
        closeQuietly(serverSocket);
        serverSocket = null;
        log("FTP server stopped.");
        notifyState();
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isUsingShellAccess() {
        return shellEnabled && shellAccess != null;
    }

    public int getPort() {
        return port;
    }

    public File getRootDir() {
        return canonicalRootDir;
    }

    public String getLastError() {
        return lastError;
    }

    public void shutdown() {
        stop();
        clients.shutdownNow();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(CONTROL_TIMEOUT_MS);
                clients.execute(new Session(client));
            } catch (IOException e) {
                if (running.get()) {
                    lastError = e.getMessage();
                    log("FTP accept failed: " + e.getMessage());
                }
            }
        }
        notifyState();
    }

    private final class Session implements Runnable {
        private final Socket control;
        private BufferedReader in;
        private BufferedWriter out;
        private File cwd;
        private boolean loggedIn;
        private ServerSocket passiveServer;
        private File renameFrom;
        private long restartOffset;
        private boolean usernameAccepted;

        Session(Socket control) {
            this.control = control;
            this.cwd = canonicalRootDir;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8));
                reply(220, "PermsTest FTP ready. Anonymous access only.");
                String line;
                while (running.get() && (line = in.readLine()) != null) {
                    if (!handle(line)) break;
                }
            } catch (SocketTimeoutException ignored) {
                try { reply(421, "Control connection timed out."); } catch (Throwable ignored2) {}
            } catch (Throwable e) {
                log("FTP session ended: " + e.getMessage());
            } finally {
                closePassive();
                closeQuietly(control);
            }
        }

        private boolean handle(String line) throws IOException {
            if (line == null) return false;
            line = line.trim();
            if (line.length() == 0) return true;

            int space = line.indexOf(' ');
            String cmd = (space >= 0 ? line.substring(0, space) : line).toUpperCase(Locale.US);
            String arg = space >= 0 ? line.substring(space + 1).trim() : "";

            if ("QUIT".equals(cmd)) {
                reply(221, "Goodbye.");
                return false;
            }

            if ("USER".equals(cmd)) {
                if (TextUtils.isEmpty(arg) || "anonymous".equalsIgnoreCase(arg)) {
                    usernameAccepted = true;
                    loggedIn = false;
                    reply(331, "Anonymous username accepted; send any password.");
                } else {
                    usernameAccepted = false;
                    loggedIn = false;
                    reply(530, "Anonymous access only.");
                }
                return true;
            }
            if ("PASS".equals(cmd)) {
                if (!usernameAccepted) usernameAccepted = true;
                loggedIn = true;
                reply(230, "Anonymous login accepted.");
                return true;
            }
            if ("AUTH".equals(cmd)) {
                reply(502, "Explicit TLS is not supported. Use plain FTP on a trusted local network.");
                return true;
            }
            if ("PBSZ".equals(cmd) || "PROT".equals(cmd)) {
                reply(503, "TLS is not active.");
                return true;
            }
            if ("FEAT".equals(cmd)) {
                raw("211-Features");
                raw(" UTF8");
                raw(" EPSV");
                raw(" PASV");
                raw(" SIZE");
                raw(" MDTM");
                raw(" REST STREAM");
                raw(" MLST type*;size*;modify*;");
                raw("211 End");
                return true;
            }
            if ("SYST".equals(cmd)) {
                reply(215, "UNIX Type: L8");
                return true;
            }
            if ("OPTS".equals(cmd)) {
                reply(200, "Options accepted.");
                return true;
            }
            if (!loggedIn) {
                reply(530, "Login with anonymous first.");
                return true;
            }

            debug("cmd=" + cmd + ("PASS".equals(cmd) ? " ***" : (TextUtils.isEmpty(arg) ? "" : " " + oneLine(arg)))
                    + "; cwd=" + safeFtpPath(cwd));

            switch (cmd) {
                case "NOOP": reply(200, "OK."); break;
                case "PWD": reply(257, quote(toFtpPath(cwd)) + " is current directory."); break;
                case "CWD": changeDirectory(arg); break;
                case "CDUP": changeDirectory(".."); break;
                case "TYPE": reply(200, "Type set to " + (TextUtils.isEmpty(arg) ? "I" : arg) + "."); break;
                case "PASV": enterPassive(false); break;
                case "EPSV": enterPassive(true); break;
                case "PORT": reply(502, "Active mode is not supported. Use passive mode."); break;
                case "EPRT": reply(502, "Active mode is not supported. Use passive mode."); break;
                case "LIST": list(arg, false, false); break;
                case "NLST": list(arg, true, false); break;
                case "MLSD": list(arg, false, true); break;
                case "MLST": mlst(arg); break;
                case "SIZE": size(arg); break;
                case "MDTM": mdtm(arg); break;
                case "RETR": retrieve(arg); break;
                case "STOR": store(arg, false); break;
                case "APPE": store(arg, true); break;
                case "DELE": deleteFile(arg); break;
                case "MKD": makeDirectory(arg); break;
                case "RMD": removeDirectory(arg); break;
                case "RNFR": renameFrom(arg); break;
                case "RNTO": renameTo(arg); break;
                case "REST": restart(arg); break;
                case "ABOR": closePassive(); reply(226, "Abort complete."); break;
                default: reply(502, "Command not implemented: " + cmd); break;
            }
            return true;
        }

        private void changeDirectory(String arg) throws IOException {
            File next = resolvePath(arg);
            debug("CWD arg=" + oneLine(arg)
                    + "; resolved=" + safePath(next)
                    + "; exists=" + exists(next)
                    + "; dir=" + isDirectory(next));
            if (next != null && isDirectory(next)) {
                cwd = isRootDirectory() ? normalizeAbsolutePath(next) : next.getCanonicalFile();
                reply(250, "Directory changed to " + toFtpPath(cwd));
                return;
            }
            if ("..".equals(cleanPathArgument(arg))) {
                cwd = canonicalRootDir;
                reply(250, "Directory changed to /");
                return;
            }
            reply(550, "Directory not found.");
        }

        private void enterPassive(boolean extended) throws IOException {
            closePassive();
            passiveServer = new ServerSocket(0);
            passiveServer.setReuseAddress(true);
            passiveServer.setSoTimeout(DATA_TIMEOUT_MS);
            int p = passiveServer.getLocalPort();
            if (extended) {
                reply(229, "Entering Extended Passive Mode (|||" + p + "|).");
                return;
            }

            InetAddress local = control.getLocalAddress();
            String host = local == null ? null : local.getHostAddress();
            if (TextUtils.isEmpty(host) || "0.0.0.0".equals(host) || host.contains(":")) {
                host = firstIpv4Address();
            }
            if (TextUtils.isEmpty(host)) host = "127.0.0.1";
            String[] octets = host.split("\\.");
            if (octets.length != 4) {
                reply(522, "Use EPSV for IPv6 connections.");
                return;
            }
            reply(227, "Entering Passive Mode (" + octets[0] + "," + octets[1] + "," + octets[2] + "," + octets[3] + "," + (p / 256) + "," + (p % 256) + ").");
        }

        private void list(String arg, boolean namesOnly, boolean machine) throws IOException {
            File target = resolveListTarget(arg);
            debug("LIST arg=" + oneLine(arg)
                    + "; target=" + safePath(target)
                    + "; exists=" + exists(target)
                    + "; mode=" + (isUsingShellAccess() ? "shizuku" : "android")
                    + "; namesOnly=" + namesOnly
                    + "; machine=" + machine);
            if (target == null || !exists(target)) {
                reply(550, "Path not found.");
                return;
            }
            Socket data = openDataSocket();
            if (data == null) return;
            reply(150, "Opening data connection.");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(data.getOutputStream(), StandardCharsets.UTF_8))) {
                ArrayList<FtpEntry> entries = isUsingShellAccess() ? shellList(target) : localList(target);
                debug("LIST entries=" + entries.size() + "; sample=" + summarizeEntries(entries));
                for (FtpEntry entry : entries) {
                    writer.write((machine ? mlsdLine(entry) : namesOnly ? entry.name : listLine(entry)) + "\r\n");
                }
                writer.flush();
                reply(226, "Transfer complete.");
            } catch (IOException e) {
                log("FTP list failed: " + e.getMessage());
                reply(451, "List failed." + (TextUtils.isEmpty(e.getMessage()) ? "" : " " + oneLine(e.getMessage())));
            } finally {
                closeQuietly(data);
                closePassive();
            }
        }

        private void mlst(String arg) throws IOException {
            File target = resolvePath(arg);
            if (target == null || !exists(target)) {
                reply(550, "Path not found.");
                return;
            }
            raw("250-Listing " + toFtpPath(target));
            if (isUsingShellAccess()) {
                FtpEntry entry = shellEntry(target);
                if (entry == null) {
                    raw("250 End");
                    return;
                }
                raw(" " + mlsdLine(entry));
            } else {
                raw(" " + mlsdLine(target));
            }
            raw("250 End");
        }

        private void size(String arg) throws IOException {
            File target = resolvePath(arg);
            if (target != null && isFile(target)) reply(213, String.valueOf(fileSize(target)));
            else reply(550, "File not found.");
        }

        private void mdtm(String arg) throws IOException {
            File target = resolvePath(arg);
            if (target != null && exists(target)) reply(213, timestamp(fileModifiedMillis(target)));
            else reply(550, "Path not found.");
        }

        private void retrieve(String arg) throws IOException {
            File target = resolvePath(arg);
            if (target == null || !isFile(target)) {
                reply(550, "File not found.");
                return;
            }
            if (isUsingShellAccess()) {
                retrieveShell(target);
                return;
            }
            Socket data = openDataSocket();
            if (data == null) return;
            reply(150, "Opening data connection.");
            long offset = Math.max(0L, restartOffset);
            restartOffset = 0L;
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(target));
                 BufferedOutputStream output = new BufferedOutputStream(data.getOutputStream())) {
                skipFully(input, offset);
                copy(input, output);
                output.flush();
                reply(226, "Transfer complete.");
            } catch (IOException e) {
                log("FTP download failed: " + e.getMessage());
                reply(451, "Download failed." + (TextUtils.isEmpty(e.getMessage()) ? "" : " " + oneLine(e.getMessage())));
            } finally {
                closeQuietly(data);
                closePassive();
            }
        }

        private void retrieveShell(File target) throws IOException {
            Socket data = openDataSocket();
            if (data == null) return;
            reply(150, "Opening data connection.");
            long offset = Math.max(0L, restartOffset);
            restartOffset = 0L;
            String cmd = offset > 0L
                    ? "tail -c +" + (offset + 1L) + " " + shQuote(target.getAbsolutePath())
                    : "cat " + shQuote(target.getAbsolutePath());
            Process p = null;
            int exit = 1;
            String err = "";
            try (BufferedOutputStream output = new BufferedOutputStream(data.getOutputStream())) {
                p = shellAccess.start(cmd);
                copy(p.getInputStream(), output);
                output.flush();
                err = readAllQuiet(p.getErrorStream());
                try { exit = p.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); exit = 1; }
            } finally {
                closeQuietly(data);
                closePassive();
                if (p != null) try { p.destroy(); } catch (Throwable ignored) {}
            }
            if (exit == 0) reply(226, "Transfer complete.");
            else reply(451, "Transfer failed." + (TextUtils.isEmpty(err) ? "" : " " + oneLine(err)));
        }

        private void store(String arg, boolean append) throws IOException {
            File target = resolvePath(arg);
            if (target == null) {
                reply(550, "Invalid path.");
                return;
            }
            File parent = target.getParentFile();
            if (parent == null || !isDirectory(parent)) {
                reply(550, "Parent directory not found.");
                return;
            }
            if (isUsingShellAccess()) {
                storeShell(target, append);
                return;
            }
            Socket data = openDataSocket();
            if (data == null) return;
            reply(150, "Opening data connection.");
            try (BufferedInputStream input = new BufferedInputStream(data.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target, append))) {
                copy(input, output);
                output.flush();
                reply(226, "Transfer complete.");
            } catch (IOException e) {
                log("FTP upload failed: " + e.getMessage());
                reply(451, "Upload failed." + (TextUtils.isEmpty(e.getMessage()) ? "" : " " + oneLine(e.getMessage())));
            } finally {
                closeQuietly(data);
                closePassive();
            }
        }

        private void storeShell(File target, boolean append) throws IOException {
            Socket data = openDataSocket();
            if (data == null) return;
            reply(150, "Opening data connection.");
            Process p = null;
            int exit = 1;
            String err = "";
            try (BufferedInputStream input = new BufferedInputStream(data.getInputStream())) {
                p = shellAccess.start("cat " + (append ? ">> " : "> ") + shQuote(target.getAbsolutePath()));
                try (BufferedOutputStream output = new BufferedOutputStream(p.getOutputStream())) {
                    copy(input, output);
                    output.flush();
                }
                err = readAllQuiet(p.getErrorStream());
                try { exit = p.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); exit = 1; }
            } catch (IOException e) {
                err = e.getMessage();
                log("FTP Shizuku upload failed: " + e.getMessage());
            } finally {
                closeQuietly(data);
                closePassive();
                if (p != null) try { p.destroy(); } catch (Throwable ignored) {}
            }
            if (exit == 0) reply(226, "Transfer complete.");
            else reply(451, "Upload failed." + (TextUtils.isEmpty(err) ? "" : " " + oneLine(err)));
        }

        private void deleteFile(String arg) throws IOException {
            File target = resolvePath(arg);
            if (target == null) {
                reply(550, "Invalid path.");
                return;
            }
            boolean ok = isUsingShellAccess()
                    ? shellOk("[ -f " + shQuote(target.getAbsolutePath()) + " ] && rm -f " + shQuote(target.getAbsolutePath()))
                    : target.isFile() && target.delete();
            reply(ok ? 250 : 550, ok ? "File deleted." : "Could not delete file.");
        }

        private void makeDirectory(String arg) throws IOException {
            File target = resolvePath(arg);
            boolean ok = target != null && (isUsingShellAccess()
                    ? shellOk("mkdir -p " + shQuote(target.getAbsolutePath()))
                    : (target.isDirectory() || target.mkdirs()));
            if (ok) reply(257, quote(toFtpPath(target)) + " created.");
            else reply(550, "Could not create directory.");
        }

        private void removeDirectory(String arg) throws IOException {
            File target = resolvePath(arg);
            boolean ok = target != null && (isUsingShellAccess()
                    ? shellOk("rmdir " + shQuote(target.getAbsolutePath()))
                    : target.isDirectory() && target.delete());
            reply(ok ? 250 : 550, ok ? "Directory removed." : "Could not remove directory.");
        }

        private void renameFrom(String arg) throws IOException {
            File target = resolvePath(arg);
            if (target != null && exists(target)) {
                renameFrom = target;
                reply(350, "Ready for RNTO.");
            } else {
                renameFrom = null;
                reply(550, "Path not found.");
            }
        }

        private void renameTo(String arg) throws IOException {
            if (renameFrom == null) {
                reply(503, "Use RNFR first.");
                return;
            }
            File target = resolvePath(arg);
            boolean ok = target != null && (isUsingShellAccess()
                    ? shellOk("mv " + shQuote(renameFrom.getAbsolutePath()) + " " + shQuote(target.getAbsolutePath()))
                    : renameFrom.renameTo(target));
            renameFrom = null;
            reply(ok ? 250 : 550, ok ? "Rename complete." : "Rename failed.");
        }

        private void restart(String arg) throws IOException {
            try {
                restartOffset = Math.max(0L, Long.parseLong(arg));
                reply(350, "Restart offset accepted.");
            } catch (Throwable ignored) {
                restartOffset = 0L;
                reply(501, "Invalid restart offset.");
            }
        }

        private Socket openDataSocket() throws IOException {
            if (passiveServer == null) {
                reply(425, "Use PASV or EPSV first.");
                return null;
            }
            try {
                Socket data = passiveServer.accept();
                data.setSoTimeout(DATA_TIMEOUT_MS);
                return data;
            } catch (SocketTimeoutException e) {
                reply(425, "Timed out waiting for data connection.");
                closePassive();
                return null;
            }
        }

        private File resolveListTarget(String arg) throws IOException {
            String cleaned = cleanListPathArgument(arg);
            if (TextUtils.isEmpty(cleaned)) return cwd;
            return resolvePath(cleaned);
        }

        private File resolvePath(String arg) throws IOException {
            String path = cleanPathArgument(arg);
            if (TextUtils.isEmpty(path)) return cwd;
            File raw;
            if (path.startsWith("/")) {
                raw = isRootDirectory() ? new File(path) : new File(canonicalRootDir, path.substring(1));
            } else {
                raw = new File(cwd, path);
            }
            File resolved = isRootDirectory() ? normalizeAbsolutePath(raw) : raw.getCanonicalFile();
            boolean underRoot = isUnderRoot(resolved);
            debug("resolve arg=" + oneLine(arg)
                    + "; raw=" + safePath(raw)
                    + "; resolved=" + safePath(resolved)
                    + "; under-root=" + underRoot);
            if (!underRoot) return null;
            return resolved;
        }

        private boolean exists(File f) {
            if (f == null) return false;
            if (isUsingShellAccess()) return shellOk("[ -e " + shQuote(f.getAbsolutePath()) + " ]");
            return f.exists();
        }

        private boolean isDirectory(File f) {
            if (f == null) return false;
            if (isUsingShellAccess()) return shellOk("[ -d " + shQuote(f.getAbsolutePath()) + " ]");
            return f.isDirectory();
        }

        private boolean isFile(File f) {
            if (f == null) return false;
            if (isUsingShellAccess()) return shellOk("[ -f " + shQuote(f.getAbsolutePath()) + " ]");
            return f.isFile();
        }

        private long fileSize(File f) {
            if (f == null) return 0L;
            if (isUsingShellAccess()) return shellLong("stat -c %s " + shQuote(f.getAbsolutePath()) + " 2>/dev/null || echo 0", 0L);
            return f.length();
        }

        private long fileModifiedMillis(File f) {
            if (f == null) return 0L;
            if (isUsingShellAccess()) return shellLong("stat -c %Y " + shQuote(f.getAbsolutePath()) + " 2>/dev/null || echo 0", 0L) * 1000L;
            return f.lastModified();
        }


        private ArrayList<FtpEntry> localList(File target) throws IOException {
            ArrayList<FtpEntry> entries = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (target.isDirectory()) {
                File[] files = target.listFiles();
                debug("local listFiles path=" + safePath(target)
                        + "; canRead=" + target.canRead()
                        + "; canExecute=" + target.canExecute()
                        + "; null=" + (files == null)
                        + "; count=" + (files == null ? 0 : files.length));
                if (files != null) {
                    for (File f : files) addLocalEntry(entries, seen, f.getName(), f.isDirectory(), f.length(), f.lastModified(), f);
                }
                ArrayList<FtpEntry> injected = injectedRootEntries(target);
                for (FtpEntry entry : injected) addEntry(entries, seen, entry);
                ArrayList<FtpEntry> shellEntries = new ArrayList<>();
                if (files == null || files.length == 0) {
                    shellEntries = localShellList(target);
                    for (FtpEntry entry : shellEntries) addEntry(entries, seen, entry);
                }
                debug("local list merged path=" + safePath(target)
                        + "; java=" + (files == null ? 0 : files.length)
                        + "; injected=" + injected.size()
                        + "; local-shell=" + shellEntries.size()
                        + "; total=" + entries.size());
            } else {
                addLocalEntry(entries, seen, target.getName(), target.isDirectory(), target.length(), target.lastModified(), target);
            }
            Collections.sort(entries, ENTRY_SORT);
            return entries;
        }

        private void addLocalEntry(ArrayList<FtpEntry> entries, Set<String> seen, String name,
                                   boolean directory, long size, long modifiedMillis, File file) {
            if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)) return;
            String key = name.toLowerCase(Locale.US);
            if (seen.contains(key)) return;
            seen.add(key);
            entries.add(new FtpEntry(name, directory, size, modifiedMillis, file));
        }

        private void addEntry(ArrayList<FtpEntry> entries, Set<String> seen, FtpEntry entry) {
            if (entry == null) return;
            addLocalEntry(entries, seen, entry.name, entry.directory, entry.size, entry.modifiedMillis, entry.file);
        }

        private ArrayList<FtpEntry> injectedRootEntries(File target) {
            ArrayList<FtpEntry> out = new ArrayList<>();
            if (target == null) return out;
            String path = target.getAbsolutePath();
            if (File.separator.equals(path)) {
                addInjectedEntry(out, target, "storage");
                addInjectedEntry(out, target, "sdcard");
            } else if ((File.separator + "storage").equals(path)) {
                addInjectedEntry(out, target, "emulated");
                addInjectedEntry(out, target, "self");
            } else if ((File.separator + "storage" + File.separator + "emulated").equals(path)) {
                addInjectedEntry(out, target, "0");
            }
            return out;
        }

        private void addInjectedEntry(ArrayList<FtpEntry> entries, File parent, String name) {
            if (entries == null || parent == null || TextUtils.isEmpty(name)) return;
            File child = new File(parent, name);
            entries.add(new FtpEntry(name, true, 0L, child.lastModified(), child));
        }

        private ArrayList<FtpEntry> localShellList(File target) {
            ArrayList<FtpEntry> out = new ArrayList<>();
            if (target == null || !target.isDirectory()) return out;
            try {
                String command = "P=" + shQuote(target.getAbsolutePath()) + "; "
                        + "cd \"$P\" 2>/dev/null || exit 2; "
                        + "ls -1Ap 2>/dev/null | while IFS= read -r n; do "
                        + "[ -z \"$n\" ] && continue; [ \"$n\" = \"./\" ] && continue; [ \"$n\" = \"../\" ] && continue; "
                        + "case \"$n\" in */) t=d; name=${n%/};; *) name=$n; if [ -d \"./$name\" ]; then t=d; else t=f; fi;; esac; "
                        + "s=$(stat -c %s \"./$name\" 2>/dev/null || echo 0); "
                        + "m=$(stat -c %Y \"./$name\" 2>/dev/null || echo 0); "
                        + "printf '%s\\t%s\\t%s\\t%s\\n' \"$t\" \"$s\" \"$m\" \"$name\"; "
                        + "done";
                Process p = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
                String stdout = readAllQuiet(p.getInputStream());
                int exit;
                try { exit = p.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return out; }
                debug("local shell list path=" + safePath(target)
                        + "; exit=" + exit
                        + "; stdout-bytes=" + stdout.length());
                if (exit != 0) return out;
                String[] lines = stdout.split("\\n");
                for (String line : lines) {
                    String[] parts = line.split("\\t", 4);
                    if (parts.length < 4) continue;
                    String name = parts[3];
                    if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)) continue;
                    File child = new File(target, name);
                    out.add(new FtpEntry(name, "d".equals(parts[0]), parseLong(parts[1], 0L), parseLong(parts[2], 0L) * 1000L, child));
                }
            } catch (Throwable ignored) {
            }
            return out;
        }

        private ArrayList<FtpEntry> shellList(File target) throws IOException {
            ArrayList<FtpEntry> out = new ArrayList<>();
            ShellResult r = shell("P=" + shQuote(target.getAbsolutePath()) + "; "
                    + "if [ -d \"$P\" ]; then printf '__PTFTP_TARGET__|d\\n'; cd \"$P\" || exit 2; "
                    + "stat -c '%F|%s|%Y|%n' ./* ./.[!.]* ./..?* 2>/dev/null || true; "
                    + "elif [ -e \"$P\" ]; then printf '__PTFTP_TARGET__|f\\n'; "
                    + "stat -c '%F|%s|%Y|%n' \"$P\" 2>/dev/null; else exit 2; fi");
            debug("shell list path=" + safePath(target)
                    + "; exit=" + (r == null ? -1 : r.exitCode)
                    + "; stdout-bytes=" + (r == null || r.stdout == null ? 0 : r.stdout.length())
                    + "; stderr=" + (r == null ? "" : oneLine(r.stderr)));
            if (r == null || r.exitCode != 0) return out;
            boolean listedDirectory = true;
            Set<String> seen = new HashSet<>();
            String[] lines = r.stdout.split("\n");
            for (String line : lines) {
                if (TextUtils.isEmpty(line)) continue;
                if (line.startsWith("__PTFTP_TARGET__|")) {
                    listedDirectory = line.endsWith("|d");
                    continue;
                }
                String[] parts = line.split("\\|", 4);
                if (parts.length < 4) continue;
                String name = parts[3];
                if (listedDirectory && name.startsWith("./")) name = name.substring(2);
                if (!listedDirectory) name = new File(name).getName();
                if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)) continue;
                String key = name.toLowerCase(Locale.US);
                if (!seen.add(key)) continue;
                File child = listedDirectory ? new File(target, name) : target;
                boolean directory = parts[0].toLowerCase(Locale.US).contains("directory");
                out.add(new FtpEntry(name, directory, parseLong(parts[1], 0L), parseLong(parts[2], 0L) * 1000L, child));
            }
            Collections.sort(out, ENTRY_SORT);
            return out;
        }

        private FtpEntry shellEntry(File target) throws IOException {
            ArrayList<FtpEntry> list = shellList(target);
            if (!list.isEmpty()) return list.get(0);
            return null;
        }

        private boolean isUnderRoot(File f) throws IOException {
            if (f == null) return false;
            if (isRootDirectory()) return true;
            String root = canonicalRootDir.getCanonicalPath();
            String path = f.getCanonicalPath();
            return path.equals(root) || path.startsWith(root + File.separator);
        }

        private boolean isRootDirectory() {
            return canonicalRootDir != null && File.separator.equals(canonicalRootDir.getPath());
        }

        private File normalizeAbsolutePath(File raw) {
            if (raw == null) return new File(File.separator);
            String value = raw.getPath();
            if (TextUtils.isEmpty(value)) return new File(File.separator);
            value = value.replace('\\', '/');
            ArrayList<String> parts = new ArrayList<>();
            for (String part : value.split("/+")) {
                if (TextUtils.isEmpty(part) || ".".equals(part)) continue;
                if ("..".equals(part)) {
                    if (!parts.isEmpty()) parts.remove(parts.size() - 1);
                    continue;
                }
                parts.add(part);
            }
            if (parts.isEmpty()) return new File(File.separator);
            StringBuilder out = new StringBuilder();
            for (String part : parts) {
                out.append(File.separator).append(part);
            }
            return new File(out.toString());
        }

        private String safeFtpPath(File f) {
            try { return toFtpPath(f); } catch (Throwable ignored) { return safePath(f); }
        }

        private String toFtpPath(File f) throws IOException {
            if (isRootDirectory()) {
                String path = normalizeAbsolutePath(f).getPath().replace(File.separatorChar, '/');
                return TextUtils.isEmpty(path) ? "/" : path;
            }
            String root = canonicalRootDir.getCanonicalPath();
            String path = f.getCanonicalPath();
            if (path.equals(root)) return "/";
            String rel = path.substring(root.length()).replace(File.separatorChar, '/');
            return rel.startsWith("/") ? rel : "/" + rel;
        }

        private void reply(int code, String message) throws IOException {
            raw(code + " " + message);
        }

        private void raw(String value) throws IOException {
            out.write(value);
            out.write("\r\n");
            out.flush();
        }

        private void closePassive() {
            closeQuietly(passiveServer);
            passiveServer = null;
        }
    }

    private static final Comparator<FtpEntry> ENTRY_SORT = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        if (a.directory != b.directory) return a.directory ? -1 : 1;
        return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
    };

    private static final class FtpEntry {
        final String name;
        final boolean directory;
        final long size;
        final long modifiedMillis;
        final File file;

        FtpEntry(String name, boolean directory, long size, long modifiedMillis, File file) {
            this.name = name == null ? "" : name;
            this.directory = directory;
            this.size = size;
            this.modifiedMillis = modifiedMillis;
            this.file = file;
        }
    }

    private static String listLine(File f) {
        return listLine(f.getName(), f.isDirectory(), f.length(), f.lastModified());
    }

    private static String listLine(FtpEntry e) {
        return listLine(e.name, e.directory, e.size, e.modifiedMillis);
    }

    private static String listLine(String name, boolean directory, long size, long modifiedMillis) {
        String perms = directory ? "drwxr-xr-x" : "-rw-r--r--";
        String date = new SimpleDateFormat("MMM dd  yyyy", Locale.US).format(new Date(modifiedMillis));
        return String.format(Locale.US, "%s 1 anonymous anonymous %12d %s %s", perms, size, date, name);
    }

    private static String mlsdLine(File f) {
        return mlsdLine(f.getName(), f.isDirectory(), f.length(), f.lastModified());
    }

    private static String mlsdLine(FtpEntry e) {
        return mlsdLine(e.name, e.directory, e.size, e.modifiedMillis);
    }

    private static String mlsdLine(String name, boolean directory, long size, long modifiedMillis) {
        String type = directory ? "dir" : "file";
        return "type=" + type + ";size=" + size + ";modify=" + timestamp(modifiedMillis) + "; " + name;
    }

    private static String timestamp(long time) {
        return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date(time));
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private ShellResult shell(String command) {
        try {
            ShellAccess access = shellAccess;
            if (access == null || TextUtils.isEmpty(command)) return new ShellResult(1, "", "Shell access unavailable.");
            return access.run(command);
        } catch (Throwable t) {
            return new ShellResult(1, "", t.toString());
        }
    }

    private boolean shellOk(String command) {
        ShellResult r = shell(command);
        return r != null && r.exitCode == 0;
    }

    private long shellLong(String command, long fallback) {
        ShellResult r = shell(command);
        if (r == null || r.exitCode != 0) return fallback;
        return parseLong(firstLine(r.stdout), fallback);
    }

    private static String cleanPathArgument(String arg) {
        String path = arg == null ? "" : arg.trim();
        if ((path.startsWith("\"") && path.endsWith("\"")) || (path.startsWith("'") && path.endsWith("'"))) {
            path = path.substring(1, path.length() - 1);
        }
        return path;
    }

    private static String cleanListPathArgument(String arg) {
        String value = cleanPathArgument(arg);
        if (TextUtils.isEmpty(value)) return "";
        String[] parts = value.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = cleanPathArgument(parts[i]);
            if (!TextUtils.isEmpty(part) && !part.startsWith("-")) return part;
        }
        return "";
    }

    private static String shQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String firstLine(String text) {
        if (text == null) return "";
        int idx = text.indexOf('\n');
        return (idx >= 0 ? text.substring(0, idx) : text).trim();
    }

    private static long parseLong(String text, long fallback) {
        try { return Long.parseLong((text == null ? "" : text.trim())); } catch (Throwable ignored) { return fallback; }
    }

    private static String oneLine(String text) {
        return (text == null ? "" : text).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String safePath(File file) {
        try { return file == null ? "null" : file.getAbsolutePath(); } catch (Throwable ignored) { return "null"; }
    }

    private static String summarizeEntries(ArrayList<FtpEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int max = Math.min(entries.size(), 8);
        for (int i = 0; i < max; i++) {
            FtpEntry entry = entries.get(i);
            if (entry == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.directory ? "d:" : "f:").append(entry.name);
        }
        if (entries.size() > max) sb.append(", ...");
        return sb.toString();
    }

    private static String readAllQuiet(InputStream input) {
        if (input == null) return "";
        try {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = input.read(buf)) >= 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = input.read(buf)) >= 0) output.write(buf, 0, n);
    }

    private static void skipFully(BufferedInputStream input, long count) throws IOException {
        long remaining = count;
        while (remaining > 0L) {
            long skipped = input.skip(remaining);
            if (skipped <= 0L) {
                if (input.read() < 0) return;
                skipped = 1L;
            }
            remaining -= skipped;
        }
    }

    private static String firstIpv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (nif == null || !nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) return addr.getHostAddress();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void debug(String message) {
        if (!debugEnabled || TextUtils.isEmpty(message)) return;
        log("debug: " + message);
    }

    private void log(String message) {
        Listener l = listener;
        if (l != null && !TextUtils.isEmpty(message)) l.onFtpLog(message);
    }

    private void notifyState() {
        Listener l = listener;
        if (l != null) l.onFtpStateChanged();
    }

    private static void closeQuietly(Object closeable) {
        try {
            if (closeable instanceof ServerSocket) ((ServerSocket) closeable).close();
            else if (closeable instanceof Socket) ((Socket) closeable).close();
        } catch (Throwable ignored) {
        }
    }
}
