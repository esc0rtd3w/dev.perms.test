package dev.perms.test.memory.payload;

import dev.perms.test.memory.MemoryToolRuntime;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Stores Hex Editor payload JSON files.
 *
 * Payloads are package-scoped because their byte signatures are only meaningful
 * inside the target process that produced them.  A payload contains the bytes to
 * find (original_hex) and the bytes to write (patched_hex); saved addresses are
 * informational only because ASLR and app restarts can move the same structure.
 *
 * Public storage under /sdcard/dev.perms.test remains the preferred location so
 * the files are easy to inspect and hand-edit.  Direct Java file IO is attempted
 * when it works, but shell/Shizuku access is used for shared-storage paths that
 * normal app APIs cannot reliably read or write on newer Android builds.
 */
public final class MemoryHexPayloadStore {
    public static final String PAYLOAD_DIR = "/sdcard/dev.perms.test/memory_payloads";
    public static final String PAYLOAD_SCHEMA = "perms_test_memory_hex_payload";

    /**
     * Fully validated payload loaded from JSON.  Both original and patched byte
     * arrays are normalized as uppercase hexadecimal text with matching lengths.
     */
    public static final class Payload {
        public final File sourceFile;
        public final String packageName;
        public final String name;
        public final String fileName;
        public final int length;
        public final String originalHex;
        public final String patchedHex;
        public final String maskHex;
        public final String sectionStartHex;
        public final String sectionEndHex;
        public final boolean enabled;
        public final boolean preserveMaskWildcards;
        public final long originalAddress;
        public final long patchedAddress;

        public Payload(File sourceFile,
                String packageName,
                String name,
                String fileName,
                int length,
                String originalHex,
                String patchedHex,
                String maskHex,
                String sectionStartHex,
                String sectionEndHex,
                boolean enabled,
                boolean preserveMaskWildcards,
                long originalAddress,
                long patchedAddress) {
            this.sourceFile = sourceFile;
            this.packageName = packageName;
            this.name = name;
            this.fileName = fileName;
            this.length = length;
            this.originalHex = originalHex;
            this.patchedHex = patchedHex;
            this.maskHex = maskHex;
            this.sectionStartHex = sectionStartHex;
            this.sectionEndHex = sectionEndHex;
            this.enabled = enabled;
            this.preserveMaskWildcards = preserveMaskWildcards;
            this.originalAddress = originalAddress;
            this.patchedAddress = patchedAddress;
        }
    }

    /**
     * Save result returned to the overlay so it can update Details, load the
     * completed payload immediately, and keep the Patch value synchronized.
     */
    public static final class SaveResult {
        public final String path;
        public final String name;
        public final String fileName;
        public final int savedBytes;
        public final boolean savedAsPatched;
        public final boolean complete;
        public final String originalHex;
        public final String patchedHex;
        public final String maskHex;
        public final String sectionStartHex;
        public final String sectionEndHex;
        public final boolean preserveMaskWildcards;

        public SaveResult(String path,
                   String name,
                   String fileName,
                   int savedBytes,
                   boolean savedAsPatched,
                   boolean complete,
                   String originalHex,
                   String patchedHex,
                   String maskHex,
                   String sectionStartHex,
                   String sectionEndHex,
                   boolean preserveMaskWildcards) {
            this.path = path;
            this.name = name;
            this.fileName = fileName;
            this.savedBytes = savedBytes;
            this.savedAsPatched = savedAsPatched;
            this.complete = complete;
            this.originalHex = originalHex;
            this.patchedHex = patchedHex;
            this.maskHex = maskHex;
            this.sectionStartHex = sectionStartHex;
            this.sectionEndHex = sectionEndHex;
            this.preserveMaskWildcards = preserveMaskWildcards;
        }
    }



    /** Result returned by the Memory-tab JSON editor after normalization. */
    public static final class EditorSaveResult {
        public final String path;
        public final String fileName;
        public final int length;
        public final int wildcardBytes;

        public EditorSaveResult(String path, String fileName, int length, int wildcardBytes) {
            this.path = path;
            this.fileName = fileName;
            this.length = length;
            this.wildcardBytes = wildcardBytes;
        }
    }

    /** Result returned after merging a mask range into an existing payload. */
    public static final class SaveMaskResult {
        public final String path;
        public final String name;
        public final int length;
        public final int maskedBytes;
        public final String maskHex;

        public SaveMaskResult(String path, String name, int length, int maskedBytes, String maskHex) {
            this.path = path;
            this.name = name;
            this.length = length;
            this.maskedBytes = maskedBytes;
            this.maskHex = maskHex;
        }
    }

    private MemoryHexPayloadStore() {
    }


    public static final int MASK_PRESET_NO_CHANGE = -1;
    public static final int MASK_PRESET_COMPARE_ALL = 0;
    public static final int MASK_PRESET_IGNORE_NON_PRINTABLE = 1;
    public static final int MASK_PRESET_IGNORE_NUL = 2;
    public static final int MASK_PRESET_IGNORE_MIDDLE_GAP = 3;
    public static final int MASK_PRESET_KEEP_READABLE_EDGES = 4;
    public static final int MASK_PRESET_KEEP_FIRST_READABLE = 5;
    public static final int MASK_PRESET_KEEP_LAST_READABLE = 6;
    public static final int MASK_PRESET_KEEP_FIRST_READABLE_LAST_BYTE = 7;
    public static final int MASK_PRESET_KEEP_FIRST_READABLE_LAST_4 = 8;
    public static final int MASK_PRESET_PREFIX_COLON = 9;
    public static final int MASK_PRESET_PREFIX_EQUALS = 10;
    public static final int MASK_PRESET_IGNORE_ASCII_DIGITS = 11;

    public static final class MaskPreset {
        public final int id;
        public final String title;
        public final String description;
        public final boolean preserveMaskedBytes;

        public MaskPreset(int id, String title, String description, boolean preserveMaskedBytes) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.preserveMaskedBytes = preserveMaskedBytes;
        }
    }

    public static MaskPreset[] maskPresets(boolean includeNoChange) {
        ArrayList<MaskPreset> presets = new ArrayList<>();
        if (includeNoChange) {
            presets.add(new MaskPreset(MASK_PRESET_NO_CHANGE,
                    "Keep existing mask",
                    "Do not change the payload mask while saving these bytes.", false));
        }
        presets.add(new MaskPreset(MASK_PRESET_COMPARE_ALL,
                "Compare all bytes",
                "Exact match. Every original byte must be found before patched bytes are written.", false));
        presets.add(new MaskPreset(MASK_PRESET_IGNORE_NON_PRINTABLE,
                "Ignore non-printable bytes",
                "Compares readable ASCII bytes and wildcards binary, pointer, padding, and NUL bytes anywhere.", true));
        presets.add(new MaskPreset(MASK_PRESET_IGNORE_NUL,
                "Ignore NUL bytes only",
                "Compares every non-zero byte and wildcards only 00 bytes. Useful for simple padding.", true));
        presets.add(new MaskPreset(MASK_PRESET_IGNORE_MIDDLE_GAP,
                "Ignore middle gap between readable text",
                "Compares bytes before/after the binary gap and wildcards the unstable middle bytes. Good for name ... value payloads.", true));
        presets.add(new MaskPreset(MASK_PRESET_KEEP_READABLE_EDGES,
                "Keep first and last readable text",
                "Compares the first readable text block and last readable value only; wildcards everything between and around them.", true));
        presets.add(new MaskPreset(MASK_PRESET_KEEP_FIRST_READABLE_LAST_BYTE,
                "Keep first readable text and last byte",
                "Compares the first readable label, one NUL delimiter, and final value byte; wildcards the unstable middle bytes.", true));
        presets.add(new MaskPreset(MASK_PRESET_KEEP_FIRST_READABLE_LAST_4,
                "Keep first readable text and last 4 bytes",
                "Compares the first readable label, one NUL delimiter, and the final dword-sized suffix.", true));
        presets.add(new MaskPreset(MASK_PRESET_KEEP_FIRST_READABLE,
                "Keep first readable text only",
                "Compares the first readable label/key and wildcards everything after it.", false));
        presets.add(new MaskPreset(MASK_PRESET_KEEP_LAST_READABLE,
                "Keep last readable text only",
                "Compares the final readable value and wildcards everything before it. Use only when the value is unique enough.", false));
        presets.add(new MaskPreset(MASK_PRESET_PREFIX_COLON,
                "Keep text through ': ', wildcard the rest",
                "Compares a prefix such as 'Player ID: ' and ignores the variable value after it.", false));
        presets.add(new MaskPreset(MASK_PRESET_PREFIX_EQUALS,
                "Keep key through '=', wildcard the value",
                "Compares key=value style names through '=' and ignores the value after it.", false));
        presets.add(new MaskPreset(MASK_PRESET_IGNORE_ASCII_DIGITS,
                "Ignore ASCII digits",
                "Compares all bytes except ASCII 0-9 digits. Useful when small decimal values move around.", true));
        return presets.toArray(new MaskPreset[0]);
    }

    public static MaskPreset maskPresetById(int presetId) {
        for (MaskPreset preset : maskPresets(true)) {
            if (preset.id == presetId) return preset;
        }
        return null;
    }

    public static String buildMaskHexForPreset(int presetId, String hex) {
        return maskPatternToHex(buildMaskPatternForPreset(presetId, hex), byteCountForHex(hex));
    }

    public static String buildMaskPatternForPreset(int presetId, String hex) {
        String clean = normalizeAnyHexBytes(hex);
        switch (presetId) {
            case MASK_PRESET_COMPARE_ALL:
                return buildAllFixedMaskPattern(clean);
            case MASK_PRESET_IGNORE_NON_PRINTABLE:
                return buildIgnoreNonPrintableMaskPattern(clean);
            case MASK_PRESET_IGNORE_NUL:
                return buildIgnoreByteValueMaskPattern(clean, 0x00, "No 00 bytes were found to wildcard.");
            case MASK_PRESET_IGNORE_MIDDLE_GAP:
                return buildIgnoreMiddleNonPrintableGapMaskPattern(clean);
            case MASK_PRESET_KEEP_READABLE_EDGES:
                return buildKeepReadableEdgesMaskPattern(clean);
            case MASK_PRESET_KEEP_FIRST_READABLE:
                return buildKeepFirstReadableRunMaskPattern(clean);
            case MASK_PRESET_KEEP_LAST_READABLE:
                return buildKeepLastReadableRunMaskPattern(clean);
            case MASK_PRESET_KEEP_FIRST_READABLE_LAST_BYTE:
                return buildKeepFirstReadableAndSuffixMaskPattern(clean, 1);
            case MASK_PRESET_KEEP_FIRST_READABLE_LAST_4:
                return buildKeepFirstReadableAndSuffixMaskPattern(clean, 4);
            case MASK_PRESET_PREFIX_COLON:
                return buildPrefixSeparatorMaskPattern(clean, ": ");
            case MASK_PRESET_PREFIX_EQUALS:
                return buildPrefixSeparatorMaskPattern(clean, "=");
            case MASK_PRESET_IGNORE_ASCII_DIGITS:
                return buildIgnoreAsciiDigitMaskPattern(clean);
            default:
                return buildAllFixedMaskPattern(clean);
        }
    }

    private static String buildAllFixedMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        StringBuilder out = new StringBuilder(bytes.length);
        for (byte b : bytes) out.append(patternFixedChar(b & 0xff));
        return out.toString();
    }

    private static String buildIgnoreNonPrintableMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        StringBuilder out = new StringBuilder(bytes.length);
        int fixed = 0;
        for (byte b : bytes) {
            int v = b & 0xff;
            if (isPrintableAscii(v)) {
                out.append(patternFixedChar(v));
                fixed++;
            } else {
                out.append('?');
            }
        }
        if (fixed <= 0) throw new IllegalArgumentException("No printable bytes were found to keep fixed.");
        return out.toString();
    }

    private static String buildIgnoreByteValueMaskPattern(String hex, int ignoredByte, String emptyMessage) {
        byte[] bytes = hexToBytes(hex);
        StringBuilder out = new StringBuilder(bytes.length);
        int fixed = 0;
        int wildcard = 0;
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v == ignoredByte) {
                out.append('?');
                wildcard++;
            } else {
                out.append(patternFixedChar(v));
                fixed++;
            }
        }
        if (fixed <= 0) throw new IllegalArgumentException("Mask must leave at least one fixed byte.");
        if (wildcard <= 0) throw new IllegalArgumentException(emptyMessage);
        return out.toString();
    }

    private static String buildIgnoreMiddleNonPrintableGapMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.size() < 2) {
            throw new IllegalArgumentException("Need at least two readable text blocks with a middle gap.");
        }
        ReadableRun first = runs.get(0);
        ReadableRun last = runs.get(runs.size() - 1);
        if (first.end >= last.start) {
            throw new IllegalArgumentException("No middle gap was found between readable text blocks.");
        }
        StringBuilder out = new StringBuilder(bytes.length);
        int fixed = 0;
        int wildcard = 0;
        for (int p = 0; p < bytes.length; p++) {
            if (p >= first.end && p < last.start) {
                out.append('?');
                wildcard++;
            } else {
                out.append(patternFixedChar(bytes[p] & 0xff));
                fixed++;
            }
        }
        if (fixed <= 0) throw new IllegalArgumentException("Mask must leave at least one fixed byte.");
        if (wildcard <= 0) throw new IllegalArgumentException("No middle gap was found between readable text blocks.");
        return out.toString();
    }

    private static String buildKeepReadableEdgesMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.size() < 2) {
            throw new IllegalArgumentException("Need at least two readable text blocks to keep readable edges.");
        }
        StringBuilder out = wildcardPattern(bytes.length);
        writeFixedRun(out, bytes, runs.get(0));
        writeFixedRun(out, bytes, runs.get(runs.size() - 1));
        return out.toString();
    }

    private static String buildKeepFirstReadableRunMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.isEmpty()) throw new IllegalArgumentException("No readable text block was found.");
        StringBuilder out = wildcardPattern(bytes.length);
        writeFixedRun(out, bytes, runs.get(0));
        return out.toString();
    }

    private static String buildKeepLastReadableRunMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.isEmpty()) throw new IllegalArgumentException("No readable text block was found.");
        StringBuilder out = wildcardPattern(bytes.length);
        writeFixedRun(out, bytes, runs.get(runs.size() - 1));
        return out.toString();
    }

    private static String buildKeepFirstReadableAndSuffixMaskPattern(String hex, int suffixBytes) {
        byte[] bytes = hexToBytes(hex);
        ArrayList<ReadableRun> runs = readableRuns(bytes, 3);
        if (runs.isEmpty()) throw new IllegalArgumentException("No readable text block was found.");
        if (bytes.length <= suffixBytes) throw new IllegalArgumentException("Payload is too short for this suffix mask.");
        StringBuilder out = wildcardPattern(bytes.length);
        ReadableRun first = runs.get(0);
        writeFixedRun(out, bytes, first);
        writeFixedNullDelimiterAfterRun(out, bytes, first);
        int start = Math.max(0, bytes.length - Math.max(1, suffixBytes));
        for (int i = start; i < bytes.length; i++) out.setCharAt(i, patternFixedChar(bytes[i] & 0xff));
        int fixed = 0;
        int wildcard = 0;
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (c == '?' || c == 'X' || c == 'x') wildcard++; else fixed++;
        }
        if (fixed <= 0) throw new IllegalArgumentException("Mask must leave at least one fixed byte.");
        if (wildcard <= 0) throw new IllegalArgumentException("No middle bytes would be wildcarded.");
        return out.toString();
    }

    private static void writeFixedNullDelimiterAfterRun(StringBuilder out, byte[] bytes, ReadableRun run) {
        if (out == null || bytes == null || run == null) return;
        int i = Math.max(0, run.end);
        if (i < bytes.length && bytes[i] == 0) {
            out.setCharAt(i, patternFixedChar(bytes[i] & 0xff));
        }
    }

    private static String buildPrefixSeparatorMaskPattern(String hex, String separator) {
        byte[] bytes = hexToBytes(hex);
        String ascii = asciiPreview(hex);
        int idx = ascii.indexOf(separator);
        if (idx < 0) throw new IllegalArgumentException("Separator not found: " + separator);
        int keepThrough = idx + separator.length();
        if (keepThrough <= 0 || keepThrough >= bytes.length) {
            throw new IllegalArgumentException("Preset would not leave both fixed and wildcard bytes.");
        }
        StringBuilder out = new StringBuilder(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            out.append(i < keepThrough ? patternFixedChar(bytes[i] & 0xff) : '?');
        }
        return out.toString();
    }

    private static String buildIgnoreAsciiDigitMaskPattern(String hex) {
        byte[] bytes = hexToBytes(hex);
        StringBuilder out = new StringBuilder(bytes.length);
        int fixed = 0;
        int wildcard = 0;
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v >= '0' && v <= '9') {
                out.append('?');
                wildcard++;
            } else {
                out.append(patternFixedChar(v));
                fixed++;
            }
        }
        if (fixed <= 0) throw new IllegalArgumentException("Mask must leave at least one fixed byte.");
        if (wildcard <= 0) throw new IllegalArgumentException("No ASCII digits were found to wildcard.");
        return out.toString();
    }

    private static final class ReadableRun {
        public final int start;
        public final int end;

        ReadableRun(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static ArrayList<ReadableRun> readableRuns(byte[] bytes, int minLength) {
        ArrayList<ReadableRun> runs = new ArrayList<>();
        int i = 0;
        while (bytes != null && i < bytes.length) {
            while (i < bytes.length && !isPrintableAscii(bytes[i] & 0xff)) i++;
            int start = i;
            while (i < bytes.length && isPrintableAscii(bytes[i] & 0xff)) i++;
            if (i - start >= minLength) runs.add(new ReadableRun(start, i));
        }
        return runs;
    }

    private static StringBuilder wildcardPattern(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) out.append('?');
        return out;
    }

    private static void writeFixedRun(StringBuilder pattern, byte[] bytes, ReadableRun run) {
        if (pattern == null || bytes == null || run == null) return;
        for (int i = Math.max(0, run.start); i < run.end && i < bytes.length && i < pattern.length(); i++) {
            pattern.setCharAt(i, patternFixedChar(bytes[i] & 0xff));
        }
    }

    private static String maskPatternToHex(String pattern, int byteCount) {
        String value = pattern == null ? "" : pattern;
        if (value.length() != byteCount) {
            throw new IllegalArgumentException("Mask pattern is " + formatPayloadLength(value.length())
                    + " but payload is " + formatPayloadLength(byteCount) + ".");
        }
        StringBuilder out = new StringBuilder(byteCount * 2);
        int fixed = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c > 0x7e) throw new IllegalArgumentException("Mask pattern must use one character per byte.");
            boolean wildcard = c == 'X' || c == 'x' || c == '?';
            out.append(wildcard ? "00" : "FF");
            if (!wildcard) fixed++;
        }
        if (fixed <= 0) throw new IllegalArgumentException("Mask must leave at least one fixed byte.");
        return out.toString();
    }

    private static boolean isPrintableAscii(int value) {
        return value >= 32 && value <= 126;
    }

    private static char patternFixedChar(int value) {
        if (isPrintableAscii(value)) {
            char c = (char) value;
            return (c == 'X' || c == 'x' || c == '?') ? 'F' : c;
        }
        return 'F';
    }

    /** Returns the public package folder used by user-visible payload JSON files. */
    public static String packageDirectoryPath(String packageName) {
        return PAYLOAD_DIR + "/" + sanitizePackageFolder(packageName);
    }

    public static String packageNameForJson(String packageName) {
        return packageName == null ? "" : packageName.trim();
    }

    public static boolean packageNameMatches(String payloadPackageName, String targetPackageName) {
        String payload = packageNameForJson(payloadPackageName);
        String target = packageNameForJson(targetPackageName);
        if (TextUtils.isEmpty(payload) || TextUtils.isEmpty(target)) return false;
        return TextUtils.equals(payload, target) || payload.equalsIgnoreCase(target);
    }

    public static String fileNameForPayloadName(String payloadName) {
        String safe = sanitizePayloadFilePart(payloadName);
        if (TextUtils.isEmpty(safe)) safe = "payload";
        return safe + ".json";
    }

    private static String normalizePayloadJsonFileName(String fileName, String fallbackFileName) {
        String value = fileName == null ? "" : fileName.trim();
        if (TextUtils.isEmpty(value)) value = fallbackFileName == null ? "payload.json" : fallbackFileName.trim();
        if (!value.toLowerCase(Locale.US).endsWith(".json")) value += ".json";
        value = sanitizePayloadFilePart(value.substring(0, value.length() - 5)) + ".json";
        if (TextUtils.equals(value, ".json")) value = "payload.json";
        return value;
    }

    /**
     * Merges the currently highlighted bytes into the package payload JSON.
     *
     * Saving as original_hex records the signature used for future searches.
     * Saving as patched_hex records the replacement bytes.  When one side already
     * exists, the byte count must match so Apply Payloads cannot write a malformed
     * patch.
     */
    public static SaveResult saveSelectedPayload(Context context,
                                          String packageName,
                                          String payloadName,
                                          String selectedHex,
                                          long selectedAddress,
                                          boolean saveAsPatched,
                                          String maskHexOverride,
                                          boolean replaceMask,
                                          Boolean preserveMaskWildcardsOverride,
                                          String sectionStartAscii,
                                          String sectionEndAscii,
                                          String sectionStartHexInput,
                                          String sectionEndHexInput,
                                          String stamp) throws Exception {
        String cleanPackage = packageNameForJson(packageName);
        if (TextUtils.isEmpty(cleanPackage)) throw new IllegalStateException("Select a target package before saving a payload.");
        String cleanName = payloadName == null ? "" : payloadName.trim();
        if (TextUtils.isEmpty(cleanName)) cleanName = "payload";
        String fileName = fileNameForPayloadName(cleanName);
        String normalizedHex = normalizeAnyHexBytes(selectedHex);
        int byteCount = byteCountForHex(normalizedHex);
        File finalFile = new File(packageDirectoryPath(cleanPackage), fileName);

        // Existing payloads are merged so a user can first save original bytes,
        // edit memory, then save the same payload name as patched bytes.
        JSONObject json = null;
        try {
            String existing = readPayloadTextFile(context, finalFile);
            if (!TextUtils.isEmpty(existing)) json = new JSONObject(existing);
        } catch (Throwable ignored) {
            json = null;
        }
        if (json == null) {
            json = new JSONObject();
            json.put("schema", PAYLOAD_SCHEMA);
            json.put("version", 1);
            json.put("package_name", cleanPackage);
            json.put("package_folder", sanitizePackageFolder(cleanPackage));
            json.put("name", cleanName);
            json.put("file_name", fileName);
            json.put("enabled", true);
            json.put("created_at", stamp);
        } else {
            String schema = json.optString("schema", "").trim();
            if (!TextUtils.isEmpty(schema) && !PAYLOAD_SCHEMA.equals(schema)) {
                throw new IllegalStateException("Existing file uses a different payload schema.");
            }
            String existingPackage = json.optString("package_name", "").trim();
            if (!TextUtils.isEmpty(existingPackage) && !TextUtils.equals(existingPackage, cleanPackage)) {
                throw new IllegalStateException("Existing file belongs to " + existingPackage + ".");
            }
            json.put("schema", PAYLOAD_SCHEMA);
            json.put("version", 1);
            json.put("package_name", cleanPackage);
            json.put("package_folder", sanitizePackageFolder(cleanPackage));
            json.put("name", cleanName);
            json.put("file_name", fileName);
        }

        // Keep the two byte blocks independent in the JSON.  Apply operations
        // search original_hex and write patched_hex; the address is not trusted.
        String originalHex = normalizeOptionalHex(json.optString("original_hex", ""));
        String patchedHex = normalizeOptionalHex(json.optString("patched_hex", ""));
        String maskHex = normalizeOptionalHex(json.optString("mask_hex", ""));
        String requestedMaskHex = replaceMask ? normalizeMaskHex(maskHexOverride, byteCount) : "";
        if (saveAsPatched) {
            if (!TextUtils.isEmpty(originalHex) && byteCountForHex(originalHex) != byteCount) {
                throw new IllegalStateException("Existing original bytes are " + formatPayloadLength(byteCountForHex(originalHex))
                        + "; selected patched bytes are " + formatPayloadLength(byteCount) + ".");
            }
            patchedHex = normalizedHex;
            json.put("patched_hex", patchedHex);
            json.put("patched_ascii_preview", asciiPreview(patchedHex));
            json.put("patched_address", formatHex(selectedAddress));
        } else {
            if (!TextUtils.isEmpty(patchedHex) && byteCountForHex(patchedHex) != byteCount) {
                throw new IllegalStateException("Existing patched bytes are " + formatPayloadLength(byteCountForHex(patchedHex))
                        + "; selected original bytes are " + formatPayloadLength(byteCount) + ".");
            }
            originalHex = normalizedHex;
            json.put("original_hex", originalHex);
            json.put("original_ascii_preview", asciiPreview(originalHex));
            json.put("original_address", formatHex(selectedAddress));
        }
        json.put("address", formatHex(selectedAddress));
        json.put("length", byteCount);
        json.put("updated_at", stamp);

        // A payload is complete only after both sides exist and have identical
        // lengths.  Incomplete files are allowed on disk but are skipped by Apply.
        boolean complete = !TextUtils.isEmpty(originalHex) && !TextUtils.isEmpty(patchedHex)
                && byteCountForHex(originalHex) == byteCountForHex(patchedHex);
        if (replaceMask) {
            maskHex = requestedMaskHex;
            int wildcardBytes = countWildcardBytes(maskHex);
            if (wildcardBytes > 0) {
                json.put("mask_hex", maskHex);
                json.put("mask_wildcard_count", wildcardBytes);
                json.put("mask_note", "FF bytes are compared. 00 bytes are wildcard bytes ignored by payload Find/Apply.");
                if (preserveMaskWildcardsOverride != null) {
                    json.put("preserve_mask_wildcards", preserveMaskWildcardsOverride.booleanValue());
                }
            } else {
                maskHex = "";
                json.remove("mask_hex");
                json.remove("mask_wildcard_count");
                json.remove("mask_note");
                json.remove("preserve_mask_wildcards");
            }
        } else if (!TextUtils.isEmpty(maskHex)) {
            if (byteCountForHex(maskHex) != byteCount) {
                throw new IllegalStateException("Existing mask is " + formatPayloadLength(byteCountForHex(maskHex))
                        + "; payload bytes are " + formatPayloadLength(byteCount) + ".");
            }
            maskHex = normalizeMaskHex(maskHex, byteCount);
            json.put("mask_hex", maskHex);
            json.put("mask_wildcard_count", countWildcardBytes(maskHex));
        }
        String sectionStartHex = normalizeSectionMarkerInput(sectionStartAscii, sectionStartHexInput, "Section start");
        String sectionEndHex = normalizeSectionMarkerInput(sectionEndAscii, sectionEndHexInput, "Section end");
        if (TextUtils.isEmpty(sectionStartHex) != TextUtils.isEmpty(sectionEndHex)) {
            throw new IllegalStateException("Section scope needs both section start and section end markers.");
        }
        if (!TextUtils.isEmpty(sectionStartHex)) {
            json.put("section_start_hex", sectionStartHex);
            json.put("section_end_hex", sectionEndHex);
            applyOptionalSectionAscii(json, "section_start_ascii", sectionStartAscii);
            applyOptionalSectionAscii(json, "section_end_ascii", sectionEndAscii);
            json.put("section_scope_note", "Payload matches are only written between the section start and end markers.");
        } else {
            json.remove("section_start_hex");
            json.remove("section_end_hex");
            json.remove("section_start_ascii");
            json.remove("section_end_ascii");
            json.remove("section_scope_note");
        }
        boolean preserveMaskWildcards = countWildcardBytes(maskHex) > 0 && json.optBoolean("preserve_mask_wildcards", false);
        String path = savePayloadTextToDisk(context, cleanPackage, fileName, prettyPayloadJson(json));
        return new SaveResult(path, cleanName, fileName, byteCount, saveAsPatched, complete, originalHex, patchedHex, maskHex, sectionStartHex, sectionEndHex, preserveMaskWildcards);
    }

    /**
     * Marks a byte range inside an existing complete payload as wildcard bytes.
     * FF bytes are compared during Find/Apply; 00 bytes are ignored.
     */
    public static SaveMaskResult savePayloadMask(Context context,
                                          String packageName,
                                          String payloadName,
                                          int maskOffset,
                                          int maskByteCount,
                                          String stamp) throws Exception {
        String cleanPackage = packageNameForJson(packageName);
        if (TextUtils.isEmpty(cleanPackage)) throw new IllegalStateException("Select a target package before saving a payload mask.");
        String cleanName = payloadName == null ? "" : payloadName.trim();
        if (TextUtils.isEmpty(cleanName)) throw new IllegalStateException("Load or save a payload before marking mask bytes.");
        File finalFile = new File(packageDirectoryPath(cleanPackage), fileNameForPayloadName(cleanName));
        JSONObject json = new JSONObject(readPayloadTextFile(context, finalFile));
        String schema = json.optString("schema", "").trim();
        if (!PAYLOAD_SCHEMA.equals(schema)) throw new IllegalStateException("Invalid payload schema.");
        String existingPackage = json.optString("package_name", "").trim();
        if (!TextUtils.equals(existingPackage, cleanPackage)) throw new IllegalStateException("Payload belongs to " + existingPackage + ".");
        String originalHex = normalizeAnyHexBytes(json.optString("original_hex", ""));
        String patchedHex = normalizeAnyHexBytes(json.optString("patched_hex", ""));
        int length = byteCountForHex(originalHex);
        if (length != byteCountForHex(patchedHex)) throw new IllegalStateException("original_hex and patched_hex lengths differ.");
        if (maskOffset < 0 || maskByteCount <= 0 || maskOffset + maskByteCount > length) {
            throw new IllegalStateException("Mask selection is outside the payload byte range.");
        }
        String maskHex = normalizeMaskHex(json.optString("mask_hex", ""), length);
        byte[] maskBytes = hexToBytes(maskHex);
        for (int i = maskOffset; i < maskOffset + maskByteCount; i++) {
            maskBytes[i] = 0;
        }
        String updatedMask = bytesToHex(maskBytes);
        int wildcardCount = countWildcardBytes(updatedMask);
        if (wildcardCount >= length) {
            throw new IllegalStateException("Mask must leave at least one fixed byte for searching.");
        }
        json.put("mask_hex", updatedMask);
        json.put("mask_wildcard_count", wildcardCount);
        json.put("mask_note", "FF bytes are compared. 00 bytes are wildcard bytes ignored by payload Find/Apply.");
        json.put("updated_at", stamp);
        String path = savePayloadTextToDisk(context, cleanPackage, json.optString("file_name", fileNameForPayloadName(cleanName)), prettyPayloadJson(json));
        return new SaveMaskResult(path, cleanName, length, wildcardCount, updatedMask);
    }


    /**
     * Saves JSON edited from the Memory tab while preserving unknown fields.
     *
     * Required payload fields are normalized or restored before disk write, so a
     * hand-edited file with missing metadata stays compatible with Find/Apply.
     */
    public static EditorSaveResult saveEditedPayload(Context context,
                                              JSONObject edited,
                                              String packageHint,
                                              String fileNameHint,
                                              String stamp) throws Exception {
        if (edited == null) throw new IllegalStateException("Payload JSON is empty.");
        String packageName = edited.optString("package_name", "").trim();
        if (TextUtils.isEmpty(packageName)) packageName = packageNameForJson(packageHint);
        if (TextUtils.isEmpty(packageName)) throw new IllegalStateException("Payload JSON is missing package_name.");
        String name = edited.optString("name", "").trim();
        if (TextUtils.isEmpty(name)) name = "payload";
        String fileName = edited.optString("file_name", "").trim();
        if (TextUtils.isEmpty(fileName)) fileName = TextUtils.isEmpty(fileNameHint) ? fileNameForPayloadName(name) : fileNameHint;
        if (!fileName.toLowerCase(Locale.US).endsWith(".json")) fileName += ".json";
        fileName = sanitizePayloadFilePart(fileName.substring(0, fileName.length() - 5)) + ".json";
        if (TextUtils.equals(fileName, ".json")) fileName = fileNameForPayloadName(name);

        String originalHex = normalizeAnyHexBytes(edited.optString("original_hex", ""));
        String patchedHex = normalizeAnyHexBytes(edited.optString("patched_hex", ""));
        int originalBytes = byteCountForHex(originalHex);
        int patchedBytes = byteCountForHex(patchedHex);
        if (originalBytes <= 0) throw new IllegalStateException("original_hex is empty.");
        if (patchedBytes <= 0) throw new IllegalStateException("patched_hex is empty.");
        if (originalBytes != patchedBytes) {
            throw new IllegalStateException("original_hex is " + formatPayloadLength(originalBytes)
                    + " but patched_hex is " + formatPayloadLength(patchedBytes) + ".");
        }
        String rawMaskHex = edited.optString("mask_hex", "").trim();
        boolean hasMaskHex = !TextUtils.isEmpty(rawMaskHex);
        String maskHex = hasMaskHex ? normalizeMaskHex(rawMaskHex, originalBytes) : "";
        int wildcardBytes = hasMaskHex ? countWildcardBytes(maskHex) : 0;
        if (hasMaskHex && wildcardBytes >= originalBytes) throw new IllegalStateException("Mask must leave at least one fixed byte for searching.");
        String sectionStartHex = normalizeSectionMarkerInput(edited.optString("section_start_ascii", ""), edited.optString("section_start_hex", ""), "Section start");
        String sectionEndHex = normalizeSectionMarkerInput(edited.optString("section_end_ascii", ""), edited.optString("section_end_hex", ""), "Section end");
        if (TextUtils.isEmpty(sectionStartHex) != TextUtils.isEmpty(sectionEndHex)) {
            throw new IllegalStateException("Section scope needs both section_start and section_end markers.");
        }

        edited.put("schema", PAYLOAD_SCHEMA);
        edited.put("version", 1);
        edited.put("package_name", packageName);
        edited.put("package_folder", sanitizePackageFolder(packageName));
        edited.put("name", name);
        edited.put("file_name", fileName);
        edited.put("original_hex", originalHex);
        edited.put("patched_hex", patchedHex);
        edited.put("length", originalBytes);
        edited.put("original_ascii_preview", asciiPreview(originalHex));
        edited.put("patched_ascii_preview", asciiPreview(patchedHex));
        if (!edited.has("enabled")) edited.put("enabled", true);
        boolean preserveMaskWildcards = hasMaskHex && edited.optBoolean("preserve_mask_wildcards", false);
        if (preserveMaskWildcards) {
            edited.put("preserve_mask_wildcards", true);
        } else {
            edited.remove("preserve_mask_wildcards");
        }
        if (hasMaskHex) {
            edited.put("mask_hex", maskHex);
            edited.put("mask_wildcard_count", wildcardBytes);
            edited.put("mask_note", "FF bytes are compared. 00 bytes are wildcard bytes ignored by payload Find/Apply.");
        } else {
            edited.remove("mask_hex");
            edited.remove("mask_wildcard_count");
            edited.remove("mask_note");
        }
        if (!TextUtils.isEmpty(sectionStartHex)) {
            edited.put("section_start_hex", sectionStartHex);
            edited.put("section_end_hex", sectionEndHex);
            edited.put("section_scope_note", "Payload matches are only written between the section start and end markers.");
        } else {
            edited.remove("section_start_hex");
            edited.remove("section_end_hex");
            edited.remove("section_start_ascii");
            edited.remove("section_end_ascii");
            edited.remove("section_scope_note");
        }
        if (TextUtils.isEmpty(edited.optString("created_at", ""))) edited.put("created_at", TextUtils.isEmpty(stamp) ? "manual" : stamp);
        edited.put("updated_at", TextUtils.isEmpty(stamp) ? edited.optString("created_at", "manual") : stamp);
        if (TextUtils.isEmpty(edited.optString("address", ""))) {
            String address = edited.optString("original_address", "").trim();
            if (TextUtils.isEmpty(address)) address = edited.optString("patched_address", "").trim();
            if (!TextUtils.isEmpty(address)) edited.put("address", address);
        }

        String path = savePayloadTextToDisk(context, packageName, fileName, prettyPayloadJson(edited));
        return new EditorSaveResult(path, fileName, originalBytes, wildcardBytes);
    }

    /** Updates the selected payload's enabled flag without changing byte content. */
    public static String setPayloadEnabled(Context context, File file, boolean enabled, String stamp) throws Exception {
        if (file == null) throw new java.io.IOException("No payload file selected");
        if (!isManagedPayloadJsonPath(context, file)) {
            throw new java.io.IOException("Refusing to edit unmanaged payload path: " + file.getAbsolutePath());
        }
        JSONObject json = new JSONObject(readPayloadTextFile(context, file));
        String packageName = json.optString("package_name", "").trim();
        if (TextUtils.isEmpty(packageName)) throw new IllegalStateException("Payload JSON is missing package_name.");
        String name = json.optString("name", "").trim();
        if (TextUtils.isEmpty(name)) name = file.getName();
        String fileName = normalizePayloadJsonFileName(json.optString("file_name", ""), file.getName());
        json.put("schema", PAYLOAD_SCHEMA);
        json.put("version", 1);
        json.put("package_name", packageName);
        json.put("package_folder", sanitizePackageFolder(packageName));
        json.put("name", name);
        json.put("file_name", fileName);
        json.put("enabled", enabled);
        json.put("updated_at", TextUtils.isEmpty(stamp) ? json.optString("updated_at", "manual") : stamp);
        return savePayloadTextToDisk(context, packageName, fileName, prettyPayloadJson(json));
    }

    /** Removes mask fields so the payload reverts to exact original_hex matching. */
    public static String clearPayloadMask(Context context, File file, String stamp) throws Exception {
        if (file == null) throw new java.io.IOException("No payload file selected");
        if (!isManagedPayloadJsonPath(context, file)) {
            throw new java.io.IOException("Refusing to edit unmanaged payload path: " + file.getAbsolutePath());
        }
        JSONObject json = new JSONObject(readPayloadTextFile(context, file));
        String packageName = json.optString("package_name", "").trim();
        if (TextUtils.isEmpty(packageName)) throw new IllegalStateException("Payload JSON is missing package_name.");
        String name = json.optString("name", "").trim();
        if (TextUtils.isEmpty(name)) name = file.getName();
        String fileName = normalizePayloadJsonFileName(json.optString("file_name", ""), file.getName());
        json.put("schema", PAYLOAD_SCHEMA);
        json.put("version", 1);
        json.put("package_name", packageName);
        json.put("package_folder", sanitizePackageFolder(packageName));
        json.put("name", name);
        json.put("file_name", fileName);
        json.remove("mask_hex");
        json.remove("mask_wildcard_count");
        json.remove("mask_note");
        json.remove("preserve_mask_wildcards");
        json.put("updated_at", TextUtils.isEmpty(stamp) ? json.optString("updated_at", "manual") : stamp);
        return savePayloadTextToDisk(context, packageName, fileName, prettyPayloadJson(json));
    }

    /**
     * Lists payload JSON files from the public package folder first, then app
     * fallback storage.  Duplicates are filtered by absolute path so shell-visible
     * files and Java-visible files do not appear twice.
     */
    public static ArrayList<File> listPayloadFiles(Context context, String packageName) {
        ArrayList<File> out = new ArrayList<>();
        String cleanPackage = packageNameForJson(packageName);
        if (TextUtils.isEmpty(cleanPackage)) return out;
        addShellPayloadFiles(context, out, cleanPackage);
        File direct = new File(packageDirectoryPath(cleanPackage));
        addPayloadFilesFromDirectory(out, direct);
        addCaseInsensitivePayloadDirectories(out, new File(PAYLOAD_DIR), sanitizePackageFolder(cleanPackage));
        File fallback = fallbackDirectory(context, cleanPackage);
        addPayloadFilesFromDirectory(out, fallback);
        File fallbackRoot = fallback == null ? null : fallback.getParentFile();
        addCaseInsensitivePayloadDirectories(out, fallbackRoot, sanitizePackageFolder(cleanPackage));
        Collections.sort(out, (a, b) -> Long.compare(payloadLastModified(context, b), payloadLastModified(context, a)));
        return out;
    }

    public static void deletePayloadFile(Context context, File file) throws java.io.IOException {
        if (file == null) throw new java.io.IOException("No payload file selected");
        if (!isManagedPayloadJsonPath(context, file)) {
            throw new java.io.IOException("Refusing to delete unmanaged payload path: " + file.getAbsolutePath());
        }
        boolean publicPayload = isPublicPayloadPath(file);
        if (publicPayload) {
            try {
                String quoted = MemoryToolRuntime.shQuote(file.getAbsolutePath());
                MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(context,
                        "rm -f " + quoted + " && [ ! -e " + quoted + " ]");
                if (r != null && r.exitCode == 0) return;
            } catch (Throwable ignored) {
            }
        }
        try {
            if (file.exists()) {
                if (file.delete()) return;
            } else if (!publicPayload) {
                return;
            }
        } catch (Throwable t) {
            throw new java.io.IOException(t.getMessage());
        }
        throw new java.io.IOException("Unable to delete " + file.getAbsolutePath());
    }

    private static boolean isManagedPayloadJsonPath(Context context, File file) {
        try {
            String path = file == null ? "" : file.getAbsolutePath();
            if (TextUtils.isEmpty(path) || !path.toLowerCase(Locale.US).endsWith(".json")) return false;
            if (path.startsWith(PAYLOAD_DIR + "/")) return true;
            File external = context == null ? null : context.getExternalFilesDir(null);
            if (external != null) {
                String root = new File(external, "memory_payloads").getAbsolutePath() + File.separator;
                if (path.startsWith(root)) return true;
            }
            File internal = context == null ? null : context.getFilesDir();
            if (internal != null) {
                String root = new File(internal, "memory_payloads").getAbsolutePath() + File.separator;
                if (path.startsWith(root)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String sectionMarkerAsciiToHex(String ascii) {
        String value = ascii == null ? "" : ascii.trim();
        if (TextUtils.isEmpty(value)) return "";
        return bytesToHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void applyOptionalSectionAscii(JSONObject json, String key, String ascii) throws Exception {
        String value = ascii == null ? "" : ascii.trim();
        if (TextUtils.isEmpty(value)) {
            json.remove(key);
        } else {
            json.put(key, value);
        }
    }

    private static String normalizeSectionMarkerInput(String ascii, String hex, String label) {
        String asciiHex = sectionMarkerAsciiToHex(ascii);
        String rawHex = hex == null ? "" : hex.trim();
        String normalizedHex = TextUtils.isEmpty(rawHex) ? "" : normalizeAnyHexBytes(rawHex);
        if (!TextUtils.isEmpty(asciiHex) && !TextUtils.isEmpty(normalizedHex) && !TextUtils.equals(asciiHex, normalizedHex)) {
            throw new IllegalStateException(label + " ASCII and hex markers do not match.");
        }
        return TextUtils.isEmpty(normalizedHex) ? asciiHex : normalizedHex;
    }

    private static String normalizeOptionalSectionMarkerHex(JSONObject json, String hexKey, String asciiKey) throws Exception {
        if (json == null) return "";
        String rawHex = json.optString(hexKey, "").trim();
        String hex = TextUtils.isEmpty(rawHex) ? "" : normalizeAnyHexBytes(rawHex);
        if (!TextUtils.isEmpty(hex)) return hex;
        String ascii = json.optString(asciiKey, "");
        if (TextUtils.isEmpty(ascii)) return "";
        byte[] bytes = ascii.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) return "";
        return bytesToHex(bytes);
    }

    /**
     * Loads and validates a payload JSON file.  Hand-edited files must preserve
     * schema, package_name, length, original_hex, and patched_hex consistency.
     */
    public static Payload loadPayload(Context context, File file) throws Exception {
        JSONObject json = new JSONObject(readPayloadTextFile(context, file));
        String schema = json.optString("schema", "").trim();
        if (!PAYLOAD_SCHEMA.equals(schema)) throw new IllegalStateException("Invalid payload schema.");
        String packageName = json.optString("package_name", "").trim();
        if (TextUtils.isEmpty(packageName)) throw new IllegalStateException("Payload JSON is missing package_name.");
        String originalHex = normalizeAnyHexBytes(json.optString("original_hex", ""));
        String patchedHex = normalizeAnyHexBytes(json.optString("patched_hex", ""));
        int originalBytes = byteCountForHex(originalHex);
        int patchedBytes = byteCountForHex(patchedHex);
        if (originalBytes != patchedBytes) {
            throw new IllegalStateException("original_hex is " + formatPayloadLength(originalBytes)
                    + " but patched_hex is " + formatPayloadLength(patchedBytes) + ".");
        }
        // Declared length is a guard for hand-edited JSON.  It must match the
        // decoded byte count or the payload is rejected before any memory write.
        Integer declaredBytes = payloadDeclaredLength(json);
        if (declaredBytes != null && declaredBytes != originalBytes) {
            throw new IllegalStateException("Payload length is " + formatPayloadLength(declaredBytes)
                    + " but original/patched bytes are " + formatPayloadLength(originalBytes) + ".");
        }
        String maskHex = normalizeMaskHex(json.optString("mask_hex", ""), originalBytes);
        String sectionStartHex = normalizeOptionalSectionMarkerHex(json, "section_start_hex", "section_start_ascii");
        String sectionEndHex = normalizeOptionalSectionMarkerHex(json, "section_end_hex", "section_end_ascii");
        if (TextUtils.isEmpty(sectionStartHex) != TextUtils.isEmpty(sectionEndHex)) {
            throw new IllegalStateException("Section scope needs both section_start and section_end markers.");
        }
        boolean enabled = !json.has("enabled") || json.optBoolean("enabled", true);
        boolean preserveMaskWildcards = countWildcardBytes(maskHex) > 0 && json.optBoolean("preserve_mask_wildcards", false);
        String name = json.optString("name", "").trim();
        String fileName = json.optString("file_name", "").trim();
        if (TextUtils.isEmpty(name)) name = file == null ? "payload" : file.getName();
        if (TextUtils.isEmpty(fileName)) fileName = file == null ? fileNameForPayloadName(name) : file.getName();
        long originalAddress = parseOptionalAddress(json.optString("original_address", ""));
        long patchedAddress = parseOptionalAddress(json.optString("patched_address", ""));
        return new Payload(file, packageName, name, fileName, originalBytes, originalHex, patchedHex, maskHex, sectionStartHex, sectionEndHex, enabled, preserveMaskWildcards, originalAddress, patchedAddress);
    }

    public static String payloadLabel(Context context, File file) {
        try {
            Payload payload = loadPayload(context, file);
            int masked = countWildcardBytes(payload.maskHex);
            return payload.name + (payload.enabled ? "" : " [disabled]") + " (" + formatPayloadLength(payload.length)
                    + (masked > 0 ? ", mask " + masked : "")
                    + (payload.preserveMaskWildcards ? ", preserve mask" : "")
                    + (!TextUtils.isEmpty(payload.sectionStartHex) ? ", section" : "")
                    + ", " + payload.packageName + ")";
        } catch (Throwable t) {
            String name = file == null ? "" : file.getName();
            String msg = t.getMessage();
            return name + " (invalid" + (TextUtils.isEmpty(msg) ? "" : ": " + msg) + ")";
        }
    }


    /**
     * Android's JSONObject writer escapes forward slashes in strings on some
     * platform builds.  The escaped form is valid JSON, but payload files are
     * meant to stay easy to hand-edit, so keep normal slash text readable.
     */
    private static String prettyPayloadJson(JSONObject json) throws Exception {
        return json.toString(2).replace("\\/", "/");
    }

    /**
     * Reads payload JSON using direct IO when available, with shell/Shizuku as the
     * privileged fallback for shared-storage paths.
     */
    public static String readPayloadTextFile(Context context, File file) throws java.io.IOException {
        if (file == null) throw new java.io.IOException("Payload file is empty");
        boolean publicPayload = isPublicPayloadPath(file);
        java.io.IOException shellFirstError = null;
        if (publicPayload) {
            try {
                String shell = readTextFileWithShell(context, file, null);
                if (!TextUtils.isEmpty(shell)) return shell;
            } catch (java.io.IOException e) {
                shellFirstError = e;
            }
        }
        try {
            String direct = readTextFileDirect(file);
            if (!TextUtils.isEmpty(direct) || !publicPayload) return direct;
        } catch (java.io.IOException directError) {
            try {
                return readTextFileWithShell(context, file, directError);
            } catch (java.io.IOException shellError) {
                if (shellFirstError != null && TextUtils.isEmpty(shellError.getMessage())) throw shellFirstError;
                throw shellError;
            }
        }
        return readTextFileWithShell(context, file, shellFirstError);
    }

    private static boolean isPublicPayloadPath(File file) {
        try {
            String path = file == null ? "" : file.getAbsolutePath();
            return !TextUtils.isEmpty(path) && path.startsWith(PAYLOAD_DIR + "/");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String readTextFileWithShell(Context context, File file, java.io.IOException cause) throws java.io.IOException {
        try {
            String cmd = "cat " + MemoryToolRuntime.shQuote(file.getAbsolutePath());
            MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(context, cmd);
            if (r != null && r.exitCode == 0 && !TextUtils.isEmpty(r.stdout)) return r.stdout;
            String msg = r == null ? "shell read failed" : ((r.stdout == null ? "" : r.stdout) + (r.stderr == null ? "" : r.stderr)).trim();
            java.io.IOException out = new java.io.IOException(TextUtils.isEmpty(msg) && cause != null ? cause.getMessage() : msg);
            if (cause != null) out.initCause(cause);
            throw out;
        } catch (java.io.IOException e) {
            throw e;
        } catch (Throwable t) {
            java.io.IOException out = new java.io.IOException(t.getMessage());
            if (cause != null) out.initCause(cause);
            throw out;
        }
    }

    /** Normalizes free-form hex text from UI or JSON into contiguous uppercase bytes. */
    public static String normalizeAnyHexBytes(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = value.replace("0x", "").replace("0X", "").replaceAll("[^0-9a-fA-F]", "");
        if (value.isEmpty()) throw new IllegalArgumentException("Enter hex bytes.");
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("Hex byte text must have an even digit count.");
        return value.toUpperCase(Locale.US);
    }

    public static int byteCountForHex(String hex) {
        return TextUtils.isEmpty(hex) ? 0 : normalizeAnyHexBytes(hex).length() / 2;
    }

    public static String asciiPreview(String hex) {
        try {
            byte[] bytes = new byte[byteCountForHex(hex)];
            String clean = normalizeAnyHexBytes(hex);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            }
            StringBuilder sb = new StringBuilder(bytes.length);
            for (byte b : bytes) {
                int v = b & 0xff;
                sb.append(v >= 32 && v <= 126 ? (char) v : '.');
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String formatPayloadLength(int bytes) {
        return bytes + " bytes (0x" + Integer.toHexString(bytes).toUpperCase(Locale.US) + ")";
    }

    /** Produces stable user-editable filenames while stripping unsafe path chars. */
    public static String sanitizePayloadFilePart(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder();
        boolean lastUnderscore = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (ok) {
                out.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore && out.length() > 0) {
                out.append('_');
                lastUnderscore = true;
            }
            if (out.length() >= 48) break;
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '_') out.deleteCharAt(out.length() - 1);
        return out.toString();
    }

    /** Keeps package folder names readable but prevents path traversal. */
    public static String sanitizePackageFolder(String value) {
        String raw = value == null ? "" : value.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (ok) out.append(c);
            if (out.length() >= 128) break;
        }
        return out.length() == 0 ? "unknown_package" : out.toString();
    }

    public static String normalizeMaskHex(String raw, int byteCount) {
        String normalized = normalizeOptionalHex(raw);
        if (TextUtils.isEmpty(normalized)) return repeatMaskByte(byteCount, "FF");
        if (byteCountForHex(normalized) != byteCount) {
            throw new IllegalStateException("mask_hex is " + formatPayloadLength(byteCountForHex(normalized))
                    + " but payload length is " + formatPayloadLength(byteCount) + ".");
        }
        byte[] bytes = hexToBytes(normalized);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = bytes[i] == 0 ? (byte) 0x00 : (byte) 0xFF;
        }
        return bytesToHex(bytes);
    }

    public static int countWildcardBytes(String maskHex) {
        try {
            byte[] bytes = hexToBytes(maskHex);
            int count = 0;
            for (byte b : bytes) {
                if (b == 0) count++;
            }
            return count;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean hasWildcardBytes(String maskHex) {
        return countWildcardBytes(maskHex) > 0;
    }

    private static String repeatMaskByte(int byteCount, String byteHex) {
        StringBuilder sb = new StringBuilder(Math.max(0, byteCount) * 2);
        for (int i = 0; i < byteCount; i++) sb.append(byteHex);
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        String clean = normalizeAnyHexBytes(hex);
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v < 0x10) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase(Locale.US));
        }
        return sb.toString();
    }

    private static long parseOptionalAddress(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (TextUtils.isEmpty(value)) return -1L;
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Long.parseUnsignedLong(value.substring(2), 16);
            }
            return Long.parseUnsignedLong(value, 16);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String normalizeOptionalHex(String raw) {
        try {
            return TextUtils.isEmpty(raw) ? "" : normalizeAnyHexBytes(raw);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Writes public JSON atomically through the current shell backend first.  The
     * app-private fallback is only for devices where public storage is unavailable.
     */
    private static String savePayloadTextToDisk(Context context, String packageName, String fileName, String text) throws java.io.IOException {
        File finalFile = new File(packageDirectoryPath(packageName), fileName);
        String shellStatus = savePayloadTextWithShell(context, finalFile, text);
        if (!TextUtils.isEmpty(shellStatus)) return shellStatus;
        File fallback = fallbackFile(context, packageName, fileName);
        if (fallback == null) throw new java.io.IOException("No writable payload directory");
        File dir = fallback.getParentFile();
        if (dir == null || ((!dir.exists() && !dir.mkdirs()) || !dir.isDirectory())) {
            throw new java.io.IOException("Cannot create fallback payload directory");
        }
        try (FileOutputStream fos = new FileOutputStream(fallback, false)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return fallback.getAbsolutePath();
    }

    private static String savePayloadTextWithShell(Context context, File finalFile, String text) {
        try {
            String dirPath = finalFile.getParentFile() == null ? PAYLOAD_DIR : finalFile.getParentFile().getAbsolutePath();
            // Base64 avoids quoting problems when JSON contains spaces, quotes,
            // or newlines, then mv makes the final file replacement atomic.
            String b64 = Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            String tmpPath = dirPath + "/." + finalFile.getName() + ".tmp." + Long.toHexString(System.nanoTime());
            StringBuilder cmd = new StringBuilder();
            cmd.append("mkdir -p ").append(MemoryToolRuntime.shQuote(dirPath)).append(" && ");
            cmd.append("if command -v base64 >/dev/null 2>&1; then ");
            cmd.append("printf %s ").append(MemoryToolRuntime.shQuote(b64)).append(" | base64 -d > ").append(MemoryToolRuntime.shQuote(tmpPath)).append("; ");
            cmd.append("else printf %s ").append(MemoryToolRuntime.shQuote(b64)).append(" | toybox base64 -d > ").append(MemoryToolRuntime.shQuote(tmpPath)).append("; fi");
            cmd.append(" && mv -f ").append(MemoryToolRuntime.shQuote(tmpPath)).append(' ').append(MemoryToolRuntime.shQuote(finalFile.getAbsolutePath()));
            cmd.append(" && chmod 0666 ").append(MemoryToolRuntime.shQuote(finalFile.getAbsolutePath()));
            MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(context, cmd.toString());
            if (r != null && r.exitCode == 0) return finalFile.getAbsolutePath();
            return "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Uses the shell-visible directory listing so Shizuku-owned writes are found. */
    private static void addShellPayloadFiles(Context context, ArrayList<File> out, String packageName) {
        try {
            String dir = packageDirectoryPath(packageName);
            String targetFolder = sanitizePackageFolder(packageName);
            String targetLower = targetFolder.toLowerCase(Locale.US);
            StringBuilder script = new StringBuilder();
            script.append("for f in ").append(MemoryToolRuntime.shQuote(dir)).append("/*.json; do [ -f \"$f\" ] && echo \"$f\"; done; ");
            script.append("for d in ").append(MemoryToolRuntime.shQuote(PAYLOAD_DIR)).append("/*; do ");
            script.append("[ -d \"$d\" ] || continue; b=\"${d##*/}\"; ");
            script.append("[ \"$(printf %s \"$b\" | tr '[:upper:]' '[:lower:]')\" = ")
                    .append(MemoryToolRuntime.shQuote(targetLower)).append(" ] || continue; ");
            script.append("for f in \"$d\"/*.json; do [ -f \"$f\" ] && echo \"$f\"; done; done");
            MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(context, script.toString());
            if (r == null || r.exitCode != 0 || TextUtils.isEmpty(r.stdout)) return;
            String[] lines = r.stdout.split("\\n");
            for (String line : lines) {
                String path = line == null ? "" : line.trim();
                if (path.endsWith(".json")) addPayloadFileIfMissing(out, new File(path));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addPayloadFilesFromDirectory(ArrayList<File> out, File dir) {
        try {
            File[] files = dir == null ? null : dir.listFiles((d, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".json"));
            if (files != null) {
                for (File f : files) addPayloadFileIfMissing(out, f);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addCaseInsensitivePayloadDirectories(ArrayList<File> out, File root, String packageFolder) {
        try {
            if (root == null || TextUtils.isEmpty(packageFolder)) return;
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs == null) return;
            for (File dir : dirs) {
                if (dir == null || !packageFolder.equalsIgnoreCase(dir.getName())) continue;
                addPayloadFilesFromDirectory(out, dir);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addPayloadFileIfMissing(ArrayList<File> out, File file) {
        if (file == null || TextUtils.isEmpty(file.getAbsolutePath())) return;
        String path = file.getAbsolutePath();
        String name = file.getName();
        for (File existing : out) {
            if (existing == null) continue;
            if (TextUtils.equals(existing.getAbsolutePath(), path)) return;
            // The public /sdcard payload folder is added first.  If an older
            // app-private fallback copy has the same JSON name, ignore it so
            // Apply All does not patch once and then report the stale duplicate.
            if (!TextUtils.isEmpty(name) && TextUtils.equals(existing.getName(), name)) return;
        }
        out.add(file);
    }

    private static long payloadLastModified(Context context, File file) {
        try {
            if (file != null && file.exists()) return file.lastModified();
        } catch (Throwable ignored) {}
        try {
            MemoryToolRuntime.CmdResult r = MemoryToolRuntime.runShellCommandCaptureSync(context,
                    "stat -c %Y " + MemoryToolRuntime.shQuote(file.getAbsolutePath()) + " 2>/dev/null || echo 0");
            String out = r == null || r.stdout == null ? "" : r.stdout.trim();
            int nl = out.indexOf('\n');
            if (nl >= 0) out = out.substring(0, nl).trim();
            return Long.parseLong(out);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static File fallbackDirectory(Context context, String packageName) {
        try {
            File base = context.getExternalFilesDir(null);
            if (base == null) base = context.getFilesDir();
            if (base == null) return null;
            return new File(new File(base, "memory_payloads"), sanitizePackageFolder(packageName));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File fallbackFile(Context context, String packageName, String fileName) {
        File dir = fallbackDirectory(context, packageName);
        return dir == null ? null : new File(dir, fileName);
    }

    private static String readTextFileDirect(File file) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            int n;
            while ((n = fis.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    /** Accepts decimal or 0x-prefixed lengths for hand-edited payload files. */
    private static Integer payloadDeclaredLength(JSONObject json) {
        if (json == null || !json.has("length")) return null;
        Object value = json.opt("length");
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof Number) {
            int n = ((Number) value).intValue();
            if (n < 0) throw new IllegalArgumentException("Payload length must not be negative.");
            return n;
        }
        String text = String.valueOf(value).trim();
        if (TextUtils.isEmpty(text)) return null;
        int n = (text.startsWith("0x") || text.startsWith("0X"))
                ? Integer.parseInt(text.substring(2), 16)
                : Integer.parseInt(text);
        if (n < 0) throw new IllegalArgumentException("Payload length must not be negative.");
        return n;
    }

    private static String formatHex(long value) {
        return "0x" + Long.toHexString(value);
    }
}
