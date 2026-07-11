package dev.perms.test.plugins;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** File/staging layer for PermsTest plugin packages. */
public final class PluginRepository {
    private static final String ASSET_PLUGIN_DIR = "plugins";
    private static final String PUBLIC_HOME_RELATIVE = "dev.perms.test";
    private static final String PUBLIC_ROOT_RELATIVE = PUBLIC_HOME_RELATIVE + "/plugins";
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 512;
    private static final long MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L;

    public static final class BundledPluginInfo {
        public final String assetName;
        public final String assetPath;
        public final PluginManifest manifest;

        private BundledPluginInfo(String assetName, String assetPath, PluginManifest manifest) {
            this.assetName = assetName == null ? "" : assetName;
            this.assetPath = assetPath == null ? "" : assetPath;
            this.manifest = manifest;
        }
    }

    private final Context context;

    public PluginRepository(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }


    public File getPublicHomeDirectory() {
        File publicHome = new File(Environment.getExternalStorageDirectory(), PUBLIC_HOME_RELATIVE);
        ensureDir(publicHome);
        return publicHome;
    }

    public boolean ensurePublicHomeDirectory() {
        File home = getPublicHomeDirectory();
        return home != null && home.isDirectory();
    }

    public File getPluginRoot() {
        File publicRoot = new File(Environment.getExternalStorageDirectory(), PUBLIC_ROOT_RELATIVE);
        if (ensureDir(publicRoot)) return publicRoot;
        File fallback = context == null ? null : context.getExternalFilesDir("plugins");
        if (ensureDir(fallback)) return fallback;
        return publicRoot;
    }

    public List<PluginManifest> loadPlugins() {
        ArrayList<PluginManifest> out = new ArrayList<>();
        File root = getPluginRoot();
        File[] dirs = root == null ? null : root.listFiles();
        if (dirs == null) return out;
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) continue;
            String name = dir.getName();
            if (TextUtils.isEmpty(name) || name.startsWith(".") || name.startsWith("_")) continue;
            try {
                out.add(PluginManifest.fromDirectory(dir));
            } catch (Throwable ignored) {
            }
        }
        Collections.sort(out, Comparator.comparing(o -> o.name.toLowerCase(Locale.US)));
        return out;
    }

    public boolean hasStagedPluginDirectories() {
        File root = getPluginRoot();
        File[] dirs = root == null ? null : root.listFiles();
        if (dirs == null) return false;
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) continue;
            String name = dir.getName();
            if (TextUtils.isEmpty(name) || name.startsWith(".") || name.startsWith("_")) continue;
            return true;
        }
        return false;
    }

    public List<String> cleanInvalidPluginDirs() {
        ArrayList<String> removed = new ArrayList<>();
        File root = getPluginRoot();
        File[] dirs = root == null ? null : root.listFiles();
        if (dirs == null) return removed;
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) continue;
            String name = dir.getName();
            if (TextUtils.isEmpty(name) || name.startsWith(".") || name.startsWith("_")) continue;
            try {
                PluginManifest manifest = PluginManifest.fromDirectory(dir);
                PluginPackageValidator.requireReadyForStaging(manifest);
            } catch (Throwable ignored) {
                deleteRecursive(dir);
                removed.add(name);
            }
        }
        Collections.sort(removed);
        return removed;
    }

    public List<BundledPluginInfo> loadBundledPluginInfos() throws Exception {
        if (context == null) throw new IllegalStateException("Context unavailable");
        ArrayList<BundledPluginInfo> out = new ArrayList<>();
        String[] names = context.getAssets().list(ASSET_PLUGIN_DIR);
        if (names == null) return out;
        for (String name : names) {
            if (!isPluginAssetName(name)) continue;
            File temp = newTempArchive("inspect", archiveSuffix(name));
            try {
                try (InputStream in = context.getAssets().open(ASSET_PLUGIN_DIR + "/" + name);
                     OutputStream dst = new BufferedOutputStream(new FileOutputStream(temp))) {
                    copyStream(in, dst);
                }
                PluginManifest manifest = inspectArchive(temp);
                if (manifest != null) {
                    out.add(new BundledPluginInfo(name, ASSET_PLUGIN_DIR + "/" + name, manifest));
                }
            } finally {
                try { temp.delete(); } catch (Throwable ignored) {}
            }
        }
        Collections.sort(out, Comparator.comparing(o -> o.manifest == null ? o.assetName.toLowerCase(Locale.US) : o.manifest.name.toLowerCase(Locale.US)));
        return out;
    }

    public List<PluginManifest> installBundledPlugins() throws Exception {
        return installBundledPlugins(null);
    }

    public List<PluginManifest> installBundledPlugins(List<String> assetNames) throws Exception {
        if (context == null) throw new IllegalStateException("Context unavailable");
        ArrayList<PluginManifest> installed = new ArrayList<>();
        Set<String> selected = null;
        if (assetNames != null && !assetNames.isEmpty()) {
            selected = new HashSet<>();
            for (String name : assetNames) {
                if (isPluginAssetName(name)) selected.add(name);
            }
        }
        String[] names = context.getAssets().list(ASSET_PLUGIN_DIR);
        if (names == null) return installed;
        for (String name : names) {
            if (!isPluginAssetName(name)) continue;
            if (selected != null && !selected.contains(name)) continue;
            installed.add(installBundledAsset(name));
        }
        Collections.sort(installed, Comparator.comparing(o -> o.name.toLowerCase(Locale.US)));
        return installed;
    }

    public PluginManifest installBundledAsset(String assetName) throws Exception {
        if (context == null) throw new IllegalStateException("Context unavailable");
        if (!isPluginAssetName(assetName)) throw new IllegalArgumentException("Invalid bundled plugin asset: " + assetName);
        File temp = newTempArchive("internal", archiveSuffix(assetName));
        try {
            try (InputStream in = context.getAssets().open(ASSET_PLUGIN_DIR + "/" + assetName);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                copyStream(in, out);
            }
            return installArchive(temp);
        } finally {
            try { temp.delete(); } catch (Throwable ignored) {}
        }
    }

    public PluginManifest installUri(Uri uri, String fallbackName) throws Exception {
        if (context == null) throw new IllegalStateException("Context unavailable");
        if (uri == null) throw new IllegalArgumentException("Plugin URI is empty");
        String suffix = archiveSuffix(fallbackName);
        File temp = newTempArchive("picked", suffix);
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                if (in == null) throw new IllegalArgumentException("Unable to open plugin URI");
                copyStream(in, out);
            }
            return installArchive(temp);
        } finally {
            try { temp.delete(); } catch (Throwable ignored) {}
        }
    }

    public PluginManifest installPath(String rawPath) throws Exception {
        if (TextUtils.isEmpty(rawPath)) throw new IllegalArgumentException("Plugin path is empty");
        String trimmed = rawPath.trim();
        if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
            return installUri(Uri.parse(trimmed), trimmed);
        }
        File source = new File(trimmed);
        if (source.isDirectory()) return installDirectory(source);
        if (source.isFile()) return installArchive(source);
        throw new IllegalArgumentException("Plugin path not found: " + trimmed);
    }

    public List<String> listPluginFilesUnder(String pluginId, String relativeDirectory) throws Exception {
        if (!PluginManifest.isSafeId(pluginId)) throw new IllegalArgumentException("Invalid plugin id: " + pluginId);
        String normalizedDir = normalizeRelativePath(relativeDirectory);
        File pluginDir = new File(getPluginRoot(), pluginId);
        if (!pluginDir.isDirectory()) return new ArrayList<>();
        File base = TextUtils.isEmpty(normalizedDir) ? pluginDir : new File(pluginDir, normalizedDir);
        String pluginRootPath = pluginDir.getCanonicalPath() + File.separator;
        String basePath = base.getCanonicalPath();
        if (!basePath.equals(pluginDir.getCanonicalPath()) && !basePath.startsWith(pluginRootPath)) {
            throw new IllegalArgumentException("Unsafe plugin directory: " + relativeDirectory);
        }
        ArrayList<String> out = new ArrayList<>();
        collectRelativeFiles(pluginDir, base, out);
        Collections.sort(out);
        return out;
    }

    public boolean deletePluginFile(String pluginId, String relativePath) throws Exception {
        if (!PluginManifest.isSafeId(pluginId)) throw new IllegalArgumentException("Invalid plugin id: " + pluginId);
        String normalized = normalizeRelativePath(relativePath);
        if (TextUtils.isEmpty(normalized)) throw new IllegalArgumentException("Plugin-relative file path is empty");
        File pluginDir = new File(getPluginRoot(), pluginId);
        String rootPath = pluginDir.getCanonicalPath() + File.separator;
        File target = new File(pluginDir, normalized);
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath)) throw new IllegalArgumentException("Unsafe plugin file target: " + relativePath);
        if (!target.exists()) return false;
        if (!target.isFile()) throw new IllegalArgumentException("Plugin asset target is not a file: " + relativePath);
        boolean deleted = target.delete();
        if (deleted) removeEmptyParents(pluginDir, target.getParentFile());
        return deleted;
    }

    private static void collectRelativeFiles(File root, File current, List<String> out) throws Exception {
        if (root == null || current == null || out == null || !current.exists()) return;
        String rootCanonical = root.getCanonicalPath();
        String rootPath = rootCanonical + File.separator;
        String currentPath = current.getCanonicalPath();
        if (!currentPath.equals(rootCanonical) && !currentPath.startsWith(rootPath)) return;
        if (current.isFile()) {
            out.add(currentPath.substring(rootPath.length()).replace(File.separatorChar, '/'));
            return;
        }
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child == null || child.getName().startsWith(".copy_") || child.getName().startsWith(".backup_")) continue;
            collectRelativeFiles(root, child, out);
        }
    }

    private static void removeEmptyParents(File root, File current) {
        try {
            String rootPath = root.getCanonicalPath();
            while (current != null && current.isDirectory() && !current.getCanonicalPath().equals(rootPath)) {
                File[] children = current.listFiles();
                if (children != null && children.length > 0) break;
                File parent = current.getParentFile();
                if (!current.delete()) break;
                current = parent;
            }
        } catch (Throwable ignored) {
        }
    }

    private static String normalizeRelativePath(String relativePath) {
        if (TextUtils.isEmpty(relativePath)) return "";
        String normalized = relativePath.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new IllegalArgumentException("Unsafe plugin-relative path: " + relativePath);
        }
        String[] parts = normalized.split("/", -1);
        for (String part : parts) {
            if (TextUtils.isEmpty(part) || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("Unsafe plugin-relative path: " + relativePath);
            }
        }
        return normalized;
    }

    public File copyUriToPluginFile(String pluginId, Uri uri, String relativePath, long maxBytes) throws Exception {
        if (context == null) throw new IllegalStateException("Context unavailable");
        if (!PluginManifest.isSafeId(pluginId)) throw new IllegalArgumentException("Invalid plugin id: " + pluginId);
        if (uri == null) throw new IllegalArgumentException("Source URI is empty");
        if (TextUtils.isEmpty(relativePath)) throw new IllegalArgumentException("Plugin-relative file path is empty");

        String normalized = relativePath.trim().replace('\\', '/');
        if (TextUtils.isEmpty(normalized) || normalized.startsWith("/") || normalized.contains(":")) {
            throw new IllegalArgumentException("Unsafe plugin-relative file path: " + relativePath);
        }
        String[] pathParts = normalized.split("/", -1);
        for (String part : pathParts) {
            if (TextUtils.isEmpty(part) || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("Unsafe plugin-relative file path: " + relativePath);
            }
        }

        File pluginDir = new File(getPluginRoot(), pluginId);
        ensureDir(pluginDir);
        String pluginRootPath = pluginDir.getCanonicalPath() + File.separator;
        File outFile = new File(pluginDir, normalized);
        String outPath = outFile.getCanonicalPath();
        if (!outPath.startsWith(pluginRootPath)) {
            throw new IllegalArgumentException("Unsafe plugin file target: " + relativePath);
        }
        ensureDir(outFile.getParentFile());

        long stamp = System.currentTimeMillis();
        File tempFile = new File(outFile.getParentFile(), ".copy_" + outFile.getName() + "_" + stamp);
        File backupFile = new File(outFile.getParentFile(), ".backup_" + outFile.getName() + "_" + stamp);
        long limit = maxBytes <= 0L ? Long.MAX_VALUE : maxBytes;
        boolean replacementStarted = false;
        boolean backupMade = false;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                if (in == null) throw new IllegalArgumentException("Unable to open selected file URI");
                byte[] buffer = new byte[BUFFER_SIZE];
                long total = 0L;
                int r;
                while ((r = in.read(buffer)) > 0) {
                    total += r;
                    if (total > limit) {
                        throw new IllegalArgumentException("Selected file exceeds the allowed size of " + limit + " bytes");
                    }
                    out.write(buffer, 0, r);
                }
                out.flush();
            }
            if (outFile.exists()) {
                if (!outFile.isFile()) {
                    throw new IllegalStateException("Plugin file target is not a regular file: " + relativePath);
                }
                if (!outFile.renameTo(backupFile)) {
                    throw new IllegalStateException("Unable to prepare existing plugin file for replacement: " + relativePath);
                }
                backupMade = true;
            }
            replacementStarted = true;
            if (!tempFile.renameTo(outFile)) {
                copyRecursive(tempFile, outFile);
                try { tempFile.delete(); } catch (Throwable ignored) {}
            }
            if (backupMade) {
                try { backupFile.delete(); } catch (Throwable ignored) {}
            }
            return outFile;
        } catch (Throwable t) {
            try { tempFile.delete(); } catch (Throwable ignored) {}
            if (replacementStarted) {
                try { deleteRecursive(outFile); } catch (Throwable ignored) {}
            }
            if (backupMade && !backupFile.renameTo(outFile)) {
                throw new IllegalStateException("Unable to restore the previous plugin file after copy failure: " + relativePath, t);
            }
            if (t instanceof Exception) throw (Exception) t;
            throw new IllegalStateException("Unable to copy selected plugin file", t);
        } finally {
            try { tempFile.delete(); } catch (Throwable ignored) {}
            if (!backupMade || outFile.exists()) {
                try { backupFile.delete(); } catch (Throwable ignored) {}
            }
        }
    }

    private PluginManifest installArchive(File archive) throws Exception {
        if (archive == null || !archive.isFile()) throw new IllegalArgumentException("Plugin archive not found");
        File tempDir = newArchiveWorkDir("plugin");
        try {
            unzipSafe(archive, tempDir);
            File manifestFile = findManifestFile(tempDir, 0);
            if (manifestFile == null) throw new IllegalArgumentException("Archive does not contain " + PluginManifest.FILE_NAME);
            File pluginSourceDir = manifestFile.getParentFile();
            PluginManifest manifest = PluginManifest.fromFile(manifestFile, pluginSourceDir);
            PluginPackageValidator.requireReadyForStaging(manifest);
            return replaceInstalledPlugin(pluginSourceDir, manifest);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    public PluginManifest inspectArchive(File archive) throws Exception {
        if (archive == null || !archive.isFile()) throw new IllegalArgumentException("Plugin archive not found");
        File tempDir = newArchiveWorkDir("inspect");
        try {
            unzipSafe(archive, tempDir);
            File manifestFile = findManifestFile(tempDir, 0);
            if (manifestFile == null) throw new IllegalArgumentException("Archive does not contain " + PluginManifest.FILE_NAME);
            PluginManifest manifest = PluginManifest.fromFile(manifestFile, manifestFile.getParentFile());
            PluginPackageValidator.requireReadyForStaging(manifest);
            return manifest;
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private PluginManifest installDirectory(File directory) throws Exception {
        if (directory == null || !directory.isDirectory()) throw new IllegalArgumentException("Plugin directory not found");
        File manifestFile = findManifestFile(directory, 0);
        if (manifestFile == null) throw new IllegalArgumentException("Directory does not contain " + PluginManifest.FILE_NAME);
        File pluginSourceDir = manifestFile.getParentFile();
        PluginManifest manifest = PluginManifest.fromFile(manifestFile, pluginSourceDir);
        PluginPackageValidator.requireReadyForStaging(manifest);
        return replaceInstalledPlugin(pluginSourceDir, manifest);
    }

    private PluginManifest replaceInstalledPlugin(File sourceDir, PluginManifest manifest) throws Exception {
        if (sourceDir == null || manifest == null) throw new IllegalArgumentException("Invalid plugin source");
        File target = new File(getPluginRoot(), manifest.id);
        File replaceTemp = new File(getPluginRoot(), ".replace_" + manifest.id + "_" + System.currentTimeMillis());
        deleteRecursive(replaceTemp);
        copyRecursive(sourceDir, replaceTemp);
        deleteRecursive(target);
        if (!replaceTemp.renameTo(target)) {
            copyRecursive(replaceTemp, target);
            deleteRecursive(replaceTemp);
        }
        return PluginManifest.fromDirectory(target);
    }

    private File findManifestFile(File dir, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 3) return null;
        File direct = new File(dir, PluginManifest.FILE_NAME);
        if (direct.isFile()) return direct;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child != null && child.isDirectory()) {
                File found = findManifestFile(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void unzipSafe(File archive, File targetDir) throws Exception {
        String canonicalTarget = targetDir.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];
            int entryCount = 0;
            long totalBytes = 0L;
            while ((entry = zin.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("Plugin archive has too many entries");
                }
                String name = entry.getName();
                if (TextUtils.isEmpty(name) || name.contains("\u0000")) continue;
                File outFile = new File(targetDir, name);
                String canonicalOut = outFile.getCanonicalPath();
                if (!canonicalOut.equals(targetDir.getCanonicalPath()) && !canonicalOut.startsWith(canonicalTarget)) {
                    throw new IllegalArgumentException("Unsafe plugin archive path: " + name);
                }
                if (entry.isDirectory()) {
                    ensureDir(outFile);
                    continue;
                }
                File parent = outFile.getParentFile();
                ensureDir(parent);
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    int r;
                    while ((r = zin.read(buffer)) > 0) {
                        totalBytes += r;
                        if (totalBytes > MAX_ARCHIVE_BYTES) {
                            throw new IllegalArgumentException("Plugin archive is too large");
                        }
                        out.write(buffer, 0, r);
                    }
                    out.flush();
                }
            }
        }
    }

    public void uninstallPlugin(String id) throws Exception {
        if (!PluginManifest.isSafeId(id)) throw new IllegalArgumentException("Invalid plugin id: " + id);
        File root = getPluginRoot();
        File target = new File(root, id);
        String rootPath = root.getCanonicalPath() + File.separator;
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath)) {
            throw new IllegalArgumentException("Unsafe plugin target: " + id);
        }
        deleteRecursive(target);
    }

    private void copyRecursive(File source, File target) throws Exception {
        if (source == null || target == null) return;
        if (source.isDirectory()) {
            ensureDir(target);
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) copyRecursive(child, new File(target, child.getName()));
            return;
        }
        File parent = target.getParentFile();
        ensureDir(parent);
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            copyStream(in, out);
        }
    }

    public static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        try { file.delete(); } catch (Throwable ignored) {}
    }

    private static boolean ensureDir(File dir) {
        if (dir == null) return false;
        try {
            return dir.isDirectory() || dir.mkdirs();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private File newTempArchive(String prefix, String suffix) throws Exception {
        File imports = new File(getPluginRoot(), ".imports");
        ensureDir(imports);
        return File.createTempFile(prefix + "_", suffix, imports);
    }

    private File newArchiveWorkDir(String prefix) throws Exception {
        File stagingRoot = new File(getPluginRoot(), ".staging");
        ensureDir(stagingRoot);
        File tempDir = new File(stagingRoot, prefix + "_" + System.currentTimeMillis() + "_" + Math.abs((int) System.nanoTime()));
        ensureDir(tempDir);
        return tempDir;
    }

    private static boolean isPluginAssetName(String name) {
        if (TextUtils.isEmpty(name)) return false;
        if (name.contains("/") || name.contains("\\") || name.contains("..") || name.contains("\u0000")) return false;
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".ptp") || lower.endsWith(".zip");
    }

    private static String archiveSuffix(String name) {
        if (TextUtils.isEmpty(name)) return ".ptp";
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".zip")) return ".zip";
        return ".ptp";
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[BUFFER_SIZE];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        out.flush();
    }
}
