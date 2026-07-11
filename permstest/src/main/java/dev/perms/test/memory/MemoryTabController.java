package dev.perms.test.memory;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.ViewConfiguration;
import android.widget.AutoCompleteTextView;

import com.google.android.material.textfield.TextInputLayout;

import dev.perms.test.databinding.TabMemoryBinding;
import dev.perms.test.memory.payload.MemoryPayloadEditorController;
import dev.perms.test.memory.payload.MemoryPayloadQueueController;
import dev.perms.test.ui.DropdownUi;

/**
 * Activity-side binding controller for the Memory tab.
 *
 * MainActivity supplies the existing app-level actions and shared dropdown helpers. This
 * controller owns only Memory-tab UI binding: dropdown open behavior, option binding,
 * button routing, payload-editor binding, and the on-show refresh hook.
 */
public final class MemoryTabController {
    public interface TargetControllerProvider {
        MemoryTargetPackageDropdownController getController();
    }

    public interface ProcessControllerProvider {
        MemoryProcessDropdownController getController();
    }

    public interface DropdownEndIconBinder {
        void bind(TextInputLayout layout, Runnable openAction);
    }

    public interface TapOnlyDropdownBinder {
        void bind(AutoCompleteTextView dropdown, int touchSlop, int timeoutMs, Runnable openAction);
    }

    public interface DropdownTweaker {
        void apply(AutoCompleteTextView dropdown);
    }

    public interface BooleanProvider {
        boolean get();
    }

    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    public interface TargetPidProvider {
        String getTargetPid();
    }

    public interface TargetPackageRefreshCallback {
        void refresh(boolean userInitiated);
    }

    public interface ProcessRefreshCallback {
        void refresh(boolean userInitiated);
    }

    public interface PackageTargetSyncCallback {
        void sync(boolean refreshProcesses);
    }

    public interface MemoryToolCommandCallback {
        void run(String command, String dataType, String value, String begin, String end);
    }

    private final Context context;
    private final TabMemoryBinding tab;
    private final SharedPreferences prefs;
    private final TargetControllerProvider targetControllerProvider;
    private final ProcessControllerProvider processControllerProvider;
    private final DropdownEndIconBinder dropdownEndIconBinder;
    private final TapOnlyDropdownBinder tapOnlyDropdownBinder;
    private final DropdownTweaker dropdownTweaker;
    private final TargetPackageRefreshCallback targetPackageRefreshCallback;
    private final ProcessRefreshCallback processRefreshCallback;
    private final PackageTargetSyncCallback packageTargetSyncCallback;
    private final Runnable stageToolAction;
    private final Runnable launchTargetAction;
    private final Runnable stopTargetAction;
    private final Runnable openMainOverlayAction;
    private final Runnable openHexOverlayAction;
    private final Runnable openDisassemblyOverlayAction;
    private final Runnable openSpecialToolsOverlayAction;
    private final Runnable clearSessionAction;
    private final MemoryToolCommandCallback memoryToolCommandCallback;
    private final BooleanProvider useOverlayProvider;
    private final BooleanProvider memoryTabVisibleProvider;
    private final TargetPackageProvider targetPackageProvider;
    private final TargetPidProvider targetPidProvider;

    public MemoryTabController(Context context,
                               TabMemoryBinding tab,
                               SharedPreferences prefs,
                               TargetControllerProvider targetControllerProvider,
                               ProcessControllerProvider processControllerProvider,
                               DropdownEndIconBinder dropdownEndIconBinder,
                               TapOnlyDropdownBinder tapOnlyDropdownBinder,
                               DropdownTweaker dropdownTweaker,
                               TargetPackageRefreshCallback targetPackageRefreshCallback,
                               ProcessRefreshCallback processRefreshCallback,
                               PackageTargetSyncCallback packageTargetSyncCallback,
                               Runnable stageToolAction,
                               Runnable launchTargetAction,
                               Runnable stopTargetAction,
                               Runnable openMainOverlayAction,
                               Runnable openHexOverlayAction,
                               Runnable openDisassemblyOverlayAction,
                               Runnable openSpecialToolsOverlayAction,
                               Runnable clearSessionAction,
                               MemoryToolCommandCallback memoryToolCommandCallback,
                               BooleanProvider useOverlayProvider,
                               BooleanProvider memoryTabVisibleProvider,
                               TargetPackageProvider targetPackageProvider,
                               TargetPidProvider targetPidProvider) {
        this.context = context;
        this.tab = tab;
        this.prefs = prefs;
        this.targetControllerProvider = targetControllerProvider;
        this.processControllerProvider = processControllerProvider;
        this.dropdownEndIconBinder = dropdownEndIconBinder;
        this.tapOnlyDropdownBinder = tapOnlyDropdownBinder;
        this.dropdownTweaker = dropdownTweaker;
        this.targetPackageRefreshCallback = targetPackageRefreshCallback;
        this.processRefreshCallback = processRefreshCallback;
        this.packageTargetSyncCallback = packageTargetSyncCallback;
        this.stageToolAction = stageToolAction;
        this.launchTargetAction = launchTargetAction;
        this.stopTargetAction = stopTargetAction;
        this.openMainOverlayAction = openMainOverlayAction;
        this.openHexOverlayAction = openHexOverlayAction;
        this.openDisassemblyOverlayAction = openDisassemblyOverlayAction;
        this.openSpecialToolsOverlayAction = openSpecialToolsOverlayAction;
        this.clearSessionAction = clearSessionAction;
        this.memoryToolCommandCallback = memoryToolCommandCallback;
        this.useOverlayProvider = useOverlayProvider;
        this.memoryTabVisibleProvider = memoryTabVisibleProvider;
        this.targetPackageProvider = targetPackageProvider;
        this.targetPidProvider = targetPidProvider;
    }

    public void bind() {
        try {
            if (tab == null) return;

            MemoryProcessDropdownController processController = getProcessController();
            if (processController != null) processController.bindInitial();

            MemoryTargetPackageDropdownController targetController = getTargetController();
            if (targetController != null) targetController.bindInitial();

            bindTargetDropdownOpenBehavior();
            bindProcessDropdownOpenBehavior();

            MemoryOptionsBinder.bind(context, tab, prefs, () -> refreshTargetPackages(false));

            tab.btnMemoryUsePackageTarget.setOnClickListener(v -> {
                syncPackageTarget(true);
                refreshTargetPackages(false);
            });
            tab.btnMemoryStageTool.setOnClickListener(v -> run(stageToolAction));
            tab.btnMemoryRefreshTargetPackages.setOnClickListener(v -> refreshTargetPackages(true));
            tab.btnMemoryRefreshProcesses.setOnClickListener(v -> refreshProcesses(true));
            tab.btnMemoryStartApp.setOnClickListener(v -> run(launchTargetAction));
            tab.btnMemoryStopApp.setOnClickListener(v -> run(stopTargetAction));
            tab.btnMemoryAttach.setOnClickListener(v -> {
                if (shouldUseOverlay()) run(openMainOverlayAction);
                runMemoryToolCommand("attach", null, null, null, null);
            });
            tab.btnMemoryDetach.setOnClickListener(v -> runMemoryToolCommand("detach", null, null, null, null));
            tab.btnMemoryOpenOverlay.setOnClickListener(v -> run(openMainOverlayAction));
            tab.btnMemoryOpenHexOverlay.setOnClickListener(v -> run(openHexOverlayAction));
            tab.btnMemoryOpenDisasmOverlay.setOnClickListener(v -> run(openDisassemblyOverlayAction));
            tab.btnMemoryOpenSpecialTools.setOnClickListener(v -> run(openSpecialToolsOverlayAction));
            tab.btnMemoryClearState.setOnClickListener(v -> run(clearSessionAction));

            new MemoryPayloadQueueController(context, tab.getRoot(), this::getTargetPackage, this::getTargetPid).bind();
            new MemoryPayloadEditorController(context, tab.getRoot(), this::getTargetPackage).bind();

            if (isMemoryTabVisible()) {
                onTabShown();
            }
        } catch (Throwable ignored) {
        }
    }

    public void onTabShown() {
        try {
            if (tab == null) return;
            String currentTarget = getTargetPackage();
            refreshTargetPackages(false);
            if (!TextUtils.isEmpty(currentTarget)) {
                refreshProcesses(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void bindTargetDropdownOpenBehavior() {
        try {
            DropdownUi.prepareExposedDropdown(tab.tilMemoryTargetPkg, tab.edtMemoryTargetPkg);
            final Runnable openTargets = () -> {
                MemoryTargetPackageDropdownController controller = getTargetController();
                if (controller != null) controller.openOrRefreshDropdown();
            };
            bindDropdownEndIcon(tab.tilMemoryTargetPkg, openTargets);
            bindTapOnlyDropdown(tab.edtMemoryTargetPkg, ViewConfiguration.get(context).getScaledTouchSlop(), 300, openTargets);
            tab.edtMemoryTargetPkg.setOnDismissListener(() -> {
                MemoryTargetPackageDropdownController controller = getTargetController();
                if (controller != null) controller.onDropdownDismissed();
            });
            tab.edtMemoryTargetPkg.setOnFocusChangeListener((v, hasFocus) -> { });
        } catch (Throwable ignored) {
        }
    }

    private void bindProcessDropdownOpenBehavior() {
        try {
            DropdownUi.prepareExposedDropdown(tab.tilMemoryProcess, tab.ddMemoryProcess);
            final Runnable openProcesses = () -> {
                try {
                    if (tab.ddMemoryProcess.getAdapter() == null || tab.ddMemoryProcess.getAdapter().getCount() <= 1) {
                        refreshProcesses(false);
                    }
                    DropdownUi.showDropdown(tab.ddMemoryProcess);
                } catch (Throwable ignored) {
                }
                tab.ddMemoryProcess.post(() -> applyDropdownTweaks(tab.ddMemoryProcess));
            };
            bindDropdownEndIcon(tab.tilMemoryProcess, openProcesses);
            bindTapOnlyDropdown(tab.ddMemoryProcess, ViewConfiguration.get(context).getScaledTouchSlop(), 300, openProcesses);
            tab.ddMemoryProcess.setOnFocusChangeListener((v, hasFocus) -> { });
        } catch (Throwable ignored) {
        }
    }

    private MemoryTargetPackageDropdownController getTargetController() {
        try {
            return targetControllerProvider == null ? null : targetControllerProvider.getController();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private MemoryProcessDropdownController getProcessController() {
        try {
            return processControllerProvider == null ? null : processControllerProvider.getController();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void bindDropdownEndIcon(TextInputLayout layout, Runnable openAction) {
        if (dropdownEndIconBinder != null) dropdownEndIconBinder.bind(layout, openAction);
    }

    private void bindTapOnlyDropdown(AutoCompleteTextView dropdown, int touchSlop, int timeoutMs, Runnable openAction) {
        if (tapOnlyDropdownBinder != null) tapOnlyDropdownBinder.bind(dropdown, touchSlop, timeoutMs, openAction);
    }

    private void applyDropdownTweaks(AutoCompleteTextView dropdown) {
        try {
            if (dropdownTweaker != null) dropdownTweaker.apply(dropdown);
        } catch (Throwable ignored) {
        }
    }

    private void refreshTargetPackages(boolean userInitiated) {
        try {
            if (targetPackageRefreshCallback != null) targetPackageRefreshCallback.refresh(userInitiated);
        } catch (Throwable ignored) {
        }
    }

    private void refreshProcesses(boolean userInitiated) {
        try {
            if (processRefreshCallback != null) processRefreshCallback.refresh(userInitiated);
        } catch (Throwable ignored) {
        }
    }

    private void syncPackageTarget(boolean refreshProcesses) {
        try {
            if (packageTargetSyncCallback != null) packageTargetSyncCallback.sync(refreshProcesses);
        } catch (Throwable ignored) {
        }
    }

    private void runMemoryToolCommand(String command, String dataType, String value, String begin, String end) {
        try {
            if (memoryToolCommandCallback != null) {
                memoryToolCommandCallback.run(command, dataType, value, begin, end);
            }
        } catch (Throwable ignored) {
        }
    }

    private String getTargetPackage() {
        try {
            return targetPackageProvider == null ? "" : targetPackageProvider.getTargetPackage();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getTargetPid() {
        try {
            return targetPidProvider == null ? "" : targetPidProvider.getTargetPid();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean shouldUseOverlay() {
        try {
            return useOverlayProvider != null && useOverlayProvider.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isMemoryTabVisible() {
        try {
            return memoryTabVisibleProvider != null && memoryTabVisibleProvider.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void run(Runnable action) {
        if (action != null) action.run();
    }
}
