package dev.perms.test.tools.alarms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.NumberPicker;

import androidx.core.content.ContextCompat;

import java.util.Locale;

import dev.perms.test.R;
import dev.perms.test.ui.DropdownUi;
import dev.perms.test.ui.NoFilterArrayAdapter;

/** Reusable alarm clock, countdown timer, and stopwatch controller. */
public final class ToolsAlarmsTimersController {
    public interface Host {
        Activity getActivity();
        View getRootView();
        void appendOutput(String message);
    }

    private static final String[] SOUND_OPTIONS = new String[] {
            PermsTestAlarmTimerService.SOUND_DEFAULT_ALARM,
            PermsTestAlarmTimerService.SOUND_DEFAULT_NOTIFICATION,
            PermsTestAlarmTimerService.SOUND_DEFAULT_RINGTONE,
            PermsTestAlarmTimerService.SOUND_SILENT,
            PermsTestAlarmTimerService.SOUND_CUSTOM_URI
    };
    private static final NumberPicker.Formatter TWO_DIGIT_FORMATTER = new NumberPicker.Formatter() {
        @Override
        public String format(int value) {
            return String.format(Locale.US, "%02d", value);
        }
    };

    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStatuses();
            if (bound) mainHandler.postDelayed(this, 1000L);
        }
    };
    private final Runnable stopwatchRunnable = new Runnable() {
        @Override
        public void run() {
            updateStopwatch();
            if (stopwatchRunning) mainHandler.postDelayed(this, 33L);
        }
    };

    private boolean bound;
    private boolean stopwatchRunning;
    private long stopwatchBaseElapsedMs;
    private long stopwatchAccumulatedMs;

    public ToolsAlarmsTimersController(Host host) {
        this.host = host;
    }

    public void bind() {
        Activity activity = activity();
        Views b = views();
        if (activity == null || b == null) return;
        bound = true;

        bindSoundDropdown(activity, b.tilToolsAlarmSound, b.ddToolsAlarmSound, b.tilToolsAlarmCustomSound);
        bindSoundDropdown(activity, b.tilToolsTimerSound, b.ddToolsTimerSound, b.tilToolsTimerCustomSound);

        b.btnToolsAlarmStart.setOnClickListener(v -> startAlarm());
        b.btnToolsAlarmStop.setOnClickListener(v -> stopServiceAction(PermsTestAlarmTimerService.ACTION_STOP_ALARM, "Alarm stop requested."));
        bindTimerWheel(b.npToolsTimerHours, 0, 99, 0);
        bindTimerWheel(b.npToolsTimerMinutes, 0, 59, 5);
        bindTimerWheel(b.npToolsTimerSeconds, 0, 59, 0);

        b.btnToolsTimerStart.setOnClickListener(v -> startTimer());
        b.btnToolsTimerStop.setOnClickListener(v -> stopServiceAction(PermsTestAlarmTimerService.ACTION_STOP_TIMER, "Timer stop requested."));
        b.btnToolsStopwatchStart.setOnClickListener(v -> toggleStopwatchRunPause());
        b.btnToolsStopwatchPause.setText("Stop");
        b.btnToolsStopwatchPause.setOnClickListener(v -> stopStopwatch());
        b.btnToolsStopwatchReset.setOnClickListener(v -> resetStopwatch());

        refreshStatuses();
        updateTimerPreview();
        updateStopwatch();
        updateStopwatchButtons();
        mainHandler.removeCallbacks(refreshRunnable);
        mainHandler.postDelayed(refreshRunnable, 1000L);
    }

    public void stop() {
        bound = false;
        mainHandler.removeCallbacks(refreshRunnable);
        mainHandler.removeCallbacks(stopwatchRunnable);
    }

    private void bindSoundDropdown(Activity activity,
                                   com.google.android.material.textfield.TextInputLayout til,
                                   com.google.android.material.textfield.MaterialAutoCompleteTextView dd,
                                   View customUriLayout) {
        if (activity == null || dd == null) return;
        dd.setAdapter(new NoFilterArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, SOUND_OPTIONS));
        if (TextUtils.isEmpty(dd.getText())) dd.setText(PermsTestAlarmTimerService.SOUND_DEFAULT_ALARM, false);
        DropdownUi.bindExposedDropdown(activity, til, dd, () -> DropdownUi.showDropdown(dd));
        dd.setOnItemClickListener((parent, view, position, id) -> updateCustomSoundVisibility(dd, customUriLayout));
        updateCustomSoundVisibility(dd, customUriLayout);
    }

    private void updateCustomSoundVisibility(com.google.android.material.textfield.MaterialAutoCompleteTextView dd, View customUriLayout) {
        if (customUriLayout == null || dd == null) return;
        String mode = dd.getText() == null ? "" : dd.getText().toString();
        customUriLayout.setVisibility(PermsTestAlarmTimerService.SOUND_CUSTOM_URI.equals(mode) ? View.VISIBLE : View.GONE);
    }

    private void bindTimerWheel(NumberPicker picker, int min, int max, int value) {
        if (picker == null) return;
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(TWO_DIGIT_FORMATTER);
        picker.setValue(Math.max(min, Math.min(max, value)));
        picker.setOnValueChangedListener((view, oldValue, newValue) -> updateTimerPreview());
    }

    private void startAlarm() {
        Activity activity = activity();
        Views b = views();
        if (activity == null || b == null) return;
        int[] hm = parseAlarmTime(textOf(b.edtToolsAlarmTime));
        if (hm == null) {
            b.txtToolsAlarmStatus.setText("Use 24-hour HH:mm time, for example 07:30 or 18:45.");
            return;
        }
        Intent intent = new Intent(activity, PermsTestAlarmTimerService.class)
                .setAction(PermsTestAlarmTimerService.ACTION_START_ALARM)
                .putExtra(PermsTestAlarmTimerService.EXTRA_LABEL, safeText(textOf(b.edtToolsAlarmName), "Alarm"))
                .putExtra(PermsTestAlarmTimerService.EXTRA_ALARM_HOUR, hm[0])
                .putExtra(PermsTestAlarmTimerService.EXTRA_ALARM_MINUTE, hm[1])
                .putExtra(PermsTestAlarmTimerService.EXTRA_REPEAT_DAILY, b.chkToolsAlarmRepeatDaily.isChecked())
                .putExtra(PermsTestAlarmTimerService.EXTRA_SOUND_MODE, selectedAlarmSound())
                .putExtra(PermsTestAlarmTimerService.EXTRA_SOUND_URI, textOf(b.edtToolsAlarmCustomSound));
        ContextCompat.startForegroundService(activity, intent);
        setAlarmStatus("Starting alarm service...");
        appendOutput("[Tools] Alarm service start requested.\n");
        refreshSoon();
    }

    private void startTimer() {
        Activity activity = activity();
        Views b = views();
        if (activity == null || b == null) return;
        long durationMs = readTimerDurationMs();
        if (durationMs <= 0L) {
            b.txtToolsTimerStatus.setText("Enter a timer duration greater than zero.");
            return;
        }
        Intent intent = new Intent(activity, PermsTestAlarmTimerService.class)
                .setAction(PermsTestAlarmTimerService.ACTION_START_TIMER)
                .putExtra(PermsTestAlarmTimerService.EXTRA_LABEL, "Timer")
                .putExtra(PermsTestAlarmTimerService.EXTRA_TIMER_DURATION_MS, durationMs)
                .putExtra(PermsTestAlarmTimerService.EXTRA_TIMER_TAP_NOTIFICATION_TO_STOP, b.chkToolsTimerTapToStop.isChecked())
                .putExtra(PermsTestAlarmTimerService.EXTRA_SOUND_MODE, selectedTimerSound())
                .putExtra(PermsTestAlarmTimerService.EXTRA_SOUND_URI, textOf(b.edtToolsTimerCustomSound));
        ContextCompat.startForegroundService(activity, intent);
        setTimerStatus("Starting timer service...");
        appendOutput("[Tools] Timer service start requested.\n");
        refreshSoon();
    }

    private void stopServiceAction(String action, String output) {
        Activity activity = activity();
        if (activity == null) return;
        Intent intent = new Intent(activity, PermsTestAlarmTimerService.class).setAction(action);
        ContextCompat.startForegroundService(activity, intent);
        appendOutput("[Tools] " + output + "\n");
        refreshSoon();
    }

    private void refreshSoon() {
        mainHandler.removeCallbacks(refreshRunnable);
        mainHandler.postDelayed(refreshRunnable, 350L);
    }

    private void refreshStatuses() {
        Activity activity = activity();
        Views b = views();
        if (activity == null || b == null) return;
        SharedPreferences sp = activity.getSharedPreferences(PermsTestAlarmTimerService.PREFS, Context.MODE_PRIVATE);
        boolean alarmActive = sp.getBoolean(PermsTestAlarmTimerService.PREF_ALARM_ACTIVE, false);
        boolean alarmRinging = sp.getBoolean(PermsTestAlarmTimerService.PREF_ALARM_RINGING, false);
        String alarmStatus = sp.getString(PermsTestAlarmTimerService.PREF_ALARM_STATUS, "No alarm active.");
        b.txtToolsAlarmStatus.setText(TextUtils.isEmpty(alarmStatus) ? "No alarm active." : alarmStatus);
        b.btnToolsAlarmStop.setText("Stop");
        b.btnToolsAlarmStop.setEnabled(alarmActive || alarmRinging);

        boolean timerActive = sp.getBoolean(PermsTestAlarmTimerService.PREF_TIMER_ACTIVE, false);
        boolean timerRinging = sp.getBoolean(PermsTestAlarmTimerService.PREF_TIMER_RINGING, false);
        long remaining = sp.getLong(PermsTestAlarmTimerService.PREF_TIMER_REMAINING_MS, 0L);
        String timerStatus = sp.getString(PermsTestAlarmTimerService.PREF_TIMER_STATUS, "No timer active.");
        if (timerActive) timerStatus = "Timer remaining: " + PermsTestAlarmTimerService.formatDuration(remaining) + ".";
        b.txtToolsTimerStatus.setText(TextUtils.isEmpty(timerStatus) ? "No timer active." : timerStatus);
        b.btnToolsTimerStop.setText("Stop");
        b.btnToolsTimerStop.setEnabled(timerActive || timerRinging);
    }

    private void updateTimerPreview() {
        Activity activity = activity();
        Views b = views();
        if (activity == null || b == null) return;
        SharedPreferences sp = activity.getSharedPreferences(PermsTestAlarmTimerService.PREFS, Context.MODE_PRIVATE);
        if (sp.getBoolean(PermsTestAlarmTimerService.PREF_TIMER_ACTIVE, false)
                || sp.getBoolean(PermsTestAlarmTimerService.PREF_TIMER_RINGING, false)) {
            return;
        }
        long durationMs = readTimerDurationMs();
        if (durationMs > 0L) {
            b.txtToolsTimerStatus.setText("Ready timer duration: " + PermsTestAlarmTimerService.formatDuration(durationMs) + ".");
        }
    }

    private long readTimerDurationMs() {
        Views b = views();
        if (b == null) return 0L;
        long h = b.npToolsTimerHours == null ? 0L : b.npToolsTimerHours.getValue();
        long m = b.npToolsTimerMinutes == null ? 0L : b.npToolsTimerMinutes.getValue();
        long s = b.npToolsTimerSeconds == null ? 0L : b.npToolsTimerSeconds.getValue();
        long totalSeconds = h * 3600L + m * 60L + s;
        if (totalSeconds <= 0L) return 0L;
        return totalSeconds * 1000L;
    }

    private String selectedAlarmSound() {
        Views b = views();
        return b == null ? PermsTestAlarmTimerService.SOUND_DEFAULT_ALARM : safeText(textOf(b.ddToolsAlarmSound), PermsTestAlarmTimerService.SOUND_DEFAULT_ALARM);
    }

    private String selectedTimerSound() {
        Views b = views();
        return b == null ? PermsTestAlarmTimerService.SOUND_DEFAULT_ALARM : safeText(textOf(b.ddToolsTimerSound), PermsTestAlarmTimerService.SOUND_DEFAULT_ALARM);
    }

    private void toggleStopwatchRunPause() {
        if (stopwatchRunning) {
            pauseStopwatch();
        } else {
            resumeStopwatch();
        }
    }

    private void resumeStopwatch() {
        if (stopwatchRunning) return;
        stopwatchRunning = true;
        stopwatchBaseElapsedMs = SystemClock.elapsedRealtime();
        setStopwatchStatus(stopwatchAccumulatedMs > 0L ? "Stopwatch resumed." : "Stopwatch running.");
        updateStopwatchButtons();
        mainHandler.removeCallbacks(stopwatchRunnable);
        mainHandler.post(stopwatchRunnable);
    }

    private void pauseStopwatch() {
        if (!stopwatchRunning) return;
        stopwatchAccumulatedMs += SystemClock.elapsedRealtime() - stopwatchBaseElapsedMs;
        stopwatchRunning = false;
        mainHandler.removeCallbacks(stopwatchRunnable);
        updateStopwatch();
        setStopwatchStatus("Stopwatch paused.");
        updateStopwatchButtons();
    }

    private void stopStopwatch() {
        stopwatchRunning = false;
        stopwatchAccumulatedMs = 0L;
        stopwatchBaseElapsedMs = SystemClock.elapsedRealtime();
        mainHandler.removeCallbacks(stopwatchRunnable);
        updateStopwatch();
        setStopwatchStatus("Stopwatch stopped.");
        updateStopwatchButtons();
    }

    private void resetStopwatch() {
        boolean wasRunning = stopwatchRunning;
        stopwatchAccumulatedMs = 0L;
        stopwatchBaseElapsedMs = SystemClock.elapsedRealtime();
        updateStopwatch();
        setStopwatchStatus(wasRunning ? "Stopwatch reset and still running." : "Stopwatch reset.");
        updateStopwatchButtons();
        if (wasRunning) {
            mainHandler.removeCallbacks(stopwatchRunnable);
            mainHandler.post(stopwatchRunnable);
        }
    }

    private void updateStopwatch() {
        Views b = views();
        if (b == null) return;
        long elapsed = stopwatchAccumulatedMs;
        if (stopwatchRunning) elapsed += SystemClock.elapsedRealtime() - stopwatchBaseElapsedMs;
        b.txtToolsStopwatchTime.setText(formatStopwatch(elapsed));
    }

    private void updateStopwatchButtons() {
        Views b = views();
        if (b == null) return;
        boolean hasElapsed = stopwatchRunning || stopwatchAccumulatedMs > 0L;
        b.btnToolsStopwatchStart.setText(stopwatchRunning ? "Pause" : (stopwatchAccumulatedMs > 0L ? "Resume" : "Start"));
        b.btnToolsStopwatchPause.setText("Stop");
        b.btnToolsStopwatchPause.setEnabled(hasElapsed);
        b.btnToolsStopwatchReset.setEnabled(hasElapsed);
    }

    private void setStopwatchStatus(String text) {
        Views b = views();
        if (b != null && b.txtToolsStopwatchStatus != null) b.txtToolsStopwatchStatus.setText(safeText(text, ""));
    }

    private void setAlarmStatus(String text) {
        Views b = views();
        if (b != null && b.txtToolsAlarmStatus != null) b.txtToolsAlarmStatus.setText(safeText(text, ""));
    }

    private void setTimerStatus(String text) {
        Views b = views();
        if (b != null && b.txtToolsTimerStatus != null) b.txtToolsTimerStatus.setText(safeText(text, ""));
    }

    private Activity activity() {
        return host == null ? null : host.getActivity();
    }

    private Views views() {
        View root = host == null ? null : host.getRootView();
        return root == null ? null : new Views(root);
    }

    private static final class Views {
        final com.google.android.material.textfield.TextInputEditText edtToolsAlarmName;
        final com.google.android.material.textfield.TextInputEditText edtToolsAlarmTime;
        final android.widget.CheckBox chkToolsAlarmRepeatDaily;
        final com.google.android.material.textfield.TextInputLayout tilToolsAlarmSound;
        final com.google.android.material.textfield.MaterialAutoCompleteTextView ddToolsAlarmSound;
        final com.google.android.material.textfield.TextInputLayout tilToolsAlarmCustomSound;
        final com.google.android.material.textfield.TextInputEditText edtToolsAlarmCustomSound;
        final android.widget.TextView txtToolsAlarmStatus;
        final com.google.android.material.button.MaterialButton btnToolsAlarmStart;
        final com.google.android.material.button.MaterialButton btnToolsAlarmStop;
        final NumberPicker npToolsTimerHours;
        final NumberPicker npToolsTimerMinutes;
        final NumberPicker npToolsTimerSeconds;
        final android.widget.CheckBox chkToolsTimerTapToStop;
        final com.google.android.material.textfield.TextInputLayout tilToolsTimerSound;
        final com.google.android.material.textfield.MaterialAutoCompleteTextView ddToolsTimerSound;
        final com.google.android.material.textfield.TextInputLayout tilToolsTimerCustomSound;
        final com.google.android.material.textfield.TextInputEditText edtToolsTimerCustomSound;
        final android.widget.TextView txtToolsTimerStatus;
        final com.google.android.material.button.MaterialButton btnToolsTimerStart;
        final com.google.android.material.button.MaterialButton btnToolsTimerStop;
        final android.widget.TextView txtToolsStopwatchTime;
        final android.widget.TextView txtToolsStopwatchStatus;
        final com.google.android.material.button.MaterialButton btnToolsStopwatchStart;
        final com.google.android.material.button.MaterialButton btnToolsStopwatchPause;
        final com.google.android.material.button.MaterialButton btnToolsStopwatchReset;

        Views(View root) {
            edtToolsAlarmName = root.findViewById(R.id.edtToolsAlarmName);
            edtToolsAlarmTime = root.findViewById(R.id.edtToolsAlarmTime);
            chkToolsAlarmRepeatDaily = root.findViewById(R.id.chkToolsAlarmRepeatDaily);
            tilToolsAlarmSound = root.findViewById(R.id.tilToolsAlarmSound);
            ddToolsAlarmSound = root.findViewById(R.id.ddToolsAlarmSound);
            tilToolsAlarmCustomSound = root.findViewById(R.id.tilToolsAlarmCustomSound);
            edtToolsAlarmCustomSound = root.findViewById(R.id.edtToolsAlarmCustomSound);
            txtToolsAlarmStatus = root.findViewById(R.id.txtToolsAlarmStatus);
            btnToolsAlarmStart = root.findViewById(R.id.btnToolsAlarmStart);
            btnToolsAlarmStop = root.findViewById(R.id.btnToolsAlarmStop);
            npToolsTimerHours = root.findViewById(R.id.npToolsTimerHours);
            npToolsTimerMinutes = root.findViewById(R.id.npToolsTimerMinutes);
            npToolsTimerSeconds = root.findViewById(R.id.npToolsTimerSeconds);
            chkToolsTimerTapToStop = root.findViewById(R.id.chkToolsTimerTapToStop);
            tilToolsTimerSound = root.findViewById(R.id.tilToolsTimerSound);
            ddToolsTimerSound = root.findViewById(R.id.ddToolsTimerSound);
            tilToolsTimerCustomSound = root.findViewById(R.id.tilToolsTimerCustomSound);
            edtToolsTimerCustomSound = root.findViewById(R.id.edtToolsTimerCustomSound);
            txtToolsTimerStatus = root.findViewById(R.id.txtToolsTimerStatus);
            btnToolsTimerStart = root.findViewById(R.id.btnToolsTimerStart);
            btnToolsTimerStop = root.findViewById(R.id.btnToolsTimerStop);
            txtToolsStopwatchTime = root.findViewById(R.id.txtToolsStopwatchTime);
            txtToolsStopwatchStatus = root.findViewById(R.id.txtToolsStopwatchStatus);
            btnToolsStopwatchStart = root.findViewById(R.id.btnToolsStopwatchStart);
            btnToolsStopwatchPause = root.findViewById(R.id.btnToolsStopwatchPause);
            btnToolsStopwatchReset = root.findViewById(R.id.btnToolsStopwatchReset);
        }
    }

    private void appendOutput(String message) {
        if (host != null) host.appendOutput(message);
    }

    private static String textOf(android.widget.TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static String safeText(String text, String fallback) {
        return TextUtils.isEmpty(text) ? fallback : text;
    }

    private static int[] parseAlarmTime(String text) {
        if (TextUtils.isEmpty(text)) return null;
        String[] parts = text.trim().split(":");
        if (parts.length != 2) return null;
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return new int[] { h, m };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String formatStopwatch(long millis) {
        long totalMs = Math.max(0L, millis);
        long h = totalMs / 3600000L;
        long m = (totalMs / 60000L) % 60L;
        long s = (totalMs / 1000L) % 60L;
        long ms = totalMs % 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, ms);
    }

}
