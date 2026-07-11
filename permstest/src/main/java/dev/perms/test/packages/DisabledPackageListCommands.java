package dev.perms.test.packages;

import androidx.annotation.NonNull;

/** Shell command builder for saving and restoring the user-disabled package list. */
public final class DisabledPackageListCommands {
    private static final String STATE_DIR = "/sdcard/dev.perms.test/package_states";
    private static final String DISABLED_PACKAGES_JSON = STATE_DIR + "/disabled_packages.json";

    private DisabledPackageListCommands() {
    }

    @NonNull
    public static String statePath() {
        return DISABLED_PACKAGES_JSON;
    }

    @NonNull
    public static String saveCommand(@NonNull String selfPackageName) {
        final String self = sanitizePackageName(selfPackageName);
        final String dir = STATE_DIR;
        final String out = DISABLED_PACKAGES_JSON;
        final String tmp = out + ".tmp";
        return "dir=" + shellQuote(dir) + "; out=" + shellQuote(out) + "; tmp=" + shellQuote(tmp) + "; self=" + shellQuote(self) + "; "
                + "mkdir -p \"$dir\" || exit 1; "
                + "{ "
                + "printf '{\\n'; "
                + "printf '  \"type\": \"disabled_packages\",\\n'; "
                + "printf '  \"user\": 0,\\n'; "
                + "printf '  \"packages\": [\\n'; "
                + "first=1; "
                + "pm list packages --user 0 -d 2>/dev/null | sed 's/^package://' | sort | while IFS= read -r pkg; do "
                + "[ -n \"$pkg\" ] || continue; "
                + "[ \"$pkg\" != \"$self\" ] || continue; "
                + "case \"$pkg\" in *[!A-Za-z0-9._-]*) continue;; esac; "
                + "if [ \"$first\" = 0 ]; then printf ',\\n'; fi; "
                + "printf '    \"%s\"' \"$pkg\"; "
                + "first=0; "
                + "done; "
                + "printf '\\n  ]\\n}\\n'; "
                + "} > \"$tmp\" || exit 1; "
                + "mv -f \"$tmp\" \"$out\" || exit 1; "
                + "chmod 0666 \"$out\" 2>/dev/null || true; "
                + "count=$(grep -oE '\"[A-Za-z0-9_][A-Za-z0-9_.-]*\\.[A-Za-z0-9_.-]*\"' \"$out\" | wc -l | tr -d ' '); "
                + "echo 'Saved disabled package list: ' \"$out\"; "
                + "echo 'Disabled packages saved: ' \"$count\"";
    }

    @NonNull
    public static String loadCommand(@NonNull String selfPackageName) {
        final String self = sanitizePackageName(selfPackageName);
        final String in = DISABLED_PACKAGES_JSON;
        return "in=" + shellQuote(in) + "; self=" + shellQuote(self) + "; "
                + "if [ ! -s \"$in\" ]; then echo 'Disabled package list not found: ' \"$in\" >&2; exit 1; fi; "
                + "ok=0; fail=0; skip=0; "
                + "pkgs=$(grep -oE '\"[A-Za-z0-9_][A-Za-z0-9_.-]*\\.[A-Za-z0-9_.-]*\"' \"$in\" | tr -d '\"' | sort -u); "
                + "if [ -z \"$pkgs\" ]; then echo 'No package names found in disabled package list.' >&2; exit 1; fi; "
                + "echo 'Loading disabled package list: ' \"$in\"; "
                + "for pkg in $pkgs; do "
                + "case \"$pkg\" in \"$self\"|android|com.android.shell|com.android.systemui) echo 'Skipping protected package: ' \"$pkg\"; skip=$((skip+1)); continue;; esac; "
                + "case \"$pkg\" in *[!A-Za-z0-9._-]*) echo 'Skipping invalid package: ' \"$pkg\"; skip=$((skip+1)); continue;; esac; "
                + "if pm disable-user --user 0 \"$pkg\" >/dev/null 2>&1; then "
                + "echo 'Disabled: ' \"$pkg\"; ok=$((ok+1)); "
                + "else echo 'Failed: ' \"$pkg\" >&2; fail=$((fail+1)); fi; "
                + "done; "
                + "echo 'Disable restore complete: disabled='\"$ok\"' failed='\"$fail\"' skipped='\"$skip\"; "
                + "[ \"$fail\" -eq 0 ]";
    }

    @NonNull
    private static String sanitizePackageName(String packageName) {
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

    @NonNull
    private static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
