package dev.perms.test.packages;

import dev.perms.test.ExecMode;
import dev.perms.test.MainActivity;
import dev.perms.test.R;
import dev.perms.test.ShizukuCompat;
import dev.perms.test.ladb.LadbClient;

import dev.perms.test.apk.BinaryXmlDebuggablePatcher;
import dev.perms.test.debug.PackageInstallDebug;
import dev.perms.test.tools.intentreceiver.IntentCaptureActivity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import rikka.shizuku.Shizuku;
import java.util.LinkedHashSet;

/**
 * Dedicated file-open (ACTION_VIEW) handler activity to keep installs seamless and avoid
 * MainActivity tab/UI lifecycle interactions.
 *
 * Triggered via the FileOpenAlias component (toggleable in Settings).
 */
public class FileOpenInstallActivity extends AppCompatActivity {

    // Keep these string keys identical to MainActivity.
    private static final String PREFS = "perms_test";
    private static final String KEY_HIDE_FILE_OPEN_UI = "hide_file_open_ui";
    private static final String KEY_SHOW_FILE_OPEN_DONE_OPEN = "show_file_open_done_open";
    private static final String KEY_CONFIRM_FILE_OPEN_INSTALL = "confirm_file_open_install";
    private static final String KEY_INSTALL_USE_ANDROID_DATA_PATH = "install_use_android_data_path";
    private static final String KEY_INSTALL_USE_STAGING_FOLDER = "install_use_staging_folder";
    private static final String KEY_INSTALL_SKIP_STAGING_LARGE_FILES = "install_skip_staging_large_files";
    private static final long INSTALL_SKIP_STAGING_LARGE_BYTES = 900L * 1024L * 1024L;
    private static final String KEY_INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK = "install_bypass_low_target_sdk_block";
    private static final String KEY_INSTALL_IGNORE_DEXOPT_PROFILE = "install_ignore_dexopt_profile";
    private static final String KEY_INSTALL_ALLOW_DOWNGRADE = "install_allow_downgrade";
    private static final String KEY_INSTALL_USE_INSTALLER_SCRIPT = "install_use_installer_script";
    private static final String KEY_SPLIT_APK_SHOW_WARNING_DIALOG = "split_apk_show_warning_dialog";

    private static final String EXT_SCRIPTS_DIR = "scripts";
    private static final String EXT_IMPORTS_DIR = "imports";
    private static final String ASSET_SCRIPTS_DIR = "scripts";
    private static final String ASSET_BIN_DIR = "bin";

    // Shell-accessible+executable location used by MainActivity for bundled binaries.
    // If external storage is mounted noexec, running unzip next to scripts may fail, so we stage here too.
    private static final String PUBLIC_TMP_ROOT = "/data/local/tmp/dev.perms.test";
    private static final String PUBLIC_BIN_DIR = PUBLIC_TMP_ROOT + "/bin";
    private static final String PUBLIC_STAGE_DIR = PUBLIC_TMP_ROOT + "/stage";
    private static final String PUBLIC_FILES_DIR = PUBLIC_TMP_ROOT + "/files";
    // Public, user-browseable log directory. Prefer the canonical emulated storage path.
    // Some devices still expose "/sdcard"; keep it as a fallback.
    private static final String PUBLIC_LOG_DIR = "/storage/emulated/0/dev.perms.test/logs";
    private static final String PUBLIC_LOG_DIR_FALLBACK = "/sdcard/dev.perms.test/logs";
    private static final String DEFAULT_INSTALL_SCRIPT = "install-apk.sh";

    private Uri openedUri;
    private String openedLabel;
    private File localFile;

    // Captures detailed diagnostics for the most recent install attempt.
    // Used by the failure dialog "Save log" action.
    private String lastInstallDebugLog = "";
    private String lastReadFailureDetails = "";

    private int lastExit = 0;
    private String lastOut = "";
    private String lastErr = "";

    private volatile boolean keepLocalFileForRetry = false;
    private volatile boolean allowDowngradeForRetry = false;

    private SharedPreferences prefs;
    private ExecMode execMode = ExecMode.SHIZUKU;
    private LadbClient ladbClient;
    private boolean ladbConnected = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "onCreate action=" + (getIntent() == null ? null : getIntent().getAction())
                + ", data=" + (getIntent() == null ? null : getIntent().getData())
                + ", flags=0x" + Integer.toHexString(getIntent() == null ? 0 : getIntent().getFlags()));

        try {
            FrameLayout inputBlocker = new FrameLayout(this);
            inputBlocker.setClickable(true);
            inputBlocker.setFocusable(true);
            inputBlocker.setFocusableInTouchMode(true);
            setContentView(inputBlocker);
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "input blocker view installed");
        } catch (Throwable t) {
            PackageInstallDebug.error(PackageInstallDebug.Area.FILE_OPEN, "failed to install input blocker", t);
        }

        try {
            Intent i = getIntent();
            final String act = (i == null) ? null : i.getAction();
            if (i == null || !(Intent.ACTION_VIEW.equals(act) || Intent.ACTION_INSTALL_PACKAGE.equals(act))) {
                finishAndRemove();
                return;
            }

            openedUri = i.getData();
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "intent resolved initialUri=" + openedUri);
            if (openedUri == null) {
                try {
                    openedUri = i.getParcelableExtra(Intent.EXTRA_STREAM);
                } catch (Throwable ignored) {}
            }
            if (openedUri == null) {
                openedUri = firstUriFromClipData(i);
            }
            if (openedUri == null) {
                PackageInstallDebug.warn(PackageInstallDebug.Area.FILE_OPEN,
                        "no readable URI found in data, EXTRA_STREAM, or ClipData; " + describeIntentGrants(i));
                finishAndRemove();
                return;
            }

            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                    "using URI=" + openedUri + "; " + describeIntentGrants(i));

            // Persist permission if caller grants it (best effort).
            try {
                final int takeFlags = i.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (takeFlags != 0) getContentResolver().takePersistableUriPermission(openedUri, takeFlags);
            } catch (Throwable t) {
                PackageInstallDebug.warn(PackageInstallDebug.Area.FILE_OPEN,
                        "takePersistableUriPermission not available/accepted: "
                                + t.getClass().getSimpleName() + ": " + t.getMessage());
            }

            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            execMode = ExecMode.get(prefs);
            ladbClient = new LadbClient(this);
            if (execMode == ExecMode.LADB && prefs.getBoolean(ExecMode.PREF_KEY_LADB_AUTOCONNECT, true)) {
                // Best-effort auto-connect. If it fails, we'll surface a clean error on first command.
                tryAutoConnectLadb();
            }

            openedLabel = queryDisplayName(openedUri);
            if (openedLabel == null || openedLabel.trim().isEmpty()) openedLabel = openedUri.toString();
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "displayName=" + openedLabel + ", execMode=" + execMode);

            if (isSmaliOpenCandidate(openedUri, openedLabel)) {
                showSmaliOpenChoiceDialog();
                return;
            }
            if (!isPackageArchiveOpenCandidate(i, openedUri, openedLabel)) {
                redirectNonPackageOpenToIntentCapture(i);
                return;
            }
            continueFileOpenInstallFromOpenedUri();
        } catch (Throwable t) {
            PackageInstallDebug.error(PackageInstallDebug.Area.FILE_OPEN, "onCreate failed", t);
            showFailureDialog("File open failed:\n" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void continueFileOpenInstallFromOpenedUri() {
        try {
            // Prefer a direct filesystem path when staging is disabled (or skipped for large files).
            // Fall back to our managed imports copy when the incoming URI does not expose a readable path.
            allowDowngradeForRetry = false;
            localFile = prepareInstallSourceFile(openedUri, openedLabel);
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "prepared localFile=" + PackageInstallDebug.describePath(localFile == null ? null : localFile.getAbsolutePath()));
            if (localFile == null || !localFile.exists() || localFile.length() <= 0) {
                String debug = buildFileReadDebugLog(openedUri, openedLabel);
                if (!TextUtils.isEmpty(debug)) lastInstallDebugLog = debug;
                showFailureDialog(buildFileReadFailureMessage(openedLabel));
                return;
            }

            if (shouldConfirmFileOpenInstall()) {
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "showing install confirmation dialog");
                showInstallConfirmationDialog();
            } else {
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "starting install worker without confirmation");
                new Thread(this::doInstallFromLocalFile, "file-open-install").start();
            }
        } catch (Throwable t) {
            PackageInstallDebug.error(PackageInstallDebug.Area.FILE_OPEN, "prepare install failed", t);
            showFailureDialog("File open failed:\n" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void showSmaliOpenChoiceDialog() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("Open smali file")
                    .setMessage("This file looks like smali source:\n" + openedLabel + "\n\nOpen it in the internal Smali Editor instead of installing it?")
                    .setPositiveButton("Open Editor", (dialog, which) -> openSmaliInMainActivity())
                    .setNeutralButton("Install Anyway", (dialog, which) -> continueFileOpenInstallFromOpenedUri())
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finishAndRemove())
                    .show();
        } catch (Throwable t) {
            openSmaliInMainActivity();
        }
    }

    private void openSmaliInMainActivity() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.setAction(Intent.ACTION_VIEW);
            i.setData(openedUri);
            i.putExtra(MainActivity.EXTRA_OPEN_SMALI_EDITOR_URI, openedUri);
            i.putExtra(MainActivity.EXTRA_OPEN_SMALI_EDITOR_LABEL, openedLabel == null ? "" : openedLabel);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (openedUri != null) {
                try { i.setClipData(ClipData.newUri(getContentResolver(), openedLabel, openedUri)); } catch (Throwable ignored) {}
            }
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Smali editor open failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            finishAndRemove();
        }
    }

    private boolean isSmaliOpenCandidate(Uri uri, String displayName) {
        try {
            if (isSmaliName(displayName)) return true;
            if (uri == null) return false;
            if (isSmaliName(uri.getLastPathSegment())) return true;
            return "file".equalsIgnoreCase(uri.getScheme()) && isSmaliName(uri.getPath());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSmaliName(String value) {
        return !TextUtils.isEmpty(value) && value.toLowerCase(Locale.US).endsWith(".smali");
    }

    private boolean isPackageArchiveOpenCandidate(Intent intent, Uri uri, String displayName) {
        try {
            String name = displayName;
            if (TextUtils.isEmpty(name) && uri != null) name = uri.getLastPathSegment();
            String lowerName = name == null ? "" : name.toLowerCase(Locale.US);
            if (lowerName.endsWith(".apk") || lowerName.endsWith(".apks") || lowerName.endsWith(".apkm")
                    || lowerName.endsWith(".xapk") || lowerName.endsWith(".zip")) {
                return true;
            }
            String type = intent == null ? null : intent.getType();
            if (TextUtils.isEmpty(type) && uri != null) {
                try { type = getContentResolver().getType(uri); } catch (Throwable ignored) {}
            }
            if (TextUtils.isEmpty(type)) return false;
            String mime = type.toLowerCase(Locale.US);
            if ("application/vnd.android.package-archive".equals(mime)) return true;
            if ("application/apks".equals(mime) || "application/vnd.apks".equals(mime) || "application/x-apks".equals(mime)) return true;
            if ("application/apkm".equals(mime) || "application/vnd.apkm".equals(mime) || "application/x-apkm".equals(mime)) return true;
            if ("application/xapk".equals(mime) || "application/vnd.xapk".equals(mime) || "application/x-xapk".equals(mime)) return true;
            return ("application/zip".equals(mime) || "application/x-zip".equals(mime) || "application/x-zip-compressed".equals(mime))
                    && (lowerName.endsWith(".zip") || lowerName.endsWith(".apks") || lowerName.endsWith(".apkm") || lowerName.endsWith(".xapk"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void redirectNonPackageOpenToIntentCapture(Intent original) {
        try {
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "non-package file routed to IntentCaptureActivity label=" + openedLabel);
            Intent capture = new Intent(original == null ? new Intent(Intent.ACTION_VIEW) : original);
            capture.setClass(this, IntentCaptureActivity.class);
            capture.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (openedUri != null) {
                String type = null;
                try { type = original == null ? null : original.getType(); } catch (Throwable ignored) {}
                try {
                    if (!TextUtils.isEmpty(type)) capture.setDataAndType(openedUri, type);
                    else capture.setData(openedUri);
                } catch (Throwable ignored) {}
                try {
                    capture.setClipData(ClipData.newUri(getContentResolver(), openedLabel == null ? "file" : openedLabel, openedUri));
                } catch (Throwable ignored) {}
            }
            startActivity(capture);
        } catch (Throwable t) {
            showFailureDialog("This file is not an APK/APKS/APKM/XAPK package:\n" + openedLabel);
            return;
        }
        finishAndRemove();
    }

    private void doInstallFromLocalFile() {
        try {
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "install worker begin localFile=" + PackageInstallDebug.describePath(localFile == null ? null : localFile.getAbsolutePath())
                    + ", label=" + openedLabel);
            if (!isShizukuReadyAndGranted()) {
                runOnUiThread(() -> showFailureDialog("Shizuku not ready or permission not granted."));
                return;
            }

            // Some stores (e.g., Aurora in single-package install mode) may forward a single split APK
            // like "config.en.apk" or "split_config.arm64_v8a.apk". Installing a split alone
            // fails because the base APK is missing. Detect this early using the original file
            // label when possible, since the staged imports copy adds a timestamp prefix.
            try {
                final String splitName = findLikelySingleSplitApkName(
                        openedLabel,
                        localFile == null ? null : localFile.getName(),
                        openedUri == null ? null : openedUri.getLastPathSegment());
                if (!TextUtils.isEmpty(splitName)) {
                    final String shownName = splitName;
                    if (!shouldShowSplitApkWarningDialog()) {
                        runOnUiThread(() -> {
                            tryHandOffSplitApkIntent();
                            finishAndRemove();
                        });
                        return;
                    }
                    runOnUiThread(() -> showFailureDialog(
                            "This looks like a split APK (" + shownName + ").\n\n" +
                            "A full install must include the base package.\n" +
                            "Use an archive (APKS/APKM/XAPK) or a base APK, or change the store's install method."));
                    return;
                }
            } catch (Throwable ignored) {}

            final String path = localFile.getAbsolutePath();
            final String lower = path.toLowerCase(Locale.US);
            final String abiWarning = lower.endsWith(".apk") ? PackageAbiInspector.buildInstallWarning(localFile) : "";
            if (!TextUtils.isEmpty(abiWarning)) {
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "abi warning: " + abiWarning);
            }
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "install branch standaloneApk=" + lower.endsWith(".apk")
                    + ", path=" + PackageInstallDebug.describePath(path));

            // Resolve the package before installing while the readable/imported source is still intact.
            // Some browser-provided names are not useful package hints, and Android may kill/restart
            // package state while the install finishes. Keep this only as UI metadata; install logic
            // still uses the normal session path below.
            final InstallUiInfo preInstallInfo = probeInstallUiInfo(path, openedLabel);

            runOnUiThread(() -> {
                try {
                    String nm = openedLabel;
                    if (nm == null || nm.trim().isEmpty()) {
                        nm = new File(path).getName();
                    }
                    Toast.makeText(this, "Installing " + nm + "...", Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
            });

            CmdResult res;
            boolean useScriptRequested = shouldUseInstallerScript();
            if (useScriptRequested) {
                File script = ensureInstallScript();
                if (script == null || !script.exists()) {
                    res = new CmdResult(1, "", "Missing install-apk.sh");
                } else {
                    String scriptInput = prepareInstallInputPathForInstallScript(path);
                    PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "running requested install-apk.sh script input="
                            + PackageInstallDebug.describePath(scriptInput));
                    String cmd = "chmod 777 " + shQuote(script.getAbsolutePath()) + " 2>/dev/null || true; "
                            + buildInstallScriptCommand(script, scriptInput);
                    res = runShizukuShellCapture(cmd);
                }
            } else if (lower.endsWith(".apk")) {
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "running built-in single APK install");
                res = runBuiltInPmSessionInstall(path);
            } else {
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "running built-in archive install");
                res = runBuiltInArchivePmSessionInstall(path);
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "archive install result exit=" + res.exitCode
                        + ", stdoutLen=" + (res.stdout == null ? 0 : res.stdout.length())
                        + ", stderrLen=" + (res.stderr == null ? 0 : res.stderr.length()));

                // Use the script fallback only for generic archive failures. Existing-package
                // conflicts must be handled by replacing the installed package first.
                if (res.exitCode != 0 && !isExistingPackageInstallConflict(res)) {
                    File script = ensureInstallScript();
                    if (script != null && script.exists()) {
                        String scriptInput = prepareInstallInputPathForInstallScript(path);
                        String cmd = "chmod 777 " + shQuote(script.getAbsolutePath()) + " 2>/dev/null || true; "
                                + buildInstallScriptCommand(script, scriptInput);
                        CmdResult fb = runShizukuShellCapture(cmd);

                        // Prefer the fallback result only if it succeeds; otherwise merge output for debugging.
                        if (fb.exitCode == 0) {
                            res = fb;
                        } else {
                            String so = (res.stdout == null ? "" : res.stdout) + "\n" + (fb.stdout == null ? "" : fb.stdout);
                            String se = (res.stderr == null ? "" : res.stderr) + "\n" + (fb.stderr == null ? "" : fb.stderr);
                            res = new CmdResult(res.exitCode, so, se);
                        }
                    }
                }
            }

            lastExit = res.exitCode;
            lastOut = res.stdout;
            lastErr = TextUtils.isEmpty(abiWarning)
                    ? res.stderr
                    : ((res.stderr == null ? "" : res.stderr)
                    + ((res.stderr != null && res.stderr.endsWith("\n")) ? "" : "\n")
                    + "[APK ABI] " + abiWarning + "\n");
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "final install result exit=" + lastExit
                    + ", stdoutLen=" + (lastOut == null ? 0 : lastOut.length())
                    + ", stderrLen=" + (lastErr == null ? 0 : lastErr.length()));

            final boolean seamless = isSeamlessEnabled();
            final boolean showDoneOpen = isShowDoneOpenEnabled();

            if (lastExit != 0) {
                lastInstallDebugLog = buildInstallDebugLog(path, openedLabel, lastExit, lastOut, lastErr);
                final String msg = buildInstallFailureMessage(path, openedLabel, lastExit, lastOut, lastErr);
                if (isExistingPackageInstallConflict(lastOut, lastErr)) {
                    String pkg = resolveReplacementPackageName(path, openedLabel, lastOut, lastErr);
                    if (!TextUtils.isEmpty(pkg)) {
                        keepLocalFileForRetry = true;
                        final String retryPkg = pkg;
                        final boolean downgrade = isPackageVersionDowngrade(lastOut, lastErr);
                        runOnUiThread(() -> showReplaceInstalledPackageDialog(msg, retryPkg, downgrade));
                        return;
                    }
                }
                runOnUiThread(() -> showFailureDialog(msg));
                return;
            }

            keepLocalFileForRetry = false;
            allowDowngradeForRetry = false;
            lastInstallDebugLog = "";

            InstallUiInfo info = probeInstallUiInfo(path, openedLabel);
            if (info == null || TextUtils.isEmpty(info.packageName)) {
                info = preInstallInfo;
            }
            if (info == null || TextUtils.isEmpty(info.packageName)) {
                info = probeInstalledPackageFromNameHints(openedLabel, localFile == null ? null : localFile.getName(), openedUri == null ? null : openedUri.getLastPathSegment());
            }
            info = refreshInstalledUiInfo(info);
            final InstallUiInfo finalInfo = info;

            if (!showDoneOpen && seamless) {
                runOnUiThread(() -> {
                    try { Toast.makeText(this, "Installed", Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
                    finishAndRemove();
                });
            } else {
                runOnUiThread(() -> showDoneOpenDialog(finalInfo));
            }
        } catch (Throwable t) {
            PackageInstallDebug.error(PackageInstallDebug.Area.FILE_OPEN, "install worker failed", t);
            final String msg = "Install failed:\n" + t.getClass().getSimpleName() + ": " + t.getMessage();
            runOnUiThread(() -> showFailureDialog(msg));
        } finally {
            // Best-effort cleanup so /data/local/tmp/dev.perms.test doesn't grow without bounds.
            try { cleanupPublicTmpArtifacts(); } catch (Throwable ignored) {}
            if (!keepLocalFileForRetry) {
                try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
            }
        }
    }

    private void cleanupPublicTmpArtifacts() {
        try {
            if (!isShizukuReadyAndGranted()) return;
            runShizukuShellCapture("rm -rf " + shQuote(PUBLIC_STAGE_DIR) + "/pkg_*" + " 2>/dev/null || true");
            runShizukuShellCapture("rm -rf " + shQuote(PUBLIC_STAGE_DIR) + "/*" + " 2>/dev/null || true");
            runShizukuShellCapture("rm -rf " + shQuote(PUBLIC_FILES_DIR) + "/*" + " 2>/dev/null || true");
            // Clean stale install staging while keeping staged tool binaries intact.
            runShizukuShellCapture("rm -rf " + shQuote(PUBLIC_BIN_DIR + "/stage") + " 2>/dev/null || true");
        } catch (Throwable ignored) {}
    }

    
    private File getManagedImportsDir() {
        try {
            File root = getExternalFilesDir(null);
            if (root == null) return null;
            return new File(root, EXT_IMPORTS_DIR);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isManagedImportFilePath(String path) {
        try {
            if (TextUtils.isEmpty(path)) return false;
            File dir = getManagedImportsDir();
            if (dir == null) return false;
            String dirPath = dir.getCanonicalPath();
            String filePath = new File(path).getCanonicalPath();
            return filePath.equals(dirPath) || filePath.startsWith(dirPath + File.separator);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void cleanupManagedImportFile(File file) {
        try {
            if (file == null) return;
            String path = file.getAbsolutePath();
            if (!isManagedImportFilePath(path)) return;
            if (file.isFile()) {
                try { file.delete(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isShowDoneOpenEnabled() {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            return sp.getBoolean(KEY_SHOW_FILE_OPEN_DONE_OPEN, true);
        } catch (Throwable ignored) {
            return false;
        }
    }

private boolean isSeamlessEnabled() {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            return sp.getBoolean(KEY_HIDE_FILE_OPEN_UI, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

        private interface LaunchIntentCallback {
        void onResolved(@androidx.annotation.Nullable Intent launchIntent);
    }

    private void resolveLaunchIntentWithRetry(final String pkg, final int attempts, final long delayMs,
                                             final LaunchIntentCallback cb) {
        if (cb == null) return;
        if (android.text.TextUtils.isEmpty(pkg)) {
            cb.onResolved(null);
            return;
        }
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] left = new int[]{Math.max(1, attempts)};
        final Runnable[] r = new Runnable[1];
        r[0] = () -> {
            Intent li = null;
            try { li = getPackageManager().getLaunchIntentForPackage(pkg); } catch (Throwable ignored) {}
            if (li != null || left[0] <= 1) {
                cb.onResolved(li);
            } else {
                left[0]--;
                h.postDelayed(r[0], Math.max(50L, delayMs));
            }
        };
        h.post(r[0]);
    }


    private boolean shouldConfirmFileOpenInstall() {
        try {
            return prefs == null || prefs.getBoolean(KEY_CONFIRM_FILE_OPEN_INSTALL, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void showInstallConfirmationDialog() {
        try {
            final String path = localFile == null ? "" : localFile.getAbsolutePath();
            final InstallUiInfo info = TextUtils.isEmpty(path) ? null : probeInstallUiInfo(path, openedLabel);
            String display = info == null ? null : info.appLabel;
            if (TextUtils.isEmpty(display)) display = openedLabel;
            if (TextUtils.isEmpty(display) && localFile != null) display = localFile.getName();
            if (TextUtils.isEmpty(display)) display = "package";

            String message = "Install this package?";
            if (info != null && !TextUtils.isEmpty(info.packageName)) {
                message += "\n" + info.packageName;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Install " + display)
                    .setMessage(message)
                    .setPositiveButton("Install", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        new Thread(this::doInstallFromLocalFile, "file-open-install").start();
                    })
                    .setNegativeButton("Cancel", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
                        finishAndRemove();
                    })
                    .setOnCancelListener(d -> {
                        try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
                        finishAndRemove();
                    })
                    .show();
        } catch (Throwable t) {
            showFailureDialog("Install confirmation failed:\n" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void showDoneOpenDialog(@Nullable InstallUiInfo info) {
        try {
            String pkg = (info == null ? null : info.packageName);
            String label = (info == null ? null : info.appLabel);

            String display = (label != null && !label.trim().isEmpty()) ? label : openedLabel;
            if (display == null || display.trim().isEmpty()) display = "Package";

            String msg = display;
            if (pkg != null && !pkg.trim().isEmpty() && (label == null || !label.contains(pkg))) {
                msg = display + "\n" + pkg;
            }

            final String pkgFinal = (pkg == null) ? "" : pkg.trim();
            final String msgFinal = msg;

            try {
                AlertDialog.Builder bld = new AlertDialog.Builder(this)
                        .setTitle("Install complete")
                        .setMessage(msgFinal);

                if (!TextUtils.isEmpty(pkgFinal)) {
                    bld.setPositiveButton("Open", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        startInstalledPackage(pkgFinal);
                        finishAndRemove();
                    });
                    bld.setNegativeButton("Done", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        finishAndRemove();
                    });
                } else {
                    bld.setPositiveButton("Done", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        finishAndRemove();
                    });
                }

                bld.setOnCancelListener(d -> finishAndRemove());
                bld.show();
            } catch (Throwable ignored) {
                try { finishAndRemove(); } catch (Throwable ignored2) {}
            }
        } catch (Throwable ignored) {
            try { finishAndRemove(); } catch (Throwable ignored2) {}
        }
    }

    private void showFailureDialog(String msg) {
        try {
            AlertDialog.Builder bld = new AlertDialog.Builder(this)
                    .setTitle("Install failed")
                    .setMessage(msg)
                    .setPositiveButton("OK", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        finishAndRemove();
                    })
                    .setOnCancelListener(d -> finishAndRemove());

            // Offer a debug log save option when we have diagnostics captured.
            if (!TextUtils.isEmpty(lastInstallDebugLog)) {
                bld.setNeutralButton("Save log", (d, w) -> {
                    try { saveInstallDebugLog(lastInstallDebugLog); } catch (Throwable ignored) {}
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    finishAndRemove();
                });
            }

            bld.show();
        } catch (Throwable ignored) {
            try { Toast.makeText(this, "Install failed", Toast.LENGTH_SHORT).show(); } catch (Throwable ignored2) {}
            finishAndRemove();
        }
    }

    private void showReplaceInstalledPackageDialog(String msg, String packageName, boolean versionDowngrade) {
        try {
            String pkg = packageName == null ? "" : packageName.trim();
            boolean canRetryDowngrade = versionDowngrade && !shouldAllowDowngradeInstall();
            String reason = versionDowngrade
                    ? (canRetryDowngrade
                            ? "A newer version is already installed. Tap Retry Downgrade to reinstall using Android's downgrade flag (-d), or uninstall the installed package first if the device still blocks it."
                            : "A newer version is already installed. The downgrade flag is enabled, but Android still blocked the install. Uninstall the installed package, then install this package.")
                    : "The installed package is signed with a different certificate. Android will not update it in place. Uninstall the installed package, then install this package.";
            String text = msg + "\n\n" + reason;
            AlertDialog.Builder bld = new AlertDialog.Builder(this)
                    .setTitle(versionDowngrade ? "Package downgrade blocked" : "Replace installed package")
                    .setMessage(text)
                    .setPositiveButton(canRetryDowngrade ? "Retry Downgrade" : "Uninstall & Install", (d, w) -> {
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        if (canRetryDowngrade) retryInstallWithDowngradeFlag();
                        else uninstallPackageAndRetry(pkg);
                    })
                    .setNegativeButton("Cancel", (d, w) -> {
                        keepLocalFileForRetry = false;
                        allowDowngradeForRetry = false;
                        try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
                        try { d.dismiss(); } catch (Throwable ignored) {}
                        finishAndRemove();
                    })
                    .setOnCancelListener(d -> {
                        keepLocalFileForRetry = false;
                        allowDowngradeForRetry = false;
                        try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
                        finishAndRemove();
                    });

            if (canRetryDowngrade && !TextUtils.isEmpty(pkg)) {
                bld.setNeutralButton("Uninstall & Install", (d, w) -> {
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    uninstallPackageAndRetry(pkg);
                });
            } else if (!TextUtils.isEmpty(lastInstallDebugLog)) {
                bld.setNeutralButton("Save log", (d, w) -> {
                    try { saveInstallDebugLog(lastInstallDebugLog); } catch (Throwable ignored) {}
                    keepLocalFileForRetry = false;
                    allowDowngradeForRetry = false;
                    try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
                    try { d.dismiss(); } catch (Throwable ignored) {}
                    finishAndRemove();
                });
            }

            bld.show();
        } catch (Throwable ignored) {
            showFailureDialog(msg);
        }
    }

    private void retryInstallWithDowngradeFlag() {
        new Thread(() -> {
            try {
                keepLocalFileForRetry = false;
                allowDowngradeForRetry = true;
                doInstallFromLocalFile();
            } catch (Throwable t) {
                allowDowngradeForRetry = false;
                final String fail = "Downgrade retry failed:\n" + t.getClass().getSimpleName() + ": " + t.getMessage();
                try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
                runOnUiThread(() -> showFailureDialog(fail));
            }
        }, "file-open-downgrade-retry").start();
    }

    private void uninstallPackageAndRetry(String packageName) {
        new Thread(() -> {
            try {
                if (TextUtils.isEmpty(packageName)) {
                    keepLocalFileForRetry = false;
                    runOnUiThread(() -> showFailureDialog("Package name could not be resolved."));
                    return;
                }
                runOnUiThread(() -> {
                    try { Toast.makeText(this, "Replacing " + packageName + "...", Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
                });

                CmdResult un = runShizukuShellCapture("am force-stop " + shQuote(packageName) + " 2>/dev/null || true; pm uninstall --user 0 " + shQuote(packageName));
                String combined = ((un == null || un.stdout == null) ? "" : un.stdout) + "\n" + ((un == null || un.stderr == null) ? "" : un.stderr);
                boolean ok = un != null && (un.exitCode == 0 || combined.toLowerCase(Locale.US).contains("success"));
                if (!ok) {
                    keepLocalFileForRetry = false;
                    final String fail = "Uninstall failed for " + packageName + ":\n" + (TextUtils.isEmpty(combined.trim()) ? ("exit " + (un == null ? 1 : un.exitCode)) : combined.trim());
                    try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
                    runOnUiThread(() -> showFailureDialog(fail));
                    return;
                }

                keepLocalFileForRetry = false;
                allowDowngradeForRetry = false;
                doInstallFromLocalFile();
            } catch (Throwable t) {
                keepLocalFileForRetry = false;
                final String fail = "Replace install failed:\n" + t.getClass().getSimpleName() + ": " + t.getMessage();
                try { cleanupManagedImportFile(localFile); } catch (Throwable ignored) {}
                runOnUiThread(() -> showFailureDialog(fail));
            }
        }, "file-open-replace-install").start();
    }

    private static boolean isExistingPackageInstallConflict(CmdResult result) {
        if (result == null) return false;
        return PackageInstallResults.isExistingPackageInstallConflict(result.stdout, result.stderr);
    }

    private static boolean isExistingPackageInstallConflict(String out, String err) {
        return PackageInstallResults.isExistingPackageInstallConflict(out, err);
    }

    private static boolean isPackageUpdateIncompatible(CmdResult result) {
        if (result == null) return false;
        return PackageInstallResults.isPackageUpdateIncompatible(result.stdout, result.stderr);
    }

    private static boolean isPackageUpdateIncompatible(String out, String err) {
        return PackageInstallResults.isPackageUpdateIncompatible(out, err);
    }

    private static boolean isPackageVersionDowngrade(String out, String err) {
        return PackageInstallResults.isPackageVersionDowngrade(out, err);
    }

    private String resolveReplacementPackageName(String path, String originalLabel, String out, String err) {
        try {
            InstallUiInfo info = probeInstallUiInfo(path, originalLabel);
            if (info != null && !TextUtils.isEmpty(info.packageName) && !TextUtils.equals(info.packageName, getPackageName())) {
                return info.packageName.trim();
            }
        } catch (Throwable ignored) {}

        String fromError = extractPackageNameFromInstallText(out, err);
        if (!TextUtils.isEmpty(fromError) && !TextUtils.equals(fromError, getPackageName())) {
            return fromError.trim();
        }

        try {
            InstallUiInfo info = probeInstalledPackageFromNameHints(
                    originalLabel,
                    localFile == null ? null : localFile.getName(),
                    openedUri == null ? null : openedUri.getLastPathSegment());
            if (info != null && !TextUtils.isEmpty(info.packageName) && !TextUtils.equals(info.packageName, getPackageName())) {
                return info.packageName.trim();
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private String extractPackageNameFromInstallText(String... texts) {
        try {
            if (texts == null) return null;
            Pattern[] patterns = new Pattern[]{
                    Pattern.compile("(?i)existing\\s+package\\s+([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)\\s+signatures"),
                    Pattern.compile("(?i)package\\s+([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)\\s+signatures\\s+do\\s+not\\s+match"),
                    Pattern.compile("(?i)existing\\s+package\\s+([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)")
            };
            for (String text : texts) {
                if (TextUtils.isEmpty(text)) continue;
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(text);
                    if (matcher.find()) {
                        String pkg = matcher.group(1);
                        if (isReasonablePackageName(pkg) && !TextUtils.equals(pkg, getPackageName())) return pkg;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void startInstalledPackage(String packageName) {
        final String pkg = packageName == null ? "" : packageName.trim();
        if (TextUtils.isEmpty(pkg)) return;

        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                return;
            }
        } catch (Throwable ignored) {}

        new Thread(() -> {
            try {
                runShizukuShellCapture("monkey -p " + shQuote(pkg) + " -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1");
            } catch (Throwable ignored) {}
        }, "file-open-launch-package").start();
    }

    private void saveInstallDebugLog(String text) {
        try {
            File root = getExternalFilesDir(null);
            if (root == null) {
                Toast.makeText(this, "No external files dir", Toast.LENGTH_SHORT).show();
                return;
            }
            File dir = new File(root, "debug");
            if (!dir.exists()) dir.mkdirs();
            String name = "install_debug_" + System.currentTimeMillis() + ".txt";
            File out = new File(dir, name);
            try (FileOutputStream fos = new FileOutputStream(out, false)) {
                fos.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            }

            // Also copy to an easy-to-browse public path using Shizuku shell, so the user
            // doesn't have to dig through Android/data.
            String bestPath = out.getAbsolutePath();
            try {
                if (isShizukuReadyAndGranted()) {
                    String publicOut = PUBLIC_LOG_DIR + "/" + name;
                    String publicOutFallback = PUBLIC_LOG_DIR_FALLBACK + "/" + name;

                    // Try the canonical path first; if that fails, fall back to /sdcard.
                    // Echo the path we actually wrote so the toast is accurate.
                    String cmd = "if mkdir -p " + shQuote(PUBLIC_LOG_DIR)
                            + " && cp -f " + shQuote(out.getAbsolutePath()) + " " + shQuote(publicOut)
                            + "; then echo " + shQuote(publicOut)
                            + "; elif mkdir -p " + shQuote(PUBLIC_LOG_DIR_FALLBACK)
                            + " && cp -f " + shQuote(out.getAbsolutePath()) + " " + shQuote(publicOutFallback)
                            + "; then echo " + shQuote(publicOutFallback)
                            + "; fi 2>/dev/null";

                    CmdResult cp = runShizukuShellCapture(cmd);
                    if (cp != null && cp.exitCode == 0) {
                        String echoed = cp.stdout == null ? "" : cp.stdout.trim();
                        if (!TextUtils.isEmpty(echoed)) bestPath = echoed;
                    }
                }
            } catch (Throwable ignored) {}

            Toast.makeText(this, "Saved: " + bestPath, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            try { Toast.makeText(this, "Save failed: " + t.getMessage(), Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
        }
    }

    private void finishAndRemove() {
        try { moveTaskToBack(true); } catch (Throwable ignored) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }
        } catch (Throwable ignored) {
            try { finish(); } catch (Throwable ignored2) {}
        }
    }

    // ---------- Install + shell helpers ----------

    private boolean isShizukuReadyAndGranted() {
        boolean binderAlive = false;
        try { binderAlive = Shizuku.pingBinder(); } catch (Throwable ignored) { binderAlive = false; }
        if (!binderAlive) return false;
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class CmdResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        CmdResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private CmdResult runShizukuShellCapture(String shCmd) {
        try {
            ExecMode modeNow = execMode;
            if (prefs != null) {
                try { modeNow = ExecMode.get(prefs); } catch (Throwable ignored) {}
            }

            if (modeNow == ExecMode.LADB) {
                if (!ladbConnected) {
                    tryAutoConnectLadb();
                }
                if (!ladbConnected) {
                    return new CmdResult(1, "", "LADB not connected. Enable Wireless Debugging and connect first.");
                }
                LadbClient.CmdResult r = ladbClient.shellShC(shCmd);
                return new CmdResult(r.exitCode, r.stdout, r.stderr);
            }

            if (modeNow == ExecMode.SYSTEM) {
                Process p = new ProcessBuilder("sh", "-c", shCmd).redirectErrorStream(false).start();
                String out = readStream(p.getInputStream());
                String err = readStream(p.getErrorStream());
                int exit = p.waitFor();
                return new CmdResult(exit, out, err);
            }

            // SHIZUKU (default)
            String[] argv = new String[]{"sh", "-c", shCmd};
            Process p = ShizukuCompat.newProcess(argv, null, null);

            String out = readStream(p.getInputStream());
            String err = readStream(p.getErrorStream());
            int exit = p.waitFor();

            return new CmdResult(exit, out, err);
        } catch (Throwable t) {
            return new CmdResult(1, "", t.toString());
        }
    }

    private static String readStream(InputStream in) {
        if (in == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }


    private void tryAutoConnectLadb() {
        try {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (ladbClient == null) ladbClient = new LadbClient(this);

            int port = prefs.getInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT);
            LadbClient.CmdResult r = ladbClient.connect(LadbClient.DEFAULT_HOST, port);
            String all = (r.stdout + "\n" + r.stderr).toLowerCase();
            ladbConnected = r.exitCode == 0 && (all.contains("connected") || all.contains("already connected"));
        } catch (Throwable ignored) {
            ladbConnected = false;
        }
    }

    
    private CmdResult runBuiltInPmSessionInstall(String apkPath) {
        try {
            apkPath = prepareInstallInputPathForPm(apkPath);

            CmdResult r2 = runShizukuShellCapture("stat -c %s " + shQuote(apkPath) + " 2>/dev/null || wc -c < " + shQuote(apkPath));
            String combined2 = (r2.stdout == null ? "" : r2.stdout) + "\n" + (r2.stderr == null ? "" : r2.stderr);
            String size = extractDigits(combined2);
            if (r2.exitCode != 0 || TextUtils.isEmpty(size)) {
                String msg = (r2.exitCode != 0)
                        ? ("Failed to get apk size\n" + combined2)
                        : ("Failed to parse apk size\n" + combined2);
                return new CmdResult(r2.exitCode != 0 ? r2.exitCode : 1, combined2, msg);
            }

            CmdResult r1 = runPmInstallCreateWithCompatibility(size);
            String combined1 = (r1.stdout == null ? "" : r1.stdout) + "\n" + (r1.stderr == null ? "" : r1.stderr);
            String sid = extractInstallSessionId(combined1);
            if (r1.exitCode != 0 || TextUtils.isEmpty(sid)) {
                String msg = (r1.exitCode != 0)
                        ? ("pm install-create failed\n" + combined1)
                        : ("Failed to parse install session id\n" + combined1);
                return new CmdResult(r1.exitCode != 0 ? r1.exitCode : 1, combined1, msg);
            }

            CmdResult r3 = runShizukuShellCapture("pm install-write -S " + size + " " + sid + " base.apk " + shQuote(apkPath));
            if (r3.exitCode != 0) {
                String combined3 = (r3.stdout == null ? "" : r3.stdout) + "\n" + (r3.stderr == null ? "" : r3.stderr);
                String msg = "pm install-write failed\n" + combined3;
                return new CmdResult(r3.exitCode, combined3, msg);
            }

            CmdResult r4 = runShizukuShellCapture("pm install-commit " + sid);
            return r4;
        } catch (Throwable t) {
            return new CmdResult(1, "", t.toString());
        }
    }

private CmdResult runBuiltInArchivePmSessionInstall(String archivePath) {
        PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "begin originalArchive=" + PackageInstallDebug.describePath(archivePath));
        archivePath = prepareInstallInputPathForPm(archivePath);
        PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "preparedArchive=" + PackageInstallDebug.describePath(archivePath));

        String stageDir = null;
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        try {
            // Stage an unzip binary if available (assets/bin/<abi>/unzip or assets/bin/unzip) and mirror it into /data/local/tmp.
            File root = getExternalFilesDir(null);
            if (root != null) {
                File scriptsDir = new File(root, EXT_SCRIPTS_DIR);
                if (!scriptsDir.exists()) scriptsDir.mkdirs();
                try { ensureBundledUnzip(scriptsDir); } catch (Throwable ignored) {}
            }

        final String stageRoot = PUBLIC_STAGE_DIR;
            // Best-effort cleanup of any leftover stage folders from prior runs.
            try { runShizukuShellCapture("rm -rf " + shQuote(stageRoot) + "/pkg_*" + " 2>/dev/null || true"); } catch (Throwable ignored) {}
            stageDir = stageRoot + "/pkg_" + System.currentTimeMillis();
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "stageDir=" + stageDir);

            CmdResult mk = runShizukuShellCapture("mkdir -p " + shQuote(stageDir) + " 2>/dev/null");
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "mkdir exit=" + mk.exitCode
                    + ", stdoutLen=" + (mk.stdout == null ? 0 : mk.stdout.length())
                    + ", stderrLen=" + (mk.stderr == null ? 0 : mk.stderr.length()));
            if (mk.exitCode != 0) {
                err.append("Failed to create staging dir: ").append(stageDir).append("\\n").append(mk.stderr);
                return new CmdResult(1, out.toString(), err.toString());
            }

            out.append("[install] Extracting archive...\\n");
            CmdResult ex = extractArchiveToDir(archivePath, stageDir);
            out.append(ex.stdout == null ? "" : ex.stdout);
            err.append(ex.stderr == null ? "" : ex.stderr);
            if (ex.exitCode != 0) {
                err.append("\\n[install] unzip failed for ").append(archivePath);
                return new CmdResult(1, out.toString(), err.toString());
            }

            // Find all APKs inside the extracted archive.
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "find APKs begin");
            CmdResult fl = runShizukuShellCapture("find " + shQuote(stageDir) + " -type f -name '*.apk' 2>/dev/null");
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "find APKs exit=" + fl.exitCode
                    + ", stdoutLen=" + (fl.stdout == null ? 0 : fl.stdout.length())
                    + ", stderrLen=" + (fl.stderr == null ? 0 : fl.stderr.length()));
            String files = (fl.stdout == null ? "" : fl.stdout).trim();
            if (TextUtils.isEmpty(files)) {
                err.append("\\n[install] No .apk files found after extract.");
                return new CmdResult(1, out.toString(), err.toString());
            }

            ArrayList<String> apks = new ArrayList<>();
            for (String line : files.split("\\n")) {
                String p = (line == null ? "" : line.trim());
                if (!TextUtils.isEmpty(p)) apks.add(p);
            }
            if (apks.isEmpty()) {
                err.append("\\n[install] No .apk files found after extract.");
                return new CmdResult(1, out.toString(), err.toString());
            }

            // Diagnostics: list discovered APKs (truncated) to help troubleshoot odd archive layouts.
            out.append("[diag] Found ").append(apks.size()).append(" APK(s) in archive:\\n");
            int maxList = Math.min(20, apks.size());
            for (int i = 0; i < maxList; i++) {
                out.append("  ").append(apks.get(i)).append("\\n");
            }
            if (apks.size() > maxList) {
                out.append("  ... (+").append(apks.size() - maxList).append(" more)\\n");
            }

            String baseApk = selectBestBaseApk(apks);

            out.append("[diag] Base APK: ").append(baseApk == null ? "" : baseApk).append("\\n");

            // Select splits: default device ABI/locale/density only (plus base + feature/unknown config splits).
            boolean customSplit = false;
            try {
                SharedPreferences sp = getSharedPreferences("perms_test", MODE_PRIVATE);
                customSplit = sp.getBoolean("custom_split_options", true);
            } catch (Throwable ignored) {}
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "split selector begin customSplit=" + customSplit
                    + ", finishing=" + isFinishing()
                    + ", destroyed=" + (Build.VERSION.SDK_INT >= 17 && isDestroyed())
                    + ", hasWindowFocus=" + hasWindowFocus());
            List<String> selectedApks = PackageArchiveSplitSelector.selectForDevice(this, apks, baseApk, customSplit);
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "split selector result=" + PackageInstallDebug.describePathList(selectedApks, 30));
            if (selectedApks == null || selectedApks.isEmpty()) {
                err.append("\n[install] Cancelled or no APKs selected.");
                return new CmdResult(1, out.toString(), err.toString());
            }
            apks = new ArrayList<>(selectedApks);

            long sessionSize = 0L;
            for (String p : apks) {
                long sz = getFileSizeBytes(p);
                if (sz > 0L) sessionSize += sz;
            }
            if (sessionSize <= 0L) sessionSize = 1024L * 1024L;

            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "install-create begin sessionSize=" + sessionSize);
            CmdResult create = runPmInstallCreateWithCompatibility(String.valueOf(sessionSize));
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "install-create exit=" + create.exitCode
                    + ", stdout=" + (create.stdout == null ? "" : create.stdout.trim())
                    + ", stderr=" + (create.stderr == null ? "" : create.stderr.trim()));
            out.append(create.stdout == null ? "" : create.stdout);
            err.append(create.stderr == null ? "" : create.stderr);
            String sid = extractInstallSessionId((create.stdout == null ? "" : create.stdout) + "\\n" + (create.stderr == null ? "" : create.stderr));
            if (create.exitCode != 0 || TextUtils.isEmpty(sid)) {
                err.append("\\n[install] pm install-create failed");
                return new CmdResult(create.exitCode != 0 ? create.exitCode : 1, out.toString(), err.toString());
            }

            out.append("[diag] Install session: ").append(sid).append("\\n");

            int splitIdx = 0;
            for (String p : apks) {
                boolean isBase = (p != null && p.equals(baseApk));
                String name = isBase ? "base.apk" : ("split_" + String.format(Locale.US, "%02d", splitIdx++) + ".apk");
                long sz = getFileSizeBytes(p);

                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "install-write begin name=" + name
                        + ", size=" + sz
                        + ", source=" + PackageInstallDebug.describePath(p));
                CmdResult wr = runShizukuShellCapture("pm install-write -S " + sz + " " + sid + " " + shQuote(name) + " " + shQuote(p));
                PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "install-write exit=" + wr.exitCode
                        + ", name=" + name
                        + ", stdout=" + (wr.stdout == null ? "" : wr.stdout.trim())
                        + ", stderr=" + (wr.stderr == null ? "" : wr.stderr.trim()));
                out.append(wr.stdout == null ? "" : wr.stdout);
                err.append(wr.stderr == null ? "" : wr.stderr);
                if (wr.exitCode != 0) {
                    err.append("\\n[install] pm install-write failed for ").append(p);
                    return new CmdResult(wr.exitCode, out.toString(), err.toString());
                }
            }

            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "install-commit begin sid=" + sid);
            CmdResult commit = runShizukuShellCapture("pm install-commit " + sid);
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN_ARCHIVE, "install-commit exit=" + commit.exitCode
                    + ", stdout=" + (commit.stdout == null ? "" : commit.stdout.trim())
                    + ", stderr=" + (commit.stderr == null ? "" : commit.stderr.trim()));
            out.append(commit.stdout == null ? "" : commit.stdout);
            err.append(commit.stderr == null ? "" : commit.stderr);
            return new CmdResult(commit.exitCode, out.toString(), err.toString());
        } catch (Throwable t) {
            return new CmdResult(1, out.toString(), (err.toString() + "\\n" + t));
        } finally {
            // Best-effort cleanup. Keep it quiet if it fails.
            try {
                if (!TextUtils.isEmpty(stageDir)) {
                    runShizukuShellCapture("rm -rf " + shQuote(stageDir) + " 2>/dev/null || true");
                }
                // Clean any other leftover staging artifacts created by archive installs.
                runShizukuShellCapture("rm -rf " + shQuote(PUBLIC_STAGE_DIR) + "/pkg_*" + " 2>/dev/null || true");
                runShizukuShellCapture("rm -rf " + shQuote(PUBLIC_FILES_DIR) + "/*" + " 2>/dev/null || true");
                // Clean stale install staging while keeping staged tool binaries intact.
                runShizukuShellCapture("rm -rf " + shQuote(PUBLIC_BIN_DIR + "/stage") + " 2>/dev/null || true");
            } catch (Throwable ignored) {}
        }
    }

    
    

    private String selectBestBaseApk(List<String> apks) {
        if (apks == null || apks.isEmpty()) return null;
        String bestPath = null;
        int bestScore = Integer.MIN_VALUE;
        long bestSize = -1L;
        for (String p : apks) {
            if (TextUtils.isEmpty(p)) continue;
            String name = new File(p).getName();
            int score = scoreApkEntryName(name);
            long size = getFileSizeBytes(p);
            if (bestPath == null || score > bestScore || (score == bestScore && size > bestSize)) {
                bestPath = p;
                bestScore = score;
                bestSize = size;
            }
        }
        return bestPath == null ? apks.get(0) : bestPath;
    }

    private CmdResult extractArchiveToDir(String archivePath, String outDir) {
        try {
            if (TextUtils.isEmpty(archivePath) || TextUtils.isEmpty(outDir)) {
                return new CmdResult(1, "", "Missing paths");
            }
            String zip = shQuote(archivePath);
            String dir = shQuote(outDir);

            // User request: prefer system tools first; use bundled staged unzip last.
            String bundled = PUBLIC_BIN_DIR + "/unzip";
            String bq = shQuote(bundled);

            String[] attempts = new String[] {
                    // Common system paths
                    "unzip -o " + zip + " -d " + dir,
                    "/system/bin/unzip -o " + zip + " -d " + dir,
                    "/system/xbin/unzip -o " + zip + " -d " + dir,
                    "/vendor/bin/unzip -o " + zip + " -d " + dir,
                    "/system_ext/bin/unzip -o " + zip + " -d " + dir,
                    "/product/bin/unzip -o " + zip + " -d " + dir,
                    "/odm/bin/unzip -o " + zip + " -d " + dir,
                    "/apex/com.android.runtime/bin/unzip -o " + zip + " -d " + dir,
                    "/apex/com.android.art/bin/unzip -o " + zip + " -d " + dir,

                    // BusyBox variants
                    "busybox unzip -o " + zip + " -d " + dir,
                    "/system/bin/busybox unzip -o " + zip + " -d " + dir,

                    // Toybox variants (syntax differs; no -o)
                    "toybox unzip -d " + dir + " " + zip,
                    "toybox unzip " + zip + " -d " + dir,
                    "/system/bin/toybox unzip -d " + dir + " " + zip,
                    "/system/bin/toybox unzip " + zip + " -d " + dir,

                    // Bundled (staged) unzip last
                    "[ -x " + bq + " ] && " + bq + " -o " + zip + " -d " + dir,
                    "[ -x " + bq + " ] && " + bq + " -d " + dir + " " + zip,
                    "[ -x " + bq + " ] && " + bq + " " + zip + " -d " + dir
            };

            StringBuilder diag = new StringBuilder();
            diag.append("[diag] unzip attempts (exit codes):\n");

            CmdResult last = new CmdResult(1, "", "unzip failed");
            for (String cmd : attempts) {
                if (TextUtils.isEmpty(cmd)) continue;
                // Keep extraction quiet; we provide our own diagnostics.
                CmdResult r = runShizukuShellCapture(cmd + " >/dev/null 2>&1");
                last = r;
                diag.append("  - ").append(shortCmdForDiag(cmd)).append(" => ").append(r.exitCode).append("\n");
                if (r.exitCode == 0) {
                    diag.append("[diag] unzip backend: ").append(shortCmdForDiag(cmd)).append("\n");
                    return new CmdResult(0, diag.toString(), "");
                }
            }
            return new CmdResult(last.exitCode != 0 ? last.exitCode : 1, diag.toString(), "unzip failed");
        } catch (Throwable t) {
            return new CmdResult(1, "", t.toString());
        }
    }

    private static String shortCmdForDiag(String cmd) {
        try {
            if (cmd == null) return "";
            String c = cmd.trim();
            // Keep it readable: collapse whitespace and trim long strings.
            c = c.replaceAll("\\s+", " ");
            if (c.length() > 80) c = c.substring(0, 80) + "…";
            return c;
        } catch (Throwable ignored) {
            return cmd;
        }
    }

    private long getFileSizeBytes(String path) {
        try {
            if (TextUtils.isEmpty(path)) return 0;
            String q = shQuote(path);
            // Try stat first, then wc -c as a fallback.
            CmdResult r = runShizukuShellCapture("stat -c %s " + q + " 2>/dev/null || busybox stat -c %s " + q + " 2>/dev/null || wc -c < " + q + " 2>/dev/null");
            String s = (r.stdout == null ? "" : r.stdout).trim();
            String d = extractDigits(s);
            if (TextUtils.isEmpty(d)) return 0;
            return Long.parseLong(d);
        } catch (Throwable ignored) {
            return 0;
        }
    }
    private String queryDisplayName(Uri uri) {
        if (uri == null) return null;
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                Cursor c = null;
                try {
                    c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                    if (c != null && c.moveToFirst()) {
                        int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) return c.getString(idx);
                    }
                } finally {
                    if (c != null) c.close();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String findLikelySingleSplitApkName(String... names) {
        if (names == null) return null;
        for (String raw : names) {
            if (isLikelySingleSplitApkName(raw)) {
                return normalizeCandidateApkName(raw);
            }
        }
        return null;
    }

    private boolean isLikelySingleSplitApkName(String rawName) {
        String n = normalizeCandidateApkName(rawName);
        if (TextUtils.isEmpty(n) || !n.endsWith(".apk")) return false;
        if ("base.apk".equals(n) || n.startsWith("base-")) return false;
        return n.startsWith("config.")
                || n.startsWith("split_config.")
                || n.startsWith("split_");
    }

    private String normalizeCandidateApkName(String rawName) {
        if (rawName == null) return null;
        String n = rawName.trim();
        if (n.isEmpty()) return null;

        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < n.length()) {
            n = n.substring(slash + 1);
        }

        n = n.replaceFirst("^[0-9]+_+", "");
        return n.toLowerCase(Locale.US);
    }

    private boolean shouldShowSplitApkWarningDialog() {
        try {
            SharedPreferences sp = prefs != null ? prefs : getSharedPreferences(PREFS, MODE_PRIVATE);
            return sp.getBoolean(KEY_SPLIT_APK_SHOW_WARNING_DIALOG, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean tryHandOffSplitApkIntent() {
        final Uri uri = openedUri;
        if (uri == null) return false;

        final PackageManager pm = getPackageManager();
        final ComponentName alias = new ComponentName(getPackageName(), getPackageName() + ".FileOpenAlias");
        final String resolvedType = resolveOpenedMimeType(uri);

        final ArrayList<Intent> candidates = new ArrayList<>();
        candidates.add(buildExternalHandoffIntent(Intent.ACTION_INSTALL_PACKAGE, uri, resolvedType));
        candidates.add(buildExternalHandoffIntent(Intent.ACTION_VIEW, uri, resolvedType));
        candidates.add(buildExternalHandoffIntent(Intent.ACTION_VIEW, uri, "application/vnd.android.package-archive"));

        try {
            pm.setComponentEnabledSetting(alias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);

            for (Intent candidate : candidates) {
                if (candidate == null) continue;
                ComponentName resolved = null;
                try {
                    resolved = candidate.resolveActivity(pm);
                } catch (Throwable ignored) {
                }
                if (resolved == null) continue;
                if (getPackageName().equals(resolved.getPackageName())) continue;
                try {
                    startActivity(candidate);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                pm.setComponentEnabledSetting(alias,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private Intent buildExternalHandoffIntent(String action, Uri uri, String mimeType) {
        if (uri == null) return null;

        Intent intent = new Intent(action);
        if (!TextUtils.isEmpty(mimeType)) {
            intent.setDataAndType(uri, mimeType);
        } else {
            intent.setData(uri);
        }
        intent.setComponent(null);
        intent.setPackage(null);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            intent.setClipData(ClipData.newUri(getContentResolver(), openedLabel == null ? "package" : openedLabel, uri));
        } catch (Throwable ignored) {
        }
        return intent;
    }

    private String resolveOpenedMimeType(Uri uri) {
        if (uri == null) return null;
        try {
            Intent src = getIntent();
            if (src != null && !TextUtils.isEmpty(src.getType())) {
                return src.getType();
            }
        } catch (Throwable ignored) {
        }
        try {
            ContentResolver cr = getContentResolver();
            if (cr != null) {
                String type = cr.getType(uri);
                if (!TextUtils.isEmpty(type)) return type;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean shouldBypassLowTargetSdkBlock() {
        try {
            SharedPreferences sp = prefs != null ? prefs : getSharedPreferences(PREFS, MODE_PRIVATE);
            return Build.VERSION.SDK_INT >= 35
                    && sp.getBoolean(KEY_INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK, true);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String getPmLowTargetBypassFlag() {
        return shouldBypassLowTargetSdkBlock() ? " --bypass-low-target-sdk-block" : "";
    }

    private boolean shouldIgnoreDexoptProfile() {
        try {
            SharedPreferences sp = prefs != null ? prefs : getSharedPreferences(PREFS, MODE_PRIVATE);
            return sp.getBoolean(KEY_INSTALL_IGNORE_DEXOPT_PROFILE, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private String getPmIgnoreDexoptProfileFlag() {
        return shouldIgnoreDexoptProfile() ? " --ignore-dexopt-profile" : "";
    }

    private boolean shouldAllowDowngradeInstall() {
        try {
            SharedPreferences sp = prefs != null ? prefs : getSharedPreferences(PREFS, MODE_PRIVATE);
            return allowDowngradeForRetry || (sp != null && sp.getBoolean(KEY_INSTALL_ALLOW_DOWNGRADE, false));
        } catch (Throwable ignored) {
            return allowDowngradeForRetry;
        }
    }

    private String getPmDowngradeFlag() {
        return shouldAllowDowngradeInstall() ? " -d" : "";
    }

    private String buildPmInstallCreateCommand() {
        return buildPmInstallCreateCommand(null);
    }

    private String buildPmInstallCreateCommand(String sizeBytes) {
        return buildPmInstallCreateCommand(sizeBytes, true);
    }

    private String buildPmInstallCreateCommand(String sizeBytes, boolean includeOptionalFlags) {
        String size = "";
        if (!TextUtils.isEmpty(sizeBytes)) {
            size = " -S " + sizeBytes.trim();
        }
        String flags = includeOptionalFlags ? (getPmLowTargetBypassFlag() + getPmIgnoreDexoptProfileFlag()) : "";
        return "pm install-create" + size + flags + getPmDowngradeFlag();
    }

    private CmdResult runPmInstallCreateWithCompatibility(String sizeBytes) {
        CmdResult first = runShizukuShellCapture(buildPmInstallCreateCommand(sizeBytes, true));
        String firstText = ((first == null || first.stdout == null) ? "" : first.stdout)
                + "\n" + ((first == null || first.stderr == null) ? "" : first.stderr);
        if (first != null && first.exitCode == 0 && !TextUtils.isEmpty(extractInstallSessionId(firstText))) {
            return first;
        }

        // Some VR/older Android package managers reject optional install-create flags.
        // Retry once with only the session size so normal APK installs still work there.
        CmdResult plain = runShizukuShellCapture(buildPmInstallCreateCommand(sizeBytes, false));
        String plainText = ((plain == null || plain.stdout == null) ? "" : plain.stdout)
                + "\n" + ((plain == null || plain.stderr == null) ? "" : plain.stderr);
        if (plain != null && plain.exitCode == 0 && !TextUtils.isEmpty(extractInstallSessionId(plainText))) {
            return plain;
        }

        String combined = firstText + "\n[retry without optional install-create flags]\n" + plainText;
        int exit = first == null ? 1 : (first.exitCode != 0 ? first.exitCode : 1);
        return new CmdResult(exit, first == null ? "" : first.stdout, combined);
    }

    private static final class PreparedInstallSource {
        final File file;
        final boolean usedImportsCopy;

        PreparedInstallSource(File file, boolean usedImportsCopy) {
            this.file = file;
            this.usedImportsCopy = usedImportsCopy;
        }
    }

    private boolean shouldUseAndroidDataInstallPath() {
        try {
            return prefs != null && prefs.getBoolean(KEY_INSTALL_USE_ANDROID_DATA_PATH, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldRestageInstallInputPath(String path) {
        if (shouldUseAndroidDataInstallPath() || TextUtils.isEmpty(path)) return false;
        String p = path.trim();
        // pm install-write is executed by system_server. On recent Android builds, files
        // under shared/emulated storage may be visible to the shell but still rejected by
        // system_server because their FUSE SELinux context is not readable from that side.
        // Restage browser/file-manager inputs into /data/local/tmp before giving them to pm.
        return p.startsWith("/storage/emulated/0/")
                || p.startsWith("/sdcard/")
                || p.startsWith("/storage/self/primary/")
                || p.startsWith("/mnt/user/0/primary/");
    }

    private boolean shouldUseInstallStagingFolder() {
        try {
            return prefs != null && prefs.getBoolean(KEY_INSTALL_USE_STAGING_FOLDER, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldSkipInstallStagingForLargeFiles() {
        try {
            return prefs != null && prefs.getBoolean(KEY_INSTALL_SKIP_STAGING_LARGE_FILES, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private long queryInstallSourceSizeBytes(Uri uri) {
        try {
            if (uri == null) return -1L;
            if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
                File f = new File(uri.getPath() == null ? "" : uri.getPath());
                return f.isFile() ? f.length() : -1L;
            }

            try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.SIZE);
                    if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
                }
            } catch (Throwable ignored) {}

            try (AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (afd != null && afd.getLength() >= 0) return afd.getLength();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return -1L;
    }

    private boolean shouldBypassImportsStaging(long sizeBytes) {
        if (!shouldUseInstallStagingFolder()) return true;
        return shouldSkipInstallStagingForLargeFiles()
                && sizeBytes > INSTALL_SKIP_STAGING_LARGE_BYTES;
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
        try (Cursor c = getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex("_data");
                if (idx >= 0 && !c.isNull(idx)) {
                    return validateReadableInstallPath(c.getString(idx));
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String resolveOwnFileProviderPath(Uri uri) {
        try {
            if (uri == null || !ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) return null;
            if (!TextUtils.equals(getPackageName() + ".files", uri.getAuthority())) return null;

            List<String> segments = uri.getPathSegments();
            if (segments == null || segments.size() < 2) return null;
            String rootName = segments.get(0);
            StringBuilder rel = new StringBuilder();
            for (int i = 1; i < segments.size(); i++) {
                if (i > 1) rel.append('/');
                rel.append(segments.get(i));
            }

            if ("external_storage".equals(rootName)) {
                return validateReadableInstallPath("/storage/emulated/0/" + rel);
            }
            if ("external_files".equals(rootName)) {
                File base = getExternalFilesDir(null);
                if (base != null) return validateReadableInstallPath(new File(base, rel.toString()).getAbsolutePath());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String resolveDirectInstallSourcePath(Uri uri) {
        try {
            if (uri == null) return null;

            if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
                return validateReadableInstallPath(uri.getPath());
            }

            String ownProviderPath = resolveOwnFileProviderPath(uri);
            if (!TextUtils.isEmpty(ownProviderPath)) return ownProviderPath;

            String docId = null;
            try {
                if (DocumentsContract.isDocumentUri(this, uri)) {
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

    private File prepareInstallSourceFile(Uri uri, String label) {
        lastReadFailureDetails = "";
        try {
            long sizeBytes = queryInstallSourceSizeBytes(uri);
            if (shouldBypassImportsStaging(sizeBytes)) {
                String directPath = resolveDirectInstallSourcePath(uri);
                if (!TextUtils.isEmpty(directPath)) {
                    File direct = new File(directPath);
                    PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                            "using direct package source: " + PackageInstallDebug.describePath(direct.getAbsolutePath()));
                    return direct;
                }
            }
        } catch (Throwable t) {
            rememberReadFailure("direct path resolve", t);
        }
        File copied = copyToImports(uri, label);
        if (copied != null) return copied;

        // Some third-party apps hand us only a broken file:// path or bare temp filename
        // without a readable content grant. When normal ContentResolver reads fail, use the
        // already-required shell backend to look for the same package filename in generic
        // external handoff/cache/download locations, copy it into PermsTest imports, then
        // continue through the normal install and cleanup path. This is intentionally a
        // fallback only; normal picker/content URI behavior stays first.
        File shellCopied = tryShellCopyMissingIncomingPackage(uri, label);
        if (shellCopied != null) return shellCopied;

        return null;
    }

    private File tryShellCopyMissingIncomingPackage(Uri uri, String label) {
        FileOpenExternalPackageImportFallback fallback = new FileOpenExternalPackageImportFallback(
                this,
                buildExternalSourcePackageHints(getIntent()),
                this::isShizukuReadyAndGranted,
                this::getManagedImportsDir,
                command -> {
                    CmdResult r = runShizukuShellCapture(command);
                    return new FileOpenExternalPackageImportFallback.ShellResult(
                            r == null ? 1 : r.exitCode,
                            r == null ? "" : r.stdout,
                            r == null ? "" : r.stderr);
                },
                this::rememberReadFailure);
        return fallback.copyMissingIncomingPackage(uri, label);
    }

    private LinkedHashSet<String> buildExternalSourcePackageHints(Intent intent) {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        try { addSourcePackageHint(packages, getCallingPackage()); } catch (Throwable ignored) {}
        try {
            Uri referrer = getReferrer();
            addSourcePackageHint(packages, packageFromReferrerUri(referrer));
        } catch (Throwable ignored) {}
        try {
            Object extraReferrer = intent == null ? null : intent.getParcelableExtra(Intent.EXTRA_REFERRER);
            if (extraReferrer instanceof Uri) {
                addSourcePackageHint(packages, packageFromReferrerUri((Uri) extraReferrer));
            }
        } catch (Throwable ignored) {}
        try {
            String referrerName = intent == null ? null : intent.getStringExtra(Intent.EXTRA_REFERRER_NAME);
            if (!TextUtils.isEmpty(referrerName)) {
                addSourcePackageHint(packages, packageFromReferrerUri(Uri.parse(referrerName)));
                addSourcePackageHint(packages, referrerName);
            }
        } catch (Throwable ignored) {}
        try {
            String sourcePackage = intent == null ? null : intent.getStringExtra("android.intent.extra.PACKAGE_NAME");
            addSourcePackageHint(packages, sourcePackage);
        } catch (Throwable ignored) {}
        PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                "source package hints for external import fallback=" + packages.size());
        return packages;
    }

    private String packageFromReferrerUri(Uri referrer) {
        try {
            if (referrer == null) return null;
            if ("android-app".equalsIgnoreCase(referrer.getScheme())) return referrer.getHost();
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void addSourcePackageHint(LinkedHashSet<String> packages, String raw) {
        try {
            if (TextUtils.isEmpty(raw)) return;
            String value = raw.trim();
            if (value.startsWith("android-app://")) {
                value = packageFromReferrerUri(Uri.parse(value));
            }
            if (TextUtils.isEmpty(value)) return;
            if ("dev.perms.test".equals(value)) return;
            if (!value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) return;
            packages.add(value);
        } catch (Throwable ignored) {}
    }

    private boolean shouldUseInstallerScript() {
        try {
            return prefs != null && prefs.getBoolean(KEY_INSTALL_USE_INSTALLER_SCRIPT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String prepareInstallInputPathForInstallScript(String sourcePath) {
        if (!PackageInstallCommands.shouldForcePmReadableRestage(sourcePath, shouldUseAndroidDataInstallPath())) {
            return sourcePath;
        }
        PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN, "restaging script install input for pm/system_server access: "
                + PackageInstallDebug.describePath(sourcePath));
        return prepareInstallInputPathForPm(sourcePath);
    }

    private String buildInstallScriptCommand(File scriptFile, String inputPath) {
        return PackageInstallCommands.buildInstallScriptCommand(
                scriptFile,
                inputPath,
                shouldUseAndroidDataInstallPath(),
                shouldUseInstallStagingFolder(),
                shouldSkipInstallStagingForLargeFiles(),
                shouldBypassLowTargetSdkBlock(),
                shouldIgnoreDexoptProfile());
    }

    private String buildRestageInstallInputCommand(String sourcePath, String stagedPath) {
        return PackageInstallCommands.buildRestageInstallInputCommand(PUBLIC_FILES_DIR, sourcePath, stagedPath);
    }

    private String prepareInstallInputPathForPm(String sourcePath) {
        if (!shouldRestageInstallInputPath(sourcePath)) {
            return sourcePath;
        }

        String name = new File(sourcePath).getName();
        if (name == null) name = "package.bin";
        name = name.replaceAll("[\\/:*?\"<>|\r\n]", "_").trim();
        if (name.isEmpty()) name = "package.bin";

        final String stagedPath = PUBLIC_FILES_DIR + "/" + System.currentTimeMillis() + "_" + name;
        CmdResult r = runShellCommandCaptureBlocking(buildRestageInstallInputCommand(sourcePath, stagedPath));
        if (r.exitCode == 0) {
            return stagedPath;
        }

        return sourcePath;
    }

    private CmdResult runShellCommandCaptureBlocking(String cmd) {
        return runShizukuShellCapture(cmd);
    }

    private File copyToImports(Uri uri, String label) {
        File out = null;
        try {
            File root = getExternalFilesDir(null);
            if (root == null) throw new IOException("external files dir is unavailable");
            File dir = new File(root, EXT_IMPORTS_DIR);
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("imports dir could not be created: " + dir.getAbsolutePath());
            }

            String name = (label == null ? "package" : label);
            name = name.replaceAll("[\\\\/:*?\\\"<>|\\r\\n]", "_").trim();
            if (name.isEmpty()) name = "package";

            out = new File(dir, System.currentTimeMillis() + "_" + name);
            // Ensure we keep extension if present.
            if (!out.getName().toLowerCase(Locale.US).matches(".*\\.(apk|apks|apkm|xapk|zip)$")) {
                // Try to infer from uri path.
                String pth = uri == null ? "" : uri.toString().toLowerCase(Locale.US);
                if (pth.endsWith(".apk")) out = new File(dir, System.currentTimeMillis() + "_" + name + ".apk");
                else if (pth.endsWith(".xapk")) out = new File(dir, System.currentTimeMillis() + "_" + name + ".xapk");
                else if (pth.endsWith(".apkm")) out = new File(dir, System.currentTimeMillis() + "_" + name + ".apkm");
                else if (pth.endsWith(".apks")) out = new File(dir, System.currentTimeMillis() + "_" + name + ".apks");
                else if (pth.endsWith(".zip")) out = new File(dir, System.currentTimeMillis() + "_" + name + ".zip");
            }

            long copied = 0L;
            try (InputStream in = new BufferedInputStream(openInstallSourceInputStream(uri));
                 FileOutputStream fos = new FileOutputStream(out, false)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) >= 0) {
                    if (read == 0) continue;
                    fos.write(buf, 0, read);
                    copied += read;
                }
                fos.flush();
            }

            if (copied <= 0L || !out.isFile() || out.length() <= 0L) {
                throw new IOException("copied 0 bytes from URI");
            }
            PackageInstallDebug.log(PackageInstallDebug.Area.FILE_OPEN,
                    "copied incoming package URI to imports: " + PackageInstallDebug.describePath(out.getAbsolutePath()));
            return out;
        } catch (Throwable t) {
            rememberReadFailure("copy incoming URI to imports", t);
            if (out != null) {
                try { out.delete(); } catch (Throwable ignored) {}
            }
            return null;
        }
    }

    private InputStream openInstallSourceInputStream(Uri uri) throws IOException {
        if (uri == null) throw new IOException("source URI is null");
        Throwable first = null;
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in != null) return in;
            first = new IOException("openInputStream returned null");
        } catch (Throwable t) {
            first = t;
        }

        try {
            AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd != null) return afd.createInputStream();
        } catch (Throwable t) {
            if (first == null) first = t;
            PackageInstallDebug.warn(PackageInstallDebug.Area.FILE_OPEN,
                    "openAssetFileDescriptor fallback failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        } catch (Throwable t) {
            if (first == null) first = t;
            PackageInstallDebug.warn(PackageInstallDebug.Area.FILE_OPEN,
                    "openFileDescriptor fallback failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        IOException io = new IOException("unable to open source URI for reading: "
                + firstSummary(first));
        if (first != null) {
            try { io.initCause(first); } catch (Throwable ignored) {}
        }
        throw io;
    }

    private Uri firstUriFromClipData(Intent intent) {
        try {
            ClipData clip = intent == null ? null : intent.getClipData();
            if (clip == null || clip.getItemCount() <= 0) return null;
            for (int i = 0; i < clip.getItemCount(); i++) {
                ClipData.Item item = itemAt(clip, i);
                Uri uri = item == null ? null : item.getUri();
                if (uri != null) return uri;
            }
        } catch (Throwable t) {
            PackageInstallDebug.warn(PackageInstallDebug.Area.FILE_OPEN,
                    "ClipData URI read failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return null;
    }

    private ClipData.Item itemAt(ClipData clip, int index) {
        try {
            return clip == null ? null : clip.getItemAt(index);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String describeIntentGrants(Intent intent) {
        try {
            int flags = intent == null ? 0 : intent.getFlags();
            int clipCount = 0;
            try {
                ClipData clip = intent == null ? null : intent.getClipData();
                clipCount = clip == null ? 0 : clip.getItemCount();
            } catch (Throwable ignored) {}
            return "flags=0x" + Integer.toHexString(flags)
                    + ", readGrant=" + ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                    + ", writeGrant=" + ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)
                    + ", persistableGrant=" + ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0)
                    + ", clipItems=" + clipCount;
        } catch (Throwable ignored) {
            return "flags=<unknown>";
        }
    }

    private void rememberReadFailure(String phase, Throwable t) {
        String detail = phase + ": " + firstSummary(t);
        if (TextUtils.isEmpty(lastReadFailureDetails)) {
            lastReadFailureDetails = detail;
        } else {
            lastReadFailureDetails = lastReadFailureDetails + "\n" + detail;
        }
        PackageInstallDebug.error(PackageInstallDebug.Area.FILE_OPEN, detail, t);
    }

    private String buildFileReadFailureMessage(String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("Failed to read file:\n");
        sb.append(TextUtils.isEmpty(label) ? "package" : label);
        if (!TextUtils.isEmpty(lastReadFailureDetails)) {
            sb.append("\n\nRead error: " ).append(lastReadFailureDetails);
        }
        sb.append("\n\nIf this came from another app, that app may not have granted PermsTest read access to its content URI. Try sharing/opening it from a file manager or Downloads, or save it locally first.");
        return sb.toString();
    }

    private String buildFileReadDebugLog(Uri uri, String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("PermsTest file-open read failure\n");
        sb.append("label=").append(label == null ? "" : label).append('\n');
        sb.append("uri=").append(uri == null ? "" : uri.toString()).append('\n');
        sb.append("details=").append(lastReadFailureDetails == null ? "" : lastReadFailureDetails).append('\n');
        sb.append("execMode=").append(execMode == null ? "" : execMode.name()).append('\n');
        return sb.toString();
    }

    private static String firstSummary(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        if (TextUtils.isEmpty(msg)) msg = t.toString();
        return t.getClass().getSimpleName() + (TextUtils.isEmpty(msg) ? "" : ": " + msg);
    }

    private File ensureInstallScript() {
        try {
            File root = getExternalFilesDir(null);
            if (root == null) return null;
            File dir = new File(root, EXT_SCRIPTS_DIR);
            if (!dir.exists()) dir.mkdirs();

            File out = new File(dir, DEFAULT_INSTALL_SCRIPT);

            // Always refresh the bundled helper from assets. User/custom script support
            // should be a separate explicit path, not inferred from version markers.
            try (InputStream in = getAssets().open(ASSET_SCRIPTS_DIR + "/" + DEFAULT_INSTALL_SCRIPT);
                 FileOutputStream fos = new FileOutputStream(out, false)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                fos.flush();
            }
            try { ensureBundledUnzip(dir); } catch (Throwable ignored) {}
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    

    private void ensureBundledUnzip(File scriptsDir) {
        try {
            if (scriptsDir == null) return;
            try { scriptsDir.mkdirs(); } catch (Throwable ignored) {}

            File out = new File(scriptsDir, "unzip");

            // If we already have a copy on disk, just ensure perms and stage it.
            if (out.exists() && out.length() > 0) {
                try { out.setExecutable(true, false); } catch (Throwable ignored) {}
                try { out.setReadable(true, false); } catch (Throwable ignored) {}
                try { out.setWritable(true, false); } catch (Throwable ignored) {}

                // Best-effort: stage to /data/local/tmp so it can execute even on noexec external mounts.
                try {
                    String dst = PUBLIC_BIN_DIR + "/unzip";
                    String cmd2 = "mkdir -p " + shQuote(PUBLIC_BIN_DIR)
                            + " && cp " + shQuote(out.getAbsolutePath()) + " " + shQuote(dst)
                            + " && chmod 755 " + shQuote(dst) + " 2>/dev/null || true";
                    runShizukuShellCapture(cmd2);
                } catch (Throwable ignored2) {}
                return;
            }

            // Optional: bundled unzip binary in assets/bin (ABI-aware). Copy to scriptsDir and stage to /data/local/tmp.
            InputStream in = null;
            try {
                in = openBundledBinInputStream("unzip");
                if (in == null) return; // no bundled unzip provided
                try (FileOutputStream fos = new FileOutputStream(out, false)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                }
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) {}
            }

            try { out.setReadable(true, false); } catch (Throwable ignored) {}
            try { out.setExecutable(true, false); } catch (Throwable ignored) {}
            try { out.setWritable(true, false); } catch (Throwable ignored) {}

            // Also chmod via shell when available; ignore failures.
            try {
                String cmd = "chmod 755 " + shQuote(out.getAbsolutePath()) + " 2>/dev/null || true";
                runShizukuShellCapture(cmd);

                String dst = PUBLIC_BIN_DIR + "/unzip";
                String cmd2 = "mkdir -p " + shQuote(PUBLIC_BIN_DIR)
                        + " && cp " + shQuote(out.getAbsolutePath()) + " " + shQuote(dst)
                        + " && chmod 755 " + shQuote(dst) + " 2>/dev/null || true";
                runShizukuShellCapture(cmd2);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
            // Asset may not exist; that's fine.
        }
    }

private InputStream openBundledBinInputStream(String name) {
        try {
            if (name == null) return null;
            // Match MainActivity's ABI-aware assets layout: assets/bin/<abi>/...
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis != null) {
                for (String abi : abis) {
                    if (abi == null) continue;
                    try {
                        return getAssets().open(ASSET_BIN_DIR + "/" + abi + "/" + name);
                    } catch (Throwable ignored) {}
                }
            }
            // Fallback to assets/bin/<name>
            try {
                return getAssets().open(ASSET_BIN_DIR + "/" + name);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }


    private static final class InstallUiInfo {
        final String packageName;
        final String appLabel;
        InstallUiInfo(String packageName, String appLabel) {
            this.packageName = packageName;
            this.appLabel = appLabel;
        }
    }

    private InstallUiInfo refreshInstalledUiInfo(@Nullable InstallUiInfo info) {
        try {
            if (info == null || TextUtils.isEmpty(info.packageName)) return info;
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(info.packageName, 0);
            String label = info.appLabel;
            try {
                CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                if (cs != null && !TextUtils.isEmpty(cs.toString())) label = cs.toString();
            } catch (Throwable ignored) {}
            return new InstallUiInfo(info.packageName, TextUtils.isEmpty(label) ? info.appLabel : label);
        } catch (Throwable ignored) {
            return info;
        }
    }

    private InstallUiInfo probeInstalledPackageFromNameHints(String... hints) {
        try {
            PackageManager pm = getPackageManager();
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            if (hints != null) {
                for (String hint : hints) {
                    addPackageNameHintCandidates(candidates, hint);
                }
            }

            for (String pkg : candidates) {
                if (TextUtils.equals(pkg, getPackageName())) continue;
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    String label = pkg;
                    try {
                        CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                        if (cs != null) label = cs.toString();
                    } catch (Throwable ignored) {}
                    return new InstallUiInfo(pkg, label);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void addPackageNameHintCandidates(LinkedHashSet<String> candidates, String hint) {
        try {
            if (candidates == null || TextUtils.isEmpty(hint)) return;

            String decoded = Uri.decode(hint);
            String text = TextUtils.isEmpty(decoded) ? hint : decoded;
            String slashText = text.replace('\\', '/');
            String name = slashText;
            int slash = slashText.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < slashText.length()) name = slashText.substring(slash + 1);
            name = name.replaceAll("(?i)\\.(apk|apkm|apks|xapk|zip)$", "");

            ArrayList<String> seeds = new ArrayList<>();
            seeds.add(text);
            if (!TextUtils.equals(text, name)) seeds.add(name);

            Pattern pkgPattern = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*){1,}");
            for (String seed : seeds) {
                if (TextUtils.isEmpty(seed)) continue;

                Matcher m = pkgPattern.matcher(seed);
                while (m.find()) {
                    addNormalizedPackageCandidate(candidates, m.group());
                }

                int cut = firstPackageHintMetadataDelimiter(seed);
                if (cut > 0) {
                    Matcher head = pkgPattern.matcher(seed.substring(0, cut));
                    while (head.find()) {
                        addNormalizedPackageCandidate(candidates, head.group());
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int firstPackageHintMetadataDelimiter(String value) {
        if (TextUtils.isEmpty(value)) return -1;
        int best = -1;
        char[] delimiters = new char[]{'_', '-', ' ', '('};
        for (char delimiter : delimiters) {
            int idx = value.indexOf(delimiter);
            if (idx > 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }

    private static void addNormalizedPackageCandidate(LinkedHashSet<String> candidates, String value) {
        if (candidates == null || TextUtils.isEmpty(value)) return;
        String candidate = value.trim();
        if (candidate.endsWith(".apk") || candidate.endsWith(".apkm") || candidate.endsWith(".apks") || candidate.endsWith(".xapk")) {
            int dot = candidate.lastIndexOf('.');
            if (dot > 0) candidate = candidate.substring(0, dot);
        }
        if (isReasonablePackageName(candidate)) candidates.add(candidate);

        String trimmed = stripLikelyPackageFilenameSuffix(candidate);
        if (!TextUtils.equals(candidate, trimmed) && isReasonablePackageName(trimmed)) {
            candidates.add(trimmed);
        }
    }

    private static String stripLikelyPackageFilenameSuffix(String candidate) {
        if (TextUtils.isEmpty(candidate)) return candidate;
        int lastDot = candidate.lastIndexOf('.');
        int underscore = candidate.indexOf('_', Math.max(0, lastDot + 1));
        if (underscore <= lastDot || underscore + 1 >= candidate.length()) return candidate;

        String suffix = candidate.substring(underscore + 1).toLowerCase(Locale.US);
        if (suffix.matches("(v?\\d.*|minapi.*|maxapi.*|arm.*|x86.*|nodpi.*|hdpi.*|xhdpi.*|xxhdpi.*|xxxhdpi.*)")) {
            return candidate.substring(0, underscore);
        }
        return candidate;
    }

    private static boolean isReasonablePackageName(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String v = value.trim();
        if (v.endsWith(".apk") || v.endsWith(".apkm") || v.endsWith(".apks") || v.endsWith(".xapk")) return false;
        if (v.startsWith("http.") || v.startsWith("https.")) return false;
        int dots = 0;
        for (int i = 0; i < v.length(); i++) if (v.charAt(i) == '.') dots++;
        return dots >= 1 && v.length() >= 5 && v.length() <= 160;
    }

    private InstallUiInfo probeInstallUiInfo(String path, String originalLabel) {
        try {
            if (path == null) return null;
            String lower = path.toLowerCase(Locale.US);

            if (lower.endsWith(".apk")) {
                return probeApkUiInfo(path, originalLabel);
            }

            if (lower.endsWith(".xapk") || lower.endsWith(".apkm") || lower.endsWith(".apks") || lower.endsWith(".zip")) {
                // Try multiple candidates from inside the archive; many bundles put base.apk in subfolders
                // or use non-standard names.
                InstallUiInfo info = probeArchiveUiInfo(path, originalLabel);
                if (info != null) return info;

                // Secondary probe: extract the single best package candidate.
                File extracted = null;
                try {
                    extracted = extractBestApkFromZip(path);
                    if (extracted != null && extracted.exists() && extracted.length() > 0) {
                        return probeApkUiInfo(extracted.getAbsolutePath(), originalLabel);
                    }
                } finally {
                    if (extracted != null) {
                        try { extracted.delete(); } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private InstallUiInfo probeArchiveUiInfo(String zipPath, String originalLabel) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipPath);

            ArrayList<ZipEntry> apks = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String n = ze.getName();
                if (n == null) continue;
                if (!n.toLowerCase(Locale.US).endsWith(".apk")) continue;
                apks.add(ze);
            }
            if (apks.isEmpty()) return null;

            apks.sort((a, b) -> {
                String an = a.getName() == null ? "" : a.getName();
                String bn = b.getName() == null ? "" : b.getName();
                int sa = scoreApkEntryName(an);
                int sb = scoreApkEntryName(bn);
                if (sa != sb) return (sb - sa);
                long asz = a.getSize();
                long bsz = b.getSize();
                if (asz != bsz) return (bsz > asz) ? 1 : -1;
                return Integer.compare(an.length(), bn.length());
            });

            for (ZipEntry ze : apks) {
                File out = null;
                try {
                    out = new File(getCacheDir(), "probe_" + System.nanoTime() + ".apk");
                    try (InputStream in = zf.getInputStream(ze);
                         FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[64 * 1024];
                        int r;
                        while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                    }
                    InstallUiInfo info = probeApkUiInfo(out.getAbsolutePath(), originalLabel);
                    if (info != null && !TextUtils.isEmpty(info.packageName)) return info;
                } catch (Throwable ignored) {
                } finally {
                    try { if (out != null) out.delete(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (zf != null) zf.close(); } catch (Throwable ignored) {}
        }
        return null;
    }

    private int scoreApkEntryName(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase(Locale.US);
        int s = 0;

        if (n.equals("base.apk") || n.endsWith("/base.apk")) s += 10000;
        if (n.contains("base") || n.contains("master") || n.contains("main")) s += 1000;

        boolean configish = (n.startsWith("config.") || n.contains("/config.") || n.contains("split_config") || n.contains("dpi") || n.contains("density") || n.contains("lang") || n.contains("locale"));
        if (configish) s -= 5000;
        else s += 500;

        long sizeHint = 0L;
        try { sizeHint = new File(name).length(); } catch (Throwable ignored) {}
        if (sizeHint > 0L) s += Math.min(500, (int) (sizeHint / (1024L * 1024L)));
        s += Math.max(0, 30 - Math.min(30, n.length() / 8));
        return s;
    }

    private InstallUiInfo probeApkUiInfo(String apkPath, String originalLabel) {
        String parsedPkg = null;
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
            if (pi != null && pi.packageName != null) {
                String pkg = pi.packageName;
                String label = originalLabel;

                try {
                    pi.applicationInfo.sourceDir = apkPath;
                    pi.applicationInfo.publicSourceDir = apkPath;
                    CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                    if (cs != null) label = cs.toString();
                } catch (Throwable ignored) {}

                return new InstallUiInfo(pkg, label);
            }
        } catch (Throwable ignored) {
        }

        try {
            parsedPkg = readPackageNameFromApkManifest(apkPath);
            if (!TextUtils.isEmpty(parsedPkg)) return new InstallUiInfo(parsedPkg, originalLabel);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String readPackageNameFromApkManifest(String apkPath) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(apkPath);
            ZipEntry manifest = zf.getEntry("AndroidManifest.xml");
            if (manifest == null || manifest.getSize() > 1024L * 1024L) return null;
            try (InputStream in = zf.getInputStream(manifest)) {
                byte[] data = readSmallStream(in, 1024 * 1024);
                return BinaryXmlDebuggablePatcher.getManifestPackageName(data);
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (zf != null) zf.close(); } catch (Throwable ignored) {}
        }
    }

    private static byte[] readSmallStream(InputStream in, int maxBytes) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[32 * 1024];
        int total = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            total += r;
            if (total > maxBytes) throw new java.io.IOException("stream is too large");
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private File extractBestApkFromZip(String zipPath) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipPath);
            ZipEntry best = null;

            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String n = ze.getName();
                if (n == null) continue;
                String nl = n.toLowerCase(Locale.US);
                if (!nl.endsWith(".apk")) continue;

                if (best == null) {
                    best = ze;
                } else {
                    // Prefer base.apk or any non-config split.
                    String bn = best.getName().toLowerCase(Locale.US);
                    boolean bestIsConfig = bn.contains("split_config") || bn.contains("config.");
                    boolean zeIsConfig = nl.contains("split_config") || nl.contains("config.");

                    if ("base.apk".equals(nl)) {
                        best = ze;
                    } else if (bestIsConfig && !zeIsConfig) {
                        best = ze;
                    } else if (!bestIsConfig && !zeIsConfig) {
                        // Prefer larger APK.
                        if (ze.getSize() > best.getSize()) best = ze;
                    } else if (bestIsConfig && zeIsConfig) {
                        if (ze.getSize() > best.getSize()) best = ze;
                    }
                }
            }

            if (best == null) return null;

            File out = new File(getCacheDir(), "probe_" + System.currentTimeMillis() + ".apk");
            try (InputStream in = zf.getInputStream(best);
                 FileOutputStream fos = new FileOutputStream(out, false)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                fos.flush();
                return out;
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (zf != null) zf.close(); } catch (Throwable ignored) {}
        }
    }

    private static String buildInstallFailureMessage(String installedFromPath, String originalLabel, int exit, String out, String err) {
        return PackageInstallResults.buildFailureMessage(installedFromPath, originalLabel, exit, out, err, true);
    }

    private static String buildInstallDebugLog(String installedFromPath, String originalLabel, int exit, String out, String err) {
        return PackageInstallResults.buildDebugLog(installedFromPath, originalLabel, exit, out, err);
    }

// --- Small helpers (local to this Activity; avoid depending on MainActivity private helpers) ---

private static String shQuote(String s) {
    if (s == null) return "''";
    // POSIX-safe single-quote escaping: ' -> '"'"'
    return "'" + s.replace("'", "'\''") + "'";
}

private static String extractDigits(String text) {
    if (text == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
    return m.find() ? m.group(1) : null;
}

private static String extractInstallSessionId(String text) {
    if (text == null) return null;
    // Typical output contains: "Success: created install session [123456789]"
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[(\\d+)\\]").matcher(text);
    if (m.find()) return m.group(1);
    // Fallback: first long-ish digit token
    java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("\\b(\\d{6,})\\b").matcher(text);
    return m2.find() ? m2.group(1) : null;
}
}
