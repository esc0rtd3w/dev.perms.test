package dev.perms.test.packages;

import androidx.annotation.NonNull;

/** Shell command builder for saving, reading, and applying package state lists. */
public final class PackageStateListCommands {
    public enum StateFilter {
        ALL("all"),
        ENABLED("enabled"),
        DISABLED("disabled");

        private final String jsonName;

        StateFilter(String jsonName) {
            this.jsonName = jsonName;
        }

        @NonNull
        public String jsonName() {
            return jsonName;
        }
    }

    private static final String STATE_DIR = "/sdcard/dev.perms.test/package_states";
    private static final String PACKAGE_STATE_LIST_JSON = STATE_DIR + "/package_state_list.json";

    private PackageStateListCommands() {
    }

    @NonNull
    public static String statePath() {
        return PACKAGE_STATE_LIST_JSON;
    }

    @NonNull
    public static String saveCommand(@NonNull String selfPackageName, @NonNull StateFilter filter) {
        final String self = sanitizePackageName(selfPackageName);
        final String selectedFilter = filter == null ? StateFilter.ALL.jsonName() : filter.jsonName();
        final String dir = STATE_DIR;
        final String out = PACKAGE_STATE_LIST_JSON;
        final String tmp = out + ".tmp";
        final String disabledTmp = out + ".disabled.tmp";
        return "dir=" + shellQuote(dir) + "; out=" + shellQuote(out) + "; tmp=" + shellQuote(tmp)
                + "; disabled_tmp=" + shellQuote(disabledTmp) + "; self=" + shellQuote(self)
                + "; filter=" + shellQuote(selectedFilter) + "; "
                + "mkdir -p \"$dir\" || exit 1; "
                + "pm list packages --user 0 -d 2>/dev/null | sed 's/^package://' | sort > \"$disabled_tmp\" || :; "
                + "{ "
                + "printf '{\\n'; "
                + "printf '  \"type\": \"package_state_list\",\\n'; "
                + "printf '  \"user\": 0,\\n'; "
                + "printf '  \"filter\": \"%s\",\\n' \"$filter\"; "
                + "printf '  \"stateSource\": \"dumpsys-user-enabled\",\\n'; "
                + "printf '  \"packages\": [\\n'; "
                + "first=1; "
                + "pm list packages --user 0 2>/dev/null | sed 's/^package://' | sort | while IFS= read -r pkg; do "
                + "[ -n \"$pkg\" ] || continue; "
                + "[ \"$pkg\" != \"$self\" ] || continue; "
                + "case \"$pkg\" in *[!A-Za-z0-9._-]*) continue;; esac; "
                + "pm_disabled=false; grep -Fxq \"$pkg\" \"$disabled_tmp\" 2>/dev/null && pm_disabled=true; "
                + "line=$(cmd package dump \"$pkg\" 2>/dev/null | grep -m 1 -E 'User 0:.* enabled=' || dumpsys package \"$pkg\" 2>/dev/null | grep -m 1 -E 'User 0:.* enabled=' || true); "
                + "state_value=$(printf '%s' \"$line\" | sed -n 's/.* enabled=\\([^ ]*\\).*/\\1/p'); "
                + "enabled=true; enabled_state=default_enabled; "
                + "case \"$state_value\" in "
                + "1|enabled|true) enabled=true; enabled_state=enabled;; "
                + "2|disabled) enabled=false; enabled_state=disabled;; "
                + "3|disabled-user|disabled_user) enabled=false; enabled_state=disabled_user;; "
                + "4|disabled-until-used|disabled_until_used) enabled=true; enabled_state=disabled_until_used;; "
                + "0|default|'') if [ \"$pm_disabled\" = true ]; then enabled=false; enabled_state=default_disabled; else enabled=true; enabled_state=default_enabled; fi;; "
                + "*) if [ \"$pm_disabled\" = true ]; then enabled=false; enabled_state=pm_list_disabled; else enabled=true; enabled_state=unknown_enabled; fi;; "
                + "esac; "
                + "case \"$filter:$enabled\" in enabled:false|disabled:true) continue;; esac; "
                + "if [ \"$first\" = 0 ]; then printf ',\\n'; fi; "
                + "printf '    {\"package\":\"%s\",\"enabled\":%s,\"enabledState\":\"%s\",\"pmListedDisabled\":%s}' \"$pkg\" \"$enabled\" \"$enabled_state\" \"$pm_disabled\"; "
                + "first=0; "
                + "done; "
                + "printf '\\n  ]\\n}\\n'; "
                + "} > \"$tmp\" || exit 1; "
                + "rm -f \"$disabled_tmp\" 2>/dev/null || true; "
                + "mv -f \"$tmp\" \"$out\" || exit 1; "
                + "chmod 0666 \"$out\" 2>/dev/null || true; "
                + "count=$(grep -oE '\"package\":\"[A-Za-z0-9_][A-Za-z0-9_.-]*\\.[A-Za-z0-9_.-]*\"' \"$out\" | wc -l | tr -d ' '); "
                + "echo 'Saved package state list: ' \"$out\"; "
                + "echo 'Package state filter saved: ' \"$filter\"; "
                + "echo 'Package state entries saved: ' \"$count\"";
    }

    @NonNull
    public static String readCommand() {
        final String in = PACKAGE_STATE_LIST_JSON;
        return "in=" + shellQuote(in) + "; "
                + "if [ ! -s \"$in\" ]; then echo 'Package state list not found: ' \"$in\" >&2; exit 1; fi; "
                + "cat \"$in\"";
    }

    @NonNull
    public static String applyCommand(@NonNull java.util.List<String> packageNames, boolean enable, @NonNull String selfPackageName) {
        final String self = sanitizePackageName(selfPackageName);
        StringBuilder cmd = new StringBuilder();
        cmd.append("self=").append(shellQuote(self)).append("; ok=0; fail=0; skip=0; ");
        for (String raw : packageNames) {
            String pkg = sanitizePackageName(raw);
            if (pkg.length() == 0) continue;
            cmd.append("pkg=").append(shellQuote(pkg)).append("; ")
                    .append("case \"$pkg\" in \"$self\"|android|com.android.shell|com.android.systemui) echo 'Skipping protected package: ' \"$pkg\"; skip=$((skip+1));; ")
                    .append("*[!A-Za-z0-9._-]*) echo 'Skipping invalid package: ' \"$pkg\"; skip=$((skip+1));; ")
                    .append("*) ");
            if (enable) {
                cmd.append("if pm enable \"$pkg\" >/dev/null 2>&1; then echo 'Enabled: ' \"$pkg\"; ok=$((ok+1)); else echo 'Failed enable: ' \"$pkg\" >&2; fail=$((fail+1)); fi");
            } else {
                cmd.append("if pm disable-user --user 0 \"$pkg\" >/dev/null 2>&1; then echo 'Disabled: ' \"$pkg\"; ok=$((ok+1)); else echo 'Failed disable: ' \"$pkg\" >&2; fail=$((fail+1)); fi");
            }
            cmd.append(";; esac; ");
        }
        cmd.append("echo 'Package state apply complete: changed='\"$ok\"' failed='\"$fail\"' skipped='\"$skip\"; [ \"$fail\" -eq 0 ]");
        return cmd.toString();
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
