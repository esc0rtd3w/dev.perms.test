package dev.perms.test.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Shared dropdown helpers for Material/AppCompat selectors.
 */
public final class DropdownUi {
    public interface ListTweaker {
        void apply(ListView listView);
    }

    private DropdownUi() {
    }

    public static int findAdapterIndexByText(ArrayAdapter<?> adapter, String text) {
        if (adapter == null || TextUtils.isEmpty(text)) return -1;
        try {
            final int n = adapter.getCount();
            for (int i = 0; i < n; i++) {
                Object it = adapter.getItem(i);
                if (it == null) continue;
                String s = it.toString();
                if (text.equals(s)) return i;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static void prepareExposedDropdown(TextInputLayout til, AutoCompleteTextView tv) {
        if (tv == null) return;
        try {
            tv.setThreshold(0);
            tv.setInputType(InputType.TYPE_NULL);
            tv.setShowSoftInputOnFocus(false);
            tv.setCursorVisible(false);
            tv.setSingleLine(true);
            tv.setHorizontallyScrolling(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);

            int horizontal = tv.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
            if (horizontal == 0) horizontal = Gravity.START;
            tv.setGravity(horizontal | Gravity.CENTER_VERTICAL);

            int surface = MaterialColors.getColor(tv,
                    com.google.android.material.R.attr.colorSurface);
            tv.setDropDownBackgroundDrawable(new ColorDrawable(surface));
        } catch (Throwable ignored) {
        }

        try {
            if (til != null) {
                til.setExpandedHintEnabled(false);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void bindExposedDropdown(Context context,
                                           TextInputLayout til,
                                           final AutoCompleteTextView tv,
                                           final Runnable onShow) {
        prepareExposedDropdown(til, tv);
        bindPreparedExposedDropdown(context, til, tv, onShow);
    }

    private static void bindPreparedExposedDropdown(Context context,
                                                    TextInputLayout til,
                                                    final AutoCompleteTextView tv,
                                                    final Runnable onShow) {
        if (tv == null) return;

        final Runnable open = () -> {
            if (onShow != null) onShow.run();
            else showDropdown(tv);
        };

        configureSafeDropdownEndIcon(context, til, open);
        tv.setOnClickListener(v -> open.run());
        tv.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) open.run();
        });
    }


    public static void bindTapOnlyExposedDropdown(Context context,
                                                 TextInputLayout til,
                                                 final AutoCompleteTextView tv,
                                                 final int slopPx,
                                                 final int maxTapMs,
                                                 final Runnable onShow) {
        prepareExposedDropdown(til, tv);
        if (tv == null) return;

        final Runnable open = () -> {
            if (onShow != null) onShow.run();
            else showDropdown(tv);
        };

        configureSafeDropdownEndIcon(context, til, open);
        configureTapOnlyDropdownField(tv, slopPx, maxTapMs, open);
        tv.setOnFocusChangeListener((v, hasFocus) -> { /* tap-only dropdown; do not open on focus */ });
    }

    public static void bindClickOnlyExposedDropdown(Context context,
                                                   TextInputLayout til,
                                                   final AutoCompleteTextView tv,
                                                   final Runnable onShow) {
        prepareExposedDropdown(til, tv);
        if (tv == null) return;

        final Runnable open = () -> {
            if (onShow != null) onShow.run();
            else showDropdown(tv);
        };

        configureSafeDropdownEndIcon(context, til, open);
        configureClickOnlyDropdownField(tv, open);
        tv.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) open.run();
        });
    }

    public static void showDropdown(AutoCompleteTextView tv) {
        showDropdown(tv, null);
    }

    public static void showDropdown(AutoCompleteTextView tv, ListTweaker listTweaker) {
        if (tv == null) return;
        String lastText = tv.getText() == null ? null : tv.getText().toString();
        showDropdownAtLastSelection(tv, lastText, listTweaker);
    }

    public static void showDropdownAtLastSelection(AutoCompleteTextView tv,
                                                   String lastText,
                                                   ListTweaker listTweaker) {
        if (tv == null) return;
        try {
            tv.showDropDown();
        } catch (Throwable ignored) {
        }

        ArrayAdapter<?> adapter;
        try {
            adapter = (tv.getAdapter() instanceof ArrayAdapter) ? (ArrayAdapter<?>) tv.getAdapter() : null;
        } catch (Throwable t) {
            adapter = null;
        }

        final int pos = findAdapterIndexByText(adapter, lastText);

        tv.post(() -> {
            tryApplyDropdownTweaks(tv, listTweaker);
            if (pos < 0) return;
            try {
                tv.setListSelection(pos);
            } catch (Throwable ignored) {
            }
            tryScrollDropdownList(tv, pos, listTweaker);
        });
    }

    public static void configureSafeDropdownEndIcon(Context context,
                                                    TextInputLayout til,
                                                    final Runnable onClick) {
        if (context == null || til == null || onClick == null) return;
        try {
            til.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            til.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_dropdown_arrow);
            til.setEndIconOnClickListener(v -> onClick.run());

            final View endIcon = til.findViewById(com.google.android.material.R.id.text_input_end_icon);
            if (endIcon != null) {
                endIcon.setOnTouchListener(new View.OnTouchListener() {
                    final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
                    float downX, downY;
                    boolean isTap;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                downX = event.getX();
                                downY = event.getY();
                                isTap = true;
                                v.getParent().requestDisallowInterceptTouchEvent(false);
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                if (isTap) {
                                    float dx = Math.abs(event.getX() - downX);
                                    float dy = Math.abs(event.getY() - downY);
                                    if (dx > slop || dy > slop) isTap = false;
                                }
                                v.getParent().requestDisallowInterceptTouchEvent(false);
                                return true;
                            case MotionEvent.ACTION_UP:
                                if (isTap) v.performClick();
                                return true;
                            case MotionEvent.ACTION_CANCEL:
                                isTap = false;
                                return true;
                            default:
                                return true;
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    public static void configureTapOnlyDropdownField(final AutoCompleteTextView tv,
                                                     final int slopPx,
                                                     final int maxTapMs,
                                                     final Runnable onTap) {
        if (tv == null || onTap == null) return;

        final java.util.concurrent.atomic.AtomicBoolean suppressNextClick = new java.util.concurrent.atomic.AtomicBoolean(false);

        tv.setOnClickListener(v -> {
            if (suppressNextClick.getAndSet(false)) return;
            onTap.run();
        });

        tv.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            long downTime;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        downTime = SystemClock.uptimeMillis();
                        moved = false;
                        suppressNextClick.set(false);
                        return false;

                    case MotionEvent.ACTION_MOVE: {
                        if (!moved) {
                            float dx = Math.abs(event.getX() - downX);
                            float dy = Math.abs(event.getY() - downY);
                            if (dx > slopPx || dy > slopPx) {
                                moved = true;
                                suppressNextClick.set(true);
                            }
                        }
                        return false;
                    }

                    case MotionEvent.ACTION_UP: {
                        long dt = SystemClock.uptimeMillis() - downTime;
                        int tapLimit = (maxTapMs <= 0) ? Integer.MAX_VALUE : maxTapMs;

                        if (!moved && dt <= tapLimit) {
                            try { v.performClick(); } catch (Throwable ignored) {}
                        } else {
                            suppressNextClick.set(true);
                        }

                        return true;
                    }

                    case MotionEvent.ACTION_CANCEL:
                    default:
                        suppressNextClick.set(true);
                        return false;
                }
            }
        });
    }

    public static void configureClickOnlyDropdownField(final AutoCompleteTextView tv, final Runnable onClick) {
        if (tv == null) return;

        try {
            tv.setCursorVisible(false);
            tv.setLongClickable(false);
            tv.setTextIsSelectable(false);
            tv.setKeyListener(null);
            tv.setInputType(InputType.TYPE_NULL);
        } catch (Throwable ignored) {}

        tv.setOnTouchListener(null);
        tv.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
    }

    public static void tryApplyDropdownTweaks(AutoCompleteTextView tv, ListTweaker listTweaker) {
        try {
            ListView lv = getPopupListView(tv);
            if (lv != null && listTweaker != null) listTweaker.apply(lv);
        } catch (Throwable ignored) {
        }
    }

    public static void tryScrollDropdownList(AutoCompleteTextView tv, int position, ListTweaker listTweaker) {
        try {
            ListView listView = getPopupListView(tv);
            if (listView == null) return;
            if (listTweaker != null) listTweaker.apply(listView);
            listView.setSelection(position);
        } catch (Throwable ignored) {
        }
    }

    public static ListView getPopupListView(AutoCompleteTextView tv) {
        try {
            if (tv == null) return null;
            java.lang.reflect.Field f = AutoCompleteTextView.class.getDeclaredField("mPopup");
            f.setAccessible(true);
            Object popup = f.get(tv);
            if (popup == null) return null;
            java.lang.reflect.Method m = popup.getClass().getMethod("getListView");
            Object lv = m.invoke(popup);
            return (lv instanceof ListView) ? (ListView) lv : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void applyListTweaks(ListView lv, boolean fat, int fatScrollBarPx, int normalScrollBarPx) {
        try {
            if (lv == null) return;
            lv.setVerticalScrollBarEnabled(true);
            try { lv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY); } catch (Throwable ignored) {}

            if (fat) {
                lv.setScrollbarFadingEnabled(false);
                try { lv.setFastScrollEnabled(true); } catch (Throwable ignored) {}
                try { lv.setFastScrollAlwaysVisible(true); } catch (Throwable ignored) {}
                try { lv.setScrollBarSize(fatScrollBarPx); } catch (Throwable ignored) {}
            } else {
                try { lv.setFastScrollAlwaysVisible(false); } catch (Throwable ignored) {}
                try { lv.setFastScrollEnabled(false); } catch (Throwable ignored) {}
                lv.setScrollbarFadingEnabled(true);
                try { lv.setScrollBarSize(normalScrollBarPx); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

}
