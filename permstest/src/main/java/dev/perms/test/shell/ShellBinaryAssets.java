package dev.perms.test.shell;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import dev.perms.test.ShizukuCompat;

/**
 * Handles app-bundled shell binaries and the public shell-readable bin folder.
 */
public final class ShellBinaryAssets {
    public interface ReadyCheck {
        boolean isReadyAndGranted();
    }

    public static final String PUBLIC_TMP_ROOT = "/data/local/tmp/dev.perms.test";
    public static final String PUBLIC_BIN_DIR = PUBLIC_TMP_ROOT + "/bin";
    public static final String PUBLIC_STAGE_DIR = PUBLIC_TMP_ROOT + "/stage";
    public static final String PUBLIC_FILES_DIR = PUBLIC_TMP_ROOT + "/files";
    public static final String PUBLIC_EXTRACTED_APKS_DIR = "/sdcard/dev.perms.test/extracted_apks";

    public static final String BUNDLED_ASSET_DIR = "bin";
    private static final String[] STANDARD_ABI_ASSET_DIRS = new String[]{"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};
    private static final String[] BIN_SCAN_DIRS = new String[]{
            "/system/bin",
            "/system/sbin",
            "/system/usr/bin",
            "/system/usr/xbin",
            "/system/xbin",
            "/vendor/bin",
            "/product/bin",
            "/system_ext/bin",
            "/apex/com.android.runtime/bin"
    };

    private final Context context;
    private final ReadyCheck readyCheck;
    private String cachedBundledAbiDir = null;
    private boolean cachedBundledAbiDirResolved = false;

    public ShellBinaryAssets(Context context, ReadyCheck readyCheck) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        this.readyCheck = readyCheck;
    }

    public String[] getBundledAssetDirs() {
        String abiDir = resolveBundledAbiDir();
        if (!TextUtils.isEmpty(abiDir)) {
            return new String[]{BUNDLED_ASSET_DIR + "/" + abiDir, BUNDLED_ASSET_DIR};
        }
        return new String[]{BUNDLED_ASSET_DIR};
    }

    public boolean hasBundledAsset(String name) {
        try {
            if (TextUtils.isEmpty(name)) return false;
            for (String d : getBundledAssetDirs()) {
                try (InputStream in = context.getAssets().open(d + "/" + name)) {
                    return true;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public InputStream openBundledAsset(String name) throws IOException {
        IOException last = null;
        for (String d : getBundledAssetDirs()) {
            try {
                return context.getAssets().open(d + "/" + name);
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) throw last;
        throw new IOException("Asset not found: " + name);
    }

    public File getBundledStageDir() {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File d = new File(base, "bin_stage");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public void ensureBundledBinaryPublic(String name) {
        try {
            if (TextUtils.isEmpty(name)) return;
            if (!hasBundledAsset(name)) return;

            File stage = new File(getBundledStageDir(), name);
            if (!stage.exists() || stage.length() == 0) {
                try (InputStream in = new BufferedInputStream(openBundledAsset(name));
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(stage))) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                }
                try { stage.setReadable(true, false); } catch (Throwable ignored) {}
            }

            if (readyCheck == null || !readyCheck.isReadyAndGranted()) return;

            String src = stage.getAbsolutePath();
            String dst = PUBLIC_BIN_DIR + "/" + name;
            String sh = "mkdir -p '" + PUBLIC_BIN_DIR + "' && cp '" + src + "' '" + dst + "' && chmod 755 '" + dst + "'";
            Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c", sh}, null, null);
            try { p.getInputStream().close(); } catch (Throwable ignored) {}
            try { p.getErrorStream().close(); } catch (Throwable ignored) {}
            try { p.waitFor(); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    public File getAppBinDir() {
        try {
            File d = new File(context.getApplicationInfo().dataDir, "bin");
            if (!d.exists()) d.mkdirs();
            return d;
        } catch (Throwable t) {
            File d = new File(context.getFilesDir(), "../bin");
            if (!d.exists()) d.mkdirs();
            return d;
        }
    }

    public boolean ensureBundledBinary(String name) {
        return hasBundledAsset(name);
    }

    public boolean isBinaryAvailableSystemOnly(String name) {
        try {
            if (TextUtils.isEmpty(name)) return false;
            for (String dir : BIN_SCAN_DIRS) {
                if (TextUtils.isEmpty(dir)) continue;
                File f = new File(dir, name);
                if (f.exists() && f.isFile()) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public boolean isBinaryInstalledPublic(String name) {
        try {
            if (TextUtils.isEmpty(name)) return false;
            File f = new File(PUBLIC_BIN_DIR, name);
            return f.exists() && f.isFile();
        } catch (Throwable ignored) {
        }
        return false;
    }

    public String rewriteCommandWithLocalBins(String cmd) {
        try {
            if (TextUtils.isEmpty(cmd)) return cmd;
            String trimmed = cmd.trim();
            if (TextUtils.isEmpty(trimmed)) return cmd;

            int sp = -1;
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (Character.isWhitespace(c)) { sp = i; break; }
            }
            String token = (sp == -1) ? trimmed : trimmed.substring(0, sp);
            if (TextUtils.isEmpty(token)) return cmd;

            final String base = new File(token).getName();
            if (!(base.equals("adb") || base.equals("dumpsys") || base.equals("pm") || base.equals("logcat") || base.equals("settings")
                    || base.equals("reboot") || base.equals("md5sum") || base.equals("lspci") || base.equals("lsusb")
                    || base.equals("netcat") || base.equals("nc") || base.equals("readelf") || base.equals("run-as")
                    || base.equals("screencap") || base.equals("toybox")
                    || base.equals("base64") || base.equals("clear") || base.equals("curl") || base.equals("ip") || base.equals("ping") || base.equals("dos2unix") || base.equals("unix2dos") || base.equals("tty") || base.equals("umount") || base.equals("vmstat") || base.equals("watch")
                    || base.equals("sha256sum") || base.equals("xxd")
                    || base.equals("yes") || base.equals("zcat") || base.equals("zipinfo") || base.equals("ziptool")
                    || base.equals("gzip") || base.equals("gunzip") || base.equals("setenforce")
                    || base.equals("screenrecord") || base.equals("start") || base.equals("sysctl") || base.equals("fastboot") || base.equals("free") || base.equals("tcpdump") || base.equals("getevent") || base.equals("lsof"))) {
                return cmd;
            }

            if (!isBinaryAvailableSystemOnly(base) && isBinaryInstalledPublic(base)) {
                String rest = (sp == -1) ? "" : trimmed.substring(sp);
                return PUBLIC_BIN_DIR + "/" + base + rest;
            }
        } catch (Throwable ignored) {
        }
        return cmd;
    }

    private boolean assetDirHasFiles(String assetPath) {
        try {
            String[] names = context.getAssets().list(assetPath);
            if (names == null || names.length == 0) return false;
            for (String n : names) {
                if (TextUtils.isEmpty(n)) continue;
                if (n.startsWith(".")) continue;
                if ("README.txt".equalsIgnoreCase(n)) continue;
                return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String mapAbiToAssetSubdir(String abi) {
        try {
            if (TextUtils.isEmpty(abi)) return null;
            for (String d : STANDARD_ABI_ASSET_DIRS) {
                if (d.equals(abi)) return d;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public String resolveBundledAbiDir() {
        if (cachedBundledAbiDirResolved) return cachedBundledAbiDir;
        cachedBundledAbiDirResolved = true;
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis != null) {
                for (String abi : abis) {
                    String dir = mapAbiToAssetSubdir(abi);
                    if (TextUtils.isEmpty(dir)) continue;
                    if (assetDirHasFiles(BUNDLED_ASSET_DIR + "/" + dir)) {
                        cachedBundledAbiDir = dir;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return cachedBundledAbiDir;
    }
}
