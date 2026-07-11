package dev.perms.test.memory.disassembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small AArch64 decoder used by PermsTest memory overlays.
 *
 * This is intentionally isolated from the overlay UI so it can be reused by future memory scanners,
 * file-backed analyzers, or payload tooling.  It currently decodes the instruction families that are
 * most useful while working with live Android/ARM64 process memory: branches, PC-relative address
 * construction, common immediates, and load/store forms.
 */
final class MemoryAarch64Disassembler implements MemoryDisassemblyDecoder {
    @Override
    public List<MemoryDisassemblyInstruction> disassemble(byte[] bytes, long baseAddress) {
        ArrayList<MemoryDisassemblyInstruction> out = new ArrayList<>();
        int count = bytes == null ? 0 : bytes.length;
        for (int i = 0; i + 3 < count; i += 4) {
            long address = baseAddress + i;
            long word = readLittleEndianWord(bytes, i);
            out.add(decode(address, word));
        }
        return out;
    }


    @Override
    public String name() {
        return "Java AArch64 fallback";
    }

    @Override
    public String detail() {
        return "project-local Java decoder";
    }

    MemoryDisassemblyInstruction decode(long address, long word) {
        long w = word & 0xffffffffL;
        MemoryDisassemblyInstruction.Builder b = MemoryDisassemblyInstruction.builder(address, w);

        if (w == 0xd503201fL) return b.opcode("nop", "").build();
        if (w == 0xd65f03c0L) return b.opcode("ret", "").returns().build();
        if ((w & 0xfffffc1fL) == 0xd61f0000L) {
            String rn = regName((w >> 5) & 0x1fL);
            return b.opcode("br", rn).branch(false, false, MemoryDisassemblyInstruction.NO_ADDRESS, "indirect-branch").build();
        }
        if ((w & 0xfffffc1fL) == 0xd63f0000L) {
            String rn = regName((w >> 5) & 0x1fL);
            return b.opcode("blr", rn).branch(true, false, MemoryDisassemblyInstruction.NO_ADDRESS, "indirect-call").build();
        }
        if ((w & 0xffffffe0L) == 0xd4000001L) return b.opcode("svc", "#" + ((w >> 5) & 0xffffL)).build();
        if ((w & 0xffffffe0L) == 0xd4200000L) return b.opcode("brk", "#" + ((w >> 5) & 0xffffL)).build();

        if ((w & 0x7c000000L) == 0x14000000L) {
            boolean call = (w & 0x80000000L) != 0;
            long imm26 = signExtend(w & 0x03ffffffL, 26) << 2;
            long target = address + imm26;
            String op = call ? "bl" : "b";
            return b.opcode(op, formatHex(target)).branch(call, false, target, call ? "call" : "branch").build();
        }
        if ((w & 0xff000010L) == 0x54000000L) {
            long imm19 = signExtend((w >> 5) & 0x7ffffL, 19) << 2;
            long target = address + imm19;
            String op = "b." + conditionName(w & 0xfL);
            return b.opcode(op, formatHex(target)).branch(false, true, target, "conditional-branch").build();
        }
        if ((w & 0x7e000000L) == 0x34000000L) {
            boolean nz = (w & 0x01000000L) != 0;
            boolean is64 = (w & 0x80000000L) != 0;
            int rt = (int) (w & 0x1fL);
            long imm19 = signExtend((w >> 5) & 0x7ffffL, 19) << 2;
            long target = address + imm19;
            String op = nz ? "cbnz" : "cbz";
            return b.opcode(op, regName(rt, is64) + ", " + formatHex(target))
                    .branch(false, true, target, "compare-branch")
                    .registers(MemoryDisassemblyInstruction.NO_REGISTER, rt, 0)
                    .build();
        }
        if ((w & 0x7e000000L) == 0x36000000L) {
            boolean nz = (w & 0x01000000L) != 0;
            boolean is64 = (w & 0x80000000L) != 0;
            int rt = (int) (w & 0x1fL);
            long bit = ((w >> 19) & 0x1fL) | (is64 ? 0x20L : 0L);
            long imm14 = signExtend((w >> 5) & 0x3fffL, 14) << 2;
            long target = address + imm14;
            String op = nz ? "tbnz" : "tbz";
            return b.opcode(op, regName(rt, is64) + ", #" + bit + ", " + formatHex(target))
                    .branch(false, true, target, "test-branch")
                    .registers(MemoryDisassemblyInstruction.NO_REGISTER, rt, bit)
                    .build();
        }
        if ((w & 0x1f000000L) == 0x10000000L) {
            boolean page = (w & 0x80000000L) != 0;
            int rd = (int) (w & 0x1fL);
            long imm = ((w >> 29) & 0x3L) | (((w >> 5) & 0x7ffffL) << 2);
            imm = signExtend(imm, 21);
            long target = page ? ((address & ~0xfffL) + (imm << 12)) : (address + imm);
            String op = page ? "adrp" : "adr";
            return b.opcode(op, regName(rd) + ", " + formatHex(target)).pcRelative(rd, target, op).build();
        }

        MemoryDisassemblyInstruction literal = decodeLoadLiteral(address, w, b);
        if (literal != null) return literal;

        if ((w & 0x7f800000L) == 0x52800000L || (w & 0x7f800000L) == 0x72800000L || (w & 0x7f800000L) == 0x12800000L) {
            boolean is64 = (w & 0x80000000L) != 0;
            int rd = (int) (w & 0x1fL);
            long imm16 = (w >> 5) & 0xffffL;
            long shift = ((w >> 21) & 0x3L) * 16L;
            String op = (w & 0x60000000L) == 0x20000000L ? "movn" : ((w & 0x60000000L) == 0x60000000L ? "movk" : "movz");
            return b.opcode(op, regName(rd, is64) + ", #0x" + Long.toHexString(imm16) + (shift == 0 ? "" : ", lsl #" + shift))
                    .registers(rd, MemoryDisassemblyInstruction.NO_REGISTER, imm16 << shift)
                    .build();
        }
        if ((w & 0x7f000000L) == 0x11000000L || (w & 0x7f000000L) == 0x51000000L || (w & 0x7f000000L) == 0x31000000L || (w & 0x7f000000L) == 0x71000000L) {
            boolean sub = (w & 0x40000000L) != 0;
            boolean setFlags = (w & 0x20000000L) != 0;
            boolean is64 = (w & 0x80000000L) != 0;
            int rd = (int) (w & 0x1fL);
            int rn = (int) ((w >> 5) & 0x1fL);
            long imm = (w >> 10) & 0xfffL;
            if (((w >> 22) & 0x3L) == 1L) imm <<= 12;
            String op = sub ? (setFlags ? "subs" : "sub") : (setFlags ? "adds" : "add");
            return b.opcode(op, regName(rd, is64) + ", " + regName(rn, is64) + ", #" + imm)
                    .registers(rd, rn, sub ? -imm : imm)
                    .build();
        }
        if ((w & 0x3b000000L) == 0x39000000L) {
            boolean isLoad = (w & 0x00400000L) != 0;
            int size = (int) ((w >> 30) & 0x3L);
            int rt = (int) (w & 0x1fL);
            int rn = (int) ((w >> 5) & 0x1fL);
            long imm = ((w >> 10) & 0xfffL) << size;
            boolean is64 = size == 3;
            String op = isLoad ? "ldr" : "str";
            return b.opcode(op, regName(rt, is64) + ", [" + regName(rn) + ", #" + imm + "]")
                    .loadStore(isLoad, !isLoad, rt, rn, imm)
                    .build();
        }
        if ((w & 0x7fc00000L) == 0x29000000L || (w & 0x7fc00000L) == 0x29400000L || (w & 0x7fc00000L) == 0xa9000000L || (w & 0x7fc00000L) == 0xa9400000L) {
            boolean isLoad = (w & 0x00400000L) != 0;
            boolean is64 = (w & 0x80000000L) != 0;
            int rt = (int) (w & 0x1fL);
            int rn = (int) ((w >> 5) & 0x1fL);
            long scale = is64 ? 8L : 4L;
            long imm7 = signExtend((w >> 15) & 0x7fL, 7) * scale;
            String op = isLoad ? "ldp" : "stp";
            return b.opcode(op, regName(rt, is64) + ", " + regName((w >> 10) & 0x1fL, is64) + ", [" + regName(rn) + ", #" + imm7 + "]")
                    .loadStore(isLoad, !isLoad, rt, rn, imm7)
                    .build();
        }
        return b.opcode("unknown", "").build();
    }

    private MemoryDisassemblyInstruction decodeLoadLiteral(long address, long w, MemoryDisassemblyInstruction.Builder b) {
        if ((w & 0x3b000000L) != 0x18000000L) return null;
        int rt = (int) (w & 0x1fL);
        long imm19 = signExtend((w >> 5) & 0x7ffffL, 19) << 2;
        long target = address + imm19;
        int opc = (int) ((w >> 30) & 0x3L);
        String op;
        boolean is64;
        switch (opc) {
            case 0:
                op = "ldr";
                is64 = false;
                break;
            case 1:
                op = "ldr";
                is64 = true;
                break;
            case 2:
                op = "ldrsw";
                is64 = true;
                break;
            default:
                op = "prfm";
                is64 = true;
                break;
        }
        return b.opcode(op, regName(rt, is64) + ", " + formatHex(target))
                .pcRelative(rt, target, "literal")
                .loadStore(true, false, rt, MemoryDisassemblyInstruction.NO_REGISTER, imm19)
                .build();
    }

    private static long readLittleEndianWord(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xffL)
                | (((long) bytes[offset + 1] & 0xffL) << 8)
                | (((long) bytes[offset + 2] & 0xffL) << 16)
                | (((long) bytes[offset + 3] & 0xffL) << 24);
    }

    static long signExtend(long value, int bits) {
        long sign = 1L << (bits - 1);
        return (value ^ sign) - sign;
    }

    static String regName(long reg) {
        return regName(reg, true);
    }

    static String regName(long reg, boolean is64) {
        if ((reg & 0x1fL) == 31L) return is64 ? "sp/xzr" : "wsp/wzr";
        return (is64 ? "x" : "w") + (reg & 0x1fL);
    }

    private static String conditionName(long cond) {
        final String[] names = {"eq", "ne", "cs", "cc", "mi", "pl", "vs", "vc", "hi", "ls", "ge", "lt", "gt", "le", "al", "nv"};
        return names[(int) (cond & 0xfL)];
    }

    static String formatHex(long value) {
        return String.format(Locale.US, "0x%x", value);
    }
}
