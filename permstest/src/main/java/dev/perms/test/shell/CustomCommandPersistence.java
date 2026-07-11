package dev.perms.test.shell;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/**
 * Preference-backed persistence for Shell custom commands.
 */
public final class CustomCommandPersistence {
    private CustomCommandPersistence() {
    }

    public static void load(Context context, String prefsName, String key, List<CustomCommand> commands) {
        if (commands == null) return;
        commands.clear();
        try {
            if (context == null || prefsName == null || key == null) return;
            SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            String raw = prefs.getString(key, null);
            commands.addAll(CustomCommandJson.parse(raw));

            // Preserve the existing pinned/order sort and normalize behavior.
            CustomCommandList.sortForDisplay(commands);
            CustomCommandList.normalizeOrders(commands);

            // Persist normalized JSON silently, matching the previous migration path.
            persist(context, prefsName, key, commands);
        } catch (Throwable ignored) {
        }
    }

    public static void persist(Context context, String prefsName, String key, List<CustomCommand> commands) {
        try {
            if (context == null || prefsName == null || key == null) return;
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .putString(key, CustomCommandJson.toPrefsJson(commands))
                    .apply();
        } catch (Throwable ignored) {
        }
    }
}
