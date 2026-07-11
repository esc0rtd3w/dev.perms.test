package dev.perms.test.debugging;

import android.app.Activity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.apk.DebuggableApkCreator;
import dev.perms.test.apk.OfficialApkSigner;
import dev.perms.test.packages.DebuggablePackageExporter;

/**
 * Debugging-tab export/sign helper for rebuilt APK outputs.
 *
 * This keeps the Debugging smali/MITM rebuilt-package export path outside
 * MainActivity while still using the existing host-owned shell and tool staging
 * backends.
 */
public final class DebuggingRebuiltApkExporter {
    public interface Host {
        Activity activity();
        ToolResult ensureBundledTool(String toolName);
        ToolResult runShell(String command);
    }

    public static final class ToolResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ToolResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final Host host;

    public DebuggingRebuiltApkExporter(Host host) {
        this.host = host;
    }

    public String export(File rebuiltUnsigned,
                         String apkOutput,
                         boolean makeDebuggable,
                         File workDir) throws Exception {
        if (host == null) throw new IOException("Debugging export host is unavailable.");
        if (rebuiltUnsigned == null || !rebuiltUnsigned.isFile()) {
            throw new IOException("Rebuilt unsigned APK is missing.");
        }
        if (workDir == null) throw new IOException("Debugging export work directory is missing.");

        File signedPackage = makeDebuggable
                ? createDebuggableSignedApk(rebuiltUnsigned, workDir)
                : signRebuiltApk(rebuiltUnsigned, workDir);
        return exportSignedApk(signedPackage, apkOutput);
    }

    public String resolveApktoolCommand(String quotedPublicBinDir) throws IOException {
        return resolveToolCommand(ApkDebugToolHelper.TOOL_APKTOOL,
                "apktool executable was not found. Add it to assets/bin/<abi>/apktool and rebuild.",
                quotedPublicBinDir);
    }

    public String resolveJadxCommand(String quotedPublicBinDir) throws IOException {
        return resolveToolCommand(ApkDebugToolHelper.TOOL_JADX,
                "jadx-go backend executable was not found. Add/build assets/bin/<abi>/jadx and rebuild.",
                quotedPublicBinDir);
    }

    private String resolveToolCommand(String toolName, String missingMessage, String quotedPublicBinDir) throws IOException {
        ToolResult staged = host == null ? null : host.ensureBundledTool(toolName);
        if (staged == null || staged.exitCode != 0) {
            throw new IOException(missingMessage);
        }
        String dir = quotedPublicBinDir == null ? "''" : quotedPublicBinDir;
        return dir + "/" + toolName;
    }

    private File createDebuggableSignedApk(File rebuiltUnsigned, File workDir) throws Exception {
        ToolResult zipalign = host.ensureBundledTool(ApkDebugToolHelper.TOOL_ZIPALIGN);
        if (zipalign == null || zipalign.exitCode != 0) {
            throw new IOException("zipalign binary was not found for this device ABI.");
        }
        String zipalignPath = ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_ZIPALIGN;
        DebuggableApkCreator.ToolRunner shellRunner = command -> {
            ToolResult result = host.runShell(command);
            if (result == null) return new DebuggableApkCreator.ToolResult(1, "", "No shell result.");
            return new DebuggableApkCreator.ToolResult(result.exitCode, result.stdout, result.stderr);
        };
        Activity activity = host.activity();
        if (activity == null) throw new IOException("Activity is unavailable for debug APK signing.");
        DebuggableApkCreator.Result create = DebuggableApkCreator.create(activity,
                rebuiltUnsigned,
                new File(workDir, "debuggable"),
                false,
                null,
                zipalignPath,
                shellRunner);
        if (create == null || !create.success || create.signedApk == null || !create.signedApk.isFile()) {
            throw new IOException(create == null ? "debug APK creation failed." : create.message);
        }
        return create.signedApk;
    }

    private File signRebuiltApk(File rebuiltUnsigned, File workDir) throws Exception {
        File signedPackage = new File(workDir, "rebuilt-signed.apk");
        OfficialApkSigner.sign(rebuiltUnsigned,
                signedPackage,
                readDebugSigningKeyStoreBytes(),
                ApkDebugToolHelper.DEBUG_KEY_ALIAS,
                ApkDebugToolHelper.DEBUG_KEY_PASSWORD);
        if (!signedPackage.isFile() || signedPackage.length() <= 0) {
            throw new IOException("APK signing did not create an output file.");
        }
        return signedPackage;
    }

    private String exportSignedApk(File signedPackage, String apkOutput) throws IOException {
        DebuggablePackageExporter.Result export = DebuggablePackageExporter.export(signedPackage, apkOutput, command -> {
            ToolResult result = host.runShell(command);
            if (result == null) return null;
            return new DebuggablePackageExporter.Result(result.exitCode, result.stdout, result.stderr);
        });
        if (export == null || export.exitCode != 0) {
            throw new IOException(export == null ? "APK export failed." : export.stderr);
        }
        return export.stdout == null ? "" : export.stdout;
    }

    private byte[] readDebugSigningKeyStoreBytes() throws IOException {
        Activity activity = host == null ? null : host.activity();
        if (activity == null) throw new IOException("Activity is unavailable for debug signing key access.");
        try (InputStream in = activity.getAssets().open(ApkDebugToolHelper.DEBUG_KEYSTORE_ASSET);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }
}
