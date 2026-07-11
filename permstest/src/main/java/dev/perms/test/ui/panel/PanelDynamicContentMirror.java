package dev.perms.test.ui.panel;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Mirrors controller-generated child views into a detached panel clone without moving the live
 * source views. Mirrored controls proxy user actions back to their source views.
 */
final class PanelDynamicContentMirror {
    private final Context context;
    private final Map<View, View> sourceByMirror = new IdentityHashMap<>();
    private boolean syncing;

    PanelDynamicContentMirror(Context context) {
        this.context = context;
    }

    void sync(ViewGroup source, ViewGroup mirror) {
        if (source == null || mirror == null) return;
        syncing = true;
        try {
            syncGroup(source, mirror, source, mirror);
        } finally {
            syncing = false;
        }
    }

    void clear(ViewGroup mirror) {
        if (mirror == null) return;
        removeMappings(mirror, false);
        try {
            mirror.removeAllViews();
        } catch (Throwable ignored) {
        }
    }

    private void syncGroup(ViewGroup source,
                           ViewGroup mirror,
                           ViewGroup rootSource,
                           ViewGroup rootMirror) {
        copyMutableState(source, mirror);
        if (needsRebuild(source, mirror)) {
            rebuildChildren(source, mirror, rootSource, rootMirror);
            return;
        }
        for (int i = 0; i < source.getChildCount(); i++) {
            View sourceChild = source.getChildAt(i);
            View mirrorChild = mirror.getChildAt(i);
            copyMutableState(sourceChild, mirrorChild);
            if (sourceChild instanceof ViewGroup && mirrorChild instanceof ViewGroup) {
                syncGroup((ViewGroup) sourceChild, (ViewGroup) mirrorChild, rootSource, rootMirror);
            }
        }
    }

    private boolean needsRebuild(ViewGroup source, ViewGroup mirror) {
        if (source.getChildCount() != mirror.getChildCount()) return true;
        for (int i = 0; i < source.getChildCount(); i++) {
            View sourceChild = source.getChildAt(i);
            View mirrorChild = mirror.getChildAt(i);
            if (sourceByMirror.get(mirrorChild) != sourceChild || !compatible(sourceChild, mirrorChild)) {
                return true;
            }
        }
        return false;
    }

    private void rebuildChildren(ViewGroup source,
                                 ViewGroup mirror,
                                 ViewGroup rootSource,
                                 ViewGroup rootMirror) {
        removeMappings(mirror, false);
        mirror.removeAllViews();
        for (int i = 0; i < source.getChildCount(); i++) {
            View sourceChild = source.getChildAt(i);
            View mirrorChild = createMirror(sourceChild, rootSource, rootMirror);
            if (mirrorChild == null) continue;
            ViewGroup.LayoutParams params = copyLayoutParams(sourceChild.getLayoutParams());
            if (params == null) {
                mirror.addView(mirrorChild);
            } else {
                mirror.addView(mirrorChild, params);
            }
            if (sourceChild instanceof ViewGroup && mirrorChild instanceof ViewGroup) {
                rebuildChildren((ViewGroup) sourceChild, (ViewGroup) mirrorChild, rootSource, rootMirror);
            }
        }
    }

    private View createMirror(View source, ViewGroup rootSource, ViewGroup rootMirror) {
        View mirror;
        try {
            if (source instanceof MaterialCardView) {
                mirror = new MaterialCardView(context);
            } else if (source instanceof MaterialButton) {
                mirror = new MaterialButton(context);
            } else if (source instanceof MaterialCheckBox) {
                mirror = new MaterialCheckBox(context);
            } else if (source instanceof CheckBox) {
                mirror = new CheckBox(context);
            } else if (source instanceof LinearLayout) {
                mirror = new LinearLayout(context);
            } else if (source instanceof ImageView) {
                mirror = new ImageView(context);
            } else if (source instanceof Space) {
                mirror = new Space(context);
            } else if (source instanceof TextView) {
                mirror = new TextView(context);
            } else {
                mirror = new View(context);
            }
        } catch (Throwable ignored) {
            return null;
        }

        sourceByMirror.put(mirror, source);
        copyInitialState(source, mirror);
        bindProxyActions(source, mirror, rootSource, rootMirror);
        return mirror;
    }

    private void bindProxyActions(View source, View mirror, ViewGroup rootSource, ViewGroup rootMirror) {
        if (source instanceof CompoundButton && mirror instanceof CompoundButton) {
            ((CompoundButton) mirror).setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (syncing) return;
                try {
                    ((CompoundButton) source).setChecked(isChecked);
                } catch (Throwable ignored) {
                }
                scheduleSync(rootSource, rootMirror);
            });
        } else if (source.isClickable() || source.hasOnClickListeners()) {
            mirror.setClickable(true);
            mirror.setOnClickListener(v -> {
                try {
                    source.performClick();
                } catch (Throwable ignored) {
                }
                scheduleSync(rootSource, rootMirror);
            });
        }

        if (source.isLongClickable()) {
            mirror.setLongClickable(true);
            mirror.setOnLongClickListener(v -> {
                boolean handled = false;
                try {
                    handled = source.performLongClick();
                } catch (Throwable ignored) {
                }
                scheduleSync(rootSource, rootMirror);
                return handled;
            });
        }
    }

    private void scheduleSync(ViewGroup rootSource, ViewGroup rootMirror) {
        if (rootSource == null || rootMirror == null) return;
        try {
            rootMirror.postDelayed(() -> sync(rootSource, rootMirror), 40L);
            rootMirror.postDelayed(() -> sync(rootSource, rootMirror), 300L);
        } catch (Throwable ignored) {
        }
    }

    private void copyInitialState(View source, View mirror) {
        copyMutableState(source, mirror);
        try { mirror.setTag(source.getTag()); } catch (Throwable ignored) {}
        try { mirror.setMinimumWidth(source.getMinimumWidth()); } catch (Throwable ignored) {}
        try { mirror.setMinimumHeight(source.getMinimumHeight()); } catch (Throwable ignored) {}
        try {
            mirror.setPadding(source.getPaddingLeft(), source.getPaddingTop(),
                    source.getPaddingRight(), source.getPaddingBottom());
        } catch (Throwable ignored) {}

        if (source instanceof LinearLayout && mirror instanceof LinearLayout) {
            LinearLayout src = (LinearLayout) source;
            LinearLayout dst = (LinearLayout) mirror;
            try { dst.setOrientation(src.getOrientation()); } catch (Throwable ignored) {}
            try { dst.setGravity(src.getGravity()); } catch (Throwable ignored) {}
            try { dst.setBaselineAligned(src.isBaselineAligned()); } catch (Throwable ignored) {}
        }

        if (source instanceof MaterialCardView && mirror instanceof MaterialCardView) {
            MaterialCardView src = (MaterialCardView) source;
            MaterialCardView dst = (MaterialCardView) mirror;
            try { dst.setUseCompatPadding(src.getUseCompatPadding()); } catch (Throwable ignored) {}
            try { dst.setCardElevation(src.getCardElevation()); } catch (Throwable ignored) {}
            try { dst.setRadius(src.getRadius()); } catch (Throwable ignored) {}
            try { dst.setStrokeWidth(src.getStrokeWidth()); } catch (Throwable ignored) {}
            try { dst.setStrokeColor(src.getStrokeColor()); } catch (Throwable ignored) {}
            try { dst.setCardBackgroundColor(src.getCardBackgroundColor()); } catch (Throwable ignored) {}
        }

        if (source instanceof MaterialButton && mirror instanceof MaterialButton) {
            try { ((MaterialButton) mirror).setAllCaps(false); } catch (Throwable ignored) {}
        }

        if (source instanceof TextView && mirror instanceof TextView) {
            TextView src = (TextView) source;
            TextView dst = (TextView) mirror;
            try { dst.setTextColor(src.getTextColors()); } catch (Throwable ignored) {}
            try { dst.setTextSize(TypedValue.COMPLEX_UNIT_PX, src.getTextSize()); } catch (Throwable ignored) {}
            try { dst.setTypeface(src.getTypeface()); } catch (Throwable ignored) {}
            try { dst.setGravity(src.getGravity()); } catch (Throwable ignored) {}
            try { dst.setMaxLines(src.getMaxLines()); } catch (Throwable ignored) {}
            try { dst.setMinLines(src.getMinLines()); } catch (Throwable ignored) {}
            try { dst.setIncludeFontPadding(src.getIncludeFontPadding()); } catch (Throwable ignored) {}
            try { dst.setEllipsize(src.getEllipsize()); } catch (Throwable ignored) {}
        }

        if (source instanceof ImageView && mirror instanceof ImageView) {
            ImageView src = (ImageView) source;
            ImageView dst = (ImageView) mirror;
            try { dst.setImageDrawable(copyDrawable(src.getDrawable())); } catch (Throwable ignored) {}
            try { dst.setScaleType(src.getScaleType()); } catch (Throwable ignored) {}
            try { dst.setAdjustViewBounds(src.getAdjustViewBounds()); } catch (Throwable ignored) {}
        }
    }

    private void copyMutableState(View source, View mirror) {
        if (source == null || mirror == null) return;
        try { mirror.setVisibility(source.getVisibility()); } catch (Throwable ignored) {}
        try { mirror.setEnabled(source.isEnabled()); } catch (Throwable ignored) {}
        try { mirror.setAlpha(source.getAlpha()); } catch (Throwable ignored) {}
        try { mirror.setSelected(source.isSelected()); } catch (Throwable ignored) {}
        try { mirror.setActivated(source.isActivated()); } catch (Throwable ignored) {}
        try { mirror.setContentDescription(source.getContentDescription()); } catch (Throwable ignored) {}

        if (source instanceof TextView && mirror instanceof TextView) {
            TextView src = (TextView) source;
            TextView dst = (TextView) mirror;
            try {
                if (!TextUtils.equals(src.getText(), dst.getText())) dst.setText(src.getText());
            } catch (Throwable ignored) {}
        }

        if (source instanceof CompoundButton && mirror instanceof CompoundButton) {
            try {
                boolean checked = ((CompoundButton) source).isChecked();
                if (((CompoundButton) mirror).isChecked() != checked) {
                    ((CompoundButton) mirror).setChecked(checked);
                }
            } catch (Throwable ignored) {}
        }
    }

    private Drawable copyDrawable(Drawable drawable) {
        if (drawable == null) return null;
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) return state.newDrawable(context.getResources()).mutate();
        } catch (Throwable ignored) {
        }
        return drawable;
    }

    private ViewGroup.LayoutParams copyLayoutParams(ViewGroup.LayoutParams source) {
        if (source == null) return null;
        if (source instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams src = (LinearLayout.LayoutParams) source;
            LinearLayout.LayoutParams dst = new LinearLayout.LayoutParams(src.width, src.height, src.weight);
            dst.gravity = src.gravity;
            copyMargins(src, dst);
            return dst;
        }
        if (source instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams src = (FrameLayout.LayoutParams) source;
            FrameLayout.LayoutParams dst = new FrameLayout.LayoutParams(src.width, src.height, src.gravity);
            copyMargins(src, dst);
            return dst;
        }
        if (source instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams src = (ViewGroup.MarginLayoutParams) source;
            ViewGroup.MarginLayoutParams dst = new ViewGroup.MarginLayoutParams(src.width, src.height);
            copyMargins(src, dst);
            return dst;
        }
        return new ViewGroup.LayoutParams(source.width, source.height);
    }

    private void copyMargins(ViewGroup.MarginLayoutParams source, ViewGroup.MarginLayoutParams target) {
        if (source == null || target == null) return;
        target.leftMargin = source.leftMargin;
        target.topMargin = source.topMargin;
        target.rightMargin = source.rightMargin;
        target.bottomMargin = source.bottomMargin;
        try {
            target.setMarginStart(source.getMarginStart());
            target.setMarginEnd(source.getMarginEnd());
        } catch (Throwable ignored) {
        }
    }

    private boolean compatible(View source, View mirror) {
        if (source instanceof MaterialCardView) return mirror instanceof MaterialCardView;
        if (source instanceof MaterialButton) return mirror instanceof MaterialButton;
        if (source instanceof MaterialCheckBox) return mirror instanceof MaterialCheckBox;
        if (source instanceof CheckBox) return mirror instanceof CheckBox;
        if (source instanceof LinearLayout) return mirror instanceof LinearLayout;
        if (source instanceof ImageView) return mirror instanceof ImageView;
        if (source instanceof Space) return mirror instanceof Space;
        if (source instanceof TextView) return mirror instanceof TextView;
        return !(source instanceof ViewGroup) && !(mirror instanceof ViewGroup);
    }

    private void removeMappings(View view, boolean includeRoot) {
        if (view == null) return;
        if (includeRoot) sourceByMirror.remove(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            removeMappings(group.getChildAt(i), true);
        }
    }
}
