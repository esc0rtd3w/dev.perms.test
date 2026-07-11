package dev.perms.test.debugging;

import android.app.Activity;

import java.io.File;
import java.io.IOException;

import dev.perms.test.apk.ApkDebugToolHelper;

/**
 * Shared Debugging-tab bridge for bundled tool resolution and rebuilt APK export.
 *
 * MainActivity still owns the app-wide shell/tool staging backends, but the
 * Debugging feature owns how smali/MITM actions use those backends.
 */
public final class DebuggingToolBridge {
    public interface Host {
        Activity activity();
        DebuggingRebuiltApkExporter.ToolResult ensureBundledTool(String toolName);
        DebuggingRebuiltApkExporter.ToolResult runShell(String command);
        String quoteShell(String value);
    }

    private final Host host;
    private DebuggingRebuiltApkExporter rebuiltApkExporter;

    public DebuggingToolBridge(Host host) {
        this.host = host;
    }

    public String exportRebuiltApk(File rebuiltUnsigned,
                                   String outputPath,
                                   boolean makeDebuggable,
                                   File workDir) throws Exception {
        return getRebuiltApkExporter().export(rebuiltUnsigned, outputPath, makeDebuggable, workDir);
    }

    public String resolveApktoolCommand() throws IOException {
        return getRebuiltApkExporter().resolveApktoolCommand(quotedPublicBinDir());
    }

    public String resolveJadxCommand() throws IOException {
        return getRebuiltApkExporter().resolveJadxCommand(quotedPublicBinDir());
    }

    private DebuggingRebuiltApkExporter getRebuiltApkExporter() {
        if (rebuiltApkExporter == null) {
            rebuiltApkExporter = new DebuggingRebuiltApkExporter(new DebuggingRebuiltApkExporter.Host() {
                @Override
                public Activity activity() {
                    return host == null ? null : host.activity();
                }

                @Override
                public DebuggingRebuiltApkExporter.ToolResult ensureBundledTool(String toolName) {
                    return host == null ? null : host.ensureBundledTool(toolName);
                }

                @Override
                public DebuggingRebuiltApkExporter.ToolResult runShell(String command) {
                    return host == null ? null : host.runShell(command);
                }
            });
        }
        return rebuiltApkExporter;
    }

    private String quotedPublicBinDir() {
        return host == null ? "''" : host.quoteShell(ApkDebugToolHelper.PUBLIC_BIN_DIR);
    }
}
