package dev.perms.test.shell;

import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import dev.perms.test.ShizukuCompat;

/**
 * Owns the Shell tab dialog for installing/removing bundled shell binaries.
 */
public final class ShellBundledBinsDialog {
    public interface Host {
        void appendOutput(String text);
        void refreshAvailability();
    }

    private final AppCompatActivity activity;
    private final ShellBinaryAssets assets;
    private final ExecutorService io;
    private final Host host;
    private AlertDialog dialog;

    public ShellBundledBinsDialog(AppCompatActivity activity,
                                  ShellBinaryAssets assets,
                                  ExecutorService io,
                                  Host host) {
        this.activity = activity;
        this.assets = assets;
        this.io = io;
        this.host = host;
    }

    public void show() {
        try {
            final List<String> elfAssets = listBundledElfAssets();
            final Map<String, File> installed = new HashMap<>();
            final Map<String, String> installedAbi = new HashMap<>();
            listInstalledBins(installed, installedAbi);

            final int pad = dp(14);
            final int padSmall = dp(8);

            ScrollView scrollView = new ScrollView(activity);
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, pad, pad, pad);
            scrollView.addView(root);

            TextView abiView = new TextView(activity);
            abiView.setText(buildDeviceAbiLine());
            root.addView(abiView);

            TextView assetsHeader = new TextView(activity);
            assetsHeader.setPadding(0, padSmall, 0, padSmall);
            assetsHeader.setText("Bundled assets (assets/bin):");
            root.addView(assetsHeader);

            if (elfAssets.isEmpty()) {
                TextView none = new TextView(activity);
                none.setText("  (none)");
                root.addView(none);
            } else {
                for (String name : elfAssets) {
                    root.addView(createBundledAssetRow(name, installed, installedAbi, padSmall));
                }
            }

            List<String> installedOnly = new ArrayList<>();
            for (String name : installed.keySet()) {
                if (!elfAssets.contains(name)) installedOnly.add(name);
            }
            Collections.sort(installedOnly);

            if (!installedOnly.isEmpty()) {
                TextView installedHeader = new TextView(activity);
                installedHeader.setPadding(0, padSmall, 0, padSmall);
                installedHeader.setText("Installed only (" + ShellBinaryAssets.PUBLIC_BIN_DIR + "):");
                root.addView(installedHeader);

                for (String name : installedOnly) {
                    root.addView(createInstalledOnlyRow(name, installed.get(name), installedAbi.get(name), padSmall));
                }
            }

            root.addView(createActionRow(elfAssets, padSmall));

            AlertDialog dlg = new AlertDialog.Builder(activity)
                    .setTitle("Manage Bundled Bins")
                    .setView(scrollView)
                    .setNegativeButton("Close", (d, w) -> { })
                    .create();
            dialog = dlg;
            dlg.show();
        } catch (Throwable ignored) {
        }
    }

    private List<String> listBundledElfAssets() {
        List<String> assetNames = new ArrayList<>();
        try {
            String abiDir = assets.resolveBundledAbiDir();
            String[] names = null;
            if (!TextUtils.isEmpty(abiDir)) {
                names = activity.getAssets().list(ShellBinaryAssets.BUNDLED_ASSET_DIR + "/" + abiDir);
            }
            if (names == null || names.length == 0) {
                names = activity.getAssets().list(ShellBinaryAssets.BUNDLED_ASSET_DIR);
            }
            if (names != null) {
                Collections.addAll(assetNames, names);
                Collections.sort(assetNames);
            }
        } catch (Throwable ignored) {
        }

        List<String> elfAssets = new ArrayList<>();
        for (String name : assetNames) {
            if (ShellElfInfo.isElfAsset(assets, name)) elfAssets.add(name);
        }
        return elfAssets;
    }

    private void listInstalledBins(Map<String, File> installed, Map<String, String> installedAbi) {
        try {
            File dir = new File(ShellBinaryAssets.PUBLIC_BIN_DIR);
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (file == null || !file.isFile()) continue;
                if (!ShellElfInfo.isElfFile(file)) continue;
                installed.put(file.getName(), file);
                installedAbi.put(file.getName(), ShellElfInfo.describeFileAbi(file));
            }
        } catch (Throwable ignored) {
        }
    }

    private String buildDeviceAbiLine() {
        StringBuilder sb = new StringBuilder("Device ABIs: ");
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis != null) {
                for (int i = 0; i < abis.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(abis[i]);
                }
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private View createBundledAssetRow(String name,
                                       Map<String, File> installed,
                                       Map<String, String> installedAbi,
                                       int padSmall) {
        final File inst = installed.get(name);
        final boolean isInstalled = inst != null && inst.exists();
        final String assetAbi = ShellElfInfo.describeAssetAbi(assets, name);
        final String instAbi = installedAbi.get(name);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, padSmall);

        TextView text = new TextView(activity);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(textParams);

        StringBuilder line = new StringBuilder();
        line.append(name);
        if (!TextUtils.isEmpty(assetAbi)) line.append(" ").append(assetAbi);
        if (isInstalled) {
            line.append("\n  installed: ").append(inst.length()).append(" bytes");
            if (!TextUtils.isEmpty(instAbi)) line.append(" ").append(instAbi);
        }
        text.setText(line.toString());
        row.addView(text);

        AppCompatImageButton installButton = new AppCompatImageButton(activity);
        installButton.setImageResource(android.R.drawable.ic_menu_save);
        installButton.setContentDescription("Install");
        setBorderlessBackground(installButton);
        installButton.setOnClickListener(v -> runIo(() -> {
            appendOutput("[*] Installing " + name + " ...\n");
            assets.ensureBundledBinaryPublic(name);
            appendOutput("[+] Done.\n");
            refreshAndReopen();
        }));
        row.addView(installButton);

        AppCompatImageButton removeButton = new AppCompatImageButton(activity);
        removeButton.setImageResource(android.R.drawable.ic_menu_delete);
        removeButton.setContentDescription("Remove");
        setBorderlessBackground(removeButton);
        removeButton.setEnabled(isInstalled);
        removeButton.setAlpha(isInstalled ? 1f : 0.35f);
        removeButton.setOnClickListener(v -> runIo(() -> {
            appendOutput("[*] Removing " + name + " ...\n");
            removePublicBin(name);
            appendOutput("[+] Done.\n");
            refreshAndReopen();
        }));
        row.addView(removeButton);

        return row;
    }

    private View createInstalledOnlyRow(String name, File file, String abi, int padSmall) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, padSmall);

        TextView text = new TextView(activity);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(textParams);
        text.setText(name + " (" + (file != null ? file.length() : 0) + " bytes)" + (TextUtils.isEmpty(abi) ? "" : " " + abi));
        row.addView(text);

        AppCompatImageButton removeButton = new AppCompatImageButton(activity);
        removeButton.setImageResource(android.R.drawable.ic_menu_delete);
        removeButton.setContentDescription("Remove");
        setBorderlessBackground(removeButton);
        removeButton.setOnClickListener(v -> runIo(() -> {
            appendOutput("[*] Removing " + name + " ...\n");
            removePublicBin(name);
            appendOutput("[+] Done.\n");
            refreshAndReopen();
        }));
        row.addView(removeButton);

        return row;
    }

    private View createActionRow(List<String> elfAssets, int padSmall) {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, padSmall, 0, 0);

        Button refreshButton = new Button(activity);
        refreshButton.setText("Refresh");
        refreshButton.setOnClickListener(v -> refreshAndReopen());
        actions.addView(refreshButton);

        Button clearButton = new Button(activity);
        clearButton.setText("Clear All");
        clearButton.setOnClickListener(v -> runIo(() -> {
            appendOutput("[*] Clearing " + ShellBinaryAssets.PUBLIC_BIN_DIR + " ...\n");
            runShellSimple("rm -rf " + shSingleQuote(ShellBinaryAssets.PUBLIC_BIN_DIR));
            appendOutput("[+] Done.\n");
            refreshAndReopen();
        }));
        actions.addView(clearButton);

        Button installAllButton = new Button(activity);
        installAllButton.setText("Install All");
        installAllButton.setOnClickListener(v -> runIo(() -> {
            appendOutput("[*] Installing bundled bins ...\n");
            for (String name : elfAssets) assets.ensureBundledBinaryPublic(name);
            appendOutput("[+] Done.\n");
            refreshAndReopen();
        }));
        actions.addView(installAllButton);

        return actions;
    }

    private void refreshAndReopen() {
        activity.runOnUiThread(() -> {
            try { if (dialog != null) dialog.dismiss(); } catch (Throwable ignored) { }
            try { if (host != null) host.refreshAvailability(); } catch (Throwable ignored) { }
            show();
        });
    }

    private void runIo(Runnable runnable) {
        try {
            if (io != null) {
                io.execute(runnable);
            } else {
                new Thread(runnable, "PermsTestShellBundledBins").start();
            }
        } catch (Throwable ignored) {
        }
    }

    private void appendOutput(String text) {
        try {
            activity.runOnUiThread(() -> {
                try { if (host != null) host.appendOutput(text); } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) {
        }
    }

    private void setBorderlessBackground(View view) {
        try {
            TypedValue out = new TypedValue();
            if (activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)) {
                view.setBackgroundResource(out.resourceId);
            }
        } catch (Throwable ignored) {
        }
    }

    private int dp(int value) {
        try {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value,
                    activity.getResources().getDisplayMetrics());
        } catch (Throwable ignored) {
            return value;
        }
    }

    private void removePublicBin(String name) {
        try {
            if (TextUtils.isEmpty(name)) return;
            runShellSimple("rm -f " + shSingleQuote(ShellBinaryAssets.PUBLIC_BIN_DIR + "/" + name));
        } catch (Throwable ignored) {
        }
    }

    private void runShellSimple(String command) {
        try {
            Process process = ShizukuCompat.newProcess(new String[]{"sh", "-c", command}, null, null);
            try { process.getInputStream().close(); } catch (Throwable ignored) { }
            try { process.getErrorStream().close(); } catch (Throwable ignored) { }
            try { process.waitFor(); } catch (Throwable ignored) { }
        } catch (Throwable ignored) {
        }
    }

    private static String shSingleQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
