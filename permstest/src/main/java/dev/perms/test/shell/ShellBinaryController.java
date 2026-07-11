package dev.perms.test.shell;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.perms.test.databinding.ActivityMainBinding;

/**
 * Coordinates Shell quick-command binary availability scans and UI updates.
 *
 * Filesystem probing is intentionally off the UI thread. The Shell tab has many
 * binary buttons, and checking every possible system/bundled path during startup
 * or tab rebinds can otherwise add avoidable input latency on slower storage.
 */
public final class ShellBinaryController {
    private final ShellBinaryAssets assets;
    private final ShellBinaryAvailability availability = new ShellBinaryAvailability();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PermsTestShellBinaryScan");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger scanGeneration = new AtomicInteger();

    public ShellBinaryController(ShellBinaryAssets assets) {
        this.assets = assets;
    }

    public void refresh(ActivityMainBinding binding) {
        final int generation = scanGeneration.incrementAndGet();
        worker.execute(() -> {
            scan();
            mainHandler.post(() -> {
                if (generation != scanGeneration.get()) return;
                applyAlpha(binding);
            });
        });
    }

    public void scan() {
        availability.scan(new ShellBinaryAvailability.Checker() {
            @Override
            public boolean isAvailable(String name) {
                return ShellBinaryController.this.isAvailable(name);
            }
        });
    }

    public void applyAlpha(ActivityMainBinding binding) {
        availability.applyAlpha(binding);
    }

    public void applyEnabled(ActivityMainBinding binding, boolean shellReady) {
        availability.applyEnabled(binding, shellReady);
    }

    private boolean isAvailable(String name) {
        try {
            if (TextUtils.isEmpty(name) || assets == null) return false;
            if (assets.isBinaryAvailableSystemOnly(name)) return true;
            // Bundled assets alone should not enable quick buttons; the binary must be installed/staged.
            return assets.isBinaryInstalledPublic(name);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
