package dev.perms.test.debugging;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.ViewConfiguration;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.apk.BinaryXmlDebuggablePatcher;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.packages.InstalledPackageExtractor;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.PackageDropdownAdapter;
import dev.perms.test.ui.PackageDropdownEntry;
import dev.perms.test.ui.PackageDropdownUi;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;

/**
 * Owns Debugging-tab APK source selection and installed-package source wiring.
 *
 * MainActivity still coordinates shared shell/backend services, while this class keeps
 * the Debugging feature state and UI source-selection behavior out of the Activity.
 */
public final class DebuggingApkSourceController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        ExecutorService getDebugApkExecutor();
        void runIo(Runnable task);
        void runOnUiThread(Runnable action);
        String queryDisplayName(Uri uri);
        File copyUriToExternalDir(Uri uri, String subdir, String filename) throws IOException;
        ArrayList<PackageDropdownEntry> snapshotAllPackages();
        InstalledPackageExtractor.ExtractedInstalledPackage extractInstalledPackageForDebug(String packageName, String displayName) throws IOException;
        boolean isSafeToken(String token);
        void setDebuggingBusy(boolean busy, String status);
        void finishDebuggingToolError(String label, Throwable error);
        void appendOutput(String text);
        void toast(String text);
        void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText);
        String currentDexEntry();
        void refreshDexEntriesFromCurrentInput(boolean forcePathRefresh);
        void applySelectedDexEntry(String entry, boolean forcePathRefresh, boolean fromUser);
        int colorGranted();
        int colorRevoked();
        boolean colorizeAppDropdown();
    }

    private static final String DEBUG_INPUTS_DIR = "debugging_inputs";

    private final Host host;
    private ActivityResultLauncher<Intent> pickDebuggingApkLauncher;
    private String pickedDebuggingApkLabel;
    private PackageDropdownAdapter packageDropdownAdapter;
    private final ArrayList<PackageDropdownEntry> appItems = new ArrayList<>();
    private String selectedPackage;
    private String lastAppDropdownText;

    public DebuggingApkSourceController(Host host) {
        this.host = host;
    }

    public void setupApkPicker() {
        ensurePickerLauncher();
    }

    public void launchApkPicker() {
        try {
            if (pickDebuggingApkLauncher == null) setupApkPicker();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/vnd.android.package-archive",
                    "application/octet-stream",
                    "application/zip"
            });
            pickDebuggingApkLauncher.launch(intent);
        } catch (Throwable t) {
            if (host != null) host.toast("Picker failed: " + t.getMessage());
        }
    }

    private void ensurePickerLauncher() {
        if (pickDebuggingApkLauncher != null) return;
        Activity activity = activity();
        if (activity == null) return;
        if (!(activity instanceof androidx.activity.result.ActivityResultCaller)) {
            if (host != null) host.appendOutput("[Debugging] APK picker unavailable: activity does not support result launchers.\n");
            return;
        }
        pickDebuggingApkLauncher = ((androidx.activity.result.ActivityResultCaller) activity).registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handlePickedApkResult);
    }

    private void handlePickedApkResult(ActivityResult result) {
        try {
            if (result == null || result.getResultCode() != Activity.RESULT_OK) return;
            Intent data = result.getData();
            if (data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;

            pickedDebuggingApkLabel = host == null ? null : host.queryDisplayName(uri);
            if (TextUtils.isEmpty(pickedDebuggingApkLabel)) pickedDebuggingApkLabel = uri.toString();
            try {
                Activity activity = activity();
                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (activity != null && takeFlags != 0) activity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (Throwable ignored) {
            }

            if (host != null) host.setDebuggingBusy(true, "Staging selected APK...");
            ExecutorService executor = host == null ? null : host.getDebugApkExecutor();
            if (executor == null) throw new IOException("Debugging executor is unavailable.");
            executor.execute(() -> {
                try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
                try {
                    String stagedName = ApkDebugToolHelper.defaultStageFilename(uri, pickedDebuggingApkLabel);
                    File staged = host.copyUriToExternalDir(uri, DEBUG_INPUTS_DIR, stagedName);
                    if (staged == null || !staged.isFile() || staged.length() <= 0) {
                        throw new IOException("Selected APK was not staged.");
                    }
                    final String label = pickedDebuggingApkLabel;
                    host.runOnUiThread(() -> applyApkSource(staged, label, null, true));
                } catch (Throwable t) {
                    host.runOnUiThread(() -> host.finishDebuggingToolError("APK browse", t));
                }
            });
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[Debugging] APK picker failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
        }
    }

    public void setupInstalledPackageDropdown() {
        try {
            ActivityMainBinding binding = binding();
            Activity activity = activity();
            if (binding == null || binding.tabDebugging == null || binding.tabDebugging.ddDebuggingInstalledPackage == null || activity == null) return;
            ensureInstalledPackageAdapter();
            DropdownUi.bindTapOnlyExposedDropdown(
                    activity,
                    binding.tabDebugging.tilDebuggingInstalledPackage,
                    binding.tabDebugging.ddDebuggingInstalledPackage,
                    ViewConfiguration.get(activity).getScaledTouchSlop(),
                    300,
                    () -> {
                        if (host != null) host.showDropdownAtLastSelection(binding.tabDebugging.ddDebuggingInstalledPackage, lastAppDropdownText);
                    }
            );
            binding.tabDebugging.ddDebuggingInstalledPackage.setOnItemClickListener((parent, view, position, id) -> {
                PackageDropdownEntry entry = null;
                try { entry = packageDropdownAdapter == null ? null : packageDropdownAdapter.getItem(position); } catch (Throwable ignored) {}
                applySelectedInstalledPackage(entry);
            });
            refreshInstalledPackages(false);
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[Debugging] installed-app dropdown setup failed: " + t.getMessage() + "\n");
        }
    }

    public void selectInstalledPackageFromPopout(Object entryObject) {
        try {
            if (!(entryObject instanceof PackageDropdownEntry)) return;
            applySelectedInstalledPackage((PackageDropdownEntry) entryObject);
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[Debugging] popout package selection failed: " + t.getMessage() + "\n");
        }
    }

    private void applySelectedInstalledPackage(PackageDropdownEntry entry) {
        if (entry == null || TextUtils.isEmpty(entry.pkg)) return;
        selectedPackage = entry.pkg;
        lastAppDropdownText = entry.toString();
        ActivityMainBinding binding = binding();
        if (binding != null && binding.tabDebugging != null) {
            try {
                if (binding.tabDebugging.ddDebuggingInstalledPackage != null) {
                    binding.tabDebugging.ddDebuggingInstalledPackage.setText(entry.toString(), false);
                }
            } catch (Throwable ignored) {}
            try {
                if (binding.tabDebugging.txtSmaliStatus != null) {
                    binding.tabDebugging.txtSmaliStatus.setText("Selected " + entry.pkg + ". Tap Use to extract its APK.");
                }
            } catch (Throwable ignored) {}
        }
    }

    public void refreshInstalledPackages(boolean userInitiated) {
        ActivityMainBinding binding = binding();
        if (binding == null || binding.tabDebugging == null || host == null) return;
        if (userInitiated) host.setDebuggingBusy(true, "Loading installed apps...");
        host.runIo(() -> {
            ArrayList<PackageDropdownEntry> snapshot = host.snapshotAllPackages();
            ArrayList<PackageDropdownEntry> apps = DebuggingInstalledPackages.load(activityPackageManager(), snapshot);
            host.runOnUiThread(() -> {
                try {
                    appItems.clear();
                    appItems.addAll(apps);
                    ensureInstalledPackageAdapter();
                    if (packageDropdownAdapter != null) packageDropdownAdapter.notifyDataSetChanged();
                    if (userInitiated) host.setDebuggingBusy(false, "Installed apps loaded: " + apps.size());
                } catch (Throwable t) {
                    if (userInitiated) host.setDebuggingBusy(false, "Installed app load failed: " + t.getMessage());
                }
            });
        });
    }

    public void prepareSelectedInstalledPackage() {
        try {
            PackageDropdownEntry entry = findSelectedPackageDropdownEntry();
            if (entry == null || TextUtils.isEmpty(entry.pkg)) {
                if (host != null) host.toast("Choose an installed app first");
                return;
            }
            selectedPackage = entry.pkg;
            prepareInstalledPackage(entry.pkg, TextUtils.isEmpty(entry.label) ? entry.pkg : entry.label);
        } catch (Throwable t) {
            if (host != null) host.finishDebuggingToolError("installed APK", t);
        }
    }

    private PackageDropdownEntry findSelectedPackageDropdownEntry() {
        ActivityMainBinding binding = binding();
        String typed = safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.ddDebuggingInstalledPackage);
        return DebuggingInstalledPackages.findSelected(appItems, selectedPackage, typed);
    }

    private void prepareInstalledPackage(String packageName, String displayName) {
        final String pkg = packageName == null ? "" : packageName.trim();
        if (host == null || !host.isSafeToken(pkg)) {
            if (host != null) host.toast("Invalid package name");
            return;
        }
        final String label = TextUtils.isEmpty(displayName) ? pkg : displayName.trim();
        host.setDebuggingBusy(true, "Extracting installed APK...");
        host.appendOutput("[Debugging] Extracting installed APK for " + pkg + "\n");
        ExecutorService executor = host.getDebugApkExecutor();
        if (executor == null) {
            host.finishDebuggingToolError("installed APK", new IOException("Debugging executor is unavailable."));
            return;
        }
        executor.execute(() -> {
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                InstalledPackageExtractor.ExtractedInstalledPackage extracted = host.extractInstalledPackageForDebug(pkg, label);
                File sourceApk = InstalledPackageExtractor.choosePrimaryApk(extracted);
                if (sourceApk == null || !sourceApk.isFile()) {
                    throw new IOException("No usable APK was extracted for " + pkg + ".");
                }
                final File apk = sourceApk;
                final int apkCount = extracted == null ? 1 : extracted.apkCount;
                host.runOnUiThread(() -> {
                    applyApkSource(apk, label, pkg, true);
                    if (apkCount > 1) {
                        host.appendOutput("[Debugging] Extracted split package; using " + apk.getName() + " for smali work.\n");
                    }
                });
            } catch (Throwable t) {
                host.runOnUiThread(() -> host.finishDebuggingToolError("installed APK", t));
            }
        });
    }

    public void applyApkSource(File sourceApk, String sourceLabel, String packageName, boolean forceDerivedPaths) {
        try {
            if (sourceApk == null) throw new IOException("Missing APK source.");
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabDebugging == null || host == null) return;
            String resolvedPackage = resolvePackageNameForApk(sourceApk, packageName);
            selectedPackage = TextUtils.isEmpty(resolvedPackage) ? null : resolvedPackage;
            pickedDebuggingApkLabel = TextUtils.isEmpty(sourceLabel) ? sourceApk.getName() : sourceLabel;
            binding.tabDebugging.edtSmaliDexInput.setText(sourceApk.getAbsolutePath());
            String clean = DebuggingWorkPaths.workName(sourceLabel, sourceApk.getName(), resolvedPackage);
            updateDerivedPaths(clean, host.currentDexEntry(), forceDerivedPaths);
            host.setDebuggingBusy(true, "Scanning DEX entries...");
            host.appendOutput("[Debugging] APK source set: " + sourceApk.getAbsolutePath() + "\n");
            if (!TextUtils.isEmpty(resolvedPackage) && TextUtils.isEmpty(packageName)) {
                host.appendOutput("[Debugging] APK package resolved for shared smali path: " + resolvedPackage + "\n");
            }
            host.refreshDexEntriesFromCurrentInput(forceDerivedPaths);
        } catch (Throwable t) {
            if (host != null) host.finishDebuggingToolError("APK source", t);
        }
    }

    public void refreshApkOutputPath(boolean force) {
        try {
            ActivityMainBinding binding = binding();
            String input = safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.edtSmaliDexInput);
            String fallback = TextUtils.isEmpty(input) ? "package.apk" : new File(input).getName();
            updateDerivedPaths(DebuggingWorkPaths.workName(pickedDebuggingApkLabel, fallback, selectedPackage),
                    host == null ? "classes.dex" : host.currentDexEntry(), force);
        } catch (Throwable ignored) {
        }
    }

    public void setDefaults(boolean force) {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabDebugging == null) return;
            String root = PermsTestSmaliTools.DEFAULT_ROOT;
            DebuggingWorkPaths.DerivedPaths paths = DebuggingWorkPaths.derive(DebuggingWorkPaths.DEFAULT_WORK_NAME, "classes.dex", true);
            if (force || safeText(binding.tabDebugging.edtSmaliOutDir).isEmpty()) {
                binding.tabDebugging.edtSmaliOutDir.setText(paths.smaliDir);
            }
            if (force || safeText(binding.tabDebugging.edtSmaliInputDir).isEmpty()) {
                binding.tabDebugging.edtSmaliInputDir.setText(paths.smaliDir);
            }
            if (force || safeText(binding.tabDebugging.edtSmaliDexOutput).isEmpty()) {
                binding.tabDebugging.edtSmaliDexOutput.setText(paths.dexOutput);
            }
            if (force || safeText(binding.tabDebugging.edtSmaliApkOutput).isEmpty()) {
                binding.tabDebugging.edtSmaliApkOutput.setText(paths.apkOutput);
            }
            if (force || safeText(binding.tabDebugging.edtJadxJavaOutDir).isEmpty()) {
                binding.tabDebugging.edtJadxJavaOutDir.setText(PermsTestSmaliTools.DEFAULT_JAVA_ROOT + "/jadx-output");
            }
            if (force && host != null) {
                host.applySelectedDexEntry("classes.dex", true, false);
            }
            if (force || safeText(binding.tabDebugging.edtSmaliApiLevel).isEmpty()) {
                binding.tabDebugging.edtSmaliApiLevel.setText(String.valueOf(Math.max(1, Build.VERSION.SDK_INT)));
            }
            String msg = "Defaults set. Working folder: " + root;
            binding.tabDebugging.txtSmaliStatus.setText(msg);
            if (force && host != null) host.appendOutput("[Debugging] " + msg + "\n");
        } catch (Throwable ignored) {
        }
    }

    public void updateDerivedPathsForSelectedDexEntry(String dexEntry, boolean force) {
        ActivityMainBinding binding = binding();
        String input = safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.edtSmaliDexInput);
        String fallback = TextUtils.isEmpty(input) ? "package.apk" : new File(input).getName();
        updateDerivedPaths(DebuggingWorkPaths.workName(pickedDebuggingApkLabel, fallback, selectedPackage), dexEntry, force);
    }

    public String currentWorkRoot() {
        ActivityMainBinding binding = binding();
        String input = safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.edtSmaliDexInput);
        return DebuggingWorkPaths.rootForInput(pickedDebuggingApkLabel, input, selectedPackage);
    }

    public String getSelectedPackage() {
        return selectedPackage;
    }

    private void updateDerivedPaths(String cleanName, String dexEntry, boolean force) {
        ActivityMainBinding binding = binding();
        if (binding == null || binding.tabDebugging == null) return;
        boolean makeDebuggable = binding.tabDebugging.chkSmaliMakeDebugApk == null || binding.tabDebugging.chkSmaliMakeDebugApk.isChecked();
        DebuggingWorkPaths.DerivedPaths paths = DebuggingWorkPaths.derive(cleanName, dexEntry, makeDebuggable);
        String smaliOutText = safeText(binding.tabDebugging.edtSmaliOutDir);
        String smaliInputText = safeText(binding.tabDebugging.edtSmaliInputDir);
        String dexOutputText = safeText(binding.tabDebugging.edtSmaliDexOutput);
        if (force || smaliOutText.isEmpty() || smaliOutText.startsWith(PermsTestSmaliTools.DEFAULT_ROOT + "/")) {
            binding.tabDebugging.edtSmaliOutDir.setText(paths.smaliDir);
        }
        if (force || smaliInputText.isEmpty() || smaliInputText.startsWith(PermsTestSmaliTools.DEFAULT_ROOT + "/")) {
            binding.tabDebugging.edtSmaliInputDir.setText(paths.smaliDir);
        }
        if (force || dexOutputText.isEmpty() || dexOutputText.startsWith(PermsTestSmaliTools.DEFAULT_ROOT + "/")) {
            binding.tabDebugging.edtSmaliDexOutput.setText(paths.dexOutput);
        }
        if (force || safeText(binding.tabDebugging.edtSmaliApkOutput).isEmpty()
                || safeText(binding.tabDebugging.edtSmaliApkOutput).startsWith(PermsTestSmaliTools.DEFAULT_ROOT + "/")) {
            binding.tabDebugging.edtSmaliApkOutput.setText(paths.apkOutput);
        }
        try {
            String javaOutText = safeText(binding.tabDebugging.edtJadxJavaOutDir);
            if (force || javaOutText.isEmpty() || javaOutText.startsWith(PermsTestSmaliTools.DEFAULT_JAVA_ROOT + "/")) {
                binding.tabDebugging.edtJadxJavaOutDir.setText(defaultJadxJavaOutDir(cleanName));
            }
        } catch (Throwable ignored) {}
    }

    private String defaultJadxJavaOutDir(String cleanName) {
        String safe = TextUtils.isEmpty(cleanName) ? "jadx-output" : cleanName;
        return PermsTestSmaliTools.DEFAULT_JAVA_ROOT + "/" + safe;
    }

    private String resolvePackageNameForApk(File sourceApk, String suppliedPackageName) {
        if (!TextUtils.isEmpty(suppliedPackageName)) return suppliedPackageName.trim();
        try {
            if (sourceApk == null || !sourceApk.isFile()) return null;
            PackageManager pm = activityPackageManager();
            PackageInfo info = pm == null ? null : pm.getPackageArchiveInfo(sourceApk.getAbsolutePath(), PackageManager.GET_META_DATA);
            if (info != null && !TextUtils.isEmpty(info.packageName)) return info.packageName;
        } catch (Throwable ignored) {}
        try {
            return readPackageNameFromApkManifest(sourceApk);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String readPackageNameFromApkManifest(File apkFile) throws IOException {
        ZipFile zipFile = null;
        try {
            if (apkFile == null || !apkFile.isFile()) return null;
            zipFile = new ZipFile(apkFile);
            ZipEntry manifest = zipFile.getEntry("AndroidManifest.xml");
            if (manifest == null || manifest.isDirectory() || manifest.getSize() > 1024L * 1024L) return null;
            try (InputStream in = zipFile.getInputStream(manifest)) {
                byte[] data = readSmallStream(in, 1024 * 1024);
                return BinaryXmlDebuggablePatcher.getManifestPackageName(data);
            }
        } finally {
            try { if (zipFile != null) zipFile.close(); } catch (Throwable ignored) {}
        }
    }

    private static byte[] readSmallStream(InputStream in, int maxBytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[32 * 1024];
        int total = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            total += r;
            if (total > maxBytes) throw new IOException("stream is too large");
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private void ensureInstalledPackageAdapter() {
        Activity activity = activity();
        ActivityMainBinding binding = binding();
        if (activity == null || binding == null || binding.tabDebugging == null || binding.tabDebugging.ddDebuggingInstalledPackage == null) return;
        if (packageDropdownAdapter == null) {
            packageDropdownAdapter = new PackageDropdownAdapter(
                    activity,
                    appItems,
                    PackageDropdownUi.ColorMode.DEBUGGABLE_HIGHLIGHT,
                    host != null && host.colorizeAppDropdown(),
                    host == null ? 0 : host.colorGranted(),
                    host == null ? 0 : host.colorRevoked());
            binding.tabDebugging.ddDebuggingInstalledPackage.setAdapter(packageDropdownAdapter);
        }
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.getBinding();
    }

    private PackageManager activityPackageManager() {
        Activity activity = activity();
        return activity == null ? null : activity.getPackageManager();
    }

    private static String safeText(android.widget.TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }
}
