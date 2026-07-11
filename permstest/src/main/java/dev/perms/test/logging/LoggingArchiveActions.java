package dev.perms.test.logging;

import android.app.Activity;
import android.os.Environment;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.storage.StorageAccessController;

/**
 * Handles whole-log archive and cleanup actions for the Logging tab.
 */
public final class LoggingArchiveActions {
    private static final String PUBLIC_ROOT = "dev.perms.test";
    private static final String PUBLIC_LOG_DIR = "logs";
    private static final String PUBLIC_ARCHIVE_DIR = "log_archives";
    private static final String PLUGIN_EXPORT_DIR = "plugin_exports";

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final OutputAppender outputAppender;
    private final Runnable refreshStatus;
    private final LastSavedFileSetter lastSavedFileSetter;
    private final ExecutorService fileIo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PermsTestLogArchive");
        t.setDaemon(true);
        return t;
    });

    public LoggingArchiveActions(Activity activity,
                                 ActivityMainBinding binding,
                                 OutputAppender outputAppender,
                                 Runnable refreshStatus,
                                 LastSavedFileSetter lastSavedFileSetter) {
        this.activity = activity;
        this.binding = binding;
        this.outputAppender = outputAppender;
        this.refreshStatus = refreshStatus;
        this.lastSavedFileSetter = lastSavedFileSetter;
    }

    public void archive() {
        if (activity == null) {
            append("[!] Archive Logs failed: Activity unavailable.\n");
            return;
        }
        if (!StorageAccessController.ensureSharedStorageWriteAccess(
                activity,
                "Archive Logs",
                "/sdcard/dev.perms.test/log_archives",
                outputAppender == null ? null : outputAppender::append)) {
            return;
        }
        append("[i] Archiving PermsTest logs...\n");
        String outputSnapshot = captureOutputSnapshot();
        fileIo.execute(() -> archiveOnWorker(outputSnapshot));
    }

    public void confirmClear() {
        if (activity == null) {
            append("[!] Clear Logs failed: Activity unavailable.\n");
            return;
        }
        if (!StorageAccessController.ensureSharedStorageWriteAccess(
                activity,
                "Clear Logs",
                "/sdcard/dev.perms.test/logs",
                outputAppender == null ? null : outputAppender::append)) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Clear PermsTest logs?")
                .setMessage("This will erase PermsTest log files under /sdcard/dev.perms.test/logs, including logs/lifetime, plus the app-private lifetime/log fallback folder. Log archives under /sdcard/dev.perms.test/log_archives will be kept.\n\nArchive Logs first if you want a timestamped backup.")
                .setPositiveButton("Clear Logs", (d, w) -> {
                    append("[i] Clearing PermsTest logs...\n");
                    fileIo.execute(this::clearOnWorker);
                })
                .setNegativeButton(R.string.shell_action_cancel, null)
                .show();
    }

    private void archiveOnWorker(String outputSnapshot) {
        File archive = null;
        String message;
        int[] stats = new int[2];
        try {
            LifetimeLogStore.flushPending(3000L);
            File archiveDir = publicArchiveDir();
            if (!archiveDir.exists() && !archiveDir.mkdirs()) {
                File base = activity.getExternalFilesDir(null);
                if (base == null) throw new IllegalStateException("External files dir unavailable");
                archiveDir = new File(base, "log_archives");
                //noinspection ResultOfMethodCallIgnored
                archiveDir.mkdirs();
            }
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            archive = new File(archiveDir, "PermsTest_logs_" + ts + ".zip");
            Set<String> usedNames = new HashSet<>();
            try (FileOutputStream fos = new FileOutputStream(archive, false);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                String manifest = "PermsTest log archive\n"
                        + "timestamp=" + ts + "\n"
                        + "public_logs=" + publicLogDir().getAbsolutePath() + "\n"
                        + "public_lifetime_logs=" + LifetimeLogStore.getPublicLogDirectory().getAbsolutePath() + "\n"
                        + "app_logs=" + appLogDir().getAbsolutePath() + "\n"
                        + "plugin_review_reports=" + pluginExportDir().getAbsolutePath() + "/*.txt\n"
                        + "public_root_exports=" + publicRootDir().getAbsolutePath() + "/perms_test_actions*.txt\n"
                        + "output_snapshot=app_private_logs/output_pane_snapshot.txt\n"
                        + "archives_kept=" + publicArchiveDir().getAbsolutePath() + "\n";
                writeEntry(zos, usedNames, "manifest.txt", manifest.getBytes(StandardCharsets.UTF_8), stats);
                addTree(zos, usedNames, publicLogDir(), "public_logs", stats);
                addTree(zos, usedNames, appLogDir(), "app_private_logs", stats);
                addTreeWithSuffix(zos, usedNames, pluginExportDir(), "plugin_exports", ".txt", stats);
                addTopLevelFilesWithPrefixSuffix(zos, usedNames, publicRootDir(), "public_root_exports",
                        "perms_test_actions", ".txt", stats);
                String snapshot = "PermsTest output pane snapshot\narchive_timestamp=" + ts + "\n\n"
                        + (outputSnapshot == null ? "" : outputSnapshot);
                writeEntry(zos, usedNames, "app_private_logs/output_pane_snapshot.txt",
                        snapshot.getBytes(StandardCharsets.UTF_8), stats);
            }
            try { archive.setReadable(true, false); } catch (Throwable ignored) {}
            message = "[+] Archived " + Math.max(0, stats[0] - 1) + " log/audit file(s) to: " + archive.getAbsolutePath() + "\n";
        } catch (Exception e) {
            message = "[!] Archive Logs failed: " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n";
            archive = null;
        }
        File finalArchive = archive;
        String finalMessage = message;
        postToUi(() -> {
            if (finalArchive != null && finalArchive.exists()) {
                if (lastSavedFileSetter != null) lastSavedFileSetter.setFile(finalArchive);
                if (binding != null && binding.tabLogging != null && binding.tabLogging.btnShareLast != null) {
                    binding.tabLogging.btnShareLast.setEnabled(true);
                }
                Toast.makeText(activity, "Log archive created", Toast.LENGTH_SHORT).show();
            }
            append(finalMessage);
            if (refreshStatus != null) refreshStatus.run();
        });
    }

    private void clearOnWorker() {
        int[] stats = new int[1];
        String message;
        try {
            deleteContents(publicLogDir(), stats);
            deleteContents(appLogDir(), stats);
            // Recreate the lifetime log file so later appends have a known destination.
            File lifetime = LifetimeLogStore.getLogFile(activity);
            File parent = lifetime.getParentFile();
            if (parent != null) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileOutputStream ignored = new FileOutputStream(lifetime, false)) {
                // recreate empty file
            }
            message = "[+] Cleared " + stats[0] + " PermsTest log file(s). Log archives were kept.\n";
        } catch (Exception e) {
            message = "[!] Clear Logs failed: " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n";
        }
        String finalMessage = message;
        postToUi(() -> {
            append(finalMessage);
            if (refreshStatus != null) refreshStatus.run();
        });
    }

    private String captureOutputSnapshot() {
        try {
            if (binding == null || binding.txtOutput == null || binding.txtOutput.getText() == null) return "";
            return binding.txtOutput.getText().toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private File publicRootDir() {
        return new File(Environment.getExternalStorageDirectory(), PUBLIC_ROOT);
    }

    private File publicLogDir() {
        return new File(publicRootDir(), PUBLIC_LOG_DIR);
    }

    private File publicArchiveDir() {
        return new File(publicRootDir(), PUBLIC_ARCHIVE_DIR);
    }

    private File pluginExportDir() {
        return new File(publicRootDir(), PLUGIN_EXPORT_DIR);
    }

    private File appLogDir() {
        File base = activity == null ? null : activity.getExternalFilesDir(null);
        if (base == null) base = activity == null ? null : activity.getFilesDir();
        return base == null ? new File("logs") : new File(base, "logs");
    }

    private static void addTreeWithSuffix(ZipOutputStream zos,
                                          Set<String> usedNames,
                                          File root,
                                          String prefix,
                                          String suffix,
                                          int[] stats) throws Exception {
        if (root == null || !root.exists()) return;
        File canonicalRoot = root.getCanonicalFile();
        addTreeWithSuffixRecursive(zos, usedNames, canonicalRoot, canonicalRoot, prefix,
                suffix == null ? "" : suffix, stats);
    }

    private static void addTreeWithSuffixRecursive(ZipOutputStream zos,
                                                   Set<String> usedNames,
                                                   File root,
                                                   File file,
                                                   String prefix,
                                                   String suffix,
                                                   int[] stats) throws Exception {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) {
                addTreeWithSuffixRecursive(zos, usedNames, root, child, prefix, suffix, stats);
            }
            return;
        }
        if (suffix.length() > 0 && !file.getName().endsWith(suffix)) return;
        addFileEntry(zos, usedNames, root, file, prefix, stats);
    }

    private static void addTopLevelFilesWithPrefixSuffix(ZipOutputStream zos,
                                                         Set<String> usedNames,
                                                         File root,
                                                         String prefix,
                                                         String namePrefix,
                                                         String suffix,
                                                         int[] stats) throws Exception {
        if (root == null || !root.exists() || !root.isDirectory()) return;
        File canonicalRoot = root.getCanonicalFile();
        File[] children = canonicalRoot.listFiles();
        if (children == null) return;
        String safePrefix = namePrefix == null ? "" : namePrefix;
        String safeSuffix = suffix == null ? "" : suffix;
        for (File child : children) {
            if (child == null || !child.isFile()) continue;
            String name = child.getName();
            if (safePrefix.length() > 0 && !name.startsWith(safePrefix)) continue;
            if (safeSuffix.length() > 0 && !name.endsWith(safeSuffix)) continue;
            addFileEntry(zos, usedNames, canonicalRoot, child, prefix, stats);
        }
    }

    private static void addTree(ZipOutputStream zos,
                                Set<String> usedNames,
                                File root,
                                String prefix,
                                int[] stats) throws Exception {
        if (root == null || !root.exists()) return;
        File canonicalRoot = root.getCanonicalFile();
        addTreeRecursive(zos, usedNames, canonicalRoot, canonicalRoot, prefix, stats);
    }

    private static void addTreeRecursive(ZipOutputStream zos,
                                         Set<String> usedNames,
                                         File root,
                                         File file,
                                         String prefix,
                                         int[] stats) throws Exception {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) addTreeRecursive(zos, usedNames, root, child, prefix, stats);
            return;
        }
        addFileEntry(zos, usedNames, root, file, prefix, stats);
    }

    private static void addFileEntry(ZipOutputStream zos,
                                     Set<String> usedNames,
                                     File root,
                                     File file,
                                     String prefix,
                                     int[] stats) throws Exception {
        String rel = root.toURI().relativize(file.getCanonicalFile().toURI()).getPath();
        if (rel == null || rel.length() == 0) rel = file.getName();
        rel = sanitizeZipPath(prefix + "/" + rel);
        if (rel.length() == 0) return;
        if (!usedNames.add(rel)) return;
        ZipEntry entry = new ZipEntry(rel);
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = bis.read(buf)) > 0) {
                zos.write(buf, 0, r);
                stats[1] += r;
            }
        }
        zos.closeEntry();
        stats[0]++;
    }

    private static void writeEntry(ZipOutputStream zos,
                                   Set<String> usedNames,
                                   String name,
                                   byte[] data,
                                   int[] stats) throws Exception {
        String safe = sanitizeZipPath(name);
        if (safe.length() == 0 || !usedNames.add(safe)) return;
        ZipEntry entry = new ZipEntry(safe);
        zos.putNextEntry(entry);
        if (data != null) zos.write(data);
        zos.closeEntry();
        stats[0]++;
    }

    private static String sanitizeZipPath(String path) {
        if (path == null) return "";
        String p = path.replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        if (p.contains("../") || p.equals("..") || p.contains("/..")) return "";
        return p;
    }

    private static void deleteContents(File root, int[] stats) {
        if (root == null || !root.exists() || !root.isDirectory()) return;
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) deleteRecursive(child, stats);
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
    }

    private static void deleteRecursive(File file, int[] stats) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child, stats);
            }
        }
        if (file.isFile()) stats[0]++;
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void postToUi(Runnable action) {
        if (action == null) return;
        try {
            if (binding != null && binding.getRoot() != null) {
                binding.getRoot().post(action);
                return;
            }
        } catch (Throwable ignored) {}
        action.run();
    }

    private void append(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface LastSavedFileSetter {
        void setFile(File file);
    }
}
