package dev.perms.test.debugging;

import dev.perms.test.debugging.smali.PermsTestSmaliTools;

import android.text.TextUtils;

import java.util.ArrayList;

public final class DebuggingDexEntries {
    public static final class ScanStatus {
        public final String status;
        public final String logLine;

        public ScanStatus(String status, String logLine) {
            this.status = status;
            this.logLine = logLine;
        }
    }

    private DebuggingDexEntries() {
    }

    public static String current(String typedEntry, String selectedEntry) {
        String entry = typedEntry == null ? "" : typedEntry.trim();
        if (TextUtils.isEmpty(entry)) entry = selectedEntry == null ? "" : selectedEntry.trim();
        if (TextUtils.isEmpty(entry)) entry = "classes.dex";
        return PermsTestSmaliTools.normalizeDexEntryName(entry);
    }

    public static ArrayList<String> fromScan(PermsTestSmaliTools.DexEntryScanResult scan) {
        ArrayList<String> out = new ArrayList<>();
        if (scan == null || scan.entries == null) return out;
        for (String entry : scan.entries) addUniqueNormalized(out, entry);
        return out;
    }

    public static ArrayList<String> displayItems(Iterable<String> entries) {
        ArrayList<String> out = new ArrayList<>();
        if (entries == null) return out;
        for (String entry : entries) addUniqueNormalized(out, entry);
        return out;
    }

    public static String choose(ArrayList<String> items, String preferredEntry) {
        if (items == null || items.isEmpty()) return "";
        String preferred = PermsTestSmaliTools.normalizeDexEntryName(preferredEntry);
        if (!TextUtils.isEmpty(preferred) && items.contains(preferred)) return preferred;
        return items.get(0);
    }

    public static ArrayList<String> activeEntries(ArrayList<String> items, boolean scanComplete, String currentEntry) {
        ArrayList<String> out = new ArrayList<>();
        if (items != null && !items.isEmpty()) {
            out.addAll(items);
            return out;
        }
        if (scanComplete) return out;
        String current = PermsTestSmaliTools.normalizeDexEntryName(currentEntry);
        if (!TextUtils.isEmpty(current)) out.add(current);
        return out;
    }

    public static String smaliDirForEntry(String dexEntry, boolean preferCurrentField,
            String currentEntry, String currentField, String workRoot) {
        String entry = PermsTestSmaliTools.normalizeDexEntryName(dexEntry);
        if (preferCurrentField && entry.equalsIgnoreCase(PermsTestSmaliTools.normalizeDexEntryName(currentEntry))
                && !TextUtils.isEmpty(currentField)) {
            return currentField;
        }
        return DebuggingWorkPaths.smaliDir(workRoot, entry);
    }

    public static String dexOutForEntry(String dexEntry, boolean preferCurrentField,
            String currentEntry, String currentField, String workRoot) {
        String entry = PermsTestSmaliTools.normalizeDexEntryName(dexEntry);
        if (preferCurrentField && entry.equalsIgnoreCase(PermsTestSmaliTools.normalizeDexEntryName(currentEntry))
                && !TextUtils.isEmpty(currentField)) {
            return currentField;
        }
        return DebuggingWorkPaths.dexOutput(workRoot, entry);
    }

    public static ScanStatus scanStatus(PermsTestSmaliTools.DexEntryScanResult scan, int displayCount, Throwable error) {
        if (error != null) {
            String msg = safeError(error);
            return new ScanStatus("DEX entry scan failed: " + msg,
                    "[Debugging] DEX entry scan failed: " + msg + "\n");
        }
        int skipped = scan == null ? 0 : Math.max(0, scan.skippedCount);
        if (displayCount <= 0) {
            String status = "No valid DEX entries found in this source.";
            if (skipped > 0) status += " Skipped non-DEX entries: " + skipped + ".";
            return new ScanStatus(status, null);
        }
        String status = "APK ready. DEX entries: " + displayCount;
        String log = null;
        if (skipped > 0) {
            status += "  skipped non-DEX: " + skipped;
            log = "[Debugging] Skipped non-DEX .dex-named entries: " + skipped + "\n";
        }
        return new ScanStatus(status, log);
    }

    private static void addUniqueNormalized(ArrayList<String> out, String entry) {
        if (out == null) return;
        String clean = PermsTestSmaliTools.normalizeDexEntryName(entry);
        if (!TextUtils.isEmpty(clean) && !out.contains(clean)) out.add(clean);
    }

    private static String safeError(Throwable t) {
        if (t == null) return "Unknown error";
        String msg = t.getMessage();
        if (TextUtils.isEmpty(msg)) msg = t.toString();
        return msg == null ? "Unknown error" : msg;
    }
}
