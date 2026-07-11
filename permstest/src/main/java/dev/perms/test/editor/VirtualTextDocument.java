package dev.perms.test.editor;

import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Line-backed document for scalable source editors.
 *
 * The document is intentionally independent from smali, shell, JSON, or any other syntax. UI
 * controllers can render only visible lines while this class owns safe load/save behavior.
 */
public final class VirtualTextDocument {
    private final File sourceFile;
    private final ArrayList<String> lines;
    private final boolean endedWithNewline;
    private boolean dirty;

    private VirtualTextDocument(File sourceFile, ArrayList<String> lines, boolean endedWithNewline) {
        this.sourceFile = sourceFile;
        this.lines = lines == null ? new ArrayList<>() : lines;
        this.endedWithNewline = endedWithNewline;
    }


    public static VirtualTextDocument fromText(File sourceFile, String text) throws IOException {
        String safeText = text == null ? "" : text;
        ArrayList<String> loaded = new ArrayList<>();
        int start = 0;
        int len = safeText.length();
        while (start < len) {
            int end = safeText.indexOf('\n', start);
            if (end < 0) end = len;
            String line = safeText.substring(start, end);
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            loaded.add(line);
            start = end + 1;
        }
        boolean trailingNewline = len > 0 && safeText.charAt(len - 1) == '\n';
        File canonical = sourceFile == null ? null : sourceFile.getCanonicalFile();
        return new VirtualTextDocument(canonical, loaded, trailingNewline);
    }

    public static VirtualTextDocument load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("File not found: " + (file == null ? "" : file.getAbsolutePath()));
        }

        ArrayList<String> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new BufferedInputStream(new FileInputStream(file)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                loaded.add(line);
            }
        }

        boolean trailingNewline = false;
        long len = file.length();
        if (len > 0L) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(len - 1L);
                trailingNewline = raf.readByte() == '\n';
            }
        }

        return new VirtualTextDocument(file.getCanonicalFile(), loaded, trailingNewline);
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public int getLineCount() {
        return lines.size();
    }

    public String getLine(int index) {
        if (index < 0 || index >= lines.size()) return "";
        return lines.get(index);
    }

    public void setLine(int index, String value) {
        if (index < 0 || index >= lines.size()) return;
        String safeValue = value == null ? "" : value;
        if (TextUtils.equals(lines.get(index), safeValue)) return;
        lines.set(index, safeValue);
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean endedWithNewline() {
        return endedWithNewline;
    }

    public List<String> snapshotLines() {
        return new ArrayList<>(lines);
    }

    public String joinText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i));
            if (i < lines.size() - 1 || endedWithNewline) builder.append('\n');
        }
        return builder.toString();
    }

    public void writeTo(File outputFile) throws IOException {
        if (outputFile == null) throw new IOException("Missing output file.");
        File canonical = outputFile.getCanonicalFile();
        File parent = canonical.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create output folder: " + parent.getAbsolutePath());
        }

        File temp = File.createTempFile(canonical.getName(), ".tmp", parent);
        boolean moved = false;
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new BufferedOutputStream(new FileOutputStream(temp, false)), StandardCharsets.UTF_8))) {
                for (int i = 0; i < lines.size(); i++) {
                    writer.write(lines.get(i));
                    if (i < lines.size() - 1 || endedWithNewline) writer.newLine();
                }
            }
            replaceFile(temp, canonical);
            moved = true;
            dirty = false;
        } finally {
            if (!moved) {
                try { temp.delete(); } catch (Throwable ignored) {}
            }
        }
    }

    static void replaceFile(File temp, File target) throws IOException {
        if (temp == null || target == null) throw new IOException("Missing file path.");
        if (target.exists() && !target.delete()) {
            copyFile(temp, target);
            if (!temp.delete()) temp.deleteOnExit();
            return;
        }
        if (!temp.renameTo(target)) {
            copyFile(temp, target);
            if (!temp.delete()) temp.deleteOnExit();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target, false))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
    }
}
