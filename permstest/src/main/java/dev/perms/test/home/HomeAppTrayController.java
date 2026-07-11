package dev.perms.test.home;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.ui.panel.GenericPanelLauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Activity-side controller for the Home tab App Tray. */
public final class HomeAppTrayController {

    public interface ShellCaptureCallback {
        void onComplete(int code, String out, String err);
    }

    public interface Host {
        void runOnBackground(Runnable task);
        int dp(int dip);
        void runShellCommandCapture(String cmd, ShellCaptureCallback cb);
        void appendOutput(String msg);
        void debugOutput(String area, String message);
        void invalidateFilesPackageIconCaches();
        void makeDebugPackage(String packageName, String label);
        void extractPackage(String packageName, String label);
        void launchWithPayloads(String packageName, String label);
        void createPayloadShortcut(String packageName, String label);
    }

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final String prefsName;
    private final String vrDetectKey;
    private final Host host;

    private RecyclerView appTrayView;
    private HomeAppTrayAdapter appTrayAdapter;
    private final java.util.ArrayList<HomeAppTrayAdapter> cloneAdapters = new java.util.ArrayList<>();
    private android.content.BroadcastReceiver packageReceiver;
    private static final long VR_APP_TRAY_INPUT_GUARD_MS = 220L;
    // Enable only while diagnosing VR App Tray focus/input routing.
    // High-frequency motion logs can cause visible App Tray lag on headset builds.
    private static final boolean APP_TRAY_DEBUG_DIAGNOSTICS = false;

    private boolean vrAppTrayInputActive;
    private boolean restoringVrParentScroll;
    private long vrAppTrayInputGuardUntilMs;
    private long lastVrInputDebugAtMs;
    private final Runnable vrAppTrayInputRelease = new Runnable() {
        @Override
        public void run() {
            try {
                if (SystemClock.uptimeMillis() < vrAppTrayInputGuardUntilMs) {
                    if (appTrayView != null) {
                        appTrayView.postDelayed(this, VR_APP_TRAY_INPUT_GUARD_MS);
                    }
                    return;
                }
                vrAppTrayInputActive = false;
                setParentInterceptDisabled(appTrayView, false);
            } catch (Throwable ignored) {
            }
        }
    };

    public HomeAppTrayController(@NonNull AppCompatActivity activity,
                                 @NonNull ActivityMainBinding binding,
                                 @NonNull String prefsName,
                                 @NonNull String vrDetectKey,
                                 @NonNull Host host) {
        this.activity = activity;
        this.binding = binding;
        this.prefsName = prefsName;
        this.vrDetectKey = vrDetectKey;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding.tabMain == null) return;
            if (binding.tabMain.chkAutoRestartLauncher == null) return;

            final ViewParent vp = binding.tabMain.chkAutoRestartLauncher.getParent();
            if (!(vp instanceof ViewGroup)) return;
            final ViewGroup parent = (ViewGroup) vp;

            // Avoid duplicating on activity recreation.
            if (appTrayView != null) return;

            final android.widget.LinearLayout headerRow = new android.widget.LinearLayout(activity);
            headerRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            final ViewGroup.MarginLayoutParams hdrLp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hdrLp.topMargin = host.dp(12);
            hdrLp.bottomMargin = host.dp(8);
            headerRow.setLayoutParams(hdrLp);

            final TextView hdr = new TextView(activity);
            hdr.setText("App Tray");
            hdr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            hdr.setTypeface(hdr.getTypeface(), android.graphics.Typeface.BOLD);
            headerRow.addView(hdr, new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final com.google.android.material.card.MaterialCardView card =
                    new com.google.android.material.card.MaterialCardView(activity);
            final ViewGroup.MarginLayoutParams cardLp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dp(420));
            cardLp.bottomMargin = host.dp(12);
            card.setLayoutParams(cardLp);
            card.setUseCompatPadding(true);
            card.setContentPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(8));

            final RecyclerView rv = new RecyclerView(activity);
            rv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            rv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            configureVrAwareInput(rv);

            final int span = Math.max(3, activity.getResources().getConfiguration().screenWidthDp / 120);
            debug("bind", "created App Tray; screenWidthDp="
                    + activity.getResources().getConfiguration().screenWidthDp
                    + ", span=" + span
                    + ", vrDetect=" + isVrAppTrayModeEnabled()
                    + ", parent=" + parent.getClass().getSimpleName());
            rv.setLayoutManager(new GridLayoutManager(activity, span));

            final HomeAppTrayAdapter ad = new HomeAppTrayAdapter(createAdapterHost());
            rv.setAdapter(ad);

            card.addView(rv);

            View appTrayAnchor = binding.tabMain.chkMainHomePopoutFullWindow != null
                    ? binding.tabMain.chkMainHomePopoutFullWindow
                    : binding.tabMain.chkAutoRestartLauncher;
            int idx = parent.indexOfChild(appTrayAnchor);
            if (idx < 0) idx = parent.indexOfChild(binding.tabMain.chkAutoRestartLauncher);
            if (idx < 0) idx = parent.getChildCount() - 1;
            parent.addView(headerRow, Math.min(idx + 1, parent.getChildCount()));
            parent.addView(card, Math.min(idx + 2, parent.getChildCount()));

            appTrayView = rv;
            appTrayAdapter = ad;

            refreshAsync();
        } catch (Throwable ignored) {
        }
    }

    public void refreshAsync() {
        host.runOnBackground(() -> {
            final ArrayList<HomeAppTrayEntry> out = loadLaunchableApps();
            activity.runOnUiThread(() -> {
                if (appTrayAdapter != null) appTrayAdapter.setItems(out);
                for (int i = cloneAdapters.size() - 1; i >= 0; i--) {
                    HomeAppTrayAdapter adapter = cloneAdapters.get(i);
                    if (adapter != null) adapter.setItems(out);
                }
                debug("refresh", "loaded launchable apps=" + out.size());
            });
        });
    }

    public HomeAppTrayAdapter attachClone(RecyclerView rv) {
        if (rv == null) return null;
        try {
            configureVrAwareInput(rv);
            final int span = Math.max(3, activity.getResources().getConfiguration().screenWidthDp / 120);
            rv.setLayoutManager(new GridLayoutManager(activity, span));
            HomeAppTrayAdapter adapter = new HomeAppTrayAdapter(createAdapterHost());
            rv.setAdapter(adapter);
            cloneAdapters.add(adapter);
            refreshAsync();
            return adapter;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void detachClone(HomeAppTrayAdapter adapter) {
        if (adapter == null) return;
        try { cloneAdapters.remove(adapter); } catch (Throwable ignored) {}
    }

    private ArrayList<HomeAppTrayEntry> loadLaunchableApps() {
        final ArrayList<HomeAppTrayEntry> out = new ArrayList<>();
        try {
            final PackageManager pm = activity.getPackageManager();
            final List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            if (apps != null) {
                for (ApplicationInfo ai : apps) {
                    if (ai == null || ai.packageName == null) continue;

                    Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                    if (launch == null) continue;

                    final boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;

                    CharSequence label;
                    try {
                        label = pm.getApplicationLabel(ai);
                    } catch (Throwable t) {
                        label = ai.packageName;
                    }
                    out.add(new HomeAppTrayEntry(ai.packageName, label, pm.getApplicationIcon(ai), isSystem));
                }

                Collections.sort(out, (a, b) -> String.valueOf(a.label)
                        .compareToIgnoreCase(String.valueOf(b.label)));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private HomeAppTrayAdapter.Host createAdapterHost() {
        return new HomeAppTrayAdapter.Host() {
            @Override
            public void launchApp(String packageName) {
                launchAppFromTray(packageName);
            }

            @Override
            public void runShell(String cmd, String labelForLog) {
                runTrayShellCommand(cmd, labelForLog);
            }

            @Override
            public void makeDebugPackage(String packageName, String label) {
                host.makeDebugPackage(packageName, label);
            }

            @Override
            public void extractPackage(String packageName, String label) {
                host.extractPackage(packageName, label);
            }

            @Override
            public void launchWithPayloads(String packageName, String label) {
                host.launchWithPayloads(packageName, label);
            }

            @Override
            public void createPayloadShortcut(String packageName, String label) {
                host.createPayloadShortcut(packageName, label);
            }

            @Override
            public Context getContext() {
                return activity;
            }
        };
    }

    public void registerPackageReceiver() {
        if (packageReceiver != null) return;
        try {
            android.content.IntentFilter f = new android.content.IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_CHANGED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addDataScheme("package");
            packageReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        host.invalidateFilesPackageIconCaches();
                    } catch (Throwable ignored) {
                    }
                    try {
                        refreshAsync();
                    } catch (Throwable ignored) {
                    }
                }
            };
            activity.registerReceiver(packageReceiver, f);
        } catch (Throwable ignored) {
            packageReceiver = null;
        }
    }

    public void shutdown() {
        if (packageReceiver != null) {
            try {
                activity.unregisterReceiver(packageReceiver);
            } catch (Throwable ignored) {
            }
            packageReceiver = null;
        }
    }


    public void openAppTrayPanel() {
        try {
            Intent intent = new Intent(activity, HomeAppTrayPanelActivity.class);
            boolean opened = GenericPanelLauncher.startPanelActivity(activity, intent, "App Tray panel");
            if (opened) {
                host.debugOutput("main:home:app-tray", "opened full App Tray panel");
            } else {
                host.debugOutput("main:home:app-tray", "open failed or was blocked");
            }
        } catch (Throwable t) {
            host.debugOutput("main:home:app-tray", "open failed: " + t);
            Toast.makeText(activity, "Unable to open App Tray panel: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isVrAppTrayModeEnabled() {
        try {
            return activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .getBoolean(vrDetectKey, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void debug(String area, String message) {
        if (!APP_TRAY_DEBUG_DIAGNOSTICS) return;
        // Enable APP_TRAY_DEBUG_DIAGNOSTICS only while diagnosing VR App Tray focus/input routing.
        try {
            host.debugOutput("main:home:app-tray:" + area, message);
        } catch (Throwable ignored) {
        }
    }

    private void debugVrInput(String area, MotionEvent event) {
        if (!APP_TRAY_DEBUG_DIAGNOSTICS) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastVrInputDebugAtMs < 500L) return;
        lastVrInputDebugAtMs = now;
        String message = "event=null";
        if (event != null) {
            message = "action=" + event.getActionMasked()
                    + ", source=0x" + Integer.toHexString(event.getSource())
                    + ", axisY=" + event.getAxisValue(MotionEvent.AXIS_Y)
                    + ", axisHatY=" + event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                    + ", axisVScroll=" + event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    + ", nested=" + (appTrayView != null && appTrayView.isNestedScrollingEnabled())
                    + ", focused=" + (appTrayView != null && appTrayView.hasFocus());
        }
        debug(area, message);
    }
    private void configureVrAwareInput(final RecyclerView rv) {
        boolean vrAppTrayInput = isVrAppTrayModeEnabled();
        debug("vr-input", "configure; enabled=" + vrAppTrayInput
                + ", sourceWidthDp=" + activity.getResources().getConfiguration().screenWidthDp);
        if (vrAppTrayInput) {
            rv.setNestedScrollingEnabled(false);
            rv.setFocusable(true);
            rv.setFocusableInTouchMode(true);
            rv.setOnHoverListener((view, event) -> {
                try {
                    if (event != null && (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER
                            || event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE)) {
                        vrAppTrayInputActive = true;
                        debugVrInput("hover", event);
                        keepVrAppTrayInputLocal(rv);
                    } else if (event != null && event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                        vrAppTrayInputActive = false;
                        debugVrInput("hover-exit", event);
                        releaseVrAppTrayInput(view);
                    }
                } catch (Throwable ignored) {
                }
                return false;
            });
            rv.setOnFocusChangeListener((view, hasFocus) -> {
                try {
                    debug("vr-input", "focus changed; hasFocus=" + hasFocus);
                    if (hasFocus) {
                        keepVrAppTrayInputLocal(rv);
                    } else {
                        releaseVrAppTrayInput(view);
                    }
                } catch (Throwable ignored) {
                }
            });
            rv.setOnTouchListener((view, event) -> {
                try {
                    if (event != null) {
                        int action = event.getActionMasked();
                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                            vrAppTrayInputActive = true;
                            debugVrInput("touch", event);
                            keepVrAppTrayInputLocal(rv);
                        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            debugVrInput("touch-release", event);
                            releaseVrAppTrayInput(view);
                        }
                    }
                } catch (Throwable ignored) {
                }
                return false;
            });
            rv.setOnGenericMotionListener((view, event) -> handleVrAppTrayGenericMotion(rv, event));
            rv.setOnKeyListener((view, keyCode, event) -> handleVrAppTrayKey(rv, keyCode, event));

            bindVrParentScrollGuard(rv);
        } else {
            rv.setNestedScrollingEnabled(true);
            debug("vr-input", "standard nested scrolling enabled");
        }
    }

    private void bindVrParentScrollGuard(final RecyclerView rv) {
        final View homeRoot = binding.tabMain == null ? null : binding.tabMain.getRoot();
        if (homeRoot == null) return;

        homeRoot.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (restoringVrParentScroll) return;
            if (!isVrAppTrayInputGuardActive(rv)) return;

            final int parentDeltaY = scrollY - oldScrollY;
            if (parentDeltaY == 0) return;

            debug("vr-input", "redirect parent scroll to App Tray; deltaY=" + parentDeltaY);
            try {
                restoringVrParentScroll = true;
                view.scrollTo(scrollX, oldScrollY);
            } catch (Throwable ignored) {
            } finally {
                restoringVrParentScroll = false;
            }

            try {
                keepVrAppTrayInputLocal(rv);
                rv.scrollBy(0, parentDeltaY);
            } catch (Throwable ignored) {
            }
        });
    }

    private boolean handleVrAppTrayGenericMotion(final RecyclerView rv, final MotionEvent event) {
        if (event == null) return false;
        try {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                float amount = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (Math.abs(amount) > 0.01f) {
                    vrAppTrayInputActive = true;
                    keepVrAppTrayInputLocal(rv);
                    rv.scrollBy(0, Math.round(-amount * host.dp(96)));
                    debugVrInput("generic-scroll amount=" + amount, event);
                    return true;
                }
            }

            final int source = event.getSource();
            final boolean gameInput = (source & InputDevice.SOURCE_CLASS_JOYSTICK) != 0
                    || (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
            if (gameInput && event.getAction() == MotionEvent.ACTION_MOVE) {
                float amount = event.getAxisValue(MotionEvent.AXIS_Y);
                if (Math.abs(amount) <= 0.25f) {
                    amount = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
                }
                if (Math.abs(amount) > 0.25f) {
                    vrAppTrayInputActive = true;
                    keepVrAppTrayInputLocal(rv);
                    rv.scrollBy(0, Math.round(amount * host.dp(36)));
                    debugVrInput("joystick-scroll amount=" + amount, event);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean handleVrAppTrayKey(final RecyclerView rv, int keyCode, final KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        try {
            int step = Math.max(host.dp(72), rv.getHeight() / 3);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                vrAppTrayInputActive = true;
                keepVrAppTrayInputLocal(rv);
                rv.scrollBy(0, step);
                debug("vr-input", "key scroll down; keyCode=" + keyCode + ", step=" + step);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                vrAppTrayInputActive = true;
                keepVrAppTrayInputLocal(rv);
                rv.scrollBy(0, -step);
                debug("vr-input", "key scroll up; keyCode=" + keyCode + ", step=" + step);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void keepVrAppTrayInputLocal(final View view) {
        if (view == null) return;
        vrAppTrayInputActive = true;
        vrAppTrayInputGuardUntilMs = SystemClock.uptimeMillis() + VR_APP_TRAY_INPUT_GUARD_MS;
        try {
            view.requestFocus();
        } catch (Throwable ignored) {
        }
        setParentInterceptDisabled(view, true);
        try {
            view.removeCallbacks(vrAppTrayInputRelease);
            view.postDelayed(vrAppTrayInputRelease, VR_APP_TRAY_INPUT_GUARD_MS + 40L);
        } catch (Throwable ignored) {
        }
    }

    private void releaseVrAppTrayInput(final View view) {
        vrAppTrayInputActive = false;
        vrAppTrayInputGuardUntilMs = 0L;
        try {
            if (view != null) view.removeCallbacks(vrAppTrayInputRelease);
        } catch (Throwable ignored) {
        }
        setParentInterceptDisabled(view, false);
    }

    private boolean isVrAppTrayInputGuardActive(final RecyclerView rv) {
        try {
            if (!vrAppTrayInputActive || appTrayView != rv) return false;
            if (SystemClock.uptimeMillis() <= vrAppTrayInputGuardUntilMs) return true;
            vrAppTrayInputActive = false;
            setParentInterceptDisabled(rv, false);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void setParentInterceptDisabled(final View view, boolean disabled) {
        try {
            ViewParent parent = view == null ? null : view.getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(disabled);
                if (parent instanceof View) {
                    parent = ((View) parent).getParent();
                } else {
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void runTrayShellCommand(String cmd, String labelForLog) {
        host.runShellCommandCapture(cmd, (code, out, err) -> {
            final String label = labelForLog == null ? "shell" : labelForLog;
            final String stdout = out == null ? "" : out;
            final String stderr = err == null ? "" : err;
            activity.runOnUiThread(() -> {
                if ("backup".equals(label) || "restore".equals(label)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[App Tray] ").append(label).append(" exit=").append(code).append("\n");
                    if (!TextUtils.isEmpty(stdout)) {
                        sb.append(stdout);
                        if (!stdout.endsWith("\n")) sb.append("\n");
                    }
                    if (!TextUtils.isEmpty(stderr)) {
                        sb.append(stderr);
                        if (!stderr.endsWith("\n")) sb.append("\n");
                    }
                    host.appendOutput(sb.toString());
                    Toast.makeText(activity,
                            code == 0 ? ("App " + label + " complete") : ("App " + label + " failed"),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                if (code != 0) {
                    Toast.makeText(activity,
                            "Command failed (" + label + ")", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void launchAppFromTray(String packageName) {
        try {
            if (TextUtils.isEmpty(packageName)) return;
            final PackageManager pm = activity.getPackageManager();
            final Intent i = pm.getLaunchIntentForPackage(packageName);
            if (i == null) {
                Toast.makeText(activity, "No launch activity", Toast.LENGTH_SHORT).show();
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(activity, "Unable to launch app", Toast.LENGTH_SHORT).show();
        }
    }
}
