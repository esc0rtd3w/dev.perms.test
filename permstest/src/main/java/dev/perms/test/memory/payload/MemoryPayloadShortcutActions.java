package dev.perms.test.memory.payload;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;

import dev.perms.test.R;
import dev.perms.test.memory.overlay.MemoryOverlayService;

public final class MemoryPayloadShortcutActions {
    public interface OutputSink {
        void append(String text);
    }

    private final Context context;
    private final OutputSink outputSink;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MemoryPayloadShortcutActions(Context context, OutputSink outputSink) {
        this.context = context;
        this.outputSink = outputSink;
    }

    public void launchPackageWithPayloads(String packageName, String label) {
        try {
            final String pkg = packageName == null ? "" : packageName.trim();
            if (TextUtils.isEmpty(pkg)) {
                Toast.makeText(context, "Select a package first.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                Toast.makeText(context, "No launchable activity for target package.", Toast.LENGTH_SHORT).show();
                appendOutput("[Memory] No launchable activity for: " + pkg + "\n");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(launch);

            Intent i = new Intent(context, MemoryOverlayService.class);
            i.setAction(MemoryOverlayService.ACTION_APPLY_PAYLOADS_ONLY);
            i.putExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE, pkg);
            i.putExtra(MemoryOverlayService.EXTRA_TARGET_LABEL, TextUtils.isEmpty(label) ? pkg : label.trim());
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
            appendOutput("[Memory] Launch With Payloads requested for " + pkg + "\n");
            Toast.makeText(context, "Launching with payloads", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            appendOutput("[Memory] Launch With Payloads failed: " + t + "\n");
            Toast.makeText(context, "Launch With Payloads failed", Toast.LENGTH_SHORT).show();
        }
    }

    public void showPayloadShortcutDialog(String packageName, String label) {
        try {
            final String initialPkg = packageName == null ? "" : packageName.trim();
            if (TextUtils.isEmpty(initialPkg)) {
                Toast.makeText(context, "Select a package first.", Toast.LENGTH_SHORT).show();
                return;
            }
            final String cleanLabel = TextUtils.isEmpty(label) ? initialPkg : label.trim();
            final android.widget.LinearLayout box = new android.widget.LinearLayout(context);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = dp(16);
            box.setPadding(pad, dp(8), pad, 0);

            final EditText nameField = new EditText(context);
            nameField.setSingleLine(true);
            nameField.setHint("Shortcut name");
            nameField.setText(cleanLabel + " Payloads");
            box.addView(nameField, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final EditText pkgField = new EditText(context);
            pkgField.setSingleLine(true);
            pkgField.setHint("Package name");
            pkgField.setText(initialPkg);
            box.addView(pkgField, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final MaterialCheckBox appIcon = new MaterialCheckBox(context);
            appIcon.setText("Use app icon when available");
            appIcon.setChecked(true);
            box.addView(appIcon, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final android.widget.LinearLayout delayRow = new android.widget.LinearLayout(context);
            delayRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            delayRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            delayRow.setPadding(0, dp(4), 0, dp(4));
            final TextView delayLabel = new TextView(context);
            delayLabel.setText("Payload delay after launch (seconds)");
            delayLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            delayLabel.setAlpha(0.82f);
            delayRow.addView(delayLabel, new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final android.widget.NumberPicker delayPicker = new android.widget.NumberPicker(context);
            delayPicker.setMinValue(0);
            delayPicker.setMaxValue(30);
            delayPicker.setValue(1);
            delayPicker.setWrapSelectorWheel(false);
            delayRow.addView(delayPicker, new android.widget.LinearLayout.LayoutParams(
                    dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(delayRow, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final TextView payloadHeader = new TextView(context);
            payloadHeader.setText("Payloads to run");
            payloadHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            payloadHeader.setTypeface(payloadHeader.getTypeface(), android.graphics.Typeface.BOLD);
            box.addView(payloadHeader, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final android.widget.LinearLayout payloadList = new android.widget.LinearLayout(context);
            payloadList.setOrientation(android.widget.LinearLayout.VERTICAL);
            payloadList.setPadding(0, dp(4), 0, dp(4));
            final ScrollView payloadScroll = new ScrollView(context);
            payloadScroll.addView(payloadList, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(payloadScroll, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));

            final ArrayList<PayloadShortcutChoice> choices = new ArrayList<>();
            populatePayloadShortcutChoices(initialPkg, payloadList, choices);

            final TextView note = new TextView(context);
            note.setText("Shortcut launches the app, waits the selected delay, applies only the checked enabled payloads, then exits.");
            note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            note.setAlpha(0.78f);
            box.addView(note, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle("Payload Shortcut")
                    .setView(box)
                    .setPositiveButton("Create", null)
                    .setNegativeButton("Cancel", null)
                    .create();
            dialog.setOnShowListener(d -> {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String shortcutName = nameField.getText() == null ? "" : nameField.getText().toString().trim();
                    String pkg = pkgField.getText() == null ? "" : pkgField.getText().toString().trim();
                    ArrayList<String> selectedPayloads = selectedPayloadShortcutFiles(choices);
                    if (selectedPayloads.isEmpty()) {
                        Toast.makeText(context, "Select at least one enabled payload.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long payloadDelayMs = Math.max(0L, delayPicker.getValue()) * 1000L;
                    pinPayloadShortcut(pkg, TextUtils.isEmpty(shortcutName) ? (pkg + " Payloads") : shortcutName, appIcon.isChecked(), selectedPayloads, payloadDelayMs);
                    dialog.dismiss();
                });
            });
            dialog.show();
        } catch (Throwable t) {
            appendOutput("[Memory] Payload shortcut dialog failed: " + t + "\n");
        }
    }

    private void populatePayloadShortcutChoices(String packageName, android.widget.LinearLayout payloadList, ArrayList<PayloadShortcutChoice> outChoices) {
        payloadList.removeAllViews();
        outChoices.clear();
        final String pkg = packageName == null ? "" : packageName.trim();
        if (TextUtils.isEmpty(pkg)) {
            addPayloadShortcutMessage(payloadList, "Package name is required before payloads can be listed.");
            return;
        }
        try {
            ArrayList<File> files = MemoryHexPayloadStore.listPayloadFiles(context.getApplicationContext(), pkg);
            for (File file : files) {
                try {
                    MemoryHexPayloadStore.Payload payload = MemoryHexPayloadStore.loadPayload(context.getApplicationContext(), file);
                    if (!MemoryHexPayloadStore.packageNameMatches(payload.packageName, pkg)) continue;
                    final String fileName = file == null ? payload.fileName : file.getName();
                    final MaterialCheckBox cb = new MaterialCheckBox(context);
                    cb.setText(payload.name + " (" + fileName + ")" + (payload.enabled ? "" : " [disabled]"));
                    cb.setSingleLine(false);
                    cb.setChecked(payload.enabled);
                    cb.setEnabled(payload.enabled);
                    cb.setPadding(0, 0, 0, 0);
                    payloadList.addView(cb, new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    outChoices.add(new PayloadShortcutChoice(fileName, cb));
                } catch (Throwable t) {
                    final TextView row = new TextView(context);
                    row.setText((file == null ? "payload" : file.getName()) + " [invalid: " + safePayloadShortcutMessage(t) + "]");
                    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    row.setAlpha(0.65f);
                    payloadList.addView(row, new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            }
            if (outChoices.isEmpty() && payloadList.getChildCount() == 0) {
                addPayloadShortcutMessage(payloadList, "No complete payload JSON files found for this package.");
            } else if (selectedPayloadShortcutFiles(outChoices).isEmpty()) {
                addPayloadShortcutMessage(payloadList, "No enabled payloads are currently selectable.");
            }
        } catch (Throwable t) {
            addPayloadShortcutMessage(payloadList, "Could not list payloads: " + safePayloadShortcutMessage(t));
        }
    }

    private void addPayloadShortcutMessage(android.widget.LinearLayout payloadList, String message) {
        final TextView row = new TextView(context);
        row.setText(TextUtils.isEmpty(message) ? "No payloads available." : message);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.setAlpha(0.72f);
        row.setPadding(0, dp(6), 0, dp(6));
        payloadList.addView(row, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private ArrayList<String> selectedPayloadShortcutFiles(ArrayList<PayloadShortcutChoice> choices) {
        ArrayList<String> selected = new ArrayList<>();
        if (choices == null) return selected;
        for (PayloadShortcutChoice choice : choices) {
            if (choice == null || TextUtils.isEmpty(choice.fileName) || choice.checkBox == null) continue;
            if (choice.checkBox.isEnabled() && choice.checkBox.isChecked()) selected.add(choice.fileName);
        }
        return selected;
    }

    private String safePayloadShortcutMessage(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.getClass().getSimpleName() : msg;
    }

    private static final class PayloadShortcutChoice {
        final String fileName;
        final MaterialCheckBox checkBox;

        PayloadShortcutChoice(String fileName, MaterialCheckBox checkBox) {
            this.fileName = fileName;
            this.checkBox = checkBox;
        }
    }

    private void pinPayloadShortcut(String packageName, String shortcutName, boolean useAppIcon) {
        pinPayloadShortcut(packageName, shortcutName, useAppIcon, null, 0L);
    }

    private void pinPayloadShortcut(String packageName, String shortcutName, boolean useAppIcon, ArrayList<String> selectedPayloadFiles, long payloadDelayMs) {
        final String pkg = packageName == null ? "" : packageName.trim();
        if (TextUtils.isEmpty(pkg)) {
            Toast.makeText(context, "Package name is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String safeShortcutName = TextUtils.isEmpty(shortcutName) ? (pkg + " Payloads") : shortcutName.trim();
        final ArrayList<String> selectedCopy = selectedPayloadFiles == null
                ? null
                : new ArrayList<>(selectedPayloadFiles);
        final long safeDelayMs = Math.max(0L, Math.min(payloadDelayMs, 30000L));

        if (Build.VERSION.SDK_INT >= 26) {
            ShortcutManager sm = context.getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                Toast.makeText(context, "Pinned shortcuts are not supported by this launcher.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(context, "Preparing shortcut", Toast.LENGTH_SHORT).show();

        /*
         * Loading a target app icon can touch the package APK/resource table. Do
         * that off the UI thread so creating a shortcut cannot stall tab input or
         * contribute to launcher/overlay ANR windows on slower storage.
         */
        new Thread(() -> {
            final Bitmap iconBitmap = buildPayloadShortcutBitmap(pkg, useAppIcon);
            mainHandler.post(() -> requestPayloadShortcut(pkg, safeShortcutName, useAppIcon, selectedCopy, safeDelayMs, iconBitmap));
        }, "PermsTestPayloadShortcutIcon").start();
    }

    private void requestPayloadShortcut(String pkg, String shortcutName, boolean useAppIcon,
                                        ArrayList<String> selectedPayloadFiles, long payloadDelayMs,
                                        Bitmap iconBitmap) {
        try {
            Intent launch = new Intent(context, MemoryPayloadLauncherActivity.class);
            launch.setAction(MemoryPayloadLauncherActivity.ACTION_LAUNCH_WITH_PAYLOADS);
            launch.putExtra(MemoryOverlayService.EXTRA_TARGET_PACKAGE, pkg);
            launch.putExtra(MemoryOverlayService.EXTRA_TARGET_LABEL, shortcutName);
            if (selectedPayloadFiles != null && !selectedPayloadFiles.isEmpty()) {
                launch.putExtra(MemoryOverlayService.EXTRA_PAYLOAD_FILE_NAMES, selectedPayloadFiles.toArray(new String[0]));
            }
            if (payloadDelayMs > 0L) {
                launch.putExtra(MemoryOverlayService.EXTRA_PAYLOAD_DELAY_MS, payloadDelayMs);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);

            if (Build.VERSION.SDK_INT >= 26) {
                ShortcutManager sm = context.getSystemService(ShortcutManager.class);
                if (sm == null || !sm.isRequestPinShortcutSupported()) {
                    Toast.makeText(context, "Pinned shortcuts are not supported by this launcher.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ShortcutInfo.Builder b = new ShortcutInfo.Builder(context, buildPayloadShortcutId(pkg, shortcutName, selectedPayloadFiles))
                        .setShortLabel(shortcutName)
                        .setLongLabel(shortcutName)
                        .setIntent(launch);
                Icon icon = buildPayloadShortcutIcon(iconBitmap);
                if (icon != null) b.setIcon(icon);
                sm.requestPinShortcut(b.build(), null);
                appendOutput("[Memory] Requested payload shortcut for " + pkg
                        + " (" + (selectedPayloadFiles == null ? 0 : selectedPayloadFiles.size())
                        + " payloads, delay " + payloadDelayMs + " ms)"
                        + (useAppIcon ? " using target icon" : " using PermsTest icon") + "\n");
                Toast.makeText(context, "Shortcut requested", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent install = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
            install.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
            install.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch);
            if (iconBitmap != null) install.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap);
            context.sendBroadcast(install);
            appendOutput("[Memory] Requested legacy payload shortcut for " + pkg
                    + " (" + (selectedPayloadFiles == null ? 0 : selectedPayloadFiles.size())
                    + " payloads, delay " + payloadDelayMs + " ms)"
                    + (useAppIcon ? " using target icon" : " using PermsTest icon") + "\n");
            Toast.makeText(context, "Shortcut requested", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            appendOutput("[Memory] Payload shortcut failed: " + t + "\n");
            Toast.makeText(context, "Shortcut failed", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildPayloadShortcutId(String packageName, String shortcutName, ArrayList<String> selectedPayloadFiles) {
        StringBuilder seed = new StringBuilder();
        seed.append(packageName == null ? "" : packageName.trim());
        seed.append('|').append(shortcutName == null ? "" : shortcutName.trim());
        if (selectedPayloadFiles != null) {
            for (String fileName : selectedPayloadFiles) {
                if (!TextUtils.isEmpty(fileName)) seed.append('|').append(fileName.trim());
            }
        }
        return "payload-" + Integer.toHexString(seed.toString().hashCode());
    }

    private Icon buildPayloadShortcutIcon(Bitmap bitmap) {
        if (Build.VERSION.SDK_INT < 26) return null;
        return bitmap == null ? Icon.createWithResource(context, R.mipmap.ic_launcher) : Icon.createWithBitmap(bitmap);
    }

    private Bitmap buildPayloadShortcutBitmap(String packageName, boolean useAppIcon) {
        try {
            Drawable d = null;
            if (useAppIcon && !TextUtils.isEmpty(packageName)) {
                try { d = context.getPackageManager().getApplicationIcon(packageName); } catch (Throwable ignored) {}
            }
            if (d == null) d = context.getApplicationInfo().loadIcon(context.getPackageManager());
            int size = Math.max(dp(48), dp(72));
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            d.setBounds(0, 0, size, size);
            d.draw(canvas);
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int dp(int dip) {
        try {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (float) dip,
                    context.getResources().getDisplayMetrics()
            );
        } catch (Throwable t) {
            return dip;
        }
    }

    private void appendOutput(String text) {
        if (outputSink != null) outputSink.append(text);
    }
}
