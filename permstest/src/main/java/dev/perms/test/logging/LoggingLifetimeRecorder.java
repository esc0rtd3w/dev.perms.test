package dev.perms.test.logging;

import android.content.Context;
import android.text.TextUtils;

/**
 * Records persistent shell/action history for the Logging tab.
 *
 * MainActivity still owns shell execution. This helper keeps lifetime-log enabled state,
 * command descriptions, and append routing inside the logging package.
 */
public final class LoggingLifetimeRecorder {
    private final Context appContext;
    private boolean enabled = true;

    public LoggingLifetimeRecorder(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String tag, String message) {
        if (!enabled || appContext == null) return;
        LifetimeLogStore.appendAsync(appContext, tag, message);
    }

    public void logActionForCommand(String command) {
        if (!enabled || TextUtils.isEmpty(command)) return;
        String description = LoggingCommandDescriber.describe(command);
        if (!TextUtils.isEmpty(description)) {
            log("action", description);
        }
    }
}
