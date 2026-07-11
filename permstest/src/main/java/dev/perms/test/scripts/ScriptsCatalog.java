package dev.perms.test.scripts;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;

public final class ScriptsCatalog {
    public static final String ASSET_SCRIPTS_DIR = "scripts";
    public static final String USER_SCRIPTS_DIR = "/sdcard/dev.perms.test/scripts";

    private ScriptsCatalog() {
    }

    public static final class ScriptRef {
        public final String displayName;
        public final String fileName;
        public final boolean isUser;
        public final String absolutePath;

        public ScriptRef(String displayName, String fileName, boolean isUser, String absolutePath) {
            this.displayName = displayName;
            this.fileName = fileName;
            this.isUser = isUser;
            this.absolutePath = absolutePath;
        }
    }

    public static final class Result {
        public final ArrayList<String> displayNames = new ArrayList<>();
        public final LinkedHashMap<String, ScriptRef> refs = new LinkedHashMap<>();
        public boolean needShellUserLoad;

        public String firstDisplayName() {
            return displayNames.isEmpty() ? null : displayNames.get(0);
        }

        public String findSameScriptDisplayName(ScriptRef previous) {
            if (previous == null) return null;
            for (ScriptRef ref : refs.values()) {
                if (ref == null) continue;
                if (ref.isUser == previous.isUser && TextUtils.equals(ref.fileName, previous.fileName)) {
                    return ref.displayName;
                }
            }
            return null;
        }
    }

    public static Result build(Context context, boolean loadUserScripts, boolean hideLabels) {
        Result result = new Result();
        addAssetScripts(context, result.displayNames, result.refs, hideLabels);
        if (loadUserScripts) {
            addUserScriptsFromFiles(result.displayNames, result.refs, hideLabels);
            result.needShellUserLoad = !hasAnyUserScript(result.refs);
        }
        sort(result.displayNames);
        return result;
    }

    public static void addShellUserScripts(ArrayList<String> displayNames,
                                           LinkedHashMap<String, ScriptRef> refs,
                                           String stdout,
                                           boolean hideLabels) {
        if (displayNames == null || refs == null || stdout == null) return;
        String[] lines = stdout.split("\n");
        for (String line : lines) {
            if (line == null) continue;
            String fileName = line.trim();
            if (!isShellScriptName(fileName)) continue;
            String displayName = uniqueDisplayName(refs, displayUserScript(fileName, hideLabels), "User");
            if (refs.containsKey(displayName)) continue;
            displayNames.add(displayName);
            refs.put(displayName, new ScriptRef(displayName, fileName, true, childPath(USER_SCRIPTS_DIR, fileName)));
        }
        sort(displayNames);
    }

    public static String displayBuiltInScript(String fileName, boolean hideLabels) {
        String name = fileName == null ? "" : fileName;
        return hideLabels ? name : "[Built-in] " + name;
    }

    public static String displayUserScript(String fileName, boolean hideLabels) {
        String name = fileName == null ? "" : fileName;
        return hideLabels ? name : "[User] " + name;
    }

    private static void addAssetScripts(Context context,
                                        ArrayList<String> displayNames,
                                        LinkedHashMap<String, ScriptRef> refs,
                                        boolean hideLabels) {
        try {
            if (context == null) return;
            String[] names = context.getAssets().list(ASSET_SCRIPTS_DIR);
            if (names == null) return;
            for (String fileName : names) {
                if (!isShellScriptName(fileName)) continue;
                String displayName = uniqueDisplayName(refs, displayBuiltInScript(fileName, hideLabels), "Built-in");
                displayNames.add(displayName);
                refs.put(displayName, new ScriptRef(displayName, fileName, false, null));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addUserScriptsFromFiles(ArrayList<String> displayNames,
                                                LinkedHashMap<String, ScriptRef> refs,
                                                boolean hideLabels) {
        try {
            File dir = new File(USER_SCRIPTS_DIR);
            try {
                if (!dir.exists()) dir.mkdirs();
            } catch (Throwable ignored) {
            }
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (file == null || !file.isFile()) continue;
                String fileName = file.getName();
                if (!isShellScriptName(fileName)) continue;
                String displayName = uniqueDisplayName(refs, displayUserScript(fileName, hideLabels), "User");
                displayNames.add(displayName);
                refs.put(displayName, new ScriptRef(displayName, fileName, true, file.getAbsolutePath()));
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasAnyUserScript(LinkedHashMap<String, ScriptRef> refs) {
        try {
            for (ScriptRef ref : refs.values()) {
                if (ref != null && ref.isUser) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isShellScriptName(String fileName) {
        if (fileName == null) return false;
        String name = fileName.trim();
        if (name.isEmpty()) return false;
        return name.toLowerCase(Locale.US).endsWith(".sh");
    }

    private static String uniqueDisplayName(LinkedHashMap<String, ScriptRef> refs, String base, String suffix) {
        String displayName = base == null ? "" : base;
        if (refs != null && refs.containsKey(displayName)) displayName = displayName + " (" + suffix + ")";
        return displayName;
    }

    private static String childPath(String dir, String child) {
        String base = dir == null ? "" : dir;
        if (!base.endsWith("/")) base += "/";
        return base + (child == null ? "" : child);
    }

    private static void sort(ArrayList<String> displayNames) {
        try {
            Collections.sort(displayNames, String.CASE_INSENSITIVE_ORDER);
        } catch (Throwable ignored) {
        }
    }
}
