package dev.perms.test.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import androidx.core.widget.NestedScrollView;
import android.widget.TextView;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Owns the shared bottom output pane text, truncation, copy, and auto-scroll behavior. */
public final class OutputPaneController {
    public interface State {
        boolean isOutputDisabled();
        boolean shouldTruncateOutput();
    }

    private final Context context;
    private final Handler mainHandler;
    private final int maxChars;
    private final State state;

    private TextView outputView;
    private NestedScrollView scrollView;
    private boolean userPinnedToBottom = true;
    private boolean programmaticScroll;
    private int autoScrollSerial;

    public OutputPaneController(Context context, Handler mainHandler, int maxChars, State state) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.maxChars = Math.max(1024, maxChars);
        this.state = state;
    }

    public void bind(TextView outputView, NestedScrollView scrollView) {
        this.outputView = outputView;
        this.scrollView = scrollView;
        try {
            if (this.scrollView != null) {
                this.scrollView.setOnScrollChangeListener((View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) -> {
                    if (programmaticScroll) return;
                    userPinnedToBottom = isAtBottom();
                });
            }
        } catch (Throwable ignored) {
        }
    }

    public void append(String msg) {
        if (TextUtils.isEmpty(msg) || isDisabled()) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            try {
                mainHandler.post(() -> append(msg));
            } catch (Throwable ignored) {
            }
            return;
        }

        try {
            if (outputView == null) return;
            boolean shouldAutoScroll = userPinnedToBottom || isAtBottom();
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String cur = outputView.getText() == null ? "" : outputView.getText().toString();
            String next = cur + "[" + ts + "] " + msg;
            if (shouldTruncate() && next.length() > maxChars) {
                next = next.substring(next.length() - maxChars);
                int firstNewline = next.indexOf('\n');
                if (firstNewline >= 0 && firstNewline + 1 < next.length()) {
                    next = next.substring(firstNewline + 1);
                }
            }
            outputView.setText(next);
            if (shouldAutoScroll) {
                userPinnedToBottom = true;
                scrollToBottom();
            }
        } catch (Throwable ignored) {
        }
    }

    public void clear() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            try {
                mainHandler.post(this::clear);
            } catch (Throwable ignored) {
            }
            return;
        }
        try {
            if (outputView != null) outputView.setText("");
        } catch (Throwable ignored) {
        }
    }

    public void copyToClipboard() {
        try {
            CharSequence text = outputView == null || outputView.getText() == null
                    ? ""
                    : outputView.getText();
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("shizuku_output", text));
                append("[i] Copied output to clipboard.\n");
            }
        } catch (Throwable t) {
            append("[!] Copy failed: " + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n");
        }
    }

    private boolean isAtBottom() {
        if (scrollView == null) return true;
        try {
            View child = scrollView.getChildCount() > 0 ? scrollView.getChildAt(0) : null;
            if (child == null) return true;
            int diff = child.getBottom() - (scrollView.getHeight() + scrollView.getScrollY());
            return diff <= 24;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void scrollToBottom() {
        if (scrollView == null) return;
        try {
            final int serial = ++autoScrollSerial;
            Runnable r = () -> {
                try {
                    if (serial != autoScrollSerial) return;
                    programmaticScroll = true;
                    View child = scrollView.getChildCount() > 0 ? scrollView.getChildAt(0) : null;
                    int y = 0;
                    if (child != null) {
                        y = Math.max(0, child.getBottom() - scrollView.getHeight());
                    }
                    scrollView.scrollTo(scrollView.getScrollX(), y);
                    userPinnedToBottom = true;
                } catch (Throwable ignored) {
                } finally {
                    try {
                        scrollView.postDelayed(() -> {
                            if (serial == autoScrollSerial) programmaticScroll = false;
                        }, 120L);
                    } catch (Throwable ignored) {
                        programmaticScroll = false;
                    }
                }
            };
            scrollView.post(r);
            scrollView.postDelayed(r, 80L);
        } catch (Throwable ignored) {
        }
    }

    private boolean isDisabled() {
        try {
            return state != null && state.isOutputDisabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldTruncate() {
        try {
            return state == null || state.shouldTruncateOutput();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
