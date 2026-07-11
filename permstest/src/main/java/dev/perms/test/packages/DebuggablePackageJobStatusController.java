package dev.perms.test.packages;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.TextView;

import dev.perms.test.settings.SettingsPreferenceKeys;

public final class DebuggablePackageJobStatusController {
    private static final String KEY_JOB_RUNNING = "debug_apk_job_running";
    private static final String KEY_JOB_STATUS = "debug_apk_job_status";
    private static final String KEY_JOB_UPDATED_AT = "debug_apk_job_updated_at";
    private static final String KEY_JOB_LOG = "debug_apk_job_log";
    private static final int MAX_JOB_LOG_CHARS = 24000;

    public interface StatusViewProvider {
        TextView getStatusView();
    }

    public interface OutputSink {
        void append(String text);
    }

    private final Context context;
    private SharedPreferences prefs;
    private final Handler mainHandler;
    private final StatusViewProvider statusViewProvider;
    private final OutputSink outputSink;
    private Runnable statusPoller;
    private String restoredLogSnapshot = "";

    public DebuggablePackageJobStatusController(Context context,
                                                SharedPreferences prefs,
                                                Handler mainHandler,
                                                StatusViewProvider statusViewProvider,
                                                OutputSink outputSink) {
        this.context = context == null ? null : context.getApplicationContext();
        this.prefs = prefs;
        this.mainHandler = mainHandler == null ? new Handler(Looper.getMainLooper()) : mainHandler;
        this.statusViewProvider = statusViewProvider;
        this.outputSink = outputSink;
    }

    public void clearLog() {
        try {
            prefs().edit().putString(KEY_JOB_LOG, "").apply();
            restoredLogSnapshot = "";
        } catch (Throwable ignored) {
        }
    }

    public void appendLog(String msg) {
        if (TextUtils.isEmpty(msg)) return;
        try {
            SharedPreferences sp = prefs();
            String current = sp.getString(KEY_JOB_LOG, "");
            String next = (current == null ? "" : current) + msg;
            if (next.length() > MAX_JOB_LOG_CHARS) {
                next = next.substring(next.length() - MAX_JOB_LOG_CHARS);
            }
            sp.edit().putString(KEY_JOB_LOG, next).apply();
        } catch (Throwable ignored) {
        }

        // APK rebuild work can outlive Activity recreation. Keep UI output routing on the
        // active Activity sink so Package rebuild status stays visible without direct view access.
        try {
            if (outputSink != null) outputSink.append(msg);
        } catch (Throwable ignored) {
        }
    }

    public void restoreLogIfNeeded() {
        try {
            String log = prefs().getString(KEY_JOB_LOG, "");
            if (TextUtils.isEmpty(log) || TextUtils.equals(log, restoredLogSnapshot)) return;
            restoredLogSnapshot = log;
            if (outputSink != null) {
                outputSink.append("[APK Debug] Restored debuggable package log after Activity recreation.\n" + log);
            }
        } catch (Throwable ignored) {
        }
    }

    public void setStatus(boolean running, String status) {
        try {
            String value = TextUtils.isEmpty(status) ? (running ? "Create Debuggable Package is running..." : "") : status.trim();
            prefs().edit()
                    .putBoolean(KEY_JOB_RUNNING, running)
                    .putString(KEY_JOB_STATUS, value)
                    .putLong(KEY_JOB_UPDATED_AT, System.currentTimeMillis())
                    .apply();
            Runnable uiUpdate = () -> {
                try {
                    TextView statusView = statusView();
                    if (statusView != null) statusView.setText(value);
                    schedulePoll();
                } catch (Throwable ignored) {
                }
            };
            if (Looper.myLooper() == Looper.getMainLooper()) uiUpdate.run();
            else mainHandler.post(uiUpdate);
        } catch (Throwable ignored) {
        }
    }

    public void restoreStatus() {
        try {
            SharedPreferences sp = prefs();
            boolean running = sp.getBoolean(KEY_JOB_RUNNING, false);
            String status = sp.getString(KEY_JOB_STATUS, "");
            TextView statusView = statusView();
            if (!TextUtils.isEmpty(status) && statusView != null) {
                statusView.setText(status + (running ? "" : ""));
            }
            if (running) {
                restoreLogIfNeeded();
                schedulePoll();
            } else {
                // Finished rebuild logs are status history, not startup output. Keep the Packages
                // status text above, but do not repopulate the shared output panel.
                clearLog();
            }
        } catch (Throwable ignored) {
        }
    }

    public void schedulePoll() {
        try {
            if (statusPoller != null) mainHandler.removeCallbacks(statusPoller);
            if (!prefs().getBoolean(KEY_JOB_RUNNING, false)) return;
            statusPoller = () -> {
                try {
                    SharedPreferences sp = prefs();
                    boolean running = sp.getBoolean(KEY_JOB_RUNNING, false);
                    String status = sp.getString(KEY_JOB_STATUS, "");
                    TextView statusView = statusView();
                    if (!TextUtils.isEmpty(status) && statusView != null) {
                        statusView.setText(status);
                    }
                    if (running) {
                        mainHandler.postDelayed(statusPoller, 1000L);
                    } else {
                        restoreLogIfNeeded();
                        statusPoller = null;
                    }
                } catch (Throwable ignored) {
                    statusPoller = null;
                }
            };
            mainHandler.postDelayed(statusPoller, 1000L);
        } catch (Throwable ignored) {
        }
    }

    public void stop() {
        try {
            if (statusPoller != null) mainHandler.removeCallbacks(statusPoller);
        } catch (Throwable ignored) {
        }
        statusPoller = null;
    }

    private SharedPreferences prefs() {
        if (prefs == null && context != null) {
            prefs = context.getSharedPreferences(SettingsPreferenceKeys.PREFS, Context.MODE_PRIVATE);
        }
        return prefs;
    }

    private TextView statusView() {
        try {
            return statusViewProvider == null ? null : statusViewProvider.getStatusView();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
