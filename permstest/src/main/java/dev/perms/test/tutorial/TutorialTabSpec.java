package dev.perms.test.tutorial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tutorial definition for one main tab. */
public final class TutorialTabSpec {
    public final int tabIndex;
    public final String key;
    public final String title;
    public final List<TutorialStep> steps;

    public TutorialTabSpec(int tabIndex, String key, String title, List<TutorialStep> steps) {
        this.tabIndex = tabIndex;
        this.key = key == null ? "" : key;
        this.title = title == null ? "" : title;
        this.steps = steps == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }
}
