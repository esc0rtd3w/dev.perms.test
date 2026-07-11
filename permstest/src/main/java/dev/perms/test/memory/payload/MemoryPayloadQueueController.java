package dev.perms.test.memory.payload;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

import dev.perms.test.R;
import dev.perms.test.memory.overlay.MemoryOverlayService;

/**
 * Activity-side queue for manually applying package-scoped memory payloads.
 *
 * This controller owns only the Memory-tab queue UI. Actual launch/wait/apply
 * work stays in MemoryOverlayService so manual runs share the same backend path
 * as Launch With Payloads and pinned payload shortcuts.
 */
public final class MemoryPayloadQueueController {
    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    public interface TargetPidProvider {
        String getTargetPid();
    }

    private static final String PREFS = "perms_test";
    private static final String KEY_QUEUE_PREFIX = "memory_payload_run_queue_";

    private final Context context;
    private final View root;
    private final TargetPackageProvider targetPackageProvider;
    private final TargetPidProvider targetPidProvider;
    private final SharedPreferences prefs;

    private LinearLayout queueList;
    private TextView status;
    private final ArrayList<String> queuedPayloadFiles = new ArrayList<>();
    private String loadedPackage = "";
    private int selectedIndex = -1;

    public MemoryPayloadQueueController(Context context,
                                        View root,
                                        TargetPackageProvider targetPackageProvider,
                                        TargetPidProvider targetPidProvider) {
        this.context = context;
        this.root = root;
        this.targetPackageProvider = targetPackageProvider;
        this.targetPidProvider = targetPidProvider;
        this.prefs = context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void bind() {
        if (root == null || context == null) return;
        View add = root.findViewById(R.id.btnMemoryPayloadQueueAdd);
        View remove = root.findViewById(R.id.btnMemoryPayloadQueueRemove);
        View run = root.findViewById(R.id.btnMemoryPayloadQueueRun);
        queueList = root.findViewById(R.id.listMemoryPayloadQueue);
        status = root.findViewById(R.id.txtMemoryPayloadQueueStatus);

        if (add != null) add.setOnClickListener(v -> showAddPayloadDialog());
        if (remove != null) remove.setOnClickListener(v -> removeSelectedPayload());
        if (run != null) run.setOnClickListener(v -> startQueuedPayloads());

        refreshForCurrentPackage();
    }

    private void refreshForCurrentPackage() {
        String pkg = getTargetPackage();
        if (!TextUtils.equals(pkg, loadedPackage)) {
            loadedPackage = pkg;
            selectedIndex = -1;
            queuedPayloadFiles.clear();
            queuedPayloadFiles.addAll(loadQueue(pkg));
        }
        renderQueue();
    }

    private void showAddPayloadDialog() {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            showStatus("Select a target package before adding payloads.", true);
            return;
        }
        refreshForCurrentPackage();
        showStatus("Loading payloads for " + pkg + "...", false);
        new Thread(() -> {
            final ArrayList<PayloadChoice> choices = loadPayloadChoices(pkg);
            runOnUi(() -> showAddPayloadDialog(pkg, choices));
        }, "MemoryPayloadQueueList").start();
    }

    private void showAddPayloadDialog(String pkg, ArrayList<PayloadChoice> choices) {
        if (choices == null || choices.isEmpty()) {
            showStatus("No enabled complete payloads found for " + pkg + ".", true);
            return;
        }
        final String[] labels = new String[choices.size()];
        final boolean[] checked = new boolean[choices.size()];
        HashSet<String> existing = new HashSet<>(queuedPayloadFiles);
        for (int i = 0; i < choices.size(); i++) {
            PayloadChoice choice = choices.get(i);
            labels[i] = choice.label;
            checked[i] = existing.contains(choice.fileName);
        }
        new AlertDialog.Builder(context)
                .setTitle("Add Payloads To Queue")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    int added = 0;
                    for (int i = 0; i < choices.size(); i++) {
                        if (!checked[i]) continue;
                        String fileName = choices.get(i).fileName;
                        if (!queuedPayloadFiles.contains(fileName)) {
                            queuedPayloadFiles.add(fileName);
                            added++;
                        }
                    }
                    saveQueue();
                    renderQueue();
                    showStatus(added == 0 ? "Payload queue unchanged." : ("Added " + added + " payload" + (added == 1 ? "" : "s") + " to queue."), false);
                })
                .show();
    }

    private ArrayList<PayloadChoice> loadPayloadChoices(String pkg) {
        ArrayList<PayloadChoice> out = new ArrayList<>();
        try {
            ArrayList<File> files = MemoryHexPayloadStore.listPayloadFiles(context.getApplicationContext(), pkg);
            for (File file : files) {
                try {
                    MemoryHexPayloadStore.Payload payload = MemoryHexPayloadStore.loadPayload(context.getApplicationContext(), file);
                    if (!MemoryHexPayloadStore.packageNameMatches(payload.packageName, pkg)) continue;
                    if (!payload.enabled) continue;
                    String fileName = file == null || TextUtils.isEmpty(file.getName()) ? payload.fileName : file.getName();
                    if (TextUtils.isEmpty(fileName)) continue;
                    String label = payload.name + " (" + fileName + ")";
                    out.add(new PayloadChoice(fileName, label));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void removeSelectedPayload() {
        refreshForCurrentPackage();
        if (selectedIndex < 0 || selectedIndex >= queuedPayloadFiles.size()) {
            showStatus("Tap a queued payload row before removing.", true);
            return;
        }
        String removed = queuedPayloadFiles.remove(selectedIndex);
        selectedIndex = -1;
        saveQueue();
        renderQueue();
        showStatus("Removed " + removed + " from queue.", false);
    }

    private void removePayloadAt(int index) {
        refreshForCurrentPackage();
        if (index < 0 || index >= queuedPayloadFiles.size()) return;
        String removed = queuedPayloadFiles.remove(index);
        selectedIndex = -1;
        saveQueue();
        renderQueue();
        showStatus("Removed " + removed + " from queue.", false);
    }

    private void startQueuedPayloads() {
        final String pkg = getTargetPackage();
        if (TextUtils.isEmpty(pkg)) {
            showStatus("Select a target package before running payloads.", true);
            return;
        }
        refreshForCurrentPackage();
        if (queuedPayloadFiles.isEmpty()) {
            showStatus("Add at least one payload to the queue first.", true);
            return;
        }
        try {
            Intent intent = new Intent(context, MemoryOverlayService.class);
            intent.setAction(MemoryOverlayService.ACTION_LAUNCH_AND_APPLY_PAYLOADS);
            intent.putExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE, pkg);
            intent.putExtra(MemoryOverlayService.EXTRA_TARGET_LABEL, pkg);
            String pid = getTargetPid();
            if (!TextUtils.isEmpty(pid)) intent.putExtra(MemoryOverlayService.EXTRA_TARGET_PID, pid);
            intent.putExtra(MemoryOverlayService.EXTRA_PAYLOAD_FILE_NAMES, queuedPayloadFiles.toArray(new String[0]));
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            showStatus("Run Payloads requested for " + queuedPayloadFiles.size() + " queued payload" + (queuedPayloadFiles.size() == 1 ? "" : "s") + ".", false);
            Toast.makeText(context, "Run Payloads requested", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            showStatus("Payload run failed: " + safeMessage(t), true);
        }
    }

    private void renderQueue() {
        if (queueList == null) return;
        queueList.removeAllViews();
        if (TextUtils.isEmpty(loadedPackage)) {
            addMessageRow("Select a target package before queueing payloads.");
            setStatusText("Queue payloads for the selected target package. Tap a queued row to select it; long-press to remove.");
            return;
        }
        if (queuedPayloadFiles.isEmpty()) {
            addMessageRow("No payloads queued for " + loadedPackage + ".");
            setStatusText("Add payloads to queue, then press Run Payloads.");
            return;
        }
        for (int i = 0; i < queuedPayloadFiles.size(); i++) {
            final int index = i;
            TextView row = new TextView(context);
            row.setText((i + 1) + ". " + queuedPayloadFiles.get(i));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(false);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackgroundResource(index == selectedIndex ? android.R.color.darker_gray : android.R.drawable.list_selector_background);
            row.setOnClickListener(v -> {
                selectedIndex = index;
                renderQueue();
            });
            row.setOnLongClickListener(v -> {
                showRowMenu(v, index);
                return true;
            });
            queueList.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        setStatusText(queuedPayloadFiles.size() + " queued payload" + (queuedPayloadFiles.size() == 1 ? "" : "s") + " for " + loadedPackage + ".");
    }

    private void addMessageRow(String message) {
        TextView row = new TextView(context);
        row.setText(message);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.setAlpha(0.72f);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        queueList.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showRowMenu(View anchor, int index) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add("Remove Payload");
        menu.setOnMenuItemClickListener(item -> {
            removePayloadAt(index);
            return true;
        });
        menu.show();
    }

    private ArrayList<String> loadQueue(String pkg) {
        ArrayList<String> out = new ArrayList<>();
        if (prefs == null || TextUtils.isEmpty(pkg)) return out;
        String raw = prefs.getString(queueKey(pkg), "");
        if (TextUtils.isEmpty(raw)) return out;
        String[] parts = raw.split("\\n");
        for (String part : parts) {
            String clean = part == null ? "" : part.trim();
            if (!TextUtils.isEmpty(clean) && !out.contains(clean)) out.add(clean);
        }
        return out;
    }

    private void saveQueue() {
        if (prefs == null || TextUtils.isEmpty(loadedPackage)) return;
        StringBuilder sb = new StringBuilder();
        for (String fileName : queuedPayloadFiles) {
            String clean = fileName == null ? "" : fileName.trim();
            if (TextUtils.isEmpty(clean)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(clean);
        }
        prefs.edit().putString(queueKey(loadedPackage), sb.toString()).apply();
    }

    private String queueKey(String pkg) {
        return KEY_QUEUE_PREFIX + Integer.toHexString((pkg == null ? "" : pkg.trim()).hashCode());
    }

    private String getTargetPackage() {
        try {
            return targetPackageProvider == null ? "" : safeTrim(targetPackageProvider.getTargetPackage());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getTargetPid() {
        try {
            return targetPidProvider == null ? "" : safeTrim(targetPidProvider.getTargetPid());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void showStatus(String message, boolean toast) {
        setStatusText(message);
        if (toast) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private void setStatusText(String message) {
        if (status != null) status.setText(TextUtils.isEmpty(message) ? "" : message);
    }

    private void runOnUi(Runnable task) {
        if (root != null) root.post(task);
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.getClass().getSimpleName() : msg;
    }

    private static final class PayloadChoice {
        final String fileName;
        final String label;

        PayloadChoice(String fileName, String label) {
            this.fileName = fileName;
            this.label = label;
        }
    }
}
