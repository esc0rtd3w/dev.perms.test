package dev.perms.test.tools.intentreceiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

/** Owns the installed receiver aliases that can be enabled for live intent-capture testing. */
final class IntentReceiverAliasRegistry {
    private IntentReceiverAliasRegistry() {
    }

    static boolean isAliasEnabled(Context context, String aliasSuffix) {
        if (context == null || TextUtils.isEmpty(aliasSuffix)) return false;
        try {
            PackageManager pm = context.getPackageManager();
            ComponentName cn = component(context, aliasSuffix);
            int state = pm.getComponentEnabledSetting(cn);
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean setAliasEnabled(Context context, String aliasSuffix, boolean enabled) {
        if (context == null || TextUtils.isEmpty(aliasSuffix)) return false;
        try {
            context.getPackageManager().setComponentEnabledSetting(
                    component(context, aliasSuffix),
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static ComponentName component(Context context, String aliasSuffix) {
        return new ComponentName(context.getPackageName(), context.getPackageName() + aliasSuffix);
    }
}
