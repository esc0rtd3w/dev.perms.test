package dev.perms.test.debugging.editor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;


import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;

/** Handles external smali editor open intents without keeping the routing logic in MainActivity. */
public final class SmaliEditorIntentHandler {
    public interface Host {
        String queryDisplayName(Uri uri);
        File copyUriToExternalDir(Uri uri, String subdir, String filename) throws IOException;
        void showDebuggingTab();
        void setDebuggingBusy(boolean busy, String status);
        void finishDebuggingToolError(String label, Throwable t);
        void openSmaliFileInInternalEditor(File file, int lineHint);
        void appendOutput(String text);
    }

    private final Activity activity;
    private final ActivityMainBinding binding;
    private final ExecutorService executor;
    private final String extraOpenSmaliEditorUri;
    private final String extraOpenSmaliEditorLabel;
    private final Host host;

    public SmaliEditorIntentHandler(Activity activity,
                                    ActivityMainBinding binding,
                                    ExecutorService executor,
                                    String extraOpenSmaliEditorUri,
                                    String extraOpenSmaliEditorLabel,
                                    Host host) {
        this.activity = activity;
        this.binding = binding;
        this.executor = executor;
        this.extraOpenSmaliEditorUri = extraOpenSmaliEditorUri;
        this.extraOpenSmaliEditorLabel = extraOpenSmaliEditorLabel;
        this.host = host;
    }

    public boolean handle(Intent intent) {
        try {
            if (activity == null || host == null || intent == null) return false;
            Uri smaliUri = null;
            String label = intent.getStringExtra(extraOpenSmaliEditorLabel);
            try {
                Object extra = intent.getParcelableExtra(extraOpenSmaliEditorUri);
                if (extra instanceof Uri) smaliUri = (Uri) extra;
            } catch (Throwable ignored) {
            }
            if (smaliUri == null && Intent.ACTION_VIEW.equals(intent.getAction())) {
                Uri data = intent.getData();
                String display = host.queryDisplayName(data);
                if (isSmaliOpenCandidate(data, display)) {
                    smaliUri = data;
                    if (TextUtils.isEmpty(label)) label = display;
                }
            }
            if (smaliUri == null) return false;
            if (TextUtils.isEmpty(label)) label = host.queryDisplayName(smaliUri);
            if (TextUtils.isEmpty(label)) label = smaliUri.toString();
            takePersistedReadWriteGrant(intent, smaliUri);
            final Uri uri = smaliUri;
            final String shownLabel = label;
            host.setDebuggingBusy(true, "Opening smali file...");
            executor.execute(() -> {
                try {
                    File file = resolveIncomingSmaliFile(uri, shownLabel);
                    activity.runOnUiThread(() -> {
                        host.showDebuggingTab();
                        enableOpenAnyMode();
                        host.setDebuggingBusy(false, "Smali file ready.");
                        host.openSmaliFileInInternalEditor(file, 1);
                    });
                } catch (Throwable t) {
                    activity.runOnUiThread(() -> host.finishDebuggingToolError("open smali file", t));
                }
            });
            return true;
        } catch (Throwable t) {
            if (host != null) {
                host.appendOutput("[Debugging] Open smali intent failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
            }
            return false;
        }
    }

    private void takePersistedReadWriteGrant(Intent intent, Uri uri) {
        try {
            final int takeFlags = intent.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (takeFlags != 0) activity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Throwable ignored) {
        }
    }

    private void enableOpenAnyMode() {
        try {
            if (binding != null && binding.tabDebugging != null && binding.tabDebugging.chkSmaliEditorOpenAny != null) {
                binding.tabDebugging.chkSmaliEditorOpenAny.setChecked(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isSmaliOpenCandidate(Uri uri, String displayName) {
        try {
            if (isSmaliName(displayName)) return true;
            if (uri == null) return false;
            if (isSmaliName(uri.getLastPathSegment())) return true;
            return "file".equalsIgnoreCase(uri.getScheme()) && isSmaliName(uri.getPath());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSmaliName(String value) {
        if (TextUtils.isEmpty(value)) return false;
        return value.toLowerCase(Locale.US).endsWith(".smali");
    }

    private File resolveIncomingSmaliFile(Uri uri, String label) throws IOException {
        if (uri == null) throw new IOException("Missing smali URI.");
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            File direct = new File(uri.getPath());
            if (direct.isFile() && isSmaliName(direct.getName())) return direct;
        }
        String name = safeDebuggingSmaliFilename(label);
        File staged = host.copyUriToExternalDir(uri, "debugging_smali_inputs", name);
        if (staged == null || !staged.isFile() || staged.length() <= 0) {
            throw new IOException("Unable to stage smali file for editor.");
        }
        return staged;
    }

    private String safeDebuggingSmaliFilename(String label) {
        String name = TextUtils.isEmpty(label) ? "external.smali" : label.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (TextUtils.isEmpty(name)) name = "external.smali";
        if (!name.toLowerCase(Locale.US).endsWith(".smali")) name += ".smali";
        if (name.length() > 96) name = name.substring(0, 90) + ".smali";
        return System.currentTimeMillis() + "_" + name;
    }
}
