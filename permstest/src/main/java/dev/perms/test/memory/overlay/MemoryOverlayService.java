package dev.perms.test.memory.overlay;

import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.PermsTestUiCompat;
import dev.perms.test.R;

import dev.perms.test.vr.PermsTestVrOverlayCompat;
import dev.perms.test.vr.MemoryOverlayVrPanelActivity;
import dev.perms.test.vr.MemoryOverlayVrTextInputActivity;
import dev.perms.test.memory.MemoryToolRuntime;
import dev.perms.test.memory.hex.MemoryHexOverlayController;
import dev.perms.test.memory.panel.MemoryHexPanelActivity;
import dev.perms.test.memory.panel.MemoryDisassemblyPanelActivity;
import dev.perms.test.memory.panel.MemorySpecialToolsPanelActivity;
import dev.perms.test.memory.payload.MemoryHexPayloadStore;
import dev.perms.test.memory.MemoryToolHelper;
import dev.perms.test.memory.disassembly.MemoryDisassemblyOverlayController;
import dev.perms.test.memory.tools.MemorySpecialToolsOverlayController;
import dev.perms.test.memory.MemoryPackageAdapter;
import dev.perms.test.memory.MemoryPackageEntry;
import dev.perms.test.memory.MemoryProcessEntry;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemoryOverlayService extends Service {
    public static final String EXTRA_TARGET_PACKAGE = "targetPackage";
    public static final String EXTRA_TARGET_PID = "targetPid";
    public static final String EXTRA_TARGET_LABEL = "targetLabel";
    public static final String EXTRA_PAYLOAD_FILE_NAMES = "payloadFileNames";
    public static final String EXTRA_PAYLOAD_DELAY_MS = "payloadDelayMs";
    public static final String ACTION_LAUNCH_AND_APPLY_PAYLOADS = "dev.perms.test.action.LAUNCH_AND_APPLY_MEMORY_PAYLOADS";
    public static final String ACTION_APPLY_PAYLOADS_ONLY = "dev.perms.test.action.APPLY_MEMORY_PAYLOADS_ONLY";
    public static final String ACTION_SHOW_OVERLAY = "dev.perms.test.action.SHOW_MEMORY_OVERLAY";
    public static final String ACTION_HIDE_OVERLAY = "dev.perms.test.action.HIDE_MEMORY_OVERLAY";
    public static final String ACTION_STOP_OVERLAY = "dev.perms.test.action.STOP_MEMORY_OVERLAY";
    public static final String ACTION_SHOW_HEX_OVERLAY = "dev.perms.test.action.SHOW_MEMORY_HEX_OVERLAY";
    public static final String ACTION_SHOW_DISASSEMBLY_OVERLAY = "dev.perms.test.action.SHOW_MEMORY_DISASSEMBLY_OVERLAY";
    public static final String ACTION_SHOW_SPECIAL_TOOLS_OVERLAY = "dev.perms.test.action.SHOW_MEMORY_SPECIAL_TOOLS_OVERLAY";
    public static final String ACTION_VR_RESTORE_OVERLAY_FOR_TARGET = "dev.perms.test.action.VR_RESTORE_MEMORY_OVERLAY_FOR_TARGET";
    private static final String ACTION_RESTORE_NOTIFICATION = "dev.perms.test.action.RESTORE_MEMORY_OVERLAY_NOTIFICATION";

    private static final String PREFS = "perms_test";
    // Keep the stored key name for compatibility with existing installs; UI now calls this Debug Output.
    private static final String PREF_DEBUG_OUTPUT = "debug_mode";
    private static final String PREF_SYNC_RESULT_VALUE = "memory_sync_result_value";
    private static final String LOG_TAG = "PermsTestMemoryOverlay";
    private static final String NOTIF_CHANNEL = "memory_overlay_status_v2";
    private static final int NOTIF_ID = 70421;
    private static final long TARGET_PACKAGE_DROPDOWN_CACHE_MS = 30000L;
    private static final long LAUNCH_PASSIVE_INPUT_MS = 5000L;
    private static final long OVERLAY_INPUT_FOCUS_WINDOW_MS = 1800L;
    private static final long NOTIFICATION_WATCHDOG_MS = 30000L;
    private static final long PAYLOAD_LAUNCH_INITIAL_DELAY_MS = 900L;
    private static final long PAYLOAD_LAUNCH_PID_TIMEOUT_MS = 18000L;
    private static final long PAYLOAD_LAUNCH_PID_POLL_MS = 350L;
    private static final long PAYLOAD_LAUNCH_PID_FALLBACK_SCAN_MS = 2500L;
    private static final String PREF_OVERLAY_X = "memory_overlay_x";
    private static final String PREF_OVERLAY_Y = "memory_overlay_y";
    private static final String PREF_OVERLAY_W = "memory_overlay_w";
    private static final String PREF_OVERLAY_H = "memory_overlay_h";
    private static final String PREF_VR_OVERLAY_X = "memory_overlay_vr_x";
    private static final String PREF_VR_OVERLAY_Y = "memory_overlay_vr_y";
    private static final String PREF_VR_OVERLAY_W = "memory_overlay_vr_w";
    private static final String PREF_VR_OVERLAY_H = "memory_overlay_vr_h";
    private static final String MEMORY_DUMP_DIR = "/sdcard/dev.perms.test/memory_dumps";
    private static final String MEMORY_PATCH_DIR = "/sdcard/dev.perms.test/memory_patches";
    private static final String PAYLOAD_DEBUG_FILE = "payload_dump.txt";
    private static final Pattern PAYLOAD_DEBUG_TID_PATTERN = Pattern.compile("\\b(?:Attached|Detached)\\s+TID:\\s*\\d+\\b\\s*");
    private static final Pattern PAYLOAD_SEARCH_VALUE_PATTERN = Pattern.compile("Address:\\s*(0x[0-9a-fA-F]+)\\s+Value:\\s*([0-9a-fA-F]+)");
    private static final int PAYLOAD_DEBUG_MAX_READBACKS = 10;
    private static final int MAX_STATE_ROWS_TO_LOAD_FOR_UI = 50000;
    private static final int FILETYPE_HEADER_READ_BYTES = 64 * 1024;
    private static final int FILETYPE_SCAN_READ_LIMIT_BYTES = 64 * 1024 * 1024;
    private static final int FILETYPE_FALLBACK_EXPORT_BYTES = 1024 * 1024;
    private static final int FILETYPE_BASE64_CAPTURE_LIMIT_BYTES = 8 * 1024 * 1024;
    private static final int FILETYPE_EOF_SCAN_WINDOW_BYTES = 2 * 1024 * 1024;
    private static final int FILETYPE_EOF_SCAN_OVERLAP_BYTES = 32;
    private static final Pattern DETAIL_HEX_ADDRESS_PATTERN = Pattern.compile("0x[0-9a-fA-F]{6,16}");
    private static final int DETAIL_HEX_ADDRESS_FALLBACK_COLOR = 0xFFCDBDFF;

    private static final int BASE_OVERLAY_FLAGS =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable notificationWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!notificationWatchdogRunning) return;
            ensureForegroundNotification();
            mainHandler.postDelayed(this, NOTIFICATION_WATCHDOG_MS);
        }
    };
    private boolean notificationWatchdogRunning;

    private WindowManager wm;
    private ContextThemeWrapper overlayThemeContext;
    private View root;
    private ViewGroup activePanelContainer;
    private Runnable activePanelCloseCallback;
    private ViewGroup activeHexPanelContainer;
    private Runnable activeHexPanelCloseCallback;
    private ViewGroup activeDisassemblyPanelContainer;
    private Runnable activeDisassemblyPanelCloseCallback;
    private ViewGroup activeSpecialToolsPanelContainer;
    private Runnable activeSpecialToolsPanelCloseCallback;
    private boolean detachedVrInputWasPanel;
    private final Object backendShellLock = new Object();
    private boolean memoryCommandInFlight;
    private String memoryCommandInFlightName;
    private WindowManager.LayoutParams overlayLayoutParams;
    private MemoryHexOverlayController hexOverlayController;
    private MemoryDisassemblyOverlayController disassemblyOverlayController;
    private MemorySpecialToolsOverlayController specialToolsOverlayController;
    private MaterialCardView card;
    private MaterialAutoCompleteTextView ddTargetPkg;
    private MaterialAutoCompleteTextView ddProcess;
    private MaterialAutoCompleteTextView ddDataType;
    private MaterialAutoCompleteTextView ddSearchMode;
    private TextInputLayout tilSearchValue;
    private android.widget.EditText edtSearchValue;
    private android.widget.EditText edtPatchName;
    private android.widget.EditText edtPatchAddress;
    private android.widget.EditText edtPatchValue;
    private android.widget.CompoundButton chkPatchHex;
    private android.widget.CompoundButton chkStringPatchTruncate;
    private android.widget.CompoundButton chkFreezePatch;
    private android.widget.CompoundButton chkDumpToDisk;
    private android.widget.EditText edtResultLimit;
    private android.widget.EditText edtMaxResults;
    private android.widget.EditText edtDumpBegin;
    private android.widget.EditText edtDumpEnd;
    private View btnPatchMultiple;
    private View btnFreezeChecked;
    private TextView txtSummary;
    private TextView txtOutput;
    private TextView txtSelectedResult;
    private TextView txtScanRangeStatus;
    private android.widget.CompoundButton chkAutoRange;
    private android.widget.CompoundButton chkSyncResultValue;
    private android.widget.EditText edtAutoRangeLimit;
    private ListView lstResults;
    private View rowSelectVisibleResults;
    private View btnSelectAllResults;
    private View btnSelectNoneResults;
    private View btnPreviousResultPage;
    private View btnNextResultPage;
    private View rowSessionButtons;
    private View rowTargetPackageLoading;
    private View rowProcessLoading;
    private MemoryPackageAdapter packageAdapter;
    private ArrayAdapter<MemoryProcessEntry> processAdapter;
    private ArrayAdapter<MemoryResultRow> resultAdapter;
    private final ArrayList<MemoryPackageEntry> packageItems = new ArrayList<>();
    private final ArrayList<MemoryProcessEntry> processItems = new ArrayList<>();
    private final ArrayList<MemoryResultRow> resultItems = new ArrayList<>();
    private final Set<String> checkedPatchResultKeys = new HashSet<>();
    private final Map<String, String> patchNameByKey = new HashMap<>();
    private final Map<String, String> savedPatchValueByKey = new HashMap<>();
    private MemoryResultRow selectedResult;
    private ArrayList<MemoryResultRow> pendingPatchResultUpdates;
    private String pendingPatchResultValue;
    private boolean hasActiveResultSet;
    private int activeResultCount;
    private String lastStateJson;
    private String resultStateSearchValue;
    private int resultPageIndex;
    private int scanRangePageIndex;
    private int scanRangeStepResults;
    private int scanRangeSkipResults;
    private String scanRangeBaseCommand;
    private String scanRangeBaseDataType;
    private String scanRangeBaseValue;
    private String scanRangeBaseScanValue;
    private final Map<Integer, Integer> scanRangeResultCountByPage = new HashMap<>();
    private final Map<Integer, Integer> scanRangeBaselineCountByPage = new HashMap<>();
    private volatile boolean autoRangeInFlight;
    private volatile boolean timerFinderInFlight;
    private volatile boolean resultValueSyncInFlight;
    private int timerFinderBaselinePageCount;
    private int timerFinderBaselineTotalCount;
    private String timerFinderBaselinePackage;
    private String timerFinderBaselinePid;
    private String pendingSelectPkg;
    private String pendingSelectPid;
    private String lastResolvedAutoPid;
    private String manualToolAddressOverride;
    private String vrStoppedTargetPackage;
    private boolean dropdownOpen;
    private boolean reopenTargetPackageDropdownAfterRefresh;
    private boolean reopenProcessDropdownAfterRefresh;
    private List<MemoryPackageEntry> pendingPackageListUpdate;
    private List<MemoryProcessEntry> pendingProcessListUpdate;
    private long lastTargetPackageRefresh;
    private long lastDropdownOpenRequest;
    private long launchPassiveUntil;
    private long overlayFocusAllowedUntil;
    private String restoreSearchValue;
    private String restorePatchName;
    private String restorePatchAddress;
    private String restorePatchValue;
    private String restoreResultLimit;
    private String restoreMaxResults;
    private String restoreDumpBegin;
    private String restoreDumpEnd;
    private String restoreAutoRangeLimit;
    // Details log is session-scoped.  It accumulates visible action output while
    // the overlay service lives, then clears on a new service session/destroy.
    private String lastOverlayDetailText;
    private final StringBuilder overlaySessionDetailLog = new StringBuilder();
    private boolean restoreOverlayInputStatePending;
    private String restoreDataType;
    private String restoreSearchMode;
    private boolean preserveScanStateOnNextOverlayShow;
    private volatile boolean targetPackageRefreshInFlight;
    private volatile int targetPackageRefreshGeneration;
    private volatile int activeTargetPackageRefreshGeneration;
    private volatile boolean processRefreshInFlight;
    private volatile int processRefreshGeneration;
    private volatile int activeProcessRefreshGeneration;
    private boolean freezePatchActive;
    private boolean freezePatchInFlight;
    private String freezePatchValue;
    private String freezePatchStateJson;
    private String freezePatchResultKey;
    private final Set<String> freezePatchResultKeys = new HashSet<>();
    private int freezePatchToken;
    private long lastFreezePatchErrorUptime;

    private final Runnable resultValueSyncRunnable = new Runnable() {
        @Override
        public void run() {
            refreshVisibleResultValuesIfNeeded();
        }
    };

    public final class LocalBinder extends Binder {
        public void showPanel(ViewGroup container, Intent requestIntent, Runnable closeCallback) {
            mainHandler.post(() -> showPanelInternal(container, requestIntent, closeCallback));
        }

        public void detachPanel(ViewGroup container) {
            mainHandler.post(() -> detachPanelInternal(container));
        }
    }

    private final IBinder localBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayThemeContext = new ContextThemeWrapper(this, R.style.Theme_ShizukuSample);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_RESTORE_NOTIFICATION.equals(action)) {
            ensureForegroundNotification();
            return START_NOT_STICKY;
        }
        if (PermsTestVrOverlayCompat.ACTION_TEXT_INPUT_RESULT.equals(action)) {
            ensureForegroundNotification();
            handleVrTextInputResult(intent);
            return START_NOT_STICKY;
        }
        if (ACTION_VR_RESTORE_OVERLAY_FOR_TARGET.equals(action)) {
            ensureForegroundNotification();
            restoreVrOverlayForTarget(intent);
            return START_NOT_STICKY;
        }
        if (ACTION_LAUNCH_AND_APPLY_PAYLOADS.equals(action) || ACTION_APPLY_PAYLOADS_ONLY.equals(action)) {
            ensureForegroundNotification();
            handleLaunchAndApplyPayloads(intent, startId, ACTION_LAUNCH_AND_APPLY_PAYLOADS.equals(action));
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_OVERLAY.equals(action)) {
            PermsTestVrOverlayCompat.clearHiddenOverlayForVr(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_HIDE_OVERLAY.equals(action)) {
            ensureForegroundNotification();
            hideOverlayIntoNotification();
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW_HEX_OVERLAY.equals(action)) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_SHORT).show();
                stopSelf();
                return START_NOT_STICKY;
            }
            ensureForegroundNotification();
            rememberToolTargetExtras(intent);
            showHexOverlayWindow();
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW_DISASSEMBLY_OVERLAY.equals(action)) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_SHORT).show();
                stopSelf();
                return START_NOT_STICKY;
            }
            ensureForegroundNotification();
            rememberToolTargetExtras(intent);
            showDisassemblyOverlayWindow();
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW_SPECIAL_TOOLS_OVERLAY.equals(action)) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_SHORT).show();
                stopSelf();
                return START_NOT_STICKY;
            }
            ensureForegroundNotification();
            rememberToolTargetExtras(intent);
            if (!PermsTestVrOverlayCompat.isEnabled(this)) {
                showOrUpdateOverlay(intent);
                showOverlayFromNotification();
            }
            showSpecialToolsOverlayWindow();
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW_OVERLAY.equals(action)) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_SHORT).show();
                stopSelf();
                return START_NOT_STICKY;
            }
            ensureForegroundNotification();
            showOrUpdateOverlay(intent);
            showOverlayFromNotification();
            return START_NOT_STICKY;
        }

        ensureForegroundNotification();
        return START_NOT_STICKY;
    }

    private void rememberToolTargetExtras(@Nullable Intent intent) {
        if (intent == null) return;
        String pkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
        String pid = intent.getStringExtra(EXTRA_TARGET_PID);
        if (!TextUtils.isEmpty(pkg)) pendingSelectPkg = pkg.trim();
        if (!TextUtils.isEmpty(pid)) pendingSelectPid = pid.trim();
    }

    private void showPanelInternal(ViewGroup container, Intent requestIntent, Runnable closeCallback) {
        if (container == null) return;
        String action = requestIntent == null ? null : requestIntent.getAction();
        if (TextUtils.isEmpty(action)) action = ACTION_SHOW_OVERLAY;
        rememberToolTargetExtras(requestIntent);
        if (ACTION_SHOW_HEX_OVERLAY.equals(action)) {
            activeHexPanelContainer = container;
            activeHexPanelCloseCallback = closeCallback;
            showHexPanelWindow(container, closeCallback);
        } else if (ACTION_SHOW_DISASSEMBLY_OVERLAY.equals(action)) {
            activeDisassemblyPanelContainer = container;
            activeDisassemblyPanelCloseCallback = closeCallback;
            showDisassemblyPanelWindow(container, closeCallback);
        } else if (ACTION_SHOW_SPECIAL_TOOLS_OVERLAY.equals(action)) {
            activeSpecialToolsPanelContainer = container;
            activeSpecialToolsPanelCloseCallback = closeCallback;
            showSpecialToolsPanelWindow(container, closeCallback);
        } else {
            activePanelContainer = container;
            activePanelCloseCallback = closeCallback;
            showOrUpdatePanel(requestIntent, container, closeCallback);
        }
    }

    private void detachPanelInternal(ViewGroup container) {
        if (container == null) return;
        if (container == activePanelContainer) {
            detachMainPanelInternal(container);
        } else if (container == activeHexPanelContainer) {
            destroyHexPanelController();
            clearPanelContainer(container);
            activeHexPanelContainer = null;
            activeHexPanelCloseCallback = null;
        } else if (container == activeDisassemblyPanelContainer) {
            destroyDisassemblyPanelController();
            clearPanelContainer(container);
            activeDisassemblyPanelContainer = null;
            activeDisassemblyPanelCloseCallback = null;
        } else if (container == activeSpecialToolsPanelContainer) {
            destroySpecialToolsPanelController();
            clearPanelContainer(container);
            activeSpecialToolsPanelContainer = null;
            activeSpecialToolsPanelCloseCallback = null;
        }
    }

    private void detachMainPanelInternal(ViewGroup container) {
        try {
            rememberOverlayInputState();
        } catch (Throwable ignored) {
        }
        try {
            stopFreezePatch(false);
        } catch (Throwable ignored) {
        }
        try {
            detachViewFromParent(root);
        } catch (Throwable ignored) {
        }
        clearMainViewReferences();
        clearPanelContainer(container);
        activePanelContainer = null;
        activePanelCloseCallback = null;
    }

    private static void clearPanelContainer(ViewGroup container) {
        if (container == null) return;
        try {
            container.removeAllViews();
        } catch (Throwable ignored) {
        }
    }

    private void showOrUpdatePanel(Intent intent, ViewGroup container, Runnable closeCallback) {
        if (container == null) return;
        if (root != null && overlayLayoutParams != null) {
            try { wm.removeView(root); } catch (Throwable ignored) {}
            clearMainViewReferences();
        }
        final boolean created = root == null;
        final boolean restoringVrScannerState = shouldPreserveVrScannerStateOnOverlayRecreate();
        debugLog("showOrUpdatePanel created=" + created
                + " vr=" + PermsTestVrOverlayCompat.isEnabled(this)
                + " restoreState=" + restoringVrScannerState
                + " activeResults=" + activeResultCount
                + " hasActive=" + hasActiveResultSet);
        if (created) {
            if (TextUtils.isEmpty(lastOverlayDetailText)) {
                resetDetailSessionLog();
            }
            root = LayoutInflater.from(overlayThemeContext).inflate(R.layout.overlay_memory_controls, null, false);
            PermsTestUiCompat.applyActivityUiProfile(container.getContext(), root);
            bindOverlayViews();
            installMainPanelChrome(closeCallback);
        }
        attachRootToPanel(container, root);
        applyOverlayPrefs();
        applyMainPanelPrefs();

        boolean targetChanged = false;
        boolean pidProvided = false;
        if (intent != null) {
            String pkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
            String pid = intent.getStringExtra(EXTRA_TARGET_PID);
            pendingSelectPkg = TextUtils.isEmpty(pkg) ? pendingSelectPkg : pkg.trim();
            pendingSelectPid = TextUtils.isEmpty(pid) ? pendingSelectPid : pid.trim();
            pidProvided = !TextUtils.isEmpty(pendingSelectPid);
            if (!TextUtils.isEmpty(pendingSelectPkg)) {
                String current = getTargetPackage();
                targetChanged = !TextUtils.equals(current, pendingSelectPkg);
                ddTargetPkg.setText(pendingSelectPkg, false);
                if (targetChanged) {
                    lastResolvedAutoPid = null;
                    if (!restoringVrScannerState) {
                        clearOverlayScanState();
                    } else {
                        debugLog("Preserved scanner state while reapplying VR panel target " + pendingSelectPkg);
                    }
                }
                ensurePackageEntryPresent(pendingSelectPkg, false);
                if (packageAdapter != null) packageAdapter.setItems(packageItems);
            }
        }
        if (created && TextUtils.isEmpty(getTargetPackage()) && !TextUtils.isEmpty(pendingSelectPkg)) {
            ddTargetPkg.setText(pendingSelectPkg, false);
            ensurePackageEntryPresent(pendingSelectPkg, false);
            if (packageAdapter != null) packageAdapter.setItems(packageItems);
        }
        restoreOverlayInputStateIfNeeded();
        restoreOverlayRuntimeStateAfterViewRecreate(created);
        preserveScanStateOnNextOverlayShow = false;

        if (created) {
            initializeTargetListsForOverlayOpen(restoringVrScannerState);
        } else if (pidProvided && !TextUtils.isEmpty(pendingSelectPid)) {
            selectPid(pendingSelectPid, true);
        } else if (targetChanged) {
            clearProcessSelectionForTargetChange();
        }
    }

    private void detachMainWindowForPanelTool() {
        if (root == null) return;
        try {
            rememberOverlayInputState();
        } catch (Throwable ignored) {
        }
        try {
            if (overlayLayoutParams != null && wm != null) {
                wm.removeView(root);
            } else {
                detachViewFromParent(root);
            }
        } catch (Throwable ignored) {
        }
        clearMainViewReferences();
    }

    private void destroyHexPanelController() {
        if (hexOverlayController == null) return;
        try { hexOverlayController.destroy(); } catch (Throwable ignored) {}
        hexOverlayController = null;
    }

    private void destroyDisassemblyPanelController() {
        if (disassemblyOverlayController == null) return;
        try { disassemblyOverlayController.destroy(); } catch (Throwable ignored) {}
        disassemblyOverlayController = null;
    }

    private void destroySpecialToolsPanelController() {
        if (specialToolsOverlayController == null) return;
        try { specialToolsOverlayController.destroy(); } catch (Throwable ignored) {}
        specialToolsOverlayController = null;
    }

    private void destroyPanelToolControllers() {
        destroyHexPanelController();
        destroyDisassemblyPanelController();
        destroySpecialToolsPanelController();
    }

    private void showHexPanelWindow(ViewGroup container, Runnable closeCallback) {
        try {
            if (container == null) return;
            activeHexPanelContainer = container;
            activeHexPanelCloseCallback = closeCallback;
            if (hexOverlayController == null) {
                hexOverlayController = new MemoryHexOverlayController(
                        this, overlayThemeContext, wm, this::defaultToolAddress, this::appendStatus,
                        this::requestToolDump, this::requestToolWriteBytes, this::requestToolSearchHexPayload,
                        this::requestToolApplyHexPayloads, this::getTargetPackage,
                        this::onHexAddressChanged, this::onHexByteSelected, this::onHexPayloadLoaded,
                        this::closeActiveHexPanel);
            }
            hexOverlayController.showInPanel(container);
            String address = defaultToolAddress();
            if (!TextUtils.isEmpty(address)) {
                hexOverlayController.setAddress(address, selectedResult != null);
                syncToolAddressToDisassembly(address, false);
            }
        } catch (Throwable t) {
            appendStatus("Hex panel failed: " + t);
            debugLog("tool panel show failed tool=hex", t);
        }
    }

    private void showDisassemblyPanelWindow(ViewGroup container, Runnable closeCallback) {
        try {
            if (container == null) return;
            activeDisassemblyPanelContainer = container;
            activeDisassemblyPanelCloseCallback = closeCallback;
            if (disassemblyOverlayController == null) {
                disassemblyOverlayController = new MemoryDisassemblyOverlayController(
                        this, overlayThemeContext, wm, this::defaultToolAddress, this::appendStatus,
                        this::requestToolDump, new MemoryDisassemblyOverlayController.ActionListener() {
                            @Override public void onOpenHex(long address) { openHexFromDisassemblyAddress(address); }
                            @Override public void onAddressSelected(long address) { onDisassemblyAddressSelected(address); }
                        }, this::closeActiveDisassemblyPanel);
            }
            disassemblyOverlayController.showInPanel(container);
            String address = defaultToolAddress();
            if (!TextUtils.isEmpty(address)) {
                disassemblyOverlayController.setAddress(address, selectedResult != null);
            }
        } catch (Throwable t) {
            appendStatus("Disassembly panel failed: " + t);
            debugLog("tool panel show failed tool=disassembly", t);
        }
    }

    private void showSpecialToolsPanelWindow(ViewGroup container, Runnable closeCallback) {
        try {
            if (container == null) return;
            activeSpecialToolsPanelContainer = container;
            activeSpecialToolsPanelCloseCallback = closeCallback;
            if (specialToolsOverlayController == null) {
                specialToolsOverlayController = new MemorySpecialToolsOverlayController(
                        this, overlayThemeContext, wm, this::defaultToolAddress, this::appendStatus,
                        new MemorySpecialToolsOverlayController.ActionListener() {
                            @Override public void onSearchMemoryFileType(String extension) { searchMemoryByFileType(extension); }
                            @Override public void onExportMemoryByType(String extension, String begin, String end) { exportMemoryByFileType(extension, begin, end); }
                            @Override public void onImportMemoryByType(String extension, String begin, String sourcePath) { importMemoryByFileType(extension, begin, sourcePath); }
                            @Override public void onStartTimerBaseline() { startTimerFinderBaseline(); }
                            @Override public void onFindTimerChanges() { findTimerCandidates(); }
                        }, this::closeActiveSpecialToolsPanel);
            }
            specialToolsOverlayController.showInPanel(container);
        } catch (Throwable t) {
            appendStatus("Special Tools panel failed: " + t);
            debugLog("tool panel show failed tool=special", t);
        }
    }

    private boolean hasAnyMemoryPanelContainer() {
        return activePanelContainer != null
                || activeHexPanelContainer != null
                || activeDisassemblyPanelContainer != null
                || activeSpecialToolsPanelContainer != null;
    }

    private void openMemoryToolPanelActivity(String action, Class<?> activityClass) {
        try {
            Intent i = new Intent(this, activityClass);
            i.setAction(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            String pkg = getTargetPackage();
            String pid = getSelectedPid();
            if (!TextUtils.isEmpty(pkg)) i.putExtra(EXTRA_TARGET_PACKAGE, pkg);
            if (!TextUtils.isEmpty(pid)) i.putExtra(EXTRA_TARGET_PID, pid);
            startActivity(i);
        } catch (Throwable t) {
            appendStatus("Memory panel failed: " + t.getMessage());
            debugLog("memory tool panel start failed action=" + cleanLogValue(action), t);
        }
    }

    private void attachRootToPanel(ViewGroup container, View view) {
        if (container == null || view == null) return;
        try {
            detachViewFromParent(view);
            container.removeAllViews();
            container.addView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable t) {
            appendStatus("Memory panel attach failed: " + t.getMessage());
        }
    }

    private void installMainPanelChrome(Runnable closeCallback) {
        View close = root == null ? null : root.findViewById(R.id.btnOverlayMemoryClose);
        if (close != null) close.setOnClickListener(v -> closeActivePanel());
        View minimize = root == null ? null : root.findViewById(R.id.btnOverlayMemoryMinimize);
        if (minimize != null) minimize.setVisibility(View.GONE);
    }

    private void applyMainPanelPrefs() {
        if (root == null) return;
        View handle = root.findViewById(R.id.overlayMemoryResizeHandle);
        if (handle != null) handle.setVisibility(View.GONE);
        View minimize = root.findViewById(R.id.btnOverlayMemoryMinimize);
        if (minimize != null) minimize.setVisibility(View.GONE);
        if (card != null) card.setAlpha(1.0f);
    }

    private void closeActivePanel() {
        Runnable callback = activePanelCloseCallback;
        if (callback != null) {
            try { callback.run(); } catch (Throwable ignored) {}
        }
    }

    private void closeActiveHexPanel() {
        Runnable callback = activeHexPanelCloseCallback;
        if (callback != null) {
            try { callback.run(); } catch (Throwable ignored) {}
        }
    }

    private void closeActiveDisassemblyPanel() {
        Runnable callback = activeDisassemblyPanelCloseCallback;
        if (callback != null) {
            try { callback.run(); } catch (Throwable ignored) {}
        }
    }

    private void closeActiveSpecialToolsPanel() {
        Runnable callback = activeSpecialToolsPanelCloseCallback;
        if (callback != null) {
            try { callback.run(); } catch (Throwable ignored) {}
        }
    }

    private static void detachViewFromParent(View view) {
        if (view == null) return;
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }

    private void clearMainViewReferences() {
        root = null;
        overlayLayoutParams = null;
        card = null;
        ddTargetPkg = null;
        ddProcess = null;
        ddDataType = null;
        ddSearchMode = null;
        tilSearchValue = null;
        edtSearchValue = null;
        edtPatchName = null;
        edtPatchAddress = null;
        edtPatchValue = null;
        chkPatchHex = null;
        chkStringPatchTruncate = null;
        chkFreezePatch = null;
        chkDumpToDisk = null;
        edtResultLimit = null;
        edtMaxResults = null;
        edtDumpBegin = null;
        edtDumpEnd = null;
        btnPatchMultiple = null;
        btnFreezeChecked = null;
        txtSummary = null;
        txtOutput = null;
        txtSelectedResult = null;
        txtScanRangeStatus = null;
        chkAutoRange = null;
        chkSyncResultValue = null;
        edtAutoRangeLimit = null;
        lstResults = null;
        rowSelectVisibleResults = null;
        btnSelectAllResults = null;
        btnSelectNoneResults = null;
        btnPreviousResultPage = null;
        btnNextResultPage = null;
        rowSessionButtons = null;
        rowTargetPackageLoading = null;
        rowProcessLoading = null;
        packageAdapter = null;
        processAdapter = null;
        resultAdapter = null;
        dropdownOpen = false;
    }

    private void showOrUpdateOverlay(Intent intent) {
        if (activePanelContainer != null) {
            detachPanelInternal(activePanelContainer);
        }
        final boolean created = root == null;
        final boolean restoringVrScannerState = shouldPreserveVrScannerStateOnOverlayRecreate();
        debugLog("showOrUpdateOverlay created=" + created
                + " vr=" + PermsTestVrOverlayCompat.isEnabled(this)
                + " restoreState=" + restoringVrScannerState
                + " activeResults=" + activeResultCount
                + " hasActive=" + hasActiveResultSet);
        if (created) {
            if (TextUtils.isEmpty(lastOverlayDetailText)) {
                resetDetailSessionLog();
            }
            root = LayoutInflater.from(overlayThemeContext).inflate(R.layout.overlay_memory_controls, null, false);
            PermsTestUiCompat.applyMemoryOverlayUiProfile(this, root);
            bindOverlayViews();
            installUnexpectedFocusGuard();
            attachDragHandler(root.findViewById(R.id.overlayMemoryHeader));
            attachResizeHandler(root.findViewById(R.id.overlayMemoryResizeHandle));
            addOverlayView();
            setOverlayInteractive(false);
        }

        applyOverlayPrefs();
        boolean targetChanged = false;
        boolean pidProvided = false;
        if (intent != null) {
            String pkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
            String pid = intent.getStringExtra(EXTRA_TARGET_PID);
            pendingSelectPkg = TextUtils.isEmpty(pkg) ? pendingSelectPkg : pkg.trim();
            pendingSelectPid = TextUtils.isEmpty(pid) ? pendingSelectPid : pid.trim();
            pidProvided = !TextUtils.isEmpty(pendingSelectPid);
            if (!TextUtils.isEmpty(pendingSelectPkg)) {
                String current = getTargetPackage();
                targetChanged = !TextUtils.equals(current, pendingSelectPkg);
                ddTargetPkg.setText(pendingSelectPkg, false);
                if (targetChanged) {
                    lastResolvedAutoPid = null;
                    if (!restoringVrScannerState) {
                        clearOverlayScanState();
                    } else {
                        debugLog("Preserved scanner state while reapplying VR target " + pendingSelectPkg);
                    }
                }
                ensurePackageEntryPresent(pendingSelectPkg, false);
                if (packageAdapter != null) packageAdapter.setItems(packageItems);
            }
        }
        if (created && TextUtils.isEmpty(getTargetPackage()) && !TextUtils.isEmpty(pendingSelectPkg)) {
            ddTargetPkg.setText(pendingSelectPkg, false);
            ensurePackageEntryPresent(pendingSelectPkg, false);
            if (packageAdapter != null) packageAdapter.setItems(packageItems);
        }
        restoreOverlayInputStateIfNeeded();
        restoreOverlayRuntimeStateAfterViewRecreate(created);
        preserveScanStateOnNextOverlayShow = false;

        if (created) {
            initializeTargetListsForOverlayOpen(restoringVrScannerState);
        } else if (pidProvided && !TextUtils.isEmpty(pendingSelectPid)) {
            selectPid(pendingSelectPid, true);
        } else if (targetChanged) {
            clearProcessSelectionForTargetChange();
        }
    }

    private void initializeTargetListsForOverlayOpen(boolean restoringVrScannerState) {
        if (restoringVrScannerState) {
            String pkg = getTargetPackage();
            if (!TextUtils.isEmpty(pkg)) {
                ensurePackageEntryPresent(pkg, true);
                if (packageAdapter != null) packageAdapter.setItems(packageItems);
            }
            if (!TextUtils.isEmpty(pendingSelectPid)) {
                selectPid(pendingSelectPid);
            }
            debugLog("Skipped startup package/process refresh while restoring VR scanner state for " + pkg);
            return;
        }
        String selectedPackage = getTargetPackage();
        if (shouldScanAtStartup()) {
            if (TextUtils.isEmpty(selectedPackage)) {
                appendStatus("Loading target packages...");
                refreshTargetPackages();
            } else {
                ensurePackageEntryPresent(selectedPackage, false);
                if (packageAdapter != null) packageAdapter.setItems(packageItems);
                seedAutoProcessSelection();
                appendStatus("Target package restored. Press Attach after the app is running, or use Refresh to scan packages/processes.");
            }
        } else if (TextUtils.isEmpty(selectedPackage)) {
            appendStatus("Startup package scan is off. Use Refresh or enter a target package.");
        } else {
            ensurePackageEntryPresent(selectedPackage, false);
            if (packageAdapter != null) packageAdapter.setItems(packageItems);
            seedAutoProcessSelection();
            appendStatus("Target package restored. Press Attach after the app is running, or use Refresh to scan processes.");
        }
    }

    private boolean shouldPreserveVrScannerStateOnOverlayRecreate() {
        if (!PermsTestVrOverlayCompat.isEnabled(this)) return false;
        return preserveScanStateOnNextOverlayShow
                || hasActiveResultSet
                || activeResultCount > 0
                || !TextUtils.isEmpty(lastStateJson)
                || restoreOverlayInputStatePending;
    }

    private void installUnexpectedFocusGuard() {
        if (root == null) return;
        root.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !canPromoteOverlayFocus()) {
                root.post(this::releaseOverlayInputFocus);
            }
        });
    }

    private void bindOverlayViews() {
        card = root.findViewById(R.id.cardMemoryOverlay);
        ddTargetPkg = root.findViewById(R.id.edtOverlayMemoryTargetPkg);
        ddProcess = root.findViewById(R.id.ddOverlayMemoryProcess);
        ddDataType = root.findViewById(R.id.ddOverlayMemoryDataType);
        ddSearchMode = root.findViewById(R.id.ddOverlayMemorySearchMode);
        tilSearchValue = root.findViewById(R.id.tilOverlayMemorySearchValue);
        edtSearchValue = root.findViewById(R.id.edtOverlayMemorySearchValue);
        edtPatchName = root.findViewById(R.id.edtOverlayMemoryPatchName);
        edtPatchAddress = root.findViewById(R.id.edtOverlayMemoryPatchAddress);
        edtPatchValue = root.findViewById(R.id.edtOverlayMemoryPatchValue);
        chkPatchHex = root.findViewById(R.id.chkOverlayMemoryPatchHex);
        chkStringPatchTruncate = root.findViewById(R.id.chkOverlayMemoryStringPatchTruncate);
        chkFreezePatch = root.findViewById(R.id.chkOverlayMemoryFreezePatch);
        chkDumpToDisk = root.findViewById(R.id.chkOverlayMemoryDumpToDisk);
        edtResultLimit = root.findViewById(R.id.edtOverlayMemoryResultLimit);
        edtMaxResults = root.findViewById(R.id.edtOverlayMemoryMaxResults);
        edtDumpBegin = root.findViewById(R.id.edtOverlayMemoryDumpBegin);
        edtDumpEnd = root.findViewById(R.id.edtOverlayMemoryDumpEnd);
        btnPatchMultiple = root.findViewById(R.id.btnOverlayMemoryPatchMultiple);
        btnFreezeChecked = root.findViewById(R.id.btnOverlayMemoryFreezeChecked);
        txtSummary = root.findViewById(R.id.txtOverlayMemorySummary);
        txtOutput = root.findViewById(R.id.txtOverlayMemoryOutput);
        txtSelectedResult = root.findViewById(R.id.txtOverlayMemorySelectedResult);
        lstResults = root.findViewById(R.id.lstOverlayMemoryResults);
        rowSelectVisibleResults = root.findViewById(R.id.rowOverlayMemorySelectResults);
        btnSelectAllResults = root.findViewById(R.id.btnOverlayMemorySelectAllResults);
        btnSelectNoneResults = root.findViewById(R.id.btnOverlayMemorySelectNoneResults);
        btnPreviousResultPage = root.findViewById(R.id.btnOverlayMemoryPreviousResultPage);
        btnNextResultPage = root.findViewById(R.id.btnOverlayMemoryNextResultPage);
        try { if (txtSummary != null) txtSummary.setTextIsSelectable(false); } catch (Throwable ignored) {}
        try { if (txtOutput != null) txtOutput.setTextIsSelectable(false); } catch (Throwable ignored) {}
        if (txtSummary != null) txtSummary.setVisibility(View.GONE);
        configureScrollableText(txtOutput);
        installDetailAddressActions(txtOutput);

        if (card != null) {
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            int minCardWidth = PermsTestVrOverlayCompat.isEnabled(this) ? dp(500) : dp(500);
            if (lp != null && lp.width > 0 && lp.width < minCardWidth) {
                lp.width = minCardWidth;
                card.setLayoutParams(lp);
            }
        }

        packageAdapter = new MemoryPackageAdapter(overlayThemeContext, packageItems);
        ddTargetPkg.setAdapter(packageAdapter);

        processAdapter = new ArrayAdapter<>(overlayThemeContext, R.layout.dropdown_memory_overlay_item, processItems);
        ddProcess.setAdapter(processAdapter);

        resultAdapter = new MemoryResultAdapter(overlayThemeContext, resultItems);
        if (lstResults != null) {
            lstResults.setAdapter(resultAdapter);
            applyResultListScrollbarTweaks(lstResults);
            installNestedScrollGuard(lstResults);
            lstResults.setOnItemClickListener((parent, view, position, id) -> {
                Object item = parent.getItemAtPosition(position);
                if (item instanceof MemoryResultRow) {
                    selectMemoryResult((MemoryResultRow) item);
                }
            });
            lstResults.setOnItemLongClickListener((parent, view, position, id) -> {
                Object item = parent.getItemAtPosition(position);
                if (item instanceof MemoryResultRow) {
                    showMemoryResultActionMenu((MemoryResultRow) item, view);
                    return true;
                }
                return false;
            });
            lstResults.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                    if (scrollState == android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                        scheduleResultValueSync(250L);
                    }
                }

                @Override
                public void onScroll(android.widget.AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                }
            });
        }

        ArrayAdapter<String> dataTypeAdapter = new ArrayAdapter<>(
                overlayThemeContext,
                R.layout.dropdown_memory_overlay_item,
                MemoryToolHelper.DATA_TYPES
        );
        ddDataType.setAdapter(dataTypeAdapter);
        ddDataType.setText(MemoryToolHelper.DEFAULT_DATA_TYPE, false);

        ArrayAdapter<String> searchModeAdapter = new ArrayAdapter<>(
                overlayThemeContext,
                R.layout.dropdown_memory_overlay_item,
                MemoryToolHelper.SEARCH_MODES
        );
        ddSearchMode.setAdapter(searchModeAdapter);
        ddSearchMode.setText(MemoryToolHelper.DEFAULT_SEARCH_MODE, false);

        rowSessionButtons = root.findViewById(R.id.rowOverlayMemorySessionButtons);
        rowTargetPackageLoading = root.findViewById(R.id.rowOverlayMemoryTargetPackageLoading);
        rowProcessLoading = root.findViewById(R.id.rowOverlayMemoryProcessLoading);
        View btnClose = root.findViewById(R.id.btnOverlayMemoryClose);
        View btnMinimize = root.findViewById(R.id.btnOverlayMemoryMinimize);
        View btnResetWindows = root.findViewById(R.id.btnOverlayMemoryResetWindows);
        try { if (btnClose != null) btnClose.setFocusable(false); } catch (Throwable ignored) {}
        try { if (btnMinimize != null) btnMinimize.setFocusable(false); } catch (Throwable ignored) {}
        try { if (btnResetWindows != null) btnResetWindows.setFocusable(false); } catch (Throwable ignored) {}
        View btnUsePackageTarget = root.findViewById(R.id.btnOverlayMemoryUsePackageTarget);
        View btnStageTool = root.findViewById(R.id.btnOverlayMemoryStageTool);
        View btnClearState = root.findViewById(R.id.btnOverlayMemoryClearState);
        View btnRefresh = root.findViewById(R.id.btnOverlayMemoryRefresh);
        View btnStartApp = root.findViewById(R.id.btnOverlayMemoryStartApp);
        View btnStopApp = root.findViewById(R.id.btnOverlayMemoryStopApp);
        View btnAttach = root.findViewById(R.id.btnOverlayMemoryAttach);
        View btnDetach = root.findViewById(R.id.btnOverlayMemoryDetach);
        View btnNewScan = root.findViewById(R.id.btnOverlayMemoryNewScan);
        View btnFind = root.findViewById(R.id.btnOverlayMemoryFind);
        View btnPrevRange = root.findViewById(R.id.btnOverlayMemoryPreviousRange);
        View btnNextRange = root.findViewById(R.id.btnOverlayMemoryNextRange);
        View btnResetRange = root.findViewById(R.id.btnOverlayMemoryResetRange);
        txtScanRangeStatus = root.findViewById(R.id.txtOverlayMemoryRangeStatus);
        chkAutoRange = root.findViewById(R.id.chkOverlayMemoryAutoRange);
        chkSyncResultValue = root.findViewById(R.id.chkOverlayMemorySyncResultValue);
        edtAutoRangeLimit = root.findViewById(R.id.edtOverlayMemoryAutoRangeLimit);
        View btnFilter = root.findViewById(R.id.btnOverlayMemoryFilter);
        View btnPatch = root.findViewById(R.id.btnOverlayMemoryPatch);
        btnPatchMultiple = root.findViewById(R.id.btnOverlayMemoryPatchMultiple);
        btnFreezeChecked = root.findViewById(R.id.btnOverlayMemoryFreezeChecked);
        View btnDump = root.findViewById(R.id.btnOverlayMemoryDump);
        View btnSavePatches = root.findViewById(R.id.btnOverlayMemorySavePatches);
        View btnLoadPatches = root.findViewById(R.id.btnOverlayMemoryLoadPatches);
        View btnHex = root.findViewById(R.id.btnOverlayMemoryHex);
        View btnDisasm = root.findViewById(R.id.btnOverlayMemoryDisasm);
        View btnSpecialTools = root.findViewById(R.id.btnOverlayMemorySpecialTools);
        try { if (btnNewScan instanceof TextView) ((TextView) btnNewScan).setText("New Scan"); } catch (Throwable ignored) {}
        try { if (btnFind instanceof TextView) ((TextView) btnFind).setText("Next Scan"); } catch (Throwable ignored) {}
        configureAutoRangeLimitField();
        updateScanRangeStatus();
        try {
            if (chkAutoRange != null) {
                chkAutoRange.setChecked(shouldAutoRangeMemorySearch());
                chkAutoRange.setOnCheckedChangeListener((buttonView, isChecked) ->
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(MemoryToolHelper.KEY_AUTO_RANGE, isChecked).apply());
            }
        } catch (Throwable ignored) {
        }
        try {
            if (chkSyncResultValue != null) {
                chkSyncResultValue.setChecked(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_SYNC_RESULT_VALUE, true));
                chkSyncResultValue.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_SYNC_RESULT_VALUE, isChecked).apply();
                    if (isChecked) {
                        scheduleResultValueSync(250L);
                    } else {
                        stopResultValueSyncLoop();
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        if (btnFilter != null) btnFilter.setVisibility(View.GONE);
        if (btnClearState != null) {
            btnClearState.setContentDescription("Clear the current memory scanner session, visible results, search value, patch value, and selected address for the target package.");
        }
        if (btnStageTool != null) btnStageTool.setVisibility(shouldAutoStage() ? View.GONE : View.VISIBLE);
        View chkScanAtStartup = root.findViewById(R.id.chkOverlayMemoryScanAtStartup);
        View chkExcludeSelf = root.findViewById(R.id.chkOverlayMemoryExcludeSelfPackage);
        View chkAutoStage = root.findViewById(R.id.chkOverlayMemoryAutoStage);
        View chkStringCaseSensitive = root.findViewById(R.id.chkOverlayMemoryStringCaseSensitive);

        try {
            android.widget.CompoundButton cb = (android.widget.CompoundButton) chkScanAtStartup;
            cb.setChecked(shouldScanAtStartup());
            cb.setOnCheckedChangeListener((buttonView, isChecked) ->
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(MemoryToolHelper.KEY_SCAN_AT_STARTUP, isChecked).apply());
        } catch (Throwable ignored) {
        }
        if (btnResetWindows != null) {
            btnResetWindows.setOnClickListener(v -> resetMemoryOverlayWindows());
        }
        try {
            android.widget.CompoundButton cb = (android.widget.CompoundButton) chkExcludeSelf;
            cb.setChecked(shouldExcludeSelfPackage());
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(MemoryToolHelper.KEY_EXCLUDE_SELF_PACKAGE, isChecked).apply();
                refreshTargetPackages();
            });
        } catch (Throwable ignored) {
        }
        try {
            android.widget.CompoundButton cb = (android.widget.CompoundButton) chkAutoStage;
            cb.setChecked(shouldAutoStage());
            cb.setOnCheckedChangeListener((buttonView, isChecked) ->
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(MemoryToolHelper.KEY_AUTO_STAGE, isChecked).apply());
        } catch (Throwable ignored) {
        }
        try {
            android.widget.CompoundButton cb = (android.widget.CompoundButton) chkStringCaseSensitive;
            cb.setChecked(shouldStringCaseSensitive());
            cb.setOnCheckedChangeListener((buttonView, isChecked) ->
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(MemoryToolHelper.KEY_STRING_CASE_SENSITIVE, isChecked).apply());
        } catch (Throwable ignored) {
        }
        try {
            if (chkPatchHex != null) {
                chkPatchHex.setOnCheckedChangeListener((buttonView, isChecked) -> refreshPatchValueDisplayForMode());
            }
        } catch (Throwable ignored) {
        }
        try {
            if (chkStringPatchTruncate != null) {
                chkStringPatchTruncate.setChecked(shouldStringPatchTruncate());
                chkStringPatchTruncate.setOnCheckedChangeListener((buttonView, isChecked) ->
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(MemoryToolHelper.KEY_STRING_PATCH_TRUNCATE, isChecked).apply());
            }
        } catch (Throwable ignored) {
        }
        try {
            if (chkFreezePatch != null) {
                chkFreezePatch.setChecked(false);
                chkFreezePatch.setVisibility(View.GONE);
                chkFreezePatch.setOnCheckedChangeListener(null);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (chkDumpToDisk != null) {
                chkDumpToDisk.setChecked(false);
            }
        } catch (Throwable ignored) {
        }
        if (chkExcludeSelf != null) chkExcludeSelf.setVisibility(View.GONE);
        if (chkAutoStage != null) chkAutoStage.setVisibility(View.GONE);

        final MaterialAutoCompleteTextView targetPkgDropdown = ddTargetPkg;
        final MaterialAutoCompleteTextView processDropdown = ddProcess;
        final MaterialAutoCompleteTextView dataTypeDropdown = ddDataType;
        final MaterialAutoCompleteTextView searchModeDropdown = ddSearchMode;

        targetPkgDropdown.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (item instanceof MemoryPackageEntry) {
                MemoryPackageEntry entry = (MemoryPackageEntry) item;
                pendingSelectPkg = entry.pkg;
                targetPkgDropdown.setText(entry.pkg, false);
                lastResolvedAutoPid = null;
                clearOverlayScanState();
                try { targetPkgDropdown.dismissDropDown(); } catch (Throwable ignored) {}
                dropdownOpen = false;
                clearProcessSelectionForTargetChange();
                appendStatus("Target package selected. Press Attach after the app is running, or use Refresh to scan processes.");
            }
        });

        processDropdown.setOnItemClickListener((parent, view, position, id) -> {
            clearOverlayScanState();
            try { processDropdown.dismissDropDown(); } catch (Throwable ignored) {}
            dropdownOpen = false;
        });

        dataTypeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedType = dataTypeDropdown.getText() == null
                    ? MemoryToolHelper.DEFAULT_DATA_TYPE
                    : dataTypeDropdown.getText().toString();
            if (TextUtils.equals(MemoryToolHelper.normalizeDataType(selectedType), "string")) {
                if (searchModeDropdown != null) searchModeDropdown.setText(MemoryToolHelper.SEARCH_MODE_EXACT, false);
                updateSearchValueHint("String value");
            } else {
                updateSearchValueHintForSearchMode(getCurrentSearchMode());
            }
            clearOverlayScanState();
        });

        searchModeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            updateSearchValueHintForSearchMode(getCurrentSearchMode());
            clearOverlayScanState();
        });

        configureOverlayDropdown(ddTargetPkg, root.findViewById(R.id.tilOverlayMemoryTargetPkg), () -> {
            if (targetPackageRefreshInFlight) {
                setTargetPackageLoading(true);
                return;
            }
            if (shouldRefreshTargetPackagesBeforeDropdown()) {
                refreshTargetPackages(true);
            }
        });
        configureOverlayDropdown(ddProcess, root.findViewById(R.id.tilOverlayMemoryProcess), () -> {
            if (processRefreshInFlight || processAdapter == null || processAdapter.getCount() <= 1) {
                String selectedPid = getSelectedManualProcessPid();
                if (TextUtils.isEmpty(selectedPid)) {
                    reopenProcessDropdownAfterRefresh = true;
                    refreshProcesses();
                } else {
                    selectPid(selectedPid, true);
                    showOverlayDropdownNow(ddProcess);
                }
            }
        });
        configureOverlayDropdown(ddDataType, root.findViewById(R.id.tilOverlayMemoryDataType), null);
        configureOverlayDropdown(ddSearchMode, root.findViewById(R.id.tilOverlayMemorySearchMode), null);

        installInputModeHooks();
        installPatchNameWatcher();

        btnClose.setOnClickListener(v -> closeOverlayFromUser());
        btnMinimize.setOnClickListener(v -> hideOverlayIntoNotification());
        btnUsePackageTarget.setOnClickListener(v -> {
            syncTargetFromPackageTools();
            releaseOverlayInputFocus();
        });
        btnStageTool.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            stageToolInOverlay();
        });
        btnClearState.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            clearStateInOverlay();
        });
        btnRefresh.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            refreshTargetPackages(false);
            refreshProcesses();
        });
        btnStartApp.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            launchTargetPackage();
        });
        btnStopApp.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            stopTargetPackage();
        });
        btnAttach.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            runMemoryCommand("attach", null, null, null, null);
        });
        btnDetach.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            stopFreezePatch(true);
            runMemoryCommand("detach", null, null, null, null);
        });
        btnNewScan.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            stopFreezePatch(true);
            resetScanRangeSession();
            clearOverlayScanState(false);
            runMemoryScanCommand(true);
        });
        btnFind.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            runMemoryScanCommand(!hasActiveResultSet);
        });
        if (btnPrevRange != null) {
            btnPrevRange.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                stopFreezePatch(true);
                moveScanRangeWindow(-1);
            });
        }
        if (btnNextRange != null) {
            btnNextRange.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                stopFreezePatch(true);
                moveScanRangeWindow(1);
            });
        }
        if (btnResetRange != null) {
            btnResetRange.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                stopFreezePatch(true);
                resetScanRangeToFirst();
            });
        }
        btnFilter.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            if (!hasActiveResultSet) {
                appendStatus("No active result set. Use Find or New Scan first.");
                return;
            }
            runMemoryScanCommand(false);
        });
        btnPatch.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            MemoryResultRow patchTarget = resolvePatchTargetRow();
            String patchValue = edtPatchValue.getText() == null ? null : edtPatchValue.getText().toString();
            if (isPatchHexEnabled() && patchTarget != null && isNumericPatchDataType(patchTarget.dataType)) {
                patchValue = normalizeHexPatchValueForTarget(patchValue, patchTarget);
                if (patchValue == null) return;
            } else if (patchTarget != null && isStringPatchDataType(patchTarget.dataType)) {
                patchValue = normalizeStringPatchValueForTarget(patchValue, patchTarget);
                if (patchValue == null) return;
            }
            String stateOverride = patchTarget == null ? null : buildSelectedResultStateJson(patchTarget);
            if (shouldRefreshFreezePatchForTarget(patchTarget)) {
                prepareFreezePatch(patchTarget == null ? null : singleRowList(patchTarget), patchValue, stateOverride);
                scheduleFreezePatch(freezePatchToken, 1000L);
            }
            setPendingPatchUpdate(patchTarget == null ? null : singleRowList(patchTarget), patchValue);
            runMemoryCommand(
                "patch",
                null,
                patchValue,
                null,
                null,
                stateOverride,
                false,
                null);
        });
        if (btnPatchMultiple != null) {
            btnPatchMultiple.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                patchCheckedResults();
            });
        }
        if (btnFreezeChecked != null) {
            btnFreezeChecked.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                freezeCheckedResults();
            });
        }
        if (btnSelectAllResults != null) {
            btnSelectAllResults.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                selectAllVisibleResults();
            });
        }
        if (btnSelectNoneResults != null) {
            btnSelectNoneResults.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                selectNoVisibleResults();
            });
        }
        if (btnPreviousResultPage != null) {
            btnPreviousResultPage.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                loadVisibleResultPage(resultPageIndex - 1, true);
            });
        }
        if (btnNextResultPage != null) {
            btnNextResultPage.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                loadVisibleResultPage(resultPageIndex + 1, true);
            });
        }
        if (btnSavePatches != null) {
            btnSavePatches.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                saveMemoryPatchSet();
            });
        }
        if (btnLoadPatches != null) {
            btnLoadPatches.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                loadMemoryPatchSet();
            });
        }
        btnDump.setOnClickListener(v -> {
            releaseOverlayInputFocus();
            applyDefaultDumpRangeIfNeeded();
            String begin = edtDumpBegin.getText() == null ? null : edtDumpBegin.getText().toString();
            String end = edtDumpEnd.getText() == null ? null : edtDumpEnd.getText().toString();
            if (TextUtils.isEmpty(begin) || TextUtils.isEmpty(end)) {
                appendStatus("Select a listed result or enter a dump begin/end range first.");
                return;
            }
            runMemoryCommand("dump", null, null, begin, end);
        });
        if (btnHex != null) {
            btnHex.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                showHexOverlayWindow();
            });
        }
        if (btnDisasm != null) {
            btnDisasm.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                showDisassemblyOverlayWindow();
            });
        }
        if (btnSpecialTools != null) {
            btnSpecialTools.setOnClickListener(v -> {
                releaseOverlayInputFocus();
                showSpecialToolsOverlayWindow();
            });
        }
    }


    private void showHexOverlayWindow() {
        if (hasAnyMemoryPanelContainer()) {
            openMemoryToolPanelActivity(ACTION_SHOW_HEX_OVERLAY, MemoryHexPanelActivity.class);
            return;
        }
        try {
            boolean keepMainOverlayVisible = isMainOverlayVisibleForToolLaunch();
            debugLog("tool show requested tool=hex vr=" + PermsTestVrOverlayCompat.isEnabled(this)
                    + " mainVisible=" + keepMainOverlayVisible
                    + " existing=" + (hexOverlayController != null));
            prepareForVrToolOverlay("hex");
            if (hexOverlayController == null) {
                hexOverlayController = new MemoryHexOverlayController(
                        this, overlayThemeContext, wm, this::defaultToolAddress, this::appendStatus,
                        this::requestToolDump, this::requestToolWriteBytes, this::requestToolSearchHexPayload,
                        this::requestToolApplyHexPayloads, this::getTargetPackage,
                        this::onHexAddressChanged, this::onHexByteSelected, this::onHexPayloadLoaded,
                        this::onVrHexOverlayClosed);
                debugLog("tool controller created tool=hex");
            }
            hexOverlayController.show();
            ensureMainOverlayVisibleAlongsideVrTool(keepMainOverlayVisible);
            String address = defaultToolAddress();
            if (!TextUtils.isEmpty(address)) {
                hexOverlayController.setAddress(address, selectedResult != null);
                syncToolAddressToDisassembly(address, false);
            }
            debugLog("tool show finished tool=hex address=" + cleanLogValue(address)
                    + " autoRead=" + (selectedResult != null)
                    + " mainVisible=" + (root != null && root.getVisibility() == View.VISIBLE));
        } catch (Throwable t) {
            appendStatus("Hex overlay failed: " + t);
            debugLog("tool show failed tool=hex", t);
        }
    }

    private void openHexFromDisassemblyAddress(long address) {
        try {
            String formatted = formatHex(address);
            manualToolAddressOverride = formatted;
            try { if (edtPatchAddress != null) edtPatchAddress.setText(formatted); } catch (Throwable ignored) {}
            try { if (edtDumpBegin != null) edtDumpBegin.setText(formatted); } catch (Throwable ignored) {}
            showHexOverlayWindow();
            if (hexOverlayController != null) {
                hexOverlayController.setAddress(formatted, true);
                mainHandler.postDelayed(() -> {
                    try {
                        if (hexOverlayController != null) hexOverlayController.setAddress(formatted, true);
                    } catch (Throwable ignored) {
                    }
                }, 120L);
            }
        } catch (Throwable t) {
            appendStatus("Open Hex from disassembly failed: " + t.getMessage());
        }
    }

    private void onDisassemblyAddressSelected(long address) {
        String formatted = formatHex(address);
        manualToolAddressOverride = formatted;
        updateMainMemoryToolAddressFields(formatted);
        if (shouldSyncDisassemblyWithHexEditor()) {
            syncToolAddressToHex(formatted, false);
        }
        syncToolAddressToDisassembly(formatted, false);
        if (txtSelectedResult != null) {
            txtSelectedResult.setText("Selected " + formatted + " from disassembly. Patch will modify only this address.");
        }
    }

    private void showDisassemblyOverlayWindow() {
        if (hasAnyMemoryPanelContainer()) {
            openMemoryToolPanelActivity(ACTION_SHOW_DISASSEMBLY_OVERLAY, MemoryDisassemblyPanelActivity.class);
            return;
        }
        try {
            boolean keepMainOverlayVisible = isMainOverlayVisibleForToolLaunch();
            debugLog("tool show requested tool=disassembly vr=" + PermsTestVrOverlayCompat.isEnabled(this)
                    + " mainVisible=" + keepMainOverlayVisible
                    + " existing=" + (disassemblyOverlayController != null));
            prepareForVrToolOverlay("disassembly");
            if (disassemblyOverlayController == null) {
                disassemblyOverlayController = new MemoryDisassemblyOverlayController(
                        this, overlayThemeContext, wm, this::defaultToolAddress, this::appendStatus,
                        this::requestToolDump, new MemoryDisassemblyOverlayController.ActionListener() {
                            @Override public void onOpenHex(long address) { openHexFromDisassemblyAddress(address); }
                            @Override public void onAddressSelected(long address) { onDisassemblyAddressSelected(address); }
                        }, this::onVrDisassemblyOverlayClosed);
                debugLog("tool controller created tool=disassembly");
            }
            disassemblyOverlayController.show();
            ensureMainOverlayVisibleAlongsideVrTool(keepMainOverlayVisible);
            String address = defaultToolAddress();
            if (!TextUtils.isEmpty(address)) {
                disassemblyOverlayController.setAddress(address, selectedResult != null);
            }
            debugLog("tool show finished tool=disassembly address=" + cleanLogValue(address)
                    + " autoRead=" + (selectedResult != null)
                    + " mainVisible=" + (root != null && root.getVisibility() == View.VISIBLE));
        } catch (Throwable t) {
            appendStatus("Disassembly overlay failed: " + t);
            debugLog("tool show failed tool=disassembly", t);
        }
    }

    private void showSpecialToolsOverlayWindow() {
        if (hasAnyMemoryPanelContainer()) {
            openMemoryToolPanelActivity(ACTION_SHOW_SPECIAL_TOOLS_OVERLAY, MemorySpecialToolsPanelActivity.class);
            return;
        }
        try {
            boolean keepMainOverlayVisible = isMainOverlayVisibleForToolLaunch();
            debugLog("tool show requested tool=special vr=" + PermsTestVrOverlayCompat.isEnabled(this)
                    + " mainVisible=" + keepMainOverlayVisible
                    + " existing=" + (specialToolsOverlayController != null));
            prepareForVrToolOverlay("special");
            if (specialToolsOverlayController == null) {
                specialToolsOverlayController = new MemorySpecialToolsOverlayController(
                        this, overlayThemeContext, wm, this::defaultToolAddress, this::appendStatus,
                        new MemorySpecialToolsOverlayController.ActionListener() {
                            @Override public void onSearchMemoryFileType(String extension) { searchMemoryByFileType(extension); }
                            @Override public void onExportMemoryByType(String extension, String begin, String end) { exportMemoryByFileType(extension, begin, end); }
                            @Override public void onImportMemoryByType(String extension, String begin, String sourcePath) { importMemoryByFileType(extension, begin, sourcePath); }
                            @Override public void onStartTimerBaseline() { startTimerFinderBaseline(); }
                            @Override public void onFindTimerChanges() { findTimerCandidates(); }
                        },
                        this::onVrSpecialToolsOverlayClosed);
                debugLog("tool controller created tool=special");
            }
            specialToolsOverlayController.show();
            ensureMainOverlayVisibleAlongsideVrTool(keepMainOverlayVisible);
            debugLog("tool show finished tool=special mainVisible=" + (root != null && root.getVisibility() == View.VISIBLE));
        } catch (Throwable t) {
            appendStatus("Special Tools overlay failed: " + t);
            debugLog("tool show failed tool=special", t);
        }
    }

    private boolean isMainOverlayVisibleForToolLaunch() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }

    private void ensureMainOverlayVisibleAlongsideVrTool(boolean keepMainOverlayVisible) {
        if (!keepMainOverlayVisible || !PermsTestVrOverlayCompat.isEnabled(this)) return;
        try {
            if (root != null) {
                root.setVisibility(View.VISIBLE);
                setOverlayInteractive(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void prepareForVrToolOverlay(String activeTool) {
        if (!PermsTestVrOverlayCompat.shouldSwitchMainOverlayOutForTool(this)) return;
        if (root != null) {
            rememberOverlayInputState();
            removeMainOverlayWindowForVrMinimize();
        }
        if (!TextUtils.equals(activeTool, "hex") && hexOverlayController != null) {
            hexOverlayController.destroy();
            hexOverlayController = null;
        }
        if (!TextUtils.equals(activeTool, "disassembly") && disassemblyOverlayController != null) {
            disassemblyOverlayController.destroy();
            disassemblyOverlayController = null;
        }
        if (!TextUtils.equals(activeTool, "special") && specialToolsOverlayController != null) {
            specialToolsOverlayController.destroy();
            specialToolsOverlayController = null;
        }
    }

    private void onVrHexOverlayClosed() {
        debugLog("tool closed tool=hex vr=" + PermsTestVrOverlayCompat.isEnabled(this));
        hexOverlayController = null;
        restoreMainOverlayAfterVrToolClose();
    }

    private void onVrDisassemblyOverlayClosed() {
        debugLog("tool closed tool=disassembly vr=" + PermsTestVrOverlayCompat.isEnabled(this));
        disassemblyOverlayController = null;
        restoreMainOverlayAfterVrToolClose();
    }

    private void onVrSpecialToolsOverlayClosed() {
        debugLog("tool closed tool=special vr=" + PermsTestVrOverlayCompat.isEnabled(this));
        specialToolsOverlayController = null;
        restoreMainOverlayAfterVrToolClose();
    }

    private void restoreMainOverlayAfterVrToolClose() {
        if (!PermsTestVrOverlayCompat.shouldRestoreMainOverlayAfterToolClose(this)) return;
        mainHandler.postDelayed(() -> {
            try {
                showOrUpdateOverlay(null);
                showOverlayFromNotification();
                appendStatus("Returned from VR tool panel.");
            } catch (Throwable ignored) {
            }
        }, 80L);
    }

    private String defaultToolAddress() {
        if (!TextUtils.isEmpty(manualToolAddressOverride)) return manualToolAddressOverride;
        if (selectedResult != null) return formatHex(selectedResult.address);
        if (edtPatchAddress != null && edtPatchAddress.getText() != null) {
            String manual = edtPatchAddress.getText().toString().trim();
            if (!TextUtils.isEmpty(manual)) return manual;
        }
        if (!resultItems.isEmpty()) return formatHex(resultItems.get(0).address);
        try {
            if (edtDumpBegin != null && !TextUtils.isEmpty(edtDumpBegin.getText())) {
                return edtDumpBegin.getText().toString().trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private void requestToolDump(String begin, String end, MemoryOverlayWindowSupport.DumpCallback callback) {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            deliverToolDump(callback, false, "Enter a target package first.");
            return;
        }
        final String targetPid = getSelectedPid();
        if (TextUtils.isEmpty(targetPid)) {
            refreshProcesses();
            deliverToolDump(callback, false, "No running PID for Auto-select yet. Refresh processes or choose the running process from the dropdown.");
            return;
        }
        new Thread(() -> {
            // Stage apk-medit once before the batch so every search/write uses
            // the same backend and target app run-as workspace.
            if (shouldAutoStage()) {
                MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                if (install == null || install.exitCode != 0) {
                    deliverToolDump(callback, false, summarizeResult("apk-medit could not be staged for the current backend.", install));
                    return;
                }
            }

            String effectivePid = targetPid;
            String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
            if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
            debugLog("tool dump start pkg=" + cleanLogValue(pkg)
                    + " pid=" + cleanLogValue(targetPid)
                    + " effectivePid=" + cleanLogValue(effectivePid)
                    + " begin=" + cleanLogValue(begin)
                    + " end=" + cleanLogValue(end));
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
            String shellCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                    pkg,
                    MemoryToolRuntime.PUBLIC_BIN_DIR,
                    withoutPtrace,
                    "dump",
                    effectivePid,
                    null,
                    null,
                    begin,
                    end,
                    null,
                    getMaxScanResults());
            MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(shellCmd);
            boolean failed = isMemoryCommandFailure(r);
            debugLog("tool dump finish success=" + (!failed)
                    + " exit=" + (r == null ? "null" : r.exitCode)
                    + " begin=" + cleanLogValue(begin)
                    + " end=" + cleanLogValue(end));
            String text = failed ? summarizeResult("dump failed.", r) : collectCommandOutput(r);
            deliverToolDump(callback, !failed, text);
        }, "MemoryOverlayToolDump").start();
    }

    /**
     * Backend entry point for Hex overlay payload Find.  The UI supplies raw hex;
     * the service owns target package/PID validation and apk-medit execution.
     */
    private void requestToolSearchHexPayload(String hexBytes, String maskHex, MemoryHexOverlayController.PayloadSearchCallback callback) {
        String normalized;
        try {
            normalized = normalizePayloadHexForApply(hexBytes);
        } catch (Throwable t) {
            deliverPayloadSearch(callback, false, "Select or load an even-length hex payload before searching.");
            return;
        }
        String normalizedMask;
        try {
            normalizedMask = normalizePayloadMaskForApply(maskHex, normalized.length() / 2);
        } catch (Throwable t) {
            deliverPayloadSearch(callback, false, "Payload mask is invalid: " + t.getMessage());
            return;
        }
        if (TextUtils.isEmpty(getTargetPackage())) {
            deliverPayloadSearch(callback, false, "Enter a target package before searching a hex payload.");
            return;
        }
        if (TextUtils.isEmpty(getSelectedPid())) {
            refreshProcesses();
            deliverPayloadSearch(callback, false, "No running PID for Auto-select yet. Refresh processes or choose the running process from the dropdown.");
            return;
        }
        if (getMaxScanResults() == 0) {
            deliverPayloadSearch(callback, false, "Cap 0 / unlimited is disabled for payload searching until the scanner is fully streaming.");
            return;
        }
        boolean masked = hasPayloadMaskWildcards(normalizedMask);
        String command = masked ? "search-bytes-mask" : "search-bytes";
        String commandValue = masked ? (normalized + " " + normalizedMask) : normalized;
        // Reuse normal scanner state/results so payload hits appear in the main
        // memory result list and can be tapped like normal search results.
        configureScanRangeBase(command, masked ? "hex-bytes-mask" : "hex-bytes", commandValue, normalized);
        runMemoryCommand(
                command,
                masked ? "hex-bytes-mask" : "hex-bytes",
                commandValue,
                null,
                null,
                null,
                true,
                normalized);
        int maskedBytes = countPayloadMaskWildcards(normalizedMask);
        deliverPayloadSearch(callback, true, "Hex payload search started (" + (normalized.length() / 2) + " bytes"
                + (maskedBytes > 0 ? ", mask " + maskedBytes : "")
                + "). Results will update in the memory scanner list.");
    }

    private void deliverPayloadSearch(MemoryHexOverlayController.PayloadSearchCallback callback, boolean success, String text) {
        runOnUiThread(() -> {
            try { if (callback != null) callback.onPayloadSearchResult(success, text); } catch (Throwable ignored) {}
        });
    }

    /** Applies payload specs asynchronously so the overlay UI stays responsive. */
    private void requestToolApplyHexPayloads(ArrayList<MemoryHexOverlayController.PayloadApplySpec> payloads,
                                             MemoryHexOverlayController.PayloadSearchCallback callback) {
        if (payloads == null || payloads.isEmpty()) {
            deliverPayloadSearch(callback, false, "No payloads selected to apply.");
            return;
        }
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            deliverPayloadSearch(callback, false, "Enter a target package before applying payloads.");
            return;
        }
        final String targetPid = getSelectedPid();
        if (TextUtils.isEmpty(targetPid)) {
            refreshProcesses();
            deliverPayloadSearch(callback, false, "No running PID for Auto-select yet. Refresh processes or choose the running process from the dropdown.");
            return;
        }
        final ArrayList<MemoryHexOverlayController.PayloadApplySpec> copy = new ArrayList<>(payloads);
        new Thread(() -> {
            String result = applyHexPayloadListSync(pkg, targetPid, copy);
            String display = result == null ? "Apply Payloads failed." : result;
            boolean success = display.startsWith("Applied ") && !display.startsWith("Applied 0 payload");
            deliverPayloadSearch(callback, success, display);
            runOnUiThread(() -> showPayloadApplyToast(display));
        }, "MemoryOverlayApplyHexPayloads").start();
    }

    /**
     * Applies payloads synchronously for both manual Apply/Apply All and Attach
     * automation.  Each payload performs: search original_hex -> runtime address
     * match(es) -> write patched_hex.  The saved JSON address is intentionally
     * ignored because ASLR can move the same structure between launches.
     */
    private String applyHexPayloadListSync(String pkg,
                                           String targetPid,
                                           ArrayList<MemoryHexOverlayController.PayloadApplySpec> payloads) {
        StringBuilder log = new StringBuilder();
        int appliedPayloads = 0;
        int failedPayloads = 0;
        int writtenInstances = 0;
        int failedInstances = 0;
        try {
            if (shouldAutoStage()) {
                MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                if (install == null || install.exitCode != 0) {
                    return summarizeResult("apk-medit could not be staged for the current backend.", install);
                }
            }
            String effectivePid = targetPid;
            String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
            if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
            boolean applyAllMatches = shouldApplyPayloadsToAllMatches();
            boolean showAllPatchedAddresses = shouldShowAllPatchedPayloadAddresses();
            int configuredCap = MemoryToolHelper.normalizeMaxResults(getMaxScanResults());
            if (configuredCap == 0) configuredCap = 1;
            int searchLimit = applyAllMatches ? Math.max(1, configuredCap) : 1;
            boolean payloadDebug = isDebugOutputEnabled();
            StringBuilder payloadDebugLog = payloadDebug ? new StringBuilder() : null;
            if (payloadDebug) {
                appendPayloadDebugLine(payloadDebugLog, "== PermsTest payload apply debug ==");
                appendPayloadDebugLine(payloadDebugLog, "time=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()));
                appendPayloadDebugLine(payloadDebugLog, "package=" + pkg);
                appendPayloadDebugLine(payloadDebugLog, "pid=" + effectivePid);
                appendPayloadDebugLine(payloadDebugLog, "apply_all_matches=" + applyAllMatches + " search_limit=" + searchLimit);
                appendPayloadDebugLine(payloadDebugLog, "payload_count=" + payloads.size());
            }

            for (MemoryHexOverlayController.PayloadApplySpec payload : payloads) {
                String name = payload == null || TextUtils.isEmpty(payload.name) ? "payload" : payload.name.trim();
                if (payload != null && !payload.enabled) {
                    if (log.length() > 0) log.append('\n');
                    log.append(name).append(": disabled; skipped.");
                    appendPayloadDebugLine(payloadDebugLog, "");
                    appendPayloadDebugLine(payloadDebugLog, "payload=" + name + " disabled; skipped");
                    continue;
                }
                try {
                    String originalHex = normalizePayloadHexForApply(payload == null ? null : payload.originalHex);
                    String patchedHex = normalizePayloadHexForApply(payload == null ? null : payload.patchedHex);
                    if (originalHex.length() != patchedHex.length()) {
                        throw new IllegalStateException("original/patched byte counts do not match");
                    }
                    String maskHex = normalizePayloadMaskForApply(payload == null ? null : payload.maskHex, originalHex.length() / 2);
                    boolean maskedPayload = hasPayloadMaskWildcards(maskHex);
                    String sectionStartHex = normalizeOptionalPayloadHexForApply(payload == null ? null : payload.sectionStartHex);
                    String sectionEndHex = normalizeOptionalPayloadHexForApply(payload == null ? null : payload.sectionEndHex);
                    if (TextUtils.isEmpty(sectionStartHex) != TextUtils.isEmpty(sectionEndHex)) {
                        throw new IllegalStateException("section scope needs both start and end markers");
                    }
                    boolean sectionScoped = !TextUtils.isEmpty(sectionStartHex);
                    appendPayloadDebugLine(payloadDebugLog, "");
                    appendPayloadDebugLine(payloadDebugLog, "payload=" + name);
                    appendPayloadDebugLine(payloadDebugLog, "length=" + (originalHex.length() / 2)
                            + " mask_wildcards=" + countPayloadMaskWildcards(maskHex)
                            + " mask_fixed=" + countPayloadMaskFixedBytes(maskHex));
                    appendPayloadDebugLine(payloadDebugLog, "original_hex=" + originalHex);
                    appendPayloadDebugLine(payloadDebugLog, "patched_hex=" + patchedHex);
                    boolean preserveMaskWildcards = payload != null && payload.preserveMaskWildcards && maskedPayload;
                    appendPayloadDebugLine(payloadDebugLog, "mask_hex=" + (maskedPayload ? maskHex : "<none>"));
                    appendPayloadDebugLine(payloadDebugLog, "preserve_mask_wildcards=" + preserveMaskWildcards);
                    appendPayloadDebugLine(payloadDebugLog, "section_scope=" + (sectionScoped ? (sectionStartHex + " .. " + sectionEndHex) : "<none>"));
                    ArrayList<PayloadSectionRange> sectionRanges = sectionScoped
                            ? resolvePayloadSectionRanges(pkg, effectivePid, withoutPtrace, sectionStartHex, sectionEndHex, searchLimit, payloadDebugLog)
                            : null;
                    if (sectionScoped && (sectionRanges == null || sectionRanges.isEmpty())) {
                        appendPayloadDebugLine(payloadDebugLog, "section_ranges=0; no write attempted");
                        failedPayloads++;
                        if (log.length() > 0) log.append('\n');
                        log.append(name).append(": section markers not found; skipped.");
                        continue;
                    }
                    debugLog("payload apply start name=" + cleanLogValue(name)
                            + " length=" + (originalHex.length() / 2)
                            + " maskWildcards=" + countPayloadMaskWildcards(maskHex)
                            + " preserveMaskWildcards=" + preserveMaskWildcards);
                    String searchCommand = maskedPayload ? "search-bytes-mask" : "search-bytes";
                    String searchValue = maskedPayload ? (originalHex + " " + maskHex) : originalHex;
                    // Locate current runtime addresses from original_hex.  This
                    // keeps payloads resilient to address changes and supports
                    // patching repeated instances when the default all-matches
                    // option is enabled.  Masked payloads compare only FF mask
                    // positions, leaving 00 mask positions as wildcards.
                    String searchCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                            pkg,
                            MemoryToolRuntime.PUBLIC_BIN_DIR,
                            withoutPtrace,
                            searchCommand,
                            effectivePid,
                            maskedPayload ? "hex-bytes-mask" : "hex-bytes",
                            searchValue,
                            null,
                            null,
                            null,
                            searchLimit);
                    MemoryToolRuntime.CmdResult search = runBackendShellCommandCaptureSync(searchCmd);
                    if (isMemoryCommandFailure(search)) {
                        throw new IllegalStateException(summarizeResult("payload search failed", search));
                    }
                    String searchOutput = collectCommandOutput(search);
                    Map<String, String> addressValues = payloadSearchValuesByAddress(searchOutput, originalHex.length() / 2);
                    ArrayList<String> rawAddresses = addressesFromPayloadSearchOutput(searchOutput, applyAllMatches);
                    rawAddresses = payloadAddressesWithStateFallback(pkg, searchOutput, rawAddresses, applyAllMatches, payloadDebugLog, "original_search");
                    ArrayList<String> addresses = filterPayloadAddressesBySection(rawAddresses, sectionRanges, originalHex.length() / 2, applyAllMatches);
                    appendPayloadDebugLine(payloadDebugLog, "original_search_exit=" + search.exitCode
                            + " matches=" + addresses.size()
                            + (sectionScoped ? (" raw_matches=" + rawAddresses.size()) : ""));
                    appendPayloadDebugLine(payloadDebugLog, "original_search_output=" + payloadDebugOneLine(searchOutput));
                    boolean matchedCurrentBytes = false;
                    if (addresses.isEmpty() && !TextUtils.equals(originalHex, patchedHex)) {
                        // If the original signature is already patched, search the
                        // replacement block too.  Masked payloads commonly keep only
                        // a fixed prefix and wildcard the changing suffix, so this
                        // turns a confusing not-found message into an idempotent
                        // re-apply/verify pass.
                        String patchedSearchValue = maskedPayload ? (patchedHex + " " + maskHex) : patchedHex;
                        String patchedSearchCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                                pkg,
                                MemoryToolRuntime.PUBLIC_BIN_DIR,
                                withoutPtrace,
                                searchCommand,
                                effectivePid,
                                maskedPayload ? "hex-bytes-mask" : "hex-bytes",
                                patchedSearchValue,
                                null,
                                null,
                                null,
                                searchLimit);
                        MemoryToolRuntime.CmdResult patchedSearch = runBackendShellCommandCaptureSync(patchedSearchCmd);
                        if (!isMemoryCommandFailure(patchedSearch)) {
                            String patchedOutput = collectCommandOutput(patchedSearch);
                            ArrayList<String> rawPatchedAddresses = addressesFromPayloadSearchOutput(patchedOutput, applyAllMatches);
                            rawPatchedAddresses = payloadAddressesWithStateFallback(pkg, patchedOutput, rawPatchedAddresses, applyAllMatches, payloadDebugLog, "patched_search");
                            addresses = filterPayloadAddressesBySection(rawPatchedAddresses, sectionRanges, patchedHex.length() / 2, applyAllMatches);
                            if (!addresses.isEmpty()) {
                                addressValues = payloadSearchValuesByAddress(patchedOutput, patchedHex.length() / 2);
                            }
                            matchedCurrentBytes = !addresses.isEmpty();
                            appendPayloadDebugLine(payloadDebugLog, "patched_search_exit=" + patchedSearch.exitCode
                                    + " matches=" + addresses.size()
                                    + (sectionScoped ? (" raw_matches=" + rawPatchedAddresses.size()) : ""));
                            appendPayloadDebugLine(payloadDebugLog, "patched_search_output=" + payloadDebugOneLine(patchedOutput));
                        } else {
                            appendPayloadDebugLine(payloadDebugLog, "patched_search_failed=" + payloadDebugOneLine(summarizeResult("payload patched search failed", patchedSearch)));
                        }
                    }
                    if (addresses.isEmpty()) {
                        appendPayloadDebugLine(payloadDebugLog, "matches=0; no write attempted");
                        failedPayloads++;
                        if (log.length() > 0) log.append('\n');
                        if (maskedPayload) {
                            int wildcardBytes = countPayloadMaskWildcards(maskHex);
                            int fixedBytes = countPayloadMaskFixedBytes(maskHex);
                            log.append(name).append(": masked bytes not found (fixed ")
                                    .append(fixedBytes).append(", wildcard ").append(wildcardBytes)
                                    .append("). FF mask bytes are compared; 00 mask bytes are ignored.");
                        } else {
                            log.append(name).append(sectionScoped ? ": original bytes not found inside section." : ": original bytes not found.");
                        }
                        continue;
                    }

                    int payloadWritten = 0;
                    int payloadFailed = 0;
                    ArrayList<String> written = new ArrayList<>();
                    ArrayList<String> writeFailures = new ArrayList<>();
                    appendPayloadDebugLine(payloadDebugLog, "matched_addresses=" + TextUtils.join(", ", addresses));
                    int debugReadbacks = 0;
                    for (String address : addresses) {
                        String beforeHex = null;
                        boolean debugReadback = payloadDebug && debugReadbacks < PAYLOAD_DEBUG_MAX_READBACKS;
                        String searchBeforeHex = addressValues.get(payloadAddressKey(address));
                        if (preserveMaskWildcards || debugReadback) {
                            try {
                                beforeHex = readPayloadBytesStrict(pkg, effectivePid, address, originalHex.length() / 2);
                            } catch (Throwable t) {
                                beforeHex = "<read failed: " + t.getMessage() + ">";
                            }
                            String expectedFixedHex = matchedCurrentBytes ? patchedHex : originalHex;
                            if (preserveMaskWildcards
                                    && shouldUsePayloadSearchValueForPreserve(beforeHex, searchBeforeHex, maskHex, expectedFixedHex)) {
                                beforeHex = searchBeforeHex;
                                if (debugReadback) appendPayloadDebugLine(payloadDebugLog, "address=" + address + " before_source=search_output");
                            }
                            if (debugReadback) appendPayloadDebugLine(payloadDebugLog, "address=" + address + " before=" + beforeHex);
                        }
                        String writeHex = patchedHex;
                        if (preserveMaskWildcards) {
                            if (!isNormalizedHexOfLength(beforeHex, patchedHex.length())) {
                                payloadFailed++;
                                failedInstances++;
                                if (writeFailures.size() < 3) writeFailures.add(address + ": preserve read unavailable; skipped");
                                appendPayloadDebugLine(payloadDebugLog, "address=" + address + " preserve_read_unavailable=" + payloadDebugOneLine(beforeHex));
                                continue;
                            }
                            writeHex = mergePayloadWriteHexWithLiveWildcards(patchedHex, maskHex, beforeHex);
                            if (debugReadback) appendPayloadDebugLine(payloadDebugLog, "address=" + address + " effective_write_hex=" + writeHex);
                        }
                        // Write only after a current address was found.  A missing
                        // signature is logged as not found instead of writing blindly.
                        String writeCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                                pkg,
                                MemoryToolRuntime.PUBLIC_BIN_DIR,
                                withoutPtrace,
                                "write-bytes",
                                effectivePid,
                                null,
                                writeHex,
                                address,
                                null,
                                null,
                                getMaxScanResults());
                        MemoryToolRuntime.CmdResult write = runBackendShellCommandCaptureSync(writeCmd);
                        if (debugReadback) {
                            appendPayloadDebugLine(payloadDebugLog, "address=" + address + " write_exit=" + (write == null ? "null" : String.valueOf(write.exitCode))
                                    + " write_output=" + payloadDebugOneLine(collectCommandOutput(write)));
                        }
                        if (isMemoryCommandFailure(write)) {
                            payloadFailed++;
                            failedInstances++;
                            if (writeFailures.size() < 3) {
                                writeFailures.add(address + ": " + summarizeResult("payload write failed", write));
                            }
                            continue;
                        }
                        if (debugReadback) {
                            String afterHex = readPayloadBytesForDebug(pkg, effectivePid, address, patchedHex.length() / 2);
                            appendPayloadDebugLine(payloadDebugLog, "address=" + address + " after=" + afterHex);
                            appendPayloadDebugLine(payloadDebugLog, "address=" + address + " expected_after=" + writeHex);
                            debugReadbacks++;
                        }
                        payloadWritten++;
                        writtenInstances++;
                        if (showAllPatchedAddresses || written.size() < 5) written.add(address);
                    }

                    if (payloadWritten > 0) {
                        appliedPayloads++;
                        if (log.length() > 0) log.append('\n');
                        log.append(name).append(": applied to ").append(payloadWritten)
                                .append(" instance").append(payloadWritten == 1 ? "" : "s")
                                .append(" (").append(patchedHex.length() / 2).append(" bytes each");
                        int maskedBytes = countPayloadMaskWildcards(maskHex);
                        if (maskedBytes > 0) log.append(", mask ").append(maskedBytes);
                        if (matchedCurrentBytes) log.append(", matched patched/current bytes");
                        if (preserveMaskWildcards) log.append(", preserved masked bytes");
                        if (sectionScoped) log.append(", section scoped");
                        log.append(")");
                        if (!written.isEmpty()) log.append(" at ").append(TextUtils.join(", ", written));
                        if (!showAllPatchedAddresses && addresses.size() > written.size()) {
                            log.append(" and ").append(addresses.size() - written.size()).append(" more");
                        }
                        if (payloadFailed > 0) {
                            log.append("; ").append(payloadFailed).append(" write")
                                    .append(payloadFailed == 1 ? "" : "s").append(" failed");
                        }
                    } else {
                        failedPayloads++;
                        if (log.length() > 0) log.append('\n');
                        log.append(name).append(": failed to write any of ").append(addresses.size())
                                .append(" found instance").append(addresses.size() == 1 ? "" : "s").append('.');
                    }
                    if (!writeFailures.isEmpty()) {
                        log.append("\n").append(TextUtils.join("\n", writeFailures));
                    }
                } catch (Throwable t) {
                    failedPayloads++;
                    if (log.length() > 0) log.append('\n');
                    log.append(name).append(": failed: ").append(t.getMessage());
                }
            }
            String prefix = "Applied " + appliedPayloads + " payload" + (appliedPayloads == 1 ? "" : "s")
                    + " to " + writtenInstances + " instance" + (writtenInstances == 1 ? "" : "s")
                    + ", " + failedPayloads + " payload" + (failedPayloads == 1 ? "" : "s") + " failed"
                    + (failedInstances > 0 ? (", " + failedInstances + " instance write" + (failedInstances == 1 ? "" : "s") + " failed") : "")
                    + ".";
            String debugPath = appendPayloadDebugDump(pkg, payloadDebugLog);
            if (!TextUtils.isEmpty(debugPath)) {
                if (log.length() > 0) log.append('\n');
                log.append("Payload debug dump: ").append(debugPath);
            }
            return TextUtils.isEmpty(log) ? prefix : (prefix + "\n" + log);
        } catch (Throwable t) {
            return "Apply Payloads failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private void appendPayloadDebugLine(StringBuilder sb, String line) {
        if (sb == null) return;
        sb.append(line == null ? "" : line).append('\n');
    }

    private String readPayloadBytesForDebug(String pkg, String pid, String address, int byteCount) {
        try {
            return readPayloadBytesStrict(pkg, pid, address, byteCount);
        } catch (Throwable t) {
            return "<read failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + ">";
        }
    }

    private String readPayloadBytesStrict(String pkg, String pid, String address, int byteCount) throws Exception {
        if (byteCount <= 0 || TextUtils.isEmpty(address)) throw new IllegalArgumentException("invalid range");
        long begin = parseFlexibleAddress(address);
        DumpBytesResult dump = readMemoryBytesSync(pkg, pid, begin, begin + byteCount);
        if (dump == null || !dump.success) {
            throw new IllegalStateException(dump == null ? "null result" : dump.message);
        }
        if (dump.bytes.length < byteCount) {
            throw new IllegalStateException("short read: " + dump.bytes.length + " of " + byteCount + " bytes");
        }
        return bytesToHex(copyBytes(dump.bytes, byteCount));
    }

    private String appendPayloadDebugDump(String pkg, StringBuilder payloadDebugLog) {
        if (!isDebugOutputEnabled() || payloadDebugLog == null || payloadDebugLog.length() == 0) return "";
        String packageName = TextUtils.isEmpty(pkg) ? "unknown" : pkg;
        String dirPath = MemoryHexPayloadStore.packageDirectoryPath(packageName);
        File finalDir = new File(dirPath);
        File finalFile = new File(finalDir, PAYLOAD_DEBUG_FILE);
        String text = sanitizePayloadDebugText(payloadDebugLog.toString());
        if (!text.endsWith("\n")) text += "\n";
        text += "\n";
        try {
            if ((!finalDir.exists() && !finalDir.mkdirs()) || !finalDir.isDirectory()) {
                throw new IllegalStateException("Cannot create payload debug directory");
            }
            sanitizeExistingPayloadDebugFile(finalFile);
            appendTextFile(finalFile, text);
            return finalFile.getAbsolutePath();
        } catch (Throwable directError) {
            try {
                File stagingRoot = getExternalFilesDir(null);
                if (stagingRoot == null) stagingRoot = getFilesDir();
                File stagingDir = new File(stagingRoot, "payload_debug_stage");
                if ((!stagingDir.exists() && !stagingDir.mkdirs()) || !stagingDir.isDirectory()) {
                    throw new IllegalStateException("Cannot create payload debug staging directory");
                }
                File staged = new File(stagingDir, PAYLOAD_DEBUG_FILE + "." + Long.toHexString(System.nanoTime()));
                writeTextFile(staged, text);
                String cmd = "mkdir -p " + MemoryToolRuntime.shQuote(dirPath)
                        + " && cat " + MemoryToolRuntime.shQuote(staged.getAbsolutePath())
                        + " >> " + MemoryToolRuntime.shQuote(finalFile.getAbsolutePath())
                        + " && chmod 0666 " + MemoryToolRuntime.shQuote(finalFile.getAbsolutePath());
                MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(cmd);
                if (r != null && r.exitCode == 0) return finalFile.getAbsolutePath();
                debugLog("payload debug dump shell append failed: " + oneLine(collectCommandOutput(r)));
            } catch (Throwable fallbackError) {
                debugLog("payload debug dump failed", fallbackError);
            }
        }
        return "";
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v < 0x10) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase(Locale.US));
        }
        return sb.toString();
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String payloadDebugOneLine(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String cleaned = sanitizePayloadDebugText(value);
        cleaned = oneLine(cleaned);
        return cleaned.replaceAll("[ \t\f]+", " ").trim();
    }

    private static String sanitizePayloadDebugText(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return PAYLOAD_DEBUG_TID_PATTERN.matcher(value).replaceAll("");
    }

    private void sanitizeExistingPayloadDebugFile(File file) {
        if (file == null || !file.isFile()) return;
        try {
            String before = readTextFile(file);
            String after = sanitizePayloadDebugText(before);
            if (!TextUtils.equals(before, after)) {
                writeTextFile(file, after);
            }
        } catch (Throwable t) {
            debugLog("payload debug dump sanitize failed: " + oneLine(t.getMessage()));
        }
    }

    private static String normalizePayloadHexForApply(String hex) {
        String value = hex == null ? "" : hex.trim().replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
        if (TextUtils.isEmpty(value)) throw new IllegalArgumentException("empty payload bytes");
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("odd hex digit count");
        return value;
    }

    private static String normalizeOptionalPayloadHexForApply(String hex) {
        String value = hex == null ? "" : hex.trim().replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
        if (TextUtils.isEmpty(value)) return "";
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("odd section marker hex digit count");
        return value;
    }

    private static String normalizePayloadMaskForApply(String maskHex, int byteCount) {
        String value = maskHex == null ? "" : maskHex.trim().replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
        if (TextUtils.isEmpty(value)) return repeatPayloadMask(byteCount, "FF");
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("odd mask hex digit count");
        if (value.length() / 2 != byteCount) throw new IllegalArgumentException("mask byte count does not match payload byte count");
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i += 2) {
            String b = value.substring(i, i + 2);
            out.append("00".equals(b) ? "00" : "FF");
        }
        return out.toString();
    }

    private static String repeatPayloadMask(int byteCount, String byteHex) {
        StringBuilder sb = new StringBuilder(Math.max(0, byteCount) * 2);
        for (int i = 0; i < byteCount; i++) sb.append(byteHex);
        return sb.toString();
    }

    private static boolean hasPayloadMaskWildcards(String maskHex) {
        return countPayloadMaskWildcards(maskHex) > 0;
    }

    private static boolean isNormalizedHexOfLength(String hex, int charLength) {
        return hex != null && hex.length() == charLength && hex.matches("[0-9A-F]+") && (hex.length() & 1) == 0;
    }

    private static boolean shouldUsePayloadSearchValueForPreserve(String liveHex, String searchHex, String maskHex, String expectedFixedHex) {
        if (!isNormalizedHexOfLength(searchHex, expectedFixedHex == null ? 0 : expectedFixedHex.length())) return false;
        if (!isNormalizedHexOfLength(liveHex, searchHex.length())) return true;
        if (isPayloadAllZeroHex(liveHex) && !isPayloadAllZeroHex(searchHex)) return true;
        return !payloadMaskedFixedBytesMatch(maskHex, liveHex, expectedFixedHex)
                && payloadMaskedFixedBytesMatch(maskHex, searchHex, expectedFixedHex);
    }

    private static boolean isPayloadAllZeroHex(String hex) {
        if (TextUtils.isEmpty(hex)) return false;
        for (int i = 0; i < hex.length(); i++) {
            if (hex.charAt(i) != '0') return false;
        }
        return true;
    }

    private static boolean payloadMaskedFixedBytesMatch(String maskHex, String valueHex, String expectedHex) {
        if (!isNormalizedHexOfLength(valueHex, expectedHex == null ? 0 : expectedHex.length())) return false;
        String mask = normalizePayloadMaskForApply(maskHex, expectedHex.length() / 2);
        for (int i = 0; i < expectedHex.length(); i += 2) {
            if ("00".equals(mask.substring(i, i + 2))) continue;
            if (!valueHex.regionMatches(true, i, expectedHex, i, 2)) return false;
        }
        return true;
    }

    private static String payloadAddressKey(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.US);
    }

    private static String mergePayloadWriteHexWithLiveWildcards(String patchedHex, String maskHex, String liveHex) {
        byte[] patched = hexToPayloadBytes(patchedHex);
        byte[] mask = hexToPayloadBytes(maskHex);
        byte[] live = hexToPayloadBytes(liveHex);
        if (patched.length != mask.length || patched.length != live.length) {
            throw new IllegalArgumentException("mask/live byte count does not match payload byte count");
        }
        for (int i = 0; i < patched.length; i++) {
            if (mask[i] == 0) patched[i] = live[i];
        }
        return bytesToHex(patched);
    }

    private static byte[] hexToPayloadBytes(String hex) {
        String clean = normalizePayloadHexForApply(hex);
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static int countPayloadMaskWildcards(String maskHex) {
        if (TextUtils.isEmpty(maskHex)) return 0;
        int count = 0;
        for (int i = 0; i + 1 < maskHex.length(); i += 2) {
            if ("00".equalsIgnoreCase(maskHex.substring(i, i + 2))) count++;
        }
        return count;
    }

    private static int countPayloadMaskFixedBytes(String maskHex) {
        if (TextUtils.isEmpty(maskHex)) return 0;
        int count = 0;
        for (int i = 0; i + 1 < maskHex.length(); i += 2) {
            if (!"00".equalsIgnoreCase(maskHex.substring(i, i + 2))) count++;
        }
        return count;
    }

    /** Returns address lines from apk-medit search-bytes output. */
    private static ArrayList<String> addressesFromPayloadSearchOutput(String output, boolean allMatches) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(output)) return out;
        HashSet<String> seen = new HashSet<>();
        String[] lines = output.split("\r?\n");
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (!t.startsWith("Address:")) continue;
            String[] parts = t.split("\\s+");
            for (String part : parts) {
                if (part == null || !(part.startsWith("0x") || part.startsWith("0X"))) continue;
                String address = part.trim();
                if (seen.add(address)) out.add(address);
                break;
            }
            if (!allMatches && !out.isEmpty()) break;
        }
        return out;
    }

    private static Map<String, String> payloadSearchValuesByAddress(String output, int byteCount) {
        HashMap<String, String> values = new HashMap<>();
        if (TextUtils.isEmpty(output) || byteCount <= 0) return values;
        Matcher matcher = PAYLOAD_SEARCH_VALUE_PATTERN.matcher(output);
        int charCount = byteCount * 2;
        while (matcher.find()) {
            String address = matcher.group(1);
            String value = matcher.group(2);
            if (TextUtils.isEmpty(address) || TextUtils.isEmpty(value)) continue;
            String clean = value.trim().replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
            if (!isNormalizedHexOfLength(clean, charCount)) continue;
            String key = payloadAddressKey(address);
            if (!values.containsKey(key)) values.put(key, clean);
        }
        return values;
    }

    private ArrayList<String> payloadAddressesWithStateFallback(String pkg,
                                                                String searchOutput,
                                                                ArrayList<String> parsedAddresses,
                                                                boolean allMatches,
                                                                StringBuilder payloadDebugLog,
                                                                String label) {
        ArrayList<String> stdoutAddresses = parsedAddresses == null ? new ArrayList<>() : parsedAddresses;
        int foundCount = payloadSearchFoundCount(searchOutput);
        int stdoutAddressCount = stdoutAddresses.size();
        appendPayloadDebugLine(payloadDebugLog, label + "_stdout_found_count=" + foundCount
                + " stdout_address_count=" + stdoutAddressCount);
        if (foundCount <= 0) return stdoutAddresses;
        if (!allMatches && stdoutAddressCount > 0) {
            appendPayloadDebugLine(payloadDebugLog, label + "_address_source=stdout");
            return stdoutAddresses;
        }
        if (allMatches && stdoutAddressCount >= foundCount && stdoutAddressCount > 0) {
            appendPayloadDebugLine(payloadDebugLog, label + "_address_source=stdout");
            return stdoutAddresses;
        }

        PayloadStateDebugInfo stateInfo = readPayloadAddressesFromMeditState(pkg);
        appendPayloadDebugLine(payloadDebugLog, label + "_state_file=" + stateInfo.path);
        appendPayloadDebugLine(payloadDebugLog, label + "_state_file_exists=" + stateInfo.exists
                + " state_file_size=" + stateInfo.sizeBytes
                + " state_read_exit=" + stateInfo.exitCode);
        appendPayloadDebugLine(payloadDebugLog, label + "_state_address_count=" + stateInfo.addresses.size());
        if (!TextUtils.isEmpty(stateInfo.error)) {
            appendPayloadDebugLine(payloadDebugLog, label + "_state_error=" + stateInfo.error);
        }
        if (!stateInfo.addresses.isEmpty()) {
            ArrayList<String> stateAddresses = allMatches ? stateInfo.addresses : singleAddressList(stateInfo.addresses.get(0));
            appendPayloadDebugLine(payloadDebugLog, label + "_address_source=state_file");
            appendPayloadDebugLine(payloadDebugLog, label + "_state_address_sample=" + summarizePayloadAddressSample(stateInfo.addresses));
            return stateAddresses;
        }

        appendPayloadDebugLine(payloadDebugLog, label + "_address_source=" + (stdoutAddresses.isEmpty() ? "none" : "stdout_partial"));
        return stdoutAddresses;
    }

    private PayloadStateDebugInfo readPayloadAddressesFromMeditState(String pkg) {
        PayloadStateDebugInfo info = new PayloadStateDebugInfo(MemoryToolHelper.statePathForCommand());
        if (TextUtils.isEmpty(pkg)) {
            info.error = "empty package";
            return info;
        }
        String quotedStatePath = MemoryToolRuntime.shQuote(info.path);
        String script = "if [ -f " + quotedStatePath + " ]; then "
                + "printf '%s\n' __PT_STATE_EXISTS__=1; "
                + "printf '%s' __PT_STATE_SIZE__=; wc -c < " + quotedStatePath + " 2>/dev/null || printf 0; "
                + "cat " + quotedStatePath + "; "
                + "else printf '%s\n' __PT_STATE_EXISTS__=0; printf '%s\n' __PT_STATE_SIZE__=0; fi";
        String cmd = "run-as " + MemoryToolRuntime.shQuote(pkg.trim()) + " sh -c " + MemoryToolRuntime.shQuote(script);
        MemoryToolRuntime.CmdResult stateResult = runBackendShellCommandCaptureSync(cmd);
        info.exitCode = stateResult == null ? Integer.MIN_VALUE : stateResult.exitCode;
        String output = collectCommandOutput(stateResult);
        info.exists = output.contains("__PT_STATE_EXISTS__=1");
        info.sizeBytes = parsePayloadStateDebugInt(output, "__PT_STATE_SIZE__=");
        if (stateResult == null) {
            info.error = "no command result";
            return info;
        }
        if (stateResult.exitCode != 0) {
            info.error = payloadDebugOneLine(output);
            return info;
        }
        int jsonStart = output.indexOf('{');
        if (jsonStart < 0) {
            if (info.exists && info.sizeBytes > 0) info.error = "state JSON not found";
            return info;
        }
        info.addresses = addressesFromPayloadStateJson(output.substring(jsonStart), true);
        return info;
    }

    private static ArrayList<String> singleAddressList(String address) {
        ArrayList<String> out = new ArrayList<>();
        if (!TextUtils.isEmpty(address)) out.add(address);
        return out;
    }

    private static int parsePayloadStateDebugInt(String output, String marker) {
        if (TextUtils.isEmpty(output) || TextUtils.isEmpty(marker)) return -1;
        int start = output.indexOf(marker);
        if (start < 0) return -1;
        start += marker.length();
        while (start < output.length() && Character.isWhitespace(output.charAt(start))) start++;
        int end = start;
        while (end < output.length() && Character.isDigit(output.charAt(end))) end++;
        if (end <= start) return -1;
        try {
            return Integer.parseInt(output.substring(start, end));
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String summarizePayloadAddressSample(ArrayList<String> addresses) {
        if (addresses == null || addresses.isEmpty()) return "<none>";
        int count = addresses.size();
        StringBuilder sb = new StringBuilder();
        int head = Math.min(3, count);
        for (int i = 0; i < head; i++) {
            if (sb.length() > 0) sb.append(',');
            sb.append(addresses.get(i));
        }
        if (count > 6) sb.append(",...");
        int tailStart = count > 6 ? count - 3 : head;
        for (int i = tailStart; i < count; i++) {
            if (i < head) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(addresses.get(i));
        }
        return sb.toString();
    }

    private static final class PayloadStateDebugInfo {
        final String path;
        ArrayList<String> addresses = new ArrayList<>();
        boolean exists;
        int sizeBytes = -1;
        int exitCode = Integer.MIN_VALUE;
        String error = "";

        PayloadStateDebugInfo(String path) {
            this.path = TextUtils.isEmpty(path) ? "<unknown>" : path;
        }
    }

    private static ArrayList<String> addressesFromPayloadStateJson(String json, boolean allMatches) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return out;
        HashSet<String> seen = new HashSet<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray founds = root.optJSONArray("founds");
            if (founds == null) return out;
            for (int i = 0; i < founds.length(); i++) {
                JSONObject found = founds.optJSONObject(i);
                if (found == null) continue;
                JSONArray addrs = found.optJSONArray("addrs");
                if (addrs == null) continue;
                for (int j = 0; j < addrs.length(); j++) {
                    String formatted = formatStateAddress(addrs.opt(j));
                    if (TextUtils.isEmpty(formatted) || !seen.add(formatted)) continue;
                    out.add(formatted);
                    if (!allMatches) return out;
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String formatStateAddress(Object value) {
        try {
            if (value == null) return "";
            long address;
            if (value instanceof Number) {
                address = ((Number) value).longValue();
            } else {
                String text = String.valueOf(value).trim();
                if (TextUtils.isEmpty(text)) return "";
                address = parseFlexibleAddress(text);
            }
            return formatHex(address);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int payloadSearchFoundCount(String output) {
        if (TextUtils.isEmpty(output)) return 0;
        Matcher m = Pattern.compile("Found:\\s*(\\d+)!!").matcher(output);
        int total = 0;
        while (m.find()) {
            try {
                total += Integer.parseInt(m.group(1));
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    private static final class PayloadSectionRange {
        final long start;
        final long end;

        PayloadSectionRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private ArrayList<PayloadSectionRange> resolvePayloadSectionRanges(String pkg,
                                                                       String effectivePid,
                                                                       boolean withoutPtrace,
                                                                       String sectionStartHex,
                                                                       String sectionEndHex,
                                                                       int searchLimit,
                                                                       StringBuilder payloadDebugLog) throws Exception {
        ArrayList<PayloadSectionRange> ranges = new ArrayList<>();
        if (TextUtils.isEmpty(sectionStartHex) || TextUtils.isEmpty(sectionEndHex)) return ranges;
        int markerLimit = Math.max(1, searchLimit);
        ArrayList<Long> starts = searchPayloadMarkerAddresses(pkg, effectivePid, withoutPtrace, sectionStartHex, markerLimit, payloadDebugLog, "section_start");
        ArrayList<Long> ends = searchPayloadMarkerAddresses(pkg, effectivePid, withoutPtrace, sectionEndHex, markerLimit, payloadDebugLog, "section_end");
        if (starts.isEmpty() || ends.isEmpty()) return ranges;
        Collections.sort(starts, Long::compareUnsigned);
        Collections.sort(ends, Long::compareUnsigned);
        int endIndex = 0;
        for (long start : starts) {
            while (endIndex < ends.size() && Long.compareUnsigned(ends.get(endIndex), start) <= 0) endIndex++;
            if (endIndex >= ends.size()) break;
            long end = ends.get(endIndex);
            ranges.add(new PayloadSectionRange(start, end));
            endIndex++;
        }
        appendPayloadDebugLine(payloadDebugLog, "section_ranges=" + formatPayloadSectionRanges(ranges));
        return ranges;
    }

    private ArrayList<Long> searchPayloadMarkerAddresses(String pkg,
                                                         String effectivePid,
                                                         boolean withoutPtrace,
                                                         String markerHex,
                                                         int searchLimit,
                                                         StringBuilder payloadDebugLog,
                                                         String label) throws Exception {
        ArrayList<Long> out = new ArrayList<>();
        String cmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                pkg,
                MemoryToolRuntime.PUBLIC_BIN_DIR,
                withoutPtrace,
                "search-bytes",
                effectivePid,
                "hex-bytes",
                markerHex,
                null,
                null,
                null,
                searchLimit);
        MemoryToolRuntime.CmdResult result = runBackendShellCommandCaptureSync(cmd);
        if (isMemoryCommandFailure(result)) {
            throw new IllegalStateException(label + " marker search failed: " + summarizeResult("marker search failed", result));
        }
        String output = collectCommandOutput(result);
        ArrayList<String> addresses = addressesFromPayloadSearchOutput(output, true);
        addresses = payloadAddressesWithStateFallback(pkg, output, addresses, true, payloadDebugLog, label);
        appendPayloadDebugLine(payloadDebugLog, label + "_matches=" + addresses.size() + " marker=" + markerHex);
        appendPayloadDebugLine(payloadDebugLog, label + "_output=" + payloadDebugOneLine(output));
        for (String address : addresses) {
            try {
                out.add(parseFlexibleAddress(address));
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static ArrayList<String> filterPayloadAddressesBySection(ArrayList<String> addresses,
                                                                     ArrayList<PayloadSectionRange> ranges,
                                                                     int byteCount,
                                                                     boolean allMatches) {
        if (addresses == null || addresses.isEmpty()) return new ArrayList<>();
        if (ranges == null || ranges.isEmpty()) return new ArrayList<>(addresses);
        ArrayList<String> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String addressText : addresses) {
            long address;
            try {
                address = parseFlexibleAddress(addressText);
            } catch (Throwable ignored) {
                continue;
            }
            if (!payloadAddressInsideAnySection(address, byteCount, ranges)) continue;
            String normalized = formatHex(address);
            if (seen.add(normalized)) out.add(normalized);
            if (!allMatches && !out.isEmpty()) break;
        }
        return out;
    }

    private static boolean payloadAddressInsideAnySection(long address, int byteCount, ArrayList<PayloadSectionRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return true;
        long endAddress = address + Math.max(0, byteCount);
        for (PayloadSectionRange range : ranges) {
            if (range == null) continue;
            if (Long.compareUnsigned(address, range.start) >= 0 && Long.compareUnsigned(endAddress, range.end) <= 0) {
                return true;
            }
        }
        return false;
    }

    private static String formatPayloadSectionRanges(ArrayList<PayloadSectionRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return "0";
        ArrayList<String> labels = new ArrayList<>();
        for (int i = 0; i < ranges.size() && i < 8; i++) {
            PayloadSectionRange range = ranges.get(i);
            if (range != null) labels.add(formatHex(range.start) + ".." + formatHex(range.end));
        }
        if (ranges.size() > labels.size()) labels.add("+" + (ranges.size() - labels.size()) + " more");
        return TextUtils.join(", ", labels);
    }

    private void requestToolWriteBytes(String address, String hexBytes, MemoryOverlayWindowSupport.ByteWriteCallback callback) {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) { deliverToolWrite(callback, false, "Enter a target package first."); return; }
        final String targetPid = getSelectedPid();
        if (TextUtils.isEmpty(targetPid)) { refreshProcesses(); deliverToolWrite(callback, false, "No running PID for Auto-select yet. Refresh processes or choose the running process from the dropdown."); return; }
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(hexBytes)) { deliverToolWrite(callback, false, "Enter an address and one or more hex bytes."); return; }
        new Thread(() -> {
            if (shouldAutoStage()) {
                MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                if (install == null || install.exitCode != 0) { deliverToolWrite(callback, false, summarizeResult("apk-medit could not be staged for the current backend.", install)); return; }
            }
            String effectivePid = targetPid;
            String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
            if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
            String shellCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(pkg, MemoryToolRuntime.PUBLIC_BIN_DIR, withoutPtrace, "write-bytes", effectivePid, null, hexBytes, address, null, null, getMaxScanResults());
            MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(shellCmd);
            boolean failed = isMemoryCommandFailure(r);
            deliverToolWrite(callback, !failed, failed ? summarizeResult("Hex write failed.", r) : summarizeResult("Hex write finished.", r));
        }, "MemoryOverlayHexWrite").start();
    }

    private void deliverToolWrite(MemoryOverlayWindowSupport.ByteWriteCallback callback, boolean success, String text) {
        runOnUiThread(() -> {
            try { if (callback != null) callback.onWriteResult(success, text); } catch (Throwable ignored) {}
            releaseOverlayInputFocus();
        });
    }

    private void deliverToolDump(MemoryOverlayWindowSupport.DumpCallback callback, boolean success, String text) {
        runOnUiThread(() -> {
            try {
                if (callback != null) callback.onDumpResult(success, text);
            } catch (Throwable ignored) {
            }
            releaseOverlayInputFocus();
        });
    }

    private void searchMemoryByFileType(String extension) {
        String finalExtension = sanitizeFileExtension(extension);
        if (TextUtils.isEmpty(finalExtension) || TextUtils.equals(finalExtension, "bin")) {
            showFileTypeStatus("Choose a known file type before searching memory.");
            return;
        }
        showFileTypeStatus("Searching readable memory for ." + finalExtension + " magic bytes...");
        runMemoryCommand(
                "search-magic",
                null,
                finalExtension,
                null,
                null,
                null,
                true,
                "." + finalExtension);
    }

    private void exportMemoryByFileType(String extension, String begin, String end) {
        final String finalExtension = sanitizeFileExtension(extension);
        ArrayList<MemoryResultRow> checkedFileRows = getCheckedFileTypeRows(finalExtension);
        if (!checkedFileRows.isEmpty()) {
            exportCheckedMemoryFileTypeMatches(finalExtension, checkedFileRows);
            return;
        }

        String actualBegin = TextUtils.isEmpty(begin) ? defaultToolAddress() : begin.trim();
        String actualEnd = TextUtils.isEmpty(end) ? "" : end.trim();
        if (TextUtils.isEmpty(actualBegin)) { appendStatus("Select a result/address or enter a begin address first."); return; }
        if (TextUtils.isEmpty(actualEnd)) {
            try { actualEnd = formatHex(parseFlexibleAddress(actualBegin) + FILETYPE_FALLBACK_EXPORT_BYTES); } catch (Throwable t) { appendStatus("Enter a valid end address for export."); return; }
        }
        final String finalBegin = actualBegin;
        final String finalEnd = actualEnd;
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) { appendStatus("Enter a target package first."); return; }
        final String targetPid = getSelectedPid();
        if (TextUtils.isEmpty(targetPid)) { refreshProcesses(); appendStatus("No running PID for Auto-select yet. Refresh processes or choose the running process from the dropdown."); return; }
        showFileTypeStatus("Exporting memory range as ." + finalExtension + "...");
        new Thread(() -> {
            String effectivePid = targetPid;
            String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
            if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
            String result;
            try {
                result = exportMemoryRangeDirectToDiskSync(pkg, effectivePid, parseFlexibleAddress(finalBegin), parseFlexibleAddress(finalEnd), finalExtension);
            } catch (Throwable t) {
                result = "Memory export failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            final String finalResult = result;
            runOnUiThread(() -> showFileTypeStatus(finalResult));
        }, "MemoryOverlayManualFileTypeExport").start();
    }

    private ArrayList<MemoryResultRow> getCheckedFileTypeRows(String extension) {
        ArrayList<MemoryResultRow> rows = new ArrayList<>();
        String normalized = normalizeSearchFileExtension(extension);
        if (checkedPatchResultKeys.isEmpty()) return rows;
        for (MemoryResultRow row : resultItems) {
            if (row == null || !checkedPatchResultKeys.contains(resultKey(row))) continue;
            String converter = row.converterId == null ? "" : row.converterId.trim().toLowerCase(Locale.US);
            String dataType = row.dataType == null ? "" : row.dataType.trim().toLowerCase(Locale.US);
            if (TextUtils.equals(converter, "file:" + normalized) || TextUtils.equals(dataType, "file:" + normalized)) rows.add(row);
        }
        return rows;
    }

    private void exportCheckedMemoryFileTypeMatches(String extension, ArrayList<MemoryResultRow> rows) {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) { appendStatus("Enter a target package first."); return; }
        final String targetPid = getSelectedPid();
        if (TextUtils.isEmpty(targetPid)) { refreshProcesses(); appendStatus("No running PID for Auto-select yet. Refresh processes or choose the running process from the dropdown."); return; }
        final String normalizedExtension = normalizeSearchFileExtension(extension);
        final ArrayList<MemoryResultRow> exportRows = new ArrayList<>(rows);
        showFileTypeStatus("Exporting " + exportRows.size() + " checked ." + normalizedExtension + " file-type match" + (exportRows.size() == 1 ? "" : "es") + "...");
        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            int saved = 0;
            String effectivePid = targetPid;
            String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
            if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
            for (MemoryResultRow row : exportRows) {
                try {
                    MagicFileRange range = resolveMagicFileRange(pkg, effectivePid, row, normalizedExtension);
                    if (range == null || range.end <= range.begin) {
                        appendLine(log, "Skipped " + formatHex(row.address) + ": file length could not be resolved.");
                        continue;
                    }
                    String result = exportMemoryRangeDirectToDiskSync(pkg, effectivePid, range.begin, range.end, normalizedExtension);
                    appendLine(log, result + "\n  Range: " + formatHex(range.begin) + "-" + formatHex(range.end)
                            + " (" + (range.end - range.begin) + " bytes, " + range.detail + ")");
                    if (result != null && result.startsWith("Export saved:")) saved++;
                } catch (Throwable t) {
                    appendLine(log, "Export failed at " + formatHex(row.address) + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
            final int finalSaved = saved;
            final String finalText = log.toString().trim();
            runOnUiThread(() -> showFileTypeStatus("Checked file-type export finished: " + finalSaved + "/" + exportRows.size() + " saved." + (TextUtils.isEmpty(finalText) ? "" : "\n" + finalText)));
        }, "MemoryOverlayFileTypeExport").start();
    }

    private MagicFileRange resolveMagicFileRange(String pkg, String pid, MemoryResultRow row, String extension) {
        String normalized = normalizeSearchFileExtension(extension);
        long start = row.address - magicSignatureOffset(normalized, row.value);
        if (start < 0L) start = row.address;
        int readSize = FILETYPE_HEADER_READ_BYTES;
        int lastByteCount = 0;
        String lastReason = "no readable bytes returned";
        DumpBytesResult lastDump = null;
        while (readSize <= FILETYPE_BASE64_CAPTURE_LIMIT_BYTES) {
            lastDump = readMemoryBytesSync(pkg, pid, start, start + readSize);
            if (lastDump == null || !lastDump.success || lastDump.bytes == null || lastDump.bytes.length == 0) {
                lastReason = lastDump == null ? "read failed" : lastDump.message;
                break;
            }
            lastByteCount = Math.max(lastByteCount, lastDump.bytes.length);
            FileLengthDetection detected = detectFileLengthDetailed(normalized, lastDump.bytes);
            if (detected.length > 0) {
                int safeLength = Math.min(detected.length, FILETYPE_SCAN_READ_LIMIT_BYTES);
                return new MagicFileRange(start, start + safeLength, true, detected.detail);
            }
            lastReason = detected.detail;
            if (lastDump.bytes.length < readSize) break;
            if (readSize == FILETYPE_BASE64_CAPTURE_LIMIT_BYTES) break;
            readSize = Math.min(FILETYPE_BASE64_CAPTURE_LIMIT_BYTES, readSize * 2);
        }

        FileLengthDetection streamed = detectFileEndByWindowScan(pkg, pid, start, normalized);
        if (streamed.length > 0) {
            int safeLength = Math.min(streamed.length, FILETYPE_SCAN_READ_LIMIT_BYTES);
            return new MagicFileRange(start, start + safeLength, true, streamed.detail);
        }
        if (!TextUtils.isEmpty(streamed.detail)) lastReason = streamed.detail;

        int fallback = FILETYPE_FALLBACK_EXPORT_BYTES;
        if (lastByteCount > 0) {
            fallback = Math.min(Math.max(lastByteCount, FILETYPE_FALLBACK_EXPORT_BYTES), FILETYPE_SCAN_READ_LIMIT_BYTES);
        }
        return new MagicFileRange(start, start + fallback, false,
                "EOF not detected; fallback used after probing " + lastByteCount + " bytes; " + lastReason);
    }

    private DumpBytesResult readMemoryBytesSync(String pkg, String pid, long begin, long end) {
        if (end <= begin) return new DumpBytesResult(false, "invalid memory range", new byte[0]);
        long length = end - begin;
        if (length > FILETYPE_SCAN_READ_LIMIT_BYTES) {
            return new DumpBytesResult(false, "range is larger than the file-type export safety limit", new byte[0]);
        }
        String targetTemp = "./.permstest_probe_" + Long.toHexString(System.nanoTime()) + ".tmp";
        try {
            String dumpError = dumpMemoryRangeToTargetTempSync(pkg, pid, begin, end, targetTemp);
            if (!TextUtils.isEmpty(dumpError)) {
                cleanupTargetTempExport(pkg, targetTemp);
                return new DumpBytesResult(false, dumpError, new byte[0]);
            }
            DumpBytesResult captured = readTargetTempBytesBase64Sync(pkg, targetTemp, length);
            cleanupTargetTempExport(pkg, targetTemp);
            return captured;
        } catch (Throwable t) {
            cleanupTargetTempExport(pkg, targetTemp);
            return new DumpBytesResult(false, t.getClass().getSimpleName() + ": " + t.getMessage(), new byte[0]);
        }
    }

    private static byte[] readFileBytes(File file, int maxBytes) throws java.io.IOException {
        if (file == null || !file.isFile() || maxBytes <= 0) return new byte[0];
        int cap = (int) Math.min(file.length(), (long) maxBytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(0, cap));
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[Math.min(64 * 1024, Math.max(1, cap))];
            int total = 0;
            int r;
            while (total < cap && (r = in.read(buf, 0, Math.min(buf.length, cap - total))) > 0) {
                out.write(buf, 0, r);
                total += r;
            }
        }
        return out.toByteArray();
    }

    private static String normalizeSearchFileExtension(String extension) {
        String ext = sanitizeFileExtension(extension);
        if (TextUtils.equals(ext, "jpeg") || TextUtils.equals(ext, "jpe")) return "jpg";
        if (TextUtils.equals(ext, "apks") || TextUtils.equals(ext, "apkm") || TextUtils.equals(ext, "xapk") || TextUtils.equals(ext, "jar")) return "zip";
        if (TextUtils.equals(ext, "so")) return "elf";
        return ext;
    }

    private static int magicSignatureOffset(String extension, String label) {
        String ext = normalizeSearchFileExtension(extension);
        if (TextUtils.equals(ext, "webp") || TextUtils.equals(ext, "wav")) return 8;
        if (TextUtils.equals(ext, "mp4")) return 4;
        return 0;
    }

    private FileLengthDetection detectFileEndByWindowScan(String pkg, String pid, long start, String extension) {
        String ext = normalizeSearchFileExtension(extension);
        if (!(TextUtils.equals(ext, "png") || TextUtils.equals(ext, "jpg") || TextUtils.equals(ext, "gif"))) {
            return new FileLengthDetection(-1, "window EOF scan is not supported for ." + ext);
        }
        byte[] marker = terminalMarkerForExtension(ext);
        if (marker == null || marker.length == 0) return new FileLengthDetection(-1, "terminal marker unavailable for ." + ext);
        int window = FILETYPE_EOF_SCAN_WINDOW_BYTES;
        int overlap = Math.max(marker.length + 8, FILETYPE_EOF_SCAN_OVERLAP_BYTES);
        long cursor = start;
        long scanned = 0L;
        while (scanned < FILETYPE_SCAN_READ_LIMIT_BYTES) {
            long begin = cursor;
            long remaining = FILETYPE_SCAN_READ_LIMIT_BYTES - scanned;
            long readLength = Math.min(window, remaining);
            DumpBytesResult chunk = readMemoryBytesSync(pkg, pid, begin, begin + readLength);
            if (chunk == null || !chunk.success || chunk.bytes == null || chunk.bytes.length == 0) {
                String reason = chunk == null ? "window scan read failed" : chunk.message;
                return new FileLengthDetection(-1, "terminal marker not found after " + scanned + " bytes; " + reason);
            }
            int found = indexOf(chunk.bytes, marker, begin == start ? 8 : 0);
            if (found >= 0) {
                int relative = (int) ((begin - start) + found + marker.length);
                return new FileLengthDetection(relative, terminalDetailForExtension(ext) + " found by window scan");
            }
            if (chunk.bytes.length < readLength) break;
            scanned = (begin - start) + chunk.bytes.length;
            cursor = start + Math.max(0L, scanned - overlap);
            if (cursor <= begin) cursor = begin + chunk.bytes.length;
        }
        return new FileLengthDetection(-1, "terminal marker not found within " + FILETYPE_SCAN_READ_LIMIT_BYTES + " bytes");
    }

    private static byte[] terminalMarkerForExtension(String extension) {
        String ext = normalizeSearchFileExtension(extension);
        if (TextUtils.equals(ext, "png")) return new byte[]{0x49, 0x45, 0x4e, 0x44, (byte) 0xae, 0x42, 0x60, (byte) 0x82};
        if (TextUtils.equals(ext, "jpg")) return new byte[]{(byte) 0xff, (byte) 0xd9};
        if (TextUtils.equals(ext, "gif")) return new byte[]{0x3b};
        return null;
    }

    private static String terminalDetailForExtension(String extension) {
        String ext = normalizeSearchFileExtension(extension);
        if (TextUtils.equals(ext, "png")) return "PNG IEND trailer";
        if (TextUtils.equals(ext, "jpg")) return "JPEG EOI marker";
        if (TextUtils.equals(ext, "gif")) return "GIF trailer";
        return "EOF marker";
    }

    private static int detectFileLength(String extension, byte[] bytes) {
        return detectFileLengthDetailed(extension, bytes).length;
    }

    private static FileLengthDetection detectFileLengthDetailed(String extension, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new FileLengthDetection(-1, "no bytes captured");
        String ext = normalizeSearchFileExtension(extension);
        int length;
        switch (ext) {
            case "png":
                length = detectPngLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "PNG IEND chunk" : "PNG IEND chunk not found in captured bytes");
            case "jpg":
                length = detectJpegLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "JPEG EOI marker" : "JPEG EOI marker not found in captured bytes");
            case "gif":
                length = detectGifLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "GIF trailer" : "GIF trailer not found in captured bytes");
            case "webp":
                length = detectRiffLength(bytes, "WEBP");
                return new FileLengthDetection(length, length > 0 ? "RIFF WEBP size" : "RIFF WEBP size unavailable");
            case "wav":
                length = detectRiffLength(bytes, "WAVE");
                return new FileLengthDetection(length, length > 0 ? "RIFF WAVE size" : "RIFF WAVE size unavailable");
            case "zip":
                length = detectZipLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "ZIP EOCD record" : "ZIP EOCD record not found in captured bytes");
            case "dex":
                length = detectDexLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "DEX header file_size" : "DEX file_size unavailable");
            case "elf":
                length = detectElfLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "ELF program headers" : "ELF program headers unavailable");
            case "ogg":
                length = detectOggLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "OGG page chain" : "OGG page chain incomplete in captured bytes");
            case "mp4":
                length = detectMp4Length(bytes);
                return new FileLengthDetection(length, length > 0 ? "MP4 atom table" : "MP4 atom table incomplete in captured bytes");
            case "json":
                length = detectJsonLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "JSON balanced document" : "JSON close token not found in captured bytes");
            case "js":
                length = detectTextLikeLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "JavaScript/text boundary" : "JavaScript/text boundary unavailable");
            case "xml":
                length = detectXmlLength(bytes);
                return new FileLengthDetection(length, length > 0 ? "XML last complete tag" : "XML boundary unavailable");
            case "mp3":
                length = detectMp3Length(bytes);
                return new FileLengthDetection(length, length > 0 ? "MP3 ID3 size" : "MP3 ID3 size unavailable");
            default:
                return new FileLengthDetection(-1, "unsupported EOF detector for ." + ext);
        }
    }

    private static int detectPngLength(byte[] b) {
        byte[] sig = new byte[]{(byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (!startsWith(b, sig)) return -1;
        int off = 8;
        while (off + 12 <= b.length) {
            long chunkLength = uint32Be(b, off);
            if (chunkLength < 0L || chunkLength > Integer.MAX_VALUE) return -1;
            long next = (long) off + 12L + chunkLength;
            if (next > b.length) return -1;
            if (asciiAt(b, off + 4, "IEND")) return (int) next;
            if (next <= off) return -1;
            off = (int) next;
        }
        return -1;
    }

    private static int detectJpegLength(byte[] b) {
        if (b.length < 4 || (b[0] & 0xff) != 0xff || (b[1] & 0xff) != 0xd8) return -1;
        for (int i = 2; i + 1 < b.length; i++) {
            if ((b[i] & 0xff) == 0xff && (b[i + 1] & 0xff) == 0xd9) return i + 2;
        }
        return -1;
    }

    private static int detectGifLength(byte[] b) {
        if (b.length < 6 || !(startsWithAscii(b, "GIF87a") || startsWithAscii(b, "GIF89a"))) return -1;
        for (int i = 6; i < b.length; i++) if ((b[i] & 0xff) == 0x3b) return i + 1;
        return -1;
    }

    private static int detectRiffLength(byte[] b, String kind) {
        if (b.length < 12 || !startsWithAscii(b, "RIFF") || !asciiAt(b, 8, kind)) return -1;
        long size = uint32Le(b, 4) + 8L;
        return size > 0L && size <= Integer.MAX_VALUE ? (int) size : -1;
    }

    private static int detectZipLength(byte[] b) {
        if (b.length < 22 || !startsWith(b, new byte[]{0x50, 0x4b})) return -1;
        for (int i = b.length - 22; i >= 0; i--) {
            if ((b[i] & 0xff) == 0x50 && (b[i + 1] & 0xff) == 0x4b && (b[i + 2] & 0xff) == 0x05 && (b[i + 3] & 0xff) == 0x06) {
                int comment = (int) uint16Le(b, i + 20);
                int end = i + 22 + comment;
                if (end <= b.length) return end;
            }
        }
        return -1;
    }

    private static int detectDexLength(byte[] b) {
        if (b.length < 0x24 || !startsWithAscii(b, "dex\n")) return -1;
        long size = uint32Le(b, 0x20);
        return size > 0L && size <= Integer.MAX_VALUE ? (int) size : -1;
    }

    private static int detectElfLength(byte[] b) {
        if (b.length < 0x40 || (b[0] & 0xff) != 0x7f || b[1] != 0x45 || b[2] != 0x4c || b[3] != 0x46) return -1;
        boolean is64 = (b[4] & 0xff) == 2;
        boolean le = (b[5] & 0xff) != 2;
        long phoff = is64 ? uint64(b, 0x20, le) : uint32(b, 0x1c, le);
        int phentsize = (int) uint16(b, is64 ? 0x36 : 0x2a, le);
        int phnum = (int) uint16(b, is64 ? 0x38 : 0x2c, le);
        long max = 0L;
        for (int i = 0; i < phnum; i++) {
            long off = phoff + (long) i * phentsize;
            if (off < 0 || off + phentsize > b.length) break;
            long pOffset = is64 ? uint64(b, (int) off + 0x08, le) : uint32(b, (int) off + 0x04, le);
            long pFileSz = is64 ? uint64(b, (int) off + 0x20, le) : uint32(b, (int) off + 0x10, le);
            if (pFileSz > 0L) max = Math.max(max, pOffset + pFileSz);
        }
        return max > 0L && max <= Integer.MAX_VALUE ? (int) max : -1;
    }

    private static int detectOggLength(byte[] b) {
        int off = 0;
        int last = -1;
        while (off + 27 <= b.length && asciiAt(b, off, "OggS")) {
            int segments = b[off + 26] & 0xff;
            if (off + 27 + segments > b.length) break;
            int payload = 0;
            for (int i = 0; i < segments; i++) payload += b[off + 27 + i] & 0xff;
            int next = off + 27 + segments + payload;
            if (next > b.length || next <= off) break;
            last = next;
            off = next;
        }
        return last;
    }

    private static int detectMp4Length(byte[] b) {
        if (b.length < 12 || !asciiAt(b, 4, "ftyp")) return -1;
        long offset = 0L;
        long max = 0L;
        while (offset + 8 <= b.length) {
            long size = uint32Be(b, (int) offset);
            int header = 8;
            if (size == 1L) {
                if (offset + 16 > b.length) break;
                size = uint64(b, (int) offset + 8, false);
                header = 16;
            } else if (size == 0L) {
                break;
            }
            if (size < header) break;
            long next = offset + size;
            max = Math.max(max, next);
            if (next > b.length) return next <= Integer.MAX_VALUE ? (int) next : -1;
            offset = next;
        }
        return max > 0L && max <= Integer.MAX_VALUE ? (int) max : -1;
    }

    private static int detectJsonLength(byte[] b) {
        int start = firstNonWhitespace(b, 0);
        if (start < 0) return -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        byte open = b[start];
        byte close = open == '{' ? (byte) '}' : open == '[' ? (byte) ']' : 0;
        if (close == 0) return -1;
        for (int i = start; i < b.length; i++) {
            byte c = b[i];
            if (inString) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return -1;
    }

    private static int detectXmlLength(byte[] b) {
        int last = -1;
        for (int i = 0; i < b.length; i++) {
            int c = b[i] & 0xff;
            if (c == 0) return last > 0 ? last : -1;
            if (c == '>') last = i + 1;
        }
        return last;
    }

    private static int detectMp3Length(byte[] b) {
        if (b.length >= 10 && asciiAt(b, 0, "ID3")) {
            int size = ((b[6] & 0x7f) << 21) | ((b[7] & 0x7f) << 14) | ((b[8] & 0x7f) << 7) | (b[9] & 0x7f);
            return size > 0 ? size + 10 : -1;
        }
        return -1;
    }

    private static int detectTextLikeLength(byte[] b) {
        if (b == null || b.length == 0) return -1;
        int lastText = -1;
        int zeroRun = 0;
        for (int i = 0; i < b.length; i++) {
            int c = b[i] & 0xff;
            if (c == 0) {
                zeroRun++;
                if (lastText > 0 && zeroRun >= 1) return lastText + 1;
                continue;
            }
            zeroRun = 0;
            if (c == '\n' || c == '\r' || c == '\t' || (c >= 0x20 && c <= 0x7e)) {
                lastText = i;
                continue;
            }
            if (lastText > 16) return lastText + 1;
        }
        return lastText > 32 ? Math.min(lastText + 1, b.length) : -1;
    }

    private static boolean startsWith(byte[] b, byte[] sig) {
        if (b == null || sig == null || b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if (b[i] != sig[i]) return false;
        return true;
    }

    private static boolean startsWithAscii(byte[] b, String text) { return asciiAt(b, 0, text); }

    private static boolean asciiAt(byte[] b, int offset, String text) {
        if (b == null || text == null || offset < 0 || offset + text.length() > b.length) return false;
        for (int i = 0; i < text.length(); i++) if ((byte) text.charAt(i) != b[offset + i]) return false;
        return true;
    }

    private static int indexOf(byte[] b, byte[] needle, int start) {
        if (b == null || needle == null || needle.length == 0) return -1;
        int begin = Math.max(0, start);
        for (int i = begin; i + needle.length <= b.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (b[i + j] != needle[j]) { match = false; break; }
            }
            if (match) return i;
        }
        return -1;
    }

    private static int firstNonWhitespace(byte[] b, int start) {
        if (b == null) return -1;
        for (int i = Math.max(0, start); i < b.length; i++) {
            int c = b[i] & 0xff;
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return i;
        }
        return -1;
    }

    private static long uint16Le(byte[] b, int off) { return uint16(b, off, true); }
    private static long uint32Le(byte[] b, int off) { return uint32(b, off, true); }
    private static long uint32Be(byte[] b, int off) { return uint32(b, off, false); }

    private static long uint16(byte[] b, int off, boolean le) {
        if (off < 0 || off + 2 > b.length) return 0L;
        return le ? ((b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8)) : (((b[off] & 0xffL) << 8) | (b[off + 1] & 0xffL));
    }

    private static long uint32(byte[] b, int off, boolean le) {
        if (off < 0 || off + 4 > b.length) return 0L;
        if (le) return (b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8) | ((b[off + 2] & 0xffL) << 16) | ((b[off + 3] & 0xffL) << 24);
        return ((b[off] & 0xffL) << 24) | ((b[off + 1] & 0xffL) << 16) | ((b[off + 2] & 0xffL) << 8) | (b[off + 3] & 0xffL);
    }

    private static long uint64(byte[] b, int off, boolean le) {
        if (off < 0 || off + 8 > b.length) return 0L;
        long v = 0L;
        if (le) {
            for (int i = 7; i >= 0; i--) v = (v << 8) | (b[off + i] & 0xffL);
        } else {
            for (int i = 0; i < 8; i++) v = (v << 8) | (b[off + i] & 0xffL);
        }
        return v;
    }

    private static byte[] copyBytes(byte[] bytes, int length) {
        int count = Math.max(0, Math.min(length, bytes == null ? 0 : bytes.length));
        byte[] out = new byte[count];
        if (count > 0) System.arraycopy(bytes, 0, out, 0, count);
        return out;
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (sb == null || TextUtils.isEmpty(line)) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(line);
    }

    private static final class FileLengthDetection {
        final int length;
        final String detail;

        FileLengthDetection(int length, String detail) {
            this.length = length;
            this.detail = detail == null ? "" : detail;
        }
    }

    private static final class MagicFileRange {
        final long begin;
        final long end;
        final boolean detected;
        final String detail;

        MagicFileRange(long begin, long end, boolean detected, String detail) {
            this.begin = begin;
            this.end = end;
            this.detected = detected;
            this.detail = detail == null ? "" : detail;
        }
    }

    private static final class DumpBytesResult {
        final boolean success;
        final String message;
        final byte[] bytes;

        DumpBytesResult(boolean success, String message, byte[] bytes) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }
    }

    private void importMemoryByFileType(String extension, String begin, String sourcePath) {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) { appendStatus("Enter a target package first."); return; }
        final String targetPid = getSelectedPid();
        if (TextUtils.isEmpty(targetPid)) { refreshProcesses(); appendStatus("No running PID for Auto-select yet. Refresh processes or choose the running process from the dropdown."); return; }
        final String actualBegin = TextUtils.isEmpty(begin) ? defaultToolAddress() : begin.trim();
        if (TextUtils.isEmpty(actualBegin)) { appendStatus("Select a result/address or enter a begin address first."); return; }
        final String actualSource = sourcePath == null ? "" : sourcePath.trim();
        if (TextUtils.isEmpty(actualSource)) { appendStatus("Enter a source file path before Import/Replace."); return; }
        final String finalExtension = sanitizeFileExtension(extension);
        appendStatus("Importing ." + finalExtension + " bytes into memory...");
        new Thread(() -> {
            String staged = "/data/local/tmp/dev.perms.test/imports/import_" + System.currentTimeMillis() + "." + finalExtension;
            try {
                String stageCmd = "mkdir -p /data/local/tmp/dev.perms.test/imports"
                        + " && cp -f " + shQuote(actualSource) + " " + shQuote(staged)
                        + " && chmod 0644 " + shQuote(staged)
                        + " && wc -c < " + shQuote(staged);
                MemoryToolRuntime.CmdResult stage = runBackendShellCommandCaptureSync(stageCmd);
                if (stage == null || stage.exitCode != 0) {
                    runOnUiThread(() -> appendStatus(summarizeResult("Import staging failed.", stage)));
                    return;
                }
                if (shouldAutoStage()) {
                    MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                    if (install == null || install.exitCode != 0) {
                        runOnUiThread(() -> appendStatus(summarizeResult("apk-medit could not be staged for the current backend.", install)));
                        return;
                    }
                }
                String effectivePid = targetPid;
                String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
                if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
                String shellCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                        pkg,
                        MemoryToolRuntime.PUBLIC_BIN_DIR,
                        withoutPtrace,
                        "write-file",
                        effectivePid,
                        null,
                        staged,
                        actualBegin,
                        null,
                        null,
                        getMaxScanResults());
                MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(shellCmd);
                boolean failed = isMemoryCommandFailure(r);
                runOnUiThread(() -> appendStatus(failed ? summarizeResult("Import/Replace failed.", r) : summarizeResult("Import/Replace finished.", r)));
            } finally {
                try { runBackendShellCommandCaptureSync("rm -f " + shQuote(staged)); } catch (Throwable ignored) {}
            }
        }, "MemoryOverlayImportReplace").start();
    }

    private void startTimerFinderBaseline() {
        if (timerFinderInFlight) {
            appendStatus("Timer Finder is already running.");
            return;
        }
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            appendStatus("Enter a target package before taking a Timer Finder baseline.");
            return;
        }
        final String targetPid = getSelectedPid();
        if (TextUtils.isEmpty(targetPid)) {
            appendStatus("No running PID for Timer Finder yet. Refresh processes or choose the running process from the dropdown.");
            refreshProcesses();
            return;
        }
        final int step = MemoryToolHelper.normalizeMaxResults(getMaxScanResults());
        if (step <= 0) {
            showCapZeroBlockedWarning();
            return;
        }
        final boolean autoRange = shouldAutoRangeMemorySearch();
        final int pageLimit = autoRange ? getAutoRangePageLimit() : 1;
        persistAutoRangePageLimitFromField(true);
        try { if (ddDataType != null) ddDataType.setText("dword", false); } catch (Throwable ignored) {}
        try { if (ddSearchMode != null) ddSearchMode.setText(MemoryToolHelper.SEARCH_MODE_DECREASED, false); } catch (Throwable ignored) {}
        stopFreezePatch(true);
        resetScanRangeSession();
        clearOverlayScanState(false);
        showCommandStartStatus("Timer Finder baseline: capturing dword snapshots"
                + (autoRange ? (pageLimit == 0 ? " across all range pages..." : " across up to " + pageLimit + " range pages...") : " for Range 1...")
                + "\nReturn to the app, let the timer change, then tap Find Timers.");
        setToolOverlaysBackendBusy(true);
        timerFinderInFlight = true;
        new Thread(() -> {
            int pagesCaptured = 0;
            int totalCaptured = 0;
            String failureMessage = null;
            String effectivePid = targetPid;
            HashMap<Integer, Integer> baselineCounts = new HashMap<>();
            try {
                if (shouldAutoStage()) {
                    MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                    if (install == null || install.exitCode != 0) {
                        failureMessage = summarizeResult("apk-medit could not be staged for the current backend.", install);
                        return;
                    }
                }
                String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
                if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
                for (int page = 0; pageLimit == 0 || page < pageLimit; page++) {
                    int skip = page * step;
                    MemoryToolRuntime.CmdResult baseline = runMemoryCommandCaptureSync(
                            pkg,
                            effectivePid,
                            withoutPtrace,
                            "snapshot",
                            "dword",
                            null,
                            step,
                            MemoryToolHelper.statePathForRange(page),
                            skip);
                    if (isMemoryCommandFailure(baseline)) {
                        failureMessage = summarizeResult("Timer Finder baseline failed on Range " + (page + 1) + ".", baseline);
                        break;
                    }
                    int count = Math.max(0, countFoundResultsFromOutput(baseline));
                    baselineCounts.put(page, count);
                    pagesCaptured++;
                    totalCaptured += count;
                    if (count < step) {
                        break;
                    }
                }
            } finally {
                final int finalPagesCaptured = pagesCaptured;
                final int finalTotalCaptured = totalCaptured;
                final String finalFailureMessage = failureMessage;
                final String finalEffectivePid = effectivePid;
                runOnUiThread(() -> {
                    timerFinderInFlight = false;
                    setToolOverlaysBackendBusy(false);
                    if (!TextUtils.isEmpty(finalFailureMessage)) {
                        appendStatus(finalFailureMessage);
                        return;
                    }
                    scanRangePageIndex = 0;
                    scanRangeStepResults = step;
                    scanRangeSkipResults = 0;
                    scanRangeBaseCommand = "snapshot";
                    scanRangeBaseDataType = "dword";
                    scanRangeBaseValue = null;
                    scanRangeBaseScanValue = null;
                    scanRangeResultCountByPage.clear();
                    scanRangeBaselineCountByPage.clear();
                    scanRangeResultCountByPage.putAll(baselineCounts);
                    scanRangeBaselineCountByPage.putAll(baselineCounts);
                    timerFinderBaselinePageCount = finalPagesCaptured;
                    timerFinderBaselineTotalCount = finalTotalCaptured;
                    timerFinderBaselinePackage = pkg;
                    timerFinderBaselinePid = finalEffectivePid;
                    hasActiveResultSet = finalTotalCaptured > 0;
                    activeResultCount = finalPagesCaptured > 0 && baselineCounts.containsKey(0) ? Math.max(0, baselineCounts.get(0)) : 0;
                    selectedResult = null;
                    resultItems.clear();
                    checkedPatchResultKeys.clear();
                    if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
                    updateScanRangeStatus();
                    updateResultListVisibility(activeResultCount);
                    appendStatus("Timer Finder baseline captured " + finalTotalCaptured + " dword address" + (finalTotalCaptured == 1 ? "" : "es")
                            + " across " + finalPagesCaptured + " range" + (finalPagesCaptured == 1 ? "" : "s")
                            + ". Return to the app, let the timer change, then tap Find Timers.");
                    releaseOverlayInputFocus();
                    if (root != null) root.postDelayed(this::releaseOverlayInputFocus, 120L);
                });
            }
        }, "MemoryOverlayTimerBaseline").start();
    }

    private void findTimerCandidates() {
        if (timerFinderInFlight) {
            appendStatus("Timer Finder is already running.");
            return;
        }
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            appendStatus("Enter a target package before finding timers.");
            return;
        }
        final String targetPid = getSelectedPid();
        if (TextUtils.isEmpty(targetPid)) {
            appendStatus("No running PID for Timer Finder yet. Refresh processes or choose the running process from the dropdown.");
            refreshProcesses();
            return;
        }
        if (!hasTimerFinderBaselineFor(pkg)) {
            appendStatus("Timer Finder needs its own baseline first. Tap Timer Baseline, return to the app, let the timer change, then tap Find Timers.");
            return;
        }
        final int pageCount = timerFinderBaselinePageCount;
        final int step = getScanRangeStepResults();
        if (pageCount <= 0 || step <= 0) {
            appendStatus("Timer Finder baseline is incomplete. Tap Timer Baseline again before Find Timers.");
            return;
        }
        try { if (ddDataType != null) ddDataType.setText("dword", false); } catch (Throwable ignored) {}
        try { if (ddSearchMode != null) ddSearchMode.setText(MemoryToolHelper.SEARCH_MODE_DECREASED, false); } catch (Throwable ignored) {}
        showCommandStartStatus("Timer Finder: comparing " + pageCount + " saved dword baseline range"
                + (pageCount == 1 ? "" : "s") + " for decreased values...");
        setToolOverlaysBackendBusy(true);
        timerFinderInFlight = true;
        new Thread(() -> {
            int pagesCompared = 0;
            int totalMatches = 0;
            int firstNonEmptyPage = -1;
            String failureMessage = null;
            String effectivePid = targetPid;
            HashMap<Integer, Integer> resultCounts = new HashMap<>();
            try {
                if (shouldAutoStage()) {
                    MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                    if (install == null || install.exitCode != 0) {
                        failureMessage = summarizeResult("apk-medit could not be staged for the current backend.", install);
                        return;
                    }
                }
                String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
                if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
                if (!TextUtils.isEmpty(timerFinderBaselinePid) && !TextUtils.equals(timerFinderBaselinePid, effectivePid)) {
                    failureMessage = "Timer Finder baseline was captured for PID " + timerFinderBaselinePid
                            + ", but the selected process is now PID " + effectivePid
                            + ". Tap Timer Baseline again for the current process.";
                    return;
                }
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
                for (int page = 0; page < pageCount; page++) {
                    MemoryToolRuntime.CmdResult filtered = runMemoryCommandCaptureSync(
                            pkg,
                            effectivePid,
                            withoutPtrace,
                            "filter-lt",
                            null,
                            null,
                            step,
                            MemoryToolHelper.statePathForRange(page),
                            0);
                    if (isMemoryCommandFailure(filtered)) {
                        failureMessage = summarizeResult("Timer Finder failed on Range " + (page + 1) + ".", filtered);
                        break;
                    }
                    int count = Math.max(0, countFoundResultsFromOutput(filtered));
                    resultCounts.put(page, count);
                    pagesCompared++;
                    totalMatches += count;
                    if (count > 0 && firstNonEmptyPage < 0) firstNonEmptyPage = page;
                }
            } finally {
                final int finalPagesCompared = pagesCompared;
                final int finalTotalMatches = totalMatches;
                final int finalFirstNonEmptyPage = firstNonEmptyPage;
                final String finalFailureMessage = failureMessage;
                runOnUiThread(() -> {
                    timerFinderInFlight = false;
                    setToolOverlaysBackendBusy(false);
                    if (!TextUtils.isEmpty(finalFailureMessage)) {
                        appendStatus(finalFailureMessage);
                        return;
                    }
                    scanRangeResultCountByPage.clear();
                    scanRangeResultCountByPage.putAll(resultCounts);
                    int displayPage = finalFirstNonEmptyPage >= 0 ? finalFirstNonEmptyPage : 0;
                    scanRangePageIndex = Math.max(0, displayPage);
                    scanRangeSkipResults = currentScanRangeSkipResults();
                    updateScanRangeStatus();
                    int displayCount = Math.max(0, resultCounts.containsKey(scanRangePageIndex) ? resultCounts.get(scanRangePageIndex) : 0);
                    if (displayCount > 0 && shouldLoadStateForVisibleRows(displayCount)) {
                        String stateJson = readMemoryStateJson(pkg, currentScanRangeStatePath());
                        updateResultListFromState(stateJson, null);
                    } else {
                        updateResultCountOnly(displayCount);
                    }
                    String msg = "Timer Finder compared " + finalPagesCompared + " baseline range"
                            + (finalPagesCompared == 1 ? "" : "s") + "; decreased candidates: " + finalTotalMatches + ".";
                    if (finalFirstNonEmptyPage >= 0) {
                        msg += " Showing " + formatScanRangeName() + "; use Prev/Next to inspect other captured ranges.";
                    } else {
                        msg += " If the timer increased instead of decreased, take a new baseline and use normal Increased/Changed scan modes.";
                    }
                    appendStatus(msg);
                    releaseOverlayInputFocus();
                    if (root != null) root.postDelayed(this::releaseOverlayInputFocus, 120L);
                });
            }
        }, "MemoryOverlayTimerFind").start();
    }

    private boolean hasTimerFinderBaselineFor(String packageName) {
        return timerFinderBaselinePageCount > 0
                && !TextUtils.isEmpty(packageName)
                && TextUtils.equals(packageName, timerFinderBaselinePackage)
                && TextUtils.equals(scanRangeBaseCommand, "snapshot")
                && TextUtils.equals(scanRangeBaseDataType, "dword");
    }

    private boolean isTimerFinderRangeSession() {
        return timerFinderBaselinePageCount > 0
                && TextUtils.equals(scanRangeBaseCommand, "snapshot")
                && TextUtils.equals(scanRangeBaseDataType, "dword");
    }

    private void clearTimerFinderBaselineTracking() {
        timerFinderBaselinePageCount = 0;
        timerFinderBaselineTotalCount = 0;
        timerFinderBaselinePackage = null;
        timerFinderBaselinePid = null;
    }

    private boolean isFreezePatchChecked() {
        return freezePatchActive;
    }

    private boolean shouldRefreshFreezePatchForTarget(@Nullable MemoryResultRow patchTarget) {
        if (!freezePatchActive || patchTarget == null) return false;
        return freezePatchResultKeys.contains(resultKey(patchTarget));
    }

    private void setFreezeControlChecked(boolean checked) {
        try {
            if (chkFreezePatch != null) chkFreezePatch.setChecked(checked);
        } catch (Throwable ignored) {
        }
    }

    private boolean startFreezePatchFromInputs() {
        MemoryResultRow patchTarget = resolvePatchTargetRow();
        return startFreezePatchForRow(patchTarget);
    }

    private boolean startFreezePatchForRow(@Nullable MemoryResultRow patchTarget) {
        if (patchTarget == null) {
            appendStatus("Select a result/address before starting Lock/Freeze.");
            return false;
        }
        ArrayList<MemoryResultRow> rows = singleRowList(patchTarget);
        return startFreezePatchForRows(rows, patchTarget, false);
    }

    private void freezeCheckedResults() {
        ArrayList<MemoryResultRow> rows = getCheckedPatchRows();
        if (rows.isEmpty()) {
            appendStatus("Check one or more visible result rows before using Freeze.");
            return;
        }
        if (!arePatchRowsCompatible(rows)) {
            appendStatus("Freeze requires checked rows with the same data type. Uncheck mixed result types first.");
            return;
        }
        startFreezePatchForRows(rows, rows.get(0), true);
    }

    private boolean startFreezePatchForRows(@Nullable ArrayList<MemoryResultRow> rows,
                                            @Nullable MemoryResultRow primaryRow,
                                            boolean multipleRows) {
        if (rows == null || rows.isEmpty() || primaryRow == null) {
            appendStatus("Select a result/address before starting Lock/Freeze.");
            return false;
        }

        String typedPatchValue = editTextValue(edtPatchValue);
        boolean keepTypedValue = (multipleRows || isCurrentPatchTarget(primaryRow)) && !TextUtils.isEmpty(typedPatchValue);
        String patchValue = keepTypedValue ? typedPatchValue : primaryRow.value;

        selectMemoryResult(primaryRow);
        if (!TextUtils.isEmpty(patchValue)) {
            setEditTextValue(edtPatchValue, patchValue);
        } else {
            patchValue = editTextValue(edtPatchValue);
        }

        if (isPatchHexEnabled() && isNumericPatchDataType(primaryRow.dataType)) {
            patchValue = normalizeHexPatchValueForTarget(patchValue, primaryRow);
            if (patchValue == null) return false;
        } else if (isStringPatchDataType(primaryRow.dataType)) {
            patchValue = normalizeStringPatchValueForTarget(patchValue, primaryRow);
            if (patchValue == null) return false;
        }

        String stateOverride = buildResultStateJson(rows);
        if (TextUtils.isEmpty(stateOverride)) {
            appendStatus("Lock/Freeze could not build a safe selected-address state.");
            return false;
        }

        prepareFreezePatch(rows, patchValue, stateOverride);
        setFreezeControlChecked(true);
        if (multipleRows) {
            appendStatus("Lock/Freeze enabled for " + rows.size() + " checked result" + (rows.size() == 1 ? "" : "s") + ". Long-press a frozen row to stop only that address.");
        } else {
            appendStatus("Lock/Freeze enabled for " + formatHex(primaryRow.address) + ". Long-press that row to stop only that address.");
        }
        scheduleFreezePatch(freezePatchToken, 0L);
        return true;
    }

    private boolean isCurrentPatchTarget(@Nullable MemoryResultRow row) {
        if (row == null) return false;
        if (selectedResult != null && selectedResult.address == row.address) return true;
        if (edtPatchAddress == null || edtPatchAddress.getText() == null) return false;
        String rawAddress = edtPatchAddress.getText().toString().trim();
        if (TextUtils.isEmpty(rawAddress)) return false;
        try {
            return parseFlexibleAddress(rawAddress) == row.address;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void prepareFreezePatch(@Nullable List<MemoryResultRow> patchTargets, @Nullable String patchValue, @Nullable String stateOverride) {
        if (patchTargets == null || patchTargets.isEmpty() || TextUtils.isEmpty(stateOverride)) return;
        freezePatchValue = patchValue == null ? "" : patchValue;
        freezePatchStateJson = stateOverride;
        freezePatchResultKeys.clear();
        for (MemoryResultRow row : patchTargets) {
            String key = resultKey(row);
            if (!TextUtils.isEmpty(key)) freezePatchResultKeys.add(key);
        }
        freezePatchResultKey = freezePatchResultKeys.isEmpty() ? null : freezePatchResultKeys.iterator().next();
        freezePatchActive = !freezePatchResultKeys.isEmpty();
        freezePatchToken++;
        lastFreezePatchErrorUptime = 0L;
    }

    private void stopFreezePatch(boolean updateCheckbox) {
        boolean wasActive = freezePatchActive;
        freezePatchActive = false;
        freezePatchToken++;
        freezePatchValue = null;
        freezePatchStateJson = null;
        freezePatchResultKey = null;
        freezePatchResultKeys.clear();
        if (updateCheckbox) {
            setFreezeControlChecked(false);
        }
        if (wasActive) {
            appendStatus("Lock/Freeze stopped.");
        }
    }

    private void stopFreezePatchForRow(@Nullable MemoryResultRow row) {
        if (row == null || !freezePatchActive) return;
        String key = resultKey(row);
        if (TextUtils.isEmpty(key) || !freezePatchResultKeys.contains(key)) return;
        freezePatchResultKeys.remove(key);
        ArrayList<MemoryResultRow> remainingRows = frozenRowsFromCurrentResults();
        if (remainingRows.isEmpty()) {
            stopFreezePatch(true);
            return;
        }
        String stateOverride = buildResultStateJson(remainingRows);
        if (TextUtils.isEmpty(stateOverride)) {
            stopFreezePatch(true);
            return;
        }
        freezePatchStateJson = stateOverride;
        freezePatchResultKey = freezePatchResultKeys.iterator().next();
        freezePatchToken++;
        appendStatus("Lock/Freeze stopped for " + formatHex(row.address) + ". " + remainingRows.size() + " frozen result" + (remainingRows.size() == 1 ? "" : "s") + " remain.");
        scheduleFreezePatch(freezePatchToken, 0L);
    }

    private ArrayList<MemoryResultRow> frozenRowsFromCurrentResults() {
        ArrayList<MemoryResultRow> rows = new ArrayList<>();
        if (freezePatchResultKeys.isEmpty()) return rows;
        for (MemoryResultRow row : resultItems) {
            if (row != null && freezePatchResultKeys.contains(resultKey(row))) rows.add(row);
        }
        freezePatchResultKeys.clear();
        for (MemoryResultRow row : rows) {
            String key = resultKey(row);
            if (!TextUtils.isEmpty(key)) freezePatchResultKeys.add(key);
        }
        return rows;
    }

    private void scheduleFreezePatch(int token, long delayMs) {
        if (root == null || !freezePatchActive || token != freezePatchToken) return;
        root.postDelayed(() -> runFreezePatchTick(token), Math.max(0L, delayMs));
    }

    private void runFreezePatchTick(final int token) {
        if (!freezePatchActive || token != freezePatchToken) return;
        if (freezePatchInFlight) {
            scheduleFreezePatch(token, 250L);
            return;
        }
        final String pkg = getTargetPackage();
        final String targetPid = getSelectedPid();
        final String patchValue = freezePatchValue;
        final String stateJson = freezePatchStateJson;
        if (TextUtils.isEmpty(pkg) || patchValue == null || TextUtils.isEmpty(stateJson)) {
            stopFreezePatch(true);
            return;
        }
        if (TextUtils.isEmpty(targetPid)) {
            maybeReportFreezeError("Lock/Freeze paused: no running PID for the target package.");
            refreshProcesses();
            scheduleFreezePatch(token, 1500L);
            return;
        }
        freezePatchInFlight = true;
        new Thread(() -> {
            try {
                if (shouldAutoStage()) {
                    MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                    if (install == null || install.exitCode != 0) {
                        runOnUiThread(() -> maybeReportFreezeError(summarizeResult("Lock/Freeze stage failed.", install)));
                        return;
                    }
                }
                String effectivePid = targetPid;
                String resolvedPid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, targetPid);
                if (!TextUtils.isEmpty(resolvedPid)) effectivePid = resolvedPid;
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
                String shellCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                        pkg,
                        MemoryToolRuntime.PUBLIC_BIN_DIR,
                        withoutPtrace,
                        "patch",
                        effectivePid,
                        null,
                        patchValue,
                        null,
                        null,
                        stateJson,
                        getMaxScanResults(),
                        shouldStringCaseSensitive(),
                        shouldStringPatchTruncate(),
                        0
                );
                MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(shellCmd);
                if (isMemoryCommandFailure(r)) {
                    String message = summarizeResult("Lock/Freeze patch failed.", r);
                    runOnUiThread(() -> {
                        maybeReportFreezeError(message);
                        if (isSegmentationFaultMessage(message)) {
                            appendStatus("Lock/Freeze stopped after medit reported a segmentation fault for that address/value.");
                            stopFreezePatch(true);
                        }
                    });
                }
            } finally {
                runOnUiThread(() -> {
                    freezePatchInFlight = false;
                    scheduleFreezePatch(token, 1000L);
                });
            }
        }, "MemoryOverlayFreezePatch").start();
    }

    private boolean isSegmentationFaultMessage(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.US);
        return lower.contains("segmentation fault") || lower.contains("sigsegv");
    }

    private void maybeReportFreezeError(String message) {
        long now = SystemClock.uptimeMillis();
        if (now - lastFreezePatchErrorUptime < 5000L) return;
        lastFreezePatchErrorUptime = now;
        appendStatus(message);
    }

    private boolean shouldSaveDumpToDisk() {
        try {
            return chkDumpToDisk != null && chkDumpToDisk.isChecked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String saveDumpTextToDisk(String pkg, String begin, String end, String dumpText) {
        if (TextUtils.isEmpty(dumpText)) {
            return "Dump-to-disk skipped: dump output was empty.";
        }
        String safePkg = sanitizeFilePart(pkg);
        String safeBegin = sanitizeFilePart(begin);
        String safeEnd = sanitizeFilePart(end);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "memory_dump_" + safePkg + "_" + safeBegin + "_" + safeEnd + "_" + stamp + ".txt";
        File finalDir = new File(MEMORY_DUMP_DIR);
        File finalFile = new File(finalDir, name);
        try {
            if ((!finalDir.exists() && !finalDir.mkdirs()) || !finalDir.isDirectory()) {
                throw new IllegalStateException("Cannot create dump directory");
            }
            writeTextFile(finalFile, dumpText);
            return "Dump saved: " + finalFile.getAbsolutePath();
        } catch (Throwable directError) {
            try {
                File stagingRoot = getExternalFilesDir(null);
                if (stagingRoot == null) stagingRoot = getFilesDir();
                File stagingDir = new File(stagingRoot, "memory_dump_stage");
                if ((!stagingDir.exists() && !stagingDir.mkdirs()) || !stagingDir.isDirectory()) {
                    throw new IllegalStateException("Cannot create staging directory");
                }
                File staged = new File(stagingDir, name);
                writeTextFile(staged, dumpText);
                String cmd = "mkdir -p " + MemoryToolRuntime.shQuote(MEMORY_DUMP_DIR)
                        + " && cp " + MemoryToolRuntime.shQuote(staged.getAbsolutePath())
                        + " " + MemoryToolRuntime.shQuote(finalFile.getAbsolutePath())
                        + " && chmod 666 " + MemoryToolRuntime.shQuote(finalFile.getAbsolutePath());
                MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(cmd);
                if (r != null && r.exitCode == 0) {
                    return "Dump saved: " + finalFile.getAbsolutePath();
                }
                String msg = r == null ? "shell copy failed" : collectCommandOutput(r);
                return "Dump saved only to app staging: " + staged.getAbsolutePath() + (TextUtils.isEmpty(msg) ? "" : "\nDisk copy failed: " + msg);
            } catch (Throwable fallbackError) {
                return "Dump-to-disk failed: " + fallbackError.getClass().getSimpleName() + ": " + fallbackError.getMessage();
            }
        }
    }


    private String saveDumpBytesToDisk(String pkg, String begin, String end, String extension, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "Export skipped: dump output was empty.";
        String safePkg = sanitizeFilePart(pkg);
        String safeBegin = sanitizeFilePart(begin);
        String safeEnd = sanitizeFilePart(end);
        String safeExt = sanitizeFileExtension(extension);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "memory_export_" + safePkg + "_" + safeBegin + "_" + safeEnd + "_" + stamp + "." + safeExt;
        File finalFile = new File(MEMORY_DUMP_DIR, name);
        try {
            File dir = finalFile.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create export directory");
            writeBytesFile(finalFile, bytes);
            return "Export saved: " + finalFile.getAbsolutePath() + " (" + bytes.length + " bytes)";
        } catch (Throwable directError) {
            try {
                File stagingRoot = getExternalFilesDir(null);
                if (stagingRoot == null) throw directError;
                File stagingDir = new File(stagingRoot, "memory_export_stage");
                if (!stagingDir.exists() && !stagingDir.mkdirs()) throw directError;
                File staged = new File(stagingDir, name);
                writeBytesFile(staged, bytes);
                String cmd = "mkdir -p " + shQuote(MEMORY_DUMP_DIR) + " && cp -f " + shQuote(staged.getAbsolutePath()) + " " + shQuote(finalFile.getAbsolutePath()) + " && chmod 0666 " + shQuote(finalFile.getAbsolutePath());
                MemoryToolRuntime.CmdResult cp = runBackendShellCommandCaptureSync(cmd);
                if (cp != null && cp.exitCode == 0) { try { staged.delete(); } catch (Throwable ignored) {} return "Export saved: " + finalFile.getAbsolutePath() + " (" + bytes.length + " bytes)"; }
                return summarizeResult("Export save failed.", cp);
            } catch (Throwable fallbackError) {
                return "Export save failed: " + fallbackError.getClass().getSimpleName() + ": " + fallbackError.getMessage();
            }
        }
    }

    private void writeBytesFile(File file, byte[] bytes) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(file, false)) { out.write(bytes == null ? new byte[0] : bytes); }
    }

    private String exportMemoryRangeDirectToDiskSync(String pkg, String pid, long begin, long end, String extension) {
        if (TextUtils.isEmpty(pkg)) return "Export failed: target package is empty.";
        if (TextUtils.isEmpty(pid)) return "Export failed: target PID is empty.";
        if (end <= begin) return "Export failed: invalid range.";
        long length = end - begin;
        if (length > FILETYPE_SCAN_READ_LIMIT_BYTES) return "Export failed: range is larger than the file-type export safety limit.";

        String safePkg = sanitizeFilePart(pkg);
        String safeBegin = sanitizeFilePart(formatHex(begin));
        String safeEnd = sanitizeFilePart(formatHex(end));
        String safeExt = sanitizeFileExtension(extension);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        String name = "memory_export_" + safePkg + "_" + safeBegin + "_" + safeEnd + "_" + stamp + "." + safeExt;
        File finalFile = new File(MEMORY_DUMP_DIR, name);
        String targetTemp = "./.permstest_export_" + Long.toHexString(System.nanoTime()) + ".tmp";
        try {
            String dumpError = dumpMemoryRangeToTargetTempSync(pkg, pid, begin, end, targetTemp);
            if (!TextUtils.isEmpty(dumpError)) {
                cleanupTargetTempExport(pkg, targetTemp);
                return dumpError;
            }

            if (length <= FILETYPE_BASE64_CAPTURE_LIMIT_BYTES) {
                DumpBytesResult captured = readTargetTempBytesBase64Sync(pkg, targetTemp, length);
                cleanupTargetTempExport(pkg, targetTemp);
                if (captured == null || !captured.success || captured.bytes == null || captured.bytes.length == 0) {
                    return "Export failed: " + (captured == null ? "no bytes were captured from target temp file" : captured.message);
                }
                String saved = saveDumpBytesToDisk(pkg, formatHex(begin), formatHex(end), safeExt, captured.bytes);
                return saved;
            }

            String finalPath = finalFile.getAbsolutePath();
            MemoryToolRuntime.CmdResult prep = runBackendShellCommandCaptureSync(
                    "mkdir -p " + shQuote(MEMORY_DUMP_DIR));
            if (prep == null || prep.exitCode != 0) {
                cleanupTargetTempExport(pkg, targetTemp);
                return summarizeResult("Export prep failed.", prep);
            }
            String copyCmd = "run-as " + shQuote(pkg) + " sh -c " + shQuote("cat " + shQuote(targetTemp))
                    + " > " + shQuote(finalPath)
                    + " && chmod 0666 " + shQuote(finalPath)
                    + " && wc -c < " + shQuote(finalPath);
            MemoryToolRuntime.CmdResult copy = runBackendShellCommandCaptureSync(copyCmd);
            cleanupTargetTempExport(pkg, targetTemp);
            if (copy == null || copy.exitCode != 0) return summarizeResult("Export save failed.", copy);
            long actual = parseFirstLong(copy.stdout);
            if (actual <= 0L) return "Export failed: output file was empty: " + finalPath;
            return "Export saved: " + finalPath + " (" + actual + " bytes)";
        } catch (Throwable t) {
            cleanupTargetTempExport(pkg, targetTemp);
            return "Export failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String dumpMemoryRangeToTargetTempSync(String pkg, String pid, long begin, long end, String targetTemp) {
        if (TextUtils.isEmpty(pkg)) return "Export failed: target package is empty.";
        if (TextUtils.isEmpty(pid)) return "Export failed: target PID is empty.";
        if (TextUtils.isEmpty(targetTemp)) return "Export failed: target temp path is empty.";
        try {
            if (shouldAutoStage()) {
                MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                if (install == null || install.exitCode != 0) return summarizeResult("apk-medit could not be staged for the current backend.", install);
            }
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
            String shellCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                    pkg,
                    MemoryToolRuntime.PUBLIC_BIN_DIR,
                    withoutPtrace,
                    "dump-file",
                    pid,
                    null,
                    targetTemp,
                    formatHex(begin),
                    formatHex(end),
                    null,
                    getMaxScanResults());
            MemoryToolRuntime.CmdResult dump = runBackendShellCommandCaptureSync(shellCmd);
            if (isMemoryCommandFailure(dump)) return summarizeResult("Export failed.", dump);
            return null;
        } catch (Throwable t) {
            return "Export failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private DumpBytesResult readTargetTempBytesBase64Sync(String pkg, String targetTemp, long expectedLength) {
        if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(targetTemp)) return new DumpBytesResult(false, "target temp path is empty", new byte[0]);
        if (expectedLength > FILETYPE_BASE64_CAPTURE_LIMIT_BYTES) {
            return new DumpBytesResult(false, "range is too large for direct byte capture", new byte[0]);
        }
        String script = "if command -v base64 >/dev/null 2>&1; then base64 " + shQuote(targetTemp)
                + "; else toybox base64 " + shQuote(targetTemp) + "; fi";
        String cmd = "run-as " + shQuote(pkg) + " sh -c " + shQuote(script);
        MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(cmd);
        if (r == null || r.exitCode != 0) return new DumpBytesResult(false, summarizeResult("target byte capture failed.", r), new byte[0]);
        String encoded = r.stdout == null ? "" : r.stdout.replaceAll("\\s+", "");
        if (TextUtils.isEmpty(encoded)) return new DumpBytesResult(false, "target byte capture returned no data", new byte[0]);
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            if (bytes == null || bytes.length == 0) return new DumpBytesResult(false, "decoded byte capture was empty", new byte[0]);
            return new DumpBytesResult(true, "", bytes);
        } catch (Throwable t) {
            return new DumpBytesResult(false, "base64 decode failed: " + t.getMessage(), new byte[0]);
        }
    }

    private static long parseFirstLong(String text) {
        if (TextUtils.isEmpty(text)) return -1L;
        String[] parts = text.trim().split("\\s+");
        for (String part : parts) {
            try { return Long.parseLong(part.trim()); } catch (Throwable ignored) {}
        }
        return -1L;
    }

    private void cleanupTargetTempExport(String pkg, String tempPath) {
        if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(tempPath)) return;
        try {
            runBackendShellCommandCaptureSync(
                    "run-as " + shQuote(pkg) + " sh -c " + shQuote("rm -f " + shQuote(tempPath)));
        } catch (Throwable ignored) {
        }
    }

    private static byte[] parseHexDumpBytesForExport(String dump) {
        ArrayList<Byte> out = new ArrayList<>();
        if (dump == null) return new byte[0];
        String[] lines = dump.split("\r?\n");
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if (TextUtils.isEmpty(t) || t.startsWith("Attached TID") || t.startsWith("Detached TID") || t.startsWith("Target PID")) continue;
            int ascii = t.indexOf('|');
            String hexPart = ascii >= 0 ? t.substring(0, ascii) : t;
            hexPart = hexPart.replace(':', ' ');
            String[] parts = hexPart.trim().split("\s+");
            if (parts.length < 2 || !isDumpAddressToken(parts[0])) continue;
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i] == null ? "" : parts[i].trim();
                if (!part.matches("[0-9a-fA-F]{2}")) continue;
                try { out.add((byte) Integer.parseInt(part, 16)); } catch (Throwable ignored) {}
            }
        }
        byte[] bytes = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) bytes[i] = out.get(i);
        return bytes;
    }

    private static boolean isDumpAddressToken(String token) {
        if (token == null) return false;
        String t = token.trim();
        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        return t.matches("[0-9a-fA-F]{6,16}");
    }

    private static String sanitizeFileExtension(String extension) {
        String value = extension == null ? "" : extension.trim().toLowerCase(Locale.US);
        if (value.startsWith(".")) value = value.substring(1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.append(c);
        }
        return out.length() == 0 ? "bin" : out.toString();
    }

    private void saveMemoryPatchSet() {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            showPatchSaveStatus("Select a target package before saving patches.");
            return;
        }
        reconcileVisiblePatchResultChecks();
        if (getCheckedPatchRows().isEmpty()) {
            showPatchSaveStatus("Check one or more patch rows before saving patches.");
            return;
        }
        try {
            rememberCurrentPatchName();
            File finalFile = memoryPatchFileForPackage(pkg);
            JSONObject patchSet = buildMemoryPatchSetJson(pkg, countExistingPatches(finalFile));
            int newCount = patchSet.optJSONArray("patches") == null ? 0 : patchSet.optJSONArray("patches").length();
            if (newCount <= 0) {
                showPatchSaveStatus("No checked patch rows were saved.");
                return;
            }
            JSONObject appended = appendMemoryPatchSetJson(pkg, finalFile, patchSet);
            int totalCount = appended.optJSONArray("patches") == null ? 0 : appended.optJSONArray("patches").length();
            String result = savePatchTextToDisk(finalFile, appended.toString(2));
            if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
            updateSelectedResultLabel(selectedResult, activeResultCount);
            showPatchSaveStatus(result + "\nAppended " + newCount + " checked patch" + (newCount == 1 ? "" : "es")
                    + ". Total saved patches: " + totalCount + ".");
        } catch (Throwable t) {
            showPatchSaveStatus("Save patches failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void loadMemoryPatchSet() {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            appendStatus("Select the target package before loading patches.");
            return;
        }
        File file = memoryPatchFileForPackage(pkg);
        try {
            String text = readPatchTextFromDisk(file);
            if (TextUtils.isEmpty(text)) {
                appendStatus("Patch file is empty: " + file.getAbsolutePath());
                return;
            }
            int loaded = applyMemoryPatchSetJson(new JSONObject(text));
            appendStatus("Loaded patches: " + loaded + " from " + file.getAbsolutePath());
        } catch (Throwable t) {
            appendStatus("Load patches failed: " + file.getAbsolutePath() + "\n" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private JSONObject buildMemoryPatchSetJson(String pkg, int existingPatchCount) throws Exception {
        JSONObject rootJson = new JSONObject();
        String savedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String patchValue = editTextValue(edtPatchValue);
        boolean patchHex = isPatchHexEnabled();
        boolean truncateStringPatches = shouldStringPatchTruncate();
        boolean caseSensitive = shouldStringCaseSensitive();
        rootJson.put("schema", "perms_test_memory_patches");
        rootJson.put("package", pkg);
        rootJson.put("title", findPackageLabel(pkg));
        rootJson.put("process", ddProcess == null || ddProcess.getText() == null ? "" : ddProcess.getText().toString());
        rootJson.put("pid", getSelectedPid() == null ? "" : getSelectedPid());
        rootJson.put("saved_at", savedAt);
        rootJson.put("patch_value", patchValue);
        rootJson.put("patch_hex", patchHex);
        rootJson.put("string_patch_truncate", truncateStringPatches);
        rootJson.put("case_sensitive", caseSensitive);

        JSONArray patches = new JSONArray();
        ArrayList<MemoryResultRow> checkedRows = getCheckedPatchRows();
        boolean singleCheckedRow = checkedRows.size() == 1;
        String selectedKey = resolvePatchSaveSelectedKey(singleCheckedRow ? checkedRows.get(0) : null);
        for (MemoryResultRow row : checkedRows) {
            if (row == null) continue;
            String key = resultKey(row);
            JSONObject item = new JSONObject();
            int patchNumber = existingPatchCount + patches.length() + 1;
            item.put("name", resolvePatchNameForSave(row, patchNumber, selectedKey, singleCheckedRow));
            item.put("address", Long.toString(row.address));
            item.put("address_hex", formatHex(row.address));
            item.put("data_type", row.dataType);
            item.put("converter", row.converterId);
            item.put("value", row.value);
            item.put("patch_value", patchValue);
            item.put("patch_hex", patchHex);
            item.put("string_patch_truncate", truncateStringPatches);
            item.put("case_sensitive", caseSensitive);
            item.put("selected", TextUtils.equals(selectedKey, key));
            item.put("freeze", freezePatchActive && freezePatchResultKeys.contains(key));
            item.put("saved_at", savedAt);
            patches.put(item);
        }
        rootJson.put("patches", patches);
        return rootJson;
    }

    private int countExistingPatches(File finalFile) {
        try {
            if (finalFile == null || !finalFile.exists()) return 0;
            String text = readPatchTextFromDisk(finalFile);
            if (TextUtils.isEmpty(text)) return 0;
            JSONObject existing = new JSONObject(text);
            if (!TextUtils.equals(existing.optString("schema", ""), "perms_test_memory_patches")) {
                throw new IllegalStateException("Invalid patch file schema");
            }
            JSONArray patches = existing.optJSONArray("patches");
            return patches == null ? 0 : patches.length();
        } catch (Throwable t) {
            return 0;
        }
    }

    private JSONObject appendMemoryPatchSetJson(String pkg, File finalFile, JSONObject newPatchSet) throws Exception {
        JSONObject out = null;
        if (finalFile != null && finalFile.exists()) {
            String existingText = readPatchTextFromDisk(finalFile);
            if (!TextUtils.isEmpty(existingText)) {
                out = new JSONObject(existingText);
                if (!TextUtils.equals(out.optString("schema", ""), "perms_test_memory_patches")) {
                    throw new IllegalStateException("Invalid patch file schema");
                }
                String existingPackage = out.optString("package", "").trim();
                if (!TextUtils.isEmpty(existingPackage) && !TextUtils.equals(existingPackage, pkg)) {
                    throw new IllegalStateException("Patch file package mismatch");
                }
            }
        }
        if (out == null) {
            out = new JSONObject();
            out.put("schema", "perms_test_memory_patches");
            out.put("package", pkg);
            out.put("title", findPackageLabel(pkg));
            out.put("created_at", newPatchSet.optString("saved_at", ""));
            out.put("patches", new JSONArray());
        }
        out.put("title", newPatchSet.optString("title", out.optString("title", "")));
        out.put("process", newPatchSet.optString("process", ""));
        out.put("pid", newPatchSet.optString("pid", ""));
        out.put("saved_at", newPatchSet.optString("saved_at", ""));
        out.put("patch_value", newPatchSet.optString("patch_value", ""));
        out.put("patch_hex", newPatchSet.optBoolean("patch_hex", false));
        out.put("string_patch_truncate", newPatchSet.optBoolean("string_patch_truncate", true));
        out.put("case_sensitive", newPatchSet.optBoolean("case_sensitive", false));

        JSONArray existingRows = out.optJSONArray("patches");
        if (existingRows == null) {
            existingRows = new JSONArray();
            out.put("patches", existingRows);
        }
        JSONArray newRows = newPatchSet.optJSONArray("patches");
        if (newRows != null) {
            for (int i = 0; i < newRows.length(); i++) {
                JSONObject row = newRows.optJSONObject(i);
                if (row != null) existingRows.put(row);
            }
        }
        return out;
    }

    private void rememberCurrentPatchName() {
        MemoryResultRow row = resolvePatchNameTargetRow();
        if (row == null) return;
        String name = editTextValue(edtPatchName).trim();
        String key = resultKey(row);
        if (TextUtils.isEmpty(key)) return;
        if (TextUtils.isEmpty(name)) {
            patchNameByKey.remove(key);
        } else {
            patchNameByKey.put(key, name);
        }
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        updateSelectedResultLabel(selectedResult, activeResultCount);
    }

    private void installPatchNameWatcher() {
        if (edtPatchName == null) return;
        edtPatchName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                rememberCurrentPatchName();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    @Nullable
    private MemoryResultRow resolvePatchNameTargetRow() {
        if (selectedResult != null) return selectedResult;
        MemoryResultRow addressRow = findResultRowForPatchAddress();
        if (addressRow != null) return addressRow;
        ArrayList<MemoryResultRow> checkedRows = getCheckedPatchRows();
        return checkedRows.size() == 1 ? checkedRows.get(0) : null;
    }

    private String resolvePatchSaveSelectedKey(@Nullable MemoryResultRow singleCheckedRow) {
        String selectedKey = selectedResult == null ? "" : resultKey(selectedResult);
        if (!TextUtils.isEmpty(selectedKey) && checkedPatchResultKeys.contains(selectedKey)) {
            return selectedKey;
        }
        String addressKey = resultKeyForPatchAddress();
        if (!TextUtils.isEmpty(addressKey) && checkedPatchResultKeys.contains(addressKey)) {
            return addressKey;
        }
        return singleCheckedRow == null ? "" : resultKey(singleCheckedRow);
    }

    private String resultKeyForPatchAddress() {
        MemoryResultRow row = findResultRowForPatchAddress();
        return row == null ? "" : resultKey(row);
    }

    @Nullable
    private MemoryResultRow findResultRowForPatchAddress() {
        if (edtPatchAddress == null || edtPatchAddress.getText() == null) return null;
        String raw = edtPatchAddress.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) return null;
        try {
            long address = parseFlexibleAddress(raw);
            for (MemoryResultRow row : resultItems) {
                if (row != null && row.address == address) return row;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String resolvePatchNameForSave(MemoryResultRow row, int patchNumber, String selectedKey, boolean singleCheckedRow) {
        String key = resultKey(row);
        String name = patchNameByKey.get(key);
        String typedName = editTextValue(edtPatchName).trim();
        if (TextUtils.isEmpty(name) && (singleCheckedRow || TextUtils.equals(key, selectedKey))) {
            name = typedName;
        }
        if (TextUtils.isEmpty(name)) name = "Patch " + Math.max(1, patchNumber);
        patchNameByKey.put(key, name);
        return name;
    }

    private int applyMemoryPatchSetJson(JSONObject patchSet) {
        if (patchSet == null) return 0;
        String schema = patchSet.optString("schema", "").trim();
        if (!TextUtils.equals(schema, "perms_test_memory_patches")) {
            appendStatus("Invalid patch file schema: " + (TextUtils.isEmpty(schema) ? "missing" : schema));
            return 0;
        }
        String pkg = patchSet.optString("package", getTargetPackage()).trim();
        if (!TextUtils.isEmpty(pkg)) {
            ddTargetPkg.setText(pkg, false);
            ensurePackageEntryPresent(pkg, false);
            if (packageAdapter != null) packageAdapter.setItems(packageItems);
        }
        setEditTextValue(edtPatchValue, patchSet.optString("patch_value", ""));
        setCompoundChecked(chkPatchHex, patchSet.optBoolean("patch_hex", false));
        setCompoundChecked(chkStringPatchTruncate, patchSet.optBoolean("string_patch_truncate", true));
        View caseBox = root == null ? null : root.findViewById(R.id.chkOverlayMemoryStringCaseSensitive);
        if (caseBox instanceof android.widget.CompoundButton) {
            setCompoundChecked((android.widget.CompoundButton) caseBox, patchSet.optBoolean("case_sensitive", shouldStringCaseSensitive()));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(MemoryToolHelper.KEY_STRING_CASE_SENSITIVE, ((android.widget.CompoundButton) caseBox).isChecked()).apply();
        }

        resultItems.clear();
        checkedPatchResultKeys.clear();
        patchNameByKey.clear();
        savedPatchValueByKey.clear();
        selectedResult = null;

        JSONArray rows = patchSet.optJSONArray("patches");
        if (rows == null) {
            appendStatus("Patch file has no patches array.");
            return 0;
        }
        boolean loadedFreezeMarker = false;
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null) continue;
                MemoryResultRow row = memoryResultRowFromJson(item, i + 1);
                if (row.address == 0L) continue;
                resultItems.add(row);
                String key = resultKey(row);
                String patchName = item.optString("name", "").trim();
                if (!TextUtils.isEmpty(patchName)) patchNameByKey.put(key, patchName);
                String savedPatchValue = item.optString("patch_value", "");
                if (!TextUtils.isEmpty(savedPatchValue)) savedPatchValueByKey.put(key, savedPatchValue);
                boolean selected = item.optBoolean("selected", false);
                boolean freeze = item.optBoolean("freeze", false);
                if (selected || (freeze && selectedResult == null)) selectedResult = row;
                loadedFreezeMarker |= freeze;
            }
        }

        if (selectedResult == null && !resultItems.isEmpty()) {
            selectedResult = resultItems.get(0);
        }
        if (selectedResult != null) {
            setEditTextValue(edtPatchAddress, formatHex(selectedResult.address));
            applySavedPatchFieldsForRow(selectedResult, true);
        }
        if (TextUtils.isEmpty(editTextValue(edtPatchValue)) && selectedResult != null) {
            setEditTextValue(edtPatchValue, selectedResult.value);
        }

        activeResultCount = resultItems.size();
        hasActiveResultSet = activeResultCount > 0;
        lastStateJson = null;
        resultStateSearchValue = null;
        resultPageIndex = 0;
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        resetResultListScrollToTop();
        updateResultListVisibility(activeResultCount);
        updateSelectedResultLabel(selectedResult, activeResultCount);
        if (selectedResult != null) {
            notifyToolAddressChanged(selectedResult);
        }
        refreshProcesses();
        if (loadedFreezeMarker) {
            appendStatus("Loaded saved freeze marker. Long-press that result row and choose Freeze this result to start applying it.");
        }
        return resultItems.size();
    }

    private MemoryResultRow memoryResultRowFromJson(JSONObject item, int fallbackIndex) {
        long address = 0L;
        try {
            address = parseFlexibleAddress(item.optString("address_hex", item.optString("address", "0")));
        } catch (Throwable ignored) {
            address = item.optLong("address", 0L);
        }
        return new MemoryResultRow(
                item.optInt("display_index", fallbackIndex),
                address,
                item.optString("data_type", "unknown"),
                item.optString("converter", ""),
                item.optString("value", ""),
                item.optInt("group_index", 0),
                item.optInt("index_in_group", fallbackIndex - 1));
    }

    private String readPatchTextFromDisk(File file) throws java.io.IOException {
        try {
            return readTextFile(file);
        } catch (java.io.IOException directError) {
            String cmd = "cat " + MemoryToolRuntime.shQuote(file.getAbsolutePath());
            MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(cmd);
            if (r != null && r.exitCode == 0 && !TextUtils.isEmpty(r.stdout)) {
                return r.stdout;
            }
            String msg = r == null ? directError.getMessage() : collectCommandOutput(r);
            java.io.IOException out = new java.io.IOException(TextUtils.isEmpty(msg) ? directError.getMessage() : msg);
            out.initCause(directError);
            throw out;
        }
    }

    private String findPackageLabel(String pkg) {
        if (TextUtils.isEmpty(pkg)) return "";
        for (MemoryPackageEntry entry : packageItems) {
            if (entry != null && TextUtils.equals(pkg, entry.pkg)) {
                return entry.label == null ? "" : entry.label;
            }
        }
        return "";
    }

    private String savePatchTextToDisk(File finalFile, String text) {
        try {
            File finalDir = finalFile.getParentFile();
            if (finalDir == null || ((!finalDir.exists() && !finalDir.mkdirs()) || !finalDir.isDirectory())) {
                throw new IllegalStateException("Cannot create patch directory");
            }
            writeTextFile(finalFile, text);
            return "Patches saved: " + finalFile.getAbsolutePath();
        } catch (Throwable directError) {
            try {
                File stagingRoot = getExternalFilesDir(null);
                if (stagingRoot == null) stagingRoot = getFilesDir();
                File stagingDir = new File(stagingRoot, "memory_patch_stage");
                if ((!stagingDir.exists() && !stagingDir.mkdirs()) || !stagingDir.isDirectory()) {
                    throw new IllegalStateException("Cannot create staging directory");
                }
                File staged = new File(stagingDir, finalFile.getName());
                writeTextFile(staged, text);
                String cmd = "mkdir -p " + MemoryToolRuntime.shQuote(MEMORY_PATCH_DIR)
                        + " && cp " + MemoryToolRuntime.shQuote(staged.getAbsolutePath())
                        + " " + MemoryToolRuntime.shQuote(finalFile.getAbsolutePath())
                        + " && chmod 666 " + MemoryToolRuntime.shQuote(finalFile.getAbsolutePath());
                MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(cmd);
                if (r != null && r.exitCode == 0) {
                    return "Patches saved: " + finalFile.getAbsolutePath();
                }
                String msg = r == null ? "shell copy failed" : collectCommandOutput(r);
                return "Patches saved only to app staging: " + staged.getAbsolutePath() + (TextUtils.isEmpty(msg) ? "" : "\nDisk copy failed: " + msg);
            } catch (Throwable fallbackError) {
                return "Save patches failed: " + fallbackError.getClass().getSimpleName() + ": " + fallbackError.getMessage();
            }
        }
    }

    private File memoryPatchFileForPackage(String pkg) {
        return new File(MEMORY_PATCH_DIR, "memory_patch_" + sanitizeFilePart(pkg) + ".json");
    }

    private static String editTextValue(android.widget.EditText editText) {
        return editText == null || editText.getText() == null ? "" : editText.getText().toString();
    }

    private static void setEditTextValue(android.widget.EditText editText, String value) {
        if (editText != null) editText.setText(value == null ? "" : value);
    }

    private static void setDropdownTextIfPresent(MaterialAutoCompleteTextView view, String value) {
        if (view != null && !TextUtils.isEmpty(value)) view.setText(value, false);
    }

    private static void setCompoundChecked(android.widget.CompoundButton button, boolean checked) {
        if (button != null) button.setChecked(checked);
    }

    private static void writeTextFile(File file, String text) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static void appendTextFile(File file, String text) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static String readTextFile(File file) throws java.io.IOException {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static String sanitizeFilePart(String value) {
        String v = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(v)) return "unknown";
        return v.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void ensureForegroundNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = nm.getNotificationChannel(NOTIF_CHANNEL);
                if (ch == null) {
                    ch = new NotificationChannel(NOTIF_CHANNEL, "Memory Overlay", NotificationManager.IMPORTANCE_DEFAULT);
                    ch.setDescription("Shows restore and stop controls for the Memory overlay.");
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
            }
            boolean visible = isOverlayVisible();
            NotificationCompat.Builder nb = new NotificationCompat.Builder(this, NOTIF_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentTitle("Memory Overlay")
                    .setContentText(visible ? "Overlay active. Tap to show controls." : "Overlay minimized. Tap to restore.")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setDeleteIntent(buildServicePendingIntent(ACTION_RESTORE_NOTIFICATION))
                    .setContentIntent(buildMemoryOverlayContentIntent(visible));

            if (visible) {
                nb.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Minimize", buildServicePendingIntent(ACTION_HIDE_OVERLAY));
            } else {
                nb.addAction(android.R.drawable.ic_menu_view, "Show", buildServicePendingIntent(ACTION_SHOW_OVERLAY));
            }
            nb.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", buildServicePendingIntent(ACTION_STOP_OVERLAY));

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, nb.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, nb.build());
            } else {
                startForeground(NOTIF_ID, nb.build());
            }
            startNotificationWatchdog();
        } catch (Throwable t) {
            try { android.util.Log.w("MemoryOverlay", "Unable to post foreground notification", t); } catch (Throwable ignored) {}
        }
    }

    private PendingIntent buildMemoryOverlayContentIntent(boolean visible) {
        return buildServicePendingIntent(ACTION_SHOW_OVERLAY);
    }

    private PendingIntent buildServicePendingIntent(String action) {
        Intent i = new Intent(this, MemoryOverlayService.class);
        i.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, action.hashCode(), i, flags);
    }

    private PendingIntent buildVrPanelPendingIntent() {
        Intent i = new Intent(this, MemoryOverlayVrPanelActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 170421, i, flags);
    }

    private boolean isOverlayVisible() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }
    private void startNotificationWatchdog() {
        if (notificationWatchdogRunning) return;
        notificationWatchdogRunning = true;
        mainHandler.removeCallbacks(notificationWatchdogRunnable);
        mainHandler.postDelayed(notificationWatchdogRunnable, NOTIFICATION_WATCHDOG_MS);
    }

    private void stopNotificationWatchdog() {
        notificationWatchdogRunning = false;
        mainHandler.removeCallbacks(notificationWatchdogRunnable);
    }


    private void showOverlayFromNotification() {
        try {
            if (root != null) {
                root.setVisibility(View.VISIBLE);
                setOverlayInteractive(false);
                PermsTestVrOverlayCompat.clearHiddenOverlayForVr(this);
            }
        } catch (Throwable ignored) {
        }
        ensureForegroundNotification();
    }

    private void hideOverlayIntoNotification() {
        String vrReturnPackage = null;
        try {
            if (root != null) {
                releaseOverlayInputFocus();
                if (PermsTestVrOverlayCompat.shouldRemoveMainOverlayOnMinimize(this)) {
                    vrReturnPackage = snapshotVrReturnTargetPackage();
                    rememberOverlayInputState();
                    removeMainOverlayWindowForVrMinimize();
                    PermsTestVrOverlayCompat.markHiddenOverlayForVr(this, vrReturnPackage, pendingSelectPid);
                } else {
                    root.setVisibility(View.GONE);
                    setOverlayInteractive(false);
                }
            }
        } catch (Throwable ignored) {
        }
        ensureForegroundNotification();
        if (!TextUtils.isEmpty(vrReturnPackage)) {
            debugLog("VR minimize return target=" + cleanLogValue(vrReturnPackage)
                    + " suppressed=" + shouldSuppressVrReturnForTarget(vrReturnPackage));
            if (shouldSuppressVrReturnForTarget(vrReturnPackage)) {
                appendStatus("VR minimize kept the stopped target closed.");
            } else {
                requestVrReturnToTargetPackage(vrReturnPackage, "minimize");
            }
        }
    }

    private void closeOverlayFromUser() {
        if (!PermsTestVrOverlayCompat.isEnabled(this)) {
            stopSelf();
            return;
        }
        try {
            rememberOverlayInputState();
        } catch (Throwable ignored) {
        }
        try {
            removeMainOverlayWindowForVrMinimize();
        } catch (Throwable ignored) {
        }
        try {
            destroyAllToolOverlaysForVrDismiss();
        } catch (Throwable ignored) {
        }
        PermsTestVrOverlayCompat.clearHiddenOverlayForVr(this);
        mainHandler.postDelayed(this::stopSelf, 120L);
    }

    private String snapshotVrReturnTargetPackage() {
        if (!PermsTestVrOverlayCompat.shouldReturnTargetOnMainOverlayDismiss(this)) return "";
        String pkg = "";
        try {
            pkg = getTargetPackage();
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(pkg)) {
            try {
                pkg = pendingSelectPkg;
            } catch (Throwable ignored) {
            }
        }
        if (pkg == null) return "";
        pkg = pkg.trim();
        if (TextUtils.isEmpty(pkg) || TextUtils.equals(pkg, getPackageName())) return "";
        return pkg;
    }

    private boolean shouldSuppressVrReturnForTarget(String packageName) {
        if (!PermsTestVrOverlayCompat.isEnabled(this)) return false;
        String stopped = vrStoppedTargetPackage == null ? "" : vrStoppedTargetPackage.trim();
        String pkg = packageName == null ? "" : packageName.trim();
        return (!TextUtils.isEmpty(stopped) && TextUtils.equals(stopped, pkg))
                || PermsTestVrOverlayCompat.isStoppedTargetForVr(this, pkg);
    }

    private void requestVrReturnToTargetPackage(String packageName, String reason) {
        if (!PermsTestVrOverlayCompat.shouldReturnTargetOnMainOverlayDismiss(this)) return;
        final String pkg = packageName == null ? "" : packageName.trim();
        if (TextUtils.isEmpty(pkg) || TextUtils.equals(pkg, getPackageName())) return;
        if (shouldSuppressVrReturnForTarget(pkg)) return;
        mainHandler.postDelayed(() -> resumeVrTargetPackage(pkg, reason), PermsTestVrOverlayCompat.targetReturnDelayMs());
    }

    private void resumeVrTargetPackage(String packageName, String reason) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(packageName);
            if (i == null) return;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Throwable t) {
            appendStatus("VR return to target failed after " + reason + ": " + t.getMessage());
        }
    }

    private void destroyAllToolOverlaysForVrDismiss() {
        if (hexOverlayController != null) {
            hexOverlayController.destroy();
            hexOverlayController = null;
        }
        if (disassemblyOverlayController != null) {
            disassemblyOverlayController.destroy();
            disassemblyOverlayController = null;
        }
        if (specialToolsOverlayController != null) {
            specialToolsOverlayController.destroy();
            specialToolsOverlayController = null;
        }
    }

    private void removeMainOverlayWindowForVrMinimize() {
        if (root == null) return;
        try {
            rememberOverlayInputState();
            pendingSelectPkg = getTargetPackage();
            pendingSelectPid = getSelectedPid();
            preserveScanStateOnNextOverlayShow = true;
        } catch (Throwable ignored) {
        }
        try {
            if (activePanelContainer != null) {
                detachViewFromParent(root);
            } else if (wm != null) {
                wm.removeView(root);
            }
        } catch (Throwable ignored) {
            try { detachViewFromParent(root); } catch (Throwable ignoredAgain) {}
        }
        clearMainViewReferences();
    }

    private void restoreVrOverlayForTarget(Intent intent) {
        if (!PermsTestVrOverlayCompat.isEnabled(this)) {
            showOrUpdateOverlay(intent);
            showOverlayFromNotification();
            return;
        }
        String pkg = intent == null ? null : intent.getStringExtra(EXTRA_TARGET_PACKAGE);
        String pid = intent == null ? null : intent.getStringExtra(EXTRA_TARGET_PID);
        if (TextUtils.isEmpty(pkg)) pkg = PermsTestVrOverlayCompat.hiddenVrTargetPackage(this);
        if (TextUtils.isEmpty(pid)) pid = PermsTestVrOverlayCompat.hiddenVrTargetPid(this);
        if (!TextUtils.isEmpty(pkg)) pendingSelectPkg = pkg.trim();
        if (!TextUtils.isEmpty(pid)) pendingSelectPid = pid.trim();
        preserveScanStateOnNextOverlayShow = true;

        try {
            showOrUpdateOverlay(intent);
            showOverlayFromNotification();
            PermsTestVrOverlayCompat.clearHiddenOverlayForVr(this);
            appendStatus("Restored VR memory overlay.");
            debugLog("VR restore showed overlay without relaunching target pkg=" + cleanLogValue(pkg)
                    + " pid=" + cleanLogValue(pid));
        } catch (Throwable t) {
            appendStatus("VR overlay restore failed: " + t.getMessage());
            debugLog("VR overlay restore failed", t);
        }
    }

    private void launchTargetPackage() {
        try {
            final String pkg = getTargetPackage();
            if (TextUtils.isEmpty(pkg)) {
                appendStatus("Select a target package first.");
                return;
            }
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                appendStatus("No launchable activity for target package.");
                return;
            }
            vrStoppedTargetPackage = null;
            PermsTestVrOverlayCompat.clearStoppedTargetForVr(this);
            boolean panelMode = hasAnyMemoryPanelContainer();
            if (!panelMode) {
                suspendOverlayInputForLaunch();
            } else {
                releaseOverlayInputFocus();
            }
            boolean detachedForVrLaunch = false;
            if (!panelMode && PermsTestVrOverlayCompat.shouldRemoveMainOverlayBeforeLaunch(this)) {
                rememberOverlayInputState();
                removeMainOverlayWindowForVrMinimize();
                detachedForVrLaunch = true;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            if (!panelMode && detachedForVrLaunch && PermsTestVrOverlayCompat.shouldRestoreMainOverlayAfterLaunch(this)) {
                mainHandler.postDelayed(() -> restoreMainOverlayAfterVrLaunch(pkg),
                        PermsTestVrOverlayCompat.mainOverlayRestoreAfterLaunchDelayMs());
            } else if (panelMode) {
                appendStatus("Launched " + pkg + ". Memory panel remains active.");
            } else {
                appendStatus("Launched " + pkg + ". Overlay input is passive while the app starts.");
            }
            if (root != null) {
                root.postDelayed(this::releaseOverlayInputFocus, 120L);
                root.postDelayed(this::releaseOverlayInputFocus, 450L);
                root.postDelayed(this::releaseOverlayInputFocus, 1200L);
                root.postDelayed(this::releaseOverlayInputFocus, 3000L);
                root.postDelayed(this::refreshProcesses, 1600L);
                root.postDelayed(this::refreshProcesses, 3500L);
            } else if (panelMode) {
                mainHandler.postDelayed(this::refreshProcesses, 1600L);
                mainHandler.postDelayed(this::refreshProcesses, 3500L);
            }
        } catch (Throwable t) {
            appendStatus("Launch failed: " + t);
        }
    }

    private void restoreMainOverlayAfterVrLaunch(String pkg) {
        try {
            if (!PermsTestVrOverlayCompat.shouldRestoreMainOverlayAfterLaunch(this)) return;
            showOrUpdateOverlay(null);
            showOverlayFromNotification();
            PermsTestVrOverlayCompat.clearHiddenOverlayForVr(this);
            appendStatus("Launched " + pkg + ". Reopened the VR memory overlay after the app became visible.");
            refreshProcesses();
            mainHandler.postDelayed(this::refreshProcesses, 1600L);
        } catch (Throwable t) {
            appendStatus("VR overlay restore after launch failed: " + t.getMessage());
        }
    }

    private void suspendOverlayInputForLaunch() {
        launchPassiveUntil = SystemClock.uptimeMillis() + LAUNCH_PASSIVE_INPUT_MS;
        releaseOverlayInputFocus();
        releaseToolOverlayInputFocus();
        setOverlayInteractive(false);
    }

    private void stopTargetPackage() {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            appendStatus("Select a target package first.");
            return;
        }
        if (PermsTestVrOverlayCompat.isEnabled(this)) {
            vrStoppedTargetPackage = pkg;
            PermsTestVrOverlayCompat.markStoppedTargetForVr(this, pkg);
        }
        appendStatus("Stopping " + pkg + "...");
        new Thread(() -> {
            MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync("am force-stop " + shQuote(pkg));
            runOnUiThread(() -> appendStatus(summarizeResult("Stopped " + pkg + ".", r)));
        }, "MemoryOverlayStopApp").start();
    }

    private static String shQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }


    private void openVrTextInputForView(View view) {
        if (view == null) return;
        String key = vrTextFieldKeyForView(view);
        if (TextUtils.isEmpty(key)) return;
        detachedVrInputWasPanel = activePanelContainer != null;
        rememberOverlayInputState();
        Intent i = new Intent(this, MemoryOverlayVrTextInputActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_KEY, key);
        i.putExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_LABEL, vrTextFieldLabel(key));
        i.putExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_HINT, vrTextFieldHint(key));
        i.putExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_VALUE, textOf((TextView) view));
        removeMainOverlayWindowForVrMinimize();
        try {
            startActivity(i);
        } catch (Throwable t) {
            restoreOverlayAfterDetachedVrInput();
            appendStatus("VR input failed: " + t);
        }
    }

    private String vrTextFieldKeyForView(View view) {
        if (view == edtSearchValue) return PermsTestVrOverlayCompat.FIELD_SEARCH_VALUE;
        if (view == edtPatchName) return PermsTestVrOverlayCompat.FIELD_PATCH_NAME;
        if (view == edtPatchAddress) return PermsTestVrOverlayCompat.FIELD_PATCH_ADDRESS;
        if (view == edtPatchValue) return PermsTestVrOverlayCompat.FIELD_PATCH_VALUE;
        if (view == edtResultLimit) return PermsTestVrOverlayCompat.FIELD_RESULT_LIMIT;
        if (view == edtMaxResults) return PermsTestVrOverlayCompat.FIELD_MAX_RESULTS;
        if (view == edtDumpBegin) return PermsTestVrOverlayCompat.FIELD_DUMP_BEGIN;
        if (view == edtDumpEnd) return PermsTestVrOverlayCompat.FIELD_DUMP_END;
        if (view == edtAutoRangeLimit) return PermsTestVrOverlayCompat.FIELD_AUTO_RANGE_LIMIT;
        return "";
    }

    private String vrTextFieldLabel(String key) {
        if (PermsTestVrOverlayCompat.FIELD_SEARCH_VALUE.equals(key)) return "Search value";
        if (PermsTestVrOverlayCompat.FIELD_PATCH_NAME.equals(key)) return "Patch name";
        if (PermsTestVrOverlayCompat.FIELD_PATCH_ADDRESS.equals(key)) return "Patch address";
        if (PermsTestVrOverlayCompat.FIELD_PATCH_VALUE.equals(key)) return "Patch value";
        if (PermsTestVrOverlayCompat.FIELD_RESULT_LIMIT.equals(key)) return "Result limit";
        if (PermsTestVrOverlayCompat.FIELD_MAX_RESULTS.equals(key)) return "Max results";
        if (PermsTestVrOverlayCompat.FIELD_DUMP_BEGIN.equals(key)) return "Dump begin";
        if (PermsTestVrOverlayCompat.FIELD_DUMP_END.equals(key)) return "Dump end";
        if (PermsTestVrOverlayCompat.FIELD_AUTO_RANGE_LIMIT.equals(key)) return "Auto limit";
        return "Input";
    }

    private String vrTextFieldHint(String key) {
        if (PermsTestVrOverlayCompat.FIELD_SEARCH_VALUE.equals(key)) return "Search value";
        if (PermsTestVrOverlayCompat.FIELD_PATCH_ADDRESS.equals(key)) return "0x...";
        if (PermsTestVrOverlayCompat.FIELD_PATCH_VALUE.equals(key)) return "Value or hex bytes";
        if (PermsTestVrOverlayCompat.FIELD_DUMP_BEGIN.equals(key)) return "Begin address";
        if (PermsTestVrOverlayCompat.FIELD_DUMP_END.equals(key)) return "End address";
        return vrTextFieldLabel(key);
    }

    private void handleVrTextInputResult(@Nullable Intent intent) {
        if (intent == null) {
            restoreOverlayAfterDetachedVrInput();
            return;
        }
        String key = intent.getStringExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_KEY);
        String value = intent.getStringExtra(PermsTestVrOverlayCompat.EXTRA_FIELD_VALUE);
        boolean cancelled = intent.getBooleanExtra(PermsTestVrOverlayCompat.EXTRA_CANCELLED, false);
        if (!cancelled && !TextUtils.isEmpty(key)) {
            setRestoredTextValue(key, value == null ? "" : value);
        }
        restoreOverlayAfterDetachedVrInput();
        if (!cancelled && !TextUtils.isEmpty(key)) {
            applyVrTextInputValue(key, value == null ? "" : value);
        }
    }

    private void restoreOverlayAfterDetachedVrInput() {
        boolean restorePanel = detachedVrInputWasPanel;
        detachedVrInputWasPanel = false;
        if (restorePanel && activePanelContainer != null) {
            showOrUpdatePanel(null, activePanelContainer, activePanelCloseCallback);
            return;
        }
        if (!Settings.canDrawOverlays(this)) return;
        showOrUpdateOverlay(null);
        showOverlayFromNotification();
    }

    private void rememberOverlayInputState() {
        restoreSearchValue = textOf(edtSearchValue);
        restorePatchName = textOf(edtPatchName);
        restorePatchAddress = textOf(edtPatchAddress);
        restorePatchValue = textOf(edtPatchValue);
        restoreResultLimit = textOf(edtResultLimit);
        restoreMaxResults = textOf(edtMaxResults);
        restoreDumpBegin = textOf(edtDumpBegin);
        restoreDumpEnd = textOf(edtDumpEnd);
        restoreAutoRangeLimit = textOf(edtAutoRangeLimit);
        restoreDataType = textOf(ddDataType);
        restoreSearchMode = textOf(ddSearchMode);
        restoreOverlayInputStatePending = true;
    }

    private void restoreOverlayInputStateIfNeeded() {
        if (!restoreOverlayInputStatePending) return;
        setTextIfNotNull(edtSearchValue, restoreSearchValue);
        setTextIfNotNull(edtPatchName, restorePatchName);
        setTextIfNotNull(edtPatchAddress, restorePatchAddress);
        setTextIfNotNull(edtPatchValue, restorePatchValue);
        setTextIfNotNull(edtResultLimit, restoreResultLimit);
        setTextIfNotNull(edtMaxResults, restoreMaxResults);
        setTextIfNotNull(edtDumpBegin, restoreDumpBegin);
        setTextIfNotNull(edtDumpEnd, restoreDumpEnd);
        setTextIfNotNull(edtAutoRangeLimit, restoreAutoRangeLimit);
        setDropdownTextIfNotNull(ddDataType, restoreDataType);
        setDropdownTextIfNotNull(ddSearchMode, restoreSearchMode);
        restoreOverlayInputStatePending = false;
    }

    private void restoreOverlayRuntimeStateAfterViewRecreate(boolean created) {
        if (!created) return;
        try {
            if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
            updateResultListVisibility(activeResultCount);
            updateSelectedResultLabel(selectedResult, activeResultCount);
            updateScanRangeStatus();
        } catch (Throwable ignored) {
        }
        try {
            if (txtOutput != null && !TextUtils.isEmpty(lastOverlayDetailText)) {
                txtOutput.setText(colorizeDetailAddresses(txtOutput, lastOverlayDetailText));
            }
        } catch (Throwable ignored) {
        }
    }

    private void setRestoredTextValue(String key, String value) {
        if (PermsTestVrOverlayCompat.FIELD_SEARCH_VALUE.equals(key)) restoreSearchValue = value;
        else if (PermsTestVrOverlayCompat.FIELD_PATCH_NAME.equals(key)) restorePatchName = value;
        else if (PermsTestVrOverlayCompat.FIELD_PATCH_ADDRESS.equals(key)) restorePatchAddress = value;
        else if (PermsTestVrOverlayCompat.FIELD_PATCH_VALUE.equals(key)) restorePatchValue = value;
        else if (PermsTestVrOverlayCompat.FIELD_RESULT_LIMIT.equals(key)) restoreResultLimit = value;
        else if (PermsTestVrOverlayCompat.FIELD_MAX_RESULTS.equals(key)) restoreMaxResults = value;
        else if (PermsTestVrOverlayCompat.FIELD_DUMP_BEGIN.equals(key)) restoreDumpBegin = value;
        else if (PermsTestVrOverlayCompat.FIELD_DUMP_END.equals(key)) restoreDumpEnd = value;
        else if (PermsTestVrOverlayCompat.FIELD_AUTO_RANGE_LIMIT.equals(key)) restoreAutoRangeLimit = value;
        restoreOverlayInputStatePending = true;
    }

    private void applyVrTextInputValue(String key, String value) {
        if (PermsTestVrOverlayCompat.FIELD_SEARCH_VALUE.equals(key)) setTextIfNotNull(edtSearchValue, value);
        else if (PermsTestVrOverlayCompat.FIELD_PATCH_NAME.equals(key)) setTextIfNotNull(edtPatchName, value);
        else if (PermsTestVrOverlayCompat.FIELD_PATCH_ADDRESS.equals(key)) setTextIfNotNull(edtPatchAddress, value);
        else if (PermsTestVrOverlayCompat.FIELD_PATCH_VALUE.equals(key)) setTextIfNotNull(edtPatchValue, value);
        else if (PermsTestVrOverlayCompat.FIELD_RESULT_LIMIT.equals(key)) setTextIfNotNull(edtResultLimit, value);
        else if (PermsTestVrOverlayCompat.FIELD_MAX_RESULTS.equals(key)) setTextIfNotNull(edtMaxResults, value);
        else if (PermsTestVrOverlayCompat.FIELD_DUMP_BEGIN.equals(key)) setTextIfNotNull(edtDumpBegin, value);
        else if (PermsTestVrOverlayCompat.FIELD_DUMP_END.equals(key)) setTextIfNotNull(edtDumpEnd, value);
        else if (PermsTestVrOverlayCompat.FIELD_AUTO_RANGE_LIMIT.equals(key)) setTextIfNotNull(edtAutoRangeLimit, value);
    }

    private String textOf(@Nullable TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }

    private void setTextIfNotNull(@Nullable TextView view, @Nullable String value) {
        if (view != null && value != null) view.setText(value);
    }

    private void setDropdownTextIfNotNull(@Nullable MaterialAutoCompleteTextView view, @Nullable String value) {
        if (view != null && value != null && !TextUtils.isEmpty(value.trim())) {
            view.setText(value.trim(), false);
        }
    }

    private void installInputModeHooks() {
        installTextInputHook(edtSearchValue);
        installTextInputHook(edtPatchName);
        installTextInputHook(edtPatchAddress);
        installTextInputHook(edtPatchValue);
        installTextInputHook(edtResultLimit);
        installTextInputHook(edtMaxResults);
        installTextInputHook(edtDumpBegin);
        installTextInputHook(edtDumpEnd);
        installTextInputHook(edtAutoRangeLimit);
        root.setOnClickListener(v -> maybeReturnToPassiveMode());
    }

    private void installTextInputHook(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            if (event == null) return false;
            if (PermsTestVrOverlayCompat.shouldUseDetachedTextInput(this)) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    openVrTextInputForView(v);
                }
                return true;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                beginMainOverlayInputSession(true);
            } else if (action == MotionEvent.ACTION_UP) {
                beginMainOverlayInputSession(true);
                v.requestFocus();
                v.postDelayed(() -> showKeyboard(v), 80L);
            }
            return false;
        });
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (canPromoteOverlayFocus()) {
                    setToolOverlaysExternalInputActive(true);
                    releaseToolOverlayInputFocus();
                    setOverlayInteractive(true);
                } else if (root != null) {
                    root.post(this::releaseOverlayInputFocus);
                }
            } else {
                root.postDelayed(() -> {
                    if (!hasAnyInputFocus()) {
                        setToolOverlaysExternalInputActive(false);
                    }
                    maybeReturnToPassiveMode();
                }, 150L);
            }
        });
    }

    private void showKeyboard(View input) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        } catch (Throwable ignored) {
        }
    }

    private void configureOverlayDropdown(MaterialAutoCompleteTextView view, @Nullable View tilView, @Nullable Runnable beforeOpen) {
        if (view == null) return;
        DropdownUi.prepareExposedDropdown(tilView instanceof TextInputLayout ? (TextInputLayout) tilView : null, view);
        try { view.setDropDownHeight(dp(280)); } catch (Throwable ignored) {}
        final Runnable opener = () -> openOverlayDropdown(view, beforeOpen);
        view.setOnClickListener(v -> {
            beginOverlayDropdownInputSession();
            opener.run();
        });
        view.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            long downTime;
            boolean moved;
            final int slop = ViewConfiguration.get(MemoryOverlayService.this).getScaledTouchSlop();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event == null) return false;
                try { v.getParent().requestDisallowInterceptTouchEvent(true); } catch (Throwable ignored) {}
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        beginOverlayDropdownInputSession();
                        downX = event.getX();
                        downY = event.getY();
                        downTime = SystemClock.uptimeMillis();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!moved) {
                            float dx = Math.abs(event.getX() - downX);
                            float dy = Math.abs(event.getY() - downY);
                            if (dx > slop || dy > slop) moved = true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved && (SystemClock.uptimeMillis() - downTime) <= 500L) {
                            beginOverlayDropdownInputSession();
                            opener.run();
                        } else {
                            try { dropdownOpen = view.isPopupShowing(); } catch (Throwable ignored) { dropdownOpen = false; }
                            if (root != null) root.postDelayed(MemoryOverlayService.this::maybeReturnToPassiveMode, 350L);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        try { dropdownOpen = view.isPopupShowing(); } catch (Throwable ignored) { dropdownOpen = false; }
                        if (root != null) root.postDelayed(MemoryOverlayService.this::maybeReturnToPassiveMode, 350L);
                        return true;
                    default:
                        return true;
                }
            }
        });
        view.setOnDismissListener(() -> onOverlayDropdownDismissed(view));
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (canPromoteOverlayFocus()) {
                    beginOverlayDropdownInputSession();
                } else if (root != null) {
                    root.post(this::releaseOverlayInputFocus);
                }
            } else if (root != null) {
                root.postDelayed(this::maybeReturnToPassiveMode, 350L);
            }
        });
        if (tilView instanceof com.google.android.material.textfield.TextInputLayout) {
            try {
                com.google.android.material.textfield.TextInputLayout til = (com.google.android.material.textfield.TextInputLayout) tilView;
                til.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM);
                til.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_dropdown_arrow);
                til.setEndIconOnClickListener(v -> {
                    beginOverlayDropdownInputSession();
                    opener.run();
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private void onOverlayDropdownDismissed(MaterialAutoCompleteTextView view) {
        dropdownOpen = isAnyOverlayDropdownShowing();
        if (view == ddTargetPkg && pendingPackageListUpdate != null) {
            List<MemoryPackageEntry> pending = pendingPackageListUpdate;
            pendingPackageListUpdate = null;
            if (root != null) root.postDelayed(() -> updateTargetPackageListNow(pending), 300L);
        } else if (view == ddProcess && pendingProcessListUpdate != null) {
            List<MemoryProcessEntry> pending = pendingProcessListUpdate;
            pendingProcessListUpdate = null;
            if (root != null) root.postDelayed(() -> updateProcessListNow(pending), 300L);
        }
        if (root != null && !dropdownOpen) root.postDelayed(this::maybeReturnToPassiveMode, 350L);
    }

    private boolean isDropdownShowing(MaterialAutoCompleteTextView view) {
        if (view == null) return false;
        try { return view.isPopupShowing(); } catch (Throwable ignored) { return false; }
    }

    private void openOverlayDropdown(MaterialAutoCompleteTextView view, @Nullable Runnable beforeOpen) {
        if (view == null) return;
        long now = SystemClock.uptimeMillis();
        beginOverlayDropdownInputSession();
        if (!canPromoteOverlayFocus()) {
            releaseOverlayInputFocus();
            return;
        }
        try {
            if (view.isPopupShowing()) {
                view.post(() -> tryApplyDropdownTweaks(view));
                return;
            }
        } catch (Throwable ignored) {
        }
        if (now - lastDropdownOpenRequest < 250L) {
            return;
        }
        lastDropdownOpenRequest = now;

        if (beforeOpen != null) {
            beforeOpen.run();
        }
        if (view == ddTargetPkg && targetPackageRefreshInFlight) {
            setTargetPackageLoading(true);
            appendStatus("Loading target packages...");
            return;
        }
        if (view == ddProcess && processRefreshInFlight) {
            setProcessLoading(true);
            appendStatus("Loading running processes...");
            return;
        }
        if (getAdapterCount(view) == 0) {
            appendStatus("List is still loading. It will open when ready.");
            return;
        }
        showOverlayDropdownNow(view);
    }

    private void showOverlayDropdownNow(MaterialAutoCompleteTextView view) {
        if (view == null) return;
        beginOverlayDropdownInputSession();
        if (!canPromoteOverlayFocus()) {
            releaseOverlayInputFocus();
            return;
        }
        view.postDelayed(() -> {
            beginOverlayDropdownInputSession();
            if (!canPromoteOverlayFocus()) {
                releaseOverlayInputFocus();
                return;
            }
            try { view.requestFocus(); } catch (Throwable ignored) {}
            try { DropdownUi.showDropdown(view, this::applyDropdownListTweaks); } catch (Throwable ignored) {}
        }, 80L);
    }

    private int getAdapterCount(MaterialAutoCompleteTextView view) {
        try {
            return view == null || view.getAdapter() == null ? 0 : view.getAdapter().getCount();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void tryApplyDropdownTweaks(AutoCompleteTextView tv) {
        DropdownUi.tryApplyDropdownTweaks(tv, this::applyDropdownListTweaks);
    }

    private void applyDropdownListTweaks(ListView lv) {
        applyListScrollbarTweaks(lv);
    }

    private void applyListScrollbarTweaks(ListView lv) {
        try {
            if (lv == null) return;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean fat = sp.getBoolean("fat_dropdown_scrollbar", true);
            lv.setVerticalScrollBarEnabled(true);
            try { lv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY); } catch (Throwable ignored) {}
            if (fat) {
                lv.setScrollbarFadingEnabled(false);
                try { lv.setFastScrollEnabled(true); } catch (Throwable ignored) {}
                try { lv.setFastScrollAlwaysVisible(true); } catch (Throwable ignored) {}
                try { lv.setScrollBarSize(dp(28)); } catch (Throwable ignored) {}
            } else {
                try { lv.setFastScrollAlwaysVisible(false); } catch (Throwable ignored) {}
                try { lv.setFastScrollEnabled(false); } catch (Throwable ignored) {}
                lv.setScrollbarFadingEnabled(true);
                try { lv.setScrollBarSize(dp(4)); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyResultListScrollbarTweaks(ListView lv) {
        try {
            if (lv == null) return;
            lv.setVerticalScrollBarEnabled(false);
            lv.setScrollbarFadingEnabled(true);
            try { lv.setFastScrollEnabled(false); } catch (Throwable ignored) {}
            try { lv.setFastScrollAlwaysVisible(false); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }


    private void installDetailAddressActions(@Nullable TextView textView) {
        if (textView == null) return;
        final ViewConfiguration vc = ViewConfiguration.get(textView.getContext());
        final int touchSlop = vc == null ? dp(8) : vc.getScaledTouchSlop();
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final boolean[] moved = new boolean[1];
        final boolean[] longPressFired = new boolean[1];
        final String[] downAddress = new String[1];
        final Runnable[] longPress = new Runnable[1];
        textView.setOnTouchListener((v, event) -> {
            if (event == null) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                requestParentDisallowIntercept(v, true);
                downX[0] = event.getX();
                downY[0] = event.getY();
                moved[0] = false;
                longPressFired[0] = false;
                downAddress[0] = findDetailAddressAt(textView, event.getX(), event.getY());
                if (!TextUtils.isEmpty(downAddress[0])) {
                    longPress[0] = () -> {
                        if (!moved[0] && !TextUtils.isEmpty(downAddress[0])) {
                            longPressFired[0] = true;
                            try { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) {}
                            showDetailAddressMenu(downAddress[0], textView);
                        }
                    };
                    mainHandler.postDelayed(longPress[0], ViewConfiguration.getLongPressTimeout());
                }
                return false;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (Math.abs(event.getX() - downX[0]) > touchSlop || Math.abs(event.getY() - downY[0]) > touchSlop) {
                    moved[0] = true;
                    if (longPress[0] != null) mainHandler.removeCallbacks(longPress[0]);
                    longPress[0] = null;
                }
                return false;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                requestParentDisallowIntercept(v, false);
                if (longPress[0] != null) mainHandler.removeCallbacks(longPress[0]);
                longPress[0] = null;
                if (action == MotionEvent.ACTION_UP && !moved[0] && !longPressFired[0] && !TextUtils.isEmpty(downAddress[0])) {
                    String upAddress = findDetailAddressAt(textView, event.getX(), event.getY());
                    if (TextUtils.equals(downAddress[0], upAddress)) {
                        openDetailAddressInHex(downAddress[0]);
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @Nullable
    private String findDetailAddressAt(TextView textView, float x, float y) {
        try {
            if (textView == null || TextUtils.isEmpty(textView.getText())) return null;
            android.text.Layout layout = textView.getLayout();
            if (layout == null) return null;
            int line = layout.getLineForVertical((int) (y + textView.getScrollY() - textView.getTotalPaddingTop()));
            int lineStart = layout.getLineStart(line);
            int lineEnd = layout.getLineEnd(line);
            int offset = layout.getOffsetForHorizontal(line, x + textView.getScrollX() - textView.getTotalPaddingLeft());
            CharSequence full = textView.getText();
            if (lineStart < 0 || lineEnd < lineStart || lineEnd > full.length()) return null;
            String lineText = full.subSequence(lineStart, lineEnd).toString();
            int lineOffset = Math.max(0, Math.min(offset - lineStart, lineText.length()));
            Matcher matcher = DETAIL_HEX_ADDRESS_PATTERN.matcher(lineText);
            while (matcher.find()) {
                if (lineOffset >= matcher.start() && lineOffset <= matcher.end()) {
                    return matcher.group();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void openDetailAddressInHex(String rawAddress) {
        long address;
        try {
            address = parseFlexibleAddress(rawAddress);
        } catch (Throwable t) {
            appendStatus("Details address is invalid: " + rawAddress);
            return;
        }
        String formatted = formatHex(address);
        manualToolAddressOverride = formatted;
        try { if (edtPatchAddress != null) edtPatchAddress.setText(formatted); } catch (Throwable ignored) {}
        try { if (edtDumpBegin != null) edtDumpBegin.setText(formatted); } catch (Throwable ignored) {}
        showHexOverlayWindow();
        try { if (hexOverlayController != null) hexOverlayController.setAddress(formatted, true); } catch (Throwable ignored) {}
        syncToolAddressToDisassembly(formatted, true);
        appendStatusPreserveDetailScroll("Opened Details address " + formatted + " in Hex.");
    }

    private void openDetailAddressInDisassembly(String rawAddress) {
        long address;
        try {
            address = parseFlexibleAddress(rawAddress);
        } catch (Throwable t) {
            appendStatus("Details address is invalid: " + rawAddress);
            return;
        }
        String formatted = formatHex(address);
        manualToolAddressOverride = formatted;
        try { if (edtPatchAddress != null) edtPatchAddress.setText(formatted); } catch (Throwable ignored) {}
        try { if (edtDumpBegin != null) edtDumpBegin.setText(formatted); } catch (Throwable ignored) {}
        showDisassemblyOverlayWindow();
        try { if (disassemblyOverlayController != null) disassemblyOverlayController.setAddress(formatted, true); } catch (Throwable ignored) {}
        appendStatusPreserveDetailScroll("Opened Details address " + formatted + " in Disassembly.");
    }

    private void showDetailAddressMenu(String rawAddress, View anchor) {
        if (TextUtils.isEmpty(rawAddress) || anchor == null) return;
        allowOverlayFocusFromUserInput();
        setOverlayInteractive(true);
        PopupMenu menu = new PopupMenu(overlayThemeContext == null ? this : overlayThemeContext, anchor);
        menu.getMenu().add(0, 1, 0, "Open in Hex");
        menu.getMenu().add(0, 2, 1, "Open in Disassembly");
        menu.getMenu().add(0, 3, 2, "Copy address");
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                openDetailAddressInHex(rawAddress);
                return true;
            }
            if (id == 2) {
                openDetailAddressInDisassembly(rawAddress);
                return true;
            }
            if (id == 3) {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PermsTest address", rawAddress));
                    Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
                return true;
            }
            return false;
        });
        menu.setOnDismissListener(ignored -> maybeReturnToPassiveMode());
        try { menu.show(); } catch (Throwable t) { appendStatus("Details address menu failed: " + t.getMessage()); }
    }

    private void updateMainMemoryToolAddressFields(String formattedAddress) {
        if (TextUtils.isEmpty(formattedAddress)) return;
        try { if (edtPatchAddress != null) edtPatchAddress.setText(formattedAddress); } catch (Throwable ignored) {}
        try { if (edtDumpBegin != null) edtDumpBegin.setText(formattedAddress); } catch (Throwable ignored) {}
    }

    private void syncToolAddressToHex(String formattedAddress, boolean autoRead) {
        if (TextUtils.isEmpty(formattedAddress)) return;
        try {
            if (hexOverlayController != null) {
                debugLog("memory tool sync disasm->hex address=" + cleanLogValue(formattedAddress) + " autoRead=" + autoRead);
                hexOverlayController.setAddress(formattedAddress, autoRead);
            }
        } catch (Throwable ignored) {
        }
    }

    private void syncToolAddressToDisassembly(String formattedAddress, boolean autoRefresh) {
        if (TextUtils.isEmpty(formattedAddress)) return;
        try {
            if (disassemblyOverlayController != null && shouldSyncDisassemblyWithHexEditor()) {
                debugLog("memory tool sync hex->disasm address=" + cleanLogValue(formattedAddress) + " autoRefresh=" + autoRefresh);
                disassemblyOverlayController.setAddressFromHexEditor(formattedAddress, autoRefresh);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean shouldSyncDisassemblyWithHexEditor() {
        try {
            return disassemblyOverlayController == null || disassemblyOverlayController.isSyncWithHexEditor();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void configureScrollableText(@Nullable TextView textView) {
        try {
            if (textView == null) return;
            textView.setVerticalScrollBarEnabled(false);
            textView.setScrollbarFadingEnabled(true);
            textView.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());
            installNestedScrollGuard(textView);
        } catch (Throwable ignored) {
        }
    }

    private void installNestedScrollGuard(@Nullable View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            requestParentDisallowIntercept(v, event != null && (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE));
            return false;
        });
    }

    private void requestParentDisallowIntercept(View view, boolean disallow) {
        try {
            ViewParent parent = view == null ? null : view.getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(disallow);
                if (parent instanceof View) {
                    parent = ((View) parent).getParent();
                } else {
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }


    private void beginMainOverlayInputSession(boolean releaseToolInput) {
        allowOverlayFocusFromUserInput();
        setToolOverlaysExternalInputActive(true);
        if (releaseToolInput) {
            releaseToolOverlayInputFocus();
        }
        setOverlayInteractive(true);
    }

    private void beginOverlayDropdownInputSession() {
        dropdownOpen = true;
        beginMainOverlayInputSession(true);
    }

    private boolean isOverlayDropdownActive() {
        return dropdownOpen || isAnyOverlayDropdownShowing();
    }

    private boolean isAnyOverlayDropdownShowing() {
        return isDropdownShowing(ddTargetPkg)
                || isDropdownShowing(ddProcess)
                || isDropdownShowing(ddDataType)
                || isDropdownShowing(ddSearchMode);
    }

    private void maybeReturnToPassiveMode() {
        if (root == null) return;
        if (isOverlayDropdownActive()) return;
        if (hasAnyInputFocus()) return;
        setToolOverlaysExternalInputActive(false);
        setOverlayInteractive(false);
    }

    private void allowOverlayFocusFromUserInput() {
        overlayFocusAllowedUntil = SystemClock.uptimeMillis() + OVERLAY_INPUT_FOCUS_WINDOW_MS;
    }

    private boolean canPromoteOverlayFocus() {
        if (PermsTestVrOverlayCompat.shouldKeepOverlayNonFocusable(this)) {
            return true;
        }
        if (isOverlayDropdownActive()) {
            return true;
        }
        return SystemClock.uptimeMillis() <= overlayFocusAllowedUntil;
    }

    private void releaseOverlayInputFocus() {
        dropdownOpen = false;
        try { if (ddTargetPkg != null) ddTargetPkg.dismissDropDown(); } catch (Throwable ignored) {}
        try { if (ddProcess != null) ddProcess.dismissDropDown(); } catch (Throwable ignored) {}
        try { if (ddDataType != null) ddDataType.dismissDropDown(); } catch (Throwable ignored) {}
        try { if (ddSearchMode != null) ddSearchMode.dismissDropDown(); } catch (Throwable ignored) {}
        try { if (root != null && root.findFocus() != null) root.findFocus().clearFocus(); } catch (Throwable ignored) {}
        try { if (ddTargetPkg != null) ddTargetPkg.clearFocus(); } catch (Throwable ignored) {}
        try { if (ddProcess != null) ddProcess.clearFocus(); } catch (Throwable ignored) {}
        try { if (ddDataType != null) ddDataType.clearFocus(); } catch (Throwable ignored) {}
        try { if (ddSearchMode != null) ddSearchMode.clearFocus(); } catch (Throwable ignored) {}
        try { if (edtSearchValue != null) edtSearchValue.clearFocus(); } catch (Throwable ignored) {}
        try { if (edtPatchName != null) edtPatchName.clearFocus(); } catch (Throwable ignored) {}
        try { if (edtPatchAddress != null) edtPatchAddress.clearFocus(); } catch (Throwable ignored) {}
        try { if (edtPatchValue != null) edtPatchValue.clearFocus(); } catch (Throwable ignored) {}
        try { if (edtResultLimit != null) edtResultLimit.clearFocus(); } catch (Throwable ignored) {}
        try { if (edtMaxResults != null) edtMaxResults.clearFocus(); } catch (Throwable ignored) {}
        try { if (chkSyncResultValue != null) chkSyncResultValue.clearFocus(); } catch (Throwable ignored) {}
        try { if (edtDumpBegin != null) edtDumpBegin.clearFocus(); } catch (Throwable ignored) {}
        try { if (edtDumpEnd != null) edtDumpEnd.clearFocus(); } catch (Throwable ignored) {}
        try { if (root != null) root.clearFocus(); } catch (Throwable ignored) {}
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && root != null && root.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
        setOverlayInteractive(false);
        setToolOverlaysExternalInputActive(false);
        releaseToolOverlayInputFocus();
    }

    private void setToolOverlaysExternalInputActive(boolean active) {
        try {
            if (hexOverlayController != null) {
                hexOverlayController.setExternalInputActive(active);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (disassemblyOverlayController != null) {
                disassemblyOverlayController.setExternalInputActive(active);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (specialToolsOverlayController != null) {
                specialToolsOverlayController.setExternalInputActive(active);
            }
        } catch (Throwable ignored) {
        }
    }

    private void setToolOverlaysBackendBusy(boolean busy) {
        try {
            if (hexOverlayController != null) {
                hexOverlayController.setBackendBusy(busy);
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseToolOverlayInputFocus() {
        try { if (hexOverlayController != null) hexOverlayController.releaseInputFocus(); } catch (Throwable ignored) {}
        try { if (disassemblyOverlayController != null) disassemblyOverlayController.releaseInputFocus(); } catch (Throwable ignored) {}
        try { if (specialToolsOverlayController != null) specialToolsOverlayController.releaseInputFocus(); } catch (Throwable ignored) {}
    }

    private void onHexPayloadLoaded(String hexBytes) {
        try {
            String value = hexBytes == null ? "" : hexBytes.trim().toUpperCase(Locale.US);
            if (edtPatchValue != null) edtPatchValue.setText(value);
            appendStatus("Loaded hex payload into Patch value (" + (value.length() / 2) + " bytes).");
        } catch (Throwable ignored) {
        }
    }

    private void onHexAddressChanged(long address) {
        try {
            String formatted = formatHex(address);
            manualToolAddressOverride = formatted;
            syncToolAddressToDisassembly(formatted, true);
        } catch (Throwable ignored) {
        }
    }

    private void onHexByteSelected(long byteAddress, long dwordAddress, long dwordValue) {
        try {
            MemoryResultRow row = new MemoryResultRow(
                    0,
                    dwordAddress,
                    "dword",
                    "dword",
                    String.valueOf(dwordValue),
                    0,
                    0);
            selectedResult = row;
            updateSelectedResultLabel(row, activeResultCount);
            applyDumpRange(row);
            if (edtPatchAddress != null) edtPatchAddress.setText(formatHex(dwordAddress));
            if (edtPatchValue != null) edtPatchValue.setText(formatPatchValueForMode(row));
            syncToolAddressToDisassembly(formatHex(dwordAddress), true);
            appendStatus("Hex selected " + formatHex(byteAddress) + " -> dword " + formatHex(dwordAddress) + " = " + dwordValue + ".");
        } catch (Throwable ignored) {
        }
    }

    private void updateSearchValueHint(String hint) {
        try {
            if (tilSearchValue != null) tilSearchValue.setHint(TextUtils.isEmpty(hint) ? "Search value" : hint);
        } catch (Throwable ignored) {
        }
    }

    private void updateSearchValueHintForSearchMode(String mode) {
        String dataType = ddDataType == null || ddDataType.getText() == null ? "" : ddDataType.getText().toString();
        if (TextUtils.equals(MemoryToolHelper.normalizeDataType(dataType), "string")) {
            updateSearchValueHint("String value");
        } else if (MemoryToolHelper.isValueGreaterThanInputSearchMode(mode) || MemoryToolHelper.isValueLessThanInputSearchMode(mode) || MemoryToolHelper.isExactSearchMode(mode)) {
            updateSearchValueHint("Search value");
        } else {
            updateSearchValueHint("Optional value");
        }
    }

    private String getCurrentSearchMode() {
        return ddSearchMode == null || ddSearchMode.getText() == null
                ? MemoryToolHelper.DEFAULT_SEARCH_MODE
                : MemoryToolHelper.normalizeSearchMode(ddSearchMode.getText().toString());
    }

    private String getCurrentSelectedDataType() {
        String selected = ddDataType == null || ddDataType.getText() == null
                ? MemoryToolHelper.DEFAULT_DATA_TYPE
                : ddDataType.getText().toString();
        return MemoryToolHelper.normalizeDataType(selected);
    }

    private String getCurrentNumericDataType() {
        String selected = getCurrentSelectedDataType();
        String numeric = MemoryToolHelper.normalizeNumericDataType(selected);
        if (!TextUtils.equals(selected, numeric) && ddDataType != null) {
            ddDataType.setText(numeric, false);
        }
        return numeric;
    }

    private String getCurrentSnapshotDataType() {
        String selected = getCurrentSelectedDataType();
        String snapshotType = MemoryToolHelper.normalizeSnapshotDataType(selected);
        if (TextUtils.equals(snapshotType, MemoryToolHelper.DEFAULT_DATA_TYPE)) {
            snapshotType = MemoryToolHelper.normalizeNumericDataType(selected);
        }
        if (!TextUtils.equals(selected, snapshotType) && ddDataType != null) {
            ddDataType.setText(snapshotType, false);
        }
        return snapshotType;
    }

    private boolean hasAnyInputFocus() {
        return hasFocus(ddTargetPkg) || hasFocus(edtSearchValue) || hasFocus(edtPatchName) || hasFocus(edtPatchAddress) || hasFocus(edtPatchValue)
                || hasFocus(edtResultLimit) || hasFocus(edtMaxResults) || hasFocus(edtDumpBegin) || hasFocus(edtDumpEnd)
                || hasFocus(ddProcess) || hasFocus(ddDataType) || hasFocus(ddSearchMode);
    }

    private boolean hasFocus(View view) {
        return view != null && view.hasFocus();
    }

    private void setOverlayInteractive(boolean interactive) {
        if (wm == null || root == null || overlayLayoutParams == null) return;
        if (interactive
                && !PermsTestVrOverlayCompat.shouldKeepOverlayNonFocusable(this)
                && !isOverlayDropdownActive()
                && (!canPromoteOverlayFocus() || SystemClock.uptimeMillis() < launchPassiveUntil)) {
            interactive = false;
        }
        int flags = PermsTestVrOverlayCompat.buildOverlayFlags(this, BASE_OVERLAY_FLAGS, interactive);
        if (overlayLayoutParams.flags == flags) return;
        overlayLayoutParams.flags = flags;
        try {
            wm.updateViewLayout(root, overlayLayoutParams);
        } catch (Throwable ignored) {
        }
    }

    private void addOverlayView() {
        final int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean vrMode = PermsTestVrOverlayCompat.isEnabled(this);
        String keyW = vrMode ? PREF_VR_OVERLAY_W : PREF_OVERLAY_W;
        String keyH = vrMode ? PREF_VR_OVERLAY_H : PREF_OVERLAY_H;
        String keyX = vrMode ? PREF_VR_OVERLAY_X : PREF_OVERLAY_X;
        String keyY = vrMode ? PREF_VR_OVERLAY_Y : PREF_OVERLAY_Y;
        int defaultWidth = vrMode ? dp(520) : MemoryOverlayWindowSupport.fitOverlayWidth(this, MemoryOverlayWindowSupport.scaleOverlayPx(this, dp(500)));
        int minWidth = vrMode ? dp(500) : MemoryOverlayWindowSupport.fitOverlayWidth(this, MemoryOverlayWindowSupport.scaleOverlayPx(this, dp(480)));
        int savedWidth = sp.getInt(keyW, defaultWidth);
        if (!vrMode) savedWidth = MemoryOverlayWindowSupport.fitOverlayWidth(this, savedWidth);
        if (savedWidth > 0 && savedWidth < minWidth) savedWidth = defaultWidth;
        if (vrMode && savedWidth > dp(720)) savedWidth = defaultWidth;
        int savedHeight = sp.getInt(keyH, WindowManager.LayoutParams.WRAP_CONTENT);
        if (!vrMode && savedHeight > 0) savedHeight = MemoryOverlayWindowSupport.fitOverlayHeight(this, savedHeight);
        if (vrMode && savedHeight > dp(720)) savedHeight = WindowManager.LayoutParams.WRAP_CONTENT;
        int defaultX = vrMode ? dp(24) : MemoryOverlayWindowSupport.scaleOverlayPx(this, dp(16));
        int defaultY = vrMode ? dp(32) : MemoryOverlayWindowSupport.scaleOverlayPx(this, dp(80));
        int savedX = sp.getInt(keyX, defaultX);
        int savedY = sp.getInt(keyY, defaultY);
        if (!vrMode) {
            savedX = MemoryOverlayWindowSupport.fitOverlayX(this, savedX, savedWidth);
            savedY = MemoryOverlayWindowSupport.fitOverlayY(this, savedY, savedHeight);
        }
        if (vrMode) {
            if (savedX < 0 || savedX > dp(720)) savedX = dp(24);
            if (savedY < 0 || savedY > dp(420)) savedY = dp(32);
        }
        overlayLayoutParams = new WindowManager.LayoutParams(
                savedWidth,
                savedHeight,
                type,
                BASE_OVERLAY_FLAGS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        overlayLayoutParams.gravity = Gravity.TOP | Gravity.START;
        overlayLayoutParams.x = savedX;
        overlayLayoutParams.y = savedY;
        try {
            wm.addView(root, overlayLayoutParams);
        } catch (Throwable t) {
            stopSelf();
        }
    }

    private void applyOverlayPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean transparent = sp.getBoolean(MemoryToolHelper.KEY_OVERLAY_TRANSPARENT, true);
        boolean resizable = sp.getBoolean(MemoryToolHelper.KEY_OVERLAY_RESIZABLE, true);
        if (card != null) {
            card.setAlpha(transparent ? 0.88f : 1.0f);
        }
        if (rowSessionButtons != null) {
            rowSessionButtons.setVisibility(shouldShowOverlaySessionButtons() ? View.VISIBLE : View.GONE);
        }
        View stageTool = root == null ? null : root.findViewById(R.id.btnOverlayMemoryStageTool);
        if (stageTool != null) stageTool.setVisibility(shouldAutoStage() ? View.GONE : View.VISIBLE);
        View handle = root.findViewById(R.id.overlayMemoryResizeHandle);
        if (handle != null) handle.setVisibility(resizable ? View.VISIBLE : View.GONE);
    }

    private boolean shouldOnlyShowRunningPackages() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_ONLY_RUNNING_PACKAGES, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldShowRunningPackages() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_SHOW_RUNNING_PACKAGES, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldLoadRunningPackageState() {
        return shouldOnlyShowRunningPackages() || shouldShowRunningPackages();
    }

    private boolean shouldScanAtStartup() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_SCAN_AT_STARTUP, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean shouldExcludeSelfPackage() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_EXCLUDE_SELF_PACKAGE, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean shouldAutoStage() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_AUTO_STAGE, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean shouldShowOverlaySessionButtons() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_OVERLAY_SHOW_SESSION_BUTTONS, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isAllowedTargetPackage(String pkg) {
        String value = pkg == null ? "" : pkg.trim();
        if (TextUtils.isEmpty(value)) return false;
        return !shouldExcludeSelfPackage() || !TextUtils.equals(getPackageName(), value);
    }

    private String getRememberedPackageToolsTargetPackage() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getString(MemoryToolHelper.KEY_LAST_PACKAGE_TOOLS_TARGET, "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private ArrayList<MemoryPackageEntry> filterTargetPackages(List<MemoryPackageEntry> packages) {
        ArrayList<MemoryPackageEntry> out = new ArrayList<>();
        if (packages == null) return out;
        boolean onlyRunning = shouldOnlyShowRunningPackages();
        boolean excludeSelf = shouldExcludeSelfPackage();
        String selfPkg = getPackageName();
        for (MemoryPackageEntry entry : packages) {
            if (entry == null) continue;
            if (onlyRunning && !entry.running) continue;
            if (excludeSelf && TextUtils.equals(selfPkg, entry.pkg)) continue;
            out.add(entry);
        }
        return out;
    }

    private boolean containsPackageItem(String pkg) {
        if (TextUtils.isEmpty(pkg)) return false;
        for (MemoryPackageEntry entry : packageItems) {
            if (entry != null && TextUtils.equals(entry.pkg, pkg)) return true;
        }
        return false;
    }

    private boolean containsOnlyPackageItem(String pkg) {
        if (TextUtils.isEmpty(pkg) || packageItems.size() != 1) return false;
        MemoryPackageEntry entry = packageItems.get(0);
        return entry != null && TextUtils.equals(entry.pkg, pkg);
    }

    private boolean shouldRefreshTargetPackagesBeforeDropdown() {
        if (packageAdapter == null || packageAdapter.getCount() == 0) return true;
        String selectedPackage = getTargetPackage();
        return !TextUtils.isEmpty(selectedPackage)
                && packageAdapter.getCount() <= 1
                && containsOnlyPackageItem(selectedPackage);
    }

    private boolean isPackageInstalled(String pkg) {
        if (TextUtils.isEmpty(pkg)) return false;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private MemoryPackageEntry makePackageEntry(String pkg, boolean running) {
        String label = pkg;
        boolean debuggable = false;
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            debuggable = ai != null && (ai.flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            CharSequence cs = getPackageManager().getApplicationLabel(ai);
            if (cs != null && cs.length() > 0) label = cs.toString();
        } catch (Throwable ignored) {
        }
        return new MemoryPackageEntry(label, pkg, running, debuggable);
    }

    private boolean hasTargetPackageRefreshViews() {
        return root != null && ddTargetPkg != null && packageAdapter != null;
    }

    private boolean hasProcessRefreshViews() {
        return root != null && ddProcess != null && processAdapter != null;
    }

    private void setTargetPackageLoading(boolean loading) {
        targetPackageRefreshInFlight = loading;
        if (rowTargetPackageLoading != null) {
            rowTargetPackageLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void setProcessLoading(boolean loading) {
        processRefreshInFlight = loading;
        if (rowProcessLoading != null) {
            rowProcessLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshTargetPackages() {
        refreshTargetPackages(false);
    }

    private void refreshTargetPackages(boolean openDropdownWhenReady) {
        lastTargetPackageRefresh = SystemClock.uptimeMillis();
        if (openDropdownWhenReady) reopenTargetPackageDropdownAfterRefresh = true;
        if (targetPackageRefreshInFlight) {
            setTargetPackageLoading(true);
            return;
        }
        final int refreshGeneration = ++targetPackageRefreshGeneration;
        activeTargetPackageRefreshGeneration = refreshGeneration;
        setTargetPackageLoading(true);
        appendStatus("Loading target packages...");
        new Thread(() -> {
            List<MemoryPackageEntry> packages = null;
            Throwable failure = null;
            try {
                packages = MemoryToolRuntime.listTargetPackages(getApplicationContext(), shouldLoadRunningPackageState());
            } catch (Throwable t) {
                failure = t;
            }
            final List<MemoryPackageEntry> finalPackages = packages;
            final Throwable finalFailure = failure;
            runOnUiThread(() -> {
                if (refreshGeneration != activeTargetPackageRefreshGeneration) {
                    debugLog("Ignored stale target package refresh.");
                    return;
                }
                activeTargetPackageRefreshGeneration = 0;
                if (!hasTargetPackageRefreshViews()) {
                    targetPackageRefreshInFlight = false;
                    pendingPackageListUpdate = null;
                    reopenTargetPackageDropdownAfterRefresh = false;
                    debugLog("Skipped target package refresh callback after overlay detached.");
                    return;
                }
                setTargetPackageLoading(false);
                if (finalFailure != null) {
                    pendingPackageListUpdate = null;
                    reopenTargetPackageDropdownAfterRefresh = false;
                    appendStatus("Target package refresh failed: " + finalFailure.getClass().getSimpleName() + ": " + finalFailure.getMessage());
                    debugLog("target package refresh failed", finalFailure);
                    return;
                }
                updateTargetPackageList(finalPackages);
            });
        }, "MemoryOverlayPackages").start();
    }

    private void updateTargetPackageList(List<MemoryPackageEntry> packages) {
        if (!hasTargetPackageRefreshViews()) {
            pendingPackageListUpdate = null;
            reopenTargetPackageDropdownAfterRefresh = false;
            return;
        }
        if (isDropdownShowing(ddTargetPkg)) {
            pendingPackageListUpdate = packages == null ? new ArrayList<>() : new ArrayList<>(packages);
            return;
        }
        updateTargetPackageListNow(packages);
    }

    private void updateTargetPackageListNow(List<MemoryPackageEntry> packages) {
        if (!hasTargetPackageRefreshViews()) {
            pendingPackageListUpdate = null;
            reopenTargetPackageDropdownAfterRefresh = false;
            return;
        }
        packageItems.clear();
        packageItems.addAll(filterTargetPackages(packages));

        String selected = !TextUtils.isEmpty(pendingSelectPkg) ? pendingSelectPkg : getTargetPackage();
        String remembered = getRememberedPackageToolsTargetPackage();
        ensurePackageEntryPresent(selected, true);
        ensurePackageEntryPresent(remembered, false);

        if (packageAdapter != null) {
            packageAdapter.setItems(packageItems);
        }

        if (TextUtils.isEmpty(selected) && !packageItems.isEmpty()) {
            ddTargetPkg.setText("", false);
        } else if (!TextUtils.isEmpty(selected)) {
            ddTargetPkg.setText(selected, false);
        }
        pendingSelectPkg = null;
        boolean shouldOpen = reopenTargetPackageDropdownAfterRefresh;
        reopenTargetPackageDropdownAfterRefresh = false;
        if (shouldOpen && ddTargetPkg != null && getAdapterCount(ddTargetPkg) > 0) {
            showOverlayDropdownNow(ddTargetPkg);
        }
    }

    private void ensurePackageEntryPresent(String pkg, boolean runningHint) {
        if (TextUtils.isEmpty(pkg)) return;
        String value = pkg.trim();
        if (TextUtils.isEmpty(value) || !value.contains(".") || !isAllowedTargetPackage(value)) return;
        if (containsPackageItem(value)) return;
        packageItems.add(0, makePackageEntry(value, runningHint));
    }

    private void refreshProcesses() {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            appendStatus("Enter a target package first.");
            return;
        }
        if (!hasProcessRefreshViews()) {
            processRefreshInFlight = false;
            activeProcessRefreshGeneration = 0;
            pendingProcessListUpdate = null;
            reopenProcessDropdownAfterRefresh = false;
            debugLog("Skipped process refresh because the main overlay is detached.");
            return;
        }
        if (processRefreshInFlight) {
            setProcessLoading(true);
            return;
        }
        final int refreshGeneration = ++processRefreshGeneration;
        activeProcessRefreshGeneration = refreshGeneration;
        setProcessLoading(true);
        appendStatus("Loading running processes for " + pkg + "...");
        new Thread(() -> {
            List<MemoryProcessEntry> processes = MemoryToolRuntime.listTargetProcesses(getApplicationContext(), pkg);
            runOnUiThread(() -> {
                if (refreshGeneration != activeProcessRefreshGeneration) {
                    debugLog("Ignored stale process refresh for " + pkg + ".");
                    return;
                }
                activeProcessRefreshGeneration = 0;
                if (!hasProcessRefreshViews()) {
                    processRefreshInFlight = false;
                    pendingProcessListUpdate = null;
                    reopenProcessDropdownAfterRefresh = false;
                    debugLog("Skipped process refresh callback after overlay detached.");
                    return;
                }
                setProcessLoading(false);
                updateProcessList(processes);
            });
        }, "MemoryOverlayRefresh").start();
    }

    private void updateProcessList(List<MemoryProcessEntry> processes) {
        if (!hasProcessRefreshViews()) {
            pendingProcessListUpdate = null;
            reopenProcessDropdownAfterRefresh = false;
            return;
        }
        if (isDropdownShowing(ddProcess)) {
            pendingProcessListUpdate = processes == null ? new ArrayList<>() : new ArrayList<>(processes);
            return;
        }
        updateProcessListNow(processes);
    }

    private void updateProcessListNow(List<MemoryProcessEntry> processes) {
        if (!hasProcessRefreshViews()) {
            pendingProcessListUpdate = null;
            reopenProcessDropdownAfterRefresh = false;
            return;
        }
        processItems.clear();
        processItems.add(new MemoryProcessEntry("", "Auto-select"));
        if (processes != null) processItems.addAll(processes);
        processAdapter.notifyDataSetChanged();
        if (!processItems.isEmpty()) {
            String current = ddProcess == null || ddProcess.getText() == null ? "" : ddProcess.getText().toString().trim();
            if (TextUtils.isEmpty(current) || current.toLowerCase(Locale.US).startsWith("auto-select")) {
                ddProcess.setText(processItems.get(0).toString(), false);
            } else {
                String currentPid = getSelectedPidFromText(current);
                if (!TextUtils.isEmpty(currentPid) && !selectPid(currentPid, false)) {
                    ddProcess.setText(processItems.get(0).toString(), false);
                }
            }
        }
        if (processes != null && !processes.isEmpty()) {
            ensurePackageEntryPresent(getTargetPackage(), true);
            if (packageAdapter != null) packageAdapter.setItems(packageItems);
        }
        lastResolvedAutoPid = chooseAutoProcessPidFromItems();
        if (!TextUtils.isEmpty(pendingSelectPid)) {
            selectPid(pendingSelectPid);
            pendingSelectPid = null;
        }
        boolean shouldOpen = reopenProcessDropdownAfterRefresh;
        reopenProcessDropdownAfterRefresh = false;
        appendStatus(processes == null || processes.isEmpty()
                ? "No running processes found for the current target package."
                : ("Found " + processes.size() + " running process" + (processes.size() == 1 ? "" : "es") + "."));
        if (shouldOpen && ddProcess != null && getAdapterCount(ddProcess) > 0) {
            showOverlayDropdownNow(ddProcess);
        }
    }

    private void selectPid(String pid) {
        selectPid(pid, true);
    }

    private boolean selectPid(String pid, boolean allowFallbackInsert) {
        if (ddProcess == null || processAdapter == null) return false;
        if (TextUtils.isEmpty(pid) || processItems.isEmpty()) return false;
        for (MemoryProcessEntry item : processItems) {
            if (pid.equals(item.pid)) {
                ddProcess.setText(item.toString(), false);
                return true;
            }
        }
        if (allowFallbackInsert) {
            MemoryProcessEntry fallback = new MemoryProcessEntry(pid, getTargetPackage());
            processItems.add(fallback);
            if (processAdapter != null) processAdapter.notifyDataSetChanged();
            ddProcess.setText(fallback.toString(), false);
            return true;
        }
        return false;
    }

    private static String getSelectedPidFromText(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String v = value.trim();
        int sep = v.indexOf('·');
        if (sep > 0) return v.substring(0, sep).trim();
        int space = v.indexOf(' ');
        if (space > 0) return v.substring(0, space).trim();
        return v;
    }

    private String getTargetPackage() {
        if (ddTargetPkg == null) {
            return TextUtils.isEmpty(pendingSelectPkg) ? "" : pendingSelectPkg.trim();
        }
        return ddTargetPkg.getText() == null ? "" : ddTargetPkg.getText().toString().trim();
    }

    private String getSelectedPid() {
        if (ddProcess == null) {
            return TextUtils.isEmpty(pendingSelectPid) ? resolveAutoSelectedPid() : pendingSelectPid.trim();
        }
        CharSequence text = ddProcess.getText();
        if (text == null) return resolveAutoSelectedPid();
        String value = text.toString().trim();
        if (value.isEmpty() || value.toLowerCase(Locale.US).startsWith("auto-select")) {
            return resolveAutoSelectedPid();
        }
        return getSelectedPidFromText(value);
    }

    private String getSelectedManualProcessPid() {
        if (ddProcess == null) return TextUtils.isEmpty(pendingSelectPid) ? "" : pendingSelectPid.trim();
        CharSequence text = ddProcess.getText();
        if (text == null) return "";
        String value = text.toString().trim();
        if (value.isEmpty() || value.toLowerCase(Locale.US).startsWith("auto-select")) return "";
        return getSelectedPidFromText(value);
    }

    private String resolveAutoSelectedPid() {
        String pid = chooseAutoProcessPidFromItems();
        if (!TextUtils.isEmpty(pid)) {
            lastResolvedAutoPid = pid;
            return pid;
        }
        return TextUtils.isEmpty(lastResolvedAutoPid) ? null : lastResolvedAutoPid;
    }

    private String chooseAutoProcessPidFromItems() {
        if (processItems.size() <= 1) return null;
        String targetPkg = getTargetPackage();
        for (int i = 1; i < processItems.size(); i++) {
            MemoryProcessEntry item = processItems.get(i);
            if (item == null || TextUtils.isEmpty(item.pid)) continue;
            String name = item.name == null ? "" : item.name.trim();
            if (!TextUtils.isEmpty(targetPkg) && (TextUtils.equals(name, targetPkg) || name.startsWith(targetPkg + " "))) {
                return item.pid;
            }
        }
        if (processItems.size() == 2) {
            MemoryProcessEntry item = processItems.get(1);
            return item == null ? null : item.pid;
        }
        return null;
    }

    private boolean isAutoProcessSelection() {
        CharSequence text = ddProcess == null ? null : ddProcess.getText();
        String value = text == null ? "" : text.toString().trim();
        return value.isEmpty() || value.toLowerCase(Locale.US).startsWith("auto-select");
    }

    private boolean commandNeedsPid(String command) {
        if (TextUtils.isEmpty(command)) return false;
        String c = command.trim().toLowerCase(Locale.US);
        return !("detach".equals(c) || "clear-state".equals(c));
    }

    private void resetScanRangeSession() {
        clearTimerFinderBaselineTracking();
        scanRangePageIndex = 0;
        scanRangeStepResults = Math.max(0, MemoryToolHelper.normalizeMaxResults(getMaxScanResults()));
        scanRangeSkipResults = 0;
        scanRangeBaseCommand = null;
        scanRangeBaseDataType = null;
        scanRangeBaseValue = null;
        scanRangeBaseScanValue = null;
        scanRangeResultCountByPage.clear();
        scanRangeBaselineCountByPage.clear();
        updateScanRangeStatus();
    }

    private void configureScanRangeBase(String command, @Nullable String dataType, @Nullable String value, @Nullable String scanValue) {
        clearTimerFinderBaselineTracking();
        scanRangePageIndex = 0;
        scanRangeStepResults = Math.max(0, MemoryToolHelper.normalizeMaxResults(getMaxScanResults()));
        scanRangeSkipResults = 0;
        scanRangeBaseCommand = command;
        scanRangeBaseDataType = dataType;
        scanRangeBaseValue = value;
        scanRangeBaseScanValue = scanValue;
        scanRangeResultCountByPage.clear();
        scanRangeBaselineCountByPage.clear();
        updateScanRangeStatus();
    }

    private int getScanRangeStepResults() {
        int step = scanRangeStepResults > 0 ? scanRangeStepResults : MemoryToolHelper.normalizeMaxResults(getMaxScanResults());
        return Math.max(0, step);
    }

    private String currentScanRangeStatePath() {
        return MemoryToolHelper.statePathForRange(scanRangePageIndex);
    }

    private int currentScanRangeSkipResults() {
        int step = getScanRangeStepResults();
        if (step <= 0) return 0;
        return Math.max(0, scanRangePageIndex) * step;
    }

    private void moveScanRangeWindow(int delta) {
        int step = getScanRangeStepResults();
        if (step <= 0) {
            appendStatus("Range navigation needs a normal Cap value. Cap 0 / unlimited is disabled, so there is no fixed range size to page by.");
            return;
        }
        if (TextUtils.isEmpty(scanRangeBaseCommand)) {
            appendStatus("Start a New Scan first. The range buttons reuse that New Scan as the baseline for each range page.");
            return;
        }
        int nextPage = scanRangePageIndex + delta;
        if (nextPage < 0) {
            appendStatus("Already on Range 1.");
            return;
        }
        activateScanRangePage(nextPage);
    }

    private void resetScanRangeToFirst() {
        if (scanRangePageIndex == 0) {
            updateScanRangeStatus();
            appendStatus("Range 1 is already active.");
            return;
        }
        activateScanRangePage(0);
    }

    private void activateScanRangePage(int pageIndex) {
        int safePage = Math.max(0, pageIndex);
        if (isTimerFinderRangeSession() && timerFinderBaselinePageCount > 0 && safePage >= timerFinderBaselinePageCount) {
            appendStatus("Timer Finder baseline captured " + timerFinderBaselinePageCount + " range"
                    + (timerFinderBaselinePageCount == 1 ? "" : "s")
                    + ". Tap Timer Baseline again to capture more/current ranges.");
            return;
        }
        scanRangePageIndex = safePage;
        scanRangeSkipResults = currentScanRangeSkipResults();
        updateScanRangeStatus();

        Integer cachedCount = scanRangeResultCountByPage.get(scanRangePageIndex);
        if (cachedCount != null) {
            restoreScanRangePageFromState(cachedCount);
            return;
        }

        clearOverlayScanState(false);
        appendStatus("Starting " + formatScanRangeName()
                + ". Baseline skips the first " + scanRangeSkipResults + " matching address"
                + (scanRangeSkipResults == 1 ? "" : "es") + ".");
        runScanRangeBaseCommand();
    }

    private void runScanRangeBaseCommand() {
        if (TextUtils.isEmpty(scanRangeBaseCommand)) {
            appendStatus("Start a New Scan before using range navigation.");
            return;
        }
        runMemoryCommand(
                scanRangeBaseCommand,
                scanRangeBaseDataType,
                scanRangeBaseValue,
                null,
                null,
                null,
                true,
                scanRangeBaseScanValue);
    }

    private void restoreScanRangePageFromState(int resultCount) {
        activeResultCount = Math.max(0, resultCount);
        hasActiveResultSet = activeResultCount > 0;
        String stateJson = null;
        if (activeResultCount <= MAX_STATE_ROWS_TO_LOAD_FOR_UI) {
            stateJson = readMemoryStateJson(getTargetPackage(), currentScanRangeStatePath());
        }
        if (!TextUtils.isEmpty(stateJson)) {
            updateResultListFromState(stateJson, scanRangeBaseScanValue);
        } else {
            updateResultCountOnly(activeResultCount);
        }
        appendStatus(formatScanRangeName() + " active. Refine commands now use this range's own saved state.");
    }

    private String formatScanRangeName() {
        return "Range " + (scanRangePageIndex + 1);
    }

    private void configureAutoRangeLimitField() {
        if (edtAutoRangeLimit == null) return;
        try {
            int savedLimit = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(
                    MemoryToolHelper.KEY_AUTO_RANGE_PAGE_LIMIT,
                    MemoryToolHelper.DEFAULT_AUTO_RANGE_PAGE_LIMIT);
            edtAutoRangeLimit.setText(Integer.toString(Math.max(0, savedLimit)));
            edtAutoRangeLimit.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    persistAutoRangePageLimitFromField(false);
                    updateScanRangeStatus();
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private int getAutoRangePageLimit() {
        try {
            if (edtAutoRangeLimit != null) {
                String raw = edtAutoRangeLimit.getText() == null ? "" : edtAutoRangeLimit.getText().toString().trim();
                if (!raw.isEmpty()) {
                    return Math.max(0, Integer.parseInt(raw));
                }
            }
        } catch (Throwable ignored) {
        }
        return Math.max(0, getSharedPreferences(PREFS, MODE_PRIVATE).getInt(
                MemoryToolHelper.KEY_AUTO_RANGE_PAGE_LIMIT,
                MemoryToolHelper.DEFAULT_AUTO_RANGE_PAGE_LIMIT));
    }

    private void persistAutoRangePageLimitFromField(boolean useDefaultWhenBlank) {
        int value;
        try {
            String raw = edtAutoRangeLimit == null || edtAutoRangeLimit.getText() == null
                    ? ""
                    : edtAutoRangeLimit.getText().toString().trim();
            if (raw.isEmpty()) {
                if (!useDefaultWhenBlank) return;
                value = MemoryToolHelper.DEFAULT_AUTO_RANGE_PAGE_LIMIT;
            } else {
                value = Math.max(0, Integer.parseInt(raw));
            }
        } catch (Throwable ignored) {
            value = MemoryToolHelper.DEFAULT_AUTO_RANGE_PAGE_LIMIT;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(MemoryToolHelper.KEY_AUTO_RANGE_PAGE_LIMIT, value)
                .apply();
    }

    private void updateScanRangeStatus() {
        if (txtScanRangeStatus == null) return;
        int step = getScanRangeStepResults();
        int start = step > 0 ? (scanRangePageIndex * step) + 1 : 1;
        int end = step > 0 ? (scanRangePageIndex + 1) * step : 0;
        String autoLimitText = shouldAutoRangeMemorySearch()
                ? " · Auto " + (getAutoRangePageLimit() == 0 ? "all" : Integer.toString(getAutoRangePageLimit()))
                : "";
        if (step <= 0) {
            txtScanRangeStatus.setText(formatScanRangeName() + " · cap unlimited" + autoLimitText);
        } else if (scanRangePageIndex == 0) {
            txtScanRangeStatus.setText("Range 1 · 1-" + end + autoLimitText);
        } else {
            txtScanRangeStatus.setText(formatScanRangeName() + " · " + start + "-" + end + autoLimitText);
        }
    }

    private boolean usesMemorySearchState(String command, boolean scanOperation) {
        if (scanOperation) return true;
        if (TextUtils.isEmpty(command)) return false;
        String c = command.trim().toLowerCase(Locale.US);
        return c.startsWith("filter") || "patch".equals(c);
    }

    private boolean commandStartsFreshResultSet(String command) {
        if (TextUtils.isEmpty(command)) return false;
        String c = command.trim().toLowerCase(Locale.US);
        return "snapshot".equals(c) || c.startsWith("find") || "search-magic".equals(c) || "search-bytes".equals(c) || "search-bytes-mask".equals(c);
    }

    private void runMemoryScanCommand(boolean newScan) {
        String mode = getCurrentSearchMode();
        String rawDataType = getCurrentSelectedDataType();
        boolean stringScan = TextUtils.equals(MemoryToolHelper.normalizeDataType(rawDataType), "string");
        boolean previousCompareMode = MemoryToolHelper.isIncreasedSearchMode(mode)
                || MemoryToolHelper.isDecreasedSearchMode(mode)
                || MemoryToolHelper.isUnchangedSearchMode(mode)
                || MemoryToolHelper.isChangedSearchMode(mode);
        boolean inputCompareMode = MemoryToolHelper.isValueGreaterThanInputSearchMode(mode)
                || MemoryToolHelper.isValueLessThanInputSearchMode(mode);
        boolean exactMode = MemoryToolHelper.isExactSearchMode(mode);
        boolean unknownMode = MemoryToolHelper.isUnknownSnapshotSearchMode(mode);

        if (stringScan && !exactMode) {
            mode = MemoryToolHelper.SEARCH_MODE_EXACT;
            exactMode = true;
            previousCompareMode = false;
            inputCompareMode = false;
            unknownMode = false;
            if (ddSearchMode != null) ddSearchMode.setText(mode, false);
            updateSearchValueHint("String value");
            appendStatus("String scans use Exact mode with the Search value text.");
        }

        String command;
        String dataType = rawDataType;
        String searchValue = edtSearchValue.getText() == null ? null : edtSearchValue.getText().toString();
        boolean startsSnapshot = newScan && (unknownMode || previousCompareMode);

        if (startsSnapshot || unknownMode) {
            if (!newScan && hasActiveResultSet && unknownMode) {
                appendStatus("Unknown mode starts a fresh snapshot. Use Increased, Decreased, Changed, Unchanged, or Exact to refine existing results.");
                return;
            }
            command = "snapshot";
            dataType = getCurrentSnapshotDataType();
            searchValue = null;
        } else if (previousCompareMode) {
            if (!hasActiveResultSet) {
                appendStatus(mode + " needs an active snapshot/result set first. Press New to start a baseline snapshot.");
                return;
            }
            if (MemoryToolHelper.isIncreasedSearchMode(mode)) {
                command = "filter-gt";
            } else if (MemoryToolHelper.isDecreasedSearchMode(mode)) {
                command = "filter-lt";
            } else if (MemoryToolHelper.isUnchangedSearchMode(mode)) {
                command = "filter-unchanged";
            } else {
                command = "filter-changed";
            }
            searchValue = null;
            dataType = null;
        } else if (inputCompareMode) {
            if (TextUtils.isEmpty(searchValue)) {
                appendStatus("Enter a search value first.");
                return;
            }
            command = MemoryToolHelper.isValueGreaterThanInputSearchMode(mode)
                    ? (newScan || !hasActiveResultSet ? "find-gt" : "filter-gt")
                    : (newScan || !hasActiveResultSet ? "find-lt" : "filter-lt");
            dataType = getCurrentNumericDataType();
        } else {
            if (TextUtils.isEmpty(searchValue)) {
                appendStatus(stringScan ? "Enter a string search value first." : "Enter a search value first.");
                return;
            }
            command = newScan || !hasActiveResultSet ? "find" : "filter";
            dataType = command.startsWith("find") ? getCurrentSelectedDataType() : null;
        }

        String commandDataType = ("snapshot".equals(command) || command.startsWith("find")) ? dataType : null;
        if (newScan && commandStartsFreshResultSet(command)) {
            configureScanRangeBase(command, commandDataType, searchValue, searchValue);
        } else if (!newScan && shouldRunAutoRangeRefine(command)) {
            runAutoRangeRefineCommand(command, commandDataType, searchValue, searchValue);
            return;
        }

        runMemoryCommand(
                command,
                commandDataType,
                searchValue,
                null,
                null,
                null,
                true,
                searchValue);
    }

    private MemoryToolRuntime.CmdResult runBackendShellCommandCaptureSync(String cmd) {
        synchronized (backendShellLock) {
            return MemoryToolRuntime.runShellCommandCaptureSync(getApplicationContext(), cmd);
        }
    }

    private boolean beginMemoryCommand(String command) {
        if (memoryCommandInFlight) {
            String running = TextUtils.isEmpty(memoryCommandInFlightName) ? "another memory command" : memoryCommandInFlightName;
            appendStatus("Memory command already running: " + running + ". Wait for it to finish before starting " + command + ".");
            return false;
        }
        memoryCommandInFlight = true;
        memoryCommandInFlightName = command;
        return true;
    }

    private void finishMemoryCommand(String command) {
        memoryCommandInFlight = false;
        memoryCommandInFlightName = null;
        setToolOverlaysBackendBusy(false);
    }

    private void runMemoryCommand(String command, String dataType, String value, String begin, String end) {
        runMemoryCommand(command, dataType, value, begin, end, null, false, null);
    }

    private void runMemoryCommand(String command,
                                  String dataType,
                                  String value,
                                  String begin,
                                  String end,
                                  @Nullable String stateJsonOverride,
                                  boolean scanOperation,
                                  @Nullable String scanValue) {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            appendStatus("Enter a target package first.");
            return;
        }
        final String targetPid = getSelectedPid();
        final boolean autoProcessSelection = isAutoProcessSelection();
        if (commandNeedsPid(command) && TextUtils.isEmpty(targetPid)) {
            appendStatus("Looking up PID for " + pkg + "...");
        }
        if ("attach".equals(command) && autoProcessSelection && !TextUtils.isEmpty(targetPid)) {
            selectPid(targetPid);
        }
        final int requestedMaxScanResults = getMaxScanResults();
        if (scanOperation && requestedMaxScanResults == 0) {
            showCapZeroBlockedWarning();
            return;
        }
        final int maxScanResults = MemoryToolHelper.effectiveMaxResultsForCommand(command, requestedMaxScanResults);
        debugLog("command start command=" + command
                + " pkg=" + cleanLogValue(pkg)
                + " pid=" + cleanLogValue(targetPid)
                + " scan=" + scanOperation
                + " active=" + hasActiveResultSet
                + " results=" + activeResultCount
                + " autoRange=" + shouldAutoRangeMemorySearch());
        final int commandRangePageIndex = scanOperation ? scanRangePageIndex : -1;
        final String commandRangeName = commandRangePageIndex >= 0 ? ("Range " + (commandRangePageIndex + 1)) : "Range 1";
        final int commandRangeSkipResults = scanOperation && commandStartsFreshResultSet(command) ? currentScanRangeSkipResults() : 0;
        final String commandStatePath = usesMemorySearchState(command, scanOperation)
                ? MemoryToolHelper.statePathForRange(Math.max(0, scanRangePageIndex))
                : MemoryToolHelper.statePathForCommand();
        showCommandStartStatus(buildRunningCommandStatus(command, scanOperation, requestedMaxScanResults, maxScanResults));
        final String restoreStateAfterSelectedPatch = "patch".equals(command) && !TextUtils.isEmpty(stateJsonOverride)
                ? lastStateJson
                : null;
        final boolean saveDumpToDisk = "dump".equals(command) && shouldSaveDumpToDisk();
        final String dumpBeginForFile = begin;
        final String dumpEndForFile = end;
        if (!beginMemoryCommand(command)) return;
        setToolOverlaysBackendBusy(true);
        new Thread(() -> {
            try {
                if (shouldAutoStage()) {
                    MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                    if (install == null || install.exitCode != 0) {
                        final String msg = summarizeResult("apk-medit could not be staged for the current backend.", install);
                        runOnUiThread(() -> appendStatus(msg));
                        return;
                    }
                }

                String effectivePid = targetPid;
                if (commandNeedsPid(command)) {
                    String resolvedPid = resolvePidForCommand(pkg, targetPid, autoProcessSelection);
                    if (TextUtils.isEmpty(resolvedPid)) {
                        runOnUiThread(() -> appendStatus("Package is not running: " + pkg));
                        return;
                    }
                    effectivePid = resolvedPid;
                    if (!TextUtils.equals(resolvedPid, targetPid)) {
                        final String pidForUi = resolvedPid;
                        runOnUiThread(() -> selectPid(pidForUi));
                    }
                }

                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
                String shellCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                        pkg,
                        MemoryToolRuntime.PUBLIC_BIN_DIR,
                        withoutPtrace,
                        command,
                        effectivePid,
                        dataType,
                        value,
                        begin,
                        end,
                        stateJsonOverride,
                        maxScanResults,
                        shouldStringCaseSensitive(),
                        shouldStringPatchTruncate(),
                        commandRangeSkipResults,
                        commandStatePath
                );
                MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(shellCmd);
                if (shouldRetryAttachAfterCorruptState(command, r)) {
                    MemoryToolRuntime.CmdResult clear = runBackendShellCommandCaptureSync(MemoryToolHelper.buildClearStateCommand(pkg));
                    debugLog("attach retry after corrupt state clear exit=" + (clear == null ? -1 : clear.exitCode));
                    r = runBackendShellCommandCaptureSync(shellCmd);
                }
                if (!TextUtils.isEmpty(restoreStateAfterSelectedPatch)) {
                    writeMemoryStateJson(pkg, restoreStateAfterSelectedPatch, commandStatePath);
                }
                final boolean failed = isMemoryCommandFailure(r);
                int outputResultCount = scanOperation && !failed ? countFoundResultsFromOutput(r) : -1;
                boolean shouldReadState = scanOperation && !failed && shouldLoadStateForVisibleRows(outputResultCount);
                String stateJson = shouldReadState ? readMemoryStateJson(pkg, commandStatePath) : null;
                String resultPrefix = failed ? (command + " failed.") : (command + " finished.");
                if (!failed && scanOperation && commandStartsFreshResultSet(command) && commandRangeSkipResults > 0) {
                    resultPrefix += "\n" + commandRangeName + " skipped first " + commandRangeSkipResults + " matching address" + (commandRangeSkipResults == 1 ? "" : "es") + ".";
                }
                if (!failed && MemoryToolHelper.isHighCapOrUnlimited(maxScanResults) && scanOperation) {
                    resultPrefix += "\n" + buildHighCapStatusLine(maxScanResults, pkg);
                }
                if (!failed && saveDumpToDisk) {
                    resultPrefix += "\n" + saveDumpTextToDisk(pkg, dumpBeginForFile, dumpEndForFile, collectCommandOutput(r));
                }
                final String prefix = resultPrefix;
                final String stateForUi = stateJson;
                final int outputCountForUi = outputResultCount;
                final MemoryToolRuntime.CmdResult resultForUi = r;
                runOnUiThread(() -> {
                    if (!failed && scanOperation && commandRangePageIndex >= 0 && outputCountForUi >= 0) {
                        if (commandStartsFreshResultSet(command)) {
                            scanRangeBaselineCountByPage.put(commandRangePageIndex, outputCountForUi);
                        }
                        scanRangeResultCountByPage.put(commandRangePageIndex, outputCountForUi);
                    }
                    debugLog("command finish command=" + command
                            + " failed=" + failed
                            + " outputCount=" + outputCountForUi
                            + " stateForUi=" + (!TextUtils.isEmpty(stateForUi)));
                    showMemoryCommandResult(command, prefix, resultForUi, stateForUi, scanValue, outputCountForUi);
                    releaseOverlayInputFocus();
                    if (root != null) root.postDelayed(this::releaseOverlayInputFocus, 120L);
                });
            } finally {
                runOnUiThread(() -> finishMemoryCommand(command));
            }
        }, "MemoryOverlayCommand").start();
    }


    private boolean shouldRetryAttachAfterCorruptState(String command, MemoryToolRuntime.CmdResult result) {
        if (!"attach".equals(command) || result == null || !isMemoryCommandFailure(result)) return false;
        String combined = ((result.stdout == null ? "" : result.stdout) + "\n"
                + (result.stderr == null ? "" : result.stderr)).toLowerCase(Locale.US);
        return combined.indexOf('\u0000') >= 0
                || (combined.contains("invalid character")
                && combined.contains("looking for beginning of value"));
    }


    private boolean shouldRunAutoRangeRefine(String command) {
        if (!shouldAutoRangeMemorySearch()) return false;
        if (autoRangeInFlight) return false;
        if (TextUtils.isEmpty(command) || commandStartsFreshResultSet(command)) return false;
        if (TextUtils.isEmpty(scanRangeBaseCommand)) return false;
        if (getScanRangeStepResults() <= 0) return false;
        String c = command.trim().toLowerCase(Locale.US);
        return c.startsWith("filter");
    }

    private void runAutoRangeRefineCommand(String command,
                                           @Nullable String dataType,
                                           @Nullable String value,
                                           @Nullable String scanValue) {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            appendStatus("Enter a target package first.");
            return;
        }
        final String targetPid = getSelectedPid();
        final boolean autoProcessSelection = isAutoProcessSelection();
        if (commandNeedsPid(command) && TextUtils.isEmpty(targetPid)) {
            appendStatus("Looking up PID for " + pkg + "...");
        }
        final int requestedMaxScanResults = getMaxScanResults();
        if (requestedMaxScanResults == 0) {
            showCapZeroBlockedWarning();
            return;
        }
        final int step = getScanRangeStepResults();
        if (step <= 0) {
            appendStatus("Auto Range needs a normal Cap value. Cap 0 / unlimited is disabled, so there is no fixed range size to page by.");
            return;
        }
        final int autoRangePageLimit = getAutoRangePageLimit();
        persistAutoRangePageLimitFromField(true);
        autoRangeInFlight = true;
        showCommandStartStatus("Auto Range running " + command + " across saved range pages"
                + (autoRangePageLimit == 0 ? " with no range limit..." : " up to " + autoRangePageLimit + " ranges..."));
        setToolOverlaysBackendBusy(true);
        new Thread(() -> {
            int pagesProcessed = 0;
            int totalMatches = 0;
            int firstNonEmptyPage = -1;
            boolean hitPageSafetyLimit = false;
            String failureMessage = null;
            try {
                if (shouldAutoStage()) {
                    MemoryToolRuntime.CmdResult install = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
                    if (install == null || install.exitCode != 0) {
                        failureMessage = summarizeResult("apk-medit could not be staged for the current backend.", install);
                        return;
                    }
                }

                String effectivePid = targetPid;
                if (commandNeedsPid(command)) {
                    String resolvedPid = resolvePidForCommand(pkg, targetPid, autoProcessSelection);
                    if (TextUtils.isEmpty(resolvedPid)) {
                        failureMessage = "Package is not running: " + pkg;
                        return;
                    }
                    effectivePid = resolvedPid;
                    if (!TextUtils.equals(resolvedPid, targetPid)) {
                        final String pidForUi = resolvedPid;
                        runOnUiThread(() -> selectPid(pidForUi));
                    }
                }

                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                boolean withoutPtrace = sp.getBoolean(MemoryToolHelper.KEY_WITHOUT_PTRACE, false);
                int maxScanResults = MemoryToolHelper.effectiveMaxResultsForCommand(command, requestedMaxScanResults);

                for (int page = 0; autoRangePageLimit == 0 || page < autoRangePageLimit; page++) {
                    int baselineCount = ensureAutoRangeBaselineForPage(pkg, effectivePid, withoutPtrace, maxScanResults, page);
                    if (baselineCount < 0) {
                        failureMessage = "Auto Range failed while preparing Range " + (page + 1) + ".";
                        break;
                    }

                    int currentCount = Math.max(0, scanRangeResultCountByPage.containsKey(page)
                            ? scanRangeResultCountByPage.get(page)
                            : baselineCount);
                    int refinedCount = 0;
                    if (currentCount > 0) {
                        String statePath = MemoryToolHelper.statePathForRange(page);
                        MemoryToolRuntime.CmdResult filterResult = runMemoryCommandCaptureSync(
                                pkg,
                                effectivePid,
                                withoutPtrace,
                                command,
                                dataType,
                                value,
                                maxScanResults,
                                statePath,
                                0);
                        if (isMemoryCommandFailure(filterResult)) {
                            failureMessage = summarizeResult("Auto Range failed on Range " + (page + 1) + ".", filterResult);
                            break;
                        }
                        refinedCount = Math.max(0, countFoundResultsFromOutput(filterResult));
                    }

                    final int pageKey = page;
                    scanRangeResultCountByPage.put(pageKey, refinedCount);
                    pagesProcessed++;
                    totalMatches += refinedCount;
                    if (refinedCount > 0 && firstNonEmptyPage < 0) {
                        firstNonEmptyPage = page;
                    }
                    if (baselineCount < step) {
                        break;
                    }
                    if (autoRangePageLimit > 0 && page == autoRangePageLimit - 1) {
                        hitPageSafetyLimit = true;
                    }
                }
            } finally {
                final int finalPagesProcessed = pagesProcessed;
                final int finalTotalMatches = totalMatches;
                final int finalFirstNonEmptyPage = firstNonEmptyPage;
                final boolean finalHitPageSafetyLimit = hitPageSafetyLimit;
                final String finalFailureMessage = failureMessage;
                runOnUiThread(() -> {
                    autoRangeInFlight = false;
                    setToolOverlaysBackendBusy(false);
                    if (!TextUtils.isEmpty(finalFailureMessage)) {
                        appendStatus(finalFailureMessage);
                        return;
                    }
                    int displayPage = finalFirstNonEmptyPage >= 0 ? finalFirstNonEmptyPage : 0;
                    scanRangePageIndex = Math.max(0, displayPage);
                    scanRangeSkipResults = currentScanRangeSkipResults();
                    updateScanRangeStatus();
                    Integer displayCount = scanRangeResultCountByPage.get(scanRangePageIndex);
                    int safeDisplayCount = displayCount == null ? 0 : Math.max(0, displayCount);
                    if (safeDisplayCount > 0 && shouldLoadStateForVisibleRows(safeDisplayCount)) {
                        String stateJson = readMemoryStateJson(pkg, currentScanRangeStatePath());
                        updateResultListFromState(stateJson, scanValue);
                    } else {
                        updateResultCountOnly(safeDisplayCount);
                    }
                    String msg = "Auto Range finished. Refined " + finalPagesProcessed + " range"
                            + (finalPagesProcessed == 1 ? "" : "s") + "; total matches: " + finalTotalMatches + ".";
                    if (finalFirstNonEmptyPage >= 0) {
                        msg += " Showing " + formatScanRangeName() + "; use Prev/Next to inspect other ranges.";
                    } else {
                        msg += " No matching addresses remain in the scanned ranges.";
                    }
                    if (finalHitPageSafetyLimit) {
                        msg += " Stopped at the configured Auto Range limit of " + autoRangePageLimit + " ranges.";
                    }
                    appendStatus(msg);
                    releaseOverlayInputFocus();
                    if (root != null) root.postDelayed(this::releaseOverlayInputFocus, 120L);
                });
            }
        }, "MemoryOverlayAutoRange").start();
    }

    private int ensureAutoRangeBaselineForPage(String pkg,
                                               String effectivePid,
                                               boolean withoutPtrace,
                                               int maxScanResults,
                                               int pageIndex) {
        Integer existing = scanRangeBaselineCountByPage.get(pageIndex);
        if (existing != null) return Math.max(0, existing);
        int skip = Math.max(0, pageIndex) * getScanRangeStepResults();
        String statePath = MemoryToolHelper.statePathForRange(pageIndex);
        MemoryToolRuntime.CmdResult baseline = runMemoryCommandCaptureSync(
                pkg,
                effectivePid,
                withoutPtrace,
                scanRangeBaseCommand,
                scanRangeBaseDataType,
                scanRangeBaseValue,
                maxScanResults,
                statePath,
                skip);
        if (isMemoryCommandFailure(baseline)) {
            return -1;
        }
        int count = Math.max(0, countFoundResultsFromOutput(baseline));
        scanRangeBaselineCountByPage.put(pageIndex, count);
        scanRangeResultCountByPage.put(pageIndex, count);
        return count;
    }

    private MemoryToolRuntime.CmdResult runMemoryCommandCaptureSync(String pkg,
                                                                    String effectivePid,
                                                                    boolean withoutPtrace,
                                                                    String command,
                                                                    @Nullable String dataType,
                                                                    @Nullable String value,
                                                                    int maxScanResults,
                                                                    String statePath,
                                                                    int skipResults) {
        String shellCmd = MemoryToolHelper.buildRunAsCommandWithStateOverride(
                pkg,
                MemoryToolRuntime.PUBLIC_BIN_DIR,
                withoutPtrace,
                command,
                effectivePid,
                dataType,
                value,
                null,
                null,
                null,
                maxScanResults,
                shouldStringCaseSensitive(),
                shouldStringPatchTruncate(),
                Math.max(0, skipResults),
                statePath);
        return runBackendShellCommandCaptureSync(shellCmd);
    }

    private String resolveTargetPackageText(String value) {
        String raw = value == null ? "" : value.trim();
        if (isAllowedTargetPackage(raw)) return raw;
        for (MemoryPackageEntry entry : packageItems) {
            if (entry == null || TextUtils.isEmpty(entry.pkg)) continue;
            if (TextUtils.equals(raw, entry.pkg) || TextUtils.equals(raw, entry.label) || TextUtils.equals(raw, entry.toString())) {
                return isAllowedTargetPackage(entry.pkg) ? entry.pkg : "";
            }
        }
        return "";
    }

    private void syncTargetFromPackageTools() {
        String pkg = resolveTargetPackageText(getTargetPackage());
        if (!isAllowedTargetPackage(pkg)) {
            pkg = getRememberedPackageToolsTargetPackage();
        }
        if (!isAllowedTargetPackage(pkg)) {
            appendStatus(shouldExcludeSelfPackage() ? "Select a non-PermsTest package first." : "Select a package first.");
            return;
        }
        ddTargetPkg.setText(pkg, false);
        lastResolvedAutoPid = null;
        clearOverlayScanState();
        ensurePackageEntryPresent(pkg, true);
        if (packageAdapter != null) packageAdapter.setItems(packageItems);
        refreshProcesses();
    }

    private void stageToolInOverlay() {
        appendStatus("Staging apk-medit...");
        new Thread(() -> {
            MemoryToolRuntime.CmdResult r = MemoryToolRuntime.ensureBundledBinaryPublicForCurrentMode(getApplicationContext(), MemoryToolHelper.TOOL_NAME);
            runOnUiThread(() -> appendStatus(summarizeResult(r != null && r.exitCode == 0 ? "apk-medit staged." : "apk-medit stage failed.", r)));
        }, "MemoryOverlayStage").start();
    }

    private void clearStateInOverlay() {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            appendStatus("Enter/select a target package first.");
            return;
        }
        appendStatus("Clearing session state...");
        new Thread(() -> {
            String shellCmd = MemoryToolHelper.buildClearStateCommand(pkg);
            MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(shellCmd);
            runOnUiThread(() -> {
                clearOverlayScanState();
                clearOverlayEntryFieldsForFreshSession();
                appendStatus(summarizeResult("Cleared session state for " + pkg + ". Ready for a new memory search.", r));
            });
        }, "MemoryOverlayClear").start();
    }

    private String summarizeResult(String prefix, MemoryToolRuntime.CmdResult r) {
        StringBuilder sb = new StringBuilder(prefix);
        if (r == null) return sb.toString();
        if (!TextUtils.isEmpty(r.stdout)) {
            sb.append('\n').append(r.stdout.trim());
        }
        if (!TextUtils.isEmpty(r.stderr)) {
            sb.append('\n').append(r.stderr.trim());
        }
        if (sb.length() == prefix.length()) {
            sb.append("\nexit=").append(r.exitCode);
        }
        return sb.toString().trim();
    }

    private boolean isMemoryCommandFailure(MemoryToolRuntime.CmdResult r) {
        if (r == null) return true;
        if (r.exitCode != 0) return true;
        String combined = ((r.stdout == null ? "" : r.stdout) + "\n" + (r.stderr == null ? "" : r.stderr)).toLowerCase();
        return combined.contains("unknown command")
                || combined.contains("not debuggable")
                || combined.contains("permission denied")
                || combined.contains("pid must be an integer that exists")
                || combined.contains("no previous results")
                || combined.contains("no previous numeric values");
    }

    private void showCapZeroBlockedWarning() {
        String msg = "Cap 0 / unlimited scans are disabled because large snapshot comparisons can exhaust device memory. Cap was reset to 500000; choose a smaller cap when the target produces too many results.";
        try { if (edtMaxResults != null) edtMaxResults.setText(Integer.toString(MemoryToolHelper.DEFAULT_MAX_RESULTS)); } catch (Throwable ignored) {}
        appendStatus(msg);
        try { Toast.makeText(this, "Cap 0 is disabled; reset to 500000.", Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
    }

    private String buildRunningCommandStatus(String command, boolean scanOperation, int requestedMaxResults, int effectiveMaxResults) {
        String text = "Running " + command + "...";
        if (scanOperation && MemoryToolHelper.isHighCapOrUnlimited(effectiveMaxResults)) {
            text += "\n" + buildHighCapStatusLine(effectiveMaxResults, getTargetPackage());
        }
        return text;
    }

    private String buildHighCapStatusLine(int maxScanResults, String packageName) {
        int normalized = MemoryToolHelper.normalizeMaxResults(maxScanResults);
        String capText = normalized == 0 ? "unlimited" : String.valueOf(normalized);
        if (normalized == 0) {
            return "Cap unlimited is active. Full comparison state is kept on disk in the target app run-as workspace; visible rows are capped for overlay responsiveness.";
        }
        return "Warning: high-cap scan mode is active (Cap " + capText + "). "
                + "Comparison state stays in the target app run-as workspace; visible rows are capped for overlay responsiveness.";
    }

    private boolean shouldLoadStateForVisibleRows(int resultCount) {
        return resultCount >= 0 && resultCount <= MAX_STATE_ROWS_TO_LOAD_FOR_UI;
    }

    private boolean isSummaryVisible() {
        return txtSummary != null && txtSummary.getVisibility() == View.VISIBLE;
    }

    private void showFileTypeStatus(String text) {
        String value = text == null ? "" : text.trim();
        appendDetailLog(value);
        if (txtSummary != null) {
            txtSummary.setText(firstLine(value));
        }
    }

    private void appendStatus(String text) {
        appendStatus(text, false);
    }

    private void appendStatusPreserveDetailScroll(String text) {
        appendStatus(text, true);
    }

    private void appendStatus(String text, boolean preserveDetailScroll) {
        String value = text == null ? "" : text.trim();
        if (TextUtils.isEmpty(value) || isRoutineDetailOnlyStatus(value)) return;
        if (isSummaryVisible() && txtSummary != null) {
            txtSummary.setText(value);
        }
        appendDetailLog(value, preserveDetailScroll);
    }

    private void setDetailLog(String text) {
        appendDetailLog(text);
    }

    private CharSequence colorizeDetailAddresses(@Nullable TextView textView, String text) {
        if (TextUtils.isEmpty(text)) return "";
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int addressColor = DETAIL_HEX_ADDRESS_FALLBACK_COLOR;
        try {
            if (textView != null && textView.getLinkTextColors() != null) {
                addressColor = textView.getLinkTextColors().getDefaultColor();
            }
        } catch (Throwable ignored) {
        }
        Matcher matcher = DETAIL_HEX_ADDRESS_PATTERN.matcher(text);
        while (matcher.find()) {
            sb.setSpan(new ForegroundColorSpan(addressColor),
                    matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    /** Appends a visible Details entry without truncating earlier session output. */
    private void appendDetailLog(String text) {
        appendDetailLog(text, false);
    }

    private void appendDetailLog(String text, boolean preserveScroll) {
        String value = sanitizeVisibleDetailText(text);
        if (TextUtils.isEmpty(value)) return;
        final int oldScrollX = txtOutput == null ? 0 : txtOutput.getScrollX();
        final int oldScrollY = txtOutput == null ? 0 : txtOutput.getScrollY();
        if (overlaySessionDetailLog.length() > 0) {
            overlaySessionDetailLog.append('\n');
        }
        overlaySessionDetailLog.append(value);
        lastOverlayDetailText = overlaySessionDetailLog.toString();
        if (txtOutput == null) return;
        txtOutput.setText(colorizeDetailAddresses(txtOutput, lastOverlayDetailText));
        txtOutput.post(() -> {
            try {
                if (preserveScroll) {
                    txtOutput.scrollTo(oldScrollX, Math.max(0, oldScrollY));
                    return;
                }
                android.text.Layout layout = txtOutput.getLayout();
                if (layout == null) return;
                int scrollY = layout.getLineTop(txtOutput.getLineCount()) - txtOutput.getHeight() + txtOutput.getTotalPaddingBottom();
                txtOutput.scrollTo(0, Math.max(0, scrollY));
            } catch (Throwable ignored) {
            }
        });
    }

    private String sanitizeVisibleDetailText(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\r?\n")) {
            String t = line == null ? "" : line.trim();
            if (TextUtils.isEmpty(t) || isRoutineDetailOnlyStatus(t)) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString().trim();
    }

    private boolean isRoutineDetailOnlyStatus(String value) {
        if (value == null) return false;
        String t = value.trim();
        return isRoutineTidLine(t)
                || t.startsWith("Hex live read returned a zero-filled frame");
    }

    /** Clears Details when a new overlay service session starts or ends. */
    private void resetDetailSessionLog() {
        overlaySessionDetailLog.setLength(0);
        lastOverlayDetailText = "";
        if (txtOutput != null) txtOutput.setText("");
    }

    private void showPatchSaveStatus(String text) {
        String value = text == null ? "" : text.trim();
        appendDetailLog(value);
        if (isSummaryVisible() && txtSummary != null) {
            txtSummary.setText(firstLine(value));
        }
    }

    private static String firstLine(String value) {
        if (value == null) return "";
        int newline = value.indexOf('\n');
        return newline >= 0 ? value.substring(0, newline).trim() : value.trim();
    }

    private void showCommandStartStatus(String text) {
        String value = text == null ? "" : text.trim();
        if (isSummaryVisible() && txtSummary != null) {
            txtSummary.setText(value);
        }
        appendDetailLog(value);
    }

    private void showCommandOutput(String summary, String details) {
        String cleanSummary = summary == null ? "" : summary.trim();
        if (isSummaryVisible() && txtSummary != null) {
            txtSummary.setText(cleanSummary);
        }
        appendDetailLog(combineStatusAndDetails(cleanSummary, details));
    }

    private String combineStatusAndDetails(String summary, String details) {
        String cleanSummary = summary == null ? "" : summary.trim();
        String cleanDetails = details == null ? "" : details.trim();
        if (TextUtils.isEmpty(cleanSummary)) return cleanDetails;
        if (TextUtils.isEmpty(cleanDetails)) return cleanSummary;
        if (TextUtils.equals(cleanSummary, cleanDetails)) return cleanDetails;
        return cleanSummary + "\n\n" + cleanDetails;
    }

    /**
     * Central command-result display path.  It keeps Summary compact while Details
     * records the full per-session command history.
     */
    private void showMemoryCommandResult(String command,
                                         String prefix,
                                         MemoryToolRuntime.CmdResult r,
                                         @Nullable String stateJson,
                                         @Nullable String scanValue,
                                         int outputResultCount) {
        String summary = buildImportantCommandSummary(command, prefix, r);
        String details = buildCompactCommandLog(r);
        if ("attach".equals(command)) {
            details = removeDuplicateDetailLines(summary, details);
        }
        showCommandOutput(summary, details);
        // Auto-apply is intentionally after a successful attach so target PID and
        // run-as state are established before payload search/write commands run.
        if ("attach".equals(command) && !isMemoryCommandFailure(r) && shouldApplyPayloadsOnAttach()) {
            applyPackagePayloadsOnAttach();
        }
        if (!TextUtils.isEmpty(stateJson)) {
            updateResultListFromState(stateJson, scanValue);
        } else if (outputResultCount >= 0) {
            updateResultCountOnly(outputResultCount);
        } else if ("detach".equals(command)) {
            clearOverlayScanState();
        }
        if ("patch".equals(command) && !isMemoryCommandFailure(r)) {
            ArrayList<MemoryResultRow> pendingRows = pendingPatchResultUpdates;
            String pendingValue = pendingPatchResultValue;
            pendingPatchResultUpdates = null;
            pendingPatchResultValue = null;
            if (pendingRows != null && !pendingRows.isEmpty()) {
                updatePatchedResultValues(pendingRows, pendingValue);
            } else {
                updatePatchedResultValue();
            }
        } else if ("patch".equals(command)) {
            pendingPatchResultUpdates = null;
            pendingPatchResultValue = null;
        }
    }

    /** Reads the Memory tab checkbox for package payload auto-apply. */
    private boolean shouldApplyPayloadsOnAttach() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_APPLY_PAYLOADS_ON_ATTACH, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Reads the default-on option for writing each payload to every found match. */
    private boolean shouldApplyPayloadsToAllMatches() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_APPLY_PAYLOADS_TO_ALL_MATCHES, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Reads the default-on option for showing every patched payload address in Details. */
    private boolean shouldShowAllPatchedPayloadAddresses() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_SHOW_ALL_PATCHED_PAYLOAD_ADDRESSES, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Loads all complete payloads for the active package and applies them. */
    private void applyPackagePayloadsOnAttach() {
        final String pkg = getTargetPackage();
        final String pid = getSelectedPid();
        if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(pid)) {
            appendStatus("Apply Payloads On Attach skipped: no package/PID selected.");
            return;
        }
        appendStatus("Apply Payloads On Attach: loading payloads for " + pkg + "...");
        new Thread(() -> {
            final String result = applyEnabledPackagePayloadsSync(pkg, pid, "Apply Payloads On Attach");
            runOnUiThread(() -> {
                appendStatus(result);
                showPayloadApplyToast(result);
            });
        }, "MemoryOverlayAutoApplyPayloads").start();
    }

    private void handleLaunchAndApplyPayloads(@Nullable Intent intent, int startId, boolean launchFirst) {
        final String pkg = intent == null ? "" : safeTrim(intent.getStringExtra(EXTRA_TARGET_PACKAGE));
        final String label = intent == null ? pkg : safeTrim(intent.getStringExtra(EXTRA_TARGET_LABEL));
        final String pidHint = intent == null ? "" : safeTrim(intent.getStringExtra(EXTRA_TARGET_PID));
        final String operation = launchFirst ? "Launch With Payloads" : "Apply Payloads";
        final ArrayList<String> selectedPayloadFiles = getPayloadFileNameExtraList(intent);
        final long requestedDelayMs = getRequestedPayloadDelayMs(intent, launchFirst ? PAYLOAD_LAUNCH_INITIAL_DELAY_MS : 0L);
        if (TextUtils.isEmpty(pkg)) {
            showLaunchPayloadToast(operation + ": missing package.");
            stopIfHeadlessPayloadRunComplete(startId);
            return;
        }
        if (launchFirst) launchPackageForPayloads(pkg);
        Log.i(LOG_TAG, operation + " request: pkg=" + pkg
                + ", selected=" + (selectedPayloadFiles == null ? 0 : selectedPayloadFiles.size())
                + ", delayMs=" + requestedDelayMs
                + ", launchFirst=" + launchFirst
                + ", pidHint=" + (TextUtils.isEmpty(pidHint) ? "" : pidHint));
        new Thread(() -> {
            if (requestedDelayMs > 0L) SystemClock.sleep(requestedDelayMs);
            String pid = launchFirst ? "" : pidHint;
            if (TextUtils.isEmpty(pid)) {
                pid = waitForPayloadLaunchPid(pkg, PAYLOAD_LAUNCH_PID_TIMEOUT_MS);
            }
            if (TextUtils.isEmpty(pid)) {
                final String msg = operation + ": no running PID found for " + pkg + ".";
                runOnUiThread(() -> showLaunchPayloadToast(msg));
                stopIfHeadlessPayloadRunComplete(startId);
                return;
            }
            final String title = operation + (TextUtils.isEmpty(label) || TextUtils.equals(label, pkg) ? "" : (" (" + label + ")"));
            final String result = applyEnabledPackagePayloadsSync(pkg, pid, title, selectedPayloadFiles);
            runOnUiThread(() -> showLaunchPayloadToast(summarizeLaunchPayloadResult(result)));
            stopIfHeadlessPayloadRunComplete(startId);
        }, launchFirst ? "MemoryPayloadLauncher" : "MemoryPayloadApply").start();
    }

    private ArrayList<String> getPayloadFileNameExtraList(@Nullable Intent intent) {
        if (intent == null) return null;
        ArrayList<String> out = new ArrayList<>();
        try {
            String[] arr = intent.getStringArrayExtra(EXTRA_PAYLOAD_FILE_NAMES);
            if (arr != null) {
                for (String value : arr) {
                    String cleaned = safeTrim(value);
                    if (!TextUtils.isEmpty(cleaned) && !out.contains(cleaned)) out.add(cleaned);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            ArrayList<String> list = intent.getStringArrayListExtra(EXTRA_PAYLOAD_FILE_NAMES);
            if (list != null) {
                for (String value : list) {
                    String cleaned = safeTrim(value);
                    if (!TextUtils.isEmpty(cleaned) && !out.contains(cleaned)) out.add(cleaned);
                }
            }
        } catch (Throwable ignored) {
        }
        return out.isEmpty() ? null : out;
    }

    private long getRequestedPayloadDelayMs(@Nullable Intent intent, long defaultDelayMs) {
        if (intent == null) return Math.max(0L, defaultDelayMs);
        long delayMs = defaultDelayMs;
        try {
            if (intent.hasExtra(EXTRA_PAYLOAD_DELAY_MS)) {
                delayMs = intent.getLongExtra(EXTRA_PAYLOAD_DELAY_MS, defaultDelayMs);
            }
        } catch (Throwable ignored) {
        }
        return Math.max(0L, Math.min(delayMs, 30000L));
    }

    private String applyEnabledPackagePayloadsSync(String pkg, String pid, String title) {
        return applyEnabledPackagePayloadsSync(pkg, pid, title, null);
    }

    private String applyEnabledPackagePayloadsSync(String pkg, String pid, String title, @Nullable ArrayList<String> selectedPayloadFiles) {
        String prefix = TextUtils.isEmpty(title) ? "Apply Payloads" : title.trim();
        try {
            ArrayList<MemoryHexOverlayController.PayloadApplySpec> payloads = new ArrayList<>();
            ArrayList<String> skipped = new ArrayList<>();
            Set<String> selected = normalizeSelectedPayloadFiles(selectedPayloadFiles);
            boolean hasSelection = selected != null && !selected.isEmpty();
            ArrayList<File> files = MemoryHexPayloadStore.listPayloadFiles(getApplicationContext(), pkg);
            for (File file : files) {
                String currentFileName = file == null ? "" : file.getName();
                try {
                    MemoryHexPayloadStore.Payload payload = MemoryHexPayloadStore.loadPayload(getApplicationContext(), file);
                    if (!MemoryHexPayloadStore.packageNameMatches(payload.packageName, pkg)) {
                        skipped.add(payload.name + ": package mismatch");
                        continue;
                    }
                    String payloadFileName = TextUtils.isEmpty(currentFileName) ? payload.fileName : currentFileName;
                    if (hasSelection && !selected.contains(payloadFileName)) {
                        continue;
                    }
                    if (!payload.enabled) {
                        skipped.add(payload.name + ": disabled");
                        continue;
                    }
                    payloads.add(new MemoryHexOverlayController.PayloadApplySpec(
                            payload.name,
                            payload.originalHex,
                            payload.patchedHex,
                            payload.maskHex,
                            payload.sectionStartHex,
                            payload.sectionEndHex,
                            payload.enabled,
                            payload.preserveMaskWildcards));
                } catch (Throwable t) {
                    if (!hasSelection || selected.contains(currentFileName)) {
                        skipped.add((file == null ? "payload" : file.getName()) + ": " + (TextUtils.isEmpty(t.getMessage()) ? t.getClass().getSimpleName() : t.getMessage()));
                    }
                }
            }
            if (payloads.isEmpty()) {
                String msg = prefix + ": no enabled complete payloads found for " + pkg + (hasSelection ? " in shortcut selection." : ".");
                if (!skipped.isEmpty()) msg += "\nSkipped:\n" + TextUtils.join("\n", skipped);
                return msg;
            }
            String result = applyHexPayloadListSync(pkg, pid, payloads);
            if (!skipped.isEmpty()) result += "\nSkipped:\n" + TextUtils.join("\n", skipped);
            return prefix + ":\n" + result;
        } catch (Throwable t) {
            return prefix + " failed: " + t.getClass().getSimpleName() + (TextUtils.isEmpty(t.getMessage()) ? "" : (": " + t.getMessage()));
        }
    }

    private Set<String> normalizeSelectedPayloadFiles(@Nullable ArrayList<String> selectedPayloadFiles) {
        if (selectedPayloadFiles == null || selectedPayloadFiles.isEmpty()) return null;
        HashSet<String> selected = new HashSet<>();
        for (String name : selectedPayloadFiles) {
            String clean = safeTrim(name);
            if (!TextUtils.isEmpty(clean)) selected.add(clean);
        }
        return selected.isEmpty() ? null : selected;
    }

    private void launchPackageForPayloads(String pkg) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                runOnUiThread(() -> showLaunchPayloadToast("No launchable activity for " + pkg + "."));
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch (Throwable t) {
            runOnUiThread(() -> showLaunchPayloadToast("Launch failed: " + t.getClass().getSimpleName()));
        }
    }

    private String waitForPayloadLaunchPid(String pkg, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + Math.max(1000L, timeoutMs);
        long nextFallbackScanAt = SystemClock.uptimeMillis() + PAYLOAD_LAUNCH_PID_FALLBACK_SCAN_MS;
        String pid = null;
        while (SystemClock.uptimeMillis() < end) {
            try {
                pid = MemoryToolRuntime.resolveTargetPidFast(getApplicationContext(), pkg, pid);
                if (!TextUtils.isEmpty(pid)) return pid;
            } catch (Throwable ignored) {
            }

            long now = SystemClock.uptimeMillis();
            if (now >= nextFallbackScanAt) {
                try {
                    pid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, pid);
                    if (!TextUtils.isEmpty(pid)) return pid;
                } catch (Throwable ignored) {
                }
                nextFallbackScanAt = now + PAYLOAD_LAUNCH_PID_FALLBACK_SCAN_MS;
            }
            SystemClock.sleep(PAYLOAD_LAUNCH_PID_POLL_MS);
        }

        try {
            pid = MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, pid);
        } catch (Throwable ignored) {
        }
        return pid;
    }

    private void stopIfHeadlessPayloadRunComplete(int startId) {
        mainHandler.post(() -> {
            try {
                if (root == null && hexOverlayController == null && disassemblyOverlayController == null && specialToolsOverlayController == null) {
                    stopSelf(startId);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void showPayloadApplyToast(String result) {
        String msg = summarizePayloadApplyToast(result);
        if (TextUtils.isEmpty(msg)) return;
        try { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
    }

    private String summarizePayloadApplyToast(String result) {
        if (TextUtils.isEmpty(result)) return "Payloads: 0 applied, 1 failed";
        Matcher matcher = Pattern.compile("Applied\\s+(\\d+)\\s+payloads?\\b.*?,\\s+(\\d+)\\s+payloads?\\s+failed", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(result);
        if (matcher.find()) {
            return "Payloads: " + matcher.group(1) + " applied, " + matcher.group(2) + " failed";
        }
        if (result.toLowerCase(Locale.US).contains("no enabled complete payloads")) {
            return "Payloads: 0 applied, 0 failed";
        }
        return "Payloads: 0 applied, 1 failed";
    }

    private void showLaunchPayloadToast(String msg) {
        try { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
    }

    private String summarizeLaunchPayloadResult(String result) {
        if (TextUtils.isEmpty(result)) return "Launch With Payloads finished.";
        String r = result.replace('\r', '\n');
        int idx = r.indexOf("Applied ");
        if (idx >= 0) {
            int end = r.indexOf('\n', idx);
            return end > idx ? r.substring(idx, end).trim() : r.substring(idx).trim();
        }
        int end = r.indexOf('\n');
        return end > 0 ? r.substring(0, end).trim() : r.trim();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    /** Removes attach lines already shown in Summary from the Details body. */
    private String removeDuplicateDetailLines(String summary, String details) {
        if (TextUtils.isEmpty(summary) || TextUtils.isEmpty(details)) return details;
        HashSet<String> summaryLines = new HashSet<>();
        for (String line : summary.split("\r?\n")) {
            String t = line == null ? "" : line.trim();
            if (!TextUtils.isEmpty(t)) summaryLines.add(t);
        }
        if (summaryLines.isEmpty()) return details;

        StringBuilder out = new StringBuilder();
        for (String line : details.split("\r?\n")) {
            String t = line == null ? "" : line.trim();
            if (TextUtils.isEmpty(t) || summaryLines.contains(t)) continue;
            if (out.length() > 0) out.append('\n');
            out.append(t);
        }
        return out.toString().trim();
    }

    private String buildImportantCommandSummary(String command, String prefix, MemoryToolRuntime.CmdResult r) {
        StringBuilder sb = new StringBuilder(prefix == null ? "" : prefix.trim());
        String raw = collectCommandOutput(r);
        if (TextUtils.isEmpty(raw)) {
            if (r != null && sb.length() > 0) sb.append("\nexit=").append(r.exitCode);
            return sb.toString().trim();
        }

        int addressCount = 0;
        String[] lines = raw.split("\r?\n");
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (TextUtils.isEmpty(t) || isRoutineTidLine(t) || "------------------------".equals(t)) {
                continue;
            }
            boolean important = t.startsWith("Search ")
                    || t.startsWith("Check previous results")
                    || t.startsWith("Compare:")
                    || t.startsWith("Compared:")
                    || t.startsWith("Previous result count:")
                    || t.startsWith("Process changed from PID")
                    || t.startsWith("Target Value:")
                    || t.startsWith("Magic:")
                    || t.startsWith("Found:")
                    || t.startsWith("Successfully patched")
                    || t.startsWith("Attach mode")
                    || t.startsWith("State cleared")
                    || t.startsWith("Already ")
                    || t.toLowerCase().contains("failed")
                    || t.toLowerCase().contains("error")
                    || t.toLowerCase().contains("permission denied")
                    || t.toLowerCase().contains("unknown command");
            if (t.startsWith("Address:")) {
                important = addressCount < 8;
                addressCount++;
            }
            if (important) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(t);
            }
        }
        if (addressCount > 8) {
            sb.append("\n").append(addressCount - 8).append(" more address").append(addressCount - 8 == 1 ? "" : "es").append(" in details.");
        }
        return sb.toString().trim();
    }

    private String buildCompactCommandLog(MemoryToolRuntime.CmdResult r) {
        String raw = collectCommandOutput(r);
        if (TextUtils.isEmpty(raw)) return "";

        int attached = 0;
        int detached = 0;
        int kept = 0;
        StringBuilder sb = new StringBuilder();
        String[] lines = raw.split("\r?\n");
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (TextUtils.isEmpty(t)) continue;
            if (t.startsWith("Attached TID:")) {
                attached++;
                continue;
            }
            if (t.startsWith("Detached TID:")) {
                detached++;
                continue;
            }
            if (kept >= 80) {
                continue;
            }
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
            kept++;
        }
        if (attached > 0 || detached > 0) {
            String tidSummary = "Attached TIDs: " + attached + ", detached TIDs: " + detached;
            if (sb.length() > 0) {
                sb.insert(0, tidSummary + "\n");
            } else {
                sb.append(tidSummary);
            }
        }
        if (lines.length > kept + attached + detached) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("Log truncated.");
        }
        return sb.toString().trim();
    }

    private String collectCommandOutput(MemoryToolRuntime.CmdResult r) {
        if (r == null) return "";
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(r.stdout)) sb.append(r.stdout.trim());
        if (!TextUtils.isEmpty(r.stderr)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(r.stderr.trim());
        }
        return sb.toString().trim();
    }


    private int countFoundResultsFromOutput(MemoryToolRuntime.CmdResult r) {
        String raw = collectCommandOutput(r);
        if (TextUtils.isEmpty(raw)) return -1;
        int total = 0;
        boolean foundAny = false;
        String[] lines = raw.split("\r?\n");
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (!t.startsWith("Found:")) continue;
            int colon = t.indexOf(':');
            int end = t.indexOf('!', colon + 1);
            if (colon < 0) continue;
            String number = (end > colon ? t.substring(colon + 1, end) : t.substring(colon + 1)).trim();
            try {
                total += Integer.parseInt(number);
                foundAny = true;
            } catch (Throwable ignored) {
            }
        }
        return foundAny ? total : -1;
    }

    private String readMemoryStateJson(String pkg) {
        return readMemoryStateJson(pkg, currentScanRangeStatePath());
    }

    private String readMemoryStateJson(String pkg, String statePath) {
        try {
            String cmd = MemoryToolHelper.buildReadStateCommand(pkg, getMaxScanResults(), statePath);
            MemoryToolRuntime.CmdResult r = runBackendShellCommandCaptureSync(cmd);
            if (r == null || TextUtils.isEmpty(r.stdout)) return null;
            return r.stdout.trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void writeMemoryStateJson(String pkg, String stateJson) {
        writeMemoryStateJson(pkg, stateJson, currentScanRangeStatePath());
    }

    private void writeMemoryStateJson(String pkg, String stateJson, String statePath) {
        try {
            String cmd = MemoryToolHelper.buildWriteStateCommand(pkg, stateJson, getMaxScanResults(), statePath);
            runBackendShellCommandCaptureSync(cmd);
        } catch (Throwable ignored) {
        }
    }

    private void updatePatchedResultValue() {
        MemoryResultRow row = resolvePatchTargetRow();
        if (row == null) return;
        String patchedValue = edtPatchValue == null || edtPatchValue.getText() == null
                ? ""
                : edtPatchValue.getText().toString();
        updatePatchedResultValues(singleRowList(row), patchedValue);
    }

    private void updatePatchedResultValues(List<MemoryResultRow> rows, String rawPatchValue) {
        if (rows == null || rows.isEmpty()) return;
        Set<Long> addresses = new HashSet<>();
        for (MemoryResultRow row : rows) {
            if (row != null) addresses.add(row.address);
        }
        MemoryResultRow updatedSelection = null;
        for (int i = 0; i < resultItems.size(); i++) {
            MemoryResultRow item = resultItems.get(i);
            if (item == null || !addresses.contains(item.address)) {
                continue;
            }
            String patchedValue = formatPatchedDisplayValueForTarget(rawPatchValue, item);
            MemoryResultRow updated = new MemoryResultRow(
                    item.displayIndex,
                    item.address,
                    item.dataType,
                    item.converterId,
                    patchedValue,
                    item.groupIndex,
                    item.indexInGroup);
            resultItems.set(i, updated);
            if (rows.size() == 1 || (selectedResult != null && selectedResult.address == item.address)) {
                updatedSelection = updated;
            }
        }
        if (updatedSelection != null) {
            selectedResult = updatedSelection;
        } else if (rows.size() == 1) {
            selectedResult = rows.get(0);
        }
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        updateSelectedResultLabel(selectedResult, activeResultCount);
        if (selectedResult != null) notifyToolAddressChanged(selectedResult);
    }

    private String formatPatchedDisplayValueForTarget(String rawPatchValue, MemoryResultRow row) {
        String patchedValue = rawPatchValue == null ? "" : rawPatchValue;
        if (isPatchHexEnabled() && isNumericPatchDataType(row.dataType)) {
            String normalized = normalizeHexPatchValueForTarget(patchedValue, row);
            if (!TextUtils.isEmpty(normalized)) return normalized;
        } else if (isStringPatchDataType(row.dataType) && shouldStringPatchTruncate()) {
            return truncateStringPatchValueForTarget(patchedValue, row);
        }
        return patchedValue;
    }

    private boolean shouldSyncResultValues() {
        try {
            if (chkSyncResultValue != null) return chkSyncResultValue.isChecked();
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_SYNC_RESULT_VALUE, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void scheduleResultValueSync(long delayMs) {
        try {
            mainHandler.removeCallbacks(resultValueSyncRunnable);
            if (!shouldSyncResultValues() || resultItems.isEmpty()) return;
            mainHandler.postDelayed(resultValueSyncRunnable, Math.max(0L, delayMs));
        } catch (Throwable ignored) {
        }
    }

    private void stopResultValueSyncLoop() {
        try { mainHandler.removeCallbacks(resultValueSyncRunnable); } catch (Throwable ignored) {}
        resultValueSyncInFlight = false;
    }

    private void refreshVisibleResultValuesIfNeeded() {
        if (!shouldSyncResultValues() || resultItems.isEmpty() || resultValueSyncInFlight) return;
        final String pkg = getTargetPackage();
        final String pid = getSelectedPid();
        if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(pid)) {
            scheduleResultValueSync(2000L);
            return;
        }
        final ArrayList<ResultValueSyncTarget> targets = collectVisibleResultValueSyncTargets();
        if (targets.isEmpty()) {
            scheduleResultValueSync(2000L);
            return;
        }
        resultValueSyncInFlight = true;
        new Thread(() -> {
            final ArrayList<ResultValueSyncUpdate> updates = new ArrayList<>();
            for (ResultValueSyncTarget target : targets) {
                try {
                    String value = readLiveValueForResult(pkg, pid, target.row);
                    if (value != null && !TextUtils.equals(value, target.row.value)) {
                        updates.add(new ResultValueSyncUpdate(target.index, target.key, value));
                    }
                } catch (Throwable ignored) {
                }
            }
            runOnUiThread(() -> {
                resultValueSyncInFlight = false;
                applyResultValueSyncUpdates(updates);
                if (shouldSyncResultValues() && !resultItems.isEmpty()) {
                    scheduleResultValueSync(1800L);
                }
            });
        }, "MemoryResultValueSync").start();
    }

    private ArrayList<ResultValueSyncTarget> collectVisibleResultValueSyncTargets() {
        ArrayList<ResultValueSyncTarget> out = new ArrayList<>();
        if (resultItems.isEmpty()) return out;
        int first = 0;
        int count = Math.min(resultItems.size(), 18);
        try {
            if (lstResults != null && lstResults.getVisibility() == View.VISIBLE) {
                first = Math.max(0, lstResults.getFirstVisiblePosition());
                int visible = Math.max(0, lstResults.getChildCount());
                if (visible > 0) count = Math.min(resultItems.size() - first, visible + 4);
            }
        } catch (Throwable ignored) {
        }
        for (int i = first; i < resultItems.size() && out.size() < Math.max(1, count); i++) {
            MemoryResultRow row = resultItems.get(i);
            if (row == null || !canSyncLiveValueForRow(row)) continue;
            out.add(new ResultValueSyncTarget(i, resultKey(row), row));
        }
        return out;
    }

    private boolean canSyncLiveValueForRow(@Nullable MemoryResultRow row) {
        if (row == null || row.address <= 0L) return false;
        String converter = TextUtils.isEmpty(row.converterId) ? converterForDataType(row.dataType) : row.converterId;
        if (TextUtils.isEmpty(converter)) return false;
        String normalized = converter.toLowerCase(Locale.US);
        return normalized.contains("string") || normalized.contains("byte") || normalized.contains("word");
    }

    @Nullable
    private String readLiveValueForResult(String pkg, String pid, MemoryResultRow row) {
        int byteCount = byteCountForLiveValue(row);
        if (byteCount <= 0) return null;
        DumpBytesResult dump = readMemoryBytesSync(pkg, pid, row.address, row.address + byteCount);
        if (dump == null || !dump.success || dump.bytes == null || dump.bytes.length == 0) return null;
        return formatLiveValueForResult(row, dump.bytes);
    }

    private int byteCountForLiveValue(MemoryResultRow row) {
        if (row == null) return 0;
        String converter = TextUtils.isEmpty(row.converterId) ? converterForDataType(row.dataType) : row.converterId;
        if (!TextUtils.isEmpty(converter) && converter.toLowerCase(Locale.US).contains("string")) {
            int existing = row.value == null ? 0 : row.value.getBytes(StandardCharsets.UTF_8).length;
            return Math.max(1, Math.min(128, existing > 0 ? existing : 32));
        }
        return byteWidthForDataType(row.dataType);
    }

    private String formatLiveValueForResult(MemoryResultRow row, byte[] bytes) {
        String converter = row == null || TextUtils.isEmpty(row.converterId) ? converterForDataType(row == null ? null : row.dataType) : row.converterId;
        if (!TextUtils.isEmpty(converter) && converter.toLowerCase(Locale.US).contains("string")) {
            int len = 0;
            while (len < bytes.length && bytes[len] != 0) len++;
            return new String(bytes, 0, len, StandardCharsets.UTF_8);
        }
        if (!TextUtils.isEmpty(converter) && converter.toLowerCase(Locale.US).contains("bytes")) {
            return bytesToHex(bytes);
        }
        long value = 0L;
        int width = Math.min(bytes.length, Math.max(1, byteWidthForDataType(row == null ? null : row.dataType)));
        for (int i = 0; i < width; i++) {
            value |= ((long) bytes[i] & 0xffL) << (8 * i);
        }
        return Long.toUnsignedString(value);
    }

    private void applyResultValueSyncUpdates(ArrayList<ResultValueSyncUpdate> updates) {
        if (updates == null || updates.isEmpty()) return;
        String selectedKey = resultKey(selectedResult);
        boolean selectionChanged = false;
        for (ResultValueSyncUpdate update : updates) {
            if (update == null || update.index < 0 || update.index >= resultItems.size()) continue;
            MemoryResultRow current = resultItems.get(update.index);
            if (current == null || !TextUtils.equals(resultKey(current), update.key)) continue;
            MemoryResultRow replacement = current.withValue(update.value);
            resultItems.set(update.index, replacement);
            if (!TextUtils.isEmpty(selectedKey) && TextUtils.equals(selectedKey, update.key)) {
                selectedResult = replacement;
                selectionChanged = true;
            }
        }
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        if (selectionChanged) updateSelectedResultLabel(selectedResult, activeResultCount);
    }

    private void seedAutoProcessSelection() {
        if (ddProcess == null || processAdapter == null) return;
        if (processItems.isEmpty()) {
            processItems.add(new MemoryProcessEntry("", "Auto-select"));
            processAdapter.notifyDataSetChanged();
        }
        if (isAutoProcessSelection()) {
            ddProcess.setText(processItems.get(0).toString(), false);
        }
    }

    private void clearProcessSelectionForTargetChange() {
        lastResolvedAutoPid = null;
        pendingSelectPid = null;
        invalidatePendingProcessRefresh();
        if (ddProcess == null || processAdapter == null) return;
        processItems.clear();
        processItems.add(new MemoryProcessEntry("", "Auto-select"));
        processAdapter.notifyDataSetChanged();
        ddProcess.setText(processItems.get(0).toString(), false);
    }

    private void invalidatePendingProcessRefresh() {
        activeProcessRefreshGeneration = 0;
        processRefreshInFlight = false;
        pendingProcessListUpdate = null;
        reopenProcessDropdownAfterRefresh = false;
        runOnUiThread(() -> {
            if (rowProcessLoading != null) {
                rowProcessLoading.setVisibility(View.GONE);
            }
        });
    }

    private String resolvePidForCommand(String pkg, String selectedPid, boolean autoSelection) {
        if (TextUtils.isEmpty(pkg)) return "";
        String preferred = selectedPid == null ? "" : selectedPid.trim();
        String resolved = autoSelection
                ? MemoryToolRuntime.resolveTargetPidFast(getApplicationContext(), pkg, preferred)
                : MemoryToolRuntime.resolveTargetPid(getApplicationContext(), pkg, preferred);
        if (!TextUtils.isEmpty(resolved)) {
            lastResolvedAutoPid = resolved;
            if (autoSelection) {
                invalidatePendingProcessRefresh();
            }
        } else if (autoSelection) {
            lastResolvedAutoPid = null;
        }
        return TextUtils.isEmpty(resolved) ? "" : resolved.trim();
    }

    private void updateResultCountOnly(int resultCount) {
        activeResultCount = Math.max(0, resultCount);
        hasActiveResultSet = activeResultCount > 0;
        selectedResult = null;
        resultItems.clear();
        checkedPatchResultKeys.clear();
        resultStateSearchValue = null;
        resultPageIndex = 0;
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        if (lstResults != null) lstResults.setVisibility(View.GONE);
        updateSelectedResultLabel(null, activeResultCount);
        updateResultListVisibility(activeResultCount);
        stopResultValueSyncLoop();
    }

    private void updateResultListFromState(@Nullable String stateJson, @Nullable String searchValue) {
        lastStateJson = TextUtils.isEmpty(stateJson) ? null : stateJson.trim();
        resultStateSearchValue = searchValue == null ? "" : searchValue.trim();
        resultPageIndex = 0;
        loadVisibleResultPage(0, false);
    }

    private void loadVisibleResultPage(int requestedPageIndex, boolean userRequested) {
        if (TextUtils.isEmpty(lastStateJson)) {
            if (userRequested) appendStatus("No paged result state is loaded for the current scan.");
            updateResultPageButtons();
            return;
        }

        int pageSize = Math.max(1, getResultListThreshold());
        int knownCount = Math.max(0, activeResultCount);
        if (knownCount <= 0) {
            ArrayList<MemoryResultRow> counted = parseResultRowsFromState(lastStateJson, resultStateSearchValue, 0, pageSize);
            resultItems.clear();
            resultItems.addAll(counted);
            knownCount = Math.max(0, activeResultCount);
        }

        int maxPageIndex = knownCount <= 0 ? 0 : (knownCount - 1) / pageSize;
        int pageIndex = Math.max(0, Math.min(requestedPageIndex, maxPageIndex));
        if (userRequested && pageIndex == resultPageIndex && !resultItems.isEmpty()) {
            appendStatus(pageIndex == 0 ? "Already showing the first result page." : "Already showing the last result page.");
            updateResultPageButtons();
            return;
        }

        ArrayList<MemoryResultRow> parsed = parseResultRowsFromState(lastStateJson, resultStateSearchValue, pageIndex * pageSize, pageSize);
        resultPageIndex = pageIndex;
        selectedResult = null;
        checkedPatchResultKeys.clear();
        resultItems.clear();
        resultItems.addAll(parsed);
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        resetResultListScrollToTop();
        hasActiveResultSet = activeResultCount > 0;
        updateSelectedResultLabel(null, activeResultCount);
        updateResultListVisibility(activeResultCount);
        if (activeResultCount > 0 && !parsed.isEmpty()) {
            applyDumpRange(parsed.get(0));
            scheduleResultValueSync(350L);
        } else {
            stopResultValueSyncLoop();
        }
        if (userRequested) appendStatus(buildResultPageStatusText());
    }

    private ArrayList<MemoryResultRow> parseResultRowsFromState(@Nullable String stateJson, @Nullable String searchValue) {
        return parseResultRowsFromState(stateJson, searchValue, 0, getResultListThreshold());
    }

    private ArrayList<MemoryResultRow> parseResultRowsFromState(@Nullable String stateJson, @Nullable String searchValue, int startIndex, int rowLimit) {
        ArrayList<MemoryResultRow> rows = new ArrayList<>();
        activeResultCount = 0;
        if (TextUtils.isEmpty(stateJson)) return rows;
        int pageStart = Math.max(0, startIndex);
        int pageLimit = Math.max(1, rowLimit);
        try {
            JSONObject root = new JSONObject(stateJson);
            JSONArray founds = root.optJSONArray("founds");
            if (founds == null) return rows;
            String value = searchValue == null ? "" : searchValue.trim();
            int total = 0;
            for (int groupIndex = 0; groupIndex < founds.length(); groupIndex++) {
                JSONObject found = founds.optJSONObject(groupIndex);
                if (found == null) continue;
                String converter = found.optString("converter", "");
                String dataType = found.optString("data_type", "");
                JSONArray addrs = found.optJSONArray("addrs");
                JSONArray values = found.optJSONArray("values");
                if (addrs == null) continue;
                for (int i = 0; i < addrs.length(); i++) {
                    total++;
                    int zeroBasedIndex = total - 1;
                    if (zeroBasedIndex >= pageStart && rows.size() < pageLimit) {
                        String rowValue = value;
                        if (values != null && i < values.length()) {
                            String parsedValue = values.optString(i, "");
                            if (!TextUtils.isEmpty(parsedValue)) rowValue = parsedValue;
                        }
                        rows.add(new MemoryResultRow(
                                total,
                                addrs.optLong(i),
                                TextUtils.isEmpty(dataType) ? "unknown" : dataType,
                                converter,
                                rowValue,
                                groupIndex,
                                i));
                    }
                }
            }
            activeResultCount = total;
        } catch (Throwable ignored) {
            activeResultCount = 0;
        }
        return rows;
    }

    private void updateResultListVisibility(int resultCount) {
        if (lstResults == null) return;
        boolean show = resultCount > 0 && !resultItems.isEmpty();
        lstResults.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rowSelectVisibleResults != null) {
            rowSelectVisibleResults.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        updateResultPageButtons();
        if (txtSelectedResult != null) {
            if (show && shouldShowResultPageStatus(resultCount)) {
                txtSelectedResult.setText(buildResultPageStatusText() + ". Tap one to patch only that address.");
            } else if (!show && resultCount > MAX_STATE_ROWS_TO_LOAD_FOR_UI) {
                txtSelectedResult.setText("Result set has " + resultCount + " addresses. Refine again; visible rows are not loaded above " + MAX_STATE_ROWS_TO_LOAD_FOR_UI + " results.");
            } else if (!show && resultCount > 0) {
                txtSelectedResult.setText("Result set has " + resultCount + " addresses, but no rows were loaded from state. Refine again or run a smaller scan.");
            }
        }
    }

    private boolean shouldShowResultPageStatus(int resultCount) {
        return resultCount > resultItems.size() || resultPageIndex > 0;
    }

    private String buildResultPageStatusText() {
        int total = Math.max(0, activeResultCount);
        if (total <= 0 || resultItems.isEmpty()) return "Result set is empty";
        int start = Math.max(1, (resultPageIndex * Math.max(1, getResultListThreshold())) + 1);
        int end = Math.min(total, start + resultItems.size() - 1);
        int pageCount = getResultPageCount();
        String pageText = pageCount > 1 ? (" · Page " + (resultPageIndex + 1) + " of " + pageCount) : "";
        return "Showing " + start + "-" + end + " of " + total + " addresses" + pageText;
    }

    private int getResultPageCount() {
        int total = Math.max(0, activeResultCount);
        if (total <= 0) return 0;
        int pageSize = Math.max(1, getResultListThreshold());
        return ((total - 1) / pageSize) + 1;
    }

    private void updateResultPageButtons() {
        int pageCount = TextUtils.isEmpty(lastStateJson) ? 0 : getResultPageCount();
        boolean showButtons = pageCount > 1 && !resultItems.isEmpty();
        updateResultPageButton(btnPreviousResultPage, showButtons, resultPageIndex > 0);
        updateResultPageButton(btnNextResultPage, showButtons, resultPageIndex + 1 < pageCount);
    }

    private void updateResultPageButton(@Nullable View button, boolean visible, boolean enabled) {
        if (button == null) return;
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.42f);
    }

    private void resetResultListScrollToTop() {
        if (lstResults == null) return;
        lstResults.post(() -> {
            try {
                lstResults.setSelectionFromTop(0, 0);
            } catch (Throwable ignored) {
            }
        });
    }

    private void selectAllVisibleResults() {
        if (resultItems.isEmpty()) {
            appendStatus("No visible result rows to select.");
            return;
        }
        for (MemoryResultRow row : resultItems) {
            checkedPatchResultKeys.add(resultKey(row));
        }
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        updateSelectedResultLabel(selectedResult, activeResultCount);
    }

    private void selectNoVisibleResults() {
        if (checkedPatchResultKeys.isEmpty()) {
            updateSelectedResultLabel(selectedResult, activeResultCount);
            return;
        }
        checkedPatchResultKeys.clear();
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        updateSelectedResultLabel(selectedResult, activeResultCount);
    }

    private boolean shouldStringCaseSensitive() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_STRING_CASE_SENSITIVE, false);
    }

    private boolean shouldAutoRangeMemorySearch() {
        try {
            if (chkAutoRange != null) return chkAutoRange.isChecked();
        } catch (Throwable ignored) {
        }
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_AUTO_RANGE, true);
    }

    private boolean shouldStringPatchTruncate() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MemoryToolHelper.KEY_STRING_PATCH_TRUNCATE, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    @Nullable
    private boolean isPatchHexEnabled() {
        try {
            return chkPatchHex != null && chkPatchHex.isChecked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void refreshPatchValueDisplayForMode() {
        if (selectedResult == null || edtPatchValue == null) return;
        edtPatchValue.setText(formatPatchValueForMode(selectedResult));
    }

    private String formatPatchValueForMode(MemoryResultRow row) {
        if (row == null) return "";
        if (!isPatchHexEnabled() || !isNumericPatchDataType(row.dataType)) {
            return row.value == null ? "" : row.value;
        }
        int width = byteWidthForDataType(row.dataType);
        if (width <= 0) width = 4;
        long value = parseUnsignedFlexible(row.value, 0L);
        return formatLittleEndianBytes(value, width);
    }

    private String normalizeHexPatchValueForTarget(String raw, MemoryResultRow target) {
        if (target == null || !isNumericPatchDataType(target.dataType)) {
            return raw;
        }
        int width = byteWidthForDataType(target.dataType);
        if (width <= 0) width = 4;
        try {
            long value = parseHexPatchBytes(raw, width);
            return Long.toUnsignedString(value);
        } catch (Throwable t) {
            appendStatus("Invalid hex patch value. Use bytes like \"72 75 65 2C\" or a hex value like \"0x2c657572\".");
            return null;
        }
    }

    private String normalizeStringPatchValueForTarget(String raw, MemoryResultRow target) {
        if (target == null || !isStringPatchDataType(target.dataType)) {
            return raw;
        }
        String value = raw == null ? "" : raw;
        if (!shouldStringPatchTruncate()) {
            return value;
        }
        int originalBytes = stringPatchByteLimitForTarget(target);
        if (originalBytes <= 0) {
            return value;
        }
        String truncated = truncateStringPatchValueForTarget(value, target);
        if (!TextUtils.equals(value, truncated)) {
            appendStatus("String patch truncated to " + originalBytes + " byte" + (originalBytes == 1 ? "" : "s") + " to match the selected value length.");
            try {
                if (edtPatchValue != null) edtPatchValue.setText(truncated);
            } catch (Throwable ignored) {
            }
        }
        return truncated;
    }

    private String truncateStringPatchValueForTarget(String raw, MemoryResultRow target) {
        String value = raw == null ? "" : raw;
        int byteLimit = stringPatchByteLimitForTarget(target);
        return byteLimit <= 0 ? value : trimUtf8ToByteLength(value, byteLimit);
    }

    private int stringPatchByteLimitForTarget(MemoryResultRow target) {
        if (target == null) return 0;
        String original = target.value == null ? "" : target.value;
        return original.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String trimUtf8ToByteLength(String value, int maxBytes) {
        if (TextUtils.isEmpty(value) || maxBytes <= 0) return "";
        int used = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int charCount = Character.charCount(codePoint);
            int byteCount = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (used + byteCount > maxBytes) {
                break;
            }
            used += byteCount;
            end += charCount;
        }
        return end >= value.length() ? value : value.substring(0, end);
    }

    private static boolean isStringPatchDataType(String dataType) {
        if (TextUtils.isEmpty(dataType)) return false;
        return dataType.toLowerCase(Locale.US).contains("string");
    }

    private static boolean isNumericPatchDataType(String dataType) {
        if (TextUtils.isEmpty(dataType)) return false;
        String v = dataType.toLowerCase(Locale.US);
        return v.contains("word") || v.contains("dword") || v.contains("qword");
    }

    private static int byteWidthForDataType(String dataType) {
        if (TextUtils.isEmpty(dataType)) return 4;
        String v = dataType.toLowerCase(Locale.US);
        if (v.contains("qword")) return 8;
        if (v.contains("dword")) return 4;
        if (v.contains("word")) return 2;
        if (v.equals("byte") || (v.contains("byte") && !v.contains("bytes"))) return 1;
        return 4;
    }

    private static long parseUnsignedFlexible(String raw, long fallback) {
        try {
            String v = raw == null ? "" : raw.trim();
            if (TextUtils.isEmpty(v)) return fallback;
            if (v.startsWith("0x") || v.startsWith("0X")) return Long.parseUnsignedLong(v.substring(2), 16);
            return Long.parseUnsignedLong(v);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String formatLittleEndianBytes(long value, int width) {
        StringBuilder sb = new StringBuilder(width * 3);
        for (int i = 0; i < width; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02X", (value >> (8 * i)) & 0xffL));
        }
        return sb.toString();
    }

    private static long parseHexPatchBytes(String raw, int width) {
        String v = raw == null ? "" : raw.trim();
        if (TextUtils.isEmpty(v)) throw new IllegalArgumentException("empty");
        if (v.startsWith("0x") || v.startsWith("0X")) {
            long value = Long.parseUnsignedLong(v.substring(2), 16);
            long mask = width >= 8 ? -1L : ((1L << (width * 8)) - 1L);
            return value & mask;
        }
        String compact = v.replace(",", " ").replace(":", " ").replace("-", " ");
        String[] parts = compact.trim().split("\\s+");
        if (parts.length == 1 && parts[0].matches("[0-9a-fA-F]+") && parts[0].length() > 2) {
            String hex = parts[0];
            if ((hex.length() & 1) != 0) hex = "0" + hex;
            parts = new String[hex.length() / 2];
            for (int i = 0; i < parts.length; i++) {
                parts[i] = hex.substring(i * 2, i * 2 + 2);
            }
        }
        if (parts.length < 1 || parts.length > width) throw new IllegalArgumentException("bad byte count");
        long value = 0L;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.matches("[0-9a-fA-F]{1,2}")) throw new IllegalArgumentException("bad byte");
            long b = Long.parseLong(part, 16) & 0xffL;
            value |= b << (8 * i);
        }
        return value;
    }

    private MemoryResultRow resolvePatchTargetRow() {
        String rawAddress = edtPatchAddress == null || edtPatchAddress.getText() == null
                ? null
                : edtPatchAddress.getText().toString().trim();
        if (TextUtils.isEmpty(rawAddress)) {
            return selectedResult;
        }
        long address;
        try {
            address = parseFlexibleAddress(rawAddress);
        } catch (Throwable ignored) {
            appendStatus("Patch address is invalid: " + rawAddress);
            return selectedResult;
        }
        if (selectedResult != null && selectedResult.address == address) {
            return selectedResult;
        }
        for (MemoryResultRow item : resultItems) {
            if (item != null && item.address == address) {
                return item;
            }
        }
        String dataType = selectedResult != null && !TextUtils.isEmpty(selectedResult.dataType)
                ? selectedResult.dataType
                : MemoryToolHelper.normalizeDataType(ddDataType == null || ddDataType.getText() == null ? null : ddDataType.getText().toString());
        if (TextUtils.isEmpty(dataType) || "all".equals(dataType)) {
            dataType = "dword";
        }
        String value = edtPatchValue == null || edtPatchValue.getText() == null ? "" : edtPatchValue.getText().toString();
        return new MemoryResultRow(0, address, dataType, converterForDataType(dataType), value, 0, 0);
    }

    private static long parseFlexibleAddress(String value) {
        String v = value == null ? "" : value.trim();
        if (v.startsWith("0x") || v.startsWith("0X")) {
            return Long.parseUnsignedLong(v.substring(2), 16);
        }
        return Long.parseLong(v);
    }

    private int getResultListThreshold() {
        try {
            if (edtResultLimit == null || edtResultLimit.getText() == null) return MemoryToolHelper.DEFAULT_RESULT_LIST_LIMIT;
            String s = edtResultLimit.getText().toString().trim();
            if (TextUtils.isEmpty(s)) return MemoryToolHelper.DEFAULT_RESULT_LIST_LIMIT;
            int value = Integer.parseInt(s);
            if (value < 1) return 1;
            if (value > 5000) return 5000;
            return value;
        } catch (Throwable ignored) {
            return MemoryToolHelper.DEFAULT_RESULT_LIST_LIMIT;
        }
    }

    private int getMaxScanResults() {
        try {
            if (edtMaxResults == null || edtMaxResults.getText() == null) return MemoryToolHelper.DEFAULT_MAX_RESULTS;
            String s = edtMaxResults.getText().toString().trim();
            if (TextUtils.isEmpty(s)) return MemoryToolHelper.DEFAULT_MAX_RESULTS;
            return MemoryToolHelper.normalizeMaxResults(Integer.parseInt(s));
        } catch (Throwable ignored) {
            return MemoryToolHelper.DEFAULT_MAX_RESULTS;
        }
    }

    private void showMemoryResultActionMenu(@Nullable MemoryResultRow row, @Nullable View anchor) {
        if (row == null || anchor == null) return;
        allowOverlayFocusFromUserInput();
        setOverlayInteractive(true);

        PopupMenu menu = new PopupMenu(overlayThemeContext == null ? this : overlayThemeContext, anchor);
        String key = resultKey(row);
        boolean frozen = freezePatchActive && freezePatchResultKeys.contains(key);
        menu.getMenu().add(0, 2, 0, "Freeze this result");
        if (frozen) {
            menu.getMenu().add(0, 3, 1, "Stop freeze");
        }
        menu.getMenu().add(0, 5, 2, "Open Hex here");
        menu.getMenu().add(0, 6, 3, "Show Disassembly");
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 2) {
                startFreezePatchForRow(row);
                return true;
            }
            if (id == 3) {
                stopFreezePatchForRow(row);
                return true;
            }
            if (id == 5) {
                selectMemoryResult(row);
                showHexOverlayWindow();
                return true;
            }
            if (id == 6) {
                selectMemoryResult(row);
                showDisassemblyOverlayWindow();
                return true;
            }
            return false;
        });
        menu.setOnDismissListener(ignored -> maybeReturnToPassiveMode());
        menu.show();
    }

    private void togglePatchResultChecked(@Nullable MemoryResultRow row) {
        if (row == null) return;
        String key = resultKey(row);
        if (TextUtils.isEmpty(key)) return;
        if (checkedPatchResultKeys.contains(key)) {
            checkedPatchResultKeys.remove(key);
        } else {
            checkedPatchResultKeys.add(key);
        }
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        updateSelectedResultLabel(selectedResult, activeResultCount);
    }

    private boolean applySavedPatchFieldsForRow(@Nullable MemoryResultRow row, boolean applyValue) {
        if (row == null) return false;
        String key = resultKey(row);
        String name = patchNameByKey.get(key);
        setEditTextValue(edtPatchName, name == null ? "" : name);
        String patchValue = savedPatchValueByKey.get(key);
        if (applyValue && !TextUtils.isEmpty(patchValue)) {
            setEditTextValue(edtPatchValue, patchValue);
            return true;
        }
        return false;
    }

    private void selectMemoryResult(MemoryResultRow row) {
        manualToolAddressOverride = null;
        selectedResult = row;
        updateSelectedResultLabel(row, activeResultCount);
        applyDumpRange(row);
        if (edtPatchAddress != null) edtPatchAddress.setText(formatHex(row.address));
        notifyToolAddressChanged(row);
        if (!applySavedPatchFieldsForRow(row, true)) {
            refreshPatchValueDisplayForMode();
        }
    }

    private void notifyToolAddressChanged(@Nullable MemoryResultRow row) {
        if (row == null) return;
        String address = formatHex(row.address);
        syncToolAddressToHex(address, false);
        syncToolAddressToDisassembly(address, true);
    }

    private void updateSelectedResultLabel(@Nullable MemoryResultRow row, int totalCount) {
        if (txtSelectedResult == null) return;
        int checkedCount = getCheckedPatchRows().size();
        String checkedSuffix = checkedCount > 0
                ? (" " + checkedCount + " checked for Patch Multiple.")
                : "";
        if (row == null) {
            txtSelectedResult.setText(totalCount > 0
                    ? ("Result set: " + totalCount + " address" + (totalCount == 1 ? "" : "es") + ". Tap one to patch only that address." + checkedSuffix)
                    : "No selected address. Scan refines active results; New Scan starts over.");
            return;
        }
        String name = patchNameByKey.get(resultKey(row));
        String namePrefix = TextUtils.isEmpty(name) ? "" : (name + " · ");
        txtSelectedResult.setText("Selected " + namePrefix + formatHex(row.address) + " · " + row.dataType + ". Patch will modify only this address." + checkedSuffix);
    }

    private void clearOverlayScanState() {
        clearOverlayScanState(true);
    }

    private void clearOverlayScanState(boolean resetRange) {
        stopFreezePatch(true);
        stopResultValueSyncLoop();
        if (resetRange) {
            resetScanRangeSession();
        } else {
            updateScanRangeStatus();
        }
        hasActiveResultSet = false;
        selectedResult = null;
        lastStateJson = null;
        resultStateSearchValue = null;
        resultPageIndex = 0;
        activeResultCount = 0;
        resultItems.clear();
        checkedPatchResultKeys.clear();
        patchNameByKey.clear();
        savedPatchValueByKey.clear();
        if (resultAdapter != null) resultAdapter.notifyDataSetChanged();
        if (lstResults != null) lstResults.setVisibility(View.GONE);
        if (rowSelectVisibleResults != null) rowSelectVisibleResults.setVisibility(View.GONE);
        if (edtPatchName != null) edtPatchName.setText("");
        if (edtPatchAddress != null) edtPatchAddress.setText("");
        updateSelectedResultLabel(null, 0);
    }

    private void clearOverlayEntryFieldsForFreshSession() {
        try { if (edtSearchValue != null) edtSearchValue.setText(""); } catch (Throwable ignored) {}
        try { if (edtPatchName != null) edtPatchName.setText(""); } catch (Throwable ignored) {}
        try { if (edtPatchAddress != null) edtPatchAddress.setText(""); } catch (Throwable ignored) {}
        try { if (edtPatchValue != null) edtPatchValue.setText(""); } catch (Throwable ignored) {}
        try { if (edtResultLimit != null) edtResultLimit.setText(Integer.toString(MemoryToolHelper.DEFAULT_RESULT_LIST_LIMIT)); } catch (Throwable ignored) {}
        try { if (edtMaxResults != null) edtMaxResults.setText(Integer.toString(MemoryToolHelper.DEFAULT_MAX_RESULTS)); } catch (Throwable ignored) {}
        try { if (ddDataType != null) ddDataType.setText(MemoryToolHelper.DEFAULT_DATA_TYPE, false); } catch (Throwable ignored) {}
        try { if (ddSearchMode != null) ddSearchMode.setText(MemoryToolHelper.DEFAULT_SEARCH_MODE, false); } catch (Throwable ignored) {}
        try { if (edtDumpBegin != null) edtDumpBegin.setText(""); } catch (Throwable ignored) {}
        try { if (edtDumpEnd != null) edtDumpEnd.setText(""); } catch (Throwable ignored) {}
    }

    private void applyDefaultDumpRangeIfNeeded() {
        if (selectedResult != null) {
            applyDumpRange(selectedResult);
            return;
        }
        if (!resultItems.isEmpty()) {
            applyDumpRange(resultItems.get(0));
        }
    }

    private void applyDumpRange(MemoryResultRow row) {
        if (row == null) return;
        long begin = row.address - 0x40L;
        if (begin < 0L) begin = 0L;
        long end = row.address + 0x80L;
        if (edtPatchAddress != null) edtPatchAddress.setText(formatHex(row.address));
        if (edtDumpBegin != null) edtDumpBegin.setText(formatHex(begin));
        if (edtDumpEnd != null) edtDumpEnd.setText(formatHex(end));
    }

    private void patchCheckedResults() {
        ArrayList<MemoryResultRow> rows = getCheckedPatchRows();
        if (rows.isEmpty()) {
            appendStatus("Check one or more visible result rows before using Patch Multiple.");
            return;
        }
        if (!arePatchRowsCompatible(rows)) {
            appendStatus("Patch Multiple requires checked rows with the same data type. Uncheck mixed result types first.");
            return;
        }
        String patchValue = edtPatchValue == null || edtPatchValue.getText() == null
                ? ""
                : edtPatchValue.getText().toString();
        MemoryResultRow first = rows.get(0);
        if (isPatchHexEnabled() && isNumericPatchDataType(first.dataType)) {
            patchValue = normalizeHexPatchValueForTarget(patchValue, first);
            if (patchValue == null) return;
        } else if (isStringPatchDataType(first.dataType)) {
            patchValue = normalizeStringPatchValueForTarget(patchValue, first);
            if (patchValue == null) return;
        }
        String stateOverride = buildResultStateJson(rows);
        if (TextUtils.isEmpty(stateOverride)) {
            appendStatus("Patch Multiple could not build a safe selected-result state.");
            return;
        }
        setPendingPatchUpdate(rows, patchValue);
        runMemoryCommand(
                "patch",
                null,
                patchValue,
                null,
                null,
                stateOverride,
                false,
                null);
    }

    private ArrayList<MemoryResultRow> getCheckedPatchRows() {
        ArrayList<MemoryResultRow> rows = new ArrayList<>();
        if (checkedPatchResultKeys.isEmpty()) return rows;
        pruneCheckedPatchResultKeysForCurrentResults();
        if (checkedPatchResultKeys.isEmpty()) return rows;
        for (MemoryResultRow row : resultItems) {
            if (row != null && checkedPatchResultKeys.contains(resultKey(row))) {
                rows.add(row);
            }
        }
        return rows;
    }

    private void reconcileVisiblePatchResultChecks() {
        pruneCheckedPatchResultKeysForCurrentResults();
        if (lstResults == null || resultAdapter == null) {
            updateSelectedResultLabel(selectedResult, activeResultCount);
            return;
        }
        int firstVisible = lstResults.getFirstVisiblePosition();
        int childCount = lstResults.getChildCount();
        for (int i = 0; i < childCount; i++) {
            int adapterPosition = firstVisible + i;
            if (adapterPosition < 0 || adapterPosition >= resultItems.size()) continue;
            MemoryResultRow row = resultItems.get(adapterPosition);
            String key = resultKey(row);
            if (TextUtils.isEmpty(key)) continue;
            CheckBox checkBox = findFirstCheckBox(lstResults.getChildAt(i));
            if (checkBox == null) continue;
            if (checkBox.isChecked()) {
                checkedPatchResultKeys.add(key);
            } else {
                checkedPatchResultKeys.remove(key);
            }
        }
        updateSelectedResultLabel(selectedResult, activeResultCount);
    }

    private void pruneCheckedPatchResultKeysForCurrentResults() {
        if (checkedPatchResultKeys.isEmpty()) return;
        Set<String> validKeys = new HashSet<>();
        for (MemoryResultRow row : resultItems) {
            String key = resultKey(row);
            if (!TextUtils.isEmpty(key)) validKeys.add(key);
        }
        checkedPatchResultKeys.retainAll(validKeys);
    }

    @Nullable
    private CheckBox findFirstCheckBox(@Nullable View view) {
        if (view instanceof CheckBox) return (CheckBox) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            CheckBox checkBox = findFirstCheckBox(group.getChildAt(i));
            if (checkBox != null) return checkBox;
        }
        return null;
    }

    private boolean arePatchRowsCompatible(List<MemoryResultRow> rows) {
        if (rows == null || rows.isEmpty()) return false;
        MemoryResultRow first = rows.get(0);
        String firstType = first == null ? "" : first.dataType;
        String firstConverter = first == null ? "" : (TextUtils.isEmpty(first.converterId) ? converterForDataType(firstType) : first.converterId);
        for (MemoryResultRow row : rows) {
            if (row == null) return false;
            String converter = TextUtils.isEmpty(row.converterId) ? converterForDataType(row.dataType) : row.converterId;
            if (!TextUtils.equals(firstType, row.dataType) || !TextUtils.equals(firstConverter, converter)) {
                return false;
            }
        }
        return true;
    }

    private void setPendingPatchUpdate(@Nullable List<MemoryResultRow> rows, @Nullable String patchValue) {
        pendingPatchResultUpdates = rows == null ? null : new ArrayList<>(rows);
        pendingPatchResultValue = patchValue;
    }

    private ArrayList<MemoryResultRow> singleRowList(MemoryResultRow row) {
        if (row == null) return null;
        ArrayList<MemoryResultRow> rows = new ArrayList<>(1);
        rows.add(row);
        return rows;
    }

    private String buildSelectedResultStateJson(MemoryResultRow row) {
        if (row == null) return null;
        ArrayList<MemoryResultRow> rows = singleRowList(row);
        return rows == null ? null : buildResultStateJson(rows);
    }

    private String buildResultStateJson(List<MemoryResultRow> rows) {
        if (rows == null || rows.isEmpty()) return null;
        try {
            JSONObject out = new JSONObject();
            String pid = readPidFromLastState();
            if (TextUtils.isEmpty(pid)) pid = getSelectedPid();
            if (!TextUtils.isEmpty(pid)) out.put("pid", pid);
            out.put("attach_requested", readAttachRequestedFromLastState());

            ArrayList<PatchStateGroup> groups = new ArrayList<>();
            for (MemoryResultRow row : rows) {
                if (row == null) continue;
                String dataType = TextUtils.isEmpty(row.dataType) ? "word" : row.dataType;
                String converter = TextUtils.isEmpty(row.converterId) ? converterForDataType(dataType) : row.converterId;
                PatchStateGroup group = null;
                for (PatchStateGroup candidate : groups) {
                    if (TextUtils.equals(candidate.dataType, dataType) && TextUtils.equals(candidate.converter, converter)) {
                        group = candidate;
                        break;
                    }
                }
                if (group == null) {
                    group = new PatchStateGroup(dataType, converter);
                    groups.add(group);
                }
                group.addrs.put(row.address);
                group.values.put(row.value == null ? "" : row.value);
            }

            JSONArray founds = new JSONArray();
            for (PatchStateGroup group : groups) {
                JSONObject found = new JSONObject();
                found.put("addrs", group.addrs);
                found.put("converter", group.converter);
                found.put("data_type", group.dataType);
                found.put("values", group.values);
                founds.put(found);
            }
            out.put("founds", founds);
            return out.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class PatchStateGroup {
        final String dataType;
        final String converter;
        final JSONArray addrs = new JSONArray();
        final JSONArray values = new JSONArray();

        PatchStateGroup(String dataType, String converter) {
            this.dataType = dataType == null ? "" : dataType;
            this.converter = converter == null ? "" : converter;
        }
    }

    private String readPidFromLastState() {
        try {
            if (TextUtils.isEmpty(lastStateJson)) return null;
            return new JSONObject(lastStateJson).optString("pid", null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean readAttachRequestedFromLastState() {
        try {
            if (TextUtils.isEmpty(lastStateJson)) return false;
            return new JSONObject(lastStateJson).optBoolean("attach_requested", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String converterForDataType(String dataType) {
        if (TextUtils.isEmpty(dataType)) return "word";
        String lower = dataType.toLowerCase(Locale.US);
        if (lower.contains("string")) return "string";
        if (lower.contains("hex") || lower.contains("bytes") || lower.startsWith("file:")) return "bytes";
        if (lower.contains("qword")) return "qword";
        if (lower.contains("dword")) return "dword";
        if (lower.contains("word")) return "word";
        return "word";
    }

    private static String formatHex(long value) {
        return "0x" + Long.toHexString(value);
    }

    private boolean isRoutineTidLine(String line) {
        return line != null && (line.startsWith("Attached TID:") || line.startsWith("Detached TID:"));
    }

    private void attachDragHandler(View header) {
        if (header == null) return;
        header.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float touchX;
            private float touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event == null || root == null) return false;
                try {
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = p.x;
                            startY = p.y;
                            touchX = event.getRawX();
                            touchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            p.x = startX + (int) (event.getRawX() - touchX);
                            p.y = startY + (int) (event.getRawY() - touchY);
                            wm.updateViewLayout(root, p);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            saveOverlayBounds(p);
                            return true;
                        default:
                            return false;
                    }
                } catch (Throwable ignored) {
                    return false;
                }
            }
        });
    }

    private void attachResizeHandler(View handle) {
        if (handle == null) return;
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int startW;
            private int startH;
            private float touchX;
            private float touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event == null || root == null) return false;
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                if (!sp.getBoolean(MemoryToolHelper.KEY_OVERLAY_RESIZABLE, true)) return false;
                try {
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startW = p.width;
                            startH = p.height <= 0 ? root.getHeight() : p.height;
                            touchX = event.getRawX();
                            touchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            boolean vrMode = PermsTestVrOverlayCompat.isEnabled(MemoryOverlayService.this);
                            int minWidth = vrMode ? dp(500) : MemoryOverlayWindowSupport.fitOverlayWidth(MemoryOverlayService.this, MemoryOverlayWindowSupport.scaleOverlayPx(MemoryOverlayService.this, dp(480)));
                            int minHeight = vrMode ? dp(260) : MemoryOverlayWindowSupport.fitOverlayHeight(MemoryOverlayService.this, MemoryOverlayWindowSupport.scaleOverlayPx(MemoryOverlayService.this, dp(260)));
                            p.width = Math.max(minWidth, startW + (int) (event.getRawX() - touchX));
                            p.height = Math.max(minHeight, startH + (int) (event.getRawY() - touchY));
                            wm.updateViewLayout(root, p);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            saveOverlayBounds(p);
                            return true;
                        default:
                            return false;
                    }
                } catch (Throwable ignored) {
                    return false;
                }
            }
        });
    }

    private void resetMemoryOverlayWindows() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .remove(PREF_OVERLAY_X)
                    .remove(PREF_OVERLAY_Y)
                    .remove(PREF_OVERLAY_W)
                    .remove(PREF_OVERLAY_H)
                    .remove(PREF_VR_OVERLAY_X)
                    .remove(PREF_VR_OVERLAY_Y)
                    .remove(PREF_VR_OVERLAY_W)
                    .remove(PREF_VR_OVERLAY_H)
                    .remove("memory_hex_overlay_x")
                    .remove("memory_hex_overlay_y")
                    .remove("memory_hex_overlay_w")
                    .remove("memory_hex_overlay_h")
                    .remove("memory_disasm_overlay_x")
                    .remove("memory_disasm_overlay_y")
                    .remove("memory_disasm_overlay_w")
                    .remove("memory_disasm_overlay_h")
                    .remove("memory_special_overlay_x")
                    .remove("memory_special_overlay_y")
                    .remove("memory_special_overlay_w")
                    .remove("memory_special_overlay_h")
                    .apply();
            boolean vrMode = PermsTestVrOverlayCompat.isEnabled(this);
            if (!vrMode && overlayLayoutParams != null && wm != null && root != null) {
                int defaultWidth = MemoryOverlayWindowSupport.fitOverlayWidth(this, MemoryOverlayWindowSupport.scaleOverlayPx(this, dp(500)));
                overlayLayoutParams.width = defaultWidth;
                overlayLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                overlayLayoutParams.x = MemoryOverlayWindowSupport.scaleOverlayPx(this, dp(16));
                overlayLayoutParams.y = MemoryOverlayWindowSupport.scaleOverlayPx(this, dp(80));
                try { wm.updateViewLayout(root, overlayLayoutParams); } catch (Throwable ignored) {}
            }
            appendDetailLog("Memory overlay windows reset to defaults.");
        } catch (Throwable t) {
            appendDetailLog("Memory overlay window reset failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void saveOverlayBounds(WindowManager.LayoutParams p) {
        boolean vrMode = PermsTestVrOverlayCompat.isEnabled(this);
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(vrMode ? PREF_VR_OVERLAY_X : PREF_OVERLAY_X, p.x)
                .putInt(vrMode ? PREF_VR_OVERLAY_Y : PREF_OVERLAY_Y, p.y)
                .putInt(vrMode ? PREF_VR_OVERLAY_W : PREF_OVERLAY_W, p.width)
                .putInt(vrMode ? PREF_VR_OVERLAY_H : PREF_OVERLAY_H, p.height)
                .apply();
    }

    private int dp(int value) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (value * d + 0.5f);
    }

    private boolean isDebugOutputEnabled() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_DEBUG_OUTPUT, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void debugLog(String message) {
        if (!isDebugOutputEnabled()) return;
        try { Log.d(LOG_TAG, message == null ? "" : message); } catch (Throwable ignored) {}
    }

    private void debugLog(String message, Throwable t) {
        if (!isDebugOutputEnabled()) return;
        try { Log.d(LOG_TAG, message == null ? "" : message, t); } catch (Throwable ignored) {}
    }

    private static String cleanLogValue(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private void runOnUiThread(Runnable runnable) {
        if (runnable == null) return;
        new android.os.Handler(getMainLooper()).post(runnable);
    }

    private String resultKey(MemoryResultRow row) {
        if (row == null) return "";
        return row.groupIndex + ":" + row.indexInGroup + ":" + row.address + ":" + row.dataType + ":" + row.converterId;
    }

    private final class MemoryResultAdapter extends ArrayAdapter<MemoryResultRow> {
        MemoryResultAdapter(ContextThemeWrapper context, ArrayList<MemoryResultRow> rows) {
            super(context, android.R.layout.simple_list_item_1, rows);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout rowView;
            CheckBox checkBox;
            TextView textView;
            if (convertView instanceof LinearLayout) {
                rowView = (LinearLayout) convertView;
                checkBox = (CheckBox) rowView.getChildAt(0);
                textView = (TextView) rowView.getChildAt(1);
            } else {
                rowView = new LinearLayout(getContext());
                rowView.setOrientation(LinearLayout.HORIZONTAL);
                rowView.setGravity(Gravity.CENTER_VERTICAL);
                rowView.setPadding(dp(6), dp(5), dp(6), dp(5));

                checkBox = new CheckBox(getContext());
                checkBox.setMinWidth(0);
                checkBox.setMinHeight(0);
                checkBox.setFocusable(false);
                checkBox.setFocusableInTouchMode(false);
                rowView.addView(checkBox, new LinearLayout.LayoutParams(dp(34), dp(34)));

                textView = new TextView(getContext());
                textView.setTextSize(14f);
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                rowView.addView(textView, textParams);
            }

            MemoryResultRow row = getItem(position);
            String key = resultKey(row);
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(checkedPatchResultKeys.contains(key));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    checkedPatchResultKeys.add(key);
                } else {
                    checkedPatchResultKeys.remove(key);
                }
                updateSelectedResultLabel(selectedResult, activeResultCount);
            });
            if (row == null) {
                textView.setText("");
            } else {
                String name = patchNameByKey.get(key);
                textView.setText(row.formatDisplayText(name));
            }
            return rowView;
        }
    }

    private static final class ResultValueSyncTarget {
        final int index;
        final String key;
        final MemoryResultRow row;

        ResultValueSyncTarget(int index, String key, MemoryResultRow row) {
            this.index = index;
            this.key = key == null ? "" : key;
            this.row = row;
        }
    }

    private static final class ResultValueSyncUpdate {
        final int index;
        final String key;
        final String value;

        ResultValueSyncUpdate(int index, String key, String value) {
            this.index = index;
            this.key = key == null ? "" : key;
            this.value = value == null ? "" : value;
        }
    }

    private static final class MemoryResultRow {
        final int displayIndex;
        final long address;
        final String dataType;
        final String converterId;
        final String value;
        final int groupIndex;
        final int indexInGroup;

        MemoryResultRow(int displayIndex, long address, String dataType, String converterId, String value, int groupIndex, int indexInGroup) {
            this.displayIndex = displayIndex;
            this.address = address;
            this.dataType = dataType == null ? "" : dataType;
            this.converterId = converterId == null ? "" : converterId;
            this.value = value == null ? "" : value;
            this.groupIndex = groupIndex;
            this.indexInGroup = indexInGroup;
        }

        MemoryResultRow withValue(String newValue) {
            return new MemoryResultRow(displayIndex, address, dataType, converterId, newValue, groupIndex, indexInGroup);
        }

        String formatDisplayText(String name) {
            String cleanName = name == null ? "" : name.trim();
            String namePart = TextUtils.isEmpty(cleanName) ? "" : (cleanName + " · ");
            String suffix = TextUtils.isEmpty(value) ? "" : (" · value=" + value);
            return String.format(Locale.US, "%d. %s0x%x · %s%s", displayIndex, namePart, address, dataType, suffix);
        }

        @Override
        public String toString() {
            return formatDisplayText(null);
        }
    }

    @Override
    public void onDestroy() {
        stopNotificationWatchdog();
        stopResultValueSyncLoop();
        PermsTestVrOverlayCompat.clearHiddenOverlayForVr(this);
        stopFreezePatch(false);
        try {
            if (activePanelContainer != null && root != null) {
                detachViewFromParent(root);
            } else if (wm != null && root != null) {
                wm.removeView(root);
            }
        } catch (Throwable ignored) {
        }
        if (hexOverlayController != null) {
            hexOverlayController.destroy();
        }
        if (disassemblyOverlayController != null) {
            disassemblyOverlayController.destroy();
        }
        if (specialToolsOverlayController != null) {
            specialToolsOverlayController.destroy();
        }
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        resetDetailSessionLog();
        root = null;
        hexOverlayController = null;
        disassemblyOverlayController = null;
        specialToolsOverlayController = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }
}
