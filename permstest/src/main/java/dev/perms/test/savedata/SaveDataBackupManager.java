package dev.perms.test.savedata;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns Save Data Editor backup path construction and backup/restore shell dispatch.
 */
final class SaveDataBackupManager {
    private static final AtomicInteger BACKUP_SEQUENCE = new AtomicInteger();

    interface ShellRunner {
        void run(String command, SaveDataEditorController.ShellCallback callback);
    }

    interface Callback {
        void onComplete(boolean success, String message);
    }

    interface ListCallback {
        void onComplete(boolean success, List<String> paths, String message);
    }

    private SaveDataBackupManager() {
    }

    static void backup(ShellRunner runner, String saveDataRoot, String packageName, String playerId,
            String sourcePath, Callback callback) {
        if (runner == null) {
            if (callback != null) callback.onComplete(false, "Shell runner unavailable.");
            return;
        }
        if (isEmpty(sourcePath)) {
            if (callback != null) callback.onComplete(false, "Save path is empty.");
            return;
        }

        String backupDir = backupDir(saveDataRoot, packageName, playerId);
        String backupPath = backupPath(backupDir, sourcePath);
        String cmd = "mkdir -p " + shQuote(backupDir)
                + " && cp -f " + shQuote(sourcePath) + " " + shQuote(backupPath)
                + " && chmod 644 " + shQuote(backupPath);
        runner.run(cmd, (code, out, err) -> {
            if (callback == null) return;
            if (code == 0) {
                callback.onComplete(true, backupPath);
            } else {
                callback.onComplete(false, trim(err));
            }
        });
    }

    static void listBackups(ShellRunner runner, String saveDataRoot, String packageName, String playerId,
            ListCallback callback) {
        if (runner == null) {
            if (callback != null) callback.onComplete(false, new ArrayList<>(), "Shell runner unavailable.");
            return;
        }
        String backupDir = backupDir(saveDataRoot, packageName, playerId);
        String cmd = "if [ -d " + shQuote(backupDir) + " ]; then "
                + "find " + shQuote(backupDir) + " -maxdepth 1 -type f -print 2>/dev/null | sort -r; "
                + "fi";
        runner.run(cmd, (code, out, err) -> {
            ArrayList<String> paths = parseLines(out);
            if (callback == null) return;
            if (code == 0) {
                callback.onComplete(true, paths, backupDir);
            } else {
                callback.onComplete(false, paths, trim(err));
            }
        });
    }

    static void restore(ShellRunner runner, String backupPath, String sourcePath, Callback callback) {
        if (runner == null) {
            if (callback != null) callback.onComplete(false, "Shell runner unavailable.");
            return;
        }
        if (isEmpty(backupPath)) {
            if (callback != null) callback.onComplete(false, "Backup path is empty.");
            return;
        }
        if (isEmpty(sourcePath)) {
            if (callback != null) callback.onComplete(false, "Save path is empty.");
            return;
        }
        String cmd = "cp -f " + shQuote(backupPath) + " " + shQuote(sourcePath)
                + " && chmod 644 " + shQuote(sourcePath);
        runner.run(cmd, (code, out, err) -> {
            if (callback == null) return;
            if (code == 0) {
                callback.onComplete(true, sourcePath);
            } else {
                callback.onComplete(false, trim(err));
            }
        });
    }

    static String fileName(String path) {
        if (path == null) return "save_data.bin";
        String name = path.substring(path.lastIndexOf('/') + 1);
        return isEmpty(name) ? "save_data.bin" : name;
    }

    static String backupDir(String saveDataRoot, String packageName, String playerId) {
        String root = isEmpty(saveDataRoot) ? "/sdcard/dev.perms.test/save_data" : saveDataRoot.trim();
        String pkg = isEmpty(packageName) ? "_unselected" : packageName.trim();
        String player = isEmpty(playerId) ? "" : "/" + playerId.trim();
        return root + "/" + pkg + "/backups" + player;
    }

    private static String backupPath(String backupDir, String sourcePath) {
        String fileName = fileName(sourcePath);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        int sequence = BACKUP_SEQUENCE.updateAndGet(value -> value >= 9999 ? 1 : value + 1);
        return backupDir + "/" + stamp + "_" + String.format(Locale.US, "%04d", sequence) + "_" + fileName;
    }

    private static ArrayList<String> parseLines(String value) {
        ArrayList<String> lines = new ArrayList<>();
        if (value == null) return lines;
        String[] parts = value.split("\\r?\\n");
        for (String part : parts) {
            String line = part == null ? "" : part.trim();
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
