package dev.perms.test.packages;

import java.io.File;

/**
 * Shared builders for package-install shell commands.
 */
public final class PackageInstallCommands {
    private PackageInstallCommands() {}

    public static String buildInstallScriptCommand(File scriptFile,
                                                   String inputPath,
                                                   boolean useAndroidDataStage,
                                                   boolean useStagingFolder,
                                                   boolean skipStagingLargeFiles,
                                                   boolean bypassLowTargetSdkBlock,
                                                   boolean ignoreDexoptProfile) {
        StringBuilder prefix = new StringBuilder();
        if (useAndroidDataStage) prefix.append("USE_ANDROID_DATA_STAGE=1 ");
        prefix.append("USE_STAGING_FOLDER=").append(useStagingFolder ? "1" : "0").append(" ");
        if (skipStagingLargeFiles) prefix.append("SKIP_STAGING_LARGE_FILES=1 ");
        if (bypassLowTargetSdkBlock) prefix.append("BYPASS_LOW_TARGET_SDK_BLOCK=1 ");
        if (ignoreDexoptProfile) prefix.append("IGNORE_DEXOPT_PROFILE=1 ");
        return prefix + "sh " + shQuote(scriptFile == null ? null : scriptFile.getAbsolutePath()) + " " + shQuote(inputPath);
    }


    public static boolean shouldForcePmReadableRestage(String inputPath, boolean useAndroidDataStage) {
        if (useAndroidDataStage || inputPath == null) return false;
        String p = inputPath.trim();
        return p.startsWith("/storage/emulated/0/")
                || p.startsWith("/sdcard/")
                || p.startsWith("/storage/self/primary/")
                || p.startsWith("/mnt/user/0/primary/");
    }

    public static String buildRestageInstallInputCommand(String publicFilesDir, String sourcePath, String stagedPath) {
        final String src = shQuote(sourcePath);
        final String dst = shQuote(stagedPath);
        return "mkdir -p " + shQuote(publicFilesDir)
                + " && rm -f " + dst
                + " && (cp -f " + src + " " + dst + " 2>/dev/null || cat " + src + " > " + dst + ")"
                + " && (chmod 0644 " + dst + " 2>/dev/null || true)";
    }

    public static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}
