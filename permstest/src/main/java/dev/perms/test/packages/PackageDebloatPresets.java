package dev.perms.test.packages;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Built-in package-state/debloat preset assets for the Packages tab. */
public final class PackageDebloatPresets {
    private static final String ASSET_DIR = "package_states";

    private static final Preset[] KNOWN_PRESETS = new Preset[] {
            new Preset("Android system preset", ASSET_DIR + "/debloat_android.json"),
            new Preset("Carrier preset", ASSET_DIR + "/debloat_carrier.json"),
            new Preset("Google preset", ASSET_DIR + "/debloat_google.json"),
            new Preset("Samsung preset", ASSET_DIR + "/debloat_samsung.json"),
            new Preset("Full debloat preset", ASSET_DIR + "/debloat_full.json")
    };

    private PackageDebloatPresets() {
    }

    @NonNull
    public static List<Preset> list(@NonNull Context context) {
        ArrayList<Preset> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        AssetManager assets = context.getAssets();
        for (Preset preset : KNOWN_PRESETS) {
            if (presetExists(assets, preset.assetPath)) {
                out.add(preset);
                seen.add(preset.assetPath);
            }
        }
        try {
            String[] names = assets.list(ASSET_DIR);
            if (names != null) {
                ArrayList<String> extra = new ArrayList<>(Arrays.asList(names));
                Collections.sort(extra, String.CASE_INSENSITIVE_ORDER);
                for (String name : extra) {
                    if (TextUtils.isEmpty(name) || !name.toLowerCase(Locale.US).endsWith(".json")) continue;
                    String path = ASSET_DIR + "/" + name;
                    if (seen.contains(path)) continue;
                    out.add(new Preset(prettyLabel(name), path));
                    seen.add(path);
                }
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(out, new Comparator<Preset>() {
            @Override
            public int compare(Preset left, Preset right) {
                int leftOrder = knownOrder(left == null ? null : left.assetPath);
                int rightOrder = knownOrder(right == null ? null : right.assetPath);
                if (leftOrder != rightOrder) return leftOrder - rightOrder;
                String a = left == null ? "" : left.label;
                String b = right == null ? "" : right.label;
                return a.compareToIgnoreCase(b);
            }
        });
        return out;
    }

    @NonNull
    public static String readJson(@NonNull Context context, @NonNull Preset preset) throws Exception {
        String path = safeAssetPath(preset.assetPath);
        if (TextUtils.isEmpty(path)) throw new IllegalArgumentException("Unsafe preset asset path");
        InputStream in = context.getAssets().open(path);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static boolean presetExists(AssetManager assets, String path) {
        try {
            InputStream in = assets.open(path);
            try { return true; } finally { try { in.close(); } catch (Throwable ignored) {} }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int knownOrder(String assetPath) {
        if (assetPath == null) return 9999;
        for (int i = 0; i < KNOWN_PRESETS.length; i++) {
            if (assetPath.equals(KNOWN_PRESETS[i].assetPath)) return i;
        }
        return 9999;
    }

    @NonNull
    private static String safeAssetPath(String path) {
        if (path == null) return "";
        String trimmed = path.trim();
        if (!trimmed.startsWith(ASSET_DIR + "/")) return "";
        if (trimmed.contains("..") || trimmed.contains("\\") || trimmed.contains("//")) return "";
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/';
            if (!ok) return "";
        }
        return trimmed;
    }

    @NonNull
    private static String prettyLabel(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        if (value.toLowerCase(Locale.US).endsWith(".json")) {
            value = value.substring(0, value.length() - 5);
        }
        value = value.replace('_', ' ').replace('-', ' ').trim();
        if (TextUtils.isEmpty(value)) return "Package preset";
        String[] parts = value.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.length() == 0) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        out.append(" preset");
        return out.toString();
    }

    public static final class Preset {
        public final String label;
        public final String assetPath;

        Preset(@NonNull String label, @NonNull String assetPath) {
            this.label = label;
            this.assetPath = assetPath;
        }
    }
}
