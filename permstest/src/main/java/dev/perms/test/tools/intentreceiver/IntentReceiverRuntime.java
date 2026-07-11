package dev.perms.test.tools.intentreceiver;

import android.content.Context;
import android.content.SharedPreferences;

/** Runtime preferences/helpers for live Intent Receiver capture behavior. */
public final class IntentReceiverRuntime {
    private static final String PREFS = "perms_test";
    public static final String KEY_PREFER_CAPTURE_FOR_BUILT_IN_HANDLERS = "intent_receiver_prefer_capture_for_builtin_handlers";

    private static final String ALIAS_OPEN_TEXT = ".tools.intentreceiver.IntentCaptureOpenTextAlias";
    private static final String ALIAS_OPEN_ANY_FILE = ".tools.intentreceiver.IntentCaptureOpenAnyFileAlias";

    private IntentReceiverRuntime() {
    }

    public static boolean isPreferCaptureForBuiltInHandlers(Context context) {
        if (context == null) return true;
        try {
            return prefs(context).getBoolean(KEY_PREFER_CAPTURE_FOR_BUILT_IN_HANDLERS, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setPreferCaptureForBuiltInHandlers(Context context, boolean enabled) {
        if (context == null) return;
        try {
            prefs(context).edit().putBoolean(KEY_PREFER_CAPTURE_FOR_BUILT_IN_HANDLERS, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean shouldCaptureTextOpen(Context context) {
        if (!isPreferCaptureForBuiltInHandlers(context)) return false;
        return isOpenTextCaptureActive(context) || isOpenAnyFileCaptureActive(context);
    }

    public static boolean isOpenTextCaptureActive(Context context) {
        return IntentReceiverAliasRegistry.isAliasEnabled(context, ALIAS_OPEN_TEXT);
    }

    public static boolean isOpenAnyFileCaptureActive(Context context) {
        return IntentReceiverAliasRegistry.isAliasEnabled(context, ALIAS_OPEN_ANY_FILE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
