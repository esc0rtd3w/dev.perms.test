package dev.perms.test.packages;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.AutoCompleteTextView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.PackageDropdownEntry;

/**
 * Activity-side owner for Packages tab app dropdown, package info, and package action wiring.
 *
 * <p>MainActivity supplies shared app services through Host while this package-owned bridge keeps
 * package UI/controller lifecycle out of the activity.</p>
 */
public final class PackagesUiActivityController {
    public interface Host {
        Context getContext();
        ActivityMainBinding getBinding();
        Handler getMainHandler();
        ExecutorService getExecutor();
        boolean isReadyAndGranted();
        boolean isSafeToken(String value);
        void refreshStatus();
        void appendOutput(String text);
        void setLastOutputTag(String tag);
        void runShellCommand(String command);
        void runShellCommandCapture(String command, PackageActions.ShellCaptureCallback callback);
        void executeIo(Runnable task);
        void runOnUiThread(Runnable task);
        void configureSafeDropdownEndIcon(TextInputLayout layout, Runnable onClick);
        void configureTapOnlyDropdownField(AutoCompleteTextView view, int touchSlop, int maxTapMs, Runnable onTap);
        void showDropdownAtLastSelection(AutoCompleteTextView view, String lastText);
        void rememberPackageToolsTargetPackage(String packageName);
        boolean usesAppPermissions();
        void refreshPermissionsForPackage(String packageName);
        void refreshHomeAppTray();
        void extractInstalledPackage(String packageName, String displayName);
        int colorDangerous();
        int colorSignature();
        int colorGranted();
        int colorRevoked();
        int colorMuted();
        boolean colorizeAppDropdown();
    }

    private final Host host;
    private PackageActions packageActions;
    private PackageAppDropdownController packageAppDropdownController;
    private PackageInfoPanelController packageInfoPanelController;

    public PackagesUiActivityController(Host host) {
        this.host = host;
    }

    public void bindPackageToolButtons() {
        getPackageActions().bindPackageToolButtons();
    }

    public void setupAppDropdown(String restoreFilterText) {
        getPackageAppDropdownController().setup(restoreFilterText);
    }

    public void setupTargetPackageWatchers() {
        getPackageInfoPanelController().setup();
    }

    public void updatePackageInfoSoon() {
        getPackageInfoPanelController().updateSoon();
    }

    public ArrayList<PackageDropdownEntry> snapshotAllApps() {
        return getPackageAppDropdownController().snapshotAllApps();
    }

    public String getSelectedPackageToolsPackage() {
        return getPackageAppDropdownController().getSelectedPackageToolsPackage();
    }

    public List<PackageDropdownEntry> getAllApps() {
        return getPackageAppDropdownController().getAllApps();
    }


    public void setColorizeAppDropdown(boolean enabled) {
        if (packageAppDropdownController != null) {
            packageAppDropdownController.setColorizeAppDropdown(enabled);
        }
    }

    public PackageAppDropdownController getPackageAppDropdownController() {
        if (packageAppDropdownController == null) {
            packageAppDropdownController = new PackageAppDropdownController(
                    host == null ? null : host.getContext(),
                    host == null ? null : host.getBinding(),
                    new PackageAppDropdownController.Host() {
                        @Override
                        public void executeIo(Runnable task) {
                            if (host != null) host.executeIo(task);
                        }

                        @Override
                        public void runOnUiThread(Runnable task) {
                            if (host != null) host.runOnUiThread(task);
                        }

                        @Override
                        public void preservePackagesScrollPosition(Runnable action) {
                            PackagesUiActivityController.this.preservePackagesScrollPosition(action);
                        }

                        @Override
                        public void setPackagesSpinnerVisible(boolean visible) {
                            PackagesUiActivityController.this.setPackagesSpinnerVisible(visible);
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
                        public void rememberPackageToolsTargetPackage(String packageName) {
                            if (host != null) host.rememberPackageToolsTargetPackage(packageName);
                        }

                        @Override
                        public void updatePackageInfoSoon() {
                            PackagesUiActivityController.this.updatePackageInfoSoon();
                        }
                    },
                    host != null && host.colorizeAppDropdown(),
                    host == null ? 0 : host.colorGranted(),
                    host == null ? 0 : host.colorRevoked());
        }
        return packageAppDropdownController;
    }

    private PackageActions getPackageActions() {
        if (packageActions == null) {
            packageActions = new PackageActions(
                    host == null ? null : host.getContext(),
                    host == null ? null : host.getBinding(),
                    new PackageActions.Host() {
                        @Override
                        public boolean isReadyAndGranted() {
                            return host != null && host.isReadyAndGranted();
                        }

                        @Override
                        public boolean isSafeToken(String value) {
                            return host != null && host.isSafeToken(value);
                        }

                        @Override
                        public void refreshStatus() {
                            if (host != null) host.refreshStatus();
                        }

                        @Override
                        public void appendOutput(String text) {
                            if (host != null) host.appendOutput(text);
                        }

                        @Override
                        public void setLastOutputTag(String tag) {
                            if (host != null) host.setLastOutputTag(tag);
                        }

                        @Override
                        public void runShellCommand(String command) {
                            if (host != null) host.runShellCommand(command);
                        }

                        @Override
                        public void runShellCommandCapture(String command, PackageActions.ShellCaptureCallback callback) {
                            if (host != null) host.runShellCommandCapture(command, callback);
                        }

                        @Override
                        public int findPackageDropdownIndex(String packageName) {
                            return getPackageAppDropdownController().findIndexByPackage(packageName);
                        }

                        @Override
                        public String getPackageFilterText() {
                            return PackagesUiActivityController.this.getPackageFilterText();
                        }

                        @Override
                        public void refreshAfterUninstall(int removedIndex, String filterText) {
                            getPackageAppDropdownController().refreshAfterUninstall(removedIndex, filterText);
                        }

                        @Override
                        public void refreshEnabledStateForPackage(String packageName) {
                            getPackageAppDropdownController().refreshEnabledStateForPackage(packageName);
                        }

                        @Override
                        public void refreshPackageDropdown() {
                            setupAppDropdown(null);
                        }

                        @Override
                        public void refreshHomeAppTray() {
                            if (host != null) host.refreshHomeAppTray();
                        }

                        @Override
                        public void extractInstalledPackage(String packageName, String displayName) {
                            if (host != null) host.extractInstalledPackage(packageName, displayName);
                        }
                    });
        }
        return packageActions;
    }

    private PackageInfoPanelController getPackageInfoPanelController() {
        if (packageInfoPanelController == null) {
            packageInfoPanelController = new PackageInfoPanelController(
                    host == null ? null : host.getContext(),
                    host == null ? null : host.getBinding(),
                    host == null ? null : host.getMainHandler(),
                    host == null ? null : host.getExecutor(),
                    new PackageInfoPanelController.Host() {
                        @Override
                        public void rememberPackageToolsTargetPackage(String packageName) {
                            if (host != null) host.rememberPackageToolsTargetPackage(packageName);
                        }

                        @Override
                        public void setPackagesSpinnerVisible(boolean visible) {
                            PackagesUiActivityController.this.setPackagesSpinnerVisible(visible);
                        }

                        @Override
                        public void preservePackagesScrollPosition(Runnable update) {
                            PackagesUiActivityController.this.preservePackagesScrollPosition(update);
                        }

                        @Override
                        public void runOnUiThread(Runnable action) {
                            if (host != null) host.runOnUiThread(action);
                        }

                        @Override
                        public boolean usesAppPermissions() {
                            return host != null && host.usesAppPermissions();
                        }

                        @Override
                        public void refreshPermissionsForPackage(String packageName) {
                            if (host != null) host.refreshPermissionsForPackage(packageName);
                        }
                    },
                    host == null ? 0 : host.colorDangerous(),
                    host == null ? 0 : host.colorSignature(),
                    host == null ? 0 : host.colorGranted(),
                    host == null ? 0 : host.colorRevoked(),
                    host == null ? 0 : host.colorMuted());
        }
        return packageInfoPanelController;
    }

    private String getPackageFilterText() {
        try {
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            return binding == null || binding.tabPackages == null || binding.tabPackages.edtAppFilter == null
                    ? ""
                    : String.valueOf(binding.tabPackages.edtAppFilter.getText());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void setPackagesSpinnerVisible(boolean visible) {
        try {
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            if (binding != null && binding.tabPackages != null && binding.tabPackages.pbPackagesLoading != null) {
                binding.tabPackages.pbPackagesLoading.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private void preservePackagesScrollPosition(Runnable update) {
        if (update == null) return;
        final View root;
        try {
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            root = binding == null || binding.tabPackages == null ? null : binding.tabPackages.getRoot();
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
        root.post(() -> {
            try { root.scrollTo(scrollX, scrollY); } catch (Throwable ignored) {}
            root.postDelayed(() -> {
                try { root.scrollTo(scrollX, scrollY); } catch (Throwable ignored) {}
            }, 80L);
        });
    }
}
