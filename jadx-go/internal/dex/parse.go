package dex

import (
	"encoding/binary"
	"fmt"
	"strings"
	"unicode/utf8"
)

type File struct {
	Name    string
	data    []byte
	strings []string
	types   []string
	protos  []Proto
	fields  []FieldID
	methods []MethodID
	classes []ClassDef
}

type Proto struct {
	Shorty     string
	ReturnType string
	Parameters []string
}

type FieldID struct {
	Class string
	Type  string
	Name  string
}

type MethodID struct {
	Class      string
	Name       string
	ReturnType string
	Parameters []string
}

type ClassDef struct {
	DexName      string
	Descriptor   string
	AccessFlags  uint32
	Superclass   string
	Interfaces   []string
	SourceFile   string
	StaticFields []EncodedField
	Fields       []EncodedField
	Direct       []EncodedMethod
	Virtual      []EncodedMethod
}

type EncodedField struct {
	ID          FieldID
	AccessFlags uint32
}

type EncodedMethod struct {
	ID          MethodID
	AccessFlags uint32
	Code        CodeInfo
}

type CodeInfo struct {
	Offset       uint32
	Registers    uint16
	Ins          uint16
	Outs         uint16
	Tries        uint16
	Insns        uint32
	HasCode      bool
	Instructions []Instruction
}

func Parse(name string, data []byte) (*File, error) {
	if !isDex(data) {
		return nil, fmt.Errorf("not a DEX file")
	}
	f := &File{Name: name, data: data}
	headerSize := f.u32(0x24)
	if headerSize < 0x70 || int(headerSize) > len(data) {
		return nil, fmt.Errorf("invalid DEX header size %d", headerSize)
	}
	stringSize, stringOff := f.u32(0x38), f.u32(0x3c)
	typeSize, typeOff := f.u32(0x40), f.u32(0x44)
	protoSize, protoOff := f.u32(0x48), f.u32(0x4c)
	fieldSize, fieldOff := f.u32(0x50), f.u32(0x54)
	methodSize, methodOff := f.u32(0x58), f.u32(0x5c)
	classSize, classOff := f.u32(0x60), f.u32(0x64)

	if err := f.readStrings(stringSize, stringOff); err != nil {
		return nil, err
	}
	if err := f.readTypes(typeSize, typeOff); err != nil {
		return nil, err
	}
	if err := f.readProtos(protoSize, protoOff); err != nil {
		return nil, err
	}
	if err := f.readFields(fieldSize, fieldOff); err != nil {
		return nil, err
	}
	if err := f.readMethods(methodSize, methodOff); err != nil {
		return nil, err
	}
	if err := f.readClasses(classSize, classOff); err != nil {
		return nil, err
	}
	return f, nil
}

func (f *File) Classes() []ClassDef {
	out := append([]ClassDef(nil), f.classes...)
	return out
}

func (f *File) readStrings(size uint32, off uint32) error {
	if size == 0 {
		return nil
	}
	if !f.rangeOK(off, size*4) {
		return fmt.Errorf("invalid string_ids range")
	}
	f.strings = make([]string, int(size))
	for i := uint32(0); i < size; i++ {
		strOff := f.u32(off + i*4)
		s, err := f.readStringData(strOff)
		if err != nil {
			return fmt.Errorf("string[%d]: %w", i, err)
		}
		f.strings[i] = s
	}
	return nil
}

func (f *File) readStringData(off uint32) (string, error) {
	pos := int(off)
	if pos < 0 || pos >= len(f.data) {
		return "", fmt.Errorf("offset out of range")
	}
	_, n, ok := readULEB(f.data, pos)
	if !ok {
		return "", fmt.Errorf("bad utf16 length")
	}
	pos += n
	start := pos
	for pos < len(f.data) && f.data[pos] != 0 {
		pos++
	}
	if pos >= len(f.data) {
		return "", fmt.Errorf("unterminated string")
	}
	raw := f.data[start:pos]
	if utf8.Valid(raw) {
		return string(raw), nil
	}
	return strings.ToValidUTF8(string(raw), "�"), nil
}

func (f *File) readTypes(size uint32, off uint32) error {
	if size == 0 {
		return nil
	}
	if !f.rangeOK(off, size*4) {
		return fmt.Errorf("invalid type_ids range")
	}
	f.types = make([]string, int(size))
	for i := uint32(0); i < size; i++ {
		idx := f.u32(off + i*4)
		f.types[i] = f.stringAt(idx)
	}
	return nil
}

func (f *File) readProtos(size uint32, off uint32) error {
	if size == 0 {
		return nil
	}
	if !f.rangeOK(off, size*12) {
		return fmt.Errorf("invalid proto_ids range")
	}
	f.protos = make([]Proto, int(size))
	for i := uint32(0); i < size; i++ {
		base := off + i*12
		shortyIdx := f.u32(base)
		returnIdx := f.u32(base + 4)
		paramsOff := f.u32(base + 8)
		f.protos[i] = Proto{
			Shorty:     f.stringAt(shortyIdx),
			ReturnType: f.typeAt(returnIdx),
			Parameters: f.readTypeList(paramsOff),
		}
	}
	return nil
}

func (f *File) readTypeList(off uint32) []string {
	if off == 0 || !f.rangeOK(off, 4) {
		return nil
	}
	size := f.u32(off)
	if size == 0 || !f.rangeOK(off+4, size*2) {
		return nil
	}
	out := make([]string, int(size))
	pos := off + 4
	for i := uint32(0); i < size; i++ {
		out[i] = f.typeAt(uint32(f.u16(pos)))
		pos += 2
	}
	return out
}

func (f *File) readFields(size uint32, off uint32) error {
	if size == 0 {
		return nil
	}
	if !f.rangeOK(off, size*8) {
		return fmt.Errorf("invalid field_ids range")
	}
	f.fields = make([]FieldID, int(size))
	for i := uint32(0); i < size; i++ {
		base := off + i*8
		f.fields[i] = FieldID{
			Class: f.typeAt(uint32(f.u16(base))),
			Type:  f.typeAt(uint32(f.u16(base + 2))),
			Name:  f.stringAt(f.u32(base + 4)),
		}
	}
	return nil
}

func (f *File) readMethods(size uint32, off uint32) error {
	if size == 0 {
		return nil
	}
	if !f.rangeOK(off, size*8) {
		return fmt.Errorf("invalid method_ids range")
	}
	f.methods = make([]MethodID, int(size))
	for i := uint32(0); i < size; i++ {
		base := off + i*8
		proto := f.protoAt(uint32(f.u16(base + 2)))
		f.methods[i] = MethodID{
			Class:      f.typeAt(uint32(f.u16(base))),
			Name:       f.stringAt(f.u32(base + 4)),
			ReturnType: proto.ReturnType,
			Parameters: proto.Parameters,
		}
	}
	return nil
}

func (f *File) readClasses(size uint32, off uint32) error {
	if size == 0 {
		return nil
	}
	if !f.rangeOK(off, size*32) {
		return fmt.Errorf("invalid class_defs range")
	}
	f.classes = make([]ClassDef, 0, int(size))
	for i := uint32(0); i < size; i++ {
		base := off + i*32
		classIdx := f.u32(base)
		accessFlags := f.u32(base + 4)
		superIdx := f.u32(base + 8)
		interfacesOff := f.u32(base + 12)
		sourceIdx := f.u32(base + 16)
		classDataOff := f.u32(base + 24)
		cls := ClassDef{
			DexName:     f.Name,
			Descriptor:  f.typeAt(classIdx),
			AccessFlags: accessFlags,
			Superclass:  f.typeAtOrEmpty(superIdx),
			Interfaces:  f.readTypeList(interfacesOff),
			SourceFile:  f.stringAtOrEmpty(sourceIdx),
		}
		if classDataOff != 0 {
			f.readClassData(classDataOff, &cls)
		}
		f.classes = append(f.classes, cls)
	}
	return nil
}

func (f *File) readClassData(off uint32, cls *ClassDef) {
	pos := int(off)
	staticCount, n, ok := readULEB(f.data, pos)
	if !ok {
		return
	}
	pos += n
	instanceCount, n, ok := readULEB(f.data, pos)
	if !ok {
		return
	}
	pos += n
	directCount, n, ok := readULEB(f.data, pos)
	if !ok {
		return
	}
	pos += n
	virtualCount, n, ok := readULEB(f.data, pos)
	if !ok {
		return
	}
	pos += n

	cls.StaticFields, pos = f.readEncodedFields(pos, staticCount)
	cls.Fields, pos = f.readEncodedFields(pos, instanceCount)
	cls.Direct, pos = f.readEncodedMethods(pos, directCount)
	cls.Virtual, _ = f.readEncodedMethods(pos, virtualCount)
}

func (f *File) readEncodedFields(pos int, count uint32) ([]EncodedField, int) {
	out := make([]EncodedField, 0, int(count))
	var idx uint32
	for i := uint32(0); i < count; i++ {
		diff, n, ok := readULEB(f.data, pos)
		if !ok {
			return out, pos
		}
		pos += n
		flags, n, ok := readULEB(f.data, pos)
		if !ok {
			return out, pos
		}
		pos += n
		idx += diff
		out = append(out, EncodedField{ID: f.fieldAt(idx), AccessFlags: flags})
	}
	return out, pos
}

func (f *File) readEncodedMethods(pos int, count uint32) ([]EncodedMethod, int) {
	out := make([]EncodedMethod, 0, int(count))
	var idx uint32
	for i := uint32(0); i < count; i++ {
		diff, n, ok := readULEB(f.data, pos)
		if !ok {
			return out, pos
		}
		pos += n
		flags, n, ok := readULEB(f.data, pos)
		if !ok {
			return out, pos
		}
		pos += n
		codeOff, n, ok := readULEB(f.data, pos)
		if !ok {
			return out, pos
		}
		pos += n
		idx += diff
		out = append(out, EncodedMethod{ID: f.methodAt(idx), AccessFlags: flags, Code: f.codeInfo(codeOff)})
	}
	return out, pos
}

func (f *File) codeInfo(off uint32) CodeInfo {
	if off == 0 || !f.rangeOK(off, 16) {
		return CodeInfo{}
	}
	info := CodeInfo{
		Offset:    off,
		Registers: f.u16(off),
		Ins:       f.u16(off + 2),
		Outs:      f.u16(off + 4),
		Tries:     f.u16(off + 6),
		Insns:     f.u32(off + 12),
		HasCode:   true,
	}
	info.Instructions = f.decodeInstructions(off, info.Insns)
	return info
}

func (f *File) u16(off uint32) uint16 {
	if !f.rangeOK(off, 2) {
		return 0
	}
	return binary.LittleEndian.Uint16(f.data[off : off+2])
}

func (f *File) u32(off uint32) uint32 {
	if !f.rangeOK(off, 4) {
		return 0
	}
	return binary.LittleEndian.Uint32(f.data[off : off+4])
}

func (f *File) rangeOK(off uint32, size uint32) bool {
	end := uint64(off) + uint64(size)
	return end <= uint64(len(f.data))
}

func (f *File) stringAt(idx uint32) string {
	if idx >= uint32(len(f.strings)) {
		return fmt.Sprintf("<string_%d>", idx)
	}
	return f.strings[idx]
}

func (f *File) stringAtOrEmpty(idx uint32) string {
	if idx == 0xffffffff {
		return ""
	}
	return f.stringAt(idx)
}

func (f *File) typeAt(idx uint32) string {
	if idx >= uint32(len(f.types)) {
		return fmt.Sprintf("Lunknown/Type%d;", idx)
	}
	return f.types[idx]
}

func (f *File) typeAtOrEmpty(idx uint32) string {
	if idx == 0xffffffff {
		return ""
	}
	return f.typeAt(idx)
}

func (f *File) protoAt(idx uint32) Proto {
	if idx >= uint32(len(f.protos)) {
		return Proto{ReturnType: "V"}
	}
	return f.protos[idx]
}

func (f *File) fieldAt(idx uint32) FieldID {
	if idx >= uint32(len(f.fields)) {
		return FieldID{Name: fmt.Sprintf("field_%d", idx), Type: "Ljava/lang/Object;"}
	}
	return f.fields[idx]
}

func (f *File) methodAt(idx uint32) MethodID {
	if idx >= uint32(len(f.methods)) {
		return MethodID{Name: fmt.Sprintf("method_%d", idx), ReturnType: "V"}
	}
	return f.methods[idx]
}

func readULEB(data []byte, pos int) (uint32, int, bool) {
	var result uint32
	var shift uint
	for i := 0; i < 5; i++ {
		if pos+i >= len(data) {
			return 0, i, false
		}
		b := data[pos+i]
		result |= uint32(b&0x7f) << shift
		if b&0x80 == 0 {
			return result, i + 1, true
		}
		shift += 7
	}
	return 0, 5, false
}
