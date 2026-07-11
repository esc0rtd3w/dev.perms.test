package rikka.shizuku;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import moe.shizuku.api.BinderContainer;
import rikka.shizuku.common.ShizukuConfig;
import rikka.sui.Sui;

/**
 * <p>
 * This provider receives binder from Shizuku server. When app process starts,
 * Shizuku server (it runs under adb/root) will send the binder to client apps with this provider.
 * </p>
 * <p>
 * Add the provider to your manifest like this:
 * </p>
 * <pre class="prettyprint">&lt;manifest&gt;
 *    ...
 *    &lt;application&gt;
 *        ...
 *        &lt;provider
 *            android:name="rikka.shizuku.ShizukuProvider"
 *            android:authorities="${applicationId}.shizuku"
 *            android:exported="true"
 *            android:multiprocess="false"
 *            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"
 *        &lt;/provider&gt;
 *        ...
 *    &lt;/application&gt;
 * &lt;/manifest&gt;</pre>
 *
 * <p>
 * There are something needs you attention:
 * </p>
 * <ol>
 * <li><code>android:permission</code> shoule be a permission that granted to Shell (com.android.shell)
 * but not normal apps (e.g., android.permission.INTERACT_ACROSS_USERS_FULL), so that it can only
 * be used by the app itself and Shizuku server.</li>
 * <li><code>android:exported</code> must be <code>true</code> so that the provider can be accessed
 * from Shizuku server runs under adb.</li>
 * <li><code>android:multiprocess</code> must be <code>false</code>
 * since Shizuku server only gets uid when app starts.</li>
 * </ol>
 * <p>
 * If your app runs in multiple processes, this provider also provides the functionality of sharing
 * the binder across processes. See {@link #enableMultiProcessSupport(boolean)}.
 * </p>
 */
public class ShizukuProvider extends ContentProvider {

    private static final String TAG = "ShizukuProvider";

    // For receive Binder from Shizuku
    public static final String METHOD_SEND_BINDER = "sendBinder";

    // For share Binder between processes
    public static final String METHOD_GET_BINDER = "getBinder";

    public static final String ACTION_BINDER_RECEIVED = "moe.shizuku.api.action.BINDER_RECEIVED";

    // Keep upstream constant values for compatibility, but use ShizukuConfig getters at runtime.
    // This allows the host app to switch to an embedded/internal variant without colliding with
    // the installed Shizuku manager's permission ownership.
    public static final String PERMISSION = ShizukuConfig.PERMISSION_EXTERNAL;

    public static final String MANAGER_APPLICATION_ID = ShizukuConfig.MANAGER_APPLICATION_ID_EXTERNAL;

    private static String getExtraBinderKey() {
        return ShizukuConfig.getManagerApplicationId() + ".intent.extra.BINDER";
    }

    public static String getPermission() {
        return ShizukuConfig.getPermission();
    }

    public static String getManagerApplicationId() {
        return ShizukuConfig.getManagerApplicationId();
    }

    private static boolean enableMultiProcess = false;

    private static boolean isProviderProcess = false;

    private static boolean enableSuiInitialization = true;

    private static volatile boolean currentBinderEmbedded = false;
    private static volatile IBinder cachedEmbeddedBinder = null;
    private static volatile IBinder cachedExternalBinder = null;

    public static boolean hasEmbeddedBinder() {
        IBinder b = Shizuku.getBinder();
        return currentBinderEmbedded && b != null && b.pingBinder();
    }

    public static boolean hasExternalBinder() {
        IBinder b = Shizuku.getBinder();
        return !currentBinderEmbedded && b != null && b.pingBinder();
    }

    public static boolean isCurrentBinderEmbedded() {
        return currentBinderEmbedded;
    }

    public static boolean hasCachedEmbeddedBinder() {
        return isAlive(cachedEmbeddedBinder);
    }

    public static boolean hasCachedExternalBinder() {
        return isAlive(cachedExternalBinder);
    }

    public static boolean restoreCachedBinder(@Nullable Context context, boolean embedded) {
        IBinder cached = embedded ? cachedEmbeddedBinder : cachedExternalBinder;
        if (!isAlive(cached)) return false;
        String packageName = null;
        try { packageName = context == null ? null : context.getPackageName(); } catch (Throwable ignored) {}
        currentBinderEmbedded = embedded;
        Shizuku.onBinderReceived(cached, packageName);
        return true;
    }

    public static boolean clearActiveEmbeddedBinder(String packageName) {
        try {
            if (currentBinderEmbedded) {
                Shizuku.onBinderReceived(null, packageName);
                currentBinderEmbedded = false;
                return true;
            }
        } catch (Throwable ignored) {
            currentBinderEmbedded = false;
        }
        return false;
    }

    public static void clearBinderSource() {
        currentBinderEmbedded = false;
    }

    private static boolean isAlive(@Nullable IBinder binder) {
        try { return binder != null && binder.pingBinder(); } catch (Throwable ignored) { return false; }
    }

    public static void setIsProviderProcess(boolean isProviderProcess) {
        ShizukuProvider.isProviderProcess = isProviderProcess;
    }

    /**
     * Enables built-in multi-process support.
     * <p>
     * This method MUST be called as early as possible (e.g., static block in Application).
     */
    public static void enableMultiProcessSupport(boolean isProviderProcess) {
        Log.d(TAG, "Enable built-in multi-process support (from " + (isProviderProcess ? "provider process" : "non-provider process") + ")");

        ShizukuProvider.isProviderProcess = isProviderProcess;
        ShizukuProvider.enableMultiProcess = true;
    }

    /**
     * Disable automatic Sui initialization.
     */
    public static void disableAutomaticSuiInitialization() {
        ShizukuProvider.enableSuiInitialization = false;
    }

    /**
     * Require binder for non-provider process, should have {@link #enableMultiProcessSupport(boolean)} called first.
     *
     * @param context Context
     */
    public static void requestBinderForNonProviderProcess(@NonNull Context context) {
        if (isProviderProcess) {
            return;
        }

        Log.d(TAG, "request binder in non-provider process");

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BinderContainer container = intent.getParcelableExtra(getExtraBinderKey());
                if (container != null && container.binder != null) {
                    Log.i(TAG, "binder received from broadcast");
                    Shizuku.onBinderReceived(container.binder, context.getPackageName());
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, new IntentFilter(ACTION_BINDER_RECEIVED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, new IntentFilter(ACTION_BINDER_RECEIVED));
        }

        Bundle reply;
        try {
            reply = context.getContentResolver().call(Uri.parse("content://" + context.getPackageName() + ".shizuku"),
                    ShizukuProvider.METHOD_GET_BINDER, null, new Bundle());
        } catch (Throwable tr) {
            reply = null;
        }

        if (reply != null) {
            reply.setClassLoader(BinderContainer.class.getClassLoader());

            BinderContainer container = reply.getParcelable(getExtraBinderKey());
            if (container != null && container.binder != null) {
                Log.i(TAG, "Binder received from other process");
                Shizuku.onBinderReceived(container.binder, context.getPackageName());
            }
        }
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);

        if (info.multiprocess)
            throw new IllegalStateException("android:multiprocess must be false");

        if (!info.exported)
            throw new IllegalStateException("android:exported must be true");

        isProviderProcess = true;
    }

    @Override
    public boolean onCreate() {
        if (enableSuiInitialization && !Sui.isSui()) {
            boolean result = Sui.init(getContext().getPackageName());
            Log.d(TAG, "Initialize Sui: " + result);
        }
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (Sui.isSui()) {
            Log.w(TAG, "Provider called when Sui is available. Are you using Shizuku and Sui at the same time?");
            return new Bundle();
        }

        if (extras == null) {
            return null;
        }

        extras.setClassLoader(BinderContainer.class.getClassLoader());

        Bundle reply = new Bundle();
        switch (method) {
            case METHOD_SEND_BINDER: {
                handleSendBinder(extras);
                break;
            }
            case METHOD_GET_BINDER: {
                if (!handleGetBinder(reply)) {
                    return null;
                }
                break;
            }
        }
        return reply;
    }

    private void handleSendBinder(@NonNull Bundle extras) {
        Context context = getContext();
        String packageName = context == null ? null : context.getPackageName();
        boolean embeddedMode = isEmbeddedModeSelected(context);

        String embeddedKey = getEmbeddedBinderKey(packageName);
        String externalKey = getExternalBinderKey();
        BinderContainer embeddedContainer = getBinderContainer(extras, embeddedKey);
        BinderContainer externalContainer = getBinderContainer(extras, externalKey);

        BinderContainer container = null;
        String binderKey = null;
        boolean embeddedBinder = false;

        if (embeddedContainer != null && embeddedContainer.binder != null) {
            container = embeddedContainer;
            binderKey = embeddedKey;
            embeddedBinder = true;
        } else if (externalContainer != null && externalContainer.binder != null) {
            container = externalContainer;
            binderKey = externalKey;
            embeddedBinder = false;
        } else {
            binderKey = getExtraBinderKey();
            container = getBinderContainer(extras, binderKey);
            embeddedBinder = embeddedKey != null && embeddedKey.equals(binderKey);
        }

        if (container == null || container.binder == null) {
            return;
        }

        if (embeddedBinder) {
            cachedEmbeddedBinder = container.binder;
            if (!embeddedMode) {
                Log.d(TAG, "cached embedded binder while external Shizuku mode is selected");
                return;
            }
            if (packageName != null) {
                ShizukuConfig.setEmbedded(true);
                ShizukuConfig.setManagerApplicationId(packageName);
                ShizukuConfig.setPermission(packageName + ".permission.SHIZUKU_API_V23");
            }
        } else {
            cachedExternalBinder = container.binder;
            if (embeddedMode) {
                // Keep installed Shizuku available for a later mode switch, but do not
                // activate it while Internal Shizuku is selected.
                Log.d(TAG, "cached external binder while embedded mode is selected");
                return;
            }
        }

        if (Shizuku.pingBinder()) {
            if (currentBinderEmbedded == embeddedBinder) {
                Log.d(TAG, "sendBinder is called when matching binder is already alive");
                return;
            }
            Log.d(TAG, embeddedBinder ? "replace external binder with embedded binder" : "replace embedded binder with external binder");
            Shizuku.onBinderReceived(null, packageName);
            currentBinderEmbedded = false;
        }

        Log.d(TAG, embeddedBinder ? "embedded binder received" : "binder received");

        currentBinderEmbedded = embeddedBinder;
        Shizuku.onBinderReceived(container.binder, packageName);

        if (enableMultiProcess && context != null && packageName != null) {
            Log.d(TAG, "broadcast binder");

            Intent intent = new Intent(ACTION_BINDER_RECEIVED)
                    .putExtra(binderKey == null ? getExtraBinderKey() : binderKey, container)
                    .setPackage(packageName);
            context.sendBroadcast(intent);
        }
    }

    @Nullable
    private static BinderContainer getBinderContainer(@NonNull Bundle extras, @Nullable String key) {
        if (key == null) return null;
        try {
            return extras.getParcelable(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String getEmbeddedBinderKey(@Nullable String packageName) {
        if (packageName == null || packageName.length() == 0) return null;
        return packageName + ".intent.extra.BINDER";
    }

    private static String getExternalBinderKey() {
        return ShizukuConfig.MANAGER_APPLICATION_ID_EXTERNAL + ".intent.extra.BINDER";
    }

    private boolean isEmbeddedModeSelected(@Nullable Context context) {
        if (ShizukuConfig.isEmbedded()) return true;
        if (context == null) return false;
        try {
            String mode = context.getSharedPreferences("perms_test", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)
                    .getString("exec_mode", "shizuku");
            return "internal_shizuku".equals(mode);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean handleGetBinder(@NonNull Bundle reply) {
        // Other processes in the same app can read the provider without permission.
        // Do not bridge an installed Shizuku binder into Internal Shizuku mode, or an
        // Internal Shizuku binder back into installed-Shizuku mode.
        IBinder binder = Shizuku.getBinder();
        if (binder == null || !binder.pingBinder())
            return false;

        if (isEmbeddedModeSelected(getContext()) != currentBinderEmbedded) {
            return false;
        }

        reply.putParcelable(getExtraBinderKey(), new BinderContainer(binder));
        return true;
    }

    // no other provider methods
    @Nullable
    @Override
    public final Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public final String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public final Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public final int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public final int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
