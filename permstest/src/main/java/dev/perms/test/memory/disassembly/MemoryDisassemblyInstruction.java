package dev.perms.test.memory.disassembly;

import java.util.Locale;

/**
 * Immutable instruction model for the memory disassembly overlay.
 *
 * The overlay reads raw process memory, so this model intentionally avoids file-specific concepts such
 * as sections, symbols, or relocation tables.  Higher-level analyzers can still attach comments or
 * references to this instruction by using its decoded address/register/target metadata.
 */
final class MemoryDisassemblyInstruction {
    static final long NO_ADDRESS = Long.MIN_VALUE;
    static final int NO_REGISTER = -1;

    final long address;
    final int size;
    final long word;
    final String mnemonic;
    final String operands;
    final String displayText;
    final boolean branch;
    final boolean call;
    final boolean conditionalBranch;
    final boolean returns;
    final boolean load;
    final boolean store;
    final boolean pcRelative;
    final long directTargetAddress;
    final String directTargetKind;
    final int destinationRegister;
    final int sourceRegister;
    final int baseRegister;
    final long immediate;

    private MemoryDisassemblyInstruction(Builder b) {
        address = b.address;
        size = b.size;
        word = b.word & 0xffffffffL;
        mnemonic = safe(b.mnemonic);
        operands = safe(b.operands);
        displayText = operands.isEmpty() ? mnemonic : mnemonic + " " + operands;
        branch = b.branch;
        call = b.call;
        conditionalBranch = b.conditionalBranch;
        returns = b.returns;
        load = b.load;
        store = b.store;
        pcRelative = b.pcRelative;
        directTargetAddress = b.directTargetAddress;
        directTargetKind = safe(b.directTargetKind);
        destinationRegister = b.destinationRegister;
        sourceRegister = b.sourceRegister;
        baseRegister = b.baseRegister;
        immediate = b.immediate;
    }

    static Builder builder(long address, long word) {
        return new Builder(address, word);
    }

    static MemoryDisassemblyInstruction withDisplay(MemoryDisassemblyInstruction base, String mnemonic, String operands, int size) {
        if (base == null) return builder(0L, 0L).opcode(mnemonic, operands).size(size).build();
        Builder b = builder(base.address, base.word)
                .size(size <= 0 ? base.size : size)
                .opcode(mnemonic, operands);
        b.branch = base.branch;
        b.call = base.call;
        b.conditionalBranch = base.conditionalBranch;
        b.returns = base.returns;
        b.load = base.load;
        b.store = base.store;
        b.pcRelative = base.pcRelative;
        b.directTargetAddress = base.directTargetAddress;
        b.directTargetKind = base.directTargetKind;
        b.destinationRegister = base.destinationRegister;
        b.sourceRegister = base.sourceRegister;
        b.baseRegister = base.baseRegister;
        b.immediate = base.immediate;
        return b.build();
    }

    boolean hasDirectTarget() {
        return directTargetAddress != NO_ADDRESS;
    }

    String bytesText() {
        return String.format(Locale.US,
                "%02x %02x %02x %02x",
                word & 0xff,
                (word >> 8) & 0xff,
                (word >> 16) & 0xff,
                (word >> 24) & 0xff);
    }

    String wordText() {
        return String.format(Locale.US, "%08x", word & 0xffffffffL);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class Builder {
        private final long address;
        private final long word;
        private int size = 4;
        private String mnemonic = "unknown";
        private String operands = "";
        private boolean branch;
        private boolean call;
        private boolean conditionalBranch;
        private boolean returns;
        private boolean load;
        private boolean store;
        private boolean pcRelative;
        private long directTargetAddress = NO_ADDRESS;
        private String directTargetKind = "";
        private int destinationRegister = NO_REGISTER;
        private int sourceRegister = NO_REGISTER;
        private int baseRegister = NO_REGISTER;
        private long immediate;

        private Builder(long address, long word) {
            this.address = address;
            this.word = word;
        }

        Builder opcode(String mnemonic, String operands) {
            this.mnemonic = mnemonic;
            this.operands = operands;
            return this;
        }

        Builder size(int size) {
            if (size > 0) this.size = size;
            return this;
        }

        Builder branch(boolean call, boolean conditional, long target, String kind) {
            this.branch = true;
            this.call = call;
            this.conditionalBranch = conditional;
            this.directTargetAddress = target;
            this.directTargetKind = kind;
            return this;
        }

        Builder returns() {
            this.returns = true;
            return this;
        }

        Builder pcRelative(int destinationRegister, long target, String kind) {
            this.pcRelative = true;
            this.destinationRegister = destinationRegister;
            this.directTargetAddress = target;
            this.directTargetKind = kind;
            return this;
        }

        Builder loadStore(boolean load, boolean store, int destinationRegister, int baseRegister, long immediate) {
            this.load = load;
            this.store = store;
            this.destinationRegister = destinationRegister;
            this.baseRegister = baseRegister;
            this.immediate = immediate;
            return this;
        }

        Builder registers(int destinationRegister, int sourceRegister, long immediate) {
            this.destinationRegister = destinationRegister;
            this.sourceRegister = sourceRegister;
            this.immediate = immediate;
            return this;
        }

        MemoryDisassemblyInstruction build() {
            return new MemoryDisassemblyInstruction(this);
        }
    }
}
