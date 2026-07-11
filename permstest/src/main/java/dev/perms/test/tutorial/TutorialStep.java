package dev.perms.test.tutorial;

import androidx.annotation.IdRes;

/** One focused tutorial highlight. Keep text and target id together for easy tab edits. */
public final class TutorialStep {
    public final int targetId;
    public final int highlightId;
    public final String title;
    public final String message;

    public TutorialStep(@IdRes int targetId, String title, String message) {
        this(targetId, 0, title, message);
    }

    public TutorialStep(@IdRes int targetId, @IdRes int highlightId, String title, String message) {
        this.targetId = targetId;
        this.highlightId = highlightId;
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
    }

    public int highlightTargetId() {
        return highlightId != 0 ? highlightId : targetId;
    }
}
