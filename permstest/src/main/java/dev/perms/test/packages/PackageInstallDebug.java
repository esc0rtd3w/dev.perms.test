package dev.perms.test.packages;

import java.util.List;

/**
 * Compatibility facade for package helpers; shared debug code lives in dev.perms.test.debug.
 */
@Deprecated
public final class PackageInstallDebug {
    public static final String TAG = dev.perms.test.debug.PackageInstallDebug.TAG;

    public static final class Area {
        public static final String PACKAGE_TAB = dev.perms.test.debug.PackageInstallDebug.Area.PACKAGE_TAB;
        public static final String ARCHIVE_INSTALL = dev.perms.test.debug.PackageInstallDebug.Area.ARCHIVE_INSTALL;
        public static final String FILE_OPEN = dev.perms.test.debug.PackageInstallDebug.Area.FILE_OPEN;
        public static final String FILE_OPEN_ARCHIVE = dev.perms.test.debug.PackageInstallDebug.Area.FILE_OPEN_ARCHIVE;
        public static final String SPLIT_SELECTOR = dev.perms.test.debug.PackageInstallDebug.Area.SPLIT_SELECTOR;
        public static final String SPLIT_DIALOG = dev.perms.test.debug.PackageInstallDebug.Area.SPLIT_DIALOG;
        public static final String INSTALL_INPUT = dev.perms.test.debug.PackageInstallDebug.Area.INSTALL_INPUT;

        private Area() {
        }
    }

    public interface AppOutput extends dev.perms.test.debug.PackageInstallDebug.AppOutput {
    }

    private PackageInstallDebug() {
    }

    public static String line(String area, String message) {
        return dev.perms.test.debug.PackageInstallDebug.line(area, message);
    }

    public static void log(String area, String message) {
        dev.perms.test.debug.PackageInstallDebug.log(area, message);
    }

    public static void logToOutput(String area, String message, dev.perms.test.debug.DebugOutput output) {
        dev.perms.test.debug.PackageInstallDebug.logToOutput(area, message, output);
    }

    public static void warn(String area, String message) {
        dev.perms.test.debug.PackageInstallDebug.warn(area, message);
    }

    public static void warnToOutput(String area, String message, dev.perms.test.debug.DebugOutput output) {
        dev.perms.test.debug.PackageInstallDebug.warnToOutput(area, message, output);
    }

    public static void error(String area, String message, Throwable t) {
        dev.perms.test.debug.PackageInstallDebug.error(area, message, t);
    }

    public static String describeLengths(String stdout, String stderr) {
        return dev.perms.test.debug.PackageInstallDebug.describeLengths(stdout, stderr);
    }

    public static String describePath(String path) {
        return dev.perms.test.debug.PackageInstallDebug.describePath(path);
    }

    public static String describePathList(List<String> paths, int maxItems) {
        return dev.perms.test.debug.PackageInstallDebug.describePathList(paths, maxItems);
    }
}
