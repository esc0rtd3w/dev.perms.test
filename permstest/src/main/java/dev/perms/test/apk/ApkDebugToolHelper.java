package dev.perms.test.apk;

import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.util.Locale;

public final class ApkDebugToolHelper {
    public static final String TOOL_ZIPALIGN = "zipalign";
    public static final String TOOL_APKTOOL = "apktool";
    public static final String TOOL_JADX = "jadx";

    public static final String DEBUG_KEYSTORE_ASSET = "tools/debug-signing/debug.keystore";
    public static final String DEBUG_KEYSTORE_NAME = "debug.keystore";
    public static final String DEBUG_KEY_ALIAS = "androiddebugkey";
    public static final String DEBUG_KEY_PASSWORD = "android";

    public static final String PUBLIC_ROOT = "/data/local/tmp/dev.perms.test";
    public static final String PUBLIC_BIN_DIR = PUBLIC_ROOT + "/bin";
    public static final String PUBLIC_SUPPORT_DIR = PUBLIC_ROOT + "/support";
    public static final String PUBLIC_APKPATCH_DIR = PUBLIC_ROOT + "/apkpatch";
    public static final String DEFAULT_EXPORT_DIR = "/storage/emulated/0/dev.perms.test/debug_packages";
    public static final String APKTOOL_LOG_DIR = "/storage/emulated/0/dev.perms.test/logs/apktool";

    private ApkDebugToolHelper() {
    }

    public static String defaultOutputPath(String sourceLabel) {
        String name = sanitizeSourceName(sourceLabel);
        String lower = name.toLowerCase(Locale.US);
        String ext = supportedPackageExtension(lower);
        if (TextUtils.isEmpty(ext)) {
            ext = ".apk";
            name = name + ext;
        }
        String stem = name.substring(0, name.length() - ext.length());
        return DEFAULT_EXPORT_DIR + "/" + stem + "-debuggable" + ext;
    }

    public static String sanitizeSourceName(String value) {
        if (TextUtils.isEmpty(value)) return "package.apk";
        String out = value.trim();
        int slash = Math.max(out.lastIndexOf('/'), out.lastIndexOf(File.separatorChar));
        if (slash >= 0 && slash + 1 < out.length()) {
            out = out.substring(slash + 1);
        }
        out = out.replaceAll("[\r\n\t\0]+", "_");
        out = out.replaceAll("[^a-zA-Z0-9._ -]", "_");
        if (out.isEmpty()) out = "package.apk";
        return out;
    }

    public static String defaultStageFilename(Uri uri, String label) {
        String name = sanitizeSourceName(label);
        if (TextUtils.isEmpty(supportedPackageExtension(name.toLowerCase(Locale.US)))) {
            name = name + ".apk";
        }
        return name;
    }

    public static String supportedPackageExtension(String lowerName) {
        if (TextUtils.isEmpty(lowerName)) return "";
        if (lowerName.endsWith(".apk")) return ".apk";
        if (lowerName.endsWith(".apkm")) return ".apkm";
        if (lowerName.endsWith(".apks")) return ".apks";
        if (lowerName.endsWith(".xapk")) return ".xapk";
        if (lowerName.endsWith(".zip")) return ".zip";
        return "";
    }

    public static String shQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
