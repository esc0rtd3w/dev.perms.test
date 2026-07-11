package dev.perms.test.packages;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;

/**
 * Owns cleanup for package-install files staged in the app-managed imports folder.
 */
public final class PackageInstallImportCleaner {
    private PackageInstallImportCleaner() {
    }

    public static File getManagedImportsDir(Context context, String importsDirName) {
        try {
            if (context == null || TextUtils.isEmpty(importsDirName)) return null;
            File root = context.getExternalFilesDir(null);
            if (root == null) return null;
            return new File(root, importsDirName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isManagedImportFilePath(Context context, String importsDirName, String path) {
        try {
            if (TextUtils.isEmpty(path)) return false;
            File dir = getManagedImportsDir(context, importsDirName);
            if (dir == null) return false;
            String dirPath = dir.getCanonicalPath();
            String filePath = new File(path).getCanonicalPath();
            return filePath.equals(dirPath) || filePath.startsWith(dirPath + File.separator);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void cleanupManagedImportFile(Context context, String importsDirName, String path) {
        try {
            if (!isManagedImportFilePath(context, importsDirName, path)) return;
            File file = new File(path);
            if (file.isFile()) {
                try { file.delete(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    public static void cleanupManagedImportsDirOnStart(Context context, String importsDirName) {
        try {
            File dir = getManagedImportsDir(context, importsDirName);
            if (dir == null || !dir.isDirectory()) return;
            File[] children = dir.listFiles();
            if (children == null || children.length == 0) return;
            for (File child : children) {
                try {
                    if (child == null) continue;
                    if (child.isDirectory()) {
                        deleteRecursiveQuietly(child);
                    } else if (child.isFile()) {
                        child.delete();
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void deleteRecursiveQuietly(File file) {
        try {
            if (file == null || !file.exists()) return;
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursiveQuietly(child);
                    }
                }
            }
            try { file.delete(); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }
}
