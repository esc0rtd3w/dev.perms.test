package dev.perms.test.packages.editor;

import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight read-only binary AndroidManifest.xml preview for devices without apktool.
 *
 * This is intentionally a viewer, not a full AXML decoder/rebuilder. Editing still requires an
 * apktool-decoded XML workspace so rebuilds do not corrupt binary XML chunks.
 */
public final class ApkEditorBinaryManifestPreview {
    private static final int CHUNK_XML = 0x00080003;
    private static final int CHUNK_STRING_POOL = 0x001C0001;
    private static final int CHUNK_RESOURCE_MAP = 0x00080180;
    private static final int CHUNK_START_NAMESPACE = 0x00100100;
    private static final int CHUNK_END_NAMESPACE = 0x00100101;
    private static final int CHUNK_START_ELEMENT = 0x00100102;
    private static final int CHUNK_END_ELEMENT = 0x00100103;
    private static final int CHUNK_CDATA = 0x00100104;

    private static final int UTF8_FLAG = 0x00000100;
    private static final int NO_INDEX = 0xFFFFFFFF;

    private static final int TYPE_NULL = 0x00;
    private static final int TYPE_REFERENCE = 0x01;
    private static final int TYPE_ATTRIBUTE = 0x02;
    private static final int TYPE_STRING = 0x03;
    private static final int TYPE_FLOAT = 0x04;
    private static final int TYPE_DIMENSION = 0x05;
    private static final int TYPE_FRACTION = 0x06;
    private static final int TYPE_INT_DEC = 0x10;
    private static final int TYPE_INT_HEX = 0x11;
    private static final int TYPE_INT_BOOLEAN = 0x12;
    private static final int TYPE_FIRST_COLOR_INT = 0x1c;
    private static final int TYPE_LAST_COLOR_INT = 0x1f;

    private ApkEditorBinaryManifestPreview() {
    }

    public static String render(File manifestFile, String header) {
        return render(manifestFile);
    }

    public static String render(File manifestFile) {
        StringBuilder out = new StringBuilder(32 * 1024);
        try {
            byte[] data = readAll(manifestFile);
            out.append(render(data));
        } catch (Throwable t) {
            out.append("<!-- Unable to render binary manifest: ")
                    .append(escapeComment(shortError(t)))
                    .append(" -->\n");
            if (manifestFile != null) {
                out.append("<!-- Extracted binary file: ")
                        .append(escapeComment(manifestFile.getAbsolutePath()))
                        .append(" -->\n");
            }
        }
        return out.toString();
    }

    public static String render(byte[] data) throws IOException {
        if (data == null || data.length < 12 || u32(data, 0) != CHUNK_XML) {
            throw new IOException("AndroidManifest.xml is not binary Android XML.");
        }
        int xmlSize = u32(data, 4);
        if (xmlSize <= 0 || xmlSize > data.length) xmlSize = data.length;

        int stringPoolOffset = 8;
        if (stringPoolOffset + 28 > xmlSize || u32(data, stringPoolOffset) != CHUNK_STRING_POOL) {
            throw new IOException("String pool chunk was not found.");
        }
        StringPoolChunk stringPool = StringPoolChunk.parse(data, stringPoolOffset);
        int pos = stringPoolOffset + stringPool.chunkSize;
        int[] resourceIds = new int[0];
        if (pos + 8 <= xmlSize && u32(data, pos) == CHUNK_RESOURCE_MAP) {
            int size = u32(data, pos + 4);
            if (size < 8 || pos + size > xmlSize) throw new IOException("Resource map chunk is malformed.");
            resourceIds = readResourceIds(data, pos, size);
            pos += size;
        }

        StringBuilder out = new StringBuilder(32 * 1024);
        int depth = 0;
        while (pos + 8 <= xmlSize) {
            int type = u32(data, pos);
            int size = u32(data, pos + 4);
            if (size < 8 || pos + size > xmlSize) {
                out.append("\n<!-- Stopped at malformed chunk offset ").append(pos).append(". -->\n");
                break;
            }
            if (type == CHUNK_START_ELEMENT) {
                depth = appendStartElement(out, data, pos, stringPool.strings, resourceIds, depth);
            } else if (type == CHUNK_END_ELEMENT) {
                depth = Math.max(0, depth - 1);
                appendIndent(out, depth);
                out.append("</").append(elementName(data, pos, stringPool.strings)).append(">\n");
            } else if (type == CHUNK_CDATA) {
                appendIndent(out, depth);
                out.append(escapeXml(stringAt(stringPool.strings, u32(data, pos + 16)))).append('\n');
            } else if (type == CHUNK_START_NAMESPACE || type == CHUNK_END_NAMESPACE) {
                // Namespace chunks are reflected through element/attribute names where possible.
            }
            pos += size;
        }
        if (out.length() == 0) throw new IOException("No XML elements were found.");
        return out.toString();
    }

    private static int appendStartElement(StringBuilder out, byte[] data, int off, List<String> strings,
                                          int[] resourceIds, int depth) {
        String name = elementName(data, off, strings);
        int chunkSize = u32(data, off + 4);
        int attrStart = u16(data, off + 24);
        int attrSize = u16(data, off + 26);
        int attrCount = u16(data, off + 28);
        int attrBase = 16 + attrStart;
        appendIndent(out, depth);
        out.append('<').append(name);
        if (attrStart >= 20 && attrSize >= 20 && attrBase + (attrCount * attrSize) <= chunkSize) {
            for (int i = 0; i < attrCount; i++) {
                int p = off + attrBase + (i * attrSize);
                out.append('\n');
                appendIndent(out, depth + 1);
                out.append(attributeName(data, p, strings, resourceIds))
                        .append("=\"")
                        .append(escapeXml(attributeValue(data, p, strings)))
                        .append('"');
            }
        }
        if (attrCount > 0) {
            out.append('\n');
            appendIndent(out, depth);
        }
        out.append(">\n");
        return depth + 1;
    }

    private static String elementName(byte[] data, int off, List<String> strings) {
        return safeXmlName(stringAt(strings, u32(data, off + 20)), "node");
    }

    private static String attributeName(byte[] data, int attrOff, List<String> strings, int[] resourceIds) {
        int nsIndex = u32(data, attrOff);
        int nameIndex = u32(data, attrOff + 4);
        String name = safeXmlName(stringAt(strings, nameIndex), "attr" + nameIndex);
        String ns = stringAt(strings, nsIndex);
        if ("http://schemas.android.com/apk/res/android".equals(ns)) return "android:" + name;
        if (!TextUtils.isEmpty(ns) && !name.startsWith("xmlns")) {
            return name;
        }
        int resId = nameIndex >= 0 && nameIndex < resourceIds.length ? resourceIds[nameIndex] : 0;
        if ((resId & 0xffff0000) == 0x01010000 && !name.startsWith("android:")) return "android:" + name;
        return name;
    }

    private static String attributeValue(byte[] data, int attrOff, List<String> strings) {
        int rawValueIndex = u32(data, attrOff + 8);
        if (rawValueIndex != NO_INDEX && rawValueIndex >= 0 && rawValueIndex < strings.size()) {
            return stringAt(strings, rawValueIndex);
        }
        int valueType = data[attrOff + 15] & 0xff;
        int value = u32(data, attrOff + 16);
        if (valueType == TYPE_STRING) return stringAt(strings, value);
        if (valueType == TYPE_INT_BOOLEAN) return value == 0 ? "false" : "true";
        if (valueType == TYPE_REFERENCE) return "@0x" + hex8(value);
        if (valueType == TYPE_ATTRIBUTE) return "?0x" + hex8(value);
        if (valueType == TYPE_FLOAT) return Float.toString(Float.intBitsToFloat(value));
        if (valueType == TYPE_DIMENSION) return complexToString(value, false);
        if (valueType == TYPE_FRACTION) return complexToString(value, true);
        if (valueType == TYPE_INT_DEC) return Integer.toString(value);
        if (valueType == TYPE_INT_HEX) return "0x" + hex8(value);
        if (valueType >= TYPE_FIRST_COLOR_INT && valueType <= TYPE_LAST_COLOR_INT) return "#" + hex8(value);
        if (valueType == TYPE_NULL) return "";
        return "0x" + hex8(value);
    }

    private static String complexToString(int value, boolean fraction) {
        float f = (value & 0xFFFFFF00) * RADIX_MULTS[(value >> 4) & 3];
        if (fraction) return f + (((value & 0xf) == 1) ? "%p" : "%");
        String[] units = {"px", "dp", "sp", "pt", "in", "mm"};
        int unit = value & 0xf;
        return f + (unit >= 0 && unit < units.length ? units[unit] : "");
    }

    private static final float[] RADIX_MULTS = {
            1.0f / (1 << 23),
            1.0f / (1 << 15),
            1.0f / (1 << 7),
            1.0f
    };

    private static int[] readResourceIds(byte[] data, int off, int size) {
        int count = Math.max(0, (size - 8) / 4);
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = u32(data, off + 8 + (i * 4));
        return ids;
    }

    private static byte[] readAll(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Manifest file is missing.");
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), 1024L * 1024L))) {
            byte[] buf = new byte[32 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    private static void appendIndent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) out.append("    ");
    }

    private static String stringAt(List<String> strings, int index) {
        return index >= 0 && strings != null && index < strings.size() ? strings.get(index) : "";
    }

    private static String safeXmlName(String name, String fallback) {
        if (TextUtils.isEmpty(name)) return fallback;
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == ':'
                    || (i > 0 && ((c >= '0' && c <= '9') || c == '-' || c == '.'));
            out.append(ok ? c : '_');
        }
        return out.length() == 0 ? fallback : out.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeComment(String s) {
        return (s == null ? "" : s).replace("--", "- -");
    }

    private static String shortError(Throwable t) {
        if (t == null) return "unknown";
        String msg = t.getMessage();
        return TextUtils.isEmpty(msg) ? t.getClass().getSimpleName() : msg;
    }

    private static String hex8(int v) {
        String s = Integer.toHexString(v);
        while (s.length() < 8) s = "0" + s;
        return s;
    }

    private static int u16(byte[] data, int off) {
        return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
    }

    private static int u32(byte[] data, int off) {
        return (data[off] & 0xff)
                | ((data[off + 1] & 0xff) << 8)
                | ((data[off + 2] & 0xff) << 16)
                | ((data[off + 3] & 0xff) << 24);
    }

    private static final class StringPoolChunk {
        final int chunkSize;
        final List<String> strings;

        StringPoolChunk(int chunkSize, List<String> strings) {
            this.chunkSize = chunkSize;
            this.strings = strings;
        }

        static StringPoolChunk parse(byte[] data, int off) throws IOException {
            int headerSize = u16(data, off + 2);
            int chunkSize = u32(data, off + 4);
            int stringCount = u32(data, off + 8);
            int styleCount = u32(data, off + 12);
            int flags = u32(data, off + 16);
            int stringsStart = u32(data, off + 20);
            int stylesStart = u32(data, off + 24);
            if (headerSize < 28 || chunkSize < headerSize || off + chunkSize > data.length) {
                throw new IOException("String pool header is malformed.");
            }
            int stringOffsetsBase = off + headerSize;
            int styleOffsetsBase = stringOffsetsBase + (stringCount * 4);
            if (styleOffsetsBase + (styleCount * 4) > off + chunkSize) {
                throw new IOException("String pool offsets are malformed.");
            }
            boolean utf8 = (flags & UTF8_FLAG) != 0;
            int stringsBase = off + stringsStart;
            int stringsEnd = stylesStart == 0 ? (off + chunkSize) : (off + stylesStart);
            if (stringsBase < off || stringsBase > stringsEnd || stringsEnd > off + chunkSize) {
                throw new IOException("String pool data is malformed.");
            }
            List<String> strings = new ArrayList<>(stringCount);
            for (int i = 0; i < stringCount; i++) {
                int so = u32(data, stringOffsetsBase + (i * 4));
                int sp = stringsBase + so;
                if (sp < stringsBase || sp >= stringsEnd) throw new IOException("String offset is malformed.");
                strings.add(utf8 ? readUtf8String(data, sp, stringsEnd) : readUtf16String(data, sp, stringsEnd));
            }
            return new StringPoolChunk(chunkSize, strings);
        }

        private static String readUtf8String(byte[] data, int off, int end) throws IOException {
            int[] a = readLength8(data, off, end);
            int p = a[1];
            int[] b = readLength8(data, p, end);
            int byteLen = b[0];
            p = b[1];
            if (p + byteLen >= end) throw new IOException("UTF-8 string extends past string pool.");
            return new String(data, p, byteLen, StandardCharsets.UTF_8);
        }

        private static String readUtf16String(byte[] data, int off, int end) throws IOException {
            int[] a = readLength16(data, off, end);
            int charLen = a[0];
            int p = a[1];
            int byteLen = charLen * 2;
            if (p + byteLen + 2 > end) throw new IOException("UTF-16 string extends past string pool.");
            byte[] tmp = new byte[byteLen];
            System.arraycopy(data, p, tmp, 0, byteLen);
            return new String(tmp, StandardCharsets.UTF_16LE);
        }

        private static int[] readLength8(byte[] data, int off, int end) throws IOException {
            if (off >= end) throw new IOException("String length extends past string pool.");
            int len = data[off] & 0xff;
            off++;
            if ((len & 0x80) != 0) {
                if (off >= end) throw new IOException("String length extends past string pool.");
                len = ((len & 0x7f) << 8) | (data[off] & 0xff);
                off++;
            }
            return new int[]{len, off};
        }

        private static int[] readLength16(byte[] data, int off, int end) throws IOException {
            if (off + 2 > end) throw new IOException("String length extends past string pool.");
            int len = u16(data, off);
            off += 2;
            if ((len & 0x8000) != 0) {
                if (off + 2 > end) throw new IOException("String length extends past string pool.");
                len = ((len & 0x7fff) << 16) | u16(data, off);
                off += 2;
            }
            return new int[]{len, off};
        }
    }
}
