package dev.perms.test.shell;

import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Shell-command variant presets used by the Shell tab.
 */
public final class ShellCommandVariants {
    private ShellCommandVariants() {
    }

public static LinkedHashMap<String, String> forBaseCommand(String base) {
        try {
            if (base == null) return null;
            String b = base.trim();

            // Grouped preset: dumpsys …
            if (isDumpsysHelpCommand(b)) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("audio", "dumpsys audio | head -n 240");
                m.put("notification", "dumpsys notification | head -n 240");
                m.put("window", "dumpsys window windows | head -n 240");
                // Moved from Logging tab: keep these under the dumpsys grouped menu.
                m.put("package (target)", "dumpsys package {pkg}");
                m.put("activity top", "dumpsys activity top");
                return m;
            }

            // Grouped preset: pm …
            if (isPmHelpCommand(b)) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("packages", "pm list packages -3 2>/dev/null | head -n 240 || pm list packages -3 | head -n 240");
                m.put("permissions", "pm list permissions -d 2>/dev/null | head -n 240 || pm list permissions -d | head -n 240");
                m.put("users", "pm list users 2>/dev/null || pm list users");
                return m;
            }

            // Grouped preset: logcat …
            if (isLogcatHelpCommand(b)) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("dump main", "logcat -d -b main | head -n 240");
                m.put("dump system", "logcat -d -b system | head -n 240");
                m.put("dump events", "logcat -d -b events | head -n 240");
                return m;
            }

            // Grouped preset: settings …
            if (isSettingsCommand(b)) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("global", "settings list global | head -n 240");
                m.put("secure", "settings list secure | head -n 240");
                m.put("system", "settings list system | head -n 240");
                return m;
            }


// Grouped preset: adb …
if (isAdbHelpCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("version", "adb version");
    m.put("devices", "adb devices -l");
    m.put("start-server", "adb start-server");
    m.put("kill-server", "adb kill-server");
    return m;
}



// Grouped preset: fastboot …
if (isFastbootCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "fastboot --help 2>/dev/null || fastboot help 2>/dev/null || fastboot");
    m.put("version", "fastboot --version 2>/dev/null || fastboot version 2>/dev/null || fastboot");
    m.put("devices", "fastboot devices");
    m.put("getvar all", "fastboot getvar all");
    m.put("reboot", "fastboot reboot");
    return m;
}

// Grouped preset: tcpdump …
if (isTcpdumpCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (-h)", "tcpdump -h 2>/dev/null || tcpdump --help 2>/dev/null || tcpdump");
    m.put("list interfaces (-D)", "tcpdump -D 2>/dev/null || tcpdump --list-interfaces 2>/dev/null || tcpdump -h");
    m.put("capture 10 packets (any)", "tcpdump -i any -c 10 2>/dev/null || tcpdump -i any -c 10");
    m.put("capture DNS (port 53) (5)", "tcpdump -i any -c 5 port 53 2>/dev/null || tcpdump -i any -c 5 port 53");
    m.put("capture HTTP (port 80) (5)", "tcpdump -i any -c 5 port 80 2>/dev/null || tcpdump -i any -c 5 port 80");
    return m;
}

// Grouped preset: getevent …
if (isGeteventCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (-h)", "getevent -h 2>/dev/null || getevent --help 2>/dev/null || getevent");
    m.put("list devices (-p) | head", "getevent -p 2>/dev/null | head -n 240 || getevent -p | head -n 240");
    m.put("list devices + labels (-pl) | head", "getevent -pl 2>/dev/null | head -n 240 || getevent -pl | head -n 240");
    return m;
}

// Grouped preset: lsof …
if (isLsofCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (-h)", "lsof -h 2>/dev/null || lsof --help 2>/dev/null || toybox lsof -h 2>/dev/null || lsof");
    m.put("list open files | head", "lsof 2>/dev/null | head -n 200 || toybox lsof 2>/dev/null | head -n 200 || lsof | head -n 200");
    m.put("list network (-i) | head", "lsof -i 2>/dev/null | head -n 200 || toybox lsof -i 2>/dev/null | head -n 200 || lsof -i | head -n 200");
    m.put("list for PID (replace {pid})", "lsof -p {pid} 2>/dev/null | head -n 200 || toybox lsof -p {pid} 2>/dev/null | head -n 200 || lsof -p {pid} | head -n 200");
    return m;
}

// Grouped preset: reboot …
if (isRebootHelpCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("reboot", "reboot");
    m.put("power off (-p)", "reboot -p");
    m.put("recovery", "reboot recovery");
    m.put("bootloader", "reboot bootloader");
    return m;
}

// Grouped preset: md5sum …
if (isMd5sumCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "md5sum --help 2>/dev/null || md5sum -h 2>/dev/null || md5sum");
    m.put("hash /system/bin/sh", "md5sum /system/bin/sh");
    m.put("hash /system/bin/toybox", "md5sum /system/bin/toybox 2>/dev/null || md5sum /system/bin/toolbox 2>/dev/null || md5sum /system/bin/sh");
    m.put("hash /system/bin/app_process64", "md5sum /system/bin/app_process64 2>/dev/null || md5sum /system/bin/app_process32 2>/dev/null || md5sum /system/bin/app_process 2>/dev/null || md5sum /system/bin/sh");
    m.put("stdin test (echo)", "echo -n test | md5sum 2>/dev/null || echo -n test | md5sum");
    m.put("version (--version)", "md5sum --version 2>/dev/null || md5sum -V 2>/dev/null || md5sum");
    return m;
}

// Grouped preset: lspci …
if (isLspciCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "lspci --help 2>/dev/null || lspci -h 2>/dev/null || lspci");
    // Prefer IDs by default so "list devices" is immediately useful.
    m.put("list devices", "lspci -nn 2>/dev/null || lspci -nn || lspci");
    m.put("list devices (basic)", "lspci");
    m.put("tree (-t)", "lspci -t 2>/dev/null || lspci -t");
    m.put("kernel drivers (-k)", "lspci -k 2>/dev/null || lspci -k");
    m.put("IDs + kernel drivers (-nnk)", "lspci -nnk 2>/dev/null || lspci -nnk || lspci -nn || lspci");
    m.put("very verbose (-vv) | head", "lspci -vv 2>/dev/null | head -n 240 || lspci -vv | head -n 240");
    return m;
}

// Grouped preset: lsusb …
if (isLsusbCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "lsusb --help 2>/dev/null || lsusb -h 2>/dev/null || lsusb");
    // Prefer a short verbose listing (still capped) so this isn't just "lsusb".
    m.put("list devices", "lsusb -v 2>/dev/null | head -n 240 || lsusb -v | head -n 240 || lsusb");
    m.put("list devices (basic)", "lsusb");
    m.put("tree (-t)", "lsusb -t 2>/dev/null || lsusb -t");
    m.put("verbose (-v) | head", "lsusb -v 2>/dev/null | head -n 240 || lsusb -v | head -n 240");
    m.put("version (--version)", "lsusb --version 2>/dev/null || lsusb -V 2>/dev/null || lsusb");
    return m;
}

// Grouped preset: netcat …
if (isNetcatCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help", "netcat -h 2>/dev/null || netcat --help 2>/dev/null || nc -h 2>/dev/null || nc --help 2>/dev/null");
    m.put("version", "nc -V 2>/dev/null || nc --version 2>/dev/null || netcat -V 2>/dev/null || netcat --version 2>/dev/null || echo n/a");
    m.put("probe localhost:5555 (-z)", "nc -z -w 2 127.0.0.1 5555 2>/dev/null && echo open || echo closed");
    m.put("probe localhost:80 (-z)", "nc -z -w 2 127.0.0.1 80 2>/dev/null && echo open || echo closed");
    m.put("probe localhost:22 (-z)", "nc -z -w 2 127.0.0.1 22 2>/dev/null && echo open || echo closed");
    m.put("send ping to localhost:5555 (timeout)", "echo ping | nc -w 2 127.0.0.1 5555 2>/dev/null || echo \"no response\"");
    m.put("resolve /etc/hosts | head", "cat /etc/hosts 2>/dev/null | head -n 200 || cat /system/etc/hosts 2>/dev/null | head -n 200");
    return m;
}


// Grouped preset: base64 …
if (isBase64Command(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "base64 --help 2>/dev/null || base64 -h 2>/dev/null || base64");
    m.put("encode (echo)", "echo -n test | base64 2>/dev/null || echo test | base64 2>/dev/null");
    m.put("decode (echo)", "echo -n dGVzdA== | base64 -d 2>/dev/null || echo -n dGVzdA== | base64 --decode 2>/dev/null");
    return m;
}

// Grouped preset: curl …
if (isCurlCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "curl --help 2>/dev/null || curl -h 2>/dev/null || curl");
    m.put("version (-V)", "curl -V 2>/dev/null || curl --version 2>/dev/null || curl");
    m.put("HEAD example.com (5s)", "curl -I --max-time 5 https://example.com 2>/dev/null || curl -I https://example.com 2>/dev/null || curl -I http://example.com 2>/dev/null");
    m.put("GET example.com (first 200)", "curl --max-time 5 -L https://example.com 2>/dev/null | head -n 200 || curl -L https://example.com 2>/dev/null | head -n 200");
    return m;
}


// Grouped preset: dos2unix …
if (isDos2unixCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "dos2unix --help 2>/dev/null || dos2unix -h 2>/dev/null || dos2unix");
    m.put("demo (/data/local/tmp)", "printf 'a\r\nb\r\n' > /data/local/tmp/dos2unix_test.txt; dos2unix /data/local/tmp/dos2unix_test.txt 2>/dev/null || dos2unix /data/local/tmp/dos2unix_test.txt; head -n 10 /data/local/tmp/dos2unix_test.txt 2>/dev/null");
    m.put("convert file (replace {path})", "dos2unix {path}");
    return m;
}

// Grouped preset: tty …
if (isTtyCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("tty", "tty 2>/dev/null || tty");
    m.put("tty ok? (-s)", "tty -s 2>/dev/null && echo ok || echo n/a");
    m.put("id + tty", "id; tty 2>/dev/null || tty");
    return m;
}

// Grouped preset: umount …
if (isUmountCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "umount --help 2>/dev/null || umount -h 2>/dev/null || umount");
    m.put("list mounts (/proc/mounts) | head", "cat /proc/mounts 2>/dev/null | head -n 120 || mount | head -n 120");
    m.put("umount {path}", "umount {path}");
    return m;
}

// Grouped preset: unix2dos …
if (isUnix2dosCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "unix2dos --help 2>/dev/null || unix2dos -h 2>/dev/null || unix2dos");
    m.put("demo (/data/local/tmp)", "printf 'a\nb\n' > /data/local/tmp/unix2dos_test.txt; unix2dos /data/local/tmp/unix2dos_test.txt 2>/dev/null || unix2dos /data/local/tmp/unix2dos_test.txt; head -n 10 /data/local/tmp/unix2dos_test.txt 2>/dev/null");
    m.put("convert file (replace {path})", "unix2dos {path}");
    return m;
}

// Grouped preset: ip …
if (isIpCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help", "ip help 2>/dev/null || ip -h 2>/dev/null || ip");
    m.put("link show", "ip link show 2>/dev/null || ip link");
    m.put("addr show", "ip addr show 2>/dev/null || ip addr");
    m.put("route show", "ip route show 2>/dev/null || ip route");
    return m;
}

// Grouped preset: ping …
if (isPingCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("ping localhost (1)", "ping -c 1 127.0.0.1 2>/dev/null || ping -c 1 localhost 2>/dev/null");
    m.put("ping 1.1.1.1 (3) (3s)", "ping -c 3 1.1.1.1 2>/dev/null || ping -c 3 8.8.8.8 2>/dev/null");
    return m;
}

// Grouped preset: sha256sum …
if (isSha256sumCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "sha256sum --help 2>/dev/null || sha256sum -h 2>/dev/null || sha256sum");
    m.put("hash (echo)", "echo -n test | sha256sum 2>/dev/null || echo test | sha256sum 2>/dev/null");
    return m;
}

// Grouped preset: xxd …
if (isXxdCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (-h)", "xxd -h 2>/dev/null || xxd --help 2>/dev/null || xxd");
    m.put("hex dump (echo)", "echo -n test | xxd 2>/dev/null || printf test | xxd 2>/dev/null");
    m.put("plain hex (-p) (echo)", "echo -n test | xxd -p 2>/dev/null || printf test | xxd -p 2>/dev/null");
    return m;
}


// Grouped preset: yes …
if (isYesCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "yes --help 2>/dev/null || yes -h 2>/dev/null || yes");
    m.put("yes (head 40)", "yes | head -n 40 2>/dev/null || yes | toybox head -n 40 2>/dev/null");
    m.put("yes test (head 40)", "yes test | head -n 40 2>/dev/null || yes test | toybox head -n 40 2>/dev/null");
    return m;
}

// Grouped preset: zcat …
if (isZcatCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "zcat --help 2>/dev/null || zcat -h 2>/dev/null || zcat");
    m.put("/proc/config.gz (head 200)", "zcat /proc/config.gz 2>/dev/null | head -n 200");
    m.put("/proc/config.gz (CONFIG_*)", "zcat /proc/config.gz 2>/dev/null | grep -E '^CONFIG_' | head -n 200");
    return m;
}

// Grouped preset: zipinfo …
if (isZipinfoCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (-h)", "zipinfo -h 2>/dev/null || zipinfo --help 2>/dev/null || zipinfo");
    m.put("framework-res.apk (head 200)", "zipinfo /system/framework/framework-res.apk 2>/dev/null | head -n 200");
    return m;
}

// Grouped preset: ziptool …
if (isZiptoolCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "ziptool --help 2>/dev/null || ziptool -h 2>/dev/null || ziptool");
    m.put("framework-res.apk (head 200)", "ziptool /system/framework/framework-res.apk 2>/dev/null | head -n 200");
    return m;
}

// Grouped preset: readelf …
if (isReadelfCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    // Use a known-good ELF so these always run without extra user input.
    m.put("help (--help)", "readelf --help 2>/dev/null || readelf -h 2>/dev/null || readelf");
    m.put("version (--version)", "readelf --version 2>/dev/null || readelf -V 2>/dev/null || readelf");
    m.put("ELF header (-h) /system/bin/sh", "readelf -h /system/bin/sh 2>/dev/null || readelf -h /system/bin/sh");
    m.put("program headers (-l) /system/bin/sh", "readelf -l /system/bin/sh 2>/dev/null || readelf -l /system/bin/sh");
    m.put("section headers (-S) | head", "readelf -S /system/bin/sh 2>/dev/null | head -n 240 || readelf -S /system/bin/sh | head -n 240");
    m.put("symbols (-s) | head", "readelf -s /system/bin/sh 2>/dev/null | head -n 240 || readelf -s /system/bin/sh | head -n 240");
    m.put("dynamic (-d) | head", "readelf -d /system/bin/sh 2>/dev/null | head -n 240 || readelf -d /system/bin/sh | head -n 240");
    m.put("relocations (-r) | head", "readelf -r /system/bin/sh 2>/dev/null | head -n 240 || readelf -r /system/bin/sh | head -n 240");
    m.put("notes (-n) | head", "readelf -n /system/bin/sh 2>/dev/null | head -n 240 || readelf -n /system/bin/sh | head -n 240");
    m.put("all (-a) | head", "readelf -a /system/bin/sh 2>/dev/null | head -n 240 || readelf -a /system/bin/sh | head -n 240");
    m.put("ELF header (-h) /system/bin/toybox", "readelf -h /system/bin/toybox 2>/dev/null || readelf -h /system/bin/toybox 2>/dev/null || readelf -h /system/bin/sh");
    return m;
}

// Grouped preset: run-as …
if (isRunAsCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "run-as --help 2>/dev/null || run-as -h 2>/dev/null || run-as");
    m.put("id (as {pkg})", "run-as {pkg} id");
    m.put("pwd (as {pkg})", "run-as {pkg} pwd");
    m.put("env (as {pkg}) | head", "run-as {pkg} env 2>/dev/null | head -n 200 || run-as {pkg} sh -c 'env 2>/dev/null | head -n 200'");
    m.put("ls app data dir (as {pkg})", "run-as {pkg} ls -la 2>/dev/null || run-as {pkg} ls -la .");
    m.put("ls files/ (as {pkg})", "run-as {pkg} ls -la files 2>/dev/null | head -n 200 || run-as {pkg} ls -la 2>/dev/null | head -n 200");
    m.put("ls cache/ (as {pkg})", "run-as {pkg} ls -la cache 2>/dev/null | head -n 200 || run-as {pkg} ls -la 2>/dev/null | head -n 200");
    return m;
}

// Grouped preset: screencap …
if (isScreencapCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "screencap --help 2>/dev/null || screencap -h 2>/dev/null || screencap");
    m.put("save PNG to /sdcard/screencap.png", "screencap -p /sdcard/screencap.png");
    m.put("save PNG to /sdcard with timestamp", "screencap -p /sdcard/screencap_$(date +%Y%m%d_%H%M%S).png");
    m.put("save RAW to /sdcard/screencap.raw", "screencap /sdcard/screencap.raw");
    m.put("list /sdcard screencap files", "ls -lt /sdcard/screencap* 2>/dev/null | head -n 40 || ls -lt /storage/emulated/0/screencap* 2>/dev/null | head -n 40");
    return m;
}

// Grouped preset: toybox …
if (isToyboxCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    // Keep these non-interactive and reasonably bounded.
    m.put("help", "toybox --help 2>/dev/null || toybox -h 2>/dev/null || toybox");
    m.put("list applets", "toybox");
    m.put("version", "toybox --version 2>/dev/null || toybox -V 2>/dev/null || toybox");

    // Identity / env
    m.put("whoami", "toybox whoami 2>/dev/null || whoami");
    m.put("id", "toybox id 2>/dev/null || id");
    m.put("uname (-a)", "toybox uname -a 2>/dev/null || uname -a");
    m.put("date", "toybox date 2>/dev/null || date");
    m.put("uptime", "toybox uptime 2>/dev/null || uptime");
    m.put("env | head", "toybox env 2>/dev/null | head -n 200 || env 2>/dev/null | head -n 200 || env | head -n 200");

    // Processes
    m.put("ps (-A)", "toybox ps -A 2>/dev/null || toybox ps 2>/dev/null || ps -A 2>/dev/null || ps");
    m.put("top (1 sample) | head", "toybox top -n 1 2>/dev/null | head -n 200 || top -n 1 2>/dev/null | head -n 200 || top -n 1 | head -n 200");
    m.put("lsof | head", "toybox lsof 2>/dev/null | head -n 200 || lsof 2>/dev/null | head -n 200 || lsof | head -n 200");

    // Filesystem / storage
    m.put("df (-h)", "toybox df -h 2>/dev/null || toybox df 2>/dev/null || df -h 2>/dev/null || df");
    m.put("mount | head", "toybox mount 2>/dev/null | head -n 200 || mount 2>/dev/null | head -n 200 || mount | head -n 200");
    m.put("ls /system/bin | head", "toybox ls /system/bin 2>/dev/null | head -n 200 || ls /system/bin 2>/dev/null | head -n 200 || ls /system/bin | head -n 200");
    m.put("ls -la /system/bin | head", "toybox ls -la /system/bin 2>/dev/null | head -n 200 || ls -la /system/bin 2>/dev/null | head -n 200 || ls -la /system/bin | head -n 200");
    m.put("stat /system/bin/sh", "toybox stat /system/bin/sh 2>/dev/null || stat /system/bin/sh 2>/dev/null || stat /system/bin/sh");
    m.put("du -s /system", "toybox du -s /system 2>/dev/null || du -s /system 2>/dev/null || du -s /system");

    // /proc quick checks
    m.put("cpuinfo | head", "toybox cat /proc/cpuinfo 2>/dev/null | head -n 200 || cat /proc/cpuinfo 2>/dev/null | head -n 200 || cat /proc/cpuinfo | head -n 200");
    m.put("meminfo | head", "toybox cat /proc/meminfo 2>/dev/null | head -n 200 || cat /proc/meminfo 2>/dev/null | head -n 200 || cat /proc/meminfo | head -n 200");
    m.put("mounts | head", "toybox cat /proc/mounts 2>/dev/null | head -n 200 || cat /proc/mounts 2>/dev/null | head -n 200 || cat /proc/mounts | head -n 200");

    // Networking
    m.put("ip addr | head", "toybox ip addr 2>/dev/null | head -n 200 || ip addr 2>/dev/null | head -n 200 || ip addr | head -n 200");
    m.put("ifconfig | head", "toybox ifconfig 2>/dev/null | head -n 200 || ifconfig 2>/dev/null | head -n 200 || ifconfig | head -n 200");
    m.put("netstat -an | head", "toybox netstat -an 2>/dev/null | head -n 200 || netstat -an 2>/dev/null | head -n 200 || netstat -an | head -n 200");
    m.put("route | head", "toybox route 2>/dev/null | head -n 200 || route 2>/dev/null | head -n 200 || route | head -n 200");
    m.put("ping (1) 127.0.0.1", "toybox ping -c 1 127.0.0.1 2>/dev/null || ping -c 1 127.0.0.1 2>/dev/null || ping -c 1 127.0.0.1");

    // Misc
    m.put("dmesg | tail", "toybox dmesg 2>/dev/null | tail -n 200 || dmesg 2>/dev/null | tail -n 200 || dmesg | tail -n 200");
    m.put("getconf | head", "toybox getconf 2>/dev/null | head -n 200 || getconf 2>/dev/null | head -n 200 || getconf | head -n 200");
    return m;
}

// Grouped preset: gzip …
if (isGzipCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "gzip --help 2>/dev/null || gzip -h 2>/dev/null || gzip");
    m.put("version (--version)", "gzip --version 2>/dev/null || gzip -V 2>/dev/null || gzip");
    m.put("roundtrip (echo)", "echo test | gzip -c 2>/dev/null | gzip -dc 2>/dev/null || echo test | gzip -c | gzip -dc");
    return m;
}

// Grouped preset: gunzip …
if (isGunzipCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "gunzip --help 2>/dev/null || gunzip -h 2>/dev/null || gunzip");
    m.put("version (--version)", "gunzip --version 2>/dev/null || gunzip -V 2>/dev/null || gunzip");
    m.put("roundtrip (echo)", "echo test | gzip -c 2>/dev/null | gunzip -c 2>/dev/null || echo test | gzip -c | gunzip -c");
    return m;
}

// Grouped preset: setenforce …
if (isSetenforceCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help/usage", "setenforce -h 2>/dev/null || setenforce --help 2>/dev/null || setenforce");
    m.put("status (getenforce)", "getenforce 2>/dev/null || getenforce");
    m.put("permissive (0)", "setenforce 0");
    m.put("enforcing (1)", "setenforce 1");
    return m;
}

// Grouped preset: screenrecord …
if (isScreenrecordCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "screenrecord --help 2>/dev/null || screenrecord -h 2>/dev/null || screenrecord");
    m.put("record 10s to /sdcard/screenrecord_YYYYMMDD_HHMMSS.mp4", "screenrecord --time-limit 10 /sdcard/screenrecord_$(date +%Y%m%d_%H%M%S).mp4");
    m.put("record 30s to /sdcard/screenrecord_YYYYMMDD_HHMMSS.mp4", "screenrecord --time-limit 30 /sdcard/screenrecord_$(date +%Y%m%d_%H%M%S).mp4");
    m.put("list /sdcard screenrecord files", "ls -lt /sdcard/screenrecord* 2>/dev/null | head -n 40 || ls -lt /storage/emulated/0/screenrecord* 2>/dev/null | head -n 40");
    return m;
}

// Grouped preset: start …
if (isStartCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("usage", "start");
	    m.put("status (getprop init.svc.*) | head", "getprop 2>/dev/null | grep -E '^init\\.svc\\.' 2>/dev/null | head -n 120 || getprop | head -n 240");
    return m;
}

// Grouped preset: sysctl …
if (isSysctlCommand(b)) {
    LinkedHashMap<String, String> m = new LinkedHashMap<>();
    m.put("help (--help)", "sysctl --help 2>/dev/null || sysctl -h 2>/dev/null || sysctl");
    m.put("list all (-a) | head", "sysctl -a 2>/dev/null | head -n 240 || sysctl -a | head -n 240");
    m.put("kernel version", "sysctl -n kernel.version 2>/dev/null || sysctl kernel.version 2>/dev/null || uname -a");
    return m;
}




            
            // Grouped preset: clear …
            if (isClearHelpCommand(b)) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("clear", "clear 2>/dev/null || clear");
                m.put("help", "clear --help 2>/dev/null || clear -h 2>/dev/null || clear");
                // Some environments support 'reset' (optional).
                m.put("reset (if available)", "reset 2>/dev/null || clear");
                return m;
            }

            // Grouped preset: free …
            if (isFreeHelpCommand(b)) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("human (-h)", "free -h 2>/dev/null || free");
                m.put("MB (-m)", "free -m 2>/dev/null || free");
                m.put("bytes (-b)", "free -b 2>/dev/null || free");
                m.put("/proc/meminfo | head", "cat /proc/meminfo 2>/dev/null | head -n 60 || cat /proc/meminfo");
                return m;
            }

            // Grouped preset: vmstat …
            if (isVmstatHelpCommand(b)) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("vmstat", "vmstat 2>/dev/null || vmstat");
                m.put("summary (-s)", "vmstat -s 2>/dev/null || vmstat -s");
                m.put("active/inactive (-a)", "vmstat -a 2>/dev/null || vmstat");
                m.put("5 samples (1s)", "vmstat 1 5 2>/dev/null || vmstat");
                m.put("/proc/vmstat | head", "cat /proc/vmstat 2>/dev/null | head -n 80 || cat /proc/vmstat");
                return m;
            }

            // Grouped preset: watch …
            if (isWatchHelpCommand(b)) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("help", "watch --help 2>/dev/null || watch -h 2>/dev/null || watch");
                // WARNING: watch runs until interrupted (Ctrl+C); use bounded loops below if you want finite output.
                m.put("watch date (Ctrl+C)", "watch -n 1 date 2>/dev/null || watch date 2>/dev/null || date");
                m.put("5x date loop (safe)", "i=0; while [ $i -lt 5 ]; do date; i=$((i+1)); sleep 1; done");
                m.put("5x free -h loop (safe)", "i=0; while [ $i -lt 5 ]; do free -h 2>/dev/null || cat /proc/meminfo | head -n 20; i=$((i+1)); sleep 1; done");
                return m;
            }

return null;
        } catch (Throwable ignored) {
            return null;
        }
    }


    private static boolean isDumpsysHelpCommand(String base) {
        try {
            if (base == null) return false;
            String b2 = base.trim();
            if (b2.equals("dumpsys")) return true;
            if (b2.equalsIgnoreCase("dumpsys -h")) return true;
            if (b2.equalsIgnoreCase("dumpsys --help")) return true;
            // Some devices accept "dumpsys help".
            if (b2.equalsIgnoreCase("dumpsys help")) return true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPmHelpCommand(String base) {
        try {
            if (base == null) return false;
            String b2 = base.trim();
            if (b2.equals("pm")) return true;
            if (b2.equalsIgnoreCase("pm help")) return true;
            if (b2.equalsIgnoreCase("pm -h")) return true;
            if (b2.equalsIgnoreCase("pm --help")) return true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLogcatHelpCommand(String base) {
        try {
            if (base == null) return false;
            String b2 = base.trim();
            if (b2.equalsIgnoreCase("logcat -h")) return true;
            if (b2.equalsIgnoreCase("logcat --help")) return true;
            if (b2.equals("logcat")) return true; // logcat with no args typically prints help/usage
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSettingsCommand(String base) {
        try {
            if (base == null) return false;
            String b2 = base.trim();
            if (b2.equals("settings")) return true;
            if (b2.equalsIgnoreCase("settings -h")) return true;
            if (b2.equalsIgnoreCase("settings --help")) return true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

private static boolean isAdbHelpCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        if (b2.equalsIgnoreCase("adb")) return true;
        if (b2.equalsIgnoreCase("adb -h")) return true;
        if (b2.equalsIgnoreCase("adb --help")) return true;
        return false;
    } catch (Throwable ignored) {
        return false;
    }
}


    private static boolean isClearHelpCommand(String base) {
        try {
            if (base == null) return false;
            String b2 = base.trim();
            if (b2.equalsIgnoreCase("clear")) return true;
            if (b2.equalsIgnoreCase("clear -h")) return true;
            if (b2.equalsIgnoreCase("clear --help")) return true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isFreeHelpCommand(String base) {
        try {
            if (base == null) return false;
            String b2 = base.trim();
            if (b2.equalsIgnoreCase("free")) return true;
            if (b2.equalsIgnoreCase("free -h")) return true;
            if (b2.equalsIgnoreCase("free --help")) return true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isVmstatHelpCommand(String base) {
        try {
            if (base == null) return false;
            String b2 = base.trim();
            if (b2.equalsIgnoreCase("vmstat")) return true;
            if (b2.equalsIgnoreCase("vmstat -h")) return true;
            if (b2.equalsIgnoreCase("vmstat --help")) return true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isWatchHelpCommand(String base) {
        try {
            if (base == null) return false;
            String b2 = base.trim();
            if (b2.equalsIgnoreCase("watch")) return true;
            if (b2.equalsIgnoreCase("watch -h")) return true;
            if (b2.equalsIgnoreCase("watch --help")) return true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
private static boolean isRebootHelpCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        // Always treat the help forms as the grouped entry point.
        if (b2.equalsIgnoreCase("reboot -h")) return true;
        if (b2.equalsIgnoreCase("reboot --help")) return true;
        return false;
    } catch (Throwable ignored) {
        return false;
    }
}

    

private static boolean isMd5sumCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        String lo = b2.toLowerCase(Locale.US);
        return lo.equals("md5sum") || lo.startsWith("md5sum ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isLspciCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        String lo = b2.toLowerCase(Locale.US);
        return lo.equals("lspci") || lo.startsWith("lspci ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isLsusbCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        String lo = b2.toLowerCase(Locale.US);
        return lo.equals("lsusb") || lo.startsWith("lsusb ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isNetcatCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("netcat") || lo.startsWith("netcat ") || lo.equals("nc") || lo.startsWith("nc ");
    } catch (Throwable ignored) {
        return false;
    }
}


private static boolean isBase64Command(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("base64") || lo.startsWith("base64 ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isCurlCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("curl") || lo.startsWith("curl ");
    } catch (Throwable ignored) {
        return false;
    }
}



private static boolean isDos2unixCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("dos2unix") || lo.startsWith("dos2unix ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isTtyCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("tty") || lo.startsWith("tty ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isUmountCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("umount") || lo.startsWith("umount ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isUnix2dosCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("unix2dos") || lo.startsWith("unix2dos ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isIpCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("ip") || lo.startsWith("ip ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isPingCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("ping") || lo.startsWith("ping ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isSha256sumCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("sha256sum") || lo.startsWith("sha256sum ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isXxdCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("xxd") || lo.startsWith("xxd ");
    } catch (Throwable ignored) {
        return false;
    }
}


private static boolean isYesCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("yes") || lo.startsWith("yes ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isZcatCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("zcat") || lo.startsWith("zcat ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isZipinfoCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("zipinfo") || lo.startsWith("zipinfo ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isZiptoolCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("ziptool") || lo.startsWith("ziptool ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isReadelfCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        String lo = b2.toLowerCase(Locale.US);
        return lo.equals("readelf") || lo.startsWith("readelf ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isRunAsCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        String lo = b2.toLowerCase(Locale.US);
        return lo.equals("run-as") || lo.startsWith("run-as ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isScreencapCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        String lo = b2.toLowerCase(Locale.US);
        return lo.equals("screencap") || lo.startsWith("screencap ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isToyboxCommand(String base) {
    try {
        if (base == null) return false;
        String b2 = base.trim();
        String lo = b2.toLowerCase(Locale.US);
        return lo.equals("toybox") || lo.startsWith("toybox ");
    } catch (Throwable ignored) {
        return false;
    }
}


private static boolean isGzipCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("gzip") || lo.startsWith("gzip ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isGunzipCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("gunzip") || lo.startsWith("gunzip ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isSetenforceCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("setenforce") || lo.startsWith("setenforce ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isScreenrecordCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("screenrecord") || lo.startsWith("screenrecord ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isStartCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("start") || lo.startsWith("start ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isSysctlCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("sysctl") || lo.startsWith("sysctl ");
    } catch (Throwable ignored) {
        return false;
    }

}
private static boolean isFastbootCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("fastboot") || lo.startsWith("fastboot ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isTcpdumpCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("tcpdump") || lo.startsWith("tcpdump ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isGeteventCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("getevent") || lo.startsWith("getevent ");
    } catch (Throwable ignored) {
        return false;
    }
}

private static boolean isLsofCommand(String base) {
    try {
        if (base == null) return false;
        String lo = base.trim().toLowerCase(Locale.US);
        return lo.equals("lsof") || lo.startsWith("lsof ");
    } catch (Throwable ignored) {
        return false;
    }
}






    public static String defaultHelpCommand(String base) {
        try {
            if (base == null) return "";
            String b2 = base.trim();
            if (b2.isEmpty()) return "";

            String lc = b2.toLowerCase(Locale.US);
            if (lc.contains(" --help") || lc.endsWith("--help")) return b2;
            if (lc.endsWith(" -h") || lc.endsWith("	-h")) return b2;
            if (lc.endsWith(" help")) return b2;

            if (isDumpsysHelpCommand(b2)) return "dumpsys -h";
            if (b2.equals("dumpsys")) return "dumpsys -h";
            if (b2.equals("logcat")) return "logcat -h";
            if (b2.equals("pm")) return "pm help";
            if (b2.equals("settings")) return "settings";
            return b2 + " --help";
        } catch (Throwable ignored) {
            return (base == null) ? "" : base;
        }
    }
}
