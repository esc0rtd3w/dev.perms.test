package dev.perms.test.tools.hex;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.databinding.TabToolsBinding;
import dev.perms.test.memory.MemoryPackageEntry;
import dev.perms.test.memory.MemoryToolRuntime;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.PackageDropdownAdapter;
import dev.perms.test.ui.PackageDropdownEntry;
import dev.perms.test.ui.FastScrollOverlay;
import dev.perms.test.ui.PackageDropdownUi;

/**
 * Controller for Tools > Hex Editor.
 *
 * File mode is the default path and edits only a bounded file window on disk.
 * Picked SAF documents are read and written through bounded descriptor windows
 * instead of staging the whole document. That keeps large files usable without
 * copying multi-GB inputs into app storage. Memory mode is intentionally
 * a separate route to the existing Memory tab Hex Editor, not a duplicate
 * memory-write backend inside Tools.
 */
public final class FileHexEditorController {
    public interface Host {
        Activity getActivity();
        ActivityMainBinding getBinding();
        void appendOutput(String message);
        void showTab(int index);
    }

    private static final long NO_SELECTION = -1L;
    private static final int TAB_MEMORY = 3;

    private final Host host;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PermsTestFileHexEditor");
        thread.setDaemon(true);
        return thread;
    });

    private ActivityResultLauncher<Intent> pickFileLauncher;
    private HexFileWindow window;
    private Uri pickedDocumentUri;
    private String pickedDocumentName;
    private long selectedOffset = NO_SELECTION;
    private int selectedLength;
    private int selectionUnit = 1;
    private int lastSearchRelative = -1;
    private HexRowAdapter rowAdapter;
    private long lastDragStatusTime;

    private final ArrayList<PackageDropdownEntry> memoryPackages = new ArrayList<>();
    private PackageDropdownAdapter memoryPackageAdapter;
    private String selectedMemoryPackage;
    private boolean memoryPackagesLoading;

    public FileHexEditorController(Host host) {
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
            tab.btnToolsHexBrowse.setOnClickListener(v -> launchFilePicker());
            tab.btnToolsHexLoad.setOnClickListener(v -> loadWindow(true));
            tab.btnToolsHexSave.setOnClickListener(v -> saveWindow());
            tab.btnToolsHexPrev.setOnClickListener(v -> pageWindow(-1));
            tab.btnToolsHexNext.setOnClickListener(v -> pageWindow(1));
            tab.btnToolsHexJump.setOnClickListener(v -> jumpToOffset());
            tab.btnToolsHexFind.setOnClickListener(v -> findNext());
            tab.btnToolsHexEdit.setOnClickListener(v -> showEditDialog());
            tab.btnToolsHexSelectByte.setOnClickListener(v -> setSelectionUnit(1));
            tab.btnToolsHexSelectWord.setOnClickListener(v -> setSelectionUnit(2));
            tab.btnToolsHexSelectDword.setOnClickListener(v -> setSelectionUnit(4));
            tab.btnToolsHexSelectQword.setOnClickListener(v -> setSelectionUnit(8));
            tab.btnToolsHexCopy.setOnClickListener(v -> copySelection(false));
            tab.btnToolsHexCut.setOnClickListener(v -> cutSelection());
            tab.btnToolsHexPaste.setOnClickListener(v -> pasteAtSelection());
            tab.chkToolsHexDragSelect.setOnCheckedChangeListener((buttonView, checked) -> {
                if (rowAdapter != null) rowAdapter.setDragSelectEnabled(checked);
                status(checked
                        ? "Drag Select is on. Drag across hex or ASCII cells to select a byte range."
                        : "Drag Select is off. Tap a byte to select it.");
            });
            tab.chkToolsHexInsertMode.setOnCheckedChangeListener((buttonView, checked) -> status(checked
                    ? "Insert Mode is guarded: inserted bytes change the loaded window length and cannot be saved until full-file rewrite support is added."
                    : "Insert Mode is off. Edits and Paste overwrite existing bytes."));
            tab.btnToolsHexMemoryRefresh.setOnClickListener(v -> refreshMemoryPackages(true));
            tab.btnToolsHexOpenMemoryHex.setOnClickListener(v -> openMemoryHexForSelectedPackage());
            tab.chkToolsHexMemoryMode.setOnCheckedChangeListener((buttonView, checked) -> applyModeGate());
            configureMemoryPackageDropdown(tab);
            configureHexRows(tab);
            configureScrollTogetherGate(tab);
            applyModeGate();
            renderEmpty();
            status("File mode is ready. Browse for a file or enter a path, then Load a bounded window.");
        } catch (Throwable t) {
            status("Hex Editor bind failed: " + safeMessage(t));
        }
    }

    private void launchFilePicker() {
        if (isMemoryMode()) {
            status("Memory mode is enabled; file picking is gated off.");
            return;
        }
        if (pickFileLauncher == null) {
            Toast.makeText(activity(), "File picker unavailable", Toast.LENGTH_SHORT).show();
            status("File picker unavailable. Restart the activity if this was opened before initialization finished.");
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
            status("File picker failed: " + safeMessage(t));
        }
    }

    private void handlePickedFile(ActivityResult result) {
        try {
            if (result == null || result.getResultCode() != Activity.RESULT_OK) return;
            Intent data = result.getData();
            Uri uri = data == null ? null : data.getData();
            if (uri == null) return;
            persistPickedDocumentGrant(data, uri);
            String name = queryDisplayName(uri);
            if (TextUtils.isEmpty(name)) name = "picked_document.bin";
            pickedDocumentUri = uri;
            pickedDocumentName = name;
            setText(tab().edtToolsHexPath, uri.toString());
            setText(tab().edtToolsHexOffset, "0x0");
            status("Picked " + name + ". Loading a bounded file window...");
            loadWindow(true);
        } catch (Throwable t) {
            status("Picked file failed: " + safeMessage(t));
        }
    }

    private void persistPickedDocumentGrant(Intent data, Uri uri) {
        Activity activity = activity();
        if (activity == null || data == null || uri == null) return;
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Throwable ignored) {
        }
    }

    private void loadWindow(boolean resetSearch) {
        if (isMemoryMode()) {
            status("Memory mode is enabled; file load is gated off.");
            return;
        }
        String path = text(tab() == null ? null : tab().edtToolsHexPath);
        boolean pickedPath = isPickedDocumentPath(path);
        if (!pickedPath) clearPickedDocumentIfManualPath(path);
        long offset;
        int length;
        try {
            offset = parseLong(text(tab() == null ? null : tab().edtToolsHexOffset), 0L);
            length = (int) Math.max(1L, Math.min((long) HexFileWindow.MAX_WINDOW_LENGTH,
                    parseLong(text(tab() == null ? null : tab().edtToolsHexLength), HexFileWindow.DEFAULT_WINDOW_LENGTH)));
        } catch (IllegalArgumentException e) {
            status(e.getMessage());
            return;
        }
        Activity currentActivity = activity();
        ContentResolver resolver = currentActivity == null ? null : currentActivity.getContentResolver();
        status("Loading file window...");
        worker.execute(() -> {
            try {
                HexFileWindow loaded = pickedPath && pickedDocumentUri != null
                        ? HexFileWindow.load(resolver, pickedDocumentUri, pickedDocumentName, offset, length)
                        : HexFileWindow.load(path, offset, length);
                runOnUi(() -> {
                    window = loaded;
                    selectedOffset = NO_SELECTION;
                    selectedLength = 0;
                    if (resetSearch) lastSearchRelative = -1;
                    setText(tab().edtToolsHexPath, loaded.path());
                    setText(tab().edtToolsHexOffset, formatInputOffset(loaded.offset()));
                    renderWindow();
                    String source = loaded.contentBacked() && !TextUtils.isEmpty(loaded.displayName())
                            ? " from picked document " + loaded.displayName()
                            : "";
                    status("Loaded " + loaded.bytes().length + " byte" + suffix(loaded.bytes().length)
                            + source + " at " + HexPaneRenderer.formatOffset(loaded.offset())
                            + " of " + loaded.fileSize() + " byte" + suffix(loaded.fileSize()) + ".");
                });
            } catch (Throwable t) {
                runOnUi(() -> status("Load failed: " + safeMessage(t)));
            }
        });
    }

    private void saveWindow() {
        if (isMemoryMode()) {
            status("Memory mode is enabled; file save is gated off.");
            return;
        }
        HexFileWindow current = window;
        if (current == null) {
            status("Load a file window before saving.");
            return;
        }
        if (!current.dirty()) {
            status("No byte changes to save.");
            return;
        }
        if (current.lengthChanged()) {
            status("Insert/delete changed the loaded window length. Reload or use overwrite edits before saving.");
            return;
        }
        Activity currentActivity = activity();
        ContentResolver resolver = currentActivity == null ? null : currentActivity.getContentResolver();
        status("Saving file window...");
        worker.execute(() -> {
            try {
                current.save(resolver);
                runOnUi(() -> {
                    renderWindow();
                    String target = current.contentBacked() ? current.displayName() : current.path();
                    status("Saved " + current.bytes().length + " byte" + suffix(current.bytes().length)
                            + " window back to " + target + ".");
                    output("[Tools Hex] Saved window at " + HexPaneRenderer.formatOffset(current.offset())
                            + " to " + target);
                });
            } catch (Throwable t) {
                runOnUi(() -> status("Save failed: " + safeMessage(t)));
            }
        });
    }

    private void pageWindow(int direction) {
        HexFileWindow current = window;
        if (current == null) {
            loadWindow(true);
            return;
        }
        if (current.dirty()) {
            status("Save or reload before paging away from a dirty window.");
            return;
        }
        long delta = Math.max(1, current.bytes().length);
        long next = Math.max(0L, current.offset() + (direction < 0 ? -delta : delta));
        setText(tab().edtToolsHexOffset, formatInputOffset(next));
        loadWindow(true);
    }

    private void jumpToOffset() {
        if (window != null && window.dirty()) {
            status("Save or reload before jumping away from a dirty window.");
            return;
        }
        loadWindow(true);
    }

    private void findNext() {
        HexFileWindow current = window;
        if (current == null) {
            status("Load a file window before searching.");
            return;
        }
        byte[] needle;
        try {
            needle = parseSearchNeedle(text(tab().edtToolsHexFind));
        } catch (IllegalArgumentException e) {
            status(e.getMessage());
            return;
        }
        int start = Math.max(0, lastSearchRelative + 1);
        int found = current.indexOf(needle, start);
        if (found < 0 && start > 0) found = current.indexOf(needle, 0);
        if (found < 0) {
            status("No match in the loaded file window.");
            return;
        }
        lastSearchRelative = found;
        selectedOffset = current.offset() + found;
        selectedLength = needle.length;
        renderWindow();
        scrollToSelectedRow();
        status("Found " + needle.length + " byte" + suffix(needle.length) + " at " + HexPaneRenderer.formatOffset(selectedOffset) + ".");
    }

    private void showEditDialog() {
        Activity activity = activity();
        HexFileWindow current = window;
        if (activity == null || current == null) {
            status("Load a file window before editing bytes.");
            return;
        }
        if (isMemoryMode()) {
            status("Memory mode is enabled; file byte edits are gated off.");
            return;
        }
        final TextInputEditText offsetInput = new TextInputEditText(activity);
        offsetInput.setSingleLine(true);
        offsetInput.setText(selectedOffset >= 0L ? formatInputOffset(selectedOffset) : formatInputOffset(current.offset()));
        TextInputLayout offsetLayout = new TextInputLayout(activity);
        offsetLayout.setHint("Absolute offset");
        offsetLayout.addView(offsetInput);

        final TextInputEditText hexInput = new TextInputEditText(activity);
        hexInput.setSingleLine(false);
        hexInput.setMinLines(2);
        hexInput.setText(selectedOffset >= 0L && selectedLength > 0 ? selectedHexPreview() : "");
        TextInputLayout hexLayout = new TextInputLayout(activity);
        hexLayout.setHint("Hex bytes");
        hexLayout.addView(hexInput);

        final TextInputEditText asciiInput = new TextInputEditText(activity);
        asciiInput.setSingleLine(false);
        asciiInput.setMinLines(2);
        asciiInput.setText(selectedAsciiPreview());
        TextInputLayout asciiLayout = new TextInputLayout(activity);
        asciiLayout.setHint("ASCII / text bytes");
        asciiLayout.addView(asciiInput);
        bindHexAsciiMirrors(hexInput, asciiInput);

        android.widget.LinearLayout box = new android.widget.LinearLayout(activity);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(18);
        box.setPadding(pad, dp(6), pad, 0);
        box.addView(offsetLayout);
        box.addView(hexLayout);
        box.addView(asciiLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Edit File Bytes")
                .setMessage("Hex and ASCII mirror each other. Insert Mode is guarded because it changes file size; overwrite edits are saved in-place.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                long absolute = parseLong(text(offsetInput), current.offset());
                byte[] replacement = parseHexBytes(text(hexInput));
                if (isInsertMode()) {
                    if (!current.insertBytes(absolute, replacement)) {
                        status("Insert range must stay inside the loaded window and below the 64 KiB window cap.");
                        return;
                    }
                } else if (!current.replaceBytes(absolute, replacement)) {
                    status("Edit range must stay inside the loaded file window.");
                    return;
                }
                selectedOffset = absolute;
                selectedLength = replacement.length;
                lastSearchRelative = (int) Math.max(-1L, absolute - current.offset() - 1L);
                renderWindow();
                scrollToSelectedRow();
                status((isInsertMode() ? "Inserted " : "Patched ") + replacement.length + " byte" + suffix(replacement.length)
                        + " at " + HexPaneRenderer.formatOffset(absolute)
                        + (isInsertMode() ? ". Insert changes are guarded from Save." : ". Save to commit."));
                dialog.dismiss();
            } catch (IllegalArgumentException e) {
                status(e.getMessage());
            }
        }));
        try { dialog.show(); } catch (Throwable t) { status("Edit dialog failed: " + safeMessage(t)); }
    }

    private void bindHexAsciiMirrors(final TextInputEditText hexInput, final TextInputEditText asciiInput) {
        final boolean[] syncing = new boolean[] { false };
        try {
            hexInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (syncing[0]) return;
                    try {
                        syncing[0] = true;
                        byte[] bytes = parseHexBytes(String.valueOf(s));
                        asciiInput.setText(asciiFromBytes(bytes));
                    } catch (Throwable ignored) {
                    } finally {
                        syncing[0] = false;
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            asciiInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (syncing[0]) return;
                    try {
                        syncing[0] = true;
                        hexInput.setText(hexFromBytes(String.valueOf(s).getBytes(StandardCharsets.UTF_8)));
                    } catch (Throwable ignored) {
                    } finally {
                        syncing[0] = false;
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        } catch (Throwable ignored) {
        }
    }

    private void configureMemoryPackageDropdown(TabToolsBinding tab) {
        MaterialAutoCompleteTextView dropdown = tab == null ? null : tab.ddToolsHexMemoryPackage;
        Activity activity = activity();
        if (dropdown == null || activity == null) return;
        if (memoryPackageAdapter == null) {
            memoryPackageAdapter = new PackageDropdownAdapter(activity, memoryPackages,
                    PackageDropdownUi.ColorMode.DEBUGGABLE_HIGHLIGHT, false, 0, 0);
        }
        dropdown.setAdapter(memoryPackageAdapter);
        DropdownUi.bindExposedDropdown(activity, tab.tilToolsHexMemoryPackage, dropdown, () -> {
            if (memoryPackages.isEmpty() && !memoryPackagesLoading) refreshMemoryPackages(true);
            else DropdownUi.showDropdown(dropdown);
        });
        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            PackageDropdownEntry entry = null;
            try { entry = memoryPackageAdapter.getItem(position); } catch (Throwable ignored) {}
            if (entry == null || TextUtils.isEmpty(entry.pkg)) return;
            selectedMemoryPackage = entry.pkg;
            try { dropdown.setText(entry.pkg, false); } catch (Throwable ignored) { dropdown.setText(entry.pkg); }
            status("Selected memory target package: " + entry.pkg);
        });
    }

    private void refreshMemoryPackages(boolean openWhenDone) {
        if (!isMemoryMode()) {
            status("Enable Memory Mode before refreshing memory targets.");
            return;
        }
        if (memoryPackagesLoading) {
            status("Memory package refresh is already running.");
            return;
        }
        Activity activity = activity();
        if (activity == null) {
            status("Activity unavailable.");
            return;
        }
        memoryPackagesLoading = true;
        status("Loading running packages for Memory Mode...");
        worker.execute(() -> {
            ArrayList<PackageDropdownEntry> loaded = new ArrayList<>();
            try {
                for (MemoryPackageEntry entry : MemoryToolRuntime.listTargetPackages(activity, true)) {
                    if (entry == null || !entry.running || TextUtils.isEmpty(entry.pkg)) continue;
                    loaded.add(new PackageDropdownEntry(entry.label, entry.pkg, true, entry.debuggable));
                }
            } catch (Throwable ignored) {
                loaded = new ArrayList<>();
            }
            final ArrayList<PackageDropdownEntry> result = loaded;
            runOnUi(() -> {
                memoryPackagesLoading = false;
                memoryPackages.clear();
                memoryPackages.addAll(result);
                if (memoryPackageAdapter != null) memoryPackageAdapter.notifyDataSetChanged();
                status(memoryPackages.isEmpty()
                        ? "No running packages were detected for Memory Mode. Start the target app, then refresh."
                        : "Loaded " + memoryPackages.size() + " running memory target package" + suffix(memoryPackages.size()) + ".");
                if (openWhenDone && tab() != null && tab().ddToolsHexMemoryPackage != null) {
                    try { DropdownUi.showDropdown(tab().ddToolsHexMemoryPackage); } catch (Throwable ignored) {}
                }
            });
        });
    }

    private void openMemoryHexForSelectedPackage() {
        if (!isMemoryMode()) {
            status("Enable Memory Mode before opening the Memory tab Hex Editor.");
            return;
        }
        PackageDropdownEntry entry = resolveSelectedMemoryPackage();
        if (entry == null || TextUtils.isEmpty(entry.pkg)) {
            status("Select a running package for Memory Mode first.");
            if (memoryPackages.isEmpty()) refreshMemoryPackages(true);
            return;
        }
        selectedMemoryPackage = entry.pkg;
        ActivityMainBinding binding = host == null ? null : host.getBinding();
        if (binding == null || binding.tabMemory == null) {
            status("Memory tab is not ready yet.");
            return;
        }

        // The Tools editor does not duplicate the live-memory writer. It selects
        // the same target field used by the Memory tab, switches to that tab,
        // then opens the existing Memory Hex Editor on the normal backend path.
        try { binding.tabMemory.edtMemoryTargetPkg.setText(entry.pkg, false); }
        catch (Throwable ignored) { binding.tabMemory.edtMemoryTargetPkg.setText(entry.pkg); }
        try { binding.tabMemory.btnMemoryUsePackageTarget.performClick(); } catch (Throwable ignored) {}
        if (host != null) host.showTab(TAB_MEMORY);
        binding.tabMemory.btnMemoryOpenHexOverlay.postDelayed(() -> {
            try { binding.tabMemory.btnMemoryOpenHexOverlay.performClick(); } catch (Throwable ignored) {}
        }, 180L);
        status("Opening Memory tab Hex Editor for " + entry.pkg + ".");
    }

    private PackageDropdownEntry resolveSelectedMemoryPackage() {
        String typed = text(tab() == null ? null : tab().ddToolsHexMemoryPackage).trim();
        for (PackageDropdownEntry entry : memoryPackages) {
            if (entry == null) continue;
            if (!TextUtils.isEmpty(selectedMemoryPackage) && selectedMemoryPackage.equals(entry.pkg)) return entry;
            if (!TextUtils.isEmpty(typed)
                    && (typed.equals(entry.pkg) || typed.equals(entry.label) || typed.equals(entry.toString()))) {
                return entry;
            }
        }
        return null;
    }

    private void applyModeGate() {
        TabToolsBinding tab = tab();
        if (tab == null) return;
        boolean memory = isMemoryMode();
        tab.rowToolsHexMemoryOptions.setVisibility(memory ? View.VISIBLE : View.GONE);
        tab.btnToolsHexBrowse.setEnabled(!memory);
        tab.btnToolsHexLoad.setEnabled(!memory);
        tab.btnToolsHexSave.setEnabled(!memory);
        tab.btnToolsHexPrev.setEnabled(!memory);
        tab.btnToolsHexNext.setEnabled(!memory);
        tab.btnToolsHexJump.setEnabled(!memory);
        tab.btnToolsHexFind.setEnabled(!memory);
        tab.btnToolsHexEdit.setEnabled(!memory);
        tab.btnToolsHexSelectByte.setEnabled(!memory);
        tab.btnToolsHexSelectWord.setEnabled(!memory);
        tab.btnToolsHexSelectDword.setEnabled(!memory);
        tab.btnToolsHexSelectQword.setEnabled(!memory);
        tab.btnToolsHexCopy.setEnabled(!memory);
        tab.btnToolsHexCut.setEnabled(!memory);
        tab.btnToolsHexPaste.setEnabled(!memory);
        tab.chkToolsHexDragSelect.setEnabled(!memory);
        tab.chkToolsHexInsertMode.setEnabled(!memory);
        tab.edtToolsHexPath.setEnabled(!memory);
        tab.edtToolsHexOffset.setEnabled(!memory);
        tab.edtToolsHexLength.setEnabled(!memory);
        tab.edtToolsHexFind.setEnabled(!memory);
        tab.ddToolsHexMemoryPackage.setEnabled(memory);
        tab.btnToolsHexMemoryRefresh.setEnabled(memory);
        tab.btnToolsHexOpenMemoryHex.setEnabled(memory);
        if (memory) {
            status("Memory Mode is on. File operations are disabled; select a running package and open the Memory tab Hex Editor.");
            if (memoryPackages.isEmpty() && !memoryPackagesLoading) refreshMemoryPackages(false);
        } else if (tab.txtToolsHexStatus != null) {
            status(window == null
                    ? "File mode is ready. Browse for a file or enter a path, then Load a bounded window."
                    : "File mode active" + (window.dirty() ? " with unsaved byte edits." : "."));
        }
    }

    private void configureHexRows(TabToolsBinding tab) {
        if (tab == null || tab.rvToolsHexRows == null) return;
        if (rowAdapter == null) {
            rowAdapter = new HexRowAdapter(new HexRowAdapter.Listener() {
                @Override public void onByteSelected(long absoluteOffset, boolean openEditor) {
                    selectByteFromRow(absoluteOffset, openEditor);
                }
                @Override public void onByteRangeSelected(long startOffset, long endOffsetInclusive) {
                    selectByteRangeFromRow(startOffset, endOffsetInclusive);
                }
            });
        }
        tab.rvToolsHexRows.setAdapter(rowAdapter);
        tab.rvToolsHexRows.setLayoutManager(new LinearLayoutManager(activity()));
        tab.rvToolsHexRows.setHasFixedSize(true);
        tab.rvToolsHexRows.setNestedScrollingEnabled(true);
        try {
            FastScrollOverlay.attach(tab.rvToolsHexRows,
                    tab.fastScrollTouchToolsHexRows,
                    tab.fastScrollThumbToolsHexRows);
        } catch (Throwable ignored) {}
        tab.rvToolsHexRows.setOnTouchListener((v, event) -> {
            ViewParent parent = v.getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
                parent = parent.getParent();
            }
            if (event != null && (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                ViewParent p = v.getParent();
                while (p != null) {
                    p.requestDisallowInterceptTouchEvent(false);
                    p = p.getParent();
                }
            }
            return false;
        });
    }

    private void configureScrollTogetherGate(TabToolsBinding tab) {
        if (tab == null || tab.chkToolsHexScrollTogether == null) return;
        tab.chkToolsHexScrollTogether.setChecked(true);
        tab.chkToolsHexScrollTogether.setEnabled(false);
        tab.chkToolsHexScrollTogether.setAlpha(0.75f);
        // Offset, hex, and ASCII are rendered by one virtualized row list.  The
        // lock is intentional: it prevents the drift that happened when panes
        // could scroll apart while still keeping the visible control informative.
    }

    private void selectByteFromRow(long absoluteOffset, boolean openEditor) {
        HexFileWindow current = window;
        if (current == null) return;
        selectedOffset = absoluteOffset;
        selectedLength = clampSelectionLength(absoluteOffset, selectionUnit);
        updateSelectionOnly();
        status("Selected " + selectedLength + " byte" + suffix(selectedLength)
                + " at " + HexPaneRenderer.formatOffset(absoluteOffset)
                + ". Use Edit, Copy, Cut, or Paste; long-press a byte to edit directly.");
        if (openEditor) showEditDialog();
    }

    private void selectByteRangeFromRow(long startOffset, long endOffsetInclusive) {
        HexFileWindow current = window;
        if (current == null) return;
        long start = Math.min(startOffset, endOffsetInclusive);
        long end = Math.max(startOffset, endOffsetInclusive);
        start = Math.max(start, current.offset());
        end = Math.min(end, current.offset() + Math.max(0, current.bytes().length - 1));
        if (end < start) return;
        int newLength = (int) Math.min(Integer.MAX_VALUE, end - start + 1);
        if (selectedOffset == start && selectedLength == newLength) return;
        selectedOffset = start;
        selectedLength = newLength;
        updateSelectionOnly();
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastDragStatusTime > 180L) {
            lastDragStatusTime = now;
            status("Selected " + selectedLength + " byte" + suffix(selectedLength)
                    + " from " + HexPaneRenderer.formatOffset(start) + " to " + HexPaneRenderer.formatOffset(end) + ".");
        }
    }

    private void setSelectionUnit(int unit) {
        selectionUnit = Math.max(1, unit);
        if (selectedOffset >= 0L) {
            selectedLength = clampSelectionLength(selectedOffset, selectionUnit);
            updateSelectionOnly();
        }
        status("Selection size set to " + selectionUnit + " byte" + suffix(selectionUnit) + ".");
    }

    private int clampSelectionLength(long absoluteOffset, int requested) {
        HexFileWindow current = window;
        if (current == null) return Math.max(1, requested);
        long relative = absoluteOffset - current.offset();
        int available = (int) Math.max(0L, current.bytes().length - Math.max(0L, relative));
        return Math.max(1, Math.min(Math.max(1, requested), Math.max(1, available)));
    }

    private void copySelection(boolean quiet) {
        HexFileWindow current = window;
        if (current == null || selectedOffset < 0L || selectedLength <= 0) {
            status("Select bytes before copying.");
            return;
        }
        byte[] selected = current.copyBytes(selectedOffset, selectedLength);
        if (selected.length == 0) {
            status("Selected bytes are outside the loaded window.");
            return;
        }
        String hex = hexFromBytes(selected);
        try {
            ClipboardManager clipboard = (ClipboardManager) activity().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("PermsTest hex bytes", hex));
        } catch (Throwable ignored) {
        }
        if (!quiet) status("Copied " + selected.length + " byte" + suffix(selected.length) + " as hex text.");
    }

    private void cutSelection() {
        HexFileWindow current = window;
        if (current == null || selectedOffset < 0L || selectedLength <= 0) {
            status("Select bytes before cutting.");
            return;
        }
        copySelection(true);
        if (isInsertMode()) {
            status("Cut copied the selected bytes without changing file size. Delete and insert save operations are unavailable.");
            return;
        }
        if (!current.clearBytes(selectedOffset, selectedLength)) {
            status("Cut range must stay inside the loaded window.");
            return;
        }
        renderWindow();
        status("Cut copied " + selectedLength + " byte" + suffix(selectedLength)
                + " and cleared the selection to 00. Save to commit.");
    }

    private void pasteAtSelection() {
        HexFileWindow current = window;
        if (current == null) {
            status("Load a file window before pasting.");
            return;
        }
        long absolute = selectedOffset >= 0L ? selectedOffset : current.offset();
        byte[] pasted = clipboardBytes();
        if (pasted.length == 0) {
            status("Clipboard does not contain hex bytes or text to paste.");
            return;
        }
        if (isInsertMode()) {
            if (!current.insertBytes(absolute, pasted)) {
                status("Insert must stay inside the loaded window and below the 64 KiB window cap.");
                return;
            }
            selectedOffset = absolute;
            selectedLength = pasted.length;
            renderWindow();
            scrollToSelectedRow();
            status("Inserted " + pasted.length + " byte" + suffix(pasted.length)
                    + ". Insert changes are guarded from Save because file size would change.");
            return;
        }
        if (!current.replaceBytes(absolute, pasted)) {
            status("Paste range must stay inside the loaded file window.");
            return;
        }
        selectedOffset = absolute;
        selectedLength = pasted.length;
        renderWindow();
        scrollToSelectedRow();
        status("Pasted " + pasted.length + " byte" + suffix(pasted.length) + " in overwrite mode. Save to commit.");
    }

    private byte[] clipboardBytes() {
        try {
            ClipboardManager clipboard = (ClipboardManager) activity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() <= 0) return new byte[0];
            CharSequence text = clip.getItemAt(0).coerceToText(activity());
            String value = text == null ? "" : text.toString().trim();
            if (value.isEmpty()) return new byte[0];
            try { return parseSearchNeedle(value); } catch (Throwable ignored) { return value.getBytes(StandardCharsets.UTF_8); }
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    private void renderEmpty() {
        if (rowAdapter != null) rowAdapter.submit(0L, new byte[0], NO_SELECTION, 0);
    }

    private void renderWindow() {
        HexFileWindow current = window;
        if (current == null) {
            renderEmpty();
            return;
        }
        if (rowAdapter != null) {
            rowAdapter.submit(current.offset(), current.bytes(), selectedOffset, selectedLength);
        }
    }

    private void updateSelectionOnly() {
        if (rowAdapter != null) {
            rowAdapter.updateSelection(selectedOffset, selectedLength);
        } else {
            renderWindow();
        }
    }

    private void scrollToSelectedRow() {
        TabToolsBinding tab = tab();
        HexFileWindow current = window;
        if (tab == null || tab.rvToolsHexRows == null || current == null || selectedOffset < current.offset()) return;
        int row = rowAdapter == null ? 0 : rowAdapter.rowForAbsoluteOffset(selectedOffset);
        tab.rvToolsHexRows.post(() -> tab.rvToolsHexRows.smoothScrollToPosition(row));
    }

    private byte[] parseSearchNeedle(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter hex bytes or text to find.");
        if (value.regionMatches(true, 0, "text:", 0, 5)) {
            String body = value.substring(5);
            if (body.isEmpty()) throw new IllegalArgumentException("Enter text after text:.");
            return body.getBytes(StandardCharsets.UTF_8);
        }
        String compact = value.replaceAll("[^0-9A-Fa-f]", "");
        if (compact.length() >= 2 && (compact.length() & 1) == 0
                && value.matches("[0-9A-Fa-fxX\\s,;:_-]+")) {
            return parseHexBytes(value);
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] parseHexBytes(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = value.replaceAll("(?i)0x", " ");
        String compact = value.replaceAll("[^0-9A-Fa-f]", "");
        if (compact.isEmpty()) throw new IllegalArgumentException("Enter hex bytes.");
        if ((compact.length() & 1) != 0) throw new IllegalArgumentException("Hex byte text must have an even digit count.");
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < compact.length(); i += 2) {
            out[i / 2] = (byte) Integer.parseInt(compact.substring(i, i + 2), 16);
        }
        return out;
    }

    private long parseLong(String raw, long fallback) {
        String value = raw == null ? "" : raw.trim().replace("_", "");
        if (value.isEmpty()) return fallback;
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Long.parseUnsignedLong(value.substring(2), 16);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + value);
        }
    }

    private String selectedHexPreview() {
        HexFileWindow current = window;
        if (current == null || selectedOffset < current.offset() || selectedLength <= 0) return "";
        long relativeLong = selectedOffset - current.offset();
        if (relativeLong < 0L || relativeLong > Integer.MAX_VALUE) return "";
        int relative = (int) relativeLong;
        byte[] data = current.bytes();
        if (relative < 0 || relative >= data.length) return "";
        int end = Math.min(data.length, relative + selectedLength);
        StringBuilder sb = new StringBuilder();
        for (int i = relative; i < end; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02X", data[i] & 0xff));
        }
        return sb.toString();
    }

    private String selectedAsciiPreview() {
        HexFileWindow current = window;
        if (current == null || selectedOffset < current.offset() || selectedLength <= 0) return "";
        byte[] selected = current.copyBytes(selectedOffset, selectedLength);
        return asciiFromBytes(selected);
    }

    private String hexFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02X", b & 0xff));
        }
        return sb.toString();
    }

    private String asciiFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            int v = b & 0xff;
            sb.append(v >= 32 && v <= 126 ? (char) v : '.');
        }
        return sb.toString();
    }

    private boolean isInsertMode() {
        TabToolsBinding tab = tab();
        return tab != null && tab.chkToolsHexInsertMode != null && tab.chkToolsHexInsertMode.isChecked();
    }

    private boolean isMemoryMode() {
        TabToolsBinding tab = tab();
        return tab != null && tab.chkToolsHexMemoryMode != null && tab.chkToolsHexMemoryMode.isChecked();
    }

    private void status(String message) {
        TabToolsBinding tab = tab();
        String safe = message == null ? "" : message;
        if (tab != null && tab.txtToolsHexStatus != null) tab.txtToolsHexStatus.setText(safe);
    }

    private void output(String message) {
        try { if (host != null) host.appendOutput(message); } catch (Throwable ignored) {}
    }

    private void runOnUi(Runnable runnable) {
        Activity activity = activity();
        if (activity == null || runnable == null) return;
        activity.runOnUiThread(runnable);
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private TabToolsBinding tab() {
        ActivityMainBinding binding = host == null ? null : host.getBinding();
        return binding == null ? null : binding.tabTools;
    }

    private String text(TextView view) {
        CharSequence text = view == null ? null : view.getText();
        return text == null ? "" : text.toString();
    }

    private void setText(TextView view, String value) {
        if (view != null) view.setText(value == null ? "" : value);
    }

    private String formatInputOffset(long value) {
        return "0x" + Long.toHexString(Math.max(0L, value)).toUpperCase(Locale.US);
    }

    private String suffix(long count) {
        return count == 1L ? "" : "s";
    }

    private int dp(float value) {
        Activity activity = activity();
        float density = activity == null ? 1f : activity.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private String safeMessage(Throwable t) {
        if (t == null) return "Unknown error";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.getClass().getSimpleName() : msg;
    }

    private String queryDisplayName(Uri uri) {
        Activity activity = activity();
        if (activity == null || uri == null) return "";
        try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Throwable ignored) {
        }
        String path = uri.getLastPathSegment();
        return TextUtils.isEmpty(path) ? "picked_document.bin" : path;
    }

    private boolean isPickedDocumentPath(String path) {
        if (pickedDocumentUri == null || TextUtils.isEmpty(path)) return false;
        return path.trim().equals(pickedDocumentUri.toString());
    }

    private void clearPickedDocumentIfManualPath(String path) {
        if (pickedDocumentUri == null || isPickedDocumentPath(path)) return;
        pickedDocumentUri = null;
        pickedDocumentName = null;
    }
}
