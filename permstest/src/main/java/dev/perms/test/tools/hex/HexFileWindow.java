package dev.perms.test.tools.hex;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Small, bounded file window used by the Tools-tab Hex Editor.
 *
 * The editor intentionally works on a window instead of loading entire files into
 * an EditText-sized buffer. That keeps large APKs, saves, logs, and dumps from
 * blocking the UI while still allowing precise in-place byte edits.
 */
public final class HexFileWindow {
    public static final int DEFAULT_WINDOW_LENGTH = 0x4000;
    public static final int MAX_WINDOW_LENGTH = 0x10000;

    private File file;
    private Uri uri;
    private String displayName;
    private long fileSize;
    private long offset;
    private byte[] bytes = new byte[0];
    private int loadedLength;
    private boolean dirty;

    public static HexFileWindow load(String path, long requestedOffset, int requestedLength) throws IOException {
        File target = new File(normalizePath(path));
        if (!target.exists()) throw new IOException("File does not exist: " + target.getPath());
        if (!target.isFile()) throw new IOException("Path is not a regular file: " + target.getPath());
        long size = target.length();
        long offset = Math.max(0L, Math.min(requestedOffset, size));
        int length = boundedLength(requestedLength);
        int available = (int) Math.max(0L, Math.min((long) length, size - offset));
        byte[] data = new byte[available];
        try (RandomAccessFile raf = new RandomAccessFile(target, "r")) {
            raf.seek(offset);
            readFully(raf.getChannel(), data);
        }
        HexFileWindow window = new HexFileWindow();
        window.file = target;
        window.fileSize = size;
        window.offset = offset;
        window.bytes = data;
        window.loadedLength = data.length;
        window.dirty = false;
        return window;
    }

    public static HexFileWindow load(ContentResolver resolver, Uri uri, String displayName,
                                     long requestedOffset, int requestedLength) throws IOException {
        if (resolver == null) throw new IOException("Content resolver unavailable.");
        if (uri == null) throw new IOException("Picked document is unavailable.");
        int length = boundedLength(requestedLength);
        long knownSize = querySize(resolver, uri);
        long offset = knownSize >= 0L ? Math.max(0L, Math.min(requestedOffset, knownSize)) : Math.max(0L, requestedOffset);
        int available = knownSize >= 0L
                ? (int) Math.max(0L, Math.min((long) length, knownSize - offset))
                : length;
        byte[] data = new byte[available];
        int count = 0;
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd == null) throw new IOException("Could not open picked document.");
            long statSize = pfd.getStatSize();
            if (knownSize < 0L && statSize >= 0L) knownSize = statSize;
            try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
                FileChannel channel = in.getChannel();
                channel.position(offset);
                count = readFully(channel, data);
            }
        }
        if (count < data.length) {
            byte[] trimmed = new byte[Math.max(0, count)];
            if (count > 0) System.arraycopy(data, 0, trimmed, 0, count);
            data = trimmed;
        }
        HexFileWindow window = new HexFileWindow();
        window.uri = uri;
        window.displayName = TextUtils.isEmpty(displayName) ? uri.toString() : displayName;
        window.fileSize = knownSize >= 0L ? knownSize : Math.max(offset + data.length, 0L);
        window.offset = offset;
        window.bytes = data;
        window.loadedLength = data.length;
        window.dirty = false;
        return window;
    }

    public void save() throws IOException {
        if (file == null) throw new IOException("No path-backed file is loaded.");
        if (lengthChanged()) throw new IOException("Insert/delete changed the loaded window length. Reload or use overwrite edits before saving.");
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(offset);
            raf.write(bytes);
        }
        fileSize = file.length();
        loadedLength = bytes().length;
        dirty = false;
    }

    public void save(ContentResolver resolver) throws IOException {
        if (lengthChanged()) throw new IOException("Insert/delete changed the loaded window length. Reload or use overwrite edits before saving.");
        if (uri == null) {
            save();
            return;
        }
        if (resolver == null) throw new IOException("Content resolver unavailable.");
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "rw")) {
            if (pfd == null) throw new IOException("Could not open picked document for writing.");
            try (FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
                FileChannel channel = out.getChannel();
                channel.position(offset);
                writeFully(channel, bytes);
                channel.force(false);
            }
            fileSize = Math.max(fileSize, offset + bytes().length);
        }
        loadedLength = bytes().length;
        dirty = false;
    }

    public String path() {
        if (file != null) return file.getPath();
        return uri == null ? "" : uri.toString();
    }

    public String displayName() {
        if (!TextUtils.isEmpty(displayName)) return displayName;
        return path();
    }

    public boolean contentBacked() {
        return uri != null;
    }

    public long fileSize() {
        return fileSize;
    }

    public long offset() {
        return offset;
    }

    public byte[] bytes() {
        return bytes == null ? new byte[0] : bytes;
    }

    public boolean dirty() {
        return dirty;
    }

    public boolean lengthChanged() {
        return bytes().length != loadedLength;
    }

    public byte[] copyBytes(long absoluteOffset, int length) {
        if (length <= 0 || bytes == null) return new byte[0];
        long relativeLong = absoluteOffset - offset;
        if (relativeLong < 0L || relativeLong > Integer.MAX_VALUE) return new byte[0];
        int relative = (int) relativeLong;
        if (relative < 0 || relative >= bytes.length) return new byte[0];
        int count = Math.max(0, Math.min(length, bytes.length - relative));
        byte[] out = new byte[count];
        if (count > 0) System.arraycopy(bytes, relative, out, 0, count);
        return out;
    }

    public boolean clearBytes(long absoluteOffset, int length) {
        if (length <= 0 || bytes == null) return false;
        long relativeLong = absoluteOffset - offset;
        if (relativeLong < 0L || relativeLong > Integer.MAX_VALUE) return false;
        int relative = (int) relativeLong;
        if (relative < 0 || relative >= bytes.length) return false;
        int count = Math.max(0, Math.min(length, bytes.length - relative));
        for (int i = 0; i < count; i++) bytes[relative + i] = 0;
        dirty = count > 0;
        return count > 0;
    }

    public boolean insertBytes(long absoluteOffset, byte[] inserted) {
        if (inserted == null || inserted.length == 0 || bytes == null) return false;
        long relativeLong = absoluteOffset - offset;
        if (relativeLong < 0L || relativeLong > Integer.MAX_VALUE) return false;
        int relative = (int) relativeLong;
        if (relative < 0 || relative > bytes.length) return false;
        if (bytes.length + inserted.length > MAX_WINDOW_LENGTH) return false;
        byte[] out = new byte[bytes.length + inserted.length];
        System.arraycopy(bytes, 0, out, 0, relative);
        System.arraycopy(inserted, 0, out, relative, inserted.length);
        System.arraycopy(bytes, relative, out, relative + inserted.length, bytes.length - relative);
        bytes = out;
        dirty = true;
        return true;
    }

    public boolean replaceBytes(long absoluteOffset, byte[] replacement) {
        if (replacement == null || replacement.length == 0 || bytes == null) return false;
        long relativeLong = absoluteOffset - offset;
        if (relativeLong < 0L || relativeLong > Integer.MAX_VALUE) return false;
        int relative = (int) relativeLong;
        if (relative < 0 || relative + replacement.length > bytes.length) return false;
        System.arraycopy(replacement, 0, bytes, relative, replacement.length);
        dirty = true;
        return true;
    }

    public int indexOf(byte[] needle, int start) {
        if (needle == null || needle.length == 0 || bytes == null || bytes.length == 0) return -1;
        int begin = Math.max(0, start);
        int limit = bytes.length - needle.length;
        for (int i = begin; i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static int boundedLength(int requestedLength) {
        return Math.max(1, Math.min(requestedLength, MAX_WINDOW_LENGTH));
    }

    private static int readFully(FileChannel channel, byte[] data) throws IOException {
        if (channel == null || data == null || data.length == 0) return 0;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int total = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) break;
            if (read == 0) break;
            total += read;
        }
        return total;
    }

    private static void writeFully(FileChannel channel, byte[] data) throws IOException {
        if (channel == null || data == null || data.length == 0) return;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static long querySize(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null) return -1L;
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd != null) {
                long size = pfd.getStatSize();
                if (size >= 0L) return size;
            }
        } catch (Throwable ignored) {
        }
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.SIZE }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Throwable ignored) {
        }
        return -1L;
    }

    private static String normalizePath(String raw) {
        String path = raw == null ? "" : raw.trim();
        if (path.startsWith("~/")) {
            String home = System.getProperty("user.home", "");
            path = home + path.substring(1);
        }
        return path;
    }
}
