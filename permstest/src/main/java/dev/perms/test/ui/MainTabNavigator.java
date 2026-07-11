package dev.perms.test.ui;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.google.android.material.tabs.TabLayout;

import dev.perms.test.R;
import dev.perms.test.databinding.ActivityMainBinding;

/** Owns the main tab strip, tab visibility, slide animation, and page-swipe routing. */
public final class MainTabNavigator {
    public interface Host {
        int dp(int value);
        void onSelectedTabChanged(int index);
        void onTabVisible(int index);
    }

    private static final String[] TAB_TITLES = new String[] {
            "Main",
            "Shell",
            "Packages",
            "Memory",
            "Files",
            "Network",
            "Scripts",
            "Debugging",
            "Tools",
            "Logging",
            "Plugins",
            "Settings",
            "About"
    };

    private static final int MIN_FLING_DIST_PX = 160;
    private static final int MIN_FLING_VELOCITY = 700;
    private static final int SLIDE_DURATION_MS = 320;

    private final Context context;
    private final ActivityMainBinding binding;
    private final Host host;

    private GestureDetector gestureDetector;
    private int currentTabIndex;
    private boolean ignoreSwipeForCurrentTouch;
    private boolean swipeConsumedForCurrentTouch;
    private float downX;
    private float downY;

    public MainTabNavigator(Context context, ActivityMainBinding binding, Host host) {
        this.context = context;
        this.binding = binding;
        this.host = host;
    }

    public int bind(int preferredIndex) {
        if (binding == null || binding.tabLayout == null) return Math.max(0, preferredIndex);

        try {
            binding.tabLayout.removeAllTabs();
            for (String title : TAB_TITLES) {
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title));
            }

            boolean isTablet = context != null && context.getResources().getBoolean(R.bool.is_tablet);
            int tabMode = (isTablet && binding.tabLayout.getTabCount() <= 8)
                    ? TabLayout.MODE_FIXED
                    : TabLayout.MODE_SCROLLABLE;
            binding.tabLayout.setTabMode(tabMode);
            binding.tabLayout.setTabGravity(tabMode == TabLayout.MODE_SCROLLABLE
                    ? TabLayout.GRAVITY_START
                    : TabLayout.GRAVITY_FILL);
            binding.tabLayout.setSmoothScrollingEnabled(true);
            binding.tabLayout.setHorizontalScrollBarEnabled(false);
            binding.tabLayout.setOnTouchListener((v, ev) -> {
                try {
                    if (ev != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                } catch (Throwable ignored) {
                }
                return false;
            });

            int idx = Math.max(0, Math.min(preferredIndex, binding.tabLayout.getTabCount() - 1));
            currentTabIndex = idx;
            notifySelectedTabChanged(idx);
            showTab(idx, false, idx);

            TabLayout.Tab tab = binding.tabLayout.getTabAt(idx);
            if (tab != null) tab.select();

            binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    int prev = currentTabIndex;
                    currentTabIndex = tab == null ? 0 : tab.getPosition();
                    notifySelectedTabChanged(currentTabIndex);
                    showTab(currentTabIndex, true, prev);
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });

            setupSwipeDetector();
            return currentTabIndex;
        } catch (Throwable ignored) {
            return currentTabIndex;
        }
    }

    public int getCurrentTabIndex() {
        return currentTabIndex;
    }

    public void showTab(int index, boolean animate) {
        if (binding == null || binding.tabLayout == null) return;
        int safeIndex = Math.max(0, Math.min(index, binding.tabLayout.getTabCount() - 1));

        // Programmatic tab changes must update the visible TabLayout selection too.
        // Otherwise helper buttons can show one tab content while the old tab label
        // remains highlighted, which is especially confusing for cross-tab transitions.
        if (binding.tabLayout.getSelectedTabPosition() != safeIndex) {
            TabLayout.Tab tab = binding.tabLayout.getTabAt(safeIndex);
            if (tab != null) {
                tab.select();
                return;
            }
        }

        showTab(safeIndex, animate, currentTabIndex);
        currentTabIndex = safeIndex;
        notifySelectedTabChanged(safeIndex);
    }

    public void handleDispatchTouchEvent(MotionEvent event) {
        try {
            if (event == null) return;

            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                ignoreSwipeForCurrentTouch = isTouchInTabStripArea(event);
                swipeConsumedForCurrentTouch = false;
                downX = event.getX();
                downY = event.getY();
            }

            // The tab strip is its own horizontal scroll surface. Page swipes start only
            // from content below the strip so tab dragging does not switch pages.
            if (!ignoreSwipeForCurrentTouch && gestureDetector != null) {
                boolean consumed = gestureDetector.onTouchEvent(event);
                swipeConsumedForCurrentTouch |= consumed;
            }

            if (action == MotionEvent.ACTION_UP) {
                if (!ignoreSwipeForCurrentTouch && !swipeConsumedForCurrentTouch) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    int minFallbackDistance = host == null ? 96 : host.dp(96);
                    if (Math.abs(dx) >= minFallbackDistance && Math.abs(dx) >= Math.abs(dy) * 1.8f) {
                        swipeConsumedForCurrentTouch = selectAdjacentTabBySwipe(dx);
                    }
                }
                ignoreSwipeForCurrentTouch = false;
                swipeConsumedForCurrentTouch = false;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                ignoreSwipeForCurrentTouch = false;
                swipeConsumedForCurrentTouch = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private void setupSwipeDetector() {
        try {
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                // Handle page swipes at Activity dispatch level so nested controls still
                // keep normal vertical scrolling and tap handling.
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (binding == null || binding.tabLayout == null || e1 == null || e2 == null) return false;
                    float dx = e2.getX() - e1.getX();
                    float dy = e2.getY() - e1.getY();
                    if (Math.abs(dx) < MIN_FLING_DIST_PX) return false;
                    if (Math.abs(velocityX) < MIN_FLING_VELOCITY) return false;
                    if (Math.abs(dx) < Math.abs(dy) * 1.8f) return false;
                    if (Math.abs(velocityX) < Math.abs(velocityY) * 1.6f) return false;
                    return selectAdjacentTabBySwipe(dx);
                }
            });
        } catch (Throwable ignored) {
            gestureDetector = null;
        }
    }

    private boolean selectAdjacentTabBySwipe(float dx) {
        if (binding == null || binding.tabLayout == null) return false;
        int cur = binding.tabLayout.getSelectedTabPosition();
        int next = dx < 0 ? cur + 1 : cur - 1;
        if (next < 0 || next >= binding.tabLayout.getTabCount()) return false;
        TabLayout.Tab tab = binding.tabLayout.getTabAt(next);
        if (tab == null) return false;
        tab.select();
        return true;
    }

    private boolean isTouchInTabStripArea(MotionEvent event) {
        if (event == null || binding == null) return false;
        try {
            View hostView = binding.tabHost;
            if (hostView != null && hostView.getVisibility() == View.VISIBLE) {
                int[] location = new int[2];
                hostView.getLocationOnScreen(location);
                return event.getRawY() < location[1];
            }
            return isTouchInsideView(binding.tabLayout, event);
        } catch (Throwable ignored) {
            return isTouchInsideView(binding.tabLayout, event);
        }
    }

    private boolean isTouchInsideView(View view, MotionEvent event) {
        if (view == null || event == null || view.getVisibility() != View.VISIBLE) return false;
        try {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            float x = event.getRawX();
            float y = event.getRawY();
            return x >= location[0]
                    && x <= location[0] + view.getWidth()
                    && y >= location[1]
                    && y <= location[1] + view.getHeight();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void showTab(int index, boolean animate, int previousIndex) {
        if (binding == null) return;
        if (!animate || previousIndex == index) {
            setTabVisibilities(index);
            return;
        }

        final View from = tabViewForIndex(previousIndex);
        final View to = tabViewForIndex(index);
        if (from == null || to == null) {
            setTabVisibilities(index);
            return;
        }

        from.setVisibility(View.VISIBLE);
        to.setVisibility(View.VISIBLE);

        final int direction = (index > previousIndex) ? 1 : -1;
        int fallbackWidth = 1;
        try {
            if (context instanceof android.app.Activity) {
                fallbackWidth = ((android.app.Activity) context).getWindow().getDecorView().getWidth();
            }
        } catch (Throwable ignored) {
            fallbackWidth = 1;
        }
        final int width = Math.max(1, from.getWidth() > 0 ? from.getWidth() : fallbackWidth);

        to.setAlpha(0.85f);
        to.setTranslationX(direction * width);
        from.setAlpha(1f);
        from.setTranslationX(0f);

        Interpolator interpolator = new DecelerateInterpolator();

        to.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(SLIDE_DURATION_MS)
                .setInterpolator(interpolator)
                .withEndAction(() -> {
                    setTabVisibilities(index);
                    from.setTranslationX(0f);
                    from.setAlpha(1f);
                    to.setTranslationX(0f);
                    to.setAlpha(1f);
                })
                .start();

        from.animate()
                .translationX(-direction * width)
                .alpha(0.85f)
                .setDuration(SLIDE_DURATION_MS)
                .setInterpolator(interpolator)
                .start();
    }

    private View tabViewForIndex(int index) {
        if (binding == null) return null;
        if (index == 0) return binding.tabMain.getRoot();
        if (index == 1) return binding.tabShell.getRoot();
        if (index == 2) return binding.tabPackages.getRoot();
        if (index == 3) return binding.tabMemory.getRoot();
        if (index == 4) return binding.tabFiles.getRoot();
        if (index == 5) return binding.tabNetwork.getRoot();
        if (index == 6) return binding.tabScripts.getRoot();
        if (index == 7) return binding.tabDebugging.getRoot();
        if (index == 8) return binding.tabTools.getRoot();
        if (index == 9) return binding.tabLogging.getRoot();
        if (index == 10) return binding.tabPlugins.getRoot();
        if (index == 11) return binding.tabSettings.getRoot();
        if (index == 12) return binding.tabAbout.getRoot();
        return null;
    }

    private void setTabVisibilities(int visibleIndex) {
        binding.tabMain.getRoot().setVisibility(visibleIndex == 0 ? View.VISIBLE : View.GONE);
        binding.tabShell.getRoot().setVisibility(visibleIndex == 1 ? View.VISIBLE : View.GONE);
        binding.tabPackages.getRoot().setVisibility(visibleIndex == 2 ? View.VISIBLE : View.GONE);
        binding.tabMemory.getRoot().setVisibility(visibleIndex == 3 ? View.VISIBLE : View.GONE);
        binding.tabFiles.getRoot().setVisibility(visibleIndex == 4 ? View.VISIBLE : View.GONE);
        binding.tabNetwork.getRoot().setVisibility(visibleIndex == 5 ? View.VISIBLE : View.GONE);
        binding.tabScripts.getRoot().setVisibility(visibleIndex == 6 ? View.VISIBLE : View.GONE);
        binding.tabDebugging.getRoot().setVisibility(visibleIndex == 7 ? View.VISIBLE : View.GONE);
        binding.tabTools.getRoot().setVisibility(visibleIndex == 8 ? View.VISIBLE : View.GONE);
        binding.tabLogging.getRoot().setVisibility(visibleIndex == 9 ? View.VISIBLE : View.GONE);
        binding.tabPlugins.getRoot().setVisibility(visibleIndex == 10 ? View.VISIBLE : View.GONE);
        binding.tabSettings.getRoot().setVisibility(visibleIndex == 11 ? View.VISIBLE : View.GONE);
        binding.tabAbout.getRoot().setVisibility(visibleIndex == 12 ? View.VISIBLE : View.GONE);
        if (host != null) host.onTabVisible(visibleIndex);
    }

    private void notifySelectedTabChanged(int index) {
        if (host != null) host.onSelectedTabChanged(index);
    }
}
