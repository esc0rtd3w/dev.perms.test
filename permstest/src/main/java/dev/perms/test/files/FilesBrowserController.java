package dev.perms.test.files;

import dev.perms.test.databinding.ActivityMainBinding;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** Activity-side controller for Files tab browser state, binding, and refresh. */
public final class FilesBrowserController {
    private static final String PREF_SCAN_ROOT_DIRECTORY = "files_scan_root_directories";
    private static final String PREF_SCAN_CURRENT_DIRECTORY = "files_scan_current_directory";

    public interface ShellCallback {
        void onComplete(int exitCode, String stdout, String stderr);
    }

    public interface PromptCallback {
        void onText(String value);
    }

    public interface Host {
        int dp(int value);
        void appendOutput(String text);
        void toast(String message);
        String shellQuote(String value);
        boolean canUseShizuku();
        void runShizukuCommandCapture(String command, ShellCallback callback);
        void runShellCommandCapture(String command, ShellCallback callback);
        void showTextPromptDialog(String title, String hint, String preset, PromptCallback callback);
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final ExecutorService io;
    private final Handler mainHandler;
    private final SharedPreferences prefs;
    private final Host host;
    private final String keyUseShizuku;
    private final String keyOpenKnown;
    private final String keyInternalApkInstall;
    private final String keyLastLeftCwd;
    private final String keyLastRightCwd;
    private final String keyLastSplit;
    private final String keyLastActiveRight;

    private FilesPaneState filesLeft;
    private FilesPaneState filesRight;
    private boolean filesActiveIsRight;
    private final FilesClipboardState filesClipboard = new FilesClipboardState();
    private EditText filesFilterEdit;
    private TextView filesStorageLabel;
    private MaterialCheckBox filesScanRootDirectoryCheckBox;
    private MaterialCheckBox filesScanCurrentDirectoryCheckBox;
    private String filesFilterText = "";
    private int filesStorageStatsSeq;
    private boolean suppressScanRootDirectoryChange;
    private View filesBtnPaste;
    private View filesBtnMkdir;
    private FilesPackageIconLoader filesPackageIconLoader;
    private FilesEntryActions filesEntryActions;
    private FilesMutationActions filesMutationActions;

    private final FilesAdapter.Callbacks filesAdapterCallbacks = new FilesAdapter.Callbacks() {
        @Override
        public int dp(int value) {
            return host.dp(value);
        }

        @Override
        public Drawable cachedPackageIcon(FileEntry entry) {
            return filesPackageIconLoader == null ? null : filesPackageIconLoader.cachedIcon(entry);
        }

        @Override
        public String fallbackIconFor(FileEntry entry) {
            return FilesBrowserUtils.iconFor(entry);
        }

        @Override
        public boolean isPackageArchive(String name) {
            return FilesBrowserUtils.isPackageArchive(name);
        }

        @Override
        public void schedulePackageIconLoad(FileEntry entry) {
            if (filesPackageIconLoader != null) filesPackageIconLoader.scheduleLoad(entry);
        }
    };

    public FilesBrowserController(@NonNull AppCompatActivity activity,
                                  @NonNull ActivityMainBinding binding,
                                  @NonNull ExecutorService io,
                                  @NonNull Handler mainHandler,
                                  @NonNull SharedPreferences prefs,
                                  @NonNull Host host,
                                  @NonNull String keyUseShizuku,
                                  @NonNull String keyOpenKnown,
                                  @NonNull String keyInternalApkInstall,
                                  @NonNull String keyLastLeftCwd,
                                  @NonNull String keyLastRightCwd,
                                  @NonNull String keyLastSplit,
                                  @NonNull String keyLastActiveRight) {
        this.activity = activity;
        this.binding = binding;
        this.io = io;
        this.mainHandler = mainHandler;
        this.prefs = prefs;
        this.host = host;
        this.keyUseShizuku = keyUseShizuku;
        this.keyOpenKnown = keyOpenKnown;
        this.keyInternalApkInstall = keyInternalApkInstall;
        this.keyLastLeftCwd = keyLastLeftCwd;
        this.keyLastRightCwd = keyLastRightCwd;
        this.keyLastSplit = keyLastSplit;
        this.keyLastActiveRight = keyLastActiveRight;
    }

    public void bind(String restoreLeftCwd,
                     String restoreRightCwd,
                     Boolean restoreSplit,
                     Boolean restoreActiveRight) {
        try {
            if (binding.tabFiles == null) return;

            final String rememberedLeft = prefs.getString(keyLastLeftCwd, "");
            final String rememberedRight = prefs.getString(keyLastRightCwd, "");
            final String leftCwd = (restoreLeftCwd != null && !restoreLeftCwd.trim().isEmpty())
                    ? restoreLeftCwd
                    : (!TextUtils.isEmpty(rememberedLeft) ? rememberedLeft : "/sdcard");
            final String rightCwd = (restoreRightCwd != null && !restoreRightCwd.trim().isEmpty())
                    ? restoreRightCwd
                    : (!TextUtils.isEmpty(rememberedRight) ? rememberedRight : leftCwd);
            filesLeft = new FilesPaneState(leftCwd, binding.tabFiles.listFilesLeft, binding.tabFiles.txtFilesPathLeft);
            filesRight = new FilesPaneState(rightCwd, binding.tabFiles.listFilesRight, binding.tabFiles.txtFilesPathRight);
            filesPackageIconLoader = new FilesPackageIconLoader(
                    activity,
                    io,
                    mainHandler,
                    FilesEntryActions::canAppReadFile,
                    this::notifyAdaptersChanged);

            filesEntryActions = new FilesEntryActions(
                    activity,
                    (cmd, cb) -> host.runShellCommandCapture(cmd, (exit, out, err) -> {
                        if (cb != null) cb.onComplete(exit, out, err);
                    }),
                    new FilesEntryActions.Callbacks() {
                        @Override
                        public void appendOutput(String text) {
                            host.appendOutput(text);
                        }

                        @Override
                        public void toast(String message) {
                            host.toast(message);
                        }

                        @Override
                        public String shellQuote(String value) {
                            return host.shellQuote(value);
                        }

                        @Override
                        public boolean useInternalApkInstall() {
                            return internalApkInstallEnabled();
                        }
                    });

            filesMutationActions = new FilesMutationActions(activity, filesClipboard, new FilesMutationActions.Callbacks() {
                @Override
                public FilesPaneState activePane() {
                    return activePaneInternal();
                }

                @Override
                public FilesPaneState leftPane() {
                    return filesLeft;
                }

                @Override
                public FilesPaneState rightPane() {
                    return filesRight;
                }

                @Override
                public FilesPaneState otherPane(FilesPaneState pane) {
                    return otherPaneInternal(pane);
                }

                @Override
                public boolean splitVisible() {
                    return splitVisibleInternal();
                }

                @Override
                public String paneLabel(FilesPaneState pane) {
                    return paneLabelInternal(pane);
                }

                @Override
                public void appendOutput(String text) {
                    host.appendOutput(text);
                }

                @Override
                public String shellQuote(String value) {
                    return host.shellQuote(value);
                }

                @Override
                public void runShellCommand(String command, FilesMutationActions.ShellCallback callback) {
                    host.runShellCommandCapture(command, (exit, out, err) -> {
                        if (callback != null) callback.onComplete(exit, out, err);
                    });
                }

                @Override
                public void runOnUiThread(Runnable runnable) {
                    activity.runOnUiThread(runnable);
                }

                @Override
                public void refreshPane(FilesPaneState pane) {
                    FilesBrowserController.this.refreshPane(pane);
                }

                @Override
                public void updateStatusLine() {
                    FilesBrowserController.this.updateStatusLine();
                }

                @Override
                public void showTextPromptDialog(String title, String hint, String preset, FilesMutationActions.PromptCallback callback) {
                    host.showTextPromptDialog(title, hint, preset, value -> {
                        if (callback != null) callback.onText(value);
                    });
                }
            });

            filesLeft.adapter = new FilesAdapter(activity, filesLeft.entries, filesAdapterCallbacks);
            filesRight.adapter = new FilesAdapter(activity, filesRight.entries, filesAdapterCallbacks);

            filesLeft.list.setAdapter(filesLeft.adapter);
            filesRight.list.setAdapter(filesRight.adapter);

            filesLeft.list.setOnItemClickListener((parent, view, position, id) -> onItemClicked(filesLeft, position));
            filesRight.list.setOnItemClickListener((parent, view, position, id) -> onItemClicked(filesRight, position));
            filesLeft.list.setOnItemLongClickListener((parent, view, position, id) -> onItemLongClicked(filesLeft, position));
            filesRight.list.setOnItemLongClickListener((parent, view, position, id) -> onItemLongClicked(filesRight, position));

            filesLeft.list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            filesRight.list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            filesLeft.list.setFastScrollEnabled(true);
            filesRight.list.setFastScrollEnabled(true);
            filesLeft.list.setSmoothScrollbarEnabled(true);
            filesRight.list.setSmoothScrollbarEnabled(true);

            final View root = binding.tabFiles.getRoot();

            filesBtnPaste = root.findViewWithTag("files_paste_icon");
            filesBtnMkdir = root.findViewWithTag("files_mkdir_icon");
            final View btnUpLeft = root.findViewWithTag("files_up_left");
            final View btnUpRight = root.findViewWithTag("files_up_right");
            final View btnRefreshLeft = root.findViewWithTag("files_refresh_left");
            final View btnRefreshRight = root.findViewWithTag("files_refresh_right");
            final TextView status = (TextView) root.findViewWithTag("files_status");
            final MaterialCheckBox useShizuku = binding.tabFiles.chkFilesUseShizuku;
            final MaterialCheckBox openKnown = binding.tabFiles.chkFilesOpenKnown;
            final MaterialCheckBox internalApkInstall = binding.tabFiles.chkFilesInternalApkInstall;
            filesFilterEdit = (EditText) root.findViewWithTag("files_filter");
            filesStorageLabel = (TextView) root.findViewWithTag("files_storage_label");
            filesScanRootDirectoryCheckBox = binding.tabFiles.chkFilesScanRootDirectory;
            filesScanCurrentDirectoryCheckBox = binding.tabFiles.chkFilesScanCurrentDirectory;

            if (filesScanRootDirectoryCheckBox != null) {
                filesScanRootDirectoryCheckBox.setChecked(prefs.getBoolean(PREF_SCAN_ROOT_DIRECTORY, false));
                filesScanRootDirectoryCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (suppressScanRootDirectoryChange) return;
                    if (isChecked) {
                        showScanRootDirectoryWarning();
                    } else {
                        prefs.edit().putBoolean(PREF_SCAN_ROOT_DIRECTORY, false).apply();
                        updateStorageLabelForActivePane();
                    }
                });
            }

            if (filesScanCurrentDirectoryCheckBox != null) {
                filesScanCurrentDirectoryCheckBox.setChecked(prefs.getBoolean(PREF_SCAN_CURRENT_DIRECTORY, false));
                filesScanCurrentDirectoryCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    prefs.edit().putBoolean(PREF_SCAN_CURRENT_DIRECTORY, isChecked).apply();
                    updateStorageLabelForActivePane();
                });
            }

            if (useShizuku != null) {
                useShizuku.setChecked(prefs.getBoolean(keyUseShizuku, true));
                useShizuku.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    prefs.edit().putBoolean(keyUseShizuku, isChecked).apply();
                    if (!isChecked) {
                        coercePaneToAndroidSafePath(filesLeft);
                        coercePaneToAndroidSafePath(filesRight);
                    }
                    refreshPane(filesLeft, false);
                    if (binding.tabFiles.chkFilesSplit.isChecked()) refreshPane(filesRight, false);
                });
            }

            if (openKnown != null) {
                openKnown.setChecked(prefs.getBoolean(keyOpenKnown, true));
                openKnown.setOnCheckedChangeListener((buttonView, isChecked) ->
                        prefs.edit().putBoolean(keyOpenKnown, isChecked).apply());
            }

            if (internalApkInstall != null) {
                internalApkInstall.setChecked(prefs.getBoolean(keyInternalApkInstall, false));
                internalApkInstall.setOnCheckedChangeListener((buttonView, isChecked) ->
                        prefs.edit().putBoolean(keyInternalApkInstall, isChecked).apply());
            }

            if (filesBtnPaste != null) filesBtnPaste.setOnClickListener(v -> { if (filesMutationActions != null) filesMutationActions.paste(); });
            if (filesBtnMkdir != null) filesBtnMkdir.setOnClickListener(v -> { if (filesMutationActions != null) filesMutationActions.mkdir(); });
            if (btnUpLeft != null) btnUpLeft.setOnClickListener(v -> goUp(filesLeft));
            if (btnUpRight != null) btnUpRight.setOnClickListener(v -> goUp(filesRight));
            if (btnRefreshLeft != null) btnRefreshLeft.setOnClickListener(v -> refreshPane(filesLeft));
            if (btnRefreshRight != null) btnRefreshRight.setOnClickListener(v -> refreshPane(filesRight));
            binding.tabFiles.txtFilesPathLeft.setOnClickListener(v -> promptGoTo(filesLeft));
            binding.tabFiles.txtFilesPathRight.setOnClickListener(v -> promptGoTo(filesRight));

            wireShortcut(root, "files_home", "/sdcard/dev.perms.test");
            wireShortcut(root, "files_sdcard", "/sdcard");
            wireShortcut(root, "files_download", "/sdcard/Download");
            wireShortcut(root, "files_android_data", "/sdcard/Android/data");
            wireShortcut(root, "files_android_media", "/sdcard/Android/media");
            wireShortcut(root, "files_obb", "/sdcard/Android/obb");
            wireShortcut(root, "files_tmp", "/data/local/tmp");
            wireShortcut(root, "files_root", "/");

            if (filesFilterEdit != null) {
                filesFilterEdit.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        filesFilterText = s == null ? "" : s.toString();
                        applyFilter(filesLeft);
                        applyFilter(filesRight);
                        updateStatusLine();
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
            }

            filesLeft.list.setOnTouchListener((v, e) -> {
                if (e != null && e.getActionMasked() == MotionEvent.ACTION_DOWN && filesActiveIsRight) {
                    setActivePane(false);
                }
                return false;
            });
            filesRight.list.setOnTouchListener((v, e) -> {
                if (e != null && e.getActionMasked() == MotionEvent.ACTION_DOWN && !filesActiveIsRight) {
                    setActivePane(true);
                }
                return false;
            });

            if (binding.tabFiles.chkFilesSplit != null) {
                boolean split = (restoreSplit != null) ? restoreSplit : prefs.getBoolean(keyLastSplit, binding.tabFiles.chkFilesSplit.isChecked());
                binding.tabFiles.chkFilesSplit.setChecked(split);
                binding.tabFiles.filesPaneRight.setVisibility(split ? View.VISIBLE : View.GONE);
                if (!split) setActivePane(false);
                else if (restoreActiveRight != null) setActivePane(restoreActiveRight);
                else setActivePane(prefs.getBoolean(keyLastActiveRight, false));
                binding.tabFiles.chkFilesSplit.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    binding.tabFiles.filesPaneRight.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    if (!isChecked) setActivePane(false);
                    persistState();
                    refreshPane(filesLeft);
                    if (isChecked) refreshPane(filesRight);
                });
            }

            refreshPane(filesLeft);
            if (binding.tabFiles.chkFilesSplit.isChecked()) refreshPane(filesRight);
            updateStatusLine();
        } catch (Throwable t) {
            host.appendOutput("[Files] setup failed: " + t + "\n");
        }
    }

    public String getLeftCwd() {
        return filesLeft == null ? null : filesLeft.cwd;
    }

    public String getRightCwd() {
        return filesRight == null ? null : filesRight.cwd;
    }

    public boolean isActiveRight() {
        return filesActiveIsRight;
    }

    public boolean isSplitChecked() {
        try {
            return binding.tabFiles != null
                    && binding.tabFiles.chkFilesSplit != null
                    && binding.tabFiles.chkFilesSplit.isChecked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void invalidatePackageIconCaches() {
        if (filesPackageIconLoader != null) filesPackageIconLoader.invalidate();
    }

    private void persistState() {
        try {
            if (filesLeft == null) return;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(keyLastLeftCwd, filesLeft.cwd == null ? "" : filesLeft.cwd);
            if (filesRight != null) editor.putString(keyLastRightCwd, filesRight.cwd == null ? "" : filesRight.cwd);
            boolean split = false;
            try { split = binding.tabFiles.chkFilesSplit != null && binding.tabFiles.chkFilesSplit.isChecked(); } catch (Throwable ignored) {}
            editor.putBoolean(keyLastSplit, split);
            editor.putBoolean(keyLastActiveRight, filesActiveIsRight);
            editor.apply();
        } catch (Throwable ignored) {}
    }

    private void wireShortcut(View root, String tag, String path) {
        try {
            View v = root.findViewWithTag(tag);
            if (v != null) v.setOnClickListener(view -> goTo(activePaneInternal(), path));
        } catch (Throwable ignored) {}
    }

    private void setActivePane(boolean right) {
        if (filesActiveIsRight == right) return;
        filesActiveIsRight = right;
        persistState();
        updateStatusLine();
        updateStorageLabelForActivePane();
    }

    private FilesPaneState activePaneInternal() {
        if (binding.tabFiles.filesPaneRight.getVisibility() == View.VISIBLE && filesActiveIsRight) {
            return filesRight;
        }
        return filesLeft;
    }

    private boolean splitVisibleInternal() {
        try {
            return binding.tabFiles.filesPaneRight.getVisibility() == View.VISIBLE;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String paneLabelInternal(FilesPaneState pane) {
        return pane == filesRight ? "Right" : "Left";
    }

    private FilesPaneState otherPaneInternal(FilesPaneState pane) {
        return pane == filesRight ? filesLeft : filesRight;
    }

    private boolean hasClipboard() {
        return filesClipboard.hasEntry();
    }

    private void showScanRootDirectoryWarning() {
        try {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Scan Root")
                    .setMessage("Scan Current controls normal directory-size totals. Scan Root additionally allows root-style paths such as / and /sdcard when Scan Current is enabled. These scans can take a long time.")
                    .setPositiveButton("Enable", (dialog, which) -> {
                        prefs.edit().putBoolean(PREF_SCAN_ROOT_DIRECTORY, true).apply();
                        updateStorageLabelForActivePane();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> setScanRootDirectoryChecked(false))
                    .setOnCancelListener(dialog -> setScanRootDirectoryChecked(false))
                    .show();
        } catch (Throwable ignored) {
            prefs.edit().putBoolean(PREF_SCAN_ROOT_DIRECTORY, true).apply();
            updateStorageLabelForActivePane();
        }
    }

    private void setScanRootDirectoryChecked(boolean checked) {
        try {
            suppressScanRootDirectoryChange = true;
            if (filesScanRootDirectoryCheckBox != null) filesScanRootDirectoryCheckBox.setChecked(checked);
        } catch (Throwable ignored) {
        } finally {
            suppressScanRootDirectoryChange = false;
        }
    }

    private boolean scanRootDirectoryEnabled() {
        try {
            if (filesScanRootDirectoryCheckBox != null) return filesScanRootDirectoryCheckBox.isChecked();
            return prefs.getBoolean(PREF_SCAN_ROOT_DIRECTORY, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean scanCurrentDirectoryEnabled() {
        try {
            if (filesScanCurrentDirectoryCheckBox != null) return filesScanCurrentDirectoryCheckBox.isChecked();
            return prefs.getBoolean(PREF_SCAN_CURRENT_DIRECTORY, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void cancelStorageLabelScanFor(FilesPaneState pane) {
        if (pane == null || pane != activePaneInternal()) return;
        filesStorageStatsSeq++;
        if (filesStorageLabel != null) filesStorageLabel.setText("Storage: pending…");
    }

    private void updateStatusLine() {
        try {
            TextView status = (TextView) binding.tabFiles.getRoot().findViewWithTag("files_status");
            if (status == null) return;
            FilesPaneState pane = activePaneInternal();
            String active = splitVisibleInternal() ? (filesActiveIsRight ? "Right" : "Left") : "Left";
            String selected = (pane == null || pane.selected == null) ? "none" : pane.selected.name + (pane.selected.isDir ? "/" : "");
            boolean split = splitVisibleInternal();
            String clip = filesClipboard.statusText(split, active);
            String visible = "";
            if (pane != null) {
                visible = " · " + pane.entries.size() + "/" + pane.allEntries.size() + " shown";
            }
            status.setText("Active: " + active + " pane" + visible + "\nSelected: " + selected + "\nClipboard: " + clip);
        } catch (Throwable ignored) {
        }
    }

    private void updateStorageLabelForActivePane() {
        updateStorageLabel(activePaneInternal());
    }

    private void updateStorageLabel(FilesPaneState pane) {
        if (filesStorageLabel == null || pane == null || TextUtils.isEmpty(pane.cwd)) return;
        final String cwd = pane.cwd;
        final int seq = ++filesStorageStatsSeq;
        filesStorageLabel.setText("Storage: checking…");
        if (useShizukuBackend() && host.canUseShizuku()) {
            updateStorageLabelViaShizuku(seq, cwd);
        } else {
            updateStorageLabelViaJava(seq, cwd);
        }
    }

    private void updateStorageLabelViaShizuku(int seq, String cwd) {
        try {
            String quoted = host.shellQuote(cwd);
            String skipCase = !scanCurrentDirectoryEnabled()
                    ? "*"
                    : (scanRootDirectoryEnabled()
                    ? "/storage|/storage/emulated|/proc|/sys|/dev|/data"
                    : "/|/sdcard|/storage/self/primary|/storage|/storage/emulated|/proc|/sys|/dev|/data");
            String cmd = "P=" + quoted + "; "
                    + "case \"$P\" in " + skipCase + ") USED= ;; *) "
                    + "USED=$({ if command -v timeout >/dev/null 2>&1; then timeout 8 du -sk \"$P\" 2>/dev/null; else du -sk \"$P\" 2>/dev/null; fi; } | awk 'NR==1{print $1}') ;; esac; "
                    + "DF=$(df -k \"$P\" 2>/dev/null | tail -n 1); "
                    + "set -- $DF; "
                    + "printf '__PT_STORAGE__|%s|%s|%s\\n' \"$USED\" \"$2\" \"$4\"";
            host.runShizukuCommandCapture(cmd, (exit, out, err) -> activity.runOnUiThread(() -> {
                if (seq != filesStorageStatsSeq) return;
                String label = parseShizukuStorageLabel(out);
                if (TextUtils.isEmpty(label)) {
                    label = "Storage: unavailable";
                    if (exit != 0) host.appendOutput("[Files] storage stats failed (" + exit + "): " + (err == null ? "" : err.trim()) + "\n");
                }
                if (filesStorageLabel != null) filesStorageLabel.setText(label);
            }));
        } catch (Throwable t) {
            if (seq == filesStorageStatsSeq && filesStorageLabel != null) filesStorageLabel.setText("Storage: unavailable");
        }
    }

    private String parseShizukuStorageLabel(String out) {
        String[] lines = out == null ? new String[0] : out.split("\r?\n");
        for (String line : lines) {
            if (line == null || !line.startsWith("__PT_STORAGE__|")) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length < 4) continue;
            long used = parseKilobytes(parts[1]);
            long total = parseKilobytes(parts[2]);
            long free = parseKilobytes(parts[3]);
            return buildStorageLabel(used, free, total);
        }
        return "";
    }

    private long parseKilobytes(String value) {
        try {
            String v = value == null ? "" : value.trim();
            if (v.isEmpty()) return -1L;
            return Long.parseLong(v) * 1024L;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private void updateStorageLabelViaJava(int seq, String cwd) {
        io.execute(() -> {
            File target = new File(cwd);
            long total = safeTotalSpace(target);
            long free = safeUsableSpace(target);
            long used = shouldSkipRecursiveStorageSize(cwd) ? -1L : safeRecursiveSize(target, seq);
            final String label = buildStorageLabel(used, free, total);
            activity.runOnUiThread(() -> {
                if (seq != filesStorageStatsSeq) return;
                if (filesStorageLabel != null) filesStorageLabel.setText(label);
            });
        });
    }

    private long safeTotalSpace(File target) {
        try { return target == null ? -1L : target.getTotalSpace(); } catch (Throwable ignored) { return -1L; }
    }

    private long safeUsableSpace(File target) {
        try { return target == null ? -1L : target.getUsableSpace(); } catch (Throwable ignored) { return -1L; }
    }

    private long safeRecursiveSize(File target, int seq) {
        try {
            SizeScanLimit limit = new SizeScanLimit(seq);
            return recursiveSize(target, limit);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private long recursiveSize(File file, SizeScanLimit limit) {
        if (file == null || limit == null || limit.cancelled()) return -1L;
        if (++limit.visited > 20000) return -1L;
        try {
            if (!file.isDirectory()) return Math.max(0L, file.length());
            File[] kids = file.listFiles();
            if (kids == null) return 0L;
            long total = 0L;
            for (File kid : kids) {
                if (limit.cancelled()) return -1L;
                long part = recursiveSize(kid, limit);
                if (part < 0L) return -1L;
                total += part;
            }
            return total;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private boolean shouldSkipRecursiveStorageSize(String cwd) {
        if (!scanCurrentDirectoryEnabled()) return true;
        String p = FilesBrowserUtils.normalizePath(cwd);
        if ("/storage".equals(p)
                || "/storage/emulated".equals(p)
                || "/proc".equals(p)
                || "/sys".equals(p)
                || "/dev".equals(p)
                || "/data".equals(p)) {
            return true;
        }
        if (scanRootDirectoryEnabled()) return false;
        return "/".equals(p)
                || "/sdcard".equals(p)
                || "/storage/self/primary".equals(p);
    }

    private void coercePaneToAndroidSafePath(FilesPaneState pane) {
        if (pane == null) return;
        String cwd = FilesBrowserUtils.normalizePath(pane.cwd);
        if (isAndroidFileBrowserPathAllowed(cwd)) return;
        pane.cwd = "/sdcard";
        pane.selected = null;
        try { pane.list.clearChoices(); } catch (Throwable ignored) {}
        host.appendOutput("[Files] Switched to Android file access; moved pane from " + cwd + " to /sdcard. Enable Use Shizuku for root/system paths.\n");
    }

    private boolean isAndroidFileBrowserPathAllowed(String path) {
        String p = FilesBrowserUtils.normalizePath(path);
        if (TextUtils.isEmpty(p)) return false;
        if ("/sdcard".equals(p) || p.startsWith("/sdcard/")) return true;
        if ("/storage/emulated/0".equals(p) || p.startsWith("/storage/emulated/0/")) return true;
        if ("/storage/self/primary".equals(p) || p.startsWith("/storage/self/primary/")) return true;
        try {
            File ext = activity == null ? null : activity.getExternalFilesDir(null);
            if (ext != null) {
                String ep = FilesBrowserUtils.normalizePath(ext.getAbsolutePath());
                return p.equals(ep) || p.startsWith(ep + "/");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private final class SizeScanLimit {
        final int seq;
        final long deadlineMs;
        int visited;

        SizeScanLimit(int seq) {
            this.seq = seq;
            this.deadlineMs = System.currentTimeMillis() + 1500L;
        }

        boolean cancelled() {
            return seq != filesStorageStatsSeq || System.currentTimeMillis() > deadlineMs;
        }
    }

    private String buildStorageLabel(long dirUsed, long free, long total) {
        return "Storage: Dir " + formatMaybeSize(dirUsed)
                + " · Free " + formatMaybeSize(free)
                + " · Total " + formatMaybeSize(total);
    }

    private String formatMaybeSize(long bytes) {
        return bytes < 0L ? "—" : FilesBrowserUtils.formatFileSize(bytes);
    }

    private void onItemClicked(FilesPaneState pane, int position) {
        if (pane == null) return;
        if (position < 0 || position >= pane.entries.size()) return;

        setActivePane(pane == filesRight);

        FileEntry clicked = pane.entries.get(position);

        // Do not mark folders selected before opening them. Selecting/restoring the old row
        // while the async directory refresh is starting can make the list appear to jump.
        if (clicked != null && clicked.isDir) {
            openDirectory(pane, clicked.fullPath);
            return;
        }

        pane.list.setItemChecked(position, true);
        pane.selected = clicked;

        // Modern file-manager behavior: tap known file types to open when enabled,
        // otherwise tap files only selects them.
        if (clicked != null && openKnownOnTapEnabled() && FilesBrowserUtils.isKnownOpenable(clicked.name)) {
            if (filesEntryActions != null) filesEntryActions.openKnownFile(clicked);
            return;
        }
        updateStatusLine();
    }

    private boolean onItemLongClicked(FilesPaneState pane, int position) {
        if (pane == null) return false;
        if (position < 0 || position >= pane.entries.size()) return false;
        setActivePane(pane == filesRight);
        FileEntry clicked = pane.entries.get(position);
        pane.selected = clicked;
        pane.list.setItemChecked(position, true);
        try { pane.list.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) {}
        showEntryActions(pane, clicked);
        updateStatusLine();
        return true;
    }

    private void showEntryActions(FilesPaneState pane, FileEntry entry) {
        if (pane == null || entry == null) return;
        final boolean split = splitVisibleInternal();
        final FilesPaneState otherPane = otherPaneInternal(pane);
        final ArrayList<String> actions = new ArrayList<>();
        if (entry.isDir) actions.add("Open folder");
        else if (FilesBrowserUtils.isPackageArchive(entry.name)) {
            actions.add("Install Package");
            actions.add("Open With...");
        }
        else if (FilesBrowserUtils.isKnownOpenable(entry.name)) actions.add("Open");
        if (!entry.isDir && !actions.contains("Open With...")) actions.add("Open With...");
        actions.add("Copy");
        actions.add("Cut");
        if (split && otherPane != null) {
            actions.add("Copy to " + paneLabelInternal(otherPane) + " pane");
            actions.add("Move to " + paneLabelInternal(otherPane) + " pane");
            if (hasClipboard()) {
                actions.add("Paste to Left pane");
                actions.add("Paste to Right pane");
            }
        } else if (hasClipboard()) {
            actions.add("Paste here");
        }
        actions.add("Rename");
        actions.add("Delete");
        actions.add("Copy path");
        actions.add("Properties");

        new MaterialAlertDialogBuilder(activity)
                .setTitle(entry.name + (entry.isDir ? "/" : ""))
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("Open folder".equals(action)) openDirectory(pane, entry.fullPath);
                    else if (("Install Package".equals(action) || "Open".equals(action)) && filesEntryActions != null) filesEntryActions.openKnownFile(entry);
                    else if ("Open With...".equals(action) && filesEntryActions != null) filesEntryActions.openWithExternalHandler(entry);
                    else if ("Copy".equals(action) && filesMutationActions != null) filesMutationActions.copyCut(false);
                    else if ("Cut".equals(action) && filesMutationActions != null) filesMutationActions.copyCut(true);
                    else if (action != null && action.startsWith("Copy to ") && filesMutationActions != null) filesMutationActions.copyMoveEntryToPane(entry, pane, otherPane, false);
                    else if (action != null && action.startsWith("Move to ") && filesMutationActions != null) filesMutationActions.copyMoveEntryToPane(entry, pane, otherPane, true);
                    else if ("Paste to Left pane".equals(action) && filesMutationActions != null) filesMutationActions.paste(filesLeft);
                    else if ("Paste to Right pane".equals(action) && filesMutationActions != null) filesMutationActions.paste(filesRight);
                    else if ("Paste here".equals(action) && filesMutationActions != null) filesMutationActions.paste(pane);
                    else if ("Rename".equals(action) && filesMutationActions != null) filesMutationActions.rename();
                    else if ("Delete".equals(action) && filesMutationActions != null) filesMutationActions.confirmDelete(pane, entry);
                    else if ("Copy path".equals(action) && filesEntryActions != null) filesEntryActions.copyPath(entry.fullPath);
                    else if ("Properties".equals(action) && filesEntryActions != null) filesEntryActions.showProperties(entry);
                })
                .show();
    }

    private boolean internalApkInstallEnabled() {
        try {
            if (binding.tabFiles.chkFilesInternalApkInstall != null) {
                return binding.tabFiles.chkFilesInternalApkInstall.isChecked();
            }
            return prefs.getBoolean(keyInternalApkInstall, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean openKnownOnTapEnabled() {
        try {
            if (binding.tabFiles.chkFilesOpenKnown != null) {
                return binding.tabFiles.chkFilesOpenKnown.isChecked();
            }
            return prefs.getBoolean(keyOpenKnown, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean useShizukuBackend() {
        try {
            if (binding.tabFiles.chkFilesUseShizuku != null) {
                return binding.tabFiles.chkFilesUseShizuku.isChecked();
            }
            return prefs.getBoolean(keyUseShizuku, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void goUp(FilesPaneState pane) {
        if (pane == null) return;
        String p = pane.cwd;
        if (p == null || p.isEmpty() || "/".equals(p)) return;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int idx = p.lastIndexOf('/');
        String parent = (idx <= 0) ? "/" : p.substring(0, idx);
        if (!useShizukuBackend() && !isAndroidFileBrowserPathAllowed(parent)) {
            host.appendOutput("[Files] Android file access cannot browse " + parent + ". Enable Use Shizuku for root/system paths.\n");
            return;
        }
        goTo(pane, parent);
    }

    private void promptGoTo(FilesPaneState pane) {
        if (pane == null) return;
        host.showTextPromptDialog("Go to path", "Path", pane.cwd, value -> {
            if (value == null) return;
            value = value.trim();
            if (value.isEmpty()) return;
            goTo(pane, value);
        });
    }

    private void goTo(FilesPaneState pane, String path) {
        if (pane == null || TextUtils.isEmpty(path)) return;
        openDirectory(pane, path.trim());
    }

    private void openDirectory(FilesPaneState pane, String dir) {
        if (pane == null || TextUtils.isEmpty(dir)) return;
        String normalized = FilesBrowserUtils.normalizePath(dir);
        if (!useShizukuBackend() && !isAndroidFileBrowserPathAllowed(normalized)) {
            host.appendOutput("[Files] Android file access cannot browse " + normalized + ". Enable Use Shizuku for root/system paths.\n");
            return;
        }
        pane.cwd = normalized;
        persistState();
        pane.selected = null;
        try { pane.list.clearChoices(); } catch (Throwable ignored) {}
        refreshPane(pane, false, false);
    }

    private void refreshPane(FilesPaneState pane) {
        refreshPane(pane, true);
    }

    private void refreshPane(FilesPaneState pane, boolean restoreScrollPosition) {
        refreshPane(pane, restoreScrollPosition, true);
    }

    private void refreshPane(FilesPaneState pane, boolean restoreScrollPosition, boolean showLoadingStatus) {
        if (pane == null) return;
        try {
            cancelStorageLabelScanFor(pane);
            pane.cwd = FilesBrowserUtils.normalizePath(pane.cwd);
            pane.path.setText(pane.cwd);
            final int seq = ++pane.refreshSeq;
            final String requestedCwd = pane.cwd;
            final int firstVisible = pane.list.getFirstVisiblePosition();
            final View firstView = pane.list.getChildAt(0);
            final int firstTop = firstView == null ? 0 : (firstView.getTop() - pane.list.getPaddingTop());

            if (showLoadingStatus) setStatusLoading(pane, requestedCwd);

            if (!useShizukuBackend()) {
                refreshPaneViaAndroid(pane, seq, requestedCwd, firstVisible, firstTop, restoreScrollPosition);
                return;
            }

            refreshPaneViaShizuku(pane, seq, requestedCwd, firstVisible, firstTop, restoreScrollPosition);
        } catch (Throwable t) {
            host.appendOutput("[Files] refresh failed: " + t + "\n");
        }
    }

    private void refreshPaneViaAndroid(FilesPaneState pane, int seq, String requestedCwd, int firstVisible, int firstTop, boolean restoreScrollPosition) {
        if (pane == null || TextUtils.isEmpty(requestedCwd)) return;
        io.execute(() -> {
            ArrayList<FileEntry> local = FilesBrowserUtils.listWithJavaIfAvailable(requestedCwd);
            final boolean listFailed = local == null;
            if (local == null) local = new ArrayList<>();
            final ArrayList<FileEntry> result = local;
            activity.runOnUiThread(() -> {
                try {
                    if (pane.refreshSeq != seq) return;
                    if (!TextUtils.equals(requestedCwd, pane.cwd)) return;
                } catch (Throwable ignored) {}
                if (listFailed) {
                    host.appendOutput("[Files] Android listing failed for " + requestedCwd + ". Check Android file permissions or enable Use Shizuku.\n");
                }
                applyNewEntries(pane, result, firstVisible, firstTop, restoreScrollPosition);
            });
        });
    }

    private void refreshPaneViaShizuku(FilesPaneState pane, int seq, String requestedCwd, int firstVisible, int firstTop, boolean restoreScrollPosition) {
        if (pane == null || TextUtils.isEmpty(requestedCwd)) return;
        if (!host.canUseShizuku()) {
            host.appendOutput("[Files] Shizuku is not ready; using Android listing fallback.\n");
            refreshPaneViaAndroid(pane, seq, requestedCwd, firstVisible, firstTop, restoreScrollPosition);
            return;
        }
        try {
            final String cmd = "P=" + host.shellQuote(requestedCwd) + "; "
                    + "if [ -d \"$P\" ]; then printf '__PT_FILES_TARGET__|d\\n'; cd \"$P\" || exit 2; "
                    + "stat -c '%F|%s|%Y|%n' ./* ./.[!.]* ./..?* 2>/dev/null || true; "
                    + "elif [ -e \"$P\" ]; then printf '__PT_FILES_TARGET__|f\\n'; "
                    + "stat -c '%F|%s|%Y|%n' \"$P\" 2>/dev/null; else exit 2; fi";
            host.runShizukuCommandCapture(cmd, (exit, out, err) -> {
                try {
                    if (pane.refreshSeq != seq) return;
                    if (!TextUtils.equals(requestedCwd, pane.cwd)) return;
                } catch (Throwable ignored) {}
                ArrayList<FileEntry> tmp = FilesBrowserUtils.parseStatListing(requestedCwd, out);
                if (exit != 0 && tmp.isEmpty()) {
                    host.appendOutput("[Files] Shizuku listing failed (" + exit + "): " + (err == null ? "" : err.trim()) + "\n");
                    refreshPaneViaAndroid(pane, seq, requestedCwd, firstVisible, firstTop, restoreScrollPosition);
                    return;
                }
                applyNewEntries(pane, tmp, firstVisible, firstTop, restoreScrollPosition);
            });
        } catch (Throwable t) {
            host.appendOutput("[Files] refresh failed: " + t + "\n");
        }
    }

    private void setStatusLoading(FilesPaneState pane, String cwd) {
        try {
            TextView status = (TextView) binding.tabFiles.getRoot().findViewWithTag("files_status");
            if (status != null && pane == activePaneInternal()) {
                status.setText("Loading " + cwd + "…");
            }
        } catch (Throwable ignored) {}
    }

    private void applyNewEntries(FilesPaneState pane, ArrayList<FileEntry> entries, int firstVisible, int firstTop, boolean restoreScrollPosition) {
        if (pane == null) return;
        String previousSelected = pane.selected == null ? null : pane.selected.fullPath;
        pane.allEntries.clear();
        if (entries != null) pane.allEntries.addAll(entries);
        applyFilter(pane);
        if (!TextUtils.isEmpty(previousSelected)) {
            pane.selected = null;
            for (int i = 0; i < pane.entries.size(); i++) {
                FileEntry e = pane.entries.get(i);
                if (e != null && previousSelected.equals(e.fullPath)) {
                    pane.selected = e;
                    try { pane.list.setItemChecked(i, true); } catch (Throwable ignored) {}
                    break;
                }
            }
        }
        try {
            if (restoreScrollPosition) {
                if (firstVisible > 0 || firstTop != 0) pane.list.setSelectionFromTop(firstVisible, firstTop);
            } else {
                pane.list.setSelectionFromTop(0, 0);
            }
        } catch (Throwable ignored) {}
        updateStatusLine();
        if (pane == activePaneInternal()) updateStorageLabelForActivePane();
    }

    private void applyFilter(FilesPaneState pane) {
        if (pane == null || pane.adapter == null) return;
        String filter = filesFilterText == null ? "" : filesFilterText.trim().toLowerCase(Locale.ROOT);
        pane.entries.clear();
        for (FileEntry e : pane.allEntries) {
            if (e == null) continue;
            if (filter.isEmpty()
                    || e.name.toLowerCase(Locale.ROOT).contains(filter)
                    || (e.meta != null && e.meta.toLowerCase(Locale.ROOT).contains(filter))) {
                pane.entries.add(e);
            }
        }
        pane.adapter.notifyDataSetChanged();
        try { pane.list.invalidateViews(); } catch (Throwable ignored) {}
    }

    private void notifyAdaptersChanged() {
        try { if (filesLeft != null && filesLeft.adapter != null) filesLeft.adapter.notifyDataSetChanged(); } catch (Throwable ignored) {}
        try { if (filesRight != null && filesRight.adapter != null) filesRight.adapter.notifyDataSetChanged(); } catch (Throwable ignored) {}
    }
}
