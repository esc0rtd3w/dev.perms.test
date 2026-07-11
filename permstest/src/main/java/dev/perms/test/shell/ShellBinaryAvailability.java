package dev.perms.test.shell;

import android.view.View;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Tracks Shell quick-command binary availability and applies the matching UI state.
 */
public final class ShellBinaryAvailability {
    public interface Checker {
        boolean isAvailable(String name);
    }

    private boolean dumpsys = true;
    private boolean pm = true;
    private boolean logcat = true;
    private boolean settings = true;
    private boolean adb = false;
    private boolean fastboot = false;
    private boolean free = true;
    private boolean tcpdump = true;
    private boolean getevent = true;
    private boolean lsof = true;
    private boolean reboot = true;
    private boolean md5sum = true;
    private boolean lspci = true;
    private boolean lsusb = true;
    private boolean netcat = true;
    private boolean readelf = true;
    private boolean runAs = true;
    private boolean screencap = true;
    private boolean toybox = true;
    private boolean gzip = true;
    private boolean gunzip = true;
    private boolean setenforce = true;
    private boolean screenrecord = true;
    private boolean start = true;
    private boolean sysctl = true;
    private boolean base64 = true;
    private boolean clear = true;
    private boolean curl = true;
    private boolean dos2unix = true;
    private boolean tty = true;
    private boolean umount = true;
    private boolean unix2dos = true;
    private boolean vmstat = true;
    private boolean watch = true;
    private boolean ip = true;
    private boolean ping = true;
    private boolean sha256sum = true;
    private boolean xxd = true;
    private boolean yes = true;
    private boolean zcat = true;
    private boolean zipinfo = true;
    private boolean ziptool = true;

    public void scan(Checker checker) {
        if (checker == null) return;
        try {
            dumpsys = checker.isAvailable("dumpsys");
            pm = checker.isAvailable("pm");
            logcat = checker.isAvailable("logcat");
            settings = checker.isAvailable("settings");
            adb = checker.isAvailable("adb");
            fastboot = checker.isAvailable("fastboot");
            free = checker.isAvailable("free");
            tcpdump = checker.isAvailable("tcpdump");
            getevent = checker.isAvailable("getevent");
            lsof = checker.isAvailable("lsof");
            reboot = checker.isAvailable("reboot");
            md5sum = checker.isAvailable("md5sum");
            lspci = checker.isAvailable("lspci");
            lsusb = checker.isAvailable("lsusb");
            netcat = checker.isAvailable("netcat") || checker.isAvailable("nc");
            readelf = checker.isAvailable("readelf");
            runAs = checker.isAvailable("run-as");
            screencap = checker.isAvailable("screencap");
            toybox = checker.isAvailable("toybox");
            tty = checker.isAvailable("tty");
            umount = checker.isAvailable("umount");
            unix2dos = checker.isAvailable("unix2dos");
            vmstat = checker.isAvailable("vmstat");
            watch = checker.isAvailable("watch");
            gzip = checker.isAvailable("gzip");
            gunzip = checker.isAvailable("gunzip");
            setenforce = checker.isAvailable("setenforce");
            screenrecord = checker.isAvailable("screenrecord");
            start = checker.isAvailable("start");
            sysctl = checker.isAvailable("sysctl");
            base64 = checker.isAvailable("base64");
            clear = checker.isAvailable("clear");
            curl = checker.isAvailable("curl");
            dos2unix = checker.isAvailable("dos2unix");
            ip = checker.isAvailable("ip");
            ping = checker.isAvailable("ping");
            sha256sum = checker.isAvailable("sha256sum");
            xxd = checker.isAvailable("xxd");
            yes = checker.isAvailable("yes");
            zcat = checker.isAvailable("zcat");
            zipinfo = checker.isAvailable("zipinfo");
            ziptool = checker.isAvailable("ziptool");
        } catch (Throwable ignored) {
        }
    }

    public void applyAlpha(ActivityMainBinding binding) {
        try {
            if (binding == null || binding.tabShell == null) return;
            setAlpha(binding.tabShell.btnQuickAdb, adb);
            setAlpha(binding.tabShell.btnQuickAdbMenu, adb);
            setAlpha(binding.tabShell.btnQuickBase64, base64);
            setAlpha(binding.tabShell.btnQuickBase64Menu, base64);
            setAlpha(binding.tabShell.btnQuickClear, clear);
            setAlpha(binding.tabShell.btnQuickClearMenu, clear);
            setAlpha(binding.tabShell.btnQuickCurl, curl);
            setAlpha(binding.tabShell.btnQuickCurlMenu, curl);
            setAlpha(binding.tabShell.btnQuickDos2unix, dos2unix);
            setAlpha(binding.tabShell.btnQuickDos2unixMenu, dos2unix);
            setAlpha(binding.tabShell.btnQuickDumpsys, dumpsys);
            setAlpha(binding.tabShell.btnQuickDumpsysMenu, dumpsys);
            setAlpha(binding.tabShell.btnQuickFastboot, fastboot);
            setAlpha(binding.tabShell.btnQuickFastbootMenu, fastboot);
            setAlpha(binding.tabShell.btnQuickFree, free);
            setAlpha(binding.tabShell.btnQuickFreeMenu, free);
            setAlpha(binding.tabShell.btnQuickGetevent, getevent);
            setAlpha(binding.tabShell.btnQuickGeteventMenu, getevent);
            setAlpha(binding.tabShell.btnQuickGzip, gzip);
            setAlpha(binding.tabShell.btnQuickGzipMenu, gzip);
            setAlpha(binding.tabShell.btnQuickGunzip, gunzip);
            setAlpha(binding.tabShell.btnQuickGunzipMenu, gunzip);
            setAlpha(binding.tabShell.btnQuickIpBin, ip);
            setAlpha(binding.tabShell.btnQuickIpBinMenu, ip);
            setAlpha(binding.tabShell.btnQuickLogcat, logcat);
            setAlpha(binding.tabShell.btnQuickLogcatMenu, logcat);
            setAlpha(binding.tabShell.btnQuickLsof, lsof);
            setAlpha(binding.tabShell.btnQuickLsofMenu, lsof);
            setAlpha(binding.tabShell.btnQuickLspci, lspci);
            setAlpha(binding.tabShell.btnQuickLspciMenu, lspci);
            setAlpha(binding.tabShell.btnQuickLsusb, lsusb);
            setAlpha(binding.tabShell.btnQuickLsusbMenu, lsusb);
            setAlpha(binding.tabShell.btnQuickMd5sum, md5sum);
            setAlpha(binding.tabShell.btnQuickMd5sumMenu, md5sum);
            setAlpha(binding.tabShell.btnQuickNetcat, netcat);
            setAlpha(binding.tabShell.btnQuickNetcatMenu, netcat);
            setAlpha(binding.tabShell.btnQuickPing, ping);
            setAlpha(binding.tabShell.btnQuickPingMenu, ping);
            setAlpha(binding.tabShell.btnQuickPm, pm);
            setAlpha(binding.tabShell.btnQuickPmMenu, pm);
            setAlpha(binding.tabShell.btnQuickReadelf, readelf);
            setAlpha(binding.tabShell.btnQuickReadelfMenu, readelf);
            setAlpha(binding.tabShell.btnQuickReboot, reboot);
            setAlpha(binding.tabShell.btnQuickRebootMenu, reboot);
            setAlpha(binding.tabShell.btnQuickRunAs, runAs);
            setAlpha(binding.tabShell.btnQuickRunAsMenu, runAs);
            setAlpha(binding.tabShell.btnQuickScreencap, screencap);
            setAlpha(binding.tabShell.btnQuickScreencapMenu, screencap);
            setAlpha(binding.tabShell.btnQuickScreenrecord, screenrecord);
            setAlpha(binding.tabShell.btnQuickScreenrecordMenu, screenrecord);
            setAlpha(binding.tabShell.btnQuickSetenforce, setenforce);
            setAlpha(binding.tabShell.btnQuickSetenforceMenu, setenforce);
            setAlpha(binding.tabShell.btnQuickSettingsGroup, settings);
            setAlpha(binding.tabShell.btnQuickSettingsGroupMenu, settings);
            setAlpha(binding.tabShell.btnQuickSha256sum, sha256sum);
            setAlpha(binding.tabShell.btnQuickSha256sumMenu, sha256sum);
            setAlpha(binding.tabShell.btnQuickStart, start);
            setAlpha(binding.tabShell.btnQuickStartMenu, start);
            setAlpha(binding.tabShell.btnQuickSysctl, sysctl);
            setAlpha(binding.tabShell.btnQuickSysctlMenu, sysctl);
            setAlpha(binding.tabShell.btnQuickTcpdump, tcpdump);
            setAlpha(binding.tabShell.btnQuickTcpdumpMenu, tcpdump);
            setAlpha(binding.tabShell.btnQuickToybox, toybox);
            setAlpha(binding.tabShell.btnQuickToyboxMenu, toybox);
            setAlpha(binding.tabShell.btnQuickTty, tty);
            setAlpha(binding.tabShell.btnQuickTtyMenu, tty);
            setAlpha(binding.tabShell.btnQuickUmount, umount);
            setAlpha(binding.tabShell.btnQuickUmountMenu, umount);
            setAlpha(binding.tabShell.btnQuickUnix2dos, unix2dos);
            setAlpha(binding.tabShell.btnQuickUnix2dosMenu, unix2dos);
            setAlpha(binding.tabShell.btnQuickVmstat, vmstat);
            setAlpha(binding.tabShell.btnQuickVmstatMenu, vmstat);
            setAlpha(binding.tabShell.btnQuickWatch, watch);
            setAlpha(binding.tabShell.btnQuickWatchMenu, watch);
            setAlpha(binding.tabShell.btnQuickXxd, xxd);
            setAlpha(binding.tabShell.btnQuickXxdMenu, xxd);
            setAlpha(binding.tabShell.btnQuickYes, yes);
            setAlpha(binding.tabShell.btnQuickYesMenu, yes);
            setAlpha(binding.tabShell.btnQuickZcat, zcat);
            setAlpha(binding.tabShell.btnQuickZcatMenu, zcat);
            setAlpha(binding.tabShell.btnQuickZipinfo, zipinfo);
            setAlpha(binding.tabShell.btnQuickZipinfoMenu, zipinfo);
            setAlpha(binding.tabShell.btnQuickZiptool, ziptool);
            setAlpha(binding.tabShell.btnQuickZiptoolMenu, ziptool);
        } catch (Throwable ignored) {
        }
    }

    public void applyEnabled(ActivityMainBinding binding, boolean shellReady) {
        try {
            if (binding == null || binding.tabShell == null) return;
            setEnabled(binding.tabShell.btnQuickDumpsys, shellReady && dumpsys);
            setEnabled(binding.tabShell.btnQuickDumpsysMenu, shellReady && dumpsys);
            setEnabled(binding.tabShell.btnQuickPm, shellReady && pm);
            setEnabled(binding.tabShell.btnQuickPmMenu, shellReady && pm);
            setEnabled(binding.tabShell.btnQuickLogcat, shellReady && logcat);
            setEnabled(binding.tabShell.btnQuickLogcatMenu, shellReady && logcat);
            setEnabled(binding.tabShell.btnQuickSettingsGroup, shellReady && settings);
            setEnabled(binding.tabShell.btnQuickSettingsGroupMenu, shellReady && settings);
            setEnabled(binding.tabShell.btnQuickAdb, shellReady && adb);
            setEnabled(binding.tabShell.btnQuickAdbMenu, shellReady && adb);
            setEnabled(binding.tabShell.btnQuickBase64, shellReady && base64);
            setEnabled(binding.tabShell.btnQuickBase64Menu, shellReady && base64);
            setEnabled(binding.tabShell.btnQuickClear, shellReady && clear);
            setEnabled(binding.tabShell.btnQuickClearMenu, shellReady && clear);
            setEnabled(binding.tabShell.btnQuickCurl, shellReady && curl);
            setEnabled(binding.tabShell.btnQuickCurlMenu, shellReady && curl);
            setEnabled(binding.tabShell.btnQuickDos2unix, shellReady && dos2unix);
            setEnabled(binding.tabShell.btnQuickDos2unixMenu, shellReady && dos2unix);
            setEnabled(binding.tabShell.btnQuickFastboot, shellReady && fastboot);
            setEnabled(binding.tabShell.btnQuickFastbootMenu, shellReady && fastboot);
            setEnabled(binding.tabShell.btnQuickFree, shellReady && free);
            setEnabled(binding.tabShell.btnQuickFreeMenu, shellReady && free);
            setEnabled(binding.tabShell.btnQuickGetevent, shellReady && getevent);
            setEnabled(binding.tabShell.btnQuickGeteventMenu, shellReady && getevent);
            setEnabled(binding.tabShell.btnQuickGzip, shellReady && gzip);
            setEnabled(binding.tabShell.btnQuickGzipMenu, shellReady && gzip);
            setEnabled(binding.tabShell.btnQuickGunzip, shellReady && gunzip);
            setEnabled(binding.tabShell.btnQuickGunzipMenu, shellReady && gunzip);
            setEnabled(binding.tabShell.btnQuickIpBin, shellReady && ip);
            setEnabled(binding.tabShell.btnQuickIpBinMenu, shellReady && ip);
            setEnabled(binding.tabShell.btnQuickLsof, shellReady && lsof);
            setEnabled(binding.tabShell.btnQuickLsofMenu, shellReady && lsof);
            setEnabled(binding.tabShell.btnQuickLspci, shellReady && lspci);
            setEnabled(binding.tabShell.btnQuickLspciMenu, shellReady && lspci);
            setEnabled(binding.tabShell.btnQuickLsusb, shellReady && lsusb);
            setEnabled(binding.tabShell.btnQuickLsusbMenu, shellReady && lsusb);
            setEnabled(binding.tabShell.btnQuickMd5sum, shellReady && md5sum);
            setEnabled(binding.tabShell.btnQuickMd5sumMenu, shellReady && md5sum);
            setEnabled(binding.tabShell.btnQuickNetcat, shellReady && netcat);
            setEnabled(binding.tabShell.btnQuickNetcatMenu, shellReady && netcat);
            setEnabled(binding.tabShell.btnQuickPing, shellReady && ping);
            setEnabled(binding.tabShell.btnQuickPingMenu, shellReady && ping);
            setEnabled(binding.tabShell.btnQuickReadelf, shellReady && readelf);
            setEnabled(binding.tabShell.btnQuickReadelfMenu, shellReady && readelf);
            setEnabled(binding.tabShell.btnQuickReboot, shellReady && reboot);
            setEnabled(binding.tabShell.btnQuickRebootMenu, shellReady && reboot);
            setEnabled(binding.tabShell.btnQuickRunAs, shellReady && runAs);
            setEnabled(binding.tabShell.btnQuickRunAsMenu, shellReady && runAs);
            setEnabled(binding.tabShell.btnQuickScreencap, shellReady && screencap);
            setEnabled(binding.tabShell.btnQuickScreencapMenu, shellReady && screencap);
            setEnabled(binding.tabShell.btnQuickScreenrecord, shellReady && screenrecord);
            setEnabled(binding.tabShell.btnQuickScreenrecordMenu, shellReady && screenrecord);
            setEnabled(binding.tabShell.btnQuickSetenforce, shellReady && setenforce);
            setEnabled(binding.tabShell.btnQuickSetenforceMenu, shellReady && setenforce);
            setEnabled(binding.tabShell.btnQuickSha256sum, shellReady && sha256sum);
            setEnabled(binding.tabShell.btnQuickSha256sumMenu, shellReady && sha256sum);
            setEnabled(binding.tabShell.btnQuickStart, shellReady && start);
            setEnabled(binding.tabShell.btnQuickStartMenu, shellReady && start);
            setEnabled(binding.tabShell.btnQuickSysctl, shellReady && sysctl);
            setEnabled(binding.tabShell.btnQuickSysctlMenu, shellReady && sysctl);
            setEnabled(binding.tabShell.btnQuickTcpdump, shellReady && tcpdump);
            setEnabled(binding.tabShell.btnQuickTcpdumpMenu, shellReady && tcpdump);
            setEnabled(binding.tabShell.btnQuickToybox, shellReady && toybox);
            setEnabled(binding.tabShell.btnQuickToyboxMenu, shellReady && toybox);
            setEnabled(binding.tabShell.btnQuickTty, shellReady && tty);
            setEnabled(binding.tabShell.btnQuickTtyMenu, shellReady && tty);
            setEnabled(binding.tabShell.btnQuickUmount, shellReady && umount);
            setEnabled(binding.tabShell.btnQuickUmountMenu, shellReady && umount);
            setEnabled(binding.tabShell.btnQuickUnix2dos, shellReady && unix2dos);
            setEnabled(binding.tabShell.btnQuickUnix2dosMenu, shellReady && unix2dos);
            setEnabled(binding.tabShell.btnQuickVmstat, shellReady && vmstat);
            setEnabled(binding.tabShell.btnQuickVmstatMenu, shellReady && vmstat);
            setEnabled(binding.tabShell.btnQuickWatch, shellReady && watch);
            setEnabled(binding.tabShell.btnQuickWatchMenu, shellReady && watch);
            setEnabled(binding.tabShell.btnQuickXxd, shellReady && xxd);
            setEnabled(binding.tabShell.btnQuickXxdMenu, shellReady && xxd);
            setEnabled(binding.tabShell.btnQuickYes, shellReady && yes);
            setEnabled(binding.tabShell.btnQuickYesMenu, shellReady && yes);
            setEnabled(binding.tabShell.btnQuickZcat, shellReady && zcat);
            setEnabled(binding.tabShell.btnQuickZcatMenu, shellReady && zcat);
            setEnabled(binding.tabShell.btnQuickZipinfo, shellReady && zipinfo);
            setEnabled(binding.tabShell.btnQuickZipinfoMenu, shellReady && zipinfo);
            setEnabled(binding.tabShell.btnQuickZiptool, shellReady && ziptool);
            setEnabled(binding.tabShell.btnQuickZiptoolMenu, shellReady && ziptool);
        } catch (Throwable ignored) {
        }
    }

    private static void setAlpha(View view, boolean available) {
        try {
            if (view == null) return;
            view.setAlpha(available ? 1f : 0.45f);
        } catch (Throwable ignored) {
        }
    }

    private static void setEnabled(View view, boolean enabled) {
        try {
            if (view != null) view.setEnabled(enabled);
        } catch (Throwable ignored) {
        }
    }
}
