package dev.perms.test.home;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.perms.test.R;
import dev.perms.test.ui.PermsTestUiCompat;

/** Activity-hosted popout panel for the Home tab App Tray. */
public class HomeAppTrayPanelActivity extends Activity {
    private HomeAppTrayAdapter adapter;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_app_tray_panel);
        findViewById(R.id.btnHomeAppTrayPanelClose).setOnClickListener(v -> finish());
        status = findViewById(R.id.txtHomeAppTrayPanelStatus);
        RecyclerView rv = findViewById(R.id.rvHomeAppTrayPanel);
        int span = Math.max(3, getResources().getConfiguration().screenWidthDp / 120);
        rv.setLayoutManager(new GridLayoutManager(this, span));
        adapter = new HomeAppTrayAdapter(new HomeAppTrayAdapter.Host() {
            @Override
            public void launchApp(String packageName) {
                launchPackage(packageName);
            }

            @Override
            public void runShell(String cmd, String labelForLog) {
                runPanelShell(cmd, labelForLog);
            }

            @Override
            public void makeDebugPackage(String packageName, String label) {
                showMainAppRequired("Make Debug Package");
            }

            @Override
            public void extractPackage(String packageName, String label) {
                showMainAppRequired("Extract Package");
            }

            @Override
            public void launchWithPayloads(String packageName, String label) {
                showMainAppRequired("Launch With Payloads");
            }

            @Override
            public void createPayloadShortcut(String packageName, String label) {
                showMainAppRequired("Create Payload Shortcut");
            }

            @Override
            public android.content.Context getContext() {
                return HomeAppTrayPanelActivity.this;
            }
        });
        rv.setAdapter(adapter);
        PermsTestUiCompat.applyActivityUiProfile(this, getWindow().getDecorView());
        try { setTitle("App Tray Panel"); } catch (Throwable ignored) {}
        try { setTaskDescription(new ActivityManager.TaskDescription("App Tray")); } catch (Throwable ignored) {}
        refreshAsync();
    }

    private void refreshAsync() {
        new Thread(() -> {
            final ArrayList<HomeAppTrayEntry> out = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                if (apps != null) {
                    for (ApplicationInfo ai : apps) {
                        if (ai == null || ai.packageName == null) continue;
                        Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                        if (launch == null) continue;
                        boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                                && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                        CharSequence label;
                        try { label = pm.getApplicationLabel(ai); } catch (Throwable t) { label = ai.packageName; }
                        out.add(new HomeAppTrayEntry(ai.packageName, label, pm.getApplicationIcon(ai), isSystem));
                    }
                    Collections.sort(out, (a, b) -> String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label)));
                }
            } catch (Throwable ignored) {
            }
            runOnUiThread(() -> {
                if (adapter != null) adapter.setItems(out);
                if (status != null) status.setText("Launchable apps: " + out.size() + ". Some package tools still run from the main Home tab.");
            });
        }, "PermsTestAppTrayPanel").start();
    }

    private void launchPackage(String packageName) {
        try {
            if (TextUtils.isEmpty(packageName)) return;
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) {
                Toast.makeText(this, "No launch intent", Toast.LENGTH_SHORT).show();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(this, "Launch failed: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private void runPanelShell(String cmd, String labelForLog) {
        if (TextUtils.isEmpty(cmd)) return;
        new Thread(() -> {
            int code = -1;
            StringBuilder out = new StringBuilder();
            try {
                Process p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null && out.length() < 8192) out.append(line).append('\n');
                }
                code = p.waitFor();
            } catch (Throwable t) {
                out.append(t);
            }
            final int result = code;
            final String msg = out.toString();
            runOnUiThread(() -> {
                if (status != null) status.setText((TextUtils.isEmpty(labelForLog) ? "shell" : labelForLog) + " exit=" + result + (TextUtils.isEmpty(msg) ? "" : "\n" + msg));
            });
        }, "PermsTestAppTrayPanelShell").start();
    }

    private void showMainAppRequired(String feature) {
        Toast.makeText(this, feature + " is available from the main Home tab", Toast.LENGTH_LONG).show();
    }
}
