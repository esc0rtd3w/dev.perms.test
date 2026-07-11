package dev.perms.test.debugging;

import android.os.Build;
import android.text.TextUtils;
import android.widget.TextView;

import dev.perms.test.databinding.ActivityMainBinding;

public final class DebuggingUi {
    private DebuggingUi() {
    }

    public static int parseApiLevel(ActivityMainBinding binding) {
        try {
            String text = textOf(binding == null || binding.tabDebugging == null ? null : binding.tabDebugging.edtSmaliApiLevel);
            int value = Integer.parseInt(text);
            return Math.max(1, Math.min(100, value));
        } catch (Throwable ignored) {
            return Math.max(1, Build.VERSION.SDK_INT);
        }
    }

    public static void setBusy(ActivityMainBinding binding, boolean busy, String status) {
        try {
            if (binding == null || binding.tabDebugging == null) return;
            binding.tabDebugging.btnSmaliDisassemble.setEnabled(!busy);
            binding.tabDebugging.btnSmaliAssemble.setEnabled(!busy);
            binding.tabDebugging.btnSmaliReassembleApk.setEnabled(!busy);
            binding.tabDebugging.btnSmaliListClasses.setEnabled(!busy);
            binding.tabDebugging.btnSmaliDisassembleAll.setEnabled(!busy);
            binding.tabDebugging.btnSmaliReassembleAllDex.setEnabled(!busy);
            binding.tabDebugging.btnSmaliDefaults.setEnabled(!busy);
            binding.tabDebugging.btnSmaliBrowseApk.setEnabled(!busy);
            binding.tabDebugging.btnDebuggingRefreshInstalled.setEnabled(!busy);
            binding.tabDebugging.btnDebuggingUseInstalled.setEnabled(!busy);
            try { binding.tabDebugging.btnSmaliEditorSearch.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnSmaliEditorBrowse.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnSmaliEditorOpen.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnSmaliEditorSave.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnSmaliEditorReload.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnSmaliEditorOpenExternal.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.chkSmaliEditorAutoOpenSelected.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnJadxDecompile.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnJadxOpenFirstJava.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.chkJadxJavaInnerNames.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.chkJadxZipOutput.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.chkJadxSelectedDexOnly.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnMitmApplyPatch.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnMitmTrustUserCas.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnMitmAllowCleartext.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnMitmPinningReport.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnMitmPatchPinning.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnTemplateMakeDebuggable.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnTemplateAllowBackup.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnTemplateActivityReport.setEnabled(!busy); } catch (Throwable ignored) {}
            try { binding.tabDebugging.btnTemplateSecureFlagReport.setEnabled(!busy); } catch (Throwable ignored) {}
            if (status != null && binding.tabDebugging.txtSmaliStatus != null) {
                binding.tabDebugging.txtSmaliStatus.setText(status);
            }
            try {
                if (binding.tabDebugging.progressDebuggingStatus != null) {
                    binding.tabDebugging.progressDebuggingStatus.setVisibility(busy ? android.view.View.VISIBLE : android.view.View.GONE);
                    binding.tabDebugging.progressDebuggingStatus.setRunning(busy);
                    binding.tabDebugging.progressDebuggingStatus.setIndeterminate(true);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    public static void setMitmPatchStatus(ActivityMainBinding binding, String status) {
        try {
            if (binding != null && binding.tabDebugging != null && binding.tabDebugging.txtMitmPatchStatus != null) {
                binding.tabDebugging.txtMitmPatchStatus.setText(status == null ? "" : status);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String trimShellError(int exitCode, String stdout, String stderr) {
        String msg = TextUtils.isEmpty(stderr) ? stdout : stderr;
        if (msg == null) msg = "";
        msg = msg.trim();
        if (msg.length() > 1200) msg = msg.substring(0, 1200) + "...";
        if (TextUtils.isEmpty(msg)) msg = "exit " + exitCode;
        return msg;
    }

    private static String textOf(TextView view) {
        if (view == null || view.getText() == null) return "";
        return view.getText().toString().trim();
    }
}
