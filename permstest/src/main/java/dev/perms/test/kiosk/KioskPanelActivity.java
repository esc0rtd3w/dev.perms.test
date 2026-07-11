package dev.perms.test.kiosk;

/** VR/panel-themed Kiosk Mode launcher surface. */
public final class KioskPanelActivity extends KioskLauncherActivity {
    @Override
    protected boolean isVrPanelActivity() {
        return true;
    }
}
