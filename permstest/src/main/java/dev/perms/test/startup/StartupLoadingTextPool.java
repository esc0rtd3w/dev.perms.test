package dev.perms.test.startup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

import dev.perms.test.R;
import dev.perms.test.settings.SettingsPreferenceKeys;

/** Shared startup loading text selection for the launcher and main-screen transition. */
public final class StartupLoadingTextPool {
    private StartupLoadingTextPool() {
    }

    public static final String CATEGORY_DEFAULT = "default";
    private static final int MAX_STAGE_CHARS = 56;
    private static final int MAX_TIP_CHARS = 112;

    /*
     * The themed groups use playful labels, but their area and component text must
     * stay tied to real PermsTest features. Those two fields feed the normal
     * technical messages, so keep mascots as the only intentionally silly input.
     */
    private static final TextGroup[] GROUPS = new TextGroup[] {
            new TextGroup(CATEGORY_DEFAULT, "Default",
                    normalStages(), normalTips(), funnyStages(), funnyTips(),
                    defaultExtraNormalStages(), defaultExtraNormalTips(), defaultExtraFunnyStages(), defaultExtraFunnyTips(),
                    defaultVrNormalStages(), defaultVrNormalTips(), defaultVrFunnyStages(), defaultVrFunnyTips()),
            themedGroup("g420", "420", "relaxed diagnostics and payload review", "startup text pool", "wizard llama"),
            themedGroup("star_wars", "Star Wars", "package inspection and smali review", "debugging workspace", "protocol droid"),
            themedGroup("android_core", "Android Core", "framework and app-state checks", "backend connection", "tiny system service"),
            themedGroup("memory_hunter", "Memory Hunter", "scanner, payload, and overlay workflows", "memory session state", "value goblin"),
            themedGroup("smali_forge", "Smali Forge", "smali decode and rebuild work", "smali workspace", "baksmali blacksmith"),
            themedGroup("network_ops", "Network Ops", "FTP, HTTP, and web controls", "network service status", "router raccoon"),
            themedGroup("file_browser", "File Browser", "Shizuku-backed file navigation", "dual-pane file browser", "folder ferret"),
            themedGroup("update_channel", "Update Channel", "release, prerelease, and APK downloads", "update candidate filter", "update gnome"),
            vrThemedGroup("vr_lab", "VR Lab", "Quest panel and overlay routing", "VR panel state", "headset hamster"),
            themedGroup("debug_circus", "Debug Circus", "MITM, reports, and debug APK prep", "debug package workflow", "certificate clown"),
            themedGroup("shell_dungeon", "Shell Dungeon", "privileged shell and command output", "shell command runner", "terminal mimic"),
            themedGroup("save_data_vault", "Save Data Vault", "backup, match, and patch profiles", "save-data profile", "vault mole"),
            themedGroup("apk_workshop", "APK Workshop", "APK, APKS, APKM, and XAPK handling", "install staging queue", "zip goblin"),
            themedGroup("permissions_desk", "Permissions Desk", "grant, revoke, enable, and disable tools", "permission ledger", "clipboard imp"),
            themedGroup("logbook", "Logbook", "lifetime logs and logcat capture", "log capture pipeline", "scribe duck"),
            themedGroup("plugins", "Plugins", "staged plugin packages and native actions", "plugin manager", "plugin penguin"),
            themedGroup("web_interface", "Web Interface", "browser control and access gates", "web interface gate", "HTML butler"),
            themedGroup("payload_lab", "Payload Lab", "payload JSON, masks, and auto-apply", "payload mask editor", "payload crab"),
            themedGroup("hex_grid", "Hex Grid", "hex editor sync and byte patches", "hex address grid", "nibble gremlin"),
            themedGroup("controller_room", "Controller Room", "UI input routing and device profiles", "input profile switchboard", "button llama"),
            themedGroup("garage_lab", "Garage Lab", "mixed diagnostics and field testing", "diagnostic tool cart", "socket mechanic"),
            themedGroup("cyberpunk_console", "Cyberpunk Console", "terminal, shell, and backend diagnostics", "privileged console bridge", "neon raccoon"),
            themedGroup("pirate_debug_bay", "Pirate Debug Bay", "APK staging and debug logs", "install debug log map", "captain ferret"),
            themedGroup("goblin_qa", "Goblin QA", "regression checks and gated toggles", "settings gate ledger", "QA goblin"),
            themedGroup("space_station", "Space Station", "overlay, network, and memory modules", "module status panel", "orbit hamster"),
            themedGroup("retro_arcade", "Retro Arcade", "tabs, payloads, and controller-style controls", "tab action dashboard", "coin-op duck")
    };

    static Sequence createSequence(Context context) {
        Random random = newRandom();
        ResolvedGroup group = resolveSelectedGroups(context, random);
        boolean funny = funnyTipsEnabled(context);
        String[] tips = shuffledCopy(funny ? group.funnyTips : group.normalTips, random);
        String[] stages = shuffledCopy(funny ? group.funnyStages : group.normalStages, random);
        int stageIndex = stages.length > 0 ? random.nextInt(stages.length) : 0;
        int tipIndex = tips.length > 0 ? random.nextInt(tips.length) : 0;
        String stage = stages.length > 0 ? stages[stageIndex] : "";
        tipIndex = balancedTipIndex(tips, tipIndex, stage);
        return new Sequence(tips, stages, tipIndex, stageIndex);
    }

    static TextSelection randomSelection(Context context) {
        Random random = newRandom();
        ResolvedGroup group = resolveSelectedGroups(context, random);
        boolean funny = funnyTipsEnabled(context);
        String[] tips = funny ? group.funnyTips : group.normalTips;
        String[] stages = funny ? group.funnyStages : group.normalStages;
        int stageIndex = stages.length > 0 ? random.nextInt(stages.length) : 0;
        String stage = stages.length > 0 ? stages[stageIndex] : "";
        int tipIndex = tips.length > 0 ? random.nextInt(tips.length) : 0;
        tipIndex = balancedTipIndex(tips, tipIndex, stage);
        return new TextSelection(
                stage,
                tips.length > 0 ? tips[tipIndex] : "");
    }

    public static String[] categoryLabels() {
        String[] labels = new String[GROUPS.length];
        for (int i = 0; i < GROUPS.length; i++) labels[i] = GROUPS[i].label;
        return labels;
    }

    public static String labelForKey(String key) {
        return findGroup(key).label;
    }

    public static String keyForLabel(String label) {
        if (label == null) return CATEGORY_DEFAULT;
        String needle = label.trim();
        for (TextGroup group : GROUPS) {
            if (group.label.equalsIgnoreCase(needle)) return group.key;
        }
        return CATEGORY_DEFAULT;
    }

    public static String normalizeCategoryKey(String key) {
        return findGroup(key).key;
    }

    private static boolean funnyTipsEnabled(Context context) {
        try {
            return context != null && context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsPreferenceKeys.FUNNY_ANIMATION_TOOLTIPS, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static TextGroup selectGroup(Context context, Random random) {
        try {
            if (context == null) return findGroup(CATEGORY_DEFAULT);
            String key = context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .getString(SettingsPreferenceKeys.ANIMATION_CATEGORY, CATEGORY_DEFAULT);
            TextGroup group = findGroup(key);
            return isGroupAllowed(context, group) ? group : findGroup(CATEGORY_DEFAULT);
        } catch (Throwable ignored) {
            return findGroup(CATEGORY_DEFAULT);
        }
    }

    private static boolean allTooltipsEnabled(Context context) {
        try {
            return context != null && context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsPreferenceKeys.RANDOMIZE_ALL_TOOLTIPS, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ResolvedGroup resolveSelectedGroups(Context context, Random random) {
        if (allTooltipsEnabled(context)) return resolveAllGroups(context);
        return resolveGroup(context, selectGroup(context, random));
    }

    private static ResolvedGroup resolveAllGroups(Context context) {
        ArrayList<String> normalStages = new ArrayList<>();
        ArrayList<String> normalTips = new ArrayList<>();
        ArrayList<String> funnyStages = new ArrayList<>();
        ArrayList<String> funnyTips = new ArrayList<>();
        for (TextGroup group : GROUPS) {
            if (!isGroupAllowed(context, group)) continue;
            ResolvedGroup resolved = resolveGroup(context, group);
            appendAll(normalStages, resolved.normalStages);
            appendAll(normalTips, resolved.normalTips);
            appendAll(funnyStages, resolved.funnyStages);
            appendAll(funnyTips, resolved.funnyTips);
        }
        return new ResolvedGroup(
                normalStages.toArray(new String[0]),
                normalTips.toArray(new String[0]),
                funnyStages.toArray(new String[0]),
                funnyTips.toArray(new String[0]));
    }

    private static void appendAll(ArrayList<String> out, String[] values) {
        if (out == null || values == null) return;
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) out.add(value);
        }
    }

    private static TextGroup findGroup(String key) {
        if (key != null) {
            String needle = key.trim();
            for (TextGroup group : GROUPS) {
                if (group.key.equalsIgnoreCase(needle)) return group;
            }
        }
        return GROUPS[0];
    }

    /**
     * Builds a full startup text group from a compact theme description.
     *
     * The default group uses explicit resource strings plus app-specific extras because it
     * represents the whole app. The themed groups are generated from the label, area,
     * component, and mascot so every category gets the same amount of real/funny text
     * without duplicating long string arrays for each theme.
     */
    private static TextGroup themedGroup(String key, String label, String area, String component, String mascot) {
        return themedGroup(key, label, area, component, mascot, false);
    }

    private static TextGroup vrThemedGroup(String key, String label, String area, String component, String mascot) {
        return themedGroup(key, label, area, component, mascot, true);
    }

    private static TextGroup themedGroup(String key, String label, String area, String component, String mascot, boolean vrOnly) {
        return new TextGroup(key, label, null, null, null, null,
                themedNormalStages(label, area, component),
                themedNormalTips(label, area, component),
                themedFunnyStages(label, component, mascot),
                themedFunnyTips(label, area, component, mascot),
                themedVrNormalStages(label, area, component),
                themedVrNormalTips(label, area, component),
                themedVrFunnyStages(label, component, mascot),
                themedVrFunnyTips(label, area, component, mascot),
                vrOnly);
    }

    /** Capitalizes generated theme fragments only when they are used at sentence start. */
    private static String sentenceStart(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String trimmed = value.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isLetter(ch)) {
                char upper = Character.toUpperCase(ch);
                if (upper == ch) return trimmed;
                return trimmed.substring(0, i) + upper + trimmed.substring(i + 1);
            }
        }
        return trimmed;
    }

    private static ResolvedGroup resolveGroup(Context context, TextGroup group) {
        if (group == null) group = findGroup(CATEGORY_DEFAULT);
        boolean vrMode = isVrModeEnabled(context);
        String[] normalStages = mergeStrings(stringsFor(context, group.normalStageIds, vrMode), group.normalStageText);
        String[] normalTips = mergeStrings(stringsFor(context, group.normalTipIds, vrMode), group.normalTipText);
        String[] funnyStages = mergeStrings(stringsFor(context, group.funnyStageIds, vrMode), group.funnyStageText);
        String[] funnyTips = mergeStrings(stringsFor(context, group.funnyTipIds, vrMode), group.funnyTipText);
        if (vrMode) {
            normalStages = mergeStrings(normalStages, group.vrNormalStageText);
            normalTips = mergeStrings(normalTips, group.vrNormalTipText);
            funnyStages = mergeStrings(funnyStages, group.vrFunnyStageText);
            funnyTips = mergeStrings(funnyTips, group.vrFunnyTipText);
        } else {
            normalStages = filterVrOnlyText(normalStages);
            normalTips = filterVrOnlyText(normalTips);
            funnyStages = filterVrOnlyText(funnyStages);
            funnyTips = filterVrOnlyText(funnyTips);
        }
        return new ResolvedGroup(normalStages, normalTips, funnyStages, funnyTips);
    }

    private static String[] filterVrOnlyText(String[] values) {
        if (values == null || values.length == 0) return new String[0];
        String[] out = new String[values.length];
        int count = 0;
        for (String value : values) {
            if (TextUtils.isEmpty(value) || isVrOnlyText(value)) continue;
            out[count++] = value;
        }
        if (count == out.length) return out;
        String[] trimmed = new String[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

    private static boolean isVrOnlyText(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("vr") || lower.contains("quest") || lower.contains("headset");
    }

    private static boolean isGroupAllowed(Context context, TextGroup group) {
        return group == null || !group.vrOnly || isVrModeEnabled(context);
    }

    private static boolean isVrModeEnabled(Context context) {
        try {
            if (context == null) return false;
            SharedPreferences prefs = context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
            return prefs.getBoolean(SettingsPreferenceKeys.UI_DETECT_VR_MODE, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String[] stringsFor(Context context, int[] ids, boolean includeVrText) {
        if (ids == null || ids.length == 0 || context == null) return new String[0];
        String[] out = new String[ids.length];
        int count = 0;
        for (int id : ids) {
            if (id == 0) continue;
            if (!includeVrText && isVrOnlyStringId(id)) continue;
            try {
                String value = context.getString(id);
                if (!TextUtils.isEmpty(value)) out[count++] = value;
            } catch (Throwable ignored) {
            }
        }
        if (count == out.length) return out;
        String[] trimmed = new String[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

    private static boolean isVrOnlyStringId(int id) {
        return id == R.string.startup_tip_vr
                || id == R.string.startup_tip_vr_panels
                || id == R.string.startup_tip_vr_app_tray
                || id == R.string.startup_loading_stage_vr_gates
                || id == R.string.startup_tip_vr_panel_separation
                || id == R.string.startup_loading_stage_vr_buttons
                || id == R.string.startup_tip_vr_buttons
                || id == R.string.startup_funny_stage_vr
                || id == R.string.startup_funny_tip_vr_windows
                || id == R.string.startup_funny_stage_vr_tape
                || id == R.string.startup_funny_tip_vr_tape
                || id == R.string.startup_funny_stage_vr_button_stretch
                || id == R.string.startup_funny_tip_vr_button_stretch;
    }

    private static String[] mergeStrings(String[] first, String[] second) {
        int a = first == null ? 0 : first.length;
        int b = second == null ? 0 : second.length;
        String[] out = new String[a + b];
        if (a > 0) System.arraycopy(first, 0, out, 0, a);
        if (b > 0) System.arraycopy(second, 0, out, a, b);
        return out;
    }

    private static Random newRandom() {
        return new Random(System.nanoTime() ^ SystemClock.uptimeMillis());
    }

    static int nextTipIndexForStage(String[] tips, int currentIndex, String stage) {
        if (tips == null || tips.length == 0) return 0;
        int next = (currentIndex + 1) % tips.length;
        return balancedTipIndex(tips, next, stage);
    }

    static int balancedTipIndex(String[] tips, int preferredIndex, String stage) {
        if (tips == null || tips.length == 0) return 0;
        int start = Math.max(0, Math.min(preferredIndex, tips.length - 1));
        if (!isAwkwardShortPair(stage, tips[start])) return start;
        for (int offset = 1; offset < tips.length; offset++) {
            int index = (start + offset) % tips.length;
            if (!isAwkwardShortPair(stage, tips[index])) return index;
        }
        return start;
    }

    private static boolean isAwkwardShortPair(String stage, String tip) {
        return compactLength(stage) <= 36 && compactLength(tip) <= 52;
    }

    private static int compactLength(String value) {
        if (value == null) return 0;
        return value.trim().replaceAll("\\s+", " ").length();
    }

    private static String[] shuffledCopy(String[] source, Random random) {
        if (source == null || source.length == 0) return new String[0];
        String[] out = source.clone();
        for (int i = out.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String tmp = out[i];
            out[i] = out[j];
            out[j] = tmp;
        }
        return out;
    }

    private static String[] themedNormalStages(String label, String area, String component) {
        return new String[] {
                "Preparing " + label + " controls",
                "Checking " + area,
                "Refreshing " + component + " status",
                "Loading " + label + " actions",
                "Checking saved " + label + " choices",
                "Preparing " + component + " output",
                "Verifying " + area + " access",
                "Loading " + label + " labels",
                "Preparing " + area + " views",
                "Checking " + component + " readiness",
                "Refreshing " + label + " filters",
                "Preparing " + component + " details",
                "Checking " + area + " settings",
                "Loading " + label + " shortcuts",
                "Finalizing " + label + " setup",
                "Preparing " + label + " status chips",
                "Checking " + component + " action buttons",
                "Refreshing " + area + " summaries",
                "Loading " + label + " picker state",
                "Checking " + component + " saved filters",
                "Preparing " + area + " result labels",
                "Refreshing " + label + " output markers",
                "Checking " + component + " retry state",
                "Loading " + area + " quick actions",
                "Preparing " + label + " safety prompts",
                "Checking " + component + " empty states",
                "Refreshing " + area + " progress text",
                "Preparing " + label + " path controls",
                "Checking " + component + " backend notes",
                "Loading " + area + " detail rows",
                "Preparing " + label + " status refresh",
                "Checking " + component + " enable gates",
                "Refreshing " + area + " warning text",
                "Preparing " + label + " selection memory",
                "Checking " + component + " output limits",
                "Loading " + area + " command hints",
                "Preparing " + label + " confirmation text",
                "Checking " + component + " active state",
                "Refreshing " + area + " user actions",
                "Preparing " + label + " export notes",
                "Checking " + component + " access messages",
                "Loading " + area + " action labels",
                "Preparing " + label + " visible work only",
                "Checking " + component + " quiet state",
                "Refreshing " + area + " start and stop text",
                "Preparing " + label + " saved defaults",
                "Checking " + component + " clean failure text",
                "Loading " + area + " open choices",
                "Preparing " + label + " scrolling",
                "Checking " + component + " address fields",
                "Refreshing " + area + " package context",
                "Preparing " + label + " file context",
                "Checking " + component + " sync targets",
                "Loading " + area + " test hints",
                "Preparing " + label + " background rules",
                "Checking " + component + " no-surprise startup",
                "Refreshing " + area + " selection badges",
                "Preparing " + label + " readable messages",
                "Checking " + component + " one-tap actions",
                "Loading " + area + " safe defaults",
                "Preparing " + label + " runtime summary",
                "Checking " + component + " saved paths",
                "Refreshing " + area + " active package",
                "Preparing " + label + " manual actions",
                "Checking " + component + " automatic actions",
                "Loading " + area + " troubleshooting hints",
                "Preparing " + label + " log markers",
                "Checking " + component + " status update",
                "Refreshing " + area + " visible controls",
                "Preparing " + label + " disable gates",
                "Checking " + component + " permission notes",
                "Loading " + area + " recent choices",
                "Preparing " + label + " current session",
                "Checking " + component + " refresh cadence",
                "Refreshing " + area + " action results",
                "Preparing " + label + " final status",
                "Checking " + component + " finish state",
                "Loading " + area + " final tips",
                "Finalizing " + label + " controls",
                "Ready for " + label + " work"
        };
    }




    private static String[] themedNormalTips(String label, String area, String component) {
        String areaStart = sentenceStart(area);
        String componentStart = sentenceStart(component);
        return new String[] {
                "Select the target app, file, service, or path before using " + label + ".",
                "Check the active backend before privileged " + area + " actions.",
                "Turn on Debug Output before repeating a " + component + " problem.",
                "Read the status and output after Start, Stop, Refresh, Apply, Save, or Install.",
                "Use Refresh after changing a backend, package, file, or Android setting.",
                "Disabled " + label + " buttons are waiting for a required selection, connection, or valid input.",
                "Confirm the package, path, address, or destination before a write, delete, install, or patch.",
                "Save or archive important output before using a Clear action.",
                "Use the visible manual action when automatic sync is not needed.",
                "Check the exact saved/exported path shown in the output pane.",
                "Long operations can report progress while you continue viewing other tabs.",
                "Use filters, paging, or result caps when " + area + " returns a large list.",
                "Keep package-specific files and profiles matched to the selected app.",
                "Use Copy or Share after saving output that needs to be reported elsewhere.",
                "A nonzero command exit code usually needs the accompanying stderr message.",
                "Use normal phone/tablet controls unless VR mode is intentionally enabled.",
                "Enable remote-access features only while they are needed.",
                "Stop active services from their matching Stop action when the session is finished.",
                "Select a text field before typing; normal dropdown taps open choices without the keyboard.",
                "Check whether a setting applies immediately or after a refresh/restart.",
                areaStart + " works best when the displayed status matches the selected target.",
                componentStart + " output can identify the backend and path used for the action.",
                "Use one clean reproduction after marking a log session.",
                "Back up original files and app data before modifying or replacing them.",
                "Use the action label and confirmation text to verify exactly what will change.",
                "Keep active sync options visible so automatic updates are not surprising.",
                "When a retry fails, recheck the current target instead of assuming the old selection is still valid.",
                "Saved settings are reused; use reset or clear actions only when you want to replace them.",
                "Choose app-focused diagnostics first, then use full-system diagnostics when Android services are involved.",
                "Use /sdcard/dev.perms.test to find shared exports created by " + label + ".",
                "Funny loading text appears only when Funny Animation Tooltips is enabled.",
                label + " is ready when its status, available buttons, and selected target agree."
        };
    }

    private static String[] themedFunnyStages(String label, String component, String mascot) {
        return new String[] {
                "Reticulating " + label + " splines",
                "Teaching " + mascot + " the " + component + " map",
                "Asking " + label + " to avoid the big red button",
                "Folding tiny " + component + " pajamas",
                "Giving " + mascot + " one safe button",
                "Sprinkling " + label + " dust",
                "Sorting " + component + " snacks",
                "Handing " + mascot + " a clipboard",
                "Warming up " + label + " nonsense",
                "Polishing " + component + " dramatically",
                "Letting " + mascot + " supervise safely",
                "Moving " + label + " furniture quietly",
                "Checking " + component + " for drama",
                "Counting " + label + " gremlins",
                "Labeling " + component + " snacks",
                "Bribing " + mascot + " with logs",
                "Dusting " + label + " switches",
                "Untangling " + component + " noodles",
                "Giving " + mascot + " tiny goggles",
                "Measuring " + label + " vibes",
                "Taping " + component + " corners",
                "Hiding " + mascot + " from root",
                "Finalizing " + label + " chaos",
                "Giving " + component + " tiny boots",
                "Checking " + mascot + " paperwork",
                "Packing " + label + " snacks",
                "Untangling " + component + " shoelaces",
                "Letting " + mascot + " count dots",
                "Sweeping " + label + " crumbs",
                "Polishing " + component + " buttons",
                "Asking " + mascot + " to blink twice",
                "Stacking " + label + " pancakes",
                "Finalizing " + component + " wiggles",
                "Giving " + label + " a pocket checklist",
                "Teaching " + component + " elevator manners",
                "Letting " + mascot + " inspect the crumbs",
                "Drawing " + label + " a tiny floor plan",
                "Asking " + component + " to stop moonwalking",
                "Handing " + mascot + " safety mittens",
                "Putting " + label + " socks in order",
                "Tuning " + label + " with a rubber screwdriver",
                "Putting " + component + " in the correct drawer",
                "Asking " + mascot + " not to optimize the furniture",
                "Counting " + label + " buttons with safety goggles",
                "Handing " + component + " a polite error message",
                "Letting " + mascot + " stamp the clean path",
                "Checking " + label + " for rogue confetti",
                "Teaching " + component + " careful scrollbar manners",
                "Giving " + mascot + " a very official log bucket",
                "Finalizing " + label + " with calibrated nonsense",
                "Giving " + mascot + " a tiny release checklist",
                "Teaching " + component + " to wave politely",
                "Sorting " + label + " noodles by seriousness",
                "Letting " + mascot + " audit the button snacks",
                "Checking " + component + " for runaway glitter",
                "Putting " + label + " labels on little helmets",
                "Asking " + mascot + " to stop renaming the dots",
                "Polishing " + component + " with a clean sock",
                "Counting " + label + " widgets with a tiny abacus",
                "Letting " + mascot + " inspect the quiet path",
                "Teaching " + component + " to use its inside voice",
                "Packing " + label + " errors in bubble wrap",
                "Giving " + mascot + " a very small clipboard",
                "Measuring " + component + " with official spaghetti",
                "Asking " + label + " to keep the drama labeled",
                "Letting " + mascot + " sort the harmless chaos",
                "Teaching " + component + " the snack protocol",
                "Checking " + label + " for loose rubber ducks",
                "Giving " + mascot + " one emergency progress dot",
                "Finalizing " + component + " with responsible silliness",
                "Loading " + label + " with a tiny hard hat",
                "Asking " + component + " to bring a receipt",
                "Letting " + mascot + " test the obvious button",
                "Calibrating " + label + " snack meters",
                "Putting " + component + " warnings in tiny cones",
                "Teaching " + mascot + " polite retry manners",
                "Dusting " + label + " status crumbs",
                "Filing " + component + " gremlins under diagnostics",
                "Giving " + mascot + " a backup flashlight",
                "Ready after " + label + " stops juggling"
        };
    }



    private static String[] themedFunnyTips(String label, String area, String component, String mascot) {
        String areaStart = sentenceStart(area);
        String componentStart = sentenceStart(component);
        String mascotStart = sentenceStart(mascot);
        return new String[] {
                label + " tip: " + mascotStart + " says the log looks fine.",
                "If " + component + " glows, blame confidence.",
                mascotStart + " filed " + area + " under important buttons.",
                label + " pixels formed a tiny committee.",
                componentStart + " is less haunted with Debug Output on.",
                mascotStart + " optimized " + area + " with a broom.",
                label + " kept its elbows off the memory table.",
                "Repeated tips are probably " + mascot + " reruns.",
                componentStart + " gremlins got snacks and a warning.",
                label + " calibrated logs and vibes.",
                mascotStart + " recommends naps before stack traces.",
                "No packages were harmed by this " + component + ".",
                label + " is almost ready; screws are being sorted.",
                mascotStart + " marked " + component + " as spicy.",
                label + " found a tiny wrench and got excited.",
                "The " + area + " broom has official paperwork.",
                componentStart + " snacks are sorted by byte order.",
                mascotStart + " promised not to press unknown buttons.",
                label + " requested one dramatic progress dot.",
                "The " + component + " goblin passed safety training.",
                mascotStart + " says the logs need a snack break.",
                label + " folded the checklist twice.",
                areaStart + " is loading with professional silliness.",
                mascotStart + " checked " + component + " with a tiny flashlight.",
                label + " found a spare progress dot under the couch.",
                "The " + area + " checklist now has snack approval.",
                componentStart + " was measured with official noodles.",
                mascotStart + " promised to keep buttons unsuspicious.",
                label + " filed its nonsense in alphabetical order.",
                "The " + component + " drawer contains emergency confetti.",
                areaStart + " passed the rubber duck inspection.",
                mascotStart + " says one more log should do it.",
                label + " is almost done pretending to be serious.",
                mascotStart + " approved the scroll lane with a tiny stamp.",
                "The " + component + " checklist now has snack tabs.",
                label + " keeps mysterious wiggles out of startup.",
                mascotStart + " put the panic button in a drawer.",
                "The " + area + " gremlin signed the quiet-work form.",
                componentStart + " wore a reflective vest for safety.",
                label + " found an extra semicolon and named it Steve.",
                mascotStart + " sorted logs by dramatic eyebrow level.",
                "The " + component + " noodle passed inspection.",
                areaStart + " is almost ready; the duck is nodding.",
                mascotStart + " checked the gate and refused to guess.",
                "The " + component + " retry button brought paperwork.",
                label + " filed logs under useful chaos.",
                "The " + area + " checklist passed snack review.",
                mascotStart + " says clear errors beat mysterious vibes.",
                "The " + component + " fallback wore a name tag.",
                label + " keeps related controls easy to find.",
                mascotStart + " inspected VR paths with tiny cones.",
                "The " + area + " gremlin only runs when enabled.",
                componentStart + " promised not to spam the log window.",
                label + " gave every progress dot a job.",
                mascotStart + " approved the clearly labeled controls.",
                "The " + component + " scrollbar practiced staying visible.",
                areaStart + " passed the no-surprise-startup test.",
                mascotStart + " stamped the bug report with crumbs.",
                label + " found a button doing pushups and told it to wait.",
                mascotStart + " says clear status text tastes better than mystery soup.",
                "The " + component + " gremlin brought a receipt for every retry.",
                areaStart + " wore safety goggles and only bumped one table.",
                label + " sorted its warnings by dramatic hat size.",
                mascotStart + " checked the backend door and rang the polite bell.",
                "The " + component + " checklist refused to guess and requested logs.",
                areaStart + " passed inspection after the duck stopped interrupting.",
                label + " found a tiny loading spinner and gave it a union break.",
                mascotStart + " filed the progress dots under mostly useful.",
                "The " + component + " fallback wore clean socks this time.",
                areaStart + " says background work should not tap dance on the UI thread.",
                label + " labeled every crumb before exporting the snack report.",
                mascotStart + " audited the empty state and found one sleepy semicolon.",
                "The " + component + " status line brought its own tiny flashlight.",
                areaStart + " kept its paws off saved settings unless invited.",
                label + " polished the error message until it stopped mumbling.",
                mascotStart + " asked the retry path to bring quieter shoes.",
                "The " + component + " drawer now contains one certified backup noodle.",
                label + " brought a backup sandwich and labeled it logs.",
                mascotStart + " says one clean error beats six spooky ones.",
                componentStart + " got a tiny map so it stops wandering.",
                areaStart + " packed a flashlight, a receipt, and no surprise work.",
                label + " finished startup with only responsible nonsense."
        };
    }



    private static String[] themedVrNormalStages(String label, String area, String component) {
        return new String[] {
                "Checking " + label + " VR panel gates",
                "Preparing headset-safe " + component + " controls",
                "Verifying VR-only " + area + " routing",
                "Loading " + label + " headset restore state",
                "Binding VR-compatible " + component + " actions",
                "Refreshing headset " + area + " status",
                "Checking " + label + " VR input guards",
                "Preparing VR-safe " + component + " labels",
                "Verifying headset " + area + " defaults",
                "Finalizing " + label + " VR startup checks"
        };
    }

    private static String[] themedVrNormalTips(String label, String area, String component) {
        return new String[] {
                "VR-specific " + label + " tips appear only while VR mode is enabled.",
                "Use VR-compatible panels when raw overlays are not suitable for the headset.",
                "Refresh " + area + " status after returning from an Android or headset settings screen.",
                "Close a VR panel from its own close control to avoid duplicate windows.",
                "Verify the selected package/process before returning to a target app.",
                "Use the same shared controls and backends as normal mode unless the headset needs a different panel surface.",
                "VR App Tray and panel input options do not change normal phone/tablet navigation while VR mode is off.",
                "Keep service notifications enabled while a matching VR panel/service is active.",
                "If a headset panel is clipped, use the VR-safe button and scrollbar settings.",
                "Disable VR mode when testing normal tablet or phone behavior."
        };
    }

    private static String[] themedVrFunnyStages(String label, String component, String mascot) {
        return new String[] {
                "Giving " + mascot + " headset goggles",
                "Measuring " + label + " VR buttons twice",
                "Teaching " + component + " not to float away",
                "Putting " + mascot + " in headset-safe socks",
                "Checking " + label + " for tiny VR cones",
                "Asking " + component + " to stay in its panel",
                "Letting " + mascot + " sweep headset confetti",
                "Stretching " + label + " pixels carefully",
                "Giving " + component + " a VR name tag",
                "Finalizing " + label + " headset nonsense"
        };
    }

    private static String[] themedVrFunnyTips(String label, String area, String component, String mascot) {
        String areaStart = sentenceStart(area);
        String componentStart = sentenceStart(component);
        String mascotStart = sentenceStart(mascot);
        return new String[] {
                mascotStart + " says headset text should only appear in VR mode.",
                label + " VR pixels brought tiny cones and a clipboard.",
                componentStart + " promised not to chew the headset window edges.",
                areaStart + " got VR goggles but left tablet mode alone.",
                mascotStart + " checked the VR gate and refused to sneak through.",
                label + " headset buttons stretched before doing serious work.",
                componentStart + " packed a tiny map for panel routing.",
                areaStart + " uses VR jokes only when the headset switch says yes.",
                mascotStart + " put normal tablet messages back on the shelf.",
                label + " finished VR setup with responsible headset silliness."
        };
    }


    /*
     * Default extras are written explicitly because they describe concrete PermsTest
     * startup, tab, backend, editor, service, and safety paths across the whole app.
     */
    private static String[] defaultExtraNormalStages() {
        return new String[] {
                "Checking selected execution mode",
                "Refreshing Shizuku and LADB status",
                "Preparing Internal Shizuku controls",
                "Checking Wireless Debugging hints",
                "Loading package action summaries",
                "Preparing permission and app-state tools",
                "Checking installer and split-APK options",
                "Preparing file browser panes",
                "Refreshing file-open handlers",
                "Checking shell command output",
                "Preparing memory target selectors",
                "Loading scan and next-scan controls",
                "Preparing patch and freeze actions",
                "Checking payload folders and masks",
                "Preparing live Hex Editor controls",
                "Checking Disassembly auto-action sync",
                "Refreshing memory details output",
                "Preparing VR-compatible panels",
                "Checking Special Tools actions",
                "Preparing FTP server controls",
                "Preparing FTP client views",
                "Checking HTTP root and index editor",
                "Loading Web Interface access gates",
                "Checking Multiplayer Link controls",
                "Preparing script editor actions",
                "Loading smali and APK debugging tools",
                "Checking MITM and report actions",
                "Preparing file Hex Editor rows",
                "Preparing Save Data configs and backups",
                "Checking Config JSON editor state",
                "Opening lifetime log buffers",
                "Preparing logcat capture buttons",
                "Preparing log archive and cleanup actions",
                "Checking Debug Output markers",
                "Preparing plugin manager controls",
                "Checking staged plugin folders",
                "Loading plugin package manifests",
                "Preparing plugin action cards",
                "Checking plugin enable states",
                "Preparing plugin import and staging paths",
                "Checking bundled diagnostic plugin packages",
                "Checking bundled Calculator plugin package",
                "Checking bundled ASCII / Hex plugin package",
                "Checking bundled Alarms and Timers plugin package",
                "Checking bundled plugin restore packages",
                "Preparing shared VR/popout app-wide panel host",
                "Loading plugin startup preferences",
                "Preparing plugin menu options",
                "Checking plugin debug log markers",
                "Preparing About API documentation buttons",
                "Checking Android API reference notes",
                "Checking backend API reference notes",
                "Checking APK debugging API reference notes",
                "Checking plugin API reference notes",
                "Applying theme and layout choices",
                "Checking update channel settings",
                "Preparing downloaded update handling",
                "Checking device profile text",
                "Preparing startup tooltip category",
                "Checking service notification restore",
                "Preparing Home Mode launcher action",
                "Checking safe startup cleanup",
                "Finalizing PermsTest startup"
        };
    }



    private static String[] defaultExtraNormalTips() {
        return new String[] {
                "Confirm the backend status chip before running privileged actions.",
                "Internal Shizuku needs Wireless Debugging for pairing and endpoint discovery.",
                "If Wireless Debugging changes its port, refresh or reconnect before retrying commands.",
                "LADB pairing and connection state are separate from Shizuku state.",
                "Use Debug Output and Mark Session before repeating a problem.",
                "Disabled plugins remain staged but do not expose action buttons.",
                "Bundled plugin choices start unchecked; restore only the ones you select.",
                "Use Refresh List after manually changing staged plugin files.",
                "Validate a plugin before Save or Package.",
                "Shell/script plugin actions run through the active execution backend.",
                "Use Large Window Override to change plugin presentation without editing plugin.json.",
                "Archive Logs preserves available logs before Clear Logs removes current log files.",
                "Save Output stores the visible output pane; Save To File stores the next raw Logcat capture.",
                "Turn on Enable Lifetime Log before using Lifetime Log actions.",
                "Package installs keep single APK and split-archive handling separate.",
                "Allow Downgrade does not bypass Android signature checks.",
                "Long-press file rows for Open With, install, copy, move, rename, delete, path, or properties.",
                "Shell quick actions use the active backend and report command results.",
                "Pick the Memory target package and process before scanning or patching.",
                "Use Unknown/Snapshot when the first memory value is not known.",
                "Use Next Scan after the target value changes.",
                "Patch only checked rows after confirming address, type, and value.",
                "Keep saved patches and payloads matched to the selected package.",
                "Use Hex Editor to inspect nearby bytes before a live memory write.",
                "VR-compatible panels are for headset-safe windows, not normal tablet changes.",
                "Enable FTP/HTTP background or sleep modes only while remote access must remain active.",
                "Web Interface sections stay unavailable until they are enabled and saved.",
                "Scripts run through the selected backend; review commands before running them.",
                "Use Open Any only when editing a smali file outside the current decoded workspace.",
                "Test debug/rebuilt APKs on copies and keep the original source.",
                "Save Data Editor creates backups before supported writes.",
                "Mark Session before a test to make exported lifetime logs easier to read.",
                "Clear Cache On Startup removes managed temporary files without deleting exports.",
                "Home Mode opens Android launcher selection after the launcher alias is enabled.",
                "Use app-focused Logcat first and full Logcat when Android services are involved.",
                "Confirm the target before high-impact install, delete, patch, or write actions."
        };
    }

    private static String[] defaultVrNormalStages() {
        return new String[] {
                "Checking VR-compatible panel gates",
                "Preparing headset-safe Memory controls",
                "Verifying VR overlay restore flags",
                "Loading Quest panel state",
                "Binding VR App Tray input guards",
                "Refreshing headset window status",
                "Checking VR-only tooltip filters",
                "Preparing panel-safe Hex controls",
                "Verifying Disassembly panel routing",
                "Loading headset button-size defaults",
                "Checking VR return-to-target state",
                "Preparing panel overlay notifications",
                "Refreshing headset package picker state",
                "Verifying VR keyboard focus",
                "Loading VR-safe Special Tools routing",
                "Checking headset service restore flags",
                "Preparing VR target relaunch guards",
                "Preparing headset-compatible scrolling",
                "Verifying VR panel close behavior",
                "Finalizing VR-specific startup text"
        };
    }

    private static String[] defaultVrNormalTips() {
        return new String[] {
                "VR startup tips appear only when Enable VR Mode is enabled.",
                "Use VR-compatible panels when headset overlays are not suitable.",
                "The selected Memory target is shared across VR Memory, Hex, Disassembly, and Special Tools panels.",
                "Refresh service and backend status after returning from Android VR settings.",
                "Close each panel from its own close control to avoid duplicate windows.",
                "Confirm the selected package before using Return To Target or relaunch actions.",
                "Keep notifications allowed while an active VR panel/service needs restoration controls.",
                "Use VR-safe button sizing and scrollbars when headset windows clip controls.",
                "VR App Tray input diagnostics remain separate from normal tablet navigation.",
                "Disable VR mode when comparing standard phone or tablet behavior."
        };
    }

    private static String[] defaultVrFunnyStages() {
        return new String[] {
                "Handing VR panels tiny helmets",
                "Measuring headset buttons twice",
                "Teaching Quest windows to stay put",
                "Putting overlay gremlins behind VR cones",
                "Giving Hex panel pixels a pep talk",
                "Asking Disassembly panels not to float away",
                "Packing headset scroll thumbs in bubble wrap",
                "Teaching VR App Tray buttons indoor voices",
                "Sorting Quest panel snacks by window size",
                "Giving headset keyboard focus a flashlight",
                "Polishing VR target-return breadcrumbs",
                "Putting panel notifications on a leash",
                "Asking headset dropdowns to stop wandering",
                "Giving Special Tools a VR name tag",
                "Counting panel windows with tiny goggles",
                "Tucking raw overlays into a headset-safe blanket",
                "Teaching VR buttons to stretch politely",
                "Letting headset hamsters check the scroll lane",
                "Asking Quest panels to bring readable labels",
                "Finalizing headset silliness behind the VR gate"
        };
    }

    private static String[] defaultVrFunnyTips() {
        return new String[] {
                "The headset hamster only tells VR jokes after Enable VR Mode says yes.",
                "VR panels brought tiny helmets so normal tablet buttons can relax.",
                "Quest windows agreed to stay in their panel instead of wandering into raw overlay traffic.",
                "The VR cone crew blocks headset-only text from sneaking onto tablet startup cards.",
                "Hex panel pixels stretched carefully and promised not to chew clipped labels.",
                "Disassembly panels stopped floating away after the scroll thumb gave them a map.",
                "The headset scroll thumb brought bubble wrap and a very serious clipboard.",
                "VR App Tray buttons practiced indoor voices behind the debug gate.",
                "Quest panel snacks are sorted by window size and emotional stability.",
                "The headset keyboard flashlight checks focus without waking normal dialogs.",
                "VR target breadcrumbs glow only when the return path is actually needed.",
                "Panel notifications wear leashes so they come back only while the service is awake.",
                "Headset dropdowns promised to stop wandering after one tiny stern meeting.",
                "Special Tools got a VR name tag and stopped borrowing tablet shoes.",
                "The panel-window counter wears goggles and only counts active headset surfaces.",
                "Raw overlays are tucked into a headset-safe blanket when panel mode takes over.",
                "VR buttons stretch politely and leave tablet layouts exactly where they found them.",
                "The headset hamster checked the scroll lane and stamped it mostly safe.",
                "Quest panels brought readable labels and one emergency progress dot.",
                "Headset silliness stays behind the VR gate like a responsible tiny goblin."
        };
    }


    private static String[] defaultExtraFunnyStages() {
        return new String[] {
                "Reticulating package socks",
                "Teaching the update gnome",
                "Folding smali paper cranes",
                "Asking overlays to whisper",
                "Polishing the Shizuku bell",
                "Convincing logcat indoors",
                "Alphabetizing payload gremlins",
                "Giving FileProvider a hat",
                "Stretching VR panels",
                "Sorting APKs by crunch",
                "Feeding Web Interface a cookie",
                "Dusting the memory scanner",
                "Counting tooltip hamsters",
                "Scanning plugin nests for tiny manifests",
                "Giving plugin penguins matching name tags",
                "Checking staged plugin shoeboxes",
                "Sorting plugin menus by snack level",
                "Teaching plugin cards to wait until startup is ready",
                "Dusting bundled .ptp test plugins",
                "Putting plugin toggles in labeled jars",
                "Handing plugin debug logs a tiny flashlight",
                "Asking plugin actions to use the front door",
                "Counting plugin enable states twice",
                "Dusting the API guide shelf",
                "Teaching API buttons to use clean labels",
                "Making backend notes wear safety goggles",
                "Taping overlay corners",
                "Packing Settings sandwiches",
                "Hiding the noisy ellipsis",
                "Training payload pigeons",
                "Washing cache socks",
                "Labeling tiny reset buttons",
                "Bribing the byte goblin",
                "Watering the log garden",
                "Teaching log archives to zip politely",
                "Checking the snack register",
                "Teaching JSON brackets manners",
                "Giving bash scripts tiny boots",
                "Coloring HTML with crayons",
                "Asking CSS to sit still",
                "Waking the JavaScript squirrel",
                "Packing editor breadcrumbs",
                "Counting line-index raccoons",
                "Stretching viewport hamsters",
                "Tuning syntax kazoo levels",
                "Checking save-path parachutes",
                "Teaching layout fragments jazz hands",
                "Polishing the exec-mode doorknob",
                "Asking scrollbars where they went",
                "Giving checkbox goblins a checklist",
                "Packing VR panels tiny lunchboxes",
                "Dusting stale XML breadcrumbs",
                "Training quiet tools to use inside voices",
                "Counting startup llamas twice",
                "Putting startup notes in clean bins",
                "Checking version badges with a tiny ruler",
                "Teaching scrollbars to stay on stage",
                "Giving HTTP editor a folding chair",
                "Asking package buttons to form a line",
                "Letting foreground jobs wear name tags",
                "Packing opened URIs in bubble wrap",
                "Marching payload queues past the clipboard",
                "Putting Web Interface tokens in a cookie jar",
                "Warming Network services with tiny socks",
                "Teaching smali search to remember politely",
                "Giving Settings mirrors a pep talk",
                "Tucking Save Data paths into labeled bins",
                "Handing shell history a quiet notebook",
                "Finalizing startup tips with a torque wrench",
                "Checking About version label",
                "Finalizing startup checks with snack tape",
                "Asking APK splits to line up by ABI",
                "Giving the installer a tiny receipt printer",
                "Teaching package rows their debuggable colors",
                "Handing delete confirmations a safety clipboard",
                "Putting FTP progress on a leash",
                "Asking HTTP imports to wipe their shoes",
                "Locking Web Interface gates with tiny labels",
                "Counting memory session crumbs carefully",
                "Giving payload preserve reads a backup lantern",
                "Combing Hex Editor rows into neat columns",
                "Drawing arrows on disassembly target signs",
                "Putting Save Data backups in labeled bunk beds",
                "Teaching script arguments not to wander off",
                "Giving shell quick actions quotation helmets",
                "Stamping log export filenames with tiny dates",
                "Asking theme colors to pass the squint test",
                "Sorting update candidates by tiny release hats",
                "Giving silent installs a quiet failure bell",
                "Measuring VR panel flags with a headset ruler",
                "Training overlay notifications to come back politely",
                "Putting Home Mode launcher hats on the right hook",
                "Checking Shizuku permission shoes",
                "Giving LADB pairing a separate toothbrush",
                "Dusting internal Shizuku endpoint crumbs",
                "Teaching backend buttons to tell the truth",
                "Putting service state in labeled jars",
                "Giving text editor intents a clean doorway",
                "Sorting syntax hats by file mood",
                "Numbering memory pages with tiny pencils",
                "Asking result checkboxes to stay on their page",
                "Polishing internal Shizuku status shoes",
                "Giving Pair and Start a very obvious sign",
                "Teaching privileged commands to name their backend",
                "Sorting permission beans into special jars",
                "Handing install dialogs a safety whistle",
                "Giving update downloads a finish-line ribbon",
                "Putting device-only tooltips behind a velvet rope",
                "Teaching tooltip filters to read the room",
                "Giving Open With a polite introduction",
                "Separating log capture ducks by pond",
                "Measuring Network roots with a tiny yardstick",
                "Asking startup defaults not to touch user snacks",
                "Teaching action buttons to explain themselves",
                "Finalizing default text with useful loading tips"
        };
    }


    private static String[] defaultExtraFunnyTips() {
        return new String[] {
                "The update gnome checks APKs for snack behavior.",
                "Reset Windows shows oversized overlays the floor plan.",
                "The smali editor packed lunch for long searches.",
                "Shizuku is warming under a diagnostic heat lamp.",
                "The payload gremlin has a clipboard and a promise.",
                "FileProvider opened the URI door and used coasters.",
                "Logcat is practicing indoor volume for one sticker.",
                "The installer wore a helmet for split APK options.",
                "VR panels are exploring window real estate.",
                "Web Interface promised not to start FTP uninvited.",
                "Memory scanner socks are paired by address.",
                "The cache broom sweeps only approved corners.",
                "A tooltip hamster shortened every long sentence.",
                "The reset button brought a tiny tape measure.",
                "Payload toast counted wins and losses politely.",
                "Settings labels got room without moving the furniture.",
                "The ellipsis was escorted out by a layout inspector.",
                "A byte goblin sorted masks by dramatic value.",
                "The log garden grew three timestamps overnight.",
                "The log archive zipper saved the report before the broom arrived.",
                "Startup cleanup remembered which temp folders are managed.",
                "Plugin penguins wait outside until the core app says startup is complete.",
                "The plugin manager checks manifests before handing out buttons.",
                "Disabled plugin cards keep their action snacks in the cupboard.",
                "The bundled .ptp plugins brought device and log notes with receipts.",
                "Plugin imports unpack in staging instead of tap dancing in assets.",
                "The hamburger menu stores plugin options without eating the X button.",
                "Enable All and Disable All herd plugin penguins without deleting nests.",
                "Large-window overrides are staying movable and out of the way.",
                "Plugin debug logs carry a flashlight through refresh, import, and action paths.",
                "The plugin card drawer closes quietly when a handler is unsupported.",
                "The overlay header learned a new refresh trick.",
                "Disabled automation stayed asleep and did not steal any startup snacks.",
                "The JSON brackets agreed to close themselves politely.",
                "Bash scripts put on boots before running anywhere.",
                "HTML brought snacks, CSS brought matching napkins.",
                "JavaScript promised not to juggle the UI thread.",
                "The editor raccoon indexed lines with tiny flags.",
                "Viewport hamsters only render what they can see.",
                "Syntax crayons are now stored in reusable boxes.",
                "The save-path parachute was inspected twice.",
                "Large files brought a map instead of a suitcase.",
                "The ANR goblin was asked to wait outside.",
                "The layout fragment ferret found two old crumbs.",
                "Exec-mode partials asked for name tags and snacks.",
                "The scrollbar detective brought a magnifying glass.",
                "Checkbox goblins now wait for permission slips.",
                "VR panels packed separate tiny helmets.",
                "The XML broom swept only stale files.",
                "Tool controls put their labels where they are easy to find.",
                "Startup llamas counted tooltips without biting anyone.",
                "Startup status lines were sorted by severity.",
                "The version badge stopped pretending it was just 1.0.",
                "The scrollbar thumb promised to remain visible and less dramatic.",
                "HTTP editor brought its own chair and stopped blocking the hallway.",
                "Package buttons lined up without changing the selected app.",
                "Foreground jobs put progress notes on tiny sticky pads.",
                "The opened URI wore bubble wrap and a readable filename.",
                "Payload queues waited their turn like civilized gremlins.",
                "Web Interface tokens stayed in the jar until enabled.",
                "Network services labeled their socks HTTP and FTP.",
                "Smali search remembered the right file and acted casual.",
                "Settings mirrors checked the box only when asked.",
                "Save Data bins stopped mixing backups with test crumbs.",
                "Shell history wrote notes instead of rerunning chaos.",
                "Startup tips passed the app-specific wording check.",
                "The About version label reports the current package version.",
                "The startup sequence got a clean status marker.",
                "The ABI goblin sorted split APK socks and wrote down which one was missing.",
                "The installer callback brought a receipt so failures stop disappearing into the couch.",
                "The package row painter kept debuggable blue away from random decoration duty.",
                "The delete dialog put on a helmet before touching real files.",
                "The FTP progress bar promised not to steal the server status microphone.",
                "The HTTP import ferret copied the file before showing it to the browser.",
                "The Web Interface gatekeeper only serves tabs that actually have a saved pass.",
                "The memory session broom sweeps scan files only when cleanup asks nicely.",
                "The payload preserve lantern checks real bytes before trusting spooky shadows.",
                "The Hex Editor combed its rows so ASCII and hex stop arguing in public.",
                "The disassembly arrow waits for a valid address before yelling follow me.",
                "The Save Data backup mole labels every tunnel by package and profile.",
                "The script runner reads Args fresh because stale arguments are dramatic little gremlins.",
                "The shell quote helmet prevents paths with spaces from doing parkour.",
                "The log export stamp helps troubleshooting match the reproduced issue.",
                "The theme goblin passed the squint test before touching readable text.",
                "The update picker made releases, prereleases, and downgrades stand in separate lines.",
                "The silent installer rings a tiny bell instead of opening surprise doors.",
                "The VR panel hamster stays behind the VR gate and leaves tablets alone.",
                "The overlay notification boomerang only flies while its service is still awake.",
                "The launcher hat rack opens Android selection after Home Mode sets the hook.",
                "The Shizuku permission shoes are tied only after the server answers.",
                "The LADB toothbrush stays in its own cup, far away from Shizuku state.",
                "The internal Shizuku crumbs get swept after a verified endpoint is found.",
                "The backend button goblin now wears a badge that matches the active mode.",
                "The service-state jars are labeled running, stopped, and please refresh politely.",
                "The text editor doorway lets plain text in before syntax hats start dancing.",
                "Syntax hats are optional; plain text gets the comfy chair by default.",
                "The memory pager counts visible rows without stealing hidden selections.",
                "The result checkbox herd stays on the current page and avoids mystery patches.",
                "Internal Shizuku status shoes only turn green after the connection is ready.",
                "Pair and Start got a bigger sign so nobody has to hunt through Settings caves.",
                "Privileged commands now introduce their backend before doing magic tricks.",
                "Permission beans stay sorted so special access stops impersonating runtime grants.",
                "Install dialogs brought whistles, helmets, and zero surprise package writes.",
                "Update downloads get a ribbon only after crossing the actual finish line.",
                "Device-only tooltips wait behind the rope until their device mode is invited.",
                "Tooltip filters read the room and stop telling tablet users headset jokes.",
                "Open With shook hands with unknown files and promised not to steal APK installs.",
                "Log ducks swim in app-focused and full-system ponds with tiny labels.",
                "Network root yardsticks measure FTP, HTTP, and browser paths without guessing.",
                "Startup defaults brought polite gloves and left saved user snacks alone.",
                "The action buttons labeled their drawers so every tool can find its settings.",
                "The loading tips brought useful app instructions."
        };
    }


    private static int[] normalStages() {
        return new int[] {
                R.string.startup_loading_stage_packages,
                R.string.startup_loading_stage_memory,
                R.string.startup_loading_stage_files,
                R.string.startup_loading_stage_network,
                R.string.startup_loading_stage_shell,
                R.string.startup_loading_stage_debugging,
                R.string.startup_loading_stage_settings,
                R.string.startup_loading_stage_web,
                R.string.startup_loading_stage_main,
                R.string.startup_loading_stage_install,
                R.string.startup_loading_stage_fileopen,
                R.string.startup_loading_stage_save_data,
                R.string.startup_loading_stage_scripts,
                R.string.startup_loading_stage_logging,
                R.string.startup_loading_stage_theme,
                R.string.startup_loading_stage_link,
                R.string.startup_loading_stage_panels,
                R.string.startup_loading_stage_services,
                R.string.startup_loading_stage_update,
                R.string.startup_loading_stage_payload_editor,
                R.string.startup_loading_stage_smali_service,
                R.string.startup_loading_stage_debug_paths,
                R.string.startup_loading_stage_cache,
                R.string.startup_loading_stage_install_sources,
                R.string.startup_loading_stage_http_service,
                R.string.startup_loading_stage_ftp_client,
                R.string.startup_loading_stage_file_icons,
                R.string.startup_loading_stage_notifications,
                R.string.startup_loading_stage_permissions,
                R.string.startup_loading_stage_payload_apply,
                R.string.startup_loading_stage_hex_sync,
                R.string.startup_loading_stage_web_access,
                R.string.startup_loading_stage_output,
                R.string.startup_loading_stage_release_selector,
                R.string.startup_loading_stage_update_channel,
                R.string.startup_loading_stage_silent_install,
                R.string.startup_loading_stage_release_assets,
                R.string.startup_loading_stage_apk_filter,
                R.string.startup_loading_stage_update_staging,
                R.string.startup_loading_stage_backend_installer,
                R.string.startup_loading_stage_diagnostics_sync,
                R.string.startup_loading_stage_smali_workspace,
                R.string.startup_loading_stage_payload_state,
                R.string.startup_loading_stage_web_bridge,
                R.string.startup_loading_stage_file_helpers,
                R.string.startup_loading_stage_vr_gates,
                R.string.startup_loading_stage_network_keepalive,
                R.string.startup_loading_stage_log_tail,
                R.string.startup_loading_stage_memory_running_labels,
                R.string.startup_loading_stage_device_os_badge,
                R.string.startup_loading_stage_package_cache_fastpath,
                R.string.startup_loading_stage_update_picker_paths,
                R.string.startup_loading_stage_smali_intents,
                R.string.startup_loading_stage_install_debug_logs,
                R.string.startup_loading_stage_memory_dropdown_modes,
                R.string.startup_loading_stage_about_layout,
                R.string.startup_loading_stage_privileged_shell,
                R.string.startup_loading_stage_payload_masks_verify,
                R.string.startup_loading_stage_file_provider,
                R.string.startup_loading_stage_network_binders,
                R.string.startup_loading_stage_vr_buttons,
                R.string.startup_loading_stage_package_extractors,
                R.string.startup_loading_stage_settings_defaults
        };
    }

    private static int[] funnyStages() {
        return new int[] {
                R.string.startup_funny_stage_caps,
                R.string.startup_funny_stage_sockets,
                R.string.startup_funny_stage_overlays,
                R.string.startup_funny_stage_splits,
                R.string.startup_funny_stage_payloads,
                R.string.startup_funny_stage_shizuku,
                R.string.startup_funny_stage_logs,
                R.string.startup_funny_stage_vr,
                R.string.startup_funny_stage_byte_soup,
                R.string.startup_funny_stage_socket_manners,
                R.string.startup_funny_stage_payload_stretch,
                R.string.startup_funny_stage_apk_shoelaces,
                R.string.startup_funny_stage_log_polite,
                R.string.startup_funny_stage_panel_behave,
                R.string.startup_funny_stage_shell_dust,
                R.string.startup_funny_stage_split_spaghetti,
                R.string.startup_funny_stage_tiny_cones,
                R.string.startup_funny_stage_startup_transition,
                R.string.startup_funny_stage_update_gnome,
                R.string.startup_funny_stage_smali_hammock,
                R.string.startup_funny_stage_cache_broom,
                R.string.startup_funny_stage_payload_crayons,
                R.string.startup_funny_stage_hex_lantern,
                R.string.startup_funny_stage_api_pigeon,
                R.string.startup_funny_stage_permissions_pep,
                R.string.startup_funny_stage_debug_snails,
                R.string.startup_funny_stage_http_cookies,
                R.string.startup_funny_stage_ftp_backpack,
                R.string.startup_funny_stage_update_ladder,
                R.string.startup_funny_stage_smali_map,
                R.string.startup_funny_stage_log_bubbles,
                R.string.startup_funny_stage_log_zipper,
                R.string.startup_funny_stage_overlay_tape,
                R.string.startup_funny_stage_install_helmet,
                R.string.startup_funny_stage_release_oracle,
                R.string.startup_funny_stage_pre_gremlins,
                R.string.startup_funny_stage_update_shoes,
                R.string.startup_funny_stage_asset_shelf,
                R.string.startup_funny_stage_backend_tea,
                R.string.startup_funny_stage_smali_laundry,
                R.string.startup_funny_stage_payload_weather,
                R.string.startup_funny_stage_vr_tape,
                R.string.startup_funny_stage_log_fountain,
                R.string.startup_funny_stage_network_socks,
                R.string.startup_funny_stage_hex_mittens,
                R.string.startup_funny_stage_permission_lantern,
                R.string.startup_funny_stage_file_biscuit,
                R.string.startup_funny_stage_shell_parade,
                R.string.startup_funny_stage_cache_sweater,
                R.string.startup_funny_stage_dropdown_sprint,
                R.string.startup_funny_stage_ptrace_hat,
                R.string.startup_funny_stage_release_fishing,
                R.string.startup_funny_stage_smali_confetti,
                R.string.startup_funny_stage_vr_button_stretch,
                R.string.startup_funny_stage_shell_marshmallows,
                R.string.startup_funny_stage_hex_spoons,
                R.string.startup_funny_stage_payload_bubbles,
                R.string.startup_funny_stage_log_hamster,
                R.string.startup_funny_stage_file_umbrella,
                R.string.startup_funny_stage_network_pancakes,
                R.string.startup_funny_stage_package_socks,
                R.string.startup_funny_stage_settings_garden,
                R.string.startup_funny_stage_update_wagon,
                R.string.startup_funny_stage_cache_sandcastle
        };
    }

    private static int[] normalTips() {
        return new int[] {
                R.string.startup_tip_shizuku,
                R.string.startup_tip_debug,
                R.string.startup_tip_vr,
                R.string.startup_tip_network,
                R.string.startup_tip_memory,
                R.string.startup_tip_web,
                R.string.startup_tip_packages,
                R.string.startup_tip_files,
                R.string.startup_tip_shell,
                R.string.startup_tip_split_apks,
                R.string.startup_tip_payloads,
                R.string.startup_tip_hex,
                R.string.startup_tip_disassembly,
                R.string.startup_tip_save_data,
                R.string.startup_tip_http_root,
                R.string.startup_tip_ftp,
                R.string.startup_tip_multiplayer,
                R.string.startup_tip_logging,
                R.string.startup_tip_home_mode,
                R.string.startup_tip_settings,
                R.string.startup_tip_web_memory,
                R.string.startup_tip_debugging,
                R.string.startup_tip_scan_unknown,
                R.string.startup_tip_scan_next,
                R.string.startup_tip_patch_values,
                R.string.startup_tip_extract_apk,
                R.string.startup_tip_build_debug,
                R.string.startup_tip_smali_search,
                R.string.startup_tip_ftp_client,
                R.string.startup_tip_http_index,
                R.string.startup_tip_logs_export,
                R.string.startup_tip_log_archive,
                R.string.startup_tip_vr_panels,
                R.string.startup_tip_file_open_handler,
                R.string.startup_tip_service_restore,
                R.string.startup_tip_shizuku_status,
                R.string.startup_tip_package_dropdown,
                R.string.startup_tip_files_context,
                R.string.startup_tip_http_access_gates,
                R.string.startup_tip_memory_freeze,
                R.string.startup_tip_disasm_targets,
                R.string.startup_tip_scripts_editor,
                R.string.startup_tip_debugging_mitm,
                R.string.startup_tip_save_data_backups,
                R.string.startup_tip_web_sleep,
                R.string.startup_tip_install_staging,
                R.string.startup_tip_output_height,
                R.string.startup_tip_theme_custom,
                R.string.startup_tip_vr_app_tray,
                R.string.startup_tip_update_checker,
                R.string.startup_tip_custom_update_server,
                R.string.startup_tip_update_download_cleanup,
                R.string.startup_tip_debugging_open_any,
                R.string.startup_tip_smali_service_search,
                R.string.startup_tip_debugging_package_paths,
                R.string.startup_tip_external_smali_open,
                R.string.startup_tip_payload_editor_colors,
                R.string.startup_tip_payload_preserve_mask,
                R.string.startup_tip_details_scroll,
                R.string.startup_tip_disasm_open_hex,
                R.string.startup_tip_memory_package_refresh,
                R.string.startup_tip_startup_cleanup,
                R.string.startup_tip_about_update_button,
                R.string.startup_tip_managed_update_install,
                R.string.startup_tip_release_selector,
                R.string.startup_tip_prerelease_channel,
                R.string.startup_tip_allow_downgrade_picker,
                R.string.startup_tip_auto_update_channel,
                R.string.startup_tip_silent_update_backend,
                R.string.startup_tip_update_staging_cleanup,
                R.string.startup_tip_smali_open_any,
                R.string.startup_tip_debugging_service_jobs,
                R.string.startup_tip_memory_payload_masks,
                R.string.startup_tip_hex_address_sync,
                R.string.startup_tip_web_interface_gates,
                R.string.startup_tip_ftp_background,
                R.string.startup_tip_files_shizuku_copy,
                R.string.startup_tip_vr_panel_separation,
                R.string.startup_tip_lifetime_log,
                R.string.startup_tip_memory_running_labels,
                R.string.startup_tip_device_os_badge,
                R.string.startup_tip_package_cache_fastpath,
                R.string.startup_tip_update_picker_paths,
                R.string.startup_tip_smali_intents,
                R.string.startup_tip_install_debug_logs,
                R.string.startup_tip_memory_dropdown_modes,
                R.string.startup_tip_about_layout,
                R.string.startup_tip_privileged_shell,
                R.string.startup_tip_payload_masks_verify,
                R.string.startup_tip_file_provider,
                R.string.startup_tip_network_binders,
                R.string.startup_tip_vr_buttons,
                R.string.startup_tip_settings_defaults
        };
    }

    private static int[] funnyTips() {
        return new int[] {
                R.string.startup_funny_tip_reticulate_splits,
                R.string.startup_funny_tip_warm_shizuku,
                R.string.startup_funny_tip_sort_bytes,
                R.string.startup_funny_tip_feed_logs,
                R.string.startup_funny_tip_overlay_gravity,
                R.string.startup_funny_tip_hex_snacks,
                R.string.startup_funny_tip_http_doorman,
                R.string.startup_funny_tip_ftp_tunnel,
                R.string.startup_funny_tip_vr_windows,
                R.string.startup_funny_tip_payload_seatbelts,
                R.string.startup_funny_tip_apk_zipper,
                R.string.startup_funny_tip_smali_threads,
                R.string.startup_funny_tip_memory_socks,
                R.string.startup_funny_tip_launcher_hats,
                R.string.startup_funny_tip_safe_mode,
                R.string.startup_funny_tip_byte_soup,
                R.string.startup_funny_tip_socket_manners,
                R.string.startup_funny_tip_payload_stretch,
                R.string.startup_funny_tip_apk_shoelaces,
                R.string.startup_funny_tip_log_polite,
                R.string.startup_funny_tip_panel_behave,
                R.string.startup_funny_tip_shell_dust,
                R.string.startup_funny_tip_split_spaghetti,
                R.string.startup_funny_tip_tiny_cones,
                R.string.startup_funny_tip_startup_transition,
                R.string.startup_funny_tip_permission_cookies,
                R.string.startup_funny_tip_network_snacks,
                R.string.startup_funny_tip_memory_lint,
                R.string.startup_funny_tip_http_sandwich,
                R.string.startup_funny_tip_debug_ducks,
                R.string.startup_funny_tip_update_gnome,
                R.string.startup_funny_tip_smali_hammock,
                R.string.startup_funny_tip_cache_broom,
                R.string.startup_funny_tip_payload_crayons,
                R.string.startup_funny_tip_hex_lantern,
                R.string.startup_funny_tip_api_pigeon,
                R.string.startup_funny_tip_permissions_pep,
                R.string.startup_funny_tip_debug_snails,
                R.string.startup_funny_tip_http_cookies,
                R.string.startup_funny_tip_ftp_backpack,
                R.string.startup_funny_tip_update_ladder,
                R.string.startup_funny_tip_smali_map,
                R.string.startup_funny_tip_log_bubbles,
                R.string.startup_funny_tip_log_zipper,
                R.string.startup_funny_tip_install_helmet,
                R.string.startup_funny_tip_release_oracle,
                R.string.startup_funny_tip_pre_gremlins,
                R.string.startup_funny_tip_update_shoes,
                R.string.startup_funny_tip_asset_shelf,
                R.string.startup_funny_tip_backend_tea,
                R.string.startup_funny_tip_smali_laundry,
                R.string.startup_funny_tip_payload_weather,
                R.string.startup_funny_tip_vr_tape,
                R.string.startup_funny_tip_log_fountain,
                R.string.startup_funny_tip_network_socks,
                R.string.startup_funny_tip_hex_mittens,
                R.string.startup_funny_tip_permission_lantern,
                R.string.startup_funny_tip_file_biscuit,
                R.string.startup_funny_tip_shell_parade,
                R.string.startup_funny_tip_cache_sweater,
                R.string.startup_funny_tip_dropdown_sprint,
                R.string.startup_funny_tip_ptrace_hat,
                R.string.startup_funny_tip_release_fishing,
                R.string.startup_funny_tip_smali_confetti,
                R.string.startup_funny_tip_vr_button_stretch,
                R.string.startup_funny_tip_shell_marshmallows,
                R.string.startup_funny_tip_hex_spoons,
                R.string.startup_funny_tip_payload_bubbles,
                R.string.startup_funny_tip_log_hamster,
                R.string.startup_funny_tip_file_umbrella,
                R.string.startup_funny_tip_network_pancakes,
                R.string.startup_funny_tip_package_socks,
                R.string.startup_funny_tip_settings_garden,
                R.string.startup_funny_tip_update_wagon,
                R.string.startup_funny_tip_cache_sandcastle,
                R.string.startup_tip_shizuku,
                R.string.startup_tip_memory,
                R.string.startup_tip_network,
                R.string.startup_tip_debugging,
                R.string.startup_tip_save_data,
                R.string.startup_tip_logging
        };
    }

    private static String[] clampText(String[] values, int maxChars) {
        if (values == null || values.length == 0) return new String[0];
        String[] out = new String[values.length];
        int count = 0;
        for (String value : values) {
            String clipped = clampText(value, maxChars);
            if (!TextUtils.isEmpty(clipped)) out[count++] = clipped;
        }
        if (count == out.length) return out;
        String[] trimmed = new String[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

    private static String clampText(String value, int maxChars) {
        if (value == null) return "";
        String clean = value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (maxChars <= 0 || clean.length() <= maxChars) return clean;
        int cut = Math.max(24, maxChars - 1);
        int space = clean.lastIndexOf(' ', cut);
        if (space >= 24) cut = space;
        return clean.substring(0, Math.min(cut, clean.length())).trim() + "…";
    }

    private static final class TextGroup {
        final String key;
        final String label;
        final int[] normalStageIds;
        final int[] normalTipIds;
        final int[] funnyStageIds;
        final int[] funnyTipIds;
        final String[] normalStageText;
        final String[] normalTipText;
        final String[] funnyStageText;
        final String[] funnyTipText;
        final String[] vrNormalStageText;
        final String[] vrNormalTipText;
        final String[] vrFunnyStageText;
        final String[] vrFunnyTipText;
        final boolean vrOnly;

        TextGroup(String key, String label, int[] normalStageIds, int[] normalTipIds, int[] funnyStageIds, int[] funnyTipIds,
                  String[] normalStageText, String[] normalTipText, String[] funnyStageText, String[] funnyTipText) {
            this(key, label, normalStageIds, normalTipIds, funnyStageIds, funnyTipIds,
                    normalStageText, normalTipText, funnyStageText, funnyTipText,
                    null, null, null, null, false);
        }

        TextGroup(String key, String label, int[] normalStageIds, int[] normalTipIds, int[] funnyStageIds, int[] funnyTipIds,
                  String[] normalStageText, String[] normalTipText, String[] funnyStageText, String[] funnyTipText,
                  String[] vrNormalStageText, String[] vrNormalTipText, String[] vrFunnyStageText, String[] vrFunnyTipText) {
            this(key, label, normalStageIds, normalTipIds, funnyStageIds, funnyTipIds,
                    normalStageText, normalTipText, funnyStageText, funnyTipText,
                    vrNormalStageText, vrNormalTipText, vrFunnyStageText, vrFunnyTipText, false);
        }

        TextGroup(String key, String label, int[] normalStageIds, int[] normalTipIds, int[] funnyStageIds, int[] funnyTipIds,
                  String[] normalStageText, String[] normalTipText, String[] funnyStageText, String[] funnyTipText,
                  String[] vrNormalStageText, String[] vrNormalTipText, String[] vrFunnyStageText, String[] vrFunnyTipText,
                  boolean vrOnly) {
            this.key = key;
            this.label = label;
            this.normalStageIds = normalStageIds == null ? new int[0] : normalStageIds;
            this.normalTipIds = normalTipIds == null ? new int[0] : normalTipIds;
            this.funnyStageIds = funnyStageIds == null ? new int[0] : funnyStageIds;
            this.funnyTipIds = funnyTipIds == null ? new int[0] : funnyTipIds;
            this.normalStageText = normalStageText == null ? new String[0] : normalStageText;
            this.normalTipText = normalTipText == null ? new String[0] : normalTipText;
            this.funnyStageText = funnyStageText == null ? new String[0] : funnyStageText;
            this.funnyTipText = funnyTipText == null ? new String[0] : funnyTipText;
            this.vrNormalStageText = vrNormalStageText == null ? new String[0] : vrNormalStageText;
            this.vrNormalTipText = vrNormalTipText == null ? new String[0] : vrNormalTipText;
            this.vrFunnyStageText = vrFunnyStageText == null ? new String[0] : vrFunnyStageText;
            this.vrFunnyTipText = vrFunnyTipText == null ? new String[0] : vrFunnyTipText;
            this.vrOnly = vrOnly;
        }
    }

    private static final class ResolvedGroup {
        final String[] normalStages;
        final String[] normalTips;
        final String[] funnyStages;
        final String[] funnyTips;

        ResolvedGroup(String[] normalStages, String[] normalTips, String[] funnyStages, String[] funnyTips) {
            this.normalStages = clampText(normalStages, MAX_STAGE_CHARS);
            this.normalTips = clampText(normalTips, MAX_TIP_CHARS);
            this.funnyStages = clampText(funnyStages, MAX_STAGE_CHARS);
            this.funnyTips = clampText(funnyTips, MAX_TIP_CHARS);
        }
    }

    static final class Sequence {
        final String[] tips;
        final String[] stages;
        final int tipIndex;
        final int stageIndex;

        Sequence(String[] tips, String[] stages, int tipIndex, int stageIndex) {
            this.tips = tips == null ? new String[0] : tips;
            this.stages = stages == null ? new String[0] : stages;
            this.tipIndex = tipIndex;
            this.stageIndex = stageIndex;
        }
    }

    static final class TextSelection {
        final String stageText;
        final String tipText;

        TextSelection(String stageText, String tipText) {
            this.stageText = stageText == null ? "" : stageText;
            this.tipText = tipText == null ? "" : tipText;
        }
    }
}
