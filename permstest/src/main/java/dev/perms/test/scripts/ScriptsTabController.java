package dev.perms.test.scripts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.DropdownUi;

public final class ScriptsTabController {
    private static final String PREF_SCRIPTS_LOAD_USER = "scripts_load_user";
    private static final String PREF_SCRIPTS_HIDE_LABELS = "scripts_hide_labels";
    private static final String EXT_SCRIPTS_DIR = "scripts";

    private final AppCompatActivity activity;
    private final Supplier<ActivityMainBinding> bindingProvider;
    private final ScriptsEditorUi editorUi;
    private final String prefsName;
    private final ShellRunner shellRunner;
    private final ShellCaptureRunner shellCaptureRunner;
    private final OutputAppender outputAppender;
    private final ExternalTextWriter externalTextWriter;

    private final ArrayList<String> availableScripts = new ArrayList<>();
    private final LinkedHashMap<String, ScriptsCatalog.ScriptRef> scriptRefs = new LinkedHashMap<>();

    private ScriptsCatalog.ScriptRef currentScriptRef;
    private boolean prefLoadUserScripts = true;
    private boolean prefHideScriptLabels = true;
    private int scriptsListSeq;
    private ActivityResultLauncher<Intent> pickScriptLauncher;
    private ScriptDropdownAdapter scriptsAdapter;
    private String selectedScriptName;
    private int scriptsDropdownDefaultTextColor;

    public ScriptsTabController(AppCompatActivity activity,
                                Supplier<ActivityMainBinding> bindingProvider,
                                ScriptsEditorUi editorUi,
                                String prefsName,
                                ShellRunner shellRunner,
                                ShellCaptureRunner shellCaptureRunner,
                                OutputAppender outputAppender,
                                ExternalTextWriter externalTextWriter) {
        this.activity = activity;
        this.bindingProvider = bindingProvider;
        this.editorUi = editorUi;
        this.prefsName = prefsName;
        this.shellRunner = shellRunner;
        this.shellCaptureRunner = shellCaptureRunner;
        this.outputAppender = outputAppender;
        this.externalTextWriter = externalTextWriter;
    }

    public void setup() {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabScripts == null) return;

            loadPreferences();
            bindPreferenceControls(binding);
            setupScriptPicker();
            bindActionButtons(binding);
            editorUi.setupGenericEditor(binding);
            editorUi.setupScrollingAndResize(binding);
            editorUi.setupSyntaxHighlighting(binding);
            bindDropdownRefresh(binding);
            rebuildScriptsList(false);
            editorUi.setEditable(binding, false);
        } catch (Throwable ignored) {
        }
    }

    public void runCurrentScriptFromEditor() {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabScripts == null) return;

            String name = selectedScriptName;
            if (binding.tabScripts.ddScript != null) {
                CharSequence cs = binding.tabScripts.ddScript.getText();
                String typed = cs == null ? "" : cs.toString().trim();
                if (!typed.isEmpty()) name = typed;
            }

            if (name == null || name.trim().isEmpty()) {
                Toast.makeText(activity, "Select a script", Toast.LENGTH_SHORT).show();
                return;
            }

            String body = editorUi.getText(binding);

            String args = "";
            if (binding.tabScripts.edtScriptArgs != null && binding.tabScripts.edtScriptArgs.getText() != null) {
                args = binding.tabScripts.edtScriptArgs.getText().toString().trim();
            }

            ScriptsCatalog.ScriptRef ref = null;
            try { ref = scriptRefs.get(name); } catch (Throwable ignored) {}

            String fileName = name;
            if (ref != null && !TextUtils.isEmpty(ref.fileName)) fileName = ref.fileName;
            fileName = sanitizeFilename(fileName);

            File scriptFile = externalTextWriter == null ? null : externalTextWriter.write(EXT_SCRIPTS_DIR, fileName, body);
            if (scriptFile == null) {
                Toast.makeText(activity, "Failed to write script", Toast.LENGTH_SHORT).show();
                return;
            }

            String spath = scriptFile.getAbsolutePath();
            String cmd = "chmod 777 " + shQuote(spath) + " 2>/dev/null || true; sh "
                    + shQuote(spath) + (args.isEmpty() ? "" : " " + args);
            if (shellRunner != null) shellRunner.runShellCommand(cmd);
        } catch (Throwable t) {
            appendOutput("[!] Run script failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
        }
    }

    private void loadPreferences() {
        try {
            SharedPreferences sp = preferences();
            prefLoadUserScripts = sp.getBoolean(PREF_SCRIPTS_LOAD_USER, true);
            prefHideScriptLabels = sp.getBoolean(PREF_SCRIPTS_HIDE_LABELS, true);
        } catch (Throwable ignored) {
            prefLoadUserScripts = true;
            prefHideScriptLabels = true;
        }
    }

    private void bindPreferenceControls(ActivityMainBinding binding) {
        try {
            if (binding.tabScripts.chkLoadUserScripts != null) {
                binding.tabScripts.chkLoadUserScripts.setChecked(prefLoadUserScripts);
                binding.tabScripts.chkLoadUserScripts.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    try {
                        prefLoadUserScripts = isChecked;
                        try { preferences().edit().putBoolean(PREF_SCRIPTS_LOAD_USER, isChecked).apply(); } catch (Throwable ignored) {}
                        rebuildScriptsList(true);
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}

        try {
            if (binding.tabScripts.chkHideScriptLabels != null) {
                binding.tabScripts.chkHideScriptLabels.setChecked(prefHideScriptLabels);
                binding.tabScripts.chkHideScriptLabels.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    try {
                        prefHideScriptLabels = isChecked;
                        try { preferences().edit().putBoolean(PREF_SCRIPTS_HIDE_LABELS, isChecked).apply(); } catch (Throwable ignored) {}
                        rebuildScriptsList(true);
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}
    }

    public void registerActivityResults() {
        setupScriptPicker();
    }

    private void setupScriptPicker() {
        try {
            if (pickScriptLauncher != null) return;
            pickScriptLauncher = activity.registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    (ActivityResult result) -> handlePickedScript(result));
        } catch (Throwable ignored) {
        }
    }

    private void handlePickedScript(ActivityResult result) {
        try {
            if (result == null || result.getResultCode() != Activity.RESULT_OK) return;
            Intent data = result.getData();
            if (data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;

            String label = queryDisplayName(uri);
            if (label == null || label.trim().isEmpty()) label = uri.toString();

            String text;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                text = (in == null) ? "" : readAll(in);
            }

            final String displayName = label;
            final String body = text == null ? "" : text;

            selectedScriptName = displayName;
            currentScriptRef = new ScriptsCatalog.ScriptRef(displayName, displayName, false, null);

            ActivityMainBinding binding = binding();
            try {
                if (binding != null && binding.tabScripts != null && binding.tabScripts.ddScript != null) {
                    binding.tabScripts.ddScript.setText(displayName, false);
                }
            } catch (Throwable ignored) {}

            try { editorUi.setText(binding, null, body); } catch (Throwable ignored) {}

            try { editorUi.setEditable(binding, editorUi.isEditable()); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            appendOutput("[!] Script picker failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
        }
    }

    private void bindActionButtons(ActivityMainBinding binding) {
        try {
            if (binding.tabScripts.btnEditScript != null) {
                binding.tabScripts.btnEditScript.setOnClickListener(v -> editorUi.toggleEditable(binding));
            }
        } catch (Throwable ignored) {}

        try {
            if (binding.tabScripts.btnRunScript != null) {
                binding.tabScripts.btnRunScript.setOnClickListener(v -> runCurrentScriptFromEditor());
            }
        } catch (Throwable ignored) {}

        try {
            if (binding.tabScripts.btnLoadScript != null) {
                binding.tabScripts.btnLoadScript.setOnClickListener(v -> launchScriptPicker());
            }
        } catch (Throwable ignored) {}

        try {
            if (binding.tabScripts.btnNewScript != null) {
                binding.tabScripts.btnNewScript.setOnClickListener(v -> newScriptBuffer());
            }
        } catch (Throwable ignored) {}

        try {
            if (binding.tabScripts.btnSaveScript != null) {
                binding.tabScripts.btnSaveScript.setOnClickListener(v -> saveScriptBuffer(false));
                binding.tabScripts.btnSaveScript.setOnLongClickListener(v -> {
                    saveScriptBuffer(true);
                    return true;
                });
            }
        } catch (Throwable ignored) {}
    }

    private void launchScriptPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            if (pickScriptLauncher != null) pickScriptLauncher.launch(intent);
        } catch (Throwable t) {
            Toast.makeText(activity, "Picker failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void newScriptBuffer() {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabScripts == null) return;
            selectedScriptName = null;
            currentScriptRef = null;
            if (binding.tabScripts.ddScript != null) binding.tabScripts.ddScript.setText("", false);
            applyScriptsSelectedTextStyling(null);
            editorUi.setText(binding, null, "#!/system/bin/sh\n\n");
            editorUi.setEditable(binding, true);
        } catch (Throwable ignored) {
        }
    }

    private void bindDropdownRefresh(ActivityMainBinding binding) {
        try {
            if (binding.tabScripts.ddScript == null) return;
            DropdownUi.bindClickOnlyExposedDropdown(
                    activity,
                    binding.tabScripts.tilScriptDropdown,
                    binding.tabScripts.ddScript,
                    () -> {
                        try { rebuildScriptsList(true); } catch (Throwable ignored) {}
                        try { DropdownUi.showDropdown(binding.tabScripts.ddScript); } catch (Throwable ignored) {}
                    });
        } catch (Throwable ignored) {
        }
    }

    private void rebuildScriptsList(boolean keepSelection) {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabScripts == null) return;

            final ScriptsCatalog.ScriptRef prevRef = keepSelection ? currentScriptRef : null;
            final String prevSelection = keepSelection ? safeText(binding.tabScripts.ddScript) : null;
            final int seq = ++scriptsListSeq;

            ScriptsCatalog.Result catalog = ScriptsCatalog.build(activity, prefLoadUserScripts, prefHideScriptLabels);

            availableScripts.clear();
            availableScripts.addAll(catalog.displayNames);
            scriptRefs.clear();
            scriptRefs.putAll(catalog.refs);

            boolean needShellUserLoad = catalog.needShellUserLoad;
            String first = availableScripts.isEmpty() ? null : availableScripts.get(0);

            if (prevRef != null) {
                try {
                    for (ScriptsCatalog.ScriptRef ref : scriptRefs.values()) {
                        if (ref != null && ref.isUser == prevRef.isUser && TextUtils.equals(ref.fileName, prevRef.fileName)) {
                            selectedScriptName = ref.displayName;
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (TextUtils.isEmpty(selectedScriptName)) {
                selectedScriptName = !TextUtils.isEmpty(prevSelection) ? prevSelection : first;
            }

            if (binding.tabScripts.ddScript != null) {
                try { binding.tabScripts.ddScript.setSaveEnabled(false); } catch (Throwable ignored) {}
                if (scriptsAdapter == null) {
                    scriptsAdapter = new ScriptDropdownAdapter(activity, android.R.layout.simple_list_item_1, availableScripts) {
                        @Override
                        public View getView(int position, View convertView, android.view.ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            applyScriptsDropdownStyling(view, getItem(position));
                            return view;
                        }

                        @Override
                        public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                            View view = super.getDropDownView(position, convertView, parent);
                            applyScriptsDropdownStyling(view, getItem(position));
                            return view;
                        }
                    };
                    binding.tabScripts.ddScript.setAdapter(scriptsAdapter);
                } else {
                    scriptsAdapter.setItems(availableScripts);
                }

                binding.tabScripts.ddScript.setOnItemClickListener((parent, view, position, id) -> {
                    try {
                        String name = position >= 0 && position < availableScripts.size() ? availableScripts.get(position) : null;
                        if (TextUtils.isEmpty(name)) return;
                        selectedScriptName = name;
                        loadScriptIntoEditor(name);
                        applyScriptsSelectedTextStyling(name);
                        try { if (!editorUi.isVirtualMode(binding) && binding.tabScripts.edtScriptBody != null) binding.tabScripts.edtScriptBody.requestFocus(); } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                });

                String selection = selectedScriptName;
                if (!TextUtils.isEmpty(selection)) {
                    try { binding.tabScripts.ddScript.setText(selection, false); } catch (Throwable ignored) {}
                    try { applyScriptsSelectedTextStyling(selection); } catch (Throwable ignored) {}
                } else if (!TextUtils.isEmpty(first)) {
                    selectedScriptName = first;
                    try { binding.tabScripts.ddScript.setText(first, false); } catch (Throwable ignored) {}
                    try { applyScriptsSelectedTextStyling(first); } catch (Throwable ignored) {}
                }
            }

            if (needShellUserLoad) {
                try { loadUserScriptsFromShell(seq, prevSelection); } catch (Throwable ignored) {}
            }

            if (!TextUtils.isEmpty(selectedScriptName)) {
                try { loadScriptIntoEditor(selectedScriptName); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadUserScriptsFromShell(int seq, String prevSelection) {
        try {
            final String dir = ScriptsCatalog.USER_SCRIPTS_DIR;
            final String cmd = "test -d " + shQuote(dir) + " || mkdir -p " + shQuote(dir)
                    + "; ls -1 " + shQuote(dir) + " 2>/dev/null";
            if (shellCaptureRunner == null) return;
            shellCaptureRunner.runShellCommandCapture(cmd, (exit, out, err) -> activity.runOnUiThread(() -> {
                try {
                    if (seq != scriptsListSeq) return;
                    if (!prefLoadUserScripts) return;
                    if (exit != 0) return;

                    ScriptsCatalog.addShellUserScripts(availableScripts, scriptRefs, out, prefHideScriptLabels);

                    ActivityMainBinding binding = binding();
                    if (binding != null && binding.tabScripts != null && binding.tabScripts.ddScript != null) {
                        try { if (scriptsAdapter != null) scriptsAdapter.setItems(availableScripts); } catch (Throwable ignored) {}

                        String selection = selectedScriptName;
                        if (TextUtils.isEmpty(selection) && !TextUtils.isEmpty(prevSelection)) {
                            selection = prevSelection;
                            selectedScriptName = selection;
                        }

                        if (!TextUtils.isEmpty(selection)) {
                            try { binding.tabScripts.ddScript.setText(selection, false); } catch (Throwable ignored) {}
                            try { applyScriptsSelectedTextStyling(selection); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }));
        } catch (Throwable ignored) {
        }
    }

    private void saveScriptBuffer(boolean forceSaveAs) {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabScripts == null) return;

            String body = editorUi.getText(binding);

            final String bodyFinal = body;
            final ScriptsCatalog.ScriptRef ref = currentScriptRef;

            if (!forceSaveAs && ref != null && ref.isUser && !TextUtils.isEmpty(ref.absolutePath)) {
                final String abs = ref.absolutePath;
                writeScriptToUserPath(abs, bodyFinal, () -> {
                    try { Toast.makeText(activity, "Saved: " + ref.fileName, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
                    try { rebuildScriptsList(true); } catch (Throwable ignored) {}
                });
                return;
            }

            String suggested = "new-script.sh";
            if (ref != null && !TextUtils.isEmpty(ref.fileName)) suggested = ref.fileName;
            showSaveScriptDialog(suggested, (fileName) -> {
                if (TextUtils.isEmpty(fileName)) return;
                String outName = fileName.trim();
                if (!outName.toLowerCase(java.util.Locale.US).endsWith(".sh")) outName += ".sh";

                String abs = ScriptsCatalog.USER_SCRIPTS_DIR;
                if (!abs.endsWith("/")) abs += "/";
                abs += outName;

                final String absFinal = abs;
                final String outNameFinal = outName;

                writeScriptToUserPath(absFinal, bodyFinal, () -> {
                    try { Toast.makeText(activity, "Saved: " + outNameFinal, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
                    final String displayName = ScriptsCatalog.displayUserScript(outNameFinal, prefHideScriptLabels);
                    currentScriptRef = new ScriptsCatalog.ScriptRef(displayName, outNameFinal, true, absFinal);
                    selectedScriptName = displayName;
                    ActivityMainBinding current = binding();
                    try { if (current != null && current.tabScripts != null && current.tabScripts.ddScript != null) current.tabScripts.ddScript.setText(displayName, false); } catch (Throwable ignored) {}
                    try { applyScriptsSelectedTextStyling(displayName); } catch (Throwable ignored) {}
                    try { rebuildScriptsList(true); } catch (Throwable ignored) {}
                });
            });
        } catch (Throwable ignored) {
        }
    }

    private void showSaveScriptDialog(String suggested, ScriptNameCallback callback) {
        try {
            final EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setText(suggested == null ? "" : suggested);
            input.setSelection(input.getText().length());

            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Save script")
                    .setMessage("Save to " + ScriptsCatalog.USER_SCRIPTS_DIR)
                    .setView(input)
                    .setPositiveButton("Save", (dialog, which) -> {
                        try {
                            String name = input.getText() == null ? "" : input.getText().toString();
                            if (callback != null) callback.onName(name);
                        } catch (Throwable ignored) {}
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void writeScriptToUserPath(String absPath, String body, Runnable onOk) {
        try {
            if (TextUtils.isEmpty(absPath)) return;

            String dir = absPath;
            int idx = dir.lastIndexOf('/');
            if (idx > 0) dir = dir.substring(0, idx);

            String encoded;
            try {
                encoded = android.util.Base64.encodeToString((body == null ? "" : body).getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
            } catch (Throwable ignored) {
                encoded = "";
            }

            final String cmd = ""
                    + "mkdir -p " + shQuote(dir) + " 2>/dev/null; "
                    + "echo " + shQuote(encoded) + " | base64 -d > " + shQuote(absPath) + " 2>/dev/null; "
                    + "chmod 777 " + shQuote(absPath) + " 2>/dev/null || true; "
                    + "echo OK";

            if (shellCaptureRunner == null) return;
            shellCaptureRunner.runShellCommandCapture(cmd, (exit, out, err) -> activity.runOnUiThread(() -> {
                try {
                    if (exit == 0) {
                        if (onOk != null) onOk.run();
                    } else {
                        appendOutput("[Scripts] Save failed (" + exit + "): " + (err == null ? "" : err) + "\n");
                    }
                } catch (Throwable ignored) {}
            }));
        } catch (Throwable t) {
            appendOutput("[Scripts] Save failed: " + t + "\n");
        }
    }

    private void loadScriptIntoEditor(String name) {
        try {
            if (TextUtils.isEmpty(name)) return;
            selectedScriptName = name;

            final ScriptsCatalog.ScriptRef ref = scriptRefs.get(name);
            currentScriptRef = ref;

            if (ref != null && ref.isUser && !TextUtils.isEmpty(ref.absolutePath)) {
                final String abs = ref.absolutePath;
                final String cmd = "cat " + shQuote(abs) + " 2>/dev/null";
                if (shellCaptureRunner == null) return;
                shellCaptureRunner.runShellCommandCapture(cmd, (exit, out, err) -> activity.runOnUiThread(() -> {
                    String text;
                    if (exit == 0) {
                        text = out == null ? "" : out;
                    } else {
                        text = "# Failed to load script: " + ref.displayName + "\n# " + (err == null ? "" : err);
                    }
                    setEditorText(text);
                    try { editorUi.setEditable(binding(), editorUi.isEditable()); } catch (Throwable ignored) {}
                }));
                return;
            }

            String assetName = (ref != null && !ref.isUser && !TextUtils.isEmpty(ref.fileName)) ? ref.fileName : name;
            String path = ScriptsCatalog.ASSET_SCRIPTS_DIR + "/" + assetName;
            String text;
            try (InputStream in = activity.getAssets().open(path)) {
                text = readAll(in);
            } catch (Throwable t) {
                text = "# Failed to load script: " + name + "\n# " + t;
            }

            setEditorText(text);
            try { editorUi.setEditable(binding(), editorUi.isEditable()); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    private void setEditorText(String text) {
        try {
            ActivityMainBinding binding = binding();
            if (binding != null && binding.tabScripts != null) {
                editorUi.setText(binding, null, text == null ? "" : text);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyScriptsDropdownStyling(View view, String displayName) {
        if (!(view instanceof TextView)) return;
        TextView tv = (TextView) view;
        try {
            if (scriptsDropdownDefaultTextColor == 0) {
                scriptsDropdownDefaultTextColor = tv.getCurrentTextColor();
            }
            ScriptsCatalog.ScriptRef ref = displayName == null ? null : scriptRefs.get(displayName);
            if (ref != null && ref.isUser) {
                tv.setTextColor(0xFF14B5FF);
            } else {
                tv.setTextColor(scriptsDropdownDefaultTextColor);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyScriptsSelectedTextStyling(String displayName) {
        try {
            ActivityMainBinding binding = binding();
            if (binding == null || binding.tabScripts == null || binding.tabScripts.ddScript == null) return;
            TextView tv = binding.tabScripts.ddScript;
            if (scriptsDropdownDefaultTextColor == 0) {
                scriptsDropdownDefaultTextColor = tv.getCurrentTextColor();
            }
            ScriptsCatalog.ScriptRef ref = displayName == null ? null : scriptRefs.get(displayName);
            if (ref != null && ref.isUser) {
                tv.setTextColor(0xFF14B5FF);
            } else {
                tv.setTextColor(scriptsDropdownDefaultTextColor);
            }
        } catch (Throwable ignored) {
        }
    }

    private ActivityMainBinding binding() {
        try {
            return bindingProvider == null ? null : bindingProvider.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private SharedPreferences preferences() {
        return activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    private void appendOutput(String text) {
        try {
            if (outputAppender != null) outputAppender.appendOutput(text);
        } catch (Throwable ignored) {
        }
    }

    private String queryDisplayName(Uri uri) {
        if (uri == null) return null;
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String readAll(InputStream in) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeText(TextView tv) {
        try {
            if (tv == null) return "";
            CharSequence cs = tv.getText();
            return cs == null ? "" : cs.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String sanitizeFilename(String s) {
        if (s == null) return "file.bin";
        String out = s.trim();
        if (out.isEmpty()) out = "file.bin";
        out = out.replaceAll("[\\/\r\n\t\0]", "_");
        out = out.replaceAll("[^a-zA-Z0-9._ -]", "_");
        if (out.length() > 128) out = out.substring(out.length() - 128);
        return out;
    }

    private static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    public interface ShellRunner {
        void runShellCommand(String command);
    }

    public interface ShellCaptureRunner {
        void runShellCommandCapture(String command, ShellCaptureCallback callback);
    }

    public interface ShellCaptureCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface OutputAppender {
        void appendOutput(String text);
    }

    public interface ExternalTextWriter {
        File write(String subdir, String filename, String text);
    }

    private interface ScriptNameCallback {
        void onName(String fileName);
    }

    private static class ScriptDropdownAdapter extends ArrayAdapter<String> {
        private final ArrayList<String> items = new ArrayList<>();

        ScriptDropdownAdapter(Context context, int resource, List<String> sourceItems) {
            super(context, resource, new ArrayList<String>());
            setItems(sourceItems);
        }

        void setItems(List<String> sourceItems) {
            items.clear();
            if (sourceItems != null) items.addAll(sourceItems);
            clear();
            addAll(items);
            notifyDataSetChanged();
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = new ArrayList<>(items);
                    results.count = items.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    clear();
                    addAll(items);
                    notifyDataSetChanged();
                }
            };
        }
    }
}
