package dev.perms.test.debugging;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;

import android.text.TextUtils;

import java.io.File;

public final class DebuggingWorkPaths {
    public static final String DEFAULT_WORK_NAME = "package";

    public static final class DerivedPaths {
        public final String root;
        public final String smaliDir;
        public final String dexOutput;
        public final String apkOutput;

        public DerivedPaths(String root, String smaliDir, String dexOutput, String apkOutput) {
            this.root = root == null ? "" : root;
            this.smaliDir = smaliDir == null ? "" : smaliDir;
            this.dexOutput = dexOutput == null ? "" : dexOutput;
            this.apkOutput = apkOutput == null ? "" : apkOutput;
        }
    }

    private DebuggingWorkPaths() {
    }

    public static String safeWorkName(String value) {
        String v = TextUtils.isEmpty(value) ? DEFAULT_WORK_NAME : value.trim();
        v = v.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (v.length() > 96) v = v.substring(0, 96);
        return TextUtils.isEmpty(v) ? DEFAULT_WORK_NAME : v;
    }

    public static String workName(String label, String fallback, String packageName) {
        String name = !TextUtils.isEmpty(packageName) ? packageName : (!TextUtils.isEmpty(label) ? label : fallback);
        if (TextUtils.isEmpty(name)) name = "package.apk";
        name = ApkDebugToolHelper.sanitizeSourceName(name);
        name = name.replaceAll("(?i)\\.(apk|apks|apkm|xapk|zip|dex)$", "");
        return safeWorkName(name);
    }

    public static String workNameForInput(String label, String inputPath, String packageName) {
        String fallback = TextUtils.isEmpty(inputPath) ? "package.apk" : new File(inputPath).getName();
        return workName(label, fallback, packageName);
    }

    public static String rootForWorkName(String workName) {
        String safeName = TextUtils.isEmpty(workName) ? DEFAULT_WORK_NAME : safeWorkName(workName);
        return PermsTestSmaliTools.DEFAULT_BUILD_ROOT + "/" + safeName;
    }

    public static String rootForInput(String label, String inputPath, String packageName) {
        return rootForWorkName(workNameForInput(label, inputPath, packageName));
    }

    public static String smaliDir(String root, String dexEntry) {
        String workName = workNameFromRoot(root);
        return PermsTestSmaliTools.DEFAULT_SMALI_ROOT + "/" + workName
                + "/" + PermsTestSmaliTools.defaultSmaliFolderNameForDexEntry(dexEntry);
    }

    public static String dexOutput(String root, String dexEntry) {
        return normalizeRoot(root) + "/" + PermsTestSmaliTools.defaultDexOutputNameForDexEntry(dexEntry);
    }

    public static DerivedPaths derive(String workName, String dexEntry, boolean debugApk) {
        String safeName = TextUtils.isEmpty(workName) ? DEFAULT_WORK_NAME : safeWorkName(workName);
        String root = rootForWorkName(safeName);
        String normalizedEntry = PermsTestSmaliTools.normalizeDexEntryName(dexEntry);
        String smaliDir = smaliDir(root, normalizedEntry);
        String dexOutput = dexOutput(root, normalizedEntry);
        String apkOutput = root + "/" + safeName + (debugApk ? "-debug-rebuilt.apk" : "-rebuilt-signed.apk");
        return new DerivedPaths(root, smaliDir, dexOutput, apkOutput);
    }

    private static String normalizeRoot(String root) {
        if (TextUtils.isEmpty(root)) return rootForWorkName(DEFAULT_WORK_NAME);
        return root;
    }

    public static String workNameFromRootSafe(String root) {
        return workNameFromRoot(root);
    }

    private static String workNameFromRoot(String root) {
        if (TextUtils.isEmpty(root)) return DEFAULT_WORK_NAME;
        String value = root.replace('\\', '/');
        while (value.endsWith("/") && value.length() > 1) value = value.substring(0, value.length() - 1);
        String prefix = PermsTestSmaliTools.DEFAULT_ROOT + "/";
        if (value.startsWith(prefix)) value = value.substring(prefix.length());
        if (value.startsWith("smali/")) value = value.substring("smali/".length());
        if (value.startsWith("java/")) value = value.substring("java/".length());
        if (value.startsWith("build/")) value = value.substring("build/".length());
        if (value.startsWith("/")) {
            int last = value.lastIndexOf('/');
            if (last >= 0 && last + 1 < value.length()) value = value.substring(last + 1);
        } else {
            int slash = value.indexOf('/');
            if (slash >= 0) value = value.substring(0, slash);
        }
        return safeWorkName(value);
    }
}
