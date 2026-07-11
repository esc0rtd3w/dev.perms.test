package dev.perms.test.memory.disassembly;

import java.util.Locale;

/**
 * Cross-reference record produced from decoded instruction metadata.
 *
 * Runtime memory does not always have symbols or relocation metadata available, so references are
 * derived from direct branch targets and conservative AArch64 PC-relative address construction.
 */
final class MemoryDisassemblyReference {
    final long sourceAddress;
    final long targetAddress;
    final String kind;
    final String detail;
    final MemoryDisassemblyInstruction instruction;

    MemoryDisassemblyReference(long sourceAddress,
                               long targetAddress,
                               String kind,
                               String detail,
                               MemoryDisassemblyInstruction instruction) {
        this.sourceAddress = sourceAddress;
        this.targetAddress = targetAddress;
        this.kind = kind == null ? "reference" : kind;
        this.detail = detail == null ? "" : detail;
        this.instruction = instruction;
    }

    boolean matches(long target) {
        return targetAddress == target;
    }

    String oneLine() {
        String decoded = instruction == null ? "" : instruction.displayText;
        return String.format(Locale.US,
                "%s -> %s  %-10s %s%s",
                formatHex(sourceAddress),
                formatHex(targetAddress),
                kind,
                decoded,
                detail.isEmpty() ? "" : "  ; " + detail);
    }

    static String formatHex(long value) {
        return "0x" + Long.toHexString(value);
    }
}
