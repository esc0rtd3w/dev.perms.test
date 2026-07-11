package dev.perms.test.packages;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.databinding.ActivityMainBinding;
import rikka.shizuku.Shizuku;

/**
 * Owns package install file-pick, file-open, and selected-APK UI state for MainActivity.
 *
 * <p>The actual install execution stays in {@link PackagePickedInstallCoordinator}; this controller
 * only coordinates the Packages-tab picker/selection lifecycle and file-open handoff.</p>
 */
public final class PackageInstallActivityController {
    public interface Host {
        AppCompatActivity getActivity();
        ActivityMainBinding getBinding();
        SharedPreferences getPreferences();
        String queryDisplayName(Uri uri);
        void installPickedPackageFile(Uri uri, String label, boolean fromFileOpen);
        void showPackagesInstallerCard();
        void appendOutput(String text);
    }

    private final Host host;
    private final String hideFileOpenUiKey;
    private final String useInstallerScriptKey;
    private PackageInstallSelectionController selectionController;
    private PackageInstallPickerController pickerController;

    public PackageInstallActivityController(Host host,
                                            String hideFileOpenUiKey,
                                            String useInstallerScriptKey) {
        this.host = host;
        this.hideFileOpenUiKey = hideFileOpenUiKey;
        this.useInstallerScriptKey = useInstallerScriptKey;
    }

    public void registerActivityResults() {
        getPickerController().registerActivityResults();
    }

    public void setup() {
        setupInstallerScriptToggle();
        getPickerController().setup();
    }

    public boolean handleFileOpenIntent(Intent intent) {
        return PackageFileOpenIntentHandler.handle(
                host == null ? null : host.getActivity(),
                host == null ? null : host.getBinding(),
                intent,
                hideFileOpenUiKey,
                new PackageFileOpenIntentHandler.Host() {
                    @Override
                    public SharedPreferences getPreferences() {
                        return host == null ? null : host.getPreferences();
                    }

                    @Override
                    public String queryDisplayName(Uri uri) {
                        return host == null ? null : host.queryDisplayName(uri);
                    }

                    @Override
                    public void setOpenedFromFileHandler(boolean opened) {
                        getSelectionController().setOpenedFromFileHandler(opened);
                    }

                    @Override
                    public void setPickedApk(Uri uri, String label) {
                        getSelectionController().setPickedApk(uri, label);
                    }

                    @Override
                    public void setPendingAutoInstall(Uri uri, String label) {
                        getSelectionController().setPendingAutoInstall(uri, label);
                    }

                    @Override
                    public void tryAutoInstallPendingFile() {
                        PackageInstallActivityController.this.tryAutoInstallPendingFile();
                    }

                    @Override
                    public void showPackagesTab() {
                        if (host != null) host.showPackagesInstallerCard();
                    }

                    @Override
                    public void appendOutput(String text) {
                        if (host != null) host.appendOutput(text);
                    }
                });
    }

    public void clearSelectionUi() {
        getSelectionController().clearSelectionUi();
    }

    public void tryAutoInstallPendingFile() {
        getSelectionController().tryAutoInstallPendingFile();
    }

    public void maybeAutoInstallPendingFile(boolean binderAlive, boolean granted) {
        getSelectionController().maybeAutoInstallPendingFile(binderAlive, granted);
    }

    public Uri getPickedApkUri() {
        return getSelectionController().getPickedApkUri();
    }

    public String getPickedApkLabel() {
        return getSelectionController().getPickedApkLabel();
    }

    private PackageInstallSelectionController getSelectionController() {
        if (selectionController == null) {
            selectionController = new PackageInstallSelectionController(new PackageInstallSelectionController.Host() {
                @Override
                public Activity getActivity() {
                    return host == null ? null : host.getActivity();
                }

                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public boolean isBackendReadyAndGranted() {
                    return PackageInstallActivityController.this.isBackendReadyAndGranted();
                }

                @Override
                public void installPickedPackageFile(Uri uri, String label, boolean fromFileOpen) {
                    if (host != null) host.installPickedPackageFile(uri, label, fromFileOpen);
                }
            });
        }
        return selectionController;
    }

    private PackageInstallPickerController getPickerController() {
        if (pickerController == null) {
            pickerController = new PackageInstallPickerController(host == null ? null : host.getActivity(), new PackageInstallPickerController.Host() {
                @Override
                public ActivityMainBinding getBinding() {
                    return host == null ? null : host.getBinding();
                }

                @Override
                public String queryDisplayName(Uri uri) {
                    return host == null ? null : host.queryDisplayName(uri);
                }

                @Override
                public void setOpenedFromFileHandler(boolean opened) {
                    getSelectionController().setOpenedFromFileHandler(opened);
                }

                @Override
                public void setPickedApk(Uri uri, String label) {
                    getSelectionController().setPickedApk(uri, label);
                }

                @Override
                public Uri getPickedApkUri() {
                    return getSelectionController().getPickedApkUri();
                }

                @Override
                public String getPickedApkLabel() {
                    return getSelectionController().getPickedApkLabel();
                }

                @Override
                public boolean wasOpenedFromFileHandler() {
                    return getSelectionController().wasOpenedFromFileHandler();
                }

                @Override
                public void installPickedPackageFile(Uri uri, String label, boolean fromFileOpen) {
                    if (host != null) host.installPickedPackageFile(uri, label, fromFileOpen);
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }
            });
        }
        return pickerController;
    }

    private void setupInstallerScriptToggle() {
        try {
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            if (binding == null || binding.tabPackages == null || binding.tabPackages.chkUseInstallerScript == null) return;
            SharedPreferences prefs = host.getPreferences();
            boolean useScript = prefs != null && prefs.getBoolean(useInstallerScriptKey, false);
            binding.tabPackages.chkUseInstallerScript.setChecked(useScript);
            binding.tabPackages.chkUseInstallerScript.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    SharedPreferences p = host == null ? null : host.getPreferences();
                    if (p != null) p.edit().putBoolean(useInstallerScriptKey, isChecked).apply();
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private boolean isBackendReadyAndGranted() {
        boolean binderAlive;
        try {
            binderAlive = Shizuku.pingBinder();
        } catch (Throwable ignored) {
            binderAlive = false;
        }
        if (!binderAlive) return false;
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
