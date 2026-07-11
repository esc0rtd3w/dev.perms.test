package dex

import (
	"fmt"
	"strconv"
	"strings"
)

type Instruction struct {
	Offset                uint32
	Opcode                byte
	Name                  string
	Text                  string
	Length                uint32
	Registers             []int
	IntLiteral            *int64
	StringLiteral         string
	TypeRef               string
	FieldRef              FieldID
	MethodRef             MethodID
	Target                int32
	ArrayDataElementWidth uint16
	ArrayDataValues       []int64
	SwitchKeys            []int32
	SwitchTargets         []int32
}

func (f *File) decodeInstructions(codeOff uint32, insnsSize uint32) []Instruction {
	if insnsSize == 0 || !f.rangeOK(codeOff+16, insnsSize*2) {
		return nil
	}
	start := codeOff + 16
	insns := make([]uint16, int(insnsSize))
	for i := uint32(0); i < insnsSize; i++ {
		insns[i] = f.u16(start + i*2)
	}
	out := make([]Instruction, 0, len(insns))
	for pc := 0; pc < len(insns); {
		ins := f.decodeOne(insns, pc)
		if ins.Length == 0 {
			ins.Length = 1
		}
		if int(ins.Length) > len(insns)-pc {
			ins.Text += " // truncated"
			ins.Length = uint32(len(insns) - pc)
			if ins.Length == 0 {
				break
			}
		}
		out = append(out, ins)
		pc += int(ins.Length)
	}
	return out
}

func (f *File) decodeOne(insns []uint16, pc int) Instruction {
	w0 := insns[pc]
	op := byte(w0 & 0xff)
	high := int((w0 >> 8) & 0xff)
	ins := Instruction{Offset: uint32(pc), Opcode: op, Name: opcodeName(op), Length: opcodeLength(op)}
	regHigh := func() int { return high }
	regLowNibble := func() int { return high & 0x0f }
	regHighNibble := func() int { return (high >> 4) & 0x0f }
	unit := func(i int) uint16 {
		if pc+i >= len(insns) {
			return 0
		}
		return insns[pc+i]
	}
	u32 := func(i int) uint32 {
		return uint32(unit(i)) | uint32(unit(i+1))<<16
	}
	s32 := func(i int) int32 { return int32(u32(i)) }
	setText := func(format string, a ...any) { ins.Text = fmt.Sprintf(format, a...) }

	switch op {
	case 0x00:
		switch high {
		case 0x00:
			setText("nop")
		case 0x01:
			size := int(unit(1))
			firstKey := s32(2)
			ins.Name = "packed-switch-payload"
			ins.Length = uint32(4 + size*2)
			setText("packed-switch-payload size=%d first_key=%d", size, firstKey)
		case 0x02:
			size := int(unit(1))
			ins.Name = "sparse-switch-payload"
			ins.Length = uint32(2 + size*4)
			setText("sparse-switch-payload size=%d", size)
		case 0x03:
			width := unit(1)
			size := u32(2)
			ins.Name = "fill-array-data-payload"
			ins.ArrayDataElementWidth = width
			ins.ArrayDataValues = parseArrayDataValues(insns, pc+4, width, size)
			ins.Length = uint32(4 + ((uint64(size)*uint64(width))+1)/2)
			setText("fill-array-data-payload width=%d size=%d", width, size)
		default:
			// Unknown payload pseudo-op. Keep one unit so the diagnostic output remains bounded.
			setText("nop/payload type=0x%02x", high)
		}
	case 0x01, 0x04, 0x07:
		a, b := regLowNibble(), regHighNibble()
		ins.Registers = []int{a, b}
		setText("%s v%d, v%d", ins.Name, a, b)
	case 0x02, 0x05, 0x08:
		a, b := regHigh(), int(unit(1))
		ins.Registers = []int{a, b}
		setText("%s v%d, v%d", ins.Name, a, b)
	case 0x03, 0x06, 0x09:
		a, b := int(unit(1)), int(unit(2))
		ins.Registers = []int{a, b}
		setText("%s v%d, v%d", ins.Name, a, b)
	case 0x0a, 0x0b, 0x0c, 0x0d, 0x0f, 0x10, 0x11, 0x27:
		a := regHigh()
		ins.Registers = []int{a}
		setText("%s v%d", ins.Name, a)
	case 0x0e:
		setText("return-void")
	case 0x12:
		a := regLowNibble()
		lit := int64(signExtend4(regHighNibble()))
		ins.Registers = []int{a}
		ins.IntLiteral = &lit
		setText("const/4 v%d, #%d", a, lit)
	case 0x13, 0x16:
		a := regHigh()
		lit := int64(int16(unit(1)))
		ins.Registers = []int{a}
		ins.IntLiteral = &lit
		setText("%s v%d, #%d", ins.Name, a, lit)
	case 0x14, 0x17:
		a := regHigh()
		lit := int64(s32(1))
		ins.Registers = []int{a}
		ins.IntLiteral = &lit
		setText("%s v%d, #%d", ins.Name, a, lit)
	case 0x15:
		a := regHigh()
		lit := int64(int32(unit(1)) << 16)
		ins.Registers = []int{a}
		ins.IntLiteral = &lit
		setText("const/high16 v%d, #%d", a, lit)
	case 0x18:
		a := regHigh()
		lit := int64(uint64(unit(1)) | uint64(unit(2))<<16 | uint64(unit(3))<<32 | uint64(unit(4))<<48)
		ins.Registers = []int{a}
		ins.IntLiteral = &lit
		setText("const-wide v%d, #%d", a, lit)
	case 0x19:
		a := regHigh()
		lit := int64(uint64(unit(1)) << 48)
		ins.Registers = []int{a}
		ins.IntLiteral = &lit
		setText("const-wide/high16 v%d, #%d", a, lit)
	case 0x1a:
		a, idx := regHigh(), uint32(unit(1))
		s := f.stringAt(idx)
		ins.Registers = []int{a}
		ins.StringLiteral = s
		setText("const-string v%d, %s", a, quoteJava(s))
	case 0x1b:
		a, idx := regHigh(), u32(1)
		s := f.stringAt(idx)
		ins.Registers = []int{a}
		ins.StringLiteral = s
		setText("const-string/jumbo v%d, %s", a, quoteJava(s))
	case 0x1c, 0x1f, 0x22:
		a, idx := regHigh(), uint32(unit(1))
		t := f.typeAt(idx)
		ins.Registers = []int{a}
		ins.TypeRef = t
		setText("%s v%d, %s", ins.Name, a, t)
	case 0x1d, 0x1e:
		a := regHigh()
		ins.Registers = []int{a}
		setText("%s v%d", ins.Name, a)
	case 0x20, 0x23:
		a, b, idx := regLowNibble(), regHighNibble(), uint32(unit(1))
		t := f.typeAt(idx)
		ins.Registers = []int{a, b}
		ins.TypeRef = t
		setText("%s v%d, v%d, %s", ins.Name, a, b, t)
	case 0x21:
		a, b := regLowNibble(), regHighNibble()
		ins.Registers = []int{a, b}
		setText("array-length v%d, v%d", a, b)
	case 0x24:
		count := invoke35cCount(high)
		idx := uint32(unit(1))
		t := f.typeAt(idx)
		regs := invoke35cRegisters(high, unit(2))
		if count < len(regs) {
			regs = regs[:count]
		}
		ins.Registers = regs
		ins.TypeRef = t
		setText("filled-new-array {%s}, %s", registerList(regs), t)
	case 0x25:
		count, idx, start := high, uint32(unit(1)), int(unit(2))
		t := f.typeAt(idx)
		ins.Registers = rangeRegisters(start, count)
		ins.TypeRef = t
		setText("filled-new-array/range {v%d .. v%d}, %s", start, start+count-1, t)
	case 0x26, 0x2b, 0x2c:
		a, target := regHigh(), s32(1)
		ins.Registers = []int{a}
		ins.Target = int32(pc) + target
		if op == 0x26 {
			if width, values, ok := readFillArrayPayload(insns, int(ins.Target)); ok {
				ins.ArrayDataElementWidth = width
				ins.ArrayDataValues = values
			}
		} else if op == 0x2b {
			ins.SwitchKeys, ins.SwitchTargets, _ = readPackedSwitchPayload(insns, pc, int(ins.Target))
		} else if op == 0x2c {
			ins.SwitchKeys, ins.SwitchTargets, _ = readSparseSwitchPayload(insns, pc, int(ins.Target))
		}
		switchText := ""
		if len(ins.SwitchKeys) > 0 && len(ins.SwitchKeys) == len(ins.SwitchTargets) {
			switchText = fmt.Sprintf(" cases=%s", formatSwitchCases(ins.SwitchKeys, ins.SwitchTargets))
		}
		setText("%s v%d, -> %+d%s", ins.Name, a, target, switchText)
	case 0x28:
		target := int32(int8(high))
		ins.Target = int32(pc) + target
		setText("goto %+d", target)
	case 0x29:
		target := int32(int16(unit(1)))
		ins.Target = int32(pc) + target
		setText("goto/16 %+d", target)
	case 0x2a:
		target := s32(1)
		ins.Target = int32(pc) + target
		setText("goto/32 %+d", target)
	case 0x2d, 0x2e, 0x2f, 0x30, 0x31, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xab, 0xac, 0xad, 0xae, 0xaf:
		a, b, c := regHigh(), int(unit(1)&0xff), int((unit(1)>>8)&0xff)
		ins.Registers = []int{a, b, c}
		setText("%s v%d, v%d, v%d", ins.Name, a, b, c)
	case 0x32, 0x33, 0x34, 0x35, 0x36, 0x37:
		a, b, target := regLowNibble(), regHighNibble(), int32(int16(unit(1)))
		ins.Registers = []int{a, b}
		ins.Target = int32(pc) + target
		setText("%s v%d, v%d, %+d", ins.Name, a, b, target)
	case 0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d:
		a, target := regHigh(), int32(int16(unit(1)))
		ins.Registers = []int{a}
		ins.Target = int32(pc) + target
		setText("%s v%d, %+d", ins.Name, a, target)
	case 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f:
		a, b, idx := regLowNibble(), regHighNibble(), uint32(unit(1))
		field := f.fieldAt(idx)
		ins.Registers = []int{a, b}
		ins.FieldRef = field
		setText("%s v%d, v%d, %s", ins.Name, a, b, formatFieldRef(field))
	case 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d:
		a, idx := regHigh(), uint32(unit(1))
		field := f.fieldAt(idx)
		ins.Registers = []int{a}
		ins.FieldRef = field
		setText("%s v%d, %s", ins.Name, a, formatFieldRef(field))
	case 0x6e, 0x6f, 0x70, 0x71, 0x72:
		count := invoke35cCount(high)
		idx := uint32(unit(1))
		method := f.methodAt(idx)
		regs := invoke35cRegisters(high, unit(2))
		if count < len(regs) {
			regs = regs[:count]
		}
		ins.Registers = regs
		ins.MethodRef = method
		setText("%s {%s}, %s", ins.Name, registerList(regs), formatMethodRef(method))
	case 0x74, 0x75, 0x76, 0x77, 0x78:
		count, idx, start := high, uint32(unit(1)), int(unit(2))
		method := f.methodAt(idx)
		ins.Registers = rangeRegisters(start, count)
		ins.MethodRef = method
		setText("%s {v%d .. v%d}, %s", ins.Name, start, start+count-1, formatMethodRef(method))
	case 0x7b, 0x7c, 0x7d, 0x7e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f:
		a, b := regLowNibble(), regHighNibble()
		ins.Registers = []int{a, b}
		setText("%s v%d, v%d", ins.Name, a, b)
	case 0xb0, 0xb1, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xbb, 0xbc, 0xbd, 0xbe, 0xbf, 0xc0, 0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xcb, 0xcc, 0xcd, 0xce, 0xcf:
		a, b := regLowNibble(), regHighNibble()
		ins.Registers = []int{a, b}
		setText("%s v%d, v%d", ins.Name, a, b)
	case 0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7:
		a, b, lit := regLowNibble(), regHighNibble(), int64(int16(unit(1)))
		ins.Registers = []int{a, b}
		ins.IntLiteral = &lit
		setText("%s v%d, v%d, #%d", ins.Name, a, b, lit)
	case 0xd8, 0xd9, 0xda, 0xdb, 0xdc, 0xdd, 0xde, 0xdf, 0xe0, 0xe1, 0xe2:
		a, b, lit := regHigh(), int(unit(1)&0xff), int64(int8((unit(1)>>8)&0xff))
		ins.Registers = []int{a, b}
		ins.IntLiteral = &lit
		setText("%s v%d, v%d, #%d", ins.Name, a, b, lit)
	case 0xfa:
		count := invoke35cCount(high)
		method := f.methodAt(uint32(unit(1)))
		proto := unit(2)
		regs := invoke35cRegisters(high, unit(3))
		if count < len(regs) {
			regs = regs[:count]
		}
		ins.Registers = regs
		ins.MethodRef = method
		setText("invoke-polymorphic {%s}, %s, proto@%d", registerList(regs), formatMethodRef(method), proto)
	case 0xfb:
		count, method, proto, start := high, f.methodAt(uint32(unit(1))), unit(2), int(unit(3))
		ins.Registers = rangeRegisters(start, count)
		ins.MethodRef = method
		setText("invoke-polymorphic/range {v%d .. v%d}, %s, proto@%d", start, start+count-1, formatMethodRef(method), proto)
	case 0xfc:
		count := invoke35cCount(high)
		idx := uint32(unit(1))
		regs := invoke35cRegisters(high, unit(2))
		if count < len(regs) {
			regs = regs[:count]
		}
		ins.Registers = regs
		setText("invoke-custom {%s}, call_site@%d", registerList(regs), idx)
	case 0xfd:
		count, idx, start := high, uint32(unit(1)), int(unit(2))
		ins.Registers = rangeRegisters(start, count)
		setText("invoke-custom/range {v%d .. v%d}, call_site@%d", start, start+count-1, idx)
	case 0xfe, 0xff:
		a, idx := regHigh(), uint32(unit(1))
		ins.Registers = []int{a}
		setText("%s v%d, item@%d", ins.Name, a, idx)
	default:
		setText("%s // raw=0x%04x", ins.Name, w0)
	}
	return ins
}

func opcodeLength(op byte) uint32 {
	switch op {
	case 0x00, 0x01, 0x04, 0x07, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x21, 0x27, 0x28, 0x7b, 0x7c, 0x7d, 0x7e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0xb0, 0xb1, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xbb, 0xbc, 0xbd, 0xbe, 0xbf, 0xc0, 0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xcb, 0xcc, 0xcd, 0xce, 0xcf:
		return 1
	case 0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29, 0xfe, 0xff, 0x2d, 0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xab, 0xac, 0xad, 0xae, 0xaf, 0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xdb, 0xdc, 0xdd, 0xde, 0xdf, 0xe0, 0xe1, 0xe2:
		return 2
	case 0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c, 0x6e, 0x6f, 0x70, 0x71, 0x72, 0x74, 0x75, 0x76, 0x77, 0x78, 0xfc, 0xfd:
		return 3
	case 0xfa, 0xfb:
		return 4
	case 0x18:
		return 5
	default:
		return 1
	}
}

func opcodeName(op byte) string {
	names := map[byte]string{
		0x00: "nop", 0x01: "move", 0x02: "move/from16", 0x03: "move/16", 0x04: "move-wide", 0x05: "move-wide/from16", 0x06: "move-wide/16", 0x07: "move-object", 0x08: "move-object/from16", 0x09: "move-object/16", 0x0a: "move-result", 0x0b: "move-result-wide", 0x0c: "move-result-object", 0x0d: "move-exception", 0x0e: "return-void", 0x0f: "return", 0x10: "return-wide", 0x11: "return-object", 0x12: "const/4", 0x13: "const/16", 0x14: "const", 0x15: "const/high16", 0x16: "const-wide/16", 0x17: "const-wide/32", 0x18: "const-wide", 0x19: "const-wide/high16", 0x1a: "const-string", 0x1b: "const-string/jumbo", 0x1c: "const-class", 0x1d: "monitor-enter", 0x1e: "monitor-exit", 0x1f: "check-cast", 0x20: "instance-of", 0x21: "array-length", 0x22: "new-instance", 0x23: "new-array", 0x24: "filled-new-array", 0x25: "filled-new-array/range", 0x26: "fill-array-data", 0x27: "throw", 0x28: "goto", 0x29: "goto/16", 0x2a: "goto/32", 0x2b: "packed-switch", 0x2c: "sparse-switch",
		0x2d: "cmpl-float", 0x2e: "cmpg-float", 0x2f: "cmpl-double", 0x30: "cmpg-double", 0x31: "cmp-long", 0x32: "if-eq", 0x33: "if-ne", 0x34: "if-lt", 0x35: "if-ge", 0x36: "if-gt", 0x37: "if-le", 0x38: "if-eqz", 0x39: "if-nez", 0x3a: "if-ltz", 0x3b: "if-gez", 0x3c: "if-gtz", 0x3d: "if-lez",
		0x44: "aget", 0x45: "aget-wide", 0x46: "aget-object", 0x47: "aget-boolean", 0x48: "aget-byte", 0x49: "aget-char", 0x4a: "aget-short", 0x4b: "aput", 0x4c: "aput-wide", 0x4d: "aput-object", 0x4e: "aput-boolean", 0x4f: "aput-byte", 0x50: "aput-char", 0x51: "aput-short",
		0x52: "iget", 0x53: "iget-wide", 0x54: "iget-object", 0x55: "iget-boolean", 0x56: "iget-byte", 0x57: "iget-char", 0x58: "iget-short", 0x59: "iput", 0x5a: "iput-wide", 0x5b: "iput-object", 0x5c: "iput-boolean", 0x5d: "iput-byte", 0x5e: "iput-char", 0x5f: "iput-short",
		0x60: "sget", 0x61: "sget-wide", 0x62: "sget-object", 0x63: "sget-boolean", 0x64: "sget-byte", 0x65: "sget-char", 0x66: "sget-short", 0x67: "sput", 0x68: "sput-wide", 0x69: "sput-object", 0x6a: "sput-boolean", 0x6b: "sput-byte", 0x6c: "sput-char", 0x6d: "sput-short",
		0x6e: "invoke-virtual", 0x6f: "invoke-super", 0x70: "invoke-direct", 0x71: "invoke-static", 0x72: "invoke-interface", 0x74: "invoke-virtual/range", 0x75: "invoke-super/range", 0x76: "invoke-direct/range", 0x77: "invoke-static/range", 0x78: "invoke-interface/range",
		0x7b: "neg-int", 0x7c: "not-int", 0x7d: "neg-long", 0x7e: "not-long", 0x7f: "neg-float", 0x80: "neg-double", 0x81: "int-to-long", 0x82: "int-to-float", 0x83: "int-to-double", 0x84: "long-to-int", 0x85: "long-to-float", 0x86: "long-to-double", 0x87: "float-to-int", 0x88: "float-to-long", 0x89: "float-to-double", 0x8a: "double-to-int", 0x8b: "double-to-long", 0x8c: "double-to-float", 0x8d: "int-to-byte", 0x8e: "int-to-char", 0x8f: "int-to-short",
		0x90: "add-int", 0x91: "sub-int", 0x92: "mul-int", 0x93: "div-int", 0x94: "rem-int", 0x95: "and-int", 0x96: "or-int", 0x97: "xor-int", 0x98: "shl-int", 0x99: "shr-int", 0x9a: "ushr-int", 0x9b: "add-long", 0x9c: "sub-long", 0x9d: "mul-long", 0x9e: "div-long", 0x9f: "rem-long", 0xa0: "and-long", 0xa1: "or-long", 0xa2: "xor-long", 0xa3: "shl-long", 0xa4: "shr-long", 0xa5: "ushr-long", 0xa6: "add-float", 0xa7: "sub-float", 0xa8: "mul-float", 0xa9: "div-float", 0xaa: "rem-float", 0xab: "add-double", 0xac: "sub-double", 0xad: "mul-double", 0xae: "div-double", 0xaf: "rem-double",
		0xb0: "add-int/2addr", 0xb1: "sub-int/2addr", 0xb2: "mul-int/2addr", 0xb3: "div-int/2addr", 0xb4: "rem-int/2addr", 0xb5: "and-int/2addr", 0xb6: "or-int/2addr", 0xb7: "xor-int/2addr", 0xb8: "shl-int/2addr", 0xb9: "shr-int/2addr", 0xba: "ushr-int/2addr", 0xbb: "add-long/2addr", 0xbc: "sub-long/2addr", 0xbd: "mul-long/2addr", 0xbe: "div-long/2addr", 0xbf: "rem-long/2addr", 0xc0: "and-long/2addr", 0xc1: "or-long/2addr", 0xc2: "xor-long/2addr", 0xc3: "shl-long/2addr", 0xc4: "shr-long/2addr", 0xc5: "ushr-long/2addr", 0xc6: "add-float/2addr", 0xc7: "sub-float/2addr", 0xc8: "mul-float/2addr", 0xc9: "div-float/2addr", 0xca: "rem-float/2addr", 0xcb: "add-double/2addr", 0xcc: "sub-double/2addr", 0xcd: "mul-double/2addr", 0xce: "div-double/2addr", 0xcf: "rem-double/2addr",
		0xd0: "add-int/lit16", 0xd1: "rsub-int", 0xd2: "mul-int/lit16", 0xd3: "div-int/lit16", 0xd4: "rem-int/lit16", 0xd5: "and-int/lit16", 0xd6: "or-int/lit16", 0xd7: "xor-int/lit16", 0xd8: "add-int/lit8", 0xd9: "rsub-int/lit8", 0xda: "mul-int/lit8", 0xdb: "div-int/lit8", 0xdc: "rem-int/lit8", 0xdd: "and-int/lit8", 0xde: "or-int/lit8", 0xdf: "xor-int/lit8", 0xe0: "shl-int/lit8", 0xe1: "shr-int/lit8", 0xe2: "ushr-int/lit8",
		0xfa: "invoke-polymorphic", 0xfb: "invoke-polymorphic/range", 0xfc: "invoke-custom", 0xfd: "invoke-custom/range", 0xfe: "const-method-handle", 0xff: "const-method-type",
	}
	if n, ok := names[op]; ok {
		return n
	}
	return fmt.Sprintf("op_0x%02x", op)
}

func readFillArrayPayload(insns []uint16, pc int) (uint16, []int64, bool) {
	if pc < 0 || pc+4 > len(insns) || insns[pc] != 0x0300 {
		return 0, nil, false
	}
	width := insns[pc+1]
	size := uint32(insns[pc+2]) | uint32(insns[pc+3])<<16
	values := parseArrayDataValues(insns, pc+4, width, size)
	if uint32(len(values)) != size {
		return width, values, false
	}
	return width, values, true
}

func readPackedSwitchPayload(insns []uint16, switchPc int, payloadPc int) ([]int32, []int32, bool) {
	if payloadPc < 0 || payloadPc+4 > len(insns) || insns[payloadPc] != 0x0100 {
		return nil, nil, false
	}
	size := int(insns[payloadPc+1])
	if size < 0 || payloadPc+4+size*2 > len(insns) {
		return nil, nil, false
	}
	firstKey := int32(uint32(insns[payloadPc+2]) | uint32(insns[payloadPc+3])<<16)
	keys := make([]int32, 0, size)
	targets := make([]int32, 0, size)
	cursor := payloadPc + 4
	for i := 0; i < size; i++ {
		off := int32(uint32(insns[cursor]) | uint32(insns[cursor+1])<<16)
		keys = append(keys, firstKey+int32(i))
		targets = append(targets, int32(switchPc)+off)
		cursor += 2
	}
	return keys, targets, true
}

func readSparseSwitchPayload(insns []uint16, switchPc int, payloadPc int) ([]int32, []int32, bool) {
	if payloadPc < 0 || payloadPc+2 > len(insns) || insns[payloadPc] != 0x0200 {
		return nil, nil, false
	}
	size := int(insns[payloadPc+1])
	if size < 0 || payloadPc+2+size*4 > len(insns) {
		return nil, nil, false
	}
	keys := make([]int32, 0, size)
	targets := make([]int32, 0, size)
	keyCursor := payloadPc + 2
	targetCursor := keyCursor + size*2
	for i := 0; i < size; i++ {
		key := int32(uint32(insns[keyCursor]) | uint32(insns[keyCursor+1])<<16)
		off := int32(uint32(insns[targetCursor]) | uint32(insns[targetCursor+1])<<16)
		keys = append(keys, key)
		targets = append(targets, int32(switchPc)+off)
		keyCursor += 2
		targetCursor += 2
	}
	return keys, targets, true
}

func formatSwitchCases(keys []int32, targets []int32) string {
	limit := len(keys)
	if len(targets) < limit {
		limit = len(targets)
	}
	if limit > 6 {
		limit = 6
	}
	parts := make([]string, 0, limit+1)
	for i := 0; i < limit; i++ {
		parts = append(parts, fmt.Sprintf("%d->%04x", keys[i], uint32(targets[i])))
	}
	if len(keys) > limit {
		parts = append(parts, fmt.Sprintf("...%d", len(keys)-limit))
	}
	return strings.Join(parts, ",")
}

func parseArrayDataValues(insns []uint16, dataPc int, width uint16, size uint32) []int64 {
	if width == 0 || size == 0 || dataPc < 0 || dataPc >= len(insns) {
		return nil
	}
	needBytes := int(uint64(width) * uint64(size))
	availableBytes := (len(insns) - dataPc) * 2
	if needBytes > availableBytes {
		needBytes = availableBytes
	}
	data := make([]byte, needBytes)
	for i := 0; i < needBytes; i++ {
		word := insns[dataPc+i/2]
		if i%2 == 0 {
			data[i] = byte(word & 0xff)
		} else {
			data[i] = byte((word >> 8) & 0xff)
		}
	}
	out := make([]int64, 0, size)
	w := int(width)
	for i := 0; i+w <= len(data) && len(out) < int(size); i += w {
		var raw uint64
		for b := 0; b < w && b < 8; b++ {
			raw |= uint64(data[i+b]) << (8 * b)
		}
		out = append(out, signExtendArrayData(raw, w))
	}
	return out
}

func signExtendArrayData(raw uint64, width int) int64 {
	switch width {
	case 1:
		return int64(int8(raw))
	case 2:
		return int64(int16(raw))
	case 4:
		return int64(int32(raw))
	case 8:
		return int64(raw)
	default:
		return int64(raw)
	}
}

func signExtend4(v int) int {
	if v&0x8 != 0 {
		return v | ^0xf
	}
	return v
}

func invoke35cCount(high int) int {
	// DEX 35c stores the argument count A in the high nibble of the first code unit high byte.
	return (high >> 4) & 0x0f
}

func invoke35cRegisters(high int, word uint16) []int {
	// DEX 35c stores C,D,E,F in the second code unit and G in the low nibble of the first code unit high byte.
	return []int{int(word & 0x0f), int((word >> 4) & 0x0f), int((word >> 8) & 0x0f), int((word >> 12) & 0x0f), high & 0x0f}
}

func rangeRegisters(start int, count int) []int {
	if count < 0 {
		count = 0
	}
	out := make([]int, 0, count)
	for i := 0; i < count; i++ {
		out = append(out, start+i)
	}
	return out
}

func registerList(regs []int) string {
	parts := make([]string, len(regs))
	for i, r := range regs {
		parts[i] = fmt.Sprintf("v%d", r)
	}
	return strings.Join(parts, ", ")
}

func formatFieldRef(f FieldID) string {
	return fmt.Sprintf("%s->%s:%s", f.Class, f.Name, f.Type)
}

func formatMethodRef(m MethodID) string {
	params := strings.Join(m.Parameters, "")
	return fmt.Sprintf("%s->%s(%s)%s", m.Class, m.Name, params, m.ReturnType)
}

func quoteJava(s string) string {
	return strconv.Quote(s)
}
