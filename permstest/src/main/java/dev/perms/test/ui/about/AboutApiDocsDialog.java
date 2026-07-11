package dev.perms.test.ui.about;

import android.content.Context;

import dev.perms.test.ui.dialog.GenericViewerDialog;

/** User-facing operational guides shown from the About tab. */
public final class AboutApiDocsDialog {
    public enum Section {
        PERMSTEST,
        ANDROID,
        BACKENDS,
        PACKAGES,
        PLUGINS
    }

    public interface ErrorReporter {
        void onError(Throwable error);
    }

    private AboutApiDocsDialog() {
    }

    public static void show(Context context, Section section, ErrorReporter reporter) {
        try {
            Section safe = section == null ? Section.PERMSTEST : section;
            GenericViewerDialog.showText(context, title(safe), subtitle(safe), body(safe));
        } catch (Throwable t) {
            if (reporter != null) reporter.onError(t);
        }
    }

    private static String title(Section section) {
        switch (section) {
            case ANDROID:
                return "PermsTest API Guide - Android";
            case BACKENDS:
                return "PermsTest API Guide - Backends";
            case PACKAGES:
                return "PermsTest API Guide - APK / Debugging";
            case PLUGINS:
                return "PermsTest API Guide - Plugins";
            case PERMSTEST:
            default:
                return "PermsTest API Guide - Shared Controls";
        }
    }

    private static String subtitle(Section section) {
        switch (section) {
            case ANDROID:
                return "How PermsTest uses Android permissions, pickers, installs, services, intents, and sharing";
            case BACKENDS:
                return "How to select, connect, and troubleshoot Shizuku, Internal Shizuku, LADB, and System";
            case PACKAGES:
                return "How to install, inspect, rebuild, test permissions, send intents, and use debugging tools";
            case PLUGINS:
                return "How to install plugins and use plugin.json, ui.json, actions, runtimes, and Plugin Editor";
            case PERMSTEST:
            default:
                return "How to use shared output, logs, panels, storage, file-open routing, and action prerequisites";
        }
    }

    private static String body(Section section) {
        switch (section) {
            case ANDROID:
                return androidApiText();
            case BACKENDS:
                return backendApiText();
            case PACKAGES:
                return packageApiText();
            case PLUGINS:
                return pluginApiText();
            case PERMSTEST:
            default:
                return permsTestApiText();
        }
    }

    private static String permsTestApiText() {
        return lines(
                "PermsTest shared controls and host services",
                "",
                "Use the output pane",
                "• Most tools write status, command output, paths, and errors to the shared output pane at the bottom of the app.",
                "• Tap Copy to copy the visible output. Tap Clear to clear only the visible pane.",
                "• Logging > Save Output writes the entire visible output pane to a timestamped text file under /sdcard/dev.perms.test/logs.",
                "• Logging > Share shares the most recently saved file. Save or export something first when Share is disabled or reports that no file is available.",
                "• Scroll upward to pause automatic following. Return to the bottom to resume following new output.",
                "",
                "Capture useful diagnostics",
                "• Turn on Settings > Debug Output before repeating a problem. Debug Output adds structured status lines without changing the action being tested.",
                "• Turn on Settings > Enable Lifetime Log to keep structured actions across app sessions under /sdcard/dev.perms.test/logs/lifetime when shared storage is available. When it is off, View Lifetime, Export Lifetime, Clear Lifetime, and Mark Session are disabled.",
                "• Use Logging > Mark Session immediately before a test. The marker makes the beginning of the test easy to find in exported logs.",
                "• Use Logging > Archive Logs to create a timestamped ZIP containing the available PermsTest logs and an output-pane snapshot.",
                "• Use Logging > Clear Logs only after saving or archiving anything that must be kept.",
                "",
                "Use popout groupboxes",
                "• Turn on Settings > Enable Popout Panels to use the ↗ buttons on supported groupboxes.",
                "• A popout is a synchronized clone. The original groupbox remains in its tab and keeps its saved collapsed state.",
                "• Closing a popout does not stop the feature unless the feature itself has a Stop or Disconnect action.",
                "• VR-specific panel routing is used only when VR mode is enabled. Normal phone and tablet controls continue using the standard app UI.",
                "",
                "Use saved files and shared storage",
                "• PermsTest stores shared exports under /sdcard/dev.perms.test. The exact path used is shown in output after a save or export.",
                "• Common folders include logs, logs/lifetime, log_archives, plugins, plugin_exports, debug_packages, debugging, scripts, memory_payloads, memory_dumps, and save_data.",
                "• Android file pickers return content URIs. PermsTest copies or stages picked content when a tool needs a normal file path.",
                "• Delete, clear, uninstall, patch, and write actions show a confirmation or require an explicit button. Read the target path or package before confirming.",
                "",
                "Open files from another app",
                "• Enable Settings > Enable File Open Handler to allow a file manager to send supported files to PermsTest.",
                "• APK/APKS/APKM/XAPK files route to the package install workflow. Text, JSON, XML, smali, and other source-like files route to the appropriate viewer or editor.",
                "• If Android shows multiple handlers, choose PermsTest only for the file you want to inspect or install.",
                "",
                "Understand action availability",
                "• Disabled buttons are waiting for a prerequisite such as a selected package, readable file, connected backend, valid plugin JSON, or enabled setting.",
                "• Refresh the relevant status after starting Shizuku, pairing LADB, changing a package, or returning from Android Settings.",
                "• When an action fails, check the selected backend, package/path shown in output, exit code, and the first useful stderr line before retrying."
        );
    }

    private static String androidApiText() {
        return lines(
                "Android integration guide",
                "",
                "Permissions and Android Settings",
                "• PermsTest requests only the Android permissions needed by the selected feature. A denied permission can leave the related button unavailable or cause Android to open Settings.",
                "• For notification-dependent services, allow notifications so active Memory, FTP, HTTP, and job-service status remains visible.",
                "• For app-specific permission changes, select the package in Packages > Permissions & State, select a permission, then tap Grant or Revoke. The selected backend must be allowed to perform the change.",
                "• Open App Settings from Packages when Android requires a manual change that cannot be completed through the selected backend.",
                "",
                "Storage Access Framework file pickers",
                "• Browse buttons that open Android's picker can select a file or folder without granting unrestricted storage access.",
                "• After selecting an item, PermsTest displays or stages the returned URI/path. Use the tool's Save, Import, Install, or Open action to continue.",
                "• If a picker selection stops working after a reboot or file move, select the file again so Android can provide a fresh URI grant.",
                "",
                "Package installer sessions",
                "• Packages > APK Installer accepts a single APK or a supported split archive.",
                "• Select the file, review the detected package and split choices, then tap Install.",
                "• Android still enforces package signatures, version codes, ABI compatibility, and install-source policy.",
                "• Allow Downgrade adds the package-manager downgrade option; it does not bypass a signature mismatch.",
                "• When a debug/repacked APK has a different signature, use the explicit Uninstall & Install replacement flow only when losing the installed app's data is acceptable.",
                "",
                "Foreground services and notifications",
                "• Memory overlays/panels, FTP/HTTP background modes, and longer debugging jobs may use a foreground service.",
                "• Start these modes from their visible Start or Run action. Stop them from the matching Stop action or notification when provided.",
                "• Sleep/keep-alive options can hold CPU or Wi-Fi locks. Enable them only for a session that must continue while the screen is off.",
                "",
                "Intents and aliases",
                "• Home Mode enables PermsTest's launcher alias and opens Android's default-launcher picker.",
                "• Intent Launcher builds and sends an Android intent from the selected action, data URI, MIME type, package/component, flags, categories, and extras.",
                "• Intent Receiver captures intents sent to declared PermsTest receiver aliases. Enable only the aliases needed for the test, send the intent, then inspect the captured fields.",
                "• Runtime receiver toggles cannot create new manifest filters. Add new filters by rebuilding an APK when a filter is not already declared.",
                "",
                "FileProvider sharing",
                "• Share actions use Android FileProvider URIs so another app can read the selected export without exposing the whole storage folder.",
                "• The receiving app must support the shared file type. Choose a different target app when Android reports that no compatible handler is installed.",
                "",
                "Device and OEM limits",
                "• Android version, OEM policy, SELinux, background restrictions, and target-app protection can block an otherwise valid action.",
                "• PermsTest reports the backend and error when possible, but it does not bypass Android signature checks, SELinux policy, or protected-system restrictions."
        );
    }

    private static String backendApiText() {
        return lines(
                "Execution backend guide",
                "",
                "Choose an execution mode first",
                "• Open Main > Backend and select Shizuku, Internal Shizuku, LADB, or System from Exec Mode.",
                "• Refresh status after changing modes. The status text/chips show whether the selected backend is ready.",
                "• Shell, package, file, install, memory, debugging, and network actions use the selected backend when that action needs privileged command execution.",
                "",
                "Shizuku",
                "• Install and start the Shizuku app, then tap Request Permission in PermsTest.",
                "• Tap Refresh after Shizuku starts or restarts. A ready binder and granted permission are required for Shizuku-backed actions.",
                "• Use Open Shizuku to switch to the Shizuku app when its service or permission needs attention.",
                "• Shizuku permission is app-specific. Reinstalling PermsTest or clearing its data can require granting permission again.",
                "",
                "Internal Shizuku",
                "• Select Internal Shizuku when using PermsTest's embedded Shizuku-compatible server path.",
                "• Enable Wireless Debugging in Android developer options, open the pairing screen, and use Pair and Start when pairing is required.",
                "• Use Start Server after a valid endpoint is available. Tap Refresh to verify that the binder attached before running privileged actions.",
                "• Use Stop Server to stop the internal server. Starting, pairing, and binder readiness are separate states, so read the status/output after each action.",
                "",
                "LADB / Wireless Debugging",
                "• Select LADB, enable Wireless Debugging, and enter the connection host/port shown by Android.",
                "• Complete pairing when Android provides a pairing code and pairing port, then tap Connect.",
                "• LADB connections can change after Wireless Debugging restarts. Re-enter the current endpoint and reconnect when commands stop working.",
                "• Disconnect stops the current LADB connection without disabling Android Wireless Debugging.",
                "",
                "System mode",
                "• System runs actions with normal app permissions only. Use it for commands and tools that do not require privileged package, protected-file, or process access.",
                "• A command that works in Shizuku or LADB can legitimately fail in System mode with permission denied.",
                "",
                "Run commands and read results",
                "• Shell > Run sends the command through the selected backend. Stop requests cancellation for the active command when supported.",
                "• Command output shows stdout, stderr, and/or an exit code. Exit code 0 usually indicates success; a nonzero exit code should be read with stderr.",
                "• Quote paths containing spaces. Verify the selected package/path before running commands that write, remove, install, stop, or change permissions.",
                "• Root Checker tests real su access separately from the selected execution backend. A device can have working Shizuku without root, or root without Shizuku."
        );
    }

    private static String packageApiText() {
        return lines(
                "APK, package, and debugging guide",
                "",
                "Install an APK or split archive",
                "• Open Packages > APK Installer and choose Browse.",
                "• Select APK for a single package, or APKS/APKM/XAPK for a split container.",
                "• Review detected splits and select the ABI, density, and language entries appropriate for the device when the split picker is shown.",
                "• Tap Install. Read the install result and saved install-debug log when installation fails.",
                "• Use Allow Downgrade only for a lower versionCode with a matching signing certificate. Use Uninstall & Install for a signature replacement only after accepting data loss.",
                "",
                "Inspect and control an installed package",
                "• In Packages > Permissions & State, choose an app from the app dropdown.",
                "• Use Launch, Force Stop, App Settings, Uninstall, Clear Data, Enable, or Disable for the selected package.",
                "• Select a permission to inspect its state. Grant/Revoke requires a backend with enough authority and the permission must be changeable by Android.",
                "• Extract/Use Installed App copies the installed base/splits into a managed workspace for inspection or editing.",
                "",
                "Create a debuggable package",
                "• Choose a source APK, select the requested patch options, then tap Create Debuggable Package.",
                "• PermsTest calls the bundled apktool-compatible binary for supported manifest changes, rebuilds, aligns, and debug-signs the result.",
                "• The output APK is a test build with a different signing certificate unless it was already signed with the same key.",
                "• Install failures commonly mean signature mismatch, lower versionCode, wrong ABI, malformed resources, or Android policy rejection. Read the generated log path shown in output.",
                "",
                "Use APK Editor",
                "• Select Use Package File to browse an APK, or Use Installed App to use the package selected in Permissions & State.",
                "• Decode/Inspect reads the manifest and package structure. Open Manifest shows the current manifest text.",
                "• Edit package name, label, or supported manifest options, then rebuild/sign without smali repack when only manifest-level changes were made.",
                "• Use smali decompile/repack only after editing smali. Repacking smali unnecessarily adds work and can introduce avoidable failures.",
                "• Export/install uses the rebuilt output. Keep the original APK and any important app data backed up before replacement.",
                "",
                "Use smali tools",
                "• In Debugging, select an installed app or browse an APK/DEX source.",
                "• Choose the DEX entry/API level when applicable, then run Disassemble or Disassemble All DEX.",
                "• Use Search to find classes, methods, or strings. Open the matching smali file, edit, and Save.",
                "• Assemble/Reassemble creates a new DEX or APK output. Review the output path and signing/install result before deleting the workspace.",
                "",
                "Use Permission Tester",
                "• Select the permissions to include.",
                "• Launchable app creates a small test APK that can open and request applicable runtime permissions.",
                "• Manifest-only APK creates a no-UI package for manifest/package-manager testing; find it in Android Settings > Apps rather than the launcher.",
                "• Third Party Apps mode repacks a selected single APK with the chosen manifest permissions. Split-container repacking is not supported by this mode.",
                "• Load From App reads permissions from the selected source and adds unknown permissions to Custom for review.",
                "",
                "Use Intent Launcher and Intent Receiver",
                "• Intent Launcher: select or type an action, optionally set data URI/MIME/package/component/flags/categories/extras, then tap the send action.",
                "• Use explicit package/component values when the intent must target one app; leave them empty for normal Android resolution.",
                "• Intent Receiver: enable a declared receiver alias/filter, send the test intent from another app or Intent Launcher, then inspect/copy the captured action, URI, categories, flags, and extras.",
                "",
                "Use MITM and report actions",
                "• Select the source APK/workspace before running Trust User CAs, Allow Cleartext, Make Debuggable, Allow Backup, certificate-pinning, exported-activity, or secure-flag actions.",
                "• Report actions inspect and export findings. Patch actions create modified outputs and should be tested on copies.",
                "• A successful patch does not guarantee the target app will run; app integrity checks, native code, signatures, or server-side policy can still reject modifications."
        );
    }

    private static String pluginApiText() {
        return lines(
                "Plugin API and Plugin Editor guide",
                "",
                "Install and run a plugin",
                "• A .ptp file is a ZIP-format PermsTest plugin package. A compatible .zip can also be imported.",
                "• Use Plugins > Import Plugin to pick and stage a package immediately. Use Select Plugin plus Install Path/Folder when entering a path, content URI, or development folder manually.",
                "• Bundled plugin choices start unchecked. Check the demos or tools you want, then use Restore Selected to reinstall those bundled plugins. Use Refresh List to reread staged plugin folders.",
                "• Plugin Runtime Policy shows the plugin execution gates. Controlled shell/script actions are user-tapped, can be disabled there, can require Review Runtime Policy approval, and can optionally require a per-run confirmation dialog before dispatch. Declarative UI shell actions use the same shell/script runtime and per-run confirmation gate, can participate in review approval when the launch action declares shell_command or shell_script in requires, and Plugin Editor Preview UI blocks shell execution. trusted_dex uses an explicit trusted-code gate and only dispatches after exact-payload trust, declared SHA-256 verification, capability checks, and a user tap; it can also require a per-run trusted-code confirmation dialog before dispatch. Capability metadata is shown on plugin cards and checked when declared.",
                "• Use ☰ > Review Runtime Policy on any installed plugin card to inspect declared/inferred capabilities, script action details, script file summaries, trusted-Dex review/readiness metadata, and action allow/block status before running them.",
                "• Enable a plugin with its Enable/X button. Tap an action button on the plugin card to run that action.",
                "• Open the ☰ menu for Load At Startup, Use Large Window Override, Enable/Disable, Edit Plugin Config, Review Runtime Policy, trusted code review/readiness when applicable, Save Runtime Review, Export Plugin Package, and Uninstall Plugin.",
                "• Uninstall Plugin removes only the staged copy and saved local options; it does not delete the original .ptp/.zip file.",
                "",
                "Plugin package layout",
                "• Every plugin must contain plugin.json at the package root.",
                "• Declarative plugins normally include ui.json. Script plugins can include one or more plugin-relative .sh files.",
                "• Optional general files belong under assets/, for example assets/help.txt or assets/template.json. Plugin Editor can stage, replace, and remove these files without changing plugin.json.",
                "• Icon, script, declarative UI, and general asset paths must stay inside the plugin folder. Absolute paths, ../ traversal, reserved plugin.json conflicts, and conflicts between managed file roles are rejected.",
                "• A staged plugin lives under /sdcard/dev.perms.test/plugins/[plugin-id].",
                "",
                "plugin.json fields",
                "• schema: use dev.perms.test.plugin.",
                "• apiVersion: use 1 for the currently supported schema.",
                "• id: unique plugin ID using letters, numbers, dots, dashes, or underscores. Do not use placeholder IDs such as id or my_plugin.",
                "• name, version, author, description, comments: text shown in the Plugins tab or kept as package metadata.",
                "• icon: optional plugin-relative image path, for example icon.png.",
                "• runtime: declarative, script, trusted_native, or trusted_dex. This is the plugin-level default used by Plugin Editor; each action's type controls its actual dispatch, so one plugin can contain multiple supported action types.",
                "• capabilities: optional array declaring the plugin's allowed runtime surfaces. Supported values are declarative_ui, host_api, shell_command, shell_script, trusted_native, and trusted_dex. If this array is absent, legacy plugins keep working with inferred capabilities.",
                "• entry: plugin-level default entry. For a declarative plugin, use the main plugin-relative UI file such as ui.json. Actions can name additional declarative UI targets separately.",
                "• windowStyle: compact or full. compact uses the smaller managed dialog; full uses the larger managed window.",
                "• windowFit: current or fit. current uses the normal managed size; fit asks the window to fit its content when possible.",
                "• actions: array of action objects shown as buttons on the plugin card.",
                "",
                "Action fields",
                "• id: unique action ID inside the plugin.",
                "• title: button label shown on the plugin card.",
                "• description: optional explanation shown with action/plugin details.",
                "• type controls how the action is dispatched: declarative_ui/ui loads a plugin-relative UI target; shell/script runs a controlled command or script; native/trusted_native calls a registered PermsTest handler; trusted_dex uses the Trusted Code Policy gate.",
                "• target: plugin-relative declarative UI file such as ui.json, or a plugin-relative .dex/.jar/.apk payload for trusted_dex review/dispatch. handler can also be used as the declarative target fallback or legacy trusted-Dex payload field. Plugin Editor can hash a staged or queued trusted-code target and fill the expected SHA-256 field without loading or running it.",
                "• command: inline shell command for a shell/script action. Use this when the command can live directly in plugin.json.",
                "• script: plugin-relative script file for a shell/script action, normally under scripts/. The file is executed with sh through the selected PermsTest backend.",
                "• handler: registered handler name for a native/trusted_native action. Unknown handler names fail closed.",
                "• presentation: omit it or use default for normal behavior; dialog/viewer requests a highlighted text dialog; window/panel/large requests a managed text window; log/output/main_output writes text output only to the shared output pane. Declarative UI actions still open their UI surface.",
                "• syntax: omit it or use default to keep the runtime/handler default. Use plain for no highlighting, or json, properties/prop/ini, shell/bash/sh, smali, or web/html/css/js for supported highlighting.",
                "• windowStyle/windowFit: omit them or use inherit to use the plugin defaults. Action overrides accept compact/full and current/fit on compatible managed UI/window surfaces.",
                "• requires: optional action-level capability array. When plugin-level capabilities is declared, each action's inferred and explicit requirements must be included or the action is blocked by policy. Plugin Editor exposes this as a comma-separated Action requires field.",
                "",
                "Declarative ui.json structure",
                "• The root can define title, description, windowStyle, windowFit, and a controls array.",
                "• Each control can have an id. Actions refer to controls by that id.",
                "• Supported controls: label/text, input, multiline, dropdown, checkbox, output, divider, group/section, button, and buttons.",
                "• label/text: use text or label; optional textSize and bold.",
                "• input/multiline: use label, default, singleLine, minLines, and optional onChange action.",
                "• dropdown: use label, values array, default, and optional onChange action.",
                "• checkbox: use label and default true/false.",
                "• output: creates selectable monospace output text. Use label for its caption.",
                "• group/section: use label/title and a nested controls array.",
                "• button: use text/label and one action object. buttons uses a buttons array and lays out up to three buttons per row.",
                "",
                "Declarative action types",
                "• toast: set message. ${controlId} placeholders are replaced with current control values.",
                "• setText: set target and value to replace a control's text.",
                "• appendText: set target and value to append text; optional then runs another action afterward.",
                "• clear: set target to clear a field/output; optional then runs next.",
                "• backspace: set target to remove the final character; optional then runs next.",
                "• shell: set command and output. The command runs through the selected PermsTest backend and writes the result into the output control ID.",
                "• sequence: set steps to an array of action objects that run in order.",
                "• api: set name and the required input/output control IDs for a supported host API.",
                "",
                "Supported declarative host APIs",
                "• calculator.evaluateInteger: reads input and base control IDs; outputs can map hex, dec, oct, and bin control IDs; status receives parse errors.",
                "• converter.textToBytes: reads input, encoding, valueType, and delimiter control IDs; writes formatted bytes to output.",
                "• converter.bytesToText: reads input, encoding, and valueType control IDs; writes decoded text to output.",
                "• text.uppercase / text.lowercase / text.trim / text.reverse / text.length / text.wordCount / text.isBlank / text.lineCount: read input and write output without shell or plugin code.",
                "• text.contains / text.replace: read text plus query/search/replacement controls and write the result to output.",
                "• json.pretty / json.minify: parse JSONObject/JSONArray text and write formatted or compact JSON. Invalid JSON writes a safe error result.",
                "• url.encode / url.decode: encode or decode text using the selected encoding and write output. Invalid input writes a safe error result.",
                "• hash.sha256: hashes input using the selected encoding and writes the hex digest to output.",
                "• encoding.base64Encode / encoding.base64Decode / encoding.hexEncode / encoding.hexDecode: read input, use the selected encoding when converting text/bytes, and write output.",
                "",
                "Script actions",
                "• Inline command example: set type=shell, enter getprop as the command, and choose default, dialog, window, or log presentation.",
                "• Script-file example: set type=shell, tap Choose Script File, select the file, then Add or Update the action and tap Build JSON. The editor stores it under scripts/[safe-name] and copies it during Save, Save UI, or Package.",
                "• Selected script files are limited to 2 MB, must stay inside the plugin folder, and are executed with sh through the selected backend. Output includes exit code, stdout, and stderr.",
                "• presentation=default follows normal script behavior; dialog opens a highlighted result dialog; window requests a managed output window; log writes only to the shared output pane. Use Large Window Override can request the larger surface for default-compatible text output.",
                "• The Plugin Runtime Policy gate can disable shell/script plugin actions without disabling non-shell declarative or trusted-native actions. It can require per-plugin Review Runtime Policy approval before top-level script actions run, and it can optionally show the exact action review before each explicit user-tapped script run. Declarative UI shell buttons also respect the shell runtime gate and per-run confirmation option, can be included in script approval by declaring shell_command/shell_script in the launch action requires list, while Preview UI blocks them completely. Runtime review includes inline command text, script file status/hash/previews where available, and can be saved to plugin_exports for auditing. Script actions remain user-tapped only; this build does not add plugin startup/background script dispatch.",
                "• Script approvals are stored per plugin/script-action fingerprint, so edited command/script entries require a fresh approval when approval enforcement is enabled. Script actions infer shell_command for inline commands and shell_script for plugin-local script files. Add those values to capabilities when authoring strict manifests.",
                "",
                "Trusted native and trusted DEX",
                "• trusted_native is for handlers already registered inside PermsTest. Recognized handlers are build_prop_dialog, device_info_dialog, log_snapshot_dialog, open_calculator_tool, open_ascii_hex_tool, and open_alarms_timers_tool.",
                "• User packages cannot create new trusted_native handlers by naming them in JSON; an unknown handler fails closed.",
                "• trusted_dex is a trusted-code action type for explicit user-tapped in-process dispatch. It requires the Trusted-Dex runtime gate, an exact reviewed payload trust record, declared SHA-256 match, a safe plugin-relative .dex/.jar/.apk payload, class/method metadata, and capability-policy approval. It can optionally show a per-run trusted-code confirmation dialog before dispatch. It does not launch plugin APK components, run at startup, or dispatch in the background.",
                "• For trusted_dex actions, target or handler should point to a plugin-local .dex, .jar, or .apk payload. Advanced JSON can include sha256, className or entryClass, and methodName for trusted code review. Plugin Editor's Hash Trusted Target helper fills sha256 from a staged or queued target file without loading code. Review Trusted Code Policy and Save Trusted Code Review inspect file presence/hash/match status without loading code. Check Trusted Code Readiness and Save Trusted Code Readiness add a loader-readiness checklist covering capability policy, exact-payload trust status, runtime gates, class/method metadata, and payload hash state. Trust Reviewed Code Payload records approval for the exact reviewed payload fingerprint, and Clear Trusted Code Trust removes that record; optional per-run trusted-code confirmation shows the action/payload review immediately before dispatch, and dispatch still rechecks the payload hash/trust record at runtime.",
                "• Trusted-code methods can return null, text, JSONObject, JSONArray, Map, Collection, or dev.perms.test.plugins.runtime.TrustedPluginResult. JSON-like returns are pretty-printed with JSON highlighting. TrustedPluginResult lets a trusted method return controlled title/subtitle/text/syntax/presentation/window metadata while still using the existing plugin dialog, log, or managed window surfaces.",
                "",
                "Use Plugin Editor",
                "• Tap New to create a blank declarative plugin template, or Open .ptp / .zip Into Editor to import and load an existing package.",
                "• Fill Plugin ID, Name, Version, Author, Description, runtime, and plugin-level window settings. Runtime supplies defaults for new actions; the Action type field controls how each individual action runs.",
                "• In Plugin Actions, select a row to edit it. Action ID must be unique inside the plugin. Action title becomes the plugin-card button label. Choose Action type first, then use Entry / target / handler for the matching UI file, command, script path, native handler, or trusted-Dex payload path.",
                "• Action requires writes optional action-level capability metadata such as shell_command, shell_script, host_api, declarative_ui, trusted_native, or trusted_dex. Action presentation chooses default, dialog, window, or log behavior for compatible text-output actions. Output syntax chooses the highlighted viewer format. Action window style/fit can inherit plugin defaults or override them for compatible managed surfaces.",
                "• For a shell action, tap Choose Script File to select a local file. The editor assigns a safe scripts/ path and copies the file during Save, Save UI, or Package after the action has been added/updated and Build JSON has applied it.",
                "• Tap Add to append the current action fields, Update to apply them to the selected action, Remove to delete it, or Move Up/Move Down to change button order.",
                "• Tap Load JSON Actions to import the current advanced plugin.json actions array into the structured list. This does not rewrite other advanced root fields.",
                "• Tap Build JSON to write the friendly plugin fields and structured action list into plugin.json. Build JSON also refreshes the manifest capabilities array from the structured action list. Pending structured-action edits keep Validate, Save, Save UI, and Package disabled until Build JSON applies them. Unknown advanced root/action fields are preserved where possible; direct advanced JSON edits remain available.",
                "• Tap Choose Icon to select an image. The image is copied into the staged plugin during Save, Save UI, or Package.",
                "• Managed Plugin Assets handles optional general files under assets/. Tap Load Staged Assets to list the current staged assets/ folder. Tap Choose Asset File, review or edit the suggested assets/[file-name] path, then tap Add / Replace to queue it. Select an asset row and tap Remove to mark an existing staged file for deletion. Save, Save UI, or Package applies queued copies, replacements, and removals.",
                "• Each managed general asset is limited to 16 MB. General assets cannot replace plugin.json, the declared icon, a declarative UI target, or a declared script path. Use the dedicated Icon, Script, and UI controls for those files.",
                "• For declarative plugins, Declarative UI Controls edits the top-level controls array. Select a row, choose its type, ID, label/text, default/value, and optional action, then use Add, Update, Remove, or Move Up/Move Down.",
                "• Select a top-level group or section, then use Selected Container Contents to edit its controls array. Select a top-level buttons control to edit its button-row items. Reload Contents discards uncommitted nested-item field changes and reloads the selected container.",
                "• Group and section children use the same structured control fields and action mappings as top-level controls. Button-row items always use button text plus an optional action. Add, Update, Remove, and Move Up/Move Down immediately update the selected container in the structured top-level list; Build UI then writes those changes into raw ui.json.",
                "• Load UI Controls imports the current raw ui.json controls array into the structured list without rewriting root fields. Build UI writes the top-level list and selected-container contents back into ui.json while preserving unknown root/control/item fields and deeper nested content where possible.",
                "• The structured UI editor directly supports label, input, multiline, dropdown, checkbox, output, button, divider, group, section, and buttons rows. Raw syntax-highlighted ui.json remains available for deeper nesting and future/unknown fields.",
                "• For dropdown controls, enter values separated by |. For button/input/multiline/dropdown actions, choose none, toast, setText, appendText, clear, backspace, shell, api, or sequence. Action data uses shared DropdownUi suggestions for supported API names, useful templates, and common shell commands; long-press or choose Custom action data for another value.",
                "• Action options JSON edits fields that do not fit the simple data/output boxes. Use it for sequence steps, supported nested then actions, calculator output maps, converter input/control maps, or other supported advanced action fields. appendText, clear, backspace, and api can run one nested then action after they finish; unsupported then combinations are rejected instead of being silently ignored. The selected Control action plus Action data and Action target/output override matching keys when Build UI writes ui.json.",
                "• Selecting calculator.evaluateInteger, converter.textToBytes, converter.bytesToText, text.*, json.*, url.*, hash.sha256, encoding.base64*, or encoding.hex* inserts a safe starter mapping when Action options JSON is empty. Edit the control IDs to match the IDs in the same ui.json. Sequence inserts a starter steps array that must contain at least one action.",
                "• Sequence mapping example: {\"steps\":[{\"type\":\"toast\",\"message\":\"Started\"},{\"type\":\"clear\",\"target\":\"output\"}]}. The steps run in array order.",
                "• Calculator mapping example: {\"input\":\"input\",\"base\":\"base\",\"outputs\":{\"hex\":\"hex\",\"dec\":\"dec\",\"oct\":\"oct\",\"bin\":\"bin\"},\"status\":\"status\"}. Every value is a control ID from the same UI.",
                "• converter.textToBytes mapping uses input, encoding, valueType, and delimiter control IDs, then Action target/output names the destination output control. converter.bytesToText uses input, encoding, and valueType, then Action target/output names the destination text control. Text/json/hash/Base64/url APIs use input/output control IDs, with encoding optional for hash/base64/url; text.contains also uses query, and text.replace uses search/replacement.",
                "• Pending structured UI edits keep Preview UI, Validate, Save, Save UI, and Package disabled until Build UI applies them. Preview UI renders the current ui.json through the same declarative runtime path without saving, staging, packaging, or running startup work. Save UI saves plugin.json plus ui.json together.",
                "• Declarative ui.json validation checks the runtime shape before Preview, Validate, Save UI, Save, and Package: controls must be objects, nested controls/buttons must use the expected arrays, IDs must be unique, dropdown values must be arrays, and action targets/outputs must point at controls in the same UI.",
                "• Tap Validate before Save or Package. Validation checks schema/API version, IDs, plugin runtime, per-action type/presentation/syntax/window values, path conflicts, duplicate actions, every required UI/script/icon file, and queued managed-asset changes.",
                "• Tap Package to create a .ptp export after all declared files exist. Buttons remain disabled while required inputs are missing and prerequisites are checked again when pressed.",
                "",
                "Troubleshoot a plugin",
                "• Turn on Debug Output, Refresh List, enable the plugin, and run one action.",
                "• Check the staged plugin ID/path, validation result, selected backend for shell actions, Plugin Runtime Policy, action type/target/handler, and presentation setting.",
                "• Clean Invalid removes staged folders that cannot be parsed as valid plugins. Use it only when those invalid staged folders are no longer needed."
        );
    }

    private static String lines(String... lines) {
        StringBuilder sb = new StringBuilder();
        if (lines == null) return "";
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i] == null ? "" : lines[i]);
        }
        return sb.toString();
    }
}
