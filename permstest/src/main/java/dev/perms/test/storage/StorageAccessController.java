package dev.perms.test.storage;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shared storage access checks/prompts for app features that write under /sdcard/dev.perms.test.
 *
 * Android 11+ exposes MANAGE_EXTERNAL_STORAGE through Settings instead of a normal runtime
 * permission dialog, so feature code should route prompts through this helper instead of each
 * tab maintaining slightly different All files access code.
 */
public final class StorageAccessController {
    public static final String KEY_STORAGE_PROMPT_SHOWN_ON_STARTUP = "storage_access_prompt_shown_on_startup";
    private static final int REQ_LEGACY_STORAGE = 41027;

    private StorageAccessController() {
    }

    public static boolean hasSharedStorageWriteAccess(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return Environment.isExternalStorageManager();
            } catch (Throwable ignored) {
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return true;
    }

    public static void maybePromptOnFirstStart(Activity activity,
                                               SharedPreferences prefs,
                                               OutputAppender outputAppender) {
        if (activity == null || prefs == null) return;
        if (hasSharedStorageWriteAccess(activity)) return;
        if (prefs.getBoolean(KEY_STORAGE_PROMPT_SHOWN_ON_STARTUP, false)) return;
        prefs.edit().putBoolean(KEY_STORAGE_PROMPT_SHOWN_ON_STARTUP, true).apply();
        showSharedStorageAccessDialog(
                activity,
                "PermsTest storage access",
                "PermsTest writes logs, dumps, exported APKs, scripts, HTTP files, FTP files, save-data backups, and other user-visible output under /sdcard/dev.perms.test.\n\nGrant All files access now so those tools can save files without each tab prompting later.",
                outputAppender,
                "[Storage] Shared storage access is not granted yet. Some save/export tools will prompt again when used.\n");
    }

    public static boolean ensureSharedStorageWriteAccess(Activity activity,
                                                         String featureName,
                                                         String targetPath,
                                                         OutputAppender outputAppender) {
        if (activity == null) return true;
        if (hasSharedStorageWriteAccess(activity)) return true;
        String safeFeature = TextUtils.isEmpty(featureName) ? "This feature" : featureName;
        String safePath = TextUtils.isEmpty(targetPath) ? "/sdcard/dev.perms.test" : targetPath;
        showSharedStorageAccessDialog(
                activity,
                safeFeature + " storage access",
                safeFeature + " needs All files access to write under " + safePath + ".\n\nGrant All files access and try again, or choose an app-specific/private output path when that feature supports one.",
                outputAppender,
                "[Storage] " + safeFeature + " needs All files access for " + safePath + ". Grant access and try again.\n");
        return false;
    }

    public static boolean hasAllFilesAccess(Context context) {
        return hasSharedStorageWriteAccess(context);
    }

    public static void openAllFilesAccessSettings(Activity activity) {
        if (activity == null) return;
        requestLegacyStoragePermissionsIfNeeded(activity);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Throwable first) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Throwable second) {
                Toast.makeText(activity, "Could not open All files access settings.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void showSharedStorageAccessDialog(Activity activity,
                                                      String title,
                                                      String message,
                                                      OutputAppender outputAppender,
                                                      String outputMessage) {
        if (activity == null) return;
        try {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Grant", (dialog, which) -> openAllFilesAccessSettings(activity))
                    .setNegativeButton("Later", null)
                    .show();
            if (outputAppender != null && !TextUtils.isEmpty(outputMessage)) {
                outputAppender.append(outputMessage);
            }
        } catch (Throwable e) {
            Toast.makeText(activity, title, Toast.LENGTH_LONG).show();
        }
    }

    private static void requestLegacyStoragePermissionsIfNeeded(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return;
        try {
            boolean needsWrite = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED;
            boolean needsRead = ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED;
            if (needsWrite || needsRead) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQ_LEGACY_STORAGE);
            }
        } catch (Throwable ignored) {
        }
    }

    public interface OutputAppender {
        void append(String text);
    }
}
