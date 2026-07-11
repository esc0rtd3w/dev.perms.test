package dev.perms.test.tools.alarms;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dev.perms.test.MainActivity;
import dev.perms.test.R;

/** Foreground service for active Tools-tab alarms and timers. */
public final class PermsTestAlarmTimerService extends Service {
    public static final String ACTION_START_ALARM = "dev.perms.test.action.START_TOOL_ALARM";
    public static final String ACTION_STOP_ALARM = "dev.perms.test.action.STOP_TOOL_ALARM";
    public static final String ACTION_START_TIMER = "dev.perms.test.action.START_TOOL_TIMER";
    public static final String ACTION_STOP_TIMER = "dev.perms.test.action.STOP_TOOL_TIMER";
    public static final String ACTION_SILENCE = "dev.perms.test.action.SILENCE_TOOL_ALARM_TIMER";
    public static final String ACTION_STOP_ALL = "dev.perms.test.action.STOP_TOOL_ALARM_TIMER_ALL";

    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_ALARM_HOUR = "alarmHour";
    public static final String EXTRA_ALARM_MINUTE = "alarmMinute";
    public static final String EXTRA_REPEAT_DAILY = "repeatDaily";
    public static final String EXTRA_TIMER_DURATION_MS = "timerDurationMs";
    public static final String EXTRA_TIMER_TAP_NOTIFICATION_TO_STOP = "timerTapNotificationToStop";
    public static final String EXTRA_SOUND_MODE = "soundMode";
    public static final String EXTRA_SOUND_URI = "soundUri";

    public static final String PREFS = "perms_test";
    public static final String PREF_ALARM_ACTIVE = "tools_alarm_active";
    public static final String PREF_ALARM_RINGING = "tools_alarm_ringing";
    public static final String PREF_ALARM_STATUS = "tools_alarm_status";
    public static final String PREF_ALARM_TARGET_MS = "tools_alarm_target_ms";
    public static final String PREF_ALARM_REPEAT_DAILY = "tools_alarm_repeat_daily";
    public static final String PREF_TIMER_ACTIVE = "tools_timer_active";
    public static final String PREF_TIMER_RINGING = "tools_timer_ringing";
    public static final String PREF_TIMER_STATUS = "tools_timer_status";
    public static final String PREF_TIMER_END_ELAPSED_MS = "tools_timer_end_elapsed_ms";
    public static final String PREF_TIMER_REMAINING_MS = "tools_timer_remaining_ms";
    public static final String PREF_TIMER_TAP_NOTIFICATION_TO_STOP = "tools_timer_tap_notification_to_stop";
    public static final String PREF_UPDATED_AT_MS = "tools_alarm_timer_updated_at_ms";

    public static final String SOUND_DEFAULT_ALARM = "Default alarm";
    public static final String SOUND_DEFAULT_NOTIFICATION = "Default notification";
    public static final String SOUND_DEFAULT_RINGTONE = "Default ringtone";
    public static final String SOUND_SILENT = "Silent";
    public static final String SOUND_CUSTOM_URI = "Custom URI";

    private static final String CHANNEL_ID = "tools_alarm_timer_status_v1";
    private static final int NOTIFICATION_ID = 70544;
    private static final long TICK_MS = 1000L;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            tick();
        }
    };

    private boolean alarmActive;
    private boolean alarmRinging;
    private long alarmTargetMs;
    private boolean alarmRepeatDaily;
    private String alarmLabel = "Alarm";
    private String alarmSoundMode = SOUND_DEFAULT_ALARM;
    private String alarmSoundUri = "";

    private boolean timerActive;
    private boolean timerRinging;
    private long timerEndElapsedMs;
    private String timerLabel = "Timer";
    private String timerSoundMode = SOUND_DEFAULT_ALARM;
    private String timerSoundUri = "";
    private boolean timerTapNotificationToStop;

    private Ringtone ringtone;
    private String ringingSource = "";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        startForegroundCompat(buildNotification("Starting alarms and timers..."));
        if (ACTION_STOP_ALL.equals(action)) {
            stopAlarmInternal("Alarm stopped.");
            stopTimerInternal("Timer stopped.");
            stopRingtone();
            maybeStopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_ALARM.equals(action)) {
            stopAlarmInternal("Alarm stopped.");
            stopRingtoneIfIdle();
            maybeStopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_TIMER.equals(action)) {
            stopTimerInternal("Timer stopped.");
            stopRingtoneIfIdle();
            maybeStopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_SILENCE.equals(action)) {
            boolean wasAlarmRinging = alarmRinging;
            boolean wasTimerRinging = timerRinging;
            alarmRinging = false;
            timerRinging = false;
            stopRingtone();
            if (wasAlarmRinging) setAlarmStatus("Alarm silenced.");
            if (wasTimerRinging) setTimerStatus("Timer silenced.");
            publishStatus();
            updateNotification();
            maybeStopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START_TIMER.equals(action)) {
            startTimer(intent);
            return START_STICKY;
        }
        if (ACTION_START_ALARM.equals(action)) {
            startAlarm(intent);
            return START_STICKY;
        }
        publishStatus();
        updateNotification();
        maybeStopSelf();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(tickRunnable);
        stopRingtone();
        super.onDestroy();
    }

    private void startAlarm(Intent intent) {
        int hour = intent == null ? 7 : intent.getIntExtra(EXTRA_ALARM_HOUR, 7);
        int minute = intent == null ? 0 : intent.getIntExtra(EXTRA_ALARM_MINUTE, 0);
        alarmLabel = safeText(intent == null ? null : intent.getStringExtra(EXTRA_LABEL), "Alarm");
        alarmRepeatDaily = intent != null && intent.getBooleanExtra(EXTRA_REPEAT_DAILY, false);
        alarmSoundMode = safeText(intent == null ? null : intent.getStringExtra(EXTRA_SOUND_MODE), SOUND_DEFAULT_ALARM);
        alarmSoundUri = safeText(intent == null ? null : intent.getStringExtra(EXTRA_SOUND_URI), "");
        alarmTargetMs = nextWallClockTarget(hour, minute);
        alarmActive = true;
        alarmRinging = false;
        publishStatus();
        updateNotification();
        scheduleTick();
    }

    private void startTimer(Intent intent) {
        long durationMs = intent == null ? 5L * 60L * 1000L : intent.getLongExtra(EXTRA_TIMER_DURATION_MS, 5L * 60L * 1000L);
        if (durationMs < 1000L) durationMs = 1000L;
        timerLabel = safeText(intent == null ? null : intent.getStringExtra(EXTRA_LABEL), "Timer");
        timerSoundMode = safeText(intent == null ? null : intent.getStringExtra(EXTRA_SOUND_MODE), SOUND_DEFAULT_ALARM);
        timerSoundUri = safeText(intent == null ? null : intent.getStringExtra(EXTRA_SOUND_URI), "");
        timerTapNotificationToStop = intent != null && intent.getBooleanExtra(EXTRA_TIMER_TAP_NOTIFICATION_TO_STOP, false);
        timerEndElapsedMs = SystemClock.elapsedRealtime() + durationMs;
        timerActive = true;
        timerRinging = false;
        publishStatus();
        updateNotification();
        scheduleTick();
    }

    private void tick() {
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        boolean changed = false;

        if (alarmActive && nowWall >= alarmTargetMs) {
            alarmRinging = true;
            changed = true;
            playSound(alarmSoundMode, alarmSoundUri, alarmLabel);
            if (alarmRepeatDaily) {
                do {
                    alarmTargetMs += DAY_MS;
                } while (alarmTargetMs <= nowWall);
            } else {
                alarmActive = false;
            }
        }

        if (timerActive && nowElapsed >= timerEndElapsedMs) {
            timerActive = false;
            timerRinging = true;
            changed = true;
            playSound(timerSoundMode, timerSoundUri, timerLabel);
        }

        publishStatus();
        updateNotification();
        if (alarmActive || timerActive) {
            mainHandler.postDelayed(tickRunnable, TICK_MS);
        } else if (!hasActiveWork()) {
            maybeStopSelf();
        }
    }

    private void scheduleTick() {
        mainHandler.removeCallbacks(tickRunnable);
        mainHandler.postDelayed(tickRunnable, TICK_MS);
    }

    private boolean hasActiveWork() {
        return alarmActive || timerActive || alarmRinging || timerRinging || isRingtonePlaying();
    }

    private void maybeStopSelf() {
        publishStatus();
        updateNotification();
        if (hasActiveWork()) {
            scheduleTick();
            return;
        }
        stopForegroundCompat();
        stopSelf();
    }

    private void stopAlarmInternal(String status) {
        alarmActive = false;
        alarmRinging = false;
        setAlarmStatus(status);
        publishStatus();
        updateNotification();
    }

    private void stopTimerInternal(String status) {
        timerActive = false;
        timerRinging = false;
        timerTapNotificationToStop = false;
        setTimerStatus(status);
        publishStatus();
        updateNotification();
    }

    private void stopRingtoneIfIdle() {
        if (!alarmRinging && !timerRinging) stopRingtone();
    }

    private void publishStatus() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(PREF_ALARM_ACTIVE, alarmActive)
                .putBoolean(PREF_ALARM_RINGING, alarmRinging)
                .putLong(PREF_ALARM_TARGET_MS, alarmTargetMs)
                .putBoolean(PREF_ALARM_REPEAT_DAILY, alarmRepeatDaily)
                .putString(PREF_ALARM_STATUS, buildAlarmStatus())
                .putBoolean(PREF_TIMER_ACTIVE, timerActive)
                .putBoolean(PREF_TIMER_RINGING, timerRinging)
                .putLong(PREF_TIMER_END_ELAPSED_MS, timerEndElapsedMs)
                .putLong(PREF_TIMER_REMAINING_MS, timerActive ? Math.max(0L, timerEndElapsedMs - SystemClock.elapsedRealtime()) : 0L)
                .putBoolean(PREF_TIMER_TAP_NOTIFICATION_TO_STOP, timerTapNotificationToStop)
                .putString(PREF_TIMER_STATUS, buildTimerStatus())
                .putLong(PREF_UPDATED_AT_MS, System.currentTimeMillis());
        editor.apply();
    }

    private String buildAlarmStatus() {
        if (alarmRinging) return safeText(alarmLabel, "Alarm") + " ringing. Tap Stop/Silence to stop the sound.";
        if (alarmActive) {
            String when = new SimpleDateFormat("EEE HH:mm", Locale.US).format(new Date(alarmTargetMs));
            return safeText(alarmLabel, "Alarm") + " active for " + when + (alarmRepeatDaily ? " (daily)." : ".");
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String stored = sp.getString(PREF_ALARM_STATUS, "");
        return TextUtils.isEmpty(stored) ? "No alarm active." : stored;
    }

    private String buildTimerStatus() {
        if (timerRinging) return safeText(timerLabel, "Timer") + " finished. Use Stop Timer to stop the sound.";
        if (timerActive) return safeText(timerLabel, "Timer") + " remaining: " + formatDuration(timerEndElapsedMs - SystemClock.elapsedRealtime()) + ".";
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String stored = sp.getString(PREF_TIMER_STATUS, "");
        return TextUtils.isEmpty(stored) ? "No timer active." : stored;
    }

    private void setAlarmStatus(String status) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_ALARM_STATUS, safeText(status, "No alarm active."))
                .apply();
    }

    private void setTimerStatus(String status) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_TIMER_STATUS, safeText(status, "No timer active."))
                .apply();
    }

    private void playSound(String soundMode, String customUri, String source) {
        if (SOUND_SILENT.equals(soundMode)) {
            ringingSource = safeText(source, "Alarm/timer");
            return;
        }
        try {
            Uri uri = resolveSoundUri(soundMode, customUri);
            if (uri == null) return;
            stopRingtone();
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone == null) return;
            if (Build.VERSION.SDK_INT >= 21) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            ringingSource = safeText(source, "Alarm/timer");
            ringtone.play();
        } catch (Throwable ignored) {
        }
    }

    private Uri resolveSoundUri(String soundMode, String customUri) {
        if (SOUND_CUSTOM_URI.equals(soundMode)) {
            return TextUtils.isEmpty(customUri) ? null : Uri.parse(customUri);
        }
        if (SOUND_DEFAULT_NOTIFICATION.equals(soundMode)) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if (SOUND_DEFAULT_RINGTONE.equals(soundMode)) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return alarm != null ? alarm : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    private boolean isRingtonePlaying() {
        try {
            return ringtone != null && ringtone.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void stopRingtone() {
        try {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        } catch (Throwable ignored) {
        }
        ringtone = null;
        ringingSource = "";
    }

    private Notification buildNotification(String fallbackStatus) {
        ensureChannel();
        String status = buildNotificationStatus(fallbackStatus);
        boolean hasAlarm = alarmActive || alarmRinging;
        boolean hasTimer = timerActive || timerRinging;

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());
        Intent silenceIntent = new Intent(this, PermsTestAlarmTimerService.class).setAction(ACTION_SILENCE);
        PendingIntent silencePending = PendingIntent.getService(this, 1, silenceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());
        Intent stopAllIntent = new Intent(this, PermsTestAlarmTimerService.class).setAction(ACTION_STOP_ALL);
        PendingIntent stopAllPending = PendingIntent.getService(this, 2, stopAllIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());
        Intent stopTimerIntent = new Intent(this, PermsTestAlarmTimerService.class).setAction(ACTION_STOP_TIMER);
        PendingIntent stopTimerPending = PendingIntent.getService(this, 3, stopTimerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());
        Intent stopAlarmIntent = new Intent(this, PermsTestAlarmTimerService.class).setAction(ACTION_STOP_ALARM);
        PendingIntent stopAlarmPending = PendingIntent.getService(this, 4, stopAlarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("PermsTest Alarms and Timers")
                .setContentText(status)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(status))
                .setOngoing(hasActiveWork())
                .setOnlyAlertOnce(true)
                .setAutoCancel(false);

        if (hasTimer) {
            if (timerTapNotificationToStop) builder.setContentIntent(stopTimerPending);
        } else if (hasAlarm) {
            builder.setContentIntent(openPending);
        }

        if (hasTimer) {
            builder.addAction(R.mipmap.ic_launcher, "Stop Timer", stopTimerPending);
        }
        if (hasAlarm) {
            builder.addAction(R.mipmap.ic_launcher, "Stop Alarm", stopAlarmPending);
        }
        if (timerRinging || alarmRinging || isRingtonePlaying()) {
            builder.addAction(R.mipmap.ic_launcher, "Silence", silencePending);
        }
        if (hasTimer && hasAlarm) {
            builder.addAction(R.mipmap.ic_launcher, "Stop All", stopAllPending);
        }

        return builder.build();
    }

    private String buildNotificationStatus(String fallbackStatus) {
        if (alarmRinging || timerRinging || isRingtonePlaying()) {
            return TextUtils.isEmpty(ringingSource) ? "Alarm/timer ringing." : ringingSource + " ringing.";
        }
        if (timerActive && alarmActive) {
            return buildAlarmStatus() + " " + buildTimerStatus();
        }
        if (alarmActive) return buildAlarmStatus();
        if (timerActive) return buildTimerStatus();
        return TextUtils.isEmpty(fallbackStatus) ? "No alarm or timer active." : fallbackStatus;
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(null));
        } catch (Throwable ignored) {
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "PermsTest alarms and timers",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Active Tools tab alarm and timer status.");
        nm.createNotificationChannel(channel);
    }

    @SuppressLint("InlinedApi")
    private void startForegroundCompat(Notification notification) {
        if (notification == null) return;
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
    }

    private static int pendingIntentImmutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private long nextWallClockTarget(int hour, int minute) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, clamp(hour, 0, 23));
        cal.set(java.util.Calendar.MINUTE, clamp(minute, 0, 59));
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long target = cal.getTimeInMillis();
        if (target <= System.currentTimeMillis()) target += DAY_MS;
        return target;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safeText(String text, String fallback) {
        return TextUtils.isEmpty(text) ? fallback : text;
    }

    public static String formatDuration(long millis) {
        long total = Math.max(0L, millis) / 1000L;
        long h = total / 3600L;
        long m = (total / 60L) % 60L;
        long s = total % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }
}
