package dev.perms.test.debugging.mitm;

import dev.perms.test.debugging.DebuggingWorkPaths;
import dev.perms.test.debugging.smali.PermsTestSmaliTools;

import android.text.TextUtils;

import dev.perms.test.databinding.ActivityMainBinding;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Small value/helper layer for Debugging-tab MITM and patch-template work.
 *
 * MainActivity still owns shell execution, signing/export, and UI threading. This class keeps
 * MITM option collection, output naming, and source validation in one place so the patch actions
 * do not duplicate UI/default handling.
 */
public final class DebuggingMitmPatch {
    public static final class FullPatchOptions {
        public final boolean trustUserCerts;
        public final boolean trustSystemCerts;
        public final boolean allowCleartext;
        public final boolean makeDebuggable;
        public final boolean patchCertificatePinning;

        public FullPatchOptions(boolean trustUserCerts, boolean trustSystemCerts, boolean allowCleartext,
                         boolean makeDebuggable, boolean patchCertificatePinning) {
            this.trustUserCerts = trustUserCerts;
            this.trustSystemCerts = trustSystemCerts;
            this.allowCleartext = allowCleartext;
            this.makeDebuggable = makeDebuggable;
            this.patchCertificatePinning = patchCertificatePinning;
        }
    }

    private DebuggingMitmPatch() {
    }

    public static FullPatchOptions readFullPatchOptions(ActivityMainBinding binding) {
        boolean trustUser = binding == null || binding.tabDebugging == null || binding.tabDebugging.chkMitmTrustUserCerts == null
                || binding.tabDebugging.chkMitmTrustUserCerts.isChecked();
        boolean trustSystem = binding == null || binding.tabDebugging == null || binding.tabDebugging.chkMitmTrustSystemCerts == null
                || binding.tabDebugging.chkMitmTrustSystemCerts.isChecked();
        boolean cleartext = binding == null || binding.tabDebugging == null || binding.tabDebugging.chkMitmAllowCleartext == null
                || binding.tabDebugging.chkMitmAllowCleartext.isChecked();
        boolean debug = binding == null || binding.tabDebugging == null || binding.tabDebugging.chkMitmMakeDebuggable == null
                || binding.tabDebugging.chkMitmMakeDebuggable.isChecked();
        return new FullPatchOptions(trustUser, trustSystem, cleartext, debug, shouldPatchCertificatePinning(binding));
    }

    public static boolean shouldPatchCertificatePinning(ActivityMainBinding binding) {
        return binding == null || binding.tabDebugging == null || binding.tabDebugging.chkMitmPatchCertificatePinning == null
                || binding.tabDebugging.chkMitmPatchCertificatePinning.isChecked();
    }

    public static PermsTestMitmPatchTool.Options buildPatchOptions(boolean networkConfig, boolean cleartext,
                                                            boolean makeDebuggable, boolean allowBackup,
                                                            boolean trustUserCerts, boolean trustSystemCerts,
                                                            boolean patchCertificatePinning) {
        PermsTestMitmPatchTool.Options options = new PermsTestMitmPatchTool.Options();
        options.applyNetworkSecurityConfig = networkConfig;
        options.trustUserCerts = trustUserCerts;
        options.trustSystemCerts = trustSystemCerts;
        options.allowCleartext = cleartext;
        options.makeDebuggable = makeDebuggable;
        options.allowBackup = allowBackup;
        options.patchCertificatePinning = patchCertificatePinning;
        return options;
    }

    public static File requireSingleApkSource(String sourceApkPath) throws IOException {
        if (TextUtils.isEmpty(sourceApkPath)) throw new IOException("Choose an APK source first.");
        File sourceApk = new File(sourceApkPath);
        if (!sourceApk.isFile()) throw new IOException("APK source does not exist: " + sourceApkPath);
        if (!sourceApk.getName().toLowerCase(Locale.US).endsWith(".apk")) {
            throw new IOException("Patch templates currently require a single APK source.");
        }
        return sourceApk;
    }

    public static String outputPath(String label, boolean debug, String sourcePath, String currentRoot, String selectedPackage) {
        try {
            String fallback = TextUtils.isEmpty(sourcePath) ? "package.apk" : new File(sourcePath).getName();
            String safeName = DebuggingWorkPaths.workName(label, fallback, selectedPackage);
            String root = TextUtils.isEmpty(currentRoot) ? DebuggingWorkPaths.rootForWorkName(safeName) : currentRoot;
            String suffix = suffixForLabel(label);
            return root + "/" + safeName + "-" + suffix + (debug ? "-debug.apk" : "-signed.apk");
        } catch (Throwable ignored) {
            return PermsTestSmaliTools.DEFAULT_ROOT + "/patched.apk";
        }
    }

    public static String cleanLabel(String label) {
        return TextUtils.isEmpty(label) ? "Patch Template" : label;
    }

    public static String failureMessage(Throwable t) {
        if (t == null) return "Exception: ";
        return t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage());
    }

    public static String patchCompletionReport(String cleanLabel, PermsTestMitmPatchTool.Result patch,
                                        String outputPath, String exportOut) {
        return "[Debugging] " + cleanLabel + " complete.\n"
                + (patch == null || patch.report == null ? "" : patch.report)
                + "Output: " + outputPath + "\n"
                + (TextUtils.isEmpty(exportOut) ? "" : (exportOut.endsWith("\n") ? exportOut : exportOut + "\n"));
    }

    private static String suffixForLabel(String label) {
        String suffix = (TextUtils.isEmpty(label) ? "patched" : label.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", ""));
        return TextUtils.isEmpty(suffix) ? "patched" : suffix;
    }
}
