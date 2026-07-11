package dev.perms.test.network.ftp;

import dev.perms.test.network.*;

import android.app.Activity;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dev.perms.test.databinding.TabNetworkBinding;
import dev.perms.test.storage.StorageAccessController;

/**
 * Storage-access checks and prompts for normal Android shared-storage server modes.
 */
public final class NetworkFtpServerStorageAccess {
    private NetworkFtpServerStorageAccess() {
    }

    public static boolean hasAllFilesAccess(Activity activity) {
        return StorageAccessController.hasAllFilesAccess(activity);
    }



    public static void showHttpStorageAccessDialog(Activity activity,
                                                   String root,
                                                   OutputAppender outputAppender) {
        if (activity == null) return;
        try {
            String safeRoot = TextUtils.isEmpty(root) ? "/storage/emulated/0/dev.perms.test/http" : root;
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("HTTP storage access")
                    .setMessage("HTTP Server needs All files access to create, list, and serve files under "
                            + safeRoot + ".\n\nGrant All files access and start HTTP again, or change the HTTP root to an app-specific folder that Android allows without broad file access.")
                    .setPositiveButton("Grant", (dialog, which) -> StorageAccessController.openAllFilesAccessSettings(activity))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            if (outputAppender != null) {
                outputAppender.append("[HTTP] Normal Android mode needs All files access for the configured shared-storage root. Grant All files access or change the HTTP root, then start HTTP again.\n");
            }
        } catch (Throwable e) {
            Toast.makeText(activity, "HTTP Server needs All files access for this root.", Toast.LENGTH_LONG).show();
        }
    }
    public static void showNormalStorageAccessDialog(Activity activity,
                                                     TabNetworkBinding network,
                                                     String root,
                                                     OutputAppender outputAppender) {
        if (activity == null) return;
        try {
            String safeRoot = TextUtils.isEmpty(root) ? NetworkFtpServerPaths.defaultRootPath() : root;
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("FTP storage access")
                    .setMessage("Normal Android FTP mode needs All files access to list and transfer files under "
                            + safeRoot + ".\n\nUse Shizuku file access to avoid this Android storage limit, or grant All files access and start FTP again.")
                    .setPositiveButton("Grant", (dialog, which) -> StorageAccessController.openAllFilesAccessSettings(activity))
                    .setNeutralButton("Use Shizuku", (dialog, which) -> {
                        if (network != null && network.chkFtpUseShizuku != null) {
                            network.chkFtpUseShizuku.setChecked(true);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            if (outputAppender != null) {
                outputAppender.append("[FTP] Normal Android mode needs All files access for shared-storage roots. Enable Shizuku file access or grant All files access, then start FTP again.\n");
            }
        } catch (Throwable e) {
            Toast.makeText(activity, "Normal FTP needs All files access for this root.", Toast.LENGTH_LONG).show();
        }
    }

    public interface OutputAppender {
        void append(String text);
    }
}
