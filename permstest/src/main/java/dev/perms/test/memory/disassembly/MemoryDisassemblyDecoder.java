package dev.perms.test.memory.disassembly;

import java.util.List;

/**
 * Reusable disassembly backend contract for memory views.
 *
 * The overlay should not care whether instructions came from the small Java fallback decoder,
 * a native Capstone bridge, or a future file-backed analyzer.  Keeping this boundary small makes
 * the decoder usable from other project areas without pulling overlay UI code with it.
 */
interface MemoryDisassemblyDecoder {
    List<MemoryDisassemblyInstruction> disassemble(byte[] bytes, long baseAddress);

    String name();

    String detail();

    static MemoryDisassemblyDecoder createBestAvailable() {
        MemoryAarch64Disassembler fallback = new MemoryAarch64Disassembler();
        try {
            MemoryCapstoneAarch64Disassembler capstone = MemoryCapstoneAarch64Disassembler.create(fallback);
            if (capstone != null && capstone.isAvailable()) return capstone;
        } catch (Throwable ignored) {
            // Fallback stays silent here; the overlay shows the selected decoder name after refresh.
        }
        return fallback;
    }
}
