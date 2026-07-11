package dev.perms.test.memory.disassembly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight cross-reference analyzer for decoded AArch64 instructions.
 *
 * This class keeps analysis separate from the overlay UI.  It is conservative by design: branch and
 * literal targets are exact, and ADRP-derived references are emitted only when a nearby ADD/LDR/STR
 * completes the page-relative address calculation without the base register being overwritten first.
 */
final class MemoryDisassemblyReferenceAnalyzer {
    private static final int ADRP_LOOKAHEAD_INSTRUCTIONS = 10;

    List<MemoryDisassemblyReference> collectReferences(List<MemoryDisassemblyInstruction> instructions) {
        ArrayList<MemoryDisassemblyReference> out = new ArrayList<>();
        if (instructions == null || instructions.isEmpty()) return out;

        for (int i = 0; i < instructions.size(); i++) {
            MemoryDisassemblyInstruction insn = instructions.get(i);
            if (insn == null) continue;
            if (insn.hasDirectTarget()) {
                String kind = insn.directTargetKind.isEmpty() ? (insn.branch ? "branch" : "target") : insn.directTargetKind;
                out.add(new MemoryDisassemblyReference(insn.address, insn.directTargetAddress, kind, "direct target", insn));
            }
            if ("adrp".equals(insn.mnemonic) && insn.destinationRegister != MemoryDisassemblyInstruction.NO_REGISTER && insn.hasDirectTarget()) {
                collectAdrpDerivedReferences(instructions, i, insn, out);
            }
        }
        return out;
    }

    List<MemoryDisassemblyReference> findReferences(List<MemoryDisassemblyInstruction> instructions, long target) {
        ArrayList<MemoryDisassemblyReference> matched = new ArrayList<>();
        for (MemoryDisassemblyReference ref : collectReferences(instructions)) {
            if (ref != null && ref.matches(target)) matched.add(ref);
        }
        return matched;
    }

    Map<Long, List<MemoryDisassemblyReference>> commentsBySource(List<MemoryDisassemblyInstruction> instructions) {
        LinkedHashMap<Long, List<MemoryDisassemblyReference>> out = new LinkedHashMap<>();
        for (MemoryDisassemblyReference ref : collectReferences(instructions)) {
            List<MemoryDisassemblyReference> list = out.get(ref.sourceAddress);
            if (list == null) {
                list = new ArrayList<>();
                out.put(ref.sourceAddress, list);
            }
            list.add(ref);
        }
        return out;
    }

    String buildSummary(List<MemoryDisassemblyInstruction> instructions, long baseAddress, int byteCount) {
        int total = instructions == null ? 0 : instructions.size();
        int branches = 0;
        int calls = 0;
        int returns = 0;
        int loads = 0;
        int stores = 0;
        int pcRel = 0;
        if (instructions != null) {
            for (MemoryDisassemblyInstruction insn : instructions) {
                if (insn == null) continue;
                if (insn.branch) branches++;
                if (insn.call) calls++;
                if (insn.returns) returns++;
                if (insn.load) loads++;
                if (insn.store) stores++;
                if (insn.pcRelative) pcRel++;
            }
        }
        List<MemoryDisassemblyReference> refs = collectReferences(instructions);
        StringBuilder sb = new StringBuilder();
        sb.append("Disassembly analysis\n");
        sb.append("Range: ").append(MemoryDisassemblyReference.formatHex(baseAddress))
                .append(" - ").append(MemoryDisassemblyReference.formatHex(baseAddress + Math.max(0, byteCount)))
                .append("\n");
        sb.append("Instructions: ").append(total)
                .append("\nBranches: ").append(branches)
                .append("\nCalls: ").append(calls)
                .append("\nReturns: ").append(returns)
                .append("\nLoads: ").append(loads)
                .append("\nStores: ").append(stores)
                .append("\nPC-relative address builders: ").append(pcRel)
                .append("\nReferences: ").append(refs.size())
                .append("\n\n");
        if (refs.isEmpty()) {
            sb.append("No direct references found in the current read window.");
        } else {
            sb.append("References found in current read window:\n");
            for (MemoryDisassemblyReference ref : refs) {
                sb.append(ref.oneLine()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private void collectAdrpDerivedReferences(List<MemoryDisassemblyInstruction> instructions,
                                              int startIndex,
                                              MemoryDisassemblyInstruction adrp,
                                              List<MemoryDisassemblyReference> out) {
        int register = adrp.destinationRegister;
        long pageBase = adrp.directTargetAddress;
        int max = Math.min(instructions.size(), startIndex + 1 + ADRP_LOOKAHEAD_INSTRUCTIONS);
        for (int i = startIndex + 1; i < max; i++) {
            MemoryDisassemblyInstruction next = instructions.get(i);
            if (next == null) continue;
            if (next.sourceRegister == register && next.destinationRegister != MemoryDisassemblyInstruction.NO_REGISTER
                    && ("add".equals(next.mnemonic) || "adds".equals(next.mnemonic))) {
                long target = pageBase + next.immediate;
                out.add(new MemoryDisassemblyReference(next.address, target, "adrp+add", "base " + MemoryDisassemblyReference.formatHex(pageBase), next));
            } else if (next.baseRegister == register && (next.load || next.store)) {
                long target = pageBase + next.immediate;
                out.add(new MemoryDisassemblyReference(next.address, target, next.load ? "adrp+ldr" : "adrp+str", "base " + MemoryDisassemblyReference.formatHex(pageBase), next));
            }
            if (registerOverwritten(next, register)) break;
        }
    }

    private static boolean registerOverwritten(MemoryDisassemblyInstruction insn, int register) {
        if (insn == null || register == MemoryDisassemblyInstruction.NO_REGISTER) return false;
        if (insn.destinationRegister != register) return false;
        if ("adrp".equals(insn.mnemonic) || "adr".equals(insn.mnemonic)) return true;
        if ("movz".equals(insn.mnemonic) || "movn".equals(insn.mnemonic)) return true;
        if ("add".equals(insn.mnemonic) || "adds".equals(insn.mnemonic) || "sub".equals(insn.mnemonic) || "subs".equals(insn.mnemonic)) {
            return insn.sourceRegister != register;
        }
        return insn.load;
    }
}
