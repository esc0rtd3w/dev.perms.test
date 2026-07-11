package dev.perms.test.apk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Binary AndroidManifest.xml helper for adding <uses-permission> entries. */
public final class BinaryXmlPermissionPatcher {
    private static final int CHUNK_XML = 0x00080003;
    private static final int CHUNK_STRING_POOL = 0x001C0001;
    private static final int CHUNK_RESOURCE_MAP = 0x00080180;
    private static final int CHUNK_START_NAMESPACE = 0x00100100;
    private static final int CHUNK_END_NAMESPACE = 0x00100101;
    private static final int CHUNK_START_ELEMENT = 0x00100102;
    private static final int CHUNK_END_ELEMENT = 0x00100103;

    private static final int UTF8_FLAG = 0x00000100;
    private static final int TYPE_STRING = 0x03;
    private static final int NO_INDEX = 0xFFFFFFFF;

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String MANIFEST = "manifest";
    private static final String APPLICATION = "application";
    private static final String USES_PERMISSION = "uses-permission";
    private static final String NAME = "name";
    private static final int ATTR_NAME = 0x01010003;

    private BinaryXmlPermissionPatcher() {
    }

    public static List<String> readUsesPermissions(byte[] manifest) throws IOException {
        if (manifest == null || manifest.length < 12) throw new IOException("AndroidManifest.xml is empty or too small.");
        if (u32(manifest, 0) != CHUNK_XML) throw new IOException("AndroidManifest.xml is not binary Android XML.");
        int originalXmlSize = u32(manifest, 4);
        if (originalXmlSize <= 0 || originalXmlSize > manifest.length) originalXmlSize = manifest.length;

        int stringPoolOffset = 8;
        if (stringPoolOffset + 28 > manifest.length || u32(manifest, stringPoolOffset) != CHUNK_STRING_POOL) {
            throw new IOException("AndroidManifest.xml has no string pool at the expected position.");
        }
        StringPoolChunk stringPool = StringPoolChunk.parse(manifest, stringPoolOffset);
        int stringPoolEnd = stringPoolOffset + stringPool.chunkSize;
        if (stringPoolEnd > manifest.length) throw new IOException("String pool chunk extends past manifest data.");

        int pos = stringPoolEnd;
        if (pos + 8 <= manifest.length && u32(manifest, pos) == CHUNK_RESOURCE_MAP) {
            int resourceMapSize = u32(manifest, pos + 4);
            if (resourceMapSize < 8 || pos + resourceMapSize > manifest.length) throw new IOException("Resource map chunk is malformed.");
            pos += resourceMapSize;
        }

        ArrayList<String> out = new ArrayList<>(collectExistingPermissions(manifest, pos, originalXmlSize, stringPool.strings));
        Collections.sort(out);
        return out;
    }

    public static byte[] patchUsesPermissions(byte[] manifest, List<String> requestedPermissions) throws IOException {
        if (manifest == null || manifest.length < 12) throw new IOException("AndroidManifest.xml is empty or too small.");
        if (u32(manifest, 0) != CHUNK_XML) throw new IOException("AndroidManifest.xml is not binary Android XML.");
        if (requestedPermissions == null || requestedPermissions.isEmpty()) return manifest;

        int originalXmlSize = u32(manifest, 4);
        if (originalXmlSize <= 0 || originalXmlSize > manifest.length) originalXmlSize = manifest.length;

        int stringPoolOffset = 8;
        if (stringPoolOffset + 28 > manifest.length || u32(manifest, stringPoolOffset) != CHUNK_STRING_POOL) {
            throw new IOException("AndroidManifest.xml has no string pool at the expected position.");
        }
        StringPoolChunk stringPool = StringPoolChunk.parse(manifest, stringPoolOffset);
        int stringPoolEnd = stringPoolOffset + stringPool.chunkSize;
        if (stringPoolEnd > manifest.length) throw new IOException("String pool chunk extends past manifest data.");

        int resourceMapOffset = -1;
        int resourceMapSize = 0;
        int pos = stringPoolEnd;
        if (pos + 8 <= manifest.length && u32(manifest, pos) == CHUNK_RESOURCE_MAP) {
            resourceMapOffset = pos;
            resourceMapSize = u32(manifest, pos + 4);
            if (resourceMapSize < 8 || pos + resourceMapSize > manifest.length) throw new IOException("Resource map chunk is malformed.");
            pos += resourceMapSize;
        }

        int manifestStart = findStartElement(manifest, pos, originalXmlSize, stringPool.strings, MANIFEST);
        if (manifestStart < 0) throw new IOException("Manifest element not found in AndroidManifest.xml.");
        int insertOffset = findStartElement(manifest, pos, originalXmlSize, stringPool.strings, APPLICATION);
        if (insertOffset < 0) insertOffset = findEndElement(manifest, pos, originalXmlSize, stringPool.strings, MANIFEST);
        if (insertOffset < 0) throw new IOException("Unable to find a safe permission insertion point.");

        Set<String> existing = collectExistingPermissions(manifest, pos, originalXmlSize, stringPool.strings);
        ArrayList<String> permissions = new ArrayList<>();
        HashSet<String> seen = new HashSet<>(existing);
        for (String raw : requestedPermissions) {
            String p = raw == null ? "" : raw.trim();
            if (p.isEmpty() || seen.contains(p)) continue;
            permissions.add(p);
            seen.add(p);
        }
        if (permissions.isEmpty()) return manifest;

        int androidNsIndex = ensureString(stringPool, ANDROID_NS);
        int usesPermissionIndex = ensureString(stringPool, USES_PERMISSION);
        int nameIndex = ensureString(stringPool, NAME);
        ArrayList<Integer> permissionIndexes = new ArrayList<>(permissions.size());
        for (String p : permissions) permissionIndexes.add(ensureString(stringPool, p));

        byte[] newStringPool = stringPool.toBytes();
        int[] resourceIds = buildResourceMapIds(manifest, resourceMapOffset, resourceMapSize, stringPool.strings.size(), nameIndex);
        byte[] newResourceMap = resourceMapToBytes(resourceIds);

        byte[] beforeInsert = slice(manifest, pos, insertOffset - pos);
        byte[] permissionChunks = buildPermissionChunks(androidNsIndex, usesPermissionIndex, nameIndex, permissionIndexes);
        byte[] afterInsert = slice(manifest, insertOffset, originalXmlSize - insertOffset);

        int newXmlSize = 8 + newStringPool.length + newResourceMap.length + beforeInsert.length + permissionChunks.length + afterInsert.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(newXmlSize);
        writeU32(out, CHUNK_XML);
        writeU32(out, newXmlSize);
        out.write(newStringPool);
        out.write(newResourceMap);
        out.write(beforeInsert);
        out.write(permissionChunks);
        out.write(afterInsert);
        return out.toByteArray();
    }

    private static int ensureString(StringPoolChunk pool, String value) {
        int index = pool.indexOf(value);
        if (index >= 0) return index;
        pool.strings.add(value);
        return pool.strings.size() - 1;
    }

    private static byte[] buildPermissionChunks(int androidNsIndex, int usesPermissionIndex,
                                                int nameIndex, List<Integer> permissionIndexes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(permissionIndexes.size() * 80);
        for (Integer valueIndex : permissionIndexes) {
            writeStartElement(out, NO_INDEX, usesPermissionIndex,
                    new Attribute[] { newStringAttribute(androidNsIndex, nameIndex, valueIndex == null ? NO_INDEX : valueIndex) });
            writeEndElement(out, NO_INDEX, usesPermissionIndex);
        }
        return out.toByteArray();
    }

    private static Attribute newStringAttribute(int nsIndex, int nameIndex, int valueIndex) {
        Attribute attr = new Attribute();
        attr.nsIndex = nsIndex;
        attr.nameIndex = nameIndex;
        attr.rawValueIndex = valueIndex;
        attr.valueType = TYPE_STRING;
        attr.valueData = valueIndex;
        return attr;
    }

    private static void writeStartElement(ByteArrayOutputStream out, int nsIndex, int nameIndex, Attribute[] attrs) {
        int count = attrs == null ? 0 : attrs.length;
        int size = 36 + (count * 20);
        writeU32(out, CHUNK_START_ELEMENT);
        writeU32(out, size);
        writeU32(out, 0); // line
        writeU32(out, NO_INDEX); // comment
        writeU32(out, nsIndex);
        writeU32(out, nameIndex);
        writeU16(out, 20); // attrStart, relative to element header after line/comment
        writeU16(out, 20); // attrSize
        writeU16(out, count);
        writeU16(out, 0); // idIndex
        writeU16(out, 0); // classIndex
        writeU16(out, 0); // styleIndex
        if (attrs != null) {
            for (Attribute attr : attrs) writeAttribute(out, attr);
        }
    }

    private static void writeEndElement(ByteArrayOutputStream out, int nsIndex, int nameIndex) {
        writeU32(out, CHUNK_END_ELEMENT);
        writeU32(out, 24);
        writeU32(out, 0); // line
        writeU32(out, NO_INDEX); // comment
        writeU32(out, nsIndex);
        writeU32(out, nameIndex);
    }

    private static void writeAttribute(ByteArrayOutputStream out, Attribute attr) {
        writeU32(out, attr.nsIndex);
        writeU32(out, attr.nameIndex);
        writeU32(out, attr.rawValueIndex);
        writeU16(out, 8); // typed value size
        out.write(0); // res0
        out.write(attr.valueType & 0xff);
        writeU32(out, attr.valueData);
    }

    private static Set<String> collectExistingPermissions(byte[] data, int start, int end, List<String> strings) throws IOException {
        HashSet<String> out = new HashSet<>();
        int pos = start;
        while (pos + 8 <= end) {
            int type = u32(data, pos);
            int size = u32(data, pos + 4);
            if (size < 8 || pos + size > end) throw new IOException("Malformed XML chunk at offset " + pos + ".");
            if (type == CHUNK_START_ELEMENT) {
                if (pos + 36 > end) throw new IOException("Malformed start element chunk at offset " + pos + ".");
                int nameIndex = u32(data, pos + 20);
                String element = nameIndex >= 0 && nameIndex < strings.size() ? strings.get(nameIndex) : null;
                if (USES_PERMISSION.equals(element)) {
                    String permission = readStringAttribute(data, pos, strings, NAME);
                    if (permission != null && !permission.trim().isEmpty()) out.add(permission.trim());
                }
            }
            pos += size;
        }
        return out;
    }

    private static int findStartElement(byte[] data, int start, int end, List<String> strings, String elementName) throws IOException {
        int pos = start;
        while (pos + 8 <= end) {
            int type = u32(data, pos);
            int size = u32(data, pos + 4);
            if (size < 8 || pos + size > end) throw new IOException("Malformed XML chunk at offset " + pos + ".");
            if (type == CHUNK_START_ELEMENT) {
                if (pos + 36 > end) throw new IOException("Malformed start element chunk at offset " + pos + ".");
                int nameIndex = u32(data, pos + 20);
                if (nameIndex >= 0 && nameIndex < strings.size() && elementName.equals(strings.get(nameIndex))) return pos;
            }
            pos += size;
        }
        return -1;
    }

    private static int findEndElement(byte[] data, int start, int end, List<String> strings, String elementName) throws IOException {
        int pos = start;
        while (pos + 8 <= end) {
            int type = u32(data, pos);
            int size = u32(data, pos + 4);
            if (size < 8 || pos + size > end) throw new IOException("Malformed XML chunk at offset " + pos + ".");
            if (type == CHUNK_END_ELEMENT) {
                if (pos + 24 > end) throw new IOException("Malformed end element chunk at offset " + pos + ".");
                int nameIndex = u32(data, pos + 20);
                if (nameIndex >= 0 && nameIndex < strings.size() && elementName.equals(strings.get(nameIndex))) return pos;
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
            if (valueType == TYPE_STRING && value >= 0 && value < strings.size()) return strings.get(value);
        }
        return null;
    }

    private static int[] buildResourceMapIds(byte[] data, int off, int size, int stringCount, int nameIndex) throws IOException {
        int[] ids = new int[Math.max(stringCount, nameIndex + 1)];
        if (off >= 0 && size >= 8) {
            int existing = (size - 8) / 4;
            if (existing > ids.length) {
                int[] expanded = new int[existing];
                System.arraycopy(ids, 0, expanded, 0, ids.length);
                ids = expanded;
            }
            for (int i = 0; i < existing; i++) ids[i] = u32(data, off + 8 + (i * 4));
            if (nameIndex >= ids.length) {
                int[] expanded = new int[nameIndex + 1];
                System.arraycopy(ids, 0, expanded, 0, ids.length);
                ids = expanded;
            }
        }
        ids[nameIndex] = ATTR_NAME;
        return ids;
    }

    private static byte[] resourceMapToBytes(int[] ids) {
        int count = ids == null ? 0 : ids.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + (count * 4));
        writeU32(out, CHUNK_RESOURCE_MAP);
        writeU32(out, 8 + (count * 4));
        if (ids != null) for (int id : ids) writeU32(out, id);
        return out.toByteArray();
    }

    public static byte[] buildStandaloneManifest(String packageName, String appLabel,
                                                 List<String> permissions, boolean debuggable,
                                                 boolean launcherActivity) {
        return buildStandaloneManifest(packageName, appLabel, permissions, debuggable, launcherActivity, true);
    }

    public static byte[] buildStandaloneManifest(String packageName, String appLabel,
                                                 List<String> permissions, boolean debuggable,
                                                 boolean launcherActivity, boolean hasCode) {
        BinaryManifestBuilder b = new BinaryManifestBuilder();
        return b.build(packageName, appLabel, permissions, debuggable, launcherActivity && hasCode, hasCode);
    }

    private static final class BinaryManifestBuilder {
        private final ArrayList<String> strings = new ArrayList<>();
        private final ArrayList<Integer> resourceIds = new ArrayList<>();

        byte[] build(String packageName, String appLabel, List<String> permissions, boolean debuggable, boolean launcherActivity, boolean hasCode) {
            String pkg = packageName == null || packageName.trim().isEmpty() ? "dev.perms.test.generated.permissiontest" : packageName.trim();
            String label = appLabel == null || appLabel.trim().isEmpty() ? "PermsTest Permission Test" : appLabel.trim();
            ArrayList<String> perms = new ArrayList<>();
            if (permissions != null) {
                HashSet<String> seen = new HashSet<>();
                for (String raw : permissions) {
                    String p = raw == null ? "" : raw.trim();
                    if (!p.isEmpty() && seen.add(p)) perms.add(p);
                }
            }

            int androidPrefix = s("android");
            int androidNs = s(ANDROID_NS);
            int manifest = s(MANIFEST);
            int pkgAttr = s("package");
            int usesPermission = s(USES_PERMISSION);
            int application = s(APPLICATION);
            int activity = s("activity");
            int intentFilter = s("intent-filter");
            int action = s("action");
            int category = s("category");
            int name = androidAttr(NAME, ATTR_NAME);
            int labelAttr = androidAttr("label", 0x01010001);
            int hasCodeAttr = androidAttr("hasCode", 0x0101000c);
            int debuggableAttr = androidAttr("debuggable", 0x0101000f);
            int exportedAttr = androidAttr("exported", 0x01010010);
            int mainAction = s("android.intent.action.MAIN");
            int launcherCategory = s("android.intent.category.LAUNCHER");
            int activityName = s("dev.perms.test.generated.PermissionTestActivity");
            int pkgValue = s(pkg);
            int labelValue = s(label);
            ArrayList<Integer> permissionValues = new ArrayList<>();
            for (String p : perms) permissionValues.add(s(p));

            ByteArrayOutputStream chunks = new ByteArrayOutputStream();
            writeNamespace(chunks, CHUNK_START_NAMESPACE, androidPrefix, androidNs);
            writeStartElement(chunks, NO_INDEX, manifest, new Attribute[] { stringAttr(NO_INDEX, pkgAttr, pkgValue) });
            for (Integer permIndex : permissionValues) {
                writeStartElement(chunks, NO_INDEX, usesPermission, new Attribute[] { stringAttr(androidNs, name, permIndex) });
                writeEndElement(chunks, NO_INDEX, usesPermission);
            }
            ArrayList<Attribute> appAttrs = new ArrayList<>();
            appAttrs.add(stringAttr(androidNs, labelAttr, labelValue));
            appAttrs.add(booleanAttr(androidNs, hasCodeAttr, hasCode));
            if (debuggable) appAttrs.add(booleanAttr(androidNs, debuggableAttr, true));
            writeStartElement(chunks, NO_INDEX, application, appAttrs.toArray(new Attribute[0]));
            if (launcherActivity) {
                writeStartElement(chunks, NO_INDEX, activity, new Attribute[] {
                        stringAttr(androidNs, name, activityName),
                        booleanAttr(androidNs, exportedAttr, true)
                });
                writeStartElement(chunks, NO_INDEX, intentFilter, new Attribute[0]);
                writeStartElement(chunks, NO_INDEX, action, new Attribute[] { stringAttr(androidNs, name, mainAction) });
                writeEndElement(chunks, NO_INDEX, action);
                writeStartElement(chunks, NO_INDEX, category, new Attribute[] { stringAttr(androidNs, name, launcherCategory) });
                writeEndElement(chunks, NO_INDEX, category);
                writeEndElement(chunks, NO_INDEX, intentFilter);
                writeEndElement(chunks, NO_INDEX, activity);
            }
            writeEndElement(chunks, NO_INDEX, application);
            writeEndElement(chunks, NO_INDEX, manifest);
            writeNamespace(chunks, CHUNK_END_NAMESPACE, androidPrefix, androidNs);

            byte[] stringPool = stringPoolToBytes(strings);
            byte[] resMap = resourceMapToBytes(toIntArray(resourceIds));
            byte[] body = chunks.toByteArray();
            int size = 8 + stringPool.length + resMap.length + body.length;
            ByteArrayOutputStream out = new ByteArrayOutputStream(size);
            writeU32(out, CHUNK_XML);
            writeU32(out, size);
            out.write(stringPool, 0, stringPool.length);
            out.write(resMap, 0, resMap.length);
            out.write(body, 0, body.length);
            return out.toByteArray();
        }

        private int s(String value) {
            for (int i = 0; i < strings.size(); i++) if (strings.get(i).equals(value)) return i;
            strings.add(value);
            resourceIds.add(0);
            return strings.size() - 1;
        }

        private int androidAttr(String value, int resId) {
            int index = s(value);
            resourceIds.set(index, resId);
            return index;
        }

        private Attribute stringAttr(int ns, int name, int value) {
            return newStringAttribute(ns, name, value);
        }

        private Attribute booleanAttr(int ns, int name, boolean value) {
            Attribute attr = new Attribute();
            attr.nsIndex = ns;
            attr.nameIndex = name;
            attr.rawValueIndex = NO_INDEX;
            attr.valueType = 0x12;
            attr.valueData = value ? 0xFFFFFFFF : 0;
            return attr;
        }

        private void writeNamespace(ByteArrayOutputStream out, int type, int prefix, int uri) {
            writeU32(out, type);
            writeU32(out, 24);
            writeU32(out, 0);
            writeU32(out, NO_INDEX);
            writeU32(out, prefix);
            writeU32(out, uri);
        }
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list == null ? 0 : list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i) == null ? 0 : list.get(i);
        return out;
    }

    private static byte[] stringPoolToBytes(List<String> strings) {
        List<byte[]> encoded = new ArrayList<>();
        int stringsBytes = 0;
        for (String s : strings) {
            byte[] e = encodeUtf8String(s == null ? "" : s);
            encoded.add(e);
            stringsBytes += e.length;
        }
        int offsetsSize = strings.size() * 4;
        int stringsStart = 28 + offsetsSize;
        int paddedStringsBytes = align4(stringsBytes);
        int chunkSize = stringsStart + paddedStringsBytes;
        ByteArrayOutputStream out = new ByteArrayOutputStream(chunkSize);
        writeU16(out, CHUNK_STRING_POOL & 0xffff);
        writeU16(out, 28);
        writeU32(out, chunkSize);
        writeU32(out, strings.size());
        writeU32(out, 0);
        writeU32(out, UTF8_FLAG);
        writeU32(out, stringsStart);
        writeU32(out, 0);
        int running = 0;
        for (byte[] e : encoded) {
            writeU32(out, running);
            running += e.length;
        }
        for (byte[] e : encoded) out.write(e, 0, e.length);
        while ((out.size() & 3) != 0) out.write(0);
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
        return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8) | ((data[off + 2] & 0xff) << 16) | ((data[off + 3] & 0xff) << 24);
    }

    private static void writeU16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
    }

    private static void writeU32(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
        out.write((v >>> 16) & 0xff);
        out.write((v >>> 24) & 0xff);
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

    private static void writeLength8(ByteArrayOutputStream out, int len) {
        if (len > 0x7f) {
            out.write(((len >>> 8) & 0x7f) | 0x80);
            out.write(len & 0xff);
        } else {
            out.write(len & 0x7f);
        }
    }

    private static int align4(int n) {
        return (n + 3) & ~3;
    }

    private static final class Attribute {
        int nsIndex;
        int nameIndex;
        int rawValueIndex;
        int valueType;
        int valueData;
    }

    private static final class StringPoolChunk {
        final int headerSize;
        final int chunkSize;
        final int styleCount;
        final int flags;
        final int stylesStart;
        final int[] styleOffsets;
        final byte[] styleData;
        final List<String> strings;

        StringPoolChunk(int headerSize, int chunkSize, int styleCount, int flags, int stylesStart,
                        int[] styleOffsets, byte[] styleData, List<String> strings) {
            this.headerSize = headerSize;
            this.chunkSize = chunkSize;
            this.styleCount = styleCount;
            this.flags = flags;
            this.stylesStart = stylesStart;
            this.styleOffsets = styleOffsets;
            this.styleData = styleData;
            this.strings = strings;
        }

        int indexOf(String s) {
            for (int i = 0; i < strings.size(); i++) if (s.equals(strings.get(i))) return i;
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
            if (headerSize < 28 || chunkSize < headerSize || off + chunkSize > data.length) throw new IOException("String pool header is malformed.");
            int stringOffsetsBase = off + headerSize;
            int styleOffsetsBase = stringOffsetsBase + (stringCount * 4);
            if (styleOffsetsBase + (styleCount * 4) > off + chunkSize) throw new IOException("String pool offsets are malformed.");
            boolean utf8 = (flags & UTF8_FLAG) != 0;
            int stringsBase = off + stringsStart;
            int stringsEnd = stylesStart == 0 ? off + chunkSize : off + stylesStart;
            if (stringsBase < off || stringsBase > stringsEnd || stringsEnd > off + chunkSize) throw new IOException("String pool data is malformed.");
            List<String> strings = new ArrayList<>(stringCount);
            for (int i = 0; i < stringCount; i++) {
                int so = u32(data, stringOffsetsBase + (i * 4));
                int sp = stringsBase + so;
                if (sp < stringsBase || sp >= stringsEnd) throw new IOException("String offset is malformed.");
                strings.add(utf8 ? readUtf8String(data, sp, stringsEnd) : readUtf16String(data, sp, stringsEnd));
            }
            int[] styleOffsets = new int[styleCount];
            for (int i = 0; i < styleCount; i++) styleOffsets[i] = u32(data, styleOffsetsBase + (i * 4));
            byte[] styleData = new byte[0];
            if (styleCount > 0 && stylesStart > 0) {
                int styleDataStart = off + stylesStart;
                styleData = slice(data, styleDataStart, off + chunkSize - styleDataStart);
            }
            return new StringPoolChunk(headerSize, chunkSize, styleCount, flags, stylesStart, styleOffsets, styleData, strings);
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

    private static byte[] encodeUtf16String(String s) {
        byte[] utf16 = s.getBytes(StandardCharsets.UTF_16LE);
        ByteArrayOutputStream out = new ByteArrayOutputStream(utf16.length + 8);
        writeLength16(out, s.length());
        out.write(utf16, 0, utf16.length);
        out.write(0);
        out.write(0);
        return out.toByteArray();
    }

    private static void writeLength16(ByteArrayOutputStream out, int len) {
        if (len > 0x7fff) {
            writeU16(out, ((len >>> 16) & 0x7fff) | 0x8000);
            writeU16(out, len & 0xffff);
        } else {
            writeU16(out, len & 0x7fff);
        }
    }
}
