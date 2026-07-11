package dev.perms.test.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

public final class FastScrollOverlay {
    private static final float TRACK_EDGE_INSET_DP = 4f;

    private FastScrollOverlay() {
    }

    public static void attach(final View scrollable, final View touch, final View thumb) {
        try {
            if (scrollable == null || touch == null || thumb == null) return;

            final Object bindingToken = new Object();
            try { touch.setTag(bindingToken); } catch (Throwable ignored) {}
            try { touch.setClickable(true); } catch (Throwable ignored) {}
            try { thumb.setClickable(true); } catch (Throwable ignored) {}

            final ScrollMetrics metrics = new ScrollMetrics();

            final Runnable updateThumb = () -> {
                try {
                    if (!isCurrentBinding(touch, bindingToken)) return;

                    int h = touch.getHeight();
                    if (h <= 0) {
                        try {
                            if (touch.getLayoutParams() != null && touch.getLayoutParams().height > 0) {
                                h = touch.getLayoutParams().height;
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (h <= 0) return;

                    computeScrollMetricsSafe(scrollable, metrics);
                    if (!metrics.ready) return;

                    int max = Math.max(0, metrics.range - metrics.extent);
                    if (max <= 0) {
                        thumb.setVisibility(View.GONE);
                        touch.setVisibility(View.INVISIBLE);
                        return;
                    }

                    if (touch.getVisibility() != View.VISIBLE) touch.setVisibility(View.VISIBLE);
                    if (thumb.getVisibility() != View.VISIBLE) thumb.setVisibility(View.VISIBLE);

                    int th = thumb.getHeight();
                    if (th <= 0) {
                        try {
                            if (thumb.getLayoutParams() != null && thumb.getLayoutParams().height > 0) {
                                th = thumb.getLayoutParams().height;
                            }
                        } catch (Throwable ignored) {}
                    }
                    int inset = trackInsetPx(touch);
                    int usableH = Math.max(0, h - (inset * 2));
                    if (usableH > 0 && th > usableH) th = usableH;
                    int trackH = Math.max(0, h - th - (inset * 2));

                    float frac = (max == 0) ? 0f : (metrics.offset / (float) max);
                    if (frac < 0f) frac = 0f;
                    if (frac > 1f) frac = 1f;

                    thumb.setTranslationY(inset + (frac * trackH));
                } catch (Throwable ignored) {
                }
            };

            final float[] grabOffset = new float[] { 0f };
            final View.OnTouchListener dragListener = (v, ev) -> {
                try {
                    if (!isCurrentBinding(touch, bindingToken)) return false;
                    if (ev == null) return false;
                    int act = ev.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
                        requestParentsDisallowIntercept(v, true);

                        int h = measuredHeight(touch);
                        int th = measuredHeight(thumb);
                        if (h <= 0 || th <= 0) return true;

                        computeScrollMetricsSafe(scrollable, metrics);
                        if (!metrics.ready) return true;
                        int max = Math.max(0, metrics.range - metrics.extent);
                        if (max <= 0) return true;

                        int inset = trackInsetPx(touch);
                        int usableH = Math.max(0, h - (inset * 2));
                        if (usableH > 0 && th > usableH) th = usableH;
                        int trackH = Math.max(0, h - th - (inset * 2));
                        if (trackH <= 0) return true;

                        if (act == MotionEvent.ACTION_DOWN) {
                            grabOffset[0] = (v == thumb) ? ev.getY() : (th / 2f);
                        }

                        float trackY;
                        if (v == thumb) {
                            trackY = thumb.getTranslationY() - inset + ev.getY() - grabOffset[0];
                        } else {
                            trackY = ev.getY() - grabOffset[0] - inset;
                        }
                        if (trackY < 0f) trackY = 0f;
                        if (trackY > trackH) trackY = trackH;

                        float frac = trackY / (float) trackH;
                        int target = (int) (frac * max + 0.5f);

                        scrollToVertical(scrollable, target, metrics.offset);
                        thumb.setTranslationY(inset + trackY);
                        return true;
                    }
                    if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                        requestParentsDisallowIntercept(v, false);
                        try { v.performClick(); } catch (Throwable ignored) {}
                        return true;
                    }
                } catch (Throwable ignored) {
                }
                return false;
            };

            try { touch.setOnTouchListener(dragListener); } catch (Throwable ignored) {}
            try { thumb.setOnTouchListener(dragListener); } catch (Throwable ignored) {}
            bindScrollableUpdates(scrollable, touch, bindingToken, updateThumb);
            try { touch.addOnLayoutChangeListener((v, l, t, r, btm, ol, ot, orr, ob) -> postRefresh(touch, scrollable, updateThumb)); } catch (Throwable ignored) {}
            try { thumb.addOnLayoutChangeListener((v, l, t, r, btm, ol, ot, orr, ob) -> postRefresh(touch, scrollable, updateThumb)); } catch (Throwable ignored) {}

            installInitialLayoutSync(scrollable, touch, thumb, metrics, updateThumb, bindingToken);
            postRefresh(touch, scrollable, updateThumb);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isCurrentBinding(View touch, Object bindingToken) {
        try { return touch != null && touch.getTag() == bindingToken; } catch (Throwable ignored) { return false; }
    }

    private static void bindScrollableUpdates(final View scrollable,
                                              final View touch,
                                              final Object bindingToken,
                                              final Runnable updateThumb) {
        try { scrollable.getViewTreeObserver().addOnScrollChangedListener(() -> updateIfCurrent(touch, bindingToken, updateThumb)); } catch (Throwable ignored) {}
        try { scrollable.addOnLayoutChangeListener((v, l, t, r, btm, ol, ot, orr, ob) -> postRefresh(touch, scrollable, updateThumb)); } catch (Throwable ignored) {}
        try {
            if (scrollable instanceof android.widget.TextView) {
                ((android.widget.TextView) scrollable).addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        postRefresh(touch, scrollable, updateThumb);
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void updateIfCurrent(View touch, Object bindingToken, Runnable updateThumb) {
        try {
            if (isCurrentBinding(touch, bindingToken)) updateThumb.run();
        } catch (Throwable ignored) {
        }
    }

    private static void postRefresh(View touch, View scrollable, Runnable updateThumb) {
        try { if (touch != null) touch.post(updateThumb); } catch (Throwable ignored) {}
        try { if (scrollable != null) scrollable.post(updateThumb); } catch (Throwable ignored) {}
        try { if (touch != null) touch.postDelayed(updateThumb, 50L); } catch (Throwable ignored) {}
        try { if (scrollable != null) scrollable.postDelayed(updateThumb, 150L); } catch (Throwable ignored) {}
    }


    private static int trackInsetPx(View view) {
        try {
            float density = view.getResources().getDisplayMetrics().density;
            return Math.max(0, (int) ((TRACK_EDGE_INSET_DP * density) + 0.5f));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int measuredHeight(View view) {
        int h = 0;
        try { h = view.getHeight(); } catch (Throwable ignored) {}
        if (h <= 0) {
            try {
                if (view.getLayoutParams() != null && view.getLayoutParams().height > 0) {
                    h = view.getLayoutParams().height;
                }
            } catch (Throwable ignored) {}
        }
        return h;
    }

    private static void requestParentsDisallowIntercept(View view, boolean disallow) {
        try {
            ViewParent p = view.getParent();
            while (p != null) {
                p.requestDisallowInterceptTouchEvent(disallow);
                if (!(p instanceof View)) break;
                p = ((View) p).getParent();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void scrollToVertical(View scrollable, int target, int currentOffset) {
        try {
            if (scrollable instanceof androidx.recyclerview.widget.RecyclerView) {
                ((androidx.recyclerview.widget.RecyclerView) scrollable).scrollBy(0, target - currentOffset);
                return;
            }
            scrollable.scrollTo(scrollable.getScrollX(), target);
        } catch (Throwable ignored) {
            try { scrollable.scrollTo(0, target); } catch (Throwable ignored2) {}
        }
    }

    private static void installInitialLayoutSync(
            final View scrollable,
            final View touch,
            final View thumb,
            final ScrollMetrics metrics,
            final Runnable updateThumb,
            final Object bindingToken) {
        try {
            final android.view.ViewTreeObserver vto = touch.getViewTreeObserver();
            final int[] globalLayoutPasses = new int[] { 0 };
            final android.view.ViewTreeObserver.OnGlobalLayoutListener gl = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    try { if (!isCurrentBinding(touch, bindingToken)) return; } catch (Throwable ignored) { return; }
                    try { updateThumb.run(); } catch (Throwable ignored) {}
                    try {
                        if (!scrollable.isShown()) return;
                        if (touch.getHeight() <= 0 || thumb.getHeight() <= 0) return;

                        computeScrollMetricsSafe(scrollable, metrics);
                        if (!metrics.ready) return;

                        globalLayoutPasses[0]++;
                        if (globalLayoutPasses[0] >= 120) {
                            removeGlobalLayoutListener(vto, this);
                            return;
                        }

                        postRefresh(touch, scrollable, updateThumb);
                        removeGlobalLayoutListener(vto, this);
                    } catch (Throwable ignored) {
                    }
                }
            };
            vto.addOnGlobalLayoutListener(gl);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private static void removeGlobalLayoutListener(android.view.ViewTreeObserver vto,
                                                   android.view.ViewTreeObserver.OnGlobalLayoutListener listener) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 16) vto.removeOnGlobalLayoutListener(listener);
            else vto.removeGlobalOnLayoutListener(listener);
        } catch (Throwable ignored) {
        }
    }

    private static void computeScrollMetricsSafe(final View scrollable, final ScrollMetrics metrics) {
        metrics.ready = true;
        metrics.extent = (scrollable != null) ? scrollable.getHeight() : 0;
        metrics.offset = (scrollable != null) ? scrollable.getScrollY() : 0;
        metrics.range = metrics.extent;

        try {
            if (scrollable == null) return;

            if (scrollable instanceof androidx.recyclerview.widget.RecyclerView) {
                androidx.recyclerview.widget.RecyclerView rv = (androidx.recyclerview.widget.RecyclerView) scrollable;
                metrics.extent = rv.computeVerticalScrollExtent();
                metrics.offset = rv.computeVerticalScrollOffset();
                metrics.range = rv.computeVerticalScrollRange();
                if (metrics.extent <= 0) metrics.extent = rv.getHeight();
                if (metrics.range <= 0) metrics.range = metrics.extent;
                return;
            }

            if (scrollable instanceof androidx.core.widget.NestedScrollView) {
                View child = ((androidx.core.widget.NestedScrollView) scrollable).getChildAt(0);
                if (child != null && child.getHeight() > 0) {
                    metrics.range = child.getHeight();
                } else {
                    metrics.ready = false;
                    metrics.range = scrollable.getHeight();
                }
                return;
            }
            if (scrollable instanceof android.widget.ScrollView) {
                View child = ((android.widget.ScrollView) scrollable).getChildAt(0);
                if (child != null && child.getHeight() > 0) {
                    metrics.range = child.getHeight();
                } else {
                    metrics.ready = false;
                    metrics.range = scrollable.getHeight();
                }
                return;
            }

            if (scrollable instanceof android.widget.TextView) {
                android.widget.TextView tv = (android.widget.TextView) scrollable;
                android.text.Layout layout = tv.getLayout();
                if (layout != null) {
                    int lineCount = 0;
                    try { lineCount = tv.getLineCount(); } catch (Throwable ignored) {}
                    int contentHeight;
                    try {
                        contentHeight = (lineCount > 0) ? layout.getLineTop(lineCount) : layout.getHeight();
                    } catch (Throwable ignored) {
                        contentHeight = layout.getHeight();
                    }
                    metrics.range = contentHeight + tv.getTotalPaddingTop() + tv.getTotalPaddingBottom();
                    metrics.range = Math.max(metrics.range, tv.getHeight());
                } else {
                    metrics.ready = false;
                    metrics.range = tv.getHeight();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class ScrollMetrics {
        boolean ready;
        int range;
        int extent;
        int offset;
    }
}
