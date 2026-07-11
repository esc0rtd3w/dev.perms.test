package dev.perms.test.memory.panel;

import dev.perms.test.memory.overlay.MemoryOverlayService;

/** VR-compatible Activity panel for the Memory Hex Editor. */
public final class MemoryHexPanelActivity extends MemoryOverlayPanelActivity {
    @Override
    protected String defaultAction() {
        return MemoryOverlayService.ACTION_SHOW_HEX_OVERLAY;
    }
}
