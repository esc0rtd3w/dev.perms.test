package dev.perms.test.debugging.editor;

import dev.perms.test.debugging.smali.PermsTestSmaliTools;

import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SmaliEditorFiles {
    public static final long TEXT_EDITOR_MAX_BYTES = 2L * 1024L * 1024L;
    public static final long INTERNAL_EDITOR_MAX_BYTES = TEXT_EDITOR_MAX_BYTES;

    private SmaliEditorFiles() {
    }

    public static boolean isSmaliFile(File file) {
        if (file == null) return false;
        String name = file.getName();
        return !TextUtils.isEmpty(name) && name.toLowerCase(Locale.US).endsWith(".smali");
    }

    public static boolean isJavaFile(File file) {
        if (file == null) return false;
        String name = file.getName();
        return !TextUtils.isEmpty(name) && name.toLowerCase(Locale.US).endsWith(".java");
    }

    public static boolean isSourceFile(File file) {
        return isSmaliFile(file) || isJavaFile(file);
    }

    public static File browseBaseDir(String workRootPath, String smaliOutPath, List<File> searchRoots, String fallbackRootPath) {
        try {
            File workRoot = TextUtils.isEmpty(workRootPath) ? null : new File(workRootPath);
            if (workRoot != null && workRoot.isDirectory()) return workRoot;

            File outDir = normalizeDirectory(smaliOutPath);
            File normalizedOut = normalizeSmaliFolderParent(outDir);
            if (normalizedOut != null && normalizedOut.isDirectory()) return normalizedOut;

            if (searchRoots != null) {
                for (File root : searchRoots) {
                    File dir = normalizeDirectory(root == null ? null : root.getAbsolutePath());
                    File normalized = normalizeSmaliFolderParent(dir);
                    if (normalized != null && normalized.isDirectory()) return normalized;
                }
            }

            if (workRoot != null) return workRoot;
        } catch (Throwable ignored) {
        }
        return new File(TextUtils.isEmpty(fallbackRootPath) ? PermsTestSmaliTools.DEFAULT_ROOT : fallbackRootPath);
    }

    public static void refreshBrowseList(File dir, File baseDir, TextView pathView,
                                  ArrayList<File> entries, ArrayList<String> labels,
                                  ArrayAdapter<String> adapter) {
        refreshBrowseList(dir, baseDir, pathView, entries, labels, adapter, false);
    }

    public static void refreshBrowseList(File dir, File baseDir, TextView pathView,
                                  ArrayList<File> entries, ArrayList<String> labels,
                                  ArrayAdapter<String> adapter, boolean allowOutsideBase) {
        if (entries == null || labels == null) return;
        entries.clear();
        labels.clear();
        File currentDir = dir;
        try {
            if (currentDir == null) currentDir = baseDir;
            if (currentDir != null && currentDir.isFile()) currentDir = currentDir.getParentFile();
            if (pathView != null) pathView.setText(currentDir == null ? "" : currentDir.getAbsolutePath());

            if (currentDir == null || !currentDir.isDirectory()) {
                entries.add(null);
                labels.add("Folder not found. Run Disassemble or DEX to Java first, then browse again.");
                return;
            }

            File parent = currentDir.getParentFile();
            if (parent != null && (allowOutsideBase
                    || (baseDir != null && !sameFile(currentDir, baseDir) && isInsideOrSame(parent, baseDir)))) {
                entries.add(parent);
                labels.add("../");
            }

            File[] children = currentDir.listFiles();
            ArrayList<File> dirs = new ArrayList<>();
            ArrayList<File> files = new ArrayList<>();
            if (children != null) {
                for (File child : children) {
                    if (child == null) continue;
                    if (child.isDirectory()) dirs.add(child);
                    else if (isSourceFile(child)) files.add(child);
                }
            }
            Comparator<File> byName = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
            try { Collections.sort(dirs, byName); } catch (Throwable ignored) {}
            try { Collections.sort(files, byName); } catch (Throwable ignored) {}

            for (File child : dirs) {
                entries.add(child);
                labels.add(child.getName() + "/");
            }
            for (File child : files) {
                entries.add(child);
                labels.add(child.getName());
            }
            if (labels.isEmpty()) {
                entries.add(null);
                labels.add("No folders or .smali/.java files here.");
            }
        } catch (Throwable t) {
            entries.clear();
            labels.clear();
            entries.add(null);
            labels.add("Browse failed: " + (t == null ? "unknown error" : t.getMessage()));
        } finally {
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    }

    public static boolean isAllowedSmaliFile(File file, List<File> roots) {
        if (!isSourceFile(file)) return false;
        try {
            String raw = file.getAbsolutePath().replace('\\', '/');
            if (raw.startsWith(PermsTestSmaliTools.DEFAULT_ROOT + "/")
                    || raw.startsWith("/sdcard/dev.perms.test/debugging/")
                    || raw.startsWith("/storage/emulated/0/dev.perms.test/debugging/")
                    || raw.startsWith("/storage/self/primary/dev.perms.test/debugging/")) {
                return true;
            }
            String canonical = file.getCanonicalPath();
            if (roots != null) {
                for (File root : roots) {
                    if (root == null) continue;
                    String rootPath;
                    try { rootPath = root.getCanonicalPath(); } catch (Throwable ignored) { rootPath = root.getAbsolutePath(); }
                    if (canonical.equals(rootPath) || canonical.startsWith(rootPath + File.separator)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static String readUtf8(File file, long maxBytes) throws IOException {
        if (file == null) return "";
        if (file.length() > maxBytes) {
            throw new IOException("File is too large for the direct text editor path: " + file.length() + " bytes");
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file));
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static void writeUtf8(File file, String text) throws IOException {
        if (file == null) throw new IOException("Choose a smali/java file first.");
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create folder: " + parent.getAbsolutePath());
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static int offsetForLine(String text, int line) {
        if (TextUtils.isEmpty(text) || line <= 1) return 0;
        int pos = 0;
        int currentLine = 1;
        while (pos < text.length() && currentLine < line) {
            int next = text.indexOf('\n', pos);
            if (next < 0) return pos;
            pos = next + 1;
            currentLine++;
        }
        return pos;
    }

    private static File normalizeDirectory(String path) {
        if (TextUtils.isEmpty(path)) return null;
        File f = new File(path.trim());
        if (f.isFile()) f = f.getParentFile();
        return f;
    }

    private static File normalizeSmaliFolderParent(File dir) {
        if (dir == null) return null;
        File parent = dir.getParentFile();
        if (parent != null && parent.isDirectory() && dir.getName().toLowerCase(Locale.US).startsWith("smali")) {
            return parent;
        }
        return dir;
    }

    private static boolean sameFile(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (Throwable ignored) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    private static boolean isInsideOrSame(File file, File root) {
        if (file == null || root == null) return false;
        try {
            String path = file.getCanonicalPath();
            String rootPath = root.getCanonicalPath();
            return path.equals(rootPath) || path.startsWith(rootPath + File.separator);
        } catch (Throwable ignored) {
            String path = file.getAbsolutePath();
            String rootPath = root.getAbsolutePath();
            return path.equals(rootPath) || path.startsWith(rootPath + File.separator);
        }
    }
}
