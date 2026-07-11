package dev.perms.test.kiosk;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.perms.test.MainActivity;
import dev.perms.test.startup.StartupLoadingHandoffOverlay;
import dev.perms.test.R;
import dev.perms.test.debug.DebugLog;
import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.ui.PermsTestUiCompat;

/** Optional, disabled-by-default, minimal full-screen HOME surface for whitelisted apps/shortcuts. */
public class KioskLauncherActivity extends AppCompatActivity {
    private static final String EXTRA_FROM_KIOSK = "dev.perms.test.kiosk.FROM_KIOSK";

    private KioskSettingsStore store;
    private KioskLauncherAdapter adapter;
    private TextView status;
    private Handler handler;
    private KioskExitPattern exitPatternDetector;
    private View kioskTouchRoot;
    private RecyclerView kioskRecyclerView;
    private GridLayoutManager gridLayoutManager;
    private final Runnable timerRefresh = this::timerRefresh;
    private int immersiveReapplyCount;
    private final Runnable immersiveReapply = new Runnable() {
        @Override
        public void run() {
            applyKioskWindowFlags();
            if (handler != null && store != null && store.hideStatusBar() && immersiveReapplyCount++ < 8) {
                handler.postDelayed(this, 900);
            }
        }
    };
    private Map<String, KioskLaunchableApp> appCache = new HashMap<>();
    private KioskHardwareButtonBypassController hardwareButtonBypassController;
    private boolean lockTaskStartAttempted;
    private final Runnable lockTaskStartRunnable = this::startLockTaskIfNeeded;
    private final Runnable lockTaskStatusRunnable = this::updateLockTaskStatusAfterStart;
    private final Runnable autoSizeGeometryRefreshRunnable = this::refreshAutoSizeGridAfterGeometryChange;
    private int lastAutoSizeGridWidthPx;
    private int lastAutoSizeGridHeightPx;
    private int lastAutoSizeOrientation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new KioskSettingsStore(this);
        handler = new Handler(getMainLooper());
        try {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleKioskBackPressed();
                }
            });
        } catch (Throwable ignored) {
        }
        hardwareButtonBypassController = new KioskHardwareButtonBypassController(this, store, "KioskLauncherActivity", new KioskHardwareButtonBypassController.Host() {
            @Override
            public void onKioskHardwareBypassTriggered() {
                openMainAndFinish();
            }

            @Override
            public void debugOutput(String area, String message) {
                kioskDebug(area, message);
            }
        });
        kioskDebug("startup", "Kiosk launcher starting; hardwareBypass=" + store.hardwareButtonBypassEnabled());
        if (store.isLauncherForceDisabled()) {
            KioskSafety.clearLauncherPreferenceAndAliasIfForced(this);
            openMainAndFinish();
            return;
        }
        if (!store.isKioskEnabled()) {
            KioskSafety.clearKioskPreferenceIfForced(this);
            openMainAndFinish();
            return;
        }
        if (!store.hasEnabledAllowedItems()) {
            store.setKioskEnabled(false);
            Toast.makeText(this, "Kiosk Mode disabled because no kiosk apps or shortcuts are enabled.", Toast.LENGTH_LONG).show();
            openMainAndFinish();
            return;
        }
        if (shouldRedirectToVrPanel()) {
            Intent panel = new Intent(this, KioskPanelActivity.class);
            panel.putExtra(EXTRA_FROM_KIOSK, true);
            panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(panel);
            finish();
            return;
        }

        applyKioskWindowFlags();
        setContentView(R.layout.activity_kiosk_launcher);
        try { setTitle(isVrPanelActivity() ? "Kiosk Panel" : "Kiosk Mode"); } catch (Throwable ignored) {}
        try { setTaskDescription(new ActivityManager.TaskDescription("Kiosk Mode")); } catch (Throwable ignored) {}
        PermsTestUiCompat.applyActivityUiProfile(this, getWindow().getDecorView());

        status = findViewById(R.id.txtKioskStatus);
        try { if (status != null) status.setVisibility(View.GONE); } catch (Throwable ignored) {}
        RecyclerView rv = findViewById(R.id.rvKioskApps);
        kioskRecyclerView = rv;
        adapter = new KioskLauncherAdapter(new KioskLauncherAdapter.Host() {
            @Override
            public void launch(KioskAllowedItem item) {
                launchItem(item);
            }

            @Override
            public Drawable iconFor(KioskAllowedItem item) {
                return KioskLauncherActivity.this.iconFor(item);
            }

            @Override
            public android.content.Context context() {
                return KioskLauncherActivity.this;
            }
        });
        gridLayoutManager = new GridLayoutManager(this, manualSpanCount()) {
            @Override
            public boolean canScrollVertically() {
                return !(store != null && store.autoSizeIcons()) && super.canScrollVertically();
            }

            @Override
            public boolean canScrollHorizontally() {
                return false;
            }
        };
        rv.setLayoutManager(gridLayoutManager);
        try { rv.setItemAnimator(null); } catch (Throwable ignored) {}
        rv.setAdapter(adapter);
        rv.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            try {
                handleKioskGridGeometryChanged(right - left, bottom - top);
            } catch (Throwable ignored) {
            }
        });

        kioskTouchRoot = findViewById(R.id.rootKioskLauncher);
        exitPatternDetector = new KioskExitPattern(store.exitPattern(), recovery -> showExitPrompt(recovery));
        refreshItems();
        applyLockTaskIfRequested();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null && (store.isLauncherForceDisabled() || !store.isKioskEnabled() || !store.hasEnabledAllowedItems())) {
            KioskSafety.clearLauncherPreferenceAndAliasIfForced(this);
            KioskSafety.clearKioskPreferenceIfForced(this);
            if (store != null && !store.hasEnabledAllowedItems()) {
                store.setKioskEnabled(false);
                Toast.makeText(this, "Kiosk Mode disabled because no kiosk apps or shortcuts are enabled.", Toast.LENGTH_LONG).show();
            }
            openMainAndFinish();
            return;
        }
        applyKioskWindowFlags();
        refreshItems();
        scheduleTimerRefresh();
        applyLockTaskIfRequested();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyKioskWindowFlags();
        resetAutoSizeGridGeometry("configuration");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (hardwareButtonBypassController != null && hardwareButtonBypassController.dispatchKeyEvent(event)) return true;
        if (handleKioskBackKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        handleKioskBackPressed();
    }

    private boolean handleKioskBackKey(KeyEvent event) {
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            handleKioskBackPressed();
        }
        return true;
    }

    private void handleKioskBackPressed() {
        kioskDebug("back", "Back ignored while Kiosk Mode is active; use kiosk exit controls instead");
        Toast.makeText(this, "Use the kiosk exit pattern to leave Kiosk Mode.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            View target = kioskTouchRoot != null ? kioskTouchRoot : getWindow().getDecorView();
            if (exitPatternDetector != null && target != null && shouldCountKioskExitTap(ev)) {
                exitPatternDetector.onTouch(target, ev);
            }
        } catch (Throwable ignored) {
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean shouldCountKioskExitTap(MotionEvent ev) {
        if (ev == null) return false;
        int action = ev.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN
                && action != MotionEvent.ACTION_MOVE
                && action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL) {
            return false;
        }
        try {
            RecyclerView rv = kioskRecyclerView;
            if (rv != null && rv.getVisibility() == View.VISIBLE && rv.getWidth() > 0 && rv.getHeight() > 0) {
                int[] loc = new int[2];
                rv.getLocationOnScreen(loc);
                float rawX = ev.getRawX();
                float rawY = ev.getRawY();
                boolean insideRv = rawX >= loc[0] && rawX <= loc[0] + rv.getWidth()
                        && rawY >= loc[1] && rawY <= loc[1] + rv.getHeight();
                if (insideRv) {
                    View child = rv.findChildViewUnder(rawX - loc[0], rawY - loc[1]);
                    if (child == null) return true;
                    View launchTarget = child.findViewById(R.id.layoutKioskLaunchTarget);
                    return launchTarget != null && !isRawPointInsideView(launchTarget, rawX, rawY);
                }
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private boolean isRawPointInsideView(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX <= loc[0] + view.getWidth()
                && rawY >= loc[1] && rawY <= loc[1] + view.getHeight();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyKioskWindowFlags();
            if (handler != null) {
                immersiveReapplyCount = 0;
                handler.postDelayed(immersiveReapply, 350);
            }
            scheduleAutoSizeGridRefresh("window-focus");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.removeCallbacks(timerRefresh);
            handler.removeCallbacks(immersiveReapply);
            handler.removeCallbacks(lockTaskStartRunnable);
            handler.removeCallbacks(lockTaskStatusRunnable);
            handler.removeCallbacks(autoSizeGeometryRefreshRunnable);
        }
    }

    private boolean shouldRedirectToVrPanel() {
        if (isVrPanelActivity()) return false;
        try {
            SharedPreferences prefs = getSharedPreferences(SettingsPreferenceKeys.PREFS, MODE_PRIVATE);
            return prefs.getBoolean(SettingsPreferenceKeys.UI_DETECT_VR_MODE, false)
                    && !getIntent().getBooleanExtra(EXTRA_FROM_KIOSK, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    protected boolean isVrPanelActivity() {
        return false;
    }

    private void handleKioskGridGeometryChanged(int widthPx, int heightPx) {
        if (store == null || !store.autoSizeIcons() || widthPx <= 0 || heightPx <= 0) return;
        int orientation = getResources().getConfiguration().orientation;
        if (lastAutoSizeGridWidthPx <= 0 || lastAutoSizeGridHeightPx <= 0 || lastAutoSizeOrientation != orientation) {
            lastAutoSizeGridWidthPx = widthPx;
            lastAutoSizeGridHeightPx = heightPx;
            lastAutoSizeOrientation = orientation;
            scheduleAutoSizeGridRefresh("initial layout " + widthPx + "x" + heightPx);
            return;
        }

        int widthDelta = Math.abs(widthPx - lastAutoSizeGridWidthPx);
        int heightDelta = Math.abs(heightPx - lastAutoSizeGridHeightPx);
        int widthThreshold = Math.max(dp(32), lastAutoSizeGridWidthPx / 10);
        int heightThreshold = Math.max(dp(56), lastAutoSizeGridHeightPx / 6);
        if (widthDelta < widthThreshold && heightDelta < heightThreshold) {
            return;
        }

        lastAutoSizeGridWidthPx = widthPx;
        lastAutoSizeGridHeightPx = heightPx;
        scheduleAutoSizeGridRefresh("layout " + widthPx + "x" + heightPx);
    }

    private void resetAutoSizeGridGeometry(String reason) {
        lastAutoSizeGridWidthPx = 0;
        lastAutoSizeGridHeightPx = 0;
        lastAutoSizeOrientation = 0;
        scheduleAutoSizeGridRefresh(reason);
    }

    private void scheduleAutoSizeGridRefresh(String reason) {
        if (store == null || !store.autoSizeIcons()) return;
        kioskDebug("layout", "Scheduling auto-size kiosk grid refresh after " + reason);
        if (handler == null) {
            refreshAutoSizeGridAfterGeometryChange();
            return;
        }
        handler.removeCallbacks(autoSizeGeometryRefreshRunnable);
        handler.post(autoSizeGeometryRefreshRunnable);
        handler.postDelayed(autoSizeGeometryRefreshRunnable, 120);
        handler.postDelayed(autoSizeGeometryRefreshRunnable, 360);
    }

    private void refreshAutoSizeGridAfterGeometryChange() {
        if (store == null || !store.autoSizeIcons()) return;
        try {
            if (kioskRecyclerView != null) {
                kioskRecyclerView.stopScroll();
                kioskRecyclerView.scrollToPosition(0);
                kioskRecyclerView.setScrollX(0);
                kioskRecyclerView.setScrollY(0);
            }
            if (gridLayoutManager != null) {
                gridLayoutManager.scrollToPositionWithOffset(0, 0);
            }
        } catch (Throwable ignored) {
        }
        refreshItems();
        try {
            if (kioskRecyclerView != null) {
                kioskRecyclerView.post(() -> {
                    try {
                        kioskRecyclerView.stopScroll();
                        if (gridLayoutManager != null) {
                            gridLayoutManager.scrollToPositionWithOffset(0, 0);
                        } else {
                            kioskRecyclerView.scrollToPosition(0);
                        }
                        kioskRecyclerView.requestLayout();
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private int manualSpanCount() {
        int icon = store == null ? KioskPrefs.DEFAULT_ICON_SIZE_DP : store.iconSizeDp();
        int width = Math.max(1, getResources().getConfiguration().screenWidthDp);
        return Math.max(2, width / Math.max(110, icon + 42));
    }

    private IconGridOptions iconGridOptions(int itemCount) {
        int manualIconSizePx = Math.round((store == null ? KioskPrefs.DEFAULT_ICON_SIZE_DP : store.iconSizeDp())
                * getResources().getDisplayMetrics().density);
        if (store == null || !store.autoSizeIcons() || itemCount <= 0) {
            return new IconGridOptions(manualSpanCount(), manualIconSizePx, 0, 0);
        }

        boolean showLabels = store.showLabels();
        int availableWidth = availableKioskGridWidthPx();
        int availableHeight = availableKioskGridHeightPx();
        int minIcon = dp(KioskPrefs.MIN_ICON_SIZE_DP);
        int maxIcon = dp(KioskPrefs.MAX_AUTO_ICON_SIZE_DP);
        int itemHorizontalReserve = dp(44);
        int itemVerticalReserve = dp(showLabels ? 108 : 44);

        IconGridOptions best = chooseAutoSizeGridOptions(itemCount, availableWidth, availableHeight,
                minIcon, maxIcon, itemHorizontalReserve, itemVerticalReserve);
        return best;
    }

    private IconGridOptions chooseAutoSizeGridOptions(int itemCount, int availableWidth, int availableHeight,
                                                      int minIconPx, int maxIconPx,
                                                      int horizontalReservePx, int verticalReservePx) {
        int bestColumns = 1;
        int bestRows = Math.max(1, itemCount);
        int bestIcon = 1;
        int bestUnusedCells = Integer.MAX_VALUE;
        boolean bestMeetsMinimum = false;
        boolean landscape = availableWidth >= availableHeight;

        for (int columns = 1; columns <= Math.max(1, itemCount); columns++) {
            int rows = Math.max(1, (int) Math.ceil(itemCount / (double) columns));
            int cellWidth = Math.max(1, availableWidth / columns);
            int cellHeight = Math.max(1, availableHeight / rows);
            int candidateIcon = Math.min(cellWidth - horizontalReservePx, cellHeight - verticalReservePx);
            candidateIcon = Math.max(1, Math.min(maxIconPx, candidateIcon));
            boolean candidateMeetsMinimum = candidateIcon >= minIconPx;
            int unusedCells = (columns * rows) - itemCount;

            boolean better = false;
            if (candidateMeetsMinimum != bestMeetsMinimum) {
                better = candidateMeetsMinimum;
            } else if (candidateIcon > bestIcon + dp(3)) {
                better = true;
            } else if (Math.abs(candidateIcon - bestIcon) <= dp(3)) {
                if (unusedCells < bestUnusedCells) {
                    better = true;
                } else if (unusedCells == bestUnusedCells) {
                    int candidateBalance = Math.abs(columns - rows);
                    int bestBalance = Math.abs(bestColumns - bestRows);
                    if (landscape && columns > bestColumns) {
                        better = true;
                    } else if (!landscape && candidateBalance < bestBalance) {
                        better = true;
                    }
                }
            }

            if (better) {
                bestColumns = columns;
                bestRows = rows;
                bestIcon = candidateIcon;
                bestUnusedCells = unusedCells;
                bestMeetsMinimum = candidateMeetsMinimum;
            }
        }

        bestIcon = Math.max(1, Math.min(maxIconPx, bestIcon));
        int itemMinHeight = Math.max(1, availableHeight / Math.max(1, bestRows));
        return new IconGridOptions(bestColumns, bestIcon, itemMinHeight, bestRows);
    }

    private int availableKioskGridWidthPx() {
        try {
            RecyclerView rv = kioskRecyclerView;
            if (rv != null && rv.getWidth() > 0) {
                return Math.max(dp(120), rv.getWidth() - rv.getPaddingLeft() - rv.getPaddingRight());
            }
        } catch (Throwable ignored) {
        }
        return Math.max(dp(120), getResources().getDisplayMetrics().widthPixels - dp(36));
    }

    private int availableKioskGridHeightPx() {
        try {
            RecyclerView rv = kioskRecyclerView;
            if (rv != null && rv.getHeight() > 0) {
                return Math.max(dp(120), rv.getHeight() - rv.getPaddingTop() - rv.getPaddingBottom());
            }
        } catch (Throwable ignored) {
        }
        return Math.max(dp(160), getResources().getDisplayMetrics().heightPixels - dp(isVrPanelActivity() ? 96 : 128));
    }

    private void refreshItems() {
        if (store == null || adapter == null) return;
        List<KioskLaunchableApp> launchable = KioskAppRepository.loadLaunchableApps(this);
        HashMap<String, KioskLaunchableApp> map = new HashMap<>();
        for (KioskLaunchableApp app : launchable) map.put(app.packageName, app);
        appCache = map;

        ArrayList<KioskAllowedItem> visible = new ArrayList<>();
        for (KioskAllowedItem item : store.loadAllowedItems()) {
            if (item == null || !item.enabled) continue;
            if (item.isApp()) {
                KioskLaunchableApp app = map.get(item.id);
                if (app == null) continue;
                item.label = app.label;
            }
            visible.add(item);
        }
        IconGridOptions gridOptions = iconGridOptions(visible.size());
        boolean autoSizeIcons = store.autoSizeIcons();
        if (kioskRecyclerView != null) {
            try { kioskRecyclerView.stopScroll(); } catch (Throwable ignored) {}
            try { kioskRecyclerView.setNestedScrollingEnabled(!autoSizeIcons); } catch (Throwable ignored) {}
            try { kioskRecyclerView.setVerticalScrollBarEnabled(!autoSizeIcons); } catch (Throwable ignored) {}
            try { kioskRecyclerView.setHorizontalScrollBarEnabled(false); } catch (Throwable ignored) {}
            try { kioskRecyclerView.setOverScrollMode(autoSizeIcons ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_IF_CONTENT_SCROLLS); } catch (Throwable ignored) {}
            if (autoSizeIcons) {
                try { kioskRecyclerView.setScrollX(0); kioskRecyclerView.setScrollY(0); } catch (Throwable ignored) {}
                try { kioskRecyclerView.scrollToPosition(0); } catch (Throwable ignored) {}
            }
        }
        if (gridLayoutManager != null && gridLayoutManager.getSpanCount() != gridOptions.spanCount) {
            gridLayoutManager.setSpanCount(gridOptions.spanCount);
        }
        if (autoSizeIcons && gridLayoutManager != null) {
            try { gridLayoutManager.scrollToPositionWithOffset(0, 0); } catch (Throwable ignored) {}
        }
        adapter.setIconOptions(gridOptions.iconSizePx, store.showLabels(), autoSizeIcons ? gridOptions.itemMinHeightPx : 0);
        adapter.setItems(visible);
        kioskDebug("layout", "autoSizeIcons=" + autoSizeIcons
                + ", count=" + visible.size()
                + ", span=" + gridOptions.spanCount
                + ", rows=" + gridOptions.rowCount
                + ", itemHeightPx=" + gridOptions.itemMinHeightPx
                + ", iconPx=" + gridOptions.iconSizePx
                + ", rv=" + availableKioskGridWidthPx() + "x" + availableKioskGridHeightPx());
        if (visible.isEmpty()) {
            if (status != null) {
                status.setText("No usable kiosk apps or shortcuts are enabled. Leaving kiosk mode for safety...");
            }
            store.setKioskEnabled(false);
            Toast.makeText(this, "Kiosk Mode disabled because no usable kiosk apps or shortcuts are enabled.", Toast.LENGTH_LONG).show();
            if (handler != null) handler.postDelayed(this::openMainAndFinish, 450);
            else openMainAndFinish();
            return;
        }
        if (status != null) {
            status.setText("");
            status.setVisibility(View.GONE);
        }
    }

    private Drawable iconFor(KioskAllowedItem item) {
        try {
            if (item != null && item.isApp()) {
                KioskLaunchableApp app = appCache.get(item.id);
                if (app != null && app.icon != null) return app.icon;
                return getPackageManager().getApplicationIcon(item.id);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void launchItem(KioskAllowedItem item) {
        if (item == null) return;
        try {
            Intent launch;
            if (item.isApp()) {
                launch = getPackageManager().getLaunchIntentForPackage(item.id);
            } else {
                launch = Intent.parseUri(item.id, Intent.URI_INTENT_SCHEME);
            }
            if (launch == null) {
                Toast.makeText(this, "No launch intent", Toast.LENGTH_SHORT).show();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            kioskDebug("launch", "Launching kiosk item: " + item.label + " / " + item.id);
            boolean lockTaskPaused = prepareLockTaskForAllowedItemLaunch();
            try {
                startActivity(launch);
            } catch (Throwable launchError) {
                if (lockTaskPaused) applyLockTaskIfRequested();
                kioskDebug("launch", "Launch failed for " + (item == null ? "null" : item.label) + ": " + shortError(launchError));
                Toast.makeText(this, "Launch failed: " + launchError, Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            kioskDebug("launch", "Launch failed for " + (item == null ? "null" : item.label) + ": " + shortError(t));
            Toast.makeText(this, "Launch failed: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private boolean prepareLockTaskForAllowedItemLaunch() {
        try {
            if (store == null || !store.lockTaskEnabled()) return false;
            if (handler != null) {
                handler.removeCallbacks(lockTaskStartRunnable);
                handler.removeCallbacks(lockTaskStatusRunnable);
            }
            if (Build.VERSION.SDK_INT >= 23) {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am == null) return false;
                int state = am.getLockTaskModeState();
                if (state == ActivityManager.LOCK_TASK_MODE_NONE) return false;
                if (state == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                    kioskDebug("launch", "Device-owner Lock Task is active; leaving it active for allowed kiosk item launch");
                    return false;
                }
            }
            kioskDebug("launch", "Temporarily stopping Android Screen Pinning before launching allowed kiosk item");
            stopLockTaskIfActive();
            lockTaskStartAttempted = false;
            return true;
        } catch (Throwable t) {
            kioskDebug("launch", "Could not pause Lock Task / Screen Pinning before launch: " + shortError(t));
            return false;
        }
    }

    private void showExitPrompt(boolean recovery) {
        if (recovery) {
            showRecoveryExitDialog();
            return;
        }
        String password = store.password();
        if (!TextUtils.isEmpty(password)) {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint("Kiosk password");
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Kiosk controls")
                    .setMessage("Enter the kiosk password to continue.")
                    .setView(input)
                    .setNegativeButton("Resume", null)
                    .setPositiveButton("Continue", (d, which) -> {
                        if (TextUtils.equals(password, String.valueOf(input.getText()))) showActionDialog(false);
                        else Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show();
                    })
                    .show();
            return;
        }
        showActionDialog(false);
    }

    private void showRecoveryExitDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("Type yes");
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Recovery exit")
                .setMessage("Recovery hold detected. Type yes to enable Exit Kiosk Mode.")
                .setView(input)
                .setNegativeButton("Resume", null)
                .setPositiveButton("Exit Kiosk Mode", (d, which) -> {
                    kioskDebug("exit", "Recovery text confirmed; disabling Kiosk Mode");
                    store.setKioskEnabled(false);
                    openMainAndFinish();
                })
                .show();
        try {
            final android.widget.Button exitButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            if (exitButton != null) exitButton.setEnabled(false);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (exitButton != null) {
                        exitButton.setEnabled("yes".equalsIgnoreCase(String.valueOf(s).trim()));
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        } catch (Throwable ignored) {
        }
    }

    private void showActionDialog(boolean recovery) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Kiosk controls")
                .setMessage("Choose what to do next.")
                .setNegativeButton("Resume", null)
                .setNeutralButton("Open PermsTest", (d, which) -> {
                    kioskDebug("exit", "Exit pattern: open PermsTest selected");
                    openMainAndFinish();
                })
                .setPositiveButton("Exit Kiosk Mode", (d, which) -> {
                    kioskDebug("exit", "Exit pattern: disabling Kiosk Mode");
                    store.setKioskEnabled(false);
                    openMainAndFinish();
                })
                .show();
    }

    private void openMainAndFinish() {
        try {
            stopLockTaskIfActive();
            clearKioskWindowFlags();
            kioskDebug("exit", "Opening MainActivity from kiosk");
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            i.putExtra(StartupLoadingHandoffOverlay.EXTRA_SHOW_HANDOFF, true);
            startActivity(i);
            try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
        finish();
    }


    private void applyLockTaskIfRequested() {
        try {
            if (store == null || !store.lockTaskEnabled()) return;
            if (handler != null) {
                handler.removeCallbacks(lockTaskStartRunnable);
                handler.postDelayed(lockTaskStartRunnable, 300);
            } else {
                startLockTaskIfNeeded();
            }
        } catch (Throwable ignored) {
        }
    }

    private void startLockTaskIfNeeded() {
        try {
            if (store == null || !store.lockTaskEnabled() || isFinishing()) return;
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && am != null
                    && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
                lockTaskStartAttempted = true;
                appendKioskStatus("Lock Task / Screen Pinning is active.");
                return;
            }
            if (lockTaskStartAttempted) return;
            lockTaskStartAttempted = true;
            startLockTask();
            appendKioskStatus("Lock Task / Screen Pinning requested. If Android shows a screen-pinning prompt, confirm it. True locked fullscreen may require device-owner allowlist or Android Screen Pinning to be enabled.");
            if (handler != null) handler.postDelayed(lockTaskStatusRunnable, 900);
        } catch (Throwable t) {
            lockTaskStartAttempted = true;
            appendKioskStatus("Lock Task / Screen Pinning could not start. Enable Android Screen Pinning or use a device-owner allowlist for true locked fullscreen. " + shortError(t));
        }
    }

    private void updateLockTaskStatusAfterStart() {
        try {
            if (store == null || !store.lockTaskEnabled()) return;
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && am != null
                    && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
                appendKioskStatus("Lock Task / Screen Pinning is active.");
            } else {
                appendKioskStatus("Lock Task / Screen Pinning is not active yet. Android may require Screen Pinning to be enabled, user confirmation, or device-owner allowlisting.");
            }
        } catch (Throwable ignored) {
        }
    }

    private void stopLockTaskIfActive() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && am != null
                    && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                return;
            }
            stopLockTask();
        } catch (Throwable ignored) {
        }
    }

    private void appendKioskStatus(String message) {
        try {
            if (TextUtils.isEmpty(message)) return;
            kioskDebug("status", message);
            if (status != null) {
                status.setText("");
                status.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private String shortError(Throwable t) {
        if (t == null) return "";
        String msg = t.getMessage();
        if (TextUtils.isEmpty(msg)) msg = t.getClass().getSimpleName();
        return "(" + msg + ")";
    }

    private void clearKioskWindowFlags() {
        try {
            Window w = getWindow();
            if (w == null) return;
            w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            View decor = w.getDecorView();
            if (decor != null) {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController controller = decor.getWindowInsetsController();
                    if (controller != null) {
                        controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class IconGridOptions {
        final int spanCount;
        final int iconSizePx;
        final int itemMinHeightPx;
        final int rowCount;

        IconGridOptions(int spanCount, int iconSizePx, int itemMinHeightPx, int rowCount) {
            this.spanCount = Math.max(1, spanCount);
            this.iconSizePx = Math.max(1, iconSizePx);
            this.itemMinHeightPx = Math.max(0, itemMinHeightPx);
            this.rowCount = Math.max(0, rowCount);
        }
    }

    private void scheduleTimerRefresh() {
        if (handler == null) return;
        handler.removeCallbacks(timerRefresh);
        if (!store.timerRefreshEnabled()) return;
        long delay = Math.max(1, store.timerRefreshMinutes()) * 60_000L;
        handler.postDelayed(timerRefresh, delay);
    }

    private void timerRefresh() {
        refreshItems();
        scheduleTimerRefresh();
    }

    private boolean isKioskDebugOutputEnabled() {
        try {
            SharedPreferences prefs = getSharedPreferences(SettingsPreferenceKeys.PREFS, MODE_PRIVATE);
            return prefs != null && prefs.getBoolean(SettingsPreferenceKeys.DEBUG_OUTPUT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void kioskDebug(String area, String message) {
        if (!isKioskDebugOutputEnabled()) return;
        try {
            DebugLog.log(DebugLog.DEFAULT_TAG, "kiosk", area, message);
        } catch (Throwable ignored) {
        }
    }

    private void applyKioskWindowFlags() {
        try {
            Window w = getWindow();
            if (w == null) return;
            boolean hideBars = store == null || store.hideStatusBar();
            if (hideBars) {
                w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            } else {
                w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(!hideBars);
            }
            View decor = w.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (hideBars) {
                flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }
            decor.setSystemUiVisibility(flags);
            decor.setOnSystemUiVisibilityChangeListener(visibility -> {
                try {
                    if (handler != null && (store == null || store.hideStatusBar())) {
                        handler.removeCallbacks(immersiveReapply);
                        handler.postDelayed(immersiveReapply, 650);
                    }
                } catch (Throwable ignored) {
                }
            });
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller != null) {
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    if (hideBars) {
                        controller.hide(WindowInsets.Type.systemBars());
                    } else {
                        controller.show(WindowInsets.Type.systemBars());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
