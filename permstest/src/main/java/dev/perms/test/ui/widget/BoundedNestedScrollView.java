package dev.perms.test.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.ScrollView;

/**
 * Fixed-height inner ScrollView for long option lists inside a parent scrolling tab.
 */
public final class BoundedNestedScrollView extends ScrollView {
    private float lastY;

    public BoundedNestedScrollView(Context context) {
        super(context);
        init();
    }

    public BoundedNestedScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BoundedNestedScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setVerticalScrollBarEnabled(true);
        setScrollbarFadingEnabled(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        updateParentIntercept(event);
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateParentIntercept(event);
        boolean handled = super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            requestParentDisallowIntercept(false);
        }
        return handled;
    }

    private void updateParentIntercept(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lastY = event.getY();
            requestParentDisallowIntercept(canScrollVertically(-1) || canScrollVertically(1));
            return;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            float y = event.getY();
            float dy = y - lastY;
            lastY = y;

            boolean canScrollInDragDirection;
            if (dy < 0f) {
                canScrollInDragDirection = canScrollVertically(1);
            } else if (dy > 0f) {
                canScrollInDragDirection = canScrollVertically(-1);
            } else {
                canScrollInDragDirection = canScrollVertically(-1) || canScrollVertically(1);
            }
            requestParentDisallowIntercept(canScrollInDragDirection);
            return;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            requestParentDisallowIntercept(false);
        }
    }

    private void requestParentDisallowIntercept(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }
}
