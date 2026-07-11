package dev.perms.test.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Shell command builder for App Tray package backup and restore operations. */
final class AppTrayBackupCommands {
    private static final String BACKUP_DIR = "/sdcard/dev.perms.test/backups";
    private static final String BACKUP_ARCHIVE = "backup.ab";

    private AppTrayBackupCommands() {
    }

    @NonNull
    static String backupPath(@Nullable String packageName) {
        return packageBackupDir(packageName) + "/" + BACKUP_ARCHIVE;
    }

    @NonNull
    static String backupCommand(@Nullable String packageName) {
        final String pkg = sanitizePackageName(packageName);
        final String pkgDir = packageBackupDir(pkg);
        final String out = pkgDir + "/" + BACKUP_ARCHIVE;
        final String tmp = out + ".tmp";
        final String apkDir = pkgDir + "/apks";
        final String apkPaths = pkgDir + "/apk_paths.txt";
        final String dataCeTar = pkgDir + "/data_ce.tar";
        final String dataDeTar = pkgDir + "/data_de.tar";
        final String meta = pkgDir + "/metadata.txt";
        final String buErr = pkgDir + "/bu.err";
        final String runAsErr = pkgDir + "/run_as.err";

        return "pkg=" + shellQuote(pkg) + "; pkgdir=" + shellQuote(pkgDir)
                + "; out=" + shellQuote(out) + "; tmp=" + shellQuote(tmp)
                + "; apkdir=" + shellQuote(apkDir)
                + "; apkpaths=" + shellQuote(apkPaths) + "; datacetar=" + shellQuote(dataCeTar)
                + "; datadetar=" + shellQuote(dataDeTar)
                + "; meta=" + shellQuote(meta) + "; buerr=" + shellQuote(buErr)
                + "; runaserr=" + shellQuote(runAsErr) + "; "
                + "if [ -z \"$pkg\" ]; then echo 'Invalid package name' >&2; exit 1; fi; "
                + "mkdir -p " + shellQuote(BACKUP_DIR) + " \"$pkgdir\" \"$apkdir\" || exit 1; "
                + "rm -f \"$tmp\" \"$out\" \"$datacetar\" \"$datadetar\" \"$buerr\" \"$runaserr\" \"$apkpaths.err\"; "
                + "{ echo 'package='\"$pkg\"; date '+created=%Y-%m-%d %H:%M:%S %z' 2>/dev/null || true; } > \"$meta\"; "
                + "echo 'Collecting package APK paths...'; "
                + "if pm path \"$pkg\" > \"$apkpaths\" 2>\"$apkpaths.err\"; then echo 'APK path list saved: ' \"$apkpaths\"; "
                + "elif cmd package path \"$pkg\" > \"$apkpaths\" 2>>\"$apkpaths.err\"; then echo 'APK path list saved: ' \"$apkpaths\"; "
                + "else echo 'Warning: package path lookup failed for ' \"$pkg\" >&2; if [ -s \"$apkpaths.err\" ]; then cat \"$apkpaths.err\" >&2; fi; fi; "
                + "rm -rf \"$apkdir\"; mkdir -p \"$apkdir\"; copied_apks=0; "
                + "while IFS= read -r line; do apk=${line#package:}; "
                + "if [ -z \"$apk\" ]; then continue; fi; "
                + "if [ -r \"$apk\" ]; then name=${apk##*/}; if cp -f \"$apk\" \"$apkdir/$name\" 2>>\"$runaserr\"; then copied_apks=$((copied_apks + 1)); "
                + "else echo 'Warning: failed to copy APK: ' \"$apk\" >&2; fi; "
                + "else echo 'Warning: APK path is not readable from this shell: ' \"$apk\" >&2; fi; "
                + "done < \"$apkpaths\"; echo 'Readable APK files copied: ' \"$copied_apks\"; "
                + "echo 'Trying run-as direct data backup...'; "
                + "run_as_ready=0; if run-as \"$pkg\" sh -c 'id >/dev/null' 2>\"$runaserr\"; then run_as_ready=1; fi; "
                + "if [ \"$run_as_ready\" = 1 ]; then "
                + "ce_saved=0; de_saved=0; "
                + "if run-as \"$pkg\" sh -c \"cd /data/data/$pkg && tar -cf - .\" > \"$datacetar\" 2>>\"$runaserr\"; then if [ -s \"$datacetar\" ]; then ce_saved=1; fi; fi; "
                + "if run-as \"$pkg\" sh -c \"if [ -d /data/user_de/0/$pkg ]; then cd /data/user_de/0/$pkg && tar -cf - .; fi\" > \"$datadetar\" 2>>\"$runaserr\"; then if [ -s \"$datadetar\" ]; then de_saved=1; fi; fi; "
                + "if [ \"$ce_saved$de_saved\" != 00 ]; then "
                + "chmod 0666 \"$datacetar\" \"$datadetar\" \"$meta\" \"$apkpaths\" 2>/dev/null || true; "
                + "cebytes=$(wc -c < \"$datacetar\" 2>/dev/null || echo 0); debytes=$(wc -c < \"$datadetar\" 2>/dev/null || echo 0); "
                + "echo 'run-as data backup saved: ' \"$pkgdir\" ' (ce='\"$cebytes\"' bytes, de='\"$debytes\"' bytes)'; exit 0; fi; "
                + "echo 'run-as was available but produced no data; trying Android backup UI fallback.' >&2; "
                + "if [ -s \"$runaserr\" ]; then cat \"$runaserr\" >&2; fi; rm -f \"$datacetar\" \"$datadetar\"; "
                + "else echo 'run-as is not available; trying Android backup UI fallback.' >&2; if [ -s \"$runaserr\" ]; then cat \"$runaserr\" >&2; fi; fi; "
                + "save_bu_archive() { if [ -s \"$tmp\" ]; then mv -f \"$tmp\" \"$out\" || return 1; chmod 0666 \"$out\" 2>/dev/null || true; bytes=$(wc -c < \"$out\" 2>/dev/null || echo 0); echo 'Backup saved: ' \"$out\" ' ('\"$bytes\"' bytes)'; return 0; fi; return 1; }; "
                + "try_bu_backup_file() { label=\"$1\"; shift; rm -f \"$tmp\" \"$buerr\"; echo 'Trying Android backup tool: ' \"$label\"; echo 'Confirm the Android backup screen when it appears.'; \"$@\" 2>\"$buerr\"; code=$?; if save_bu_archive; then exit 0; fi; rm -f \"$tmp\"; if [ \"$code\" -ne 0 ]; then echo 'Android backup tool exit code: ' \"$code\" >&2; fi; if [ -s \"$buerr\" ]; then cat \"$buerr\" >&2; fi; return 1; }; "
                + "try_bu_backup() { label=\"$1\"; shift; rm -f \"$tmp\" \"$buerr\"; echo 'Trying Android backup tool: ' \"$label\"; echo 'Confirm the Android backup screen when it appears.'; \"$@\" > \"$tmp\" 2>\"$buerr\"; code=$?; if save_bu_archive; then exit 0; fi; rm -f \"$tmp\"; if [ \"$code\" -ne 0 ]; then echo 'Android backup tool exit code: ' \"$code\" >&2; fi; if [ -s \"$buerr\" ]; then cat \"$buerr\" >&2; fi; return 1; }; "
                + "if command -v bu >/dev/null 2>&1; then "
                + "try_bu_backup_file 'package data and apk file output' bu backup -user 0 -f \"$tmp\" -apk -obb \"$pkg\"; "
                + "try_bu_backup 'package data' bu backup \"$pkg\"; "
                + "try_bu_backup 'package data with user 0' bu backup -user 0 \"$pkg\"; "
                + "try_bu_backup 'package data and apk' bu backup -apk -obb \"$pkg\"; "
                + "try_bu_backup 'package data and apk with user 0' bu backup -user 0 -apk -obb \"$pkg\"; "
                + "try_bu_backup 'key-value fallback' bu backup -keyvalue \"$pkg\"; "
                + "try_bu_backup 'key-value fallback with user 0' bu backup -user 0 -keyvalue \"$pkg\"; "
                + "echo 'Android backup tool did not produce a usable archive.' >&2; "
                + "else echo 'bu binary not found.' >&2; fi; "
                + "echo 'APK metadata was saved, but private app data was not backed up: ' \"$pkgdir\" >&2; "
                + "exit 1";
    }

    @NonNull
    static String restoreCommand(@Nullable String packageName) {
        final String pkg = sanitizePackageName(packageName);
        final String in = backupPath(pkg);
        final String pkgDir = packageBackupDir(pkg);
        final String dataCeTar = pkgDir + "/data_ce.tar";
        final String dataDeTar = pkgDir + "/data_de.tar";
        final String buErr = pkgDir + "/restore_bu.err";
        final String runAsErr = pkgDir + "/restore_run_as.err";
        return "pkg=" + shellQuote(pkg) + "; in=" + shellQuote(in)
                + "; pkgdir=" + shellQuote(pkgDir)
                + "; datacetar=" + shellQuote(dataCeTar) + "; datadetar=" + shellQuote(dataDeTar)
                + "; buerr=" + shellQuote(buErr)
                + "; runaserr=" + shellQuote(runAsErr) + "; "
                + "if [ -z \"$pkg\" ]; then echo 'Invalid package name' >&2; exit 1; fi; "
                + "if [ -s \"$in\" ]; then "
                + "if ! command -v bu >/dev/null 2>&1; then echo 'bu binary not found' >&2; exit 127; fi; "
                + "try_bu_restore() { label=\"$1\"; shift; rm -f \"$buerr\"; echo 'Trying Android restore tool: ' \"$label\"; if \"$@\" < \"$in\" 2>\"$buerr\"; then echo 'Restore finished: ' \"$in\"; exit 0; fi; code=$?; echo 'Android restore tool exit code: ' \"$code\" >&2; if [ -s \"$buerr\" ]; then cat \"$buerr\" >&2; fi; return 1; }; "
                + "try_bu_restore 'default user' bu restore; "
                + "try_bu_restore 'user 0' bu restore -user 0; "
                + "echo 'Android backup restore failed.' >&2; exit 1; fi; "
                + "am force-stop \"$pkg\" 2>/dev/null || true; "
                + "if [ -s \"$datacetar\" ] || [ -s \"$datadetar\" ]; then "
                + "if ! run-as \"$pkg\" sh -c 'id >/dev/null' 2>\"$runaserr\"; then "
                + "echo 'run-as restore is not available. Install a debuggable build before restoring run-as data backups.' >&2; "
                + "if [ -s \"$runaserr\" ]; then cat \"$runaserr\" >&2; fi; exit 1; fi; "
                + "code=0; "
                + "if [ -s \"$datacetar\" ]; then cat \"$datacetar\" | run-as \"$pkg\" sh -c \"cd /data/data/$pkg && tar -xf -\" 2>>\"$runaserr\" || code=$?; fi; "
                + "if [ -s \"$datadetar\" ]; then cat \"$datadetar\" | run-as \"$pkg\" sh -c \"if [ -d /data/user_de/0/$pkg ]; then cd /data/user_de/0/$pkg && tar -xf -; fi\" 2>>\"$runaserr\" || code=$?; fi; "
                + "if [ \"$code\" -eq 0 ]; then echo 'run-as data restore finished: ' \"$pkg\"; exit 0; fi; "
                + "echo 'run-as data restore failed.' >&2; if [ -s \"$runaserr\" ]; then cat \"$runaserr\" >&2; fi; exit \"$code\"; fi; "
                + "echo 'Backup not found: ' \"$in\" >&2; exit 1";
    }

    @NonNull
    static String shellQuote(@Nullable String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    @NonNull
    private static String packageBackupDir(@Nullable String packageName) {
        String pkg = sanitizePackageName(packageName);
        if (pkg.isEmpty()) pkg = "unknown";
        return BACKUP_DIR + "/" + pkg;
    }


    @NonNull
    private static String sanitizePackageName(@Nullable String packageName) {
        if (packageName == null) return "";
        String trimmed = packageName.trim();
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
