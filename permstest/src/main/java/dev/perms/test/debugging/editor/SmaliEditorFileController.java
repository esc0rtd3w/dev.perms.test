package dev.perms.test.debugging.editor;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;
import dev.perms.test.editor.VirtualTextDocument;
import dev.perms.test.editor.VirtualTextEditorController;
import dev.perms.test.ui.IntentGrantUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Owns Smali/Java editor file actions and bridge state for the Debugging tab.
 */
public final class SmaliEditorFileController {
    private static final String KEY_SMALI_EDITOR_AUTO_OPEN_SELECTED = "smali_editor_auto_open_selected";

    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        SharedPreferences getPreferences();
        Handler getMainHandler();
        ExecutorService getIoExecutor();
        ArrayList<File> searchRoots();
        String currentDebuggingWorkRoot();
        String currentDebuggingDexEntry();
        void revealEditorCard();
        void appendOutput(String text);
        void setSearchRunning(boolean running);
    }

    private final Host host;
    private SmaliEditorViewController viewController;
    private VirtualTextEditorController textEditorController;
    private File selectedFile;
    private int openGeneration;

    public SmaliEditorFileController(Host host) {
        this.host = host;
    }

    public void setup() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            if (b.tabDebugging.btnSmaliEditorBrowse != null) {
                b.tabDebugging.btnSmaliEditorBrowse.setOnClickListener(v -> browseFile());
            }
            if (b.tabDebugging.btnSmaliEditorOpen != null) {
                b.tabDebugging.btnSmaliEditorOpen.setOnClickListener(v -> openPath());
            }
            if (b.tabDebugging.btnSmaliEditorReload != null) {
                b.tabDebugging.btnSmaliEditorReload.setOnClickListener(v -> reloadFile());
            }
            if (b.tabDebugging.btnSmaliEditorSave != null) {
                b.tabDebugging.btnSmaliEditorSave.setOnClickListener(v -> saveFile());
            }
            if (b.tabDebugging.btnSmaliEditorOpenExternal != null) {
                b.tabDebugging.btnSmaliEditorOpenExternal.setOnClickListener(v -> openSelectedExternal());
            }
            if (b.tabDebugging.btnSmaliEditorOpenMatchingSmali != null) {
                b.tabDebugging.btnSmaliEditorOpenMatchingSmali.setOnClickListener(v -> openMatchingSmaliForCurrentSource());
            }
            if (b.tabDebugging.btnSmaliEditorOpenMatchingJava != null) {
                b.tabDebugging.btnSmaliEditorOpenMatchingJava.setOnClickListener(v -> openMatchingJavaForCurrentSource());
            }
            updateBridgeState(null);
            restoreAutoOpenOption();
            setupScrollingAndResize();
        } catch (Throwable t) {
            host.appendOutput("[Debugging] Smali editor setup failed: " + t.getMessage() + "\n");
        }
    }

    public void openSmaliFileInInternalEditor(File file, int lineHint) {
        openSourceFileInInternalEditor(file, lineHint);
    }

    public void openJavaFileInInternalEditor(File file, int lineHint) {
        openSourceFileInInternalEditor(file, lineHint);
    }

    public void openSearchResult(SmaliEditorSearch.Result result) {
        if (result == null || result.file == null) return;
        selectedFile = result.file;
        ActivityMainBinding b = binding();
        if (b != null && b.tabDebugging != null && b.tabDebugging.edtSmaliEditorPath != null) {
            b.tabDebugging.edtSmaliEditorPath.setText(result.file.getAbsolutePath());
        }
        updateBridgeState(result.file);
        if (!isAutoOpenSelectedEnabled()) {
            setStatus("Selected " + result.file.getName() + ". Press Open to load it.");
            return;
        }
        boolean internal = b == null || b.tabDebugging == null || b.tabDebugging.chkSmaliUseInternalEditor == null
                || b.tabDebugging.chkSmaliUseInternalEditor.isChecked();
        if (internal) openSmaliFileInInternalEditor(result.file, result.line);
        else openExternal(result.file);
    }

    public void setStatus(String status) {
        try {
            ActivityMainBinding b = binding();
            if (b != null && b.tabDebugging != null && b.tabDebugging.txtSmaliEditorStatus != null) {
                b.tabDebugging.txtSmaliEditorStatus.setText(status == null ? "" : status);
            }
        } catch (Throwable ignored) {
        }
    }

    public void finishError(String label, Throwable t) {
        host.setSearchRunning(false);
        String msg = t == null ? "unknown error" : (t.getClass().getSimpleName() + ": " + t.getMessage());
        setStatus(label + " failed: " + msg);
        host.appendOutput("[Debugging] Smali editor " + label + " failed: " + msg + "\n");
    }

    private SmaliEditorViewController viewController() {
        if (viewController == null) {
            viewController = new SmaliEditorViewController(host.getActivity(), host.getPreferences(), host.getMainHandler());
        }
        return viewController;
    }

    private VirtualTextEditorController textEditorController() {
        ActivityMainBinding b = binding();
        if (textEditorController == null && b != null && b.tabDebugging != null) {
            textEditorController = new VirtualTextEditorController(host.getActivity(),
                    b.tabDebugging.edtSmaliEditorBody,
                    b.tabDebugging.tilSmaliEditorBody,
                    b.tabDebugging.rvSmaliEditorVirtualLines,
                    b.tabDebugging.fastScrollTouchSmaliEditor,
                    b.tabDebugging.fastScrollThumbSmaliEditor,
                    new VirtualTextEditorController.Host() {
                        @Override
                        public void onStatus(String status) {
                            setStatus(status);
                        }

                        @Override
                        public void onError(String label, Throwable error) {
                            finishError(label, error);
                        }
                    });
            textEditorController.setLineFormatter(CodeSyntaxHighlighter::formatSmaliLine);
        }
        return textEditorController;
    }

    private void setupScrollingAndResize() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            textEditorController();
            viewController().setup(b.tabDebugging);
        } catch (Throwable ignored) {
        }
    }

    private void configureSyntaxForFile(File file) {
        try {
            boolean java = isJavaSourceFile(file);
            viewController().setSyntaxMode(java
                    ? SmaliEditorViewController.SyntaxMode.JAVA
                    : SmaliEditorViewController.SyntaxMode.SMALI);
            VirtualTextEditorController editor = textEditorController();
            if (editor != null) {
                editor.setLineFormatter(java ? CodeSyntaxHighlighter::formatJavaLine : CodeSyntaxHighlighter::formatSmaliLine);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isJavaSourceFile(File file) {
        try {
            return file != null && file.getName() != null
                    && file.getName().toLowerCase(Locale.US).endsWith(".java");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSourceFile(File file) {
        try {
            if (file == null || file.getName() == null) return false;
            String lower = file.getName().toLowerCase(Locale.US);
            return lower.endsWith(".smali") || lower.endsWith(".java");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void scheduleSyntaxHighlight(boolean immediate) {
        try {
            viewController().scheduleSyntaxHighlight(immediate);
        } catch (Throwable ignored) {
        }
    }

    private void restoreAutoOpenOption() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null || b.tabDebugging.chkSmaliEditorAutoOpenSelected == null) return;
            boolean enabled = host.getPreferences().getBoolean(KEY_SMALI_EDITOR_AUTO_OPEN_SELECTED, true);
            b.tabDebugging.chkSmaliEditorAutoOpenSelected.setChecked(enabled);
            b.tabDebugging.chkSmaliEditorAutoOpenSelected.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    host.getPreferences().edit()
                            .putBoolean(KEY_SMALI_EDITOR_AUTO_OPEN_SELECTED, isChecked)
                            .apply();
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private boolean isAutoOpenSelectedEnabled() {
        try {
            ActivityMainBinding b = binding();
            return b == null || b.tabDebugging == null
                    || b.tabDebugging.chkSmaliEditorAutoOpenSelected == null
                    || b.tabDebugging.chkSmaliEditorAutoOpenSelected.isChecked();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void browseFile() {
        try {
            boolean openAny = isOpenAnyEnabled();
            File baseDir = openAny ? new File(PermsTestSmaliTools.DEFAULT_ROOT) : browseBaseDir();
            try {
                if (openAny && baseDir != null && !baseDir.exists()) baseDir.mkdirs();
            } catch (Throwable ignored) {
            }
            File startDir = baseDir;
            if (startDir == null) throw new IOException("Debugging work folder is unavailable.");
            showBrowseDialog(startDir, baseDir, openAny);
        } catch (Throwable t) {
            finishError("browse", t);
        }
    }

    private File browseBaseDir() {
        try {
            ActivityMainBinding b = binding();
            String selected = safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtSmaliEditorPath);
            if (!TextUtils.isEmpty(selected)) {
                File chosen = new File(selected);
                if (chosen.isFile()) chosen = chosen.getParentFile();
                if (chosen != null && chosen.isDirectory()) return chosen;
            }
            File debugRoot = new File(PermsTestSmaliTools.DEFAULT_ROOT);
            if (debugRoot.isDirectory() || debugRoot.mkdirs()) return debugRoot;
        } catch (Throwable ignored) {
        }
        ActivityMainBinding b = binding();
        String out = safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtSmaliOutDir);
        return SmaliEditorFiles.browseBaseDir(host.currentDebuggingWorkRoot(), out,
                host.searchRoots(), PermsTestSmaliTools.DEFAULT_ROOT);
    }

    private boolean isOpenAnyEnabled() {
        ActivityMainBinding b = binding();
        return b != null && b.tabDebugging != null && b.tabDebugging.chkSmaliEditorOpenAny != null
                && b.tabDebugging.chkSmaliEditorOpenAny.isChecked();
    }

    private void showBrowseDialog(File startDir, File baseDir, boolean openAny) {
        SmaliEditorBrowseDialog.show(host.getActivity(), startDir, baseDir, openAny, new SmaliEditorBrowseDialog.Host() {
            @Override
            public boolean isAllowedSmaliFile(File file) {
                return isAllowedFile(file);
            }

            @Override
            public void onSmaliFileSelected(File file) {
                selectedFile = file;
                ActivityMainBinding b = binding();
                if (b != null && b.tabDebugging != null && b.tabDebugging.edtSmaliEditorPath != null) {
                    b.tabDebugging.edtSmaliEditorPath.setText(file.getAbsolutePath());
                }
                updateBridgeState(file);
                if (isAutoOpenSelectedEnabled()) {
                    setStatus("Selected " + file.getName() + ". Opening...");
                    host.getMainHandler().post(() -> openPath());
                } else {
                    setStatus("Selected " + file.getName() + ". Press Open to load it.");
                }
            }

            @Override
            public void onSmaliBrowseError(String label, Throwable error) {
                finishError(label, error);
            }
        });
    }

    private File currentSelectedFile() {
        try {
            File file = selectedFile;
            ActivityMainBinding b = binding();
            String path = safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtSmaliEditorPath);
            if (!TextUtils.isEmpty(path)) file = new File(path);
            return file;
        } catch (Throwable ignored) {
            return selectedFile;
        }
    }

    private void updateBridgeState(File file) {
        try {
            File target = file == null ? currentSelectedFile() : file;
            boolean isJava = SourceBridge.isJavaFile(target);
            boolean isSmali = SourceBridge.isSmaliFile(target);
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            if (b.tabDebugging.txtSmaliEditorBridgeStatus != null) {
                b.tabDebugging.txtSmaliEditorBridgeStatus.setText(SourceBridge.describe(target,
                        safeText(b.tabDebugging.edtJadxJavaOutDir),
                        host.currentDebuggingDexEntry(),
                        host.currentDebuggingWorkRoot()));
            }
            if (b.tabDebugging.btnSmaliEditorOpenMatchingSmali != null) {
                b.tabDebugging.btnSmaliEditorOpenMatchingSmali.setEnabled(isJava || isSmali);
            }
            if (b.tabDebugging.btnSmaliEditorOpenMatchingJava != null) {
                b.tabDebugging.btnSmaliEditorOpenMatchingJava.setEnabled(isJava || isSmali);
            }
            if (b.tabDebugging.btnSmaliEditorSave != null) {
                b.tabDebugging.btnSmaliEditorSave.setEnabled(!isJava && isSmali);
            }
        } catch (Throwable ignored) {
        }
    }

    private void setBridgeStatus(String status) {
        try {
            ActivityMainBinding b = binding();
            if (b != null && b.tabDebugging != null && b.tabDebugging.txtSmaliEditorBridgeStatus != null) {
                b.tabDebugging.txtSmaliEditorBridgeStatus.setText(status == null ? "" : status);
            }
        } catch (Throwable ignored) {
        }
    }

    private void openMatchingSmaliForCurrentSource() {
        try {
            File source = currentSelectedFile();
            if (source == null) {
                Toast.makeText(host.getActivity(), "Open a Java or smali file first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (SourceBridge.isSmaliFile(source)) {
                openSmaliFileInInternalEditor(source, 1);
                return;
            }
            SourceBridge.Match match = SourceBridge.findMatchingSmali(source, host.currentDebuggingDexEntry(), host.currentDebuggingWorkRoot());
            setBridgeStatus(match.status);
            if (!match.found()) {
                Toast.makeText(host.getActivity(), match.status, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(host.getActivity(), "Opening matching smali", Toast.LENGTH_SHORT).show();
            openSmaliFileInInternalEditor(match.file, 1);
        } catch (Throwable t) {
            finishError("open matching smali", t);
        }
    }

    private void openMatchingJavaForCurrentSource() {
        try {
            File source = currentSelectedFile();
            if (source == null) {
                Toast.makeText(host.getActivity(), "Open a Java or smali file first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (SourceBridge.isJavaFile(source)) {
                openJavaFileInInternalEditor(source, 1);
                return;
            }
            ActivityMainBinding b = binding();
            SourceBridge.Match match = SourceBridge.findMatchingJava(source,
                    safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtJadxJavaOutDir),
                    host.currentDebuggingWorkRoot());
            setBridgeStatus(match.status);
            if (!match.found()) {
                Toast.makeText(host.getActivity(), match.status, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(host.getActivity(), "Opening matching Java map", Toast.LENGTH_SHORT).show();
            openJavaFileInInternalEditor(match.file, 1);
        } catch (Throwable t) {
            finishError("open matching Java", t);
        }
    }

    private void openPath() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            String path = safeText(b.tabDebugging.edtSmaliEditorPath);
            if (TextUtils.isEmpty(path)) {
                Toast.makeText(host.getActivity(), "Choose a smali/java file first", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(path);
            selectedFile = file;
            updateBridgeState(file);
            if (b.tabDebugging.chkSmaliUseInternalEditor == null || b.tabDebugging.chkSmaliUseInternalEditor.isChecked()) {
                openSmaliFileInInternalEditor(file, 1);
            } else {
                openExternal(file);
            }
        } catch (Throwable t) {
            finishError("open", t);
        }
    }

    private void openSourceFileInInternalEditor(File file, int lineHint) {
        try {
            if (file == null || !file.isFile()) throw new IOException("File not found: " + (file == null ? "" : file.getAbsolutePath()));
            if (!isSourceFile(file)) throw new IOException("Not a .smali/.java file: " + file.getName());

            final File target = file.getCanonicalFile();
            final long size = target.length();
            configureSyntaxForFile(target);
            host.revealEditorCard();
            selectedFile = target;
            ActivityMainBinding b = binding();
            if (b != null && b.tabDebugging != null && b.tabDebugging.edtSmaliEditorPath != null) {
                b.tabDebugging.edtSmaliEditorPath.setText(target.getAbsolutePath());
            }

            final int generation = ++openGeneration;
            final int safeLineHint = Math.max(1, lineHint);
            final boolean useVirtualEditor = size > SmaliEditorFiles.TEXT_EDITOR_MAX_BYTES;
            setFileButtonsEnabled(false);
            setStatus("Loading " + target.getName() + "...");
            try {
                viewController().cancelSyntaxHighlight();
            } catch (Throwable ignored) {
            }

            host.getIoExecutor().execute(() -> {
                try {
                    if (useVirtualEditor) {
                        final VirtualTextDocument document = VirtualTextDocument.load(target);
                        host.getMainHandler().post(() -> finishVirtualOpen(generation, target, document, safeLineHint, size));
                    } else {
                        final String text = SmaliEditorFiles.readUtf8(target, SmaliEditorFiles.TEXT_EDITOR_MAX_BYTES);
                        host.getMainHandler().post(() -> finishTextOpen(generation, target, text, safeLineHint));
                    }
                } catch (Throwable t) {
                    host.getMainHandler().post(() -> finishInternalOpenError(generation, t));
                }
            });
        } catch (Throwable t) {
            finishError("internal open", t);
        }
    }

    private void finishTextOpen(int generation, File file, String text, int lineHint) {
        try {
            if (generation != openGeneration) return;
            setFileButtonsEnabled(true);
            selectedFile = file;
            ActivityMainBinding b = binding();
            if (b != null && b.tabDebugging != null) {
                if (b.tabDebugging.edtSmaliEditorPath != null) b.tabDebugging.edtSmaliEditorPath.setText(file.getAbsolutePath());
                updateBridgeState(file);
                VirtualTextEditorController editor = textEditorController();
                if (editor != null) editor.showText(file, text);
                if (b.tabDebugging.edtSmaliEditorBody != null) {
                    int len = text == null ? 0 : text.length();
                    int pos = SmaliEditorFiles.offsetForLine(text, lineHint);
                    try {
                        b.tabDebugging.edtSmaliEditorBody.setSelection(Math.max(0, Math.min(pos, len)));
                    } catch (Throwable ignored) {
                    }
                    if (CodeSyntaxHighlighter.canHighlightLength(len)) {
                        scheduleSyntaxHighlight(true);
                    }
                    try {
                        b.tabDebugging.edtSmaliEditorBody.requestFocus();
                    } catch (Throwable ignored) {
                    }
                }
            }
            String suffix = lineHint > 1 ? " at line " + lineHint : "";
            String highlightSuffix = CodeSyntaxHighlighter.canHighlightLength(text == null ? 0 : text.length()) ? "" : " Syntax highlight skipped for large text.";
            setStatus("Loaded " + file.getName() + suffix + "." + highlightSuffix
                    + (SourceBridge.isJavaFile(file) ? " Java output is a read-only map; open matching smali to patch/repack." : ""));
        } catch (Throwable t) {
            finishError("internal open", t);
        }
    }

    private void finishVirtualOpen(int generation, File file, VirtualTextDocument document, int lineHint, long size) {
        try {
            if (generation != openGeneration) return;
            setFileButtonsEnabled(true);
            selectedFile = file;
            ActivityMainBinding b = binding();
            if (b != null && b.tabDebugging != null && b.tabDebugging.edtSmaliEditorPath != null) {
                b.tabDebugging.edtSmaliEditorPath.setText(file.getAbsolutePath());
            }
            updateBridgeState(file);
            try {
                viewController().cancelSyntaxHighlight();
            } catch (Throwable ignored) {
            }
            VirtualTextEditorController editor = textEditorController();
            if (editor != null) editor.showDocument(document, lineHint);
            try {
                viewController().cancelSyntaxHighlight();
            } catch (Throwable ignored) {
            }
            String suffix = lineHint > 1 ? " at line " + lineHint : "";
            setStatus("Loaded " + file.getName() + suffix + " in scalable editor (" + document.getLineCount() + " lines, " + formatByteCount(size) + ")."
                    + (SourceBridge.isJavaFile(file) ? " Java output is a read-only map; open matching smali to patch/repack." : ""));
        } catch (Throwable t) {
            finishError("virtual open", t);
        }
    }

    private void finishInternalOpenError(int generation, Throwable t) {
        if (generation != openGeneration) return;
        setFileButtonsEnabled(true);
        finishError("internal open", t);
    }

    private void setFileButtonsEnabled(boolean enabled) {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            if (b.tabDebugging.btnSmaliEditorBrowse != null) b.tabDebugging.btnSmaliEditorBrowse.setEnabled(enabled);
            if (b.tabDebugging.btnSmaliEditorOpen != null) b.tabDebugging.btnSmaliEditorOpen.setEnabled(enabled);
            if (b.tabDebugging.btnSmaliEditorReload != null) b.tabDebugging.btnSmaliEditorReload.setEnabled(enabled);
            if (b.tabDebugging.btnSmaliEditorSave != null) b.tabDebugging.btnSmaliEditorSave.setEnabled(enabled && !SourceBridge.isJavaFile(currentSelectedFile()));
            if (b.tabDebugging.btnSmaliEditorOpenExternal != null) b.tabDebugging.btnSmaliEditorOpenExternal.setEnabled(enabled);
            if (b.tabDebugging.btnSmaliEditorOpenMatchingSmali != null) b.tabDebugging.btnSmaliEditorOpenMatchingSmali.setEnabled(enabled && currentSelectedFile() != null);
            if (b.tabDebugging.btnSmaliEditorOpenMatchingJava != null) b.tabDebugging.btnSmaliEditorOpenMatchingJava.setEnabled(enabled && currentSelectedFile() != null);
        } catch (Throwable ignored) {
        }
    }

    private void reloadFile() {
        try {
            ActivityMainBinding b = binding();
            File file = selectedFile;
            String path = safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtSmaliEditorPath);
            if (file == null && !TextUtils.isEmpty(path)) file = new File(path);
            if (file == null) {
                Toast.makeText(host.getActivity(), "Choose a smali/java file first", Toast.LENGTH_SHORT).show();
                return;
            }
            openSmaliFileInInternalEditor(file, 1);
        } catch (Throwable t) {
            finishError("reload", t);
        }
    }

    private void saveFile() {
        try {
            ActivityMainBinding b = binding();
            if (b == null || b.tabDebugging == null) return;
            String path = safeText(b.tabDebugging.edtSmaliEditorPath);
            File file = TextUtils.isEmpty(path) ? selectedFile : new File(path);
            if (file == null) throw new IOException("Choose a smali/java file first.");
            if (SourceBridge.isJavaFile(file)) {
                throw new IOException("Generated Java output is read-only in this workflow. Open matching smali to patch and repack.");
            }
            if (!isOpenAnyEnabled() && !isAllowedFile(file)) {
                throw new IOException("Refusing to save outside the current debugging source folders.");
            }
            VirtualTextEditorController editor = textEditorController();
            if (editor != null) {
                editor.writeTo(file);
            } else {
                String text = safeText(b.tabDebugging.edtSmaliEditorBody);
                SmaliEditorFiles.writeUtf8(file, text);
            }
            selectedFile = file;
            setStatus("Saved " + file.getName() + ".");
            host.appendOutput("[Debugging] Saved smali file: " + file.getAbsolutePath() + "\n");
        } catch (Throwable t) {
            finishError("save", t);
        }
    }

    private boolean isAllowedFile(File file) {
        return SmaliEditorFiles.isAllowedSmaliFile(file, host.searchRoots());
    }

    private void openSelectedExternal() {
        try {
            ActivityMainBinding b = binding();
            File file = selectedFile;
            String path = safeText(b == null || b.tabDebugging == null ? null : b.tabDebugging.edtSmaliEditorPath);
            if (file == null && !TextUtils.isEmpty(path)) file = new File(path);
            if (file == null) {
                Toast.makeText(host.getActivity(), "Choose a smali/java file first", Toast.LENGTH_SHORT).show();
                return;
            }
            openExternal(file);
        } catch (Throwable t) {
            finishError("external open", t);
        }
    }

    private void openExternal(File file) {
        try {
            if (file == null || !file.isFile()) throw new IOException("File not found: " + (file == null ? "" : file.getAbsolutePath()));
            Activity activity = host.getActivity();
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setDataAndType(uri, "text/plain");
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), file.getName(), uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            IntentGrantUtils.grantReadForIntent(activity, intent, uri);
            activity.startActivity(intent);
            setStatus("Opened external editor for " + file.getName() + ".");
        } catch (Throwable t) {
            finishError("external open", t);
        }
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.getBinding();
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

    private static String formatByteCount(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024.0) return String.format(Locale.US, "%.1f KB", value);
        value /= 1024.0;
        if (value < 1024.0) return String.format(Locale.US, "%.1f MB", value);
        value /= 1024.0;
        return String.format(Locale.US, "%.1f GB", value);
    }
}
