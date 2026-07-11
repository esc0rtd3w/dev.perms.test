package dev.perms.test.kiosk;

import android.view.KeyEvent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Shared Volume Up + Volume Down escape handling for optional Kiosk Mode. */
public final class KioskHardwareButtonBypassController {
    public interface Host {
        void onKioskHardwareBypassTriggered();
        void debugOutput(String area, String message);
    }

    private final AppCompatActivity activity;
    private final KioskSettingsStore store;
    private final String owner;
    private final Host host;
    private boolean volumeUpHeld;
    private boolean volumeDownHeld;
    private boolean triggered;

    public KioskHardwareButtonBypassController(AppCompatActivity activity,
                                               KioskSettingsStore store,
                                               String owner,
                                               Host host) {
        this.activity = activity;
        this.store = store;
        this.owner = owner == null ? "unknown" : owner;
        this.host = host;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event == null || store == null) return false;
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false;

        try {
            if (!store.hardwareButtonBypassEnabled() || !store.isKioskModeRequested()) return false;
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpHeld = true;
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) volumeDownHeld = true;
                if (!triggered && volumeUpHeld && volumeDownHeld) {
                    triggered = true;
                    triggerBypass();
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volumeUpHeld = false;
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) volumeDownHeld = false;
                if (!volumeUpHeld && !volumeDownHeld) triggered = false;
                return true;
            }
            return true;
        } catch (Throwable t) {
            debug("hardware-bypass", "Volume-button bypass handling failed in " + owner + ": " + shortError(t));
            return false;
        }
    }

    public void reset() {
        volumeUpHeld = false;
        volumeDownHeld = false;
        triggered = false;
    }

    private void triggerBypass() {
        try { store.setKioskEnabled(false); } catch (Throwable ignored) {}
        debug("hardware-bypass", "Volume Up + Volume Down detected in " + owner + "; Kiosk Mode disabled");
        try { Toast.makeText(activity, "Hardware bypass: Kiosk Mode disabled", Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
        try { if (host != null) host.onKioskHardwareBypassTriggered(); } catch (Throwable ignored) {}
    }

    private void debug(String area, String message) {
        try { if (host != null) host.debugOutput(area, message); } catch (Throwable ignored) {}
    }

    private static String shortError(Throwable t) {
        if (t == null) return "";
        String msg = t.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = t.getClass().getSimpleName();
        return "(" + msg + ")";
    }
}
