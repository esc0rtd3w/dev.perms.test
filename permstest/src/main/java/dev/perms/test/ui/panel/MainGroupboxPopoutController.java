package dev.perms.test.ui.panel;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import dev.perms.test.ExecMode;
import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.home.HomeAppTrayAdapter;
import dev.perms.test.home.HomeAppTrayController;
import dev.perms.test.settings.SettingsPreferenceKeys;
import dev.perms.test.ui.CollapsibleGroupboxController;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.NoFilterArrayAdapter;
import dev.perms.test.ui.dialog.MovableDialogChrome;

/** Adds controlled cloned popout windows for selected Main tab groupboxes. */
public final class MainGroupboxPopoutController {
    public interface Host {
        int dp(int dip);
        void debugOutput(String area, String message);
        void selectExecMode(ExecMode mode);
    }

    private static final Set<String> ACTIVE_KEYS = Collections.synchronizedSet(new HashSet<>());

    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final SharedPreferences prefs;
    private final HomeAppTrayController homeAppTrayController;
    private final Host host;

    public MainGroupboxPopoutController(AppCompatActivity activity,
                                        ActivityMainBinding binding,
                                        SharedPreferences prefs,
                                        HomeAppTrayController homeAppTrayController,
                                        Host host) {
        this.activity = activity;
        this.binding = binding;
        this.prefs = prefs;
        this.homeAppTrayController = homeAppTrayController;
        this.host = host;
    }

    public void bind() {
        try {
            if (binding == null || binding.tabMain == null) return;
            bindButton(binding.tabMain.btnMainBackendPopout, this::openBackendPopout);
            bindButton(binding.tabMain.btnMainHomePopout, this::openHomePopout);
            bindHomePopoutFullWindowOption();
        } catch (Throwable ignored) {
        }
    }

    private void bindButton(MaterialButton button, View.OnClickListener listener) {
        if (button == null) return;
        try {
            button.setAllCaps(false);
            button.setOnClickListener(listener);
        } catch (Throwable ignored) {
        }
    }

    private void bindHomePopoutFullWindowOption() {
        try {
            MaterialCheckBox checkbox = binding.tabMain.chkMainHomePopoutFullWindow;
            if (checkbox == null) return;
            checkbox.setChecked(isHomePopoutFullWindowEnabled());
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    prefs.edit().putBoolean(SettingsPreferenceKeys.HOME_POPOUT_FULL_WINDOW, isChecked).apply();
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void openBackendPopout(View ignored) {
        if (!ensurePanelsEnabled()) return;
        final String key = "main:backend";
        if (!reserve(key)) return;
        try {
            View content = LayoutInflater.from(activity).inflate(R.layout.popout_main_backend, null, false);
            final boolean[] syncing = new boolean[]{false};
            bindBackendClone(content, syncing);
            syncBackendClone(content, syncing);

            final Runnable syncRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        syncBackendClone(content, syncing);
                        content.postDelayed(this, 1000L);
                    } catch (Throwable ignored) {
                    }
                }
            };
            content.postDelayed(syncRunnable, 1000L);
            showMovableClone(key, "Backend", content, () -> content.removeCallbacks(syncRunnable));
            debug(key, "opened cloned groupbox: Backend");
        } catch (Throwable t) {
            release(key);
            debug(key, "open failed: " + safeMessage(t));
            Toast.makeText(activity, "Backend popout failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private void openHomePopout(View ignored) {
        if (!ensurePanelsEnabled()) return;
        if (isHomePopoutFullWindowEnabled()) {
            try {
                if (homeAppTrayController != null) {
                    homeAppTrayController.openAppTrayPanel();
                    return;
                }
            } catch (Throwable t) {
                debug("main:home", "full-window open failed: " + safeMessage(t));
                Toast.makeText(activity, "Home full-window popout failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
                return;
            }
        }
        final String key = "main:home";
        if (!reserve(key)) return;
        try {
            View content = LayoutInflater.from(activity).inflate(R.layout.popout_main_home, null, false);
            final boolean[] syncing = new boolean[]{false};
            bindHomeClone(content, syncing);
            syncHomeClone(content, syncing);

            HomeAppTrayAdapter adapter = null;
            try {
                RecyclerView rv = content.findViewById(R.id.rvMainHomeAppTrayClone);
                if (homeAppTrayController != null && rv != null) {
                    adapter = homeAppTrayController.attachClone(rv);
                }
            } catch (Throwable ignored1) {
            }
            final HomeAppTrayAdapter cloneAdapter = adapter;
            showMovableClone(key, "Home", content, () -> {
                try {
                    if (homeAppTrayController != null) homeAppTrayController.detachClone(cloneAdapter);
                } catch (Throwable ignored12) {
                }
            });
            debug(key, "opened cloned groupbox: Home");
        } catch (Throwable t) {
            release(key);
            debug(key, "open failed: " + safeMessage(t));
            Toast.makeText(activity, "Home popout failed: " + safeMessage(t), Toast.LENGTH_LONG).show();
        }
    }

    private void bindBackendClone(View clone, final boolean[] syncing) {
        if (clone == null) return;
        try {
            bindExecModeDropdown(clone);
            int[] buttonIds = {
                    R.id.btnRefresh,
                    R.id.btnRequest,
                    R.id.btnRunId,
                    R.id.btnOpenShizuku,
                    R.id.btnStartShizuku,
                    R.id.btnDownloadShizuku,
                    R.id.btnInternalShizukuPairStart,
                    R.id.btnInternalShizukuStartServer,
                    R.id.btnInternalShizukuStopServer,
                    R.id.btnInternalShizukuWirelessSettings,
                    R.id.btnOpenWirelessDebug,
                    R.id.btnOpenPairHelper,
                    R.id.btnLadbConnect,
                    R.id.btnLadbDisconnect,
                    R.id.btnLadbPair,
                    R.id.btnLadbPairNotif
            };
            for (int id : buttonIds) bindProxyButton(clone, id, syncing);

            bindProxyCheckBox(clone, R.id.chkPairHelperNotification, syncing);
            bindProxyCheckBox(clone, R.id.chkLadbAutoConnect, syncing);
        } catch (Throwable ignored) {
        }
    }

    private void bindHomeClone(View clone, final boolean[] syncing) {
        if (clone == null) return;
        bindProxyCheckBox(clone, R.id.chkRunAsLauncher, syncing);
        bindProxyCheckBox(clone, R.id.chkKioskMode, syncing);
        bindProxyCheckBox(clone, R.id.chkAutoRestartLauncher, syncing);
    }

    private void bindExecModeDropdown(View clone) {
        try {
            AutoCompleteTextView dd = clone.findViewById(R.id.ddExecMode);
            TextInputLayout til = clone.findViewById(R.id.tilExecMode);
            if (dd == null) return;
            ArrayAdapter<String> adapter = new NoFilterArrayAdapter(
                    activity,
                    android.R.layout.simple_list_item_1,
                    new String[]{ExecMode.SHIZUKU.displayName(), ExecMode.INTERNAL_SHIZUKU.displayName(), ExecMode.SYSTEM.displayName(), ExecMode.LADB.displayName()}
            );
            dd.setAdapter(adapter);
            DropdownUi.bindTapOnlyExposedDropdown(
                    activity,
                    til,
                    dd,
                    ViewConfiguration.get(activity).getScaledTouchSlop(),
                    300,
                    () -> DropdownUi.showDropdownAtLastSelection(dd,
                            dd.getText() == null ? "" : dd.getText().toString(),
                            null));
            dd.setText(currentExecMode().displayName(), false);
            dd.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    Object item = parent == null ? null : parent.getItemAtPosition(position);
                    ExecMode mode = ExecMode.fromDisplayName(item == null ? null : item.toString());
                    if (mode == null) mode = ExecMode.SHIZUKU;
                    if (host != null) host.selectExecMode(mode);
                    dd.setText(mode.displayName(), false);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private ExecMode currentExecMode() {
        try {
            return ExecMode.get(prefs);
        } catch (Throwable ignored) {
            return ExecMode.SHIZUKU;
        }
    }

    private void bindProxyButton(View clone, int id, final boolean[] syncing) {
        View dst = clone.findViewById(id);
        final View src = findOriginalBackendView(id);
        if (dst == null || src == null) return;
        dst.setOnClickListener(v -> {
            try {
                copyCloneInputsToOriginal(clone);
                src.performClick();
                clone.postDelayed(() -> syncBackendClone(clone, syncing), 250L);
            } catch (Throwable ignored) {
            }
        });
    }

    private void bindProxyCheckBox(View clone, int id, final boolean[] syncing) {
        View dst = clone.findViewById(id);
        if (!(dst instanceof CompoundButton)) return;
        final CompoundButton clonedButton = (CompoundButton) dst;
        clonedButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncing[0]) return;
            try {
                View src = findOriginalViewForCloneId(id);
                if (src instanceof CompoundButton) {
                    ((CompoundButton) src).setChecked(isChecked);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void syncBackendClone(View clone, final boolean[] syncing) {
        if (clone == null || binding == null || binding.tabMain == null) return;
        syncing[0] = true;
        try {
            int[] ids = {
                    R.id.txtStatus,
                    R.id.txtShizukuOptionsHeader,
                    R.id.grpShizukuRow1,
                    R.id.grpShizukuRow2,
                    R.id.btnRefresh,
                    R.id.btnRequest,
                    R.id.btnRunId,
                    R.id.btnOpenShizuku,
                    R.id.btnStartShizuku,
                    R.id.btnDownloadShizuku,
                    R.id.txtInternalShizukuOptionsHeader,
                    R.id.txtInternalShizukuModeHint,
                    R.id.rowInternalShizukuButtons,
                    R.id.btnInternalShizukuPairStart,
                    R.id.btnInternalShizukuStartServer,
                    R.id.btnInternalShizukuStopServer,
                    R.id.rowInternalShizukuWirelessSettings,
                    R.id.btnInternalShizukuWirelessSettings,
                    R.id.txtInternalShizukuPairHint,
                    R.id.txtLadbOptionsHeader,
                    R.id.includeExecModeLadb,
                    R.id.txtLadbStatus,
                    R.id.txtWirelessDebugInfo,
                    R.id.btnOpenWirelessDebug,
                    R.id.btnOpenPairHelper,
                    R.id.chkPairHelperNotification,
                    R.id.chkLadbAutoConnect,
                    R.id.btnLadbConnect,
                    R.id.btnLadbDisconnect,
                    R.id.edtLadbConnectPort,
                    R.id.btnLadbPair,
                    R.id.btnLadbPairNotif,
                    R.id.edtLadbPairPort,
                    R.id.edtLadbPairCode
            };
            for (int id : ids) copyStateIgnoringGroupboxCollapse(findOriginalBackendView(id), clone.findViewById(id));
            AutoCompleteTextView dd = clone.findViewById(R.id.ddExecMode);
            if (dd != null && !dd.hasFocus()) dd.setText(currentExecMode().displayName(), false);
        } catch (Throwable ignored) {
        } finally {
            syncing[0] = false;
        }
    }

    private void syncHomeClone(View clone, final boolean[] syncing) {
        if (clone == null || binding == null || binding.tabMain == null) return;
        syncing[0] = true;
        try {
            copyStateIgnoringGroupboxCollapse(binding.tabMain.chkRunAsLauncher, clone.findViewById(R.id.chkRunAsLauncher));
            copyStateIgnoringGroupboxCollapse(binding.tabMain.chkKioskMode, clone.findViewById(R.id.chkKioskMode));
            copyStateIgnoringGroupboxCollapse(binding.tabMain.chkAutoRestartLauncher, clone.findViewById(R.id.chkAutoRestartLauncher));
        } catch (Throwable ignored) {
        } finally {
            syncing[0] = false;
        }
    }

    private void copyCloneInputsToOriginal(View clone) {
        if (clone == null) return;
        int[] ids = {
                R.id.chkPairHelperNotification,
                R.id.chkLadbAutoConnect,
                R.id.edtLadbConnectPort,
                R.id.edtLadbPairPort,
                R.id.edtLadbPairCode
        };
        for (int id : ids) copyInput(clone.findViewById(id), findOriginalBackendView(id));
    }

    private void copyInput(View src, View dst) {
        if (src == null || dst == null) return;
        try {
            if (src instanceof CompoundButton && dst instanceof CompoundButton) {
                ((CompoundButton) dst).setChecked(((CompoundButton) src).isChecked());
            } else if (src instanceof TextView && dst instanceof TextView) {
                ((TextView) dst).setText(((TextView) src).getText());
            }
        } catch (Throwable ignored) {
        }
    }

    private void copyStateIgnoringGroupboxCollapse(View src, View dst) {
        copyState(src, dst, true);
    }

    private void copyState(View src, View dst) {
        copyState(src, dst, false);
    }

    private void copyState(View src, View dst, boolean ignoreGroupboxCollapse) {
        if (src == null || dst == null) return;
        try {
            int visibility = ignoreGroupboxCollapse
                    ? CollapsibleGroupboxController.visibilityIgnoringGroupboxCollapse(src)
                    : src.getVisibility();
            dst.setVisibility(visibility);
        } catch (Throwable ignored) {}
        try { dst.setEnabled(src.isEnabled()); } catch (Throwable ignored) {}
        try { dst.setAlpha(src.getAlpha()); } catch (Throwable ignored) {}
        try {
            if (src instanceof CompoundButton && dst instanceof CompoundButton) {
                ((CompoundButton) dst).setChecked(((CompoundButton) src).isChecked());
            }
        } catch (Throwable ignored) {}
        try {
            if (src instanceof TextView && dst instanceof TextView) {
                if (dst instanceof EditText && dst.hasFocus()) return;
                CharSequence text = ((TextView) src).getText();
                if (!TextUtils.equals(((TextView) dst).getText(), text)) {
                    ((TextView) dst).setText(text);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private View findOriginalViewForCloneId(int id) {
        View v = findOriginalBackendView(id);
        if (v != null) return v;
        if (binding != null && binding.tabMain != null) {
            if (id == R.id.chkRunAsLauncher) return binding.tabMain.chkRunAsLauncher;
            if (id == R.id.chkKioskMode) return binding.tabMain.chkKioskMode;
            if (id == R.id.chkAutoRestartLauncher) return binding.tabMain.chkAutoRestartLauncher;
        }
        return null;
    }

    private View findOriginalBackendView(int id) {
        try {
            if (binding == null || binding.tabMain == null || binding.tabMain.cardMainBackend == null) return null;
            return binding.tabMain.cardMainBackend.findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void showMovableClone(String key, String title, View content, Runnable onDismiss) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        MovableDialogChrome.Chrome chrome = MovableDialogChrome.create(activity, scroll, MovableDialogChrome.STYLE_FULL);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(chrome.root)
                .create();
        dialog.setOnDismissListener(d -> {
            try { if (onDismiss != null) onDismiss.run(); } catch (Throwable ignored) {}
            release(key);
            debug(key, "closed cloned groupbox: " + title);
        });
        if (chrome.closeButton != null) chrome.closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        MovableDialogChrome.applyWindowStyle(dialog, MovableDialogChrome.STYLE_FULL, MovableDialogChrome.FIT_CURRENT);
        MovableDialogChrome.enable(dialog, chrome.dragHandle);
    }

    private boolean isHomePopoutFullWindowEnabled() {
        try {
            return prefs.getBoolean(SettingsPreferenceKeys.HOME_POPOUT_FULL_WINDOW, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean ensurePanelsEnabled() {
        if (PermsTestPanelSettings.isPanelHostEnabled(activity)) return true;
        Toast.makeText(activity, "Enable Popout Panels in Settings > UI first", Toast.LENGTH_SHORT).show();
        return false;
    }

    private boolean reserve(String key) {
        synchronized (ACTIVE_KEYS) {
            if (ACTIVE_KEYS.contains(key)) {
                debug(key, "open skipped: already open");
                Toast.makeText(activity, "That groupbox popout is already open", Toast.LENGTH_SHORT).show();
                return false;
            }
            ACTIVE_KEYS.add(key);
            return true;
        }
    }

    private void release(String key) {
        if (key == null) return;
        ACTIVE_KEYS.remove(key);
    }

    private void debug(String area, String message) {
        try { if (host != null) host.debugOutput(area, message); } catch (Throwable ignored) {}
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.toString() : msg;
    }
}
