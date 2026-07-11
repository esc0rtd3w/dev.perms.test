package dev.perms.test.packages.editor;

import android.text.TextUtils;

import dev.perms.test.apk.BinaryXmlDebuggablePatcher;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkEditorManifestExporter {
    public static final class Result {
        public final boolean success;
        public final String message;
        public final File manifestFile;
        public final String packageName;
        public final boolean debuggable;

        Result(boolean success, String message, File manifestFile, String packageName, boolean debuggable) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.manifestFile = manifestFile;
            this.packageName = packageName == null ? "" : packageName;
            this.debuggable = debuggable;
        }
    }

    private ApkEditorManifestExporter() {
    }

    public static Result export(File stagedPackage, File workspace) {
        try {
            if (stagedPackage == null || !stagedPackage.isFile()) {
                return new Result(false, "Source package is missing.", null, "", false);
            }
            if (workspace == null) {
                return new Result(false, "Workspace is missing.", null, "", false);
            }
            if (!workspace.exists() && !workspace.mkdirs()) {
                return new Result(false, "Unable to create workspace: " + workspace.getAbsolutePath(), null, "", false);
            }
            File toolApk = ApkEditorFileUtils.findToolInputApk(stagedPackage, new File(workspace, "stage"));
            File outDir = new File(workspace, "manifest");
            if (!outDir.exists() && !outDir.mkdirs()) {
                return new Result(false, "Unable to create manifest directory.", null, "", false);
            }
            File out = new File(outDir, "AndroidManifest.xml");
            byte[] manifest = extractManifest(toolApk, out);
            String packageName = nullToEmpty(BinaryXmlDebuggablePatcher.getManifestPackageName(manifest));
            boolean debuggable = BinaryXmlDebuggablePatcher.isDebuggableEnabled(manifest);
            StringBuilder msg = new StringBuilder();
            msg.append("Manifest extracted to ").append(out.getAbsolutePath()).append('\n');
            msg.append("The saved file is binary Android XML. Use Full Decompile for decoded XML editing when apktool is available.\n");
            if (!TextUtils.isEmpty(packageName)) msg.append("Package: ").append(packageName).append('\n');
            msg.append("Debuggable: ").append(debuggable ? "true" : "false/unknown").append('\n');
            return new Result(true, msg.toString(), out, packageName, debuggable);
        } catch (Throwable t) {
            return new Result(false, "Manifest export failed: " + ApkEditorArchiveInspector.shortError(t), null, "", false);
        }
    }

    private static byte[] extractManifest(File apk, File out) throws IOException {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry e = zip.getEntry("AndroidManifest.xml");
            if (e == null || e.isDirectory()) throw new IOException("AndroidManifest.xml was not found.");
            byte[] data;
            try (InputStream in = zip.getInputStream(e);
                 java.io.ByteArrayOutputStream mem = new java.io.ByteArrayOutputStream()) {
                ApkEditorFileUtils.copy(in, mem);
                data = mem.toByteArray();
            }
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out, false))) {
                bos.write(data);
                bos.flush();
            }
            return data;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
