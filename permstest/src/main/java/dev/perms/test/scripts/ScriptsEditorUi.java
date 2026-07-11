package dev.perms.test.scripts;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.text.method.MovementMethod;
import android.text.method.TextKeyListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;

import java.io.File;
import java.nio.charset.StandardCharsets;

import androidx.core.view.ViewCompat;

import dev.perms.test.databinding.ActivityMainBinding;
import dev.perms.test.editor.SourceSyntaxHighlighter;
import dev.perms.test.editor.VirtualTextDocument;
import dev.perms.test.editor.VirtualTextEditorController;

public final class ScriptsEditorUi {
    private static final String PREF_SCRIPT_EDITOR_H = "script_editor_h";

    private final Activity activity;
    private final Handler mainHandler;
    private final String prefsName;

    private KeyListener bodyKeyListener;
    private MovementMethod bodyMovementMethod;
    private boolean editorEditable;
    private boolean syntaxWatcherAttached;
    private boolean syntaxApplying;
    private Runnable syntaxRunnable;
    private VirtualTextEditorController virtualEditor;

    private static final int VIRTUAL_EDITOR_MAX_BYTES = 2 * 1024 * 1024;
    private static final int VIRTUAL_EDITOR_MAX_LINES = 20_000;

    public ScriptsEditorUi(Activity activity, Handler mainHandler, String prefsName) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.prefsName = prefsName;
    }

    public void setupGenericEditor(ActivityMainBinding binding) {
        try {
            if (virtualEditor != null) return;
            if (binding == null || binding.tabScripts == null) return;
            virtualEditor = new VirtualTextEditorController(activity,
                    binding.tabScripts.edtScriptBody,
                    binding.tabScripts.tilScriptBody,
                    binding.tabScripts.rvScriptVirtualLines,
                    binding.tabScripts.fastScrollTouchScript,
                    binding.tabScripts.fastScrollThumbScript,
                    new VirtualTextEditorController.Host() {
                        @Override
                        public void onStatus(String status) {}

                        @Override
                        public void onError(String label, Throwable error) {}
                    });
            virtualEditor.setLineFormatter(SourceSyntaxHighlighter::formatShellLine);
        } catch (Throwable ignored) {}
    }

    public void setText(ActivityMainBinding binding, File sourceFile, String text) {
        try {
            setupGenericEditor(binding);
            String safeText = text == null ? "" : text;
            if (virtualEditor != null && shouldUseVirtualEditor(safeText)) {
                virtualEditor.showDocument(VirtualTextDocument.fromText(sourceFile, safeText), 1);
                if (binding != null && binding.tabScripts != null && binding.tabScripts.btnEditScript != null) {
                    binding.tabScripts.btnEditScript.setText("Line Edit");
                }
                return;
            }
            if (virtualEditor != null) virtualEditor.showText(sourceFile, safeText);
            else if (binding != null && binding.tabScripts != null && binding.tabScripts.edtScriptBody != null) {
                binding.tabScripts.edtScriptBody.setText(safeText);
            }
            try {
                if (binding != null && binding.tabScripts != null && binding.tabScripts.edtScriptBody != null) {
                    binding.tabScripts.edtScriptBody.setSelection(0);
                }
            } catch (Throwable ignored) {}
            scheduleSyntaxHighlight(binding, true);
        } catch (Throwable ignored) {
            try {
                if (binding != null && binding.tabScripts != null && binding.tabScripts.edtScriptBody != null) {
                    binding.tabScripts.edtScriptBody.setText(text == null ? "" : text);
                    binding.tabScripts.edtScriptBody.setSelection(0);
                }
            } catch (Throwable ignored2) {}
        }
    }

    public String getText(ActivityMainBinding binding) {
        try {
            setupGenericEditor(binding);
            if (virtualEditor != null) return virtualEditor.getText();
        } catch (Throwable ignored) {}
        try {
            if (binding != null && binding.tabScripts != null && binding.tabScripts.edtScriptBody != null
                    && binding.tabScripts.edtScriptBody.getText() != null) {
                return binding.tabScripts.edtScriptBody.getText().toString();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public boolean isVirtualMode(ActivityMainBinding binding) {
        try {
            setupGenericEditor(binding);
            return virtualEditor != null && virtualEditor.isVirtualMode();
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean shouldUseVirtualEditor(String text) {
        try {
            if (text == null) return false;
            if (text.getBytes(StandardCharsets.UTF_8).length > VIRTUAL_EDITOR_MAX_BYTES) return true;
            int lines = 1;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n' && ++lines > VIRTUAL_EDITOR_MAX_LINES) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean isEditable() {
        return editorEditable;
    }

    public void toggleEditable(ActivityMainBinding binding) {
        setEditable(binding, !editorEditable);
    }

    public void setEditable(ActivityMainBinding binding, boolean editable) {
        editorEditable = editable;
        try {
            setupGenericEditor(binding);
            if (binding == null || binding.tabScripts == null || binding.tabScripts.edtScriptBody == null) return;
            if (virtualEditor != null && virtualEditor.isVirtualMode()) {
                if (binding.tabScripts.btnEditScript != null) binding.tabScripts.btnEditScript.setText("Line Edit");
                return;
            }

            if (bodyKeyListener == null) {
                try { bodyKeyListener = binding.tabScripts.edtScriptBody.getKeyListener(); } catch (Throwable ignored) {}
            }

            if (editable) {
                try { binding.tabScripts.edtScriptBody.setTextIsSelectable(false); } catch (Throwable ignored) {}

                KeyListener keyListener = bodyKeyListener;
                if (keyListener == null) {
                    keyListener = TextKeyListener.getInstance();
                }
                binding.tabScripts.edtScriptBody.setKeyListener(keyListener);

                binding.tabScripts.edtScriptBody.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

                binding.tabScripts.edtScriptBody.setSingleLine(false);
                binding.tabScripts.edtScriptBody.setHorizontallyScrolling(false);
                binding.tabScripts.edtScriptBody.setFocusable(true);
                binding.tabScripts.edtScriptBody.setFocusableInTouchMode(true);
                binding.tabScripts.edtScriptBody.setCursorVisible(true);
                binding.tabScripts.edtScriptBody.setLongClickable(true);
                binding.tabScripts.edtScriptBody.setTextIsSelectable(false);

                try {
                    binding.tabScripts.edtScriptBody.requestFocus();
                    binding.tabScripts.edtScriptBody.post(() -> {
                        try {
                            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) imm.showSoftInput(binding.tabScripts.edtScriptBody, InputMethodManager.SHOW_IMPLICIT);
                        } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}

                if (binding.tabScripts.btnEditScript != null) binding.tabScripts.btnEditScript.setText("Lock");
            } else {
                try {
                    KeyListener current = binding.tabScripts.edtScriptBody.getKeyListener();
                    if (current != null) bodyKeyListener = current;
                } catch (Throwable ignored) {}

                binding.tabScripts.edtScriptBody.setKeyListener(null);
                binding.tabScripts.edtScriptBody.setCursorVisible(false);
                binding.tabScripts.edtScriptBody.setFocusable(true);
                binding.tabScripts.edtScriptBody.setFocusableInTouchMode(true);
                binding.tabScripts.edtScriptBody.setLongClickable(true);
                binding.tabScripts.edtScriptBody.setTextIsSelectable(true);

                try {
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(binding.tabScripts.edtScriptBody.getWindowToken(), 0);
                } catch (Throwable ignored) {}

                if (binding.tabScripts.btnEditScript != null) binding.tabScripts.btnEditScript.setText("Edit");
            }
        } catch (Throwable ignored) {
        }
    }

    public void setupScrollingAndResize(ActivityMainBinding binding) {
        try {
            if (binding == null || binding.tabScripts == null) return;
            setupGenericEditor(binding);

            try {
                if (binding.tabScripts.scriptBodyFrame != null) {
                    SharedPreferences sp = activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                    int saved = sp.getInt(PREF_SCRIPT_EDITOR_H, 0);
                    if (saved > 0) {
                        ViewGroup.LayoutParams lp = binding.tabScripts.scriptBodyFrame.getLayoutParams();
                        lp.height = saved;
                        binding.tabScripts.scriptBodyFrame.setLayoutParams(lp);
                    }
                }
            } catch (Throwable ignored) {}

            try {
                if (binding.tabScripts.edtScriptBody != null) {
                    if (bodyMovementMethod == null) bodyMovementMethod = binding.tabScripts.edtScriptBody.getMovementMethod();
                    binding.tabScripts.edtScriptBody.setVerticalScrollBarEnabled(false);
                    try { ViewCompat.setNestedScrollingEnabled(binding.tabScripts.edtScriptBody, true); } catch (Throwable ignored2) {}

                    final float[] lastTouchY = new float[1];
                    binding.tabScripts.edtScriptBody.setOnTouchListener((v, ev) -> {
                        try {
                            if (ev == null) return false;
                            final ViewParent parent = binding.tabScripts.getRoot();
                            final int action = ev.getActionMasked();
                            if (parent != null) {
                                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                                    parent.requestDisallowInterceptTouchEvent(false);
                                } else if (action == MotionEvent.ACTION_DOWN) {
                                    lastTouchY[0] = ev.getY();
                                    parent.requestDisallowInterceptTouchEvent(true);
                                } else if (action == MotionEvent.ACTION_MOVE) {
                                    float y = ev.getY();
                                    float dy = y - lastTouchY[0];
                                    lastTouchY[0] = y;
                                    int dir = (dy > 0f) ? -1 : 1;
                                    parent.requestDisallowInterceptTouchEvent(v.canScrollVertically(dir));
                                }
                            }
                        } catch (Throwable ignored) {}
                        return false;
                    });
                }
            } catch (Throwable ignored) {}

            try {
                if (binding.tabScripts.viewScriptResizeHandle != null && binding.tabScripts.scriptBodyFrame != null) {
                    final int minH = dpToPx(120);
                    final int maxH = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.85f);
                    binding.tabScripts.viewScriptResizeHandle.setOnTouchListener(new View.OnTouchListener() {
                        float startY;
                        int startH;

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            try {
                                if (event == null) return false;
                                int action = event.getActionMasked();
                                if (action == MotionEvent.ACTION_DOWN) {
                                    startY = event.getRawY();
                                    startH = binding.tabScripts.scriptBodyFrame.getHeight();
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                    return true;
                                } else if (action == MotionEvent.ACTION_MOVE) {
                                    float dy = event.getRawY() - startY;
                                    int newH = (int) (startH + dy);
                                    if (newH < minH) newH = minH;
                                    if (newH > maxH) newH = maxH;
                                    ViewGroup.LayoutParams lp = binding.tabScripts.scriptBodyFrame.getLayoutParams();
                                    lp.height = newH;
                                    binding.tabScripts.scriptBodyFrame.setLayoutParams(lp);
                                    try { activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().putInt(PREF_SCRIPT_EDITOR_H, newH).apply(); } catch (Throwable ignored2) {}
                                    return true;
                                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                                    v.getParent().requestDisallowInterceptTouchEvent(false);
                                    return true;
                                }
                            } catch (Throwable ignored) {}
                            return false;
                        }
                    });
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    public void setupSyntaxHighlighting(ActivityMainBinding binding) {
        try {
            setupGenericEditor(binding);
            if (syntaxWatcherAttached) return;
            if (binding == null || binding.tabScripts == null || binding.tabScripts.edtScriptBody == null) return;
            if (virtualEditor != null && virtualEditor.isVirtualMode()) return;
            syntaxWatcherAttached = true;
            binding.tabScripts.edtScriptBody.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (syntaxApplying) return;
                    scheduleSyntaxHighlight(binding, false);
                }
            });
            scheduleSyntaxHighlight(binding, true);
        } catch (Throwable ignored) {}
    }

    private void scheduleSyntaxHighlight(ActivityMainBinding binding, boolean immediate) {
        try {
            if (syntaxRunnable != null) mainHandler.removeCallbacks(syntaxRunnable);
            syntaxRunnable = () -> applySyntaxHighlight(binding);
            if (immediate) mainHandler.post(syntaxRunnable);
            else mainHandler.postDelayed(syntaxRunnable, 350L);
        } catch (Throwable ignored) {}
    }

    private void applySyntaxHighlight(ActivityMainBinding binding) {
        if (syntaxApplying) return;
        try {
            if (binding == null || binding.tabScripts == null || binding.tabScripts.edtScriptBody == null) return;
            syntaxApplying = true;
            SourceSyntaxHighlighter.applyShell(binding.tabScripts.edtScriptBody);
        } catch (Throwable ignored) {
        } finally {
            syntaxApplying = false;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
