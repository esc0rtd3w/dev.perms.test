package dev.perms.test.memory;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.AutoCompleteTextView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.ExecMode;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.memory.overlay.MemoryOverlayActions;
import dev.perms.test.memory.overlay.MemoryOverlayService;
import dev.perms.test.memory.panel.MemoryPanelActions;
import dev.perms.test.memory.payload.MemoryPayloadShortcutActions;
import dev.perms.test.ui.PackageDropdownEntry;

/**
 * Lazy Activity-side controller holder for the Memory tab and Memory app actions.
 *
 * MainActivity keeps Android lifecycle and shared app services. This class owns only the
 * Memory controller construction/wiring so the Activity does not keep growing with feature
 * controller factories.
 */
public final class MemoryActivityControllers {
    private final MemoryActivityDependencies dependencies;
    private final MemoryActivityState activityState = new MemoryActivityState();

    private MemoryAppActions appActions;
    private MemoryToolCommandRunner toolCommandRunner;
    private MemoryOverlayActions overlayActions;
    private MemoryPanelActions panelActions;
    private MemoryPayloadShortcutActions payloadShortcutActions;
    private MemoryProcessDropdownController processController;
    private MemoryTargetPackageDropdownController targetPackageController;
    private MemoryPackageToolTargetController packageToolTargetController;
    private MemoryTabController tabController;

    public MemoryActivityControllers(MemoryActivityDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public void restoreActivityState(Bundle savedInstanceState) {
        activityState.restore(savedInstanceState);
    }

    public void saveActivityState(Bundle outState) {
        ActivityMainBinding binding = getBinding();
        activityState.save(outState, binding == null ? null : binding.tabMemory);
    }

    private void ensureDefaults() {
        MemorySettings.ensureDefaults(getPrefs());
    }

    private boolean shouldPatchWithoutPtrace() {
        return MemorySettings.shouldPatchWithoutPtrace(getPrefs());
    }

    private boolean shouldUseOverlay() {
        return MemorySettings.shouldUseOverlay(getPrefs());
    }

    private boolean shouldUseMemoryPanel() {
        return MemorySettings.shouldDisableOverlaysForVrCompatible(getActivity(), getPrefs());
    }

    private boolean shouldOnlyShowRunningPackages() {
        return MemorySettings.shouldOnlyShowRunningPackages(getPrefs());
    }

    private boolean shouldShowRunningPackages() {
        return MemorySettings.shouldShowRunningPackages(getPrefs());
    }

    private boolean shouldLoadRunningPackageState() {
        return shouldOnlyShowRunningPackages() || shouldShowRunningPackages();
    }

    private boolean shouldExcludeSelfPackage() {
        return MemorySettings.shouldExcludeSelfPackage(getPrefs());
    }

    private boolean shouldStringCaseSensitive() {
        return MemorySettings.shouldStringCaseSensitive(getPrefs());
    }

    public void rememberPackageToolsTargetPackage(String pkg) {
        MemoryPackageToolTargetController controller = getPackageToolTargetController();
        if (controller != null) controller.rememberPackageToolsTargetPackage(pkg);
        else MemorySettings.setLastPackageToolsTarget(getPrefs(), pkg);
    }

    private MemoryPackageToolTargetController getPackageToolTargetController() {
        ActivityMainBinding binding = getBinding();
        if (packageToolTargetController == null && binding != null) {
            packageToolTargetController = new MemoryPackageToolTargetController(
                    getActivity(),
                    binding,
                    getPrefs(),
                    getSelfPackageName(),
                    this::getSelectedPackage,
                    this::getInstalledApps,
                    this::getTargetPackage,
                    this::setTargetPackageText,
                    this::getTargetPackageItemsSnapshot,
                    this::shouldExcludeSelfPackage,
                    this::refreshProcesses);
        }
        return packageToolTargetController;
    }

    private MemoryTargetPackageDropdownController getTargetPackageController() {
        ActivityMainBinding binding = getBinding();
        if (targetPackageController == null
                && binding != null
                && binding.tabMemory != null
                && binding.tabMemory.edtMemoryTargetPkg != null) {
            targetPackageController = new MemoryTargetPackageDropdownController(
                    getActivity(),
                    binding.tabMemory,
                    activityState == null ? null : activityState.consumeTargetPackage(),
                    packages -> MemoryTargets.filterPackages(
                            packages,
                            shouldOnlyShowRunningPackages(),
                            shouldExcludeSelfPackage(),
                            getSelfPackageName()),
                    this::shouldLoadRunningPackageState,
                    this::refreshProcesses,
                    this::executeInBackground,
                    this::executeOnUi,
                    this::appendOutput,
                    this::showDropdown,
                    this::applyDropdownTweaks);
        }
        return targetPackageController;
    }

    private ArrayList<MemoryPackageEntry> getTargetPackageItemsSnapshot() {
        MemoryTargetPackageDropdownController controller = getTargetPackageController();
        return controller == null ? new ArrayList<>() : controller.getPackageItemsSnapshot();
    }

    private String getTargetPackage() {
        MemoryTargetPackageDropdownController controller = getTargetPackageController();
        if (controller != null) return controller.getTargetPackage();
        try {
            ActivityMainBinding binding = getBinding();
            if (binding != null && binding.tabMemory != null && binding.tabMemory.edtMemoryTargetPkg != null
                    && binding.tabMemory.edtMemoryTargetPkg.getText() != null) {
                return binding.tabMemory.edtMemoryTargetPkg.getText().toString().trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private void setTargetPackageText(String pkg) {
        MemoryTargetPackageDropdownController controller = getTargetPackageController();
        if (controller != null) {
            controller.setTargetPackage(pkg);
            return;
        }
        try {
            ActivityMainBinding binding = getBinding();
            if (binding != null && binding.tabMemory != null && binding.tabMemory.edtMemoryTargetPkg != null) {
                binding.tabMemory.edtMemoryTargetPkg.setText(pkg == null ? "" : pkg, false);
            }
        } catch (Throwable ignored) {
        }
    }

    public void refreshTargetPackages(boolean userInitiated) {
        MemoryTargetPackageDropdownController controller = getTargetPackageController();
        if (controller != null) controller.refresh(userInitiated);
    }

    private String getSelectedPid() {
        MemoryProcessDropdownController controller = getProcessController();
        return controller == null ? null : controller.getSelectedPid();
    }
    private void syncTargetFromPackageTools(boolean refreshProcesses) {
        MemoryPackageToolTargetController controller = getPackageToolTargetController();
        if (controller != null) controller.syncMemoryTargetFromPackageTools(refreshProcesses);
    }

    public void refreshProcesses(boolean userInitiated) {
        MemoryProcessDropdownController controller = getProcessController();
        if (controller != null) controller.refresh(userInitiated);
    }

    public void onTabShown() {
        MemoryTabController controller = getTabController();
        if (controller != null) controller.onTabShown();
    }

    private void openMainOverlay() {
        if (shouldUseMemoryPanel()) {
            getPanelActions().openMainPanel();
        } else {
            getOverlayActions().openMainOverlay();
        }
    }

    private void openHexOverlay() {
        openMemoryToolWindow(MemoryOverlayService.ACTION_SHOW_HEX_OVERLAY, "Hex overlay opened", "Hex panel opened");
    }

    private void openDisassemblyOverlay() {
        openMemoryToolWindow(MemoryOverlayService.ACTION_SHOW_DISASSEMBLY_OVERLAY, "Disassembly overlay opened", "Disassembly panel opened");
    }

    private void openSpecialToolsOverlay() {
        openMemoryToolWindow(MemoryOverlayService.ACTION_SHOW_SPECIAL_TOOLS_OVERLAY, "Special Tools overlay opened", "Special Tools panel opened");
    }

    private void openMemoryToolWindow(String action, String overlayToastText, String panelToastText) {
        if (shouldUseMemoryPanel()) {
            getPanelActions().openPanelAction(action, panelToastText);
        } else {
            getOverlayActions().openOverlayAction(action, overlayToastText);
        }
    }

    private MemoryProcessDropdownController getProcessController() {
        ActivityMainBinding binding = getBinding();
        if (processController == null
                && binding != null
                && binding.tabMemory != null
                && binding.tabMemory.ddMemoryProcess != null) {
            processController = new MemoryProcessDropdownController(
                    getActivity(),
                    binding.tabMemory.ddMemoryProcess,
                    activityState == null ? null : activityState.consumeProcessText(),
                    this::getTargetPackage,
                    this::executeInBackground,
                    this::executeOnUi,
                    this::appendOutput);
        }
        return processController;
    }

    private MemoryTabController getTabController() {
        ActivityMainBinding binding = getBinding();
        if (tabController == null && binding != null && binding.tabMemory != null) {
            tabController = new MemoryTabController(
                    getActivity(),
                    binding.tabMemory,
                    getPrefs(),
                    this::getTargetPackageController,
                    this::getProcessController,
                    this::bindDropdownEndIcon,
                    this::bindTapOnlyDropdown,
                    this::applyDropdownTweaks,
                    this::refreshTargetPackages,
                    this::refreshProcesses,
                    this::syncTargetFromPackageTools,
                    this::stageToolFromUi,
                    this::launchTargetPackage,
                    this::stopTargetPackage,
                    this::openMainOverlay,
                    this::openHexOverlay,
                    this::openDisassemblyOverlay,
                    this::openSpecialToolsOverlay,
                    this::clearSession,
                    this::runToolCommand,
                    this::shouldUseOverlay,
                    this::isMemoryTabVisible,
                    this::getTargetPackage,
                    this::getSelectedPid);
        }
        return tabController;
    }

    public void bindTab() {
        try {
            if (getBinding() == null) return;
            ensureDefaults();
            MemoryTabController controller = getTabController();
            if (controller != null) controller.bind();
        } catch (Throwable ignored) {
        }
    }

    private void launchTargetPackage() {
        getAppActions().launchTargetPackage();
    }

    private void stopTargetPackage() {
        getAppActions().stopTargetPackage();
    }

    private void clearSession() {
        getAppActions().clearSession();
    }

    private void runToolCommand(String command, String dataType, String value, String begin, String end) {
        getToolCommandRunner().run(command, dataType, value, begin, end);
    }

    private void stageToolFromUi() {
        getToolCommandRunner().stageToolFromUi();
    }

    public void launchPackageWithPayloads(String packageName, String label) {
        getPayloadShortcutActions().launchPackageWithPayloads(packageName, label);
    }

    public void showPayloadShortcutDialog(String packageName, String label) {
        getPayloadShortcutActions().showPayloadShortcutDialog(packageName, label);
    }

    private MemoryAppActions getAppActions() {
        if (appActions == null) {
            appActions = new MemoryAppActions(
                    getActivity(),
                    this::getTargetPackage,
                    this::executeInBackground,
                    this::runAppActionCommand,
                    this::appendOutput);
        }
        return appActions;
    }

    private MemoryToolCommandRunner getToolCommandRunner() {
        if (toolCommandRunner == null) {
            toolCommandRunner = new MemoryToolCommandRunner(
                    getActivity(),
                    getPublicBinDir(),
                    this::getExecMode,
                    this::getTargetPackage,
                    this::getSelectedPid,
                    this::shouldPatchWithoutPtrace,
                    this::shouldStringCaseSensitive,
                    this::executeInBackground,
                    this::executeOnUi,
                    this::stageTool,
                    this::runShellCommandAndAppend,
                    this::appendOutput);
        }
        return toolCommandRunner;
    }

    private MemoryOverlayActions getOverlayActions() {
        if (overlayActions == null) {
            overlayActions = new MemoryOverlayActions(
                    getActivity(),
                    this::getTargetPackage,
                    this::getSelectedPid,
                    this::appendOutput);
        }
        return overlayActions;
    }

    private MemoryPanelActions getPanelActions() {
        if (panelActions == null) {
            panelActions = new MemoryPanelActions(
                    getActivity(),
                    this::getTargetPackage,
                    this::getSelectedPid,
                    this::appendOutput);
        }
        return panelActions;
    }

    private MemoryPayloadShortcutActions getPayloadShortcutActions() {
        if (payloadShortcutActions == null) {
            payloadShortcutActions = new MemoryPayloadShortcutActions(getActivity(), this::appendOutput);
        }
        return payloadShortcutActions;
    }

    private Activity getActivity() {
        return dependencies == null ? null : dependencies.getActivity();
    }

    private ActivityMainBinding getBinding() {
        return dependencies == null ? null : dependencies.getBinding();
    }

    private SharedPreferences getPrefs() {
        return dependencies == null ? null : dependencies.getPreferences();
    }

    private String getSelfPackageName() {
        return dependencies == null ? null : dependencies.getSelfPackageName();
    }

    private String getPublicBinDir() {
        return dependencies == null ? null : dependencies.getPublicBinDir();
    }

    private String getSelectedPackage() {
        return dependencies == null ? null : dependencies.getSelectedPackage();
    }

    private List<PackageDropdownEntry> getInstalledApps() {
        return dependencies == null ? null : dependencies.getInstalledApps();
    }

    private void bindDropdownEndIcon(TextInputLayout layout, Runnable openAction) {
        if (dependencies != null) dependencies.bindDropdownEndIcon(layout, openAction);
    }

    private void bindTapOnlyDropdown(AutoCompleteTextView dropdown, int touchSlop, int timeoutMs, Runnable openAction) {
        if (dependencies != null) dependencies.bindTapOnlyDropdown(dropdown, touchSlop, timeoutMs, openAction);
    }

    private void applyDropdownTweaks(AutoCompleteTextView dropdown) {
        if (dependencies != null) dependencies.applyDropdownTweaks(dropdown);
    }

    private void showDropdown(AutoCompleteTextView dropdown, String lastText) {
        if (dependencies != null) dependencies.showDropdown(dropdown, lastText);
    }

    private void executeInBackground(Runnable task) {
        if (dependencies != null) dependencies.executeInBackground(task);
    }

    private void executeOnUi(Runnable task) {
        if (dependencies != null) dependencies.executeOnUi(task);
    }

    private void appendOutput(String text) {
        if (dependencies != null) dependencies.appendOutput(text);
    }

    private ExecMode getExecMode() {
        return dependencies == null ? ExecMode.SYSTEM : dependencies.getExecMode();
    }

    private MemoryAppActions.CommandResult runAppActionCommand(String command) {
        return dependencies == null ? null : dependencies.runAppActionCommand(command);
    }

    private MemoryToolCommandRunner.CommandResult stageTool(String name) {
        return dependencies == null ? null : dependencies.stageTool(name);
    }

    private void runShellCommandAndAppend(String command) {
        if (dependencies != null) dependencies.runShellCommandAndAppend(command);
    }

    private boolean isMemoryTabVisible() {
        return dependencies != null && dependencies.isMemoryTabVisible();
    }
}
