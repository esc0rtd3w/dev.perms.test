package dev.perms.test.debug;

import java.util.List;

/**
 * Install-specific debug facade backed by the shared debug package.
 */
public final class PackageInstallDebug {
    public static final String TAG = "PermsTestInstall";
    public static final String CHANNEL = "install";

    public static final class Area {
        public static final String PACKAGE_TAB = "package-tab";
        public static final String ARCHIVE_INSTALL = "archive-install";
        public static final String FILE_OPEN = "file-open";
        public static final String FILE_OPEN_ARCHIVE = "file-open-archive";
        public static final String SPLIT_SELECTOR = "split-selector";
        public static final String SPLIT_DIALOG = "split-dialog";
        public static final String INSTALL_INPUT = "install-input";

        private Area() {
        }
    }

    public interface AppOutput extends DebugOutput {
    }

    private PackageInstallDebug() {
    }

    public static String line(String area, String message) {
        return DebugLog.line(CHANNEL, area, message);
    }

    public static void log(String area, String message) {
        DebugLog.log(TAG, CHANNEL, area, message);
    }

    public static void logToOutput(String area, String message, DebugOutput output) {
        DebugLog.logToOutput(TAG, CHANNEL, area, message, output);
    }

    public static void warn(String area, String message) {
        DebugLog.warn(TAG, CHANNEL, area, message);
    }

    public static void warnToOutput(String area, String message, DebugOutput output) {
        DebugLog.warnToOutput(TAG, CHANNEL, area, message, output);
    }

    public static void error(String area, String message, Throwable t) {
        DebugLog.error(TAG, CHANNEL, area, message, t);
    }

    public static String describeLengths(String stdout, String stderr) {
        return DebugLog.describeLengths(stdout, stderr);
    }

    public static String describePath(String path) {
        return DebugLog.describePath(path);
    }

    public static String describePathList(List<String> paths, int maxItems) {
        return DebugLog.describePathList(paths, maxItems);
    }
}
