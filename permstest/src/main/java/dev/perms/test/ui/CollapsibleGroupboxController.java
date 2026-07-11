package dev.perms.test.ui;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

import dev.perms.test.R;
import dev.perms.test.settings.SettingsPreferenceKeys;

/**
 * Adds lightweight collapse/expand behavior to normal tab-page groupboxes.
 *
 * The tab XML models groupboxes as MaterialCardView containers with either a
 * direct title TextView or a tagged title row. This controller keeps that header
 * intact and only hides/shows the content rows below it. Overlay and panel XML is
 * intentionally ignored by requiring a normal tab-page ancestor.
 */
public final class CollapsibleGroupboxController {
    private static final String STATE_PREFIX = "groupbox_collapsed_";
    private static final String SUFFIX_EXPANDED = "  ▾";
    private static final String SUFFIX_COLLAPSED = "  ▸";

    private static final WeakHashMap<TextView, CharSequence> ORIGINAL_TITLES = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> RESTORE_VISIBILITY = new WeakHashMap<>();

    private CollapsibleGroupboxController() {
    }

    public static void apply(View root, SharedPreferences prefs) {
        if (root == null || prefs == null) return;
        boolean enabled = prefs.getBoolean(SettingsPreferenceKeys.ENABLE_COLLAPSIBLE_GROUPBOXES, true);
        boolean autoCollapse = prefs.getBoolean(SettingsPreferenceKeys.AUTO_COLLAPSE_GROUPBOXES, false);
        String profile = GroupboxCollapseProfiles.normalizeProfile(
                prefs.getString(SettingsPreferenceKeys.AUTO_COLLAPSE_GROUPBOX_PROFILE,
                        GroupboxCollapseProfiles.defaultProfile()));
        List<MaterialCardView> cards = new ArrayList<>();
        collectCards(root, cards);
        for (MaterialCardView card : cards) {
            bindCard(card, prefs, enabled, autoCollapse, profile);
        }
    }

    /**
     * Opens collapsed tab-page groupboxes that contain the supplied child view.
     *
     * This is used by guided UI flows that must measure/highlight a child view
     * without changing the user's saved collapse state. The returned session
     * restores only groupboxes that were temporarily opened by this call.
     */
    public static ExpandSession expandGroupboxesContaining(View child) {
        return openGroupboxesContaining(child, true);
    }

    /**
     * Opens collapsed tab-page groupboxes that contain the supplied child view.
     *
     * Unlike {@link #expandGroupboxesContaining(View)}, this is a one-way reveal
     * for user-facing navigation such as external file-open intents. It does not
     * overwrite the saved collapse preference; it only makes the loaded content
     * visible for the current UI session.
     */
    public static void revealGroupboxesContaining(View child) {
        openGroupboxesContaining(child, false);
    }

    /**
     * Reveals a target groupbox immediately and again after the next layout pass.
     * External file-open intents often switch tabs before the tab page has finished
     * measuring; the delayed reveals keep loaded editor/install content visible
     * without changing the user's saved collapse preference.
     */
    public static void revealGroupboxesContainingAfterLayout(View child) {
        if (child == null) return;
        revealGroupboxesContaining(child);
        try { child.post(() -> revealGroupboxesContaining(child)); } catch (Throwable ignored) {}
        try { child.postDelayed(() -> revealGroupboxesContaining(child), 250L); } catch (Throwable ignored) {}
    }

    private static ExpandSession openGroupboxesContaining(View child, boolean recordForRestore) {
        ExpandSession session = new ExpandSession();
        View current = child;
        while (current != null) {
            if (current instanceof MaterialCardView && hasTabPageAncestor(current)) {
                MaterialCardView card = (MaterialCardView) current;
                GroupboxParts parts = findParts(card);
                if (parts != null && parts.title != null && isTitleCollapsed(parts.title)) {
                    CharSequence original = ORIGINAL_TITLES.get(parts.title);
                    if (original == null) {
                        original = stripSuffix(parts.title.getText());
                        ORIGINAL_TITLES.put(parts.title, original);
                    }
                    updateTitle(parts.title, original, false);
                    setCollapsed(parts, false);
                    if (recordForRestore) session.add(card, original);
                }
            }
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return session;
    }

    private static void collectCards(View view, List<MaterialCardView> out) {
        if (view == null || out == null) return;
        if (view instanceof MaterialCardView && hasTabPageAncestor(view)) {
            out.add((MaterialCardView) view);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectCards(group.getChildAt(i), out);
        }
    }

    private static void bindCard(MaterialCardView card, SharedPreferences prefs, boolean enabled,
                                 boolean autoCollapse, String profile) {
        GroupboxParts parts = findParts(card);
        if (parts == null || parts.title == null || parts.content == null) return;

        CharSequence original = ORIGINAL_TITLES.get(parts.title);
        if (original == null) {
            original = stripSuffix(parts.title.getText());
            ORIGINAL_TITLES.put(parts.title, original);
        }

        if (!enabled) {
            parts.title.setOnClickListener(null);
            parts.title.setClickable(false);
            parts.title.setText(original);
            setCollapsed(parts, false);
            return;
        }

        final CharSequence stableTitle = original;
        final String idPart = resourceName(card);
        final String titlePart = sanitize(String.valueOf(stableTitle));
        final String key = stateKey(card, parts.title, stableTitle);
        boolean userCollapsed = prefs.getBoolean(key, false);
        boolean collapsed = autoCollapse
                ? GroupboxCollapseProfiles.shouldCollapse(profile, idPart, titlePart, userCollapsed)
                : userCollapsed;
        updateTitle(parts.title, stableTitle, collapsed);
        setCollapsed(parts, collapsed);
        parts.title.setClickable(true);
        parts.title.setOnClickListener(v -> {
            boolean nextCollapsed = !isTitleCollapsed(parts.title);
            prefs.edit().putBoolean(key, nextCollapsed).apply();
            updateTitle(parts.title, stableTitle, nextCollapsed);
            setCollapsed(parts, nextCollapsed);
        });
    }

    private static GroupboxParts findParts(MaterialCardView card) {
        if (card == null || card.getChildCount() == 0) return null;
        View first = card.getChildAt(0);
        if (!(first instanceof ViewGroup)) return null;
        ViewGroup content = (ViewGroup) first;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child instanceof TextView && child.getVisibility() != View.GONE) {
                CharSequence text = ((TextView) child).getText();
                if (!TextUtils.isEmpty(text)) {
                    return new GroupboxParts(content, (TextView) child, i);
                }
            }
            if (child instanceof ViewGroup && isGroupboxHeaderRow(child)) {
                TextView title = findHeaderTitle((ViewGroup) child);
                if (title != null) {
                    return new GroupboxParts(content, title, i);
                }
            }
        }
        return null;
    }

    private static boolean isGroupboxHeaderRow(View view) {
        if (view == null || view.getVisibility() == View.GONE) return false;
        Object tag = view.getTag();
        return tag != null && "collapsible_groupbox_header".equals(String.valueOf(tag));
    }

    private static TextView findHeaderTitle(ViewGroup header) {
        if (header == null) return null;
        for (int i = 0; i < header.getChildCount(); i++) {
            View child = header.getChildAt(i);
            if (child instanceof TextView && child.getVisibility() != View.GONE) {
                CharSequence text = ((TextView) child).getText();
                if (!TextUtils.isEmpty(text)) return (TextView) child;
            }
        }
        return null;
    }

    /**
     * Returns a view's normal visibility when it is currently hidden only because
     * its tab groupbox is collapsed. This lets cloned popout surfaces stay useful
     * without expanding or mutating the original groupbox.
     */
    public static int visibilityIgnoringGroupboxCollapse(View view) {
        if (view == null) return View.GONE;
        Integer restore = RESTORE_VISIBILITY.get(view);
        return restore != null ? restore : view.getVisibility();
    }

    private static void setCollapsed(GroupboxParts parts, boolean collapsed) {
        ViewGroup content = parts.content;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (i == parts.titleIndex) continue;
            if (collapsed) {
                if (!RESTORE_VISIBILITY.containsKey(child)) {
                    RESTORE_VISIBILITY.put(child, child.getVisibility());
                }
                child.setVisibility(View.GONE);
            } else {
                Integer restore = RESTORE_VISIBILITY.remove(child);
                if (restore != null) child.setVisibility(restore);
            }
        }
    }

    private static void updateTitle(TextView title, CharSequence original, boolean collapsed) {
        title.setText(String.valueOf(original) + (collapsed ? SUFFIX_COLLAPSED : SUFFIX_EXPANDED));
    }

    private static boolean isTitleCollapsed(TextView title) {
        return title != null && String.valueOf(title.getText()).endsWith(SUFFIX_COLLAPSED);
    }

    private static CharSequence stripSuffix(CharSequence text) {
        String value = String.valueOf(text == null ? "" : text);
        if (value.endsWith(SUFFIX_EXPANDED) || value.endsWith(SUFFIX_COLLAPSED)) {
            return value.substring(0, value.length() - SUFFIX_EXPANDED.length());
        }
        return value;
    }

    private static String stateKey(MaterialCardView card, TextView title, CharSequence original) {
        String idPart = resourceName(card);
        if (TextUtils.isEmpty(idPart)) idPart = resourceName(title);
        String titlePart = sanitize(String.valueOf(original));
        if (TextUtils.isEmpty(idPart)) idPart = titlePart;
        return STATE_PREFIX + idPart + "_" + titlePart;
    }

    private static String resourceName(View view) {
        if (view == null || view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Resources.NotFoundException ignored) {
            return "";
        }
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) sb.append(ch);
            else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
        }
        String out = sb.toString().toLowerCase(Locale.US);
        while (out.endsWith("_")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static boolean hasTabPageAncestor(View view) {
        View current = view;
        while (current != null) {
            int id = current.getId();
            if (id == R.id.tabMain || id == R.id.tabShell || id == R.id.tabPackages
                    || id == R.id.tabMemory || id == R.id.tabFiles || id == R.id.tabNetwork
                    || id == R.id.tabScripts || id == R.id.tabDebugging || id == R.id.tabTools
                    || id == R.id.tabLogging || id == R.id.tabPlugins
                    || id == R.id.tabSettings || id == R.id.tabAbout) {
                return true;
            }
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }


    public static final class ExpandSession {
        private final List<ExpandedGroupbox> expanded = new ArrayList<>();

        private ExpandSession() {
        }

        private void add(MaterialCardView card, CharSequence originalTitle) {
            expanded.add(new ExpandedGroupbox(card, originalTitle));
        }

        public boolean isEmpty() {
            return expanded.isEmpty();
        }

        public void restore() {
            for (int i = expanded.size() - 1; i >= 0; i--) {
                ExpandedGroupbox item = expanded.get(i);
                if (item == null || item.card == null) continue;
                GroupboxParts parts = findParts(item.card);
                if (parts == null || parts.title == null || isTitleCollapsed(parts.title)) continue;
                CharSequence original = item.originalTitle;
                if (TextUtils.isEmpty(original)) {
                    original = ORIGINAL_TITLES.get(parts.title);
                }
                if (TextUtils.isEmpty(original)) {
                    original = stripSuffix(parts.title.getText());
                }
                ORIGINAL_TITLES.put(parts.title, original);
                updateTitle(parts.title, original, true);
                setCollapsed(parts, true);
            }
            expanded.clear();
        }
    }

    private static final class ExpandedGroupbox {
        final MaterialCardView card;
        final CharSequence originalTitle;

        ExpandedGroupbox(MaterialCardView card, CharSequence originalTitle) {
            this.card = card;
            this.originalTitle = originalTitle;
        }
    }

    private static final class GroupboxParts {
        final ViewGroup content;
        final TextView title;
        final int titleIndex;

        GroupboxParts(ViewGroup content, TextView title, int titleIndex) {
            this.content = content;
            this.title = title;
            this.titleIndex = titleIndex;
        }
    }
}
