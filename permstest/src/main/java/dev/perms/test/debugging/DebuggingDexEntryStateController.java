package dev.perms.test.debugging;

import android.text.TextUtils;
import android.widget.TextView;

import java.util.ArrayList;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;

/**
 * Owns Debugging-tab DEX-entry scan/selection state.
 *
 * The dropdown renderer remains in DebuggingDexEntryDropdownController; this
 * controller keeps the selected DEX entry, scanned entry list, and per-entry
 * smali/dex output path derivation together inside the Debugging feature.
 */
public final class DebuggingDexEntryStateController {
    public interface Host {
        ActivityMainBinding getBinding();
        void runIo(Runnable action);
        void runOnUiThread(Runnable action);
        void appendOutput(String text);
        void setDebuggingBusy(boolean busy, String status);
        void finishDebuggingToolError(String label, Throwable error);
        String currentDebuggingWorkRoot();
        void updateDerivedPathsForSelectedDexEntry(String dexEntry, boolean forcePathRefresh);
        void setupDexEntryDropdown(ArrayList<String> allItems, ArrayList<String> displayItems, String selectedEntry, String lastDropdownText);
        String applyDexEntryListToDropdown(ArrayList<String> allItems, ArrayList<String> displayItems, String preferredEntry);
        void resizeDexEntryDropdown(ArrayList<String> displayItems);
        void setDexEntrySelectedText(String entry);
    }

    private final Host host;
    private final ArrayList<String> allItems = new ArrayList<>();
    private final ArrayList<String> displayItems = new ArrayList<>();
    private boolean scanComplete;
    private String selectedEntry = "classes.dex";
    private String lastDropdownText = "classes.dex";

    public DebuggingDexEntryStateController(Host host) {
        this.host = host;
    }

    public void setupDropdown() {
        if (host == null) return;
        host.setupDexEntryDropdown(allItems, displayItems, selectedEntry, lastDropdownText);
    }

    public String lastDropdownText() {
        return lastDropdownText == null ? "" : lastDropdownText;
    }

    public String currentEntry() {
        ActivityMainBinding binding = binding();
        return DebuggingDexEntries.current(
                safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.ddSmaliDexEntry),
                selectedEntry);
    }

    public ArrayList<String> currentEntries() {
        return DebuggingDexEntries.activeEntries(displayItems, scanComplete, currentEntry());
    }

    public boolean isSelectionInvalid() {
        return scanComplete && displayItems.isEmpty();
    }

    public boolean shouldRefreshIfInputPresent() {
        return !scanComplete
                || allItems.isEmpty()
                || (allItems.size() == 1 && "classes.dex".equals(allItems.get(0)));
    }

    public void refreshFromCurrentInput(boolean forcePathRefresh) {
        if (host == null) return;
        ActivityMainBinding binding = binding();
        final String input = safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.edtSmaliDexInput);
        final String current = currentEntry();
        host.runIo(() -> {
            PermsTestSmaliTools.DexEntryScanResult scan = null;
            Throwable error = null;
            try {
                if (!TextUtils.isEmpty(input)) scan = PermsTestSmaliTools.listDexEntriesDetailed(input);
            } catch (Throwable t) {
                error = t;
            }
            final PermsTestSmaliTools.DexEntryScanResult finalScan = scan;
            final Throwable dexError = error;
            host.runOnUiThread(() -> applyScanResult(finalScan, current, forcePathRefresh, dexError));
        });
    }

    public void rememberSelectedWithoutPathRefresh(String entry) {
        try {
            if (host == null) return;
            String clean = PermsTestSmaliTools.normalizeDexEntryName(entry);
            selectedEntry = clean;
            lastDropdownText = clean;
            host.setDexEntrySelectedText(clean);
            host.resizeDexEntryDropdown(displayItems);
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[Debugging] DEX entry selection failed: " + t.getMessage() + "\n");
        }
    }

    public void applySelected(String entry, boolean forcePathRefresh, boolean fromUser) {
        try {
            ActivityMainBinding binding = binding();
            if (host == null || binding == null || binding.tabDebugging == null) return;
            String clean = PermsTestSmaliTools.normalizeDexEntryName(entry);
            selectedEntry = clean;
            lastDropdownText = clean;
            host.setDexEntrySelectedText(clean);
            host.updateDerivedPathsForSelectedDexEntry(clean, forcePathRefresh);
            host.resizeDexEntryDropdown(displayItems);
            if (fromUser && binding.tabDebugging.txtSmaliStatus != null) {
                binding.tabDebugging.txtSmaliStatus.setText("Selected DEX entry: " + clean);
            }
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[Debugging] DEX entry selection failed: " + t.getMessage() + "\n");
        }
    }

    public String smaliDirForEntry(String dexEntry, boolean preferCurrentField) {
        ActivityMainBinding binding = binding();
        return DebuggingDexEntries.smaliDirForEntry(dexEntry, preferCurrentField,
                currentEntry(),
                safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.edtSmaliInputDir),
                host == null ? "" : host.currentDebuggingWorkRoot());
    }

    public String dexOutForEntry(String dexEntry, boolean preferCurrentField) {
        ActivityMainBinding binding = binding();
        return DebuggingDexEntries.dexOutForEntry(dexEntry, preferCurrentField,
                currentEntry(),
                safeText(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.edtSmaliDexOutput),
                host == null ? "" : host.currentDebuggingWorkRoot());
    }

    private void applyScanResult(PermsTestSmaliTools.DexEntryScanResult scan, String preferredEntry, boolean forcePathRefresh, Throwable error) {
        try {
            ActivityMainBinding binding = binding();
            if (host == null || binding == null || binding.tabDebugging == null) return;
            scanComplete = true;
            allItems.clear();
            allItems.addAll(DebuggingDexEntries.fromScan(scan));

            String chosen = host.applyDexEntryListToDropdown(allItems, displayItems, preferredEntry);
            if (TextUtils.isEmpty(chosen)) {
                selectedEntry = "";
                lastDropdownText = "";
            } else {
                applySelected(chosen, forcePathRefresh, false);
            }

            DebuggingDexEntries.ScanStatus status = DebuggingDexEntries.scanStatus(scan, displayItems.size(), error);
            if (!TextUtils.isEmpty(status.logLine)) host.appendOutput(status.logLine);
            host.setDebuggingBusy(false, status.status);
        } catch (Throwable t) {
            host.finishDebuggingToolError("DEX entry scan", t);
        }
    }

    private ActivityMainBinding binding() {
        return host == null ? null : host.getBinding();
    }

    private static String safeText(TextView view) {
        try {
            CharSequence text = view == null ? null : view.getText();
            return text == null ? "" : text.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
