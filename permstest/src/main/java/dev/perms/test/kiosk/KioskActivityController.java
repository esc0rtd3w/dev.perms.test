package dev.perms.test.kiosk;

import android.os.Handler;
import android.view.KeyEvent;

import androidx.appcompat.app.AppCompatActivity;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.settings.LaunchAndFileOpenSettingsController;

/**
 * Main-activity-facing kiosk and launcher wiring.
 *
 * Behavior belongs here so MainActivity can stay a thin lifecycle/event host while
 * kiosk-specific state remains in the kiosk package.
 */
public final class KioskActivityController {
    public interface Host {
        ActivityMainBinding binding();
        void appendOutput(String text);
        boolean isDebugOutputEnabled();
        void debugOutput(String channel, String area, String message);
    }

    private final AppCompatActivity activity;
    private final Handler mainHandler;
    private final Class<?> relaunchActivityClass;
    private final Host host;
    private final KioskSettingsStore kioskStore;

    private LaunchAndFileOpenSettingsController launchAndFileOpenSettingsController;
    private KioskHardwareButtonBypassController hardwareButtonBypassController;
    private boolean kioskSettingsBound;

    public KioskActivityController(AppCompatActivity activity,
                                   Handler mainHandler,
                                   Class<?> relaunchActivityClass,
                                   Host host) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.relaunchActivityClass = relaunchActivityClass;
        this.host = host;
        this.kioskStore = new KioskSettingsStore(activity);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            return getHardwareButtonBypassController().dispatchKeyEvent(event);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void setupLauncherModeToggle() {
        getLaunchAndFileOpenSettingsController().setup();
    }

    public void maybeOpenHomeSettingsAfterModeSwitch() {
        getLaunchAndFileOpenSettingsController().maybeOpenHomeSettingsAfterModeSwitch();
    }

    public void syncLauncherAndKioskCheckboxesFromPrefs() {
        try {
            getLaunchAndFileOpenSettingsController().syncLauncherAndKioskCheckboxesFromPrefs();
        } catch (Throwable ignored) {
        }
    }

    public void setupKioskSettings() {
        if (kioskSettingsBound) return;
        ActivityMainBinding binding = requireBinding("kiosk settings controller");
        new KioskSettingsController(activity, binding, new KioskSettingsController.Host() {
            @Override
            public void appendOutput(String text) {
                host.appendOutput(text);
            }

            @Override
            public boolean isDebugOutputEnabled() {
                return host.isDebugOutputEnabled();
            }

            @Override
            public void debugOutput(String area, String message) {
                host.debugOutput("kiosk", area, message);
            }
        }).setup();
        kioskSettingsBound = true;
    }

    public void release() {
        launchAndFileOpenSettingsController = null;
        hardwareButtonBypassController = null;
        kioskSettingsBound = false;
    }

    private KioskHardwareButtonBypassController getHardwareButtonBypassController() {
        if (hardwareButtonBypassController == null) {
            hardwareButtonBypassController = new KioskHardwareButtonBypassController(
                    activity,
                    kioskStore,
                    "MainActivity",
                    new KioskHardwareButtonBypassController.Host() {
                        @Override
                        public void onKioskHardwareBypassTriggered() {
                            if (host.binding() == null) return;
                            syncLauncherAndKioskCheckboxesFromPrefs();
                        }

                        @Override
                        public void debugOutput(String area, String message) {
                            host.debugOutput("kiosk", area, message);
                        }
                    });
        }
        return hardwareButtonBypassController;
    }

    private LaunchAndFileOpenSettingsController getLaunchAndFileOpenSettingsController() {
        if (launchAndFileOpenSettingsController == null) {
            launchAndFileOpenSettingsController = new LaunchAndFileOpenSettingsController(
                    activity,
                    requireBinding("launcher settings controller"),
                    mainHandler,
                    relaunchActivityClass,
                    new LaunchAndFileOpenSettingsController.Host() {
                        @Override
                        public void appendOutput(String text) {
                            host.appendOutput(text);
                        }

                        @Override
                        public boolean isDebugOutputEnabled() {
                            return host.isDebugOutputEnabled();
                        }

                        @Override
                        public void debugOutput(String area, String message) {
                            host.debugOutput("launcher", area, message);
                        }
                    });
        }
        return launchAndFileOpenSettingsController;
    }

    private ActivityMainBinding requireBinding(String owner) {
        ActivityMainBinding binding = host.binding();
        if (binding == null) {
            throw new IllegalStateException("Main binding is not ready for " + owner);
        }
        return binding;
    }
}
