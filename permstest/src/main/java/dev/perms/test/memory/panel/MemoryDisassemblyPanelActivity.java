package dev.perms.test.memory.panel;

import dev.perms.test.memory.overlay.MemoryOverlayService;

/** VR-compatible Activity panel for the Memory Disassembly viewer. */
public final class MemoryDisassemblyPanelActivity extends MemoryOverlayPanelActivity {
    @Override
    protected String defaultAction() {
        return MemoryOverlayService.ACTION_SHOW_DISASSEMBLY_OVERLAY;
    }
}
