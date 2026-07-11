package dev.perms.test.files;

import android.text.TextUtils;

/** Copy/cut clipboard state shared by the Files tab panes. */
public final class FilesClipboardState {
    public String path;
    public boolean cut;
    public boolean fromRight;
    public String displayName;

    public boolean hasEntry() {
        return !TextUtils.isEmpty(path == null ? null : path.trim());
    }

    public void set(String path, boolean cut, boolean fromRight, String displayName) {
        this.path = path;
        this.cut = cut;
        this.fromRight = fromRight;
        this.displayName = displayName;
    }

    public void clear() {
        path = null;
        cut = false;
        fromRight = false;
        displayName = null;
    }

    public String statusText(boolean splitVisible, String activePaneLabel) {
        if (!hasEntry()) return "(empty)";
        return (cut ? "Cut" : "Copy")
                + " "
                + (fromRight ? "Right" : "Left")
                + (splitVisible ? (" → " + activePaneLabel) : "")
                + ": "
                + (displayName == null ? path : displayName);
    }
}
