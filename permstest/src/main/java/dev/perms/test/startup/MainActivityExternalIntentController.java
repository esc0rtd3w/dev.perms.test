package dev.perms.test.startup;

import android.app.Activity;
import android.content.Intent;

/**
 * Keeps MainActivity startup/new-intent routing ordered and isolated from the
 * rest of activity setup. The order is intentional: editor intents are more
 * specific than generic file-open/install intents.
 */
public final class MainActivityExternalIntentController {
    public interface Host {
        boolean handleToolsTextEditorIntent(Intent intent);
        boolean handleSmaliEditorIntent(Intent intent);
        boolean handleFileOpenIntent(Intent intent);
    }

    private final Activity activity;
    private final Host host;

    public MainActivityExternalIntentController(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    public boolean handleStartupIntent(Intent intent) {
        boolean handled = handle(intent);
        if (handled) clearHandledIntent();
        return handled;
    }

    public boolean handleNewIntent(Intent intent) {
        try {
            activity.setIntent(intent);
        } catch (Throwable ignored) {
        }
        boolean handled = handle(intent);
        if (handled) clearHandledIntent();
        return handled;
    }

    private boolean handle(Intent intent) {
        if (intent == null || host == null) return false;
        if (safeHandleToolsTextEditorIntent(intent)) return true;
        if (safeHandleSmaliEditorIntent(intent)) return true;
        return safeHandleFileOpenIntent(intent);
    }

    private boolean safeHandleToolsTextEditorIntent(Intent intent) {
        try {
            return host.handleToolsTextEditorIntent(intent);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeHandleSmaliEditorIntent(Intent intent) {
        try {
            return host.handleSmaliEditorIntent(intent);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeHandleFileOpenIntent(Intent intent) {
        try {
            return host.handleFileOpenIntent(intent);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void clearHandledIntent() {
        try {
            Intent neutral = new Intent(activity, activity.getClass());
            neutral.setAction(Intent.ACTION_MAIN);
            neutral.addCategory(Intent.CATEGORY_LAUNCHER);
            activity.setIntent(neutral);
        } catch (Throwable ignored) {
        }
    }
}
