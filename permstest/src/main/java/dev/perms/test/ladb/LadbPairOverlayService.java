package dev.perms.test.ladb;

import dev.perms.test.ExecMode;
import dev.perms.test.R;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.Nullable;

/**
 * Overlay UI that can stay on top of Settings > Developer Options > Wireless debugging.
 * Requires SYSTEM_ALERT_WINDOW permission.
 */
public final class LadbPairOverlayService extends Service {

    public static final String EXTRA_PAIR_PORT = "pairPort";
    public static final String EXTRA_PAIR_CODE = "pairCode";
    public static final String EXTRA_CONNECT_PORT = "connectPort";

    private static final String PREFS = "ladb_pair_prefs";
    private static final String PREF_OVERLAY_X = "overlay_x";
    private static final String PREF_OVERLAY_Y = "overlay_y";

    private WindowManager wm;
    private View root;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showOverlay(intent);
        return START_NOT_STICKY;
    }

    private void showOverlay(Intent intent) {
        if (root != null) return;

        final int pairPort = intent != null ? intent.getIntExtra(EXTRA_PAIR_PORT, ExecMode.LADB_DEFAULT_PAIR_PORT) : ExecMode.LADB_DEFAULT_PAIR_PORT;
        final int connectPort = intent != null ? intent.getIntExtra(EXTRA_CONNECT_PORT, ExecMode.LADB_DEFAULT_CONNECT_PORT) : ExecMode.LADB_DEFAULT_CONNECT_PORT;
        final String pairCode = intent != null ? intent.getStringExtra(EXTRA_PAIR_CODE) : null;

        root = LayoutInflater.from(this).inflate(R.layout.overlay_ladb_pair, null, false);
        final EditText edtPairPort = root.findViewById(R.id.edtOverlayPairPort);
        final EditText edtPairCode = root.findViewById(R.id.edtOverlayPairCode);
        final EditText edtConnectPort = root.findViewById(R.id.edtOverlayConnectPort);
        final View btnClose = root.findViewById(R.id.btnOverlayClose);
        final View btnPair = root.findViewById(R.id.btnOverlayPair);

        edtPairPort.setText(String.valueOf(pairPort));
        edtConnectPort.setText(String.valueOf(connectPort));
        if (!TextUtils.isEmpty(pairCode)) edtPairCode.setText(pairCode);

        btnClose.setOnClickListener(v -> stopSelf());
        btnPair.setOnClickListener(v -> {
            final int p = safeParseInt(edtPairPort.getText().toString(), 0);
            final int c = safeParseInt(edtConnectPort.getText().toString(), 0);
            final String code = edtPairCode.getText().toString().trim();
            if (p <= 0 || c <= 0 || TextUtils.isEmpty(code)) {
				android.widget.Toast.makeText(getApplicationContext(),
						"Enter Pair port, Pair code, and Connect port.",
						android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            doPairAndConnect(p, code, c);
        });


        final View header = root.findViewById(R.id.overlayHeader);
        if (header != null) {
            header.setOnTouchListener(new View.OnTouchListener() {
                private int startX, startY;
                private float touchX, touchY;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event == null) return false;
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            try {
                                WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
                                startX = p.x;
                                startY = p.y;
                                touchX = event.getRawX();
                                touchY = event.getRawY();
                            } catch (Throwable ignored) {}
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            try {
                                WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
                                int dx = (int) (event.getRawX() - touchX);
                                int dy = (int) (event.getRawY() - touchY);
                                p.x = startX + dx;
                                p.y = startY + dy;
                                wm.updateViewLayout(root, p);
                            } catch (Throwable ignored) {}
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            try {
                                WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                        .putInt(PREF_OVERLAY_X, p.x)
                                        .putInt(PREF_OVERLAY_Y, p.y)
                                        .apply();
                            } catch (Throwable ignored) {}
                            return true;
                    }
                    return false;
                }
            });
        }

        final int type = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        lp.x = sp.getInt(PREF_OVERLAY_X, dp(16));
        lp.y = sp.getInt(PREF_OVERLAY_Y, dp(80));

        try {
            wm.addView(root, lp);
        } catch (Throwable t) {
            // If addView fails, stop.
            stopSelf();
        }
    }

    private int dp(int dp) {
        final float d = getResources().getDisplayMetrics().density;
        return (int) (dp * d + 0.5f);
    }

    private int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    private void doPairAndConnect(final int pairPort, final String pairCode, final int connectPort) {
        new Thread(() -> {
            try {
                LadbClient client = new LadbClient(getApplicationContext());
                LadbClient.CmdResult r1 = client.pair(LadbClient.DEFAULT_HOST, pairPort, pairCode);
                if (r1 == null || r1.exitCode != 0) {
                    postResult("Pair failed: " + summarize(r1), false);
                    return;
                }
                LadbClient.CmdResult r2 = client.connect(LadbClient.DEFAULT_HOST, connectPort);
                if (r2 == null || r2.exitCode != 0) {
                    postResult("Connect failed: " + summarize(r2), false);
                    return;
                }
                postResult("Paired + connected (" + connectPort + ")", true);
            } catch (Throwable t) {
                postResult("Pair/connect error: " + t, false);
            }
        }, "LadbPairOverlay").start();
    }

    private String summarize(LadbClient.CmdResult r) {
        if (r == null) return "unknown";
        String s = (!TextUtils.isEmpty(r.stderr) ? r.stderr : r.stdout);
        if (s == null) s = "";
        s = s.trim().replace('\n', ' ');
        if (s.length() > 120) s = s.substring(0, 120) + "…";
        return "exit=" + r.exitCode + " " + s;
    }

    private void postResult(String msg, boolean ok) {
        try {
            androidx.core.app.NotificationCompat.Builder nb = new androidx.core.app.NotificationCompat.Builder(this, "ladb_pair")
                    .setSmallIcon(ok ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                    .setContentTitle("LADB Pair Helper")
                    .setContentText(msg)
                    .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(msg))
                    .setAutoCancel(true)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH);
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            try {
                if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                    android.app.NotificationChannel ch = nm.getNotificationChannel("ladb_pair");
                    if (ch == null) nm.createNotificationChannel(new android.app.NotificationChannel("ladb_pair", "LADB Pair Helper", android.app.NotificationManager.IMPORTANCE_HIGH));
                }
            } catch (Throwable ignored) {}
            if (nm != null) nm.notify(20022, nb.build());
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroy() {
        try {
            if (wm != null && root != null) {
                wm.removeView(root);
            }
        } catch (Throwable ignored) {}
        root = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
