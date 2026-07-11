package dev.perms.test.vr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Quest/Horizon helper panel for the memory overlay.
 *
 * Raw Android overlay windows are still used for the normal phone/tablet path. This
 * panel is only a VR restore bridge so Quest users can return to the target app and
 * reopen the memory overlay from a native 2D panel without touching the standard path.
 */
public final class MemoryOverlayVrPanelActivity extends Activity {
    private String targetPackage;
    private String targetPid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        targetPackage = PermsTestVrOverlayCompat.hiddenVrTargetPackage(this);
        targetPid = PermsTestVrOverlayCompat.hiddenVrTargetPid(this);
        buildContent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        targetPackage = PermsTestVrOverlayCompat.hiddenVrTargetPackage(this);
        targetPid = PermsTestVrOverlayCompat.hiddenVrTargetPid(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try { setIntent(intent); } catch (Throwable ignored) {}
        targetPackage = PermsTestVrOverlayCompat.hiddenVrTargetPackage(this);
        targetPid = PermsTestVrOverlayCompat.hiddenVrTargetPid(this);
        buildContent();
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("VR Memory Panel");
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView details = new TextView(this);
        details.setText(buildDetailsText());
        details.setTextSize(14f);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailsParams.topMargin = dp(10);
        root.addView(details, detailsParams);

        Button restore = new Button(this);
        restore.setText("Restore overlay over target");
        restore.setOnClickListener(v -> restoreOverlayOverTarget());
        addButton(root, restore);

        Button overlayOnly = new Button(this);
        overlayOnly.setText("Show overlay only");
        overlayOnly.setOnClickListener(v -> showOverlayOnly());
        addButton(root, overlayOnly);

        Button target = new Button(this);
        target.setText("Return to target app");
        target.setOnClickListener(v -> returnToTargetOnly());
        addButton(root, target);

        Button close = new Button(this);
        close.setText("Close panel");
        close.setOnClickListener(v -> finish());
        addButton(root, close);

        setContentView(root);
    }

    private String buildDetailsText() {
        if (TextUtils.isEmpty(targetPackage)) {
            return "No VR memory target is saved yet. Open the Memory overlay once, choose a target, then minimize it from VR mode.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Saved target: ").append(targetPackage);
        if (!TextUtils.isEmpty(targetPid)) sb.append("\nPID hint: ").append(targetPid);
        sb.append("\n\nThis panel keeps the Quest path separate from the normal Android overlay path.");
        return sb.toString();
    }

    private void restoreOverlayOverTarget() {
        MemoryOverlayVrRestoreActions.restoreOverlayOverTarget(this, targetPackage, targetPid);
    }

    private void showOverlayOnly() {
        MemoryOverlayVrRestoreActions.showOverlayOnly(this, targetPackage, targetPid);
    }

    private void returnToTargetOnly() {
        if (!TextUtils.isEmpty(targetPackage)) {
            try {
                Intent target = getPackageManager().getLaunchIntentForPackage(targetPackage);
                if (target != null) {
                    target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    startActivity(target);
                }
            } catch (Throwable ignored) {
            }
        }
        moveTaskToBack(true);
    }

    private void addButton(LinearLayout root, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        root.addView(button, params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
