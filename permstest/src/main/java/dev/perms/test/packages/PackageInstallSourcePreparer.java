package dev.perms.test.packages;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;

/**
 * Resolves package-install input files and pm-install option preferences.
 */
public final class PackageInstallSourcePreparer {
    public interface UriCopyHandler {
        File copy(Uri uri, String subdir, String filename);
    }

    public static final class PreparedSource {
        public final File file;
        public final boolean usedImportsCopy;
        public final boolean requestedDirectPath;
        public final boolean skippedByLargeFile;

        public PreparedSource(File file, boolean usedImportsCopy, boolean requestedDirectPath, boolean skippedByLargeFile) {
            this.file = file;
            this.usedImportsCopy = usedImportsCopy;
            this.requestedDirectPath = requestedDirectPath;
            this.skippedByLargeFile = skippedByLargeFile;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final String keyUseAndroidDataPath;
    private final String keyUseStagingFolder;
    private final String keySkipStagingLargeFiles;
    private final String keyBypassLowTargetSdkBlock;
    private final String keyIgnoreDexoptProfile;
    private final String keyAllowDowngrade;
    private final String importsDirName;
    private final long largeFileThresholdBytes;
    private final UriCopyHandler copyHandler;

    public PackageInstallSourcePreparer(Context context,
                                        SharedPreferences prefs,
                                        String keyUseAndroidDataPath,
                                        String keyUseStagingFolder,
                                        String keySkipStagingLargeFiles,
                                        String keyBypassLowTargetSdkBlock,
                                        String keyIgnoreDexoptProfile,
                                        String keyAllowDowngrade,
                                        String importsDirName,
                                        long largeFileThresholdBytes,
                                        UriCopyHandler copyHandler) {
        this.context = context;
        this.prefs = prefs;
        this.keyUseAndroidDataPath = keyUseAndroidDataPath;
        this.keyUseStagingFolder = keyUseStagingFolder;
        this.keySkipStagingLargeFiles = keySkipStagingLargeFiles;
        this.keyBypassLowTargetSdkBlock = keyBypassLowTargetSdkBlock;
        this.keyIgnoreDexoptProfile = keyIgnoreDexoptProfile;
        this.keyAllowDowngrade = keyAllowDowngrade;
        this.importsDirName = importsDirName;
        this.largeFileThresholdBytes = largeFileThresholdBytes;
        this.copyHandler = copyHandler;
    }

    public boolean shouldBypassLowTargetSdkBlock() {
        try {
            return Build.VERSION.SDK_INT >= 35
                    && prefs != null
                    && prefs.getBoolean(keyBypassLowTargetSdkBlock, true);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String getPmLowTargetBypassFlag() {
        return shouldBypassLowTargetSdkBlock() ? " --bypass-low-target-sdk-block" : "";
    }

    public boolean shouldIgnoreDexoptProfile() {
        try {
            return prefs == null || prefs.getBoolean(keyIgnoreDexoptProfile, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public String getPmIgnoreDexoptProfileFlag() {
        return shouldIgnoreDexoptProfile() ? " --ignore-dexopt-profile" : "";
    }

    public boolean shouldAllowDowngradeInstall() {
        try {
            return prefs != null && prefs.getBoolean(keyAllowDowngrade, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String getPmDowngradeFlag() {
        return shouldAllowDowngradeInstall() ? " -d" : "";
    }

    public String buildPmInstallCreateCommand(String sizeBytes) {
        return buildPmInstallCreateCommand(sizeBytes, true);
    }

    public String buildPmInstallCreateCommand(String sizeBytes, boolean includeOptionalFlags) {
        StringBuilder cmd = new StringBuilder("pm install-create");
        if (!TextUtils.isEmpty(sizeBytes)) cmd.append(" -S ").append(sizeBytes);
        if (includeOptionalFlags) {
            cmd.append(getPmLowTargetBypassFlag());
            cmd.append(getPmIgnoreDexoptProfileFlag());
        }
        cmd.append(getPmDowngradeFlag());
        return cmd.toString();
    }

    public boolean shouldUseAndroidDataInstallPath() {
        try {
            return prefs != null && prefs.getBoolean(keyUseAndroidDataPath, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean shouldRestageInstallInputPath(String path) {
        if (shouldUseAndroidDataInstallPath() || TextUtils.isEmpty(path)) return false;
        String p = path.trim();
        // pm install-write runs from system_server. On recent Android/VR builds, shared
        // storage paths can be visible to shell listings but unreadable to package install.
        // Restage public shared-storage inputs through /data/local/tmp before handing them to pm.
        return p.startsWith("/storage/emulated/0/")
                || p.startsWith("/sdcard/")
                || p.startsWith("/storage/self/primary/")
                || p.startsWith("/mnt/user/0/primary/");
    }

    public boolean shouldUseInstallStagingFolder() {
        try {
            return prefs != null && prefs.getBoolean(keyUseStagingFolder, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean shouldSkipInstallStagingForLargeFiles() {
        try {
            return prefs != null && prefs.getBoolean(keySkipStagingLargeFiles, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public PreparedSource prepareInstallSourceFile(Uri uri, String displayName) {
        try {
            long sizeBytes = queryInstallSourceSizeBytes(uri);
            boolean requestedDirectPath = shouldBypassImportsStaging(sizeBytes);
            boolean skippedByLargeFile = sizeBytes > largeFileThresholdBytes;

            if (requestedDirectPath) {
                String directPath = resolveDirectInstallSourcePath(uri);
                if (!TextUtils.isEmpty(directPath)) {
                    return new PreparedSource(new File(directPath), false, true, skippedByLargeFile);
                }
            }

            File copied = copyHandler == null ? null : copyHandler.copy(uri, importsDirName, displayName);
            return copied == null ? null : new PreparedSource(copied, true, requestedDirectPath, skippedByLargeFile);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private long queryInstallSourceSizeBytes(Uri uri) {
        try {
            if (uri == null || context == null) return -1L;
            if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
                File f = new File(uri.getPath() == null ? "" : uri.getPath());
                return f.isFile() ? f.length() : -1L;
            }

            ContentResolver resolver = context.getContentResolver();
            try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.SIZE);
                    if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
                }
            } catch (Throwable ignored) {}

            try (AssetFileDescriptor afd = resolver.openAssetFileDescriptor(uri, "r")) {
                if (afd != null && afd.getLength() >= 0) return afd.getLength();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return -1L;
    }

    private boolean shouldBypassImportsStaging(long sizeBytes) {
        if (!shouldUseInstallStagingFolder()) return true;
        return shouldSkipInstallStagingForLargeFiles()
                && sizeBytes > largeFileThresholdBytes;
    }

    private String validateReadableInstallPath(String path) {
        try {
            if (TextUtils.isEmpty(path)) return null;
            File f = new File(path);
            if (!f.isFile()) return null;
            return f.getAbsolutePath();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String queryDataColumnPath(Uri uri) {
        if (context == null) return null;
        try (Cursor c = context.getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex("_data");
                if (idx >= 0 && !c.isNull(idx)) {
                    return validateReadableInstallPath(c.getString(idx));
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String resolveDirectInstallSourcePath(Uri uri) {
        try {
            if (uri == null || context == null) return null;

            if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
                return validateReadableInstallPath(uri.getPath());
            }

            String docId = null;
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    docId = DocumentsContract.getDocumentId(uri);
                }
            } catch (Throwable ignored) {}

            if (!TextUtils.isEmpty(docId)) {
                if (docId.startsWith("raw:")) {
                    String rawPath = validateReadableInstallPath(docId.substring(4));
                    if (!TextUtils.isEmpty(rawPath)) return rawPath;
                }

                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    String[] parts = docId.split(":", 2);
                    if (parts.length == 2 && "primary".equalsIgnoreCase(parts[0])) {
                        String rel = parts[1].startsWith("/") ? parts[1].substring(1) : parts[1];
                        String extPath = validateReadableInstallPath("/storage/emulated/0/" + rel);
                        if (!TextUtils.isEmpty(extPath)) return extPath;
                    }
                }
            }

            String lastSegment = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(lastSegment)) {
                String decoded = Uri.decode(lastSegment);
                if (!TextUtils.isEmpty(decoded) && decoded.startsWith("raw:")) {
                    String rawPath = validateReadableInstallPath(decoded.substring(4));
                    if (!TextUtils.isEmpty(rawPath)) return rawPath;
                }
            }

            return queryDataColumnPath(uri);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
