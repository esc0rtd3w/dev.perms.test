package dev.perms.test.packages;

import android.content.SharedPreferences;

import java.io.File;

/**
 * Owns package-install script options and command preparation.
 */
public final class PackageInstallScriptController {
    public interface Host {
        SharedPreferences getPrefs();
        boolean isInstallerScriptUiChecked();
        boolean shouldUseAndroidDataInstallPath();
        boolean shouldUseInstallStagingFolder();
        boolean shouldSkipInstallStagingForLargeFiles();
        boolean shouldBypassLowTargetSdkBlock();
        boolean shouldIgnoreDexoptProfile();
        String prepareInstallInputPathForPmSync(String sourcePath);
        void appendOutput(String text);
    }

    private final Host host;
    private final String useInstallerScriptKey;

    public PackageInstallScriptController(Host host, String useInstallerScriptKey) {
        this.host = host;
        this.useInstallerScriptKey = useInstallerScriptKey;
    }

    public boolean isScriptRequested() {
        if (host == null) return false;
        try {
            boolean checked = host.isInstallerScriptUiChecked();
            SharedPreferences prefs = host.getPrefs();
            if (prefs != null && useInstallerScriptKey != null) {
                prefs.edit().putBoolean(useInstallerScriptKey, checked).apply();
            }
            return checked;
        } catch (Throwable ignored) {
            try {
                SharedPreferences prefs = host.getPrefs();
                return prefs != null
                        && useInstallerScriptKey != null
                        && prefs.getBoolean(useInstallerScriptKey, false);
            } catch (Throwable ignored2) {
                return false;
            }
        }
    }

    public String prepareInputPathForScript(String sourcePath) {
        if (host == null) return sourcePath;
        if (!PackageInstallCommands.shouldForcePmReadableRestage(
                sourcePath,
                host.shouldUseAndroidDataInstallPath())) {
            return sourcePath;
        }
        host.appendOutput("[i] Restaging script install input for pm/system_server access.\n");
        return host.prepareInstallInputPathForPmSync(sourcePath);
    }

    public String buildScriptCommand(File scriptFile, String inputPath) {
        if (host == null) {
            return PackageInstallCommands.buildInstallScriptCommand(
                    scriptFile,
                    inputPath,
                    false,
                    false,
                    false,
                    false,
                    false);
        }
        return PackageInstallCommands.buildInstallScriptCommand(
                scriptFile,
                inputPath,
                host.shouldUseAndroidDataInstallPath(),
                host.shouldUseInstallStagingFolder(),
                host.shouldSkipInstallStagingForLargeFiles(),
                host.shouldBypassLowTargetSdkBlock(),
                host.shouldIgnoreDexoptProfile());
    }
}
