package dev.perms.test.packages;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;

import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;

/** Owns the Packages tab package-info panel refresh and debounce behavior. */
public final class PackageInfoPanelController {
    public interface Host {
        void rememberPackageToolsTargetPackage(String packageName);
        void setPackagesSpinnerVisible(boolean visible);
        void preservePackagesScrollPosition(Runnable update);
        void runOnUiThread(Runnable action);
        boolean usesAppPermissions();
        void refreshPermissionsForPackage(String packageName);
    }

    private final Context context;
    private final ActivityMainBinding binding;
    private final Handler mainHandler;
    private final ExecutorService io;
    private final Host host;
    private final int colorDangerous;
    private final int colorSignature;
    private final int colorGranted;
    private final int colorRevoked;
    private final int colorMuted;

    private Runnable pendingRefresh;

    public PackageInfoPanelController(Context context,
                                      ActivityMainBinding binding,
                                      Handler mainHandler,
                                      ExecutorService io,
                                      Host host,
                                      int colorDangerous,
                                      int colorSignature,
                                      int colorGranted,
                                      int colorRevoked,
                                      int colorMuted) {
        this.context = context;
        this.binding = binding;
        this.mainHandler = mainHandler;
        this.io = io;
        this.host = host;
        this.colorDangerous = colorDangerous;
        this.colorSignature = colorSignature;
        this.colorGranted = colorGranted;
        this.colorRevoked = colorRevoked;
        this.colorMuted = colorMuted;
    }

    public void setup() {
        if (binding == null || binding.tabPackages == null || binding.tabPackages.edtTargetPkg == null) return;
        binding.tabPackages.edtTargetPkg.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (host != null) {
                    host.rememberPackageToolsTargetPackage(s == null ? "" : s.toString());
                }
                updateSoon();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        updateSoon();
    }

    public void updateSoon() {
        if (mainHandler == null) return;
        if (pendingRefresh != null) mainHandler.removeCallbacks(pendingRefresh);
        pendingRefresh = this::updateNow;
        mainHandler.postDelayed(pendingRefresh, 250L);
    }

    public void updateNow() {
        if (binding == null || binding.tabPackages == null || binding.tabPackages.edtTargetPkg == null) return;
        final String pkg = binding.tabPackages.edtTargetPkg.getText() == null
                ? "" : binding.tabPackages.edtTargetPkg.getText().toString().trim();
        if (TextUtils.isEmpty(pkg)) {
            if (binding.tabPackages.txtPkgInfo != null) binding.tabPackages.txtPkgInfo.setText("");
            return;
        }

        if (host != null) host.setPackagesSpinnerVisible(true);

        try {
            io.execute(() -> {
                CharSequence info = PackageInfoFormatter.build(
                        context,
                        pkg,
                        colorDangerous,
                        colorSignature,
                        colorGranted,
                        colorRevoked,
                        colorMuted);
                if (host != null) {
                    host.runOnUiThread(() -> host.preservePackagesScrollPosition(() -> {
                        if (binding.tabPackages.txtPkgInfo != null) binding.tabPackages.txtPkgInfo.setText(info);
                        boolean appPermsChecked = binding.tabPackages.chkUseAppPerms != null
                                && binding.tabPackages.chkUseAppPerms.isChecked();
                        if (!appPermsChecked) host.setPackagesSpinnerVisible(false);
                    }));
                    if (host.usesAppPermissions()) {
                        host.refreshPermissionsForPackage(pkg);
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Activity is being torn down (rotation); ignore.
        }
    }
}
