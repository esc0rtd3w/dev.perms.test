package dev.perms.test.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wires the Shell tab quick-command buttons without keeping the button table in MainActivity.
 */
public final class ShellQuickActions {
    public interface Callbacks {
        void runShellCommand(String command);
        void runAndFillCommand(String command);
        void showToast(String message);
        String getSelectedPackageName();
        String getSelfPackageName();
        void showManageBundledBinsDialog();
    }

    private final Activity activity;
    private final ActivityMainBinding b;
    private final Callbacks callbacks;

    public ShellQuickActions(Activity activity, ActivityMainBinding binding, Callbacks callbacks) {
        this.activity = activity;
        this.b = binding;
        this.callbacks = callbacks;
    }

    public void bind() {
        if (activity == null || b == null || callbacks == null) return;
        bindCoreCommands();
        bindGroupedPresetCommands();
        bindBundledBinaryControls();
        bindScannedBinaryButtons();
        bindDiagnosticsCommands();
    }

    private void bindCoreCommands() {
        setCommand(b.tabShell.btnQuickGetprop, "getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.product.manufacturer; getprop ro.product.model");
        setCommand(b.tabShell.btnQuickPkgs, "pm list packages -3 | head -n 50");
        setCommand(b.tabShell.btnQuickPermDanger, "pm list permissions -d -g 2>/dev/null | head -n 240 || pm list permissions -d 2>/dev/null | head -n 240 || true");
        setCommand(b.tabShell.btnQuickFeatures, "pm list features 2>/dev/null | head -n 240 || pm list features | head -n 240");

        setTargetPackageCommand(b.tabShell.btnQuickPkgPaths, pkg -> "pm path " + pkg + " 2>/dev/null || cmd package path " + pkg + " 2>/dev/null || true");
        setTargetPackageCommand(b.tabShell.btnQuickPkgDumpSel, pkg -> "dumpsys package " + pkg + " | head -n 240");

        setCommand(b.tabShell.btnQuickWhoami, "id; whoami");
        setCommand(b.tabShell.btnQuickUptime, "uptime; cat /proc/uptime");
        setCommand(b.tabShell.btnQuickDf, "df -h | head -n 60");
        setCommand(b.tabShell.btnQuickDate, "date; getprop persist.sys.timezone 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickPwd, "pwd; echo HOME=$HOME; echo SHELL=$SHELL");
        setCommand(b.tabShell.btnQuickEnv, "env | sort | head -n 160");
        setCommand(b.tabShell.btnQuickIp, "ip -brief addr 2>/dev/null || ip addr");
        setCommand(b.tabShell.btnQuickBattery, "dumpsys battery");
        setSelfPackageCommand(b.tabShell.btnQuickPkgSelf, pkg -> "dumpsys package " + pkg + " | head -n 120");
        setSelfPackageCommand(b.tabShell.btnQuickAppops, pkg -> "appops get " + pkg + " | head -n 80");
        setCommand(b.tabShell.btnQuickPs, "ps -A 2>/dev/null | head -n 80 || ps | head -n 80");
        setCommand(b.tabShell.btnQuickTop, "top -n 1 -b 2>/dev/null | head -n 60 || top -n 1 2>/dev/null | head -n 60");
        setCommand(b.tabShell.btnQuickUname, "uname -a; getprop ro.build.fingerprint");
        setCommand(b.tabShell.btnQuickMount, "mount | head -n 120");
        setCommand(b.tabShell.btnQuickSelinux, "getenforce; cat /sys/fs/selinux/enforce 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickRoute, "ip route 2>/dev/null || route -n 2>/dev/null || netstat -rn 2>/dev/null");
        setCommand(b.tabShell.btnQuickCpuinfo, "cat /proc/cpuinfo | head -n 160");
        setCommand(b.tabShell.btnQuickWmSize, "wm size");
        setCommand(b.tabShell.btnQuickWmDensity, "wm density");
        setCommand(b.tabShell.btnQuickSettingsGlobal, "settings list global 2>/dev/null | head -n 240 || true");
        setCommand(b.tabShell.btnQuickSettingsSecure, "settings list secure 2>/dev/null | head -n 240 || true");
        setCommand(b.tabShell.btnQuickMeminfo, "cat /proc/meminfo | head -n 120");
        setCommand(b.tabShell.btnQuickActTop, "dumpsys activity top | head -n 200");

        setCommand(b.tabShell.btnQuickSettings, "settings get global airplane_mode_on; settings get secure adb_enabled 2>/dev/null || settings get global adb_enabled 2>/dev/null; settings get global development_settings_enabled 2>/dev/null; settings get secure location_mode 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickUsers, "pm list users 2>/dev/null || cmd user list 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickInstallSessions, "cmd package list staged-sessions 2>/dev/null || dumpsys package install 2>/dev/null | head -n 240 || true");
        setCommand(b.tabShell.btnQuickPermGroups, "pm list permissions -g 2>/dev/null | head -n 240 || pm list permissions 2>/dev/null | head -n 240 || true");
        setCommand(b.tabShell.btnQuickDisabledPkgs, "pm list packages -d 2>/dev/null | head -n 120 || true");
        setCommand(b.tabShell.btnQuickWifi, "cmd wifi status 2>/dev/null || dumpsys wifi | head -n 200");
        setCommand(b.tabShell.btnQuickDns, "getprop | grep -E 'dns|net\\.dns' | head -n 80; dumpsys connectivity 2>/dev/null | grep -i dns | head -n 80 || true");
        setCommand(b.tabShell.btnQuickSockets, "ss -tunap 2>/dev/null | head -n 120 || netstat -tunap 2>/dev/null | head -n 120 || cat /proc/net/tcp 2>/dev/null | head -n 80");
        setCommand(b.tabShell.btnQuickPower, "dumpsys power | head -n 220");
        setCommand(b.tabShell.btnQuickDeviceIdle, "dumpsys deviceidle | head -n 220");
        setCommand(b.tabShell.btnQuickNet, "ip route 2>/dev/null || route -n 2>/dev/null; ip neigh 2>/dev/null | head -n 80 || true; netstat -an 2>/dev/null | head -n 80 || true");
        setCommand(b.tabShell.btnQuickNetpolicy, "cmd netpolicy list 2>/dev/null | head -n 240 || dumpsys netpolicy | head -n 240");
        setCommand(b.tabShell.btnQuickNeigh, "ip neigh 2>/dev/null || ip n 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickStorage, "df -h | head -n 80; ls -la /sdcard 2>/dev/null | head -n 80 || ls -la /storage/emulated/0 2>/dev/null | head -n 80");
        setCommand(b.tabShell.btnQuickInput, "getevent -p 2>/dev/null | head -n 160 || dumpsys input | head -n 200");
        setCommand(b.tabShell.btnQuickBatteryStats, "dumpsys batterystats --charged 2>/dev/null | head -n 240 || dumpsys batterystats 2>/dev/null | head -n 240");
        setCommand(b.tabShell.btnQuickWakeState, "dumpsys power 2>/dev/null | grep -E 'mWakefulness|Display Power|mHolding|Wake' | head -n 120 || dumpsys power | head -n 180");
        setCommand(b.tabShell.btnQuickInputHelp, "input -h 2>/dev/null || input 2>&1 | head -n 80");
        setCommand(b.tabShell.btnQuickDisplay, "dumpsys display | head -n 220");
        setCommand(b.tabShell.btnQuickServices, "service list | head -n 220");
        setCommand(b.tabShell.btnQuickProps, "getprop | head -n 220");
        setCommand(b.tabShell.btnQuickBuildProps, "getprop ro.build.fingerprint; getprop ro.build.description; getprop ro.bootimage.build.fingerprint 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickKernelInfo, "cat /proc/version 2>/dev/null; cat /proc/cmdline 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickBootState, "getprop ro.boot.verifiedbootstate; getprop ro.boot.flash.locked; getprop ro.boot.vbmeta.device_state 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickLocales, "getprop persist.sys.locale; getprop ro.product.locale; settings get system system_locales 2>/dev/null || true");

        // These groups are read-only diagnostics so quick buttons stay useful without risking device state changes.
        setCommand(b.tabShell.btnQuickLsSdcard, "ls -la /sdcard 2>/dev/null | head -n 120 || ls -la /storage/emulated/0 2>/dev/null | head -n 120");
        setCommand(b.tabShell.btnQuickAppFolder, "ls -la /sdcard/dev.perms.test 2>/dev/null | head -n 160 || true");
        setCommand(b.tabShell.btnQuickDuApp, "du -h -d 2 /sdcard/dev.perms.test 2>/dev/null | sort -h | tail -n 80 || du -h /sdcard/dev.perms.test 2>/dev/null | tail -n 80 || true");
        setCommand(b.tabShell.btnQuickTmp, "ls -la /data/local/tmp 2>/dev/null | head -n 120 || true");
        setCommand(b.tabShell.btnQuickFindDirs, "find /sdcard -maxdepth 2 -type d 2>/dev/null | head -n 120 || find /sdcard -type d 2>/dev/null | head -n 120 || true");
        setCommand(b.tabShell.btnQuickMountsFile, "cat /proc/mounts 2>/dev/null | head -n 160 || true");
        setCommand(b.tabShell.btnQuickSmVolumes, "sm list-volumes all 2>/dev/null; sm list-disks 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickStatShell, "stat /system/bin/sh 2>/dev/null || ls -l /system/bin/sh 2>/dev/null || true");

        setCommand(b.tabShell.btnQuickLogCrash, "logcat -d -b crash 2>/dev/null | tail -n 120 || true");
        setCommand(b.tabShell.btnQuickLogEvents, "logcat -d -b events 2>/dev/null | tail -n 120 || true");
        setCommand(b.tabShell.btnQuickActivityProcesses, "dumpsys activity processes | head -n 240");
        setCommand(b.tabShell.btnQuickSystemServer, "pidof system_server 2>/dev/null; ps -A 2>/dev/null | grep system_server || true");
        setCommand(b.tabShell.btnQuickThreads, "ps -AT 2>/dev/null | head -n 120 || ps -A 2>/dev/null | head -n 120");
        setCommand(b.tabShell.btnQuickAnr, "ls -la /data/anr 2>/dev/null | head -n 80 || true");
        setCommand(b.tabShell.btnQuickTombstones, "ls -la /data/tombstones 2>/dev/null | head -n 80 || true");
        setCommand(b.tabShell.btnQuickActivityServices, "dumpsys activity services | head -n 240");

        setCommand(b.tabShell.btnQuickDevicePolicy, "dumpsys device_policy | head -n 240");
        setCommand(b.tabShell.btnQuickKeystore, "dumpsys android.security.keystore2 2>/dev/null | head -n 240 || dumpsys keystore 2>/dev/null | head -n 240 || true");
        setCommand(b.tabShell.btnQuickCertStore, "ls -la /system/etc/security/cacerts 2>/dev/null | head -n 120; ls -la /apex/com.android.conscrypt/cacerts 2>/dev/null | head -n 120 || true");
        setCommand(b.tabShell.btnQuickRoles, "cmd role holders android.app.role.HOME 2>/dev/null; cmd role holders android.app.role.BROWSER 2>/dev/null || true");
        setCommand(b.tabShell.btnQuickPermsFull, "pm list permissions -f 2>/dev/null | head -n 240 || true");
        setCommand(b.tabShell.btnQuickAppopsHelp, "appops help 2>/dev/null | head -n 120 || cmd appops help 2>/dev/null | head -n 120 || true");
        setCommand(b.tabShell.btnQuickSelinuxDenials, "dmesg 2>/dev/null | grep -i avc | tail -n 80 || logcat -d -b all 2>/dev/null | grep -i avc | tail -n 80 || true");
        setCommand(b.tabShell.btnQuickLockSettings, "cmd lock_settings help 2>/dev/null | head -n 120 || true");
    }

    private void bindGroupedPresetCommands() {
        setCommand(b.tabShell.btnQuickConn, "dumpsys connectivity | head -n 240");
        setCommand(b.tabShell.btnQuickDumpsys, "dumpsys --help");
        setVariantMenu(b.tabShell.btnQuickDumpsysMenu, "dumpsys …", "dumpsys --help");

        setCommand(b.tabShell.btnQuickPm, "pm help");
        setVariantMenu(b.tabShell.btnQuickPmMenu, "pm", "pm help");

        setCommand(b.tabShell.btnQuickLogcat, "logcat -h");
        setVariantMenu(b.tabShell.btnQuickLogcatMenu, "logcat", "logcat -h");

        setCommand(b.tabShell.btnQuickSettingsGroup, "settings");
        setVariantMenu(b.tabShell.btnQuickSettingsGroupMenu, "settings", "settings");
    }

    private void bindBundledBinaryControls() {
        setClick(b.tabShell.btnManageBundledBins, () -> callbacks.showManageBundledBinsDialog());
    }

    private void bindScannedBinaryButtons() {
        wireScannedBinButton(b.tabShell.btnQuickAdb, b.tabShell.btnQuickAdbMenu, "adb --help", "adb --help", "adb …");
        wireScannedBinButton(b.tabShell.btnQuickFastboot, b.tabShell.btnQuickFastbootMenu, "fastboot --help", "fastboot", R.string.shell_menu_title_fastboot);
        wireScannedBinButton(b.tabShell.btnQuickFree, b.tabShell.btnQuickFreeMenu, "free -h 2>/dev/null || free --help 2>/dev/null || free", "free", R.string.shell_menu_title_free);
        wireScannedBinButton(b.tabShell.btnQuickTcpdump, b.tabShell.btnQuickTcpdumpMenu, "tcpdump -h 2>/dev/null || tcpdump --help 2>/dev/null || tcpdump", "tcpdump", R.string.shell_menu_title_tcpdump);
        wireScannedBinButton(b.tabShell.btnQuickGetevent, b.tabShell.btnQuickGeteventMenu, "getevent -pl 2>/dev/null | head -n 240 || getevent -p 2>/dev/null | head -n 240 || getevent -h 2>/dev/null || getevent", "getevent", R.string.shell_menu_title_getevent);
        wireScannedBinButton(b.tabShell.btnQuickLsof, b.tabShell.btnQuickLsofMenu, "lsof 2>/dev/null | head -n 200 || toybox lsof 2>/dev/null | head -n 200 || lsof", "lsof", R.string.shell_menu_title_lsof);
        wireScannedBinButton(b.tabShell.btnQuickReboot, b.tabShell.btnQuickRebootMenu, "reboot --help", "reboot --help", "reboot …");
        wireScannedBinButton(b.tabShell.btnQuickMd5sum, b.tabShell.btnQuickMd5sumMenu, "md5sum /system/bin/sh", "md5sum /system/bin/sh", "md5sum");
        wireScannedBinButton(b.tabShell.btnQuickLspci, b.tabShell.btnQuickLspciMenu, "lspci --help 2>/dev/null || lspci -h 2>/dev/null || lspci", "lspci", "lspci");
        wireScannedBinButton(b.tabShell.btnQuickLsusb, b.tabShell.btnQuickLsusbMenu, "lsusb --help 2>/dev/null || lsusb -h 2>/dev/null || lsusb", "lsusb", "lsusb");
        wireScannedBinButton(b.tabShell.btnQuickNetcat, b.tabShell.btnQuickNetcatMenu, "netcat -h 2>/dev/null || netcat --help 2>/dev/null || nc -h 2>/dev/null || nc --help 2>/dev/null", "netcat -h", "netcat");
        wireScannedBinButton(b.tabShell.btnQuickReadelf, b.tabShell.btnQuickReadelfMenu, "readelf --help 2>/dev/null || readelf -h 2>/dev/null || readelf", "readelf", "readelf");
        wireScannedBinButton(b.tabShell.btnQuickRunAs, b.tabShell.btnQuickRunAsMenu, "run-as --help 2>/dev/null || run-as -h 2>/dev/null || run-as", "run-as", "run-as");
        wireScannedBinButton(b.tabShell.btnQuickScreencap, b.tabShell.btnQuickScreencapMenu, "screencap --help 2>/dev/null || screencap -h 2>/dev/null || screencap", "screencap", "screencap");
        wireScannedBinButton(b.tabShell.btnQuickToybox, b.tabShell.btnQuickToyboxMenu, "toybox --help 2>/dev/null || toybox -h 2>/dev/null || toybox", "toybox", "toybox");
        wireScannedBinButton(b.tabShell.btnQuickGzip, b.tabShell.btnQuickGzipMenu, "gzip --help 2>/dev/null || gzip -h 2>/dev/null || gzip", "gzip", R.string.shell_menu_title_gzip);
        wireScannedBinButton(b.tabShell.btnQuickGunzip, b.tabShell.btnQuickGunzipMenu, "gunzip --help 2>/dev/null || gunzip -h 2>/dev/null || gunzip", "gunzip", R.string.shell_menu_title_gunzip);
        wireScannedBinButton(b.tabShell.btnQuickSetenforce, b.tabShell.btnQuickSetenforceMenu, "setenforce -h 2>/dev/null || setenforce --help 2>/dev/null || setenforce", "setenforce", R.string.shell_menu_title_setenforce);
        wireScannedBinButton(b.tabShell.btnQuickScreenrecord, b.tabShell.btnQuickScreenrecordMenu, "screenrecord --help 2>/dev/null || screenrecord -h 2>/dev/null || screenrecord", "screenrecord", R.string.shell_menu_title_screenrecord);
        wireScannedBinButton(b.tabShell.btnQuickStart, b.tabShell.btnQuickStartMenu, "start", "start", R.string.shell_menu_title_start);
        wireScannedBinButton(b.tabShell.btnQuickSysctl, b.tabShell.btnQuickSysctlMenu, "sysctl --help 2>/dev/null || sysctl -h 2>/dev/null || sysctl", "sysctl", R.string.shell_menu_title_sysctl);
        wireScannedBinButton(b.tabShell.btnQuickBase64, b.tabShell.btnQuickBase64Menu, "base64 --help 2>/dev/null || base64 -h 2>/dev/null || base64", "base64", R.string.shell_menu_title_base64);
        wireScannedBinButton(b.tabShell.btnQuickClear, b.tabShell.btnQuickClearMenu, "clear 2>/dev/null || clear", "clear", R.string.shell_menu_title_clear);
        wireScannedBinButton(b.tabShell.btnQuickCurl, b.tabShell.btnQuickCurlMenu, "curl --help 2>/dev/null || curl -h 2>/dev/null || curl", "curl", R.string.shell_menu_title_curl);
        wireScannedBinButton(b.tabShell.btnQuickDos2unix, b.tabShell.btnQuickDos2unixMenu, "dos2unix --help 2>/dev/null || dos2unix -h 2>/dev/null || dos2unix", "dos2unix", R.string.shell_menu_title_dos2unix);
        wireScannedBinButton(b.tabShell.btnQuickIpBin, b.tabShell.btnQuickIpBinMenu, "ip -V 2>/dev/null || ip --version 2>/dev/null || ip help 2>/dev/null || ip", "ip", R.string.shell_menu_title_ip);
        wireScannedBinButton(b.tabShell.btnQuickPing, b.tabShell.btnQuickPingMenu, "ping -c 1 127.0.0.1 2>/dev/null || ping -c 1 localhost 2>/dev/null", "ping", R.string.shell_menu_title_ping);
        wireScannedBinButton(b.tabShell.btnQuickSha256sum, b.tabShell.btnQuickSha256sumMenu, "sha256sum --help 2>/dev/null || sha256sum -h 2>/dev/null || sha256sum", "sha256sum", R.string.shell_menu_title_sha256sum);
        wireScannedBinButton(b.tabShell.btnQuickTty, b.tabShell.btnQuickTtyMenu, "tty 2>/dev/null || tty", "tty", R.string.shell_menu_title_tty);
        wireScannedBinButton(b.tabShell.btnQuickUmount, b.tabShell.btnQuickUmountMenu, "umount --help 2>/dev/null || umount -h 2>/dev/null || umount", "umount", R.string.shell_menu_title_umount);
        wireScannedBinButton(b.tabShell.btnQuickUnix2dos, b.tabShell.btnQuickUnix2dosMenu, "unix2dos --help 2>/dev/null || unix2dos -h 2>/dev/null || unix2dos", "unix2dos", R.string.shell_menu_title_unix2dos);
        wireScannedBinButton(b.tabShell.btnQuickVmstat, b.tabShell.btnQuickVmstatMenu, "vmstat --help 2>/dev/null || vmstat -h 2>/dev/null || vmstat", "vmstat", R.string.shell_menu_title_vmstat);
        wireScannedBinButton(b.tabShell.btnQuickWatch, b.tabShell.btnQuickWatchMenu, "watch --help 2>/dev/null || watch -h 2>/dev/null || watch", "watch", R.string.shell_menu_title_watch);
        wireScannedBinButton(b.tabShell.btnQuickXxd, b.tabShell.btnQuickXxdMenu, "xxd -h 2>/dev/null || xxd --help 2>/dev/null || xxd", "xxd", R.string.shell_menu_title_xxd);
        wireScannedBinButton(b.tabShell.btnQuickYes, b.tabShell.btnQuickYesMenu, "yes test | head -n 40 2>/dev/null || yes test | toybox head -n 40 2>/dev/null", "yes", R.string.shell_menu_title_yes);
        wireScannedBinButton(b.tabShell.btnQuickZcat, b.tabShell.btnQuickZcatMenu, "zcat /proc/config.gz 2>/dev/null | head -n 200 || zcat --help 2>/dev/null || zcat -h 2>/dev/null || zcat", "zcat", R.string.shell_menu_title_zcat);
        wireScannedBinButton(b.tabShell.btnQuickZipinfo, b.tabShell.btnQuickZipinfoMenu, "zipinfo /system/framework/framework-res.apk 2>/dev/null | head -n 160 || zipinfo -h 2>/dev/null || zipinfo --help 2>/dev/null || zipinfo", "zipinfo", R.string.shell_menu_title_zipinfo);
        wireScannedBinButton(b.tabShell.btnQuickZiptool, b.tabShell.btnQuickZiptoolMenu, "ziptool --help 2>/dev/null || ziptool -h 2>/dev/null || ziptool", "ziptool", R.string.shell_menu_title_ziptool);
    }


    private void wireScannedBinButton(View button, View menuButton, String defaultCommand, String variantsKey, int titleRes) {
        wireScannedBinButton(button, menuButton, defaultCommand, variantsKey, titleRes, null);
    }

    private void wireScannedBinButton(View button, View menuButton, String defaultCommand, String variantsKey, String title) {
        wireScannedBinButton(button, menuButton, defaultCommand, variantsKey, 0, title);
    }

    private void wireScannedBinButton(View button, View menuButton, String defaultCommand, String variantsKey, int titleRes, String title) {
        setClick(button, () -> runCommand(defaultCommand));
        setClick(menuButton, () -> showScannedBinVariants(variantsKey, titleRes, title));
    }

    private void showScannedBinVariants(String variantsKey, int titleRes, String title) {
        try {
            LinkedHashMap<String, String> variants = ShellCommandVariants.forBaseCommand(variantsKey);
            if (variants == null || variants.isEmpty()) return;

            final ArrayList<String> labels = new ArrayList<>();
            final ArrayList<String> commands = new ArrayList<>();
            for (Map.Entry<String, String> e : variants.entrySet()) {
                if (e == null) continue;
                labels.add(safe(e.getKey()));
                commands.add(safe(e.getValue()));
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            if (title != null) {
                builder.setTitle(title);
            } else {
                builder.setTitle(titleRes);
            }
            builder.setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                try {
                    if (which >= 0 && which < commands.size()) {
                        callbacks.runAndFillCommand(commands.get(which));
                    }
                } catch (Throwable ignored) {
                }
            })
            .setNegativeButton(R.string.shell_action_cancel, null)
            .show();
        } catch (Throwable ignored) {
        }
    }

    private void bindDiagnosticsCommands() {
        setCommand(b.tabShell.btnQuickActRecents, "dumpsys activity recents | head -n 240");
        setCommand(b.tabShell.btnQuickPropDump, "getprop | sort | head -n 240");
        setCommand(b.tabShell.btnQuickNetstats, "dumpsys netstats | head -n 240");
        setCommand(b.tabShell.btnQuickProcstats, "dumpsys procstats --hours 3 2>/dev/null | head -n 240 || dumpsys procstats 2>/dev/null | head -n 240");
        setCommand(b.tabShell.btnQuickJobs, "dumpsys jobscheduler | head -n 240");
        setCommand(b.tabShell.btnQuickAlarm, "dumpsys alarm | head -n 240");
        setCommand(b.tabShell.btnQuickDropbox, "dumpsys dropbox --print | head -n 240");
        setCommand(b.tabShell.btnQuickTelephony, "dumpsys telephony.registry 2>/dev/null | head -n 240 || dumpsys telephony 2>/dev/null | head -n 240");
        setCommand(b.tabShell.btnQuickSensors, "dumpsys sensorservice 2>/dev/null | head -n 240 || dumpsys sensorservice 2>/dev/null | head -n 240");
        setCommand(b.tabShell.btnQuickSurface, "dumpsys SurfaceFlinger --list 2>/dev/null || dumpsys SurfaceFlinger 2>/dev/null | head -n 240");
        setCommand(b.tabShell.btnQuickUsage, "dumpsys usagestats | head -n 240");
    }

    private void setCommand(View button, String command) {
        setClick(button, () -> runCommand(command));
    }

    private void setTargetPackageCommand(View button, PackageCommandBuilder builder) {
        setClick(button, () -> {
            String pkg = safe(callbacks.getSelectedPackageName());
            if (TextUtils.isEmpty(pkg)) {
                callbacks.showToast("Enter/select a target package first.");
                return;
            }
            runCommand(builder.build(pkg));
        });
    }

    private void setSelfPackageCommand(View button, PackageCommandBuilder builder) {
        setClick(button, () -> runCommand(builder.build(safe(callbacks.getSelfPackageName()))));
    }

    private void setVariantMenu(View button, String title, String baseCommand) {
        setClick(button, () -> {
            LinkedHashMap<String, String> variants = ShellCommandVariants.forBaseCommand(baseCommand);
            if (variants == null || variants.isEmpty()) return;

            final ArrayList<String> labels = new ArrayList<>();
            final ArrayList<String> commands = new ArrayList<>();
            for (Map.Entry<String, String> e : variants.entrySet()) {
                if (e == null) continue;
                labels.add(safe(e.getKey()));
                commands.add(safe(e.getValue()));
            }

            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                        try {
                            if (which >= 0 && which < commands.size()) {
                                callbacks.runAndFillCommand(commands.get(which));
                            }
                        } catch (Throwable ignored) {
                        }
                    })
                    .show();
        });
    }

    private void setClick(View view, Runnable action) {
        if (view == null || action == null) return;
        view.setOnClickListener(v -> {
            try {
                action.run();
            } catch (Throwable ignored) {
            }
        });
    }

    private void runCommand(String command) {
        try {
            String cmd = safe(command);
            b.tabShell.edtCmd.setText(cmd);
            callbacks.runShellCommand(currentCommandText());
        } catch (Throwable ignored) {
        }
    }

    private String currentCommandText() {
        return b.tabShell.edtCmd.getText() == null ? "" : b.tabShell.edtCmd.getText().toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private interface PackageCommandBuilder {
        String build(String packageName);
    }
}
