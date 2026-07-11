package dev.perms.test.apk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BinaryXmlDebuggablePatcher {
    private static final int CHUNK_XML = 0x00080003;
    private static final int CHUNK_STRING_POOL = 0x001C0001;
    private static final int CHUNK_RESOURCE_MAP = 0x00080180;
    private static final int CHUNK_START_ELEMENT = 0x00100102;

    private static final int UTF8_FLAG = 0x00000100;
    private static final int TYPE_INT_BOOLEAN = 0x12;
    private static final int NO_INDEX = 0xFFFFFFFF;

    private static final String MANIFEST = "manifest";
    private static final String PACKAGE = "package";
    private static final String APPLICATION = "application";
    private static final String DEBUGGABLE = "debuggable";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final int ATTR_DEBUGGABLE = 0x0101000f;

    private BinaryXmlDebuggablePatcher() {
    }

    public static boolean isDebuggableEnabled(byte[] manifest) {
        try {
            if (manifest == null || manifest.length < 12 || u32(manifest, 0) != CHUNK_XML) return false;
            int xmlSize = u32(manifest, 4);
            if (xmlSize <= 0 || xmlSize > manifest.length) xmlSize = manifest.length;
            int stringPoolOffset = 8;
            if (stringPoolOffset + 28 > manifest.length || u32(manifest, stringPoolOffset) != CHUNK_STRING_POOL) return false;
            StringPoolChunk stringPool = StringPoolChunk.parse(manifest, stringPoolOffset);
            int stringPoolEnd = stringPoolOffset + stringPool.chunkSize;
            int resourceMapOffset = -1;
            int resourceMapSize = 0;
            int pos = stringPoolEnd;
            if (pos + 8 <= manifest.length && u32(manifest, pos) == CHUNK_RESOURCE_MAP) {
                resourceMapOffset = pos;
                resourceMapSize = u32(manifest, pos + 4);
                if (resourceMapSize < 8 || pos + resourceMapSize > manifest.length) return false;
                pos += resourceMapSize;
            }
            int applicationStartOffset = findApplicationStart(manifest, pos, xmlSize, stringPool.strings);
            if (applicationStartOffset < 0) return false;
            int[] resourceIds = readResourceMapIds(manifest, resourceMapOffset, resourceMapSize);
            return applicationHasDebuggable(manifest, applicationStartOffset, stringPool.strings, resourceIds);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String getManifestPackageName(byte[] manifest) {
        try {
            if (manifest == null || manifest.length < 12 || u32(manifest, 0) != CHUNK_XML) return null;
            int xmlSize = u32(manifest, 4);
            if (xmlSize <= 0 || xmlSize > manifest.length) xmlSize = manifest.length;
            int stringPoolOffset = 8;
            if (stringPoolOffset + 28 > manifest.length || u32(manifest, stringPoolOffset) != CHUNK_STRING_POOL) return null;
            StringPoolChunk stringPool = StringPoolChunk.parse(manifest, stringPoolOffset);
            int pos = stringPoolOffset + stringPool.chunkSize;
            if (pos + 8 <= manifest.length && u32(manifest, pos) == CHUNK_RESOURCE_MAP) {
                int size = u32(manifest, pos + 4);
                if (size < 8 || pos + size > manifest.length) return null;
                pos += size;
            }
            int manifestStart = findStartElement(manifest, pos, xmlSize, stringPool.strings, MANIFEST);
            if (manifestStart < 0) return null;
            return readStringAttribute(manifest, manifestStart, stringPool.strings, PACKAGE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static byte[] patchDebuggable(byte[] manifest) throws IOException {
        if (manifest == null || manifest.length < 12) {
            throw new IOException("AndroidManifest.xml is empty or too small.");
        }
        if (u32(manifest, 0) != CHUNK_XML) {
            throw new IOException("AndroidManifest.xml is not binary Android XML.");
        }

        int originalXmlSize = u32(manifest, 4);
        if (originalXmlSize <= 0 || originalXmlSize > manifest.length) {
            originalXmlSize = manifest.length;
        }

        int stringPoolOffset = 8;
        if (stringPoolOffset + 28 > manifest.length || u32(manifest, stringPoolOffset) != CHUNK_STRING_POOL) {
            throw new IOException("AndroidManifest.xml has no string pool at the expected position.");
        }

        StringPoolChunk stringPool = StringPoolChunk.parse(manifest, stringPoolOffset);
        int stringPoolEnd = stringPoolOffset + stringPool.chunkSize;
        if (stringPoolEnd > manifest.length) {
            throw new IOException("String pool chunk extends past manifest data.");
        }

        int resourceMapOffset = -1;
        int resourceMapSize = 0;
        int pos = stringPoolEnd;
        if (pos + 8 <= manifest.length && u32(manifest, pos) == CHUNK_RESOURCE_MAP) {
            resourceMapOffset = pos;
            resourceMapSize = u32(manifest, pos + 4);
            if (resourceMapSize < 8 || pos + resourceMapSize > manifest.length) {
                throw new IOException("Resource map chunk is malformed.");
            }
            pos += resourceMapSize;
        }

        int applicationStartOffset = findApplicationStart(manifest, pos, originalXmlSize, stringPool.strings);
        if (applicationStartOffset < 0) {
            throw new IOException("Application element not found in AndroidManifest.xml.");
        }

        int debugStringIndex = stringPool.indexOf(DEBUGGABLE);
        boolean addedDebugString = false;
        if (debugStringIndex < 0) {
            debugStringIndex = stringPool.strings.size();
            stringPool.strings.add(DEBUGGABLE);
            addedDebugString = true;
        }

        int androidNsIndex = stringPool.indexOf(ANDROID_NS);
        if (androidNsIndex < 0) {
            throw new IOException("Android namespace string not found in AndroidManifest.xml.");
        }

        byte[] newStringPool = addedDebugString ? stringPool.toBytes() : slice(manifest, stringPoolOffset, stringPool.chunkSize);
        int[] resourceIds = buildResourceMapIds(manifest, resourceMapOffset, resourceMapSize, debugStringIndex);
        byte[] newResourceMap = resourceMapToBytes(resourceIds);
        byte[] beforeChunks = slice(manifest, pos, applicationStartOffset - pos);
        byte[] patchedApplication = patchApplicationStart(manifest, applicationStartOffset, androidNsIndex, debugStringIndex, resourceIds);

        int applicationSize = u32(manifest, applicationStartOffset + 4);
        int afterApplicationOffset = applicationStartOffset + applicationSize;
        if (afterApplicationOffset > originalXmlSize) {
            throw new IOException("Application start chunk extends past manifest data.");
        }
        byte[] afterApplication = slice(manifest, afterApplicationOffset, originalXmlSize - afterApplicationOffset);

        int newXmlSize = 8 + newStringPool.length + newResourceMap.length + beforeChunks.length + patchedApplication.length + afterApplication.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(newXmlSize);
        writeU32(out, CHUNK_XML);
        writeU32(out, newXmlSize);
        out.write(newStringPool);
        out.write(newResourceMap);
        out.write(beforeChunks);
        out.write(patchedApplication);
        out.write(afterApplication);
        return out.toByteArray();
    }

    private static int findStartElement(byte[] data, int start, int end, List<String> strings, String elementName) throws IOException {
        int pos = start;
        while (pos + 8 <= end) {
            int type = u32(data, pos);
            int size = u32(data, pos + 4);
            if (size < 8 || pos + size > end) {
                throw new IOException("Malformed XML chunk at offset " + pos + ".");
            }
            if (type == CHUNK_START_ELEMENT) {
                if (pos + 36 > end) throw new IOException("Malformed start element chunk at offset " + pos + ".");
                int nameIndex = u32(data, pos + 20);
                if (nameIndex >= 0 && nameIndex < strings.size() && elementName.equals(strings.get(nameIndex))) {
                    return pos;
                }
            }
            pos += size;
        }
        return -1;
    }

    private static String readStringAttribute(byte[] data, int off, List<String> strings, String attrName) {
        int chunkSize = u32(data, off + 4);
        if (chunkSize < 36 || off + chunkSize > data.length) return null;
        int attrStart = u16(data, off + 24);
        int attrSize = u16(data, off + 26);
        int attrCount = u16(data, off + 28);
        int attrBase = 16 + attrStart;
        if (attrStart < 20 || attrSize < 20 || attrBase + (attrCount * attrSize) > chunkSize) return null;
        for (int i = 0; i < attrCount; i++) {
            int p = off + attrBase + (i * attrSize);
            int nameIndex = u32(data, p + 4);
            String name = nameIndex >= 0 && nameIndex < strings.size() ? strings.get(nameIndex) : null;
            if (!attrName.equals(name)) continue;
            int rawValueIndex = u32(data, p + 8);
            if (rawValueIndex >= 0 && rawValueIndex < strings.size()) return strings.get(rawValueIndex);
            int valueType = data[p + 15] & 0xff;
            int value = u32(data, p + 16);
            if (valueType == 0x03 && value >= 0 && value < strings.size()) return strings.get(value);
        }
        return null;
    }

    private static int findApplicationStart(byte[] data, int start, int end, List<String> strings) throws IOException {
        int pos = start;
        while (pos + 8 <= end) {
            int type = u32(data, pos);
            int size = u32(data, pos + 4);
            if (size < 8 || pos + size > end) {
                throw new IOException("Malformed XML chunk at offset " + pos + ".");
            }
            if (type == CHUNK_START_ELEMENT) {
                if (pos + 36 > end) throw new IOException("Malformed start element chunk at offset " + pos + ".");
                int nameIndex = u32(data, pos + 20);
                if (nameIndex >= 0 && nameIndex < strings.size() && APPLICATION.equals(strings.get(nameIndex))) {
                    return pos;
                }
            }
            pos += size;
        }
        return -1;
    }

    private static boolean applicationHasDebuggable(byte[] data, int off, List<String> strings, int[] resourceIds) throws IOException {
        int chunkSize = u32(data, off + 4);
        if (chunkSize < 36 || off + chunkSize > data.length) return false;
        int attrStart = u16(data, off + 24);
        int attrSize = u16(data, off + 26);
        int attrCount = u16(data, off + 28);
        int attrBase = 16 + attrStart;
        if (attrStart < 20 || attrSize < 20 || attrBase + (attrCount * attrSize) > chunkSize) return false;
        for (int i = 0; i < attrCount; i++) {
            int p = off + attrBase + (i * attrSize);
            int nameIndex = u32(data, p + 4);
            int resId = (resourceIds != null && nameIndex >= 0 && nameIndex < resourceIds.length) ? resourceIds[nameIndex] : 0;
            String name = nameIndex >= 0 && nameIndex < strings.size() ? strings.get(nameIndex) : null;
            if (resId == ATTR_DEBUGGABLE || DEBUGGABLE.equals(name)) {
                int valueType = data[p + 15] & 0xff;
                int value = u32(data, p + 16);
                return valueType == TYPE_INT_BOOLEAN && value != 0;
            }
        }
        return false;
    }

    private static int[] readResourceMapIds(byte[] data, int off, int size) {
        if (off < 0 || size < 8 || off + size > data.length) return new int[0];
        int count = (size - 8) / 4;
        int[] ids = new int[Math.max(0, count)];
        for (int i = 0; i < ids.length; i++) ids[i] = u32(data, off + 8 + (i * 4));
        return ids;
    }

    private static byte[] patchApplicationStart(byte[] data, int off, int androidNsIndex, int debugStringIndex, int[] resourceIds) throws IOException {
        int chunkSize = u32(data, off + 4);
        if (chunkSize < 36 || off + chunkSize > data.length) {
            throw new IOException("Application start chunk is malformed.");
        }
        int attrStart = u16(data, off + 24);
        int attrSize = u16(data, off + 26);
        int attrCount = u16(data, off + 28);
        int idIndex = u16(data, off + 30);
        int classIndex = u16(data, off + 32);
        int styleIndex = u16(data, off + 34);
        int attrBase = 16 + attrStart;
        if (attrStart < 20 || attrSize < 20 || attrBase + (attrCount * attrSize) > chunkSize) {
            throw new IOException("Application attribute table is malformed.");
        }

        byte[] chunk = slice(data, off, chunkSize);
        ArrayList<byte[]> attrs = new ArrayList<>(attrCount + 1);
        byte[] idAttr = null;
        byte[] classAttr = null;
        byte[] styleAttr = null;
        boolean patchedExisting = false;
        for (int i = 0; i < attrCount; i++) {
            int p = attrBase + (i * attrSize);
            byte[] attr = new byte[attrSize];
            System.arraycopy(chunk, p, attr, 0, attrSize);
            int nameIndex = u32(attr, 4);
            if (nameIndex == debugStringIndex) {
                writeDebuggableAttribute(attr, androidNsIndex, debugStringIndex);
                patchedExisting = true;
            }
            attrs.add(attr);
            if (idIndex == i + 1) idAttr = attr;
            if (classIndex == i + 1) classAttr = attr;
            if (styleIndex == i + 1) styleAttr = attr;
        }

        if (!patchedExisting) {
            byte[] attr = new byte[attrSize];
            writeDebuggableAttribute(attr, androidNsIndex, debugStringIndex);
            attrs.add(attr);
        }

        attrs.sort((a, b) -> {
            long ka = attributeSortKey(a, resourceIds);
            long kb = attributeSortKey(b, resourceIds);
            if (ka != kb) return ka < kb ? -1 : 1;
            int na = u32(a, 4);
            int nb = u32(b, 4);
            return Integer.compare(na, nb);
        });

        int oldAttrsEnd = attrBase + (attrCount * attrSize);
        ByteArrayOutputStream out = new ByteArrayOutputStream(chunkSize + (patchedExisting ? 0 : attrSize));
        out.write(chunk, 0, attrBase);
        for (byte[] attr : attrs) out.write(attr, 0, attr.length);
        out.write(chunk, oldAttrsEnd, chunkSize - oldAttrsEnd);
        byte[] patched = out.toByteArray();
        putU32(patched, 4, patched.length);
        putU16(patched, 28, attrs.size());
        putU16(patched, 30, oneBasedIndexOf(attrs, idAttr));
        putU16(patched, 32, oneBasedIndexOf(attrs, classAttr));
        putU16(patched, 34, oneBasedIndexOf(attrs, styleAttr));
        return patched;
    }

    private static void writeDebuggableAttribute(byte[] attr, int androidNsIndex, int debugStringIndex) {
        putU32(attr, 0, androidNsIndex);
        putU32(attr, 4, debugStringIndex);
        putU32(attr, 8, NO_INDEX);
        putU16(attr, 12, 8);
        attr[14] = 0;
        attr[15] = (byte) TYPE_INT_BOOLEAN;
        putU32(attr, 16, 0xFFFFFFFF);
    }

    private static int oneBasedIndexOf(List<byte[]> attrs, byte[] target) {
        if (attrs == null || target == null) return 0;
        for (int i = 0; i < attrs.size(); i++) {
            if (attrs.get(i) == target) return i + 1;
        }
        return 0;
    }

    private static long attributeSortKey(byte[] attr, int[] resourceIds) {
        if (attr == null || attr.length < 8) return 0xffffffffL;
        int nameIndex = u32(attr, 4);
        long resId = (resourceIds != null && nameIndex >= 0 && nameIndex < resourceIds.length) ? (resourceIds[nameIndex] & 0xffffffffL) : 0L;
        return resId == 0L ? 0xffffffffL : resId;
    }

    private static int[] buildResourceMapIds(byte[] data, int off, int size, int debugStringIndex) throws IOException {
        int neededEntries = debugStringIndex + 1;
        int[] ids = new int[neededEntries];
        if (off >= 0 && size >= 8) {
            int existing = (size - 8) / 4;
            if (existing > ids.length) {
                int[] expanded = new int[existing];
                System.arraycopy(ids, 0, expanded, 0, ids.length);
                ids = expanded;
            }
            for (int i = 0; i < existing; i++) {
                ids[i] = u32(data, off + 8 + (i * 4));
            }
            if (debugStringIndex >= ids.length) {
                int[] expanded = new int[debugStringIndex + 1];
                System.arraycopy(ids, 0, expanded, 0, ids.length);
                ids = expanded;
            }
        }
        ids[debugStringIndex] = ATTR_DEBUGGABLE;
        return ids;
    }

    private static byte[] resourceMapToBytes(int[] ids) {
        int count = ids == null ? 0 : ids.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + (count * 4));
        writeU32(out, CHUNK_RESOURCE_MAP);
        writeU32(out, 8 + (count * 4));
        if (ids != null) {
            for (int id : ids) writeU32(out, id);
        }
        return out.toByteArray();
    }

    private static byte[] slice(byte[] data, int off, int len) throws IOException {
        if (len < 0 || off < 0 || off + len > data.length) throw new IOException("Invalid binary XML slice.");
        byte[] out = new byte[len];
        System.arraycopy(data, off, out, 0, len);
        return out;
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

    private static void putU16(byte[] data, int off, int v) {
        data[off] = (byte) (v & 0xff);
        data[off + 1] = (byte) ((v >>> 8) & 0xff);
    }

    private static void putU32(byte[] data, int off, int v) {
        data[off] = (byte) (v & 0xff);
        data[off + 1] = (byte) ((v >>> 8) & 0xff);
        data[off + 2] = (byte) ((v >>> 16) & 0xff);
        data[off + 3] = (byte) ((v >>> 24) & 0xff);
    }

    private static void writeU32(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
        out.write((v >>> 16) & 0xff);
        out.write((v >>> 24) & 0xff);
    }

    private static final class StringPoolChunk {
        final int offset;
        final int headerSize;
        final int chunkSize;
        final int stringCount;
        final int styleCount;
        final int flags;
        final int stringsStart;
        final int stylesStart;
        final int[] styleOffsets;
        final byte[] styleData;
        final List<String> strings;

        StringPoolChunk(int offset, int headerSize, int chunkSize, int stringCount, int styleCount,
                        int flags, int stringsStart, int stylesStart, int[] styleOffsets,
                        byte[] styleData, List<String> strings) {
            this.offset = offset;
            this.headerSize = headerSize;
            this.chunkSize = chunkSize;
            this.stringCount = stringCount;
            this.styleCount = styleCount;
            this.flags = flags;
            this.stringsStart = stringsStart;
            this.stylesStart = stylesStart;
            this.styleOffsets = styleOffsets;
            this.styleData = styleData;
            this.strings = strings;
        }

        int indexOf(String s) {
            for (int i = 0; i < strings.size(); i++) {
                if (s.equals(strings.get(i))) return i;
            }
            return -1;
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

            int[] styleOffsets = new int[styleCount];
            for (int i = 0; i < styleCount; i++) styleOffsets[i] = u32(data, styleOffsetsBase + (i * 4));
            byte[] styleData;
            if (styleCount > 0 && stylesStart > 0) {
                int styleDataStart = off + stylesStart;
                styleData = slice(data, styleDataStart, off + chunkSize - styleDataStart);
            } else {
                styleData = new byte[0];
            }
            return new StringPoolChunk(off, headerSize, chunkSize, stringCount, styleCount, flags, stringsStart, stylesStart, styleOffsets, styleData, strings);
        }

        byte[] toBytes() {
            boolean utf8 = (flags & UTF8_FLAG) != 0;
            List<byte[]> encoded = new ArrayList<>(strings.size());
            int stringsBytes = 0;
            for (String s : strings) {
                byte[] e = utf8 ? encodeUtf8String(s) : encodeUtf16String(s);
                encoded.add(e);
                stringsBytes += e.length;
            }
            int offsetsSize = (strings.size() * 4) + (styleCount * 4);
            int newStringsStart = 28 + offsetsSize;
            int paddedStringsBytes = align4(stringsBytes);
            int newStylesStart = styleCount > 0 ? newStringsStart + paddedStringsBytes : 0;
            int newChunkSize = newStringsStart + paddedStringsBytes + styleData.length;
            ByteArrayOutputStream out = new ByteArrayOutputStream(newChunkSize);
            writeU16(out, CHUNK_STRING_POOL & 0xffff);
            writeU16(out, 28);
            writeU32(out, newChunkSize);
            writeU32(out, strings.size());
            writeU32(out, styleCount);
            writeU32(out, flags);
            writeU32(out, newStringsStart);
            writeU32(out, newStylesStart);
            int running = 0;
            for (byte[] e : encoded) {
                writeU32(out, running);
                running += e.length;
            }
            for (int styleOffset : styleOffsets) writeU32(out, styleOffset);
            for (byte[] e : encoded) out.write(e, 0, e.length);
            while ((out.size() & 3) != 0) out.write(0);
            out.write(styleData, 0, styleData.length);
            return out.toByteArray();
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

        private static byte[] encodeUtf8String(String s) {
            byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream(utf8.length + 8);
            writeLength8(out, s.length());
            writeLength8(out, utf8.length);
            out.write(utf8, 0, utf8.length);
            out.write(0);
            return out.toByteArray();
        }

        private static byte[] encodeUtf16String(String s) {
            byte[] utf16 = s.getBytes(StandardCharsets.UTF_16LE);
            ByteArrayOutputStream out = new ByteArrayOutputStream(utf16.length + 8);
            writeLength16(out, s.length());
            out.write(utf16, 0, utf16.length);
            out.write(0);
            out.write(0);
            return out.toByteArray();
        }

        private static void writeLength8(ByteArrayOutputStream out, int len) {
            if (len > 0x7f) {
                out.write(((len >>> 8) & 0x7f) | 0x80);
                out.write(len & 0xff);
            } else {
                out.write(len & 0x7f);
            }
        }

        private static void writeLength16(ByteArrayOutputStream out, int len) {
            if (len > 0x7fff) {
                writeU16(out, ((len >>> 16) & 0x7fff) | 0x8000);
                writeU16(out, len & 0xffff);
            } else {
                writeU16(out, len & 0x7fff);
            }
        }

        private static int align4(int n) {
            return (n + 3) & ~3;
        }
    }

    private static void writeU16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
    }
}
