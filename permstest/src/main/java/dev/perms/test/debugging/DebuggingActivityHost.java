package dev.perms.test.debugging;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.content.SharedPreferences;
import android.widget.AutoCompleteTextView;

import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.packages.InstalledPackageExtractor;
import dev.perms.test.ui.PackageDropdownEntry;
import dev.perms.test.debugging.mitm.DebuggingMitmController;

/**
 * Activity services required by the Debugging tab controllers.
 *
 * Keep this interface in the Debugging package so new Debugging wiring does not
 * grow more MainActivity sidecar classes. MainActivity supplies these callbacks
 * while Debugging-specific controllers stay package-local to this feature.
 */
public interface DebuggingActivityHost {
    Activity getActivity();
    ActivityMainBinding getBinding();
    ExecutorService getDebugApkExecutor();
    ExecutorService getDebuggingIoExecutor();
    SharedPreferences getDebuggingPreferences();
    Handler getMainHandler();
    String getOpenSmaliEditorUriExtra();
    String getOpenSmaliEditorLabelExtra();

    String queryDisplayName(Uri uri);
    File copyUriToExternalDir(Uri uri, String subdir, String filename) throws IOException;
    void selectDebuggingTab();
    void revealSmaliEditorCard();

    ArrayList<PackageDropdownEntry> snapshotAllPackages();
    InstalledPackageExtractor.ExtractedInstalledPackage extractInstalledPackageForDebug(String packageName, String displayName) throws IOException;
    boolean isSafeToken(String token);
    void toast(String text);
    int colorGranted();
    int colorRevoked();
    boolean colorizeAppDropdown();

    int dpToPx(int dp);
    void configureSafeDropdownEndIcon(TextInputLayout layout, Runnable onClick);
    void configureTapOnlyDropdownField(AutoCompleteTextView view, int touchSlop, int maxTapMs, Runnable onTap);
    void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText);
    String getLastDebuggingDexEntryDropdownText();

    void setDebuggingBusy(boolean busy, String status);
    void finishDebuggingToolError(String label, Throwable t);
    void appendOutput(String text);
    void runOnUiThread(Runnable action);
    void runDebuggingIo(Runnable action);
    DebuggingRebuiltApkExporter.ToolResult ensureBundledDebuggingTool(String toolName);
    DebuggingRebuiltApkExporter.ToolResult runDebuggingShellCommandCapture(String command);
    DebuggingMitmController.ShellResult runMitmShellCommandCaptureSync(String command);
    File getWorkRoot(String type);
    String quoteShell(String value);
    void deleteTreeQuietly(File file);
    boolean isAppDebugOutputEnabled();
}
