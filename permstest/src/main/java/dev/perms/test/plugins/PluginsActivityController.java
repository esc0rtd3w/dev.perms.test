package dev.perms.test.plugins;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabPluginsBinding;
import dev.perms.test.editor.SourceSyntaxHighlighter;
import dev.perms.test.plugins.editor.PluginEditorActionStore;
import dev.perms.test.plugins.editor.PluginEditorAssetStore;
import dev.perms.test.plugins.editor.PluginEditorDeclarativeUiValidator;
import dev.perms.test.plugins.editor.PluginEditorUiControlStore;
import dev.perms.test.plugins.runtime.DeclarativePluginRuntime;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.NoFilterArrayAdapter;
import dev.perms.test.ui.ThemeColorController;

/** Owns the Plugins tab UI and staged-plugin lifecycle. */
public final class PluginsActivityController {
    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        SharedPreferences getSharedPreferences();
        void appendOutput(String message);
        boolean isDebugOutputEnabled();
        void debugOutput(String area, String message);
        boolean showPluginTextPanel(String panelKey, String title, String subtitle, String text, String syntax, String windowStyle, String windowFit);
        boolean openPermsTestTool(String pluginId, String toolId, boolean requestPanel, String windowStyle, String windowFit);
        void runShellCommandCapture(String command, ShellCallback callback);
    }

    private static final String PREF_DISABLED_PREFIX = "plugin_disabled_";
    private static final String PREF_LOAD_STARTUP_PREFIX = "plugin_load_startup_";
    private static final String PREF_RUN_WINDOW_PREFIX = "plugin_run_window_";

    private static final int MENU_LOAD_STARTUP = 1;
    private static final int MENU_RUN_WINDOW = 2;
    private static final int MENU_TOGGLE_ENABLED = 3;
    private static final int MENU_UNINSTALL = 4;
    private static final int MENU_EDIT_PLUGIN = 5;
    private static final int MENU_EXPORT_PLUGIN = 6;
    private static final int MENU_REVIEW_POLICY = 7;
    private static final int MENU_APPROVE_SCRIPT_POLICY = 8;
    private static final int MENU_CLEAR_SCRIPT_APPROVAL = 9;
    private static final int MENU_SAVE_POLICY_REVIEW = 10;
    private static final int MENU_REVIEW_TRUSTED_CODE = 11;
    private static final int MENU_SAVE_TRUSTED_CODE_REVIEW = 12;
    private static final int MENU_APPROVE_TRUSTED_CODE = 13;
    private static final int MENU_CLEAR_TRUSTED_CODE_APPROVAL = 14;
    private static final int MENU_CHECK_TRUSTED_CODE_READINESS = 15;
    private static final int MENU_SAVE_TRUSTED_CODE_READINESS = 16;
    private static final int MENU_CHECK_PACKAGE_READINESS = 17;
    private static final int MENU_SAVE_PACKAGE_READINESS = 18;
    private static final int MENU_CHECK_SCRIPT_READINESS = 19;
    private static final int MENU_SAVE_SCRIPT_READINESS = 20;
    private static final int MENU_CHECK_DECLARATIVE_READINESS = 21;
    private static final int MENU_SAVE_DECLARATIVE_READINESS = 22;
    private static final int MENU_CHECK_COMPLETE_READINESS = 23;
    private static final int MENU_SAVE_COMPLETE_READINESS = 24;
    private static final int MENU_CHECK_EXPORT_ROUND_TRIP = 25;
    private static final int MENU_SAVE_EXPORT_ROUND_TRIP = 26;
    private static final long MAX_EDITOR_ICON_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_EDITOR_SCRIPT_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_EDITOR_ASSET_BYTES = 16L * 1024L * 1024L;
    private static final String CUSTOM_ACTION_TITLE_LABEL = "Custom action title...";
    private static final String CUSTOM_ACTION_TARGET_LABEL = "Custom entry / target / handler...";
    private static final String CUSTOM_UI_ACTION_DATA_LABEL = "Custom action data...";
    private static final String[] ACTION_TITLE_SUGGESTIONS = new String[]{
            "Open Plugin UI",
            "Run Plugin Action",
            "Run Script",
            "Open PermsTest Tool",
            "Show Plugin Output",
            CUSTOM_ACTION_TITLE_LABEL
    };
    private static final String[] UI_CONTROL_TYPE_SUGGESTIONS = new String[]{
            "label", "text", "input", "multiline", "dropdown", "checkbox", "output",
            "button", "divider", "group", "section", "buttons"
    };
    private static final String[] UI_ACTION_TYPE_SUGGESTIONS = new String[]{
            "none", "toast", "setText", "appendText", "clear", "backspace", "shell", "api", "sequence"
    };
    private static final String[] UI_API_NAME_SUGGESTIONS = new String[]{
            "calculator.evaluateInteger",
            "converter.textToBytes",
            "converter.bytesToText",
            "text.uppercase",
            "text.lowercase",
            "text.trim",
            "text.reverse",
            "text.length",
            "text.wordCount",
            "text.isBlank",
            "text.lineCount",
            "text.contains",
            "text.replace",
            "json.pretty",
            "json.minify",
            "url.encode",
            "url.decode",
            "hash.sha256",
            "encoding.base64Encode",
            "encoding.base64Decode",
            "encoding.hexEncode",
            "encoding.hexDecode",
            CUSTOM_UI_ACTION_DATA_LABEL
    };
    private static final String[] PLUGIN_ACTION_TYPE_SUGGESTIONS = new String[]{
            "declarative_ui", "shell", "native", "trusted_dex"
    };
    private static final String[] PLUGIN_ACTION_PRESENTATION_SUGGESTIONS = new String[]{
            "default", "dialog", "window", "log"
    };
    private static final String[] PLUGIN_ACTION_SYNTAX_SUGGESTIONS = new String[]{
            "default", "plain", "json", "properties", "shell", "smali", "web"
    };
    private static final String[] PLUGIN_ACTION_WINDOW_STYLE_SUGGESTIONS = new String[]{
            "inherit", "compact", "full"
    };
    private static final String[] PLUGIN_ACTION_WINDOW_FIT_SUGGESTIONS = new String[]{
            "inherit", "current", "fit"
    };

    private interface EditorValueCallback {
        void onValue(String value);
    }

    private enum EditorAction {
        NEW,
        OPEN_PACKAGE,
        CHOOSE_ICON,
        CHOOSE_SCRIPT,
        CHOOSE_ASSET,
        ASSETS_LOAD,
        ASSET_ADD,
        ASSET_REMOVE,
        ACTIONS_LOAD_JSON,
        TRUSTED_HASH_TARGET,
        ACTION_ADD,
        ACTION_UPDATE,
        ACTION_REMOVE,
        ACTION_UP,
        ACTION_DOWN,
        UI_LOAD_JSON,
        UI_ADD,
        UI_UPDATE,
        UI_REMOVE,
        UI_UP,
        UI_DOWN,
        UI_NESTED_LOAD,
        UI_NESTED_ADD,
        UI_NESTED_UPDATE,
        UI_NESTED_REMOVE,
        UI_NESTED_UP,
        UI_NESTED_DOWN,
        BUILD_JSON,
        VALIDATE,
        SAVE,
        BUILD_UI,
        PREVIEW_UI,
        SAVE_UI,
        PACKAGE
    }

    private enum PickerMode {
        SELECT_PATH,
        IMPORT_PLUGIN,
        OPEN_EDITOR_PLUGIN,
        CHOOSE_EDITOR_ICON,
        CHOOSE_EDITOR_SCRIPT,
        CHOOSE_EDITOR_ASSET
    }

    private enum SelectionMode {
        ALL,
        NONE,
        DEMOS
    }

    private final Host host;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PermsTestPlugins");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> selectedBundledAssets = new HashSet<>();
    private final List<PluginRepository.BundledPluginInfo> currentBundledPlugins = new ArrayList<>();
    private final Set<String> activeActionKeys = new HashSet<>();

    private ActivityResultLauncher<Intent> pluginPickerLauncher;
    private PickerMode pendingPickerMode = PickerMode.SELECT_PATH;
    private Uri pendingEditorIconUri;
    private final Map<String, Uri> pendingEditorScriptUris = new HashMap<>();
    private Uri pendingEditorAssetPickerUri;
    private final Map<String, Uri> pendingEditorAssetUris = new HashMap<>();
    private final Set<String> removedEditorAssetPaths = new HashSet<>();
    private boolean bundledSelectionInitialized;
    private boolean bound;
    private String editorPluginId;
    private String editorHandlerSuggestionRuntime;
    private String editorUiActionSuggestionType;
    private String editorUiNestedActionSuggestionType;
    private final PluginEditorActionStore editorActionStore = new PluginEditorActionStore();
    private final PluginEditorAssetStore editorAssetStore = new PluginEditorAssetStore();
    private final PluginEditorUiControlStore editorUiControlStore = new PluginEditorUiControlStore();
    private final PluginEditorUiControlStore editorUiNestedStore = new PluginEditorUiControlStore();
    private boolean editorActionFieldSyncing;
    private boolean editorActionListDirty;
    private boolean editorUiFieldSyncing;
    private boolean editorUiListDirty;
    private boolean editorUiNestedFieldSyncing;
    private boolean editorUiNestedFieldsDirty;
    private final Runnable editorActionStateRefresh = this::updatePluginEditorActionState;

    public PluginsActivityController(Host host) {
        this.host = host;
    }

    public void registerActivityResults() {
        try {
            if (pluginPickerLauncher != null) return;
            Activity activity = activity();
            if (!(activity instanceof ComponentActivity)) return;
            pluginPickerLauncher = ((ComponentActivity) activity).registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handlePickedPlugin);
            updatePluginEditorActionState();
        } catch (Throwable ignored) {
        }
    }

    public void bind() {
        if (bound) return;
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        bound = true;
        try {
            debug("bind", "binding plugin tab after core startup finished");
            tab.btnPluginsRefresh.setOnClickListener(v -> refreshPlugins());
            tab.btnPluginsReloadInternal.setOnClickListener(v -> restoreSelectedBundledPlugins());
            tab.btnPluginsCleanInvalid.setOnClickListener(v -> cleanInvalidPlugins());
            tab.btnPluginsSelectBundledAll.setOnClickListener(v -> selectBundledPlugins(SelectionMode.ALL));
            tab.btnPluginsSelectBundledNone.setOnClickListener(v -> selectBundledPlugins(SelectionMode.NONE));
            tab.btnPluginsSelectBundledDemos.setOnClickListener(v -> selectBundledPlugins(SelectionMode.DEMOS));
            tab.btnPluginsImport.setOnClickListener(v -> launchPluginPicker(true));
            tab.btnPluginsBrowse.setOnClickListener(v -> launchPluginPicker(false));
            tab.btnPluginsStagePath.setOnClickListener(v -> stagePathFromField());
            bindPluginRuntimePolicy(tab);
            tab.btnPluginsEnableAll.setOnClickListener(v -> setAllPluginsDisabled(false));
            tab.btnPluginsDisableAll.setOnClickListener(v -> setAllPluginsDisabled(true));
            tab.btnPluginsCheckAllReadiness.setOnClickListener(v -> showAllPluginReadiness());
            tab.btnPluginsSaveAllReadiness.setOnClickListener(v -> saveAllPluginReadiness());
            bindPluginEditorActions(tab);
            bindPluginEditorDropdowns(tab);
            setupPluginEditorJsonEditor(tab.edtPluginEditorJson);
            setupPluginEditorJsonEditor(tab.edtPluginEditorUiJson);
            setupPluginEditorJsonEditor(tab.edtPluginEditorUiActionOptionsJson);
            setupPluginEditorJsonEditor(tab.edtPluginEditorUiNestedActionOptionsJson);
            bindPluginEditorStateWatchers(tab);
            renderEditorActions();
            renderEditorAssets();
            renderEditorUiControls();
            renderEditorUiNestedItems();
            setStatus("Plugin root: " + repository().getPluginRoot().getAbsolutePath());
            loadBundledPluginList();
            ensureFirstRunDefaultToolPluginsThenRefresh();
        } catch (Throwable t) {
            setStatus("Plugin setup failed: " + safeMessage(t));
        }
    }

    public void stop() {
        try { worker.shutdownNow(); } catch (Throwable ignored) {}
    }

    private void bindPluginRuntimePolicy(TabPluginsBinding tab) {
        if (tab == null) return;
        SharedPreferences prefs = prefs();
        try {
            if (tab.chkPluginsEnableScriptRuntime != null) {
                tab.chkPluginsEnableScriptRuntime.setOnCheckedChangeListener(null);
                tab.chkPluginsEnableScriptRuntime.setChecked(PluginRuntimePolicy.isScriptRuntimeEnabled(prefs));
                tab.chkPluginsEnableScriptRuntime.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    PluginRuntimePolicy.setScriptRuntimeEnabled(prefs(), isChecked);
                    append("[plugins] Controlled shell/script plugin actions " + (isChecked ? "enabled" : "disabled") + " by runtime policy.\n");
                    setStatus(isChecked
                            ? "Controlled shell/script plugin actions enabled for user-tapped plugin actions."
                            : "Controlled shell/script plugin actions disabled. Declarative and trusted-native actions remain available.");
                    updatePluginRuntimePolicyStatus();
                });
            }
            if (tab.chkPluginsRequireScriptApproval != null) {
                tab.chkPluginsRequireScriptApproval.setOnCheckedChangeListener(null);
                tab.chkPluginsRequireScriptApproval.setChecked(PluginRuntimePolicy.isScriptApprovalRequired(prefs));
                tab.chkPluginsRequireScriptApproval.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    PluginRuntimePolicy.setScriptApprovalRequired(prefs(), isChecked);
                    append("[plugins] Script plugin review approval " + (isChecked ? "required" : "optional") + " by runtime policy.\n");
                    setStatus(isChecked
                            ? "Script plugins now require Review Runtime Policy approval before script actions run."
                            : "Script plugin review approval is optional; script runtime gate still applies.");
                    updatePluginRuntimePolicyStatus();
                    refreshPlugins();
                });
            }
            if (tab.chkPluginsConfirmScriptRun != null) {
                tab.chkPluginsConfirmScriptRun.setOnCheckedChangeListener(null);
                tab.chkPluginsConfirmScriptRun.setChecked(PluginRuntimePolicy.isScriptRunConfirmationRequired(prefs));
                tab.chkPluginsConfirmScriptRun.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    PluginRuntimePolicy.setScriptRunConfirmationRequired(prefs(), isChecked);
                    append("[plugins] Per-run shell/script confirmation " + (isChecked ? "required" : "optional") + " by runtime policy.\n");
                    setStatus(isChecked
                            ? "Shell/script plugin actions will show the script review before each explicit run."
                            : "Shell/script plugin actions no longer require per-run confirmation; other policy gates still apply.");
                    updatePluginRuntimePolicyStatus();
                    refreshPlugins();
                });
            }
            if (tab.chkPluginsEnableTrustedDexRuntime != null) {
                tab.chkPluginsEnableTrustedDexRuntime.setOnCheckedChangeListener(null);
                tab.chkPluginsEnableTrustedDexRuntime.setChecked(PluginRuntimePolicy.isTrustedDexRuntimeRequested(prefs));
                tab.chkPluginsEnableTrustedDexRuntime.setEnabled(PluginRuntimePolicy.isTrustedDexRuntimeCompiledEnabled());
                tab.chkPluginsEnableTrustedDexRuntime.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    PluginRuntimePolicy.setTrustedDexRuntimeRequested(prefs(), isChecked);
                    append("[plugins] trusted_dex runtime gate " + (isChecked ? "enabled" : "disabled") + "; dispatch still requires exact payload trust and SHA-256 verification.\n");
                    updatePluginRuntimePolicyStatus();
                });
            }
            if (tab.chkPluginsConfirmTrustedDexRun != null) {
                tab.chkPluginsConfirmTrustedDexRun.setOnCheckedChangeListener(null);
                tab.chkPluginsConfirmTrustedDexRun.setChecked(PluginRuntimePolicy.isTrustedDexRunConfirmationRequired(prefs));
                tab.chkPluginsConfirmTrustedDexRun.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    PluginRuntimePolicy.setTrustedDexRunConfirmationRequired(prefs(), isChecked);
                    append("[plugins] Per-run trusted-code confirmation " + (isChecked ? "required" : "optional") + " by runtime policy.\n");
                    setStatus(isChecked
                            ? "Trusted-code plugin actions will show the trusted-code review before each explicit run."
                            : "Trusted-code plugin actions no longer require per-run confirmation; other policy gates still apply.");
                    updatePluginRuntimePolicyStatus();
                    refreshPlugins();
                });
            }
            if (tab.btnPluginsClearScriptApprovals != null) {
                tab.btnPluginsClearScriptApprovals.setOnClickListener(v -> confirmClearAllScriptApprovals());
            }
            if (tab.btnPluginsClearTrustedCodeTrust != null) {
                tab.btnPluginsClearTrustedCodeTrust.setOnClickListener(v -> confirmClearAllTrustedCodeTrust());
            }
            updatePluginRuntimePolicyStatus();
        } catch (Throwable t) {
            debug("runtime-policy", "bind failed: " + safeMessage(t));
        }
    }

    private void updatePluginRuntimePolicyStatus() {
        TabPluginsBinding tab = tab();
        if (tab != null && tab.txtPluginsRuntimePolicyStatus != null) {
            tab.txtPluginsRuntimePolicyStatus.setText(PluginRuntimePolicy.statusText(prefs()));
        }
    }

    private void confirmClearAllScriptApprovals() {
        Activity activity = activity();
        if (activity == null) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Clear Script Approvals")
                .setMessage("Clear all saved script-plugin approval records? This does not disable the script runtime, delete plugins, or change plugin files. Plugins that require script approval will need to be reviewed/approved again before controlled script actions can run.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    int count = PluginRuntimePolicy.clearAllScriptApprovals(prefs());
                    append("[plugins] Cleared " + count + " saved script approval record(s).\n");
                    setStatus("Cleared " + count + " script approval record(s).");
                    updatePluginRuntimePolicyStatus();
                    refreshPlugins();
                })
                .show();
    }

    private void confirmClearAllTrustedCodeTrust() {
        Activity activity = activity();
        if (activity == null) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Clear Trusted Code Trust")
                .setMessage("Clear all saved trusted-Dex payload trust records? This does not disable the trusted-Dex runtime gate, delete plugins, load code, or change plugin files. Trusted-Dex actions will require exact-payload review/trust again before in-process dispatch can run.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    int count = PluginRuntimePolicy.clearAllTrustedDexApprovals(prefs());
                    append("[plugins] Cleared " + count + " trusted-code trust record(s).\n");
                    setStatus("Cleared " + count + " trusted-code trust record(s).");
                    updatePluginRuntimePolicyStatus();
                    refreshPlugins();
                })
                .show();
    }

    private void launchPluginPicker(boolean importImmediately) {
        launchPicker(importImmediately ? PickerMode.IMPORT_PLUGIN : PickerMode.SELECT_PATH, "*/*");
    }

    private void launchEditorPluginPicker() {
        launchPicker(PickerMode.OPEN_EDITOR_PLUGIN, "*/*");
    }

    private void launchEditorIconPicker() {
        launchPicker(PickerMode.CHOOSE_EDITOR_ICON, "image/*");
    }

    private void launchEditorScriptPicker() {
        launchPicker(PickerMode.CHOOSE_EDITOR_SCRIPT, "*/*");
    }

    private void launchEditorAssetPicker() {
        launchPicker(PickerMode.CHOOSE_EDITOR_ASSET, "*/*");
    }

    private void launchPicker(PickerMode mode, String mimeType) {
        Activity activity = activity();
        if (activity == null) return;
        if (pluginPickerLauncher == null) {
            Toast.makeText(activity, "Plugin picker unavailable", Toast.LENGTH_SHORT).show();
            setPickerStatus(mode, "Plugin picker unavailable. Restart PermsTest if this tab opened before initialization finished.");
            return;
        }
        try {
            pendingPickerMode = mode == null ? PickerMode.SELECT_PATH : mode;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(TextUtils.isEmpty(mimeType) ? "*/*" : mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            pluginPickerLauncher.launch(intent);
        } catch (Throwable t) {
            setPickerStatus(mode, "File picker failed: " + safeMessage(t));
            pendingPickerMode = PickerMode.SELECT_PATH;
        }
    }

    private void handlePickedPlugin(ActivityResult result) {
        PickerMode mode = pendingPickerMode;
        try {
            if (result == null || result.getResultCode() != Activity.RESULT_OK) return;
            Intent data = result.getData();
            Uri uri = data == null ? null : data.getData();
            if (uri == null) return;
            persistUriGrant(data, uri);
            String displayName = queryDisplayName(uri);
            if (mode == PickerMode.IMPORT_PLUGIN) {
                stageUri(uri, displayName);
            } else if (mode == PickerMode.OPEN_EDITOR_PLUGIN) {
                importPluginIntoEditor(uri, displayName);
            } else if (mode == PickerMode.CHOOSE_EDITOR_ICON) {
                selectEditorIcon(uri, displayName);
            } else if (mode == PickerMode.CHOOSE_EDITOR_SCRIPT) {
                selectEditorScript(uri, displayName);
            } else if (mode == PickerMode.CHOOSE_EDITOR_ASSET) {
                selectEditorAsset(uri, displayName);
            } else {
                TabPluginsBinding tab = tab();
                if (tab != null && tab.edtPluginPath != null) tab.edtPluginPath.setText(uri.toString());
                setStatus("Selected plugin URI. Tap Install Path/Folder to stage it, or use Import Plugin to pick and install in one step.");
            }
        } catch (Throwable t) {
            setPickerStatus(mode, "File picker result failed: " + safeMessage(t));
        } finally {
            pendingPickerMode = PickerMode.SELECT_PATH;
        }
    }

    private void setPickerStatus(PickerMode mode, String message) {
        if (mode == PickerMode.OPEN_EDITOR_PLUGIN || mode == PickerMode.CHOOSE_EDITOR_ICON
                || mode == PickerMode.CHOOSE_EDITOR_SCRIPT || mode == PickerMode.CHOOSE_EDITOR_ASSET) {
            setEditorStatus(message);
        } else {
            setStatus(message);
        }
    }

    private void importPluginIntoEditor(Uri uri, String name) {
        if (uri == null) return;
        debug("editor", "importing plugin package into editor uri=" + uri);
        setEditorStatus("Importing plugin package into staged plugins...");
        worker.execute(() -> {
            try {
                PluginManifest manifest = repository().installUri(uri, name);
                runUi(() -> {
                    clearPendingEditorIcon();
                    loadPluginIntoEditor(manifest);
                    setEditorStatus("Imported and opened " + manifest.id + ". The staged plugin is ready to edit.");
                    append("[plugins] Imported plugin into editor: " + manifest.id + "\n");
                    refreshPlugins();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("editor", "open package failed: " + safeMessage(t));
                    setEditorStatus("Open plugin package failed: " + safeMessage(t));
                });
            }
        });
    }

    private void selectEditorIcon(Uri uri, String displayName) {
        TabPluginsBinding tab = tab();
        if (tab == null || uri == null) return;
        String safeName = safePluginAssetFileName(displayName, "icon.png");
        pendingEditorIconUri = uri;
        tab.edtPluginEditorIcon.setText(safeName);
        updateEditorJsonIconPath(tab, safeName);
        setEditorStatus("Selected icon " + safeName + ". It will be copied into the plugin when Save, Save UI, or Package is used (maximum 8 MB).");
        debug("editor", "selected pending icon name=" + safeName);
        updatePluginEditorActionState();
    }

    private void clearPendingEditorIcon() {
        pendingEditorIconUri = null;
    }

    private void selectEditorScript(Uri uri, String displayName) {
        TabPluginsBinding tab = tab();
        if (tab == null || uri == null) return;
        String safeName = safePluginAssetFileName(displayName, "run.sh");
        if (!safeName.contains(".")) safeName += ".sh";
        String relativePath = "scripts/" + safeName;
        pendingEditorScriptUris.put(relativePath, uri);
        tab.ddPluginEditorActionType.setText("shell", false);
        tab.edtPluginEditorHandler.setText(relativePath, false);
        setEditorStatus("Selected script " + relativePath + ". It will be copied into the staged plugin when Save, Save UI, or Package is used (maximum 2 MB).");
        debug("editor", "selected pending script path=" + relativePath);
        updatePluginEditorActionState();
    }

    private void clearPendingEditorScripts() {
        pendingEditorScriptUris.clear();
    }

    private void selectEditorAsset(Uri uri, String displayName) {
        TabPluginsBinding tab = tab();
        if (tab == null || uri == null) return;
        String safeName = safePluginAssetFileName(displayName, "asset.bin");
        pendingEditorAssetPickerUri = uri;
        tab.edtPluginEditorAssetPath.setText("assets/" + safeName);
        setEditorStatus("Selected asset file for " + safeName + ". Review the assets/ path, then tap Add / Replace. Managed assets are copied during Save, Save UI, or Package (maximum 16 MB each).");
        debug("editor-assets", "selected asset source name=" + safeName);
        updatePluginEditorActionState();
    }

    private void clearEditorAssetState() {
        pendingEditorAssetPickerUri = null;
        pendingEditorAssetUris.clear();
        removedEditorAssetPaths.clear();
        editorAssetStore.clear();
        TabPluginsBinding tab = tab();
        if (tab != null && tab.edtPluginEditorAssetPath != null) tab.edtPluginEditorAssetPath.setText("");
        renderEditorAssets();
    }

    private void copyPendingEditorAssetsIfAny(ValidationResult validation) throws Exception {
        if (pendingEditorAssetUris.isEmpty()) return;
        if (validation == null || !validation.ok || !PluginManifest.isSafeId(validation.id)) {
            throw new IllegalArgumentException("Save a valid plugin ID before copying selected assets.");
        }
        ArrayList<String> copiedPaths = new ArrayList<>();
        for (Map.Entry<String, Uri> entry : new ArrayList<>(pendingEditorAssetUris.entrySet())) {
            String relativePath = normalizePluginPath(entry.getKey());
            String problem = managedAssetPathProblem(relativePath, textOf(tab() == null ? null : tab().edtPluginEditorJson));
            if (!TextUtils.isEmpty(problem)) throw new IllegalArgumentException(problem);
            File copied = repository().copyUriToPluginFile(validation.id, entry.getValue(), relativePath, MAX_EDITOR_ASSET_BYTES);
            copiedPaths.add(relativePath);
            debug("editor-assets", "copied selected asset to " + copied.getAbsolutePath());
        }
        for (String path : copiedPaths) pendingEditorAssetUris.remove(path);
        renderEditorAssets();
    }

    private void applyRemovedEditorAssetsIfAny(ValidationResult validation) throws Exception {
        if (removedEditorAssetPaths.isEmpty()) return;
        if (validation == null || !validation.ok || !PluginManifest.isSafeId(validation.id)) {
            throw new IllegalArgumentException("Save a valid plugin ID before removing staged assets.");
        }
        for (String path : new ArrayList<>(removedEditorAssetPaths)) {
            String problem = managedAssetPathProblem(path, textOf(tab() == null ? null : tab().edtPluginEditorJson));
            if (!TextUtils.isEmpty(problem)) throw new IllegalArgumentException(problem);
            repository().deletePluginFile(validation.id, path);
            debug("editor-assets", "removed staged asset " + path);
        }
        removedEditorAssetPaths.clear();
        renderEditorAssets();
    }

    private void updateEditorJsonIconPath(TabPluginsBinding tab, String iconPath) {
        if (tab == null || tab.edtPluginEditorJson == null || TextUtils.isEmpty(iconPath)) return;
        String json = textOf(tab.edtPluginEditorJson).trim();
        if (TextUtils.isEmpty(json)) return;
        try {
            JSONObject root = new JSONObject(json);
            root.put("icon", iconPath);
            tab.edtPluginEditorJson.setText(root.toString(2) + "\n");
        } catch (Throwable ignored) {
        }
    }

    private void copyPendingEditorIconIfAny(ValidationResult validation) throws Exception {
        if (pendingEditorIconUri == null) return;
        if (validation == null || !validation.ok || !PluginManifest.isSafeId(validation.id)) {
            throw new IllegalArgumentException("Save a valid plugin ID before copying the selected icon.");
        }
        String iconPath = validation.icon;
        if (TextUtils.isEmpty(iconPath)) {
            throw new IllegalArgumentException("plugin.json does not declare an icon path. Build or edit plugin.json before saving the selected icon.");
        }
        if (!isSafeRelativePluginPath(iconPath)) {
            throw new IllegalArgumentException("Unsafe icon path: " + iconPath);
        }
        File copied = repository().copyUriToPluginFile(validation.id, pendingEditorIconUri, iconPath, MAX_EDITOR_ICON_BYTES);
        debug("editor", "copied selected icon to " + copied.getAbsolutePath());
        clearPendingEditorIcon();
    }

    private void copyPendingEditorScriptsIfAny(ValidationResult validation, String pluginJson) throws Exception {
        if (pendingEditorScriptUris.isEmpty()) return;
        if (validation == null || !validation.ok || !PluginManifest.isSafeId(validation.id)) {
            throw new IllegalArgumentException("Save a valid plugin ID before copying selected scripts.");
        }
        Set<String> declaredScripts = declaredScriptPaths(pluginJson);
        if (declaredScripts.isEmpty()) return;
        ArrayList<String> copiedPaths = new ArrayList<>();
        for (Map.Entry<String, Uri> entry : new ArrayList<>(pendingEditorScriptUris.entrySet())) {
            String relativePath = entry.getKey();
            Uri uri = entry.getValue();
            if (!declaredScripts.contains(relativePath)) continue;
            if (!looksLikePluginScriptPath(relativePath)) {
                throw new IllegalArgumentException("Unsafe script path: " + relativePath);
            }
            File copied = repository().copyUriToPluginFile(validation.id, uri, relativePath, MAX_EDITOR_SCRIPT_BYTES);
            copiedPaths.add(relativePath);
            debug("editor", "copied selected script to " + copied.getAbsolutePath());
        }
        for (String path : copiedPaths) pendingEditorScriptUris.remove(path);
    }

    private void loadEditorAssetsFromStaged() {
        loadEditorAssetsFromStaged(true);
    }

    private void loadEditorAssetsFromStaged(boolean showStatus) {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        String id = textOf(tab.edtPluginEditorId).trim();
        if (!PluginManifest.isSafeId(id)) {
            if (showStatus) setEditorStatus("Enter or load a valid Plugin ID before loading staged assets.");
            return;
        }
        try {
            ArrayList<String> paths = new ArrayList<>(repository().listPluginFilesUnder(id, "assets"));
            paths.removeAll(removedEditorAssetPaths);
            for (String path : pendingEditorAssetUris.keySet()) {
                if (!paths.contains(path)) paths.add(path);
            }
            java.util.Collections.sort(paths);
            editorAssetStore.load(paths);
            renderEditorAssets();
            if (showStatus) setEditorStatus("Loaded " + paths.size() + " managed asset(s) from " + id + ". General assets stay under assets/ and are included when the plugin is packaged.");
            debug("editor-assets", "loaded staged assets count=" + paths.size() + ", id=" + id);
        } catch (Throwable t) {
            if (showStatus) setEditorStatus("Load staged assets failed: " + safeMessage(t));
            debug("editor-assets", "load failed: " + safeMessage(t));
        }
    }

    private void queueEditorAsset() {
        TabPluginsBinding tab = tab();
        if (tab == null || pendingEditorAssetPickerUri == null) return;
        String path = normalizePluginPath(textOf(tab.edtPluginEditorAssetPath));
        String problem = managedAssetPathProblem(path, textOf(tab.edtPluginEditorJson));
        if (!TextUtils.isEmpty(problem)) {
            setEditorStatus(problem);
            return;
        }
        String selectedPath = editorAssetStore.selectedPath();
        if (!TextUtils.isEmpty(selectedPath) && !selectedPath.equals(path)
                && pendingEditorAssetUris.containsKey(selectedPath)) {
            pendingEditorAssetUris.remove(selectedPath);
            editorAssetStore.removeSelected();
        }
        pendingEditorAssetUris.put(path, pendingEditorAssetPickerUri);
        removedEditorAssetPaths.remove(path);
        pendingEditorAssetPickerUri = null;
        editorAssetStore.addOrSelect(path);
        renderEditorAssets();
        setEditorStatus("Queued " + path + " for copy or replacement. Tap Save, Save UI, or Package to apply managed asset changes.");
        debug("editor-assets", "queued path=" + path);
    }

    private void removeSelectedEditorAsset() {
        TabPluginsBinding tab = tab();
        String path = editorAssetStore.selectedPath();
        if (tab == null || TextUtils.isEmpty(path)) return;
        pendingEditorAssetUris.remove(path);
        if (path.equals(normalizePluginPath(textOf(tab.edtPluginEditorAssetPath)))) pendingEditorAssetPickerUri = null;
        String id = textOf(tab.edtPluginEditorId).trim();
        File staged = PluginManifest.isSafeId(id) ? new File(new File(repository().getPluginRoot(), id), path) : null;
        boolean markRemoval = staged != null && staged.isFile();
        if (markRemoval) removedEditorAssetPaths.add(path);
        else removedEditorAssetPaths.remove(path);
        editorAssetStore.removeSelected();
        tab.edtPluginEditorAssetPath.setText("");
        renderEditorAssets();
        setEditorStatus(markRemoval
                ? "Marked " + path + " for removal. Tap Save, Save UI, or Package to apply the deletion."
                : "Removed pending asset " + path + ".");
        debug("editor-assets", (markRemoval ? "marked removal path=" : "removed pending path=") + path);
    }

    private void selectEditorAssetRow(int index) {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        editorAssetStore.select(index);
        String path = editorAssetStore.selectedPath();
        tab.edtPluginEditorAssetPath.setText(path);
        pendingEditorAssetPickerUri = pendingEditorAssetUris.get(path);
        renderEditorAssets();
    }

    private void renderEditorAssets() {
        TabPluginsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.llPluginEditorAssetsList == null) return;
        tab.llPluginEditorAssetsList.removeAllViews();
        List<String> paths = editorAssetStore.snapshot();
        if (paths.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No managed general assets loaded. Choose a file or load assets/ from the staged plugin.");
            empty.setTextSize(12f);
            empty.setPadding(dp(8), dp(10), dp(8), dp(10));
            tab.llPluginEditorAssetsList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            String id = textOf(tab.edtPluginEditorId).trim();
            File pluginDir = PluginManifest.isSafeId(id) ? new File(repository().getPluginRoot(), id) : null;
            for (int i = 0; i < paths.size(); i++) {
                final int index = i;
                String path = paths.get(i);
                boolean selected = index == editorAssetStore.selectedIndex();
                MaterialCardView card = new MaterialCardView(activity);
                card.setUseCompatPadding(false);
                card.setCardElevation(0f);
                card.setRadius(dp(8));
                card.setStrokeWidth(dp(selected ? 2 : 1));
                card.setStrokeColor(pluginRowStroke(activity, false));
                if (selected) card.setCardBackgroundColor(pluginRowFill(activity, false));
                card.setClickable(true);
                card.setFocusable(true);
                card.setContentDescription("Select managed plugin asset " + path);
                card.setOnClickListener(v -> selectEditorAssetRow(index));

                LinearLayout box = new LinearLayout(activity);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(10), dp(7), dp(10), dp(7));
                card.addView(box, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView title = new TextView(activity);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setText(path);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(title, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                boolean pending = pendingEditorAssetUris.containsKey(path);
                boolean staged = pluginDir != null && new File(pluginDir, path).isFile();
                TextView details = new TextView(activity);
                details.setText(pending ? (staged ? "Pending replacement" : "Pending copy") : (staged ? "Staged asset" : "Asset path"));
                details.setTextSize(12f);
                box.addView(details, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(6);
                tab.llPluginEditorAssetsList.addView(card, lp);
            }
        }
        if (tab.txtPluginEditorAssetSelection != null) {
            String path = editorAssetStore.selectedPath();
            String suffix = removedEditorAssetPaths.isEmpty() ? "" : " " + removedEditorAssetPaths.size() + " staged asset(s) are marked for removal.";
            tab.txtPluginEditorAssetSelection.setText(TextUtils.isEmpty(path)
                    ? "No managed asset selected." + suffix
                    : "Selected managed asset: " + path + ". Choose a replacement file or tap Remove." + suffix);
        }
        updatePluginEditorActionState();
    }

    private String managedAssetPathProblem(String rawPath, String pluginJson) {
        String path = normalizePluginPath(rawPath);
        if (TextUtils.isEmpty(path)) return "Enter an assets/ path before adding the selected file.";
        if (!isSafeRelativePluginPath(path) || !path.startsWith("assets/") || path.length() <= "assets/".length()) {
            return "Managed general assets must use a safe path under assets/, for example assets/help.txt.";
        }
        if (pluginFilePathsConflict(path, PluginManifest.FILE_NAME)) {
            return "Managed asset path conflicts with reserved " + PluginManifest.FILE_NAME + ".";
        }
        if (TextUtils.isEmpty(pluginJson == null ? "" : pluginJson.trim())) return "Build or enter plugin.json before adding managed assets.";
        try {
            JSONObject root = new JSONObject(pluginJson);
            String icon = normalizePluginPath(root.optString("icon", ""));
            if (!TextUtils.isEmpty(icon) && pluginFilePathsConflict(path, icon)) return "Managed asset path conflicts with the declared icon: " + icon;
            String runtime = root.optString("runtime", "declarative").trim();
            String entry = root.optString("entry", "").trim();
            JSONArray actions = root.optJSONArray("actions");
            for (String uiPath : declaredDeclarativeUiPaths(actions, runtime, entry)) {
                if (pluginFilePathsConflict(path, uiPath)) return "Managed asset path conflicts with declarative UI file: " + uiPath;
            }
            for (String scriptPath : declaredScriptPaths(pluginJson)) {
                if (pluginFilePathsConflict(path, scriptPath)) return "Managed asset path conflicts with script file: " + scriptPath;
            }
        } catch (Throwable t) {
            return "Correct plugin.json before adding managed assets: " + safeMessage(t);
        }
        return "";
    }

    private static String normalizePluginPath(String path) {
        return path == null ? "" : path.trim().replace('\\', '/');
    }

    private static Set<String> declaredDeclarativeUiPaths(JSONArray actions, String runtime, String entry) {
        Set<String> paths = new java.util.LinkedHashSet<>();
        if ("declarative".equals(runtime)) {
            String rootEntry = TextUtils.isEmpty(entry) ? "ui.json" : entry.trim().replace('\\', '/');
            if (!TextUtils.isEmpty(rootEntry)) paths.add(rootEntry);
        }
        if (actions == null) return paths;
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action == null) continue;
            String type = action.optString("type", actionTypeForRuntime(runtime)).trim();
            if (!isDeclarativeActionType(type)) continue;
            String path = firstNonEmpty(action.optString("target", ""), action.optString("handler", ""));
            if (!TextUtils.isEmpty(path)) paths.add(path.trim().replace('\\', '/'));
        }
        return paths;
    }

    private static Set<String> declaredDeclarativeUiPaths(PluginManifest manifest) {
        Set<String> paths = new java.util.LinkedHashSet<>();
        if (manifest == null) return paths;
        if ("declarative".equals(manifest.runtime)) {
            paths.add(TextUtils.isEmpty(manifest.entry) ? "ui.json" : manifest.entry.trim().replace('\\', '/'));
        }
        for (PluginAction action : manifest.actions) {
            if (action == null || !action.isDeclarativeAction()) continue;
            String path = firstNonEmpty(action.target, action.handler);
            if (!TextUtils.isEmpty(path)) paths.add(path.trim().replace('\\', '/'));
        }
        return paths;
    }

    private static boolean pluginPathsEqual(String first, String second) {
        if (TextUtils.isEmpty(first) || TextUtils.isEmpty(second)) return false;
        return first.trim().replace('\\', '/').equals(second.trim().replace('\\', '/'));
    }

    private static Set<String> declaredScriptPaths(String pluginJson) {
        Set<String> paths = new HashSet<>();
        if (TextUtils.isEmpty(pluginJson)) return paths;
        try {
            JSONArray actions = new JSONObject(pluginJson).optJSONArray("actions");
            if (actions == null) return paths;
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.optJSONObject(i);
                if (action == null) continue;
                String script = action.optString("script", "").trim().replace('\\', '/');
                if (!TextUtils.isEmpty(script)) paths.add(script);
            }
        } catch (Throwable ignored) {
        }
        return paths;
    }

    private boolean hasPendingEditorScript(String relativePath) {
        if (TextUtils.isEmpty(relativePath)) return false;
        return pendingEditorScriptUris.containsKey(relativePath.trim().replace('\\', '/'));
    }

    private static String safePluginAssetFileName(String displayName, String fallback) {
        String raw = TextUtils.isEmpty(displayName) ? fallback : displayName;
        raw = raw == null ? "" : raw.replace('\\', '/');
        int slash = raw.lastIndexOf('/');
        if (slash >= 0) raw = raw.substring(slash + 1);
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            out.append(ok ? c : '_');
        }
        String safe = out.toString();
        while (safe.startsWith(".")) safe = safe.substring(1);
        if (TextUtils.isEmpty(safe) || PluginManifest.FILE_NAME.equalsIgnoreCase(safe) || "ui.json".equalsIgnoreCase(safe)) {
            safe = TextUtils.isEmpty(fallback) ? "icon.png" : fallback;
        }
        if (safe.length() > 96) safe = safe.substring(safe.length() - 96);
        return safe;
    }


    private void ensureFirstRunDefaultToolPluginsThenRefresh() {
        debug("startup-defaults", "checking first-run bundled tool plugin defaults");
        worker.execute(() -> {
            try {
                PluginDefaultInstaller.Result result = PluginDefaultInstaller.ensureFirstRunToolPlugins(prefs(), repository());
                runUi(() -> {
                    if (result != null && result.attempted) {
                        int count = result.installed == null ? 0 : result.installed.size();
                        append("[plugins] Installed " + count + " default bundled tool plugin(s) for first run.\n");
                        debug("startup-defaults", "installed default bundled tool plugins count=" + count);
                    } else if (result != null && result.skippedExisting) {
                        debug("startup-defaults", "skipped default install because staged plugins already exist");
                    }
                    refreshPlugins();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("startup-defaults", "default tool plugin install failed: " + safeMessage(t));
                    setStatus("Default tool plugin setup failed: " + safeMessage(t));
                    refreshPlugins();
                });
            }
        });
    }

    private void loadBundledPluginList() {
        debug("bundled", "loading bundled plugin asset list");
        worker.execute(() -> {
            try {
                List<PluginRepository.BundledPluginInfo> bundled = repository().loadBundledPluginInfos();
                runUi(() -> renderBundledPlugins(bundled));
            } catch (Throwable t) {
                runUi(() -> {
                    debug("bundled", "bundled list failed: " + safeMessage(t));
                    renderBundledPlugins(null);
                    setStatus("Bundled plugin list failed: " + safeMessage(t));
                });
            }
        });
    }

    private void renderBundledPlugins(List<PluginRepository.BundledPluginInfo> bundled) {
        TabPluginsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.llPluginsBundledList == null) return;
        tab.llPluginsBundledList.removeAllViews();
        currentBundledPlugins.clear();
        if (bundled != null) currentBundledPlugins.addAll(bundled);

        if (bundled == null || bundled.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No bundled plugins found in assets/plugins.");
            empty.setTextSize(12f);
            empty.setPadding(0, dp(8), 0, dp(8));
            tab.llPluginsBundledList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        if (!bundledSelectionInitialized) {
            selectedBundledAssets.clear();
            bundledSelectionInitialized = true;
        }

        pruneBundledSelection();
        for (PluginRepository.BundledPluginInfo info : bundled) {
            tab.llPluginsBundledList.addView(buildBundledPluginRow(activity, info), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        debug("bundled", "rendered bundled asset choices=" + bundled.size() + ", selected=" + selectedBundledAssets.size());
    }

    private View buildBundledPluginRow(Activity activity, PluginRepository.BundledPluginInfo info) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setUseCompatPadding(false);
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardLp);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(6), dp(8), dp(6));
        card.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        PluginManifest manifest = info == null ? null : info.manifest;
        String assetName = info == null ? "" : info.assetName;
        String titleText = manifest == null ? assetName : manifest.name + "  v" + manifest.version;

        CheckBox check = new CheckBox(activity);
        check.setText(titleText);
        check.setTextSize(13f);
        check.setTypeface(Typeface.DEFAULT_BOLD);
        check.setChecked(selectedBundledAssets.contains(assetName));
        check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (TextUtils.isEmpty(assetName)) return;
            if (isChecked) selectedBundledAssets.add(assetName);
            else selectedBundledAssets.remove(assetName);
            debug("bundled", "selection " + (isChecked ? "checked " : "unchecked ") + assetName);
        });
        box.addView(check, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(activity);
        StringBuilder details = new StringBuilder();
        if (manifest != null && !TextUtils.isEmpty(manifest.description)) details.append(manifest.description).append('\n');
        details.append("Asset: ").append(TextUtils.isEmpty(assetName) ? "unknown" : assetName);
        if (manifest != null) details.append("  ID: ").append(manifest.id);
        desc.setText(details.toString());
        desc.setTextSize(12f);
        desc.setPadding(dp(32), 0, 0, 0);
        box.addView(desc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private List<String> getSelectedBundledAssetNames() {
        return new ArrayList<>(selectedBundledAssets);
    }

    private void selectBundledPlugins(SelectionMode mode) {
        if (mode == null) return;
        if (mode == SelectionMode.NONE) {
            selectedBundledAssets.clear();
        } else {
            selectedBundledAssets.clear();
            for (PluginRepository.BundledPluginInfo info : currentBundledPlugins) {
                if (info == null || TextUtils.isEmpty(info.assetName)) continue;
                if (mode == SelectionMode.ALL || isDemoBundledPlugin(info)) {
                    selectedBundledAssets.add(info.assetName);
                }
            }
        }
        bundledSelectionInitialized = true;
        renderBundledPlugins(new ArrayList<>(currentBundledPlugins));
        int total = currentBundledPlugins.size();
        int selected = selectedBundledAssets.size();
        String label = mode == SelectionMode.ALL ? "Selected all bundled plugins."
                : mode == SelectionMode.NONE ? "Cleared bundled plugin selection."
                : "Selected bundled demo/test plugins.";
        setStatus(label + " " + selected + " of " + total + " selected.");
        debug("bundled", "selection mode=" + mode + " selected=" + selected + "/" + total);
    }

    private void pruneBundledSelection() {
        Set<String> valid = new HashSet<>();
        for (PluginRepository.BundledPluginInfo info : currentBundledPlugins) {
            if (info != null && !TextUtils.isEmpty(info.assetName)) valid.add(info.assetName);
        }
        selectedBundledAssets.retainAll(valid);
    }

    private boolean isDemoBundledPlugin(PluginRepository.BundledPluginInfo info) {
        if (info == null) return false;
        PluginManifest manifest = info.manifest;
        String id = manifest == null ? "" : manifest.id;
        String name = manifest == null ? "" : manifest.name;
        String asset = info.assetName;
        String combined = (id + " " + name + " " + asset).toLowerCase(Locale.US);
        return combined.contains("demo")
                || combined.contains("_test")
                || combined.contains(" test")
                || combined.contains("build_prop")
                || combined.contains("device_info")
                || combined.contains("log_snapshot");
    }

    private void restoreSelectedBundledPlugins() {
        List<String> selected = getSelectedBundledAssetNames();
        if (selected.isEmpty()) {
            setStatus("Select at least one bundled plugin to restore.");
            return;
        }
        debug("reload-internal", "restoring selected bundled asset plugins count=" + selected.size());
        setStatus("Restoring selected bundled plugins from assets...");
        worker.execute(() -> {
            try {
                List<PluginManifest> manifests = repository().installBundledPlugins(selected);
                runUi(() -> {
                    int count = manifests == null ? 0 : manifests.size();
                    setStatus("Restored " + count + " selected bundled plugin(s).");
                    debug("reload-internal", "restored selected count=" + count);
                    append("[plugins] Restored " + count + " selected bundled plugin(s) from assets.\n");
                    refreshPlugins();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("reload-internal", "restore selected failed: " + safeMessage(t));
                    setStatus("Restore selected bundled plugins failed: " + safeMessage(t));
                });
            }
        });
    }

    private void stagePathFromField() {
        TabPluginsBinding tab = tab();
        String raw = tab == null || tab.edtPluginPath == null ? "" : String.valueOf(tab.edtPluginPath.getText()).trim();
        if (TextUtils.isEmpty(raw)) {
            setStatus("Enter or select a .ptp/.zip plugin, content URI, or development plugin folder first.");
            return;
        }
        if (raw.startsWith("content://") || raw.startsWith("file://")) {
            stageUri(Uri.parse(raw), raw);
            return;
        }
        debug("stage", "staging plugin path=" + raw);
        setStatus("Installing plugin path/folder...");
        worker.execute(() -> {
            try {
                PluginManifest manifest = repository().installPath(raw);
                runUi(() -> {
                    setStatus("Staged " + manifest.name + " v" + manifest.version + ".");
                    debug("stage", "path staged id=" + manifest.id + ", version=" + manifest.version);
                    append("[plugins] Staged path: " + manifest.id + "\n");
                    refreshPlugins();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("stage", "path stage failed: " + safeMessage(t));
                    setStatus("Install path/folder failed: " + safeMessage(t));
                });
            }
        });
    }

    private void stageUri(Uri uri, String name) {
        if (uri == null) return;
        debug("import", "importing plugin uri=" + uri);
        setStatus("Importing plugin...");
        worker.execute(() -> {
            try {
                PluginManifest manifest = repository().installUri(uri, name);
                runUi(() -> {
                    setStatus("Imported " + manifest.name + " v" + manifest.version + ".");
                    debug("import", "imported id=" + manifest.id + ", version=" + manifest.version);
                    append("[plugins] Imported plugin: " + manifest.id + "\n");
                    refreshPlugins();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("import", "import failed: " + safeMessage(t));
                    setStatus("Import Plugin failed: " + safeMessage(t));
                });
            }
        });
    }

    private void refreshPlugins() {
        debug("refresh", "loading staged plugin manifests");
        worker.execute(() -> {
            try {
                List<PluginManifest> plugins = repository().loadPlugins();
                debug("refresh", "loaded " + (plugins == null ? 0 : plugins.size()) + " staged plugin manifest(s)");
                runUi(() -> renderPlugins(plugins));
            } catch (Throwable t) {
                runUi(() -> {
                    debug("refresh", "refresh failed: " + safeMessage(t));
                    setStatus("Refresh failed: " + safeMessage(t));
                });
            }
        });
    }

    private void renderPlugins(List<PluginManifest> plugins) {
        TabPluginsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.llPluginsList == null) return;
        tab.llPluginsList.removeAllViews();

        if (plugins == null || plugins.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No staged plugins found. Check bundled plugins and tap Restore Selected, or import a .ptp/.zip plugin.");
            empty.setTextSize(13f);
            empty.setPadding(0, dp(8), 0, dp(8));
            tab.llPluginsList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setStatus("No plugins staged. Root: " + repository().getPluginRoot().getAbsolutePath());
            return;
        }

        int enabled = 0;
        for (PluginManifest plugin : plugins) {
            if (!isPluginDisabled(plugin.id)) enabled++;
            tab.llPluginsList.addView(buildPluginCard(activity, plugin), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        debug("render", "rendered staged=" + plugins.size() + ", enabled=" + enabled);
        setStatus("Plugins: " + plugins.size() + " staged, " + enabled + " enabled. Root: " + repository().getPluginRoot().getAbsolutePath());
    }


    private void showAllPluginReadiness() {
        Activity activity = activity();
        if (activity == null) return;
        setStatus("Checking all plugin readiness...");
        debug("all-readiness", "check requested");
        worker.execute(() -> {
            try {
                List<PluginManifest> plugins = repository().loadPlugins();
                Set<String> disabledIds = disabledPluginIds(plugins);
                String report = PluginAllReadinessReport.build(prefs(), plugins, disabledIds);
                runUi(() -> {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("All Plugin Readiness")
                            .setMessage(report + "\nThis all-plugin check is read-only. It does not run plugin actions, execute shell commands, approve scripts, trust code payloads, load trusted-code payloads, or launch plugin APK components.")
                            .setPositiveButton("OK", null)
                            .show();
                    setStatus("All plugin readiness checked: " + (plugins == null ? 0 : plugins.size()) + " staged plugin(s).");
                    debug("all-readiness", "shown count=" + (plugins == null ? 0 : plugins.size()));
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("all-readiness", "check failed: " + safeMessage(t));
                    setStatus("All plugin readiness failed: " + safeMessage(t));
                });
            }
        });
    }

    private void saveAllPluginReadiness() {
        setStatus("Saving all plugin readiness...");
        debug("all-readiness", "save requested");
        worker.execute(() -> {
            try {
                List<PluginManifest> plugins = repository().loadPlugins();
                Set<String> disabledIds = disabledPluginIds(plugins);
                String report = PluginAllReadinessReport.build(prefs(), plugins, disabledIds)
                        + "\n\nThis saved all-plugin readiness report is an audit aid only. It does not run plugin actions, execute shell commands, approve scripts, trust code payloads, load trusted-code payloads, launch plugin APK components, or enable background execution.";
                File out = writeAllPluginReadiness(report);
                runUi(() -> {
                    append("[plugins] Saved all plugin readiness: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved all plugin readiness: " + out.getAbsolutePath());
                    debug("all-readiness", "saved path=" + out.getAbsolutePath() + ", count=" + (plugins == null ? 0 : plugins.size()));
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("all-readiness", "save failed: " + safeMessage(t));
                    setStatus("Save all plugin readiness failed: " + safeMessage(t));
                });
            }
        });
    }

    private Set<String> disabledPluginIds(List<PluginManifest> plugins) {
        HashSet<String> out = new HashSet<>();
        if (plugins == null) return out;
        for (PluginManifest plugin : plugins) {
            if (plugin != null && isPluginDisabled(plugin.id)) out.add(plugin.id);
        }
        return out;
    }

    private View buildPluginCard(Activity activity, PluginManifest plugin) {
        MaterialCardView card = new MaterialCardView(activity);
        boolean disabled = isPluginDisabled(plugin.id);
        card.setUseCompatPadding(false);
        card.setCardElevation(0f);
        card.setRadius(dp(10));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(pluginRowStroke(activity, disabled));
        card.setCardBackgroundColor(pluginRowFill(activity, disabled));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardLp);
        card.setOnLongClickListener(v -> {
            loadPluginIntoEditor(plugin);
            return true;
        });

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView icon = buildPluginIconView(activity, plugin);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconLp.rightMargin = dp(10);
        header.addView(icon, iconLp);

        TextView title = new TextView(activity);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setText(plugin.name + "  v" + plugin.version + (disabled ? "  • disabled" : "  • enabled"));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton menuButton = compactPluginButton(activity, "☰");
        menuButton.setContentDescription("Plugin options");
        menuButton.setOnClickListener(v -> showPluginOptionsMenu(menuButton, plugin));
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        menuLp.rightMargin = dp(6);
        header.addView(menuButton, menuLp);

        MaterialButton toggle = compactPluginButton(activity, disabled ? "Enable" : "✕");
        toggle.setContentDescription(disabled ? "Enable plugin" : "Disable plugin");
        toggle.setMinWidth(dp(disabled ? 86 : 44));
        toggle.setOnClickListener(v -> setPluginDisabled(plugin.id, !disabled));
        header.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(activity);
        desc.setText(buildPluginDescription(plugin, disabled));
        desc.setTextSize(12f);
        desc.setPadding(dp(48), dp(4), 0, 0);
        desc.setOnLongClickListener(v -> {
            loadPluginIntoEditor(plugin);
            return true;
        });
        box.addView(desc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (disabled) return card;

        if (plugin.actions.isEmpty()) {
            TextView none = new TextView(activity);
            none.setText("No valid actions are declared in this plugin.");
            none.setTextSize(12f);
            none.setPadding(0, dp(8), 0, 0);
            box.addView(none, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return card;
        }

        LinearLayout row = null;
        for (int i = 0; i < plugin.actions.size(); i++) {
            if ((i & 1) == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = dp(8);
                box.addView(row, rowLp);
            }
            PluginAction action = plugin.actions.get(i);
            MaterialButton button = compactPluginButton(activity, TextUtils.isEmpty(action.title) ? action.id : action.title);
            button.setOnClickListener(v -> runPluginAction(plugin, action));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if ((i & 1) == 1) lp.leftMargin = dp(8);
            if (row != null) row.addView(button, lp);
        }
        if ((plugin.actions.size() & 1) == 1 && row != null) {
            TextView spacer = new TextView(activity);
            row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        }
        return card;
    }

    private ImageView buildPluginIconView(Activity activity, PluginManifest plugin) {
        ImageView view = new ImageView(activity);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Drawable drawable = loadPluginIcon(plugin);
        if (drawable != null) {
            view.setImageDrawable(drawable);
        } else {
            view.setBackgroundColor(withAlpha(pluginRowStroke(activity, isPluginDisabled(plugin.id)), 0x78));
            view.setContentDescription("Plugin icon placeholder");
        }
        return view;
    }

    private Drawable loadPluginIcon(PluginManifest plugin) {
        if (plugin == null || plugin.homeDir == null || TextUtils.isEmpty(plugin.icon)) return null;
        try {
            File file = new File(plugin.homeDir, plugin.icon);
            String root = plugin.homeDir.getCanonicalPath() + File.separator;
            String path = file.getCanonicalPath();
            if (!path.startsWith(root) || !file.isFile()) return null;
            return Drawable.createFromPath(file.getAbsolutePath());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private MaterialButton compactPluginButton(Activity activity, String text) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(dp(40));
        button.setMinWidth(dp(44));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(4));
        return button;
    }

    private int pluginRowFill(Activity activity, boolean disabled) {
        int base = ThemeColorController.isCustom(activity)
                ? ThemeColorController.getCustomColor(activity)
                : 0xFF88D8FF;
        int tint = disabled ? 0xFFFF4F5F : 0xFF46D36A;
        return withAlpha(darken(blend(base, tint, disabled ? 0.62f : 0.48f), 0.34f), disabled ? 0x4A : 0x42);
    }

    private int pluginRowStroke(Activity activity, boolean disabled) {
        int base = ThemeColorController.isCustom(activity)
                ? ThemeColorController.getCustomColor(activity)
                : 0xFFBFD7FF;
        int tint = disabled ? 0xFFFF4F5F : 0xFF46D36A;
        return withAlpha(lighten(blend(base, tint, disabled ? 0.72f : 0.54f), 0.20f), disabled ? 0xB6 : 0xAA);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private static int darken(int color, float keep) {
        keep = Math.max(0f, Math.min(1f, keep));
        return Color.rgb(
                Math.max(0, Math.round(Color.red(color) * keep)),
                Math.max(0, Math.round(Color.green(color) * keep)),
                Math.max(0, Math.round(Color.blue(color) * keep)));
    }

    private static int lighten(int color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * amount)),
                Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * amount)),
                Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount)));
    }

    private static int blend(int from, int to, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    private String buildPluginDescription(PluginManifest plugin, boolean disabled) {
        StringBuilder sb = new StringBuilder();
        if (disabled) sb.append("Disabled. Actions are not available until re-enabled.\n");
        if (!TextUtils.isEmpty(plugin.description)) sb.append(plugin.description).append('\n');
        sb.append("Config: window ")
                .append(TextUtils.isEmpty(plugin.windowStyle) ? "compact" : plugin.windowStyle)
                .append("/")
                .append(TextUtils.isEmpty(plugin.windowFit) ? "current" : plugin.windowFit)
                .append(", reserved startup flag ")
                .append(isPluginLoadAtStartup(plugin.id) ? "saved on" : "off")
                .append(" (disabled/inactive; future feature, no runtime effect)")
                .append(", large override ")
                .append(isPluginRunInWindow(plugin.id) ? "on" : "off")
                .append('\n');
        sb.append("Capabilities: ").append(PluginRuntimePolicy.capabilitySummary(plugin)).append('\n');
        if (PluginRuntimePolicy.pluginHasScriptActions(plugin)) {
            sb.append("Script approval: ").append(PluginRuntimePolicy.scriptApprovalStatus(prefs(), plugin)).append('\n');
            sb.append("Script run confirm: ")
                    .append(PluginRuntimePolicy.isScriptRunConfirmationRequired(prefs()) ? "on" : "off")
                    .append('\n');
        }
        if (PluginRuntimePolicy.pluginHasTrustedDexActions(plugin)) {
            sb.append("Trusted code: ").append(PluginRuntimePolicy.trustedDexApprovalStatus(prefs(), plugin)).append('\n');
            sb.append("Trusted run confirm: ")
                    .append(PluginRuntimePolicy.isTrustedDexRunConfirmationRequired(prefs()) ? "on" : "off")
                    .append('\n');
        }
        if (!TextUtils.isEmpty(plugin.author)) sb.append("Author: ").append(plugin.author).append('\n');
        sb.append("ID: ").append(plugin.id).append("  API: ").append(plugin.apiVersion);
        if (plugin.homeDir != null) sb.append("\nHome: ").append(plugin.homeDir.getAbsolutePath());
        if (!TextUtils.isEmpty(plugin.comments)) sb.append("\n").append(plugin.comments);
        return sb.toString();
    }

    private void showPluginOptionsMenu(View anchor, PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || anchor == null || plugin == null) return;
        boolean disabled = isPluginDisabled(plugin.id);
        PopupMenu popup = new PopupMenu(activity, anchor);
        Menu menu = popup.getMenu();

        MenuItem startup = menu.add(0, MENU_LOAD_STARTUP, 0, "Reserved Startup Flag (Disabled/Inactive)");
        startup.setCheckable(true);
        startup.setChecked(isPluginLoadAtStartup(plugin.id));

        MenuItem window = menu.add(0, MENU_RUN_WINDOW, 1, "Use Large Window Override");
        window.setCheckable(true);
        window.setChecked(isPluginRunInWindow(plugin.id));

        menu.add(0, MENU_TOGGLE_ENABLED, 2, disabled ? "Enable Plugin" : "Disable Plugin");
        menu.add(0, MENU_EDIT_PLUGIN, 3, "Edit Plugin Config");
        menu.add(0, MENU_CHECK_COMPLETE_READINESS, 4, "Check Complete Readiness");
        menu.add(0, MENU_SAVE_COMPLETE_READINESS, 5, "Save Complete Readiness");
        menu.add(0, MENU_REVIEW_POLICY, 6, "Review Runtime Policy");
        if (PluginRuntimePolicy.pluginHasTrustedDexActions(plugin)) {
            menu.add(0, MENU_REVIEW_TRUSTED_CODE, 5, "Review Trusted Code Policy");
            menu.add(0, MENU_CHECK_TRUSTED_CODE_READINESS, 6, "Check Trusted Code Readiness");
            menu.add(0, MENU_SAVE_TRUSTED_CODE_REVIEW, 7, "Save Trusted Code Review");
            menu.add(0, MENU_SAVE_TRUSTED_CODE_READINESS, 8, "Save Trusted Code Readiness");
            menu.add(0, MENU_APPROVE_TRUSTED_CODE, 9, "Trust Reviewed Code Payload");
            menu.add(0, MENU_CLEAR_TRUSTED_CODE_APPROVAL, 10, "Clear Trusted Code Trust");
        }
        if (PluginRuntimePolicy.pluginHasScriptActions(plugin)) {
            menu.add(0, MENU_CHECK_SCRIPT_READINESS, 9, "Check Script Readiness");
            menu.add(0, MENU_SAVE_SCRIPT_READINESS, 10, "Save Script Readiness");
            menu.add(0, MENU_APPROVE_SCRIPT_POLICY, 11, "Approve Script Actions");
            menu.add(0, MENU_CLEAR_SCRIPT_APPROVAL, 12, "Clear Script Approval");
        }
        if (PluginDeclarativeReadinessReport.pluginHasDeclarativeUi(plugin)) {
            menu.add(0, MENU_CHECK_DECLARATIVE_READINESS, 13, "Check Declarative UI Readiness");
            menu.add(0, MENU_SAVE_DECLARATIVE_READINESS, 14, "Save Declarative UI Readiness");
        }
        menu.add(0, MENU_CHECK_PACKAGE_READINESS, 15, "Check Package Readiness");
        menu.add(0, MENU_SAVE_PACKAGE_READINESS, 16, "Save Package Readiness");
        menu.add(0, MENU_CHECK_EXPORT_ROUND_TRIP, 17, "Check Export Round Trip");
        menu.add(0, MENU_SAVE_EXPORT_ROUND_TRIP, 18, "Save Export Round Trip");
        menu.add(0, MENU_SAVE_POLICY_REVIEW, 19, "Save Runtime Review");
        menu.add(0, MENU_EXPORT_PLUGIN, 20, "Export Plugin Package");
        menu.add(0, MENU_UNINSTALL, 21, "Uninstall Plugin");

        popup.setOnMenuItemClickListener(item -> handlePluginMenuItem(plugin, item));
        popup.show();
    }

    private boolean handlePluginMenuItem(PluginManifest plugin, MenuItem item) {
        if (plugin == null || item == null) return false;
        switch (item.getItemId()) {
            case MENU_LOAD_STARTUP:
                setPluginLoadAtStartup(plugin.id, !isPluginLoadAtStartup(plugin.id));
                return true;
            case MENU_RUN_WINDOW:
                setPluginRunInWindow(plugin.id, !isPluginRunInWindow(plugin.id));
                return true;
            case MENU_TOGGLE_ENABLED:
                setPluginDisabled(plugin.id, !isPluginDisabled(plugin.id));
                return true;
            case MENU_EDIT_PLUGIN:
                loadPluginIntoEditor(plugin);
                return true;
            case MENU_REVIEW_POLICY:
                showPluginRuntimePolicyReview(plugin);
                return true;
            case MENU_CHECK_COMPLETE_READINESS:
                showPluginCompleteReadiness(plugin);
                return true;
            case MENU_SAVE_COMPLETE_READINESS:
                savePluginCompleteReadiness(plugin);
                return true;
            case MENU_REVIEW_TRUSTED_CODE:
                showPluginTrustedCodeReview(plugin);
                return true;
            case MENU_SAVE_TRUSTED_CODE_REVIEW:
                savePluginTrustedCodeReview(plugin);
                return true;
            case MENU_CHECK_TRUSTED_CODE_READINESS:
                showPluginTrustedCodeReadiness(plugin);
                return true;
            case MENU_SAVE_TRUSTED_CODE_READINESS:
                savePluginTrustedCodeReadiness(plugin);
                return true;
            case MENU_APPROVE_TRUSTED_CODE:
                confirmApproveTrustedCode(plugin);
                return true;
            case MENU_CLEAR_TRUSTED_CODE_APPROVAL:
                confirmClearTrustedCodeApproval(plugin);
                return true;
            case MENU_APPROVE_SCRIPT_POLICY:
                confirmApproveScriptPolicy(plugin);
                return true;
            case MENU_CLEAR_SCRIPT_APPROVAL:
                confirmClearScriptApproval(plugin);
                return true;
            case MENU_CHECK_SCRIPT_READINESS:
                showPluginScriptReadiness(plugin);
                return true;
            case MENU_SAVE_SCRIPT_READINESS:
                savePluginScriptReadiness(plugin);
                return true;
            case MENU_CHECK_DECLARATIVE_READINESS:
                showPluginDeclarativeReadiness(plugin);
                return true;
            case MENU_SAVE_DECLARATIVE_READINESS:
                savePluginDeclarativeReadiness(plugin);
                return true;
            case MENU_CHECK_PACKAGE_READINESS:
                showPluginPackageReadiness(plugin);
                return true;
            case MENU_SAVE_PACKAGE_READINESS:
                savePluginPackageReadiness(plugin);
                return true;
            case MENU_CHECK_EXPORT_ROUND_TRIP:
                showPluginExportRoundTrip(plugin);
                return true;
            case MENU_SAVE_EXPORT_ROUND_TRIP:
                savePluginExportRoundTrip(plugin);
                return true;
            case MENU_SAVE_POLICY_REVIEW:
                savePluginRuntimePolicyReview(plugin);
                return true;
            case MENU_EXPORT_PLUGIN:
                exportInstalledPlugin(plugin);
                return true;
            case MENU_UNINSTALL:
                confirmUninstallPlugin(plugin);
                return true;
            default:
                return false;
        }
    }


    private void showPluginCompleteReadiness(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            String report = PluginCompleteReadinessReport.build(prefs(), staged, isPluginDisabled(staged.id));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Complete Plugin Readiness")
                    .setMessage(report + "\nThis combined check is read-only. It does not run plugin actions, execute shell commands, approve scripts, trust code payloads, load trusted-code payloads, or launch plugin APK components.")
                    .setPositiveButton("OK", null)
                    .show();
            debug("complete-readiness", "review shown for " + staged.id);
        } catch (Throwable t) {
            setStatus("Complete readiness check failed: " + safeMessage(t));
            debug("complete-readiness", "review failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void savePluginCompleteReadiness(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Saving complete readiness for " + plugin.name + "...");
        debug("complete-readiness", "save requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = PluginCompleteReadinessReport.build(prefs(), staged, isPluginDisabled(staged.id))
                        + "\n\nThis saved complete-readiness report is an audit aid only. It does not run plugin actions, execute shell commands, approve scripts, trust code payloads, load trusted-code payloads, launch plugin APK components, or enable background execution.";
                File out = writePluginCompleteReadiness(staged, report);
                runUi(() -> {
                    append("[plugins] Saved complete readiness: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved complete readiness: " + out.getAbsolutePath());
                    debug("complete-readiness", "saved id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("complete-readiness", "save failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Save complete readiness failed: " + safeMessage(t));
                });
            }
        });
    }

    private void showPluginPackageReadiness(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            String report = PluginPackageValidator.buildReadinessReport(staged);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Plugin Package Readiness")
                    .setMessage(report + "\nThis check reads plugin files only. It does not run plugin code, launch plugin APK components, approve script actions, or trust trusted-code payloads.")
                    .setPositiveButton("OK", null)
                    .show();
            debug("package-readiness", "review shown for " + staged.id);
        } catch (Throwable t) {
            setStatus("Package readiness check failed: " + safeMessage(t));
            debug("package-readiness", "review failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void savePluginPackageReadiness(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Saving package readiness for " + plugin.name + "...");
        debug("package-readiness", "save requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = PluginPackageValidator.buildReadinessReport(staged)
                        + "\nThis saved package-readiness report is an audit aid only. It does not run plugin code, approve script actions, or trust trusted-code payloads.";
                File out = writePluginPackageReadiness(staged, report);
                runUi(() -> {
                    append("[plugins] Saved package readiness: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved package readiness: " + out.getAbsolutePath());
                    debug("package-readiness", "saved id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("package-readiness", "save failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Save package readiness failed: " + safeMessage(t));
                });
            }
        });
    }

    private void showPluginExportRoundTrip(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Checking export round trip for " + plugin.name + "...");
        debug("export-roundtrip", "check requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = buildPluginExportRoundTripReport(staged, false)
                        + "\nThis export round-trip check is read-only. It packages to a temporary archive, inspects that archive through the normal import validator, then deletes the temporary archive. It does not install a copy, run plugin actions, execute shell commands, approve scripts, trust code payloads, or launch plugin APK components.";
                runUi(() -> {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("Plugin Export Round Trip")
                            .setMessage(report)
                            .setPositiveButton("OK", null)
                            .show();
                    setStatus("Export round trip checked for " + staged.name + ".");
                    debug("export-roundtrip", "check shown id=" + staged.id);
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("export-roundtrip", "check failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Export round trip failed: " + safeMessage(t));
                });
            }
        });
    }

    private void savePluginExportRoundTrip(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Saving export round trip for " + plugin.name + "...");
        debug("export-roundtrip", "save requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = buildPluginExportRoundTripReport(staged, false)
                        + "\n\nThis saved export-round-trip report is an audit aid only. It does not install a copy, run plugin actions, execute shell commands, approve scripts, trust code payloads, load trusted-code payloads, launch plugin APK components, or enable background execution.";
                File out = writePluginExportRoundTrip(staged, report);
                runUi(() -> {
                    append("[plugins] Saved export round trip: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved export round trip: " + out.getAbsolutePath());
                    debug("export-roundtrip", "saved id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("export-roundtrip", "save failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Save export round trip failed: " + safeMessage(t));
                });
            }
        });
    }

    private String buildPluginExportRoundTripReport(PluginManifest staged, boolean keepArchive) throws Exception {
        File archive = null;
        try {
            archive = createPluginRoundTripTempArchive(staged);
            packagePluginToArchive(staged, archive);
            PluginManifest inspected = repository().inspectArchive(archive);
            return PluginExportRoundTripReport.build(staged, inspected, archive, keepArchive);
        } finally {
            if (!keepArchive && archive != null) {
                try { archive.delete(); } catch (Throwable ignored) {}
            }
        }
    }


    private void showPluginScriptReadiness(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            String report = PluginRuntimeReviewReport.buildScriptReadiness(
                    prefs(), staged, isPluginDisabled(staged.id), staged.homeDir);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Script Action Readiness")
                    .setMessage(report + "\n\nThis check reads plugin manifest/script/UI files only. It does not run shell commands, approve scripts, launch plugin APK components, or change plugin policy.")
                    .setPositiveButton("OK", null)
                    .show();
            debug("script-readiness", "review shown for " + staged.id);
        } catch (Throwable t) {
            setStatus("Script readiness check failed: " + safeMessage(t));
            debug("script-readiness", "review failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void savePluginScriptReadiness(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Saving script readiness for " + plugin.name + "...");
        debug("script-readiness", "save requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = PluginRuntimeReviewReport.buildScriptReadiness(
                        prefs(), staged, isPluginDisabled(staged.id), staged.homeDir)
                        + "\n\nThis saved script-readiness report is an audit aid only. It does not run shell commands, approve scripts, or enable background execution.";
                File out = writePluginScriptReadiness(staged, report);
                runUi(() -> {
                    append("[plugins] Saved script readiness: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved script readiness: " + out.getAbsolutePath());
                    debug("script-readiness", "saved id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("script-readiness", "save failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Save script readiness failed: " + safeMessage(t));
                });
            }
        });
    }

    private void showPluginDeclarativeReadiness(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            String report = PluginDeclarativeReadinessReport.build(staged, isPluginDisabled(staged.id));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Declarative UI Readiness")
                    .setMessage(report + "\n\nThis check reads plugin manifest/UI files only. It does not open plugin UI, run plugin actions, execute shell commands, approve scripts, trust code payloads, or change plugin policy.")
                    .setPositiveButton("OK", null)
                    .show();
            debug("declarative-readiness", "review shown for " + staged.id);
        } catch (Throwable t) {
            setStatus("Declarative readiness check failed: " + safeMessage(t));
            debug("declarative-readiness", "review failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void savePluginDeclarativeReadiness(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Saving declarative UI readiness for " + plugin.name + "...");
        debug("declarative-readiness", "save requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = PluginDeclarativeReadinessReport.build(staged, isPluginDisabled(staged.id))
                        + "\n\nThis saved declarative-readiness report is an audit aid only. It does not open plugin UI, run plugin actions, execute shell commands, approve scripts, or trust code payloads.";
                File out = writePluginDeclarativeReadiness(staged, report);
                runUi(() -> {
                    append("[plugins] Saved declarative UI readiness: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved declarative UI readiness: " + out.getAbsolutePath());
                    debug("declarative-readiness", "saved id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("declarative-readiness", "save failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Save declarative readiness failed: " + safeMessage(t));
                });
            }
        });
    }

    private void showPluginRuntimePolicyReview(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = plugin;
            try {
                staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            } catch (Throwable t) {
                debug("runtime-policy", "review using loaded manifest for " + plugin.id + ": " + safeMessage(t));
            }
            String report = PluginRuntimeReviewReport.build(prefs(), staged, isPluginDisabled(plugin.id));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Plugin Runtime Review")
                    .setMessage(report)
                    .setPositiveButton("OK", null)
                    .show();
            debug("runtime-policy", "review shown for " + plugin.id);
        } catch (Throwable t) {
            setStatus("Runtime review failed: " + safeMessage(t));
            debug("runtime-policy", "review failed for " + plugin.id + ": " + safeMessage(t));
        }
    }
    private void showPluginTrustedCodeReview(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            String report = PluginRuntimeReviewReport.build(prefs(), staged, isPluginDisabled(staged.id));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Trusted Code Review")
                    .setMessage(report + "\n\nThis review does not run in-process code. Use Trust Reviewed Code Payload only after checking the exact payload fingerprint. Trusted-Dex dispatch still requires the runtime gate, exact-payload trust, and SHA-256 verification.")
                    .setPositiveButton("OK", null)
                    .show();
            debug("runtime-policy", "trusted code review shown for " + staged.id);
        } catch (Throwable t) {
            setStatus("Trusted code review failed: " + safeMessage(t));
            debug("runtime-policy", "trusted review failed for " + plugin.id + ": " + safeMessage(t));
        }
    }


    private void showPluginTrustedCodeReadiness(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            String report = PluginRuntimeReviewReport.buildTrustedCodeReadiness(
                    prefs(), staged, isPluginDisabled(staged.id), staged.homeDir);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Trusted Code Readiness")
                    .setMessage(report + "\n\nThis check does not load code, open a ClassLoader, run plugin APK code, or call plugin methods.")
                    .setPositiveButton("OK", null)
                    .show();
            debug("runtime-policy", "trusted code readiness shown for " + staged.id);
        } catch (Throwable t) {
            setStatus("Trusted code readiness check failed: " + safeMessage(t));
            debug("runtime-policy", "trusted readiness failed for " + plugin.id + ": " + safeMessage(t));
        }
    }


    private void confirmApproveTrustedCode(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            if (!PluginRuntimePolicy.pluginHasTrustedDexActions(staged)) {
                setStatus("No trusted-code actions are declared by " + staged.name + ".");
                return;
            }
            String trustProblem = trustedCodeTrustProblem(staged);
            if (!TextUtils.isEmpty(trustProblem)) {
                setStatus("Trusted code trust blocked: " + trustProblem);
                showPluginTrustedCodeReview(staged);
                return;
            }
            String report = PluginRuntimeReviewReport.build(prefs(), staged, isPluginDisabled(staged.id));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Trust Reviewed Code Payload")
                    .setMessage(report + "\n\nThis records trust for the exact reviewed Trusted-Dex payload fingerprint only. It does not run code by itself. Trusted-Dex dispatch still requires the runtime gate and explicit user tap. If the payload, manifest metadata, expected hash, class, method, capabilities, or plugin version changes, this trust record will no longer match.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Trust Payload", (dialog, which) -> {
                        PluginRuntimePolicy.setTrustedDexPluginApproved(prefs(), staged, true);
                        append("[plugins] Trusted reviewed code payload for " + staged.id
                                + " fingerprint=" + PluginRuntimePolicy.trustedDexApprovalFingerprint(staged)
                                + ". Trusted-Dex dispatch still requires the runtime gate and explicit user tap.\n");
                        setStatus("Trusted code payload recorded for " + staged.name + ".");
                        refreshPlugins();
                    })
                    .show();
        } catch (Throwable t) {
            setStatus("Trust reviewed code payload failed: " + safeMessage(t));
            debug("runtime-policy", "trusted approval failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void confirmClearTrustedCodeApproval(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Clear Trusted Code Trust")
                    .setMessage("Clear saved trusted-Dex payload trust for " + staged.name + "? This only removes the exact-payload trust record for this staged plugin. It does not disable the trusted-Dex runtime gate, delete plugin files, load code, run code, or change the plugin package.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear Trust", (dialog, which) -> clearTrustedCodeApproval(staged))
                    .show();
        } catch (Throwable t) {
            setStatus("Clear trusted code trust failed: " + safeMessage(t));
            debug("runtime-policy", "clear trusted approval confirm failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void clearTrustedCodeApproval(PluginManifest plugin) {
        if (plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            PluginRuntimePolicy.setTrustedDexPluginApproved(prefs(), staged, false);
            append("[plugins] Cleared trusted-code payload trust for " + staged.id + ".\n");
            setStatus("Trusted code trust cleared for " + staged.name + ".");
            refreshPlugins();
        } catch (Throwable t) {
            setStatus("Clear trusted code trust failed: " + safeMessage(t));
            debug("runtime-policy", "clear trusted approval failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void confirmApproveScriptPolicy(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            if (!PluginRuntimePolicy.pluginHasScriptActions(staged)) {
                setStatus("No script actions are declared by " + staged.name + ".");
                return;
            }
            String report = PluginRuntimeReviewReport.build(prefs(), staged, isPluginDisabled(staged.id));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Approve Script Actions")
                    .setMessage(report + "\n\nApprove only if you trust this staged plugin manifest and its declared command/script/UI files. Approval is tied to this manifest/script/UI-file fingerprint and will not automatically apply after script action, staged script file, or shell-capable UI file changes.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Approve", (dialog, which) -> {
                        PluginRuntimePolicy.setScriptPluginApproved(prefs(), staged, true);
                        append("[plugins] Approved controlled script actions for " + staged.id + ".\n");
                        setStatus("Script actions approved for " + staged.name + ".");
                        refreshPlugins();
                    })
                    .show();
        } catch (Throwable t) {
            setStatus("Approve script actions failed: " + safeMessage(t));
            debug("runtime-policy", "approve failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void confirmClearScriptApproval(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Clear Script Approval")
                    .setMessage("Clear saved script-action approval for " + staged.name + "? This only removes the manifest/script/UI-file fingerprint approval for this staged plugin. It does not disable the script runtime, delete plugin files, run commands, or change the plugin package.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear Approval", (dialog, which) -> clearScriptApproval(staged))
                    .show();
        } catch (Throwable t) {
            setStatus("Clear script approval failed: " + safeMessage(t));
            debug("runtime-policy", "clear approval confirm failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void clearScriptApproval(PluginManifest plugin) {
        if (plugin == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            PluginRuntimePolicy.setScriptPluginApproved(prefs(), staged, false);
            append("[plugins] Cleared script action approval for " + staged.id + ".\n");
            setStatus("Script action approval cleared for " + staged.name + ".");
            refreshPlugins();
        } catch (Throwable t) {
            setStatus("Clear script approval failed: " + safeMessage(t));
            debug("runtime-policy", "clear approval failed for " + plugin.id + ": " + safeMessage(t));
        }
    }

    private void savePluginRuntimePolicyReview(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Saving runtime review for " + plugin.name + "...");
        debug("runtime-policy", "save review requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = PluginRuntimeReviewReport.build(prefs(), staged, isPluginDisabled(staged.id));
                File out = writePluginRuntimeReview(staged, report);
                runUi(() -> {
                    append("[plugins] Saved runtime review: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved runtime review: " + out.getAbsolutePath());
                    debug("runtime-policy", "saved review id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("runtime-policy", "save review failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Save runtime review failed: " + safeMessage(t));
                });
            }
        });
    }


    private void savePluginTrustedCodeReview(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Saving trusted code review for " + plugin.name + "...");
        debug("runtime-policy", "save trusted review requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = PluginRuntimeReviewReport.build(prefs(), staged, isPluginDisabled(staged.id))
                        + "\n\nTrusted-Dex dispatch still requires the runtime gate and explicit user tap. This saved review is an audit aid only.";
                File out = writePluginRuntimeReview(staged, report);
                runUi(() -> {
                    append("[plugins] Saved trusted code review: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved trusted code review: " + out.getAbsolutePath());
                    debug("runtime-policy", "saved trusted review id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("runtime-policy", "save trusted review failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Save trusted code review failed: " + safeMessage(t));
                });
            }
        });
    }


    private void savePluginTrustedCodeReadiness(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Saving trusted code readiness for " + plugin.name + "...");
        debug("runtime-policy", "save trusted readiness requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String report = PluginRuntimeReviewReport.buildTrustedCodeReadiness(
                        prefs(), staged, isPluginDisabled(staged.id), staged.homeDir)
                        + "\n\nThis saved readiness report is an audit aid only. It does not load code or enable trusted-code execution.";
                File out = writePluginRuntimeReview(staged, report);
                runUi(() -> {
                    append("[plugins] Saved trusted code readiness: " + out.getAbsolutePath() + "\n");
                    setStatus("Saved trusted code readiness: " + out.getAbsolutePath());
                    debug("runtime-policy", "saved trusted readiness id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("runtime-policy", "save trusted readiness failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Save trusted code readiness failed: " + safeMessage(t));
                });
            }
        });
    }


    private void exportInstalledPlugin(PluginManifest plugin) {
        if (plugin == null || TextUtils.isEmpty(plugin.id)) return;
        setStatus("Exporting " + plugin.name + "...");
        debug("export", "export requested id=" + plugin.id);
        worker.execute(() -> {
            try {
                PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
                String requiredFileProblem = requiredPluginFileProblem(staged);
                if (!TextUtils.isEmpty(requiredFileProblem)) {
                    runUi(() -> {
                        debug("export", "blocked id=" + plugin.id + ": " + requiredFileProblem);
                        setStatus("Export blocked: " + requiredFileProblem);
                    });
                    return;
                }
                File out = exportPluginPackage(staged);
                runUi(() -> {
                    append("[plugins] Exported plugin package: " + out.getAbsolutePath() + "\n");
                    setStatus("Exported plugin package: " + out.getAbsolutePath());
                    debug("export", "exported id=" + staged.id + " path=" + out.getAbsolutePath());
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("export", "export failed id=" + plugin.id + ": " + safeMessage(t));
                    setStatus("Export failed: " + safeMessage(t));
                });
            }
        });
    }

    private void confirmUninstallPlugin(PluginManifest plugin) {
        Activity activity = activity();
        if (activity == null || plugin == null) return;
        new AlertDialog.Builder(activity)
                .setTitle("Uninstall Plugin")
                .setMessage("Remove the staged folder for " + plugin.name + "? The original .ptp/.zip package is not touched.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Uninstall", (dialog, which) -> uninstallPlugin(plugin.id))
                .show();
    }

    private void uninstallPlugin(String id) {
        if (TextUtils.isEmpty(id)) return;
        setStatus("Uninstalling " + id + "...");
        worker.execute(() -> {
            try {
                repository().uninstallPlugin(id);
                SharedPreferences prefs = prefs();
                if (prefs != null) {
                    prefs.edit()
                            .remove(PREF_DISABLED_PREFIX + id)
                            .remove(PREF_LOAD_STARTUP_PREFIX + id)
                            .remove(PREF_RUN_WINDOW_PREFIX + id)
                            .apply();
                }
                runUi(() -> {
                    debug("uninstall", "removed staged plugin id=" + id);
                    append("[plugins] Uninstalled staged plugin " + id + "\n");
                    setStatus("Uninstalled staged plugin " + id + ".");
                    refreshPlugins();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("uninstall", "uninstall failed id=" + id + ": " + safeMessage(t));
                    setStatus("Uninstall failed: " + safeMessage(t));
                });
            }
        });
    }

    private void runPluginAction(PluginManifest plugin, PluginAction action) {
        if (plugin == null || action == null) return;
        if (isPluginDisabled(plugin.id)) {
            setStatus(plugin.name + " is disabled.");
            return;
        }
        if (action.isScriptAction() && PluginRuntimePolicy.isScriptRunConfirmationRequired(prefs())) {
            confirmRunScriptAction(plugin, action);
            return;
        }
        if (action.isTrustedDexAction() && PluginRuntimePolicy.isTrustedDexRunConfirmationRequired(prefs())) {
            confirmRunTrustedCodeAction(plugin, action);
            return;
        }
        runPluginActionDispatch(plugin, action);
    }

    private void runPluginActionDispatch(PluginManifest plugin, PluginAction action) {
        if (plugin == null || action == null) return;
        final String actionKey = plugin.id + ":" + action.id;
        synchronized (activeActionKeys) {
            if (activeActionKeys.contains(actionKey)) {
                setStatus(plugin.name + " / " + action.title + " is already running.");
                debug("action", "duplicate active action blocked key=" + actionKey);
                return;
            }
            activeActionKeys.add(actionKey);
        }
        final boolean[] async = new boolean[] { false };
        debug("action", "run requested plugin=" + plugin.id + ", action=" + action.id + ", handler=" + action.handler);
        boolean handled = false;
        try {
            handled = PluginActionRegistry.execute(pluginRuntimeHost(actionKey, async), plugin, action);
            if (!handled) {
                debug("action", "unsupported handler=" + action.handler + " for plugin=" + plugin.id);
                setStatus("Unsupported plugin action: " + action.handler);
            }
        } finally {
            if (!async[0]) finishActiveAction(actionKey);
        }
    }


    private void confirmRunScriptAction(PluginManifest plugin, PluginAction action) {
        Activity activity = activity();
        if (activity == null || plugin == null || action == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            PluginAction stagedAction = findPluginAction(staged, action.id);
            if (stagedAction == null) stagedAction = action;
            String report = PluginRuntimeReviewReport.buildScriptActionRunReview(
                    prefs(),
                    staged,
                    stagedAction,
                    isPluginDisabled(staged.id),
                    staged.homeDir);
            PluginAction confirmedAction = stagedAction;
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Run Script Action")
                    .setMessage(report + "\n\nRun this shell/script action now?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Run", (dialog, which) -> {
                        if (isPluginDisabled(staged.id)) {
                            setStatus(staged.name + " is disabled.");
                            return;
                        }
                        append("[plugins] Per-run script confirmation accepted for " + staged.id + "/" + confirmedAction.id + ".\n");
                        runPluginActionDispatch(staged, confirmedAction);
                    })
                    .show();
        } catch (Throwable t) {
            setStatus("Script run confirmation failed: " + safeMessage(t));
            debug("runtime-policy", "script run confirmation failed for " + plugin.id + "/" + action.id + ": " + safeMessage(t));
        }
    }

    private void confirmRunTrustedCodeAction(PluginManifest plugin, PluginAction action) {
        Activity activity = activity();
        if (activity == null || plugin == null || action == null) return;
        try {
            PluginManifest staged = PluginManifest.fromDirectory(new File(repository().getPluginRoot(), plugin.id));
            PluginAction stagedAction = findPluginAction(staged, action.id);
            if (stagedAction == null) stagedAction = action;
            String report = PluginRuntimeReviewReport.buildTrustedCodeActionRunReview(
                    prefs(),
                    staged,
                    stagedAction,
                    isPluginDisabled(staged.id),
                    staged.homeDir);
            PluginAction confirmedAction = stagedAction;
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Run Trusted Code")
                    .setMessage(report + "\n\nRun this trusted-code action now?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Run", (dialog, which) -> {
                        if (isPluginDisabled(staged.id)) {
                            setStatus(staged.name + " is disabled.");
                            return;
                        }
                        append("[plugins] Per-run trusted-code confirmation accepted for " + staged.id + "/" + confirmedAction.id + ".\n");
                        runPluginActionDispatch(staged, confirmedAction);
                    })
                    .show();
        } catch (Throwable t) {
            setStatus("Trusted-code run confirmation failed: " + safeMessage(t));
            debug("runtime-policy", "trusted-code run confirmation failed for " + plugin.id + "/" + action.id + ": " + safeMessage(t));
        }
    }

    private PluginAction findPluginAction(PluginManifest plugin, String actionId) {
        if (plugin == null || plugin.actions == null || TextUtils.isEmpty(actionId)) return null;
        for (PluginAction action : plugin.actions) {
            if (action != null && actionId.equals(action.id)) return action;
        }
        return null;
    }

    private PluginActionRegistry.Host pluginRuntimeHost(final String actionKey, final boolean[] async) {
        return new PluginActionRegistry.Host() {
            @Override
            public Activity getActivity() {
                return activity();
            }

            @Override
            public ActivityMainBinding getBinding() {
                return host == null ? null : host.getBinding();
            }

            @Override
            public SharedPreferences getSharedPreferences() {
                return prefs();
            }

            @Override
            public void appendOutput(String message) {
                append(message);
            }

            @Override
            public boolean shouldRunPluginInPanel(String pluginId) {
                return isPluginRunInWindow(pluginId);
            }

            @Override
            public boolean showPluginTextPanel(String panelKey, String title, String subtitle, String text, String syntax, String windowStyle, String windowFit) {
                return host != null && host.showPluginTextPanel(panelKey, title, subtitle, text, syntax, windowStyle, windowFit);
            }

            @Override
            public boolean openPermsTestTool(String pluginId, String toolId, boolean requestPanel, String windowStyle, String windowFit) {
                return host != null && host.openPermsTestTool(pluginId, toolId, requestPanel, windowStyle, windowFit);
            }

            @Override
            public void runShellCommandCapture(String command, PluginActionRegistry.ShellCallback callback) {
                if (async != null) async[0] = true;
                if (host == null) {
                    finishActiveAction(actionKey);
                    return;
                }
                host.runShellCommandCapture(command, callback == null
                        ? (code, out, err) -> finishActiveAction(actionKey)
                        : (code, out, err) -> {
                            try { callback.onComplete(code, out, err); }
                            finally { finishActiveAction(actionKey); }
                        });
            }
        };
    }

    private void finishActiveAction(String actionKey) {
        if (TextUtils.isEmpty(actionKey)) return;
        synchronized (activeActionKeys) {
            activeActionKeys.remove(actionKey);
        }
    }

    private boolean isPluginDisabled(String id) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(PREF_DISABLED_PREFIX + id, false);
    }

    private void setPluginDisabled(String id, boolean disabled) {
        if (TextUtils.isEmpty(id)) return;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(PREF_DISABLED_PREFIX + id, disabled).apply();
        debug("enable", (disabled ? "disabled " : "enabled ") + id);
        append("[plugins] " + (disabled ? "Disabled " : "Enabled ") + id + "\n");
        refreshPlugins();
    }

    private boolean isPluginLoadAtStartup(String id) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(PREF_LOAD_STARTUP_PREFIX + id, false);
    }

    private void setPluginLoadAtStartup(String id, boolean enabled) {
        if (TextUtils.isEmpty(id)) return;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(PREF_LOAD_STARTUP_PREFIX + id, enabled).apply();
        debug("options", "reserved_startup_flag_inactive=" + enabled + " id=" + id);
        append("[plugins] Reserved Startup Flag " + (enabled ? "saved on for " : "saved off for ") + id + " (disabled/inactive; future feature, no runtime effect).\n");
        setStatus("Reserved Startup Flag " + (enabled ? "saved on" : "saved off") + " for " + id + "; disabled/inactive, future feature, no runtime effect.");
        refreshPlugins();
    }

    private boolean isPluginRunInWindow(String id) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(PREF_RUN_WINDOW_PREFIX + id, false);
    }

    private void setPluginRunInWindow(String id, boolean enabled) {
        if (TextUtils.isEmpty(id)) return;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(PREF_RUN_WINDOW_PREFIX + id, enabled).apply();
        debug("options", "run_in_window=" + enabled + " id=" + id);
        append("[plugins] Large Window Override " + (enabled ? "enabled for " : "disabled for ") + id + "\n");
        setStatus("Large Window Override " + (enabled ? "enabled" : "disabled") + " for " + id + ". Off uses the plugin.json windowStyle/windowFit defaults; on requests the larger movable window on phone/tablet and the VR panel path when VR mode is active.");
        refreshPlugins();
    }

    private void cleanInvalidPlugins() {
        setStatus("Scanning staged plugins for invalid folders...");
        debug("clean", "invalid staged plugin cleanup requested");
        worker.execute(() -> {
            try {
                List<String> removed = repository().cleanInvalidPluginDirs();
                runUi(() -> {
                    int count = removed == null ? 0 : removed.size();
                    String names = count == 0 ? "" : " (" + TextUtils.join(", ", removed) + ")";
                    append("[plugins] Clean Invalid removed " + count + " staged folder(s)" + names + "\n");
                    setStatus(count == 0 ? "No invalid staged plugin folders found." : "Removed " + count + " invalid staged plugin folder(s)" + names + ".");
                    refreshPlugins();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("clean", "cleanup failed: " + safeMessage(t));
                    setStatus("Clean Invalid failed: " + safeMessage(t));
                });
            }
        });
    }

    private void setAllPluginsDisabled(boolean disabled) {
        setStatus((disabled ? "Disabling" : "Enabling") + " all staged plugins...");
        debug("enable", (disabled ? "disable" : "enable") + " all requested");
        worker.execute(() -> {
            try {
                List<PluginManifest> plugins = repository().loadPlugins();
                SharedPreferences prefs = prefs();
                int count = 0;
                if (prefs != null && plugins != null) {
                    SharedPreferences.Editor editor = prefs.edit();
                    for (PluginManifest plugin : plugins) {
                        if (plugin == null || TextUtils.isEmpty(plugin.id)) continue;
                        editor.putBoolean(PREF_DISABLED_PREFIX + plugin.id, disabled);
                        count++;
                    }
                    editor.apply();
                }
                final int changed = count;
                runUi(() -> {
                    debug("enable", (disabled ? "disabled" : "enabled") + " all staged plugins count=" + changed);
                    append("[plugins] " + (disabled ? "Disabled " : "Enabled ") + changed + " staged plugin(s).\n");
                    setStatus((disabled ? "Disabled " : "Enabled ") + changed + " staged plugin(s).");
                    refreshPlugins();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    debug("enable", "bulk update failed: " + safeMessage(t));
                    setStatus("Bulk plugin update failed: " + safeMessage(t));
                });
            }
        });
    }

    private void bindPluginEditorActions(TabPluginsBinding tab) {
        if (tab == null) return;
        tab.btnPluginEditorNew.setOnClickListener(v -> runEditorAction(EditorAction.NEW, this::newPluginTemplate));
        tab.btnPluginEditorOpenPackage.setOnClickListener(v -> runEditorAction(EditorAction.OPEN_PACKAGE, this::launchEditorPluginPicker));
        tab.btnPluginEditorChooseIcon.setOnClickListener(v -> runEditorAction(EditorAction.CHOOSE_ICON, this::launchEditorIconPicker));
        tab.btnPluginEditorChooseScript.setOnClickListener(v -> runEditorAction(EditorAction.CHOOSE_SCRIPT, this::launchEditorScriptPicker));
        tab.btnPluginEditorChooseAsset.setOnClickListener(v -> runEditorAction(EditorAction.CHOOSE_ASSET, this::launchEditorAssetPicker));
        tab.btnPluginEditorAssetsLoad.setOnClickListener(v -> runEditorAction(EditorAction.ASSETS_LOAD, this::loadEditorAssetsFromStaged));
        tab.btnPluginEditorAssetAdd.setOnClickListener(v -> runEditorAction(EditorAction.ASSET_ADD, this::queueEditorAsset));
        tab.btnPluginEditorAssetRemove.setOnClickListener(v -> runEditorAction(EditorAction.ASSET_REMOVE, this::removeSelectedEditorAsset));
        tab.btnPluginEditorActionsLoadJson.setOnClickListener(v -> runEditorAction(EditorAction.ACTIONS_LOAD_JSON, this::loadEditorActionsFromJson));
        tab.btnPluginEditorTrustedHashTarget.setOnClickListener(v -> runEditorAction(EditorAction.TRUSTED_HASH_TARGET, this::hashEditorTrustedTarget));
        tab.btnPluginEditorActionAdd.setOnClickListener(v -> runEditorAction(EditorAction.ACTION_ADD, this::addEditorAction));
        tab.btnPluginEditorActionUpdate.setOnClickListener(v -> runEditorAction(EditorAction.ACTION_UPDATE, this::updateSelectedEditorAction));
        tab.btnPluginEditorActionRemove.setOnClickListener(v -> runEditorAction(EditorAction.ACTION_REMOVE, this::removeSelectedEditorAction));
        tab.btnPluginEditorActionUp.setOnClickListener(v -> runEditorAction(EditorAction.ACTION_UP, () -> moveSelectedEditorAction(-1)));
        tab.btnPluginEditorActionDown.setOnClickListener(v -> runEditorAction(EditorAction.ACTION_DOWN, () -> moveSelectedEditorAction(1)));
        tab.btnPluginEditorUiLoadJson.setOnClickListener(v -> runEditorAction(EditorAction.UI_LOAD_JSON, this::loadEditorUiControlsFromJson));
        tab.btnPluginEditorUiApplyApiPreset.setOnClickListener(v -> applyEditorUiActionApiPreset());
        tab.btnPluginEditorUiClearActionMapping.setOnClickListener(v -> clearEditorUiActionMapping());
        tab.btnPluginEditorUiAdd.setOnClickListener(v -> runEditorAction(EditorAction.UI_ADD, this::addEditorUiControl));
        tab.btnPluginEditorUiUpdate.setOnClickListener(v -> runEditorAction(EditorAction.UI_UPDATE, this::updateSelectedEditorUiControl));
        tab.btnPluginEditorUiRemove.setOnClickListener(v -> runEditorAction(EditorAction.UI_REMOVE, this::removeSelectedEditorUiControl));
        tab.btnPluginEditorUiUp.setOnClickListener(v -> runEditorAction(EditorAction.UI_UP, () -> moveSelectedEditorUiControl(-1)));
        tab.btnPluginEditorUiDown.setOnClickListener(v -> runEditorAction(EditorAction.UI_DOWN, () -> moveSelectedEditorUiControl(1)));
        tab.btnPluginEditorUiNestedLoad.setOnClickListener(v -> runEditorAction(EditorAction.UI_NESTED_LOAD, this::loadEditorUiNestedItemsFromSelected));
        tab.btnPluginEditorUiNestedAdd.setOnClickListener(v -> runEditorAction(EditorAction.UI_NESTED_ADD, this::addEditorUiNestedItem));
        tab.btnPluginEditorUiNestedUpdate.setOnClickListener(v -> runEditorAction(EditorAction.UI_NESTED_UPDATE, this::updateSelectedEditorUiNestedItem));
        tab.btnPluginEditorUiNestedRemove.setOnClickListener(v -> runEditorAction(EditorAction.UI_NESTED_REMOVE, this::removeSelectedEditorUiNestedItem));
        tab.btnPluginEditorUiNestedUp.setOnClickListener(v -> runEditorAction(EditorAction.UI_NESTED_UP, () -> moveSelectedEditorUiNestedItem(-1)));
        tab.btnPluginEditorUiNestedDown.setOnClickListener(v -> runEditorAction(EditorAction.UI_NESTED_DOWN, () -> moveSelectedEditorUiNestedItem(1)));
        tab.btnPluginEditorBuildJson.setOnClickListener(v -> runEditorAction(EditorAction.BUILD_JSON, this::buildEditorJsonFromFields));
        tab.btnPluginEditorValidate.setOnClickListener(v -> runEditorAction(EditorAction.VALIDATE, this::validateEditorManifest));
        tab.btnPluginEditorSave.setOnClickListener(v -> runEditorAction(EditorAction.SAVE, this::saveEditorManifest));
        tab.btnPluginEditorBuildUi.setOnClickListener(v -> runEditorAction(EditorAction.BUILD_UI, this::buildEditorUiJsonFromFields));
        tab.btnPluginEditorPreviewUi.setOnClickListener(v -> runEditorAction(EditorAction.PREVIEW_UI, this::previewEditorUi));
        tab.btnPluginEditorSaveUi.setOnClickListener(v -> runEditorAction(EditorAction.SAVE_UI, () -> saveEditorUiJson(true)));
        tab.btnPluginEditorPackage.setOnClickListener(v -> runEditorAction(EditorAction.PACKAGE, this::packageEditorPlugin));
    }

    private void runEditorAction(EditorAction action, Runnable runnable) {
        String problem = editorPrerequisiteProblem(action);
        if (!TextUtils.isEmpty(problem)) {
            setEditorStatus(problem);
            debug("editor", "action blocked " + action + ": " + problem);
            updatePluginEditorActionState();
            return;
        }
        try {
            if (runnable != null) runnable.run();
        } finally {
            updatePluginEditorActionState();
        }
    }

    private void bindPluginEditorStateWatchers(TabPluginsBinding tab) {
        if (tab == null) return;
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (editorActionFieldSyncing || editorUiFieldSyncing) return;
                View root = tab.getRoot();
                if (root == null) return;
                root.removeCallbacks(editorActionStateRefresh);
                root.postDelayed(editorActionStateRefresh, 120L);
            }
        };
        addEditorWatcher(tab.edtPluginEditorId, watcher);
        addEditorWatcher(tab.edtPluginEditorName, watcher);
        addEditorWatcher(tab.edtPluginEditorVersion, watcher);
        addEditorWatcher(tab.edtPluginEditorIcon, watcher);
        addEditorWatcher(tab.edtPluginEditorDescription, watcher);
        addEditorWatcher(tab.ddPluginEditorRuntime, watcher);
        addEditorWatcher(tab.ddPluginEditorWindowStyle, watcher);
        addEditorWatcher(tab.ddPluginEditorWindowFit, watcher);
        addEditorWatcher(tab.edtPluginEditorActionId, watcher);
        addEditorWatcher(tab.edtPluginEditorActionTitle, watcher);
        addEditorWatcher(tab.edtPluginEditorHandler, watcher);
        addEditorWatcher(tab.edtPluginEditorActionRequires, watcher);
        addEditorWatcher(tab.edtPluginEditorTrustedSha256, watcher);
        addEditorWatcher(tab.edtPluginEditorTrustedClassName, watcher);
        addEditorWatcher(tab.edtPluginEditorTrustedMethodName, watcher);
        addEditorWatcher(tab.ddPluginEditorActionType, watcher);
        addEditorWatcher(tab.ddPluginEditorActionPresentation, watcher);
        addEditorWatcher(tab.ddPluginEditorActionSyntax, watcher);
        addEditorWatcher(tab.ddPluginEditorActionWindowStyle, watcher);
        addEditorWatcher(tab.ddPluginEditorActionWindowFit, watcher);
        addEditorWatcher(tab.edtPluginEditorJson, watcher);
        addEditorWatcher(tab.edtPluginEditorUiJson, watcher);
        addEditorWatcher(tab.edtPluginEditorAssetPath, watcher);
        TextWatcher actionFieldWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (editorActionFieldSyncing) return;
                editorActionListDirty = true;
                updatePluginEditorActionState();
            }
        };
        addEditorWatcher(tab.edtPluginEditorActionId, actionFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorActionTitle, actionFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorHandler, actionFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorActionRequires, actionFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorTrustedSha256, actionFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorTrustedClassName, actionFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorTrustedMethodName, actionFieldWatcher);
        addEditorWatcher(tab.ddPluginEditorActionType, actionFieldWatcher);
        addEditorWatcher(tab.ddPluginEditorActionPresentation, actionFieldWatcher);
        addEditorWatcher(tab.ddPluginEditorActionSyntax, actionFieldWatcher);
        addEditorWatcher(tab.ddPluginEditorActionWindowStyle, actionFieldWatcher);
        addEditorWatcher(tab.ddPluginEditorActionWindowFit, actionFieldWatcher);
        addEditorWatcher(tab.ddPluginEditorRuntime, actionFieldWatcher);
        TextWatcher uiFieldWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (editorUiFieldSyncing) return;
                editorUiListDirty = true;
                updatePluginEditorActionState();
            }
        };
        addEditorWatcher(tab.ddPluginEditorUiType, uiFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiId, uiFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiLabel, uiFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiValue, uiFieldWatcher);
        addEditorWatcher(tab.ddPluginEditorUiActionType, uiFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiActionData, uiFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiActionOutput, uiFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiActionOptionsJson, uiFieldWatcher);
        TextWatcher nestedFieldWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (editorUiNestedFieldSyncing) return;
                editorUiNestedFieldsDirty = true;
                updatePluginEditorActionState();
            }
        };
        addEditorWatcher(tab.ddPluginEditorUiNestedType, nestedFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiNestedId, nestedFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiNestedLabel, nestedFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiNestedValue, nestedFieldWatcher);
        addEditorWatcher(tab.ddPluginEditorUiNestedActionType, nestedFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiNestedActionData, nestedFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiNestedActionOutput, nestedFieldWatcher);
        addEditorWatcher(tab.edtPluginEditorUiNestedActionOptionsJson, nestedFieldWatcher);
    }

    private void addEditorWatcher(TextView view, TextWatcher watcher) {
        if (view instanceof EditText && watcher != null) {
            try { ((EditText) view).addTextChangedListener(watcher); } catch (Throwable ignored) {}
        }
    }

    private void updatePluginEditorActionState() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        updateEditorUiActionFieldAvailability(tab);
        updateEditorUiNestedFieldAvailability(tab);
        setEditorButtonReady(tab.btnPluginEditorNew, editorPrerequisiteProblem(EditorAction.NEW));
        setEditorButtonReady(tab.btnPluginEditorOpenPackage, editorPrerequisiteProblem(EditorAction.OPEN_PACKAGE));
        setEditorButtonReady(tab.btnPluginEditorChooseIcon, editorPrerequisiteProblem(EditorAction.CHOOSE_ICON));
        setEditorButtonReady(tab.btnPluginEditorChooseScript, editorPrerequisiteProblem(EditorAction.CHOOSE_SCRIPT));
        setEditorButtonReady(tab.btnPluginEditorChooseAsset, editorPrerequisiteProblem(EditorAction.CHOOSE_ASSET));
        setEditorButtonReady(tab.btnPluginEditorAssetsLoad, editorPrerequisiteProblem(EditorAction.ASSETS_LOAD));
        setEditorButtonReady(tab.btnPluginEditorAssetAdd, editorPrerequisiteProblem(EditorAction.ASSET_ADD));
        setEditorButtonReady(tab.btnPluginEditorAssetRemove, editorPrerequisiteProblem(EditorAction.ASSET_REMOVE));
        setEditorButtonReady(tab.btnPluginEditorActionsLoadJson, editorPrerequisiteProblem(EditorAction.ACTIONS_LOAD_JSON));
        setEditorButtonReady(tab.btnPluginEditorTrustedHashTarget, editorPrerequisiteProblem(EditorAction.TRUSTED_HASH_TARGET));
        setEditorButtonReady(tab.btnPluginEditorActionAdd, editorPrerequisiteProblem(EditorAction.ACTION_ADD));
        setEditorButtonReady(tab.btnPluginEditorActionUpdate, editorPrerequisiteProblem(EditorAction.ACTION_UPDATE));
        setEditorButtonReady(tab.btnPluginEditorActionRemove, editorPrerequisiteProblem(EditorAction.ACTION_REMOVE));
        setEditorButtonReady(tab.btnPluginEditorActionUp, editorPrerequisiteProblem(EditorAction.ACTION_UP));
        setEditorButtonReady(tab.btnPluginEditorActionDown, editorPrerequisiteProblem(EditorAction.ACTION_DOWN));
        setEditorButtonReady(tab.btnPluginEditorUiLoadJson, editorPrerequisiteProblem(EditorAction.UI_LOAD_JSON));
        setEditorButtonReady(tab.btnPluginEditorUiAdd, editorPrerequisiteProblem(EditorAction.UI_ADD));
        setEditorButtonReady(tab.btnPluginEditorUiUpdate, editorPrerequisiteProblem(EditorAction.UI_UPDATE));
        setEditorButtonReady(tab.btnPluginEditorUiRemove, editorPrerequisiteProblem(EditorAction.UI_REMOVE));
        setEditorButtonReady(tab.btnPluginEditorUiUp, editorPrerequisiteProblem(EditorAction.UI_UP));
        setEditorButtonReady(tab.btnPluginEditorUiDown, editorPrerequisiteProblem(EditorAction.UI_DOWN));
        setEditorButtonReady(tab.btnPluginEditorUiNestedLoad, editorPrerequisiteProblem(EditorAction.UI_NESTED_LOAD));
        setEditorButtonReady(tab.btnPluginEditorUiNestedAdd, editorPrerequisiteProblem(EditorAction.UI_NESTED_ADD));
        setEditorButtonReady(tab.btnPluginEditorUiNestedUpdate, editorPrerequisiteProblem(EditorAction.UI_NESTED_UPDATE));
        setEditorButtonReady(tab.btnPluginEditorUiNestedRemove, editorPrerequisiteProblem(EditorAction.UI_NESTED_REMOVE));
        setEditorButtonReady(tab.btnPluginEditorUiNestedUp, editorPrerequisiteProblem(EditorAction.UI_NESTED_UP));
        setEditorButtonReady(tab.btnPluginEditorUiNestedDown, editorPrerequisiteProblem(EditorAction.UI_NESTED_DOWN));
        setEditorButtonReady(tab.btnPluginEditorBuildJson, editorPrerequisiteProblem(EditorAction.BUILD_JSON));
        setEditorButtonReady(tab.btnPluginEditorValidate, editorPrerequisiteProblem(EditorAction.VALIDATE));
        setEditorButtonReady(tab.btnPluginEditorSave, editorPrerequisiteProblem(EditorAction.SAVE));
        setEditorButtonReady(tab.btnPluginEditorBuildUi, editorPrerequisiteProblem(EditorAction.BUILD_UI));
        setEditorButtonReady(tab.btnPluginEditorPreviewUi, editorPrerequisiteProblem(EditorAction.PREVIEW_UI));
        setEditorButtonReady(tab.btnPluginEditorSaveUi, editorPrerequisiteProblem(EditorAction.SAVE_UI));
        setEditorButtonReady(tab.btnPluginEditorPackage, editorPrerequisiteProblem(EditorAction.PACKAGE));
    }

    private void setEditorButtonReady(MaterialButton button, String problem) {
        if (button == null) return;
        boolean ready = TextUtils.isEmpty(problem);
        try { button.setEnabled(ready); } catch (Throwable ignored) {}
        try { button.setAlpha(ready ? 1f : 0.5f); } catch (Throwable ignored) {}
        try { button.setContentDescription(ready ? button.getText() : button.getText() + ": " + problem); } catch (Throwable ignored) {}
    }

    private String editorPrerequisiteProblem(EditorAction action) {
        TabPluginsBinding tab = tab();
        if (tab == null) return "Plugin Editor is not ready.";
        if (action == EditorAction.NEW) return "";
        if (editorUiNestedFieldsDirty && (action == EditorAction.UI_LOAD_JSON
                || action == EditorAction.UI_ADD || action == EditorAction.UI_UPDATE
                || action == EditorAction.UI_REMOVE || action == EditorAction.UI_UP || action == EditorAction.UI_DOWN
                || action == EditorAction.UI_NESTED_REMOVE || action == EditorAction.UI_NESTED_UP || action == EditorAction.UI_NESTED_DOWN
                || action == EditorAction.BUILD_UI || action == EditorAction.PREVIEW_UI || action == EditorAction.VALIDATE
                || action == EditorAction.SAVE || action == EditorAction.SAVE_UI || action == EditorAction.PACKAGE)) {
            return "Tap Add or Update in Selected Container Contents, or Reload Contents to discard the pending nested-item fields.";
        }
        if (action == EditorAction.OPEN_PACKAGE || action == EditorAction.CHOOSE_ICON) {
            return pluginPickerLauncher == null ? "Plugin file picker is not ready yet." : "";
        }
        if (action == EditorAction.CHOOSE_SCRIPT) {
            if (pluginPickerLauncher == null) return "Plugin file picker is not ready yet.";
            String type = textOr(tab.ddPluginEditorActionType, actionTypeForRuntime(textOr(tab.ddPluginEditorRuntime, "declarative"))).trim();
            return isScriptActionType(type) ? "" : "Choose a shell or script action type before selecting a script file.";
        }
        if (action == EditorAction.CHOOSE_ASSET) {
            return pluginPickerLauncher == null ? "Plugin file picker is not ready yet." : "";
        }
        if (action == EditorAction.ASSETS_LOAD) {
            String id = textOf(tab.edtPluginEditorId).trim();
            if (!PluginManifest.isSafeId(id)) return "Enter or load a valid Plugin ID before loading staged assets.";
            return new File(repository().getPluginRoot(), id).isDirectory() ? "" : "Save or load the staged plugin before loading its assets.";
        }
        if (action == EditorAction.ASSET_ADD) {
            if (pendingEditorAssetPickerUri == null) return "Choose an asset file before adding or replacing it.";
            return managedAssetPathProblem(textOf(tab.edtPluginEditorAssetPath), textOf(tab.edtPluginEditorJson));
        }
        if (action == EditorAction.ASSET_REMOVE) {
            return editorAssetStore.hasSelection() ? "" : "Select a managed asset before removing it.";
        }
        if (action == EditorAction.ACTIONS_LOAD_JSON) return editorJsonActionsProblem(tab);
        if (action == EditorAction.TRUSTED_HASH_TARGET) return editorTrustedHashTargetProblem(tab);
        if (action == EditorAction.ACTION_ADD) return editorActionFieldProblem(tab, -1);
        if (action == EditorAction.ACTION_UPDATE) {
            if (!editorActionStore.hasSelection()) return "Select an action before updating it.";
            return editorActionFieldProblem(tab, editorActionStore.selectedIndex());
        }
        if (action == EditorAction.ACTION_REMOVE) {
            return editorActionStore.hasSelection() ? "" : "Select an action before removing it.";
        }
        if (action == EditorAction.ACTION_UP) {
            return editorActionStore.hasSelection() && editorActionStore.selectedIndex() > 0
                    ? "" : "Select an action that can move up.";
        }
        if (action == EditorAction.ACTION_DOWN) {
            return editorActionStore.hasSelection() && editorActionStore.selectedIndex() < editorActionStore.size() - 1
                    ? "" : "Select an action that can move down.";
        }
        if (action == EditorAction.UI_LOAD_JSON) return editorUiControlsJsonProblem(tab);
        if (action == EditorAction.UI_ADD) return editorUiControlFieldProblem(tab, -1);
        if (action == EditorAction.UI_UPDATE) {
            if (!editorUiControlStore.hasSelection()) return "Select a UI control before updating it.";
            return editorUiControlFieldProblem(tab, editorUiControlStore.selectedIndex());
        }
        if (action == EditorAction.UI_REMOVE) {
            return editorUiControlStore.hasSelection() ? "" : "Select a UI control before removing it.";
        }
        if (action == EditorAction.UI_UP) {
            return editorUiControlStore.hasSelection() && editorUiControlStore.selectedIndex() > 0
                    ? "" : "Select a UI control that can move up.";
        }
        if (action == EditorAction.UI_DOWN) {
            return editorUiControlStore.hasSelection() && editorUiControlStore.selectedIndex() < editorUiControlStore.size() - 1
                    ? "" : "Select a UI control that can move down.";
        }
        if (action == EditorAction.UI_NESTED_LOAD) return selectedUiContainerProblem(tab);
        if (action == EditorAction.UI_NESTED_ADD) {
            String problem = selectedUiContainerProblem(tab);
            return TextUtils.isEmpty(problem) ? editorUiNestedItemFieldProblem(tab, -1) : problem;
        }
        if (action == EditorAction.UI_NESTED_UPDATE) {
            String problem = selectedUiContainerProblem(tab);
            if (!TextUtils.isEmpty(problem)) return problem;
            if (!editorUiNestedStore.hasSelection()) return "Select a nested item before updating it.";
            return editorUiNestedItemFieldProblem(tab, editorUiNestedStore.selectedIndex());
        }
        if (action == EditorAction.UI_NESTED_REMOVE) {
            String problem = selectedUiContainerProblem(tab);
            if (!TextUtils.isEmpty(problem)) return problem;
            return editorUiNestedStore.hasSelection() ? "" : "Select a nested item before removing it.";
        }
        if (action == EditorAction.UI_NESTED_UP) {
            String problem = selectedUiContainerProblem(tab);
            if (!TextUtils.isEmpty(problem)) return problem;
            return editorUiNestedStore.hasSelection() && editorUiNestedStore.selectedIndex() > 0
                    ? "" : "Select a nested item that can move up.";
        }
        if (action == EditorAction.UI_NESTED_DOWN) {
            String problem = selectedUiContainerProblem(tab);
            if (!TextUtils.isEmpty(problem)) return problem;
            return editorUiNestedStore.hasSelection() && editorUiNestedStore.selectedIndex() < editorUiNestedStore.size() - 1
                    ? "" : "Select a nested item that can move down.";
        }
        if (action == EditorAction.BUILD_JSON) {
            String baseProblem = editorBaseFieldProblem(tab);
            if (!TextUtils.isEmpty(baseProblem)) return baseProblem;
            if (editorActionStore.isEmpty()) return "Add or load at least one plugin action before building plugin.json.";
            return editorActionStore.hasSelection()
                    ? editorActionFieldProblem(tab, editorActionStore.selectedIndex()) : "";
        }
        if (action == EditorAction.BUILD_UI) {
            if (!"declarative".equals(textOr(tab.ddPluginEditorRuntime, "declarative").trim())) {
                return "Build UI is only available for declarative plugins.";
            }
            if (TextUtils.isEmpty(textOf(tab.edtPluginEditorName).trim())) return "Enter a plugin name before building ui.json.";
            return editorUiControlStore.hasSelection() ? editorUiControlFieldProblem(tab, editorUiControlStore.selectedIndex()) : "";
        }
        if (action == EditorAction.PREVIEW_UI) {
            if (!"declarative".equals(textOr(tab.ddPluginEditorRuntime, "declarative").trim())) {
                return "Preview UI is only available for declarative plugins.";
            }
            if (editorUiListDirty) return "Tap Build UI to apply the structured declarative UI changes before previewing.";
            String uiJson = textOf(tab.edtPluginEditorUiJson).trim();
            if (TextUtils.isEmpty(uiJson)) return "Build UI or enter ui.json before previewing.";
            String uiProblem = editorUiJsonProblem(tab);
            return TextUtils.isEmpty(uiProblem) ? editorUiRuntimeProblem(uiJson) : uiProblem;
        }
        if (editorActionListDirty && (action == EditorAction.VALIDATE || action == EditorAction.SAVE
                || action == EditorAction.SAVE_UI || action == EditorAction.PACKAGE)) {
            return "Tap Build JSON to apply the structured action changes first.";
        }
        if (editorUiListDirty && "declarative".equals(textOr(tab.ddPluginEditorRuntime, "declarative").trim())
                && (action == EditorAction.PREVIEW_UI || action == EditorAction.VALIDATE || action == EditorAction.SAVE
                || action == EditorAction.SAVE_UI || action == EditorAction.PACKAGE)) {
            return "Tap Build UI to apply the structured declarative UI changes first.";
        }
        String json = textOf(tab.edtPluginEditorJson).trim();
        if (TextUtils.isEmpty(json)) return "Build or enter plugin.json first.";
        if (action == EditorAction.VALIDATE) return "";
        ValidationResult validation = validatePluginJson(json, action == EditorAction.PACKAGE);
        if (!validation.ok) return validation.message;
        if (action == EditorAction.SAVE || action == EditorAction.SAVE_UI) {
            if (action == EditorAction.SAVE_UI && !"declarative".equals(validation.runtime)) {
                return "Save UI is only available for declarative plugins.";
            }
            if ("declarative".equals(validation.runtime)) {
                String uiProblem = editorUiJsonProblem(tab);
                if (!TextUtils.isEmpty(uiProblem)) return uiProblem;
                String rawUi = textOf(tab.edtPluginEditorUiJson).trim();
                if (!TextUtils.isEmpty(rawUi)) return editorUiRuntimeProblem(rawUi);
            }
        }
        if (action == EditorAction.PACKAGE) return editorPackagePrerequisiteProblem(validation, tab);
        return "";
    }

    private String editorBaseFieldProblem(TabPluginsBinding tab) {
        String id = textOf(tab == null ? null : tab.edtPluginEditorId).trim();
        if (!PluginManifest.isSafeId(id) || isPlaceholderPluginId(id)) return "Enter a valid non-placeholder Plugin ID before building plugin.json.";
        if (TextUtils.isEmpty(textOf(tab.edtPluginEditorName).trim())) return "Enter a plugin name before building plugin.json.";
        if (TextUtils.isEmpty(textOf(tab.edtPluginEditorVersion).trim())) return "Enter a plugin version before building plugin.json.";
        if (!isSupportedRuntime(textOr(tab.ddPluginEditorRuntime, "declarative").trim())) return "Select a supported plugin runtime.";
        return "";
    }

    private String editorActionFieldProblem(TabPluginsBinding tab, int ignoreIndex) {
        String title = textOf(tab == null ? null : tab.edtPluginEditorActionTitle).trim();
        if (TextUtils.isEmpty(title)) return "Choose or enter an action title.";
        String actionId = editorActionIdValue(tab);
        if (!PluginManifest.isSafeId(actionId)) return "Enter a safe Action ID using letters, numbers, dots, underscores, or hyphens.";
        if (editorActionIdExists(actionId, ignoreIndex)) return "Action ID already exists: " + actionId;
        String type = textOr(tab.ddPluginEditorActionType, actionTypeForRuntime(textOr(tab.ddPluginEditorRuntime, "declarative"))).trim();
        if (!isSupportedPluginActionType(type)) return "Select a supported action type.";
        String presentation = textOr(tab.ddPluginEditorActionPresentation, "default").trim();
        if (!isSupportedPluginActionPresentation(presentation)) return "Select a supported action presentation.";
        String syntax = textOr(tab.ddPluginEditorActionSyntax, "default").trim();
        if (!isSupportedPluginActionSyntax(syntax)) return "Select a supported output syntax.";
        String windowStyle = textOr(tab.ddPluginEditorActionWindowStyle, "inherit").trim();
        if (!isSupportedPluginActionWindowStyle(windowStyle)) return "Select inherit, compact, or full for the action window style.";
        String windowFit = textOr(tab.ddPluginEditorActionWindowFit, "inherit").trim();
        if (!isSupportedPluginActionWindowFit(windowFit)) return "Select inherit, current, or fit for the action window fit.";
        String requiresProblem = actionRequiresCsvProblem(textOf(tab.edtPluginEditorActionRequires));
        if (!TextUtils.isEmpty(requiresProblem)) return requiresProblem;
        String trustedSha256 = textOf(tab.edtPluginEditorTrustedSha256).trim();
        if (!TextUtils.isEmpty(trustedSha256) && !isFullSha256(trustedSha256)) {
            return "Trusted code expected SHA-256 must be a full 64-character hex string.";
        }
        String trustedClassName = textOf(tab.edtPluginEditorTrustedClassName).trim();
        if (!TextUtils.isEmpty(trustedClassName) && !looksLikeJavaClassName(trustedClassName)) {
            return "Trusted code class/entryClass must look like a Java class name.";
        }
        String trustedMethodName = textOf(tab.edtPluginEditorTrustedMethodName).trim();
        if (!TextUtils.isEmpty(trustedMethodName) && !looksLikeJavaIdentifier(trustedMethodName)) {
            return "Trusted code method must look like a Java method name.";
        }
        String value = textOf(tab.edtPluginEditorHandler).trim();
        if (TextUtils.isEmpty(value)) return "Choose or enter an entry, target, handler, command, or script path.";
        if (isDeclarativeActionType(type) && !isSafeRelativePluginPath(value)) {
            return "Declarative UI target must stay inside the plugin folder.";
        }
        if (isScriptActionType(type) && looksLikePluginScriptPathCandidate(value) && !looksLikePluginScriptPath(value)) {
            return "Script path must stay inside the plugin folder and use a supported script filename.";
        }
        if (isNativeActionType(type) && !isRegisteredNativeHandler(value)) {
            return "Choose a native handler registered by this PermsTest build.";
        }
        if (isTrustedDexActionType(type) && !isSafeRelativePluginPath(value)) {
            return "Trusted-Dex entry must be a safe plugin-relative path.";
        }
        return "";
    }

    private String editorTrustedHashTargetProblem(TabPluginsBinding tab) {
        if (tab == null) return "Plugin Editor is not ready.";
        String type = textOr(tab.ddPluginEditorActionType, actionTypeForRuntime(textOr(tab.ddPluginEditorRuntime, "declarative"))).trim();
        if (!isTrustedDexActionType(type)) return "Choose trusted_dex as the action type before hashing a trusted-code target.";
        String target = normalizePluginPath(textOf(tab.edtPluginEditorHandler));
        if (TextUtils.isEmpty(target)) return "Enter the trusted-code target path before hashing it.";
        if (!isSafeRelativePluginPath(target)) return "Trusted-code target must stay inside the plugin folder.";
        String id = textOf(tab.edtPluginEditorId).trim();
        if (!PluginManifest.isSafeId(id)) return "Enter or load a valid Plugin ID before hashing a staged trusted-code target.";
        if (pendingEditorAssetUris.containsKey(target)) return "";
        String assetPath = normalizePluginPath(textOf(tab.edtPluginEditorAssetPath));
        if (target.equals(assetPath) && pendingEditorAssetPickerUri != null) return "";
        File staged = new File(new File(repository().getPluginRoot(), id), target);
        return staged.isFile() ? "" : "Stage, save, or queue the target payload before hashing it: " + target;
    }

    private void hashEditorTrustedTarget() {
        TabPluginsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null) return;
        final String id = textOf(tab.edtPluginEditorId).trim();
        final String target = normalizePluginPath(textOf(tab.edtPluginEditorHandler));
        final Uri pendingUri;
        if (pendingEditorAssetUris.containsKey(target)) {
            pendingUri = pendingEditorAssetUris.get(target);
        } else if (target.equals(normalizePluginPath(textOf(tab.edtPluginEditorAssetPath)))) {
            pendingUri = pendingEditorAssetPickerUri;
        } else {
            pendingUri = null;
        }
        setEditorStatus("Hashing trusted-code target " + target + "...");
        debug("editor-trusted", "hash requested id=" + id + " target=" + target + (pendingUri != null ? " pending" : " staged"));
        worker.execute(() -> {
            try {
                final String hash;
                final String sourceLabel;
                if (pendingUri != null) {
                    try (InputStream in = activity.getContentResolver().openInputStream(pendingUri)) {
                        if (in == null) throw new IllegalArgumentException("Unable to open selected target file.");
                        hash = sha256Full(in);
                    }
                    sourceLabel = "selected pending payload";
                } else {
                    File staged = new File(new File(repository().getPluginRoot(), id), target);
                    hash = sha256Full(staged);
                    sourceLabel = "staged payload";
                }
                runUi(() -> {
                    TabPluginsBinding uiTab = tab();
                    if (uiTab == null) return;
                    uiTab.edtPluginEditorTrustedSha256.setText(hash);
                    editorActionListDirty = true;
                    setEditorStatus("Filled trusted-code SHA-256 from " + sourceLabel + ": " + target);
                    append("[plugins] Trusted-code SHA-256 for " + id + "/" + target + ": " + hash + "\n");
                    debug("editor-trusted", "hashed id=" + id + " target=" + target + " sha256=" + hash);
                    updatePluginEditorActionState();
                });
            } catch (Throwable t) {
                runUi(() -> {
                    setEditorStatus("Hash trusted-code target failed: " + safeMessage(t));
                    debug("editor-trusted", "hash failed id=" + id + " target=" + target + ": " + safeMessage(t));
                });
            }
        });
    }

    private String editorJsonActionsProblem(TabPluginsBinding tab) {
        String json = textOf(tab == null ? null : tab.edtPluginEditorJson).trim();
        if (TextUtils.isEmpty(json)) return "Enter or build plugin.json before loading its actions.";
        try {
            JSONArray actions = new JSONObject(json).optJSONArray("actions");
            return actions == null || actions.length() == 0 ? "plugin.json does not contain any actions to load." : "";
        } catch (Throwable t) {
            return "Plugin JSON is invalid: " + safeMessage(t);
        }
    }

    private String editorUiControlsJsonProblem(TabPluginsBinding tab) {
        if (!"declarative".equals(textOr(tab == null ? null : tab.ddPluginEditorRuntime, "declarative").trim())) {
            return "Structured UI controls are only available for declarative plugins.";
        }
        String json = textOf(tab == null ? null : tab.edtPluginEditorUiJson).trim();
        if (TextUtils.isEmpty(json)) return "Enter or build ui.json before loading its controls.";
        try {
            JSONArray controls = new JSONObject(json).optJSONArray("controls");
            return controls == null || controls.length() == 0 ? "ui.json does not contain any top-level controls to load." : "";
        } catch (Throwable t) {
            return "ui.json is invalid: " + safeMessage(t);
        }
    }

    private String editorUiControlFieldProblem(TabPluginsBinding tab, int ignoreIndex) {
        if (!"declarative".equals(textOr(tab == null ? null : tab.ddPluginEditorRuntime, "declarative").trim())) {
            return "Structured UI controls are only available for declarative plugins.";
        }
        String type = textOr(tab == null ? null : tab.ddPluginEditorUiType, "label").trim();
        if (!isSupportedUiControlType(type)) return "Select a supported declarative UI control type.";
        String id = textOf(tab.edtPluginEditorUiId).trim();
        if (!TextUtils.isEmpty(id) && !PluginManifest.isSafeId(id)) {
            return "Control ID must use letters, numbers, dots, underscores, or hyphens.";
        }
        if (!TextUtils.isEmpty(id) && editorUiControlIdExists(id, ignoreIndex)) return "Control ID already exists: " + id;
        if (!TextUtils.isEmpty(id) && ignoreIndex == editorUiControlStore.selectedIndex()
                && editorUiNestedStoreContainsId(id, -1)) {
            return "Control ID conflicts with an item inside the selected container: " + id;
        }
        if (requiresUiControlId(type) && TextUtils.isEmpty(id)) return "Enter a Control ID for " + type + ".";
        String label = textOf(tab.edtPluginEditorUiLabel).trim();
        String value = textOf(tab.edtPluginEditorUiValue).trim();
        if ("button".equals(type) && TextUtils.isEmpty(label)) return "Enter button text.";
        if ("dropdown".equals(type) && TextUtils.isEmpty(value)) return "Enter one or more dropdown values separated by |.";
        String actionType = textOr(tab.ddPluginEditorUiActionType, "none").trim();
        if (!isSupportedUiActionType(actionType)) return "Select a supported control action.";
        if (!"none".equals(actionType) && !supportsUiControlAction(type)) {
            return "Structured actions are supported for button, input, multiline, and dropdown controls. Use advanced ui.json for this control type.";
        }
        String data = textOf(tab.edtPluginEditorUiActionData).trim();
        String output = textOf(tab.edtPluginEditorUiActionOutput).trim();
        String optionsProblem = editorUiActionOptionsProblem(tab, actionType);
        if (!TextUtils.isEmpty(optionsProblem)) return optionsProblem;
        if (("toast".equals(actionType) || "shell".equals(actionType) || "api".equals(actionType)) && TextUtils.isEmpty(data)) {
            return "Enter action data for " + actionType + ".";
        }
        if ("api".equals(actionType) && !isSupportedDeclarativeApiName(data)) {
            return "Choose a supported declarative API name or use advanced ui.json for a future API.";
        }
        if (("setText".equals(actionType) || "appendText".equals(actionType))
                && (TextUtils.isEmpty(data) || TextUtils.isEmpty(output))) {
            return "Enter both action data and a target control ID for " + actionType + ".";
        }
        if (("clear".equals(actionType) || "backspace".equals(actionType)) && TextUtils.isEmpty(output)) {
            return "Enter the target control ID for " + actionType + ".";
        }
        return "";
    }

    private String selectedUiContainerProblem(TabPluginsBinding tab) {
        if (!"declarative".equals(textOr(tab == null ? null : tab.ddPluginEditorRuntime, "declarative").trim())) {
            return "Selected Container Contents is only available for declarative plugins.";
        }
        if (!editorUiControlStore.hasSelection()) return "Select a top-level group, section, or buttons control first.";
        String type = selectedUiContainerType();
        if (!isUiContainerType(type)) return "Select a group, section, or buttons control to edit its contents.";
        String editedType = textOr(tab == null ? null : tab.ddPluginEditorUiType, type).trim();
        if (!type.equals(editedType)) {
            return "Tap Update on the selected top-level control before editing its container contents after changing the control type.";
        }
        return "";
    }

    private String editorUiNestedItemFieldProblem(TabPluginsBinding tab, int ignoreIndex) {
        String containerProblem = selectedUiContainerProblem(tab);
        if (!TextUtils.isEmpty(containerProblem)) return containerProblem;
        boolean buttonRow = "buttons".equals(selectedUiContainerType());
        String type = buttonRow ? "button" : textOr(tab.ddPluginEditorUiNestedType, "label").trim();
        if (!isSupportedUiControlType(type)) return "Select a supported nested declarative UI control type.";
        String id = buttonRow ? "" : textOf(tab.edtPluginEditorUiNestedId).trim();
        if (!TextUtils.isEmpty(id) && !PluginManifest.isSafeId(id)) {
            return "Nested control ID must use letters, numbers, dots, underscores, or hyphens.";
        }
        if (!TextUtils.isEmpty(id) && editorUiNestedControlIdExists(id, ignoreIndex)) {
            return "Control ID already exists in the current UI structure: " + id;
        }
        if (!buttonRow && requiresUiControlId(type) && TextUtils.isEmpty(id)) return "Enter a Control ID for nested " + type + ".";
        String label = textOf(tab.edtPluginEditorUiNestedLabel).trim();
        String value = textOf(tab.edtPluginEditorUiNestedValue).trim();
        if ("button".equals(type) && TextUtils.isEmpty(label)) return "Enter nested button text.";
        if ("dropdown".equals(type) && TextUtils.isEmpty(value)) return "Enter one or more nested dropdown values separated by |.";
        String actionType = textOr(tab.ddPluginEditorUiNestedActionType, "none").trim();
        if (!isSupportedUiActionType(actionType)) return "Select a supported nested control action.";
        if (!"none".equals(actionType) && !supportsUiControlAction(type)) {
            return "Structured actions are supported for nested button, input, multiline, and dropdown controls.";
        }
        String data = textOf(tab.edtPluginEditorUiNestedActionData).trim();
        String output = textOf(tab.edtPluginEditorUiNestedActionOutput).trim();
        String optionsProblem = editorUiActionOptionsProblem(textOf(tab.edtPluginEditorUiNestedActionOptionsJson), actionType);
        if (!TextUtils.isEmpty(optionsProblem)) return optionsProblem.replace("Action options", "Nested action options");
        if (("toast".equals(actionType) || "shell".equals(actionType) || "api".equals(actionType)) && TextUtils.isEmpty(data)) {
            return "Enter nested action data for " + actionType + ".";
        }
        if ("api".equals(actionType) && !isSupportedDeclarativeApiName(data)) {
            return "Choose a supported nested declarative API name or use raw ui.json for a future API.";
        }
        if (("setText".equals(actionType) || "appendText".equals(actionType))
                && (TextUtils.isEmpty(data) || TextUtils.isEmpty(output))) {
            return "Enter both nested action data and a target control ID for " + actionType + ".";
        }
        if (("clear".equals(actionType) || "backspace".equals(actionType)) && TextUtils.isEmpty(output)) {
            return "Enter the nested target control ID for " + actionType + ".";
        }
        return "";
    }

    private String editorUiActionOptionsProblem(TabPluginsBinding tab, String actionType) {
        return editorUiActionOptionsProblem(textOf(tab == null ? null : tab.edtPluginEditorUiActionOptionsJson), actionType);
    }

    private String editorUiActionOptionsProblem(String rawJson, String actionType) {
        if ("none".equals(actionType)) return "";
        String json = rawJson == null ? "" : rawJson.trim();
        if (TextUtils.isEmpty(json)) {
            return "sequence".equals(actionType) ? "Enter Action options JSON with a non-empty steps array for sequence." : "";
        }
        try {
            JSONObject options = new JSONObject(json);
            if ("sequence".equals(actionType)) {
                JSONArray steps = options.optJSONArray("steps");
                if (steps == null || steps.length() == 0) return "Sequence Action options JSON must contain a non-empty steps array.";
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.optJSONObject(i);
                    if (step == null) return "Sequence step " + (i + 1) + " must be an action object.";
                    String stepProblem = nestedDeclarativeActionProblem(step, "Sequence step " + (i + 1), 0);
                    if (!TextUtils.isEmpty(stepProblem)) return stepProblem;
                }
            }
            if (options.has("then")) {
                if (!supportsDeclarativeThenAction(actionType)) {
                    return "Nested then is supported for appendText, clear, backspace, and api actions.";
                }
                JSONObject then = options.optJSONObject("then");
                if (then == null) return "Action options then must be an action object.";
                String thenProblem = nestedDeclarativeActionProblem(then, "Nested then action", 0);
                if (!TextUtils.isEmpty(thenProblem)) return thenProblem;
            }
            return "";
        } catch (Throwable t) {
            return "Action options JSON is invalid: " + safeMessage(t);
        }
    }

    private String nestedDeclarativeActionProblem(JSONObject action, String label, int depth) {
        if (action == null) return label + " must be an action object.";
        if (depth > 8) return label + " is nested too deeply.";
        String type = action.optString("type", "").trim();
        if (!isSupportedUiActionType(type) || "none".equals(type)) return label + " uses an unsupported action type: " + type;
        if ("toast".equals(type) && TextUtils.isEmpty(action.optString("message", "").trim())) {
            return label + " requires message.";
        }
        if (("setText".equals(type) || "appendText".equals(type))
                && (TextUtils.isEmpty(action.optString("target", "").trim())
                || TextUtils.isEmpty(action.optString("value", "").trim()))) {
            return label + " requires target and value.";
        }
        if (("clear".equals(type) || "backspace".equals(type))
                && TextUtils.isEmpty(action.optString("target", "").trim())) {
            return label + " requires target.";
        }
        if ("shell".equals(type) && TextUtils.isEmpty(action.optString("command", "").trim())) {
            return label + " requires command.";
        }
        if ("api".equals(type) && !isSupportedDeclarativeApiName(action.optString("name", "").trim())) {
            return label + " requires a supported API name.";
        }
        if ("sequence".equals(type)) {
            JSONArray steps = action.optJSONArray("steps");
            if (steps == null || steps.length() == 0) return label + " requires a non-empty steps array.";
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step == null) return label + " step " + (i + 1) + " must be an action object.";
                String problem = nestedDeclarativeActionProblem(step, label + " step " + (i + 1), depth + 1);
                if (!TextUtils.isEmpty(problem)) return problem;
            }
        }
        if (action.has("then")) {
            if (!supportsDeclarativeThenAction(type)) {
                return label + " uses then on an action type that does not run nested then actions.";
            }
            JSONObject then = action.optJSONObject("then");
            if (then == null) return label + " then must be an action object.";
            return nestedDeclarativeActionProblem(then, label + " then", depth + 1);
        }
        return "";
    }

    private static boolean supportsDeclarativeThenAction(String type) {
        return "appendText".equals(type) || "clear".equals(type)
                || "backspace".equals(type) || "api".equals(type);
    }

    private boolean editorUiControlIdExists(String id, int ignoreIndex) {
        if (TextUtils.isEmpty(id)) return false;
        List<JSONObject> controls = editorUiControlStore.snapshot();
        for (int i = 0; i < controls.size(); i++) {
            if (i == ignoreIndex) continue;
            if (id.equals(controls.get(i).optString("id", "").trim())) return true;
        }
        return false;
    }

    private String editorActionIdValue(TabPluginsBinding tab) {
        String id = textOf(tab == null ? null : tab.edtPluginEditorActionId).trim();
        if (!TextUtils.isEmpty(id)) return id;
        return safeActionId(textOf(tab == null ? null : tab.edtPluginEditorActionTitle).trim());
    }

    private boolean editorActionIdExists(String id, int ignoreIndex) {
        if (TextUtils.isEmpty(id)) return false;
        List<JSONObject> actions = editorActionStore.snapshot();
        for (int i = 0; i < actions.size(); i++) {
            if (i == ignoreIndex) continue;
            if (id.equals(actions.get(i).optString("id", "").trim())) return true;
        }
        return false;
    }

    private String editorUiJsonProblem(TabPluginsBinding tab) {
        String ui = textOf(tab == null ? null : tab.edtPluginEditorUiJson).trim();
        if (TextUtils.isEmpty(ui)) return "";
        try {
            new JSONObject(ui);
            return "";
        } catch (Throwable t) {
            return "ui.json is invalid: " + safeMessage(t);
        }
    }

    private String editorUiRuntimeProblem(String uiJson) {
        return PluginEditorDeclarativeUiValidator.runtimeProblem(uiJson);
    }

    private String editorPackagePrerequisiteProblem(ValidationResult validation, TabPluginsBinding tab) {
        if (validation == null || !validation.ok) return validation == null ? "Plugin validation failed." : validation.message;
        File dir = new File(repository().getPluginRoot(), validation.id);
        if (!TextUtils.isEmpty(validation.icon) && pendingEditorIconUri == null
                && !new File(dir, validation.icon).isFile()) {
            return "Choose the declared icon or stage it before packaging: " + validation.icon;
        }
        if ("declarative".equals(validation.runtime)) {
            String rawUi = textOf(tab == null ? null : tab.edtPluginEditorUiJson).trim();
            String uiProblem = TextUtils.isEmpty(rawUi) ? "" : editorUiRuntimeProblem(rawUi);
            if (!TextUtils.isEmpty(uiProblem)) return uiProblem;
        }
        try {
            JSONObject root = new JSONObject(textOf(tab.edtPluginEditorJson));
            JSONArray actions = root.optJSONArray("actions");
            String rawUi = textOf(tab.edtPluginEditorUiJson).trim();
            for (String uiPath : declaredDeclarativeUiPaths(actions, validation.runtime, validation.entry)) {
                boolean pendingRootUi = "declarative".equals(validation.runtime)
                        && pluginPathsEqual(uiPath, validation.entry)
                        && !TextUtils.isEmpty(rawUi);
                if (!new File(dir, uiPath).isFile() && !pendingRootUi) {
                    return "Stage the required declarative UI file before packaging: " + uiPath;
                }
            }
            if (actions != null) {
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject item = actions.optJSONObject(i);
                    if (item == null) continue;
                    String script = item.optString("script", "").trim();
                    if (!TextUtils.isEmpty(script) && !new File(dir, script).isFile()
                            && !hasPendingEditorScript(script)) {
                        return "Choose or stage the required script before packaging: " + script;
                    }
                }
            }
        } catch (Throwable t) {
            return "Plugin JSON is invalid: " + safeMessage(t);
        }
        return "";
    }

    private void setupPluginEditorJsonEditor(EditText editor) {
        if (editor == null) return;
        try {
            editor.setVerticalScrollBarEnabled(true);
            editor.setHorizontalScrollBarEnabled(true);
            editor.setHorizontallyScrolling(true);
            ViewCompat.setNestedScrollingEnabled(editor, true);
        } catch (Throwable ignored) {}
        final float[] lastTouchY = new float[1];
        editor.setOnTouchListener((v, event) -> {
            try {
                if (event == null) return false;
                ViewParent parent = v.getParent();
                int action = event.getActionMasked();
                if (parent != null) {
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        parent.requestDisallowInterceptTouchEvent(false);
                    } else if (action == MotionEvent.ACTION_DOWN) {
                        lastTouchY[0] = event.getY();
                        parent.requestDisallowInterceptTouchEvent(true);
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        float y = event.getY();
                        float dy = y - lastTouchY[0];
                        lastTouchY[0] = y;
                        int direction = dy > 0f ? -1 : 1;
                        parent.requestDisallowInterceptTouchEvent(v.canScrollVertically(direction));
                    }
                }
            } catch (Throwable ignored) {}
            return false;
        });
        final boolean[] applying = new boolean[1];
        final Runnable highlight = () -> {
            if (applying[0]) return;
            try {
                applying[0] = true;
                SourceSyntaxHighlighter.applyJson(editor);
            } catch (Throwable ignored) {
            } finally {
                applying[0] = false;
            }
        };
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (applying[0]) return;
                editor.removeCallbacks(highlight);
                editor.postDelayed(highlight, 180L);
            }
        });
        editor.post(highlight);
    }

    private void bindPluginEditorDropdowns(TabPluginsBinding tab) {
        if (tab == null) return;
        bindEditorDropdown(tab.tilPluginEditorRuntime, tab.ddPluginEditorRuntime, new String[] {
                "declarative", "script", "trusted_native", "trusted_dex"
        }, "declarative");
        bindEditorDropdown(tab.tilPluginEditorWindowStyle, tab.ddPluginEditorWindowStyle, new String[] {
                "compact", "full"
        }, "compact");
        bindEditorDropdown(tab.tilPluginEditorWindowFit, tab.ddPluginEditorWindowFit, new String[] {
                "current", "fit"
        }, "current");
        bindEditorSuggestionDropdown(tab.tilPluginEditorActionTitle, tab.edtPluginEditorActionTitle,
                ACTION_TITLE_SUGGESTIONS, CUSTOM_ACTION_TITLE_LABEL, "Action title");
        bindEditorDropdown(tab.tilPluginEditorActionType, tab.ddPluginEditorActionType,
                PLUGIN_ACTION_TYPE_SUGGESTIONS, "declarative_ui");
        bindEditorDropdown(tab.tilPluginEditorActionPresentation, tab.ddPluginEditorActionPresentation,
                PLUGIN_ACTION_PRESENTATION_SUGGESTIONS, "default");
        bindEditorDropdown(tab.tilPluginEditorActionSyntax, tab.ddPluginEditorActionSyntax,
                PLUGIN_ACTION_SYNTAX_SUGGESTIONS, "default");
        bindEditorDropdown(tab.tilPluginEditorActionWindowStyle, tab.ddPluginEditorActionWindowStyle,
                PLUGIN_ACTION_WINDOW_STYLE_SUGGESTIONS, "inherit");
        bindEditorDropdown(tab.tilPluginEditorActionWindowFit, tab.ddPluginEditorActionWindowFit,
                PLUGIN_ACTION_WINDOW_FIT_SUGGESTIONS, "inherit");
        bindEditorDropdown(tab.tilPluginEditorUiType, tab.ddPluginEditorUiType,
                UI_CONTROL_TYPE_SUGGESTIONS, "label");
        bindEditorDropdown(tab.tilPluginEditorUiActionType, tab.ddPluginEditorUiActionType,
                UI_ACTION_TYPE_SUGGESTIONS, "none");
        bindEditorDropdown(tab.tilPluginEditorUiNestedType, tab.ddPluginEditorUiNestedType,
                UI_CONTROL_TYPE_SUGGESTIONS, "label");
        bindEditorDropdown(tab.tilPluginEditorUiNestedActionType, tab.ddPluginEditorUiNestedActionType,
                UI_ACTION_TYPE_SUGGESTIONS, "none");
        refreshEditorHandlerSuggestions(tab);
        refreshEditorUiActionDataSuggestions(tab);
        refreshEditorUiNestedActionDataSuggestions(tab);
        tab.ddPluginEditorRuntime.setOnItemClickListener((parent, view, position, id) -> {
            refreshEditorHandlerSuggestions(tab);
            updatePluginEditorActionState();
        });
        tab.ddPluginEditorActionType.setOnItemClickListener((parent, view, position, id) -> {
            refreshEditorHandlerSuggestions(tab);
            updatePluginEditorActionState();
        });
        tab.ddPluginEditorActionPresentation.setOnItemClickListener((parent, view, position, id) -> updatePluginEditorActionState());
        tab.ddPluginEditorActionSyntax.setOnItemClickListener((parent, view, position, id) -> updatePluginEditorActionState());
        tab.ddPluginEditorActionWindowStyle.setOnItemClickListener((parent, view, position, id) -> updatePluginEditorActionState());
        tab.ddPluginEditorActionWindowFit.setOnItemClickListener((parent, view, position, id) -> updatePluginEditorActionState());
        tab.ddPluginEditorUiType.setOnItemClickListener((parent, view, position, id) -> updatePluginEditorActionState());
        tab.ddPluginEditorUiActionType.setOnItemClickListener((parent, view, position, id) -> {
            refreshEditorUiActionDataSuggestions(tab);
            updatePluginEditorActionState();
        });
        tab.ddPluginEditorUiNestedType.setOnItemClickListener((parent, view, position, id) -> updatePluginEditorActionState());
        tab.ddPluginEditorUiNestedActionType.setOnItemClickListener((parent, view, position, id) -> {
            refreshEditorUiNestedActionDataSuggestions(tab);
            updatePluginEditorActionState();
        });
        tab.ddPluginEditorRuntime.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refreshEditorHandlerSuggestions(tab); }
        });
        tab.ddPluginEditorActionType.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refreshEditorHandlerSuggestions(tab); }
        });
        tab.ddPluginEditorUiActionType.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refreshEditorUiActionDataSuggestions(tab); }
        });
        tab.edtPluginEditorUiActionData.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (editorUiFieldSyncing) return;
                maybeApplyEditorUiActionMappingPreset(tab,
                        textOr(tab.ddPluginEditorUiActionType, "none").trim(),
                        s == null ? "" : s.toString().trim());
            }
        });
        tab.ddPluginEditorUiNestedActionType.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refreshEditorUiNestedActionDataSuggestions(tab); }
        });
        tab.edtPluginEditorUiNestedActionData.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (editorUiNestedFieldSyncing) return;
                maybeApplyEditorUiNestedActionMappingPreset(tab,
                        textOr(tab.ddPluginEditorUiNestedActionType, "none").trim(),
                        s == null ? "" : s.toString().trim());
            }
        });
    }

    private void bindEditorDropdown(TextInputLayout layout, MaterialAutoCompleteTextView view, String[] values, String fallback) {
        Activity activity = activity();
        if (activity == null || layout == null || view == null) return;
        view.setAdapter(new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, values));
        if (TextUtils.isEmpty(textOf(view))) view.setText(fallback, false);
        DropdownUi.bindExposedDropdown(activity, layout, view, () -> DropdownUi.showDropdown(view));
    }

    private void bindEditorSuggestionDropdown(TextInputLayout layout,
                                              MaterialAutoCompleteTextView view,
                                              String[] values,
                                              String customLabel,
                                              String customTitle) {
        Activity activity = activity();
        if (activity == null || layout == null || view == null) return;
        view.setAdapter(new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, values));
        DropdownUi.bindExposedDropdown(activity, layout, view, () -> DropdownUi.showDropdown(view));
        view.setOnLongClickListener(v -> {
            showEditorCustomValueDialog(customTitle, textOf(view), value -> {
                view.setText(value, false);
                updatePluginEditorActionState();
            });
            return true;
        });
        view.setOnItemClickListener((parent, itemView, position, id) -> {
            Object item = parent == null ? null : parent.getItemAtPosition(position);
            String value = item == null ? "" : String.valueOf(item);
            if (customLabel.equals(value)) {
                view.setText("", false);
                showEditorCustomValueDialog(customTitle, "", custom -> {
                    view.setText(custom, false);
                    updatePluginEditorActionState();
                });
            } else {
                view.setText(value, false);
                updatePluginEditorActionState();
            }
        });
    }

    private void refreshEditorUiActionDataSuggestions(TabPluginsBinding tab) {
        Activity activity = activity();
        if (activity == null || tab == null || tab.edtPluginEditorUiActionData == null) return;
        String actionType = textOr(tab.ddPluginEditorUiActionType, "none").trim();
        boolean typeChanged = !TextUtils.equals(editorUiActionSuggestionType, actionType);
        if (!typeChanged && tab.edtPluginEditorUiActionData.getAdapter() != null) {
            updateEditorUiActionFieldAvailability(tab);
            return;
        }
        editorUiActionSuggestionType = actionType;
        if (typeChanged && "sequence".equals(actionType)
                && TextUtils.isEmpty(textOf(tab.edtPluginEditorUiActionOptionsJson).trim())) {
            tab.edtPluginEditorUiActionOptionsJson.setText("{\n  \"steps\": [\n    {\n      \"type\": \"toast\",\n      \"message\": \"Step 1\"\n    }\n  ]\n}\n");
        }
        String[] values = editorUiActionDataSuggestions(actionType);
        tab.edtPluginEditorUiActionData.setAdapter(new NoFilterArrayAdapter(activity,
                android.R.layout.simple_dropdown_item_1line, values));
        DropdownUi.bindExposedDropdown(activity, tab.tilPluginEditorUiActionData, tab.edtPluginEditorUiActionData,
                () -> DropdownUi.showDropdown(tab.edtPluginEditorUiActionData));
        tab.edtPluginEditorUiActionData.setOnLongClickListener(v -> {
            showEditorCustomValueDialog("Action data / command / API name", textOf(tab.edtPluginEditorUiActionData), value -> {
                tab.edtPluginEditorUiActionData.setText(value, false);
                updatePluginEditorActionState();
            });
            return true;
        });
        tab.edtPluginEditorUiActionData.setOnItemClickListener((parent, itemView, position, id) -> {
            Object item = parent == null ? null : parent.getItemAtPosition(position);
            String value = item == null ? "" : String.valueOf(item);
            if (CUSTOM_UI_ACTION_DATA_LABEL.equals(value)) {
                String initial = textOf(tab.edtPluginEditorUiActionData).trim();
                tab.edtPluginEditorUiActionData.setText("", false);
                showEditorCustomValueDialog("Action data / command / API name", initial, custom -> {
                    tab.edtPluginEditorUiActionData.setText(custom, false);
                    updatePluginEditorActionState();
                });
            } else {
                tab.edtPluginEditorUiActionData.setText(value, false);
                maybeApplyEditorUiActionMappingPreset(tab, actionType, value);
                updatePluginEditorActionState();
            }
        });
        updateEditorUiActionFieldAvailability(tab);
    }

    private void maybeApplyEditorUiActionMappingPreset(TabPluginsBinding tab, String actionType, String value) {
        if (tab == null || !"api".equals(actionType)
                || !TextUtils.isEmpty(textOf(tab.edtPluginEditorUiActionOptionsJson).trim())) return;
        applyEditorUiActionApiPreset(tab, value, false);
    }

    private void applyEditorUiActionApiPreset() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        if (!"api".equals(textOr(tab.ddPluginEditorUiActionType, "none").trim())) {
            setEditorStatus("Choose Control action = api before filling an API mapping preset.");
            return;
        }
        String api = textOf(tab.edtPluginEditorUiActionData).trim();
        if (TextUtils.isEmpty(api) || CUSTOM_UI_ACTION_DATA_LABEL.equals(api)) {
            setEditorStatus("Choose a supported API name before filling an API mapping preset.");
            return;
        }
        applyEditorUiActionApiPreset(tab, api, true);
    }

    private void applyEditorUiActionApiPreset(TabPluginsBinding tab, String value, boolean showStatus) {
        if (tab == null) return;
        try {
            JSONObject options = editorUiApiMappingPreset(tab, value);
            if (options == null) {
                if (showStatus) setEditorStatus("No API mapping preset is available for: " + value);
                return;
            }
            tab.edtPluginEditorUiActionOptionsJson.setText(options.toString(2) + "\n");
            editorUiListDirty = true;
            updatePluginEditorActionState();
            if (showStatus) setEditorStatus("Filled API mapping preset for " + value + ".");
        } catch (Throwable t) {
            if (showStatus) setEditorStatus("API mapping preset failed: " + safeMessage(t));
        }
    }

    private JSONObject editorUiApiMappingPreset(TabPluginsBinding tab, String value) throws Exception {
        if (tab == null || TextUtils.isEmpty(value)) return null;
        JSONObject options = new JSONObject();
        if ("calculator.evaluateInteger".equalsIgnoreCase(value)) {
            options.put("input", "input");
            options.put("base", "base");
            JSONObject outputs = new JSONObject();
            outputs.put("hex", "hex");
            outputs.put("dec", "dec");
            outputs.put("oct", "oct");
            outputs.put("bin", "bin");
            options.put("outputs", outputs);
            options.put("status", "status");
            tab.edtPluginEditorUiActionOutput.setText("");
        } else if ("converter.textToBytes".equalsIgnoreCase(value)) {
            options.put("input", "text");
            options.put("encoding", "encoding");
            options.put("valueType", "type");
            options.put("delimiter", "delimiter");
            tab.edtPluginEditorUiActionOutput.setText("values");
        } else if ("converter.bytesToText".equalsIgnoreCase(value)) {
            options.put("input", "values");
            options.put("encoding", "encoding");
            options.put("valueType", "type");
            tab.edtPluginEditorUiActionOutput.setText("text");
        } else if ("text.contains".equalsIgnoreCase(value)) {
            options.put("input", "text");
            options.put("query", "query");
            tab.edtPluginEditorUiActionOutput.setText("output");
        } else if ("text.replace".equalsIgnoreCase(value)) {
            options.put("input", "text");
            options.put("search", "search");
            options.put("replacement", "replacement");
            tab.edtPluginEditorUiActionOutput.setText("output");
        } else if (isSimpleDeclarativeApiName(value)) {
            options.put("input", "text");
            if ("hash.sha256".equalsIgnoreCase(value)
                    || "encoding.base64Encode".equalsIgnoreCase(value)
                    || "encoding.base64Decode".equalsIgnoreCase(value)
                    || "encoding.hexEncode".equalsIgnoreCase(value)
                    || "encoding.hexDecode".equalsIgnoreCase(value)
                    || "url.encode".equalsIgnoreCase(value)
                    || "url.decode".equalsIgnoreCase(value)) {
                options.put("encoding", "encoding");
            }
            if ("encoding.hexEncode".equalsIgnoreCase(value)) {
                options.put("delimiter", "delimiter");
            }
            tab.edtPluginEditorUiActionOutput.setText("output");
        } else {
            return null;
        }
        return options;
    }

    private void clearEditorUiActionMapping() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        tab.edtPluginEditorUiActionOptionsJson.setText("");
        tab.edtPluginEditorUiActionOutput.setText("");
        editorUiListDirty = true;
        setEditorStatus("Cleared the selected declarative action mapping fields.");
        updatePluginEditorActionState();
    }

    private String[] editorUiActionDataSuggestions(String actionType) {
        if ("api".equals(actionType)) return UI_API_NAME_SUGGESTIONS;
        if ("shell".equals(actionType)) return new String[]{"id", "getprop", "pm list packages", CUSTOM_UI_ACTION_DATA_LABEL};
        if ("toast".equals(actionType)) return new String[]{"Done", "${input}", "${output}", CUSTOM_UI_ACTION_DATA_LABEL};
        if ("setText".equals(actionType) || "appendText".equals(actionType)) {
            return new String[]{"${input}", "${output}", CUSTOM_UI_ACTION_DATA_LABEL};
        }
        return new String[]{CUSTOM_UI_ACTION_DATA_LABEL};
    }

    private static boolean isSimpleDeclarativeApiName(String name) {
        if (TextUtils.isEmpty(name)) return false;
        return "text.uppercase".equalsIgnoreCase(name)
                || "text.lowercase".equalsIgnoreCase(name)
                || "text.trim".equalsIgnoreCase(name)
                || "text.reverse".equalsIgnoreCase(name)
                || "text.length".equalsIgnoreCase(name)
                || "text.wordCount".equalsIgnoreCase(name)
                || "text.isBlank".equalsIgnoreCase(name)
                || "text.lineCount".equalsIgnoreCase(name)
                || "json.pretty".equalsIgnoreCase(name)
                || "json.minify".equalsIgnoreCase(name)
                || "url.encode".equalsIgnoreCase(name)
                || "url.decode".equalsIgnoreCase(name)
                || "hash.sha256".equalsIgnoreCase(name)
                || "encoding.base64Encode".equalsIgnoreCase(name)
                || "encoding.base64Decode".equalsIgnoreCase(name)
                || "encoding.hexEncode".equalsIgnoreCase(name)
                || "encoding.hexDecode".equalsIgnoreCase(name);
    }

    private void updateEditorUiActionFieldAvailability(TabPluginsBinding tab) {
        if (tab == null) return;
        String type = textOr(tab.ddPluginEditorUiActionType, "none").trim();
        boolean hasAction = !"none".equals(type);
        boolean dataEnabled = "toast".equals(type) || "setText".equals(type) || "appendText".equals(type)
                || "shell".equals(type) || "api".equals(type);
        boolean outputEnabled = "setText".equals(type) || "appendText".equals(type) || "clear".equals(type)
                || "backspace".equals(type) || "shell".equals(type) || "api".equals(type);
        try { tab.tilPluginEditorUiActionData.setEnabled(dataEnabled); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiActionData.setEnabled(dataEnabled); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiActionOutput.setEnabled(outputEnabled); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiActionOptionsJson.setEnabled(hasAction); } catch (Throwable ignored) {}
    }

    private void refreshEditorUiNestedActionDataSuggestions(TabPluginsBinding tab) {
        Activity activity = activity();
        if (activity == null || tab == null || tab.edtPluginEditorUiNestedActionData == null) return;
        String actionType = textOr(tab.ddPluginEditorUiNestedActionType, "none").trim();
        boolean typeChanged = !TextUtils.equals(editorUiNestedActionSuggestionType, actionType);
        if (!typeChanged && tab.edtPluginEditorUiNestedActionData.getAdapter() != null) {
            updateEditorUiNestedFieldAvailability(tab);
            return;
        }
        editorUiNestedActionSuggestionType = actionType;
        if (typeChanged && "sequence".equals(actionType)
                && TextUtils.isEmpty(textOf(tab.edtPluginEditorUiNestedActionOptionsJson).trim())) {
            tab.edtPluginEditorUiNestedActionOptionsJson.setText("{\n  \"steps\": [\n    {\n      \"type\": \"toast\",\n      \"message\": \"Step 1\"\n    }\n  ]\n}\n");
        }
        tab.edtPluginEditorUiNestedActionData.setAdapter(new NoFilterArrayAdapter(activity,
                android.R.layout.simple_dropdown_item_1line, editorUiActionDataSuggestions(actionType)));
        DropdownUi.bindExposedDropdown(activity, tab.tilPluginEditorUiNestedActionData,
                tab.edtPluginEditorUiNestedActionData, () -> DropdownUi.showDropdown(tab.edtPluginEditorUiNestedActionData));
        tab.edtPluginEditorUiNestedActionData.setOnLongClickListener(v -> {
            showEditorCustomValueDialog("Nested action data / command / API name", textOf(tab.edtPluginEditorUiNestedActionData), value -> {
                tab.edtPluginEditorUiNestedActionData.setText(value, false);
                updatePluginEditorActionState();
            });
            return true;
        });
        tab.edtPluginEditorUiNestedActionData.setOnItemClickListener((parent, itemView, position, id) -> {
            Object item = parent == null ? null : parent.getItemAtPosition(position);
            String value = item == null ? "" : String.valueOf(item);
            if (CUSTOM_UI_ACTION_DATA_LABEL.equals(value)) {
                String initial = textOf(tab.edtPluginEditorUiNestedActionData).trim();
                tab.edtPluginEditorUiNestedActionData.setText("", false);
                showEditorCustomValueDialog("Nested action data / command / API name", initial, custom -> {
                    tab.edtPluginEditorUiNestedActionData.setText(custom, false);
                    updatePluginEditorActionState();
                });
            } else {
                tab.edtPluginEditorUiNestedActionData.setText(value, false);
                maybeApplyEditorUiNestedActionMappingPreset(tab, actionType, value);
                updatePluginEditorActionState();
            }
        });
        updateEditorUiNestedFieldAvailability(tab);
    }

    private void maybeApplyEditorUiNestedActionMappingPreset(TabPluginsBinding tab, String actionType, String value) {
        if (tab == null || !"api".equals(actionType)
                || !TextUtils.isEmpty(textOf(tab.edtPluginEditorUiNestedActionOptionsJson).trim())) return;
        try {
            JSONObject options = new JSONObject();
            if ("calculator.evaluateInteger".equalsIgnoreCase(value)) {
                options.put("input", "input");
                options.put("base", "base");
                JSONObject outputs = new JSONObject();
                outputs.put("hex", "hex");
                outputs.put("dec", "dec");
                outputs.put("oct", "oct");
                outputs.put("bin", "bin");
                options.put("outputs", outputs);
                options.put("status", "status");
                tab.edtPluginEditorUiNestedActionOutput.setText("");
            } else if ("converter.textToBytes".equalsIgnoreCase(value)) {
                options.put("input", "text");
                options.put("encoding", "encoding");
                options.put("valueType", "type");
                options.put("delimiter", "delimiter");
                tab.edtPluginEditorUiNestedActionOutput.setText("values");
            } else if ("converter.bytesToText".equalsIgnoreCase(value)) {
                options.put("input", "values");
                options.put("encoding", "encoding");
                options.put("valueType", "type");
                tab.edtPluginEditorUiNestedActionOutput.setText("text");
            } else if ("text.contains".equalsIgnoreCase(value)) {
                options.put("input", "text");
                options.put("query", "query");
                tab.edtPluginEditorUiNestedActionOutput.setText("output");
            } else if ("text.replace".equalsIgnoreCase(value)) {
                options.put("input", "text");
                options.put("search", "search");
                options.put("replacement", "replacement");
                tab.edtPluginEditorUiNestedActionOutput.setText("output");
            } else if (isSimpleDeclarativeApiName(value)) {
                options.put("input", "text");
                if ("hash.sha256".equalsIgnoreCase(value)
                        || "encoding.base64Encode".equalsIgnoreCase(value)
                        || "encoding.base64Decode".equalsIgnoreCase(value)
                        || "encoding.hexEncode".equalsIgnoreCase(value)
                        || "encoding.hexDecode".equalsIgnoreCase(value)
                        || "url.encode".equalsIgnoreCase(value)
                        || "url.decode".equalsIgnoreCase(value)) {
                    options.put("encoding", "encoding");
                }
                if ("encoding.hexEncode".equalsIgnoreCase(value)) {
                    options.put("delimiter", "delimiter");
                }
                tab.edtPluginEditorUiNestedActionOutput.setText("output");
            } else {
                return;
            }
            tab.edtPluginEditorUiNestedActionOptionsJson.setText(options.toString(2) + "\n");
        } catch (Throwable ignored) {
        }
    }

    private void updateEditorUiNestedFieldAvailability(TabPluginsBinding tab) {
        if (tab == null) return;
        String containerType = selectedUiContainerType();
        boolean enabled = TextUtils.isEmpty(selectedUiContainerProblem(tab));
        boolean buttonRow = enabled && "buttons".equals(containerType);
        if (buttonRow && !"button".equals(textOf(tab.ddPluginEditorUiNestedType))) {
            boolean previous = editorUiNestedFieldSyncing;
            editorUiNestedFieldSyncing = true;
            try { tab.ddPluginEditorUiNestedType.setText("button", false); } catch (Throwable ignored) {}
            editorUiNestedFieldSyncing = previous;
        }
        String actionType = textOr(tab.ddPluginEditorUiNestedActionType, "none").trim();
        boolean hasAction = enabled && !"none".equals(actionType);
        boolean dataEnabled = enabled && ("toast".equals(actionType) || "setText".equals(actionType)
                || "appendText".equals(actionType) || "shell".equals(actionType) || "api".equals(actionType));
        boolean outputEnabled = enabled && ("setText".equals(actionType) || "appendText".equals(actionType)
                || "clear".equals(actionType) || "backspace".equals(actionType)
                || "shell".equals(actionType) || "api".equals(actionType));
        try { tab.tilPluginEditorUiNestedType.setEnabled(enabled && !buttonRow); } catch (Throwable ignored) {}
        try { tab.ddPluginEditorUiNestedType.setEnabled(enabled && !buttonRow); } catch (Throwable ignored) {}
        try { tab.tilPluginEditorUiNestedId.setEnabled(enabled && !buttonRow); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiNestedId.setEnabled(enabled && !buttonRow); } catch (Throwable ignored) {}
        try { tab.tilPluginEditorUiNestedLabel.setEnabled(enabled); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiNestedLabel.setEnabled(enabled); } catch (Throwable ignored) {}
        try { tab.tilPluginEditorUiNestedValue.setEnabled(enabled && !buttonRow); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiNestedValue.setEnabled(enabled && !buttonRow); } catch (Throwable ignored) {}
        try { tab.tilPluginEditorUiNestedActionType.setEnabled(enabled); } catch (Throwable ignored) {}
        try { tab.ddPluginEditorUiNestedActionType.setEnabled(enabled); } catch (Throwable ignored) {}
        try { tab.tilPluginEditorUiNestedActionData.setEnabled(dataEnabled); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiNestedActionData.setEnabled(dataEnabled); } catch (Throwable ignored) {}
        try { tab.tilPluginEditorUiNestedActionOutput.setEnabled(outputEnabled); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiNestedActionOutput.setEnabled(outputEnabled); } catch (Throwable ignored) {}
        try { tab.tilPluginEditorUiNestedActionOptionsJson.setEnabled(hasAction); } catch (Throwable ignored) {}
        try { tab.edtPluginEditorUiNestedActionOptionsJson.setEnabled(hasAction); } catch (Throwable ignored) {}
    }

    private void refreshEditorHandlerSuggestions(TabPluginsBinding tab) {
        Activity activity = activity();
        if (activity == null || tab == null || tab.edtPluginEditorHandler == null) return;
        String runtime = textOr(tab.ddPluginEditorRuntime, "declarative").trim();
        String actionType = textOr(tab.ddPluginEditorActionType, actionTypeForRuntime(runtime)).trim();
        String suggestionRuntime = handlerSuggestionRuntime(actionType, runtime);
        String current = textOf(tab.edtPluginEditorHandler).trim();
        boolean runtimeChanged = !TextUtils.equals(editorHandlerSuggestionRuntime, suggestionRuntime);
        if (!runtimeChanged && tab.edtPluginEditorHandler.getAdapter() != null) return;
        if (runtimeChanged && (TextUtils.isEmpty(current) || isKnownEditorHandlerSuggestion(current))) {
            current = defaultEditorHandlerSuggestion(suggestionRuntime);
        }
        editorHandlerSuggestionRuntime = suggestionRuntime;
        String[] values = editorHandlerSuggestions(suggestionRuntime);
        tab.edtPluginEditorHandler.setAdapter(new NoFilterArrayAdapter(activity,
                android.R.layout.simple_dropdown_item_1line, values));
        DropdownUi.bindExposedDropdown(activity, tab.tilPluginEditorHandler, tab.edtPluginEditorHandler,
                () -> DropdownUi.showDropdown(tab.edtPluginEditorHandler));
        tab.edtPluginEditorHandler.setOnLongClickListener(v -> {
            showEditorCustomValueDialog("Entry / target / handler", textOf(tab.edtPluginEditorHandler), value -> {
                tab.edtPluginEditorHandler.setText(value, false);
                updatePluginEditorActionState();
            });
            return true;
        });
        tab.edtPluginEditorHandler.setOnItemClickListener((parent, itemView, position, id) -> {
            Object item = parent == null ? null : parent.getItemAtPosition(position);
            String value = item == null ? "" : String.valueOf(item);
            if (CUSTOM_ACTION_TARGET_LABEL.equals(value)) {
                tab.edtPluginEditorHandler.setText("", false);
                showEditorCustomValueDialog("Entry / target / handler", "", custom -> {
                    tab.edtPluginEditorHandler.setText(custom, false);
                    updatePluginEditorActionState();
                });
            } else {
                tab.edtPluginEditorHandler.setText(value, false);
                updatePluginEditorActionState();
            }
        });
        if (!TextUtils.isEmpty(current)) tab.edtPluginEditorHandler.setText(current, false);
    }

    private String[] editorHandlerSuggestions(String runtime) {
        if ("script".equals(runtime)) {
            return new String[]{"getprop", "id", "uname -a", "scripts/run.sh", CUSTOM_ACTION_TARGET_LABEL};
        }
        if ("trusted_native".equals(runtime)) {
            return new String[]{
                    BuildPropPluginAction.HANDLER,
                    DeviceInfoPluginAction.HANDLER,
                    LogSnapshotPluginAction.HANDLER,
                    ToolGroupPluginAction.HANDLER_CALCULATOR,
                    ToolGroupPluginAction.HANDLER_ASCII_HEX,
                    ToolGroupPluginAction.HANDLER_ALARMS_TIMERS,
                    CUSTOM_ACTION_TARGET_LABEL
            };
        }
        if ("trusted_dex".equals(runtime)) {
            return new String[]{"classes.dex", "plugin.dex", CUSTOM_ACTION_TARGET_LABEL};
        }
        return new String[]{"ui.json", "ui/main.json", CUSTOM_ACTION_TARGET_LABEL};
    }

    private String defaultEditorHandlerSuggestion(String runtime) {
        String[] values = editorHandlerSuggestions(runtime);
        return values.length == 0 ? "" : values[0];
    }

    private boolean isKnownEditorHandlerSuggestion(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String[] runtimes = new String[]{"declarative", "script", "trusted_native", "trusted_dex"};
        for (String runtime : runtimes) {
            for (String suggestion : editorHandlerSuggestions(runtime)) {
                if (!CUSTOM_ACTION_TARGET_LABEL.equals(suggestion) && value.equals(suggestion)) return true;
            }
        }
        return false;
    }

    private void showEditorCustomValueDialog(String title, String initial, EditorValueCallback callback) {
        Activity activity = activity();
        if (activity == null || callback == null) return;
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setText(initial == null ? "" : initial);
        field.setSelectAllOnFocus(true);
        int pad = dp(16);
        field.setPadding(pad, dp(8), pad, dp(8));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(field)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = field.getText() == null ? "" : field.getText().toString().trim();
            callback.onValue(value);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void loadEditorActionsFromJson() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        try {
            JSONObject root = new JSONObject(textOf(tab.edtPluginEditorJson));
            editorActionStore.load(root.optJSONArray("actions"));
            editorActionListDirty = false;
            renderEditorActions();
            loadSelectedEditorActionFields();
            setEditorStatus("Loaded " + editorActionStore.size() + " structured action(s) from plugin.json. Other advanced JSON fields were left unchanged.");
            debug("editor-actions", "loaded from json count=" + editorActionStore.size());
        } catch (Throwable t) {
            setEditorStatus("Load JSON Actions failed: " + safeMessage(t));
        }
    }

    private void addEditorAction() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        try {
            JSONObject action = buildEditorActionFromFields(null);
            editorActionStore.add(action);
            editorActionListDirty = true;
            renderEditorActions();
            loadSelectedEditorActionFields();
            setEditorStatus("Added action " + action.optString("id", "action") + ". Tap Build JSON when the structured action list is ready.");
            debug("editor-actions", "added id=" + action.optString("id", ""));
        } catch (Throwable t) {
            setEditorStatus("Add action failed: " + safeMessage(t));
        }
    }

    private void updateSelectedEditorAction() {
        try {
            JSONObject action = buildEditorActionFromFields(editorActionStore.selectedCopy());
            if (!editorActionStore.updateSelected(action)) {
                setEditorStatus("Select an action before updating it.");
                return;
            }
            editorActionListDirty = true;
            renderEditorActions();
            loadSelectedEditorActionFields();
            setEditorStatus("Updated action " + action.optString("id", "action") + ". Tap Build JSON to update plugin.json.");
            debug("editor-actions", "updated id=" + action.optString("id", ""));
        } catch (Throwable t) {
            setEditorStatus("Update action failed: " + safeMessage(t));
        }
    }

    private void removeSelectedEditorAction() {
        JSONObject selected = editorActionStore.selectedCopy();
        String id = selected == null ? "action" : selected.optString("id", "action");
        if (!editorActionStore.removeSelected()) return;
        editorActionListDirty = true;
        renderEditorActions();
        loadSelectedEditorActionFields();
        setEditorStatus("Removed action " + id + ". Tap Build JSON to update plugin.json.");
        debug("editor-actions", "removed id=" + id);
    }

    private void moveSelectedEditorAction(int delta) {
        if (!editorActionStore.moveSelected(delta)) return;
        editorActionListDirty = true;
        renderEditorActions();
        loadSelectedEditorActionFields();
        setEditorStatus("Moved selected action " + (delta < 0 ? "up" : "down") + ". Tap Build JSON to update plugin.json.");
        debug("editor-actions", "moved selected delta=" + delta + ", index=" + editorActionStore.selectedIndex());
    }

    private JSONObject buildEditorActionFromFields(JSONObject existing) throws Exception {
        TabPluginsBinding tab = tab();
        if (tab == null) throw new IllegalStateException("Plugin Editor is not ready.");
        JSONObject action = existing == null ? new JSONObject() : new JSONObject(existing.toString());
        String runtime = textOr(tab.ddPluginEditorRuntime, "declarative").trim();
        String title = textOf(tab.edtPluginEditorActionTitle).trim();
        String id = editorActionIdValue(tab);
        String value = textOf(tab.edtPluginEditorHandler).trim();
        String type = textOr(tab.ddPluginEditorActionType, actionTypeForRuntime(runtime)).trim();
        String presentation = textOr(tab.ddPluginEditorActionPresentation, "default").trim();
        String syntax = textOr(tab.ddPluginEditorActionSyntax, "default").trim();
        String windowStyle = textOr(tab.ddPluginEditorActionWindowStyle, "inherit").trim();
        String windowFit = textOr(tab.ddPluginEditorActionWindowFit, "inherit").trim();
        JSONArray requires = capabilityArrayFromCsv(textOf(tab.edtPluginEditorActionRequires));
        String trustedSha256 = textOf(tab.edtPluginEditorTrustedSha256).trim();
        String trustedClassName = textOf(tab.edtPluginEditorTrustedClassName).trim();
        String trustedMethodName = textOf(tab.edtPluginEditorTrustedMethodName).trim();
        action.put("id", id);
        action.put("title", title);
        action.put("type", type);
        if (TextUtils.isEmpty(action.optString("description", "").trim())) {
            action.put("description", defaultEditorActionDescription(type));
        }
        action.remove("handler");
        action.remove("target");
        action.remove("command");
        action.remove("script");
        if (isScriptActionType(type)) {
            if (looksLikePluginScriptPath(value)) action.put("script", value);
            else action.put("command", value);
        } else if (isNativeActionType(type)) {
            action.put("handler", value);
        } else {
            action.put("target", value);
        }
        action.remove("display");
        if ("default".equals(presentation)) action.remove("presentation");
        else action.put("presentation", presentation);
        if ("default".equals(syntax)) action.remove("syntax");
        else action.put("syntax", syntax);
        if ("inherit".equals(windowStyle)) action.remove("windowStyle");
        else action.put("windowStyle", windowStyle);
        if ("inherit".equals(windowFit)) action.remove("windowFit");
        else action.put("windowFit", windowFit);
        if (requires.length() == 0) action.remove("requires");
        else action.put("requires", requires);
        action.remove("sha256");
        action.remove("className");
        action.remove("entryClass");
        action.remove("class");
        action.remove("methodName");
        action.remove("method");
        if (!TextUtils.isEmpty(trustedSha256)) action.put("sha256", trustedSha256.toLowerCase(Locale.US));
        if (!TextUtils.isEmpty(trustedClassName)) action.put("className", trustedClassName);
        if (!TextUtils.isEmpty(trustedMethodName)) action.put("methodName", trustedMethodName);
        return action;
    }

    private String defaultEditorActionDescription(String type) {
        if ("shell".equals(type) || "script".equals(type)) {
            return "Controlled shell or script action routed through the PermsTest plugin runtime.";
        }
        if ("native".equals(type) || "trusted_native".equals(type) || "trusted_dex".equals(type)) {
            return "Advanced plugin action gated by the PermsTest plugin API.";
        }
        return "Declarative UI entry rendered by the PermsTest plugin runtime.";
    }

    private void selectEditorAction(int index) {
        editorActionStore.select(index);
        renderEditorActions();
        loadSelectedEditorActionFields();
        updatePluginEditorActionState();
    }

    private void loadSelectedEditorActionFields() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        JSONObject action = editorActionStore.selectedCopy();
        editorActionFieldSyncing = true;
        try {
            String runtime = textOr(tab.ddPluginEditorRuntime, "declarative").trim();
            if (action == null) {
                tab.edtPluginEditorActionId.setText("");
                tab.edtPluginEditorActionTitle.setText("", false);
                tab.ddPluginEditorActionType.setText(actionTypeForRuntime(runtime), false);
                tab.ddPluginEditorActionPresentation.setText("default", false);
                tab.ddPluginEditorActionSyntax.setText("default", false);
                tab.ddPluginEditorActionWindowStyle.setText("inherit", false);
                tab.ddPluginEditorActionWindowFit.setText("inherit", false);
                tab.edtPluginEditorActionRequires.setText("");
                tab.edtPluginEditorTrustedSha256.setText("");
                tab.edtPluginEditorTrustedClassName.setText("");
                tab.edtPluginEditorTrustedMethodName.setText("");
                refreshEditorHandlerSuggestions(tab);
                tab.edtPluginEditorHandler.setText(defaultEditorHandlerSuggestion(
                        handlerSuggestionRuntime(actionTypeForRuntime(runtime), runtime)), false);
                return;
            }
            String type = action.optString("type", actionTypeForRuntime(runtime)).trim();
            tab.edtPluginEditorActionId.setText(action.optString("id", ""));
            tab.edtPluginEditorActionTitle.setText(action.optString("title", action.optString("id", "")), false);
            tab.ddPluginEditorActionType.setText(type, false);
            tab.ddPluginEditorActionPresentation.setText(firstNonEmpty(
                    action.optString("presentation", ""), action.optString("display", ""), "default"), false);
            tab.ddPluginEditorActionSyntax.setText(firstNonEmpty(action.optString("syntax", ""), "default"), false);
            tab.ddPluginEditorActionWindowStyle.setText(firstNonEmpty(action.optString("windowStyle", ""), "inherit"), false);
            tab.ddPluginEditorActionWindowFit.setText(firstNonEmpty(action.optString("windowFit", ""), "inherit"), false);
            tab.edtPluginEditorActionRequires.setText(capabilityCsvFromArray(action.optJSONArray("requires")));
            tab.edtPluginEditorTrustedSha256.setText(action.optString("sha256", ""));
            tab.edtPluginEditorTrustedClassName.setText(firstNonEmpty(
                    action.optString("className", ""), action.optString("entryClass", ""), action.optString("class", "")));
            tab.edtPluginEditorTrustedMethodName.setText(firstNonEmpty(
                    action.optString("methodName", ""), action.optString("method", "")));
            refreshEditorHandlerSuggestions(tab);
            tab.edtPluginEditorHandler.setText(firstNonEmpty(
                    action.optString("target", ""),
                    action.optString("handler", ""),
                    action.optString("script", ""),
                    action.optString("command", "")), false);
        } finally {
            editorActionFieldSyncing = false;
        }
    }

    private void renderEditorActions() {
        TabPluginsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.llPluginEditorActionsList == null) return;
        tab.llPluginEditorActionsList.removeAllViews();
        List<JSONObject> actions = editorActionStore.snapshot();
        if (actions.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No structured actions loaded. Add an action or load the current plugin.json action array.");
            empty.setTextSize(12f);
            empty.setPadding(dp(8), dp(10), dp(8), dp(10));
            tab.llPluginEditorActionsList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (int i = 0; i < actions.size(); i++) {
                final int index = i;
                JSONObject action = actions.get(i);
                boolean selected = index == editorActionStore.selectedIndex();
                MaterialCardView card = new MaterialCardView(activity);
                card.setUseCompatPadding(false);
                card.setCardElevation(0f);
                card.setRadius(dp(8));
                card.setStrokeWidth(dp(selected ? 2 : 1));
                card.setStrokeColor(pluginRowStroke(activity, false));
                if (selected) card.setCardBackgroundColor(pluginRowFill(activity, false));
                card.setClickable(true);
                card.setFocusable(true);
                card.setContentDescription("Select plugin action " + action.optString("id", String.valueOf(index + 1)));
                card.setOnClickListener(v -> selectEditorAction(index));

                LinearLayout box = new LinearLayout(activity);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(10), dp(7), dp(10), dp(7));
                card.addView(box, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView title = new TextView(activity);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setText((index + 1) + ". " + action.optString("title", action.optString("id", "action"))
                        + "  [" + action.optString("id", "action") + "]");
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(title, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView details = new TextView(activity);
                StringBuilder detailText = new StringBuilder();
                detailText.append(action.optString("type", "native")).append(" • ").append(firstNonEmpty(
                        action.optString("target", ""), action.optString("handler", ""),
                        action.optString("script", ""), action.optString("command", ""), "no target"));
                String presentation = firstNonEmpty(action.optString("presentation", ""), action.optString("display", ""));
                if (!TextUtils.isEmpty(presentation)) detailText.append(" • ").append(presentation);
                if (!TextUtils.isEmpty(action.optString("syntax", ""))) detailText.append(" • ").append(action.optString("syntax", ""));
                if (!TextUtils.isEmpty(action.optString("windowStyle", ""))) detailText.append(" • ").append(action.optString("windowStyle", ""));
                if (!TextUtils.isEmpty(action.optString("windowFit", ""))) detailText.append("/").append(action.optString("windowFit", ""));
                details.setText(detailText.toString());
                details.setTextSize(12f);
                details.setSingleLine(true);
                details.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(details, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(6);
                tab.llPluginEditorActionsList.addView(card, lp);
            }
        }
        if (tab.txtPluginEditorActionSelection != null) {
            int index = editorActionStore.selectedIndex();
            String dirty = editorActionListDirty ? " Structured changes are pending; tap Build JSON before Validate, Save, or Package." : "";
            tab.txtPluginEditorActionSelection.setText(index >= 0
                    ? "Selected action " + (index + 1) + " of " + editorActionStore.size() + ". Edit the fields below, then tap Update." + dirty
                    : "No action selected." + dirty);
        }
        updatePluginEditorActionState();
    }

    private void loadActionsFromJsonText(String json) {
        try {
            JSONObject root = new JSONObject(json);
            editorActionStore.load(root.optJSONArray("actions"));
        } catch (Throwable ignored) {
            editorActionStore.clear();
        }
        editorActionListDirty = false;
        renderEditorActions();
        loadSelectedEditorActionFields();
    }

    private void loadEditorUiControlsFromJson() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        loadUiControlsFromJsonText(textOf(tab.edtPluginEditorUiJson));
        setEditorStatus("Loaded " + editorUiControlStore.size() + " top-level control(s) from ui.json. Structured changes do not update raw ui.json until Build UI is used.");
        debug("editor-ui", "loaded controls=" + editorUiControlStore.size());
    }

    private void loadUiControlsFromJsonText(String json) {
        try {
            JSONObject root = new JSONObject(json);
            editorUiControlStore.load(root.optJSONArray("controls"));
        } catch (Throwable ignored) {
            editorUiControlStore.clear();
        }
        editorUiListDirty = false;
        renderEditorUiControls();
        loadSelectedEditorUiControlFields();
        loadEditorUiNestedItemsForSelected(false);
    }

    private void addEditorUiControl() {
        try {
            JSONObject control = buildEditorUiControlFromFields(null);
            editorUiControlStore.add(control);
            editorUiListDirty = true;
            renderEditorUiControls();
            loadSelectedEditorUiControlFields();
            loadEditorUiNestedItemsForSelected(false);
            setEditorStatus("Added UI control " + uiControlDisplayName(control) + ". Tap Build UI when the structured control list is ready.");
            debug("editor-ui", "added type=" + control.optString("type", "label") + ", id=" + control.optString("id", ""));
        } catch (Throwable t) {
            setEditorStatus("Add UI control failed: " + safeMessage(t));
        }
    }

    private void updateSelectedEditorUiControl() {
        try {
            JSONObject control = buildEditorUiControlFromFields(editorUiControlStore.selectedCopy());
            if (!editorUiControlStore.updateSelected(control)) {
                setEditorStatus("Select a UI control before updating it.");
                return;
            }
            editorUiListDirty = true;
            renderEditorUiControls();
            loadSelectedEditorUiControlFields();
            loadEditorUiNestedItemsForSelected(false);
            setEditorStatus("Updated UI control " + uiControlDisplayName(control) + ". Tap Build UI to update ui.json.");
            debug("editor-ui", "updated type=" + control.optString("type", "label") + ", id=" + control.optString("id", ""));
        } catch (Throwable t) {
            setEditorStatus("Update UI control failed: " + safeMessage(t));
        }
    }

    private void removeSelectedEditorUiControl() {
        JSONObject selected = editorUiControlStore.selectedCopy();
        String name = uiControlDisplayName(selected);
        if (!editorUiControlStore.removeSelected()) return;
        editorUiListDirty = true;
        renderEditorUiControls();
        loadSelectedEditorUiControlFields();
        loadEditorUiNestedItemsForSelected(false);
        setEditorStatus("Removed UI control " + name + ". Tap Build UI to update ui.json.");
        debug("editor-ui", "removed " + name);
    }

    private void moveSelectedEditorUiControl(int delta) {
        if (!editorUiControlStore.moveSelected(delta)) return;
        editorUiListDirty = true;
        renderEditorUiControls();
        loadSelectedEditorUiControlFields();
        loadEditorUiNestedItemsForSelected(false);
        setEditorStatus("Moved selected UI control " + (delta < 0 ? "up" : "down") + ". Tap Build UI to update ui.json.");
        debug("editor-ui", "moved selected delta=" + delta + ", index=" + editorUiControlStore.selectedIndex());
    }

    private void selectEditorUiControl(int index) {
        if (editorUiNestedFieldsDirty) {
            setEditorStatus("Tap Add or Update in Selected Container Contents, or Reload Contents to discard the pending nested-item fields before selecting another top-level control.");
            updatePluginEditorActionState();
            return;
        }
        editorUiControlStore.select(index);
        renderEditorUiControls();
        loadSelectedEditorUiControlFields();
        loadEditorUiNestedItemsForSelected(false);
        updatePluginEditorActionState();
    }

    private void loadEditorUiNestedItemsFromSelected() {
        loadEditorUiNestedItemsForSelected(true);
    }

    private void loadEditorUiNestedItemsForSelected(boolean showStatus) {
        JSONObject parent = editorUiControlStore.selectedCopy();
        String type = parent == null ? "" : parent.optString("type", "");
        editorUiNestedStore.clear();
        if (isUiContainerType(type)) {
            editorUiNestedStore.load(parent.optJSONArray(uiContainerArrayKey(type)));
        }
        editorUiNestedFieldsDirty = false;
        renderEditorUiNestedItems();
        loadSelectedEditorUiNestedItemFields();
        if (showStatus) {
            if (isUiContainerType(type)) {
                setEditorStatus("Loaded " + editorUiNestedStore.size() + " item(s) from selected " + type
                        + ". Add, update, remove, or reorder items here; Build UI applies the selected container to raw ui.json.");
            } else {
                setEditorStatus("Select a top-level group, section, or buttons control before loading its contents.");
            }
        }
    }

    private void addEditorUiNestedItem() {
        try {
            JSONObject item = buildEditorUiNestedItemFromFields(null);
            editorUiNestedStore.add(item);
            commitEditorUiNestedItemsToSelected();
            loadSelectedEditorUiNestedItemFields();
            setEditorStatus("Added nested item " + uiNestedItemDisplayName(item) + ". Tap Build UI when the structured UI is ready.");
            debug("editor-ui-nested", "added parent=" + selectedUiContainerType() + ", item=" + uiNestedItemDisplayName(item));
        } catch (Throwable t) {
            setEditorStatus("Add nested item failed: " + safeMessage(t));
        }
    }

    private void updateSelectedEditorUiNestedItem() {
        try {
            JSONObject item = buildEditorUiNestedItemFromFields(editorUiNestedStore.selectedCopy());
            if (!editorUiNestedStore.updateSelected(item)) {
                setEditorStatus("Select a nested item before updating it.");
                return;
            }
            commitEditorUiNestedItemsToSelected();
            loadSelectedEditorUiNestedItemFields();
            setEditorStatus("Updated nested item " + uiNestedItemDisplayName(item) + ". Tap Build UI when the structured UI is ready.");
            debug("editor-ui-nested", "updated parent=" + selectedUiContainerType() + ", item=" + uiNestedItemDisplayName(item));
        } catch (Throwable t) {
            setEditorStatus("Update nested item failed: " + safeMessage(t));
        }
    }

    private void removeSelectedEditorUiNestedItem() {
        JSONObject selected = editorUiNestedStore.selectedCopy();
        String name = uiNestedItemDisplayName(selected);
        if (!editorUiNestedStore.removeSelected()) return;
        commitEditorUiNestedItemsToSelected();
        loadSelectedEditorUiNestedItemFields();
        setEditorStatus("Removed nested item " + name + ". Tap Build UI when the structured UI is ready.");
        debug("editor-ui-nested", "removed parent=" + selectedUiContainerType() + ", item=" + name);
    }

    private void moveSelectedEditorUiNestedItem(int delta) {
        if (!editorUiNestedStore.moveSelected(delta)) return;
        commitEditorUiNestedItemsToSelected();
        loadSelectedEditorUiNestedItemFields();
        setEditorStatus("Moved selected nested item " + (delta < 0 ? "up" : "down") + ". Tap Build UI when the structured UI is ready.");
        debug("editor-ui-nested", "moved parent=" + selectedUiContainerType() + ", delta=" + delta
                + ", index=" + editorUiNestedStore.selectedIndex());
    }

    private void selectEditorUiNestedItem(int index) {
        if (editorUiNestedFieldsDirty) {
            setEditorStatus("Tap Add or Update, or Reload Contents to discard the pending nested-item fields before selecting another nested item.");
            updatePluginEditorActionState();
            return;
        }
        editorUiNestedStore.select(index);
        renderEditorUiNestedItems();
        loadSelectedEditorUiNestedItemFields();
        updatePluginEditorActionState();
    }

    private void commitEditorUiNestedItemsToSelected() {
        JSONObject parent = editorUiControlStore.selectedCopy();
        String type = parent == null ? "" : parent.optString("type", "");
        if (!isUiContainerType(type)) throw new IllegalStateException("Select a group, section, or buttons control first.");
        try {
            parent.put(uiContainerArrayKey(type), editorUiNestedStore.toJsonArray());
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to update selected container: " + safeMessage(t));
        }
        if (!editorUiControlStore.updateSelected(parent)) throw new IllegalStateException("Selected container is no longer available.");
        editorUiNestedFieldsDirty = false;
        editorUiListDirty = true;
        renderEditorUiControls();
        renderEditorUiNestedItems();
    }

    private void loadSelectedEditorUiNestedItemFields() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        JSONObject item = editorUiNestedStore.selectedCopy();
        boolean buttonRow = "buttons".equals(selectedUiContainerType());
        editorUiNestedFieldSyncing = true;
        try {
            String type = buttonRow ? "button" : (item == null ? "label" : item.optString("type", "label"));
            tab.ddPluginEditorUiNestedType.setText(type, false);
            tab.edtPluginEditorUiNestedId.setText(buttonRow || item == null ? "" : item.optString("id", ""));
            String label = item == null ? "" : ("button".equals(type)
                    ? item.optString("text", "")
                    : ("label".equals(type) || "text".equals(type) ? "" : item.optString("label", "")));
            tab.edtPluginEditorUiNestedLabel.setText(label);
            tab.edtPluginEditorUiNestedValue.setText(buttonRow || item == null ? "" : uiControlValue(item));
            JSONObject action = firstUiControlAction(item);
            String actionType = action == null ? "none" : action.optString("type", "none");
            tab.ddPluginEditorUiNestedActionType.setText(actionType, false);
            tab.edtPluginEditorUiNestedActionData.setText(uiActionData(action), false);
            tab.edtPluginEditorUiNestedActionOutput.setText(uiActionOutput(action));
            tab.edtPluginEditorUiNestedActionOptionsJson.setText(uiActionOptionsJson(action));
            refreshEditorUiNestedActionDataSuggestions(tab);
            editorUiNestedFieldsDirty = false;
        } finally {
            editorUiNestedFieldSyncing = false;
        }
        updateEditorUiNestedFieldAvailability(tab);
    }

    private void renderEditorUiNestedItems() {
        TabPluginsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.llPluginEditorUiNestedItemsList == null) return;
        tab.llPluginEditorUiNestedItemsList.removeAllViews();
        String containerType = selectedUiContainerType();
        List<JSONObject> items = editorUiNestedStore.snapshot();
        if (!isUiContainerType(containerType)) {
            TextView empty = new TextView(activity);
            empty.setText("Select a top-level group, section, or buttons control to edit its contents.");
            empty.setTextSize(12f);
            empty.setPadding(dp(8), dp(10), dp(8), dp(10));
            tab.llPluginEditorUiNestedItemsList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (items.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("The selected " + containerType + " has no structured items. Fill the fields below and tap Add.");
            empty.setTextSize(12f);
            empty.setPadding(dp(8), dp(10), dp(8), dp(10));
            tab.llPluginEditorUiNestedItemsList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (int i = 0; i < items.size(); i++) {
                final int index = i;
                JSONObject item = items.get(i);
                boolean selected = index == editorUiNestedStore.selectedIndex();
                MaterialCardView card = new MaterialCardView(activity);
                card.setUseCompatPadding(false);
                card.setCardElevation(0f);
                card.setRadius(dp(8));
                card.setStrokeWidth(dp(selected ? 2 : 1));
                card.setStrokeColor(pluginRowStroke(activity, false));
                if (selected) card.setCardBackgroundColor(pluginRowFill(activity, false));
                card.setClickable(true);
                card.setFocusable(true);
                card.setContentDescription("Select nested UI item " + uiNestedItemDisplayName(item));
                card.setOnClickListener(v -> selectEditorUiNestedItem(index));

                LinearLayout box = new LinearLayout(activity);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(10), dp(7), dp(10), dp(7));
                card.addView(box, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView title = new TextView(activity);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setText((index + 1) + ". " + uiNestedItemDisplayName(item));
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(title, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                JSONObject action = firstUiControlAction(item);
                String itemType = "buttons".equals(containerType) ? "button-row item" : item.optString("type", "label");
                String detailsText = itemType + (action == null ? "" : " • " + action.optString("type", "action"));
                TextView details = new TextView(activity);
                details.setText(detailsText);
                details.setTextSize(12f);
                details.setSingleLine(true);
                details.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(details, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(6);
                tab.llPluginEditorUiNestedItemsList.addView(card, lp);
            }
        }
        if (tab.txtPluginEditorUiNestedSelection != null) {
            int index = editorUiNestedStore.selectedIndex();
            String pending = editorUiNestedFieldsDirty ? " Nested item fields have pending changes; tap Add or Update, or Reload Contents to discard them." : "";
            if (!isUiContainerType(containerType)) {
                tab.txtPluginEditorUiNestedSelection.setText("Select a group, section, or buttons control to edit its contents." + pending);
            } else if (index >= 0) {
                tab.txtPluginEditorUiNestedSelection.setText("Selected nested item " + (index + 1) + " of " + editorUiNestedStore.size()
                        + " inside " + containerType + ". Edit the fields below, then tap Update." + pending);
            } else {
                tab.txtPluginEditorUiNestedSelection.setText("No nested item selected inside " + containerType + ". Fill the fields below, then tap Add." + pending);
            }
        }
        updatePluginEditorActionState();
    }

    private String selectedUiContainerType() {
        JSONObject selected = editorUiControlStore.selectedCopy();
        return selected == null ? "" : selected.optString("type", "").trim();
    }

    private static boolean isUiContainerType(String type) {
        return "group".equals(type) || "section".equals(type) || "buttons".equals(type);
    }

    private static String uiContainerArrayKey(String type) {
        return "buttons".equals(type) ? "buttons" : "controls";
    }

    private static String uiNestedItemDisplayName(JSONObject item) {
        if (item == null) return "item";
        return firstNonEmpty(item.optString("id", ""), item.optString("label", ""),
                item.optString("text", ""), item.optString("type", "item"));
    }

    private boolean editorUiNestedStoreContainsId(String id, int ignoreIndex) {
        if (TextUtils.isEmpty(id)) return false;
        List<JSONObject> nested = editorUiNestedStore.snapshot();
        for (int i = 0; i < nested.size(); i++) {
            if (i == ignoreIndex) continue;
            if (id.equals(nested.get(i).optString("id", "").trim())) return true;
        }
        return false;
    }

    private boolean editorUiNestedControlIdExists(String id, int ignoreIndex) {
        if (TextUtils.isEmpty(id)) return false;
        List<JSONObject> topLevel = editorUiControlStore.snapshot();
        for (JSONObject control : topLevel) {
            if (id.equals(control.optString("id", "").trim())) return true;
        }
        return editorUiNestedStoreContainsId(id, ignoreIndex);
    }

    private JSONObject buildEditorUiControlFromFields(JSONObject existing) throws Exception {
        TabPluginsBinding tab = tab();
        if (tab == null) throw new IllegalStateException("Plugin Editor is not ready.");
        return buildEditorUiControl(existing,
                textOr(tab.ddPluginEditorUiType, "label").trim(),
                textOf(tab.edtPluginEditorUiId).trim(),
                textOf(tab.edtPluginEditorUiLabel).trim(),
                textOf(tab.edtPluginEditorUiValue).trim(),
                textOr(tab.ddPluginEditorUiActionType, "none").trim(),
                textOf(tab.edtPluginEditorUiActionData).trim(),
                textOf(tab.edtPluginEditorUiActionOutput).trim(),
                textOf(tab.edtPluginEditorUiActionOptionsJson), false);
    }

    private JSONObject buildEditorUiNestedItemFromFields(JSONObject existing) throws Exception {
        TabPluginsBinding tab = tab();
        if (tab == null) throw new IllegalStateException("Plugin Editor is not ready.");
        boolean buttonRowItem = "buttons".equals(selectedUiContainerType());
        return buildEditorUiControl(existing,
                buttonRowItem ? "button" : textOr(tab.ddPluginEditorUiNestedType, "label").trim(),
                buttonRowItem ? "" : textOf(tab.edtPluginEditorUiNestedId).trim(),
                textOf(tab.edtPluginEditorUiNestedLabel).trim(),
                buttonRowItem ? "" : textOf(tab.edtPluginEditorUiNestedValue).trim(),
                textOr(tab.ddPluginEditorUiNestedActionType, "none").trim(),
                textOf(tab.edtPluginEditorUiNestedActionData).trim(),
                textOf(tab.edtPluginEditorUiNestedActionOutput).trim(),
                textOf(tab.edtPluginEditorUiNestedActionOptionsJson), buttonRowItem);
    }

    private JSONObject buildEditorUiControl(JSONObject existing,
                                             String type,
                                             String id,
                                             String label,
                                             String value,
                                             String actionType,
                                             String actionData,
                                             String actionOutput,
                                             String actionOptionsJson,
                                             boolean buttonRowItem) throws Exception {
        JSONObject control = existing == null ? new JSONObject() : new JSONObject(existing.toString());
        JSONArray preservedControls = control.optJSONArray("controls");
        JSONArray preservedButtons = control.optJSONArray("buttons");

        control.put("type", type);
        if (TextUtils.isEmpty(id)) control.remove("id"); else control.put("id", id);
        control.remove("label");
        control.remove("text");
        control.remove("default");
        control.remove("values");
        control.remove("controls");
        control.remove("buttons");
        control.remove("action");
        control.remove("onChange");

        if ("label".equals(type) || "text".equals(type)) {
            control.put("text", firstNonEmpty(value, label));
        } else if ("button".equals(type)) {
            control.put("text", label);
        } else if ("output".equals(type)) {
            if (!TextUtils.isEmpty(label)) control.put("label", label);
            if (!TextUtils.isEmpty(value)) control.put("text", value);
        } else if ("input".equals(type) || "multiline".equals(type)) {
            if (!TextUtils.isEmpty(label)) control.put("label", label);
            if (!TextUtils.isEmpty(value)) control.put("default", value);
        } else if ("checkbox".equals(type)) {
            if (!TextUtils.isEmpty(label)) control.put("label", label);
            if (!TextUtils.isEmpty(value)) control.put("default", Boolean.parseBoolean(value));
        } else if ("dropdown".equals(type)) {
            if (!TextUtils.isEmpty(label)) control.put("label", label);
            JSONArray values = splitUiValues(value);
            control.put("values", values);
            String currentDefault = existing == null ? "" : existing.optString("default", "");
            if (TextUtils.isEmpty(currentDefault) || !jsonStringArrayContains(values, currentDefault)) {
                currentDefault = values.length() > 0 ? values.optString(0, "") : "";
            }
            control.put("default", currentDefault);
        } else if ("group".equals(type) || "section".equals(type)) {
            if (!TextUtils.isEmpty(label)) control.put("label", label);
            control.put("controls", preservedControls == null ? new JSONArray() : preservedControls);
        } else if ("buttons".equals(type)) {
            control.put("buttons", preservedButtons == null ? new JSONArray() : preservedButtons);
        } else if (!TextUtils.isEmpty(label)) {
            control.put("label", label);
        }

        if (!"none".equals(actionType) && supportsUiControlAction(type)) {
            JSONObject action = parseEditorUiActionOptions(actionOptionsJson);
            action.put("type", actionType);
            action.remove("message");
            action.remove("target");
            action.remove("value");
            action.remove("command");
            action.remove("output");
            action.remove("name");
            if ("toast".equals(actionType)) {
                action.put("message", actionData);
            } else if ("setText".equals(actionType) || "appendText".equals(actionType)) {
                action.put("target", actionOutput);
                action.put("value", actionData);
            } else if ("clear".equals(actionType) || "backspace".equals(actionType)) {
                action.put("target", actionOutput);
            } else if ("shell".equals(actionType)) {
                action.put("command", actionData);
                if (!TextUtils.isEmpty(actionOutput)) action.put("output", actionOutput);
            } else if ("api".equals(actionType)) {
                action.put("name", actionData);
                if (!TextUtils.isEmpty(actionOutput)) action.put("output", actionOutput);
            }
            control.put(uiControlActionKey(type), action);
        }
        if (buttonRowItem) {
            control.remove("type");
            control.remove("id");
            control.remove("label");
            control.remove("default");
            control.remove("values");
            control.remove("controls");
            control.remove("buttons");
            control.remove("onChange");
        }
        return control;
    }

    private void loadSelectedEditorUiControlFields() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        JSONObject control = editorUiControlStore.selectedCopy();
        editorUiFieldSyncing = true;
        try {
            if (control == null) {
                tab.ddPluginEditorUiType.setText("label", false);
                tab.edtPluginEditorUiId.setText("");
                tab.edtPluginEditorUiLabel.setText("");
                tab.edtPluginEditorUiValue.setText("");
                tab.ddPluginEditorUiActionType.setText("none", false);
                tab.edtPluginEditorUiActionData.setText("", false);
                tab.edtPluginEditorUiActionOutput.setText("");
                tab.edtPluginEditorUiActionOptionsJson.setText("");
                refreshEditorUiActionDataSuggestions(tab);
                return;
            }
            String type = control.optString("type", "label");
            tab.ddPluginEditorUiType.setText(type, false);
            tab.edtPluginEditorUiId.setText(control.optString("id", ""));
            String label = "button".equals(type)
                    ? control.optString("text", "")
                    : ("label".equals(type) || "text".equals(type) ? "" : control.optString("label", ""));
            tab.edtPluginEditorUiLabel.setText(label);
            tab.edtPluginEditorUiValue.setText(uiControlValue(control));
            JSONObject action = firstUiControlAction(control);
            String actionType = action == null ? "none" : action.optString("type", "none");
            tab.ddPluginEditorUiActionType.setText(actionType, false);
            tab.edtPluginEditorUiActionData.setText(uiActionData(action), false);
            tab.edtPluginEditorUiActionOutput.setText(uiActionOutput(action));
            tab.edtPluginEditorUiActionOptionsJson.setText(uiActionOptionsJson(action));
            refreshEditorUiActionDataSuggestions(tab);
        } finally {
            editorUiFieldSyncing = false;
        }
    }

    private void renderEditorUiControls() {
        TabPluginsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.llPluginEditorUiControlsList == null) return;
        tab.llPluginEditorUiControlsList.removeAllViews();
        List<JSONObject> controls = editorUiControlStore.snapshot();
        if (controls.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No structured UI controls loaded. Add a control or load the current ui.json control array.");
            empty.setTextSize(12f);
            empty.setPadding(dp(8), dp(10), dp(8), dp(10));
            tab.llPluginEditorUiControlsList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (int i = 0; i < controls.size(); i++) {
                final int index = i;
                JSONObject control = controls.get(i);
                boolean selected = index == editorUiControlStore.selectedIndex();
                MaterialCardView card = new MaterialCardView(activity);
                card.setUseCompatPadding(false);
                card.setCardElevation(0f);
                card.setRadius(dp(8));
                card.setStrokeWidth(dp(selected ? 2 : 1));
                card.setStrokeColor(pluginRowStroke(activity, false));
                if (selected) card.setCardBackgroundColor(pluginRowFill(activity, false));
                card.setClickable(true);
                card.setFocusable(true);
                card.setContentDescription("Select declarative UI control " + uiControlDisplayName(control));
                card.setOnClickListener(v -> selectEditorUiControl(index));

                LinearLayout box = new LinearLayout(activity);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(10), dp(7), dp(10), dp(7));
                card.addView(box, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView title = new TextView(activity);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setText((index + 1) + ". " + uiControlDisplayName(control));
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(title, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                JSONObject action = firstUiControlAction(control);
                String controlType = control.optString("type", "label");
                JSONArray containerItems = isUiContainerType(controlType)
                        ? control.optJSONArray(uiContainerArrayKey(controlType)) : null;
                String detailsText = controlType
                        + (containerItems == null ? "" : " • " + containerItems.length()
                        + ("buttons".equals(controlType) ? " button(s)" : " child control(s)"))
                        + (action == null ? "" : " • " + action.optString("type", "action"));
                TextView details = new TextView(activity);
                details.setText(detailsText);
                details.setTextSize(12f);
                details.setSingleLine(true);
                details.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(details, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(6);
                tab.llPluginEditorUiControlsList.addView(card, lp);
            }
        }
        if (tab.txtPluginEditorUiSelection != null) {
            int index = editorUiControlStore.selectedIndex();
            String dirty = editorUiListDirty ? " Structured changes are pending; tap Build UI before Validate, Save, or Package." : "";
            tab.txtPluginEditorUiSelection.setText(index >= 0
                    ? "Selected UI control " + (index + 1) + " of " + editorUiControlStore.size() + ". Edit the fields below, then tap Update." + dirty
                    : "No UI control selected." + dirty);
        }
        updatePluginEditorActionState();
    }

    private static String uiControlDisplayName(JSONObject control) {
        if (control == null) return "control";
        return firstNonEmpty(control.optString("id", ""), control.optString("label", ""),
                control.optString("text", ""), control.optString("type", "control"));
    }

    private static JSONObject firstUiControlAction(JSONObject control) {
        if (control == null) return null;
        JSONObject action = control.optJSONObject("action");
        return action == null ? control.optJSONObject("onChange") : action;
    }

    private static String uiControlActionKey(String type) {
        return "input".equals(type) || "multiline".equals(type) || "dropdown".equals(type) ? "onChange" : "action";
    }

    private static boolean supportsUiControlAction(String type) {
        return "button".equals(type) || "input".equals(type) || "multiline".equals(type) || "dropdown".equals(type);
    }

    private static String uiControlValue(JSONObject control) {
        if (control == null) return "";
        String type = control.optString("type", "label");
        if ("dropdown".equals(type)) {
            JSONArray values = control.optJSONArray("values");
            if (values == null) return "";
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < values.length(); i++) {
                if (out.length() > 0) out.append(" | ");
                out.append(values.optString(i, ""));
            }
            return out.toString();
        }
        if ("checkbox".equals(type)) return String.valueOf(control.optBoolean("default", false));
        if ("input".equals(type) || "multiline".equals(type)) return control.optString("default", "");
        if ("label".equals(type) || "text".equals(type) || "output".equals(type)) return control.optString("text", "");
        return "";
    }

    private static JSONObject parseEditorUiActionOptions(String json) throws Exception {
        if (TextUtils.isEmpty(json == null ? "" : json.trim())) return new JSONObject();
        JSONObject options = new JSONObject(json);
        options.remove("type");
        return options;
    }

    private static String uiActionOptionsJson(JSONObject action) {
        if (action == null) return "";
        try {
            JSONObject options = new JSONObject(action.toString());
            options.remove("type");
            options.remove("message");
            options.remove("target");
            options.remove("value");
            options.remove("command");
            options.remove("output");
            options.remove("name");
            return options.length() == 0 ? "" : options.toString(2) + "\n";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String uiActionData(JSONObject action) {
        if (action == null) return "";
        return firstNonEmpty(action.optString("message", ""), action.optString("value", ""),
                action.optString("command", ""), action.optString("name", ""));
    }

    private static String uiActionOutput(JSONObject action) {
        if (action == null) return "";
        return firstNonEmpty(action.optString("target", ""), action.optString("output", ""));
    }

    private static boolean jsonStringArrayContains(JSONArray values, String expected) {
        if (values == null || TextUtils.isEmpty(expected)) return false;
        for (int i = 0; i < values.length(); i++) {
            if (expected.equals(values.optString(i, ""))) return true;
        }
        return false;
    }

    private static JSONArray splitUiValues(String value) {
        JSONArray array = new JSONArray();
        if (TextUtils.isEmpty(value)) return array;
        String[] parts = value.split("\\|");
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (!TextUtils.isEmpty(trimmed)) array.put(trimmed);
        }
        return array;
    }

    private void newPluginTemplate() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        editorPluginId = "";
        clearPendingEditorIcon();
        clearPendingEditorScripts();
        clearEditorAssetState();
        tab.edtPluginEditorId.setText("");
        tab.edtPluginEditorName.setText("");
        tab.edtPluginEditorVersion.setText("1.0");
        tab.edtPluginEditorAuthor.setText("PermsTest User");
        tab.edtPluginEditorIcon.setText("");
        tab.edtPluginEditorDescription.setText("Custom declarative PermsTest plugin.");
        tab.ddPluginEditorRuntime.setText("declarative", false);
        tab.ddPluginEditorWindowStyle.setText("compact", false);
        tab.ddPluginEditorWindowFit.setText("current", false);
        refreshEditorHandlerSuggestions(tab);
        tab.edtPluginEditorActionId.setText("open_plugin_ui");
        tab.edtPluginEditorActionTitle.setText("Open Plugin UI", false);
        tab.ddPluginEditorActionType.setText("declarative_ui", false);
        tab.ddPluginEditorActionPresentation.setText("default", false);
        tab.ddPluginEditorActionSyntax.setText("default", false);
        tab.ddPluginEditorActionWindowStyle.setText("inherit", false);
        tab.ddPluginEditorActionWindowFit.setText("inherit", false);
        tab.edtPluginEditorActionRequires.setText("");
        tab.edtPluginEditorTrustedSha256.setText("");
        tab.edtPluginEditorTrustedClassName.setText("");
        tab.edtPluginEditorTrustedMethodName.setText("");
        tab.edtPluginEditorHandler.setText("ui.json", false);
        editorActionStore.clear();
        try { editorActionStore.add(buildEditorActionFromFields(null)); } catch (Throwable ignored) {}
        renderEditorActions();
        loadSelectedEditorActionFields();
        buildEditorJsonFromFields();
        editorActionListDirty = false;
        String starterUi = starterUiJsonString(textOr(tab.edtPluginEditorName, "Plugin UI"), textOf(tab.edtPluginEditorDescription).trim());
        tab.edtPluginEditorUiJson.setText(starterUi);
        loadUiControlsFromJsonText(starterUi);
        editorUiListDirty = false;
        setEditorStatus("New plugin template ready. Enter a unique Plugin ID and Name; icons and managed assets are optional before saving or packaging.");
        updatePluginEditorActionState();
    }

    private void loadPluginIntoEditor(PluginManifest plugin) {
        TabPluginsBinding tab = tab();
        if (tab == null || plugin == null) return;
        editorPluginId = plugin.id;
        clearPendingEditorIcon();
        clearPendingEditorScripts();
        clearEditorAssetState();
        tab.edtPluginEditorId.setText(plugin.id);
        tab.edtPluginEditorName.setText(plugin.name);
        tab.edtPluginEditorVersion.setText(plugin.version);
        tab.edtPluginEditorAuthor.setText(plugin.author);
        tab.edtPluginEditorIcon.setText(plugin.icon);
        tab.edtPluginEditorDescription.setText(plugin.description);
        tab.ddPluginEditorRuntime.setText(TextUtils.isEmpty(plugin.runtime) ? "declarative" : plugin.runtime, false);
        tab.ddPluginEditorWindowStyle.setText(TextUtils.isEmpty(plugin.windowStyle) ? "compact" : plugin.windowStyle, false);
        tab.ddPluginEditorWindowFit.setText(TextUtils.isEmpty(plugin.windowFit) ? "current" : plugin.windowFit, false);
        refreshEditorHandlerSuggestions(tab);
        String json = readPluginJson(plugin);
        if (TextUtils.isEmpty(json)) json = buildEditorJsonString();
        tab.edtPluginEditorJson.setText(json);
        loadActionsFromJsonText(json);
        editorActionListDirty = false;
        String uiJson = readPluginUiJson(plugin);
        tab.edtPluginEditorUiJson.setText(uiJson);
        loadUiControlsFromJsonText(uiJson);
        editorUiListDirty = false;
        loadEditorAssetsFromStaged(false);
        setEditorStatus("Loaded " + plugin.id + ". Structured actions, managed assets, and top-level UI controls are ready; raw plugin.json and ui.json remain available for advanced fields.");
        debug("editor", "loaded plugin id=" + plugin.id);
        updatePluginEditorActionState();
    }

    private void buildEditorJsonFromFields() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        try {
            if (editorActionStore.hasSelection()) {
                editorActionStore.updateSelected(buildEditorActionFromFields(editorActionStore.selectedCopy()));
            } else if (editorActionStore.isEmpty()) {
                editorActionStore.add(buildEditorActionFromFields(null));
            }
            String json = buildEditorJsonString();
            tab.edtPluginEditorJson.setText(json);
            editorActionListDirty = false;
            renderEditorActions();
            setEditorStatus("plugin.json rebuilt from the friendly fields and " + editorActionStore.size() + " structured action(s). Unknown advanced root/action fields were preserved where possible.");
        } catch (Throwable t) {
            setEditorStatus("Build JSON failed: " + safeMessage(t));
        }
    }

    private void validateEditorManifest() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        String json = textOf(tab.edtPluginEditorJson);
        if (TextUtils.isEmpty(json.trim())) {
            json = buildEditorJsonString();
            tab.edtPluginEditorJson.setText(json);
        }
        ValidationResult validation = validatePluginJson(json, true);
        if (!validation.ok) {
            setEditorStatus(validation.message);
            debug("editor", "validate failed: " + validation.message);
            return;
        }
        String ui = textOf(tab.edtPluginEditorUiJson).trim();
        if ("declarative".equals(validation.runtime) && !TextUtils.isEmpty(ui)) {
            String uiProblem = editorUiRuntimeProblem(ui);
            if (!TextUtils.isEmpty(uiProblem)) {
                setEditorStatus(uiProblem);
                debug("editor", "ui.json validate failed: " + uiProblem);
                return;
            }
        }
        setEditorStatus(buildEditorValidationSummary(validation));
        debug("editor", "validate ok id=" + validation.id + ", runtime=" + validation.runtime
                + ", actions=" + validation.actionCount + ", warnings=" + validation.warnings.size());
    }

    private void buildEditorUiJsonFromFields() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        try {
            if (editorUiControlStore.isEmpty() && !editorUiListDirty) {
                String starter = starterUiJsonString(textOr(tab.edtPluginEditorName, "Plugin UI"), textOf(tab.edtPluginEditorDescription).trim());
                try { editorUiControlStore.load(new JSONObject(starter).optJSONArray("controls")); } catch (Throwable ignored) {}
            }
            if (editorUiControlStore.hasSelection()) {
                editorUiControlStore.updateSelected(buildEditorUiControlFromFields(editorUiControlStore.selectedCopy()));
            }
            String json = buildEditorUiJsonString();
            tab.edtPluginEditorUiJson.setText(json);
            editorUiListDirty = false;
            renderEditorUiControls();
            loadEditorUiNestedItemsForSelected(false);
            setEditorStatus("ui.json rebuilt from " + editorUiControlStore.size() + " structured top-level control(s). Unknown root/control fields and nested group/button content were preserved where possible.");
        } catch (Throwable t) {
            setEditorStatus("Build UI failed: " + safeMessage(t));
        }
    }


    private void previewEditorUi() {
        TabPluginsBinding tab = tab();
        if (tab == null) return;
        String uiJson = textOf(tab.edtPluginEditorUiJson).trim();
        String problem = editorUiRuntimeProblem(uiJson);
        if (!TextUtils.isEmpty(problem)) {
            setEditorStatus(problem);
            return;
        }
        boolean[] async = new boolean[]{false};
        boolean shown = DeclarativePluginRuntime.preview(
                pluginRuntimeHost("", async),
                textOf(tab.edtPluginEditorId).trim(),
                textOr(tab.edtPluginEditorName, "Plugin UI"),
                textOf(tab.edtPluginEditorDescription).trim(),
                textOr(tab.ddPluginEditorWindowStyle, "compact"),
                textOr(tab.ddPluginEditorWindowFit, "current"),
                uiJson);
        setEditorStatus(shown
                ? "Opened declarative UI preview from the current ui.json. This does not save, stage, package, or run startup work."
                : "Preview UI failed; check ui.json and the main output log.");
    }

    private String buildEditorUiJsonString() {
        TabPluginsBinding tab = tab();
        try {
            JSONObject root;
            String current = textOf(tab == null ? null : tab.edtPluginEditorUiJson).trim();
            try {
                root = TextUtils.isEmpty(current) ? new JSONObject() : new JSONObject(current);
            } catch (Throwable ignored) {
                root = new JSONObject();
            }
            String title = textOr(tab == null ? null : tab.edtPluginEditorName, "Plugin UI");
            String description = textOf(tab == null ? null : tab.edtPluginEditorDescription).trim();
            if (TextUtils.isEmpty(description)) description = "Generated declarative plugin UI.";
            root.put("title", title);
            root.put("description", description);
            root.put("controls", editorUiControlStore.toJsonArray());
            return root.toString(2) + "\n";
        } catch (Throwable t) {
            return starterUiJsonString("Plugin UI", "Generated declarative plugin UI.");
        }
    }

    private boolean saveEditorUiJson(boolean showStatus) {
        TabPluginsBinding tab = tab();
        if (tab == null) return false;
        String json = textOf(tab.edtPluginEditorJson);
        if (TextUtils.isEmpty(json.trim())) {
            json = buildEditorJsonString();
            tab.edtPluginEditorJson.setText(json);
        }
        ValidationResult validation = validatePluginJson(json, false);
        if (!validation.ok) {
            if (showStatus) setEditorStatus(validation.message);
            return false;
        }
        if (!"declarative".equals(validation.runtime)) {
            if (showStatus) setEditorStatus("ui.json is only used by declarative plugins.");
            return false;
        }
        try {
            File dir = new File(repository().getPluginRoot(), validation.id);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Unable to create " + dir.getAbsolutePath());
            writeUtf8(new File(dir, PluginManifest.FILE_NAME), json);
            copyPendingEditorIconIfAny(validation);
            copyPendingEditorScriptsIfAny(validation, json);
            copyPendingEditorAssetsIfAny(validation);
            writeEditorUiJsonIfPresent(dir, validation, tab);
            applyRemovedEditorAssetsIfAny(validation);
            editorPluginId = validation.id;
            if (showStatus) {
                setEditorStatus("Saved UI file for " + validation.id + ".");
                append("[plugins] Saved plugin UI file: " + validation.id + "\n");
            }
            return true;
        } catch (Throwable t) {
            if (showStatus) setEditorStatus("Save UI failed: " + safeMessage(t));
            return false;
        }
    }

    private String buildEditorJsonString() {
        TabPluginsBinding tab = tab();
        try {
            JSONObject root;
            String current = textOf(tab == null ? null : tab.edtPluginEditorJson).trim();
            try {
                root = TextUtils.isEmpty(current) ? new JSONObject() : new JSONObject(current);
            } catch (Throwable ignored) {
                root = new JSONObject();
            }
            String id = editorText(tab, "");
            if (TextUtils.isEmpty(id)) id = "my_plugin";
            String runtime = textOr(tab == null ? null : tab.ddPluginEditorRuntime, "declarative").trim();
            String windowStyle = textOr(tab == null ? null : tab.ddPluginEditorWindowStyle, "compact").trim();
            String windowFit = textOr(tab == null ? null : tab.ddPluginEditorWindowFit, "current").trim();
            String entry = firstEditorActionValueForRuntime(runtime);
            if (TextUtils.isEmpty(entry) && "declarative".equals(runtime)) entry = "ui.json";

            root.put("schema", PluginManifest.SCHEMA);
            root.put("apiVersion", 1);
            root.put("id", id);
            root.put("name", textOr(tab == null ? null : tab.edtPluginEditorName, id));
            root.put("version", textOr(tab == null ? null : tab.edtPluginEditorVersion, "1.0"));
            root.put("author", textOf(tab == null ? null : tab.edtPluginEditorAuthor).trim());
            root.put("icon", textOf(tab == null ? null : tab.edtPluginEditorIcon).trim());
            root.put("description", textOf(tab == null ? null : tab.edtPluginEditorDescription).trim());
            root.put("runtime", runtime);
            root.put("entry", entry);
            root.put("windowStyle", windowStyle);
            root.put("windowFit", windowFit);
            if (!root.has("comments")) {
                root.put("comments", "Created or edited with the PermsTest Plugin Editor. Declarative plugins normally keep ui.json beside plugin.json.");
            }
            JSONArray actions = editorActionStore.toJsonArray();
            root.put("actions", actions);
            root.put("capabilities", inferredCapabilitiesForActions(actions));
            return root.toString(2) + "\n";
        } catch (Throwable t) {
            return "{}\n";
        }
    }

    private String firstEditorActionValueForRuntime(String runtime) {
        List<JSONObject> actions = editorActionStore.snapshot();
        for (JSONObject action : actions) {
            if (action == null) continue;
            String type = action.optString("type", actionTypeForRuntime(runtime)).trim();
            if (!actionTypeMatchesRuntime(type, runtime)) continue;
            String value = actionEntryValue(type, action);
            if (!TextUtils.isEmpty(value)) return value;
        }
        for (JSONObject action : actions) {
            if (action == null) continue;
            String type = action.optString("type", actionTypeForRuntime(runtime)).trim();
            String value = actionEntryValue(type, action);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String actionEntryValue(String type, JSONObject action) {
        if (action == null) return "";
        if (isDeclarativeActionType(type)) {
            return firstNonEmpty(action.optString("target", ""), action.optString("handler", ""));
        }
        if (isScriptActionType(type)) {
            return firstNonEmpty(action.optString("script", ""), action.optString("command", ""));
        }
        if (isNativeActionType(type)) return action.optString("handler", "").trim();
        if (isTrustedDexActionType(type)) {
            return firstNonEmpty(action.optString("target", ""), action.optString("handler", ""));
        }
        return firstNonEmpty(action.optString("target", ""), action.optString("handler", ""),
                action.optString("script", ""), action.optString("command", ""));
    }

    private static boolean actionTypeMatchesRuntime(String type, String runtime) {
        if ("declarative".equals(runtime)) return isDeclarativeActionType(type);
        if ("script".equals(runtime)) return isScriptActionType(type);
        if ("trusted_native".equals(runtime)) return isNativeActionType(type);
        if ("trusted_dex".equals(runtime)) return isTrustedDexActionType(type);
        return false;
    }

    private boolean saveEditorManifest() {
        TabPluginsBinding tab = tab();
        if (tab == null) return false;
        String json = textOf(tab.edtPluginEditorJson);
        if (TextUtils.isEmpty(json.trim())) {
            json = buildEditorJsonString();
            tab.edtPluginEditorJson.setText(json);
        }
        ValidationResult validation = validatePluginJson(json, false);
        if (!validation.ok) {
            setEditorStatus(validation.message);
            debug("editor", "validation failed on save: " + validation.message);
            return false;
        }
        try {
            File dir = new File(repository().getPluginRoot(), validation.id);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Unable to create " + dir.getAbsolutePath());
            File out = new File(dir, PluginManifest.FILE_NAME);
            writeUtf8(out, json);
            copyPendingEditorIconIfAny(validation);
            copyPendingEditorScriptsIfAny(validation, json);
            copyPendingEditorAssetsIfAny(validation);
            writeEditorUiJsonIfPresent(dir, validation, tab);
            applyRemovedEditorAssetsIfAny(validation);
            PluginManifest saved = PluginManifest.fromDirectory(dir);
            editorPluginId = saved.id;
            setEditorStatus("Saved " + saved.id + " to staged plugins.");
            append("[plugins] Saved plugin manifest: " + saved.id + "\n");
            refreshPlugins();
            return true;
        } catch (Throwable t) {
            setEditorStatus("Save failed: " + safeMessage(t));
            return false;
        }
    }

    private void packageEditorPlugin() {
        TabPluginsBinding tab = tab();
        String json = textOf(tab == null ? null : tab.edtPluginEditorJson);
        if (TextUtils.isEmpty(json.trim())) {
            json = buildEditorJsonString();
            if (tab != null) tab.edtPluginEditorJson.setText(json);
        }
        ValidationResult validation = validatePluginJson(json, true);
        if (!validation.ok) {
            setEditorStatus(validation.message);
            debug("editor", "validation failed on package: " + validation.message);
            return;
        }
        if (!saveEditorManifest()) return;
        String id = editorPluginId;
        if (!PluginManifest.isSafeId(id)) {
            setEditorStatus("Save a valid plugin before packaging.");
            return;
        }
        try {
            File dir = new File(repository().getPluginRoot(), id);
            PluginManifest manifest = PluginManifest.fromDirectory(dir);
            String requiredFileProblem = requiredPluginFileProblem(manifest);
            if (!TextUtils.isEmpty(requiredFileProblem)) {
                setEditorStatus("Package blocked: " + requiredFileProblem);
                debug("editor", "package blocked: " + requiredFileProblem);
                return;
            }
            File out = exportPluginPackage(manifest);
            setEditorStatus("Packaged plugin: " + out.getAbsolutePath());
            append("[plugins] Packaged plugin: " + out.getAbsolutePath() + "\n");
        } catch (Throwable t) {
            setEditorStatus("Package failed: " + safeMessage(t));
        }
    }

    private ValidationResult validatePluginJson(String json, boolean packaging) {
        try {
            if (TextUtils.isEmpty(json) || TextUtils.isEmpty(json.trim())) {
                return ValidationResult.fail("Plugin JSON is empty.");
            }
            JSONObject root = new JSONObject(json);
            ArrayList<String> warnings = new ArrayList<>();
            String schema = root.optString("schema", "");
            if (!TextUtils.isEmpty(schema) && !PluginManifest.SCHEMA.equals(schema)) {
                return ValidationResult.fail("Unsupported schema: " + schema);
            }
            int apiVersion = root.optInt("apiVersion", 1);
            if (apiVersion <= 0 || apiVersion > PluginManifest.SUPPORTED_API_VERSION) {
                return ValidationResult.fail("Unsupported plugin apiVersion: " + apiVersion
                        + ". PermsTest currently supports apiVersion " + PluginManifest.SUPPORTED_API_VERSION + ".");
            }
            String id = root.optString("id", "").trim();
            if (!PluginManifest.isSafeId(id)) {
                return ValidationResult.fail("Plugin ID is required. Use letters, numbers, dash, underscore, or dot only.");
            }
            if (isPlaceholderPluginId(id)) {
                return ValidationResult.fail("Plugin ID '" + id + "' is a placeholder. Enter a unique ID before saving or packaging.");
            }
            String name = root.optString("name", "").trim();
            if (TextUtils.isEmpty(name) || "Custom Plugin".equalsIgnoreCase(name)) {
                return ValidationResult.fail("Plugin name is required. Enter a real display name.");
            }
            String version = root.optString("version", "").trim();
            if (TextUtils.isEmpty(version)) {
                return ValidationResult.fail("Plugin version is required.");
            }
            String runtime = root.optString("runtime", "declarative").trim();
            if (!isSupportedRuntime(runtime)) {
                return ValidationResult.fail("Unsupported runtime: " + runtime);
            }
            String windowStyle = root.optString("windowStyle", "compact").trim();
            if (!"compact".equals(windowStyle) && !"full".equals(windowStyle)) {
                return ValidationResult.fail("windowStyle must be compact or full.");
            }
            String windowFit = root.optString("windowFit", "current").trim();
            if (!"current".equals(windowFit) && !"fit".equals(windowFit)) {
                return ValidationResult.fail("windowFit must be current or fit.");
            }
            String icon = root.optString("icon", "").trim();
            if (!TextUtils.isEmpty(icon) && !isSafeRelativePluginPath(icon)) {
                return ValidationResult.fail("Icon path must stay inside the plugin folder: " + icon);
            }
            if (TextUtils.isEmpty(icon)) warnings.add("No icon is declared; PermsTest will use the default plugin icon.");
            if ("trusted_dex".equals(runtime)) {
                warnings.add("trusted_dex dispatch requires the runtime gate, exact-payload trust, SHA-256 match, and explicit user tap.");
            }
            JSONArray declaredCapabilitiesJson = root.optJSONArray("capabilities");
            Set<String> declaredCapabilities = readCapabilitySet(declaredCapabilitiesJson);
            if (declaredCapabilitiesJson != null) {
                String capabilityProblem = validateCapabilityArray(declaredCapabilitiesJson, "Plugin capabilities");
                if (!TextUtils.isEmpty(capabilityProblem)) return ValidationResult.fail(capabilityProblem);
            }

            JSONArray actions = root.optJSONArray("actions");
            if (actions == null || actions.length() == 0) {
                return ValidationResult.fail("At least one action is required.");
            }
            Set<String> actionIds = new HashSet<>();
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.optJSONObject(i);
                if (action == null) return ValidationResult.fail("Action " + (i + 1) + " is not an object.");
                String actionId = action.optString("id", "").trim();
                if (!PluginManifest.isSafeId(actionId)) {
                    return ValidationResult.fail("Action " + (i + 1) + " needs a safe id.");
                }
                if (!actionIds.add(actionId)) {
                    return ValidationResult.fail("Duplicate action id: " + actionId);
                }
                String actionTitle = action.optString("title", "").trim();
                if (TextUtils.isEmpty(actionTitle)) {
                    return ValidationResult.fail("Action " + actionId + " needs a title.");
                }
                String type = action.optString("type", actionTypeForRuntime(runtime)).trim();
                if (!isSupportedPluginActionType(type)) {
                    return ValidationResult.fail("Action " + actionId + " has unsupported type: " + type);
                }
                String presentation = action.optString("presentation", action.optString("display", "default")).trim();
                if (TextUtils.isEmpty(presentation)) presentation = "default";
                if (!isSupportedPluginActionPresentation(presentation)) {
                    return ValidationResult.fail("Action " + actionId + " has unsupported presentation: " + presentation);
                }
                String syntax = action.optString("syntax", "default").trim();
                if (TextUtils.isEmpty(syntax)) syntax = "default";
                if (!isSupportedPluginActionSyntax(syntax)) {
                    return ValidationResult.fail("Action " + actionId + " has unsupported syntax: " + syntax);
                }
                String actionWindowStyle = action.optString("windowStyle", "inherit").trim();
                if (TextUtils.isEmpty(actionWindowStyle)) actionWindowStyle = "inherit";
                if (!isSupportedPluginActionWindowStyle(actionWindowStyle)) {
                    return ValidationResult.fail("Action " + actionId + " windowStyle must be compact, full, or omitted.");
                }
                String actionWindowFit = action.optString("windowFit", "inherit").trim();
                if (TextUtils.isEmpty(actionWindowFit)) actionWindowFit = "inherit";
                if (!isSupportedPluginActionWindowFit(actionWindowFit)) {
                    return ValidationResult.fail("Action " + actionId + " windowFit must be current, fit, or omitted.");
                }
                JSONArray actionRequiresJson = action.optJSONArray("requires");
                if (actionRequiresJson != null) {
                    String requiresProblem = validateCapabilityArray(actionRequiresJson, "Action " + actionId + " requires");
                    if (!TextUtils.isEmpty(requiresProblem)) return ValidationResult.fail(requiresProblem);
                }
                if (isTrustedDexActionType(type)) {
                    warnings.add("Action " + actionId + " uses trusted_dex; dispatch requires the runtime gate, exact-payload trust, SHA-256 match, and explicit user tap.");
                }
                String target = action.optString("target", "").trim();
                String handler = action.optString("handler", "").trim();
                String command = action.optString("command", "");
                String script = action.optString("script", "").trim();
                boolean hasTarget = !TextUtils.isEmpty(target);
                boolean hasHandler = !TextUtils.isEmpty(handler);
                boolean hasCommand = !TextUtils.isEmpty(command.trim());
                boolean hasScript = !TextUtils.isEmpty(script);
                if (!hasTarget && !hasHandler && !hasCommand && !hasScript) {
                    return ValidationResult.fail("Action " + actionId + " needs target, handler, command, or script.");
                }
                boolean declarativeAction = isDeclarativeActionType(type);
                if (declarativeAction && !hasTarget && !hasHandler) {
                    return ValidationResult.fail("Declarative action " + actionId + " needs a ui.json target.");
                }
                if (declarativeAction) {
                    String uiTarget = firstNonEmpty(target, handler);
                    if (!isSafeRelativePluginPath(uiTarget)) {
                        return ValidationResult.fail("Declarative action " + actionId + " has an unsafe UI target: " + uiTarget);
                    }
                }
                boolean scriptAction = isScriptActionType(type);
                if (scriptAction && !hasCommand && !hasScript) {
                    return ValidationResult.fail("Script action " + actionId + " needs a command or script.");
                }
                if (hasScript && !isSafeRelativePluginPath(script)) {
                    return ValidationResult.fail("Script action " + actionId + " has an unsafe script path: " + script);
                }
                if (isNativeActionType(type) && !hasHandler) {
                    return ValidationResult.fail("Native action " + actionId + " needs a registered handler.");
                }
                if (isNativeActionType(type) && !isRegisteredNativeHandler(handler)) {
                    return ValidationResult.fail("Native action " + actionId + " uses an unregistered handler: " + handler);
                }
                if (isTrustedDexActionType(type) && !hasTarget && !hasHandler) {
                    return ValidationResult.fail("trusted_dex action " + actionId + " needs a target or handler entry.");
                }
                if (isTrustedDexActionType(type)) {
                    String dexEntry = firstNonEmpty(target, handler, action.optString("dex", "").trim(), action.optString("path", "").trim());
                    if (!isSafeRelativePluginPath(dexEntry)) {
                        return ValidationResult.fail("trusted_dex action " + actionId + " has an unsafe payload path: " + dexEntry);
                    }
                    if (!looksLikeTrustedDexPayloadPath(dexEntry)) {
                        warnings.add("Action " + actionId + " trusted-Dex payload should normally be a .dex, .jar, or .apk file: " + dexEntry);
                    }
                    String expectedSha = action.optString("sha256", "").trim();
                    if (!TextUtils.isEmpty(expectedSha) && !isFullSha256(expectedSha)) {
                        return ValidationResult.fail("trusted_dex action " + actionId + " sha256 must be a full 64-character SHA-256 hex string.");
                    }
                    String className = firstNonEmpty(action.optString("className", ""), action.optString("entryClass", ""), action.optString("class", ""));
                    if (!TextUtils.isEmpty(className) && !looksLikeJavaClassName(className)) {
                        return ValidationResult.fail("trusted_dex action " + actionId + " has an invalid className/entryClass: " + className);
                    }
                    String methodName = firstNonEmpty(action.optString("methodName", ""), action.optString("method", ""));
                    if (!TextUtils.isEmpty(methodName) && !looksLikeJavaIdentifier(methodName)) {
                        return ValidationResult.fail("trusted_dex action " + actionId + " has an invalid methodName: " + methodName);
                    }
                    warnings.add("Action " + actionId + " is trusted-Dex metadata; dispatch requires the runtime gate, exact-payload trust, SHA-256 match, and explicit user tap.");
                }
                if (declaredCapabilitiesJson != null) {
                    Set<String> required = inferredCapabilitySetForAction(type, hasCommand, hasScript, actionRequiresJson);
                    for (String capability : required) {
                        if (!declaredCapabilities.contains(capability)) {
                            return ValidationResult.fail("Action " + actionId + " requires capability '" + capability
                                    + "' but plugin capabilities does not declare it.");
                        }
                    }
                }
            }
            String entry = root.optString("entry", "").trim();
            if ("declarative".equals(runtime) && TextUtils.isEmpty(entry)) {
                entry = firstActionEntryForRuntime(actions, runtime);
                if (TextUtils.isEmpty(entry)) entry = "ui.json";
            }
            String normalizedIcon = icon.replace('\\', '/');
            if (!TextUtils.isEmpty(entry) && "declarative".equals(runtime)
                    && !isSafeRelativePluginPath(entry)) {
                return ValidationResult.fail("Plugin entry path must stay inside the plugin folder: " + entry);
            }
            if (!TextUtils.isEmpty(icon) && pluginFilePathsConflict(normalizedIcon, PluginManifest.FILE_NAME)) {
                return ValidationResult.fail("Icon path conflicts with reserved " + PluginManifest.FILE_NAME + ".");
            }
            Set<String> uiPaths = declaredDeclarativeUiPaths(actions, runtime, entry);
            Set<String> scriptPaths = new java.util.LinkedHashSet<>();
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.optJSONObject(i);
                if (action == null) continue;
                String script = action.optString("script", "").trim().replace('\\', '/');
                if (!TextUtils.isEmpty(script)) scriptPaths.add(script);
            }
            for (String uiPath : uiPaths) {
                if (pluginFilePathsConflict(uiPath, PluginManifest.FILE_NAME)) {
                    return ValidationResult.fail("Declarative UI path conflicts with reserved " + PluginManifest.FILE_NAME + ": " + uiPath);
                }
                if (!TextUtils.isEmpty(icon) && pluginFilePathsConflict(normalizedIcon, uiPath)) {
                    return ValidationResult.fail("Icon path conflicts with declarative UI file: " + uiPath);
                }
            }
            for (String scriptPath : scriptPaths) {
                if (pluginFilePathsConflict(scriptPath, PluginManifest.FILE_NAME)) {
                    return ValidationResult.fail("Script path conflicts with reserved " + PluginManifest.FILE_NAME + ": " + scriptPath);
                }
                if (!TextUtils.isEmpty(icon) && pluginFilePathsConflict(normalizedIcon, scriptPath)) {
                    return ValidationResult.fail("Icon path conflicts with script file: " + scriptPath);
                }
                for (String uiPath : uiPaths) {
                    if (pluginFilePathsConflict(scriptPath, uiPath)) {
                        return ValidationResult.fail("Script path conflicts with declarative UI file: " + scriptPath);
                    }
                }
            }
            return ValidationResult.ok(id, runtime, entry, icon, actions.length(), warnings);
        } catch (Throwable t) {
            return ValidationResult.fail("Plugin JSON is invalid: " + safeMessage(t));
        }
    }

    private String buildEditorValidationSummary(ValidationResult validation) {
        if (validation == null || !validation.ok) return validation == null ? "Validation failed." : validation.message;
        ArrayList<String> warnings = new ArrayList<>(validation.warnings);
        File dir = new File(repository().getPluginRoot(), validation.id);
        if (!TextUtils.isEmpty(validation.icon)) {
            File iconFile = new File(dir, validation.icon);
            if (pendingEditorIconUri != null) {
                warnings.add("Selected icon is pending copy to " + validation.icon + ".");
            } else if (!iconFile.isFile()) {
                warnings.add("Declared icon file is not staged yet: " + validation.icon);
            }
        }
        TabPluginsBinding editorTab = tab();
        String pluginJson = textOf(editorTab == null ? null : editorTab.edtPluginEditorJson).trim();
        String uiText = textOf(editorTab == null ? null : editorTab.edtPluginEditorUiJson).trim();
        try {
            JSONObject root = new JSONObject(pluginJson);
            for (String uiPath : declaredDeclarativeUiPaths(root.optJSONArray("actions"), validation.runtime, validation.entry)) {
                boolean pendingRootUi = "declarative".equals(validation.runtime)
                        && pluginPathsEqual(uiPath, validation.entry)
                        && !TextUtils.isEmpty(uiText);
                if (!new File(dir, uiPath).isFile() && !pendingRootUi) {
                    warnings.add("Declared declarative UI file is not staged yet: " + uiPath);
                }
            }
        } catch (Throwable ignored) {
        }
        for (String script : declaredScriptPaths(pluginJson)) {
            File scriptFile = new File(dir, script);
            if (hasPendingEditorScript(script)) {
                warnings.add("Selected script is pending copy to " + script + ".");
            } else if (!scriptFile.isFile()) {
                warnings.add("Declared script file is not staged yet: " + script);
            }
        }
        if (!pendingEditorAssetUris.isEmpty()) warnings.add(pendingEditorAssetUris.size() + " managed asset(s) are pending copy or replacement.");
        if (!removedEditorAssetPaths.isEmpty()) warnings.add(removedEditorAssetPaths.size() + " managed asset(s) are marked for removal on Save or Package.");
        StringBuilder summary = new StringBuilder();
        summary.append("Valid plugin.json: ").append(validation.id)
                .append(" • runtime=").append(validation.runtime)
                .append(" • actions=").append(validation.actionCount);
        if (!TextUtils.isEmpty(validation.entry)) summary.append(" • entry=").append(validation.entry);
        try {
            JSONObject root = new JSONObject(pluginJson);
            JSONArray capabilities = root.optJSONArray("capabilities");
            if (capabilities != null && capabilities.length() > 0) {
                summary.append("\nCapabilities: ");
                for (int i = 0; i < capabilities.length(); i++) {
                    if (i > 0) summary.append(", ");
                    summary.append(capabilities.optString(i));
                }
            }
        } catch (Throwable ignored) {
        }
        if ("declarative".equals(validation.runtime) && !TextUtils.isEmpty(uiText)) {
            summary.append("\nValid ui.json: declarative controls/actions passed runtime checks.");
        }
        if (!warnings.isEmpty()) {
            summary.append("\nWarnings:");
            for (String warning : warnings) summary.append("\n• ").append(warning);
        }
        return summary.toString();
    }


    private File writeAllPluginReadiness(String report) throws Exception {
        File exportRoot = new File(repository().getPluginRoot().getParentFile(), "plugin_exports");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + exportRoot.getAbsolutePath());
        }
        File out = new File(exportRoot, "all-plugins-complete-readiness-" + System.currentTimeMillis() + ".txt");
        writeUtf8(out, report);
        return out;
    }

    private File writePluginCompleteReadiness(PluginManifest manifest, String report) throws Exception {
        if (manifest == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Plugin manifest is unavailable");
        }
        File exportRoot = new File(repository().getPluginRoot().getParentFile(), "plugin_exports");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + exportRoot.getAbsolutePath());
        }
        String safeVersion = TextUtils.isEmpty(manifest.version)
                ? "unknown"
                : manifest.version.replaceAll("[^A-Za-z0-9._-]", "_");
        File out = new File(exportRoot, manifest.id + "-" + safeVersion + "-complete-readiness-" + System.currentTimeMillis() + ".txt");
        writeUtf8(out, report);
        return out;
    }

    private File writePluginPackageReadiness(PluginManifest manifest, String report) throws Exception {
        if (manifest == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Plugin manifest is unavailable");
        }
        File exportRoot = new File(repository().getPluginRoot().getParentFile(), "plugin_exports");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + exportRoot.getAbsolutePath());
        }
        String safeVersion = TextUtils.isEmpty(manifest.version)
                ? "unknown"
                : manifest.version.replaceAll("[^A-Za-z0-9._-]", "_");
        File out = new File(exportRoot, manifest.id + "-" + safeVersion + "-package-readiness-" + System.currentTimeMillis() + ".txt");
        writeUtf8(out, report);
        return out;
    }

    private File writePluginExportRoundTrip(PluginManifest manifest, String report) throws Exception {
        if (manifest == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Plugin manifest is unavailable");
        }
        File exportRoot = new File(repository().getPluginRoot().getParentFile(), "plugin_exports");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + exportRoot.getAbsolutePath());
        }
        String safeVersion = TextUtils.isEmpty(manifest.version)
                ? "unknown"
                : manifest.version.replaceAll("[^A-Za-z0-9._-]", "_");
        File out = new File(exportRoot, manifest.id + "-" + safeVersion + "-export-roundtrip-" + System.currentTimeMillis() + ".txt");
        writeUtf8(out, report);
        return out;
    }

    private File writePluginScriptReadiness(PluginManifest manifest, String report) throws Exception {
        if (manifest == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Plugin manifest is unavailable");
        }
        File exportRoot = new File(repository().getPluginRoot().getParentFile(), "plugin_exports");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + exportRoot.getAbsolutePath());
        }
        String safeVersion = TextUtils.isEmpty(manifest.version)
                ? "unknown"
                : manifest.version.replaceAll("[^A-Za-z0-9._-]", "_");
        File out = new File(exportRoot, manifest.id + "-" + safeVersion + "-script-readiness-" + System.currentTimeMillis() + ".txt");
        writeUtf8(out, report);
        return out;
    }

    private File writePluginDeclarativeReadiness(PluginManifest manifest, String report) throws Exception {
        if (manifest == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Plugin manifest is unavailable");
        }
        File exportRoot = new File(repository().getPluginRoot().getParentFile(), "plugin_exports");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + exportRoot.getAbsolutePath());
        }
        String safeVersion = TextUtils.isEmpty(manifest.version)
                ? "unknown"
                : manifest.version.replaceAll("[^A-Za-z0-9._-]", "_");
        File out = new File(exportRoot, manifest.id + "-" + safeVersion + "-declarative-readiness-" + System.currentTimeMillis() + ".txt");
        writeUtf8(out, report);
        return out;
    }

    private File writePluginRuntimeReview(PluginManifest manifest, String report) throws Exception {
        if (manifest == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Plugin manifest is unavailable");
        }
        File exportRoot = new File(repository().getPluginRoot().getParentFile(), "plugin_exports");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + exportRoot.getAbsolutePath());
        }
        String safeVersion = TextUtils.isEmpty(manifest.version)
                ? "unknown"
                : manifest.version.replaceAll("[^A-Za-z0-9._-]", "_");
        File out = new File(exportRoot, manifest.id + "-" + safeVersion + "-runtime-review-" + System.currentTimeMillis() + ".txt");
        writeUtf8(out, report);
        return out;
    }

    private File exportPluginPackage(PluginManifest manifest) throws Exception {
        if (manifest == null || manifest.homeDir == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Staged plugin is unavailable");
        }
        File exportRoot = new File(repository().getPluginRoot().getParentFile(), "plugin_exports");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + exportRoot.getAbsolutePath());
        }
        String safeVersion = TextUtils.isEmpty(manifest.version)
                ? "unknown"
                : manifest.version.replaceAll("[^A-Za-z0-9._-]", "_");
        String name = manifest.id + "-" + safeVersion + "-" + System.currentTimeMillis() + ".ptp";
        File out = new File(exportRoot, name);
        packagePluginToArchive(manifest, out);
        repository().inspectArchive(out);
        return out;
    }

    private File createPluginRoundTripTempArchive(PluginManifest manifest) throws Exception {
        if (manifest == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Plugin manifest is unavailable");
        }
        File roundTripRoot = new File(repository().getPluginRoot(), ".roundtrip");
        if (!roundTripRoot.isDirectory() && !roundTripRoot.mkdirs()) {
            throw new IllegalStateException("Unable to create " + roundTripRoot.getAbsolutePath());
        }
        String safeId = manifest.id.replaceAll("[^A-Za-z0-9._-]", "_");
        return File.createTempFile("roundtrip_" + safeId + "_", ".ptp", roundTripRoot);
    }

    private void packagePluginToArchive(PluginManifest manifest, File out) throws Exception {
        if (manifest == null || manifest.homeDir == null || TextUtils.isEmpty(manifest.id)) {
            throw new IllegalArgumentException("Staged plugin is unavailable");
        }
        if (!manifest.homeDir.isDirectory()) {
            throw new IllegalArgumentException("Staged plugin folder is missing: " + manifest.id);
        }
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            zipPluginDirectory(zip, manifest.homeDir, manifest.homeDir.getName());
        }
    }

    private String trustedCodeTrustProblem(PluginManifest manifest) {
        String required = requiredPluginFileProblem(manifest);
        if (!TextUtils.isEmpty(required)) return required;
        if (manifest == null || manifest.homeDir == null || manifest.actions == null) return "Staged plugin directory is unavailable.";
        for (PluginAction action : manifest.actions) {
            if (action == null || !action.isTrustedDexAction()) continue;
            String expected = action.raw == null ? "" : action.raw.optString("sha256", "").trim();
            if (TextUtils.isEmpty(expected)) {
                return "Trusted-Dex action " + action.id + " must declare a sha256 value before it can be trusted.";
            }
            String payload = trustedDexPayloadPath(action);
            try {
                File file = new File(manifest.homeDir, payload);
                String actual = sha256Full(file);
                if (!expected.equalsIgnoreCase(actual)) {
                    return "Trusted-Dex payload hash mismatch for action " + action.id + ".";
                }
            } catch (Throwable t) {
                return "Unable to hash Trusted-Dex payload for action " + action.id + ": " + safeMessage(t);
            }
        }
        return "";
    }

    private static String sha256Full(File file) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return sha256Full((InputStream) in);
        }
    }

    private static String sha256Full(InputStream source) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream in = new BufferedInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    private String requiredPluginFileProblem(PluginManifest manifest) {
        return PluginPackageValidator.requiredFileProblem(manifest);
    }

    private static String trustedDexPayloadPath(PluginAction action) {
        if (action == null) return "";
        String value = firstNonEmpty(action.target, action.handler);
        if (!TextUtils.isEmpty(value)) return value;
        JSONObject raw = action.raw;
        if (raw == null) return "";
        return firstNonEmpty(raw.optString("dex", ""), raw.optString("path", ""));
    }

    private void writeEditorUiJsonIfPresent(File dir, ValidationResult validation, TabPluginsBinding tab) throws Exception {
        if (dir == null || validation == null || !"declarative".equals(validation.runtime)) return;
        String entry = TextUtils.isEmpty(validation.entry) ? "ui.json" : validation.entry;
        if (!isSafeRelativePluginPath(entry)) throw new IllegalArgumentException("Unsafe UI entry path: " + entry);
        String uiJson = textOf(tab == null ? null : tab.edtPluginEditorUiJson).trim();
        if (TextUtils.isEmpty(uiJson)) {
            uiJson = buildEditorUiJsonString();
            if (tab != null && tab.edtPluginEditorUiJson != null) tab.edtPluginEditorUiJson.setText(uiJson);
        }
        new JSONObject(uiJson);
        File ui = new File(dir, entry);
        File parent = ui.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        writeUtf8(ui, uiJson);
    }

    private String readPluginUiJson(PluginManifest plugin) {
        if (plugin == null || plugin.homeDir == null || !"declarative".equals(plugin.runtime)) return "";
        try {
            String entry = plugin.entry;
            if (TextUtils.isEmpty(entry)) {
                PluginAction first = plugin.actions.isEmpty() ? null : plugin.actions.get(0);
                entry = firstNonEmpty(first == null ? "" : first.target, first == null ? "" : first.handler, "ui.json");
            }
            if (!isSafeRelativePluginPath(entry)) return "";
            File file = new File(plugin.homeDir, entry);
            if (!file.isFile()) return "";
            return readUtf8Limited(file, 512 * 1024);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String starterUiJsonString(String title, String description) {
        return "{\n"
                + "  \"title\": \"" + escapeJson(TextUtils.isEmpty(title) ? "Plugin UI" : title) + "\",\n"
                + "  \"description\": \"" + escapeJson(TextUtils.isEmpty(description) ? "Generated starter UI. Edit ui.json to customize this plugin." : description) + "\",\n"
                + "  \"controls\": [\n"
                + "    { \"type\": \"label\", \"text\": \"Hello from a declarative PermsTest plugin.\" },\n"
                + "    { \"type\": \"input\", \"id\": \"message\", \"label\": \"Message\", \"default\": \"Edit ui.json to change this UI.\" },\n"
                + "    { \"type\": \"output\", \"id\": \"output\", \"label\": \"Output\", \"text\": \"Ready.\" },\n"
                + "    { \"type\": \"button\", \"text\": \"Echo\", \"action\": { \"type\": \"setText\", \"target\": \"output\", \"value\": \"${message}\" } }\n"
                + "  ]\n"
                + "}\n";
    }

    private static boolean looksLikePluginScriptPathCandidate(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String path = value.trim().replace('\\', '/').toLowerCase(Locale.US);
        return path.startsWith("scripts/") || path.endsWith(".sh")
                || path.endsWith(".cmd") || path.endsWith(".script");
    }

    private static boolean looksLikePluginScriptPath(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String path = value.trim().replace('\\', '/').toLowerCase(Locale.US);
        if (!isSafeRelativePluginPath(path) || !path.matches("[a-z0-9._/-]+")) return false;
        return path.startsWith("scripts/") || path.endsWith(".sh")
                || path.endsWith(".cmd") || path.endsWith(".script");
    }

    private static boolean isSafeRelativePluginPath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String p = path.trim().replace('\\', '/');
        if (TextUtils.isEmpty(p) || p.startsWith("/") || p.contains(":")) return false;
        String[] parts = p.split("/", -1);
        for (String part : parts) {
            if (TextUtils.isEmpty(part) || ".".equals(part) || "..".equals(part)) return false;
        }
        return true;
    }

    private static boolean pluginFilePathsConflict(String first, String second) {
        if (TextUtils.isEmpty(first) || TextUtils.isEmpty(second)) return false;
        String a = first.trim().replace('\\', '/').toLowerCase(Locale.US);
        String b = second.trim().replace('\\', '/').toLowerCase(Locale.US);
        return a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/");
    }

    private static String readUtf8Limited(File file, int maxBytes) throws Exception {
        if (file == null || !file.isFile()) return "";
        int limit = Math.max(1, maxBytes);
        byte[] data = new byte[(int) Math.min(file.length(), limit)];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int offset = 0;
            int r;
            while (offset < data.length && (r = in.read(data, offset, data.length - offset)) > 0) offset += r;
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static boolean isDeclarativeActionType(String type) {
        return "declarative_ui".equals(type) || "declarative".equals(type) || "ui".equals(type);
    }

    private static boolean isScriptActionType(String type) {
        return "shell".equals(type) || "script".equals(type);
    }

    private static boolean isNativeActionType(String type) {
        return "native".equals(type) || "trusted_native".equals(type);
    }

    private static boolean isTrustedDexActionType(String type) {
        return "trusted_dex".equals(type) || "dex".equals(type);
    }

    private static boolean isRegisteredNativeHandler(String handler) {
        return BuildPropPluginAction.HANDLER.equals(handler)
                || DeviceInfoPluginAction.HANDLER.equals(handler)
                || LogSnapshotPluginAction.HANDLER.equals(handler)
                || ToolGroupPluginAction.handles(handler);
    }

    private static String actionRequiresCsvProblem(String csv) {
        JSONArray array = capabilityArrayFromCsv(csv);
        return validateCapabilityArray(array, "Action requires");
    }

    private static JSONArray capabilityArrayFromCsv(String csv) {
        JSONArray array = new JSONArray();
        if (TextUtils.isEmpty(csv)) return array;
        String[] pieces = csv.split(",");
        for (String piece : pieces) {
            String value = piece == null ? "" : piece.trim();
            if (TextUtils.isEmpty(value)) continue;
            array.put(value);
        }
        return array;
    }

    private static String capabilityCsvFromArray(JSONArray array) {
        if (array == null || array.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (TextUtils.isEmpty(value)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(value);
        }
        return sb.toString();
    }

    private static String validateCapabilityArray(JSONArray array, String label) {
        if (array == null) return "";
        Set<String> seen = new HashSet<>();
        String safeLabel = TextUtils.isEmpty(label) ? "Capabilities" : label;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!PluginManifest.isSafeId(value)) {
                return safeLabel + " contains an unsafe value at index " + (i + 1) + ".";
            }
            if (!PluginRuntimePolicy.isSupportedCapability(value)) {
                return safeLabel + " contains unsupported capability: " + value;
            }
            if (!seen.add(value)) {
                return safeLabel + " contains duplicate capability: " + value;
            }
        }
        return "";
    }

    private static Set<String> readCapabilitySet(JSONArray array) {
        Set<String> values = new HashSet<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!TextUtils.isEmpty(value)) values.add(value);
        }
        return values;
    }

    private static Set<String> inferredCapabilitySetForAction(String type, boolean hasCommand, boolean hasScript, JSONArray requires) {
        Set<String> values = new HashSet<>();
        if (isDeclarativeActionType(type)) {
            values.add(PluginRuntimePolicy.CAP_DECLARATIVE_UI);
            values.add(PluginRuntimePolicy.CAP_HOST_API);
        } else if (isScriptActionType(type)) {
            if (hasCommand) values.add(PluginRuntimePolicy.CAP_SHELL_COMMAND);
            if (hasScript) values.add(PluginRuntimePolicy.CAP_SHELL_SCRIPT);
            if (!hasCommand && !hasScript) values.add(PluginRuntimePolicy.CAP_SHELL_COMMAND);
        } else if (isTrustedDexActionType(type)) {
            values.add(PluginRuntimePolicy.CAP_TRUSTED_DEX);
        } else if (isNativeActionType(type)) {
            values.add(PluginRuntimePolicy.CAP_TRUSTED_NATIVE);
        }
        if (requires != null) {
            for (int i = 0; i < requires.length(); i++) {
                String value = requires.optString(i, "").trim();
                if (!TextUtils.isEmpty(value)) values.add(value);
            }
        }
        return values;
    }

    private static JSONArray inferredCapabilitiesForActions(JSONArray actions) {
        ArrayList<String> values = new ArrayList<>();
        if (actions != null) {
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.optJSONObject(i);
                if (action == null) continue;
                String type = action.optString("type", "native").trim();
                boolean hasCommand = !TextUtils.isEmpty(action.optString("command", "").trim());
                boolean hasScript = !TextUtils.isEmpty(action.optString("script", "").trim());
                Set<String> inferred = inferredCapabilitySetForAction(type, hasCommand, hasScript, action.optJSONArray("requires"));
                for (String value : inferred) {
                    if (!values.contains(value)) values.add(value);
                }
            }
        }
        JSONArray out = new JSONArray();
        for (String value : values) out.put(value);
        return out;
    }


    private static boolean looksLikeTrustedDexPayloadPath(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String p = path.trim().toLowerCase(Locale.US);
        return p.endsWith(".dex") || p.endsWith(".jar") || p.endsWith(".apk");
    }

    private static boolean isFullSha256(String value) {
        if (TextUtils.isEmpty(value) || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    private static boolean looksLikeJavaClassName(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String[] parts = value.trim().split("\\.");
        if (parts.length == 0) return false;
        for (String part : parts) {
            if (!looksLikeJavaIdentifier(part)) return false;
        }
        return true;
    }

    private static boolean looksLikeJavaIdentifier(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String v = value.trim();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = (i == 0)
                    ? (c == '_' || c == '$' || Character.isLetter(c))
                    : (c == '_' || c == '$' || Character.isLetterOrDigit(c));
            if (!ok) return false;
        }
        return true;
    }

    private static boolean isSupportedPluginActionType(String type) {
        return isDeclarativeActionType(type) || isScriptActionType(type)
                || isNativeActionType(type) || isTrustedDexActionType(type);
    }

    private static boolean isSupportedPluginActionPresentation(String presentation) {
        return "default".equals(presentation) || "dialog".equals(presentation) || "viewer".equals(presentation)
                || "window".equals(presentation) || "panel".equals(presentation) || "large".equals(presentation)
                || "log".equals(presentation) || "output".equals(presentation) || "main_output".equals(presentation);
    }

    private static boolean isSupportedPluginActionSyntax(String syntax) {
        return "default".equals(syntax) || "plain".equals(syntax) || "text".equals(syntax) || "json".equals(syntax)
                || "properties".equals(syntax) || "prop".equals(syntax) || "ini".equals(syntax)
                || "shell".equals(syntax) || "bash".equals(syntax) || "sh".equals(syntax)
                || "smali".equals(syntax) || "web".equals(syntax) || "html".equals(syntax)
                || "css".equals(syntax) || "js".equals(syntax);
    }

    private static boolean isSupportedPluginActionWindowStyle(String style) {
        return "inherit".equals(style) || "compact".equals(style) || "full".equals(style);
    }

    private static boolean isSupportedPluginActionWindowFit(String fit) {
        return "inherit".equals(fit) || "current".equals(fit) || "fit".equals(fit);
    }

    private static String handlerSuggestionRuntime(String actionType, String fallbackRuntime) {
        if (isScriptActionType(actionType)) return "script";
        if (isNativeActionType(actionType)) return "trusted_native";
        if (isTrustedDexActionType(actionType)) return "trusted_dex";
        if (isDeclarativeActionType(actionType)) return "declarative";
        return TextUtils.isEmpty(fallbackRuntime) ? "declarative" : fallbackRuntime;
    }

    private static boolean isSupportedUiControlType(String type) {
        if (TextUtils.isEmpty(type)) return false;
        for (String value : UI_CONTROL_TYPE_SUGGESTIONS) {
            if (value.equals(type)) return true;
        }
        return false;
    }

    private static boolean isSupportedDeclarativeApiName(String name) {
        if (TextUtils.isEmpty(name)) return false;
        for (String value : UI_API_NAME_SUGGESTIONS) {
            if (!CUSTOM_UI_ACTION_DATA_LABEL.equals(value) && value.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static boolean isSupportedUiActionType(String type) {
        if (TextUtils.isEmpty(type)) return false;
        for (String value : UI_ACTION_TYPE_SUGGESTIONS) {
            if (value.equals(type)) return true;
        }
        return false;
    }

    private static boolean requiresUiControlId(String type) {
        return "input".equals(type) || "multiline".equals(type) || "dropdown".equals(type)
                || "checkbox".equals(type) || "output".equals(type);
    }

    private static boolean isSupportedRuntime(String runtime) {
        return "declarative".equals(runtime)
                || "script".equals(runtime)
                || "trusted_native".equals(runtime)
                || "trusted_dex".equals(runtime);
    }

    private static boolean isPlaceholderPluginId(String id) {
        return "id".equalsIgnoreCase(id)
                || "plugin_id".equalsIgnoreCase(id)
                || "my_plugin".equalsIgnoreCase(id)
                || "custom_plugin".equalsIgnoreCase(id);
    }

    private static String firstActionEntryForRuntime(JSONArray actions, String runtime) {
        if (actions == null) return "";
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action == null) continue;
            String type = action.optString("type", actionTypeForRuntime(runtime)).trim();
            if (!actionTypeMatchesRuntime(type, runtime)) continue;
            String value = actionEntryValue(type, action);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String actionTypeForRuntime(String runtime) {
        if ("script".equals(runtime)) return "shell";
        if ("trusted_dex".equals(runtime)) return "trusted_dex";
        if ("trusted_native".equals(runtime)) return "native";
        return "declarative_ui";
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private static final class ValidationResult {
        final boolean ok;
        final String id;
        final String runtime;
        final String entry;
        final String icon;
        final int actionCount;
        final List<String> warnings;
        final String message;

        private ValidationResult(boolean ok,
                                 String id,
                                 String runtime,
                                 String entry,
                                 String icon,
                                 int actionCount,
                                 List<String> warnings,
                                 String message) {
            this.ok = ok;
            this.id = id == null ? "" : id;
            this.runtime = runtime == null ? "" : runtime;
            this.entry = entry == null ? "" : entry;
            this.icon = icon == null ? "" : icon;
            this.actionCount = Math.max(0, actionCount);
            this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
            this.message = message == null ? "" : message;
        }

        static ValidationResult ok(String id,
                                   String runtime,
                                   String entry,
                                   String icon,
                                   int actionCount,
                                   List<String> warnings) {
            return new ValidationResult(true, id, runtime, entry, icon, actionCount, warnings, "");
        }

        static ValidationResult fail(String message) {
            return new ValidationResult(false, "", "", "", "", 0, null, message);
        }
    }

    private String readPluginJson(PluginManifest plugin) {
        if (plugin == null || plugin.homeDir == null) return "";
        try {
            File file = new File(plugin.homeDir, PluginManifest.FILE_NAME);
            if (!file.isFile()) return "";
            return readUtf8Limited(file, 512 * 1024);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void zipPluginDirectory(ZipOutputStream zip, File file, String entryName) throws Exception {
        if (zip == null || file == null || TextUtils.isEmpty(entryName)) return;
        if (skipPluginPackageEntry(file)) return;
        if (file.isDirectory()) {
            String dirName = entryName.endsWith("/") ? entryName : entryName + "/";
            zip.putNextEntry(new ZipEntry(dirName));
            zip.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) zipPluginDirectory(zip, child, dirName + child.getName());
            }
            return;
        }
        zip.putNextEntry(new ZipEntry(entryName));
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[32768];
            int r;
            while ((r = in.read(buffer)) > 0) zip.write(buffer, 0, r);
        }
        zip.closeEntry();
    }

    private static boolean skipPluginPackageEntry(File file) {
        if (file == null) return true;
        String name = file.getName();
        return name.startsWith(".copy_") || name.startsWith(".backup_")
                || name.startsWith(".replace_") || name.startsWith(".tmp_");
    }

    private static void writeUtf8(File file, String text) throws Exception {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void setEditorStatus(String text) {
        TabPluginsBinding tab = tab();
        if (tab != null && tab.txtPluginEditorStatus != null) tab.txtPluginEditorStatus.setText(text == null ? "" : text);
    }

    private static String textOf(TextView view) {
        return view == null || view.getText() == null ? "" : String.valueOf(view.getText());
    }

    private static String textOr(TextView view, String fallback) {
        String value = textOf(view).trim();
        return TextUtils.isEmpty(value) ? (fallback == null ? "" : fallback) : value;
    }

    private String editorText(TabPluginsBinding tab, String fallback) {
        String value = textOf(tab == null ? null : tab.edtPluginEditorId).trim();
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static String safeActionId(String title) {
        String raw = TextUtils.isEmpty(title) ? "action" : title.toLowerCase(Locale.US);
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (ok) {
                sb.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        String out = sb.toString();
        while (out.startsWith("_")) out = out.substring(1);
        while (out.endsWith("_")) out = out.substring(0, out.length() - 1);
        return TextUtils.isEmpty(out) ? "action" : out;
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static String extractJsonString(String json, String key) {
        if (TextUtils.isEmpty(json) || TextUtils.isEmpty(key)) return "";
        String needle = "\"" + key + "\"";
        int pos = json.indexOf(needle);
        if (pos < 0) return "";
        int colon = json.indexOf(':', pos + needle.length());
        if (colon < 0) return "";
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return "";
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                out.append(c == 'n' ? '\n' : c == 'r' ? '\r' : c == 't' ? '\t' : c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return "";
    }

    private void persistUriGrant(Intent data, Uri uri) {
        Activity activity = activity();
        if (activity == null || data == null || uri == null) return;
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) activity.getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Throwable ignored) {
        }
    }

    private String queryDisplayName(Uri uri) {
        Activity activity = activity();
        if (activity == null || uri == null) return "";
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (cursor != null) cursor.close(); } catch (Throwable ignored) {}
        }
        return uri.toString();
    }

    private PluginRepository repository() {
        return new PluginRepository(activity());
    }

    private TabPluginsBinding tab() {
        try {
            ActivityMainBinding binding = host == null ? null : host.getBinding();
            return binding == null ? null : binding.tabPlugins;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private SharedPreferences prefs() {
        return host == null ? null : host.getSharedPreferences();
    }

    private void debug(String area, String message) {
        try {
            if (host != null && host.isDebugOutputEnabled()) host.debugOutput(area, message);
        } catch (Throwable ignored) {
        }
    }

    private void setStatus(String message) {
        TabPluginsBinding tab = tab();
        if (tab != null && tab.txtPluginsStatus != null) tab.txtPluginsStatus.setText(message == null ? "" : message);
    }

    private void append(String message) {
        if (host != null && !TextUtils.isEmpty(message)) host.appendOutput(message);
    }

    private void runUi(Runnable task) {
        Activity activity = activity();
        if (task == null) return;
        if (activity == null) {
            task.run();
            return;
        }
        activity.runOnUiThread(task);
    }

    private int dp(int value) {
        Activity activity = activity();
        if (activity == null) return value;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                activity.getResources().getDisplayMetrics());
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        if (TextUtils.isEmpty(msg)) msg = t.toString();
        return msg == null ? "unknown" : msg;
    }
}
