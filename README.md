# PermsTest

**PermsTest** is an Android diagnostics, testing, package-management, file-management, network, memory, APK, debugging, plugin, logging, and VR-compatible utility suite. It is built for users who need one Android app that can combine normal Android APIs with optional Shizuku, Internal Shizuku, LADB-style wireless debugging, system-level shell paths, and root/Magisk access where those backends are available.

PermsTest does not bypass Android security by itself. Each action is gated by the selected execution mode, Android permissions, device policy, package state, file access, and the privileges actually granted on the device.

Current documented app version: **v1.0-20260710.06**

## Contents

- [Core model](#core-model)
- [Supported access backends](#supported-access-backends)
- [Main and startup controls](#main-and-startup-controls)
- [Kiosk and home launcher mode](#kiosk-and-home-launcher-mode)
- [Shell tab](#shell-tab)
- [Files tab](#files-tab)
- [Network tab](#network-tab)
- [Packages tab](#packages-tab)
- [APK installer](#apk-installer)
- [APK editor](#apk-editor)
- [Debugging tab](#debugging-tab)
- [DEX-to-Java output](#dex-to-java-output)
- [Smali workflows](#smali-workflows)
- [Memory tab](#memory-tab)
- [Memory payloads and shortcuts](#memory-payloads-and-shortcuts)
- [Tools tab](#tools-tab)
- [Plugins tab](#plugins-tab)
- [Logging tab](#logging-tab)
- [Settings tab](#settings-tab)
- [VR and popout panels](#vr-and-popout-panels)
- [Public storage layout](#public-storage-layout)
- [Bundled helper components](#bundled-helper-components)
- [Safety model](#safety-model)
- [Troubleshooting workflow](#troubleshooting-workflow)
- [Build from source](#build-from-source)
- [Repository layout](#repository-layout)
- [Attribution and upstream components](#attribution-and-upstream-components)
- [License](#license)

## Core model

PermsTest is organized around tabbed tool areas. Most tools share the same operating pattern:

1. Select or configure the target package, file, backend, path, server, script, plugin, or memory process.
2. Review the enabled controls. Disabled controls usually mean a required backend, package, path, file, setting, or editor input is missing.
3. Run the action explicitly from the UI.
4. Review the shared output pane for status, command output, exit codes, generated paths, saved reports, or error details.
5. Use Logging tools to save, export, archive, or share the output when needed.

The bottom output pane is the central status area. It is used by shell commands, package actions, file operations, servers, APK tools, debugging tasks, plugin actions, memory tools, and diagnostics. Scroll up to inspect earlier output. Return to the bottom to continue following new output.

PermsTest separates normal phone/tablet behavior from VR behavior. VR-specific UI changes are controlled by VR settings and should not affect normal device paths while VR mode is off.

## Supported access backends

PermsTest can run actions through several execution paths. The selected backend controls what privileged actions can do.

### Normal app access

Normal app mode uses Android APIs available to the app without Shizuku, wireless ADB, or root. It supports shared-storage workflows, file pickers, content URI handling, package installer intents, standard Android UI actions, and features that do not need privileged shell access.

### Shizuku

Shizuku mode uses the installed Shizuku service after Android grants PermsTest Shizuku permission. It is used for shell-level package, permission, and file operations that are available to the Shizuku service.

Typical Shizuku workflow:

1. Start Shizuku from the Shizuku app or supported startup method.
2. Grant PermsTest permission when Android asks.
3. Select Shizuku as the backend.
4. Tap Refresh after service or permission changes.
5. Run package, shell, file, or diagnostic actions that require shell privileges.

### Internal Shizuku

Internal Shizuku mode provides in-app wireless debugging pairing and server-start controls where supported by the device. It must show a ready binder before privileged actions should be expected to work. Wireless debugging ports can change, so reconnecting or refreshing may be needed after Android restarts debugging or the network changes.

### LADB-style wireless debugging

The LADB path uses Android wireless debugging pairing/connection behavior. It can run shell commands through a local ADB-style connection when pairing succeeds and Android keeps the wireless debugging session active.

### Root / Magisk

Root actions require a working `su` path and user approval through Magisk or the device root manager. Root-only diagnostics and protected paths stay gated behind root settings and explicit actions.

Root mode is not assumed globally. PermsTest should request root only when a root-gated action is tapped or when the selected feature explicitly requires it.

### System mode

System mode keeps actions within the normal app/system API path available to the installed app. It does not imply root, Shizuku, or device-owner privileges.

## Main and startup controls

The Main tab is the entry point for backend selection, quick status, launcher behavior, service state, package actions, and common panels.

Main tab capabilities include:

- Backend selection and status refresh.
- Shizuku, Internal Shizuku, LADB, root, and system-path status checks.
- Home launcher toggle for using PermsTest as the device launcher where Android allows it.
- Kiosk Mode entry and status.
- App tray and package shortcut handling.
- Quick access to common tools and feature panels.
- Popout groupbox support when enabled in Settings.
- Startup intent routing for files opened from other apps.
- Device default application for phone, tablet, and VR-compatible profiles.

Startup behavior includes:

- Binding the app UI and tab controllers.
- Applying device UI defaults when enabled.
- Registering launcher/file-open handlers where enabled.
- Restoring cached preferences.
- Loading plugin state after core startup checks.
- Refreshing package lists and backend state.
- Routing external file-open intents to the correct editor, installer, or viewer.

## Kiosk and home launcher mode

PermsTest includes a kiosk launcher flow for controlled access to selected apps and shortcuts.

Kiosk features include:

- Enable or disable Kiosk Mode from the Main tab.
- Configure allowed kiosk apps in Settings > Kiosk.
- Add installed launchable apps to the allowed list.
- Add custom shortcuts where supported.
- Drag allowed items in Settings to control kiosk launcher order.
- Remove allowed items from the kiosk list.
- Enable or disable individual allowed items.
- Configure kiosk icon size manually.
- Enable **Auto Size Icons** to size launcher icons based on the number of active kiosk items and available screen area.
- Keep manual icon mode scrollable when Auto Size Icons is off.
- Keep Auto Size Icons non-scrollable so active icons are intended to stay visible and centered.
- Use Immersive Fullscreen in kiosk.
- Use Lock Task / Screen Pinning Mode where Android allows it.
- Use an exit pattern based on blank kiosk background taps.
- Use a recovery exit pattern when needed.
- Optionally use hardware button bypass with Volume Up + Volume Down.
- Use `.off` marker files as safety exits for launcher/kiosk recovery paths.

Kiosk touch behavior is designed so tapping the actual kiosk icon/card launches the app while blank grid/background space remains available for kiosk background taps and exit-pattern input.

Lock Task and screen pinning behavior depends on Android version, device policy, whether PermsTest is a device owner or normal app, and whether the user has allowed screen pinning. PermsTest can request supported modes, but Android controls the final system behavior.

## Shell tab

The Shell tab runs commands through the selected backend and displays stdout, stderr, and exit code in the shared output pane.

Shell capabilities include:

- Run ad-hoc shell commands.
- Use quick command buttons for common Android diagnostics.
- Save custom commands.
- Group and organize custom commands.
- Reorder custom commands.
- Import and export custom command sets.
- Fill command text from saved entries.
- Scan available device binaries.
- Scan bundled helper binaries.
- Run commands through Shizuku, Internal Shizuku, LADB, root, or normal mode as supported.
- Capture command output for logs or reports.

Shell actions can change device or package state. Review commands that write, delete, reset, uninstall, grant, revoke, enable, disable, or modify files before running them.

## Files tab

The Files tab provides shared-storage and backend-assisted file management.

Files capabilities include:

- Browse shared storage.
- Browse protected paths where the selected backend supports access.
- Open files in internal viewers/editors.
- Open supported files through Android handlers.
- Send APK-like files to APK install workflows.
- Copy, cut, paste, rename, and delete files/folders.
- Show file paths and properties.
- Use long-press context actions.
- Use split-pane transfers between two file locations.
- Stage files selected through Android content pickers when a normal filesystem path is needed.
- Use root or Shizuku paths for protected file operations where available.
- Run optional directory-size scans.

Supported file workflows include regular text/source files, APK/APKS/APKM/XAPK packages, smali files, JSON/XML-like files, generated debugging output, plugin files, scripts, and logs.

## Network tab

The Network tab collects local-network servers, clients, remote access helpers, diagnostics, and web controls.

Network capabilities include:

- FTP Server.
- FTP Client.
- HTTP Server.
- Web Interface.
- SSH Server.
- SSH Client.
- Multiplayer Link testing.
- Host/network tests.
- HTTP request testing.
- TCP port checks.
- Local IP/status display.
- Service status refresh.
- Background/sleep operation controls where supported.

Network tools should be used only on trusted networks. Remote access to files, shell, package controls, memory controls, or plugin controls should not be exposed to untrusted networks.

### FTP Server

FTP Server capabilities include:

- Start and stop a local FTP service.
- Configure the served root path.
- Browse served files from another device on the same network.
- Use foreground-service behavior where required by Android.
- Use background/sleep operation options where supported.
- View current server status and connection details.

### FTP Client

FTP Client capabilities include:

- Connect to remote FTP servers.
- Browse local and remote paths.
- Upload and download files.
- Rename and delete remote items where permitted.
- Create folders where permitted.
- Refresh local and remote lists.
- Track selected local/remote rows before transfer actions.

### HTTP Server

HTTP Server capabilities include:

- Serve a selected root folder.
- Serve or edit an index file where supported.
- Provide simple local-network access to shared files or generated output.
- Keep the service controlled from inside the app.

### Web Interface

The Web Interface exposes selected app sections remotely when enabled.

Web Interface behavior includes:

- Section-by-section access controls.
- Optional access to package, file, memory, network, logging, plugin, and shell-related controls depending on settings.
- FTP controls that respect current FTP running state.
- Hidden and rejected API calls for disabled sections.

### SSH Server

SSH Server capabilities include:

- Password-protected local-network SSH/SFTP access.
- Foreground-service operation with an ongoing notification.
- Configurable SFTP root.
- Host-key reset controls.
- Shell access disabled by default.
- App-owned shell behavior unless a backend explicitly routes an action elsewhere.

### SSH Client

SSH Client capabilities include:

- Connect to trusted SSH hosts.
- Run remote commands from the Network tab.
- Review output in the shared output pane.

## Packages tab

The Packages tab handles package inspection, permissions, state, installer workflows, activities, and package-state review.

Package capabilities include:

- List installed packages.
- Search/filter package lists.
- Inspect package details.
- Inspect permissions.
- Grant or revoke supported permissions through the selected backend.
- Enable or disable packages where the backend allows it.
- Save package state lists as JSON.
- Load package state lists.
- Compare All, Enabled, and Disabled state snapshots.
- Color-code loaded state rows.
- Use debloat presets as review lists.
- Confirm package enable/disable operations before applying.
- Inspect exported and non-exported activities.
- Launch exported activities where Android allows it.
- Launch with parameters where supported.
- Launch with root where supported.
- Create activity shortcuts.
- Hide or show non-exported/root-only actions according to settings.

Android platform rules still apply. Signature checks, protected permissions, managed-profile policy, target SDK rules, and OEM restrictions may block requested package actions.

## APK installer

APK installer workflows support common Android package formats and install options.

Installer capabilities include:

- Install single APK files.
- Install split package sets from APKS/APKM/XAPK-style sources.
- Review split choices before installing.
- Stage install files under the configured staging path.
- Use or skip staging according to settings.
- Allow downgrade where Android permits it.
- Use Android 15+ low target-SDK bypass controls where available and enabled.
- Use internal APK install behavior where enabled.
- Route APK-like files from file managers through the file-open handler.
- Capture installer output and failure details.

Installer limitations include:

- Allow Downgrade does not bypass signature mismatch.
- Target-SDK bypass does not bypass signature, ABI, package-name, version-code, or system policy failures.
- Replacing a package can affect app data. Back up important data before uninstall/replace flows.

## APK editor

The APK Editor provides inspect, decode, modify, rebuild, sign, and install-test workflows.

APK Editor capabilities include:

- Select APK sources.
- Inspect APK metadata.
- Preview AndroidManifest.xml.
- Extract split APK sources.
- Decode APKs with the bundled apktool-compatible backend.
- Rebuild APKs after supported edits.
- Sign rebuilt APKs.
- Install signed test builds.
- Rename supported manifest/app-label fields.
- Preview or patch manifest values where supported.
- Use binary XML patching helpers for focused manifest edits.
- Capture decode/build/sign/install output.
- Save logs under APK tool log locations.

APK editing notes:

- Manifest-only edits do not require a smali repack path.
- Smali changes require smali/decode/rebuild workflows.
- Resource-backed labels and complex resource references can require manual review after rebuild.
- Java output is not a rebuild source. Use smali for rebuildable changes.
- Keep original APKs and app data backups before installing modified packages.

## Debugging tab

The Debugging tab provides APK/DEX analysis, smali output, Java-view output, source browsing, editors, patch helpers, and diagnostic tooling.

Debugging capabilities include:

- Select APK or DEX sources.
- Select DEX entries from APK sources.
- List classes and methods where supported.
- Generate smali output.
- Search smali workspaces.
- Open smali files in the smali editor.
- Edit smali files.
- Assemble/rebuild smali outputs where supported.
- Export rebuilt APKs where supported.
- Generate Java-like readable output through jadx-go.
- Process all top-level DEX entries or only a selected DEX entry.
- Use readable inner-name output for Java views.
- Zip generated Java output after completion.
- Browse generated Java output.
- Open large Java/smali files through virtualized editor paths.
- Use MITM/template helpers where supported.
- Capture job diagnostics and tool output.

Large files use virtualized editor logic to avoid loading every line into the visible Android view at once.

## DEX-to-Java output

PermsTest includes a `jadx-go` helper for best-effort DEX/APK Java-view generation.

DEX-to-Java behavior:

- Generates readable Java-like files for inspection and navigation.
- Supports all top-level DEX entries or selected DEX entry mode.
- Writes output under the shared debugging Java folder.
- Can zip generated output when complete.
- Reports progress and output paths.
- Captures logs and summary data.

Generated Java output is read-only reference output. It is useful for search, navigation, and mapping back to smali. It is not a Java-to-smali rebuild pipeline.

## Smali workflows

Smali workflows are the editable/rebuildable path for APK modification.

Smali capabilities include:

- Disassemble APK/DEX content to smali.
- Browse generated smali folders.
- Search smali files.
- Open and edit smali source.
- Assemble or rebuild supported outputs.
- Export rebuilt APK artifacts.
- Use source/line navigation where available.
- Save edits through the shared editor path.

Use smali workflows when the goal is to produce a modified APK output. Use Java output when the goal is inspection and search.

## Memory tab

The Memory tab provides process/package selection, attach state, scanning, patching, dumping, hex/disassembly views, overlays/panels, and payload helpers.

Memory capabilities include:

- Select target package.
- Select target process.
- Attach/detach to supported processes.
- Check attach status.
- Perform exact-value scans.
- Perform unknown/snapshot scans.
- Perform next scans after values change.
- Review found addresses.
- Apply value writes where supported.
- Use masks and data types for matching/writing.
- Open live-process memory hex tools.
- Open disassembly tools.
- Dump memory regions where supported.
- Use overlay or panel UI depending on device/profile settings.
- Use VR-compatible panel behavior when VR Mode is enabled.
- Use app/game-specific helper targets where configured.

Memory actions are sensitive. Confirm package scope, process, address, value type, mask, and original bytes before writing.

## Memory payloads and shortcuts

Memory payload tools store reusable package-scoped patch data.

Payload capabilities include:

- Store original bytes.
- Store patched bytes.
- Store masks.
- Store optional section markers.
- Find matching bytes when addresses change.
- Load matched addresses.
- Apply individual payloads.
- Apply all selected payloads.
- Apply on attach where configured.
- Create launcher shortcuts for payload workflows.
- Launch target apps from payload shortcuts.

Payloads should be tested one at a time on unfamiliar targets. Keep original bytes and a recovery path available.

## Tools tab

The Tools tab collects focused Android utilities and helper tools.

Tools capabilities include:

- Root Checker.
- System Analyzer hardware/software snapshot.
- Intent Launcher.
- Intent Receiver.
- Saved intent templates.
- Permission Tester APK generation.
- Activity Manager.
- Save Data Editor.
- Calculator.
- ASCII/Hex Converter.
- Alarms and timers.
- Stopwatch and timer controls.
- Text editor.
- File hex editor.
- Package/debug helper shortcuts.

### Intent Launcher and Receiver

Intent tools can:

- Configure Android action, data, type, package, component, categories, flags, and extras.
- Send launcher or broadcast-style intents where supported.
- Save reusable intent definitions.
- Use templates for common Android intent patterns.
- Capture incoming intents sent to declared receiver aliases.

### Permission Tester

Permission Tester can:

- Build test APKs with selected permissions.
- Create launchable test APK variants.
- Create permission-only manifest APK variants.
- Create launcher-activity variants.
- Generate tests based on selected third-party apps where supported.
- Install generated APKs through the normal installer path.

### Activity Manager

Activity Manager can:

- List package activities.
- Show exported and non-exported activity state.
- Search activity lists.
- Launch exported activities.
- Launch with parameters where supported.
- Launch with root where supported.
- Create shortcuts for selected activities.

### Save Data Editor

Save Data Editor can:

- Load supported save-data profiles.
- Read supported save JSON/data paths.
- Apply profile-specific edits.
- Use presets where defined.
- Back up supported save files before writes.
- Restore backups where supported.
- Page long result lists.

## Plugins tab

PermsTest supports bundled and external plugins.

Plugin capabilities include:

- Import plugin packages from `.ptp` or `.zip` files.
- Stage external plugin folders.
- Enable and disable staged plugins.
- Render plugin action buttons.
- Run plugin actions only from explicit user taps.
- Restore selected bundled plugins.
- Clean invalid staged plugins.
- Export plugin packages.
- Save plugin readiness reports.
- Validate plugin definitions.
- Edit plugin configuration through Plugin Editor.
- Edit raw `plugin.json` and `ui.json` for advanced fields.
- Manage plugin assets.
- Package plugin outputs.
- Use declarative UI controls.
- Use controlled shell/script actions through selected backends.
- Use trusted-Dex actions only after review and trust checks.

Plugin safety model:

- Disabled plugins remain inert.
- Plugins do not run at boot.
- Plugins do not run hidden background services.
- Plugins do not launch arbitrary APK components unless a supported explicit action exists.
- Declarative plugins run host-owned actions.
- Script plugins require explicit policy review/approval.
- Trusted-Dex plugins require matching payload details, hashes, class/method declarations, and explicit trust records.

Plugin source layout:

- One plugin per folder under `plugins/<plugin_id>/`.
- Core plugin metadata in `plugin.json`.
- Optional UI metadata in `ui.json`.
- Optional assets in plugin-owned asset folders.

## Logging tab

The Logging tab captures output, Logcat, lifetime actions, archive bundles, diagnostics, and root-only Android diagnostics where enabled.

Logging capabilities include:

- Save the visible bottom output pane.
- Share the most recently saved/exported file.
- Capture Logcat with filter and line controls.
- Save the next raw Logcat capture to a file.
- Capture Logcat All.
- Enable Lifetime Log.
- View Lifetime Log.
- Export Lifetime Log.
- Clear Lifetime Log.
- Mark Session.
- Archive Logs to a timestamped ZIP.
- Clear active logs without removing existing archives.
- Run Full Diagnostic capture.
- Run diagnostics/debug capture groups.
- Use root-gated Android diagnostics where enabled.
- Back up root diagnostic files before root-only clearing where supported.

Full Diagnostic is intended for reproducible reports. A useful report usually includes Debug Output enabled, a session marker, one clean reproduction, and then Full Diagnostic or Archive Logs.

## Settings tab

Settings controls app behavior, UI behavior, feature gates, logging behavior, launcher/kiosk behavior, device defaults, and VR compatibility.

Settings capabilities include:

- Automatically Apply Device Defaults.
- Enable or disable root-feature UI gates.
- Select execution-related behavior.
- Enable Debug Output.
- Enable Lifetime Log.
- Enable File Open Handler.
- Enable Popout Panels.
- Enable VR Mode.
- Disable Overlays for VR-compatible behavior.
- Enable Activity-hosted popout panels.
- Control app dropdown coloring.
- Enable fat dropdown scrollbars.
- Configure themes/UI behavior.
- Configure startup behavior.
- Configure package list/app dropdown behavior.
- Configure Kiosk settings.
- Configure Home Launcher behavior.
- Configure logging and diagnostic behavior.
- Configure accessible Web Interface sections.

Device defaults are intended to apply recommended defaults for the current device profile without overwriting later manual choices.

## VR and popout panels

PermsTest supports both normal phone/tablet UI and VR-compatible panel behavior.

VR/panel behavior includes:

- Standard phone/tablet UI by default.
- VR Mode as the gate for headset-specific behavior.
- Disable Overlays for VR Compatible behavior.
- Activity-hosted panels for VR devices where overlay windows are not appropriate.
- Popout groupbox buttons for supported sections.
- Synchronized popout content and main-tab state.
- Separate VR routing so normal device behavior is not changed when VR Mode is off.

Supported popout/panel areas include major groups such as Main, Shell, Packages, Memory, Network, Debugging, Tools, Logging, and Plugins where enabled.

## Public storage layout

Default shared root:

```text
/sdcard/dev.perms.test
```

Common public folders:

```text
/sdcard/dev.perms.test/logs
/sdcard/dev.perms.test/logs/lifetime
/sdcard/dev.perms.test/log_archives
/sdcard/dev.perms.test/debug_packages
/sdcard/dev.perms.test/debugging
/sdcard/dev.perms.test/debugging/smali
/sdcard/dev.perms.test/debugging/java
/sdcard/dev.perms.test/logs/apktool
/sdcard/dev.perms.test/logs/jadx-go
/sdcard/dev.perms.test/plugins
/sdcard/dev.perms.test/plugin_exports
/sdcard/dev.perms.test/scripts
/sdcard/dev.perms.test/memory_payloads
/sdcard/dev.perms.test/memory_dumps
/sdcard/dev.perms.test/package_states
/sdcard/dev.perms.test/save_data
```

Generated output paths are normally printed to the shared output pane after the action completes.

## Bundled helper components

PermsTest integrates several helper components and source trees.

Included helper areas include:

- Shizuku/Rish modules for privileged shell integration.
- LADB-style wireless debugging logic.
- `apktool-go` for APK decode/build workflows.
- `jadx-go` for DEX/APK Java-view output.
- `medit` for memory tooling integration.
- smali/baksmali integration for smali workflows.
- Android PackageInstaller API usage for install flows.
- FTP, HTTP, and SSH server/client components.
- Android Storage Access Framework integration.

Helper binaries are treated as standalone tools invoked by the app. Their source trees are kept in the repository so they can be built, inspected, and updated separately from the Android UI.

## Safety model

PermsTest is designed around explicit user action and backend gating.

Important safety rules:

- Disabled controls usually indicate a missing requirement.
- Destructive actions should require an explicit button and target review.
- Root-only actions stay behind root gates.
- Plugin actions run only after staging/enabling and explicit taps.
- Script and trusted-code plugin paths require policy/trust review.
- Network servers should be used only on trusted networks.
- Memory writes should be small, verified, and reversible.
- APK edits should be tested on backup copies.
- Package disable/debloat workflows are review lists until explicitly applied.

Android version, OEM firmware, SELinux policy, storage policy, package signatures, target SDK rules, managed profile rules, and backend readiness can all change what an action can do.

## Troubleshooting workflow

For useful troubleshooting data:

1. Enable **Settings > Debug Output**.
2. Enable **Settings > Enable Lifetime Log** if actions should persist across sessions.
3. Open **Logging** and tap **Mark Session**.
4. Reproduce the issue once.
5. Use **Full Diagnostic** or **Archive Logs**.
6. Save or share the generated output file/ZIP.

For package install issues, include installer output and package details.

For Shizuku/Internal Shizuku/LADB issues, include backend status and any pairing/start output.

For APK Editor or Debugging issues, include source APK name, selected DEX entry, output folder, and the relevant `logs/apktool` or `logs/jadx-go` files.

For memory issues, include target package/process, attach status, value type, scan mode, payload name, and whether VR/panel mode was enabled.

For network issues, include IP address, port, server root, Web Interface sections enabled, and whether the device screen/sleep options were active.

## Build from source

Typical debug build:

```bash
./gradlew :permstest:assembleDebug
```

A Windows shell can use:

```bat
gradlew.bat :permstest:assembleDebug
```

Build requirements:

- Android SDK.
- Compatible Android Gradle Plugin environment.
- Gradle wrapper from the repository.
- Native build tools/CMake where required by native components.
- Network access if Gradle dependencies are not already cached.
- A configured Android signing/debug environment for local install tests.

Build outputs are generated by Gradle under the module build folders. Generated APKs, logs, local SDK paths, local signing material, temporary packages, and release ZIPs should not be committed to source control.

## Repository layout

Important repository areas:

```text
permstest/                         Main Android app module
permstest/src/main/java/dev/perms/test/
                                   Main app source packages
permstest/src/main/res/layout/     Android layout resources
permstest/src/main/res/values/     Android values, strings, booleans, styles
permstest/src/main/assets/         Bundled assets, presets, plugins, helper binaries
plugins/                           Source plugin examples and packages
apktool-go/                        apktool-compatible helper source
jadx-go/                           DEX-to-Java helper source
medit/                             Memory helper source
api/ provider/ server/ rish/       Shizuku/Rish integration modules
common/ shared/ starter/ aidl/     Shared support modules
docs/                              Build, release, and structure notes
```

Source organization rules:

- Feature behavior belongs in feature-owned packages and controllers.
- `MainActivity` should remain the app shell and binding entry point.
- VR-specific behavior should stay gated by VR settings.
- Phone/tablet layout behavior should not be broken by VR-only changes.
- Helper tools should stay standalone and generic.
- Generated files, logs, local SDK paths, signing material, and packaged release artifacts should stay out of version control.

## Attribution and upstream components

PermsTest includes or integrates with Android platform APIs and third-party tooling. Review each project and included license file when redistributing source or builds.

Reference projects and APIs:

- Shizuku: https://github.com/RikkaApps/Shizuku
- Shizuku API: https://github.com/RikkaApps/Shizuku-API
- Sui: https://github.com/RikkaApps/Sui
- Rish: https://github.com/RikkaApps/Shizuku-API/tree/master/rish
- LADB: https://github.com/tytydraco/LADB
- apk-medit: https://github.com/sterrasec/apk-medit
- Android-Disassembler: https://github.com/yhs0602/Android-Disassembler
- FTPClient for Android: https://codeberg.org/qwerty287/ftpclient
- Apache Commons Net: https://commons.apache.org/proper/commons-net/
- Apache MINA SSHD: https://github.com/apache/mina-sshd
- smali / baksmali: https://github.com/JesusFreke/smali
- Apktool source: https://github.com/iBotPeaches/Apktool
- JADX source: https://github.com/skylot/jadx
- Android PackageInstaller: https://developer.android.com/reference/android/content/pm/PackageInstaller
- Android Storage Access Framework: https://developer.android.com/guide/topics/providers/document-provider
- Android platform source browser: https://cs.android.com/

## License

Review the repository license and the license files for bundled or integrated components. PermsTest combines original app code with Android tooling integrations, so upstream attribution and license terms should be preserved when distributing builds or source packages.
