package dev.perms.test.savedata;

import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Applies and scans section-scoped save-data byte edits defined by SaveDataConfig. */
public final class SaveDataPatchEngine {
    private SaveDataPatchEngine() {}

    public static Result apply(File file, SaveDataConfig config, String presetName) throws Exception {
        return apply(file, config, presetName, false, true, null);
    }

    public static Result apply(File file,
                               SaveDataConfig config,
                               String presetName,
                               boolean applyEnabledPresets,
                               boolean applyAllInstances,
                               List<String> selectedMatchIds) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("Save file was not copied locally.");
        if (config == null) throw new IllegalArgumentException("Config is not loaded.");

        String hex = bytesToHex(readAll(file));
        List<Match> matches = scan(hex, config, presetName, applyEnabledPresets);
        if (matches.isEmpty()) return new Result(0, 0, 0, 0, "No matching save-data instances were found.");

        Set<String> selected = new HashSet<>();
        if (selectedMatchIds != null) selected.addAll(selectedMatchIds);
        ArrayList<Match> targets = new ArrayList<>();
        for (Match match : matches) {
            if (match == null) continue;
            if (applyAllInstances || selected.contains(match.id)) targets.add(match);
        }
        if (targets.isEmpty()) return new Result(0, matches.size(), 0, 0, "No save-data instances were selected.");

        Collections.sort(targets, (a, b) -> b.hexOffset - a.hexOffset);
        int changes = 0;
        int alreadyPatched = 0;
        int selectedTargets = targets.size();
        StringBuilder detail = new StringBuilder();
        String lastPreset = "";
        int presetChanges = 0;
        int presetAlreadyPatched = 0;
        for (Match match : targets) {
            if (!lastPreset.equals(match.presetName)) {
                appendPresetDetail(detail, lastPreset, presetChanges, presetAlreadyPatched);
                lastPreset = match.presetName;
                presetChanges = 0;
                presetAlreadyPatched = 0;
            }
            if (match.alreadyPatched) {
                alreadyPatched++;
                presetAlreadyPatched++;
                continue;
            }
            if (match.hexOffset >= 0
                    && match.hexOffset + match.originalHex.length() <= hex.length()
                    && hex.regionMatches(match.hexOffset, match.originalHex, 0, match.originalHex.length())) {
                hex = hex.substring(0, match.hexOffset) + match.patchedHex + hex.substring(match.hexOffset + match.originalHex.length());
                changes++;
                presetChanges++;
            } else if (match.hexOffset >= 0
                    && match.hexOffset + match.patchedHex.length() <= hex.length()
                    && hex.regionMatches(match.hexOffset, match.patchedHex, 0, match.patchedHex.length())) {
                alreadyPatched++;
                presetAlreadyPatched++;
            }
        }
        appendPresetDetail(detail, lastPreset, presetChanges, presetAlreadyPatched);

        if (changes > 0) {
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(hexToBytes(hex));
                out.flush();
            }
        }
        return new Result(changes, matches.size(), selectedTargets, alreadyPatched, detail.toString());
    }

    public static List<Match> scan(File file, SaveDataConfig config, String presetName, boolean applyEnabledPresets) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("Save file was not copied locally.");
        if (config == null) throw new IllegalArgumentException("Config is not loaded.");
        return scan(bytesToHex(readAll(file)), config, presetName, applyEnabledPresets);
    }

    private static List<Match> scan(String hex, SaveDataConfig config, String presetName, boolean applyEnabledPresets) {
        ArrayList<Match> matches = new ArrayList<>();
        List<SaveDataConfig.Preset> presets = selectedPresets(config, presetName, applyEnabledPresets);
        if (TextUtils.isEmpty(hex) || presets.isEmpty()) return matches;

        for (SaveDataConfig.Preset preset : presets) {
            if (preset == null) continue;
            for (SaveDataConfig.Edit edit : preset.edits) {
                if (edit == null || TextUtils.isEmpty(edit.originalHex) || TextUtils.isEmpty(edit.patchedHex)) continue;
                scanEdit(hex, preset, edit, matches);
            }
        }
        return matches;
    }

    private static List<SaveDataConfig.Preset> selectedPresets(SaveDataConfig config, String presetName, boolean applyEnabledPresets) {
        ArrayList<SaveDataConfig.Preset> presets = new ArrayList<>();
        if (config == null) return presets;
        if (!applyEnabledPresets) {
            if (!TextUtils.isEmpty(presetName)) {
                SaveDataConfig.Preset preset = config.findPreset(presetName);
                if (preset != null) presets.add(preset);
            }
            return presets;
        }
        for (SaveDataConfig.Preset preset : config.presets) {
            if (preset != null && preset.enabled) presets.add(preset);
        }
        return presets;
    }

    private static void scanEdit(String hex, SaveDataConfig.Preset preset, SaveDataConfig.Edit edit, ArrayList<Match> out) {
        int start = 0;
        int end = hex.length();
        if (!TextUtils.isEmpty(edit.sectionStartHex) && !TextUtils.isEmpty(edit.sectionEndHex)) {
            int s = hex.indexOf(edit.sectionStartHex);
            int e = hex.indexOf(edit.sectionEndHex, s < 0 ? 0 : s + edit.sectionStartHex.length());
            if (s < 0 || e < 0 || s >= e) return;
            start = s;
            end = e;
        }

        ArrayList<FoundMatch> found = new ArrayList<>();
        findPattern(hex, start, end, edit.originalHex, false, found);
        if (!edit.originalHex.equals(edit.patchedHex)) {
            findPattern(hex, start, end, edit.patchedHex, true, found);
        }
        Collections.sort(found, (a, b) -> a.hexOffset - b.hexOffset);

        int max = edit.maxChanges <= 0 ? 99999 : edit.maxChanges;
        for (int i = 0; i < found.size() && i < max; i++) {
            FoundMatch foundMatch = found.get(i);
            String id = matchId(preset, edit, foundMatch.hexOffset);
            out.add(new Match(id, preset.name, edit.name, edit.sectionStartLabel, i, foundMatch.hexOffset,
                    edit.originalHex, edit.patchedHex, foundMatch.alreadyPatched));
        }
    }

    private static void findPattern(String hex, int start, int end, String pattern, boolean alreadyPatched, ArrayList<FoundMatch> out) {
        if (TextUtils.isEmpty(pattern)) return;
        int pos = start;
        while (pos < end) {
            int idx = hex.indexOf(pattern, pos);
            if (idx < 0 || idx >= end) break;
            out.add(new FoundMatch(idx, alreadyPatched));
            pos = idx + Math.max(2, pattern.length());
        }
    }

    private static String matchId(SaveDataConfig.Preset preset, SaveDataConfig.Edit edit, int hexOffset) {
        int presetIndex = preset == null ? -1 : preset.index;
        int editIndex = edit == null ? -1 : edit.index;
        return "p" + presetIndex + "e" + editIndex + "o" + hexOffset;
    }

    private static void appendPresetDetail(StringBuilder detail, String presetName, int changes, int alreadyPatched) {
        if (TextUtils.isEmpty(presetName)) return;
        detail.append(presetName).append(": ").append(changes).append(" changes");
        if (alreadyPatched > 0) detail.append(", ").append(alreadyPatched).append(" already patched");
        detail.append('\n');
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    private static String bytesToHex(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format(Locale.US, "%02X", b & 0xff));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        String clean = SaveDataConfig.cleanHex(hex);
        int len = clean.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i + 1 < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(clean.substring(i, i + 2), 16);
        }
        return out;
    }

    private static final class FoundMatch {
        final int hexOffset;
        final boolean alreadyPatched;

        FoundMatch(int hexOffset, boolean alreadyPatched) {
            this.hexOffset = hexOffset;
            this.alreadyPatched = alreadyPatched;
        }
    }

    public static final class Match {
        public final String id;
        public final String presetName;
        public final String editName;
        public final String sectionName;
        public final int instanceIndex;
        public final int hexOffset;
        public final int byteOffset;
        public final String originalHex;
        public final String patchedHex;
        public final boolean alreadyPatched;

        Match(String id,
              String presetName,
              String editName,
              String sectionName,
              int instanceIndex,
              int hexOffset,
              String originalHex,
              String patchedHex,
              boolean alreadyPatched) {
            this.id = id == null ? "" : id;
            this.presetName = presetName == null ? "" : presetName;
            this.editName = editName == null ? "" : editName;
            this.sectionName = sectionName == null ? "" : sectionName;
            this.instanceIndex = instanceIndex;
            this.hexOffset = hexOffset;
            this.byteOffset = Math.max(0, hexOffset / 2);
            this.originalHex = originalHex == null ? "" : originalHex;
            this.patchedHex = patchedHex == null ? "" : patchedHex;
            this.alreadyPatched = alreadyPatched;
        }

        public String label() {
            StringBuilder sb = new StringBuilder();
            if (alreadyPatched) sb.append("[patched] ");
            if (!TextUtils.isEmpty(presetName)) sb.append(presetName).append(" / ");
            sb.append(TextUtils.isEmpty(editName) ? "Edit" : editName);
            sb.append(" #").append(instanceIndex + 1);
            sb.append(" @ 0x").append(Integer.toHexString(byteOffset).toUpperCase(Locale.US));
            if (!TextUtils.isEmpty(sectionName)) sb.append(" (").append(sectionName).append(")");
            return sb.toString();
        }
    }

    public static final class Result {
        public final int changes;
        public final int edits;
        public final int selected;
        public final int alreadyPatched;
        public final String detail;

        Result(int changes, int edits, int selected, int alreadyPatched, String detail) {
            this.changes = changes;
            this.edits = edits;
            this.selected = selected;
            this.alreadyPatched = alreadyPatched;
            this.detail = detail == null ? "" : detail;
        }
    }
}
