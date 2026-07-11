package dev.perms.test.ui.about;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

/**
 * Condensed technical help shown from the About tab.
 */
public final class AboutHelpDialog {
    public interface ErrorReporter {
        void onError(Throwable error);
    }

    private AboutHelpDialog() {
    }

    public static void show(Context context, ErrorReporter reporter) {
        try {
            new AlertDialog.Builder(context)
                    .setTitle("PermsTest Quick Help")
                    .setMessage(helpMessage())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (Throwable t) {
            if (reporter != null) {
                reporter.onError(t);
            }
        }
    }

    private static String helpMessage() {
        return "Start here\n"
                + "• Choose an execution mode on Main > Backend before using privileged tools.\n"
                + "• Tap Refresh after starting Shizuku, connecting LADB, or changing an Android setting.\n"
                + "• Disabled buttons are waiting for a required package, file, backend, setting, or valid editor input.\n\n"
                + "Save useful troubleshooting data\n"
                + "• Turn on Settings > Debug Output before repeating a problem.\n"
                + "• Turn on Settings > Enable Lifetime Log when actions should remain available across sessions.\n"
                + "• Use Logging > Mark Session before the test, then Export Lifetime or Archive Logs afterward.\n"
                + "• Save Output stores the visible bottom output pane; Save To File stores the next raw Logcat capture.\n\n"
                + "Install and package tools\n"
                + "• Use Packages > APK Installer for APK, APKS, APKM, and XAPK files.\n"
                + "• Android still checks signatures, version codes, ABIs, and install-source policy.\n"
                + "• Keep the original APK and important app data before rebuilding or replacing a package.\n\n"
                + "Memory and VR\n"
                + "• Select the target package/process and confirm attach state before scanning or writing.\n"
                + "• Check addresses, value types, masks, and package scope before applying patches or payloads.\n"
                + "• VR-specific panels require VR mode; normal phone/tablet behavior remains the standard path.\n\n"
                + "Network access\n"
                + "• Verify the FTP/HTTP root and port before starting a server.\n"
                + "• Enable background or sleep operation only while remote access must remain active.\n"
                + "• Enable only the Web Interface sections that should be reachable remotely.\n\n"
                + "Plugins\n"
                + "• Import a .ptp/.zip, enable the staged plugin, then tap one of its action buttons.\n"
                + "• Use Edit Plugin Config to manage plugin actions and declarative UI controls, then Validate before Package. Raw plugin.json/ui.json editors remain available for advanced fields.\n"
                + "• Shell/script plugin actions run through the currently selected execution backend.\n\n"
                + "Important paths\n"
                + "• Shared root: /sdcard/dev.perms.test\n"
                + "• Logs: /sdcard/dev.perms.test/logs\n"
                + "• Lifetime logs: /sdcard/dev.perms.test/logs/lifetime\n"
                + "• Log archives: /sdcard/dev.perms.test/log_archives\n"
                + "• Debug APKs: /sdcard/dev.perms.test/debug_packages\n"
                + "• Plugins: /sdcard/dev.perms.test/plugins\n"
                + "• Payloads: /sdcard/dev.perms.test/memory_payloads\n"
                + "• Save data configs: /sdcard/dev.perms.test/save_data";
    }
}
