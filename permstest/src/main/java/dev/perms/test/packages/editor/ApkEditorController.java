package dev.perms.test.packages.editor;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.perms.test.apk.ApkDebugToolHelper;
import dev.perms.test.apk.DebuggableApkCreator;
import dev.perms.test.databinding.TabPackagesBinding;
import dev.perms.test.debugging.editor.SmaliEditorBrowseDialog;
import dev.perms.test.editor.SourceSyntaxHighlighter;
import dev.perms.test.packages.PackageAbiInspector;
import dev.perms.test.packages.InstalledPackageExtractor;
import dev.perms.test.packages.editor.jobs.ApkEditorJobClient;
import dev.perms.test.service.LongOperationStatusStore;

import java.io.File;
import java.util.concurrent.ExecutorService;

public final class ApkEditorController {
    public interface Host {
        Uri getPickedPackageUri();
        String getPickedPackageLabel();
        String queryDisplayName(Uri uri);
        ContentResolver contentResolver();
        File getExternalFilesDir(String type);
        ToolResult ensureBundledTool(String toolName);
        ToolResult runShell(String command);
        void appendOutput(String text);
        void runOnUiThread(Runnable runnable);
        void openSmaliWorkspace(String apkInputPath,
                                String smaliRootPath,
                                String selectedSmaliFilePath,
                                String dexOutputPath,
                                String apkOutputPath,
                                String dexEntry);
    }

    public static final class ToolResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ToolResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private final Context context;
    private final TabPackagesBinding binding;
    private final ExecutorService executor;
    private final Host host;
    private final ApkEditorJobClient jobClient;

    private Uri sourceUri;
    private String sourceLabel;
    private File stagedPackage;
    private File lastWorkspace;
    private File lastDecompileDir;
    private ApkEditorSmaliWorkspace.Workspace lastSmaliWorkspace;
    private boolean sourceReady;
    private boolean installedPackageSource;
    private boolean resourceWorkspaceReady;
    private boolean smaliWorkspaceReady;
    private boolean splitContainerReady;
    private String workspaceText = "";
    private String newPackageText = "";
    private String newAppNameText = "";
    private boolean makeDebuggableRequested = true;
    private boolean keepWorkspaceRequested = true;
    private boolean quickPatchRequested = true;
    private boolean renameAuthoritiesRequested = true;
    private boolean splitAwareRequested = true;
    private boolean useApktoolRequested = true;
    private volatile boolean apktoolAvailable;
    private volatile boolean zipalignAvailable;
    private volatile boolean toolProbeRunning;
    private volatile boolean smaliServiceJobRunning;
    private volatile boolean smaliJobPolling;

    public ApkEditorController(Context context, TabPackagesBinding binding, ExecutorService executor, Host host) {
        this.context = context;
        this.binding = binding;
        this.executor = executor;
        this.host = host;
        this.jobClient = new ApkEditorJobClient(context);
    }

    public void bind() {
        if (binding == null) return;
        if (binding.btnApkEditorUsePackageFile != null) binding.btnApkEditorUsePackageFile.setOnClickListener(v -> usePackageFile());
        if (binding.btnApkEditorUseInstalledPackage != null) binding.btnApkEditorUseInstalledPackage.setOnClickListener(v -> useInstalledPackage());
        if (binding.btnApkEditorInspect != null) binding.btnApkEditorInspect.setOnClickListener(v -> inspect());
        if (binding.btnApkEditorManifest != null) binding.btnApkEditorManifest.setOnClickListener(v -> exportManifest());
        if (binding.btnApkEditorUnpack != null) binding.btnApkEditorUnpack.setOnClickListener(v -> simpleUnpack());
        if (binding.btnApkEditorExtractSplits != null) binding.btnApkEditorExtractSplits.setOnClickListener(v -> extractSplits());
        if (binding.btnApkEditorSmaliDecompile != null) binding.btnApkEditorSmaliDecompile.setOnClickListener(v -> smaliDecompile());
        if (binding.btnApkEditorOpenSmali != null) binding.btnApkEditorOpenSmali.setOnClickListener(v -> openSmaliWorkspace());
        if (binding.btnApkEditorRepackSmali != null) binding.btnApkEditorRepackSmali.setOnClickListener(v -> repackSmali());
        if (binding.btnApkEditorDecompile != null) binding.btnApkEditorDecompile.setOnClickListener(v -> fullDecompile());
        if (binding.btnApkEditorRename != null) binding.btnApkEditorRename.setOnClickListener(v -> renamePackageAndApp());
        if (binding.btnApkEditorRebuild != null) binding.btnApkEditorRebuild.setOnClickListener(v -> rebuildAndSign());
        if (binding.btnApkEditorClear != null) binding.btnApkEditorClear.setOnClickListener(v -> clearWork());
        if (binding.chkApkEditorUseApktool != null) {
            binding.chkApkEditorUseApktool.setOnCheckedChangeListener((buttonView, isChecked) -> {
                useApktoolRequested = isChecked;
                toolProbeRunning = false;
                if (isChecked) {
                    refreshToolAvailability("Checking APK Editor tools...");
                } else {
                    apktoolAvailable = false;
                    resourceWorkspaceReady = false;
                    refreshToolAvailability("Apktool disabled. Checking zipalign for smali repack...");
                }
            });
        }
        sourceReady = false;
        installedPackageSource = false;
        resourceWorkspaceReady = false;
        smaliWorkspaceReady = false;
        splitContainerReady = false;
        setStatus("Ready. Pick a package file above, or select an installed package and tap Use Installed App.");
        updateToolButtons("Pick a package file above or select an installed app. apktool handles decode/rename/rebuild; smali repack is only needed after smali edits.");
        refreshToolAvailability("Checking APK Editor tools...");
        resumeSmaliDecompileJobIfNeeded();
    }

    private void usePackageFile() {
        try {
            Uri pickedUri = host == null ? null : host.getPickedPackageUri();
            String pickedLabel = host == null ? "" : host.getPickedPackageLabel();
            if (pickedUri == null) {
                toast("Pick a package file first.");
                setStatus("Pick an APK/APKS/APKM/XAPK/ZIP in APK Installer first.");
                return;
            }
            if (TextUtils.isEmpty(pickedLabel) && host != null) pickedLabel = host.queryDisplayName(pickedUri);
            if (TextUtils.isEmpty(pickedLabel)) pickedLabel = "package.apk";

            boolean changedPackage = sourceUri == null
                    || !pickedUri.equals(sourceUri)
                    || !TextUtils.equals(pickedLabel, sourceLabel);
            sourceUri = pickedUri;
            sourceLabel = pickedLabel;
            installedPackageSource = false;
            if (changedPackage) resetPackageWorkspaceState();

            if (binding.edtApkEditorSource != null) binding.edtApkEditorSource.setText(sourceLabel);
            if (binding.edtApkEditorWorkspace != null && changedPackage) {
                binding.edtApkEditorWorkspace.setText(ApkEditorFileUtils.publicWorkspace("", sourceLabel).getAbsolutePath());
            } else if (binding.edtApkEditorWorkspace != null && TextUtils.isEmpty(text(binding.edtApkEditorWorkspace))) {
                binding.edtApkEditorWorkspace.setText(ApkEditorFileUtils.publicWorkspace("", sourceLabel).getAbsolutePath());
            }
            if (changedPackage && binding.edtApkEditorNewPackage != null) binding.edtApkEditorNewPackage.setText("");
            if (changedPackage && binding.edtApkEditorNewAppName != null) binding.edtApkEditorNewAppName.setText("");

            captureUiState();
            File staged = ensureStagedPackage();
            ApkEditorArchiveInspector.Summary summary = null;
            try {
                summary = ApkEditorArchiveInspector.inspect(context, staged);
            } catch (Throwable ignored) {
            }
            splitContainerReady = summary != null && summary.isSplitContainer();
            sourceReady = true;
            if (summary != null && !TextUtils.isEmpty(summary.packageName) && binding.edtApkEditorNewPackage != null
                    && TextUtils.isEmpty(text(binding.edtApkEditorNewPackage))) {
                binding.edtApkEditorNewPackage.setText(summary.packageName + ".debug");
            }
            if (splitContainerReady) {
                setStatus("Loaded split package. Base: " + summary.baseApkEntryName + ". Inspect/extract and built-in smali are available; resource decode/rename/rebuild use apktool.");
            } else {
                setStatus("Loaded " + sourceLabel + ". Inspect/unpack and built-in smali are available; resource decode/rename/rebuild use apktool.");
            }
            toolProbeRunning = false;
            refreshToolAvailability(null);
        } catch (Throwable t) {
            append("[APK Editor] Failed to use selected package: " + t + "\n");
            setStatus("Failed to load selected package.");
        }
    }

    private void useInstalledPackage() {
        final String pkg = text(binding.edtTargetPkg);
        if (TextUtils.isEmpty(pkg)) {
            toast("Select an installed package first.");
            setStatus("Select an installed package in Packages > Permissions & State first.");
            return;
        }
        final String label = text(binding.ddApp);
        runJob("Extracting installed package for APK Editor...", () -> {
            File root = host == null ? null : host.getExternalFilesDir("apk_editor_installed");
            InstalledPackageExtractor extractor = new InstalledPackageExtractor(command -> {
                ToolResult r = host == null ? null : host.runShell(command);
                return new InstalledPackageExtractor.CommandResult(
                        r == null ? 1 : r.exitCode,
                        r == null ? "" : r.stdout,
                        r == null ? "Shell runner unavailable." : r.stderr);
            });
            InstalledPackageExtractor.ExtractedInstalledPackage extracted = extractor.extractForDebug(pkg, label, root);
            File packageFile = extracted == null ? null : extracted.packageFile;
            if (packageFile == null || !packageFile.isFile()) throw new java.io.IOException("Installed package extraction did not produce an APK/APKS file.");
            host.runOnUiThread(() -> {
                resetPackageWorkspaceState();
                sourceUri = null;
                installedPackageSource = true;
                sourceLabel = extracted.sourceLabel;
                if (TextUtils.isEmpty(sourceLabel)) sourceLabel = pkg + (extracted.apkCount > 1 ? ".apks" : ".apk");
                stagedPackage = packageFile;
                sourceReady = true;
                if (binding.edtApkEditorSource != null) binding.edtApkEditorSource.setText(sourceLabel);
                if (binding.edtApkEditorWorkspace != null) binding.edtApkEditorWorkspace.setText(ApkEditorFileUtils.publicWorkspace("", sourceLabel).getAbsolutePath());
                if (binding.edtApkEditorNewPackage != null && TextUtils.isEmpty(text(binding.edtApkEditorNewPackage))) binding.edtApkEditorNewPackage.setText(pkg + ".debug");
                if (binding.edtApkEditorNewAppName != null && TextUtils.isEmpty(text(binding.edtApkEditorNewAppName)) && !TextUtils.isEmpty(label)) binding.edtApkEditorNewAppName.setText(label);
                splitContainerReady = extracted.apkCount > 1;
                toolProbeRunning = false;
                refreshToolAvailability(null);
                updateToolButtons("Loaded installed package " + pkg + " for APK Editor.");
                setStatus("Loaded installed package " + pkg + " into APK Editor workspace.");
            });
            append("\n[APK Editor] Extracted installed package " + pkg + " to " + packageFile.getAbsolutePath() + "\n");
        });
    }

    private void resetPackageWorkspaceState() {
        stagedPackage = null;
        lastWorkspace = null;
        lastDecompileDir = null;
        lastSmaliWorkspace = null;
        sourceReady = false;
        installedPackageSource = false;
        resourceWorkspaceReady = false;
        smaliWorkspaceReady = false;
        splitContainerReady = false;
        workspaceText = "";
        newPackageText = "";
        newAppNameText = "";
    }


    private void inspect() {
        runJob("Inspecting package...", () -> {
            File staged = ensureStagedPackage();
            ApkEditorArchiveInspector.Summary summary = ApkEditorArchiveInspector.inspect(context, staged);
            splitContainerReady = summary != null && summary.isSplitContainer();
            String report = summary == null ? "Inspect failed." : summary.toReport(staged);
            append("\n" + report);
            showTextDialogOnUi("APK Editor Inspect", report, false);
            updateToolButtonsOnUi("Inspect complete: " + (summary == null || TextUtils.isEmpty(summary.packageName) ? staged.getName() : summary.packageName));
        });
    }

    private void exportManifest() {
        runJob("Loading manifest...", () -> {
            File staged = ensureStagedPackage();
            File workspace = workspaceDir();
            File decoded = currentDecodedWorkspace(workspace);
            File manifest = decoded == null ? null : new File(decoded, "AndroidManifest.xml");

            if (manifest == null || !manifest.isFile()) {
                File decodedNow = prepareDecompiledWorkspace(staged, workspace, true);
                if (decodedNow != null) manifest = new File(decodedNow, "AndroidManifest.xml");
            }

            if (manifest != null && manifest.isFile()) {
                showManifestEditorOnUi(manifest);
                append("\n[APK Editor] Manifest editor opened: " + manifest.getAbsolutePath() + "\n");
                updateToolButtonsOnUi("Manifest editor opened. Save changes, then Rebuild to apply them.");
                return;
            }

            ApkEditorManifestExporter.Result result = ApkEditorManifestExporter.export(staged, workspace);
            String preview = result.success && result.manifestFile != null
                    ? ApkEditorBinaryManifestPreview.render(result.manifestFile)
                    : result.message;
            String note = result.success
                    ? result.message + "\nRead-only binary manifest preview. Install apktool and Decode Resources to edit the real XML."
                    : null;
            append("\n[APK Editor] " + result.message);
            showTextDialogOnUi(result.success ? "AndroidManifest.xml (read-only)" : "AndroidManifest.xml", note, preview, result.success, true);
            setStatusOnUi(result.success ? "Binary manifest opened read-only. Install apktool to edit decoded XML." : "Manifest extract failed. Check log.");
        });
    }

    private void simpleUnpack() {
        runJob("Unpacking package...", () -> {
            File staged = ensureStagedPackage();
            File workspace = workspaceDir();
            File rawDir = new File(workspace, "raw");
            ApkEditorFileUtils.deleteTree(rawDir);
            ApkEditorFileUtils.unzip(staged, rawDir);
            append("\n[APK Editor] Simple unpack complete: " + rawDir.getAbsolutePath() + "\n");
            if (splitAwareRequested && ApkEditorSplitArchive.isSplitContainer(staged)) {
                ApkEditorSplitArchive.Workspace split = ApkEditorSplitArchive.extractSplitWorkspace(staged, workspace);
                append("[APK Editor] Split workspace ready: " + split.splitDir.getAbsolutePath() + "\n");
                append(split.toReport());
            }
            setStatusOnUi("Simple unpack complete: " + rawDir.getAbsolutePath());
        });
    }

    private void extractSplits() {
        runJob("Extracting split APKs...", () -> {
            File staged = ensureStagedPackage();
            File workspace = workspaceDir();
            if (!ApkEditorSplitArchive.isSplitContainer(staged)) {
                append("\n[APK Editor] Selected package is not a split archive. Single APK editing is already available.\n");
                setStatusOnUi("Selected file is a single APK, not a split archive.");
                return;
            }
            ApkEditorSplitArchive.Workspace split = ApkEditorSplitArchive.extractSplitWorkspace(staged, workspace);
            append("\n[APK Editor] Split APK workspace extracted.\n");
            append(split.toReport());
            setStatusOnUi("Split workspace ready. Decompile/Rename works on base APK and preserves other splits.");
        });
    }

    private void smaliDecompile() {
        captureUiState();
        try {
            File staged = ensureStagedPackage();
            File workspace = workspaceDir();
            lastWorkspace = workspace;
            smaliServiceJobRunning = true;
            setStatus("Starting foreground smali decompile service...");
            updateToolButtons(null);
            append("\n[APK Editor] Starting foreground smali decompile service for "
                    + (TextUtils.isEmpty(sourceLabel) ? staged.getName() : sourceLabel) + "\n");
            jobClient.startSmaliDecompile(staged, workspace, sourceLabel, splitAwareRequested, 35);
            startSmaliJobPolling();
        } catch (Throwable t) {
            smaliServiceJobRunning = false;
            append("[APK Editor] Failed to start smali decompile service: " + ApkEditorArchiveInspector.shortError(t) + "\n");
            setStatus("Smali decompile could not start. Check log.");
            updateToolButtons(null);
        }
    }

    private void openSmaliWorkspace() {
        runJob("Opening smali workspace...", () -> {
            File staged = ensureStagedPackage();
            File workspace = workspaceDir();
            ApkEditorSmaliWorkspace.Workspace smali = lastSmaliWorkspace;
            if (smali == null || !ApkEditorSmaliWorkspace.hasSmaliWorkspace(workspace)) {
                smali = ApkEditorSmaliWorkspace.restoreExisting(staged, workspace, splitAwareRequested);
            }
            lastSmaliWorkspace = smali;
            smaliWorkspaceReady = true;
            File firstFile = ApkEditorSmaliWorkspace.findFirstSmaliFile(smali.smaliRoot);
            if (firstFile == null) {
                append("\n[APK Editor] No .smali file was found. Run Smali Decompile first.\n");
                setStatusOnUi("No smali files found. Run Smali Decompile first.");
                updateToolButtonsOnUi(null);
                return;
            }
            showSmaliPickerOnUi(smali);
            updateToolButtonsOnUi("Choose a smali file from the decompiled workspace.");
        });
    }

    private void showSmaliPickerOnUi(ApkEditorSmaliWorkspace.Workspace smali) {
        if (host == null || smali == null) return;
        host.runOnUiThread(() -> showSmaliPicker(smali));
    }

    private void showSmaliPicker(ApkEditorSmaliWorkspace.Workspace smali) {
        try {
            File startDir = smali.firstSmaliDir();
            if (startDir == null || !startDir.isDirectory()) startDir = smali.smaliRoot;
            SmaliEditorBrowseDialog.show(context, startDir, smali.smaliRoot, false, new SmaliEditorBrowseDialog.Host() {
                @Override
                public boolean isAllowedSmaliFile(File file) {
                    return isInside(file, smali.smaliRoot);
                }

                @Override
                public void onSmaliFileSelected(File file) {
                    openSelectedSmaliFile(smali, file);
                }

                @Override
                public void onSmaliBrowseError(String label, Throwable error) {
                    append("[APK Editor] Smali browse " + label + " failed: "
                            + ApkEditorArchiveInspector.shortError(error) + "\n");
                    setStatus("Smali browse failed. Check log.");
                }
            });
            setStatus("Choose a smali file from the decompiled workspace.");
        } catch (Throwable t) {
            append("[APK Editor] Smali picker failed: " + ApkEditorArchiveInspector.shortError(t) + "\n");
            setStatus("Smali picker failed. Check log.");
        }
    }

    private void openSelectedSmaliFile(ApkEditorSmaliWorkspace.Workspace smali, File selectedFile) {
        if (smali == null || selectedFile == null || host == null) return;
        try {
            String dexEntry = smali.dexEntryForSmaliFile(selectedFile);
            File smaliDir = smali.smaliDirFor(dexEntry);
            File dexOut = smali.dexOutFor(dexEntry);
            host.openSmaliWorkspace(
                    smali.editApk.getAbsolutePath(),
                    smaliDir.getAbsolutePath(),
                    selectedFile.getAbsolutePath(),
                    dexOut.getAbsolutePath(),
                    smali.outputPackage.getAbsolutePath(),
                    dexEntry);
            append("\n[APK Editor] Smali editor opened at " + selectedFile.getAbsolutePath() + "\n");
            setStatus("Opened " + selectedFile.getName() + ". Save edits there, then return and tap Repack Smali.");
        } catch (Throwable t) {
            append("[APK Editor] Failed to open selected smali file: "
                    + ApkEditorArchiveInspector.shortError(t) + "\n");
            setStatus("Smali open failed. Check log.");
        }
    }

    private static boolean isInside(File file, File root) {
        if (file == null || root == null) return false;
        try {
            String filePath = file.getCanonicalPath();
            String rootPath = root.getCanonicalPath();
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void repackSmali() {
        runJob("Preparing smali repack...", () -> {
            File staged = ensureStagedPackage();
            File workspace = workspaceDir();
            ApkEditorSmaliWorkspace.Workspace smali = lastSmaliWorkspace;
            if (smali == null || !ApkEditorSmaliWorkspace.hasSmaliWorkspace(workspace)) {
                smali = ApkEditorSmaliWorkspace.restoreExisting(staged, workspace, splitAwareRequested);
            }
            lastSmaliWorkspace = smali;
            smaliWorkspaceReady = true;
            ToolResult zipalign = requireTool(ApkDebugToolHelper.TOOL_ZIPALIGN);
            if (zipalign == null) {
                zipalignAvailable = false;
                updateToolButtonsOnUi("zipalign is unavailable. Smali Repack needs signing/zipalign to create an installable package.");
                return;
            }
            setStatusOnUi("Assembling smali and replacing DEX...");
            ApkEditorSmaliWorkspace.RepackResult repack = ApkEditorSmaliWorkspace.assembleAndRebuildBase(smali, 35, true);
            append("\n" + repack.report);
            File export;
            if (smali.splitArchive) {
                export = rebuildAndSignSplitArchive(workspace,
                        new File(workspace, "smali_build"),
                        smali.originalPackage,
                        repack.unsignedBaseApk,
                        "smali-edited",
                        zipalign);
            } else {
                export = signAndExportSingleApk(workspace, repack.unsignedBaseApk, "smali-edited", zipalign);
            }
            if (export != null) {
                updateToolButtonsOnUi("Smali repack complete: " + export.getAbsolutePath());
            }
        });
    }

    private void fullDecompile() {
        runJob("Preparing apktool resource decode...", () -> {
            File staged = ensureStagedPackage();
            File workspace = workspaceDir();
            File outDir = prepareDecompiledWorkspace(staged, workspace, false);
            if (outDir != null && outDir.isDirectory()) {
                lastDecompileDir = outDir;
                resourceWorkspaceReady = true;
                append("[APK Editor] Decoded resource workspace: " + outDir.getAbsolutePath() + "\n");
                updateToolButtonsOnUi("Resource decode complete. Rename and Rebuild + Sign are enabled for this workspace.");
            }
        });
    }

    private void renamePackageAndApp() {
        runJob("Preparing package rename...", () -> {
            ensureStagedPackage();
            File workspace = workspaceDir();
            File decompiled = currentDecodedWorkspace(workspace);
            if (decompiled == null || !decompiled.isDirectory()) {
                append("\n[APK Editor] Run Decode Resources first. Rename uses the apktool-decoded manifest/resource workspace.\n");
                setStatusOnUi("Run Decode Resources first, then Rename.");
                return;
            }
            String oldPackage = ApkEditorWorkspacePatcher.readPackageName(decompiled);
            if (TextUtils.isEmpty(newPackageText) && TextUtils.isEmpty(newAppNameText)) {
                append("\n[APK Editor] Enter a new package name and/or app name before running Rename Package/App.\n");
                setStatusOnUi("Enter a new package name or app name first.");
                return;
            }
            setStatusOnUi("Applying package/app rename...");
            ApkEditorWorkspacePatcher.RenameResult patch = ApkEditorWorkspacePatcher.renamePackageAndLabel(
                    decompiled,
                    oldPackage,
                    newPackageText,
                    newAppNameText,
                    quickPatchRequested,
                    renameAuthoritiesRequested);
            append("\n[APK Editor] Rename result: " + patch.message + "\n");
            append("[APK Editor] Mode: " + (quickPatchRequested ? "targeted decoded manifest/resource patch" : "deep text reference pass")
                    + ", authorities=" + renameAuthoritiesRequested + "\n");
            if (!patch.success) {
                setStatusOnUi("Rename skipped. Check log.");
                return;
            }
            File export = rebuildAndSignWorkspace(workspace, decompiled, "renamed");
            if (export != null) setStatusOnUi("Renamed APK created: " + export.getAbsolutePath());
        });
    }

    private void rebuildAndSign() {
        runJob("Preparing rebuild...", () -> {
            File workspace = workspaceDir();
            File decompiled = currentDecodedWorkspace(workspace);
            if (!decompiled.isDirectory() || !new File(decompiled, "AndroidManifest.xml").isFile()) {
                append("\n[APK Editor] No decoded resource workspace found. Run Decode Resources first.\n");
                setStatusOnUi("Run Decode Resources first, then edit and rebuild.");
                return;
            }
            rebuildAndSignWorkspace(workspace, decompiled, "edited");
        });
    }

    private File prepareDecompiledWorkspace(File staged, File workspace, boolean reuseExisting) throws Exception {
        if (!apktoolUseAllowed("Decode Resources")) return null;
        File outDir = new File(workspace, "decompiled");
        if (reuseExisting && new File(outDir, "AndroidManifest.xml").isFile()) {
            lastDecompileDir = outDir;
            append("[APK Editor] Reusing decoded resource workspace: " + outDir.getAbsolutePath() + "\n");
            return outDir;
        }
        File toolInput;
        if (splitAwareRequested && ApkEditorSplitArchive.isSplitContainer(staged)) {
            ApkEditorSplitArchive.Workspace split = ApkEditorSplitArchive.extractSplitWorkspace(staged, workspace);
            toolInput = split.baseApk;
            append("[APK Editor] Split-aware mode: decompiling base APK " + split.info.baseEntryName + "\n");
            append("[APK Editor] Other splits stay staged in " + split.apkDir.getAbsolutePath() + " and are preserved during split rebuild.\n");
        } else {
            toolInput = ApkEditorFileUtils.findToolInputApk(staged, new File(workspace, "stage"));
        }
        ToolResult apktool = requireTool(ApkDebugToolHelper.TOOL_APKTOOL);
        if (apktool == null) return null;
        ApkEditorFileUtils.deleteTree(outDir);
        setStatusOnUi("Running apktool resource decode...");
        File decodeLog = apktoolLogFile("editor-decode");
        String cmd = ApkDebugToolHelper.shQuote(ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_APKTOOL)
                + " d -f -v --no-debug-info --log " + ApkDebugToolHelper.shQuote(decodeLog.getAbsolutePath())
                + " " + ApkDebugToolHelper.shQuote(toolInput.getAbsolutePath())
                + " -o " + ApkDebugToolHelper.shQuote(outDir.getAbsolutePath());
        append("[APK Editor] apktool decode log: " + decodeLog.getAbsolutePath() + "\n");
        ToolResult r = runShell(cmd);
        appendToolOutput("[APK Editor] apktool decode", r);
        if (r.exitCode == 0 && outDir.isDirectory()) {
            lastDecompileDir = outDir;
            return outDir;
        }
        setStatusOnUi("Resource decode failed. Check log.");
        return null;
    }

    private File rebuildAndSignWorkspace(File workspace, File decompiled, String suffix) throws Exception {
        if (!apktoolUseAllowed("Rebuild + Sign")) return null;
        ToolResult apktool = requireTool(ApkDebugToolHelper.TOOL_APKTOOL);
        if (apktool == null) {
            apktoolAvailable = false;
            updateToolButtonsOnUi("apktool is unavailable. Inspect/unpack and built-in smali still work; resource decode/rename/rebuild are disabled.");
            return null;
        }
        ToolResult zipalign = requireTool(ApkDebugToolHelper.TOOL_ZIPALIGN);
        if (zipalign == null) {
            zipalignAvailable = false;
            updateToolButtonsOnUi("zipalign is unavailable. Decompile can still work; rename/rebuild export is disabled.");
            return null;
        }
        File buildDir = new File(workspace, "build");
        if (!buildDir.exists() && !buildDir.mkdirs()) throw new java.io.IOException("Unable to create " + buildDir.getAbsolutePath());
        File unsigned = new File(buildDir, "apk-editor-unsigned.apk");
        if (unsigned.isFile() && !unsigned.delete()) {
            append("[APK Editor] Warning: unable to delete previous unsigned rebuild before apktool build: " + unsigned.getAbsolutePath() + "\n");
        }
        File buildLog = apktoolLogFile("editor-build");
        String buildCmd = ApkDebugToolHelper.shQuote(ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_APKTOOL)
                + " b -f -v"
                + (makeDebuggableRequested ? " --debuggable" : "")
                + " --log " + ApkDebugToolHelper.shQuote(buildLog.getAbsolutePath())
                + " " + ApkDebugToolHelper.shQuote(decompiled.getAbsolutePath())
                + " -o " + ApkDebugToolHelper.shQuote(unsigned.getAbsolutePath());
        append("[APK Editor] apktool build log: " + buildLog.getAbsolutePath() + "\n");
        setStatusOnUi("Rebuilding decoded APK workspace...");
        ToolResult build = runShell(buildCmd);
        appendToolOutput("[APK Editor] apktool build", build);
        if (build.exitCode != 0 || !unsigned.isFile()) {
            setStatusOnUi("Rebuild failed. Check log.");
            return null;
        }
        if (!makeDebuggableRequested) {
            append("[APK Editor] Rebuild produced an unsigned APK. Enable Make debuggable when an installable debug-signed export is required.\n");
            setStatusOnUi("Enable Make debuggable to export a signed rebuild.");
            return null;
        }
        File original = ensureStagedPackage();
        if (splitAwareRequested && ApkEditorSplitArchive.isSplitContainer(original)) {
            return rebuildAndSignSplitArchive(workspace, buildDir, original, unsigned, suffix, zipalign);
        }
        return signAndExportSingleApk(workspace, unsigned, suffix, zipalign);
    }

    private File signAndExportSingleApk(File workspace, File unsigned, String suffix, ToolResult zipalign) throws Exception {
        setStatusOnUi("Zipaligning and signing rebuilt APK...");
        DebuggableApkCreator.ToolRunner toolRunner = command -> {
            ToolResult shell = runShell(command);
            return new DebuggableApkCreator.ToolResult(shell.exitCode, shell.stdout, shell.stderr);
        };
        File signDir = new File(workspace, "signed");
        DebuggableApkCreator.Result signed = DebuggableApkCreator.create(
                context,
                unsigned,
                signDir,
                false,
                null,
                ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_ZIPALIGN,
                toolRunner);
        if (signed == null || !signed.success || signed.signedApk == null || !signed.signedApk.isFile()) {
            append("[APK Editor] Sign/export failed: " + (signed == null ? "unknown" : signed.message) + "\n");
            setStatusOnUi("Signing failed. Check log.");
            return null;
        }
        File export = new File(workspace, ApkEditorFileUtils.stem(sourceLabel) + "-" + suffix + "-debuggable.apk");
        ApkEditorFileUtils.exportFile(signed.signedApk, export);
        append("[APK Editor] Rebuilt, patched debuggable, signed, and exported: " + export.getAbsolutePath() + "\n");
        appendAbiWarningIfNeeded(export);
        setStatusOnUi("Created: " + export.getAbsolutePath());
        cleanupBuildDirs(workspace);
        return export;
    }

    private File rebuildAndSignSplitArchive(File workspace, File buildDir, File originalArchive, File rebuiltBaseApk, String suffix, ToolResult zipalign) throws Exception {
        setStatusOnUi("Preparing split archive export...");
        ApkEditorSplitArchive.Info info = ApkEditorSplitArchive.inspect(originalArchive);
        if (TextUtils.isEmpty(info.baseEntryName)) {
            append("[APK Editor] Split export failed: no base APK entry was found.\n");
            setStatusOnUi("Split export failed: no base APK found.");
            return null;
        }
        File merged = new File(buildDir, "apk-editor-split-replaced-base.zip");
        ApkEditorSplitArchive.createArchiveReplacingBase(originalArchive, info.baseEntryName, rebuiltBaseApk, merged);
        append("[APK Editor] Split-aware export: replaced base " + info.baseEntryName + " and preserved "
                + Math.max(0, info.apkEntries.size() - 1) + " companion split(s)/metadata.\n");
        DebuggableApkCreator.ToolRunner toolRunner = command -> {
            ToolResult shell = runShell(command);
            return new DebuggableApkCreator.ToolResult(shell.exitCode, shell.stdout, shell.stderr);
        };
        File signDir = new File(workspace, "signed_splits");
        DebuggableApkCreator.Result signed = DebuggableApkCreator.create(
                context,
                merged,
                signDir,
                false,
                null,
                ApkDebugToolHelper.PUBLIC_BIN_DIR + "/" + ApkDebugToolHelper.TOOL_ZIPALIGN,
                toolRunner);
        if (signed == null || !signed.success || signed.signedApk == null || !signed.signedApk.isFile()) {
            append("[APK Editor] Split sign/export failed: " + (signed == null ? "unknown" : signed.message) + "\n");
            setStatusOnUi("Split signing failed. Check log.");
            return null;
        }
        File export = new File(workspace, ApkEditorFileUtils.stem(sourceLabel) + "-" + suffix + "-debuggable.apks");
        ApkEditorFileUtils.exportFile(signed.signedApk, export);
        append("[APK Editor] Split archive rebuilt and signed: " + export.getAbsolutePath() + "\n");
        append("[APK Editor] Install this as a split package archive, not as a single APK.\n");
        setStatusOnUi("Created split archive: " + export.getAbsolutePath());
        cleanupBuildDirs(workspace);
        return export;
    }

    private void appendAbiWarningIfNeeded(File apk) {
        String warning = PackageAbiInspector.buildInstallWarning(apk);
        if (!TextUtils.isEmpty(warning)) {
            append("[APK Editor] ABI warning: " + warning + "\n");
        }
    }

    private void cleanupBuildDirs(File workspace) {
        if (keepWorkspaceRequested) return;
        ApkEditorFileUtils.deleteTree(new File(workspace, "build"));
        ApkEditorFileUtils.deleteTree(new File(workspace, "signed"));
        ApkEditorFileUtils.deleteTree(new File(workspace, "signed_splits"));
    }

    private void clearWork() {
        runJob("Clearing APK Editor workspace...", () -> {
            File workspace = workspaceDir();
            ApkEditorFileUtils.deleteTree(workspace);
            stagedPackage = null;
            sourceUri = null;
            sourceLabel = "";
            installedPackageSource = false;
            lastWorkspace = null;
            lastDecompileDir = null;
            lastSmaliWorkspace = null;
            sourceReady = false;
            resourceWorkspaceReady = false;
            smaliWorkspaceReady = false;
            splitContainerReady = false;
            host.runOnUiThread(() -> {
                if (binding.edtApkEditorSource != null) binding.edtApkEditorSource.setText("");
                if (binding.edtApkEditorWorkspace != null) binding.edtApkEditorWorkspace.setText("");
                if (binding.edtApkEditorNewPackage != null) binding.edtApkEditorNewPackage.setText("");
                if (binding.edtApkEditorNewAppName != null) binding.edtApkEditorNewAppName.setText("");
                updateToolButtons("Workspace cleared.");
            });
            append("\n[APK Editor] Cleared workspace: " + workspace.getAbsolutePath() + "\n");
            setStatusOnUi("Workspace cleared.");
        });
    }

    private File ensureStagedPackage() throws Exception {
        if (installedPackageSource && stagedPackage != null && stagedPackage.isFile()) return stagedPackage;
        Uri pickedUri = host == null ? null : host.getPickedPackageUri();
        if (sourceUri == null && pickedUri != null) {
            sourceUri = pickedUri;
            installedPackageSource = false;
            sourceLabel = host == null ? "" : host.getPickedPackageLabel();
            if (TextUtils.isEmpty(sourceLabel) && host != null) sourceLabel = host.queryDisplayName(pickedUri);
        } else if (pickedUri != null && sourceUri != null && !pickedUri.equals(sourceUri)) {
            sourceUri = pickedUri;
            installedPackageSource = false;
            sourceLabel = host == null ? "" : host.getPickedPackageLabel();
            if (TextUtils.isEmpty(sourceLabel) && host != null) sourceLabel = host.queryDisplayName(pickedUri);
            resetPackageWorkspaceState();
        }
        if (sourceUri == null) throw new java.io.IOException("Pick a package file first.");
        if (TextUtils.isEmpty(sourceLabel)) sourceLabel = "package.apk";
        if (stagedPackage != null && stagedPackage.isFile()) return stagedPackage;
        File base = host == null ? null : host.getExternalFilesDir("apk_editor_stage");
        stagedPackage = ApkEditorFileUtils.stageUri(host.contentResolver(), sourceUri, base, sourceLabel);
        return stagedPackage;
    }

    private File workspaceDir() {
        File workspace = ApkEditorFileUtils.publicWorkspace(workspaceText, sourceLabel);
        lastWorkspace = workspace;
        if (!workspace.exists()) workspace.mkdirs();
        return workspace;
    }


    private File currentDecodedWorkspace(File workspace) {
        File decompiled = lastDecompileDir != null ? lastDecompileDir : new File(workspace, "decompiled");
        if (decompiled.isDirectory() && new File(decompiled, "AndroidManifest.xml").isFile()) {
            resourceWorkspaceReady = true;
        }
        return decompiled;
    }

    private boolean hasSourceReady() {
        return sourceReady || sourceUri != null || (stagedPackage != null && stagedPackage.isFile());
    }

    private boolean hasSmaliWorkspaceReady() {
        if (smaliWorkspaceReady) return true;
        File workspace = lastWorkspace;
        if (workspace == null && !TextUtils.isEmpty(workspaceText)) workspace = ApkEditorFileUtils.publicWorkspace(workspaceText, sourceLabel);
        if (ApkEditorSmaliWorkspace.hasSmaliWorkspace(workspace)) {
            smaliWorkspaceReady = true;
            return true;
        }
        return false;
    }

    private boolean apktoolUseAllowed(String action) {
        if (useApktoolRequested) return true;
        append("[APK Editor] " + action + " needs apktool. Enable Use apktool, then refresh/use a backend that can run the bundled tools.\n");
        setStatusOnUi(action + " needs apktool. Enable Use apktool first.");
        return false;
    }

    private void resumeSmaliDecompileJobIfNeeded() {
        try {
            LongOperationStatusStore.Snapshot snapshot = jobClient.snapshot(false);
            if (snapshot.running) {
                smaliServiceJobRunning = true;
                if (!TextUtils.isEmpty(snapshot.status)) setStatus(snapshot.status);
                startSmaliJobPolling();
            }
        } catch (Throwable ignored) {
        }
    }

    private void startSmaliJobPolling() {
        if (smaliJobPolling) return;
        smaliJobPolling = true;
        pollSmaliJob();
    }

    private void pollSmaliJob() {
        if (binding == null || binding.getRoot() == null) {
            smaliJobPolling = false;
            return;
        }
        LongOperationStatusStore.Snapshot snapshot;
        try {
            snapshot = jobClient.snapshot(true);
        } catch (Throwable t) {
            smaliServiceJobRunning = false;
            smaliJobPolling = false;
            setStatus("APK Editor service status unavailable.");
            updateToolButtons(null);
            return;
        }
        if (!TextUtils.isEmpty(snapshot.log)) append(snapshot.log);
        if (!TextUtils.isEmpty(snapshot.status)) setStatus(snapshot.status);
        smaliServiceJobRunning = snapshot.running;
        if (snapshot.running) {
            updateToolButtons(null);
            binding.getRoot().postDelayed(this::pollSmaliJob, 900L);
            return;
        }

        smaliJobPolling = false;
        if (snapshot.success) {
            smaliWorkspaceReady = true;
            if (!TextUtils.isEmpty(snapshot.resultPath)) {
                lastWorkspace = new File(snapshot.resultPath);
                try { lastSmaliWorkspace = restoreServiceSmaliWorkspace(lastWorkspace); } catch (Throwable ignored) {}
            }
            updateToolButtons("Smali workspace ready. Open Smali to choose a file, then Repack Smali when finished.");
        } else if (!TextUtils.isEmpty(snapshot.error)) {
            updateToolButtons("Smali decompile failed. Check log.");
        } else {
            updateToolButtons(null);
        }
    }

    private ApkEditorSmaliWorkspace.Workspace restoreServiceSmaliWorkspace(File workspace) throws Exception {
        File staged = ensureStagedPackage();
        return ApkEditorSmaliWorkspace.restoreExisting(staged, workspace, splitAwareRequested);
    }

    private void refreshToolAvailability(String status) {
        captureUiState();
        if (executor == null || host == null) {
            apktoolAvailable = false;
            zipalignAvailable = false;
            updateToolButtons("APK Editor tools are still being checked.");
            return;
        }
        if (toolProbeRunning) return;
        toolProbeRunning = true;
        if (!TextUtils.isEmpty(status)) setStatus(status);
        executor.execute(() -> {
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                ToolResult apktool = useApktoolRequested ? host.ensureBundledTool(ApkDebugToolHelper.TOOL_APKTOOL) : null;
                ToolResult zipalign = host.ensureBundledTool(ApkDebugToolHelper.TOOL_ZIPALIGN);
                apktoolAvailable = useApktoolRequested && apktool != null && apktool.exitCode == 0;
                zipalignAvailable = zipalign != null && zipalign.exitCode == 0;
                if (!useApktoolRequested) resourceWorkspaceReady = false;
                String message;
                if (!useApktoolRequested) {
                    message = zipalignAvailable
                            ? "Apktool disabled. Inspect/unpack and built-in smali work; Smali Repack can export with zipalign/signing."
                            : "Apktool disabled. Inspect/unpack and built-in smali decompile work; Smali Repack needs zipalign/signing.";
                } else if (apktoolAvailable && zipalignAvailable) {
                    message = "APK Editor tools ready. Decode Resources is enabled; Rename/Rebuild enable after a decoded workspace exists. Rebuild does not need smali unless smali was edited.";
                } else if (apktoolAvailable) {
                    message = "apktool ready, but zipalign is unavailable. Decode Resources is enabled; Rename/Rebuild and Smali Repack export are disabled.";
                } else {
                    message = zipalignAvailable
                            ? "apktool unavailable. Inspect/unpack and built-in smali work; resource decode/rename/rebuild are disabled."
                            : "apktool and zipalign unavailable. Inspect/unpack and built-in smali decompile still work.";
                }
                updateToolButtonsOnUi(message);
            } catch (Throwable t) {
                apktoolAvailable = false;
                zipalignAvailable = false;
                updateToolButtonsOnUi("APK Editor tool check failed. Inspect/unpack and built-in smali decompile still work; apktool actions are disabled.");
            }
        });
    }

    private void updateToolButtonsOnUi(String status) {
        if (host == null) {
            updateToolButtons(status);
            return;
        }
        host.runOnUiThread(() -> updateToolButtons(status));
    }

    private void updateToolButtons(String status) {
        toolProbeRunning = false;
        boolean hasSource = hasSourceReady();
        boolean hasSmali = hasSmaliWorkspaceReady();
        boolean busy = smaliServiceJobRunning;
        boolean apktoolEnabled = useApktoolRequested && apktoolAvailable;
        boolean exportEnabled = apktoolEnabled && zipalignAvailable;
        boolean decodedReady = false;
        try {
            File workspace = lastWorkspace;
            if (workspace == null && hasSource) workspace = ApkEditorFileUtils.publicWorkspace(workspaceText, sourceLabel);
            if (workspace != null) {
                File decoded = currentDecodedWorkspace(workspace);
                decodedReady = decoded.isDirectory() && new File(decoded, "AndroidManifest.xml").isFile();
            }
        } catch (Throwable ignored) {
            decodedReady = resourceWorkspaceReady;
        }
        if (binding != null) {
            if (binding.btnApkEditorInspect != null) binding.btnApkEditorInspect.setEnabled(hasSource && !busy);
            if (binding.btnApkEditorManifest != null) binding.btnApkEditorManifest.setEnabled(hasSource && !busy);
            if (binding.btnApkEditorUnpack != null) binding.btnApkEditorUnpack.setEnabled(hasSource && !busy);
            if (binding.btnApkEditorExtractSplits != null) binding.btnApkEditorExtractSplits.setEnabled(hasSource && splitContainerReady && !busy);
            if (binding.btnApkEditorSmaliDecompile != null) binding.btnApkEditorSmaliDecompile.setEnabled(hasSource && !busy);
            if (binding.btnApkEditorOpenSmali != null) binding.btnApkEditorOpenSmali.setEnabled(hasSource && hasSmali && !busy);
            if (binding.btnApkEditorRepackSmali != null) binding.btnApkEditorRepackSmali.setEnabled(hasSource && hasSmali && zipalignAvailable && !busy);
            if (binding.btnApkEditorDecompile != null) binding.btnApkEditorDecompile.setEnabled(hasSource && apktoolEnabled && !busy);
            if (binding.btnApkEditorRename != null) binding.btnApkEditorRename.setEnabled(hasSource && decodedReady && exportEnabled && !busy);
            if (binding.btnApkEditorRebuild != null) binding.btnApkEditorRebuild.setEnabled(hasSource && decodedReady && exportEnabled && !busy);
            if (binding.btnApkEditorClear != null) binding.btnApkEditorClear.setEnabled(!busy);
            boolean renameOptionsEnabled = apktoolEnabled && decodedReady && !busy;
            if (binding.chkApkEditorQuickPatch != null) binding.chkApkEditorQuickPatch.setEnabled(renameOptionsEnabled);
            if (binding.chkApkEditorRenameAuthorities != null) binding.chkApkEditorRenameAuthorities.setEnabled(renameOptionsEnabled);
        }
        if (!TextUtils.isEmpty(status)) setStatus(status);
    }


    private File apktoolLogFile(String phase) {
        String safePhase = TextUtils.isEmpty(phase) ? "apktool" : phase.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeSource = ApkDebugToolHelper.sanitizeSourceName(sourceLabel);
        safeSource = safeSource.replaceAll("[^a-zA-Z0-9._-]", "_");
        return new File(ApkDebugToolHelper.APKTOOL_LOG_DIR,
                "apktool-" + System.currentTimeMillis() + "-" + safeSource + "-" + safePhase + ".log");
    }

    private ToolResult requireTool(String tool) {
        ToolResult r = host == null ? null : host.ensureBundledTool(tool);
        if (r == null || r.exitCode != 0) {
            append("[APK Editor] " + tool + " is not ready. "
                    + (r == null ? "No tool result." : (r.stderr + r.stdout)) + "\n");
            setStatusOnUi(tool + " is missing or backend is not ready.");
            return null;
        }
        return r;
    }

    private void runJob(String status, Job job) {
        captureUiState();
        setStatus(status);
        if (executor == null) {
            append("[APK Editor] Background executor is missing.\n");
            setStatus("APK Editor is not ready.");
            return;
        }
        executor.execute(() -> {
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) {}
            try {
                if (job != null) job.run();
            } catch (Throwable t) {
                append("[APK Editor] Failed: " + ApkEditorArchiveInspector.shortError(t) + "\n");
                setStatusOnUi("APK Editor failed. Check log.");
            }
        });
    }

    private void captureUiState() {
        try { workspaceText = binding == null ? "" : text(binding.edtApkEditorWorkspace); } catch (Throwable ignored) { workspaceText = ""; }
        try { newPackageText = binding == null ? "" : text(binding.edtApkEditorNewPackage); } catch (Throwable ignored) { newPackageText = ""; }
        try { newAppNameText = binding == null ? "" : text(binding.edtApkEditorNewAppName); } catch (Throwable ignored) { newAppNameText = ""; }
        try { makeDebuggableRequested = binding == null || binding.chkApkEditorMakeDebuggable == null || binding.chkApkEditorMakeDebuggable.isChecked(); } catch (Throwable ignored) { makeDebuggableRequested = true; }
        try { keepWorkspaceRequested = binding == null || binding.chkApkEditorKeepWorkspace == null || binding.chkApkEditorKeepWorkspace.isChecked(); } catch (Throwable ignored) { keepWorkspaceRequested = true; }
        try { quickPatchRequested = binding == null || binding.chkApkEditorQuickPatch == null || binding.chkApkEditorQuickPatch.isChecked(); } catch (Throwable ignored) { quickPatchRequested = true; }
        try { renameAuthoritiesRequested = binding == null || binding.chkApkEditorRenameAuthorities == null || binding.chkApkEditorRenameAuthorities.isChecked(); } catch (Throwable ignored) { renameAuthoritiesRequested = true; }
        try { splitAwareRequested = binding == null || binding.chkApkEditorSplitAware == null || binding.chkApkEditorSplitAware.isChecked(); } catch (Throwable ignored) { splitAwareRequested = true; }
        try { useApktoolRequested = binding == null || binding.chkApkEditorUseApktool == null || binding.chkApkEditorUseApktool.isChecked(); } catch (Throwable ignored) { useApktoolRequested = true; }
    }

    private ToolResult runShell(String command) {
        ToolResult r = host == null ? null : host.runShell(command);
        return r == null ? new ToolResult(1, "", "No shell result.") : r;
    }

    private void appendToolOutput(String title, ToolResult r) {
        StringBuilder out = new StringBuilder();
        out.append(title).append(" exit=").append(r == null ? -1 : r.exitCode).append('\n');
        if (r != null && !TextUtils.isEmpty(r.stdout)) out.append(r.stdout.endsWith("\n") ? r.stdout : (r.stdout + "\n"));
        if (r != null && !TextUtils.isEmpty(r.stderr)) out.append(r.stderr.endsWith("\n") ? r.stderr : (r.stderr + "\n"));
        append(out.toString());
    }

    private void setStatus(String text) {
        if (binding != null && binding.txtApkEditorStatus != null) binding.txtApkEditorStatus.setText(text == null ? "" : text);
    }

    private void setStatusOnUi(String text) {
        if (host == null) return;
        host.runOnUiThread(() -> setStatus(text));
    }

    private void append(String text) {
        if (host != null) host.appendOutput(text == null ? "" : text);
    }

    private void toast(String text) {
        try { Toast.makeText(context, text, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
    }

    private void showTextDialogOnUi(String title, String body, boolean syntaxXml) {
        showTextDialogOnUi(title, null, body, syntaxXml, false);
    }

    private void showTextDialogOnUi(String title, String note, String body, boolean syntaxXml, boolean wrapLines) {
        if (host == null) return;
        host.runOnUiThread(() -> showTextDialog(title, note, body, syntaxXml, wrapLines));
    }

    private void showManifestEditorOnUi(File manifest) {
        if (host == null || manifest == null) return;
        host.runOnUiThread(() -> ApkEditorDialogs.showEditableFile(context, manifest, "AndroidManifest.xml", true, new ApkEditorDialogs.SaveHost() {
            @Override
            public void onSaved(File file) {
                resourceWorkspaceReady = true;
                append("[APK Editor] Manifest saved: " + file.getAbsolutePath() + "\n");
                updateToolButtons("Manifest saved. Rebuild to package the edited manifest.");
            }

            @Override
            public void onError(String label, Throwable error) {
                append("[APK Editor] Manifest " + label + " failed: " + ApkEditorArchiveInspector.shortError(error) + "\n");
                setStatus("Manifest " + label + " failed. Check log.");
            }
        }));
    }

    private void showTextDialog(String title, String note, String body, boolean syntaxXml, boolean wrapLines) {
        try {
            EditText editor = new EditText(context);
            editor.setText(body == null ? "" : body);
            editor.setTextIsSelectable(true);
            editor.setSingleLine(false);
            editor.setHorizontallyScrolling(!wrapLines);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setKeyListener(null);
            editor.setMinLines(10);
            editor.setMaxLines(22);
            int pad = (int) (12f * context.getResources().getDisplayMetrics().density);
            editor.setPadding(pad, pad, pad, pad);
            if (syntaxXml && SourceSyntaxHighlighter.canHighlightLength(editor.length())) {
                SourceSyntaxHighlighter.applyWeb(editor);
            }

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(pad, 0, pad, 0);
            if (!TextUtils.isEmpty(note)) {
                TextView noteView = new TextView(context);
                noteView.setText(note);
                noteView.setTextIsSelectable(true);
                noteView.setTextSize(12);
                noteView.setPadding(0, 0, 0, pad / 2);
                content.addView(noteView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            content.addView(editor, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout wrap = new FrameLayout(context);
            wrap.setPadding(0, 0, 0, 0);
            wrap.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            new AlertDialog.Builder(context)
                    .setTitle(title == null ? "APK Editor" : title)
                    .setView(wrap)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (Throwable t) {
            append("[APK Editor] Dialog failed: " + ApkEditorArchiveInspector.shortError(t) + "\n");
        }
    }

    private static String text(android.widget.TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString().trim();
    }

    private interface Job {
        void run() throws Exception;
    }
}
