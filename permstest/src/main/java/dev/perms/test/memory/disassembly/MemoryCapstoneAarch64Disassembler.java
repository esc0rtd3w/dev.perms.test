package dev.perms.test.memory.disassembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AArch64 decoder backed by the native Capstone bridge.
 *
 * Capstone supplies the canonical mnemonic/operand text.  The Java fallback decoder is still used
 * for PermsTest-specific metadata such as branch targets and ADRP reference tracking, because the
 * overlay reference analyzer already consumes that project-local model.
 */
final class MemoryCapstoneAarch64Disassembler implements MemoryDisassemblyDecoder {
    private final MemoryAarch64Disassembler metadataDecoder;

    private MemoryCapstoneAarch64Disassembler(MemoryAarch64Disassembler metadataDecoder) {
        this.metadataDecoder = metadataDecoder == null ? new MemoryAarch64Disassembler() : metadataDecoder;
    }

    static MemoryCapstoneAarch64Disassembler create(MemoryAarch64Disassembler metadataDecoder) {
        if (!MemoryCapstoneNative.isLoaded()) return null;
        return new MemoryCapstoneAarch64Disassembler(metadataDecoder);
    }

    boolean isAvailable() {
        return MemoryCapstoneNative.isLoaded();
    }

    @Override
    public List<MemoryDisassemblyInstruction> disassemble(byte[] bytes, long baseAddress) {
        List<MemoryDisassemblyInstruction> fallback = metadataDecoder.disassemble(bytes, baseAddress);
        String[] nativeRows = MemoryCapstoneNative.disassemble(bytes, baseAddress, 0);
        if (nativeRows.length == 0) return fallback;

        ArrayList<MemoryDisassemblyInstruction> out = new ArrayList<>(nativeRows.length);
        for (String row : nativeRows) {
            NativeInsn nativeInsn = NativeInsn.parse(row);
            if (nativeInsn == null) continue;
            MemoryDisassemblyInstruction metadata = metadataForAddress(fallback, nativeInsn.address);
            if (metadata == null) {
                long word = readLittleEndianWord(bytes, (int) Math.max(0L, nativeInsn.address - baseAddress));
                metadata = MemoryDisassemblyInstruction.builder(nativeInsn.address, word).opcode("unknown", "").build();
            }
            out.add(MemoryDisassemblyInstruction.withDisplay(metadata, nativeInsn.mnemonic, nativeInsn.operands, nativeInsn.size));
        }
        return out.isEmpty() ? fallback : out;
    }

    @Override
    public String name() {
        return "Capstone AArch64";
    }

    @Override
    public String detail() {
        return "native Capstone bridge + Java reference metadata";
    }

    private static MemoryDisassemblyInstruction metadataForAddress(List<MemoryDisassemblyInstruction> instructions, long address) {
        if (instructions == null) return null;
        for (MemoryDisassemblyInstruction instruction : instructions) {
            if (instruction != null && instruction.address == address) return instruction;
        }
        return null;
    }

    private static long readLittleEndianWord(byte[] bytes, int offset) {
        if (bytes == null || offset < 0 || offset + 3 >= bytes.length) return 0L;
        return ((long) bytes[offset] & 0xffL)
                | (((long) bytes[offset + 1] & 0xffL) << 8)
                | (((long) bytes[offset + 2] & 0xffL) << 16)
                | (((long) bytes[offset + 3] & 0xffL) << 24);
    }

    private static final class NativeInsn {
        final long address;
        final int size;
        final String mnemonic;
        final String operands;

        NativeInsn(long address, int size, String mnemonic, String operands) {
            this.address = address;
            this.size = size;
            this.mnemonic = mnemonic == null ? "" : mnemonic;
            this.operands = operands == null ? "" : operands;
        }

        static NativeInsn parse(String row) {
            if (row == null || row.trim().isEmpty()) return null;
            String[] parts = row.split("\\t", -1);
            if (parts.length < 5) return null;
            try {
                long address = Long.parseUnsignedLong(parts[0], 16);
                int size = Integer.parseInt(parts[1]);
                return new NativeInsn(address, Math.max(1, size), parts[3], parts[4]);
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "0x%x %s %s", address, mnemonic, operands).trim();
        }
    }
}
