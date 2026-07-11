package dev.perms.test.debugging.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.core.view.ViewCompat;

import dev.perms.test.databinding.TabDebuggingBinding;

/**
 * Handles smali editor view behavior that is independent of the editor actions.
 */
public class SmaliEditorViewController {
    private static final String PREF_SMALI_EDITOR_H = "smali_editor_h";

    private final Context context;
    private final SharedPreferences prefs;
    private final android.os.Handler mainHandler;

    private TabDebuggingBinding binding;
    private boolean syntaxWatcherAttached;
    private boolean applyingSyntax;
    private Runnable syntaxRunnable;
    private SyntaxMode syntaxMode = SyntaxMode.SMALI;

    public enum SyntaxMode {
        SMALI,
        JAVA
    }

    public SmaliEditorViewController(Context context, SharedPreferences prefs, android.os.Handler mainHandler) {
        this.context = context;
        this.prefs = prefs;
        this.mainHandler = mainHandler;
    }

    public void setup(TabDebuggingBinding binding) {
        this.binding = binding;
        if (binding == null) return;
        restoreEditorHeight(binding);
        setupEditorScrolling(binding);
        setupResizeHandle(binding);
        setupSyntaxHighlighting(binding);
    }

    public void setSyntaxMode(SyntaxMode mode) {
        syntaxMode = mode == null ? SyntaxMode.SMALI : mode;
    }

    public void scheduleSyntaxHighlight(boolean immediate) {
        try {
            if (syntaxRunnable != null) mainHandler.removeCallbacks(syntaxRunnable);
            syntaxRunnable = this::applySyntaxHighlight;
            if (immediate) mainHandler.post(syntaxRunnable);
            else mainHandler.postDelayed(syntaxRunnable, 350L);
        } catch (Throwable ignored) {
        }
    }

    public void cancelSyntaxHighlight() {
        try {
            if (syntaxRunnable != null) mainHandler.removeCallbacks(syntaxRunnable);
            syntaxRunnable = null;
        } catch (Throwable ignored) {
        }
    }

    private void restoreEditorHeight(TabDebuggingBinding binding) {
        try {
            if (binding.smaliEditorBodyFrame == null) return;
            int saved = prefs.getInt(PREF_SMALI_EDITOR_H, 0);
            if (saved <= 0) return;
            ViewGroup.LayoutParams lp = binding.smaliEditorBodyFrame.getLayoutParams();
            lp.height = saved;
            binding.smaliEditorBodyFrame.setLayoutParams(lp);
        } catch (Throwable ignored) {
        }
    }

    private void setupEditorScrolling(TabDebuggingBinding binding) {
        try {
            if (binding.edtSmaliEditorBody == null) return;
            binding.edtSmaliEditorBody.setVerticalScrollBarEnabled(false);
            binding.edtSmaliEditorBody.setHorizontalScrollBarEnabled(true);
            binding.edtSmaliEditorBody.setHorizontallyScrolling(true);
            try { ViewCompat.setNestedScrollingEnabled(binding.edtSmaliEditorBody, true); } catch (Throwable ignored) {}

            final float[] lastTouchY = new float[1];
            binding.edtSmaliEditorBody.setOnTouchListener((v, ev) -> {
                try {
                    if (ev == null) return false;
                    final ViewParent parent = binding.getRoot();
                    final int act = ev.getActionMasked();
                    if (parent != null) {
                        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                            parent.requestDisallowInterceptTouchEvent(false);
                        } else if (act == MotionEvent.ACTION_DOWN) {
                            lastTouchY[0] = ev.getY();
                            parent.requestDisallowInterceptTouchEvent(true);
                        } else if (act == MotionEvent.ACTION_MOVE) {
                            float y = ev.getY();
                            float dy = y - lastTouchY[0];
                            lastTouchY[0] = y;
                            int dir = (dy > 0f) ? -1 : 1;
                            parent.requestDisallowInterceptTouchEvent(v.canScrollVertically(dir));
                        }
                    }
                } catch (Throwable ignored) {
                }
                return false;
            });
        } catch (Throwable ignored) {
        }
    }

    private void setupResizeHandle(TabDebuggingBinding binding) {
        try {
            if (binding.viewSmaliEditorResizeHandle == null || binding.smaliEditorBodyFrame == null) return;
            final int minH = dpToPx(140);
            final int maxH = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.85f);
            binding.viewSmaliEditorResizeHandle.setOnTouchListener(new View.OnTouchListener() {
                float startY;
                int startH;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    try {
                        if (event == null) return false;
                        int act = event.getActionMasked();
                        if (act == MotionEvent.ACTION_DOWN) {
                            startY = event.getRawY();
                            startH = binding.smaliEditorBodyFrame.getHeight();
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            return true;
                        } else if (act == MotionEvent.ACTION_MOVE) {
                            int newH = (int) (startH + (event.getRawY() - startY));
                            if (newH < minH) newH = minH;
                            if (newH > maxH) newH = maxH;
                            ViewGroup.LayoutParams lp = binding.smaliEditorBodyFrame.getLayoutParams();
                            lp.height = newH;
                            binding.smaliEditorBodyFrame.setLayoutParams(lp);
                            try { prefs.edit().putInt(PREF_SMALI_EDITOR_H, newH).apply(); } catch (Throwable ignored) {}
                            return true;
                        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                    return false;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void setupSyntaxHighlighting(TabDebuggingBinding binding) {
        try {
            if (syntaxWatcherAttached) return;
            if (binding.edtSmaliEditorBody == null) return;
            syntaxWatcherAttached = true;
            binding.edtSmaliEditorBody.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (applyingSyntax) return;
                    scheduleSyntaxHighlight(false);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void applySyntaxHighlight() {
        if (applyingSyntax) return;
        try {
            if (binding == null || binding.edtSmaliEditorBody == null) return;
            applyingSyntax = true;
            if (syntaxMode == SyntaxMode.JAVA) CodeSyntaxHighlighter.applyJava(binding.edtSmaliEditorBody);
            else CodeSyntaxHighlighter.applySmali(binding.edtSmaliEditorBody);
        } catch (Throwable ignored) {
        } finally {
            applyingSyntax = false;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
