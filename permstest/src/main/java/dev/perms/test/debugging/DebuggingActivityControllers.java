package dev.perms.test.debugging;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.AutoCompleteTextView;

import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debugging.editor.SmaliEditorFileController;
import dev.perms.test.debugging.editor.SmaliEditorIntentHandler;
import dev.perms.test.debugging.editor.SmaliEditorSearch;
import dev.perms.test.debugging.editor.SmaliEditorSearchController;
import dev.perms.test.debugging.jadx.DebuggingJadxController;
import dev.perms.test.debugging.smali.DebuggingSmaliActionController;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;
import dev.perms.test.debugging.mitm.DebuggingMitmController;

/**
 * Activity-side holder for the Debugging tab controllers.
 *
 * MainActivity keeps shared app state and the existing smali/APK operations. This
 * class owns Debugging-tab controller construction and button/dropdown/intent
 * wiring so those paths can continue moving out of the Activity without changing
 * the underlying behavior.
 */
public final class DebuggingActivityControllers {
    private final DebuggingActivityHost host;
    private DebuggingApkSourceController apkSourceController;
    private DebuggingDexEntryDropdownController dexEntryDropdownController;
    private DebuggingDexEntryStateController dexEntryStateController;
    private DebuggingMitmController mitmController;
    private DebuggingJadxController jadxController;
    private SmaliEditorSearchController smaliEditorSearchController;
    private SmaliEditorFileController smaliEditorFileController;
    private DebuggingSmaliActionController smaliActionController;
    private SmaliEditorIntentHandler smaliEditorIntentHandler;
    private DebuggingToolBridge toolBridge;

    public DebuggingActivityControllers(DebuggingActivityHost host) {
        this.host = host;
    }

    public void registerActivityResults() {
        setupDebuggingApkPicker();
    }

    public void bind() {
        try {
            ActivityMainBinding binding = getBinding();
            if (binding == null || binding.tabDebugging == null) return;
            setDebuggingDefaults(false);
            setupDebuggingApkPicker();
            setupDebuggingInstalledPackageDropdown();
            getDexEntryStateController().setupDropdown();
            setupSmaliEditor();
            restoreSmaliEditorSearchJobStatus();
            setupMitmPatchControls();
            setupJadxControls();
            binding.tabDebugging.btnSmaliDefaults.setOnClickListener(v -> setDebuggingDefaults(true));
            binding.tabDebugging.btnSmaliDisassemble.setOnClickListener(v -> runSmaliDisassemble());
            binding.tabDebugging.btnSmaliAssemble.setOnClickListener(v -> runSmaliAssemble());
            binding.tabDebugging.btnSmaliReassembleApk.setOnClickListener(v -> runSmaliReassembleApk());
            binding.tabDebugging.btnSmaliReassembleAllDex.setOnClickListener(v -> runSmaliReassembleAllDex());
            binding.tabDebugging.btnSmaliListClasses.setOnClickListener(v -> runSmaliListClasses());
            binding.tabDebugging.btnSmaliDisassembleAll.setOnClickListener(v -> runSmaliDisassembleAll());
            binding.tabDebugging.btnSmaliBrowseApk.setOnClickListener(v -> launchDebuggingApkPicker());
            binding.tabDebugging.btnDebuggingRefreshInstalled.setOnClickListener(v -> refreshDebuggingInstalledPackages(true));
            binding.tabDebugging.btnDebuggingUseInstalled.setOnClickListener(v -> prepareSelectedDebuggingInstalledPackage());
            binding.tabDebugging.chkSmaliMakeDebugApk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDebuggingApkOutputPath(false));
            refreshDebuggingDexEntriesIfInputPresent(false);
            restoreSmaliDisassembleAllJobStatus();
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[Debugging] setup failed: " + t.getMessage() + "\n");
        }
    }

    public boolean handleIncomingSmaliEditorIntent(Intent intent) {
        return getSmaliEditorIntentHandler().handle(intent);
    }

    public void setupDexEntryDropdown(ArrayList<String> allItems,
                                      ArrayList<String> displayItems,
                                      String selectedEntry,
                                      String lastDropdownText) {
        ActivityMainBinding binding = getBinding();
        if (binding == null || binding.tabDebugging == null) return;
        getDexEntryDropdownController().setup(binding.tabDebugging,
                allItems,
                displayItems,
                selectedEntry,
                lastDropdownText);
    }

    public String applyDexEntryListToDropdown(ArrayList<String> allItems,
                                              ArrayList<String> displayItems,
                                              String preferredEntry) {
        ActivityMainBinding binding = getBinding();
        if (binding == null || binding.tabDebugging == null) return "";
        return getDexEntryDropdownController().applyList(binding.tabDebugging, allItems, displayItems, preferredEntry);
    }

    public void resizeDexEntryDropdown(ArrayList<String> displayItems) {
        ActivityMainBinding binding = getBinding();
        if (binding == null || binding.tabDebugging == null) return;
        getDexEntryDropdownController().resize(binding.tabDebugging, displayItems);
    }

    public void setDexEntrySelectedText(String entry) {
        ActivityMainBinding binding = getBinding();
        if (binding == null || binding.tabDebugging == null) return;
        getDexEntryDropdownController().setSelectedText(binding.tabDebugging, entry);
    }

    public String getLastDexEntryDropdownText() {
        return getDexEntryStateController().lastDropdownText();
    }

    public String currentDexEntry() {
        return getDexEntryStateController().currentEntry();
    }

    public ArrayList<String> currentDexEntries() {
        return getDexEntryStateController().currentEntries();
    }

    public boolean isDexEntrySelectionInvalid() {
        return getDexEntryStateController().isSelectionInvalid();
    }

    public boolean shouldRefreshDexEntriesIfInputPresent() {
        return getDexEntryStateController().shouldRefreshIfInputPresent();
    }

    public void refreshDexEntriesFromCurrentInput(boolean forcePathRefresh) {
        getDexEntryStateController().refreshFromCurrentInput(forcePathRefresh);
    }

    public void applySelectedDexEntry(String entry, boolean forcePathRefresh, boolean fromUser) {
        getDexEntryStateController().applySelected(entry, forcePathRefresh, fromUser);
    }

    public void rememberSelectedDexEntryWithoutPathRefresh(String entry) {
        getDexEntryStateController().rememberSelectedWithoutPathRefresh(entry);
    }

    public String smaliDirForEntry(String dexEntry, boolean preferCurrentField) {
        return getDexEntryStateController().smaliDirForEntry(dexEntry, preferCurrentField);
    }

    public String dexOutForEntry(String dexEntry, boolean preferCurrentField) {
        return getDexEntryStateController().dexOutForEntry(dexEntry, preferCurrentField);
    }

    public void runSmaliDisassemble() {
        getSmaliActionController().runDisassemble();
    }

    public void runSmaliDisassembleAll() {
        getSmaliActionController().runDisassembleAll();
    }

    public void restoreSmaliDisassembleAllJobStatus() {
        getSmaliActionController().restoreDisassembleAllJobStatus();
    }

    public void refreshDebuggingDexEntriesIfInputPresent(boolean forcePathRefresh) {
        getSmaliActionController().refreshDexEntriesIfInputPresent(forcePathRefresh);
    }

    public void runSmaliAssemble() {
        getSmaliActionController().runAssemble();
    }

    public void runSmaliReassembleApk() {
        getSmaliActionController().runReassembleApk();
    }

    public void runSmaliReassembleAllDex() {
        getSmaliActionController().runReassembleAllDex();
    }

    public void runSmaliListClasses() {
        getSmaliActionController().runListClasses();
    }

    public void openApkEditorSmaliWorkspace(String apkInputPath,
                                            String smaliInputDirPath,
                                            String selectedSmaliFilePath,
                                            String dexOutputPath,
                                            String apkOutputPath,
                                            String dexEntry) {
        try {
            ActivityMainBinding binding = getBinding();
            if (binding == null || binding.tabDebugging == null) return;
            String cleanEntry = PermsTestSmaliTools.normalizeDexEntryName(dexEntry);
            if (binding.tabDebugging.edtSmaliDexInput != null) binding.tabDebugging.edtSmaliDexInput.setText(apkInputPath == null ? "" : apkInputPath);
            if (binding.tabDebugging.edtSmaliOutDir != null) binding.tabDebugging.edtSmaliOutDir.setText(smaliInputDirPath == null ? "" : smaliInputDirPath);
            if (binding.tabDebugging.edtSmaliInputDir != null) binding.tabDebugging.edtSmaliInputDir.setText(smaliInputDirPath == null ? "" : smaliInputDirPath);
            if (binding.tabDebugging.edtSmaliDexOutput != null) binding.tabDebugging.edtSmaliDexOutput.setText(dexOutputPath == null ? "" : dexOutputPath);
            if (binding.tabDebugging.edtSmaliApkOutput != null) binding.tabDebugging.edtSmaliApkOutput.setText(apkOutputPath == null ? "" : apkOutputPath);
            if (binding.tabDebugging.edtSmaliEditorPath != null) binding.tabDebugging.edtSmaliEditorPath.setText(selectedSmaliFilePath == null ? "" : selectedSmaliFilePath);
            rememberSelectedDexEntryWithoutPathRefresh(cleanEntry);
            if (host != null) host.selectDebuggingTab();
            if (!TextUtils.isEmpty(selectedSmaliFilePath)) {
                openSmaliFileInInternalEditor(new File(selectedSmaliFilePath), 1);
            } else {
                setSmaliEditorStatus("APK Editor smali workspace loaded.");
            }
            if (host != null) host.appendOutput("[APK Editor] Loaded smali workspace into Debugging tab. Save edits there, then use APK Editor > Repack Smali.\n");
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[APK Editor] Failed to open smali workspace: " + t.getMessage() + "\n");
        }
    }


    public void setupDebuggingApkPicker() {
        getApkSourceController().setupApkPicker();
    }

    public void launchDebuggingApkPicker() {
        getApkSourceController().launchApkPicker();
    }

    public void setupDebuggingInstalledPackageDropdown() {
        getApkSourceController().setupInstalledPackageDropdown();
    }

    public void selectDebuggingInstalledPackageFromPopout(Object entryObject) {
        getApkSourceController().selectInstalledPackageFromPopout(entryObject);
    }

    public void refreshDebuggingInstalledPackages(boolean userInitiated) {
        getApkSourceController().refreshInstalledPackages(userInitiated);
    }

    public void prepareSelectedDebuggingInstalledPackage() {
        getApkSourceController().prepareSelectedInstalledPackage();
    }

    public String currentDebuggingWorkRoot() {
        return getApkSourceController().currentWorkRoot();
    }

    public String getSelectedDebuggingPackage() {
        return getApkSourceController().getSelectedPackage();
    }

    public void refreshDebuggingApkOutputPath(boolean forcePathRefresh) {
        getApkSourceController().refreshApkOutputPath(forcePathRefresh);
    }

    public void setDebuggingDefaults(boolean force) {
        getApkSourceController().setDefaults(force);
    }

    public void updateDerivedPathsForSelectedDebuggingDexEntry(String dexEntry, boolean forcePathRefresh) {
        getApkSourceController().updateDerivedPathsForSelectedDexEntry(dexEntry, forcePathRefresh);
    }

    private DebuggingApkSourceController getApkSourceController() {
        if (apkSourceController == null) {
            apkSourceController = new DebuggingApkSourceController(new DebuggingApkSourceController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return DebuggingActivityControllers.this.getBinding();
                }

                @Override
                public java.util.concurrent.ExecutorService getDebugApkExecutor() {
                    return host == null ? null : host.getDebugApkExecutor();
                }

                @Override
                public void runIo(Runnable task) {
                    if (host != null) host.runDebuggingIo(task);
                }

                @Override
                public void runOnUiThread(Runnable action) {
                    if (host != null) host.runOnUiThread(action);
                }

                @Override
                public String queryDisplayName(Uri uri) {
                    return host == null ? null : host.queryDisplayName(uri);
                }

                @Override
                public File copyUriToExternalDir(Uri uri, String subdir, String filename) throws IOException {
                    if (host == null) throw new IOException("Debugging host is unavailable.");
                    return host.copyUriToExternalDir(uri, subdir, filename);
                }

                @Override
                public ArrayList<dev.perms.test.ui.PackageDropdownEntry> snapshotAllPackages() {
                    return host == null ? new ArrayList<>() : host.snapshotAllPackages();
                }

                @Override
                public dev.perms.test.packages.InstalledPackageExtractor.ExtractedInstalledPackage extractInstalledPackageForDebug(String packageName, String displayName) throws IOException {
                    if (host == null) throw new IOException("Debugging host is unavailable.");
                    return host.extractInstalledPackageForDebug(packageName, displayName);
                }

                @Override
                public boolean isSafeToken(String token) {
                    return host != null && host.isSafeToken(token);
                }

                @Override
                public void setDebuggingBusy(boolean busy, String status) {
                    if (host != null) host.setDebuggingBusy(busy, status);
                }

                @Override
                public void finishDebuggingToolError(String label, Throwable error) {
                    if (host != null) host.finishDebuggingToolError(label, error);
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void toast(String text) {
                    if (host != null) host.toast(text);
                }

                @Override
                public void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText) {
                    if (host != null) host.showDropdownAtLastSelection(view, lastText);
                }

                @Override
                public String currentDexEntry() {
                    return DebuggingActivityControllers.this.currentDexEntry();
                }

                @Override
                public void refreshDexEntriesFromCurrentInput(boolean forcePathRefresh) {
                    DebuggingActivityControllers.this.refreshDexEntriesFromCurrentInput(forcePathRefresh);
                }

                @Override
                public void applySelectedDexEntry(String entry, boolean forcePathRefresh, boolean fromUser) {
                    DebuggingActivityControllers.this.applySelectedDexEntry(entry, forcePathRefresh, fromUser);
                }

                @Override
                public int colorGranted() {
                    return host == null ? 0 : host.colorGranted();
                }

                @Override
                public int colorRevoked() {
                    return host == null ? 0 : host.colorRevoked();
                }

                @Override
                public boolean colorizeAppDropdown() {
                    return host != null && host.colorizeAppDropdown();
                }
            });
        }
        return apkSourceController;
    }

    private DebuggingDexEntryStateController getDexEntryStateController() {
        if (dexEntryStateController == null) {
            dexEntryStateController = new DebuggingDexEntryStateController(new DebuggingDexEntryStateController.Host() {
                @Override
                public ActivityMainBinding getBinding() {
                    return DebuggingActivityControllers.this.getBinding();
                }

                @Override
                public void runIo(Runnable action) {
                    if (host != null) host.runDebuggingIo(action);
                }

                @Override
                public void runOnUiThread(Runnable action) {
                    if (host != null) host.runOnUiThread(action);
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void setDebuggingBusy(boolean busy, String status) {
                    if (host != null) host.setDebuggingBusy(busy, status);
                }

                @Override
                public void finishDebuggingToolError(String label, Throwable error) {
                    if (host != null) host.finishDebuggingToolError(label, error);
                }

                @Override
                public String currentDebuggingWorkRoot() {
                    return DebuggingActivityControllers.this.currentDebuggingWorkRoot();
                }

                @Override
                public void updateDerivedPathsForSelectedDexEntry(String dexEntry, boolean forcePathRefresh) {
                    DebuggingActivityControllers.this.updateDerivedPathsForSelectedDebuggingDexEntry(dexEntry, forcePathRefresh);
                }

                @Override
                public void setupDexEntryDropdown(ArrayList<String> allItems, ArrayList<String> displayItems, String selectedEntry, String lastDropdownText) {
                    DebuggingActivityControllers.this.setupDexEntryDropdown(allItems, displayItems, selectedEntry, lastDropdownText);
                }

                @Override
                public String applyDexEntryListToDropdown(ArrayList<String> allItems, ArrayList<String> displayItems, String preferredEntry) {
                    return DebuggingActivityControllers.this.applyDexEntryListToDropdown(allItems, displayItems, preferredEntry);
                }

                @Override
                public void resizeDexEntryDropdown(ArrayList<String> displayItems) {
                    DebuggingActivityControllers.this.resizeDexEntryDropdown(displayItems);
                }

                @Override
                public void setDexEntrySelectedText(String entry) {
                    DebuggingActivityControllers.this.setDexEntrySelectedText(entry);
                }
            });
        }
        return dexEntryStateController;
    }

    private DebuggingDexEntryDropdownController getDexEntryDropdownController() {
        if (dexEntryDropdownController == null) {
            Activity activity = host == null ? null : host.getActivity();
            dexEntryDropdownController = new DebuggingDexEntryDropdownController(activity,
                    new DebuggingDexEntryDropdownController.Host() {
                        @Override
                        public int dpToPx(int dp) {
                            return host == null ? dp : host.dpToPx(dp);
                        }

                        @Override
                        public void configureSafeDropdownEndIcon(TextInputLayout layout, Runnable onClick) {
                            if (host != null) host.configureSafeDropdownEndIcon(layout, onClick);
                        }

                        @Override
                        public void configureTapOnlyDropdownField(AutoCompleteTextView view, int touchSlop, int maxTapMs, Runnable onTap) {
                            if (host != null) host.configureTapOnlyDropdownField(view, touchSlop, maxTapMs, onTap);
                        }

                        @Override
                        public void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText) {
                            if (host != null) host.showDropdownAtLastSelection(view, lastText);
                        }

                        @Override
                        public String getLastDropdownText() {
                            return DebuggingActivityControllers.this.getLastDexEntryDropdownText();
                        }

                        @Override
                        public void onDexEntrySelected(String entry, boolean forcePathRefresh, boolean fromUser) {
                            DebuggingActivityControllers.this.applySelectedDexEntry(entry, forcePathRefresh, fromUser);
                        }

                        @Override
                        public void appendOutput(String text) {
                            if (host != null) host.appendOutput(text);
                        }
                    });
        }
        return dexEntryDropdownController;
    }


    public void setupSmaliEditor() {
        try {
            ActivityMainBinding binding = getBinding();
            if (binding == null || binding.tabDebugging == null) return;
            getSmaliEditorSearchController().setup();
            getSmaliEditorFileController().setup();
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[Debugging] Smali editor setup failed: " + t.getMessage() + "\n");
        }
    }

    public void restoreSmaliEditorSearchJobStatus() {
        getSmaliEditorSearchController().restoreJobStatus();
    }

    public void openSmaliFileInInternalEditor(File file, int lineHint) {
        getSmaliEditorFileController().openSmaliFileInInternalEditor(file, lineHint);
    }

    public void openJavaFileInInternalEditor(File file, int lineHint) {
        getSmaliEditorFileController().openJavaFileInInternalEditor(file, lineHint);
    }

    public void setSmaliEditorStatus(String status) {
        getSmaliEditorFileController().setStatus(status);
    }

    public void finishSmaliEditorError(String label, Throwable error) {
        getSmaliEditorFileController().finishError(label, error);
    }

    private SmaliEditorSearchController getSmaliEditorSearchController() {
        if (smaliEditorSearchController == null) {
            smaliEditorSearchController = new SmaliEditorSearchController(new SmaliEditorSearchController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return DebuggingActivityControllers.this.getBinding();
                }

                @Override
                public SharedPreferences getPreferences() {
                    return host == null ? null : host.getDebuggingPreferences();
                }

                @Override
                public android.os.Handler getMainHandler() {
                    return host == null ? null : host.getMainHandler();
                }

                @Override
                public String currentDebuggingWorkRoot() {
                    return DebuggingActivityControllers.this.currentDebuggingWorkRoot();
                }

                @Override
                public ArrayList<String> currentDebuggingDexEntries() {
                    return DebuggingActivityControllers.this.currentDexEntries();
                }

                @Override
                public String debuggingSmaliDirForEntry(String entry, boolean preferCurrentField) {
                    return DebuggingActivityControllers.this.smaliDirForEntry(entry, preferCurrentField);
                }

                @Override
                public void openSearchResult(SmaliEditorSearch.Result result) {
                    getSmaliEditorFileController().openSearchResult(result);
                }

                @Override
                public void setSearchStatus(String status) {
                    getSmaliEditorFileController().setStatus(status);
                }

                @Override
                public void finishSearchError(String label, Throwable error) {
                    getSmaliEditorFileController().finishError(label, error);
                }
            });
        }
        return smaliEditorSearchController;
    }

    private SmaliEditorFileController getSmaliEditorFileController() {
        if (smaliEditorFileController == null) {
            smaliEditorFileController = new SmaliEditorFileController(new SmaliEditorFileController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return DebuggingActivityControllers.this.getBinding();
                }

                @Override
                public SharedPreferences getPreferences() {
                    return host == null ? null : host.getDebuggingPreferences();
                }

                @Override
                public android.os.Handler getMainHandler() {
                    return host == null ? null : host.getMainHandler();
                }

                @Override
                public java.util.concurrent.ExecutorService getIoExecutor() {
                    return host == null ? null : host.getDebuggingIoExecutor();
                }

                @Override
                public ArrayList<File> searchRoots() {
                    return getSmaliEditorSearchController().searchRoots();
                }

                @Override
                public String currentDebuggingWorkRoot() {
                    return DebuggingActivityControllers.this.currentDebuggingWorkRoot();
                }

                @Override
                public String currentDebuggingDexEntry() {
                    return DebuggingActivityControllers.this.currentDexEntry();
                }

                @Override
                public void revealEditorCard() {
                    if (host != null) host.revealSmaliEditorCard();
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void setSearchRunning(boolean running) {
                    if (smaliEditorSearchController != null) {
                        smaliEditorSearchController.setSearchRunning(running);
                    }
                }
            });
        }
        return smaliEditorFileController;
    }

    private DebuggingSmaliActionController getSmaliActionController() {
        if (smaliActionController == null) {
            smaliActionController = new DebuggingSmaliActionController(new DebuggingSmaliActionController.Host() {
                @Override
                public Activity activity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding binding() {
                    return DebuggingActivityControllers.this.getBinding();
                }

                @Override
                public java.util.concurrent.ExecutorService ioExecutor() {
                    return host == null ? null : host.getDebuggingIoExecutor();
                }

                @Override
                public java.util.concurrent.ExecutorService debugApkExecutor() {
                    return host == null ? null : host.getDebugApkExecutor();
                }

                @Override
                public boolean isDexEntrySelectionInvalid() {
                    return DebuggingActivityControllers.this.isDexEntrySelectionInvalid();
                }

                @Override
                public ArrayList<String> currentDexEntries() {
                    return DebuggingActivityControllers.this.currentDexEntries();
                }

                @Override
                public String currentDexEntry() {
                    return DebuggingActivityControllers.this.currentDexEntry();
                }

                @Override
                public String smaliDirForEntry(String dexEntry, boolean preferCurrentField) {
                    return DebuggingActivityControllers.this.smaliDirForEntry(dexEntry, preferCurrentField);
                }

                @Override
                public String dexOutForEntry(String dexEntry, boolean preferCurrentField) {
                    return DebuggingActivityControllers.this.dexOutForEntry(dexEntry, preferCurrentField);
                }

                @Override
                public boolean shouldRefreshDexEntriesIfInputPresent() {
                    return DebuggingActivityControllers.this.shouldRefreshDexEntriesIfInputPresent();
                }

                @Override
                public void refreshDebuggingDexEntriesFromCurrentInput(boolean forcePathRefresh) {
                    DebuggingActivityControllers.this.refreshDexEntriesFromCurrentInput(forcePathRefresh);
                }

                @Override
                public void refreshDebuggingApkOutputPath(boolean forcePathRefresh) {
                    DebuggingActivityControllers.this.refreshDebuggingApkOutputPath(forcePathRefresh);
                }

                @Override
                public String exportRebuiltDebuggingApk(File rebuiltUnsigned, String apkOutput, boolean makeDebuggable, File workDir) throws Exception {
                    return getToolBridge().exportRebuiltApk(rebuiltUnsigned, apkOutput, makeDebuggable, workDir);
                }

                @Override
                public void deleteTreeQuietly(File file) {
                    if (host != null) host.deleteTreeQuietly(file);
                }

                @Override
                public void setBusy(boolean busy, String status) {
                    if (host != null) host.setDebuggingBusy(busy, status);
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void runOnUiThread(Runnable action) {
                    if (host != null) host.runOnUiThread(action);
                }

                @Override
                public void finishError(String label, Throwable error) {
                    if (host != null) host.finishDebuggingToolError(label, error);
                }
            });
        }
        return smaliActionController;
    }

    private void setupMitmPatchControls() {
        getMitmController().setup();
    }

    private void setupJadxControls() {
        getJadxController().setup();
    }

    private DebuggingJadxController getJadxController() {
        if (jadxController == null) {
            jadxController = new DebuggingJadxController(getBinding(), host == null ? null : host.getDebugApkExecutor(),
                    new DebuggingJadxController.Host() {
                        @Override
                        public void setDebuggingBusy(boolean busy, String status) {
                            if (host != null) host.setDebuggingBusy(busy, status);
                        }

                        @Override
                        public void appendOutput(String text) {
                            if (host != null) host.appendOutput(text);
                        }

                        @Override
                        public void runOnUiThread(Runnable action) {
                            if (host != null) host.runOnUiThread(action);
                        }
                        @Override
                        public void openJavaFileInInternalEditor(File file, int lineHint) {
                            DebuggingActivityControllers.this.openJavaFileInInternalEditor(file, lineHint);
                        }
                    });
        }
        return jadxController;
    }

    private DebuggingMitmController getMitmController() {
        if (mitmController == null) {
            mitmController = new DebuggingMitmController(getBinding(), host == null ? null : host.getDebugApkExecutor(),
                    new DebuggingMitmController.Host() {
                        @Override
                        public void setDebuggingBusy(boolean busy, String status) {
                            if (host != null) host.setDebuggingBusy(busy, status);
                        }

                        @Override
                        public void appendOutput(String text) {
                            if (host != null) host.appendOutput(text);
                        }

                        @Override
                        public void runOnUiThread(Runnable action) {
                            if (host != null) host.runOnUiThread(action);
                        }

                        @Override
                        public String resolveApktoolCommand() throws IOException {
                            return getToolBridge().resolveApktoolCommand();
                        }

                        @Override
                        public DebuggingMitmController.ShellResult runShellCommandCaptureSync(String command) {
                            return host == null ? null : host.runMitmShellCommandCaptureSync(command);
                        }

                        @Override
                        public String exportRebuiltApk(File rebuiltUnsigned, String outputPath, boolean makeDebuggable, File workDir) throws Exception {
                            return getToolBridge().exportRebuiltApk(rebuiltUnsigned, outputPath, makeDebuggable, workDir);
                        }

                        @Override
                        public File getWorkRoot(String type) {
                            return host == null ? null : host.getWorkRoot(type);
                        }

                        @Override
                        public String getCurrentDebuggingWorkRoot() {
                            return DebuggingActivityControllers.this.currentDebuggingWorkRoot();
                        }

                        @Override
                        public String getSelectedDebuggingPackage() {
                            return DebuggingActivityControllers.this.getSelectedDebuggingPackage();
                        }

                        @Override
                        public String shQuote(String value) {
                            return host == null ? "''" : host.quoteShell(value);
                        }

                        @Override
                        public void deleteTreeQuietly(File file) {
                            if (host != null) host.deleteTreeQuietly(file);
                        }

                        @Override
                        public boolean isDebugOutputEnabled() {
                            return host != null && host.isAppDebugOutputEnabled();
                        }
                    });
        }
        return mitmController;
    }

    private DebuggingToolBridge getToolBridge() {
        if (toolBridge == null) {
            toolBridge = new DebuggingToolBridge(new DebuggingToolBridge.Host() {
                @Override
                public Activity activity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public DebuggingRebuiltApkExporter.ToolResult ensureBundledTool(String toolName) {
                    return host == null ? null : host.ensureBundledDebuggingTool(toolName);
                }

                @Override
                public DebuggingRebuiltApkExporter.ToolResult runShell(String command) {
                    return host == null ? null : host.runDebuggingShellCommandCapture(command);
                }

                @Override
                public String quoteShell(String value) {
                    return host == null ? "''" : host.quoteShell(value);
                }
            });
        }
        return toolBridge;
    }

    private SmaliEditorIntentHandler getSmaliEditorIntentHandler() {
        if (smaliEditorIntentHandler == null) {
            smaliEditorIntentHandler = new SmaliEditorIntentHandler(
                    host == null ? null : host.getActivity(),
                    getBinding(),
                    host == null ? null : host.getDebugApkExecutor(),
                    host == null ? null : host.getOpenSmaliEditorUriExtra(),
                    host == null ? null : host.getOpenSmaliEditorLabelExtra(),
                    new SmaliEditorIntentHandler.Host() {
                        @Override
                        public String queryDisplayName(Uri uri) {
                            return host == null ? null : host.queryDisplayName(uri);
                        }

                        @Override
                        public File copyUriToExternalDir(Uri uri, String subdir, String filename) throws IOException {
                            if (host == null) throw new IOException("Debugging host is unavailable.");
                            return host.copyUriToExternalDir(uri, subdir, filename);
                        }

                        @Override
                        public void showDebuggingTab() {
                            if (host != null) host.selectDebuggingTab();
                        }

                        @Override
                        public void setDebuggingBusy(boolean busy, String status) {
                            if (host != null) host.setDebuggingBusy(busy, status);
                        }

                        @Override
                        public void finishDebuggingToolError(String label, Throwable t) {
                            if (host != null) host.finishDebuggingToolError(label, t);
                        }

                        @Override
                        public void openSmaliFileInInternalEditor(File file, int lineHint) {
                            DebuggingActivityControllers.this.openSmaliFileInInternalEditor(file, lineHint);
                        }

                        @Override
                        public void appendOutput(String text) {
                            if (host != null) host.appendOutput(text);
                        }
                    });
        }
        return smaliEditorIntentHandler;
    }

    private ActivityMainBinding getBinding() {
        return host == null ? null : host.getBinding();
    }
}
