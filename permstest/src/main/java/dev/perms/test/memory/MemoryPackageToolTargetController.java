package dev.perms.test.memory;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.PackageDropdownEntry;

/**
 * Activity-side coordinator for syncing the Packages tab target into Memory tools.
 *
 * MainActivity supplies the current UI state and selected Memory target. This controller owns
 * the package-target resolution order, remembered-target fallback, and the Memory target sync
 * button behavior shared by the Packages and Memory tabs.
 */
public final class MemoryPackageToolTargetController {
    public interface SelectedPackageProvider {
        String getSelectedPackage();
    }

    public interface InstalledAppsProvider {
        List<PackageDropdownEntry> getInstalledApps();
    }

    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    public interface TargetPackageSetter {
        void setTargetPackage(String pkg);
    }

    public interface TargetPackageItemsProvider {
        ArrayList<MemoryPackageEntry> getPackageItems();
    }

    public interface ExcludeSelfProvider {
        boolean shouldExcludeSelfPackage();
    }

    public interface ProcessRefreshCallback {
        void refresh(boolean userInitiated);
    }

    private final Context context;
    private final ActivityMainBinding binding;
    private final SharedPreferences prefs;
    private final String selfPackageName;
    private final SelectedPackageProvider selectedPackageProvider;
    private final InstalledAppsProvider installedAppsProvider;
    private final TargetPackageProvider targetPackageProvider;
    private final TargetPackageSetter targetPackageSetter;
    private final TargetPackageItemsProvider targetPackageItemsProvider;
    private final ExcludeSelfProvider excludeSelfProvider;
    private final ProcessRefreshCallback processRefreshCallback;

    public MemoryPackageToolTargetController(Context context,
                                             ActivityMainBinding binding,
                                             SharedPreferences prefs,
                                             String selfPackageName,
                                             SelectedPackageProvider selectedPackageProvider,
                                             InstalledAppsProvider installedAppsProvider,
                                             TargetPackageProvider targetPackageProvider,
                                             TargetPackageSetter targetPackageSetter,
                                             TargetPackageItemsProvider targetPackageItemsProvider,
                                             ExcludeSelfProvider excludeSelfProvider,
                                             ProcessRefreshCallback processRefreshCallback) {
        this.context = context;
        this.binding = binding;
        this.prefs = prefs;
        this.selfPackageName = selfPackageName;
        this.selectedPackageProvider = selectedPackageProvider;
        this.installedAppsProvider = installedAppsProvider;
        this.targetPackageProvider = targetPackageProvider;
        this.targetPackageSetter = targetPackageSetter;
        this.targetPackageItemsProvider = targetPackageItemsProvider;
        this.excludeSelfProvider = excludeSelfProvider;
        this.processRefreshCallback = processRefreshCallback;
    }

    public String getCurrentPackageToolsTarget() {
        try {
            CharSequence selected = null;
            CharSequence typed = null;
            try {
                if (binding != null && binding.tabPackages != null && binding.tabPackages.ddApp != null) {
                    selected = binding.tabPackages.ddApp.getText();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (binding != null && binding.tabPackages != null && binding.tabPackages.edtTargetPkg != null) {
                    typed = binding.tabPackages.edtTargetPkg.getText();
                }
            } catch (Throwable ignored) {
            }
            return MemoryPackageToolTargets.resolveCurrentPackageToolsTarget(
                    getSelectedPackageToolsPackage(),
                    selected,
                    typed,
                    getInstalledApps(),
                    selfPackageName,
                    shouldExcludeSelfPackage()
            );
        } catch (Throwable ignored) {
            return "";
        }
    }

    public String getPreferredPackageToolsTarget() {
        try {
            return MemoryPackageToolTargets.resolvePreferredPackageToolsTarget(
                    getCurrentPackageToolsTarget(),
                    getRememberedPackageToolsTargetPackage(),
                    selfPackageName,
                    shouldExcludeSelfPackage()
            );
        } catch (Throwable ignored) {
            return "";
        }
    }

    public String resolveMemoryPackageText(String value) {
        return MemoryTargets.resolvePackageText(
                value,
                getMemoryTargetPackageItems(),
                selfPackageName,
                shouldExcludeSelfPackage()
        );
    }

    public void syncMemoryTargetFromPackageTools(boolean refreshProcesses) {
        String pkg = "";
        try {
            pkg = MemoryPackageToolTargets.resolveSyncedMemoryTarget(
                    getCurrentMemoryTargetPackage(),
                    getMemoryTargetPackageItems(),
                    getCurrentPackageToolsTarget(),
                    getRememberedPackageToolsTargetPackage(),
                    selfPackageName,
                    shouldExcludeSelfPackage()
            );
            if (TextUtils.isEmpty(pkg)) {
                Toast.makeText(context, MemoryPackageToolTargets.formatMissingPackageToolsTargetMessage(shouldExcludeSelfPackage()), Toast.LENGTH_SHORT).show();
                return;
            }
            setCurrentMemoryTargetPackage(pkg);
        } catch (Throwable ignored) {
        }
        if (refreshProcesses || !TextUtils.isEmpty(pkg)) {
            refreshProcesses(false);
        }
    }

    public void rememberPackageToolsTargetPackage(String pkg) {
        MemorySettings.setLastPackageToolsTarget(prefs, pkg);
    }

    public String getRememberedPackageToolsTargetPackage() {
        return MemorySettings.getLastPackageToolsTarget(prefs);
    }

    private String getSelectedPackageToolsPackage() {
        try {
            return selectedPackageProvider == null ? "" : selectedPackageProvider.getSelectedPackage();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private List<PackageDropdownEntry> getInstalledApps() {
        try {
            return installedAppsProvider == null ? null : installedAppsProvider.getInstalledApps();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String getCurrentMemoryTargetPackage() {
        try {
            return targetPackageProvider == null ? "" : targetPackageProvider.getTargetPackage();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void setCurrentMemoryTargetPackage(String pkg) {
        try {
            if (targetPackageSetter != null) targetPackageSetter.setTargetPackage(pkg);
        } catch (Throwable ignored) {
        }
    }

    private ArrayList<MemoryPackageEntry> getMemoryTargetPackageItems() {
        try {
            ArrayList<MemoryPackageEntry> items = targetPackageItemsProvider == null ? null : targetPackageItemsProvider.getPackageItems();
            return items == null ? new ArrayList<>() : items;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private boolean shouldExcludeSelfPackage() {
        try {
            return excludeSelfProvider != null && excludeSelfProvider.shouldExcludeSelfPackage();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void refreshProcesses(boolean userInitiated) {
        try {
            if (processRefreshCallback != null) processRefreshCallback.refresh(userInitiated);
        } catch (Throwable ignored) {
        }
    }
}
