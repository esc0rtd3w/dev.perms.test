package dev.perms.test.tools.permissions;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.apk.BinaryXmlDebuggablePatcher;
import dev.perms.test.apk.BinaryXmlManifestVersionPatcher;
import dev.perms.test.apk.BinaryXmlPermissionPatcher;
import dev.perms.test.apk.OfficialApkSigner;

/** Creates permission-test APKs and permission-patched single APK repacks. */
final class PermissionTestApkCreator {
    static final class Result {
        final boolean success;
        final String message;
        final File apk;

        Result(boolean success, String message, File apk) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.apk = apk;
        }
    }

    private PermissionTestApkCreator() {
    }

    static Result createStandalone(Context context, String packageName, String label,
                                   List<String> permissions, boolean debuggable,
                                   boolean launcherActivity, File outputApk) {
        return createGenerated(context, packageName, label, permissions, debuggable, launcherActivity, true, outputApk);
    }

    static Result createPermissionOnly(Context context, String packageName, String label,
                                       List<String> permissions, boolean debuggable, File outputApk) {
        return createGenerated(context, packageName, label, permissions, debuggable, false, false, outputApk);
    }

    private static Result createGenerated(Context context, String packageName, String label,
                                          List<String> permissions, boolean debuggable,
                                          boolean launcherActivity, boolean includeCode, File outputApk) {
        if (context == null) return new Result(false, "Context is missing.", null);
        if (permissions == null || permissions.isEmpty()) return new Result(false, "No permissions selected.", null);
        if (outputApk == null) return new Result(false, "Output APK path is missing.", null);
        File workDir = new File(outputApk.getParentFile(), ".work-" + stem(outputApk.getName()));
        File unsigned = new File(workDir, includeCode ? "permission-test-unsigned.apk" : "manifest-only-unsigned.apk");
        try {
            deleteTree(workDir);
            if (!workDir.exists() && !workDir.mkdirs()) throw new IOException("Unable to create work directory.");
            File parent = outputApk.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create output directory.");

            byte[] manifest = BinaryXmlPermissionPatcher.buildStandaloneManifest(packageName, label, permissions, debuggable, launcherActivity, includeCode);
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(unsigned, false))) {
                ZipEntry manifestEntry = new ZipEntry("AndroidManifest.xml");
                manifestEntry.setTime(System.currentTimeMillis());
                out.putNextEntry(manifestEntry);
                out.write(manifest);
                out.closeEntry();

                if (includeCode) {
                    ZipEntry dexEntry = new ZipEntry("classes.dex");
                    dexEntry.setTime(System.currentTimeMillis());
                    out.putNextEntry(dexEntry);
                    out.write(PermissionTestDexBuilder.build());
                    out.closeEntry();
                }
            }
            sign(context, unsigned, outputApk);
            try { outputApk.setReadable(true, false); } catch (Throwable ignored) {}
            return new Result(true, includeCode ? "Created launchable permission test APK." : "Created manifest-only permission APK.", outputApk);
        } catch (Throwable t) {
            return new Result(false, message(t), null);
        } finally {
            deleteTree(workDir);
        }
    }


    static Result repackCurrentPermsTest(Context context, List<String> permissions,
                                         boolean debuggable, File outputApk) {
        if (context == null) return new Result(false, "Context is missing.", null);
        try {
            String sourceDir = context.getApplicationInfo() == null ? null : context.getApplicationInfo().sourceDir;
            if (TextUtils.isEmpty(sourceDir)) return new Result(false, "Current PermsTest APK path is unavailable.", null);
            File sourceApk = new File(sourceDir);
            VersionInfo versionInfo = currentPackageVersion(context);
            Result result = repackSingleApk(context, sourceApk, permissions, debuggable, outputApk, versionInfo);
            if (result == null || !result.success) return result;
            String suffix = versionInfo == null ? "" : " Version " + versionInfo.display() + " was preserved for install compatibility.";
            return new Result(true, "Created signed permission-repacked copy of the current PermsTest APK." + suffix, result.apk);
        } catch (Throwable t) {
            return new Result(false, message(t), null);
        }
    }

    static Result repackSingleApk(Context context, File sourceApk, List<String> permissions,
                                  boolean debuggable, File outputApk) {
        return repackSingleApk(context, sourceApk, permissions, debuggable, outputApk, null);
    }

    private static Result repackSingleApk(Context context, File sourceApk, List<String> permissions,
                                          boolean debuggable, File outputApk, VersionInfo versionInfo) {
        if (context == null) return new Result(false, "Context is missing.", null);
        if (sourceApk == null || !sourceApk.isFile()) return new Result(false, "Source APK is missing.", null);
        if (permissions == null || permissions.isEmpty()) return new Result(false, "No permissions selected.", null);
        if (outputApk == null) return new Result(false, "Output APK path is missing.", null);
        if (!isSingleApk(sourceApk)) {
            return new Result(false, "Permission repack supports single APK files only. Use the APK Editor split-aware workflow for split archives.", null);
        }
        File workDir = new File(outputApk.getParentFile(), ".work-" + stem(outputApk.getName()));
        File unsigned = new File(workDir, "permission-repack-unsigned.apk");
        try {
            deleteTree(workDir);
            if (!workDir.exists() && !workDir.mkdirs()) throw new IOException("Unable to create work directory.");
            File parent = outputApk.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create output directory.");

            rebuildWithPermissions(sourceApk, unsigned, permissions, debuggable, versionInfo);
            signRepack(context, unsigned, outputApk);
            try { outputApk.setReadable(true, false); } catch (Throwable ignored) {}
            return new Result(true, "Created signed permission-repacked APK.", outputApk);
        } catch (Throwable t) {
            return new Result(false, message(t), null);
        } finally {
            deleteTree(workDir);
        }
    }

    private static void rebuildWithPermissions(File sourceApk, File unsignedApk, List<String> permissions, boolean debuggable, VersionInfo versionInfo) throws IOException {
        try (ZipFile inZip = new ZipFile(sourceApk);
             CountingOutputStream rawOut = new CountingOutputStream(new FileOutputStream(unsignedApk, false));
             ZipOutputStream out = new ZipOutputStream(rawOut)) {
            boolean patchedManifest = false;
            Enumeration<? extends ZipEntry> entries = inZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String name = ze.getName();
                if (isSignatureEntry(name)) continue;
                if ("AndroidManifest.xml".equals(name)) {
                    byte[] data;
                    try (InputStream in = inZip.getInputStream(ze)) {
                        data = readAllBounded(in, 8 * 1024 * 1024, "AndroidManifest.xml");
                    }
                    // Keep the known-good debuggable patcher on the original binary XML shape.
                    // The permission patcher may need to add element chunks and string-pool entries,
                    // so it should run after any application-tag attribute update.
                    if (debuggable) data = patchDebuggableManifest(data);
                    data = patchPermissionManifest(data, permissions);
                    data = patchManifestVersion(data, versionInfo);
                    writeEntry(out, rawOut, ze, data);
                    patchedManifest = true;
                } else {
                    try (InputStream in = inZip.getInputStream(ze)) {
                        writeEntryStream(out, rawOut, ze, in);
                    }
                }
            }
            if (!patchedManifest) throw new IOException("AndroidManifest.xml was not found in the APK.");
        }
    }


    private static byte[] patchDebuggableManifest(byte[] data) throws IOException {
        try {
            return BinaryXmlDebuggablePatcher.patchDebuggable(data);
        } catch (IOException e) {
            throw new IOException("Debuggable manifest patch failed: " + safeMessage(e), e);
        } catch (Throwable t) {
            throw new IOException("Debuggable manifest patch failed: " + safeMessage(t), t);
        }
    }

    private static byte[] patchPermissionManifest(byte[] data, List<String> permissions) throws IOException {
        try {
            return BinaryXmlPermissionPatcher.patchUsesPermissions(data, permissions);
        } catch (IOException e) {
            throw new IOException("Permission manifest patch failed: " + safeMessage(e), e);
        } catch (Throwable t) {
            throw new IOException("Permission manifest patch failed: " + safeMessage(t), t);
        }
    }

    private static byte[] patchManifestVersion(byte[] data, VersionInfo versionInfo) throws IOException {
        if (versionInfo == null || !versionInfo.hasValue()) return data;
        try {
            return BinaryXmlManifestVersionPatcher.patchVersion(data, versionInfo.versionCode, versionInfo.versionName);
        } catch (IOException e) {
            throw new IOException("Manifest version patch failed: " + safeMessage(e), e);
        } catch (Throwable t) {
            throw new IOException("Manifest version patch failed: " + safeMessage(t), t);
        }
    }

    private static VersionInfo currentPackageVersion(Context context) {
        if (context == null) return null;
        try {
            PackageManager pm = context.getPackageManager();
            if (pm == null) return null;
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            int code;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                long longCode = info.getLongVersionCode();
                code = longCode > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, longCode);
            } else {
                code = Math.max(0, info.versionCode);
            }
            return new VersionInfo(code, info.versionName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class VersionInfo {
        final int versionCode;
        final String versionName;

        VersionInfo(int versionCode, String versionName) {
            this.versionCode = Math.max(0, versionCode);
            this.versionName = versionName == null ? "" : versionName.trim();
        }

        boolean hasValue() {
            return versionCode > 0 || !TextUtils.isEmpty(versionName);
        }

        String display() {
            if (versionCode > 0 && !TextUtils.isEmpty(versionName)) return versionName + " (" + versionCode + ")";
            if (versionCode > 0) return String.valueOf(versionCode);
            return versionName;
        }
    }

    private static void sign(Context context, File unsignedApk, File signedApk) throws Exception {
        byte[] keyStoreBytes;
        try (InputStream in = context.getAssets().open(ApkDebugToolHelper.DEBUG_KEYSTORE_ASSET)) {
            keyStoreBytes = readAllBounded(in, 2 * 1024 * 1024, "debug.keystore");
        }
        OfficialApkSigner.signPreservingAlignment(unsignedApk, signedApk, keyStoreBytes,
                ApkDebugToolHelper.DEBUG_KEY_ALIAS,
                ApkDebugToolHelper.DEBUG_KEY_PASSWORD);
    }

    private static void signRepack(Context context, File unsignedApk, File signedApk) throws Exception {
        byte[] keyStoreBytes;
        try (InputStream in = context.getAssets().open(ApkDebugToolHelper.DEBUG_KEYSTORE_ASSET)) {
            keyStoreBytes = readAllBounded(in, 2 * 1024 * 1024, "debug.keystore");
        }
        // Third-party debug repacks must keep resources.arsc stored and 4-byte aligned
        // on Android 11+. V1-only signing lets us realign the final ZIP without
        // invalidating the signature; production-style APK signing remains used elsewhere.
        OfficialApkSigner.signV1OnlyAndAlign(unsignedApk, signedApk, keyStoreBytes,
                ApkDebugToolHelper.DEBUG_KEY_ALIAS,
                ApkDebugToolHelper.DEBUG_KEY_PASSWORD);
    }

    private static boolean isSingleApk(File file) {
        if (file == null || !file.isFile()) return false;
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            return manifest != null && !manifest.isDirectory();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSignatureEntry(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String n = name.replace('\\', '/').toUpperCase(Locale.US);
        if (!n.startsWith("META-INF/")) return false;
        return n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC")
                || n.endsWith(".SF") || n.endsWith(".MF")
                || n.startsWith("META-INF/ANDROID")
                || n.startsWith("META-INF/MANIFEST.MF");
    }

    private static void writeEntry(ZipOutputStream out, CountingOutputStream rawOut, ZipEntry source, byte[] data) throws IOException {
        if (shouldStoreEntry(source)) {
            writeStoredEntry(out, rawOut, source, data);
            return;
        }
        ZipEntry entry = new ZipEntry(source.getName());
        entry.setTime(source.getTime() > 0 ? source.getTime() : System.currentTimeMillis());
        entry.setMethod(ZipEntry.DEFLATED);
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();
    }

    private static void writeEntryStream(ZipOutputStream out, CountingOutputStream rawOut, ZipEntry source, InputStream in) throws IOException {
        if (shouldStoreEntry(source)) {
            byte[] data = readAllBounded(in, maxEntryBytes(source), source.getName());
            writeStoredEntry(out, rawOut, source, data);
            return;
        }
        ZipEntry entry = new ZipEntry(source.getName());
        entry.setTime(source.getTime() > 0 ? source.getTime() : System.currentTimeMillis());
        entry.setMethod(ZipEntry.DEFLATED);
        out.putNextEntry(entry);
        copy(in, out);
        out.closeEntry();
    }

    private static boolean shouldStoreEntry(ZipEntry source) {
        if (source == null) return false;
        String name = source.getName();
        return isResourcesTable(name) || source.getMethod() == ZipEntry.STORED;
    }

    private static boolean isResourcesTable(String name) {
        return "resources.arsc".equals(name);
    }

    private static int maxEntryBytes(ZipEntry source) throws IOException {
        if (source == null) return 256 * 1024 * 1024;
        long size = source.getSize();
        if (size > Integer.MAX_VALUE) throw new IOException(source.getName() + " is too large.");
        if (size > 0) return (int) Math.min(Integer.MAX_VALUE, Math.max(size + 1, 8L * 1024L * 1024L));
        return 256 * 1024 * 1024;
    }

    private static void writeStoredEntry(ZipOutputStream out, CountingOutputStream rawOut, ZipEntry source, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(source.getName());
        entry.setTime(source.getTime() > 0 ? source.getTime() : System.currentTimeMillis());
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc32(data));
        if (isResourcesTable(source.getName())) {
            entry.setExtra(buildAlignmentExtra(rawOut.getCount(), source.getName(), 4));
        } else if (source.getExtra() != null) {
            entry.setExtra(source.getExtra());
        }
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();
    }

    private static long crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return crc.getValue();
    }

    private static byte[] buildAlignmentExtra(long headerOffset, String name, int alignment) {
        if (alignment <= 1) return null;
        int nameBytes = name == null ? 0 : name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int fixedExtraBytes = 6;
        long baseDataOffset = headerOffset + 30L + nameBytes + fixedExtraBytes;
        int padding = (int) ((alignment - (baseDataOffset % alignment)) % alignment);
        byte[] extra = new byte[fixedExtraBytes + padding];
        // Android zipalign extra field: header id 0xd935, payload starts with the requested alignment.
        extra[0] = 0x35;
        extra[1] = (byte) 0xd9;
        int payloadSize = 2 + padding;
        extra[2] = (byte) (payloadSize & 0xff);
        extra[3] = (byte) ((payloadSize >>> 8) & 0xff);
        extra[4] = (byte) (alignment & 0xff);
        extra[5] = (byte) ((alignment >>> 8) & 0xff);
        return extra;
    }

    private static byte[] readAllBounded(InputStream in, int maxBytes, String label) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) throw new IOException(label + " is too large.");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        long getCount() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        try { file.delete(); } catch (Throwable ignored) {}
    }

    private static String stem(String name) {
        if (TextUtils.isEmpty(name)) return "permission-output";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String message(Throwable t) {
        if (t == null) return "Unknown error";
        return t.getClass().getSimpleName() + ": " + safeMessage(t);
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "Unknown error";
        String m = t.getMessage();
        return TextUtils.isEmpty(m) ? t.getClass().getSimpleName() : m;
    }
}
