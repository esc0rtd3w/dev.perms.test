package dev.perms.test.memory;

import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import dev.perms.test.ExecMode;

/**
 * Activity-side runner for apk-medit commands launched from the Memory tab.
 *
 * The Activity supplies current UI state, shell staging, and shell output plumbing. This class
 * keeps Memory command validation, tool staging flow, command construction, and stable log text
 * together without owning any widgets.
 */
public final class MemoryToolCommandRunner {
    public interface ExecModeProvider {
        ExecMode getExecMode();
    }

    public interface TargetPackageProvider {
        String getTargetPackage();
    }

    public interface SelectedPidProvider {
        String getSelectedPid();
    }

    public interface BooleanProvider {
        boolean get();
    }

    public interface BackgroundExecutor {
        void execute(Runnable task);
    }

    public interface UiExecutor {
        void execute(Runnable task);
    }

    public interface ToolStager {
        CommandResult stage(String name);
    }

    public interface ShellAppendRunner {
        void runAndAppend(String command);
    }

    public interface OutputAppender {
        void append(String text);
    }

    public static final class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private final Context context;
    private final String publicBinDir;
    private final ExecModeProvider execModeProvider;
    private final TargetPackageProvider targetPackageProvider;
    private final SelectedPidProvider selectedPidProvider;
    private final BooleanProvider withoutPtraceProvider;
    private final BooleanProvider stringCaseSensitiveProvider;
    private final BackgroundExecutor backgroundExecutor;
    private final UiExecutor uiExecutor;
    private final ToolStager toolStager;
    private final ShellAppendRunner shellAppendRunner;
    private final OutputAppender outputAppender;

    public MemoryToolCommandRunner(Context context,
                                   String publicBinDir,
                                   ExecModeProvider execModeProvider,
                                   TargetPackageProvider targetPackageProvider,
                                   SelectedPidProvider selectedPidProvider,
                                   BooleanProvider withoutPtraceProvider,
                                   BooleanProvider stringCaseSensitiveProvider,
                                   BackgroundExecutor backgroundExecutor,
                                   UiExecutor uiExecutor,
                                   ToolStager toolStager,
                                   ShellAppendRunner shellAppendRunner,
                                   OutputAppender outputAppender) {
        this.context = context;
        this.publicBinDir = publicBinDir;
        this.execModeProvider = execModeProvider;
        this.targetPackageProvider = targetPackageProvider;
        this.selectedPidProvider = selectedPidProvider;
        this.withoutPtraceProvider = withoutPtraceProvider;
        this.stringCaseSensitiveProvider = stringCaseSensitiveProvider;
        this.backgroundExecutor = backgroundExecutor;
        this.uiExecutor = uiExecutor;
        this.toolStager = toolStager;
        this.shellAppendRunner = shellAppendRunner;
        this.outputAppender = outputAppender;
    }

    public void stageToolFromUi() {
        executeInBackground(() -> {
            final CommandResult result = stageTool(MemoryToolHelper.TOOL_NAME);
            executeOnUi(() -> {
                if (result != null && result.exitCode == 0) {
                    appendOutput("[Memory] Staged apk-medit to " + publicBinDir + "/" + MemoryToolHelper.TOOL_NAME + "\n");
                    Toast.makeText(context, "apk-medit staged", Toast.LENGTH_SHORT).show();
                } else {
                    appendOutput("[Memory] Failed to stage apk-medit.\n");
                    appendCommandOutput(result);
                    Toast.makeText(context, "apk-medit stage failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    public void run(String command, String dataType, String value, String begin, String end) {
        try {
            ExecMode modeNow = getExecMode();
            if (modeNow == ExecMode.SYSTEM) {
                Toast.makeText(context, "Memory tools require Shizuku or LADB.", Toast.LENGTH_SHORT).show();
                appendOutput("[Memory] System mode is not supported. Use Shizuku or LADB.\n");
                return;
            }

            final String pkg = getTargetPackage();
            if (TextUtils.isEmpty(pkg)) {
                Toast.makeText(context, "Enter a target package first.", Toast.LENGTH_SHORT).show();
                return;
            }

            final String normalizedType = MemoryToolHelper.normalizeDataType(dataType);
            final boolean withoutPtrace = getBoolean(withoutPtraceProvider);
            final String selectedPid = getSelectedPid();
            final String targetValue = value;
            final String dumpBegin = begin;
            final String dumpEnd = end;
            executeInBackground(() -> {
                CommandResult installResult = stageTool(MemoryToolHelper.TOOL_NAME);
                if (installResult == null || installResult.exitCode != 0) {
                    executeOnUi(() -> {
                        appendOutput("[Memory] apk-medit is not installed or could not be staged for the current backend.\n");
                        appendCommandOutput(installResult);
                    });
                    return;
                }

                final String shellCmd = MemoryToolHelper.buildRunAsCommand(
                        pkg,
                        publicBinDir,
                        withoutPtrace,
                        command,
                        selectedPid,
                        normalizedType,
                        targetValue,
                        dumpBegin,
                        dumpEnd,
                        getBoolean(stringCaseSensitiveProvider)
                );
                executeOnUi(() -> runShellCommandAndAppend(shellCmd));
            });
        } catch (Throwable t) {
            appendOutput("[Memory] Command setup failed: " + t + "\n");
        }
    }

    private ExecMode getExecMode() {
        return execModeProvider == null ? ExecMode.SYSTEM : execModeProvider.getExecMode();
    }

    private String getTargetPackage() {
        return targetPackageProvider == null ? null : targetPackageProvider.getTargetPackage();
    }

    private String getSelectedPid() {
        return selectedPidProvider == null ? null : selectedPidProvider.getSelectedPid();
    }

    private boolean getBoolean(BooleanProvider provider) {
        return provider != null && provider.get();
    }

    private void executeInBackground(Runnable task) {
        if (backgroundExecutor != null) backgroundExecutor.execute(task);
    }

    private void executeOnUi(Runnable task) {
        if (uiExecutor != null) uiExecutor.execute(task);
    }

    private CommandResult stageTool(String name) {
        return toolStager == null ? null : toolStager.stage(name);
    }

    private void runShellCommandAndAppend(String command) {
        if (shellAppendRunner != null) shellAppendRunner.runAndAppend(command);
    }

    private void appendCommandOutput(CommandResult result) {
        if (result == null) return;
        if (!TextUtils.isEmpty(result.stdout)) appendOutput(result.stdout.endsWith("\n") ? result.stdout : (result.stdout + "\n"));
        if (!TextUtils.isEmpty(result.stderr)) appendOutput(result.stderr.endsWith("\n") ? result.stderr : (result.stderr + "\n"));
    }

    private void appendOutput(String text) {
        if (outputAppender != null) outputAppender.append(text);
    }
}
