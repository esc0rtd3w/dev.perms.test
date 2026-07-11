package dev.perms.test.memory;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.text.TextUtils;

import dev.perms.test.ExecMode;
import dev.perms.test.ladb.LadbClient;
import dev.perms.test.ShizukuCompat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import rikka.shizuku.Shizuku;

public final class MemoryToolRuntime {
    public static final String PREFS = "perms_test";
    public static final String PUBLIC_TMP_ROOT = "/data/local/tmp/dev.perms.test";
    public static final String PUBLIC_BIN_DIR = PUBLIC_TMP_ROOT + "/bin";

    private static final String BUNDLED_ASSET_DIR = "bin";
    private static final String[] STANDARD_ABI_ASSET_DIRS = new String[]{"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};

    private MemoryToolRuntime() {
    }

    public static final class CmdResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        CmdResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    public static List<MemoryPackageEntry> listTargetPackages(Context context) {
        return listTargetPackages(context, true);
    }

    public static List<MemoryPackageEntry> listTargetPackages(Context context, boolean includeRunningState) {
        ArrayList<MemoryPackageEntry> out = new ArrayList<>();
        if (context == null) {
            return out;
        }

        HashSet<String> runningPkgs = new HashSet<>();
        if (includeRunningState) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> running = am == null ? null : am.getRunningAppProcesses();
                if (running != null) {
                    for (ActivityManager.RunningAppProcessInfo info : running) {
                        if (info == null) continue;
                        try {
                            addPackageName(runningPkgs, info.processName);
                        } catch (Throwable ignored) {
                        }
                        try {
                            if (info.pkgList != null) {
                                for (String pkg : info.pkgList) {
                                    addPackageName(runningPkgs, pkg);
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                runningPkgs.addAll(listRunningPackagesViaShell(context));
            } catch (Throwable ignored) {
            }
        }

        Set<String> shellInstalledPkgs = new HashSet<>();
        if (includeRunningState) {
            try {
                shellInstalledPkgs.addAll(listInstalledPackagesViaShell(context));
            } catch (Throwable ignored) {
            }
        }

        Set<String> shellDebuggablePkgs = new HashSet<>();
        if (includeRunningState) {
            try {
                shellDebuggablePkgs.addAll(listDebuggablePackagesViaShell(context));
            } catch (Throwable ignored) {
            }
        }

        Set<String> launcherPkgs = new HashSet<>();
        try {
            launcherPkgs.addAll(listLauncherPackages(context));
        } catch (Throwable ignored) {
        }

        PackageManager pm = context.getPackageManager();
        HashSet<String> candidates = new HashSet<>();

        try {
            int flags = packageListFlags();
            List<ApplicationInfo> installed = pm == null ? null : pm.getInstalledApplications(flags);
            if (installed != null) {
                for (ApplicationInfo ai : installed) {
                    if (ai == null) continue;
                    addPackageName(candidates, ai.packageName);
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            int flags = packageListFlags();
            List<PackageInfo> installedPackages = pm == null ? null : pm.getInstalledPackages(flags);
            if (installedPackages != null) {
                for (PackageInfo pi : installedPackages) {
                    if (pi == null) continue;
                    addPackageName(candidates, pi.packageName);
                }
            }
        } catch (Throwable ignored) {
        }

        for (String pkg : launcherPkgs) {
            addPackageName(candidates, pkg);
        }
        for (String pkg : shellInstalledPkgs) {
            addPackageName(candidates, pkg);
        }
        for (String pkg : runningPkgs) {
            addPackageName(candidates, pkg);
        }

        for (String pkg : candidates) {
            if (TextUtils.isEmpty(pkg)) continue;
            ApplicationInfo ai = getApplicationInfo(pm, pkg);
            boolean debuggable = shellDebuggablePkgs.contains(pkg) || isApplicationDebuggable(ai) || isPackageDebuggable(pm, pkg);
            out.add(new MemoryPackageEntry(resolvePackageLabel(pm, pkg, ai), pkg, runningPkgs.contains(pkg), debuggable));
        }

        Collections.sort(out, new Comparator<MemoryPackageEntry>() {
            @Override
            public int compare(MemoryPackageEntry a, MemoryPackageEntry b) {
                String as = a == null ? "" : (TextUtils.isEmpty(a.label) ? a.pkg : a.label);
                String bs = b == null ? "" : (TextUtils.isEmpty(b.label) ? b.pkg : b.label);
                return as.toLowerCase(Locale.ROOT).compareTo(bs.toLowerCase(Locale.ROOT));
            }
        });
        return out;
    }

    static int packageListFlags() {
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags |= PackageManager.MATCH_DISABLED_COMPONENTS;
            flags |= PackageManager.MATCH_DIRECT_BOOT_AWARE;
            flags |= PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        }
        return flags;
    }

    static ApplicationInfo getApplicationInfo(PackageManager pm, String pkg) {
        if (pm == null || TextUtils.isEmpty(pkg)) return null;
        try {
            return pm.getApplicationInfo(pkg, packageListFlags());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addPackageName(Set<String> out, String raw) {
        if (out == null) return;
        String pkg = cleanPackageName(raw);
        if (!TextUtils.isEmpty(pkg)) {
            out.add(pkg);
        }
    }

    private static String cleanPackageName(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.startsWith("package:")) {
            value = value.substring("package:".length()).trim();
        }
        int colon = value.indexOf(':');
        if (colon > 0) {
            String base = value.substring(0, colon).trim();
            if (!TextUtils.isEmpty(base)) value = base;
        }
        int space = value.indexOf(' ');
        if (space > 0) {
            value = value.substring(0, space).trim();
        }
        if (TextUtils.isEmpty(value) || !value.contains(".")) return "";
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '.';
            if (!ok) return "";
        }
        return value;
    }

    public static boolean isApplicationDebuggable(ApplicationInfo ai) {
        try {
            return ai != null && (ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPackageDebuggable(PackageManager pm, String pkg) {
        if (pm == null || TextUtils.isEmpty(pkg)) return false;
        try {
            return isApplicationDebuggable(pm.getApplicationInfo(pkg, 0));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String resolvePackageLabel(PackageManager pm, String pkg) {
        if (pm == null || TextUtils.isEmpty(pkg)) return pkg == null ? "" : pkg;
        try {
            return resolvePackageLabel(pm, pkg, pm.getApplicationInfo(pkg, 0));
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    static String resolvePackageLabel(PackageManager pm, String pkg, ApplicationInfo ai) {
        if (pm == null || ai == null) return pkg == null ? "" : pkg;
        try {
            CharSequence label = pm.getApplicationLabel(ai);
            return label == null ? pkg : label.toString();
        } catch (Throwable ignored) {
            return pkg == null ? "" : pkg;
        }
    }

    private static Set<String> listDebuggablePackagesViaShell(Context context) {
        HashSet<String> out = new HashSet<>();
        if (context == null) return out;
        StringBuilder script = new StringBuilder();
        script.append("dumpsys package packages 2>/dev/null | sed -n '");
        script.append("/^  Package \\[/ {s/^  Package \\[//;s/\\].*//;h};");
        script.append("/pkgFlags=.*DEBUGGABLE/ {g;p};/privateFlags=.*DEBUGGABLE/ {g;p}' | sort -u");
        CmdResult r = runShellCommandCaptureSync(context, script.toString());
        if (r == null || (r.exitCode != 0 && TextUtils.isEmpty(r.stdout))) return out;
        String[] lines = (r.stdout == null ? "" : r.stdout).split("\n");
        for (String line : lines) {
            addPackageName(out, line);
        }
        return out;
    }

    private static Set<String> listLauncherPackages(Context context) {
        HashSet<String> out = new HashSet<>();
        if (context == null) return out;
        try {
            PackageManager pm = context.getPackageManager();
            if (pm == null) return out;
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            if (activities != null) {
                for (ResolveInfo ri : activities) {
                    if (ri == null || ri.activityInfo == null) continue;
                    addPackageName(out, ri.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Set<String> listInstalledPackagesViaShell(Context context) {
        HashSet<String> out = new HashSet<>();
        if (context == null) {
            return out;
        }
        StringBuilder script = new StringBuilder();
        script.append("{ ");
        script.append("cmd package list packages 2>/dev/null || true; ");
        script.append("cmd package list packages --user 0 2>/dev/null || true; ");
        script.append("cmd package list packages -3 2>/dev/null || true; ");
        script.append("cmd package list packages -3 --user 0 2>/dev/null || true; ");
        script.append("cmd package list packages -d --user 0 2>/dev/null || true; ");
        script.append("cmd package list packages -e --user 0 2>/dev/null || true; ");
        script.append("pm list packages 2>/dev/null || true; ");
        script.append("pm list packages --user 0 2>/dev/null || true; ");
        script.append("pm list packages -3 2>/dev/null || true; ");
        script.append("pm list packages -d 2>/dev/null || true; ");
        script.append("pm list packages -e 2>/dev/null || true; ");
        script.append("pm list packages -a 2>/dev/null || true; ");
        script.append("} | sed 's/^package://' | awk '{print $1}' | sort -u");
        CmdResult r = runShellCommandCaptureSync(context, script.toString());
        String stdout = r == null || r.stdout == null ? "" : r.stdout;
        String[] lines = stdout.split("\n");
        for (String line : lines) {
            addPackageName(out, line);
        }
        return out;
    }

    private static Set<String> listRunningPackagesViaShell(Context context) {
        HashSet<String> out = new HashSet<>();
        if (context == null) {
            return out;
        }

        StringBuilder script = new StringBuilder();
        script.append("{ ");
        script.append("for f in /proc/[0-9]*/cmdline; do ");
        script.append("  [ -r \"$f\" ] || continue; ");
        script.append("  cmd=$(tr '\\000' ' ' <\"$f\" 2>/dev/null); ");
        script.append("  [ -z \"$cmd\" ] && continue; ");
        script.append("  set -- $cmd; first=$1; ");
        script.append("  [ -z \"$first\" ] && continue; ");
        script.append("  case \"$first\" in *.*) base=${first%%:*}; echo \"$base\" ;; esac; ");
        script.append("done; ");
        script.append("if command -v ps >/dev/null 2>&1; then ");
        script.append("  ps -A -o NAME= 2>/dev/null | while read name; do ");
        script.append("    [ -z \"$name\" ] && continue; first=${name%% *}; case \"$first\" in *.*) echo ${first%%:*} ;; esac; ");
        script.append("  done; ");
        script.append("fi; ");
        script.append("} | sort -u");

        CmdResult r = runShellCommandCaptureSync(context, script.toString());
        String stdout = r.stdout == null ? "" : r.stdout;
        String[] lines = stdout.split("\n");
        for (String line : lines) {
            addPackageName(out, line);
        }
        return out;
    }

    public static CmdResult ensureBundledBinaryPublicForCurrentMode(Context context, String name) {
        try {
            if (context == null || TextUtils.isEmpty(name)) {
                return new CmdResult(1, "", "Binary name is empty.");
            }
            String dst = PUBLIC_BIN_DIR + "/" + name;
            if (!hasBundledAsset(context, name)) {
                return new CmdResult(1, "", "Bundled binary not found for this ABI: " + name);
            }

            File stageDir = getBundledStageDir(context);
            if (stageDir == null) {
                return new CmdResult(1, "", "Unable to access staged binary directory.");
            }
            if (!stageDir.exists() && !stageDir.mkdirs()) {
                return new CmdResult(1, "", "Unable to create staged binary directory.");
            }

            File stage = new File(stageDir, name);
            // Always refresh the private staged copy from the APK asset before publishing it.
            // The overlay runs outside MainActivity and must not reuse a stale public medit
            // binary left over from an earlier APK or manual staging pass.
            try (InputStream in = new BufferedInputStream(openBundledAsset(context, name));
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(stage, false))) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                }
                out.flush();
            }
            try { stage.setReadable(true, false); } catch (Throwable ignored) {}
            try { stage.setExecutable(true, false); } catch (Throwable ignored) {}

            String tmpDst = dst + ".new." + Long.toHexString(System.nanoTime());
            String cmd = "mkdir -p " + shQuote(PUBLIC_BIN_DIR)
                    + " && chmod 777 " + shQuote(PUBLIC_TMP_ROOT)
                    + " && cp " + shQuote(stage.getAbsolutePath()) + " " + shQuote(tmpDst)
                    + " && chmod 755 " + shQuote(tmpDst)
                    + " && mv -f " + shQuote(tmpDst) + " " + shQuote(dst);
            return runShellCommandCaptureSync(context, cmd);
        } catch (Throwable t) {
            return new CmdResult(1, "", "Bundled binary install failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static List<MemoryProcessEntry> listTargetProcesses(Context context, String packageName) {
        ArrayList<MemoryProcessEntry> out = new ArrayList<>();
        if (context == null || TextUtils.isEmpty(packageName)) {
            return out;
        }
        String pkg = packageName.trim();
        if (pkg.isEmpty()) {
            return out;
        }

        StringBuilder script = new StringBuilder();
        script.append("pkg=").append(shQuote(pkg)).append("; ");
        script.append("seen=' '; ");
        script.append("if command -v pidof >/dev/null 2>&1; then ");
        script.append("  for pid in $(pidof \"$pkg\" 2>/dev/null || true); do ");
        script.append("    [ -z \"$pid\" ] && continue; ");
        script.append("    case \" $seen \" in *\" $pid \"*) continue ;; esac; ");
        script.append("    cmd=$(tr '\\000' ' ' </proc/$pid/cmdline 2>/dev/null); ");
        script.append("    [ -z \"$cmd\" ] && cmd=$(cat /proc/$pid/comm 2>/dev/null); ");
        script.append("    seen=\"$seen$pid \"; echo \"$pid|$cmd\"; ");
        script.append("  done; ");
        script.append("fi; ");
        script.append("if [ \"$seen\" = ' ' ]; then ");
        script.append("  for f in /proc/[0-9]*/cmdline; do ");
        script.append("    [ -r \"$f\" ] || continue; ");
        script.append("    pid=${f#/proc/}; pid=${pid%/cmdline}; ");
        script.append("    cmd=$(tr '\\000' ' ' <\"$f\" 2>/dev/null); ");
        script.append("    [ -z \"$cmd\" ] && cmd=$(cat /proc/$pid/comm 2>/dev/null); ");
        script.append("    [ -z \"$cmd\" ] && continue; ");
        script.append("    case \"$cmd\" in \"$pkg\"|\"$pkg \"*|\"$pkg:\"*) ");
        script.append("      case \" $seen \" in *\" $pid \"*) continue ;; esac; ");
        script.append("      seen=\"$seen$pid \"; echo \"$pid|$cmd\"; ;; ");
        script.append("    esac; ");
        script.append("  done; ");
        script.append("fi");

        CmdResult r = runShellCommandCaptureSync(context, script.toString());
        if (r.exitCode != 0 && TextUtils.isEmpty(r.stdout)) {
            return out;
        }

        String[] lines = (r.stdout == null ? "" : r.stdout).split("\n");
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;
            int sep = t.indexOf('|');
            String pid = sep >= 0 ? t.substring(0, sep).trim() : t;
            String name = sep >= 0 ? t.substring(sep + 1).trim() : "";
            if (pid.isEmpty()) continue;
            out.add(new MemoryProcessEntry(pid, name));
        }
        return out;
    }


    public static String resolveTargetPidFast(Context context, String packageName, String preferredPid) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return preferredPid == null ? "" : preferredPid.trim();
        }
        String pkg = packageName.trim();
        String pref = preferredPid == null ? "" : preferredPid.trim();
        StringBuilder script = new StringBuilder();
        script.append("pkg=").append(shQuote(pkg)).append("; ");
        script.append("pref=").append(shQuote(pref)).append("; ");
        script.append("if [ -n \"$pref\" ] && [ -d \"/proc/$pref\" ]; then ");
        script.append("  cmd=$(tr '\\000' ' ' </proc/$pref/cmdline 2>/dev/null); ");
        script.append("  case \"$cmd\" in \"$pkg\"|\"$pkg \"*|\"$pkg:\"*) echo \"$pref\"; exit 0 ;; esac; ");
        script.append("fi; ");
        script.append("if command -v pidof >/dev/null 2>&1; then ");
        script.append("  for pid in $(pidof \"$pkg\" 2>/dev/null || true); do [ -n \"$pid\" ] && echo \"$pid\" && exit 0; done; ");
        script.append("fi");
        CmdResult r = runShellCommandCaptureSync(context, script.toString());
        String stdout = r == null || r.stdout == null ? "" : r.stdout.trim();
        if (TextUtils.isEmpty(stdout)) return "";
        int nl = stdout.indexOf('\n');
        return (nl >= 0 ? stdout.substring(0, nl) : stdout).trim();
    }

    public static String resolveTargetPid(Context context, String packageName, String preferredPid) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return preferredPid == null ? "" : preferredPid.trim();
        }
        String pkg = packageName.trim();
        String pref = preferredPid == null ? "" : preferredPid.trim();
        StringBuilder script = new StringBuilder();
        script.append("pkg=").append(shQuote(pkg)).append("; ");
        script.append("pref=").append(shQuote(pref)).append("; ");
        script.append("if [ -n \"$pref\" ] && [ -d \"/proc/$pref\" ]; then ");
        script.append("  cmd=$(tr '\\000' ' ' </proc/$pref/cmdline 2>/dev/null); ");
        script.append("  case \"$cmd\" in \"$pkg\"|\"$pkg \"*|\"$pkg:\"*) echo \"$pref\"; exit 0 ;; esac; ");
        script.append("fi; ");
        script.append("if command -v pidof >/dev/null 2>&1; then ");
        script.append("  for pid in $(pidof \"$pkg\" 2>/dev/null || true); do [ -n \"$pid\" ] && echo \"$pid\" && exit 0; done; ");
        script.append("fi; ");
        script.append("for f in /proc/[0-9]*/cmdline; do ");
        script.append("  [ -r \"$f\" ] || continue; ");
        script.append("  pid=${f#/proc/}; pid=${pid%/cmdline}; ");
        script.append("  cmd=$(tr '\\000' ' ' <\"$f\" 2>/dev/null); ");
        script.append("  case \"$cmd\" in \"$pkg\"|\"$pkg \"*|\"$pkg:\"*) echo \"$pid\"; exit 0 ;; esac; ");
        script.append("done");
        CmdResult r = runShellCommandCaptureSync(context, script.toString());
        String stdout = r == null || r.stdout == null ? "" : r.stdout.trim();
        if (TextUtils.isEmpty(stdout)) {
            return pref;
        }
        int nl = stdout.indexOf('\n');
        return (nl >= 0 ? stdout.substring(0, nl) : stdout).trim();
    }

    public static CmdResult runShellCommandCaptureSync(Context context, String cmd) {
        if (context == null) return new CmdResult(-1, "", "Context is null");
        if (TextUtils.isEmpty(cmd)) return new CmdResult(-1, "", "Command is empty");
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            ExecMode mode = ExecMode.get(prefs);
            String trimmed = cmd.trim();

            if (mode == ExecMode.LADB) {
                LadbClient ladbClient = new LadbClient(context.getApplicationContext());
                int port = prefs.getInt(ExecMode.PREF_KEY_LADB_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT);
                LadbClient.CmdResult connect = ladbClient.connect(LadbClient.DEFAULT_HOST, port);
                String connectCombined = ((connect.stdout == null ? "" : connect.stdout) + "\n" + (connect.stderr == null ? "" : connect.stderr)).toLowerCase();
                boolean connected = connect.exitCode == 0 && (connectCombined.contains("connected") || connectCombined.contains("already connected"));
                if (!connected) {
                    return new CmdResult(1, connect.stdout, TextUtils.isEmpty(connect.stderr) ? "LADB not connected." : connect.stderr);
                }
                LadbClient.CmdResult r;
                if (trimmed.startsWith("adb ")) {
                    r = ladbClient.rawAdb(LadbClient.tokenizeAdbArgs(trimmed));
                } else {
                    r = ladbClient.shellShC(trimmed);
                }
                return new CmdResult(r.exitCode, r.stdout, r.stderr);
            }

            if (mode == ExecMode.SYSTEM) {
                Process p = new ProcessBuilder("sh", "-c", trimmed).redirectErrorStream(false).start();
                String out = readStream(p.getInputStream());
                String err = readStream(p.getErrorStream());
                int exit = p.waitFor();
                return new CmdResult(exit, out, err);
            }

            boolean binderAlive = false;
            try { binderAlive = Shizuku.pingBinder(); } catch (Throwable ignored) { binderAlive = false; }
            if (!binderAlive) return new CmdResult(1, "", "Shizuku binder is not available.");
            try {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return new CmdResult(1, "", "Shizuku permission is not granted.");
                }
            } catch (Throwable ignored) {
                return new CmdResult(1, "", "Shizuku permission check failed.");
            }

            Process p = ShizukuCompat.newProcess(new String[]{"sh", "-c", trimmed}, null, null);
            String out = readStream(p.getInputStream());
            String err = readStream(p.getErrorStream());
            int exit = p.waitFor();
            return new CmdResult(exit, out, err);
        } catch (Throwable t) {
            return new CmdResult(1, "", t.toString());
        }
    }

    private static boolean hasBundledAsset(Context context, String name) {
        return !TextUtils.isEmpty(resolveBundledAssetPath(context, name));
    }

    private static InputStream openBundledAsset(Context context, String name) throws IOException {
        String assetPath = resolveBundledAssetPath(context, name);
        if (TextUtils.isEmpty(assetPath)) {
            throw new IOException("Bundled asset not found: " + name);
        }
        return context.getAssets().open(assetPath);
    }

    private static File getBundledStageDir(Context context) {
        File root = null;
        try {
            root = context.getExternalFilesDir(null);
        } catch (Throwable ignored) {
        }
        if (root == null) root = context.getFilesDir();
        if (root == null) root = context.getCacheDir();
        if (root == null) return null;
        File d = new File(root, "bundled_bin_stage");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String resolveBundledAssetPath(Context context, String name) {
        if (context == null || TextUtils.isEmpty(name)) return null;
        List<String> candidates = new ArrayList<>();
        try {
            String[] supported = Build.SUPPORTED_ABIS;
            if (supported != null) {
                for (String abi : supported) {
                    String mapped = mapAbiToAssetSubdir(abi);
                    if (!TextUtils.isEmpty(mapped) && !candidates.contains(mapped)) {
                        candidates.add(mapped);
                    }
                }
            }
        } catch (Throwable ignored) {}
        for (String std : STANDARD_ABI_ASSET_DIRS) {
            if (!candidates.contains(std)) candidates.add(std);
        }
        for (String dir : candidates) {
            if (assetExists(context, BUNDLED_ASSET_DIR + "/" + dir, name)) {
                return BUNDLED_ASSET_DIR + "/" + dir + "/" + name;
            }
        }
        if (assetExists(context, BUNDLED_ASSET_DIR, name)) {
            return BUNDLED_ASSET_DIR + "/" + name;
        }
        return null;
    }

    private static boolean assetExists(Context context, String assetDir, String name) {
        try {
            String[] names = context.getAssets().list(assetDir);
            if (names == null || names.length == 0) return false;
            for (String n : names) {
                if (name.equals(n)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String mapAbiToAssetSubdir(String abi) {
        if (TextUtils.isEmpty(abi)) return null;
        for (String d : STANDARD_ABI_ASSET_DIRS) {
            if (d.equalsIgnoreCase(abi)) return d;
        }
        if (abi.startsWith("arm64")) return "arm64-v8a";
        if (abi.startsWith("armeabi") || abi.startsWith("arm")) return "armeabi-v7a";
        if (abi.startsWith("x86_64")) return "x86_64";
        if (abi.startsWith("x86")) return "x86";
        return null;
    }

    private static String readStream(InputStream in) {
        if (in == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String shQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
