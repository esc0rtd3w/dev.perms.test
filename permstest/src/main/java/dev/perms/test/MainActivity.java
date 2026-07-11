package dev.perms.test;

import dev.perms.test.memory.overlay.MemoryOverlayActions;
import dev.perms.test.vr.MemoryOverlayVrRestoreActions;
import dev.perms.test.memory.MemoryToolHelper;
import dev.perms.test.memory.MemoryAppActions;
import dev.perms.test.memory.MemoryToolCommandRunner;
import dev.perms.test.memory.MemoryActivityControllers;
import dev.perms.test.memory.MemoryActivityDependencies;
import dev.perms.test.logging.LoggingActivityControllers;
import dev.perms.test.logging.LoggingActivityDependencies;
import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.debugging.DebuggingActivityControllers;
import dev.perms.test.debugging.DebuggingActivityHost;
import dev.perms.test.debugging.DebuggingUi;
import dev.perms.test.debugging.DebuggingRebuiltApkExporter;
import dev.perms.test.ui.PackageDropdownEntry;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.CollapsibleGroupboxController;
import dev.perms.test.ui.OutputPaneHostController;
import dev.perms.test.ui.MainActivityChromeController;
import dev.perms.test.ui.about.AboutTabController;
import dev.perms.test.ui.StartupInputGuard;
import dev.perms.test.update.PermsTestUpdateController;
import dev.perms.test.update.PermsTestSilentUpdateInstaller;
import dev.perms.test.ui.MainTabNavigator;
import dev.perms.test.ui.MainActivitySavedState;
import dev.perms.test.ui.NoFilterArrayAdapter;
import dev.perms.test.ui.ThemeColorController;
import dev.perms.test.shizuku.ShizukuMainController;
import dev.perms.test.shizuku.BackendStatusController;
import dev.perms.test.shizuku.ShizukuStatusRefreshController;
import dev.perms.test.ladb.LadbClient;
import dev.perms.test.ladb.LadbController;
import dev.perms.test.shizuku.internal.InternalShizukuController;
import dev.perms.test.network.NetworkActivityControllers;
import dev.perms.test.network.NetworkActivityDependencies;
import dev.perms.test.tools.ToolsActivityControllers;
import dev.perms.test.plugins.PluginsActivityController;
import dev.perms.test.ui.PermsTestUiCompat;
import dev.perms.test.plugins.PluginToolPanelActivity;
import dev.perms.test.plugins.PluginToolSurfaceDialog;
import dev.perms.test.ui.panel.GenericPanelLauncher;
import dev.perms.test.ui.panel.MainGroupboxPopoutController;
import dev.perms.test.ui.panel.LoggingGroupboxPopoutController;
import dev.perms.test.ui.panel.PluginsGroupboxPopoutController;
import dev.perms.test.ui.panel.MemoryGroupboxPopoutController;
import dev.perms.test.ui.panel.NetworkGroupboxPopoutController;
import dev.perms.test.ui.panel.DebuggingGroupboxPopoutController;
import dev.perms.test.ui.panel.PackagesGroupboxPopoutController;
import dev.perms.test.ui.panel.ShellGroupboxPopoutController;
import dev.perms.test.ui.panel.ToolsGroupboxPopoutController;
import dev.perms.test.ui.dialog.GenericViewerDialog;
import dev.perms.test.home.HomeAppTrayController;
import dev.perms.test.kiosk.KioskActivityController;
import dev.perms.test.files.FilesBrowserController;
import dev.perms.test.files.FilesTabController;
import dev.perms.test.files.AppExternalFileStore;
import dev.perms.test.permissions.PermissionDropdownController;
import dev.perms.test.packages.PackageInstallImportCleaner;
import dev.perms.test.packages.PackageInstallSourcePreparer;
import dev.perms.test.packages.PackageInstallActivityController;
import dev.perms.test.packages.PackageInstallExecutionActivityController;
import dev.perms.test.packages.PackagesUiActivityController;
import dev.perms.test.packages.PackageInstallRuntimeActivityController;
import dev.perms.test.packages.InstalledPackageExtractor;
import dev.perms.test.packages.InstalledPackageActivityController;
import dev.perms.test.packages.DebuggablePackageActivityController;
import dev.perms.test.packages.editor.ApkEditorActivityController;
import dev.perms.test.debug.PackageInstallDebug;
import dev.perms.test.debug.DebugLog;
import dev.perms.test.scripts.ScriptsEditorUi;
import dev.perms.test.scripts.ScriptsTabController;
import dev.perms.test.shell.ShellActivityControllers;
import dev.perms.test.shell.ShellBinaryAssets;
import dev.perms.test.shell.ShellBinaryController;
import dev.perms.test.shell.ShellRuntimeController;
import dev.perms.test.settings.SettingsPreferenceDefaults;
import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.settings.SettingsTabController;
import dev.perms.test.startup.StartupCacheCleaner;
import dev.perms.test.startup.StartupLoadingHandoffOverlay;
import dev.perms.test.startup.MainActivityExternalIntentController;
import dev.perms.test.storage.StorageAccessController;
import dev.perms.test.tutorial.TutorialController;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Intent;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ScrollView;
import android.net.Uri;
import android.os.Bundle;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Looper;
import android.os.Handler;
import android.os.Build;
import android.content.SharedPreferences;
import android.content.ComponentName;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dev.perms.test.databinding.ActivityMainBinding;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import com.google.android.material.textfield.TextInputEditText;
import android.os.SystemClock;
import android.view.ViewConfiguration;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.LinkedHashSet;
import java.util.HashMap;

@SuppressLint({"SetTextI18n","ClickableViewAccessibility"})
public class MainActivity extends AppCompatActivity implements DebuggingActivityHost {
    private static final String TAG = "PermsTest";
    public static final String EXTRA_OPEN_SMALI_EDITOR_URI = "dev.perms.test.extra.OPEN_SMALI_EDITOR_URI";
    public static final String EXTRA_OPEN_SMALI_EDITOR_LABEL = "dev.perms.test.extra.OPEN_SMALI_EDITOR_LABEL";


    
    // SharedPreferences file name used across the app.
    // Keep stable for backward compatibility.
    private static final String PREFS = SettingsPreferenceKeys.PREFS;

private static final int REQ_CODE = 1001;
    ActivityMainBinding b;
    private StartupLoadingHandoffOverlay startupHandoffOverlay;
    private OutputPaneHostController outputPaneHostController;
    private MainActivityChromeController chromeController;
    private TutorialController tutorialController;
    private AboutTabController aboutTabController;
    private boolean startupUiReady;
    private int pendingStartupTutorialTabIndex = -1;

    private final PackageInstallDebug.AppOutput packageInstallDebugOutput = new PackageInstallDebug.AppOutput() {
        @Override
        public boolean isEnabled() {
            return isAppDebugOutputEnabled();
        }

        @Override
        public void appendLine(String line) {
            appendOutput(line + "\n");
        }
    };

    private android.content.SharedPreferences prefs;
    private ExecMode execMode = ExecMode.SHIZUKU;
    private LadbController ladbController;
    private KioskActivityController kioskActivityController;
    private MainActivityExternalIntentController externalIntentController;

    // "Soft" disconnect: we don't stop the Shizuku server, but we treat it as disabled inside the app.
    private volatile boolean shizukuSoftDisconnected = false;

    // Internal Shizuku is selected only through Exec Mode. Once selected, the rest of
    // the app continues to use the normal Shizuku API path.
    private volatile boolean internalShizukuEnabled = false;


// Scanned binaries (Shell tab) - enables/disables raw command buttons per device.
// App-bundled binaries are managed by the Shell tab helper so shell-readable
// staging paths stay consistent across Shell, package, and debug tooling.
private static final String PUBLIC_TMP_ROOT = ShellBinaryAssets.PUBLIC_TMP_ROOT;
private static final String PUBLIC_BIN_DIR = ShellBinaryAssets.PUBLIC_BIN_DIR;
private static final String PUBLIC_STAGE_DIR = ShellBinaryAssets.PUBLIC_STAGE_DIR;
private static final String PUBLIC_FILES_DIR = ShellBinaryAssets.PUBLIC_FILES_DIR;

private ShellActivityControllers shellActivityControllers;

private ShellActivityControllers getShellActivityControllers() {
    if (shellActivityControllers == null) {
        shellActivityControllers = new ShellActivityControllers(
                this,
                io,
                shellIo,
                PREFS,
                KEY_CUSTOM_COMMANDS,
                new ShellActivityControllers.Host() {
                    @Override
                    public ActivityMainBinding binding() {
                        return b;
                    }

                    @Override
                    public SharedPreferences prefs() {
                        if (prefs == null) {
                            try { prefs = getSharedPreferences(PREFS, MODE_PRIVATE); } catch (Throwable ignored) {}
                        }
                        return prefs;
                    }

                    @Override
                    public boolean isBackendReadyAndGranted() {
                        return MainActivity.this.safeIsReadyAndGranted();
                    }

                    @Override
                    public void refreshStatus() {
                        MainActivity.this.refreshStatus();
                    }

                    @Override
                    public void appendOutput(String text) {
                        MainActivity.this.appendOutput(text);
                    }

                    @Override
                    public void executeIo(Runnable task) {
                        MainActivity.this.ioExecute(task);
                    }

                    @Override
                    public void runOnUiThread(Runnable task) {
                        MainActivity.this.runOnUiThread(task);
                    }

                    @Override
                    public ExecMode getExecMode() {
                        ExecMode mode = execMode;
                        if (prefs != null) {
                            try { mode = ExecMode.get(prefs); } catch (Throwable ignored) {}
                        }
                        return mode;
                    }

                    @Override
                    public LadbController getLadbController() {
                        return MainActivity.this.getLadbController();
                    }

                    @Override
                    public LadbClient getLadbClient() {
                        return MainActivity.this.getLadbController().getClientForShell();
                    }

                    @Override
                    public void lifetimeLogActionForCommand(String command) {
                        MainActivity.this.lifetimeLogActionForCommand(command);
                    }

                    @Override
                    public void lifetimeLog(String tag, String message) {
                        MainActivity.this.lifetimeLog(tag, message);
                    }

                    @Override
                    public void setInteractiveShellRunningUi(boolean running) {
                        MainActivity.this.setInteractiveShellRunningUi(running);
                    }

                    @Override
                    public void setOutputTag(String tag) {
                        lastOutputTag = tag;
                    }

                    @Override
                    public void runShellCommand(String command) {
                        MainActivity.this.runShellCommand(command);
                    }

                    @Override
                    public void stopInteractiveShellCommand() {
                        MainActivity.this.stopInteractiveShellCommand();
                    }

                    @Override
                    public void copyOutputToClipboard() {
                        MainActivity.this.copyOutputToClipboard();
                    }

                    @Override
                    public void clearOutput() {
                        MainActivity.this.clearOutput();
                    }

                    @Override
                    public void resetOutputPanelHeight() {
                        MainActivity.this.getOutputPaneHostController().resetOutputPanelHeight();
                    }

                    @Override
                    public String getTargetPackage() {
                        return b == null || b.tabPackages == null ? "" : MainActivity.this.safeText(b.tabPackages.edtTargetPkg);
                    }

                    @Override
                    public String getSelfPackageName() {
                        return MainActivity.this.getPackageName();
                    }

                    @Override
                    public boolean isSafeToken(String token) {
                        return MainActivity.this.isSafeToken(token);
                    }

                    @Override
                    public void toast(String message) {
                        MainActivity.this.toast(message);
                    }

                    @Override
                    public int dp(int value) {
                        return MainActivity.this.dp(value);
                    }
                });
    }
    return shellActivityControllers;
}

private ShellBinaryAssets getShellBinaryAssets() {
    return getShellActivityControllers().getBinaryAssets();
}

private boolean hasBundledAsset(String name) {
    return getShellActivityControllers().hasBundledAsset(name);
}

private java.io.InputStream openBundledAsset(String name) throws java.io.IOException {
    return getShellActivityControllers().openBundledAsset(name);
}

private void ensureBundledBinaryPublic(String name) {
    getShellActivityControllers().ensureBundledBinaryPublic(name);
}

private File getAppBinDir() {
    return getShellActivityControllers().getAppBinDir();
}

private boolean ensureBundledBinary(String name) {
    return getShellActivityControllers().ensureBundledBinary(name);
}

private boolean isBinaryAvailableSystemOnly(String name) {
    return getShellActivityControllers().isBinaryAvailableSystemOnly(name);
}

private boolean isBinaryInstalledPublic(String name) {
    return getShellActivityControllers().isBinaryInstalledPublic(name);
}

private String rewriteCommandWithLocalBins(String cmd) {
    return getShellActivityControllers().rewriteCommandWithLocalBins(cmd);
}

private File getBundledStageDir() {
    return getShellActivityControllers().getBundledStageDir();
}

private String resolveBundledAbiDir() {
    return getShellActivityControllers().resolveBundledAbiDir();
}

private ShellBinaryController getShellBinaryController() {
    return getShellActivityControllers().getBinaryController();
}

    // Colors for package permission rendering (set in onCreate)
    private int colorDangerous;
    private int colorSignature;
    private int colorGranted;
    private int colorRevoked;
    private int colorMuted;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService shellIo = Executors.newSingleThreadExecutor();
    static final ExecutorService DEBUG_APK_IO = Executors.newSingleThreadExecutor();

    private void ioExecute(Runnable r) {
        try {
            io.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Activity is being torn down (rotation); ignore.
        }
    }

    private File lastSavedFile = null;


    // Home "App Tray"
    private HomeAppTrayController homeAppTrayController;
    private MainGroupboxPopoutController mainGroupboxPopoutController;
    private ShellGroupboxPopoutController shellGroupboxPopoutController;
    private PackagesGroupboxPopoutController packagesGroupboxPopoutController;
    private MemoryGroupboxPopoutController memoryGroupboxPopoutController;
    private NetworkGroupboxPopoutController networkGroupboxPopoutController;
    private DebuggingGroupboxPopoutController debuggingGroupboxPopoutController;
    private ToolsGroupboxPopoutController toolsGroupboxPopoutController;
    private LoggingGroupboxPopoutController loggingGroupboxPopoutController;
    private PluginsGroupboxPopoutController pluginsGroupboxPopoutController;

    private HomeAppTrayController getHomeAppTrayController() {
        if (homeAppTrayController == null) {
            homeAppTrayController = new HomeAppTrayController(
                    this,
                    b,
                    PREFS,
                    KEY_UI_DETECT_VR_MODE,
                    new HomeAppTrayController.Host() {
                        @Override
                        public void runOnBackground(Runnable task) {
                            MainActivity.this.ioExecute(task);
                        }

                        @Override
                        public int dp(int dip) {
                            return MainActivity.this.dp(dip);
                        }

                        @Override
                        public void runShellCommandCapture(String cmd, HomeAppTrayController.ShellCaptureCallback cb) {
                            MainActivity.this.runShellCommandCapture(cmd, cb == null
                                    ? null
                                    : (code, out, err) -> cb.onComplete(code, out, err));
                        }

                        @Override
                        public void appendOutput(String msg) {
                            MainActivity.this.appendOutput(msg);
                        }

                        @Override
                        public void debugOutput(String area, String message) {
                            MainActivity.this.debugOutput("panel", area, message);
                        }

                        @Override
                        public void invalidateFilesPackageIconCaches() {
                            MainActivity.this.filesInvalidatePackageIconCaches();
                        }

                        @Override
                        public void makeDebugPackage(String packageName, String label) {
                            MainActivity.this.getInstalledPackageActivityController().showMakeDebugPackageDialog(packageName, label);
                        }

                        @Override
                        public void extractPackage(String packageName, String label) {
                            MainActivity.this.getInstalledPackageActivityController().extractInstalledPackageToPublicFolder(packageName, label);
                        }

                        @Override
                        public void launchWithPayloads(String packageName, String label) {
                            MainActivity.this.getMemoryActivityControllers().launchPackageWithPayloads(packageName, label);
                        }

                        @Override
                        public void createPayloadShortcut(String packageName, String label) {
                            MainActivity.this.getMemoryActivityControllers().showPayloadShortcutDialog(packageName, label);
                        }
                    });
        }
        return homeAppTrayController;
    }

    private MainGroupboxPopoutController getMainGroupboxPopoutController() {
        if (mainGroupboxPopoutController == null) {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            mainGroupboxPopoutController = new MainGroupboxPopoutController(
                    this,
                    b,
                    prefs,
                    getHomeAppTrayController(),
                    new MainGroupboxPopoutController.Host() {
                        @Override
                        public int dp(int dip) {
                            return MainActivity.this.dp(dip);
                        }

                        @Override
                        public void debugOutput(String area, String message) {
                            MainActivity.this.debugOutput("panel", area, message);
                        }

                        @Override
                        public void selectExecMode(ExecMode mode) {
                            MainActivity.this.selectExecMode(mode, true);
                        }
                    });
        }
        return mainGroupboxPopoutController;
    }

    private ShellGroupboxPopoutController getShellGroupboxPopoutController() {
        if (shellGroupboxPopoutController == null) {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            shellGroupboxPopoutController = new ShellGroupboxPopoutController(
                    this,
                    b,
                    (area, message) -> MainActivity.this.debugOutput("panel", area, message));
        }
        return shellGroupboxPopoutController;
    }

    private PackagesGroupboxPopoutController getPackagesGroupboxPopoutController() {
        if (packagesGroupboxPopoutController == null) {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            packagesGroupboxPopoutController = new PackagesGroupboxPopoutController(
                    this,
                    b,
                    getPackagesUiActivityController().getPackageAppDropdownController(),
                    getPermissionDropdownController(),
                    new PackagesGroupboxPopoutController.Host() {
                        @Override
                        public void debugOutput(String area, String message) {
                            MainActivity.this.debugOutput("panel", area, message);
                        }
                    });
        }
        return packagesGroupboxPopoutController;
    }

    private MemoryGroupboxPopoutController getMemoryGroupboxPopoutController() {
        if (memoryGroupboxPopoutController == null) {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            memoryGroupboxPopoutController = new MemoryGroupboxPopoutController(
                    this,
                    b,
                    (area, message) -> MainActivity.this.debugOutput("panel", area, message));
        }
        return memoryGroupboxPopoutController;
    }

    private NetworkGroupboxPopoutController getNetworkGroupboxPopoutController() {
        if (networkGroupboxPopoutController == null) {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            networkGroupboxPopoutController = new NetworkGroupboxPopoutController(
                    this,
                    b,
                    (area, message) -> MainActivity.this.debugOutput("panel", area, message));
        }
        return networkGroupboxPopoutController;
    }

    private DebuggingGroupboxPopoutController getDebuggingGroupboxPopoutController() {
        if (debuggingGroupboxPopoutController == null) {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            debuggingGroupboxPopoutController = new DebuggingGroupboxPopoutController(
                    this,
                    b,
                    new DebuggingGroupboxPopoutController.Host() {
                        @Override
                        public void debugOutput(String area, String message) {
                            MainActivity.this.debugOutput("panel", area, message);
                        }

                        @Override
                        public void selectInstalledPackageFromClone(Object entryObject) {
                            MainActivity.this.getDebuggingActivityControllers().selectDebuggingInstalledPackageFromPopout(entryObject);
                        }

                        @Override
                        public void selectDexEntryFromClone(String entry) {
                            MainActivity.this.getDebuggingActivityControllers().applySelectedDexEntry(entry, false, true);
                        }
                    });
        }
        return debuggingGroupboxPopoutController;
    }

    private ToolsGroupboxPopoutController getToolsGroupboxPopoutController() {
        if (toolsGroupboxPopoutController == null) {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            toolsGroupboxPopoutController = new ToolsGroupboxPopoutController(
                    this,
                    b,
                    new ToolsGroupboxPopoutController.Host() {
                        @Override
                        public void debugOutput(String area, String message) {
                            MainActivity.this.debugOutput("panel", area, message);
                        }
                    });
        }
        return toolsGroupboxPopoutController;
    }

    private LoggingGroupboxPopoutController getLoggingGroupboxPopoutController() {
        if (loggingGroupboxPopoutController == null) {
            loggingGroupboxPopoutController = new LoggingGroupboxPopoutController(
                    this,
                    b,
                    (area, message) -> MainActivity.this.debugOutput("panel", area, message));
        }
        return loggingGroupboxPopoutController;
    }

    private PluginsGroupboxPopoutController getPluginsGroupboxPopoutController() {
        if (pluginsGroupboxPopoutController == null) {
            pluginsGroupboxPopoutController = new PluginsGroupboxPopoutController(
                    this,
                    b,
                    (area, message) -> MainActivity.this.debugOutput("panel", area, message));
        }
        return pluginsGroupboxPopoutController;
    }

    private static final String KEY_SELECTED_SCRIPT_NAME = "selected_script_name";
    private static final String KEY_RUN_AS_LAUNCHER = SettingsPreferenceKeys.RUN_AS_LAUNCHER;
    private static final String KEY_AUTO_RESTART_LAUNCHER = SettingsPreferenceKeys.AUTO_RESTART_LAUNCHER;
    private static final String KEY_PENDING_OPEN_HOME_SETTINGS = SettingsPreferenceKeys.PENDING_OPEN_HOME_SETTINGS;
    private static final String KEY_ENABLE_FILE_OPEN_HANDLER = SettingsPreferenceKeys.ENABLE_FILE_OPEN_HANDLER;
    private static final String KEY_HIDE_FILE_OPEN_UI = SettingsPreferenceKeys.HIDE_FILE_OPEN_UI;
    private static final String KEY_SHOW_FILE_OPEN_DONE_OPEN = SettingsPreferenceKeys.SHOW_FILE_OPEN_DONE_OPEN;
    private static final String KEY_CONFIRM_FILE_OPEN_INSTALL = SettingsPreferenceKeys.CONFIRM_FILE_OPEN_INSTALL;
    private static final String KEY_INSTALL_USE_ANDROID_DATA_PATH = SettingsPreferenceKeys.INSTALL_USE_ANDROID_DATA_PATH;
    private static final String KEY_INSTALL_USE_STAGING_FOLDER = SettingsPreferenceKeys.INSTALL_USE_STAGING_FOLDER;
    private static final String KEY_INSTALL_SKIP_STAGING_LARGE_FILES = SettingsPreferenceKeys.INSTALL_SKIP_STAGING_LARGE_FILES;
    private static final long INSTALL_SKIP_STAGING_LARGE_BYTES = 900L * 1024L * 1024L;
    private static final String KEY_INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK = SettingsPreferenceKeys.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK;
    private static final String KEY_INSTALL_IGNORE_DEXOPT_PROFILE = SettingsPreferenceKeys.INSTALL_IGNORE_DEXOPT_PROFILE;
    private static final String KEY_INSTALL_ALLOW_DOWNGRADE = SettingsPreferenceKeys.INSTALL_ALLOW_DOWNGRADE;
    private static final String KEY_INSTALL_USE_INSTALLER_SCRIPT = SettingsPreferenceKeys.INSTALL_USE_INSTALLER_SCRIPT;
    private static final String KEY_SPLIT_APK_SHOW_WARNING_DIALOG = SettingsPreferenceKeys.SPLIT_APK_SHOW_WARNING_DIALOG;
    private static final String KEY_CUSTOM_SPLIT_OPTIONS = SettingsPreferenceKeys.CUSTOM_SPLIT_OPTIONS;
    private static final String KEY_USE_APP_PERMS_IN_DROPDOWN = SettingsPreferenceKeys.USE_APP_PERMS_IN_DROPDOWN;
    private static final String KEY_REMEMBER_OUTPUT_HEIGHT = SettingsPreferenceKeys.REMEMBER_OUTPUT_HEIGHT;
    private static final String KEY_OUTPUT_HEIGHT_PX = SettingsPreferenceKeys.OUTPUT_HEIGHT_PX;
    private static final String KEY_OUTPUT_RESTORE_HEIGHT_PX = SettingsPreferenceKeys.OUTPUT_RESTORE_HEIGHT_PX;
    private static final String KEY_OUTPUT_MINIMIZED = SettingsPreferenceKeys.OUTPUT_MINIMIZED;
    private static final String KEY_KEEP_BOTTOM_LOG_ABOVE_NAV_BAR = SettingsPreferenceKeys.KEEP_BOTTOM_LOG_ABOVE_NAV_BAR;
    private static final String KEY_FAT_DROPDOWN_SCROLLBAR = SettingsPreferenceKeys.FAT_DROPDOWN_SCROLLBAR;
    private static final String KEY_SAMSUNG_DROPDOWN_FIX = SettingsPreferenceKeys.SAMSUNG_DROPDOWN_FIX;
    private static final String KEY_UI_DETECT_VR_MODE = SettingsPreferenceKeys.UI_DETECT_VR_MODE;
    // Keep the stored key name for compatibility with existing installs; UI now calls this Debug Output.
    private static final String KEY_DEBUG_OUTPUT = SettingsPreferenceKeys.DEBUG_OUTPUT;
    private static final String KEY_CLEAR_CACHE_ON_STARTUP = SettingsPreferenceKeys.CLEAR_CACHE_ON_STARTUP;
    private static final String KEY_TRUNCATE_SHELL_OUTPUT = SettingsPreferenceKeys.TRUNCATE_SHELL_OUTPUT;
    private static final String KEY_UI_VR_DEFAULT_RESET_APPLIED = SettingsPreferenceKeys.UI_VR_DEFAULT_RESET_APPLIED;
    private static final String KEY_MEMORY_WITHOUT_PTRACE = MemoryToolHelper.KEY_WITHOUT_PTRACE;
    private static final int MAX_OUTPUT_CHARS = 96000;

// Internal Shizuku runtime state keys. Exec Mode is the only selector.
private static final String PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING = InternalShizukuController.PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING;
private static final String KEY_MISSING_SHIZUKU_PROMPT_SHOWN = "missing_installed_shizuku_prompt_shown";

    private static final String KEY_COLORIZE_APP_DROPDOWN = SettingsPreferenceKeys.COLORIZE_APP_DROPDOWN;
    private static final String KEY_CUSTOM_COMMANDS = "custom_shell_commands";


    private String restoreFilesLeftCwd = null;
    private String restoreFilesRightCwd = null;
    private Boolean restoreFilesSplit = null;
    private Boolean restoreFilesActiveRight = null;
    private String restoreAppFilterText = null;
    private MemoryActivityControllers memoryActivityControllers = null;
    private LoggingActivityControllers loggingActivityControllers = null;
    private NetworkActivityControllers networkActivityControllers = null;
    private ToolsActivityControllers toolsActivityControllers = null;
    private PluginsActivityController pluginsActivityController = null;
    private int currentTabIndex = 0;
    private MainTabNavigator mainTabNavigator;
    private ShizukuStatusRefreshController shizukuStatusRefreshController;
    private BackendStatusController backendStatusController;
    private ShizukuMainController shizukuMainController;
    private InternalShizukuController internalShizukuController;
    private PackagesUiActivityController packagesUiActivityController;
    private DebuggablePackageActivityController debuggablePackageActivityController;
    private InstalledPackageActivityController installedPackageActivityController;
    private ApkEditorActivityController apkEditorActivityController;
    private PermsTestUpdateController permsTestUpdateController;
    private PermsTestSilentUpdateInstaller permsTestSilentUpdateInstaller;


    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScriptsEditorUi scriptsEditorUi = new ScriptsEditorUi(this, mainHandler, PREFS);
    private volatile boolean activityDestroyed;
    private PackageInstallRuntimeActivityController packageInstallRuntimeActivityController;
    private PackageInstallExecutionActivityController packageInstallExecutionActivityController;
    private AppExternalFileStore appExternalFileStore;
    private boolean colorizeAppDropdown = true;

    // User-saved shell commands (persisted in SharedPreferences)

    // APK installer (Package Tools)
    private PackageInstallActivityController packageInstallActivityController;

    // Debugging tab APK/source selection. Keep this separate from the Packages-tab picker
    // so browsing for smali work does not disturb the install/debug package UI state.

    private ScriptsTabController scriptsTabController;
    private DebuggingActivityControllers debuggingActivityControllers;

    private static final String ASSET_BIN_DIR = "bin";
    private static final String DEFAULT_INSTALL_SCRIPT = "install-apk.sh";
    private static final String EXT_SCRIPTS_DIR = "scripts";
    private static final String EXT_IMPORTS_DIR = "imports";



    // Used for filename when saving output
    private String lastOutputTag = "PermsTest";

    private PermissionDropdownController permissionDropdownController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startupUiReady = false;
        pendingStartupTutorialTabIndex = -1;
        StartupInputGuard.block(this);
        registerEarlyActivityResultLaunchers();
        if (shouldShowStartupHandoff(savedInstanceState)) {
            startupHandoffOverlay = StartupLoadingHandoffOverlay.showAsContent(this);
            View root = getWindow() == null ? null : getWindow().getDecorView();
            Runnable continueStartup = () -> { if (!isFinishing()) continueMainCreate(savedInstanceState); };
            if (root != null) {
                root.postDelayed(continueStartup, 120L);
            } else {
                mainHandler.post(continueStartup);
            }
            return;
        }
        continueMainCreate(savedInstanceState);
    }

    private boolean shouldShowStartupHandoff(Bundle savedInstanceState) {
        try {
            return savedInstanceState == null
                    && getIntent() != null
                    && getIntent().getBooleanExtra(StartupLoadingHandoffOverlay.EXTRA_SHOW_HANDOFF, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void registerEarlyActivityResultLaunchers() {
        try { ensureApkPickerLauncher(); } catch (Throwable ignored) {}
        try { getDebuggingActivityControllers().registerActivityResults(); } catch (Throwable ignored) {}
        try { getScriptsTabController().registerActivityResults(); } catch (Throwable ignored) {}
        try { getNetworkActivityControllers().registerActivityResults(); } catch (Throwable ignored) {}
        try { getToolsActivityControllers().registerActivityResults(); } catch (Throwable ignored) {}
        try { getPluginsActivityController().registerActivityResults(); } catch (Throwable ignored) {}
        try { getShellActivityControllers().registerCustomCommandActivityResults(); } catch (Throwable ignored) {}
    }

    private void continueMainCreate(Bundle savedInstanceState) {
        try {
            final StartupLoadingHandoffOverlay startupContentOverlay = startupHandoffOverlay;
            startupHandoffOverlay = null;
            if (startupContentOverlay != null) {
                startupContentOverlay.dismissNow();
            }
            b = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(b.getRoot());
            ThemeColorController.applyToActivity(this, b.getRoot());
            startupHandoffOverlay = StartupLoadingHandoffOverlay.attachIfRequested(this, getIntent());
            getOutputPaneHostController().bind(b);
            debugStartupLoaded("activity", "content view bound");

	        getHomeAppTrayController().bind();
		getHomeAppTrayController().registerPackageReceiver();
            debugStartupLoaded("home-tray", "controls bound, receiver registered");
	        applySystemBarPadding();
            debugStartupLoaded("system-bars", "padding applied");

            // Enable marquee (scrolling) on dropdown text when values are long.
            b.tabPackages.ddApp.setSelected(true);
            b.tabPackages.ddPerm.setSelected(true);
            debugStartupLoaded("packages-marquee", "dropdown marquee enabled");


        // Restore small UI state (tab selection, Files tab, Memory tab, and last saved file).
        if (savedInstanceState != null) {
            MainActivitySavedState.Restored restored = MainActivitySavedState.restore(
                    savedInstanceState,
                    b,
                    getMemoryActivityControllers());
            currentTabIndex = restored.currentTabIndex;
            if (restored.lastSavedFile != null) lastSavedFile = restored.lastSavedFile;
            getOutputPaneHostController().restoreInstanceOutputHeight(restored.outputHeightPx);
            restoreFilesLeftCwd = restored.filesLeftCwd;
            restoreFilesRightCwd = restored.filesRightCwd;
            restoreFilesSplit = restored.filesSplit;
            restoreFilesActiveRight = restored.filesActiveRight;
            restoreAppFilterText = restored.appFilterText;
        }

        try { if (savedInstanceState == null) b.txtOutput.setText(""); } catch (Throwable ignored) {}

        // Init colors for permission rendering
        colorDangerous = ContextCompat.getColor(this, android.R.color.holo_orange_dark);
        colorSignature = ContextCompat.getColor(this, android.R.color.holo_blue_dark);
        colorGranted = ContextCompat.getColor(this, android.R.color.holo_green_dark);
        colorRevoked = ContextCompat.getColor(this, android.R.color.holo_red_dark);
        colorMuted = ContextCompat.getColor(this, android.R.color.darker_gray);
        debugStartupLoaded("permission-colors", "permission palette initialized");

		setupTabs();
            debugStartupLoaded("tabs", "navigator bound");
            setupFilesTab();
            debugStartupLoaded("files", "tab controls bound");
            setupNetworkTab();
            getNetworkGroupboxPopoutController().bind();
            debugStartupLoaded("network", "tab controls bound");

        getShizukuStatusRefreshController().register();
        debugStartupLoaded("shizuku-status", "refresh callbacks registered");

        b.tabMain.btnRefresh.setOnClickListener(v -> refreshStatus());

        setupShellCommandBar();
        debugStartupLoaded("shell", "command/output controls bound");
        setupFastScrollOverlay();
        debugStartupLoaded("fast-scroll", "global output fast-scroll bound");
        cleanupManagedImportsDirOnStart();
        ensureDefaultPrefs();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getMainGroupboxPopoutController().bind();
        getShellGroupboxPopoutController().bind();
        getPackagesGroupboxPopoutController().bind();
        getOutputPaneHostController().restorePanelStateFromPrefs(prefs);
        cleanupAppCacheOnStartupIfEnabled();
        debugStartupLoaded("prefs-cache", "defaults restored, startup cleanup queued");
        execMode = ExecMode.get(prefs);
        internalShizukuEnabled = execMode.isInternalShizuku();
        shizukuSoftDisconnected = !execMode.isShizukuLike();
        setupExecModeUi();
        setupLadbUi();
        setupInternalShizukuUi();
        setupShizukuMainUi();
        applyExecModeUi();
        maybeAutoConnectLadb();
        debugStartupLoaded("exec-mode", "mode=" + (execMode == null ? "null" : execMode.prefValue()) + ", internal=" + internalShizukuEnabled);

        setupSettingsTab();
        debugStartupLoaded("settings", "tab controls bound");
        setupMemoryTab();
        getMemoryGroupboxPopoutController().bind();
        debugStartupLoaded("memory", "tab controllers bound");
        getOutputPaneHostController().setupResizer();
        applyDeviceUiProfile();
        debugStartupLoaded("device-ui", "profile applied");
        setupCustomCommands();
        debugStartupLoaded("custom-commands", "controls bound");

        // Scan /system*/bin and similar locations to enable/disable raw binary buttons per device.
        refreshBinaryAvailabilityUi();
        debugStartupLoaded("shell-bins", "availability scan queued");


        setupShellQuickActions();
        debugStartupLoaded("shell-actions", "quick actions bound");

        // Permission / package tools
        b.tabPackages.edtTargetPkg.setText(getPackageName());
        getPackagesUiActivityController().bindPackageToolButtons();
        setupTargetPkgWatchers();
        setupLauncherModeToggle();
        maybeOpenHomeSettingsAfterModeSwitch();
        debugStartupLoaded("packages", "tool buttons/watchers bound");


        // Logging
        getLoggingActivityControllers().bind();
        getLoggingGroupboxPopoutController().bind();
        debugStartupLoaded("logging", "tab controls and groupbox popouts bound");

        setupColorizeAppDropdownToggle();
        setupAppDropdown();
        debugStartupLoaded("app-dropdown", "package list refresh queued");
        getPermissionDropdownController().bind();
        debugStartupLoaded("permission-dropdown", "common permissions loaded, full list lazy");
        setupApkInstaller();
        debugStartupLoaded("apk-installer", "controls bound");
        setupScriptsTab();
        debugStartupLoaded("scripts", "tab controls bound");
        setupDebuggingTab();
        debugStartupLoaded("debugging", "tab controllers bound");
        setupToolsTab();
        debugStartupLoaded("tools", "tab controllers bound");
        setupAboutTab();
        debugStartupLoaded("about", "tab controls bound");
        setupDebuggablePackageUi();
        debugStartupLoaded("debuggable-package", "controls bound");
        setupApkEditorUi();
        debugStartupLoaded("apk-editor", "controls bound");
        applyCollapsibleGroupboxes();
        debugStartupLoaded("groupboxes", "collapse controls bound");

        boolean handledStartupIntent = getExternalIntentController().handleStartupIntent(getIntent());
        debugStartupLoaded("startup-intent", "handled=" + handledStartupIntent);

        if (savedInstanceState == null) {
            appendOutput("Ready. Tip: use the quick buttons or type a command and tap Run.\n");
        }
        debugStartupLoaded("ready", "main activity startup complete");

        refreshStatus();
        debugStartupLoaded("status", "initial refresh requested");
        if (savedInstanceState == null) {
            mainHandler.postDelayed(this::maybeShowMissingInstalledShizukuPrompt, 450L);
            mainHandler.postDelayed(() -> StorageAccessController.maybePromptOnFirstStart(
                    MainActivity.this,
                    prefs == null ? getSharedPreferences(PREFS, MODE_PRIVATE) : prefs,
                    MainActivity.this::appendOutput), 1200L);
        }
        if (startupHandoffOverlay != null) {
            startupHandoffOverlay.dismissWhenContentReady(b.getRoot());
        }
        markStartupUiReady();
        schedulePluginsAfterSuccessfulStartup(savedInstanceState);
    
        } catch (Throwable fatal) {
            StartupInputGuard.unblock(this);
            try { if (startupHandoffOverlay != null) startupHandoffOverlay.dismissNow(); } catch (Throwable ignored) {}
            Log.e(TAG, "Fatal error in onCreate", fatal);
            try {
                TextView tv = new TextView(this);
                tv.setText("Fatal startup error\n\n" + stackTraceToString(fatal));
                tv.setTextIsSelectable(true);
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                tv.setPadding(pad, pad, pad, pad);
                ScrollView sv = new ScrollView(this);
                sv.addView(tv);
                setContentView(sv);
            } catch (Throwable ignored) {
                // ignore
            }
        }
}

    private LadbController getLadbController() {
        if (ladbController == null) {
            ladbController = new LadbController(
                    this,
                    b,
                    getSharedPreferences(PREFS, MODE_PRIVATE),
                    io,
                    new LadbController.Host() {
                        @Override
                        public ExecMode getExecMode() {
                            return execMode;
                        }

                        @Override
                        public void refreshStatus() {
                            MainActivity.this.refreshStatus();
                        }

                        @Override
                        public void toast(String message) {
                            MainActivity.this.toast(message);
                        }
                    });
        }
        return ladbController;
    }

    private ShizukuStatusRefreshController getShizukuStatusRefreshController() {
        if (shizukuStatusRefreshController == null) {
            shizukuStatusRefreshController = new ShizukuStatusRefreshController(
                    this,
                    REQ_CODE,
                    this::refreshStatus);
        }
        return shizukuStatusRefreshController;
    }

    private PermsTestUpdateController getPermsTestUpdateController() {
        if (permsTestUpdateController == null) {
            permsTestUpdateController = new PermsTestUpdateController(
                    this,
                    b,
                    getSharedPreferences(PREFS, MODE_PRIVATE),
                    io,
                    this::appendOutput,
                    getPermsTestSilentUpdateInstaller(),
                    getString(R.string.about_version));
        }
        return permsTestUpdateController;
    }

    private ShizukuMainController getShizukuMainController() {
        if (shizukuMainController == null) {
            shizukuMainController = new ShizukuMainController(
                    this,
                    b,
                    getSharedPreferences(PREFS, MODE_PRIVATE),
                    io,
                    REQ_CODE,
                    new ShizukuMainController.Host() {
                        @Override
                        public ExecMode getExecMode() {
                            ExecMode modeNow = execMode;
                            try {
                                if (prefs != null) modeNow = ExecMode.get(prefs);
                            } catch (Throwable ignored) {
                            }
                            return modeNow;
                        }

                        @Override
                        public boolean isInternalShizukuEnabled() {
                            return execMode != null && execMode.isInternalShizuku();
                        }

                        @Override
                        public boolean isShizukuSoftDisconnected() {
                            return shizukuSoftDisconnected;
                        }

                        @Override
                        public void setShizukuSoftDisconnected(boolean disconnected) {
                            shizukuSoftDisconnected = disconnected;
                        }

                        @Override
                        public boolean isBackendReadyAndGranted() {
                            return MainActivity.this.safeIsReadyAndGranted();
                        }

                        @Override
                        public void refreshStatus() {
                            MainActivity.this.refreshStatus();
                        }

                        @Override
                        public void applyExecModeUi() {
                            MainActivity.this.applyExecModeUi();
                        }

                        @Override
                        public void appendOutput(String text) {
                            MainActivity.this.appendOutput(text);
                        }

                        @Override
                        public void resetInternalServerStateAndStartDiscovery() {
                            MainActivity.this.getInternalShizukuController().resetServerStateAndStartDiscovery();
                        }

                        @Override
                        public void requestInternalShizukuStartServer() {
                            MainActivity.this.getInternalShizukuController().requestStartServer();
                        }
                    });
        }
        return shizukuMainController;
    }

    private InternalShizukuController getInternalShizukuController() {
        if (internalShizukuController == null) {
            internalShizukuController = new InternalShizukuController(
                    this,
                    b,
                    getSharedPreferences(PREFS, MODE_PRIVATE),
                    new InternalShizukuController.Host() {
                        @Override
                        public ExecMode getExecMode() {
                            return execMode;
                        }

                        @Override
                        public void appendOutput(String text) {
                            MainActivity.this.appendOutput(text);
                        }

                        @Override
                        public void openWirelessDebuggingSettings() {
                            MainActivity.this.openWirelessDebuggingSettings();
                        }

                        @Override
                        public void applyExecModeUi() {
                            MainActivity.this.applyExecModeUi();
                        }

                        @Override
                        public void refreshStatus() {
                            MainActivity.this.refreshStatus();
                        }
                    });
        }
        return internalShizukuController;
    }

    private void setupShellCommandBar() {
        getShellActivityControllers().bindCommandBar();
    }

    private void setupShellQuickActions() {
        getShellActivityControllers().bindQuickActions();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        MainActivitySavedState.save(
                outState,
                currentTabIndex,
                getOutputPaneHostController().getCurrentOutputHeightPx(),
                lastSavedFile,
                getFilesBrowserControllerForSavedState(),
                b,
                getMemoryActivityControllers());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        try {
            if (getLadbController().handleRequestPermissionsResult(requestCode, grantResults)) {
                // LADB Pair Helper notification permission result was handled by the LADB controller.
            } else if (MemoryOverlayActions.handleNotificationPermissionResult(this, requestCode, grantResults)) {
                // Memory overlay notification permission result was handled by the Memory overlay helper.
            }
        } catch (Throwable ignored) {}
    }

protected void onDestroy() {
        activityDestroyed = true;
        try { if (startupHandoffOverlay != null) startupHandoffOverlay.dismissNow(); } catch (Throwable ignored) {}
        try { if (kioskActivityController != null) kioskActivityController.release(); } catch (Throwable ignored) {}
        // Rotation / configuration changes will destroy and recreate the Activity.
        // Cancel any pending UI callbacks first so they don't fire after we've shut down executors.
        try { mainHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
        try { if (networkActivityControllers != null) networkActivityControllers.shutdown(); } catch (Throwable ignored) {}
        try { if (toolsActivityControllers != null) toolsActivityControllers.stop(); } catch (Throwable ignored) {}
        try { if (pluginsActivityController != null) pluginsActivityController.stop(); } catch (Throwable ignored) {}


        try { if (homeAppTrayController != null) homeAppTrayController.shutdown(); } catch (Throwable ignored) {}
        try { if (shizukuStatusRefreshController != null) shizukuStatusRefreshController.unregisterQuietly(); } catch (Throwable ignored) {}
        try { if (permsTestUpdateController != null) permsTestUpdateController.shutdown(); } catch (Throwable ignored) {}

        try { if (!shellIo.isShutdown()) shellIo.shutdownNow(); } catch (Throwable ignored) {}
        try { if (!io.isShutdown()) io.shutdownNow(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (homeAppTrayController != null) homeAppTrayController.refreshAsync();
        } catch (Throwable ignored) {}
        restoreDebuggablePackageJobStatus();
        try { getDebuggingActivityControllers().restoreSmaliDisassembleAllJobStatus(); } catch (Throwable ignored) {}
        try { getDebuggingActivityControllers().refreshDebuggingDexEntriesIfInputPresent(false); } catch (Throwable ignored) {}
        try { getNetworkActivityControllers().refreshVisibleState(); } catch (Throwable ignored) {}
        try { getKioskActivityController().syncLauncherAndKioskCheckboxesFromPrefs(); } catch (Throwable ignored) {}
        // Keep Quest overlay restore explicit. Opening PermsTest from the app drawer must not
        // automatically relaunch the last VR target or reopen the raw overlay.
    }


    private void maybeRestoreVrMemoryOverlayFromLauncher() {
        MemoryOverlayVrRestoreActions.restoreHiddenOverlayFromMainLaunch(
                this,
                mainHandler,
                this::appendOutput);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getExternalIntentController().handleNewIntent(intent);
    }

    private MainActivityExternalIntentController getExternalIntentController() {
        if (externalIntentController == null) {
            externalIntentController = new MainActivityExternalIntentController(this, new MainActivityExternalIntentController.Host() {
                @Override
                public boolean handleToolsTextEditorIntent(Intent intent) {
                    return MainActivity.this.handleIncomingToolsTextEditorIntent(intent);
                }

                @Override
                public boolean handleSmaliEditorIntent(Intent intent) {
                    return MainActivity.this.handleIncomingSmaliEditorIntent(intent);
                }

                @Override
                public boolean handleFileOpenIntent(Intent intent) {
                    return MainActivity.this.handleFileOpenIntent(intent);
                }
            });
        }
        return externalIntentController;
    }


    private TutorialController getTutorialController() {
        if (tutorialController == null && b != null) {
            tutorialController = new TutorialController(this, b, getSharedPreferences(PREFS, MODE_PRIVATE));
        }
        return tutorialController;
    }

    private MainTabNavigator getMainTabNavigator() {
        if (mainTabNavigator == null && b != null) {
            mainTabNavigator = new MainTabNavigator(this, b, new MainTabNavigator.Host() {
                @Override
                public int dp(int value) {
                    return MainActivity.this.dp(value);
                }

                @Override
                public void onSelectedTabChanged(int index) {
                    currentTabIndex = index;
                }

                @Override
                public void onTabVisible(int index) {
                    if (index == 3) {
                        try { onMemoryTabShown(); } catch (Throwable ignored) {}
                    }
                    if (startupUiReady) {
                        try { getTutorialController().onTabVisible(index); } catch (Throwable ignored) {}
                    } else {
                        pendingStartupTutorialTabIndex = index;
                    }
                }
            });
        }
        return mainTabNavigator;
    }

    private void markStartupUiReady() {
        startupUiReady = true;
        StartupInputGuard.unblock(this);
        int tutorialTab = pendingStartupTutorialTabIndex >= 0 ? pendingStartupTutorialTabIndex : currentTabIndex;
        pendingStartupTutorialTabIndex = -1;
        mainHandler.postDelayed(() -> {
            try { getTutorialController().onTabVisible(tutorialTab); } catch (Throwable ignored) {}
        }, 650L);
    }


    private void setupTabs() {
        try {
            MainTabNavigator navigator = getMainTabNavigator();
            if (navigator != null) {
                currentTabIndex = navigator.bind(currentTabIndex);
            }
        } catch (Throwable ignored) {
        }
    }

    void showTab(int idx, boolean animate) {
        try {
            MainTabNavigator navigator = getMainTabNavigator();
            if (navigator != null) {
                navigator.showTab(idx, animate);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (getKioskActivityController().dispatchKeyEvent(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private KioskActivityController getKioskActivityController() {
        if (kioskActivityController == null) {
            kioskActivityController = new KioskActivityController(this, mainHandler, MainActivity.class, new KioskActivityController.Host() {
                @Override
                public ActivityMainBinding binding() {
                    return b;
                }

                @Override
                public void appendOutput(String text) {
                    MainActivity.this.appendOutput(text);
                }

                @Override
                public boolean isDebugOutputEnabled() {
                    return MainActivity.this.isAppDebugOutputEnabled();
                }

                @Override
                public void debugOutput(String channel, String area, String message) {
                    MainActivity.this.debugOutput(channel, area, message);
                }
            });
        }
        return kioskActivityController;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            MainTabNavigator navigator = mainTabNavigator;
            if (navigator != null) navigator.handleDispatchTouchEvent(ev);
        } catch (Throwable ignored) {
        }
        return super.dispatchTouchEvent(ev);
    }


    private void setupNetworkTab() {
        try {
            if (b == null || b.tabNetwork == null) return;
            getNetworkActivityControllers().bind();
        } catch (Throwable ignored) {
        }
    }



    @Override
    public boolean isAppDebugOutputEnabled() {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            return sp != null && sp.getBoolean(KEY_DEBUG_OUTPUT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void debugPackageInstall(String area, String message) {
        PackageInstallDebug.logToOutput(area, message, packageInstallDebugOutput);
    }

    private void debugOutput(String channel, String area, String message) {
        if (!isAppDebugOutputEnabled()) return;
        String line = DebugLog.line(channel, area, message);
        DebugLog.log(DebugLog.DEFAULT_TAG, channel, area, message);
        lifetimeLog("debug", line + "\n");
        appendOutput(line + "\n");
    }

    private void debugOutputWarn(String channel, String area, String message) {
        if (!isAppDebugOutputEnabled()) return;
        String line = DebugLog.line(channel, area, message);
        DebugLog.warn(DebugLog.DEFAULT_TAG, channel, area, message);
        lifetimeLog("debug-warn", line + "\n");
        appendOutput(line + "\n");
    }

    private void debugStartupLoaded(String area, String message) {
        debugOutput("startup", area, TextUtils.isEmpty(message) ? "loaded" : message);
    }

    private void debugStartupLoaded(String area) {
        debugStartupLoaded(area, "loaded");
    }

    private void debugExecModeState(String area, ExecMode mode) {
        if (!isAppDebugOutputEnabled()) return;
        boolean embedded = false;
        boolean external = false;
        boolean cachedEmbedded = false;
        boolean cachedExternal = false;
        boolean currentEmbedded = false;
        try { embedded = ShizukuProvider.hasEmbeddedBinder(); } catch (Throwable ignored) {}
        try { external = ShizukuProvider.hasExternalBinder(); } catch (Throwable ignored) {}
        try { cachedEmbedded = ShizukuProvider.hasCachedEmbeddedBinder(); } catch (Throwable ignored) {}
        try { cachedExternal = ShizukuProvider.hasCachedExternalBinder(); } catch (Throwable ignored) {}
        try { currentEmbedded = ShizukuProvider.isCurrentBinderEmbedded(); } catch (Throwable ignored) {}
        debugOutput("exec-mode", area, "mode=" + (mode == null ? "null" : mode.name())
                + " embedded=" + embedded
                + " external=" + external
                + " cachedEmbedded=" + cachedEmbedded
                + " cachedExternal=" + cachedExternal
                + " currentEmbedded=" + currentEmbedded
                + " softDisconnected=" + shizukuSoftDisconnected);
    }

    private static int findAdapterIndexByText(ArrayAdapter<?> adapter, String text) {
        return DropdownUi.findAdapterIndexByText(adapter, text);
    }

    @Override
    public void showDropdownAtLastSelection(AutoCompleteTextView tv, String lastText) {
        DropdownUi.showDropdownAtLastSelection(tv, lastText, this::applyDropdownListTweaks);
    }

    @Override
    public void configureSafeDropdownEndIcon(com.google.android.material.textfield.TextInputLayout til, final Runnable onClick) {
        DropdownUi.configureSafeDropdownEndIcon(this, til, onClick);
    }

    @Override
    public void configureTapOnlyDropdownField(final AutoCompleteTextView tv,
                                               final int slopPx,
                                               final int maxTapMs,
                                               final Runnable onTap) {
        DropdownUi.configureTapOnlyDropdownField(tv, slopPx, maxTapMs, onTap);
    }

    private void configureClickOnlyDropdownField(final AutoCompleteTextView tv, final Runnable onClick) {
        DropdownUi.configureClickOnlyDropdownField(tv, onClick);
    }

    private void tryApplyDropdownTweaks(AutoCompleteTextView tv) {
        DropdownUi.tryApplyDropdownTweaks(tv, this::applyDropdownListTweaks);
    }

    private void tryScrollDropdownList(AutoCompleteTextView tv, int position) {
        DropdownUi.tryScrollDropdownList(tv, position, this::applyDropdownListTweaks);
    }

private void setPackagesSpinnerVisible(boolean visible) {
    if (b == null) return;
    try {
        if (b.tabPackages.pbPackagesLoading != null) {
            b.tabPackages.pbPackagesLoading.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    } catch (Throwable ignored) {
    }
}

    private void preservePackagesScrollPosition(Runnable update) {
        if (update == null) return;
        final View root;
        try {
            root = b == null || b.tabPackages == null ? null : b.tabPackages.getRoot();
        } catch (Throwable ignored) {
            update.run();
            return;
        }
        if (root == null) {
            update.run();
            return;
        }

        final int scrollX = root.getScrollX();
        final int scrollY = root.getScrollY();
        update.run();

        // Async dropdown/permission updates can change focus/layout while the user is reading or
        // scrolling the Packages page. Restore the current scroll offset after layout settles so
        // package population does not jump past the permission area.
        root.post(() -> {
            try { root.scrollTo(scrollX, scrollY); } catch (Throwable ignored) {}
            root.postDelayed(() -> {
                try { root.scrollTo(scrollX, scrollY); } catch (Throwable ignored) {}
            }, 80L);
        });
    }




    private PackagesUiActivityController getPackagesUiActivityController() {
        if (packagesUiActivityController == null) {
            packagesUiActivityController = new PackagesUiActivityController(new PackagesUiActivityController.Host() {
                @Override
                public android.content.Context getContext() {
                    return MainActivity.this;
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return b;
                }

                @Override
                public Handler getMainHandler() {
                    return mainHandler;
                }

                @Override
                public ExecutorService getExecutor() {
                    return io;
                }

                @Override
                public boolean isReadyAndGranted() {
                    return MainActivity.this.safeIsReadyAndGranted();
                }

                @Override
                public boolean isSafeToken(String value) {
                    return MainActivity.this.isSafeToken(value);
                }

                @Override
                public void refreshStatus() {
                    MainActivity.this.refreshStatus();
                }

                @Override
                public void appendOutput(String text) {
                    MainActivity.this.appendOutput(text);
                }

                @Override
                public void setLastOutputTag(String tag) {
                    MainActivity.this.lastOutputTag = tag;
                }

                @Override
                public void runShellCommand(String command) {
                    MainActivity.this.runShellCommand(command);
                }

                @Override
                public void runShellCommandCapture(String command, dev.perms.test.packages.PackageActions.ShellCaptureCallback callback) {
                    MainActivity.this.runShellCommandCapture(command,
                            callback == null ? null : callback::onComplete);
                }

                @Override
                public void executeIo(Runnable task) {
                    MainActivity.this.ioExecute(task);
                }

                @Override
                public void runOnUiThread(Runnable task) {
                    MainActivity.this.runOnUiThread(task);
                }

                @Override
                public void configureSafeDropdownEndIcon(com.google.android.material.textfield.TextInputLayout layout, Runnable onClick) {
                    MainActivity.this.configureSafeDropdownEndIcon(layout, onClick);
                }

                @Override
                public void configureTapOnlyDropdownField(AutoCompleteTextView view, int touchSlop, int maxTapMs, Runnable onTap) {
                    MainActivity.this.configureTapOnlyDropdownField(view, touchSlop, maxTapMs, onTap);
                }

                @Override
                public void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText) {
                    MainActivity.this.showDropdownAtLastSelection(view, lastText);
                }

                @Override
                public void rememberPackageToolsTargetPackage(String packageName) {
                    MainActivity.this.rememberPackageToolsTargetPackage(packageName);
                }

                @Override
                public boolean usesAppPermissions() {
                    return getPermissionDropdownController().usesAppPermissions();
                }

                @Override
                public void refreshPermissionsForPackage(String packageName) {
                    getPermissionDropdownController().refreshForPackage(packageName);
                }

                @Override
                public void refreshHomeAppTray() {
                    if (MainActivity.this.homeAppTrayController != null) {
                        MainActivity.this.homeAppTrayController.refreshAsync();
                    }
                }

                @Override
                public void extractInstalledPackage(String packageName, String displayName) {
                    MainActivity.this.getInstalledPackageActivityController().extractInstalledPackageToPublicFolder(packageName, displayName);
                }

                @Override
                public int colorDangerous() {
                    return colorDangerous;
                }

                @Override
                public int colorSignature() {
                    return colorSignature;
                }

                @Override
                public int colorGranted() {
                    return colorGranted;
                }

                @Override
                public int colorRevoked() {
                    return colorRevoked;
                }

                @Override
                public int colorMuted() {
                    return colorMuted;
                }

                @Override
                public boolean colorizeAppDropdown() {
                    return colorizeAppDropdown;
                }
            });
        }
        return packagesUiActivityController;
    }

    private void setupAppDropdown() {
        String restoreFilter = restoreAppFilterText;
        restoreAppFilterText = null;
        getPackagesUiActivityController().setupAppDropdown(restoreFilter);
    }

    private void setupTargetPkgWatchers() {
        getPackagesUiActivityController().setupTargetPackageWatchers();
    }

    private void updatePkgInfoSoon() {
        getPackagesUiActivityController().updatePackageInfoSoon();
    }

    private void setupLauncherModeToggle() {
        getKioskActivityController().setupLauncherModeToggle();
    }

    private void maybeOpenHomeSettingsAfterModeSwitch() {
        getKioskActivityController().maybeOpenHomeSettingsAfterModeSwitch();
    }

    private void refreshBinaryAvailabilityUi() {
        getShellActivityControllers().refreshBinaryAvailabilityUi();
    }

    private PackageInstallActivityController getPackageInstallActivityController() {
        if (packageInstallActivityController == null) {
            packageInstallActivityController = new PackageInstallActivityController(
                    new PackageInstallActivityController.Host() {
                        @Override
                        public androidx.appcompat.app.AppCompatActivity getActivity() {
                            return MainActivity.this;
                        }

                        @Override
                        public ActivityMainBinding getBinding() {
                            return b;
                        }

                        @Override
                        public SharedPreferences getPreferences() {
                            return getSharedPreferences(PREFS, MODE_PRIVATE);
                        }

                        @Override
                        public String queryDisplayName(Uri uri) {
                            return MainActivity.this.queryDisplayName(uri);
                        }

                        @Override
                        public void installPickedPackageFile(Uri uri, String label, boolean fromFileOpen) {
                            MainActivity.this.installPickedPackageFile(uri, label, fromFileOpen);
                        }

                        @Override
                        public void showPackagesInstallerCard() {
                            try {
                                showTab(2, true);
                                revealPackageInstallerCard();
                            } catch (Throwable ignored) {
                            }
                        }

                        @Override
                        public void appendOutput(String text) {
                            MainActivity.this.appendOutput(text);
                        }
                    },
                    KEY_HIDE_FILE_OPEN_UI,
                    KEY_INSTALL_USE_INSTALLER_SCRIPT);
        }
        return packageInstallActivityController;
    }

    private boolean handleFileOpenIntent(Intent intent) {
        return getPackageInstallActivityController().handleFileOpenIntent(intent);
    }

    private void revealPackageInstallerCard() {
        try {
            if (b != null && b.tabPackages != null && b.tabPackages.cardApkInstaller != null) {
                CollapsibleGroupboxController.revealGroupboxesContainingAfterLayout(b.tabPackages.cardApkInstaller);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void revealSmaliEditorCard() {
        try {
            if (b != null && b.tabDebugging != null && b.tabDebugging.cardSmaliEditor != null) {
                CollapsibleGroupboxController.revealGroupboxesContainingAfterLayout(b.tabDebugging.cardSmaliEditor);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public ArrayList<PackageDropdownEntry> snapshotAllPackages() {
        return getPackagesUiActivityController().snapshotAllApps();
    }

    @Override
    public void toast(String text) {
        try {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int colorGranted() {
        return colorGranted;
    }

    @Override
    public int colorRevoked() {
        return colorRevoked;
    }

    @Override
    public boolean colorizeAppDropdown() {
        return colorizeAppDropdown;
    }

    private DebuggingActivityControllers getDebuggingActivityControllers() {
        if (debuggingActivityControllers == null) {
            debuggingActivityControllers = new DebuggingActivityControllers(this);
        }
        return debuggingActivityControllers;
    }

    @Override
    public android.app.Activity getActivity() {
        return this;
    }

    @Override
    public ActivityMainBinding getBinding() {
        return b;
    }

    @Override
    public ExecutorService getDebugApkExecutor() {
        return DEBUG_APK_IO;
    }

    @Override
    public ExecutorService getDebuggingIoExecutor() {
        return io;
    }

    @Override
    public SharedPreferences getDebuggingPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    public Handler getMainHandler() {
        return mainHandler;
    }

    @Override
    public String getOpenSmaliEditorUriExtra() {
        return EXTRA_OPEN_SMALI_EDITOR_URI;
    }

    @Override
    public String getOpenSmaliEditorLabelExtra() {
        return EXTRA_OPEN_SMALI_EDITOR_LABEL;
    }

    @Override
    public void runDebuggingIo(Runnable action) {
        ioExecute(action);
    }

    @Override
    public void selectDebuggingTab() {
        try {
            if (b != null && b.tabLayout != null) {
                TabLayout.Tab tab = b.tabLayout.getTabAt(7);
                if (tab != null) tab.select();
                else showTab(7, true);
            } else {
                showTab(7, true);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String getLastDebuggingDexEntryDropdownText() {
        return getDebuggingActivityControllers().getLastDexEntryDropdownText();
    }

    @Override
    public dev.perms.test.debugging.mitm.DebuggingMitmController.ShellResult runMitmShellCommandCaptureSync(String command) {
        CmdResult result = runShellCommandCaptureSync(command);
        if (result == null) return null;
        return new dev.perms.test.debugging.mitm.DebuggingMitmController.ShellResult(result.exitCode, result.stdout, result.stderr);
    }

    @Override
    public File getWorkRoot(String type) {
        return getExternalFilesDir(type);
    }

    @Override
    public String quoteShell(String value) {
        return shQuote(value);
    }

    private boolean handleIncomingToolsTextEditorIntent(Intent intent) {
        try {
            return getToolsActivityControllers().handleIncomingTextEditorIntent(intent);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean handleIncomingSmaliEditorIntent(Intent intent) {
        return getDebuggingActivityControllers().handleIncomingSmaliEditorIntent(intent);
    }

    private void clearPickedApkSelectionUi() {
        getPackageInstallActivityController().clearSelectionUi();
    }

    private void tryAutoInstallPendingFile() {
        getPackageInstallActivityController().tryAutoInstallPendingFile();
    }

    private void maybeAutoInstallPendingFile(boolean binderAlive, boolean granted) {
        getPackageInstallActivityController().maybeAutoInstallPendingFile(binderAlive, granted);
    }

    private void ensureApkPickerLauncher() {
        getPackageInstallActivityController().registerActivityResults();
    }

    private void setupApkInstaller() {
        getPackageInstallActivityController().setup();
    }


    private ScriptsTabController getScriptsTabController() {
        if (scriptsTabController == null) {
            scriptsTabController = new ScriptsTabController(
                    this,
                    () -> b,
                    scriptsEditorUi,
                    PREFS,
                    this::runShellCommand,
                    (command, callback) -> runShellCommandCapture(command, callback == null
                            ? null
                            : (exit, out, err) -> callback.onComplete(exit, out, err)),
                    this::appendOutput,
                    this::writeTextToExternalDir);
        }
        return scriptsTabController;
    }

    private void setupScriptsTab() {
        getScriptsTabController().setup();
    }

    private static String safeText(TextView tv) {
        try {
            if (tv == null) return "";
            CharSequence cs = tv.getText();
            return cs == null ? "" : cs.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }


    private void setupDebuggingTab() {
        getDebuggingActivityControllers().bind();
        getDebuggingGroupboxPopoutController().bind();
    }


    @Override
    public DebuggingRebuiltApkExporter.ToolResult ensureBundledDebuggingTool(String toolName) {
        CmdResult result = ensureBundledBinaryPublicForCurrentMode(toolName);
        if (result == null) return null;
        return new DebuggingRebuiltApkExporter.ToolResult(result.exitCode, result.stdout, result.stderr);
    }

    @Override
    public DebuggingRebuiltApkExporter.ToolResult runDebuggingShellCommandCapture(String command) {
        CmdResult result = runShellCommandCaptureSync(command);
        if (result == null) return null;
        return new DebuggingRebuiltApkExporter.ToolResult(result.exitCode, result.stdout, result.stderr);
    }


    @Override
    public void setDebuggingBusy(boolean busy, String status) {
        DebuggingUi.setBusy(b, busy, status);
    }

    @Override
    public void finishDebuggingToolError(String label, Throwable t) {
        String msg = t == null ? "unknown error" : (t.getClass().getSimpleName() + ": " + t.getMessage());
        setDebuggingBusy(false, label + " failed: " + msg);
        appendOutput("[Debugging] " + label + " failed: " + msg + "\n");
    }


    // Name used by older wiring; keep as a thin wrapper.
    private void runSelectedScript() {
        getScriptsTabController().runCurrentScriptFromEditor();
    }

    private PackageInstallExecutionActivityController getPackageInstallExecutionActivityController() {
        if (packageInstallExecutionActivityController == null) {
            packageInstallExecutionActivityController = new PackageInstallExecutionActivityController(
                    new PackageInstallExecutionActivityController.Host() {
                        @Override
                        public android.app.Activity getActivity() {
                            return MainActivity.this;
                        }

                        @Override
                        public ActivityMainBinding getBinding() {
                            return b;
                        }

                        @Override
                        public SharedPreferences getPreferences() {
                            return getSharedPreferences(PREFS, MODE_PRIVATE);
                        }

                        @Override
                        public void runIo(Runnable runnable) {
                            io.execute(runnable);
                        }

                        @Override
                        public void runOnUi(Runnable runnable) {
                            MainActivity.this.runOnUiThread(runnable);
                        }

                        @Override
                        public String queryDisplayName(Uri uri) {
                            return MainActivity.this.queryDisplayName(uri);
                        }

                        @Override
                        public String sanitizeFilename(String name) {
                            return MainActivity.this.sanitizeFilename(name);
                        }

                        @Override
                        public PackageInstallSourcePreparer.PreparedSource prepareInstallSourceFile(Uri uri, String displayName) {
                            return MainActivity.this.getPackageInstallRuntimeActivityController().prepareInstallSourceFile(uri, displayName);
                        }

                        @Override
                        public boolean isScriptRequested() {
                            return MainActivity.this.getPackageInstallRuntimeActivityController().isScriptRequested();
                        }

                        @Override
                        public String prepareInstallInputPathForScript(String sourcePath) {
                            return MainActivity.this.getPackageInstallRuntimeActivityController().prepareInputPathForScript(sourcePath);
                        }

                        @Override
                        public String buildInstallScriptCommand(File scriptFile, String inputPath) {
                            return MainActivity.this.getPackageInstallRuntimeActivityController().buildScriptCommand(scriptFile, inputPath);
                        }

                        @Override
                        public void prepareInstallInputPathForPmAsync(String sourcePath, PackageInstallExecutionActivityController.PathCallback callback) {
                            MainActivity.this.getPackageInstallRuntimeActivityController().prepareInputPathForPmAsync(sourcePath, path -> {
                                if (callback != null) callback.onReady(path);
                            });
                        }

                        @Override
                        public String prepareInstallInputPathForPmSync(String sourcePath) {
                            return MainActivity.this.getPackageInstallRuntimeActivityController().prepareInputPathForPmSync(sourcePath);
                        }

                        @Override
                        public String buildPmInstallCreateCommand(String sizeBytes) {
                            return MainActivity.this.getPackageInstallRuntimeActivityController().buildPmInstallCreateCommand(sizeBytes);
                        }

                        @Override
                        public void runShellCommandCaptureAndAppend(String command, PackageInstallExecutionActivityController.ResultCallback callback) {
                            MainActivity.this.runShellCommandCaptureAndAppend(command, (exitCode, stdout, stderr) -> {
                                if (callback != null) callback.onComplete(exitCode, stdout, stderr);
                            });
                        }

                        @Override
                        public PackageInstallExecutionActivityController.ShellResult runRawShizukuShellCapture(String command) {
                            CmdResult result = MainActivity.this.runShizukuShellCapture(command);
                            return new PackageInstallExecutionActivityController.ShellResult(
                                    result == null ? -1 : result.exitCode,
                                    result == null ? "" : result.stdout,
                                    result == null ? "" : result.stderr);
                        }

                        @Override
                        public void runShellCommand(String command) {
                            MainActivity.this.runShellCommand(command);
                        }

                        @Override
                        public void ensureBundledBinaryPublic(String toolName) {
                            MainActivity.this.ensureBundledBinaryPublic(toolName);
                        }

                        @Override
                        public String getPublicStageDir() {
                            return PUBLIC_STAGE_DIR;
                        }

                        @Override
                        public String getPublicFilesDir() {
                            return PUBLIC_FILES_DIR;
                        }

                        @Override
                        public String getPublicBinDir() {
                            return PUBLIC_BIN_DIR;
                        }

                        @Override
                        public boolean isCustomSplitOptionsEnabled() {
                            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CUSTOM_SPLIT_OPTIONS, true);
                        }

                        @Override
                        public String[] getBundledAssetDirs() {
                            return MainActivity.this.getShellBinaryAssets().getBundledAssetDirs();
                        }

                        @Override
                        public void clearPickedSelectionUi() {
                            MainActivity.this.clearPickedApkSelectionUi();
                        }

                        @Override
                        public void closeTaskAfterFileOpen() {
                            MainActivity.this.closeTaskAfterFileOpen();
                        }

                        @Override
                        public void cleanupManagedImportFile(String path) {
                            MainActivity.this.cleanupManagedImportFile(path);
                        }

                        @Override
                        public void appendOutput(String text) {
                            MainActivity.this.appendOutput(text);
                        }

                        @Override
                        public void debug(String area, String message) {
                            MainActivity.this.debugPackageInstall(area, message);
                        }
                    },
                    KEY_HIDE_FILE_OPEN_UI,
                    DEFAULT_INSTALL_SCRIPT);
        }
        return packageInstallExecutionActivityController;
    }

    private void installPickedPackageFile(final Uri uri, final String label) {
        getPackageInstallExecutionActivityController().install(uri, label);
    }

    private void installPickedPackageFile(final Uri uri, final String label, final boolean fromFileOpen) {
        getPackageInstallExecutionActivityController().install(uri, label, fromFileOpen);
    }


    private void closeTaskAfterFileOpen() {
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

    private void cleanupManagedImportFile(String path) {
        PackageInstallImportCleaner.cleanupManagedImportFile(this, EXT_IMPORTS_DIR, path);
    }

    private void cleanupManagedImportsDirOnStart() {
        PackageInstallImportCleaner.cleanupManagedImportsDirOnStart(this, EXT_IMPORTS_DIR);
    }

    private void cleanupAppCacheOnStartupIfEnabled() {
        try {
            new StartupCacheCleaner(
                    this,
                    prefs != null ? prefs : getSharedPreferences(PREFS, MODE_PRIVATE),
                    KEY_CLEAR_CACHE_ON_STARTUP,
                    EXT_IMPORTS_DIR,
                    this::ioExecute,
                    message -> runOnUiThread(() -> appendOutput(message))
            ).cleanupOnStartupIfEnabled();
        } catch (Throwable ignored) {
        }
    }

    static final class CmdResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        CmdResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = (stdout == null) ? "" : stdout;
            this.stderr = (stderr == null) ? "" : stderr;
        }
    }

    private static String readProcessStream(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private CmdResult runShizukuShellCapture(String shCmd) {
        try {
            boolean binderAlive = false;
            try { binderAlive = Shizuku.pingBinder(); } catch (Throwable ignored) { binderAlive = false; }
            boolean granted = false;
            if (binderAlive) {
                try { granted = (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED); } catch (Throwable ignored) { granted = false; }
            }
            if (!binderAlive || !granted) {
                return new CmdResult(1, "", "Shizuku not ready or permission not granted.");
            }

            Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c", shCmd}, null, null);
            String out = "";
            String err = "";
            try {
                out = readProcessStream(p.getInputStream());
            } catch (Throwable ignored) {}
            try {
                err = readProcessStream(p.getErrorStream());
            } catch (Throwable ignored) {}
            int code = 1;
            try { code = p.waitFor(); } catch (Throwable ignored) { code = 1; }
            return new CmdResult(code, out, err);
        } catch (Throwable t) {
            return new CmdResult(1, "", "Shell failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }





    private void runShellCommandCaptureAndAppend(String cmd, final CaptureCallback cb) {
        getShellRuntimeController().runCaptureAndAppend(cmd, (exit, out, err) -> {
            if (cb != null) cb.onComplete(exit, out, err);
        });
    }




    private AppExternalFileStore getAppExternalFileStore() {
        if (appExternalFileStore == null) {
            appExternalFileStore = new AppExternalFileStore(this, this::appendOutput);
        }
        return appExternalFileStore;
    }

    @Override
    public File copyUriToExternalDir(Uri uri, String subdir, String filename) {
        return getAppExternalFileStore().copyUriToExternalDir(uri, subdir, filename);
    }

    private File writeTextToExternalDir(String subdir, String filename, String text) {
        return getAppExternalFileStore().writeTextToExternalDir(subdir, filename, text);
    }

    @Override
    public String queryDisplayName(Uri uri) {
        return getAppExternalFileStore().queryDisplayName(uri);
    }

    private static String sanitizeFilename(String s) {
        return AppExternalFileStore.sanitizeFilename(s);
    }

    static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }






    private NetworkActivityControllers getNetworkActivityControllers() {
        if (networkActivityControllers == null) {
            networkActivityControllers = new NetworkActivityControllers(
                    new NetworkActivityDependencies(
                            this,
                            () -> b,
                            () -> getSharedPreferences(PREFS, MODE_PRIVATE),
                            mainHandler,
                            io,
                            command -> {
                                CmdResult result = runShizukuShellCapture(command);
                                return new NetworkActivityDependencies.ShellResult(result.exitCode, result.stdout, result.stderr);
                            },
                            command -> {
                                if (!isShizukuReady()) throw new IOException("Shizuku not ready or permission not granted.");
                                try {
                                    return ShizukuCompat.newProcess(new String[]{"sh", "-c", command}, null, null);
                                } catch (Exception e) {
                                    IOException io = new IOException("Shizuku process start failed: " + e.getMessage());
                                    io.initCause(e);
                                    throw io;
                                }
                            },
                            this::isAppDebugOutputEnabled,
                            this::appendOutput,
                            this::filesCanUseShizuku,
                            (cmd, cb) -> runFilesShizukuCommandCapture(cmd, cb == null
                                    ? null
                                    : (exit, out, err) -> cb.onComplete(exit, out, err)),
                            tag -> lastOutputTag = tag,
                            this::runShellCommand));
        }
        return networkActivityControllers;
    }

    private void schedulePluginsAfterSuccessfulStartup(Bundle savedInstanceState) {
        mainHandler.postDelayed(() -> {
            try {
                if (isFinishing() || b == null || !startupUiReady) return;
                debugOutput("startup", "plugins", "loading last after core startup checks");
                setupPluginsTab();
                debugOutput("startup", "plugins", "post-core tab controls bound");
            } catch (Throwable t) {
                debugOutputWarn("plugins", "startup", "post-core plugin setup failed: " + (t == null ? "unknown" : t.getMessage()));
                try { appendOutput("[plugins] Post-startup plugin setup failed: " + (t == null ? "unknown" : t.getMessage()) + "\n"); } catch (Throwable ignored) {}
            }
        }, savedInstanceState == null ? 300L : 120L);
    }

    private void setupPluginsTab() {
        getPluginsActivityController().bind();
        getPluginsGroupboxPopoutController().bind();
    }

    private PluginsActivityController getPluginsActivityController() {
        if (pluginsActivityController == null) {
            pluginsActivityController = new PluginsActivityController(new PluginsActivityController.Host() {
                @Override
                public android.app.Activity getActivity() {
                    return MainActivity.this;
                }

                @Override
                public dev.perms.test.databinding.ActivityMainBinding getBinding() {
                    return b;
                }

                @Override
                public android.content.SharedPreferences getSharedPreferences() {
                    return MainActivity.this.getSharedPreferences(PREFS, MODE_PRIVATE);
                }

                @Override
                public void appendOutput(String message) {
                    MainActivity.this.appendOutput(message);
                }

                @Override
                public boolean isDebugOutputEnabled() {
                    return MainActivity.this.isAppDebugOutputEnabled();
                }

                @Override
                public void debugOutput(String area, String message) {
                    MainActivity.this.debugOutput("plugins", area, message);
                }

                @Override
                public boolean showPluginTextPanel(String panelKey, String title, String subtitle, String text, String syntax, String windowStyle, String windowFit) {
                    boolean opened = false;
                    if (PermsTestUiCompat.shouldUseVrProfile(MainActivity.this)) {
                        opened = GenericPanelLauncher.showTextPanel(
                                MainActivity.this,
                                panelKey,
                                title,
                                subtitle,
                                text,
                                syntax);
                    }
                    if (!opened) {
                        GenericViewerDialog.showHighlightedTextWindow(
                                MainActivity.this,
                                title,
                                subtitle,
                                text,
                                syntax,
                                TextUtils.isEmpty(windowStyle) ? "full" : windowStyle,
                                TextUtils.isEmpty(windowFit) ? "current" : windowFit);
                        opened = true;
                    }
                    debugOutput("panel", "text surface request key=" + panelKey + ", vrPanel=" + PermsTestUiCompat.shouldUseVrProfile(MainActivity.this) + ", opened=" + opened);
                    return opened;
                }

                @Override
                public boolean openPermsTestTool(String pluginId, String toolId, boolean requestPanel, String windowStyle, String windowFit) {
                    return MainActivity.this.openPluginToolSurface(pluginId, toolId, requestPanel, windowStyle, windowFit);
                }

                @Override
                public void runShellCommandCapture(String command, PluginsActivityController.ShellCallback callback) {
                    MainActivity.this.runShellCommandCapture(command, callback == null
                            ? null
                            : (code, out, err) -> callback.onComplete(code, out, err));
                }
            });
        }
        return pluginsActivityController;
    }

    private boolean openPluginToolSurface(String pluginId, String toolId, boolean requestPanel, String windowStyle, String windowFit) {
        try {
            if (TextUtils.isEmpty(toolId)) return false;
            if (requestPanel && PermsTestUiCompat.shouldUseVrProfile(this)) {
                boolean opened = PluginToolPanelActivity.start(this, pluginId, toolId);
                if (opened) appendOutput("[plugins] Opened " + PluginToolSurfaceDialog.titleFor(toolId) + " plugin panel.\n");
                return opened;
            }
            String style = requestPanel && (TextUtils.isEmpty(windowStyle) || "compact".equalsIgnoreCase(windowStyle)) ? "full" : windowStyle;
            String fit = requestPanel && TextUtils.isEmpty(windowFit) ? "current" : windowFit;
            return PluginToolSurfaceDialog.show(this, pluginId, toolId, style, fit, message -> MainActivity.this.appendOutput(message));
        } catch (Throwable t) {
            try { appendOutput("[plugins] Tool surface open failed: " + (t == null ? "unknown" : t.getMessage()) + "\n"); } catch (Throwable ignored) {}
            return false;
        }
    }

    private void setupToolsTab() {
        getToolsActivityControllers().bind();
        getToolsGroupboxPopoutController().bind();
    }

    private ToolsActivityControllers getToolsActivityControllers() {
        if (toolsActivityControllers == null) {
            toolsActivityControllers = new ToolsActivityControllers(new ToolsActivityControllers.Host() {
                @Override
                public android.app.Activity getActivity() {
                    return MainActivity.this;
                }

                @Override
                public dev.perms.test.databinding.ActivityMainBinding getBinding() {
                    return b;
                }

                @Override
                public void appendOutput(String message) {
                    MainActivity.this.appendOutput(message);
                }

                @Override
                public boolean isDebugOutputEnabled() {
                    return MainActivity.this.isAppDebugOutputEnabled();
                }

                @Override
                public void debugOutput(String area, String message) {
                    MainActivity.this.debugOutput("tools", area, message);
                }

                @Override
                public android.content.SharedPreferences getSharedPreferences() {
                    return MainActivity.this.getSharedPreferences(PREFS, MODE_PRIVATE);
                }

                @Override
                public void runShellCommandCapture(String command, ToolsActivityControllers.ShellCallback callback) {
                    MainActivity.this.runShellCommandCapture(command, callback == null
                            ? null
                            : (code, out, err) -> callback.onComplete(code, out, err));
                }

                @Override
                public void showTab(int index) {
                    MainActivity.this.showTab(index, true);
                }
            });
        }
        return toolsActivityControllers;
    }

    private LoggingActivityControllers getLoggingActivityControllers() {
        if (loggingActivityControllers == null) {
            loggingActivityControllers = new LoggingActivityControllers(
                    new LoggingActivityDependencies(
                            this,
                            () -> b,
                            () -> getSharedPreferences(PREFS, MODE_PRIVATE),
                            this::safeIsReadyAndGranted,
                            this::refreshStatus,
                            this::appendOutput,
                            tag -> lastOutputTag = tag,
                            () -> lastOutputTag,
                            () -> lastSavedFile,
                            file -> lastSavedFile = file,
                            this::runShellCommand,
                            (command, callback) -> runShellCommandCapture(command, callback == null
                                    ? null
                                    : (exit, out, err) -> callback.onComplete(exit, out, err)),
                            command -> {
                                CmdResult r = runShizukuShellCapture(command);
                                return r != null && r.exitCode == 0;
                            }));
        }
        return loggingActivityControllers;
    }

    private MemoryActivityControllers getMemoryActivityControllers() {
        if (memoryActivityControllers == null) {
            memoryActivityControllers = new MemoryActivityControllers(
                    new MemoryActivityDependencies(
                            this,
                            () -> b,
                            () -> getSharedPreferences(PREFS, MODE_PRIVATE),
                            getPackageName(),
                            PUBLIC_BIN_DIR,
                            () -> getPackagesUiActivityController().getSelectedPackageToolsPackage(),
                            () -> getPackagesUiActivityController().getAllApps(),
                            this::configureSafeDropdownEndIcon,
                            this::configureTapOnlyDropdownField,
                            this::tryApplyDropdownTweaks,
                            this::showDropdownAtLastSelection,
                            this::ioExecute,
                            task -> runOnUiThread(task),
                            this::appendOutput,
                            this::getCurrentMemoryExecMode,
                            this::runMemoryAppActionCommand,
                            this::stageMemoryToolCommandBinary,
                            shellCommand -> runShellCommandCaptureAndAppend(shellCommand, null),
                            () -> currentTabIndex == 3));
        }
        return memoryActivityControllers;
    }
    private void rememberPackageToolsTargetPackage(String pkg) {
        getMemoryActivityControllers().rememberPackageToolsTargetPackage(pkg);
    }
    private void refreshMemoryTargetPackages() {
        refreshMemoryTargetPackages(false);
    }

    private void refreshMemoryTargetPackages(boolean userInitiated) {
        getMemoryActivityControllers().refreshTargetPackages(userInitiated);
    }
    private void refreshMemoryProcesses() {
        refreshMemoryProcesses(true);
    }

    private void refreshMemoryProcesses(boolean userInitiated) {
        getMemoryActivityControllers().refreshProcesses(userInitiated);
    }

    private void onMemoryTabShown() {
        getMemoryActivityControllers().onTabShown();
    }
    private void setupMemoryTab() {
        getMemoryActivityControllers().bindTab();
    }
    private MemoryAppActions.CommandResult runMemoryAppActionCommand(String command) {
        final CmdResult r = runShellCommandCaptureSync(command);
        if (r == null) return null;
        return new MemoryAppActions.CommandResult(r.exitCode, r.stdout, r.stderr);
    }
    private ExecMode getCurrentMemoryExecMode() {
        ExecMode modeNow = execMode;
        if (prefs != null) {
            try { modeNow = ExecMode.get(prefs); } catch (Throwable ignored) {}
        }
        return modeNow;
    }

    private MemoryToolCommandRunner.CommandResult stageMemoryToolCommandBinary(String name) {
        CmdResult r = ensureBundledBinaryPublicForCurrentMode(name);
        if (r == null) return null;
        return new MemoryToolCommandRunner.CommandResult(r.exitCode, r.stdout, r.stderr);
    }

    private CmdResult ensureBundledBinaryPublicForCurrentMode(String name) {
        try {
            if (TextUtils.isEmpty(name)) return new CmdResult(1, "", "Binary name is empty.");
            String dst = PUBLIC_BIN_DIR + "/" + name;
            if (!hasBundledAsset(name)) return new CmdResult(1, "", "Bundled binary not found for this ABI: " + name);

            File stageDir = getBundledStageDir();
            if (stageDir == null) return new CmdResult(1, "", "Unable to access staged binary directory.");
            if (!stageDir.exists() && !stageDir.mkdirs()) {
                return new CmdResult(1, "", "Unable to create staged binary directory.");
            }

            File stage = new File(stageDir, name);
            // Always refresh the private staged copy from the APK asset before publishing it.
            // Memory tools are iterated frequently, and a stale /data/local/tmp copy can keep
            // running an older medit build after the APK has been updated.
            try (InputStream in = new BufferedInputStream(openBundledAsset(name));
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(stage, false))) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                out.flush();
            }
            try { stage.setReadable(true, false); } catch (Throwable ignored) {}
            try { stage.setExecutable(true, false); } catch (Throwable ignored) {}

            String cmd = "mkdir -p " + shQuote(PUBLIC_BIN_DIR)
                    + " && rm -f " + shQuote(dst)
                    + " && cp " + shQuote(stage.getAbsolutePath()) + " " + shQuote(dst)
                    + " && chmod 755 " + shQuote(dst);
            return runShellCommandCaptureSync(cmd);
        } catch (Throwable t) {
            return new CmdResult(1, "", "Bundled binary install failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    CmdResult runShellCommandCaptureSync(String cmd) {
        if (!safeIsReadyAndGranted()) {
            return new CmdResult(-1, "", "Backend not ready");
        }
        if (TextUtils.isEmpty(cmd)) {
            return new CmdResult(-1, "", "Command is empty");
        }
        try {
            ExecMode modeNow = execMode;
            if (prefs != null) {
                try { modeNow = ExecMode.get(prefs); } catch (Throwable ignored) {}
            }
            final String trimmed = cmd.trim();
            if (modeNow == ExecMode.LADB) {
                LadbClient.CmdResult r;
                if (trimmed.startsWith("adb ")) {
                    r = getLadbController().rawAdb(LadbClient.tokenizeAdbArgs(trimmed));
                } else {
                    r = getLadbController().shellShC(trimmed);
                }
                return new CmdResult(r.exitCode, r.stdout, r.stderr);
            }
            if (modeNow == ExecMode.SYSTEM) {
                Process p = new ProcessBuilder("sh", "-c", trimmed).redirectErrorStream(false).start();
                String out = readAll(p.getInputStream());
                String err = readAll(p.getErrorStream());
                int code = p.waitFor();
                return new CmdResult(code, out == null ? "" : out, err == null ? "" : err);
            }
            return runShizukuShellCapture(trimmed);
        } catch (Throwable t) {
            return new CmdResult(1, "", t.toString());
        }
    }


    private DebuggablePackageActivityController getDebuggablePackageActivityController() {
        if (debuggablePackageActivityController == null) {
            debuggablePackageActivityController = new DebuggablePackageActivityController(new DebuggablePackageActivityController.Host() {
                @Override
                public android.content.Context getContext() {
                    return MainActivity.this;
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return b;
                }

                @Override
                public SharedPreferences getPreferences() {
                    return prefs;
                }

                @Override
                public Handler getMainHandler() {
                    return mainHandler;
                }

                @Override
                public ExecutorService getExecutor() {
                    return DEBUG_APK_IO;
                }

                @Override
                public Uri getPickedPackageUri() {
                    return getPackageInstallActivityController().getPickedApkUri();
                }

                @Override
                public String getPickedPackageLabel() {
                    return getPackageInstallActivityController().getPickedApkLabel();
                }

                @Override
                public String queryDisplayName(Uri uri) {
                    return MainActivity.this.queryDisplayName(uri);
                }

                @Override
                public File copyUriToExternalDir(Uri uri, String subdir, String filename) {
                    return MainActivity.this.copyUriToExternalDir(uri, subdir, filename);
                }

                @Override
                public DebuggablePackageActivityController.ToolResult ensureBundledBinaryPublicForCurrentMode(String toolName) {
                    CmdResult result = MainActivity.this.ensureBundledBinaryPublicForCurrentMode(toolName);
                    if (result == null) return null;
                    return new DebuggablePackageActivityController.ToolResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public DebuggablePackageActivityController.ToolResult runShellCommandCaptureSync(String command) {
                    CmdResult result = MainActivity.this.runShellCommandCaptureSync(command);
                    if (result == null) return null;
                    return new DebuggablePackageActivityController.ToolResult(result.exitCode, result.stdout, result.stderr);
                }

                @Override
                public File getExternalFilesDir(String type) {
                    return MainActivity.this.getExternalFilesDir(type);
                }

                @Override
                public void deleteTreeQuietly(File file) {
                    MainActivity.this.deleteTreeQuietly(file);
                }

                @Override
                public void appendOutput(String text) {
                    MainActivity.this.appendOutput(text);
                }

                @Override
                public void runOnUiThread(Runnable action) {
                    MainActivity.this.runOnUiThread(action);
                }
            });
        }
        return debuggablePackageActivityController;
    }

    private void clearDebuggablePackageJobLog() {
        getDebuggablePackageActivityController().clearLog();
    }

    private void appendDebuggablePackageLog(String msg) {
        getDebuggablePackageActivityController().appendLog(msg);
    }

    private void restoreDebuggablePackageLogIfNeeded() {
        getDebuggablePackageActivityController().restoreLogIfNeeded();
    }

    private void setDebuggablePackageJobStatus(boolean running, String status) {
        getDebuggablePackageActivityController().setStatus(running, status);
    }

    private void restoreDebuggablePackageJobStatus() {
        getDebuggablePackageActivityController().restoreStatus();
    }

    private void scheduleDebuggablePackageStatusPoll() {
        getDebuggablePackageActivityController().schedulePoll();
    }

    private InstalledPackageActivityController getInstalledPackageActivityController() {
        if (installedPackageActivityController == null) {
            installedPackageActivityController = new InstalledPackageActivityController(new InstalledPackageActivityController.Host() {
                @Override
                public android.content.Context getContext() {
                    return MainActivity.this;
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return b;
                }

                @Override
                public ExecutorService getExecutor() {
                    return DEBUG_APK_IO;
                }

                @Override
                public boolean isSafePackageName(String packageName) {
                    return MainActivity.this.isSafeToken(packageName);
                }

                @Override
                public InstalledPackageActivityController.ToolResult runShellCommandCaptureSync(String command) {
                    CmdResult r = MainActivity.this.runShellCommandCaptureSync(command);
                    if (r == null) return new InstalledPackageActivityController.ToolResult(1, "", "Command failed.");
                    return new InstalledPackageActivityController.ToolResult(r.exitCode, r.stdout, r.stderr);
                }

                @Override
                public File getExternalFilesDir(String type) {
                    return MainActivity.this.getExternalFilesDir(type);
                }

                @Override
                public void clearDebuggablePackageLog() {
                    MainActivity.this.clearDebuggablePackageJobLog();
                }

                @Override
                public void appendDebuggablePackageLog(String text) {
                    MainActivity.this.appendDebuggablePackageLog(text);
                }

                @Override
                public void setDebuggablePackageJobStatus(boolean running, String status) {
                    MainActivity.this.setDebuggablePackageJobStatus(running, status);
                }

                @Override
                public void runCreateDebuggablePackage(Uri sourceUri, String sourceLabel, String outputPath, boolean useApktool) {
                    MainActivity.this.getDebuggablePackageActivityController()
                            .runCreateDebuggablePackage(sourceUri, sourceLabel, outputPath, useApktool);
                }

                @Override
                public void deleteTreeQuietly(File file) {
                    MainActivity.this.deleteTreeQuietly(file);
                }

                @Override
                public void appendOutput(String text) {
                    MainActivity.this.appendOutput(text);
                }

                @Override
                public void runOnUiThread(Runnable action) {
                    MainActivity.this.runOnUiThread(action);
                }
            });
        }
        return installedPackageActivityController;
    }

    private InstalledPackageExtractor.ExtractedInstalledPackage extractInstalledPackageForPublicExport(String packageName, String displayName) throws IOException {
        return getInstalledPackageActivityController().extractForPublicExport(packageName, displayName);
    }

    @Override
    public InstalledPackageExtractor.ExtractedInstalledPackage extractInstalledPackageForDebug(String packageName, String displayName) throws IOException {
        return getInstalledPackageActivityController().extractForDebug(packageName, displayName);
    }

    private ApkEditorActivityController getApkEditorActivityController() {
        if (apkEditorActivityController == null) {
            apkEditorActivityController = new ApkEditorActivityController(new ApkEditorActivityController.Host() {
                @Override
                public android.app.Activity getActivity() {
                    return MainActivity.this;
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return b;
                }

                @Override
                public ExecutorService getExecutor() {
                    return DEBUG_APK_IO;
                }

                @Override
                public Uri getPickedPackageUri() {
                    return getPackageInstallActivityController().getPickedApkUri();
                }

                @Override
                public String getPickedPackageLabel() {
                    return getPackageInstallActivityController().getPickedApkLabel();
                }

                @Override
                public String queryDisplayName(Uri uri) {
                    return MainActivity.this.queryDisplayName(uri);
                }

                @Override
                public android.content.ContentResolver contentResolver() {
                    return getContentResolver();
                }

                @Override
                public File getExternalFilesDir(String type) {
                    return MainActivity.this.getExternalFilesDir(type);
                }

                @Override
                public ApkEditorActivityController.ToolResult ensureBundledTool(String toolName) {
                    CmdResult r = ensureBundledBinaryPublicForCurrentMode(toolName);
                    if (r == null) return new ApkEditorActivityController.ToolResult(1, "", "No tool result.");
                    return new ApkEditorActivityController.ToolResult(r.exitCode, r.stdout, r.stderr);
                }

                @Override
                public ApkEditorActivityController.ToolResult runShell(String command) {
                    CmdResult r = runShellCommandCaptureSync(command);
                    if (r == null) return new ApkEditorActivityController.ToolResult(1, "", "No shell result.");
                    return new ApkEditorActivityController.ToolResult(r.exitCode, r.stdout, r.stderr);
                }

                @Override
                public void appendOutput(String text) {
                    MainActivity.this.appendOutput(text);
                }

                @Override
                public void runOnUiThread(Runnable runnable) {
                    MainActivity.this.runOnUiThread(runnable);
                }

                @Override
                public void openSmaliWorkspace(String apkInputPath,
                                               String smaliInputDirPath,
                                               String selectedSmaliFilePath,
                                               String dexOutputPath,
                                               String apkOutputPath,
                                               String dexEntry) {
                    MainActivity.this.getDebuggingActivityControllers().openApkEditorSmaliWorkspace(apkInputPath,
                            smaliInputDirPath,
                            selectedSmaliFilePath,
                            dexOutputPath,
                            apkOutputPath,
                            dexEntry);
                }
            });
        }
        return apkEditorActivityController;
    }

    private void setupApkEditorUi() {
        getApkEditorActivityController().bind();
    }

    private void setupDebuggablePackageUi() {
        getDebuggablePackageActivityController().setup();
    }

    @Override
    public void deleteTreeQuietly(File file) {
        try {
            if (file == null || !file.exists()) return;
            if (file.isDirectory()) {
                File[] kids = file.listFiles();
                if (kids != null) {
                    for (File kid : kids) deleteTreeQuietly(kid);
                }
            }
            try { file.delete(); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    private CmdResult ensureBundledSupportAssetPublic(String assetPath, String publicPath) {
        try {
            if (TextUtils.isEmpty(assetPath) || TextUtils.isEmpty(publicPath)) {
                return new CmdResult(1, "", "Support asset path is empty.");
            }
            File stageDir = getBundledStageDir();
            if (stageDir == null) return new CmdResult(1, "", "Unable to access staged support asset directory.");
            File stage = new File(stageDir, sanitizeFilename(new File(publicPath).getName()));
            File parent = stage.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (InputStream in = getAssets().open(assetPath); OutputStream out = new BufferedOutputStream(new FileOutputStream(stage, false))) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                out.flush();
            }
            String publicDir = new File(publicPath).getParent();
            String cmd = "mkdir -p " + shQuote(publicDir)
                    + " && cp " + shQuote(stage.getAbsolutePath()) + " " + shQuote(publicPath)
                    + " && chmod 600 " + shQuote(publicPath);
            return runShellCommandCaptureSync(cmd);
        } catch (Throwable t) {
            return new CmdResult(1, "", "Support asset install failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private PermsTestSilentUpdateInstaller getPermsTestSilentUpdateInstaller() {
        if (permsTestSilentUpdateInstaller == null) {
            permsTestSilentUpdateInstaller = new PermsTestSilentUpdateInstaller(new PermsTestSilentUpdateInstaller.Host() {
                @Override
                public boolean isReadyAndGranted() {
                    return MainActivity.this.safeIsReadyAndGranted();
                }

                @Override
                public void refreshStatus() {
                    MainActivity.this.refreshStatus();
                }

                @Override
                public void appendOutput(String text) {
                    MainActivity.this.appendOutput(text);
                }

                @Override
                public void runInstallCommand(String command, PermsTestSilentUpdateInstaller.ResultCallback callback) {
                    MainActivity.this.runShellCommandCaptureAndAppend(command, (exit, out, err) -> {
                        if (callback != null) callback.onComplete(exit, out, err);
                    });
                }
            });
        }
        return permsTestSilentUpdateInstaller;
    }

    private AboutTabController getAboutTabController() {
        if (aboutTabController == null) {
            aboutTabController = new AboutTabController(this, b, getPermsTestUpdateController(), new AboutTabController.Host() {
                @Override
                public void debug(String area, String stage, String message) {
                    MainActivity.this.debugOutput(area, stage, message);
                }

                @Override
                public void warn(String area, String stage, String message) {
                    MainActivity.this.debugOutputWarn(area, stage, message);
                }

                @Override
                public void appendOutput(String text) {
                    MainActivity.this.appendOutput(text);
                }
            });
        }
        return aboutTabController;
    }

private void setupAboutTab() {
        getAboutTabController().setup();
    }

    private void showManageBundledBinsDialog() {
        getShellActivityControllers().showManageBundledBinsDialog();
    }

    private PermissionDropdownController getPermissionDropdownController() {
        if (permissionDropdownController == null) {
            permissionDropdownController = new PermissionDropdownController(
                    this,
                    b,
                    getSharedPreferences(PREFS, MODE_PRIVATE),
                    this::ioExecute,
                    KEY_USE_APP_PERMS_IN_DROPDOWN,
                    new PermissionDropdownController.Host() {
                        @Override
                        public void configureSafeDropdownEndIcon(com.google.android.material.textfield.TextInputLayout layout, Runnable onClick) {
                            MainActivity.this.configureSafeDropdownEndIcon(layout, onClick);
                        }

                        @Override
                        public void configureTapOnlyDropdownField(AutoCompleteTextView view, int touchSlop, int maxTapMs, Runnable onTap) {
                            MainActivity.this.configureTapOnlyDropdownField(view, touchSlop, maxTapMs, onTap);
                        }

                        @Override
                        public void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText) {
                            MainActivity.this.showDropdownAtLastSelection(view, lastText);
                        }

                        @Override
                        public void preservePackagesScrollPosition(Runnable action) {
                            MainActivity.this.preservePackagesScrollPosition(action);
                        }

                        @Override
                        public void setPackagesSpinnerVisible(boolean visible) {
                            MainActivity.this.setPackagesSpinnerVisible(visible);
                        }

                        @Override
                        public boolean isSafeToken(String value) {
                            return MainActivity.this.isSafeToken(value);
                        }
                    });
        }
        return permissionDropdownController;
    }


    private void setupColorizeAppDropdownToggle() {
        if (b.tabSettings.chkColorizeAppDropdown == null) return;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean def = sp.getBoolean(KEY_COLORIZE_APP_DROPDOWN, true);
        colorizeAppDropdown = def;
        b.tabSettings.chkColorizeAppDropdown.setChecked(def);

        b.tabSettings.chkColorizeAppDropdown.setOnCheckedChangeListener((buttonView, isChecked) -> {
            colorizeAppDropdown = isChecked;
            try {
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_COLORIZE_APP_DROPDOWN, isChecked)
                        .apply();
            } catch (Throwable ignored) {
            }
            if (packagesUiActivityController != null) {
                packagesUiActivityController.setColorizeAppDropdown(isChecked);
            }
        });
    }



    private BackendStatusController getBackendStatusController() {
        if (backendStatusController == null) {
            backendStatusController = new BackendStatusController(this, b, new BackendStatusController.Host() {
                @Override
                public SharedPreferences prefs() {
                    try {
                        if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                    } catch (Throwable ignored) {
                    }
                    return prefs;
                }

                @Override
                public ExecMode getExecMode() {
                    return execMode;
                }

                @Override
                public void setExecMode(ExecMode mode) {
                    execMode = mode == null ? ExecMode.SHIZUKU : mode;
                }

                @Override
                public boolean isShizukuSoftDisconnected() {
                    return shizukuSoftDisconnected;
                }

                @Override
                public void setInternalShizukuEnabled(boolean enabled) {
                    internalShizukuEnabled = enabled;
                }

                @Override
                public LadbController getLadbController() {
                    return MainActivity.this.getLadbController();
                }

                @Override
                public boolean hasAcceptedShizukuBinder(boolean internalMode) {
                    return MainActivity.this.hasAcceptedShizukuBinder(internalMode);
                }

                @Override
                public SharedPreferences getInternalShizukuPrefs() {
                    return MainActivity.this.getInternalShizukuPrefs();
                }

                @Override
                public void maybeAutoInstallPendingFile(boolean binderAlive, boolean granted) {
                    MainActivity.this.maybeAutoInstallPendingFile(binderAlive, granted);
                }

                @Override
                public void applyExecModeUi() {
                    MainActivity.this.applyExecModeUi();
                }

                @Override
                public void refreshBinaryAvailabilityUi() {
                    MainActivity.this.refreshBinaryAvailabilityUi();
                }

                @Override
                public ShellBinaryController getShellBinaryController() {
                    return MainActivity.this.getShellBinaryController();
                }
            });
        }
        return backendStatusController;
    }

    private void refreshStatus() {
        getBackendStatusController().refreshStatus();
    }


    private void runDumpsysPackage() {
        if (!safeIsReadyAndGranted()) {
            refreshStatus();
            appendOutput("[!] Shizuku not ready or permission not granted.\n");
            return;
        }
        String pkg = b.tabPackages.edtTargetPkg.getText() == null ? "" : b.tabPackages.edtTargetPkg.getText().toString().trim();
        if (!isSafeToken(pkg)) {
            appendOutput("[!] Invalid package name.\n");
            return;
        }
        lastOutputTag = "dumpsys_package";
        runShellCommand("dumpsys package " + pkg);
    }

    private void runDumpsysTop() {
        if (!safeIsReadyAndGranted()) {
            refreshStatus();
            appendOutput("[!] Shizuku not ready or permission not granted.\n");
            return;
        }
        lastOutputTag = "dumpsys_activity";
        runShellCommand("dumpsys activity top");
    }

    // UI button label is "dumpsys act".
    private void runDumpsysAct() {
        runDumpsysTop();
    }

    private void runMeminfo() {
        if (!safeIsReadyAndGranted()) {
            refreshStatus();
            appendOutput("[!] Shizuku not ready or permission not granted.\n");
            return;
        }
        String pkg = b.tabPackages.edtTargetPkg.getText() == null ? "" : b.tabPackages.edtTargetPkg.getText().toString().trim();
        if (!TextUtils.isEmpty(pkg) && isSafeToken(pkg)) {
            lastOutputTag = "meminfo_" + pkg.replace(".", "_");
            runShellCommand("dumpsys meminfo " + pkg);
        } else {
            lastOutputTag = "meminfo";
            runShellCommand("dumpsys meminfo");
        }
    }



    private void ensureDefaultPrefs() {
        SettingsPreferenceDefaults.ensure(this, PREFS);
    }

    @Override
    public int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private MainActivityChromeController getChromeController() {
        if (chromeController == null) {
            chromeController = new MainActivityChromeController(this, PREFS, KEY_KEEP_BOTTOM_LOG_ABOVE_NAV_BAR);
        }
        return chromeController;
    }

    private void applyDeviceUiProfile() {
        getChromeController().applyDeviceUiProfile(b);
    }

    private void applyCollapsibleGroupboxes() {
        getChromeController().applyCollapsibleGroupboxes(b);
    }

    private void setupSettingsTab() {
        SettingsTabController.bind(b, getSharedPreferences(PREFS, MODE_PRIVATE), new SettingsTabController.Host() {
            @Override
            public int getCurrentOutputHeightPx() {
                return getOutputPaneHostController().getCurrentOutputHeightPx();
            }

            @Override
            public void applyDeviceUiProfile() {
                MainActivity.this.applyDeviceUiProfile();
            }

            @Override
            public void applySamsungDropdownFix(boolean enabled) {
                MainActivity.this.applySamsungDropdownFix(enabled);
            }

            @Override
            public void applyThemeColors() {
                ThemeColorController.applyToActivity(MainActivity.this, b == null ? null : b.getRoot());
                getOutputPaneHostController().applyResizeHandleTheme();
            }

            @Override
            public void applyBottomLogNavigationBarPadding() {
                MainActivity.this.applySystemBarPadding();
            }

            @Override
            public void applyCollapsibleGroupboxes() {
                MainActivity.this.applyCollapsibleGroupboxes();
            }

            @Override
            public void onRootFeaturesChanged(boolean enabled) {
                MainActivity.this.getLoggingActivityControllers().applyRootFeaturesGate(enabled);
            }

            @Override
            public void resolveDeviceSerial(SettingsTabController.SerialResultCallback callback) {
                if (!MainActivity.this.safeIsReadyAndGranted()) {
                    if (callback != null) callback.onComplete(-1, "", "Backend not ready");
                    return;
                }
                MainActivity.this.runShellCommandCapture(
                        dev.perms.test.device.DeviceDetection.buildSerialProbeCommand(),
                        callback == null ? null : callback::onComplete);
            }
        });
        getKioskActivityController().setupKioskSettings();
        getPermsTestUpdateController().bindSettings();
    }

    private void applySamsungDropdownFix(boolean enabled) {
        getChromeController().applySamsungDropdownFix(b, enabled, () -> getPermissionDropdownController().updateHint());
    }



    private void maybeShowMissingInstalledShizukuPrompt() {
        try {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            ExecMode modeNow = ExecMode.get(prefs);
            if (modeNow != ExecMode.SHIZUKU) return;

            if (isShizukuPackageInstalled()) {
                prefs.edit().remove(KEY_MISSING_SHIZUKU_PROMPT_SHOWN).apply();
                return;
            }

            if (prefs.getBoolean(KEY_MISSING_SHIZUKU_PROMPT_SHOWN, false)) return;
            prefs.edit().putBoolean(KEY_MISSING_SHIZUKU_PROMPT_SHOWN, true).apply();

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Shizuku is not installed")
                    .setMessage("Installed Shizuku mode needs the Shizuku app. Download the latest Shizuku APK, or choose another execution mode from the Main tab.")
                    .setNegativeButton("Later", null)
                    .setPositiveButton("Download Shizuku", (d, w) -> openShizukuDownload())
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void maybeShowExperimentalExecModeWarning(ExecMode mode) {
        try {
            if (mode == null) return;
            final boolean internal = mode == ExecMode.INTERNAL_SHIZUKU;
            final boolean ladb = mode == ExecMode.LADB;
            if (!internal && !ladb) return;

            final String title = internal ? "Internal Shizuku requirements" : "LADB requirements";
            final String message = internal
                    ? "Internal Shizuku can run without the installed Shizuku app. Verify pairing, server status, and binder readiness before using privileged actions."
                    : "LADB uses local Wireless Debugging ADB. Pair or reconnect when Android changes the wireless debugging endpoint.";

            new MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setNegativeButton("Switch to Shizuku", (d, w) -> switchExecModeToInstalledShizuku())
                    .setPositiveButton("Continue", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void switchExecModeToInstalledShizuku() {
        try {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            ExecMode oldMode = execMode == null ? ExecMode.get(prefs) : execMode;
            ExecMode.set(prefs, ExecMode.SHIZUKU);
            execMode = ExecMode.SHIZUKU;
            internalShizukuEnabled = false;
            shizukuSoftDisconnected = false;
            PermsTestApp.applyShizukuRuntimeConfig(this, ExecMode.SHIZUKU);
            debugExecModeState("before-restore-installed", ExecMode.SHIZUKU);
            restoreInstalledShizukuBinderAfterModeSwitch(oldMode);
            debugExecModeState("after-restore-installed", ExecMode.SHIZUKU);
            try { b.tabMain.includeExecModeSelector.ddExecMode.setText(ExecMode.SHIZUKU.displayName(), false); } catch (Throwable ignored) {}
            appendOutput("[shizuku] Switched back to installed Shizuku mode.\n");
            applyExecModeUi();
            refreshStatus();
            mainHandler.postDelayed(() -> {
                try {
                    restoreInstalledShizukuBinderAfterModeSwitch(ExecMode.SHIZUKU);
                    debugExecModeState("delayed-installed-refresh", ExecMode.SHIZUKU);
                    applyExecModeUi();
                    refreshStatus();
                } catch (Throwable ignored) {}
            }, 700L);
            maybeShowMissingInstalledShizukuPrompt();
        } catch (Throwable ignored) {
        }
    }

    private void restoreInstalledShizukuBinderAfterModeSwitch(ExecMode previousMode) {
        try {
            debugExecModeState("restore-installed-enter", ExecMode.SHIZUKU);
            try { ShizukuProvider.clearActiveEmbeddedBinder(getPackageName()); } catch (Throwable ignored) {}
            boolean restored = false;
            try { restored = ShizukuProvider.restoreCachedBinder(this, false); } catch (Throwable ignored) {}
            debugOutput("exec-mode", "restore-installed", "restoredExternal=" + restored + " previous=" + (previousMode == null ? "null" : previousMode.name()));
            try { ShizukuProvider.requestBinderForNonProviderProcess(this); } catch (Throwable ignored) {}
            debugExecModeState("restore-installed-exit", ExecMode.SHIZUKU);
        } catch (Throwable ignored) {
        }
    }

    private void setupExecModeUi() {
        try {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            execMode = ExecMode.get(prefs);

            // Non-filtering adapter: always show all modes even when the field already contains
            // a selected value (MaterialAutoCompleteTextView normally filters by current text).
            ArrayAdapter<String> adapter = new NoFilterArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    new String[]{ExecMode.SHIZUKU.displayName(), ExecMode.INTERNAL_SHIZUKU.displayName(), ExecMode.SYSTEM.displayName(), ExecMode.LADB.displayName()}
            );
            b.tabMain.includeExecModeSelector.ddExecMode.setAdapter(adapter);
            try {
                final AutoCompleteTextView tv = b.tabMain.includeExecModeSelector.ddExecMode;
                DropdownUi.bindTapOnlyExposedDropdown(
                        this,
                        b.tabMain.includeExecModeSelector.tilExecMode,
                        tv,
                        ViewConfiguration.get(this).getScaledTouchSlop(),
                        300,
                        () -> showDropdownAtLastSelection(tv, tv.getText() == null ? "" : tv.getText().toString()));
            } catch (Throwable ignored) { }

            // Set initial display value without triggering selection logic.
            b.tabMain.includeExecModeSelector.ddExecMode.setText(execMode.displayName(), false);

            b.tabMain.includeExecModeSelector.ddExecMode.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    String sel = null;
                    try { sel = (String) parent.getItemAtPosition(position); } catch (Throwable ignored) {}
                    ExecMode mode = ExecMode.fromDisplayName(sel);
                    if (mode == null) mode = ExecMode.SHIZUKU;
                    selectExecMode(mode, true);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            // Keep app usable even if dropdown fails.
        }
    }

    private void selectExecMode(ExecMode mode, boolean showUserFeedback) {
        try {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (mode == null) mode = ExecMode.SHIZUKU;

            ExecMode previousMode = execMode;
            boolean wasInternal = previousMode != null && previousMode.isInternalShizuku();

            ExecMode.set(prefs, mode);
            execMode = mode;
            internalShizukuEnabled = mode.isInternalShizuku();
            try { b.tabMain.includeExecModeSelector.ddExecMode.setText(mode.displayName(), false); } catch (Throwable ignored) {}

            // Keep the Shizuku provider/server identity aligned with the selected backend
            // without killing the app process. This lets Internal Shizuku receive its own
            // binder instead of reusing an installed Shizuku binder that may already exist.
            PermsTestApp.applyShizukuRuntimeConfig(this, mode);
            debugExecModeState("selected", mode);

            boolean nowInternal = mode.isInternalShizuku();
            if (wasInternal != nowInternal) {
                if (nowInternal) {
                    appendOutput("[internal-shizuku] Internal Shizuku selected. Installed Shizuku binder will be ignored until the internal server is running.\n");
                    try { ShizukuProvider.restoreCachedBinder(this, true); } catch (Throwable ignored) {}
                    try { getInternalShizukuController().resetServerStateAndStartDiscovery(); } catch (Throwable ignored) {}
                } else {
                    if (mode == ExecMode.SHIZUKU) {
                        restoreInstalledShizukuBinderAfterModeSwitch(ExecMode.INTERNAL_SHIZUKU);
                        appendOutput("[shizuku] Installed Shizuku selected. Refreshing installed Shizuku status.\n");
                    } else {
                        appendOutput("[shizuku] Left Internal Shizuku mode. The internal binder will stay isolated from non-Shizuku modes.\n");
                    }
                }
            } else if (mode == ExecMode.SHIZUKU) {
                restoreInstalledShizukuBinderAfterModeSwitch(previousMode);
            }

            // Prevent backend conflicts: in non-Shizuku modes, treat Shizuku as disabled within the app.
            shizukuSoftDisconnected = !mode.isShizukuLike();
            debugExecModeState("after-soft-disconnect", mode);

            applyExecModeUi();
            if (mode == ExecMode.LADB) maybeAutoConnectLadb();
            if (showUserFeedback) {
                if (mode == ExecMode.SHIZUKU) {
                    try {
                        if (hasAcceptedShizukuBinder(false)) {
                            toast("Installed Shizuku selected");
                        } else {
                            toast("Installed Shizuku selected; waiting for binder");
                        }
                    } catch (Throwable ignored) {}
                } else if (mode == ExecMode.INTERNAL_SHIZUKU) {
                    toast("Internal Shizuku selected");
                }
            }
            refreshStatus();
            if (mode == ExecMode.SHIZUKU) {
                maybeShowMissingInstalledShizukuPrompt();
            } else {
                final ExecMode warningMode = mode;
                mainHandler.postDelayed(() -> maybeShowExperimentalExecModeWarning(warningMode), 250L);
            }
        } catch (Throwable ignored) {
        }
    }

    private void setupLadbUi() {
        getLadbController().bind();
    }

    private void applyExecModeUi() {
        try {
            getShizukuMainController().applyExecModeUi(execMode, internalShizukuEnabled, shizukuSoftDisconnected);
            getLadbController().applyExecModeUi(execMode);
        } catch (Throwable ignored) {
        }
    }

    private void setupInternalShizukuUi() {
        getInternalShizukuController().bind();
    }

    private void setupShizukuMainUi() {
        getShizukuMainController().bind();
    }


    private void maybeAutoConnectLadb() {
        getLadbController().maybeAutoConnect();
    }

    private void applyDropdownListTweaks(ListView lv) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean fat = sp.getBoolean(KEY_FAT_DROPDOWN_SCROLLBAR, true);
            DropdownUi.applyListTweaks(lv, fat, dpToPx(28), dpToPx(4));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isSafeToken(String s) {
        if (TextUtils.isEmpty(s)) return false;
        // Basic token validation: no spaces/quotes; allow a-zA-Z0-9._
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' ;
            if (!ok) return false;
        }
        return true;
    }

    private void requestShizukuPermission() {
        getShizukuMainController().requestPermission();
    }

    private void showServerInfo() {
        getShizukuMainController().showServerInfo();
    }

    


    // -------------------- Files Tab --------------------

    private FilesTabController filesTabController;

    private FilesTabController getFilesTabController() {
        if (filesTabController == null) {
            filesTabController = new FilesTabController(
                    this,
                    b,
                    io,
                    mainHandler,
                    getSharedPreferences(PREFS, MODE_PRIVATE),
                    new FilesTabController.Host() {
                        @Override
                        public int dp(int value) {
                            return MainActivity.this.dp(value);
                        }

                        @Override
                        public void appendOutput(String text) {
                            MainActivity.this.appendOutput(text);
                        }

                        @Override
                        public void toast(String message) {
                            MainActivity.this.toast(message);
                        }

                        @Override
                        public String shellQuote(String value) {
                            return MainActivity.this.shQuote(value);
                        }

                        @Override
                        public void runShellCommandCapture(String command, FilesBrowserController.ShellCallback callback) {
                            MainActivity.this.runShellCommandCapture(command, (exit, out, err) -> {
                                if (callback != null) callback.onComplete(exit, out, err);
                            });
                        }
                    });
        }
        return filesTabController;
    }

    private FilesBrowserController getFilesBrowserControllerForSavedState() {
        return filesTabController == null ? null : filesTabController.getBrowserController();
    }

    private void setupFilesTab() {
        try {
            getFilesTabController().bind(restoreFilesLeftCwd, restoreFilesRightCwd, restoreFilesSplit, restoreFilesActiveRight);
            restoreFilesLeftCwd = null;
            restoreFilesRightCwd = null;
            restoreFilesSplit = null;
            restoreFilesActiveRight = null;
        } catch (Throwable t) {
            appendOutput("[Files] setup failed: " + t + "\n");
        }
    }

    private void filesInvalidatePackageIconCaches() {
        if (filesTabController != null) filesTabController.invalidatePackageIconCaches();
    }

    private boolean filesCanUseShizuku() {
        return getFilesTabController().canUseShizuku();
    }

    private void runFilesShizukuCommandCapture(String cmd, final CaptureCallback cb) {
        getFilesTabController().runShizukuCommandCapture(cmd, (exit, out, err) -> {
            if (cb != null) cb.onComplete(exit, out, err);
        });
    }

    private interface CaptureCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    private PackageInstallRuntimeActivityController getPackageInstallRuntimeActivityController() {
        if (packageInstallRuntimeActivityController == null) {
            packageInstallRuntimeActivityController = new PackageInstallRuntimeActivityController(
                    new PackageInstallRuntimeActivityController.Host() {
                        @Override
                        public Context getContext() {
                            return MainActivity.this;
                        }

                        @Override
                        public ActivityMainBinding getBinding() {
                            return b;
                        }

                        @Override
                        public SharedPreferences getPreferences() {
                            return prefs != null ? prefs : getSharedPreferences(PREFS, MODE_PRIVATE);
                        }

                        @Override
                        public File copyUriToExternalDir(Uri uri, String subdir, String filename) {
                            return MainActivity.this.copyUriToExternalDir(uri, subdir, filename);
                        }

                        @Override
                        public void runShellCommandCaptureAndAppend(String command, PackageInstallRuntimeActivityController.ShellCallback callback) {
                            MainActivity.this.runShellCommandCaptureAndAppend(command, (exitCode, stdout, stderr) -> {
                                if (callback != null) callback.onComplete(exitCode, stdout, stderr);
                            });
                        }

                        @Override
                        public PackageInstallRuntimeActivityController.ShellResult runShellCommandCaptureBlocking(String command) {
                            CmdResult result = MainActivity.this.runShellCommandCaptureBlocking(command);
                            return new PackageInstallRuntimeActivityController.ShellResult(
                                    result == null ? -1 : result.exitCode,
                                    result == null ? "" : result.stdout,
                                    result == null ? "" : result.stderr);
                        }

                        @Override
                        public void appendOutput(String text) {
                            MainActivity.this.appendOutput(text);
                        }

                        @Override
                        public void debug(String area, String message) {
                            MainActivity.this.debugPackageInstall(area, message);
                        }
                    },
                    KEY_INSTALL_USE_ANDROID_DATA_PATH,
                    KEY_INSTALL_USE_STAGING_FOLDER,
                    KEY_INSTALL_SKIP_STAGING_LARGE_FILES,
                    KEY_INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK,
                    KEY_INSTALL_IGNORE_DEXOPT_PROFILE,
                    KEY_INSTALL_ALLOW_DOWNGRADE,
                    KEY_INSTALL_USE_INSTALLER_SCRIPT,
                    EXT_IMPORTS_DIR,
                    PUBLIC_FILES_DIR,
                    INSTALL_SKIP_STAGING_LARGE_BYTES);
        }
        return packageInstallRuntimeActivityController;
    }

    private ShellRuntimeController getShellRuntimeController() {
        return getShellActivityControllers().getRuntimeController();
    }

    private CmdResult runShellCommandCaptureBlocking(String cmd) {
        ShellActivityControllers.CommandResult result = getShellActivityControllers().runCaptureBlocking(cmd);
        return new CmdResult(result.exitCode, result.stdout, result.stderr);
    }

    private void runShellCommandCapture(String cmd, final CaptureCallback cb) {
        getShellActivityControllers().runCaptureAsync(cmd, (exit, out, err) -> {
            if (cb != null) cb.onComplete(exit, out, err);
        });
    }

    private void runShellCommand(String cmd) {
        getShellActivityControllers().runInteractiveCommand(cmd);
    }

    private void stopInteractiveShellCommand() {
        getShellActivityControllers().stopInteractiveCommand();
    }

    private void setInteractiveShellRunningUi(boolean running) {
        try { b.tabShell.btnRunCmd.setEnabled(!running); } catch (Throwable ignored) {}
        try { b.tabShell.btnStopShell.setEnabled(running); } catch (Throwable ignored) {}
    }



    private void openShizukuManager() {
        getShizukuMainController().openManager();
    }

    private void openWirelessDebuggingSettings() {
        getLadbController().openWirelessDebuggingSettings();
    }

    private boolean isShizukuReady() {
        try {
            return getShizukuMainController().isShizukuReady();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void startShizukuServer() {
        getShizukuMainController().startOrToggle();
    }

    private void openShizukuDownload() {
        getShizukuMainController().openDownload();
    }




    private void lifetimeLog(String tag, String msg) {
        getLoggingActivityControllers().logLifetime(tag, msg);
    }

    private void lifetimeLogActionForCommand(String cmd) {
        getLoggingActivityControllers().logLifetimeActionForCommand(cmd);
    }


    private OutputPaneHostController getOutputPaneHostController() {
        if (outputPaneHostController == null) {
            outputPaneHostController = new OutputPaneHostController(this, mainHandler, MAX_OUTPUT_CHARS, new OutputPaneHostController.State() {
                @Override
                public boolean isOutputDisabled() {
                    return activityDestroyed;
                }

                @Override
                public boolean shouldTruncateOutput() {
                    return shouldTruncateShellOutput();
                }
            });
        }
        if (b != null) outputPaneHostController.bind(b);
        return outputPaneHostController;
    }

    private boolean shouldTruncateShellOutput() {
        try {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            return prefs == null || prefs.getBoolean(KEY_TRUNCATE_SHELL_OUTPUT, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public void appendOutput(String msg) {
        getOutputPaneHostController().appendOutput(msg);
    }

    private void copyOutputToClipboard() {
        getOutputPaneHostController().copyOutputToClipboard();
    }

            // -------------------------
    // Custom shell commands
    // -------------------------

    // Custom command UI/persistence/dialog/import/export ownership lives in dev.perms.test.shell.

    private void setupCustomCommands() {
        getShellActivityControllers().bindCustomCommands();
    }

    private void runAndFillCommand(String cmd) {
        getShellActivityControllers().runAndFillCommand(cmd);
    }


    private String readAll(InputStream in) {
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

    private boolean hasAcceptedShizukuBinder(boolean internalMode) {
        try {
            if (internalMode) {
                if (ShizukuProvider.hasEmbeddedBinder()) return true;
                try { ShizukuProvider.restoreCachedBinder(this, true); } catch (Throwable ignored) {}
                return ShizukuProvider.hasEmbeddedBinder();
            }
            if (ShizukuProvider.hasExternalBinder()) return true;
            try { ShizukuProvider.restoreCachedBinder(this, false); } catch (Throwable ignored) {}
            return ShizukuProvider.hasExternalBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isShizukuPackageInstalled() {
        try {
            getPackageManager().getPackageInfo(ShizukuMainController.SHIZUKU_PKG, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeIsReadyAndGranted() {
        try {
            if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            execMode = ExecMode.get(prefs);
        } catch (Throwable ignored) {
            execMode = ExecMode.SHIZUKU;
        }

        if (execMode == ExecMode.SYSTEM) return true;
        if (execMode == ExecMode.LADB) return getLadbController().isConnected();
        if (execMode == ExecMode.INTERNAL_SHIZUKU) {
            boolean internalRunning = false;
            try { internalRunning = getInternalShizukuPrefs().getBoolean(PREF_KEY_INTERNAL_SHIZUKU_SERVER_RUNNING, false); } catch (Throwable ignored) {}
            return internalRunning && hasAcceptedShizukuBinder(true);
        }

        try {
            return hasAcceptedShizukuBinder(false) && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private SharedPreferences getInternalShizukuPrefs() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE | MODE_MULTI_PROCESS);
        } catch (Throwable ignored) {
            return prefs != null ? prefs : getSharedPreferences(PREFS, MODE_PRIVATE);
        }
    }

	private int dp(int dip) {
	    try {
	        return (int) TypedValue.applyDimension(
	                TypedValue.COMPLEX_UNIT_DIP,
	                (float) dip,
	                getResources().getDisplayMetrics()
	        );
	    } catch (Throwable t) {
	        return dip;
	    }
	}

	private void applySystemBarPadding() {
	    getChromeController().applySystemBarPadding(b);
	}

    private static String safeText(TextInputEditText edit) {
        try {
            if (edit == null) return "";
            android.text.Editable e = edit.getText();
            return e == null ? "" : e.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String trimOneLine(String s) {
        try {
            if (s == null) return "";
            String t = s.replace("\r", "");
            int nl = t.indexOf('\n');
            if (nl >= 0) t = t.substring(0, nl);
            t = t.trim();
            // Keep status lines compact.
            final int MAX = 200;
            if (t.length() > MAX) t = t.substring(0, MAX) + "…";
            return t;
        } catch (Throwable ignored) {
            return "";
        }
    }

private static String stackTraceToString(Throwable t) {
    try {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    } catch (Throwable ignored) {
        return String.valueOf(t);
    }
}

private void showFatalDialog(String title, Throwable t) {
    try {
        String msg = (t == null) ? "(unknown error)" : (t.getClass().getSimpleName() + ": " + t.getMessage());
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .show();
    } catch (Throwable ignored) {
    }
}

private void clearOutput() {
    getOutputPaneHostController().clearOutput();
}

    private void setupFastScrollOverlay() {
        getChromeController().attachFastScrollOverlays(b);
    }


}
