package dev.perms.test.debugging.jadx;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.debugging.DebuggingDexEntries;
import dev.perms.test.debugging.jobs.JadxGoJobClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Optional DEX-to-Java controls for the Debugging tab.
 *
 * This controller stages/runs the bundled standalone jadx-go backend named jadx
 * from assets/bin/<abi>/jadx. The upstream jadx Java/Gradle source is not
 * treated as a runnable Android binary here.
 */
public final class DebuggingJadxController {
    public interface Host {
        void setDebuggingBusy(boolean busy, String status);
        void appendOutput(String text);
        void runOnUiThread(Runnable action);
        void openJavaFileInInternalEditor(File file, int lineHint);
    }

    private final ActivityMainBinding binding;
    private final ExecutorService executor;
    private final Host host;
    private JadxGoJobClient jobClient;

    public DebuggingJadxController(ActivityMainBinding binding, ExecutorService executor, Host host) {
        this.binding = binding;
        this.executor = executor;
        this.host = host;
    }

    public void setup() {
        try {
            if (binding == null || binding.tabDebugging == null) return;
            if (binding.tabDebugging.btnJadxDecompile != null) {
                binding.tabDebugging.btnJadxDecompile.setOnClickListener(v -> runDecompileFromUi());
            }
            if (binding.tabDebugging.btnJadxOpenFirstJava != null) {
                binding.tabDebugging.btnJadxOpenFirstJava.setOnClickListener(v -> openFirstJavaFromUi());
            }
            if (binding.tabDebugging.chkJadxJavaInnerNames != null && !binding.tabDebugging.chkJadxJavaInnerNames.isChecked()) {
                // XML defaults this on. Leave a user-changed unchecked state alone after initial inflate.
            }
            getJobClient().restore();
            refreshDefaultOutput(false);
        } catch (Throwable t) {
            if (host != null) host.appendOutput("[Debugging] DEX to Java setup failed: " + safeMessage(t) + "\n");
        }
    }

    public void refreshDefaultOutput(boolean force) {
        try {
            if (binding == null || binding.tabDebugging == null || binding.tabDebugging.edtJadxJavaOutDir == null) return;
            if (!force && !TextUtils.isEmpty(textOf(binding.tabDebugging.edtJadxJavaOutDir))) return;
            String input = textOf(binding.tabDebugging.edtSmaliDexInput);
            String stem = sourceStem(input);
            binding.tabDebugging.edtJadxJavaOutDir.setText(dev.perms.test.debugging.smali.PermsTestSmaliTools.DEFAULT_JAVA_ROOT + "/" + stem);
        } catch (Throwable ignored) {
        }
    }

    private void runDecompileFromUi() {
        if (binding == null || binding.tabDebugging == null || host == null) return;
        final String input = textOf(binding.tabDebugging.edtSmaliDexInput);
        final String outDir = textOf(binding.tabDebugging.edtJadxJavaOutDir);
        if (TextUtils.isEmpty(input)) {
            host.setDebuggingBusy(false, "Choose an APK/DEX input before running DEX to Java.");
            return;
        }
        if (TextUtils.isEmpty(outDir)) {
            host.setDebuggingBusy(false, "Choose a Java output folder before running DEX to Java.");
            return;
        }
        boolean zipOutput = binding.tabDebugging.chkJadxZipOutput != null && binding.tabDebugging.chkJadxZipOutput.isChecked();
        boolean javaInnerNames = binding.tabDebugging.chkJadxJavaInnerNames != null && binding.tabDebugging.chkJadxJavaInnerNames.isChecked();
        boolean selectedDexOnly = binding.tabDebugging.chkJadxSelectedDexOnly != null && binding.tabDebugging.chkJadxSelectedDexOnly.isChecked();
        String dexEntry = selectedDexOnly ? DebuggingDexEntries.current(textOf(binding.tabDebugging.ddSmaliDexEntry), null) : "";
        if (outputFolderHasFiles(outDir)) {
            confirmJavaOutputOverwrite(outDir, () -> getJobClient().start(input, outDir, zipOutput, javaInnerNames, dexEntry));
            return;
        }
        getJobClient().start(input, outDir, zipOutput, javaInnerNames, dexEntry);
    }

    private void confirmJavaOutputOverwrite(String outDir, Runnable onConfirm) {
        if (onConfirm == null) return;
        Context context = null;
        try {
            if (binding != null && binding.getRoot() != null) context = binding.getRoot().getContext();
        } catch (Throwable ignored) {
        }
        if (context == null) {
            onConfirm.run();
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle("Java output already exists")
                .setMessage("The selected Java output folder already contains files.\n\n"
                        + outDir + "\n\n"
                        + "Starting again can overwrite matching Java output files and leave stale files from an older run.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Overwrite", (dialog, which) -> onConfirm.run())
                .show();
    }

    private boolean outputFolderHasFiles(String path) {
        if (TextUtils.isEmpty(path)) return false;
        try {
            File file = new File(path);
            if (!file.exists()) return false;
            if (file.isFile()) return true;
            File[] children = file.listFiles();
            return children != null && children.length > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openFirstJavaFromUi() {
        if (binding == null || binding.tabDebugging == null || host == null) return;
        final String outDir = textOf(binding.tabDebugging.edtJadxJavaOutDir);
        if (TextUtils.isEmpty(outDir)) {
            host.setDebuggingBusy(false, "Choose a Java output folder first.");
            return;
        }
        host.setDebuggingBusy(true, "Finding Java output...");
        if (executor == null) {
            host.setDebuggingBusy(false, "DEX to Java executor unavailable.");
            return;
        }
        executor.execute(() -> {
            try {
                File first = findFirstJavaFile(new File(outDir));
                if (first == null) throw new IOException("No .java files found under " + outDir);
                host.runOnUiThread(() -> {
                    host.setDebuggingBusy(false, "Opening " + first.getName() + ".");
                    host.openJavaFileInInternalEditor(first, 1);
                });
            } catch (Throwable t) {
                host.runOnUiThread(() -> {
                    String msg = safeMessage(t);
                    host.setDebuggingBusy(false, "Open Java failed: " + msg);
                    host.appendOutput("[Debugging] Open Java failed: " + msg + "\n");
                });
            }
        });
    }


    private JadxGoJobClient getJobClient() {
        if (jobClient == null) {
            android.content.Context context = null;
            try {
                if (binding != null && binding.getRoot() != null) context = binding.getRoot().getContext();
            } catch (Throwable ignored) {
            }
            jobClient = new JadxGoJobClient(context, new Handler(Looper.getMainLooper()), new JadxGoJobClient.Callbacks() {
                @Override
                public void setBusy(boolean busy, String status) {
                    if (host != null) host.setDebuggingBusy(busy, status);
                    updateSharedStatusProgress(status, busy, 0, 0, busy ? "start" : "");
                }

                @Override
                public void appendOutput(String text) {
                    if (host != null) host.appendOutput(text);
                }

                @Override
                public void setStatusText(String status) {
                    setStatusOnly(status);
                }

                @Override
                public void setProgressState(boolean running, String status, int current, int total, String phase) {
                    updateSharedStatusProgress(status, running, current, total, phase);
                }
            });
        }
        return jobClient;
    }

    private void setStatusOnly(String status) {
        try {
            if (TextUtils.isEmpty(status)) return;
            updateSharedStatusProgress(status, false, 0, 0, "");
        } catch (Throwable ignored) {
        }
    }

    private void updateSharedStatusProgress(String status, boolean running, int current, int total, String phase) {
        try {
            if (binding == null || binding.tabDebugging == null) return;
            if (TextUtils.isEmpty(status)) status = running ? "DEX to Java running..." : "DEX to Java ready.";
            if (binding.tabDebugging.rowDebuggingStatus != null) {
                binding.tabDebugging.rowDebuggingStatus.setVisibility(View.VISIBLE);
            }
            if (binding.tabDebugging.txtSmaliStatus != null) {
                binding.tabDebugging.txtSmaliStatus.setText(status);
            }
            if (binding.tabDebugging.progressDebuggingStatus != null) {
                binding.tabDebugging.progressDebuggingStatus.setVisibility(running ? View.VISIBLE : View.GONE);
                binding.tabDebugging.progressDebuggingStatus.setRunning(running);
                boolean indeterminate = total <= 0 || current <= 0 || current > total
                        || "zip".equals(phase) || "finalize".equals(phase) || "start".equals(phase);
                binding.tabDebugging.progressDebuggingStatus.setIndeterminate(indeterminate);
                if (!indeterminate) {
                    binding.tabDebugging.progressDebuggingStatus.setProgressFraction(Math.max(0f, Math.min(1f, current / (float) total)));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private File findFirstJavaFile(File root) {
        if (root == null || !root.exists()) return null;
        if (root.isFile()) return root.getName().toLowerCase(java.util.Locale.US).endsWith(".java") ? root : null;
        File[] files = root.listFiles();
        if (files == null) return null;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            if (file != null && file.isFile() && file.getName().toLowerCase(java.util.Locale.US).endsWith(".java")) return file;
        }
        for (File file : files) {
            File found = findFirstJavaFile(file);
            if (found != null) return found;
        }
        return null;
    }

    private static String textOf(android.widget.TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }

    private static String sourceStem(String input) {
        if (TextUtils.isEmpty(input)) return "jadx-output";
        String name = input;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(File.separatorChar));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        return TextUtils.isEmpty(name) ? "jadx-output" : name;
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (TextUtils.isEmpty(msg) ? "" : (": " + msg));
    }
}
