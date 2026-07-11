package dev.perms.test.network.web;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.perms.test.ExecMode;
import dev.perms.test.memory.MemoryPackageEntry;
import dev.perms.test.memory.MemoryProcessEntry;
import dev.perms.test.memory.MemorySettings;
import dev.perms.test.memory.MemoryToolHelper;
import dev.perms.test.memory.MemoryToolRuntime;

/**
 * Background-safe Memory Web Interface actions.
 *
 * This class only bridges web requests to the same apk-medit command builder and
 * runtime helpers used by the Memory tab/service. It does not own overlay UI.
 */
public final class PermsTestWebMemoryApi {
    private static final int MAX_PACKAGE_ROWS = 5000;
    private static final int MAX_PROCESS_ROWS = 200;
    private static final int MAX_COMMAND_OUTPUT = 24000;

    private PermsTestWebMemoryApi() {
    }

    public static String statusJson(Context context, SharedPreferences prefs) {
        ExecMode mode = prefs == null ? ExecMode.SHIZUKU : ExecMode.get(prefs);
        return "{"
                + "\"ok\":true,"
                + "\"backend\":\"" + jsonEscape(mode.displayName()) + "\","
                + "\"withoutPtrace\":" + MemorySettings.shouldPatchWithoutPtrace(prefs) + ","
                + "\"caseSensitive\":" + MemorySettings.shouldStringCaseSensitive(prefs) + ","
                + "\"onlyRunningPackages\":" + MemorySettings.shouldOnlyShowRunningPackages(prefs) + ","
                + "\"excludeSelfPackage\":" + MemorySettings.shouldExcludeSelfPackage(prefs) + ","
                + "\"publicBinDir\":\"" + jsonEscape(MemoryToolRuntime.PUBLIC_BIN_DIR) + "\","
                + "\"stateFile\":\"" + jsonEscape(MemoryToolHelper.STATE_FILE_NAME) + "\","
                + "\"packageName\":\"" + jsonEscape(context == null ? "" : context.getPackageName()) + "\""
                + "}";
    }

    public static String packagesJson(Context context, SharedPreferences prefs) {
        try {
            List<MemoryPackageEntry> all = MemoryToolRuntime.listTargetPackages(context);
            boolean onlyRunning = MemorySettings.shouldOnlyShowRunningPackages(prefs);
            boolean excludeSelf = MemorySettings.shouldExcludeSelfPackage(prefs);
            String self = context == null ? "" : context.getPackageName();
            StringBuilder out = new StringBuilder(16384);
            int count = 0;
            int matching = 0;
            boolean truncated = false;
            out.append("{\"ok\":true,\"packages\":[");
            if (all != null) {
                for (MemoryPackageEntry entry : all) {
                    if (entry == null || TextUtils.isEmpty(entry.pkg)) continue;
                    if (onlyRunning && !entry.running) continue;
                    if (excludeSelf && entry.pkg.equals(self)) continue;
                    matching++;
                    if (count >= MAX_PACKAGE_ROWS) {
                        truncated = true;
                        continue;
                    }
                    if (count > 0) out.append(',');
                    out.append("{\"label\":\"").append(jsonEscape(entry.label)).append("\",")
                            .append("\"package\":\"").append(jsonEscape(entry.pkg)).append("\",")
                            .append("\"running\":").append(entry.running).append(',')
                            .append("\"debuggable\":").append(entry.debuggable).append('}');
                    count++;
                }
            }
            out.append("],\"count\":").append(count)
                    .append(",\"total\":").append(matching)
                    .append(",\"truncated\":").append(truncated)
                    .append('}');
            return out.toString();
        } catch (Throwable t) {
            return errorJson("list packages failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static String processesJson(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) return errorJson("missing package");
        try {
            List<MemoryProcessEntry> all = MemoryToolRuntime.listTargetProcesses(context, packageName.trim());
            StringBuilder out = new StringBuilder(2048);
            int count = 0;
            out.append("{\"ok\":true,\"processes\":[");
            if (all != null) {
                for (MemoryProcessEntry entry : all) {
                    if (entry == null) continue;
                    if (count > 0) out.append(',');
                    out.append("{\"pid\":\"").append(jsonEscape(entry.pid)).append("\",")
                            .append("\"name\":\"").append(jsonEscape(entry.name)).append("\",")
                            .append("\"label\":\"").append(jsonEscape(entry.displayLabel())).append("\"}");
                    count++;
                    if (count >= MAX_PROCESS_ROWS) break;
                }
            }
            out.append("],\"count\":").append(count).append('}');
            return out.toString();
        } catch (Throwable t) {
            return errorJson("list processes failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static String actionJson(Context context, SharedPreferences prefs, String path, String query) {
        String pkg = queryValue(query, "pkg");
        if (TextUtils.isEmpty(pkg)) pkg = queryValue(query, "package");
        if (path.endsWith("/packages")) return packagesJson(context, prefs);
        if (path.endsWith("/processes")) return processesJson(context, pkg);
        if (path.endsWith("/status")) return statusJson(context, prefs);
        if (path.endsWith("/attach")) return runMeditCommandJson(context, prefs, withCommand(query, "attach"));
        if (path.endsWith("/detach")) return runMeditCommandJson(context, prefs, withCommand(query, "detach"));
        if (path.endsWith("/launch")) return launchJson(context, pkg);
        if (path.endsWith("/stop")) {
            if (TextUtils.isEmpty(pkg)) return errorJson("missing package");
            return runSimpleCommandJson(context, "am force-stop " + MemoryToolRuntime.shQuote(pkg.trim()), "stop", pkg.trim());
        }
        if (path.endsWith("/clear")) return clearJson(context, pkg);
        if (path.endsWith("/state")) return readStateJson(context, pkg, queryInt(query, "max", MemoryToolHelper.DEFAULT_RESULT_LIST_LIMIT));
        if (path.endsWith("/run")) return runMeditCommandJson(context, prefs, query);
        return errorJson("unknown memory action");
    }

    private static String launchJson(Context context, String pkg) {
        if (context == null) return errorJson("context unavailable");
        if (TextUtils.isEmpty(pkg)) return errorJson("missing package");
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm == null ? null : pm.getLaunchIntentForPackage(pkg.trim());
            if (intent == null) return errorJson("no launchable activity for " + pkg.trim());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return okMessageJson("launch requested", "package", pkg.trim());
        } catch (Throwable t) {
            return errorJson("launch failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static String clearJson(Context context, String pkg) {
        if (TextUtils.isEmpty(pkg)) return errorJson("missing package");
        String cmd = MemoryToolHelper.buildClearStateCommand(pkg.trim());
        return runSimpleCommandJson(context, cmd, "clear", pkg.trim());
    }

    private static String readStateJson(Context context, String pkg, int maxResults) {
        if (TextUtils.isEmpty(pkg)) return errorJson("missing package");
        String cmd = MemoryToolHelper.buildReadStateCommand(pkg.trim(), maxResults);
        MemoryToolRuntime.CmdResult result = MemoryToolRuntime.runShellCommandCaptureSync(context, cmd);
        return commandResultJson("state", pkg.trim(), cmd, result, true);
    }

    private static String runMeditCommandJson(Context context, SharedPreferences prefs, String query) {
        String pkg = queryValue(query, "pkg");
        if (TextUtils.isEmpty(pkg)) pkg = queryValue(query, "package");
        String command = queryValue(query, "command");
        if (TextUtils.isEmpty(command)) command = queryValue(query, "cmd");
        if (TextUtils.isEmpty(pkg)) return errorJson("missing package");
        if (TextUtils.isEmpty(command)) return errorJson("missing command");

        String normalizedCommand = command.trim().toLowerCase(Locale.US);
        if (!isAllowedCommand(normalizedCommand)) {
            return errorJson("unsupported command: " + normalizedCommand);
        }

        MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(context, MemoryToolHelper.TOOL_NAME);
        if (install == null || install.exitCode != 0) {
            return commandResultJson("stage", pkg.trim(), "stage " + MemoryToolHelper.TOOL_NAME, install, false);
        }

        String pid = queryValue(query, "pid");
        if (TextUtils.isEmpty(pid)) pid = MemoryToolRuntime.resolveTargetPid(context, pkg.trim(), "");
        String type = queryValue(query, "type");
        if (TextUtils.isEmpty(type)) type = queryValue(query, "dataType");
        String value = queryValue(query, "value");
        String begin = queryValue(query, "begin");
        if (TextUtils.isEmpty(begin)) begin = queryValue(query, "address");
        String end = queryValue(query, "end");
        int max = queryInt(query, "max", MemoryToolHelper.DEFAULT_MAX_RESULTS);
        boolean withoutPtrace = MemorySettings.shouldPatchWithoutPtrace(prefs);
        boolean caseSensitive = MemorySettings.shouldStringCaseSensitive(prefs);

        String shell = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                pkg.trim(),
                MemoryToolRuntime.PUBLIC_BIN_DIR,
                withoutPtrace,
                normalizedCommand,
                pid,
                type,
                value,
                begin,
                end,
                null,
                max,
                caseSensitive,
                true);
        MemoryToolRuntime.CmdResult result = MemoryToolRuntime.runShellCommandCaptureSync(context, shell);
        return commandResultJson(normalizedCommand, pkg.trim(), shell, result, false);
    }

    private static String runSimpleCommandJson(Context context, String command, String action, String pkg) {
        if (TextUtils.isEmpty(command)) return errorJson("empty command");
        MemoryToolRuntime.CmdResult result = MemoryToolRuntime.runShellCommandCaptureSync(context, command);
        return commandResultJson(action, pkg, command, result, false);
    }

    private static String commandResultJson(String action,
                                            String pkg,
                                            String shellCommand,
                                            MemoryToolRuntime.CmdResult result,
                                            boolean rawState) {
        int exit = result == null ? -1 : result.exitCode;
        String stdout = result == null ? "" : limit(result.stdout, MAX_COMMAND_OUTPUT);
        String stderr = result == null ? "no result" : limit(result.stderr, MAX_COMMAND_OUTPUT);
        StringBuilder out = new StringBuilder(2048 + stdout.length() + stderr.length());
        out.append("{\"ok\":").append(exit == 0).append(',')
                .append("\"action\":\"").append(jsonEscape(action)).append("\",")
                .append("\"package\":\"").append(jsonEscape(pkg)).append("\",")
                .append("\"exit\":").append(exit).append(',')
                .append("\"stdout\":\"").append(jsonEscape(stdout)).append("\",")
                .append("\"stderr\":\"").append(jsonEscape(stderr)).append("\"");
        if (rawState) out.append(",\"stateJson\":\"").append(jsonEscape(stdout)).append("\"");
        if (!TextUtils.isEmpty(shellCommand)) {
            out.append(",\"shell\":\"").append(jsonEscape(limit(shellCommand, 4000))).append("\"");
        }
        out.append('}');
        return out.toString();
    }

    private static boolean isAllowedCommand(String command) {
        if (TextUtils.isEmpty(command)) return false;
        switch (command.trim().toLowerCase(Locale.US)) {
            case "attach":
            case "detach":
            case "find":
            case "find-gt":
            case "find-lt":
            case "snapshot":
            case "filter":
            case "filter-gt":
            case "filter-lt":
            case "filter-unchanged":
            case "filter-changed":
            case "patch":
            case "dump":
            case "dump-file":
            case "search-magic":
            case "search-bytes":
            case "search-bytes-mask":
            case "write-bytes":
            case "write-file":
                return true;
            default:
                return false;
        }
    }

    private static int queryInt(String query, String key, int fallback) {
        try {
            String value = queryValue(query, key);
            if (TextUtils.isEmpty(value)) return fallback;
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String withCommand(String query, String command) {
        String base = query == null ? "" : query.trim();
        String encoded = "command=" + urlEncode(command);
        if (base.length() == 0) return encoded;
        if (!TextUtils.isEmpty(queryValue(base, "command")) || !TextUtils.isEmpty(queryValue(base, "cmd"))) return base;
        return base + "&" + encoded;
    }

    private static String urlEncode(String value) {
        try { return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8"); } catch (Throwable ignored) { return value == null ? "" : value; }
    }

    public static String queryValue(String query, String key) {
        if (query == null || key == null) return "";
        String[] parts = query.split("&");
        for (String part : parts) {
            int equals = part.indexOf('=');
            String name = equals >= 0 ? part.substring(0, equals) : part;
            if (key.equals(urlDecode(name))) return equals >= 0 ? urlDecode(part.substring(equals + 1)) : "";
        }
        return "";
    }

    private static String urlDecode(String value) {
        try { return java.net.URLDecoder.decode(value == null ? "" : value, "UTF-8"); } catch (Throwable ignored) { return value == null ? "" : value; }
    }

    public static String accessJson(Map<String, Boolean> access) {
        StringBuilder out = new StringBuilder(256);
        out.append("{\"ok\":true,\"access\":{");
        boolean first = true;
        if (access != null) {
            for (Map.Entry<String, Boolean> entry : access.entrySet()) {
                if (entry == null || TextUtils.isEmpty(entry.getKey())) continue;
                if (!first) out.append(',');
                out.append('\"').append(jsonEscape(entry.getKey())).append("\":").append(Boolean.TRUE.equals(entry.getValue()));
                first = false;
            }
        }
        out.append("}}");
        return out.toString();
    }

    public static String errorJson(String error) {
        return "{\"ok\":false,\"error\":\"" + jsonEscape(error) + "\"}";
    }

    private static String okMessageJson(String message, String key, String value) {
        return "{\"ok\":true,\"message\":\"" + jsonEscape(message) + "\",\"" + jsonEscape(key) + "\":\"" + jsonEscape(value) + "\"}";
    }

    private static String limit(String text, int max) {
        if (text == null) return "";
        if (max <= 0 || text.length() <= max) return text;
        return text.substring(0, max) + "\n[truncated]";
    }

    public static String jsonEscape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
