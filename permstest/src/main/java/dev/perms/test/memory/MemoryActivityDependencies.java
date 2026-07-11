package dev.perms.test.memory;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.AutoCompleteTextView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import dev.perms.test.ExecMode;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.PackageDropdownEntry;

/**
 * External services supplied by MainActivity for Activity-side Memory controllers.
 *
 * The Memory package owns feature wiring, while MainActivity keeps process-wide Android
 * services such as lifecycle, shell execution, shared dropdown helpers, and UI dispatch.
 */
public final class MemoryActivityDependencies {
    private final Activity activity;
    private final BindingProvider bindingProvider;
    private final SharedPreferencesProvider prefsProvider;
    private final String selfPackageName;
    private final String publicBinDir;
    private final SelectedPackageProvider selectedPackageProvider;
    private final InstalledAppsProvider installedAppsProvider;
    private final DropdownEndIconBinder dropdownEndIconBinder;
    private final TapOnlyDropdownBinder tapOnlyDropdownBinder;
    private final DropdownTweaker dropdownTweaker;
    private final DropdownShower dropdownShower;
    private final BackgroundExecutor backgroundExecutor;
    private final UiExecutor uiExecutor;
    private final OutputAppender outputAppender;
    private final ExecModeProvider execModeProvider;
    private final AppActionCommandRunner appActionCommandRunner;
    private final ToolCommandStager toolCommandStager;
    private final ShellAppendRunner shellAppendRunner;
    private final MemoryTabVisibleProvider memoryTabVisibleProvider;

    public MemoryActivityDependencies(Activity activity,
                                      BindingProvider bindingProvider,
                                      SharedPreferencesProvider prefsProvider,
                                      String selfPackageName,
                                      String publicBinDir,
                                      SelectedPackageProvider selectedPackageProvider,
                                      InstalledAppsProvider installedAppsProvider,
                                      DropdownEndIconBinder dropdownEndIconBinder,
                                      TapOnlyDropdownBinder tapOnlyDropdownBinder,
                                      DropdownTweaker dropdownTweaker,
                                      DropdownShower dropdownShower,
                                      BackgroundExecutor backgroundExecutor,
                                      UiExecutor uiExecutor,
                                      OutputAppender outputAppender,
                                      ExecModeProvider execModeProvider,
                                      AppActionCommandRunner appActionCommandRunner,
                                      ToolCommandStager toolCommandStager,
                                      ShellAppendRunner shellAppendRunner,
                                      MemoryTabVisibleProvider memoryTabVisibleProvider) {
        this.activity = activity;
        this.bindingProvider = bindingProvider;
        this.prefsProvider = prefsProvider;
        this.selfPackageName = selfPackageName;
        this.publicBinDir = publicBinDir;
        this.selectedPackageProvider = selectedPackageProvider;
        this.installedAppsProvider = installedAppsProvider;
        this.dropdownEndIconBinder = dropdownEndIconBinder;
        this.tapOnlyDropdownBinder = tapOnlyDropdownBinder;
        this.dropdownTweaker = dropdownTweaker;
        this.dropdownShower = dropdownShower;
        this.backgroundExecutor = backgroundExecutor;
        this.uiExecutor = uiExecutor;
        this.outputAppender = outputAppender;
        this.execModeProvider = execModeProvider;
        this.appActionCommandRunner = appActionCommandRunner;
        this.toolCommandStager = toolCommandStager;
        this.shellAppendRunner = shellAppendRunner;
        this.memoryTabVisibleProvider = memoryTabVisibleProvider;
    }

    Activity getActivity() {
        return activity;
    }

    ActivityMainBinding getBinding() {
        return bindingProvider == null ? null : bindingProvider.getBinding();
    }

    SharedPreferences getPreferences() {
        return prefsProvider == null ? null : prefsProvider.getPreferences();
    }

    String getSelfPackageName() {
        return selfPackageName;
    }

    String getPublicBinDir() {
        return publicBinDir;
    }

    String getSelectedPackage() {
        return selectedPackageProvider == null ? null : selectedPackageProvider.getSelectedPackage();
    }

    List<PackageDropdownEntry> getInstalledApps() {
        return installedAppsProvider == null ? null : installedAppsProvider.getInstalledApps();
    }

    void bindDropdownEndIcon(TextInputLayout layout, Runnable openAction) {
        if (dropdownEndIconBinder != null) dropdownEndIconBinder.bind(layout, openAction);
    }

    void bindTapOnlyDropdown(AutoCompleteTextView dropdown, int touchSlop, int timeoutMs, Runnable openAction) {
        if (tapOnlyDropdownBinder != null) tapOnlyDropdownBinder.bind(dropdown, touchSlop, timeoutMs, openAction);
    }

    void applyDropdownTweaks(AutoCompleteTextView dropdown) {
        if (dropdownTweaker != null) dropdownTweaker.apply(dropdown);
    }

    void showDropdown(AutoCompleteTextView dropdown, String lastText) {
        if (dropdownShower != null) dropdownShower.show(dropdown, lastText);
    }

    void executeInBackground(Runnable task) {
        if (backgroundExecutor != null) backgroundExecutor.execute(task);
    }

    void executeOnUi(Runnable task) {
        if (uiExecutor != null) uiExecutor.execute(task);
    }

    void appendOutput(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }

    ExecMode getExecMode() {
        return execModeProvider == null ? ExecMode.SYSTEM : execModeProvider.getExecMode();
    }

    MemoryAppActions.CommandResult runAppActionCommand(String command) {
        return appActionCommandRunner == null ? null : appActionCommandRunner.run(command);
    }

    MemoryToolCommandRunner.CommandResult stageTool(String name) {
        return toolCommandStager == null ? null : toolCommandStager.stage(name);
    }

    void runShellCommandAndAppend(String command) {
        if (shellAppendRunner != null) shellAppendRunner.runAndAppend(command);
    }

    boolean isMemoryTabVisible() {
        return memoryTabVisibleProvider != null && memoryTabVisibleProvider.isVisible();
    }

    public interface BindingProvider {
        ActivityMainBinding getBinding();
    }

    public interface SharedPreferencesProvider {
        SharedPreferences getPreferences();
    }

    public interface SelectedPackageProvider {
        String getSelectedPackage();
    }

    public interface InstalledAppsProvider {
        List<PackageDropdownEntry> getInstalledApps();
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

    public interface DropdownShower {
        void show(AutoCompleteTextView dropdown, String lastText);
    }

    public interface BackgroundExecutor {
        void execute(Runnable task);
    }

    public interface UiExecutor {
        void execute(Runnable task);
    }

    public interface OutputAppender {
        void append(String text);
    }

    public interface ExecModeProvider {
        ExecMode getExecMode();
    }

    public interface AppActionCommandRunner {
        MemoryAppActions.CommandResult run(String command);
    }

    public interface ToolCommandStager {
        MemoryToolCommandRunner.CommandResult stage(String name);
    }

    public interface ShellAppendRunner {
        void runAndAppend(String command);
    }

    public interface MemoryTabVisibleProvider {
        boolean isVisible();
    }
}
