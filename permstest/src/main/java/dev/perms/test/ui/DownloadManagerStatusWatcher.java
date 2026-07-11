package dev.perms.test.ui;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

public final class DownloadManagerStatusWatcher {
    public interface Listener {
        void onFinalStatus(long downloadId, int status, int reason);

        void onStatusUnavailable(long downloadId);
    }

    private static final long POLL_DELAY_MS = 1000L;
    private static final long WATCH_TIMEOUT_MS = 5L * 60L * 1000L;

    private DownloadManagerStatusWatcher() {
    }

    public static void watch(Context context,
                             DownloadManager downloadManager,
                             long downloadId,
                             Listener listener) {
        if (context == null || downloadManager == null || downloadId <= 0L || listener == null) return;

        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;

        new Watch(appContext, downloadManager, downloadId, listener).start();
    }

    private static final class Watch {
        private final Context context;
        private final DownloadManager downloadManager;
        private final long downloadId;
        private final Listener listener;
        private final Handler handler;
        private final long deadlineMillis;

        private BroadcastReceiver receiver;
        private boolean finished;

        Watch(Context context,
              DownloadManager downloadManager,
              long downloadId,
              Listener listener) {
            this.context = context;
            this.downloadManager = downloadManager;
            this.downloadId = downloadId;
            this.listener = listener;
            this.handler = new Handler(Looper.getMainLooper());
            this.deadlineMillis = System.currentTimeMillis() + WATCH_TIMEOUT_MS;
        }

        void start() {
            registerReceiver();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    pollStatus();
                }
            }, POLL_DELAY_MS);
        }

        private void registerReceiver() {
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
                    long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                    if (completedId != downloadId) return;
                    finishFromQuery(true);
                }
            };

            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    // DownloadManager completion is delivered by the platform downloads provider.
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    context.registerReceiver(receiver, filter);
                }
            } catch (Throwable ignored) {
                receiver = null;
                // Polling below still reports completion even when the broadcast path is unavailable.
            }
        }

        private void pollStatus() {
            if (finished) return;

            Status status = queryStatus(downloadManager, downloadId);
            if (status.available && isTerminalStatus(status.status)) {
                finish(status);
                return;
            }

            if (System.currentTimeMillis() >= deadlineMillis) {
                finishUnavailable();
                return;
            }

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    pollStatus();
                }
            }, POLL_DELAY_MS);
        }

        private void finishFromQuery(boolean allowNonTerminalStatus) {
            if (finished) return;

            Status status = queryStatus(downloadManager, downloadId);
            if (!status.available) {
                finishUnavailable();
                return;
            }
            if (allowNonTerminalStatus || isTerminalStatus(status.status)) {
                finish(status);
            }
        }

        private void finish(Status status) {
            if (finished) return;
            finished = true;
            unregisterReceiver();
            listener.onFinalStatus(downloadId, status.status, status.reason);
        }

        private void finishUnavailable() {
            if (finished) return;
            finished = true;
            unregisterReceiver();
            listener.onStatusUnavailable(downloadId);
        }

        private void unregisterReceiver() {
            BroadcastReceiver oldReceiver = receiver;
            receiver = null;
            if (oldReceiver == null) return;
            try {
                context.unregisterReceiver(oldReceiver);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isTerminalStatus(int status) {
        return status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED;
    }

    private static Status queryStatus(DownloadManager downloadManager, long downloadId) {
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            cursor = downloadManager.query(query);
            if (cursor == null || !cursor.moveToFirst()) return Status.unavailable();

            int status = getInt(cursor, DownloadManager.COLUMN_STATUS, -1);
            int reason = getInt(cursor, DownloadManager.COLUMN_REASON, 0);
            return Status.available(status, reason);
        } catch (Throwable ignored) {
            return Status.unavailable();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static int getInt(Cursor cursor, String columnName, int fallback) {
        try {
            int index = cursor.getColumnIndex(columnName);
            return index >= 0 ? cursor.getInt(index) : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static final class Status {
        final boolean available;
        final int status;
        final int reason;

        private Status(boolean available, int status, int reason) {
            this.available = available;
            this.status = status;
            this.reason = reason;
        }

        static Status available(int status, int reason) {
            return new Status(true, status, reason);
        }

        static Status unavailable() {
            return new Status(false, -1, 0);
        }
    }
}
