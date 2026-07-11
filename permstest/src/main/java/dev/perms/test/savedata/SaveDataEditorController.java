package dev.perms.test.savedata;

import android.app.Activity;
import android.widget.Adapter;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dev.perms.test.assets.AssetDefaultsInstaller;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debug.DebugLog;
import dev.perms.test.databinding.TabToolsBinding;
import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.PackageDropdownAdapter;
import dev.perms.test.ui.PackageDropdownEntry;
import dev.perms.test.ui.PackageDropdownUi;
import dev.perms.test.vr.PermsTestVrOverlayCompat;

/**
 * Controller for Tools > Save Data Editor.
 *
 * MainActivity owns lifecycle and backend execution. This controller owns the Save Data
 * UI, config loading, profile detection, backup orchestration, and patch/copy workflow.
 */
public final class SaveDataEditorController {
    private static final String SAVE_DATA_ROOT = "/sdcard/dev.perms.test/save_data";
    private static final String PRESET_PLACEHOLDER = "Select Preset";

    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
        void runShellCommandCapture(String command, ShellCallback callback);
    }

    private final Host host;
    private SaveDataConfig currentConfig;
    private File currentConfigFile;
    private final ArrayList<PackageDropdownEntry> packageItems = new ArrayList<>();
    private PackageDropdownAdapter packageAdapter;
    private boolean packageDropdownLoading;
    private boolean showPackageDropdownWhenLoaded;
    private int packageDropdownRequestId;
    private String selectedPackageName = "";
    private final ArrayList<SaveDataPatchEngine.Match> scannedMatches = new ArrayList<>();
    private SaveDataMatchAdapter matchAdapter;
    private boolean lastMatchScanWasEnabledPresets;
    private String lastMatchScanPresetName = "";
    private final ExecutorService saveDataWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PermsTestSaveDataEditor");
        thread.setDaemon(true);
        return thread;
    });

    public SaveDataEditorController(Host host) {
        this.host = host;
    }

    public void stop() {
        try { saveDataWorker.shutdownNow(); } catch (Throwable ignored) {}
    }

    public void bind() {
        TabToolsBinding tab = tab();
        if (tab == null) return;
        try {
            bindPackageDropdown();
            DropdownUi.bindClickOnlyExposedDropdown(host.getActivity(), tab.tilSaveDataConfig, tab.ddSaveDataConfig, () -> showConfigDropdown());
            DropdownUi.bindClickOnlyExposedDropdown(host.getActivity(), tab.tilSaveDataPreset, tab.ddSaveDataPreset, () -> showPresetDropdown());
            tab.btnSaveDataLoadConfigs.setOnClickListener(v -> loadConfigs(true));
            tab.ddSaveDataConfig.setOnItemClickListener((parent, view, position, id) -> loadSelectedConfig());
            tab.ddSaveDataPreset.setOnItemClickListener((parent, view, position, id) -> refreshMatchesAfterPresetSelection());
            tab.btnSaveDataDetectPlayer.setOnClickListener(v -> detectProfileParameter(true));
            tab.btnSaveDataBackup.setOnClickListener(v -> backupCurrentSave(false, null));
            tab.btnSaveDataRestore.setOnClickListener(v -> showRestoreBackupDialog());
            tab.btnSaveDataScanMatches.setOnClickListener(v -> scanSaveDataMatches());
            tab.btnSaveDataApplyPreset.setOnClickListener(v -> applySaveData(false));
            tab.btnSaveDataApplyEnabled.setOnClickListener(v -> applySaveData(true));
            tab.chkSaveDataApplyAllInstances.setOnClickListener(v -> toggleVisibleMatches(tab.chkSaveDataApplyAllInstances.isChecked()));
            bindMatchList();
            bindConfigJsonScroller();
            clearEditorForPackageSelection();
            debug("bind", "Save Data Editor controls bound");
            status("Select a package, then load save-data configs for that package.");
        } catch (Throwable t) {
            debugWarn("bind", "failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            status("Save Data Editor bind failed: " + t.getMessage());
        }
    }

    private void bindPackageDropdown() {
        TabToolsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.ddSaveDataPackage == null) return;
        if (packageAdapter == null) {
            packageAdapter = new PackageDropdownAdapter(activity, packageItems,
                    PackageDropdownUi.ColorMode.DEBUGGABLE_HIGHLIGHT, false, 0, 0);
            tab.ddSaveDataPackage.setAdapter(packageAdapter);
        }
        DropdownUi.bindClickOnlyExposedDropdown(activity, tab.tilSaveDataPackage, tab.ddSaveDataPackage, () -> showPackageDropdown());
        tab.ddSaveDataPackage.setOnItemClickListener((parent, view, position, id) -> {
            PackageDropdownEntry entry = null;
            try { entry = packageAdapter == null ? null : packageAdapter.getItem(position); } catch (Throwable ignored) {}
            selectPackage(entry);
        });
        refreshPackageDropdownAsync(false);
    }

    private void showPackageDropdown() {
        try {
            TabToolsBinding tab = tab();
            if (tab == null || tab.ddSaveDataPackage == null) return;
            if (packageItems.isEmpty()) {
                refreshPackageDropdownAsync(true);
                return;
            }
            if (PermsTestVrOverlayCompat.isEnabled(activity())) {
                showPackageDialog();
            } else {
                showDropdownWithFastScroll(tab.ddSaveDataPackage);
            }
        } catch (Throwable ignored) {}
    }

    private void selectPackage(PackageDropdownEntry entry) {
        try {
            if (entry == null || isEmpty(entry.pkg)) return;
            selectedPackageName = entry.pkg.trim();
            debug("package", "selected package=" + selectedPackageName + ", label=" + entry.label);
            setPackageDropdownText(selectedPackageName);
            clearEditorForPackageSelection();
            loadConfigs(true);
        } catch (Throwable t) {
            debugWarn("package", "selection failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            status("Package selection failed: " + t.getMessage());
        }
    }


    private void showConfigDropdown() {
        TabToolsBinding tab = tab();
        if (tab == null || tab.ddSaveDataConfig == null) return;
        if (PermsTestVrOverlayCompat.isEnabled(activity())) {
            showStringDropdownDialog("Save-edit config", tab.ddSaveDataConfig, () -> loadSelectedConfig());
        } else {
            showDropdownWithFastScroll(tab.ddSaveDataConfig);
        }
    }

    private void showPresetDropdown() {
        TabToolsBinding tab = tab();
        if (tab == null || tab.ddSaveDataPreset == null) return;
        if (PermsTestVrOverlayCompat.isEnabled(activity())) {
            showStringDropdownDialog("Preset", tab.ddSaveDataPreset, () -> refreshMatchesAfterPresetSelection());
        } else {
            showDropdownWithFastScroll(tab.ddSaveDataPreset);
        }
    }

    private void showDropdownWithFastScroll(final AutoCompleteTextView dropdown) {
        try {
            if (dropdown == null) return;
            DropdownUi.showDropdown(dropdown, this::styleFastList);
        } catch (Throwable ignored) {
        }
    }

    private void showPackageDialog() {
        try {
            Activity activity = activity();
            if (activity == null) return;
            if (packageItems.isEmpty()) {
                refreshPackageDropdownAsync(true);
                return;
            }
            final ArrayList<PackageDropdownEntry> items = new ArrayList<>(packageItems);
            if (items.isEmpty()) {
                status("No packages are available yet.");
                return;
            }
            final PackageDropdownAdapter adapter = new PackageDropdownAdapter(activity, items,
                    PackageDropdownUi.ColorMode.DEBUGGABLE_HIGHLIGHT, false, 0, 0);
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                    .setTitle("Package")
                    .setAdapter(adapter, (d, which) -> {
                        try {
                            PackageDropdownEntry entry = adapter.getItem(which);
                            if (entry == null || isEmpty(entry.pkg)) return;
                            selectPackage(entry);
                        } catch (Throwable ignored) {
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            styleDialogList(dialog);
        } catch (Throwable t) {
            status("Package picker failed: " + t.getMessage());
        }
    }

    private void showStringDropdownDialog(String title, final AutoCompleteTextView dropdown, final Runnable afterPick) {
        try {
            Activity activity = activity();
            if (activity == null || dropdown == null) return;
            Adapter adapter = dropdown.getAdapter();
            final ArrayList<String> labels = new ArrayList<>();
            if (adapter != null) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    Object item = adapter.getItem(i);
                    if (item != null) labels.add(String.valueOf(item));
                }
            }
            if (labels.isEmpty()) {
                status("No " + title.toLowerCase(Locale.US) + " entries are available.");
                return;
            }
            final ArrayAdapter<String> dialogAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, labels);
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setAdapter(dialogAdapter, (d, which) -> {
                        if (which >= 0 && which < labels.size()) {
                            dropdown.setText(labels.get(which), false);
                            if (afterPick != null) afterPick.run();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            styleDialogList(dialog);
        } catch (Throwable t) {
            status(title + " picker failed: " + t.getMessage());
        }
    }

    private void styleDialogList(AlertDialog dialog) {
        try {
            if (dialog == null) return;
            ListView list = dialog.getListView();
            if (list != null) styleFastList(list);
        } catch (Throwable ignored) {
        }
    }

    private void applyDropdownFastScroll(AutoCompleteTextView dropdown) {
        try {
            if (dropdown == null) return;
            java.lang.reflect.Field f = AutoCompleteTextView.class.getDeclaredField("mPopup");
            f.setAccessible(true);
            Object popup = f.get(dropdown);
            if (popup == null) return;
            java.lang.reflect.Method m = popup.getClass().getMethod("getListView");
            Object lv = m.invoke(popup);
            if (lv instanceof ListView) styleFastList((ListView) lv);
        } catch (Throwable ignored) {
        }
    }

    private void styleFastList(ListView list) {
        try {
            if (list == null) return;
            list.setVerticalScrollBarEnabled(true);
            list.setFastScrollEnabled(true);
            list.setFastScrollAlwaysVisible(true);
            list.setScrollbarFadingEnabled(false);
            list.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            list.setScrollBarSize(dp(28));
        } catch (Throwable ignored) {
        }
    }

    private int dp(int value) {
        Activity activity = activity();
        float density = activity == null ? 1.0f : activity.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }

    private void refreshPackageDropdownAsync(boolean showWhenLoaded) {
        Activity activity = activity();
        if (activity == null || packageAdapter == null) return;
        if (showWhenLoaded) {
            showPackageDropdownWhenLoaded = true;
        }
        if (packageDropdownLoading) {
            if (showWhenLoaded) status("Package list is still loading...");
            return;
        }
        packageDropdownLoading = true;
        final int requestId = ++packageDropdownRequestId;
        if (showWhenLoaded) status("Loading package list...");
        saveDataWorker.execute(() -> {
            ArrayList<PackageDropdownEntry> loaded = buildPackageDropdownEntries(activity.getPackageManager());
            runOnUiThread(() -> {
                if (requestId != packageDropdownRequestId) return;
                packageDropdownLoading = false;
                packageItems.clear();
                packageItems.addAll(loaded);
                try { packageAdapter.notifyDataSetChanged(); } catch (Throwable ignored) {}
                boolean openAfterLoad = showPackageDropdownWhenLoaded;
                showPackageDropdownWhenLoaded = false;
                if (openAfterLoad) {
                    if (packageItems.isEmpty()) {
                        status("No packages are available yet.");
                    } else if (PermsTestVrOverlayCompat.isEnabled(activity())) {
                        showPackageDialog();
                    } else {
                        TabToolsBinding tab = tab();
                        if (tab != null && tab.ddSaveDataPackage != null) showDropdownWithFastScroll(tab.ddSaveDataPackage);
                    }
                }
            });
        });
    }

    private ArrayList<PackageDropdownEntry> buildPackageDropdownEntries(PackageManager pm) {
        ArrayList<PackageDropdownEntry> loaded = new ArrayList<>();
        try {
            List<ApplicationInfo> installed = pm == null ? Collections.emptyList() : pm.getInstalledApplications(PackageManager.GET_META_DATA);
            if (installed != null) {
                for (ApplicationInfo ai : installed) {
                    if (ai == null || isEmpty(ai.packageName)) continue;
                    String label = ai.packageName;
                    try {
                        CharSequence cs = pm.getApplicationLabel(ai);
                        if (cs != null && !isEmpty(cs.toString())) label = cs.toString();
                    } catch (Throwable ignored) {}
                    boolean debuggable = (ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                    loaded.add(new PackageDropdownEntry(label, ai.packageName, ai.enabled, debuggable));
                }
            }
            Collections.sort(loaded, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                    a == null || a.label == null ? "" : a.label,
                    b == null || b.label == null ? "" : b.label));
        } catch (Throwable t) {
            runOnUiThread(() -> status("Package list failed: " + t.getMessage()));
        }
        return loaded;
    }

    private void clearEditorForPackageSelection() {
        TabToolsBinding tab = tab();
        if (tab == null) return;
        try { tab.ddSaveDataConfig.setText("", false); } catch (Throwable ignored) {}
        try { tab.ddSaveDataPreset.setText("", false); } catch (Throwable ignored) {}
        try { tab.edtSaveDataPlayerId.setText(""); } catch (Throwable ignored) {}
        setProfileParameterUi(null);
        try { tab.edtSaveDataPath.setText(""); } catch (Throwable ignored) {}
        try { tab.edtSaveDataConfigJson.setText(""); } catch (Throwable ignored) {}
        currentConfig = null;
        currentConfigFile = null;
        clearMatches();
        bindPresets(new ArrayList<>());
    }

    private void loadConfigs(boolean showStatus) {
        try {
            debug("config", "load configs requested package=" + selectedPackageName + ", showStatus=" + showStatus);
            TabToolsBinding tab = tab();
            Activity activity = activity();
            if (tab == null || activity == null) return;
            String pkg = packageName();
            if (isEmpty(pkg)) {
                clearConfigDropdown();
                if (showStatus) status("Select a package first.");
                return;
            }
            if (tab.chkSaveDataInstallBundled == null || tab.chkSaveDataInstallBundled.isChecked()) {
                copyBundledSaveDataConfigs(pkg);
            }
            File dir = existingConfigDir(pkg);
            ArrayList<String> names = new ArrayList<>();
            File[] files = dir.listFiles((d, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".json"));
            if (files != null) {
                for (File f : files) names.add(f.getName());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, names);
            tab.ddSaveDataConfig.setAdapter(adapter);
            if (!names.isEmpty() && isEmpty(text(tab.ddSaveDataConfig))) {
                tab.ddSaveDataConfig.setText(names.get(0), false);
                loadSelectedConfig();
            } else if (showStatus) {
                status(names.isEmpty()
                        ? "No save-data configs found for " + pkg + "."
                        : "Loaded " + names.size() + " save-data config(s) for " + pkg + ".");
            }
        } catch (Throwable t) {
            debugWarn("config", "load configs failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            status("Load configs failed: " + t.getMessage());
        }
    }


    private void clearConfigDropdown() {
        TabToolsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.ddSaveDataConfig == null) return;
        tab.ddSaveDataConfig.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, new ArrayList<String>()));
        try { tab.ddSaveDataConfig.setText("", false); } catch (Throwable ignored) {}
        bindPresets(new ArrayList<>());
    }

    private void copyBundledSaveDataConfigs(String packageName) {
        Activity activity = activity();
        if (activity == null || isEmpty(packageName)) return;
        int copied = AssetDefaultsInstaller.installBundledSaveDataConfigs(activity, packageName, false);
        if (copied > 0) {
            append("[+] Installed " + copied + " bundled save-data config(s) for " + packageName + "\n");
        }
    }

    private void loadSelectedConfig() {
        try {
            TabToolsBinding tab = tab();
            if (tab == null) return;
            String selected = text(tab.ddSaveDataConfig);
            if (isEmpty(selected)) {
                status("Select a save-data config first.");
                return;
            }
            File file = new File(existingConfigDir(packageName()), selected);
            String json = readText(file);
            debug("config", "loading config=" + file.getAbsolutePath() + ", chars=" + json.length());
            currentConfig = SaveDataConfig.parse(json);
            currentConfigFile = file;
            tab.edtSaveDataConfigJson.setText(json);
            setProfileParameterUi(currentConfig);
            if (!isEmpty(currentConfig.packageName)) {
                String resolvedPackage = resolvePackageDropdownText(currentConfig.packageName);
                if (isEmpty(selectedPackageName) || !packageMatches(selectedPackageName, resolvedPackage)) {
                    selectedPackageName = resolvedPackage;
                    setPackageDropdownText(resolvedPackage);
                }
            }
            String player = text(tab.edtSaveDataPlayerId);
            String resolved = currentConfig.resolveSavePath(player);
            if (!currentConfig.requiresProfileId() || !isEmpty(player)) {
                if (!isEmpty(resolved)) tab.edtSaveDataPath.setText(resolved);
            } else {
                tab.edtSaveDataPath.setText("");
            }
            bindPresets(currentConfig.presetNames());
            tab.chkSaveDataApplyAllInstances.setChecked(false);
            clearMatches();
            if (currentConfig.requiresProfileId() && currentConfig.autoDetectProfileId && !isEmpty(currentConfig.playerIdGlob)) {
                status("Loaded config: " + file.getName() + ". Detecting profile parameter...");
                detectProfileParameter(false);
            } else {
                status("Loaded config: " + file.getName());
            }
        } catch (Throwable t) {
            currentConfig = null;
            currentConfigFile = null;
            status("Load config failed: " + t.getMessage());
        }
    }

    private void bindPresets(List<String> names) {
        try {
            TabToolsBinding tab = tab();
            Activity activity = activity();
            if (tab == null || activity == null) return;
            ArrayList<String> entries = new ArrayList<>();
            if (names != null && !names.isEmpty()) {
                entries.add(PRESET_PLACEHOLDER);
                entries.addAll(names);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, entries);
            tab.ddSaveDataPreset.setAdapter(adapter);
            tab.ddSaveDataPreset.setText(entries.isEmpty() ? "" : PRESET_PLACEHOLDER, false);
            lastMatchScanWasEnabledPresets = false;
            lastMatchScanPresetName = "";
            clearMatches();
        } catch (Throwable ignored) {}
    }

    private void bindMatchList() {
        try {
            TabToolsBinding tab = tab();
            Activity activity = activity();
            if (tab == null || activity == null || tab.listSaveDataMatches == null) return;
            matchAdapter = new SaveDataMatchAdapter(activity);
            matchAdapter.attachListView(tab.listSaveDataMatches);
            tab.listSaveDataMatches.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            tab.listSaveDataMatches.setItemsCanFocus(false);
            tab.listSaveDataMatches.setAdapter(matchAdapter);
            tab.listSaveDataMatches.setOnItemClickListener((parent, view, position, id) -> {
                syncSelectAllCheckbox();
                if (matchAdapter != null) matchAdapter.notifyDataSetChanged();
            });
            styleFastList(tab.listSaveDataMatches);
            bindNestedListTouchGuard(tab.listSaveDataMatches);
        } catch (Throwable ignored) {}
    }

    private void bindNestedListTouchGuard(final ListView list) {
        try {
            if (list == null) return;
            try { ViewCompat.setNestedScrollingEnabled(list, true); } catch (Throwable ignored) {}
            list.setOnTouchListener((v, event) -> {
                try {
                    boolean keepListScrolling = event != null
                            && event.getActionMasked() != MotionEvent.ACTION_UP
                            && event.getActionMasked() != MotionEvent.ACTION_CANCEL;
                    ViewParent parent = v == null ? null : v.getParent();
                    while (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(keepListScrolling);
                        parent = parent.getParent();
                    }
                } catch (Throwable ignored) {
                }
                return false;
            });
        } catch (Throwable ignored) {
        }
    }

    private void bindConfigJsonScroller() {
        try {
            TabToolsBinding tab = tab();
            if (tab == null || tab.edtSaveDataConfigJson == null) return;
            tab.edtSaveDataConfigJson.setVerticalScrollBarEnabled(true);
            tab.edtSaveDataConfigJson.setScrollbarFadingEnabled(false);
            tab.edtSaveDataConfigJson.setOnTouchListener((v, event) -> {
                try {
                    if (event != null && (event.getActionMasked() == MotionEvent.ACTION_DOWN
                            || event.getActionMasked() == MotionEvent.ACTION_MOVE)) {
                        requestParentDisallowIntercept(v, true);
                    } else if (event != null && (event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                        requestParentDisallowIntercept(v, false);
                    }
                } catch (Throwable ignored) {
                }
                return false;
            });
        } catch (Throwable ignored) {
        }
    }

    private void requestParentDisallowIntercept(View view, boolean disallow) {
        try {
            android.view.ViewParent parent = view == null ? null : view.getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(disallow);
                parent = parent.getParent();
            }
        } catch (Throwable ignored) {
        }
    }

    private void clearMatches() {
        scannedMatches.clear();
        if (matchAdapter != null) {
            matchAdapter.setMatches(scannedMatches);
        }
        TabToolsBinding tab = tab();
        if (tab != null && tab.listSaveDataMatches != null) tab.listSaveDataMatches.clearChoices();
        if (tab != null && tab.chkSaveDataApplyAllInstances != null) tab.chkSaveDataApplyAllInstances.setChecked(false);
    }

    private void detectProfileParameter(boolean manual) {
        try {
            debug("profile", "detect requested manual=" + manual + ", package=" + selectedPackageName);
            SaveDataConfig config = configFromEditor();
            String glob = config == null ? "" : config.playerIdGlob;
            if (isEmpty(glob)) {
                if (manual) status("This config does not define a profile-parameter glob.");
                return;
            }
            String lower = glob.replace("/Android/data/", "/android/data/");
            String cmd = "for d in " + shellGlobPattern(glob) + " " + shellGlobPattern(lower) + "; do "
                    + "[ -d \"$d\" ] || continue; basename \"$d\"; done | head -n 1";
            status("Detecting save-data profile parameter...");
            runShell(cmd, (code, out, err) -> {
                String id = firstLine(out);
                if (code == 0 && !isEmpty(id)) {
                    TabToolsBinding tab = tab();
                    if (tab != null) {
                        tab.edtSaveDataPlayerId.setText(id);
                        SaveDataConfig c = configFromEditorQuiet();
                        if (c != null) tab.edtSaveDataPath.setText(c.resolveSavePath(id));
                    }
                    status("Detected profile parameter: " + id);
                } else {
                    if (!manual) append("[i] Save Data profile auto-detect did not find a profile for glob: " + glob + "\n");
                    status((manual ? "No profile parameter found. " : "Profile parameter was not auto-detected. ") + trim(err));
                }
            });
        } catch (Throwable t) {
            debugWarn("profile", "detect failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            if (manual) status("Detect failed: " + t.getMessage());
        }
    }

    private void backupCurrentSave(boolean fromApply, Runnable afterBackup) {
        try {
            String source = savePathForCurrentUi();
            debug("backup", "requested fromApply=" + fromApply + ", source=" + source);
            if (isEmpty(source)) {
                status("Save path is empty.");
                return;
            }
            TabToolsBinding tab = tab();
            String player = tab == null ? "" : text(tab.edtSaveDataPlayerId);
            status("Backing up save data...");
            SaveDataBackupManager.backup(this::runShell, SAVE_DATA_ROOT, packageName(), player, source, (success, message) -> {
                if (success) {
                    status((fromApply ? "Backup complete; applying edits..." : "Backup complete: ") + message);
                    append("[+] Save backup: " + message + "\n");
                    if (afterBackup != null) afterBackup.run();
                } else {
                    status("Backup failed: " + trim(message));
                }
            });
        } catch (Throwable t) {
            debugWarn("backup", "failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            status("Backup failed: " + t.getMessage());
        }
    }

    private void scanSaveDataMatches() {
        scanSaveDataMatches(false);
    }

    private void scanSaveDataMatches(boolean applyEnabledPresets) {
        scanSaveDataMatches(applyEnabledPresets, null);
    }

    private void scanSaveDataMatches(boolean applyEnabledPresets, String presetOverride) {
        try {
            debug("matches", "scan requested applyEnabled=" + applyEnabledPresets + ", presetOverride=" + presetOverride);
            SaveDataConfig config = configFromEditor();
            TabToolsBinding tab = tab();
            if (config == null || tab == null) return;
            String source = savePathForCurrentUi();
            if (isEmpty(source)) {
                status("Save path is empty.");
                return;
            }
            String resolved = config.resolveSavePath(text(tab.edtSaveDataPlayerId));
            if (!tab.chkSaveDataAllowAnyFile.isChecked() && !pathsEqual(source, resolved)) {
                status("Save path differs from the loaded config. Enable 'Allow any file' to override it.");
                return;
            }
            final String requestedPreset = applyEnabledPresets ? null : selectedPresetForScan(presetOverride);
            if (!applyEnabledPresets && isEmpty(requestedPreset)) {
                clearMatches();
                status("Select a preset first.");
                return;
            }
            File workDir = new File(configDir(packageName()), "work");
            if (!workDir.exists() && !workDir.mkdirs()) {
                status("Unable to create work folder: " + workDir.getAbsolutePath());
                return;
            }
            File local = new File(workDir, localWorkName(source));
            String copyIn = "mkdir -p " + shQuote(workDir.getAbsolutePath())
                    + " && cp -f " + shQuote(source) + " " + shQuote(local.getAbsolutePath())
                    + " && chmod 644 " + shQuote(local.getAbsolutePath());
            status("Copying save data for match scan...");
            runShell(copyIn, (code, out, err) -> {
                if (code != 0) {
                    status("Copy failed: " + trim(err));
                    return;
                }
                final String preset = requestedPreset;
                lastMatchScanWasEnabledPresets = applyEnabledPresets;
                lastMatchScanPresetName = applyEnabledPresets ? "" : preset;
                if (!applyEnabledPresets) setPresetDropdownText(preset);
                status("Scanning save-data matches...");
                saveDataWorker.execute(() -> {
                    try {
                        List<SaveDataPatchEngine.Match> matches = SaveDataPatchEngine.scan(local, config, preset, applyEnabledPresets);
                        int alreadyPatched = countAlreadyPatched(matches);
                        int unpatched = Math.max(0, matches.size() - alreadyPatched);
                        debug("matches", "scan complete total=" + matches.size() + ", unpatched=" + unpatched + ", alreadyPatched=" + alreadyPatched);
                        runOnUiThread(() -> {
                            bindMatches(matches);
                            if (matches.isEmpty()) {
                                status(applyEnabledPresets
                                        ? "Found 0 save-data match(es) for enabled presets."
                                        : "Found 0 save-data match(es) for the selected preset.");
                            } else {
                                status("Found " + unpatched + " unpatched and " + alreadyPatched + " already-patched save-data match(es)."
                                        + (applyEnabledPresets ? " Enabled presets were scanned." : "")
                                        + " Select rows, or use Select All before applying.");
                            }
                        });
                    } catch (Throwable t) {
                        debugWarn("matches", "scan worker failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                        runOnUiThread(() -> status("Match scan failed: " + t.getMessage()));
                    }
                });
            });
        } catch (Throwable t) {
            debugWarn("matches", "scan failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            status("Match scan failed: " + t.getMessage());
        }
    }

    private int countAlreadyPatched(List<SaveDataPatchEngine.Match> matches) {
        int count = 0;
        if (matches == null) return 0;
        for (SaveDataPatchEngine.Match match : matches) {
            if (match != null && match.alreadyPatched) count++;
        }
        return count;
    }

    private void bindMatches(List<SaveDataPatchEngine.Match> matches) {
        clearMatches();
        if (matches != null) scannedMatches.addAll(matches);
        if (matchAdapter == null) bindMatchList();
        if (matchAdapter != null) {
            matchAdapter.setMatches(scannedMatches);
        }
    }

    private void applySaveData(boolean applyEnabledPresets) {
        try {
            debug("apply", "requested applyEnabled=" + applyEnabledPresets);
            TabToolsBinding tab = tab();
            if (tab == null) return;
            if (tab.chkSaveDataAutoBackup.isChecked()) {
                backupCurrentSave(true, () -> applySaveDataAfterBackup(applyEnabledPresets));
            } else {
                applySaveDataAfterBackup(applyEnabledPresets);
            }
        } catch (Throwable t) {
            debugWarn("apply", "failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            status("Apply failed: " + t.getMessage());
        }
    }

    private void applySaveDataAfterBackup(boolean applyEnabledPresets) {
        try {
            SaveDataConfig config = configFromEditor();
            TabToolsBinding tab = tab();
            if (config == null || tab == null) return;
            String source = savePathForCurrentUi();
            if (isEmpty(source)) {
                status("Save path is empty.");
                return;
            }
            String resolved = config.resolveSavePath(text(tab.edtSaveDataPlayerId));
            if (!tab.chkSaveDataAllowAnyFile.isChecked() && !pathsEqual(source, resolved)) {
                status("Save path differs from the loaded config. Enable 'Allow any file' to override it.");
                return;
            }
            final String preset = applyEnabledPresets ? null : selectedPresetForScan(null);
            if (!applyEnabledPresets && isEmpty(preset)) {
                status("Select a preset first.");
                return;
            }
            if (!applyEnabledPresets) setPresetDropdownText(preset);

            File workDir = new File(configDir(packageName()), "work");
            if (!workDir.exists() && !workDir.mkdirs()) {
                status("Unable to create work folder: " + workDir.getAbsolutePath());
                return;
            }
            File local = new File(workDir, localWorkName(source));
            String copyIn = "mkdir -p " + shQuote(workDir.getAbsolutePath())
                    + " && cp -f " + shQuote(source) + " " + shQuote(local.getAbsolutePath())
                    + " && chmod 644 " + shQuote(local.getAbsolutePath());
            status("Copying save data for patching...");
            runShell(copyIn, (code, out, err) -> {
                if (code != 0) {
                    status("Copy failed: " + trim(err));
                    return;
                }
                final String refreshPreset = preset;
                final boolean autoRefresh = isAutoRefreshEnabled();
                final boolean applyAll = applyEnabledPresets;
                final List<String> selectedIds = applyEnabledPresets ? null : selectedMatchIds();
                if (!applyEnabledPresets && selectedIds.isEmpty()) {
                    status("Select one or more save-data matches, or use Select All.");
                    return;
                }
                status(applyEnabledPresets ? "Applying enabled save-data presets..." : "Applying selected save-data match(es)...");
                saveDataWorker.execute(() -> {
                    try {
                        SaveDataPatchEngine.Result result = SaveDataPatchEngine.apply(local, config, preset, applyEnabledPresets, applyAll, selectedIds);
                        runOnUiThread(() -> handlePatchResult(source, local, result, applyEnabledPresets, refreshPreset, autoRefresh));
                    } catch (Throwable t) {
                        runOnUiThread(() -> status("Patch failed: " + t.getMessage()));
                    }
                });
            });
        } catch (Throwable t) {
            status("Apply failed: " + t.getMessage());
        }
    }

    private void handlePatchResult(String source,
                                   File local,
                                   SaveDataPatchEngine.Result result,
                                   boolean applyEnabledPresets,
                                   String refreshPreset,
                                   boolean autoRefresh) {
        debug("apply", "patch result=" + (result == null ? "null" : ("changes=" + result.changes + ", alreadyPatched=" + result.alreadyPatched + ", detailLen=" + (result.detail == null ? 0 : result.detail.length()))) + ", source=" + source + ", local=" + (local == null ? "" : local.getAbsolutePath()));
        if (result == null) {
            status("Patch failed: no result.");
            return;
        }
        if (result.changes <= 0) {
            if (result.alreadyPatched > 0) {
                status("Selected save-data match(es) are already patched. Restore a backup to revert them.");
            } else if (result.detail != null && result.detail.contains("No save-data instances were selected")) {
                status("Selected save-data matches are no longer available. Run Find Matches again, or enable Select All.");
            } else if (result.detail != null && result.detail.contains("No matching save-data instances")) {
                status("No save-data matches were found for the selected edit(s).");
            } else {
                status("No save edits were applied. Check preset, section markers, and JSON patterns.");
            }
            append("[i] Save Data patch result: no changes.\n" + result.detail);
            if (autoRefresh) {
                refreshMatchesAfterSaveChange(applyEnabledPresets, refreshPreset, "No save-data changes were applied. Refreshing matches...");
            } else {
                clearMatches();
            }
            return;
        }
        String copyOut = "cp -f " + shQuote(local.getAbsolutePath()) + " " + shQuote(source)
                + " && chmod 644 " + shQuote(source);
        status("Writing patched save data...");
        runShell(copyOut, (code2, out2, err2) -> {
            if (code2 == 0) {
                append("[+] Save Data patched: " + source + "\n" + result.detail);
                if (autoRefresh) {
                    refreshMatchesAfterSaveChange(applyEnabledPresets, refreshPreset,
                            "Applied " + result.changes + " save-data change(s). Refreshing matches...");
                } else {
                    clearMatches();
                    status("Applied " + result.changes + " save-data change(s). Run Find Matches again to show remaining unpatched instances.");
                }
            } else {
                status("Write failed: " + trim(err2));
            }
        });
    }

    private void showRestoreBackupDialog() {
        try {
            Activity activity = activity();
            TabToolsBinding tab = tab();
            if (activity == null || tab == null) return;
            String source = savePathForCurrentUi();
            if (isEmpty(source)) {
                status("Save path is empty.");
                return;
            }
            String backupDir = SaveDataBackupManager.backupDir(SAVE_DATA_ROOT, packageName(), text(tab.edtSaveDataPlayerId));
            String cmd = "if [ -d " + shQuote(backupDir) + " ]; then find " + shQuote(backupDir)
                    + " -maxdepth 1 -type f | sort -r; fi";
            status("Loading save-data backups...");
            runShell(cmd, (code, out, err) -> {
                if (code != 0) {
                    status("Backup list failed: " + trim(err));
                    return;
                }
                ArrayList<String> backups = nonEmptyLines(out);
                if (backups.isEmpty()) {
                    status("No save-data backups found for this package/profile.");
                    return;
                }
                activity.runOnUiThread(() -> showRestoreBackupDialog(activity, backups));
            });
        } catch (Throwable t) {
            status("Restore failed: " + t.getMessage());
        }
    }

    private void showRestoreBackupDialog(Activity activity, ArrayList<String> backups) {
        try {
            if (activity == null || backups == null || backups.isEmpty()) return;
            ArrayList<String> labels = new ArrayList<>();
            for (String path : backups) labels.add(fileName(path));
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, labels);
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                    .setTitle("Restore Save Backup")
                    .setAdapter(adapter, (d, which) -> {
                        if (which >= 0 && which < backups.size()) confirmRestoreBackup(backups.get(which));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            styleDialogList(dialog);
        } catch (Throwable t) {
            status("Restore picker failed: " + t.getMessage());
        }
    }

    private void confirmRestoreBackup(String backupPath) {
        try {
            Activity activity = activity();
            String target = savePathForCurrentUi();
            if (activity == null || isEmpty(backupPath) || isEmpty(target)) return;
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Restore backup?")
                    .setMessage("Restore this backup over the current save file?\n\n" + fileName(backupPath)
                            + "\n\nTarget:\n" + target)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (dialog, which) -> restoreBackup(backupPath))
                    .show();
        } catch (Throwable t) {
            status("Restore confirm failed: " + t.getMessage());
        }
    }

    private void restoreBackup(String backupPath) {
        try {
            TabToolsBinding tab = tab();
            if (tab != null && tab.chkSaveDataAutoBackup != null && tab.chkSaveDataAutoBackup.isChecked()) {
                backupCurrentSave(false, () -> restoreBackupNow(backupPath));
            } else {
                restoreBackupNow(backupPath);
            }
        } catch (Throwable t) {
            status("Restore failed: " + t.getMessage());
        }
    }

    private void restoreBackupNow(String backupPath) {
        try {
            String target = savePathForCurrentUi();
            if (isEmpty(backupPath) || isEmpty(target)) {
                status("Restore path is empty.");
                return;
            }
            String cmd = "cp -f " + shQuote(backupPath) + " " + shQuote(target)
                    + " && chmod 644 " + shQuote(target);
            status("Restoring save-data backup...");
            runShell(cmd, (code, out, err) -> {
                if (code == 0) {
                    append("[+] Save Data restored: " + backupPath + " -> " + target + "\n");
                    if (isAutoRefreshEnabled()) {
                        refreshMatchesAfterSaveChange(lastMatchScanWasEnabledPresets, lastMatchScanPresetName,
                                "Restored save-data backup. Refreshing matches...");
                    } else {
                        clearMatches();
                        status("Restored save-data backup: " + fileName(backupPath));
                    }
                } else {
                    status("Restore failed: " + trim(err));
                }
            });
        } catch (Throwable t) {
            status("Restore failed: " + t.getMessage());
        }
    }


    private void refreshMatchesAfterPresetSelection() {
        lastMatchScanWasEnabledPresets = false;
        lastMatchScanPresetName = selectedPresetForScan(null);
        if (isEmpty(lastMatchScanPresetName)) {
            clearMatches();
            status("Select a preset first.");
            return;
        }
        if (isAutoRefreshEnabled()) {
            refreshMatchesAfterSaveChange(false, lastMatchScanPresetName, "Refreshing matches for selected preset...");
        } else {
            clearMatches();
        }
    }

    private void refreshMatchesAfterSaveChange(boolean applyEnabledPresets, String presetName, String message) {
        runOnUiThread(() -> {
            String preset = applyEnabledPresets ? "" : selectedPresetForScan(presetName);
            if (!applyEnabledPresets && isEmpty(preset)) {
                clearMatches();
                status("Select a preset first.");
                return;
            }
            if (!isEmpty(message)) status(message);
            scanSaveDataMatches(applyEnabledPresets, preset);
        });
    }

    private String selectedPresetForScan(String presetOverride) {
        String preset = presetOverride == null ? "" : presetOverride.trim();
        TabToolsBinding tab = tab();
        if (isEmpty(preset)) {
            preset = tab == null ? "" : text(tab.ddSaveDataPreset);
        }
        preset = preset == null ? "" : preset.trim();
        return isPresetPlaceholder(preset) ? "" : preset;
    }

    private boolean isPresetPlaceholder(String preset) {
        return preset != null && PRESET_PLACEHOLDER.equalsIgnoreCase(preset.trim());
    }

    private void setPresetDropdownText(String presetName) {
        try {
            TabToolsBinding tab = tab();
            if (tab == null || tab.ddSaveDataPreset == null || isEmpty(presetName)) return;
            tab.ddSaveDataPreset.setText(presetName, false);
        } catch (Throwable ignored) {
        }
    }

    private void toggleVisibleMatches(boolean checked) {
        try {
            TabToolsBinding tab = tab();
            if (tab == null || tab.listSaveDataMatches == null) return;
            for (int i = 0; i < scannedMatches.size(); i++) {
                tab.listSaveDataMatches.setItemChecked(i, checked);
            }
            if (matchAdapter != null) matchAdapter.notifyDataSetChanged();
        } catch (Throwable ignored) {
        }
    }

    private void syncSelectAllCheckbox() {
        try {
            TabToolsBinding tab = tab();
            if (tab == null || tab.chkSaveDataApplyAllInstances == null || tab.listSaveDataMatches == null) return;
            boolean allChecked = !scannedMatches.isEmpty();
            for (int i = 0; i < scannedMatches.size(); i++) {
                if (!tab.listSaveDataMatches.isItemChecked(i)) {
                    allChecked = false;
                    break;
                }
            }
            tab.chkSaveDataApplyAllInstances.setChecked(allChecked);
        } catch (Throwable ignored) {
        }
    }

    private boolean isAutoRefreshEnabled() {
        TabToolsBinding tab = tab();
        return tab == null || tab.chkSaveDataAutoRefresh == null || tab.chkSaveDataAutoRefresh.isChecked();
    }

    private List<String> selectedMatchIds() {
        ArrayList<String> ids = new ArrayList<>();
        TabToolsBinding tab = tab();
        if (tab == null || tab.listSaveDataMatches == null) return ids;
        boolean selectAll = tab.chkSaveDataApplyAllInstances != null && tab.chkSaveDataApplyAllInstances.isChecked();
        SparseBooleanArray checked = tab.listSaveDataMatches.getCheckedItemPositions();
        for (int i = 0; i < scannedMatches.size(); i++) {
            if (selectAll || (checked != null && checked.get(i))) {
                SaveDataPatchEngine.Match match = scannedMatches.get(i);
                if (match != null && !isEmpty(match.id)) ids.add(match.id);
            }
        }
        return ids;
    }

    private void setProfileParameterUi(SaveDataConfig config) {
        TabToolsBinding tab = tab();
        if (tab == null || tab.rowSaveDataProfileId == null) return;
        boolean show = config != null && config.requiresProfileId() && config.showProfileField;
        tab.rowSaveDataProfileId.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        if (config != null && tab.tilSaveDataProfileId != null && !isEmpty(config.profileParameterName)) {
            tab.tilSaveDataProfileId.setHint(config.profileParameterName);
        }
    }

    private SaveDataConfig configFromEditor() throws Exception {
        TabToolsBinding tab = tab();
        if (tab == null) throw new IllegalStateException("Tools tab is not bound.");
        String json = text(tab.edtSaveDataConfigJson);
        if (isEmpty(json) && currentConfigFile != null) json = readText(currentConfigFile);
        SaveDataConfig parsed = SaveDataConfig.parse(json);
        currentConfig = parsed;
        return parsed;
    }

    private SaveDataConfig configFromEditorQuiet() {
        try { return configFromEditor(); } catch (Throwable ignored) { return currentConfig; }
    }

    private String savePathForCurrentUi() {
        TabToolsBinding tab = tab();
        if (tab == null) return "";
        String path = text(tab.edtSaveDataPath);
        if (isEmpty(path)) {
            SaveDataConfig config = configFromEditorQuiet();
            if (config != null) path = config.resolveSavePath(text(tab.edtSaveDataPlayerId));
        }
        return path == null ? "" : path.trim();
    }

    private String packageName() {
        TabToolsBinding tab = tab();
        String typed = tab == null ? "" : text(tab.ddSaveDataPackage);
        String resolved = resolvePackageDropdownText(typed);
        if (!isEmpty(resolved)) selectedPackageName = resolved;
        if (isEmpty(selectedPackageName) && !isEmpty(typed)) selectedPackageName = typed.trim();
        return isEmpty(selectedPackageName) ? "" : selectedPackageName.trim();
    }

    private String resolvePackageDropdownText(String text) {
        String value = text == null ? "" : text.trim();
        if (isEmpty(value)) return "";
        for (PackageDropdownEntry entry : packageItems) {
            if (entry == null || isEmpty(entry.pkg)) continue;
            if (value.equals(entry.pkg) || value.equals(entry.label) || value.equals(entry.toString())
                    || value.equalsIgnoreCase(entry.pkg)) return entry.pkg;
        }
        return value;
    }

    private boolean packageMatches(String a, String b) {
        if (isEmpty(a) || isEmpty(b)) return false;
        return a.trim().equals(b.trim()) || a.trim().equalsIgnoreCase(b.trim());
    }

    private void setPackageDropdownText(String packageName) {
        TabToolsBinding tab = tab();
        if (tab == null || tab.ddSaveDataPackage == null || isEmpty(packageName)) return;
        for (PackageDropdownEntry entry : packageItems) {
            if (entry != null && (packageName.equals(entry.pkg) || packageName.equalsIgnoreCase(entry.pkg))) {
                tab.ddSaveDataPackage.setText(entry.toString(), false);
                return;
            }
        }
        tab.ddSaveDataPackage.setText(packageName, false);
    }

    private File configDir(String packageName) {
        return new File(SAVE_DATA_ROOT, isEmpty(packageName) ? "_unselected" : packageName);
    }

    private File existingConfigDir(String packageName) {
        File exact = configDir(packageName);
        if (isEmpty(packageName)) return exact;
        if (hasJsonConfig(exact)) return exact;
        try {
            File root = new File(SAVE_DATA_ROOT);
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir != null && packageName.equalsIgnoreCase(dir.getName()) && hasJsonConfig(dir)) return dir;
                }
            }
        } catch (Throwable ignored) {
        }
        return exact;
    }

    private boolean hasJsonConfig(File dir) {
        try {
            if (dir == null || !dir.isDirectory()) return false;
            File[] files = dir.listFiles((d, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".json"));
            return files != null && files.length > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String localWorkName(String path) {
        if (path == null) return "save_data.bin";
        String name = path.substring(path.lastIndexOf('/') + 1);
        return isEmpty(name) ? "save_data.bin" : name;
    }

    private static boolean pathsEqual(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equals(b.trim());
    }

    private TabToolsBinding tab() {
        ActivityMainBinding binding = host == null ? null : host.getBinding();
        return binding == null ? null : binding.tabTools;
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private void runShell(String command, ShellCallback callback) {
        if (host != null) host.runShellCommandCapture(command, callback);
    }

    private void runOnUiThread(Runnable action) {
        if (action == null) return;
        Activity activity = activity();
        if (activity != null) {
            activity.runOnUiThread(action);
        } else {
            action.run();
        }
    }

    private void append(String message) {
        if (host != null) host.appendOutput(message);
    }

    private boolean isDebugOutputEnabled() {
        try {
            Activity activity = activity();
            return activity != null
                    && activity.getSharedPreferences(SettingsPreferenceKeys.PREFS, android.content.Context.MODE_PRIVATE)
                    .getBoolean(SettingsPreferenceKeys.DEBUG_OUTPUT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void debug(String area, String message) {
        if (!isDebugOutputEnabled()) return;
        DebugLog.log(DebugLog.DEFAULT_TAG, "savedata", area, message);
        append(DebugLog.line("savedata", area, message) + "\n");
    }

    private void debugWarn(String area, String message) {
        if (!isDebugOutputEnabled()) return;
        DebugLog.warn(DebugLog.DEFAULT_TAG, "savedata", area, message);
        append(DebugLog.line("savedata", area, message) + "\n");
    }

    private void status(String message) {
        TabToolsBinding tab = tab();
        if (tab != null && tab.txtSaveDataStatus != null) tab.txtSaveDataStatus.setText(message == null ? "" : message);
    }

    private static String text(android.widget.TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }

    private static boolean isEmpty(String value) {
        return TextUtils.isEmpty(value == null ? null : value.trim());
    }


    private static ArrayList<String> nonEmptyLines(String value) {
        ArrayList<String> lines = new ArrayList<>();
        if (value == null) return lines;
        for (String line : value.replace("\r", "").split("\n")) {
            if (!isEmpty(line)) lines.add(line.trim());
        }
        return lines;
    }

    private static String fileName(String path) {
        if (path == null) return "";
        String clean = path.trim();
        int slash = clean.lastIndexOf('/');
        return slash >= 0 && slash + 1 < clean.length() ? clean.substring(slash + 1) : clean;
    }

    private static String firstLine(String value) {
        if (value == null) return "";
        String[] lines = value.replace("\r", "").split("\n");
        for (String line : lines) {
            if (!isEmpty(line)) return line.trim();
        }
        return "";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String shellGlobPattern(String pattern) {
        if (pattern == null || pattern.length() == 0) return "''";
        StringBuilder out = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' || c == '?' || c == '[' || c == ']') {
                if (literal.length() > 0) {
                    out.append(shQuote(literal.toString()));
                    literal.setLength(0);
                }
                out.append(c);
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) out.append(shQuote(literal.toString()));
        return out.length() == 0 ? "''" : out.toString();
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String readText(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
