package dev.perms.test.memory;

import android.text.TextUtils;

/**
 * Builds apk-medit command lines and shared Memory-tab preference keys.
 *
 * This helper does not execute shell commands; it only normalizes UI values into
 * command strings that MemoryOverlayService or MainActivity can run through the
 * selected privileged backend.
 */
public final class MemoryToolHelper {
    public static final String TOOL_NAME = "medit";
    public static final String LOCAL_TOOL_NAME = ".permstest_medit";
    public static final String STATE_FILE_NAME = ".permstest_medit_state.json";
    public static final String DEFAULT_DATA_TYPE = "all";
    public static final String DEFAULT_SEARCH_MODE = "Exact";
    public static final int DEFAULT_MAX_RESULTS = 500000;
    public static final int DEFAULT_RESULT_LIST_LIMIT = 500;
    public static final int PUBLIC_STATE_RESULTS_THRESHOLD = 1000000;
    public static final int DEFAULT_AUTO_RANGE_PAGE_LIMIT = 128;
    public static final String SEARCH_MODE_EXACT = "Exact";
    public static final String SEARCH_MODE_INCREASED = "Increased Value";
    public static final String SEARCH_MODE_DECREASED = "Decreased Value";
    public static final String SEARCH_MODE_UNCHANGED = "Unchanged Value";
    public static final String SEARCH_MODE_CHANGED = "Changed Value";
    public static final String SEARCH_MODE_VALUE_GREATER_THAN = "Value > Input";
    public static final String SEARCH_MODE_VALUE_LESS_THAN = "Value < Input";
    public static final String SEARCH_MODE_UNKNOWN_SNAPSHOT = "Unknown / Start Snapshot";
    public static final String[] DATA_TYPES = new String[]{"all", "string", "word", "dword", "qword"};
    public static final String[] SEARCH_MODES = new String[]{SEARCH_MODE_EXACT, SEARCH_MODE_INCREASED, SEARCH_MODE_DECREASED, SEARCH_MODE_CHANGED, SEARCH_MODE_UNCHANGED, SEARCH_MODE_VALUE_GREATER_THAN, SEARCH_MODE_VALUE_LESS_THAN, SEARCH_MODE_UNKNOWN_SNAPSHOT};

    public static final String KEY_WITHOUT_PTRACE = "memory_without_ptrace";
    public static final String KEY_USE_OVERLAY = "memory_use_overlay";
    public static final String KEY_OVERLAY_TRANSPARENT = "memory_overlay_transparent";
    public static final String KEY_OVERLAY_RESIZABLE = "memory_overlay_resizable";
    public static final String KEY_DISABLE_OVERLAYS_VR_COMPATIBLE = "memory_disable_overlays_vr_compatible";
    public static final String KEY_ONLY_RUNNING_PACKAGES = "memory_only_running_packages";
    public static final String KEY_SHOW_RUNNING_PACKAGES = "memory_show_running_packages";
    public static final String KEY_SCAN_AT_STARTUP = "memory_scan_at_startup";
    public static final String KEY_EXCLUDE_SELF_PACKAGE = "memory_exclude_self_package";
    public static final String KEY_AUTO_STAGE = "memory_auto_stage";
    public static final String KEY_STRING_CASE_SENSITIVE = "memory_string_case_sensitive";
    public static final String KEY_STRING_PATCH_TRUNCATE = "memory_string_patch_truncate";
    public static final String KEY_AUTO_RANGE = "memory_auto_range";
    public static final String KEY_AUTO_RANGE_PAGE_LIMIT = "memory_auto_range_page_limit";
    public static final String KEY_OVERLAY_SHOW_SESSION_BUTTONS = "memory_overlay_show_session_buttons";
    // When true, a successful attach scans the selected package payload folder
    // and applies every complete original_hex/patched_hex payload JSON file.
    public static final String KEY_APPLY_PAYLOADS_ON_ATTACH = "memory_apply_payloads_on_attach";
    // When true, each payload apply searches all bounded original_hex matches
    // and writes patched_hex at every discovered runtime address.
    public static final String KEY_APPLY_PAYLOADS_TO_ALL_MATCHES = "memory_apply_payloads_to_all_matches";
    // When true, the overlay details log lists every successfully patched
    // payload address instead of summarizing after the first few entries.
    public static final String KEY_SHOW_ALL_PATCHED_PAYLOAD_ADDRESSES = "memory_show_all_patched_payload_addresses";
    public static final String KEY_LAST_PACKAGE_TOOLS_TARGET = "memory_last_package_tools_target";

    private MemoryToolHelper() {
    }

    public static String normalizeDataType(String value) {
        if (TextUtils.isEmpty(value)) return DEFAULT_DATA_TYPE;
        String v = value.trim().toLowerCase();
        if ("strings".equals(v) || "utf8".equals(v) || "utf-8".equals(v)) {
            return "string";
        }
        if ("string".equals(v) || "word".equals(v) || "dword".equals(v) || "qword".equals(v)) {
            return v;
        }
        return DEFAULT_DATA_TYPE;
    }

    public static String normalizeSearchMode(String value) {
        if (TextUtils.isEmpty(value)) return DEFAULT_SEARCH_MODE;
        String v = value.trim();
        for (String mode : SEARCH_MODES) {
            if (mode.equals(v)) return mode;
        }
        return DEFAULT_SEARCH_MODE;
    }

    public static boolean isExactSearchMode(String value) {
        return SEARCH_MODE_EXACT.equals(normalizeSearchMode(value));
    }

    public static boolean isIncreasedSearchMode(String value) {
        return SEARCH_MODE_INCREASED.equals(normalizeSearchMode(value));
    }

    public static boolean isDecreasedSearchMode(String value) {
        return SEARCH_MODE_DECREASED.equals(normalizeSearchMode(value));
    }

    public static boolean isValueGreaterThanInputSearchMode(String value) {
        return SEARCH_MODE_VALUE_GREATER_THAN.equals(normalizeSearchMode(value));
    }

    public static boolean isValueLessThanInputSearchMode(String value) {
        return SEARCH_MODE_VALUE_LESS_THAN.equals(normalizeSearchMode(value));
    }

    public static boolean isUnchangedSearchMode(String value) {
        return SEARCH_MODE_UNCHANGED.equals(normalizeSearchMode(value));
    }

    public static boolean isChangedSearchMode(String value) {
        return SEARCH_MODE_CHANGED.equals(normalizeSearchMode(value));
    }

    public static boolean isUnknownSnapshotSearchMode(String value) {
        return SEARCH_MODE_UNKNOWN_SNAPSHOT.equals(normalizeSearchMode(value));
    }

    public static String normalizeNumericDataType(String value) {
        String v = normalizeDataType(value);
        if ("word".equals(v) || "dword".equals(v) || "qword".equals(v)) {
            return v;
        }
        return "dword";
    }

    public static String normalizeSnapshotDataType(String value) {
        String v = normalizeDataType(value);
        if ("word".equals(v) || "dword".equals(v) || "qword".equals(v) || DEFAULT_DATA_TYPE.equals(v)) {
            return v;
        }
        return "dword";
    }

    public static String buildRunAsCommand(String packageName,
                                    String publicBinDir,
                                    boolean withoutPtrace,
                                    String command,
                                    String targetPid,
                                    String dataType,
                                    String value,
                                    String begin,
                                    String end) {
        return buildRunAsCommand(
                packageName,
                publicBinDir,
                withoutPtrace,
                command,
                targetPid,
                dataType,
                value,
                begin,
                end,
                false
        );
    }

    public static String buildRunAsCommand(String packageName,
                                    String publicBinDir,
                                    boolean withoutPtrace,
                                    String command,
                                    String targetPid,
                                    String dataType,
                                    String value,
                                    String begin,
                                    String end,
                                    boolean stringCaseSensitive) {
        return buildRunAsCommandWithStateOverride(
                packageName,
                publicBinDir,
                withoutPtrace,
                command,
                targetPid,
                dataType,
                value,
                begin,
                end,
                null,
                DEFAULT_MAX_RESULTS,
                stringCaseSensitive,
                true
        );
    }

    public static String buildRunAsCommandWithStateOverride(String packageName,
                                                     String publicBinDir,
                                                     boolean withoutPtrace,
                                                     String command,
                                                     String targetPid,
                                                     String dataType,
                                                     String value,
                                                     String begin,
                                                     String end,
                                                     String stateJsonOverride,
                                                     int maxResults) {
        return buildRunAsCommandWithStateOverride(
                packageName,
                publicBinDir,
                withoutPtrace,
                command,
                targetPid,
                dataType,
                value,
                begin,
                end,
                stateJsonOverride,
                maxResults,
                false,
                true
        );
    }

    public static String buildRunAsCommandWithStateOverride(String packageName,
                                                     String publicBinDir,
                                                     boolean withoutPtrace,
                                                     String command,
                                                     String targetPid,
                                                     String dataType,
                                                     String value,
                                                     String begin,
                                                     String end,
                                                     String stateJsonOverride,
                                                     int maxResults,
                                                     boolean stringCaseSensitive) {
        return buildRunAsCommandWithStateOverride(
                packageName,
                publicBinDir,
                withoutPtrace,
                command,
                targetPid,
                dataType,
                value,
                begin,
                end,
                stateJsonOverride,
                maxResults,
                stringCaseSensitive,
                true
        );
    }

    public static String buildRunAsCommandWithStateOverride(String packageName,
                                                     String publicBinDir,
                                                     boolean withoutPtrace,
                                                     String command,
                                                     String targetPid,
                                                     String dataType,
                                                     String value,
                                                     String begin,
                                                     String end,
                                                     String stateJsonOverride,
                                                     int maxResults,
                                                     boolean stringCaseSensitive,
                                                     boolean stringPatchTruncate) {
        return buildRunAsCommandWithStateOverride(
                packageName,
                publicBinDir,
                withoutPtrace,
                command,
                targetPid,
                dataType,
                value,
                begin,
                end,
                stateJsonOverride,
                maxResults,
                stringCaseSensitive,
                stringPatchTruncate,
                0,
                statePathForCommand()
        );
    }

    public static String buildRunAsCommandWithStateOverride(String packageName,
                                                     String publicBinDir,
                                                     boolean withoutPtrace,
                                                     String command,
                                                     String targetPid,
                                                     String dataType,
                                                     String value,
                                                     String begin,
                                                     String end,
                                                     String stateJsonOverride,
                                                     int maxResults,
                                                     boolean stringCaseSensitive,
                                                     boolean stringPatchTruncate,
                                                     int skipResults) {
        return buildRunAsCommandWithStateOverride(
                packageName,
                publicBinDir,
                withoutPtrace,
                command,
                targetPid,
                dataType,
                value,
                begin,
                end,
                stateJsonOverride,
                maxResults,
                stringCaseSensitive,
                stringPatchTruncate,
                skipResults,
                statePathForCommand()
        );
    }

    public static String buildRunAsCommandWithStateOverride(String packageName,
                                                     String publicBinDir,
                                                     boolean withoutPtrace,
                                                     String command,
                                                     String targetPid,
                                                     String dataType,
                                                     String value,
                                                     String begin,
                                                     String end,
                                                     String stateJsonOverride,
                                                     int maxResults,
                                                     boolean stringCaseSensitive,
                                                     boolean stringPatchTruncate,
                                                     int skipResults,
                                                     String statePathOverride) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(publicBinDir) || TextUtils.isEmpty(command)) {
            return null;
        }

        String toolSourcePath = publicBinDir + "/" + TOOL_NAME;
        String localToolPath = "./" + LOCAL_TOOL_NAME + "_" + Long.toHexString(System.nanoTime());
        int normalizedMaxResults = normalizeMaxResults(maxResults);
        int normalizedSkipResults = Math.max(0, skipResults);
        String statePath = TextUtils.isEmpty(statePathOverride) ? statePathForCommand() : statePathOverride.trim();
        String normalizedCommand = command.trim().toLowerCase();

        StringBuilder script = new StringBuilder();
        script.append("trap ").append(shQuote("rm -f " + shQuote(localToolPath))).append(" EXIT");
        script.append("; ");
        script.append("cp ").append(shQuote(toolSourcePath)).append(" ").append(shQuote(localToolPath));
        script.append(" && chmod 700 ").append(shQuote(localToolPath));
        if (!TextUtils.isEmpty(stateJsonOverride)) {
            script.append(" && printf %s ").append(shQuote(stateJsonOverride)).append(" > ").append(shQuote(statePath));
        }
        script.append(" && ").append(shQuote(localToolPath));
        if (!TextUtils.isEmpty(targetPid)) {
            script.append(" --pid ").append(shQuote(targetPid.trim()));
        }
        if (withoutPtrace) script.append(" --without-ptrace");
        if (stringCaseSensitive) script.append(" --case-sensitive");
        if (!stringPatchTruncate) script.append(" --string-patch-truncate=false");
        script.append(" --max-results ").append(normalizedMaxResults);
        if (normalizedSkipResults > 0) script.append(" --skip-results ").append(normalizedSkipResults);
        script.append(" --command ").append(shQuote(buildCommandArgument(normalizedCommand, dataType, value, begin, end, targetPid)));
        script.append(" --state-file ").append(shQuote(statePath));

        return "run-as " + shQuote(packageName.trim()) + " sh -c " + shQuote(script.toString());
    }

    public static String buildReadStateCommand(String packageName) {
        return buildReadStateCommand(packageName, DEFAULT_MAX_RESULTS);
    }

    public static String buildReadStateCommand(String packageName, int maxResults) {
        return buildReadStateCommand(packageName, maxResults, statePathForCommand());
    }

    public static String buildReadStateCommand(String packageName, int maxResults, String statePathOverride) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        String statePath = TextUtils.isEmpty(statePathOverride) ? statePathForCommand() : statePathOverride.trim();
        String script = "cat " + shQuote(statePath) + " 2>/dev/null || true";
        return "run-as " + shQuote(packageName.trim()) + " sh -c " + shQuote(script);
    }

    public static String buildWriteStateCommand(String packageName, String stateJson) {
        return buildWriteStateCommand(packageName, stateJson, DEFAULT_MAX_RESULTS);
    }

    public static String buildWriteStateCommand(String packageName, String stateJson, int maxResults) {
        return buildWriteStateCommand(packageName, stateJson, maxResults, statePathForCommand());
    }

    public static String buildWriteStateCommand(String packageName, String stateJson, int maxResults, String statePathOverride) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(stateJson)) {
            return null;
        }
        String statePath = TextUtils.isEmpty(statePathOverride) ? statePathForCommand() : statePathOverride.trim();
        String script = "printf %s " + shQuote(stateJson) + " > " + shQuote(statePath);
        return "run-as " + shQuote(packageName.trim()) + " sh -c " + shQuote(script);
    }


    public static String buildClearStateCommand(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }

        StringBuilder script = new StringBuilder();
        script.append("rm -f ").append(shQuote("./" + LOCAL_TOOL_NAME));
        script.append(" ./").append(LOCAL_TOOL_NAME).append("_*");
        script.append(' ').append(shQuote("./" + STATE_FILE_NAME));
        script.append(" ./").append(STATE_FILE_NAME.replace(".json", "_r*.json"));
        // Keep cleanup scoped to apk-medit session files inside the target app's private run-as directory.
        script.append(" ./").append(STATE_FILE_NAME.replace("state.json", "state*.json"));
        script.append(" 2>/dev/null || true");
        return "run-as " + shQuote(packageName.trim()) + " sh -c " + shQuote(script.toString());
    }

    public static int normalizeMaxResults(int value) {
        if (value == 0) return 0;
        if (value < 0) return DEFAULT_MAX_RESULTS;
        return value;
    }

    public static boolean isHighCapOrUnlimited(int maxResults) {
        int normalized = normalizeMaxResults(maxResults);
        return normalized == 0 || normalized >= PUBLIC_STATE_RESULTS_THRESHOLD;
    }

    public static boolean isSnapshotCommand(String command) {
        return !TextUtils.isEmpty(command) && "snapshot".equals(command.trim().toLowerCase());
    }

    public static boolean isUnlimitedScanCap(int maxResults) {
        return normalizeMaxResults(maxResults) == 0;
    }

    public static int effectiveMaxResultsForCommand(String command, int maxResults) {
        return normalizeMaxResults(maxResults);
    }

    public static String statePathForCommand() {
        return "./" + STATE_FILE_NAME;
    }

    public static String statePathForRange(int pageIndex) {
        int safePage = Math.max(0, pageIndex);
        return "./" + STATE_FILE_NAME.replace(".json", "_r" + safePage + ".json");
    }

    private static String buildCommandArgument(String command,
                                               String dataType,
                                               String value,
                                               String begin,
                                               String end,
                                               String targetPid) {
        StringBuilder arg = new StringBuilder(command);
        switch (command) {
            case "find":
                String normalizedType = normalizeDataType(dataType);
                // Always include the datatype token, including "all".
                // The medit one-shot parser treats a three-token find command as
                // "find <datatype> <value>" and joins the remaining value tokens.
                // Without the explicit "all" token, an all-type string search such
                // as "all purchases" becomes "find all purchases" and is parsed as
                // datatype=all, value=purchases.
                if (!TextUtils.isEmpty(normalizedType)) {
                    arg.append(' ').append(normalizedType);
                }
                if (!TextUtils.isEmpty(value)) arg.append(' ').append(value.trim());
                break;
            case "find-gt":
            case "find-lt":
                arg.append(' ').append(normalizeNumericDataType(dataType));
                if (!TextUtils.isEmpty(value)) arg.append(' ').append(value.trim());
                break;
            case "snapshot":
                arg.append(' ').append(normalizeSnapshotDataType(dataType));
                break;
            case "filter":
            case "filter-gt":
            case "filter-lt":
            case "filter-unchanged":
            case "filter-changed":
            case "patch":
                if (!TextUtils.isEmpty(value)) arg.append(' ').append(value.trim());
                break;
            case "search-magic":
            case "search-bytes":
            case "search-bytes-mask":
                // Raw byte searches pass the hex block directly to apk-medit.
                // Payload Find/Apply uses this path for original_hex signatures.
                if (!TextUtils.isEmpty(value)) arg.append(' ').append(value.trim());
                break;
            case "dump":
                if (!TextUtils.isEmpty(begin)) arg.append(' ').append(begin.trim());
                if (!TextUtils.isEmpty(end)) arg.append(' ').append(end.trim());
                break;
            case "dump-file":
                if (!TextUtils.isEmpty(begin)) arg.append(' ').append(begin.trim());
                if (!TextUtils.isEmpty(end)) arg.append(' ').append(end.trim());
                if (!TextUtils.isEmpty(value)) arg.append(' ').append(value.trim());
                break;
            case "write-bytes":
            case "write-file":
                // write-bytes expects begin/address first, then hex bytes.
                // Payload writes use patched_hex through this command form.
                if (!TextUtils.isEmpty(begin)) arg.append(' ').append(begin.trim());
                if (!TextUtils.isEmpty(value)) arg.append(' ').append(value.trim());
                break;
            case "attach":
                // The staged medit wrapper already receives the selected pid via --pid.
                // Passing the pid again inside the command string can cause the wrapper to
                // treat it as an unknown subcommand form (for example: "attach 1234").
                // Keep the command itself as plain "attach" and let --pid carry the target.
                break;
            default:
                break;
        }
        return arg.toString();
    }

    private static String shQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
