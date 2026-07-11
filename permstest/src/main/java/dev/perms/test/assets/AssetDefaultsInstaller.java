package dev.perms.test.assets;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/** Copies bundled JSON defaults from assets into the public PermsTest working folders. */
public final class AssetDefaultsInstaller {
    public static final String SAVE_DATA_ASSET_ROOT = "save_data";
    public static final String MEMORY_PAYLOAD_ASSET_ROOT = "memory_payloads";
    public static final String PUBLIC_HOME_RELATIVE = "dev.perms.test";
    public static final String PUBLIC_HOME_ROOT = "/sdcard/dev.perms.test";
    public static final String SAVE_DATA_PUBLIC_ROOT = "/sdcard/dev.perms.test/save_data";
    public static final String MEMORY_PAYLOAD_PUBLIC_ROOT = "/sdcard/dev.perms.test/memory_payloads";

    private AssetDefaultsInstaller() {
    }

    /**
     * Best-effort public home-folder bootstrap.
     *
     * The app keeps working if shared storage is not available yet; feature-owned
     * folders will retry their own creation later through their normal paths.
     */
    public static boolean ensurePublicHomeDirectory() {
        try {
            File direct = new File(PUBLIC_HOME_ROOT);
            if (direct.isDirectory()) return true;
            if (direct.mkdirs()) return true;
        } catch (Throwable ignored) {
        }
        try {
            File external = Environment.getExternalStorageDirectory();
            File home = new File(external, PUBLIC_HOME_RELATIVE);
            return home.isDirectory() || home.mkdirs();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int installBundledSaveDataConfigs(Context context, String packageName, boolean overwrite) {
        return installBundledJsonPackage(context, SAVE_DATA_ASSET_ROOT, SAVE_DATA_PUBLIC_ROOT, packageName, overwrite);
    }

    public static int installBundledMemoryPayloads(Context context, String packageName, boolean overwrite) {
        return installBundledJsonPackage(context, MEMORY_PAYLOAD_ASSET_ROOT, MEMORY_PAYLOAD_PUBLIC_ROOT, packageName, overwrite);
    }

    public static int installBundledJsonPackage(Context context,
                                                String assetRoot,
                                                String publicRoot,
                                                String packageName,
                                                boolean overwrite) {
        if (context == null || TextUtils.isEmpty(assetRoot) || TextUtils.isEmpty(publicRoot) || TextUtils.isEmpty(packageName)) {
            return 0;
        }
        try {
            AssetManager assets = context.getAssets();
            String cleanPackage = packageName.trim();
            String assetPackage = resolveBundledPackageFolder(assets, assetRoot, cleanPackage);
            if (TextUtils.isEmpty(assetPackage)) return 0;
            String assetDir = assetRoot + "/" + assetPackage;
            String[] names = assets.list(assetDir);
            if (names == null || names.length == 0) return 0;

            File outDir = new File(publicRoot, cleanPackage);
            if (!outDir.exists() && !outDir.mkdirs()) return 0;

            int copied = 0;
            for (String name : names) {
                if (TextUtils.isEmpty(name) || !name.toLowerCase(Locale.US).endsWith(".json")) continue;
                File out = new File(outDir, name);
                if (!overwrite && out.exists() && out.length() > 0) continue;
                try (InputStream in = assets.open(assetDir + "/" + name);
                     FileOutputStream fos = new FileOutputStream(out, false)) {
                    byte[] buf = new byte[32 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                    fos.flush();
                    copied++;
                }
            }
            return copied;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String resolveBundledPackageFolder(AssetManager assets, String assetRoot, String packageName) {
        if (assets == null || TextUtils.isEmpty(assetRoot) || TextUtils.isEmpty(packageName)) return "";
        try {
            String[] direct = assets.list(assetRoot + "/" + packageName);
            if (direct != null && direct.length > 0) return packageName;
        } catch (Throwable ignored) {
        }
        try {
            String[] packages = assets.list(assetRoot);
            if (packages == null) return "";
            for (String candidate : packages) {
                if (!TextUtils.isEmpty(candidate) && packageName.equals(candidate)) return candidate;
            }
            for (String candidate : packages) {
                if (!TextUtils.isEmpty(candidate) && packageName.equalsIgnoreCase(candidate)) return candidate;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
