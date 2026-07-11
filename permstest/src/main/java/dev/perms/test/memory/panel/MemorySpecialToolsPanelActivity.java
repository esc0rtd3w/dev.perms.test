package dev.perms.test.memory.panel;

import dev.perms.test.memory.overlay.MemoryOverlayService;

/** VR-compatible Activity panel for the Memory Special Tools. */
public final class MemorySpecialToolsPanelActivity extends MemoryOverlayPanelActivity {
    @Override
    protected String defaultAction() {
        return MemoryOverlayService.ACTION_SHOW_SPECIAL_TOOLS_OVERLAY;
    }
}
