package dev.perms.test.tutorial;

import java.util.Arrays;
import java.util.List;

import dev.perms.test.R;

/** Central tutorial content list. Add or reorder steps here without touching MainActivity. */
public final class TutorialCatalog {
    private TutorialCatalog() {
    }

    public static TutorialTabSpec forTab(int tabIndex) {
        switch (tabIndex) {
            case 0:
                return spec(tabIndex, "main", "Main", Arrays.asList(
                        step(R.id.cardMainBackend, "Choose a backend", "This section controls Shizuku, Internal Shizuku, LADB, and System mode. Pick the backend first because most tools follow this status."),
                        step(R.id.includeExecModeSelector, "Execution mode", "Use the Exec Mode selector when switching between Installed Shizuku, Internal Shizuku, LADB, or normal System mode."),
                        step(R.id.cardMainHome, "Home launcher options", "Home Mode can make PermsTest act like a launcher while keeping backend controls separate above.")
                ));
            case 1:
                return spec(tabIndex, "shell", "Shell", Arrays.asList(
                        step(R.id.tilCmd, "Command box", "Type one-off shell commands here. They run through the currently selected backend when it is ready."),
                        step(R.id.cardShellCommands, "Quick commands", "These safe command buttons are grouped diagnostics for common Android, package, storage, and network checks."),
                        step(R.id.cardSystemBinaries, "Scanned binaries", "PermsTest scans available command-line tools and exposes matching actions here without changing the command runner.")
                ));
            case 2:
                return spec(tabIndex, "packages", "Packages", Arrays.asList(
                        step(R.id.cardApkInstaller, "APK installer", "Pick APK/APKS/APKM/XAPK files here, then install using the selected staging and split options."),
                        step(R.id.tilAppDropdown, "Installed package dropdown", "Select a package to launch, inspect, extract, clear, force stop, or manage permissions."),
                        step(R.id.cardApkEditor, "APK Editor", "Use APK Editor to inspect packages, manage smali workspaces, rename apps, rebuild APKs, and sign test outputs.")
                ));
            case 3:
                return spec(tabIndex, "memory", "Memory", Arrays.asList(
                        step(R.id.btnMemoryOpenOverlay, "Memory overlay", "Open the scanner overlay for active games or apps. VR-compatible panel mode stays gated separately in settings."),
                        step(R.id.tilMemoryTargetPkg, "Target package", "Choose the target package first. Running-only filters help avoid attaching to the wrong process."),
                        step(R.id.btnMemoryAttach, "Attach workflow", "After staging tools and picking a process, attach before scanning, patching, or opening live hex/disassembly tools.")
                ));
            case 4:
                return spec(tabIndex, "files", "Files", Arrays.asList(
                        step(R.id.chkFilesUseShizuku, "File access mode", "Enable Shizuku file access when you need privileged paths. Normal Android file access stays available for regular storage."),
                        step(R.id.chkFilesSplit, "Split-pane browsing", "Split view lets you copy or move between two folder panes while preserving the active side state."),
                        step(R.id.chkFilesOpenKnown, "Open known files", "When enabled, recognized files can route to package install, external handlers, or file tools from the browser.")
                ));
            case 5:
                return spec(tabIndex, "network", "Network", Arrays.asList(
                        step(R.id.btnFtpStart, "FTP server", "Start FTP when you want file access from another device. Background and sleep behavior remain behind explicit checkboxes."),
                        step(R.id.btnHttpStart, "HTTP server", "HTTP serves the configured root and can host the Web Interface only when those gates are enabled."),
                        step(R.id.btnNetworkIpInfo, "Network diagnostics", "Use these buttons for IP, routes, DNS, sockets, connectivity, and host checks without leaving the app.")
                ));
            case 6:
                return spec(tabIndex, "scripts", "Scripts", Arrays.asList(
                        step(R.id.tilScriptDropdown, "Script selector", "Pick bundled or user scripts here. User scripts live under the shared PermsTest scripts folder."),
                        step(R.id.btnRunScript, "Run script", "Run executes the selected script through the same backend path as shell commands."),
                        step(R.id.tilScriptBody, "Script body", "Edit script text here, then save or run the current version from this tab.")
                ));
            case 7:
                return spec(tabIndex, "debugging", "Debugging", Arrays.asList(
                        step(R.id.tilDebuggingInstalledPackage, "Debug target", "Choose an installed app or APK source before smali, DEX, MITM, or report actions."),
                        step(R.id.btnSmaliDisassemble, "Smali tools", "Disassemble and assemble DEX/APK content from this section while keeping editor workspaces separate."),
                        step(R.id.cardSmaliEditor, "Smali editor", "Open, search, page, edit, and save smali files here without forcing huge files into one giant text view.")
                ));
            case 8:
                return spec(tabIndex, "tools", "Tools", Arrays.asList(
                        step(R.id.cardFileHexEditor, "File Hex Editor", "This editor loads bounded file windows so large files do not become giant UI-thread text blocks."),
                        step(R.id.cardToolsTextEditor, "Text Editor", "Use this for shell, JSON, web, smali, or plain text files with the shared virtual editor path."),
                        step(R.id.cardMultiplayerLink, "Multiplayer Link", "Link mode is gated by settings and shared-object checkboxes before remote actions are allowed.")
                ));
            case 9:
                return spec(tabIndex, "logging", "Logging", Arrays.asList(
                        step(R.id.btnLogcat, "Logcat capture", "Capture filtered logcat output here. Save To File preserves raw diagnostic output when enabled."),
                        step(R.id.btnSaveOutput, "Save Output", "Save the entire currently visible shared PermsTest output pane to a timestamped text file."),
                        step(R.id.btnLifetimeView, "Lifetime log", "Lifetime logs track app sessions separately from one-off logcat captures.")
                ));
            case 10:
                return spec(tabIndex, "plugins", "Plugins", Arrays.asList(
                        step(R.id.cardPluginsManager, "Plugin manager", "Install bundled, .ptp, or .zip plugins here. Valid plugins are unpacked under the shared PermsTest plugins folder."),
                        step(R.id.llPluginsBundledList, "Bundled plugin picker", "Bundled choices start unchecked. Check the demos or tools you want, then tap Restore Selected."),
                        step(R.id.cardPluginsInstalled, "Plugin cards", "Each enabled plugin gets an icon-aware tinted card and action buttons. The X button disables that plugin without changing normal PermsTest behavior."),
                        step(R.id.cardPluginsEditor, "Plugin Editor", "Create or import plugins, manage multiple action types and general assets, edit top-level and selected-container declarative UI controls, preview ui.json safely, validate the advanced JSON, save staged folders, and package them as .ptp files.")
                ));
            case 11:
                return spec(tabIndex, "settings", "Settings", Arrays.asList(
                        step(R.id.chkDisableTutorial, R.id.rowDisableTutorial, "Tutorial controls", "Turn Disable Tutorial on to stop first-run tab tips, or use Reset Tutorial to show them again after testing."),
                        step(R.id.chkDetectVrMode, "VR gate", "Enable VR Mode is the explicit gate for VR-compatible UI paths so tablet and phone behavior stays separate."),
                        step(R.id.chkAutoCollapseGroupboxes, R.id.rowAutoCollapseGroupboxes, "Groupbox profiles", "Auto Collapse Groupboxes applies startup layouts while manual expand/collapse changes still save to the User profile.")
                ));
            case 12:
                return spec(tabIndex, "about", "About", Arrays.asList(
                        step(R.id.txtAboutVersion, "Version", "The visible version helps match screenshots, logs, and test builds."),
                        step(R.id.btnCheckForUpdates, "Check for updates", "Manual update checks use the selected release channel and show user-visible results."),
                        step(R.id.btnOpenHelp, "Help", "The help dialog gives quick app guidance without changing backend state.")
                ));
            default:
                return null;
        }
    }

    private static TutorialTabSpec spec(int tabIndex, String key, String title, List<TutorialStep> steps) {
        return new TutorialTabSpec(tabIndex, key, title, steps);
    }

    private static TutorialStep step(int targetId, String title, String message) {
        return new TutorialStep(targetId, title, message);
    }

    private static TutorialStep step(int targetId, int highlightId, String title, String message) {
        return new TutorialStep(targetId, highlightId, title, message);
    }
}
