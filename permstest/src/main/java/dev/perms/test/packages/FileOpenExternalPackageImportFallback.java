package dev.perms.test.packages;

import dev.perms.test.debug.PackageInstallDebug;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Generic fallback importer for broken external-app APK handoffs.
 *
 * Some sender apps start PermsTest with a file:// URI, a bare filename, or a content:// URI
 * that no longer grants readable access. Normal ContentResolver importing must always run first.
 * This helper only runs after that path fails. It uses generic filename/path/package hints from
 * the incoming intent to copy the same package into PermsTest's managed import directory, then
 * FileOpenInstallActivity continues through the normal install and cleanup flow.
 */
final class FileOpenExternalPackageImportFallback {

    interface ShellReadyChecker {
        boolean isReady();
    }

    interface ImportsDirProvider {
        File getImportsDir();
    }

    interface ShellRunner {
        ShellResult run(String command);
    }

    interface FailureRecorder {
        void record(String phase, Throwable t);
    }

    static final class ShellResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final Context context;
    private final LinkedHashSet<String> sourcePackageHints;
    private final ShellReadyChecker shellReadyChecker;
    private final ImportsDirProvider importsDirProvider;
    private final ShellRunner shellRunner;
    private final FailureRecorder failureRecorder;

    FileOpenExternalPackageImportFallback(
            Context context,
            LinkedHashSet<String> sourcePackageHints,
            ShellReadyChecker shellReadyChecker,
            ImportsDirProvider importsDirProvider,
            ShellRunner shellRunner,
            FailureRecorder failureRecorder) {
        this.context = context;
        this.sourcePackageHints = sanitizePackageHints(sourcePackageHints);
        this.shellReadyChecker = shellReadyChecker;
        this.importsDirProvider = importsDirProvider;
        this.shellRunner = shellRunner;
        this.failureRecorder = failureRecorder;
    }

    File copyMissingIncomingPackage(Uri uri, String label) {
        try {
            File dir = importsDirProvider == null ? null : importsDirProvider.getImportsDir();
            if (dir == null) {
                recordFailure("external import fallback", new IOException("imports dir is unavailable"));
                return null;
            }
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
                recordFailure("external import fallback",
                        new IOException("imports dir could not be created: " + dir.getAbsolutePath()));
                return null;
            }

            LinkedHashSet<String> names = buildIncomingPackageCandidateNames(uri, label);
            LinkedHashSet<String> exactPaths = buildIncomingPackageCandidatePaths(uri);
            if (names.isEmpty() && exactPaths.isEmpty()) {
                recordFailure("external import fallback", new IOException("no filename/path candidates were available"));
                return null;
            }

            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                    "external import fallback begin names=" + names.size()
                            + ", exactPaths=" + exactPaths.size()
                            + ", sourcePackageHints=" + sourcePackageHints.size());

            File shellCopied = tryShellCopy(dir, names, exactPaths);
            if (shellCopied != null) return shellCopied;

            File rootCopied = tryRootCopy(dir, names, exactPaths);
            if (rootCopied != null) return rootCopied;

            recordFailure("external import fallback",
                    new IOException("unable to find/copy matching package from external handoff locations"));
        } catch (Throwable t) {
            recordFailure("external import fallback", t);
        }
        return null;
    }

    private File tryShellCopy(File dir, LinkedHashSet<String> names, LinkedHashSet<String> exactPaths) {
        if (shellReadyChecker == null || !shellReadyChecker.isReady()) {
            PackageInstallDebug.warn(PackageInstallDebug.Area.FILE_OPEN,
                    "shell import fallback skipped: shell backend is not ready/granted");
            return null;
        }
        LinkedHashSet<String> namesToTry = names.isEmpty() ? buildNamesFromPaths(exactPaths) : names;
        for (String name : namesToTry) {
            if (TextUtils.isEmpty(name)) continue;
            String safeName = sanitizePackageFileName(name);
            if (TextUtils.isEmpty(safeName)) continue;

            File out = new File(dir, System.currentTimeMillis() + "_" + safeName);
            String cmd = buildShellImportCommand(safeName, out.getAbsolutePath(), exactPaths, false);
            ShellResult r = shellRunner == null ? null : shellRunner.run(cmd);
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                    "shell import fallback result name=" + safeName
                            + ", exit=" + (r == null ? -1 : r.exitCode)
                            + ", stdout=" + trimForLog(r == null ? "" : r.stdout)
                            + ", stderr=" + trimForLog(r == null ? "" : r.stderr));

            if (r != null && r.exitCode == 0 && out.isFile() && out.length() > 0L) {
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                        "shell import fallback copied package to imports: "
                                + PackageInstallDebug.describePath(out.getAbsolutePath())
                                + ", bytes=" + out.length());
                return out;
            }
            deleteQuietly(out);
        }
        return null;
    }

    private File tryRootCopy(File dir, LinkedHashSet<String> names, LinkedHashSet<String> exactPaths) {
        LinkedHashSet<String> namesToTry = names.isEmpty() ? buildNamesFromPaths(exactPaths) : names;
        for (String name : namesToTry) {
            if (TextUtils.isEmpty(name)) continue;
            String safeName = sanitizePackageFileName(name);
            if (TextUtils.isEmpty(safeName)) continue;

            File out = new File(dir, System.currentTimeMillis() + "_" + safeName);
            String cmd = buildShellImportCommand(safeName, out.getAbsolutePath(), exactPaths, true);
            ShellResult r = runSuCommand(cmd);
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                    "root import fallback result name=" + safeName
                            + ", exit=" + r.exitCode
                            + ", stdout=" + trimForLog(r.stdout)
                            + ", stderr=" + trimForLog(r.stderr));

            if (r.exitCode == 0 && out.isFile() && out.length() > 0L) {
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                        "root import fallback copied package to imports: "
                                + PackageInstallDebug.describePath(out.getAbsolutePath())
                                + ", bytes=" + out.length());
                return out;
            }
            deleteQuietly(out);
        }
        return null;
    }

    private LinkedHashSet<String> buildIncomingPackageCandidateNames(Uri uri, String label) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        addPackageCandidateName(names, label);
        try { addPackageCandidateName(names, uri == null ? null : uri.getLastPathSegment()); } catch (Throwable ignored) {}
        try { addPackageCandidateName(names, uri == null ? null : uri.getPath()); } catch (Throwable ignored) {}
        try { addPackageCandidateName(names, uri == null ? null : Uri.decode(uri.toString())); } catch (Throwable ignored) {}
        return names;
    }

    private LinkedHashSet<String> buildNamesFromPaths(LinkedHashSet<String> paths) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (paths == null) return names;
        for (String path : paths) {
            addPackageCandidateName(names, path);
        }
        return names;
    }

    private void addPackageCandidateName(LinkedHashSet<String> names, String raw) {
        try {
            String name = sanitizePackageFileName(raw);
            if (TextUtils.isEmpty(name)) return;
            if (!hasSupportedPackageExtension(name)) return;
            names.add(name);
        } catch (Throwable ignored) {}
    }

    private String sanitizePackageFileName(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        String value = raw.trim();
        try { value = Uri.decode(value); } catch (Throwable ignored) {}
        if (TextUtils.isEmpty(value)) return null;
        if (value.startsWith("raw:")) value = value.substring(4);
        int q = value.indexOf('?');
        if (q >= 0) value = value.substring(0, q);
        int hash = value.indexOf('#');
        if (hash >= 0) value = value.substring(0, hash);
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < value.length()) value = value.substring(slash + 1);
        value = value.replaceAll("[\\\\/:*?\\\"<>|\\r\\n]", "_").trim();
        if (value.length() > 160) value = value.substring(value.length() - 160);
        return value.isEmpty() ? null : value;
    }

    private LinkedHashSet<String> buildIncomingPackageCandidatePaths(Uri uri) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        try {
            if (uri == null) return paths;
            if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
                addShellCandidatePath(paths, uri.getPath());
            }

            String docId = null;
            try {
                if (context != null && DocumentsContract.isDocumentUri(context, uri)) {
                    docId = DocumentsContract.getDocumentId(uri);
                }
            } catch (Throwable ignored) {}
            if (!TextUtils.isEmpty(docId)) {
                addDecodedShellCandidatePath(paths, docId);
                if (docId.startsWith("raw:")) {
                    addShellCandidatePath(paths, docId.substring(4));
                }
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    String[] parts = docId.split(":", 2);
                    if (parts.length == 2 && "primary".equalsIgnoreCase(parts[0])) {
                        String rel = parts[1].startsWith("/") ? parts[1].substring(1) : parts[1];
                        addShellCandidatePath(paths, "/storage/emulated/0/" + rel);
                        addShellCandidatePath(paths, "/sdcard/" + rel);
                    }
                }
            }

            String last = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(last)) {
                addDecodedShellCandidatePath(paths, last);
                String decoded = Uri.decode(last);
                if (!TextUtils.isEmpty(decoded) && decoded.startsWith("raw:")) {
                    addShellCandidatePath(paths, decoded.substring(4));
                }
            }

            addDecodedShellCandidatePath(paths, uri.getPath());
        } catch (Throwable ignored) {}
        return paths;
    }

    private void addDecodedShellCandidatePath(LinkedHashSet<String> paths, String raw) {
        try {
            if (TextUtils.isEmpty(raw)) return;
            addShellCandidatePath(paths, Uri.decode(raw));
        } catch (Throwable ignored) {}
    }

    private void addShellCandidatePath(LinkedHashSet<String> paths, String raw) {
        try {
            if (TextUtils.isEmpty(raw)) return;
            String path = raw.trim();
            if (path.startsWith("raw:")) path = path.substring(4);
            if (!path.startsWith("/")) return;
            if (!hasSupportedPackageExtension(path)) return;
            paths.add(path);
        } catch (Throwable ignored) {}
    }

    private String buildShellImportCommand(String fileName, String outPath, LinkedHashSet<String> exactPaths, boolean includeRootPrivateSearch) {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(shQuote(fileName)).append("; ");
        sb.append("out=").append(shQuote(outPath)).append("; ");
        sb.append("found=''; ");

        appendExactPathProbe(sb, exactPaths);
        appendPublicDropFolderProbe(sb);
        appendSourcePackageExternalProbe(sb);
        appendGenericExternalSearchProbe(sb);
        if (includeRootPrivateSearch) {
            appendSourcePackagePrivateProbe(sb);
            appendGenericPrivateSearchProbe(sb);
        }
        appendCopyResult(sb, outPath);
        return sb.toString();
    }

    private void appendExactPathProbe(StringBuilder sb, LinkedHashSet<String> exactPaths) {
        if (exactPaths == null || exactPaths.isEmpty()) return;
        sb.append("for p in ");
        for (String path : exactPaths) {
            if (!TextUtils.isEmpty(path)) sb.append(shQuote(path)).append(' ');
        }
        sb.append("; do if [ -f \"$p\" ]; then found=\"$p\"; break; fi; done; ");
    }

    private void appendPublicDropFolderProbe(StringBuilder sb) {
        sb.append("if [ -z \"$found\" ]; then ");
        sb.append("for p in ")
                .append("\"/storage/emulated/0/Download/$name\" ")
                .append("\"/storage/emulated/0/Downloads/$name\" ")
                .append("\"/storage/emulated/0/Documents/$name\" ")
                .append("\"/storage/emulated/0/APK/$name\" ")
                .append("\"/storage/emulated/0/APKs/$name\" ")
                .append("\"/storage/emulated/0/Packages/$name\" ")
                .append("\"/storage/emulated/0/Temp/$name\" ")
                .append("\"/storage/emulated/0/tmp/$name\" ")
                .append("\"/sdcard/Download/$name\" ")
                .append("\"/sdcard/Downloads/$name\" ")
                .append("\"/sdcard/Documents/$name\" ")
                .append("\"/sdcard/APK/$name\" ")
                .append("\"/sdcard/APKs/$name\" ")
                .append("\"/sdcard/Packages/$name\" ")
                .append("\"/sdcard/Temp/$name\" ")
                .append("\"/sdcard/tmp/$name\" ")
                .append("; do if [ -f \"$p\" ]; then found=\"$p\"; break; fi; done; ");
        sb.append("fi; ");
    }

    private void appendSourcePackageExternalProbe(StringBuilder sb) {
        if (sourcePackageHints.isEmpty()) return;
        sb.append("if [ -z \"$found\" ]; then for pkg in ");
        for (String pkg : sourcePackageHints) sb.append(shQuote(pkg)).append(' ');
        sb.append("; do for d in ")
                .append("\"/storage/emulated/0/Android/data/$pkg\" ")
                .append("\"/storage/emulated/0/Android/media/$pkg\" ")
                .append("\"/sdcard/Android/data/$pkg\" ")
                .append("\"/sdcard/Android/media/$pkg\" ")
                .append("; do if [ -d \"$d\" ]; then ")
                .append("found=$(find \"$d\" -maxdepth 10 -type f -name \"$name\" 2>/dev/null | head -n 1); ")
                .append("[ -n \"$found\" ] && break; fi; done; ")
                .append("[ -n \"$found\" ] && break; done; fi; ");
    }

    private void appendGenericExternalSearchProbe(StringBuilder sb) {
        sb.append("if [ -z \"$found\" ]; then ");
        sb.append("for spec in ")
                .append("'/storage/emulated/0/Download|8' ")
                .append("'/storage/emulated/0/Downloads|8' ")
                .append("'/storage/emulated/0/Documents|8' ")
                .append("'/storage/emulated/0/Android/data|8' ")
                .append("'/storage/emulated/0/Android/media|8' ")
                .append("'/storage/emulated/0|4' ")
                .append("'/sdcard/Download|8' ")
                .append("'/sdcard/Downloads|8' ")
                .append("'/sdcard/Documents|8' ")
                .append("'/sdcard/Android/data|8' ")
                .append("'/sdcard/Android/media|8' ")
                .append("'/sdcard|4' ")
                .append("; do ");
        sb.append("d=${spec%|*}; depth=${spec##*|}; ");
        sb.append("if [ -d \"$d\" ]; then ");
        sb.append("found=$(find \"$d\" -maxdepth \"$depth\" -type f -name \"$name\" 2>/dev/null | head -n 1); ");
        sb.append("[ -n \"$found\" ] && break; ");
        sb.append("fi; ");
        sb.append("done; fi; ");
    }

    private void appendSourcePackagePrivateProbe(StringBuilder sb) {
        if (sourcePackageHints.isEmpty()) return;
        sb.append("if [ -z \"$found\" ]; then for pkg in ");
        for (String pkg : sourcePackageHints) sb.append(shQuote(pkg)).append(' ');
        sb.append("; do for d in ")
                .append("\"/data/user/0/$pkg\" ")
                .append("\"/data/data/$pkg\" ")
                .append("\"/data/user_de/0/$pkg\" ")
                .append("; do if [ -d \"$d\" ]; then ")
                .append("found=$(find \"$d\" -maxdepth 8 -type f -name \"$name\" 2>/dev/null | head -n 1); ")
                .append("[ -n \"$found\" ] && break; fi; done; ")
                .append("[ -n \"$found\" ] && break; done; fi; ");
    }

    private void appendGenericPrivateSearchProbe(StringBuilder sb) {
        sb.append("if [ -z \"$found\" ]; then ");
        sb.append("for spec in '/data/user/0|6' '/data/data|6' '/data/user_de/0|6'; do ");
        sb.append("d=${spec%|*}; depth=${spec##*|}; ");
        sb.append("if [ -d \"$d\" ]; then ");
        sb.append("found=$(find \"$d\" -maxdepth \"$depth\" -type f -name \"$name\" 2>/dev/null | head -n 1); ");
        sb.append("[ -n \"$found\" ] && break; ");
        sb.append("fi; done; fi; ");
    }

    private void appendCopyResult(StringBuilder sb, String outPath) {
        sb.append("if [ -n \"$found\" ] && [ -f \"$found\" ]; then ");
        sb.append("mkdir -p ").append(shQuote(new File(outPath).getParent())).append(" && ");
        sb.append("cp -f \"$found\" \"$out\" && chmod 666 \"$out\" && printf '%s\\n' \"$found\"; exit 0; ");
        sb.append("fi; ");
        sb.append("printf '%s\\n' 'not found'; exit 2");
    }

    private ShellResult runSuCommand(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(false).start();
            String stdout = readProcessStream(process.getInputStream());
            String stderr = readProcessStream(process.getErrorStream());
            int exit = process.waitFor();
            return new ShellResult(exit, stdout, stderr);
        } catch (Throwable t) {
            recordFailure("root import fallback", t);
            return new ShellResult(127, "", t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            try { if (process != null) process.destroy(); } catch (Throwable ignored) {}
        }
    }

    private String readProcessStream(java.io.InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
                if (sb.length() > 4096) {
                    sb.append("\n...");
                    break;
                }
            }
        }
        return sb.toString();
    }

    private LinkedHashSet<String> sanitizePackageHints(LinkedHashSet<String> rawHints) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (rawHints == null) return hints;
        for (String raw : rawHints) {
            if (TextUtils.isEmpty(raw)) continue;
            String pkg = raw.trim();
            if (pkg.startsWith("android-app://")) {
                try {
                    Uri parsed = Uri.parse(pkg);
                    pkg = parsed == null ? "" : parsed.getHost();
                } catch (Throwable ignored) {}
            }
            if (isSafePackageName(pkg)) hints.add(pkg);
        }
        return hints;
    }

    private boolean isSafePackageName(String pkg) {
        return !TextUtils.isEmpty(pkg)
                && pkg.length() <= 180
                && !"dev.perms.test".equals(pkg)
                && pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    }

    private boolean hasSupportedPackageExtension(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.endsWith(".apk") || lower.endsWith(".apks") || lower.endsWith(".apkm")
                || lower.endsWith(".xapk") || lower.endsWith(".zip");
    }

    private void recordFailure(String phase, Throwable t) {
        if (failureRecorder != null) {
            failureRecorder.record(phase, t);
        }
    }

    private void deleteQuietly(File file) {
        try { if (file != null && file.exists()) file.delete(); } catch (Throwable ignored) {}
    }

    private String trimForLog(String s) {
        if (s == null) return "";
        String v = s.trim().replace('\n', ' ');
        return v.length() > 240 ? v.substring(0, 240) + "..." : v;
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
