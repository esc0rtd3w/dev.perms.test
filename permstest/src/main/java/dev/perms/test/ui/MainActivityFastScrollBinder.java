package dev.perms.test.ui;

import dev.perms.test.databinding.ActivityMainBinding;

/** Binds fast-scroll overlays owned by the main activity layout. */
public final class MainActivityFastScrollBinder {
    private MainActivityFastScrollBinder() {
    }

    public static void attach(ActivityMainBinding binding) {
        try {
            if (binding == null) return;
            FastScrollOverlay.attach(binding.scrollOutput, binding.fastScrollTouch, binding.fastScrollThumb);
            FastScrollOverlay.attach(binding.tabScripts.edtScriptBody,
                    binding.tabScripts.fastScrollTouchScript,
                    binding.tabScripts.fastScrollThumbScript);
            FastScrollOverlay.attach(binding.tabDebugging.edtSmaliEditorBody,
                    binding.tabDebugging.fastScrollTouchSmaliEditor,
                    binding.tabDebugging.fastScrollThumbSmaliEditor);
            FastScrollOverlay.attach(binding.tabNetwork.edtHttpIndexEditor,
                    binding.tabNetwork.fastScrollTouchHttpIndexEditor,
                    binding.tabNetwork.fastScrollThumbHttpIndexEditor);
        } catch (Throwable ignored) {
        }
    }
}
