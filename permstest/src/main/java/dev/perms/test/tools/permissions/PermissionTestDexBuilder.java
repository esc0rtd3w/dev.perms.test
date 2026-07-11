package dev.perms.test.tools.permissions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.zip.Adler32;

/**
 * Builds the tiny classes.dex used by generated Permission Tester APKs.
 *
 * The generated APK contains one Activity class that inherits the platform
 * Activity implementation. This keeps runtime APK generation independent from
 * Gradle, d8, or external Android build tools while still producing a real
 * code-bearing, launchable APK.
 */
final class PermissionTestDexBuilder {
    static final String ACTIVITY_CLASS_NAME = "dev.perms.test.generated.PermissionTestActivity";
    private static final String ACTIVITY_DESCRIPTOR = "Ldev/perms/test/generated/PermissionTestActivity;";
    private static final String SUPER_DESCRIPTOR = "Landroid/app/Activity;";
    private static final String VOID_DESCRIPTOR = "V";
    private static final String INIT_NAME = "<init>";
    private static final String SOURCE_NAME = "PermissionTestActivity.java";

    private PermissionTestDexBuilder() {
    }

    static byte[] build() throws IOException {
        return new DexBuilder().build();
    }

    private static final class DexBuilder {
        private final ArrayList<String> strings = new ArrayList<>();
        private final ArrayList<String> types = new ArrayList<>();
        private final ArrayList<MethodRef> methods = new ArrayList<>();
        private final LinkedHashMap<String, Integer> stringIndex = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> typeIndex = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> methodIndex = new LinkedHashMap<>();

        byte[] build() throws IOException {
            addString(INIT_NAME);
            addString(ACTIVITY_DESCRIPTOR);
            addString(SOURCE_NAME);
            addString(SUPER_DESCRIPTOR);
            addString(VOID_DESCRIPTOR);
            sortStrings();

            addType(VOID_DESCRIPTOR);
            addType(SUPER_DESCRIPTOR);
            addType(ACTIVITY_DESCRIPTOR);
            sortTypes();

            int protoReturnType = typeIndex.get(VOID_DESCRIPTOR);
            int protoShortyIndex = stringIndex.get(VOID_DESCRIPTOR);
            addMethod(SUPER_DESCRIPTOR, INIT_NAME);
            addMethod(ACTIVITY_DESCRIPTOR, INIT_NAME);
            sortMethods();
            int superCtor = methodIndex.get(SUPER_DESCRIPTOR + "->" + INIT_NAME);
            int thisCtor = methodIndex.get(ACTIVITY_DESCRIPTOR + "->" + INIT_NAME);

            Layout layout = layout(thisCtor);
            ByteArrayOutputStream out = new ByteArrayOutputStream(layout.fileSize);

            writeHeader(out, layout);
            writeStringIds(out, layout);
            writeTypeIds(out, layout.typeIdsOff);
            writeProtoIds(out, layout.protoIdsOff, protoShortyIndex, protoReturnType);
            writeMethodIds(out, layout.methodIdsOff);
            writeClassDefs(out, layout.classDefsOff, layout.classDataOff);
            writePaddingTo(out, layout.dataOff);
            writeStringDataItems(out, layout.stringDataOffsets);
            writePaddingTo(out, layout.classDataOff);
            writeClassData(out, thisCtor, layout.codeOff);
            writePaddingTo(out, layout.codeOff);
            writeConstructorCode(out, superCtor);
            writePaddingTo(out, layout.mapOff);
            writeMapList(out, layout);

            byte[] dex = out.toByteArray();
            if (dex.length != layout.fileSize) throw new IOException("Generated dex size mismatch: got " + dex.length + ", expected " + layout.fileSize);
            fillSignatureAndChecksum(dex);
            return dex;
        }

        private Layout layout(int thisCtorMethodIndex) throws IOException {
            Layout l = new Layout();
            l.stringIdsOff = 0x70;
            l.typeIdsOff = l.stringIdsOff + strings.size() * 4;
            l.protoIdsOff = l.typeIdsOff + types.size() * 4;
            l.methodIdsOff = l.protoIdsOff + 12;
            l.classDefsOff = l.methodIdsOff + methods.size() * 8;
            l.dataOff = align4(l.classDefsOff + 32);
            l.stringDataOffsets = new int[strings.size()];
            int pos = l.dataOff;
            for (int i = 0; i < strings.size(); i++) {
                l.stringDataOffsets[i] = pos;
                pos += stringDataSize(strings.get(i));
            }
            l.classDataOff = pos;
            int classDataSize = classDataSize(thisCtorMethodIndex, align4(pos + 8));
            int codeOff;
            while (true) {
                codeOff = align4(pos + classDataSize);
                int adjustedSize = classDataSize(thisCtorMethodIndex, codeOff);
                if (adjustedSize == classDataSize) break;
                classDataSize = adjustedSize;
            }
            pos += classDataSize;
            l.codeOff = codeOff;
            pos = l.codeOff + 24;
            l.mapOff = align4(pos);
            pos = l.mapOff + 4 + (12 * 10);
            l.fileSize = pos;
            l.dataSize = l.fileSize - l.dataOff;
            return l;
        }

        private int addString(String value) {
            Integer index = stringIndex.get(value);
            if (index != null) return index;
            strings.add(value);
            index = strings.size() - 1;
            stringIndex.put(value, index);
            return index;
        }

        private void sortStrings() {
            Collections.sort(strings);
            stringIndex.clear();
            for (int i = 0; i < strings.size(); i++) stringIndex.put(strings.get(i), i);
        }

        private void addType(String descriptor) {
            if (typeIndex.containsKey(descriptor)) return;
            types.add(descriptor);
            typeIndex.put(descriptor, types.size() - 1);
        }

        private void sortTypes() {
            Collections.sort(types, Comparator.comparingInt(o -> stringIndex.get(o)));
            typeIndex.clear();
            for (int i = 0; i < types.size(); i++) typeIndex.put(types.get(i), i);
        }

        private void addMethod(String ownerDescriptor, String name) {
            String key = ownerDescriptor + "->" + name;
            if (methodIndex.containsKey(key)) return;
            methods.add(new MethodRef(ownerDescriptor, name));
            methodIndex.put(key, methods.size() - 1);
        }

        private void sortMethods() {
            Collections.sort(methods, (a, b) -> {
                int ca = typeIndex.get(a.ownerDescriptor);
                int cb = typeIndex.get(b.ownerDescriptor);
                if (ca != cb) return ca - cb;
                int pa = 0;
                int pb = 0;
                if (pa != pb) return pa - pb;
                return stringIndex.get(a.name) - stringIndex.get(b.name);
            });
            methodIndex.clear();
            for (int i = 0; i < methods.size(); i++) {
                MethodRef ref = methods.get(i);
                methodIndex.put(ref.ownerDescriptor + "->" + ref.name, i);
            }
        }

        private void writeHeader(ByteArrayOutputStream out, Layout l) throws IOException {
            writeBytes(out, new byte[]{'d','e','x','\n','0','3','5',0});
            writeU32(out, 0);
            for (int i = 0; i < 20; i++) out.write(0);
            writeU32(out, l.fileSize);
            writeU32(out, 0x70);
            writeU32(out, 0x12345678);
            writeU32(out, 0);
            writeU32(out, 0);
            writeU32(out, l.mapOff);
            writeU32(out, strings.size());
            writeU32(out, l.stringIdsOff);
            writeU32(out, types.size());
            writeU32(out, l.typeIdsOff);
            writeU32(out, 1);
            writeU32(out, l.protoIdsOff);
            writeU32(out, 0);
            writeU32(out, 0);
            writeU32(out, methods.size());
            writeU32(out, l.methodIdsOff);
            writeU32(out, 1);
            writeU32(out, l.classDefsOff);
            writeU32(out, l.dataSize);
            writeU32(out, l.dataOff);
        }

        private void writeStringIds(ByteArrayOutputStream out, Layout l) throws IOException {
            assertOffset(out, l.stringIdsOff);
            for (int offset : l.stringDataOffsets) writeU32(out, offset);
        }

        private void writeTypeIds(ByteArrayOutputStream out, int expectedOff) throws IOException {
            assertOffset(out, expectedOff);
            for (String descriptor : types) writeU32(out, stringIndex.get(descriptor));
        }

        private void writeProtoIds(ByteArrayOutputStream out, int expectedOff, int shortyIndex, int returnTypeIndex) throws IOException {
            assertOffset(out, expectedOff);
            writeU32(out, shortyIndex);
            writeU32(out, returnTypeIndex);
            writeU32(out, 0);
        }

        private void writeMethodIds(ByteArrayOutputStream out, int expectedOff) throws IOException {
            assertOffset(out, expectedOff);
            for (MethodRef ref : methods) {
                writeU16(out, typeIndex.get(ref.ownerDescriptor));
                writeU16(out, 0);
                writeU32(out, stringIndex.get(ref.name));
            }
        }

        private void writeClassDefs(ByteArrayOutputStream out, int expectedOff, int classDataOff) throws IOException {
            assertOffset(out, expectedOff);
            writeU32(out, typeIndex.get(ACTIVITY_DESCRIPTOR));
            writeU32(out, 0x00000001);
            writeU32(out, typeIndex.get(SUPER_DESCRIPTOR));
            writeU32(out, 0);
            writeU32(out, stringIndex.get(SOURCE_NAME));
            writeU32(out, 0);
            writeU32(out, classDataOff);
            writeU32(out, 0);
        }

        private void writeStringDataItems(ByteArrayOutputStream out, int[] offsets) throws IOException {
            for (int i = 0; i < strings.size(); i++) {
                assertOffset(out, offsets[i]);
                writeStringData(out, strings.get(i));
            }
        }

        private void writeClassData(ByteArrayOutputStream out, int thisCtorMethodIndex, int codeOff) throws IOException {
            writeUleb128(out, 0);
            writeUleb128(out, 0);
            writeUleb128(out, 1);
            writeUleb128(out, 0);
            writeUleb128(out, thisCtorMethodIndex);
            writeUleb128(out, 0x00010001);
            writeUleb128(out, codeOff);
        }

        private int classDataSize(int thisCtorMethodIndex, int codeOff) throws IOException {
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            writeClassData(tmp, thisCtorMethodIndex, codeOff);
            return tmp.size();
        }

        private void writeConstructorCode(ByteArrayOutputStream out, int superCtorMethodIndex) throws IOException {
            writeU16(out, 1);
            writeU16(out, 1);
            writeU16(out, 1);
            writeU16(out, 0);
            writeU32(out, 0);
            writeU32(out, 4);
            // invoke-direct {v0}, super.<init>()
            // Format 35c packs argument count in the high nibble of the second byte.
            writeU16(out, 0x1070);
            writeU16(out, superCtorMethodIndex);
            writeU16(out, 0x0000);
            writeU16(out, 0x000e);
        }

        private void writeMapList(ByteArrayOutputStream out, Layout l) throws IOException {
            assertOffset(out, l.mapOff);
            writeU32(out, 10);
            writeMap(out, 0x0000, 1, 0);
            writeMap(out, 0x0001, strings.size(), l.stringIdsOff);
            writeMap(out, 0x0002, types.size(), l.typeIdsOff);
            writeMap(out, 0x0003, 1, l.protoIdsOff);
            writeMap(out, 0x0005, methods.size(), l.methodIdsOff);
            writeMap(out, 0x0006, 1, l.classDefsOff);
            writeMap(out, 0x2002, strings.size(), l.stringDataOffsets[0]);
            writeMap(out, 0x2000, 1, l.classDataOff);
            writeMap(out, 0x2001, 1, l.codeOff);
            writeMap(out, 0x1000, 1, l.mapOff);
        }

        private void writeMap(ByteArrayOutputStream out, int type, int size, int offset) throws IOException {
            writeU16(out, type);
            writeU16(out, 0);
            writeU32(out, size);
            writeU32(out, offset);
        }
    }

    private static final class MethodRef {
        final String ownerDescriptor;
        final String name;

        MethodRef(String ownerDescriptor, String name) {
            this.ownerDescriptor = ownerDescriptor;
            this.name = name;
        }
    }

    private static final class Layout {
        int fileSize;
        int stringIdsOff;
        int typeIdsOff;
        int protoIdsOff;
        int methodIdsOff;
        int classDefsOff;
        int dataOff;
        int dataSize;
        int[] stringDataOffsets;
        int classDataOff;
        int codeOff;
        int mapOff;
    }

    private static int align4(int value) {
        return (value + 3) & ~3;
    }

    private static int stringDataSize(String value) throws IOException {
        return uleb128Size(value.length()) + value.getBytes("UTF-8").length + 1;
    }

    private static void writeStringData(ByteArrayOutputStream out, String value) throws IOException {
        writeUleb128(out, value.length());
        writeBytes(out, value.getBytes("UTF-8"));
        out.write(0);
    }

    private static int uleb128Size(int value) {
        int size = 1;
        while ((value >>>= 7) != 0) size++;
        return size;
    }

    private static void writeUleb128(ByteArrayOutputStream out, int value) {
        int remaining = value >>> 7;
        while (remaining != 0) {
            out.write((value & 0x7f) | 0x80);
            value = remaining;
            remaining >>>= 7;
        }
        out.write(value & 0x7f);
    }

    private static void writePaddingTo(ByteArrayOutputStream out, int offset) throws IOException {
        while (out.size() < offset) out.write(0);
        assertOffset(out, offset);
    }

    private static void assertOffset(ByteArrayOutputStream out, int expected) throws IOException {
        if (out.size() != expected) {
            throw new IOException("Unexpected dex offset: got " + out.size() + ", expected " + expected);
        }
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void writeU32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private static void fillSignatureAndChecksum(byte[] dex) throws IOException {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(dex, 32, dex.length - 32);
            byte[] sig = sha1.digest();
            System.arraycopy(sig, 0, dex, 12, 20);
            Adler32 adler32 = new Adler32();
            adler32.update(dex, 12, dex.length - 12);
            int checksum = (int) adler32.getValue();
            dex[8] = (byte) (checksum & 0xff);
            dex[9] = (byte) ((checksum >>> 8) & 0xff);
            dex[10] = (byte) ((checksum >>> 16) & 0xff);
            dex[11] = (byte) ((checksum >>> 24) & 0xff);
        } catch (Throwable t) {
            throw new IOException("Unable to finalize generated dex: " + t.getMessage(), t);
        }
    }
}
