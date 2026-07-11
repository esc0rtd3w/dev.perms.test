package dev.perms.test.files;

import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/** Mutable state for one Files tab browser pane. */
public final class FilesPaneState {
    public String cwd;
    public int refreshSeq = 0;
    public FileEntry selected;
    public FilesAdapter adapter;
    public final ArrayList<FileEntry> allEntries = new ArrayList<>();
    public final ArrayList<FileEntry> entries = new ArrayList<>();
    public final ListView list;
    public final TextView path;

    public FilesPaneState(String initialCwd, ListView list, TextView path) {
        this.cwd = initialCwd;
        this.list = list;
        this.path = path;
    }
}
