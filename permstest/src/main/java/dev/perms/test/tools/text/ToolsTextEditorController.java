package dev.perms.test.tools.text;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabToolsBinding;
import dev.perms.test.editor.SourceSyntaxHighlighter;
import dev.perms.test.editor.VirtualTextDocument;
import dev.perms.test.editor.VirtualTextEditorController;
import dev.perms.test.ui.CollapsibleGroupboxController;
import dev.perms.test.ui.DropdownUi;

/**
 * Basic Tools-tab text editor built on the shared virtual source editor surface.
 *
 * File paths and external document URIs both load through this controller. Small files use the
 * normal EditText path; larger files switch to the line-backed virtual editor so the Tools tab
 * does not need a second custom text-editing backend.
 */
public final class ToolsTextEditorController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
        void showTab(int index);
    }

    public static final String EXTRA_OPEN_TEXT_EDITOR_URI = "dev.perms.test.extra.OPEN_TEXT_EDITOR_URI";
    public static final String EXTRA_OPEN_TEXT_EDITOR_LABEL = "dev.perms.test.extra.OPEN_TEXT_EDITOR_LABEL";
    public static final String EXTRA_SCROLL_TO_TEXT_EDITOR = "dev.perms.test.extra.SCROLL_TO_TEXT_EDITOR";

    private static final int TAB_TOOLS = 8;
    private static final int TEXT_MAX_BYTES = 2 * 1024 * 1024;
    private static final int VIRTUAL_MAX_LINES = 20_000;

    private static final String SYNTAX_PLAIN = "Plain Text";
    private static final String SYNTAX_SHELL = "Shell/Bash";
    private static final String SYNTAX_JSON = "JSON";
    private static final String SYNTAX_WEB = "Web (HTML/CSS/JS)";
    private static final String SYNTAX_PROPERTIES = "Properties/INI";
    private static final String SYNTAX_SMALI = "Smali";

    private final Host host;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PermsTestToolsTextEditor");
        thread.setDaemon(true);
        return thread;
    });

    private ActivityResultLauncher<Intent> pickFileLauncher;
    private VirtualTextEditorController editor;
    private Uri currentUri;
    private File currentFile;
    private String currentLabel;
    private String currentSyntax = SYNTAX_PLAIN;

    public ToolsTextEditorController(Host host) {
        this.host = host;
    }

    public void registerActivityResults() {
        try {
            if (pickFileLauncher != null) return;
            Activity activity = activity();
            if (!(activity instanceof ComponentActivity)) return;
            pickFileLauncher = ((ComponentActivity) activity).registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handlePickedFile);
        } catch (Throwable ignored) {
        }
    }

    public void bind() {
        TabToolsBinding tab = tab();
        if (tab == null) return;
        try {
            setupEditor();
            setupSyntaxDropdown();
            tab.btnToolsTextBrowse.setOnClickListener(v -> launchFilePicker());
            tab.btnToolsTextLoad.setOnClickListener(v -> loadFromPathField());
            tab.btnToolsTextSave.setOnClickListener(v -> saveCurrentText());
            tab.btnToolsTextClear.setOnClickListener(v -> clearEditor());
            status("Text editor ready. Plain text is the default syntax mode.");
        } catch (Throwable t) {
            status("Text editor setup failed: " + safeMessage(t));
        }
    }

    public boolean handleIncomingIntent(Intent intent) {
        try {
            if (intent == null) return false;
            Uri uri = null;
            try { uri = intent.getParcelableExtra(EXTRA_OPEN_TEXT_EDITOR_URI); } catch (Throwable ignored) {}
            if (uri == null) return false;

            String label = intent.getStringExtra(EXTRA_OPEN_TEXT_EDITOR_LABEL);
            if (TextUtils.isEmpty(label)) label = queryDisplayName(uri);
            if (TextUtils.isEmpty(label)) label = uri.toString();

            persistUriGrant(intent, uri);
            currentUri = uri;
            currentFile = null;
            currentLabel = label;
            if (tab() != null && tab().edtToolsTextPath != null) tab().edtToolsTextPath.setText(uri.toString());
            if (host != null) host.showTab(TAB_TOOLS);
            revealTextEditorCard();
            if (intent.getBooleanExtra(EXTRA_SCROLL_TO_TEXT_EDITOR, false)) scrollToTextEditorCard();
            loadFromUri(uri, label, true);
            return true;
        } catch (Throwable t) {
            status("Text editor intent failed: " + safeMessage(t));
            return true;
        }
    }

    private void revealTextEditorCard() {
        final TabToolsBinding tab = tab();
        if (tab == null || tab.cardToolsTextEditor == null) return;
        try { CollapsibleGroupboxController.revealGroupboxesContainingAfterLayout(tab.cardToolsTextEditor); } catch (Throwable ignored) {}
    }

    private void scrollToTextEditorCard() {
        final TabToolsBinding tab = tab();
        if (tab == null || tab.tabTools == null || tab.cardToolsTextEditor == null) return;
        Runnable r = () -> {
            try {
                View target = tab.cardToolsTextEditor;
                tab.tabTools.smoothScrollTo(0, Math.max(0, target.getTop() - 12));
            } catch (Throwable ignored) {
            }
        };
        try { tab.tabTools.post(r); } catch (Throwable ignored) {}
        try { tab.tabTools.postDelayed(r, 250L); } catch (Throwable ignored) {}
    }

    private void setupEditor() {
        if (editor != null) return;
        TabToolsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null) return;
        editor = new VirtualTextEditorController(activity,
                tab.edtToolsTextBody,
                tab.tilToolsTextBody,
                tab.rvToolsTextVirtualLines,
                tab.fastScrollTouchToolsText,
                tab.fastScrollThumbToolsText,
                new VirtualTextEditorController.Host() {
                    @Override
                    public void onStatus(String status) {
                        ToolsTextEditorController.this.status(status);
                    }

                    @Override
                    public void onError(String label, Throwable error) {
                        ToolsTextEditorController.this.status(label + " failed: " + safeMessage(error));
                    }
                });
        applySyntaxFormatter();
    }

    private void setupSyntaxDropdown() {
        TabToolsBinding tab = tab();
        Activity activity = activity();
        if (tab == null || activity == null || tab.ddToolsTextSyntax == null) return;
        String[] values = {SYNTAX_PLAIN, SYNTAX_SHELL, SYNTAX_JSON, SYNTAX_WEB, SYNTAX_PROPERTIES, SYNTAX_SMALI};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, values);
        tab.ddToolsTextSyntax.setAdapter(adapter);
        DropdownUi.bindExposedDropdown(activity, tab.tilToolsTextSyntax, tab.ddToolsTextSyntax, () -> DropdownUi.showDropdown(tab.ddToolsTextSyntax));
        tab.ddToolsTextSyntax.setText(SYNTAX_PLAIN, false);
        tab.ddToolsTextSyntax.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < values.length) {
                currentSyntax = values[position];
                applySyntaxToCurrentText();
            }
        });
    }

    private void launchFilePicker() {
        if (pickFileLauncher == null) {
            Toast.makeText(activity(), "File picker unavailable", Toast.LENGTH_SHORT).show();
            status("File picker unavailable. Restart PermsTest if the Tools tab opened before initialization finished.");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            pickFileLauncher.launch(intent);
        } catch (Throwable t) {
            status("Browse failed: " + safeMessage(t));
        }
    }

    private void handlePickedFile(ActivityResult result) {
        try {
            if (result == null || result.getResultCode() != Activity.RESULT_OK) return;
            Intent data = result.getData();
            Uri uri = data == null ? null : data.getData();
            if (uri == null) return;
            persistUriGrant(data, uri);
            String label = queryDisplayName(uri);
            if (TextUtils.isEmpty(label)) label = uri.toString();
            currentUri = uri;
            currentFile = null;
            currentLabel = label;
            if (tab() != null && tab().edtToolsTextPath != null) tab().edtToolsTextPath.setText(uri.toString());
            loadFromUri(uri, label, true);
        } catch (Throwable t) {
            status("Picked text file failed: " + safeMessage(t));
        }
    }

    private void loadFromPathField() {
        try {
            String raw = text(tab() == null ? null : tab().edtToolsTextPath);
            if (TextUtils.isEmpty(raw)) {
                status("Enter a text file path or browse for a document first.");
                return;
            }
            Uri uri = parseContentUri(raw);
            if (uri != null) {
                currentUri = uri;
                currentFile = null;
                String label = queryDisplayName(uri);
                if (TextUtils.isEmpty(label)) label = uri.toString();
                currentLabel = label;
                loadFromUri(uri, label, false);
                return;
            }
            File file = new File(raw);
            currentUri = null;
            currentFile = file;
            currentLabel = file.getName();
            loadFromFile(file);
        } catch (Throwable t) {
            status("Load failed: " + safeMessage(t));
        }
    }

    private void loadFromFile(File file) {
        if (file == null) {
            status("Missing text file path.");
            return;
        }
        status("Loading text file...");
        worker.execute(() -> {
            try {
                if (!file.isFile()) throw new java.io.IOException("File not found: " + file.getAbsolutePath());
                long length = file.length();
                if (length > TEXT_MAX_BYTES) {
                    VirtualTextDocument document = VirtualTextDocument.load(file);
                    runOnUi(() -> showDocument(file, document, "Loaded " + document.getLineCount() + " line" + suffix(document.getLineCount())
                            + " from " + file.getName() + " in virtual mode."));
                    return;
                }
                String text = readFileText(file);
                runOnUi(() -> showText(file, text, "Loaded " + byteCount(text) + " byte" + suffix(byteCount(text))
                        + " from " + file.getName() + "."));
            } catch (Throwable t) {
                runOnUi(() -> status("Load failed: " + safeMessage(t)));
            }
        });
    }

    private void loadFromUri(Uri uri, String label, boolean fromExternalIntent) {
        if (uri == null) {
            status("Missing document URI.");
            return;
        }
        status("Loading text document...");
        ContentResolver resolver = resolver();
        worker.execute(() -> {
            try {
                String text = readUriText(resolver, uri);
                File source = null;
                if (!TextUtils.isEmpty(label)) source = new File(label);
                final File sourceFile = source;
                if (shouldUseVirtualEditor(text)) {
                    VirtualTextDocument document = VirtualTextDocument.fromText(sourceFile, text);
                    runOnUi(() -> showDocument(sourceFile, document, "Loaded " + document.getLineCount()
                            + " line" + suffix(document.getLineCount()) + " from " + safeLabel(label, uri)
                            + " in virtual mode."));
                } else {
                    runOnUi(() -> showText(sourceFile, text, "Loaded " + byteCount(text) + " byte" + suffix(byteCount(text))
                            + " from " + safeLabel(label, uri) + (fromExternalIntent ? " via external file intent." : ".")));
                }
            } catch (Throwable t) {
                runOnUi(() -> status("Load failed: " + safeMessage(t)));
            }
        });
    }

    private void showText(File sourceFile, String text, String message) {
        try {
            setupEditor();
            if (editor != null) editor.showText(sourceFile, text == null ? "" : text);
            applySyntaxToCurrentText();
            status(message);
            output("[Tools Text] " + message + "\n");
        } catch (Throwable t) {
            status("Display failed: " + safeMessage(t));
        }
    }

    private void showDocument(File sourceFile, VirtualTextDocument document, String message) {
        try {
            setupEditor();
            if (editor != null) editor.showDocument(document, 1);
            applySyntaxFormatter();
            status(message);
            output("[Tools Text] " + message + "\n");
        } catch (Throwable t) {
            status("Display failed: " + safeMessage(t));
        }
    }

    private void saveCurrentText() {
        setupEditor();
        if (editor == null) {
            status("Text editor is not ready.");
            return;
        }
        Uri uri = currentUri;
        File file = currentFile;
        String path = text(tab() == null ? null : tab().edtToolsTextPath);
        if (file == null && uri == null && !TextUtils.isEmpty(path)) {
            Uri parsed = parseContentUri(path);
            if (parsed != null) uri = parsed;
            else file = new File(path);
        }
        final Uri saveUri = uri;
        final File saveFile = file;
        if (saveUri == null && saveFile == null) {
            status("Load or enter a text file before saving.");
            return;
        }
        final String textToSave = editor.getText();
        status("Saving text...");
        worker.execute(() -> {
            try {
                if (saveUri != null) {
                    writeUriText(saveUri, textToSave);
                    runOnUi(() -> status("Saved text to " + safeLabel(currentLabel, saveUri) + "."));
                } else {
                    writeFileText(saveFile, textToSave);
                    runOnUi(() -> status("Saved text to " + saveFile.getAbsolutePath() + "."));
                }
            } catch (Throwable t) {
                runOnUi(() -> status("Save failed: " + safeMessage(t)));
            }
        });
    }

    private void clearEditor() {
        try {
            currentUri = null;
            currentFile = null;
            currentLabel = null;
            if (editor != null) editor.clear();
            if (tab() != null && tab().edtToolsTextPath != null) tab().edtToolsTextPath.setText("");
            status("Text editor cleared.");
        } catch (Throwable ignored) {
        }
    }

    private void applySyntaxToCurrentText() {
        try {
            setupEditor();
            applySyntaxFormatter();
            if (editor == null || !editor.isTextMode()) {
                status("Syntax mode: " + currentSyntax + ". Virtual rows will use this formatter.");
                return;
            }
            EditText text = tab() == null ? null : tab().edtToolsTextBody;
            if (text == null || text.getText() == null) return;
            int start = Math.max(0, text.getSelectionStart());
            int end = Math.max(0, text.getSelectionEnd());
            String value = text.getText().toString();
            text.setText(value);
            int length = text.getText() == null ? 0 : text.getText().length();
            try { text.setSelection(Math.min(start, length), Math.min(end, length)); } catch (Throwable ignored) {}
            if (SYNTAX_PLAIN.equals(currentSyntax)) {
                status("Syntax mode: Plain Text.");
                return;
            }
            if (!SourceSyntaxHighlighter.canHighlightLength(value.length())) {
                status("Syntax mode set to " + currentSyntax + ", but highlighting is skipped for this size.");
                return;
            }
            if (SYNTAX_SHELL.equals(currentSyntax)) SourceSyntaxHighlighter.applyShell(text);
            else if (SYNTAX_JSON.equals(currentSyntax)) SourceSyntaxHighlighter.applyJson(text);
            else if (SYNTAX_WEB.equals(currentSyntax)) SourceSyntaxHighlighter.applyWeb(text);
            else if (SYNTAX_PROPERTIES.equals(currentSyntax)) SourceSyntaxHighlighter.applyProperties(text);
            else if (SYNTAX_SMALI.equals(currentSyntax)) SourceSyntaxHighlighter.applySmali(text);
            status("Syntax mode: " + currentSyntax + ".");
        } catch (Throwable t) {
            status("Syntax update failed: " + safeMessage(t));
        }
    }

    private void applySyntaxFormatter() {
        if (editor == null) return;
        if (SYNTAX_SHELL.equals(currentSyntax)) editor.setLineFormatter(SourceSyntaxHighlighter::formatShellLine);
        else if (SYNTAX_JSON.equals(currentSyntax)) editor.setLineFormatter(SourceSyntaxHighlighter::formatJsonLine);
        else if (SYNTAX_WEB.equals(currentSyntax)) editor.setLineFormatter(SourceSyntaxHighlighter::formatWebLine);
        else if (SYNTAX_PROPERTIES.equals(currentSyntax)) editor.setLineFormatter(SourceSyntaxHighlighter::formatPropertiesLine);
        else if (SYNTAX_SMALI.equals(currentSyntax)) editor.setLineFormatter(SourceSyntaxHighlighter::formatSmaliLine);
        else editor.setLineFormatter(null);
    }

    private void persistUriGrant(Intent data, Uri uri) {
        Activity activity = activity();
        if (activity == null || data == null || uri == null) return;
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags != 0) activity.getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Throwable ignored) {
        }
    }

    private Uri parseContentUri(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            Uri uri = Uri.parse(raw.trim());
            String scheme = uri == null ? null : uri.getScheme();
            if ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)) return uri;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String readFileText(File file) throws java.io.IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return readStreamText(in);
        }
    }

    private String readUriText(ContentResolver resolver, Uri uri) throws java.io.IOException {
        if (resolver == null) throw new java.io.IOException("Content resolver unavailable.");
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("Unable to open document input stream.");
            return readStreamText(in);
        }
    }

    private String readStreamText(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private void writeUriText(Uri uri, String text) throws java.io.IOException {
        ContentResolver resolver = resolver();
        if (resolver == null) throw new java.io.IOException("Content resolver unavailable.");
        try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
            if (out == null) throw new java.io.IOException("Unable to open document output stream.");
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeFileText(File file, String text) throws java.io.IOException {
        if (file == null) throw new java.io.IOException("Missing output path.");
        File parent = file.getCanonicalFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new java.io.IOException("Unable to create output folder: " + parent.getAbsolutePath());
        }
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file, false))) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private boolean shouldUseVirtualEditor(String text) {
        try {
            if (text == null) return false;
            if (text.getBytes(StandardCharsets.UTF_8).length > TEXT_MAX_BYTES) return true;
            int lines = 1;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n' && ++lines > VIRTUAL_MAX_LINES) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String queryDisplayName(Uri uri) {
        Activity activity = activity();
        if (activity == null || uri == null) return null;
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
        }
        return null;
    }

    private int byteCount(String text) {
        try { return (text == null ? "" : text).getBytes(StandardCharsets.UTF_8).length; }
        catch (Throwable ignored) { return text == null ? 0 : text.length(); }
    }

    private String safeLabel(String label, Uri uri) {
        if (!TextUtils.isEmpty(label)) return label;
        return uri == null ? "document" : uri.toString();
    }

    private String suffix(long count) {
        return count == 1L ? "" : "s";
    }

    private String text(EditText editText) {
        return editText == null || editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void status(String message) {
        try {
            TabToolsBinding tab = tab();
            if (tab != null && tab.txtToolsTextStatus != null) tab.txtToolsTextStatus.setText(message == null ? "" : message);
        } catch (Throwable ignored) {
        }
    }

    private void output(String message) {
        if (host != null && !TextUtils.isEmpty(message)) host.appendOutput(message);
    }

    private void runOnUi(Runnable runnable) {
        Activity activity = activity();
        if (activity == null || runnable == null) return;
        activity.runOnUiThread(runnable);
    }

    private ContentResolver resolver() {
        Activity activity = activity();
        return activity == null ? null : activity.getContentResolver();
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.getBinding();
    }

    private TabToolsBinding tab() {
        ActivityMainBinding binding = binding();
        return binding == null ? null : binding.tabTools;
    }

    private String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.getClass().getSimpleName() : msg;
    }
}
