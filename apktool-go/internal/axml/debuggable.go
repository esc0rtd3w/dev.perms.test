package axml

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"sort"
	"unicode/utf16"
)

const (
	fullChunkXML          = 0x00080003
	fullChunkStringPool   = 0x001c0001
	fullChunkResourceMap  = 0x00080180
	fullChunkStartElement = 0x00100102

	typeIntBoolean = 0x12
	noIndex        = 0xffffffff

	androidNamespaceURI = "http://schemas.android.com/apk/res/android"
	applicationElement  = "application"
	debuggableName      = "debuggable"
	attrDebuggableID    = 0x0101000f
)

// PatchDebuggableTrue applies the focused binary-XML edit current decode/repack workflows need for
// debug-package creation: android:debuggable="true" on the <application> node.
// It preserves the existing binary manifest layout wherever possible and only
// expands the string/resource pools when the debuggable attribute name is absent.
func PatchDebuggableTrue(manifest []byte) ([]byte, error) {
	if len(manifest) < 12 {
		return nil, fmt.Errorf("AndroidManifest.xml is empty or too small")
	}
	if u32(manifest, 0) != fullChunkXML {
		return nil, fmt.Errorf("AndroidManifest.xml is not Android binary XML")
	}
	xmlSize := int(u32(manifest, 4))
	if xmlSize <= 0 || xmlSize > len(manifest) {
		xmlSize = len(manifest)
	}

	stringPoolOffset := 8
	if stringPoolOffset+28 > len(manifest) || u32(manifest, stringPoolOffset) != fullChunkStringPool {
		return nil, fmt.Errorf("binary manifest string pool was not found")
	}
	pool, err := parsePatchStringPool(manifest, stringPoolOffset)
	if err != nil {
		return nil, err
	}
	stringPoolEnd := stringPoolOffset + pool.chunkSize
	if stringPoolEnd > len(manifest) {
		return nil, fmt.Errorf("binary manifest string pool extends past file data")
	}

	resourceMapOffset := -1
	resourceMapSize := 0
	pos := stringPoolEnd
	if pos+8 <= len(manifest) && u32(manifest, pos) == fullChunkResourceMap {
		resourceMapOffset = pos
		resourceMapSize = int(u32(manifest, pos+4))
		if resourceMapSize < 8 || pos+resourceMapSize > len(manifest) {
			return nil, fmt.Errorf("binary manifest resource map is malformed")
		}
		pos += resourceMapSize
	}

	appStart, err := findStartElement(manifest, pos, xmlSize, pool.strings, applicationElement)
	if err != nil {
		return nil, err
	}
	if appStart < 0 {
		return nil, fmt.Errorf("application element not found in AndroidManifest.xml")
	}

	debugIndex := pool.indexOf(debuggableName)
	addedString := false
	if debugIndex < 0 {
		debugIndex = len(pool.strings)
		pool.strings = append(pool.strings, debuggableName)
		addedString = true
	}
	androidNSIndex := pool.indexOf(androidNamespaceURI)
	if androidNSIndex < 0 {
		return nil, fmt.Errorf("Android namespace string not found in AndroidManifest.xml")
	}

	stringPoolBytes := manifest[stringPoolOffset:stringPoolEnd]
	if addedString {
		stringPoolBytes, err = pool.toBytes()
		if err != nil {
			return nil, err
		}
	}
	resourceIDs := buildResourceMapIDs(manifest, resourceMapOffset, resourceMapSize, debugIndex)
	resourceMapBytes := resourceMapToBytes(resourceIDs)
	beforeChunks := append([]byte(nil), manifest[pos:appStart]...)
	patchedApplication, err := patchApplicationStart(manifest, appStart, androidNSIndex, debugIndex, resourceIDs)
	if err != nil {
		return nil, err
	}
	appSize := int(u32(manifest, appStart+4))
	afterApp := appStart + appSize
	if appSize < 8 || afterApp > xmlSize {
		return nil, fmt.Errorf("application start chunk extends past manifest data")
	}
	afterApplication := append([]byte(nil), manifest[afterApp:xmlSize]...)

	newSize := 8 + len(stringPoolBytes) + len(resourceMapBytes) + len(beforeChunks) + len(patchedApplication) + len(afterApplication)
	out := bytes.NewBuffer(make([]byte, 0, newSize))
	writeU32(out, fullChunkXML)
	writeU32(out, uint32(newSize))
	out.Write(stringPoolBytes)
	out.Write(resourceMapBytes)
	out.Write(beforeChunks)
	out.Write(patchedApplication)
	out.Write(afterApplication)
	return out.Bytes(), nil
}

func findStartElement(data []byte, start, end int, strings []string, elementName string) (int, error) {
	for pos := start; pos+8 <= end; {
		typ := u32(data, pos)
		size := int(u32(data, pos+4))
		if size < 8 || pos+size > end {
			return -1, fmt.Errorf("malformed XML chunk at offset 0x%x", pos)
		}
		if typ == fullChunkStartElement {
			if pos+36 > end {
				return -1, fmt.Errorf("malformed start element chunk at offset 0x%x", pos)
			}
			nameIndex := int(u32(data, pos+20))
			if nameIndex >= 0 && nameIndex < len(strings) && strings[nameIndex] == elementName {
				return pos, nil
			}
		}
		pos += size
	}
	return -1, nil
}

func patchApplicationStart(data []byte, off int, androidNSIndex int, debugIndex int, resourceIDs []uint32) ([]byte, error) {
	chunkSize := int(u32(data, off+4))
	if chunkSize < 36 || off+chunkSize > len(data) {
		return nil, fmt.Errorf("application start chunk is malformed")
	}
	attrStart := int(u16(data, off+24))
	attrSize := int(u16(data, off+26))
	attrCount := int(u16(data, off+28))
	idIndex := int(u16(data, off+30))
	classIndex := int(u16(data, off+32))
	styleIndex := int(u16(data, off+34))
	attrBase := 16 + attrStart
	if attrStart < 20 || attrSize < 20 || attrBase+attrCount*attrSize > chunkSize {
		return nil, fmt.Errorf("application attribute table is malformed")
	}

	chunk := append([]byte(nil), data[off:off+chunkSize]...)
	attrs := make([][]byte, 0, attrCount+1)
	var idAttr, classAttr, styleAttr []byte
	patchedExisting := false
	for i := 0; i < attrCount; i++ {
		p := attrBase + i*attrSize
		attr := append([]byte(nil), chunk[p:p+attrSize]...)
		nameIndex := int(u32(attr, 4))
		resID := resourceIDForName(resourceIDs, nameIndex)
		if nameIndex == debugIndex || resID == attrDebuggableID {
			writeDebuggableAttribute(attr, androidNSIndex, debugIndex)
			patchedExisting = true
		}
		attrs = append(attrs, attr)
		if idIndex == i+1 {
			idAttr = attr
		}
		if classIndex == i+1 {
			classAttr = attr
		}
		if styleIndex == i+1 {
			styleAttr = attr
		}
	}
	if !patchedExisting {
		attr := make([]byte, attrSize)
		writeDebuggableAttribute(attr, androidNSIndex, debugIndex)
		attrs = append(attrs, attr)
	}

	sort.SliceStable(attrs, func(i, j int) bool {
		ka := attributeSortKey(attrs[i], resourceIDs)
		kb := attributeSortKey(attrs[j], resourceIDs)
		if ka != kb {
			return ka < kb
		}
		return u32(attrs[i], 4) < u32(attrs[j], 4)
	})

	oldAttrsEnd := attrBase + attrCount*attrSize
	out := bytes.NewBuffer(make([]byte, 0, chunkSize))
	out.Write(chunk[:attrBase])
	for _, attr := range attrs {
		out.Write(attr)
	}
	out.Write(chunk[oldAttrsEnd:])
	patched := out.Bytes()
	putU32(patched, 4, uint32(len(patched)))
	putU16(patched, 28, uint16(len(attrs)))
	putU16(patched, 30, oneBasedIndexOf(attrs, idAttr))
	putU16(patched, 32, oneBasedIndexOf(attrs, classAttr))
	putU16(patched, 34, oneBasedIndexOf(attrs, styleAttr))
	return patched, nil
}

func writeDebuggableAttribute(attr []byte, androidNSIndex, debugIndex int) {
	putU32(attr, 0, uint32(androidNSIndex))
	putU32(attr, 4, uint32(debugIndex))
	putU32(attr, 8, noIndex)
	putU16(attr, 12, 8)
	attr[14] = 0
	attr[15] = typeIntBoolean
	putU32(attr, 16, 0xffffffff)
}

func oneBasedIndexOf(attrs [][]byte, target []byte) uint16 {
	if target == nil {
		return 0
	}
	for i, attr := range attrs {
		if len(attr) > 0 && &attr[0] == &target[0] {
			return uint16(i + 1)
		}
	}
	return 0
}

func attributeSortKey(attr []byte, resourceIDs []uint32) uint32 {
	if len(attr) < 8 {
		return 0xffffffff
	}
	resID := resourceIDForName(resourceIDs, int(u32(attr, 4)))
	if resID == 0 {
		return 0xffffffff
	}
	return resID
}

func resourceIDForName(resourceIDs []uint32, nameIndex int) uint32 {
	if nameIndex < 0 || nameIndex >= len(resourceIDs) {
		return 0
	}
	return resourceIDs[nameIndex]
}

func buildResourceMapIDs(data []byte, off int, size int, debugIndex int) []uint32 {
	count := debugIndex + 1
	if off >= 0 && size >= 8 {
		existing := (size - 8) / 4
		if existing > count {
			count = existing
		}
	}
	ids := make([]uint32, count)
	if off >= 0 && size >= 8 && off+size <= len(data) {
		existing := (size - 8) / 4
		for i := 0; i < existing && i < len(ids); i++ {
			ids[i] = u32(data, off+8+i*4)
		}
	}
	ids[debugIndex] = attrDebuggableID
	return ids
}

func resourceMapToBytes(ids []uint32) []byte {
	out := bytes.NewBuffer(make([]byte, 0, 8+len(ids)*4))
	writeU32(out, fullChunkResourceMap)
	writeU32(out, uint32(8+len(ids)*4))
	for _, id := range ids {
		writeU32(out, id)
	}
	return out.Bytes()
}

type patchStringPool struct {
	headerSize   int
	chunkSize    int
	styleCount   int
	flags        uint32
	styleOffsets []uint32
	styleData    []byte
	strings      []string
}

func (p *patchStringPool) indexOf(s string) int {
	for i, v := range p.strings {
		if v == s {
			return i
		}
	}
	return -1
}

func parsePatchStringPool(data []byte, off int) (*patchStringPool, error) {
	headerSize := int(u16(data, off+2))
	chunkSize := int(u32(data, off+4))
	stringCount := int(u32(data, off+8))
	styleCount := int(u32(data, off+12))
	flags := u32(data, off+16)
	stringsStart := int(u32(data, off+20))
	stylesStart := int(u32(data, off+24))
	if headerSize < 28 || chunkSize < headerSize || off+chunkSize > len(data) {
		return nil, fmt.Errorf("string pool header is malformed")
	}
	stringOffsetsBase := off + headerSize
	styleOffsetsBase := stringOffsetsBase + stringCount*4
	if styleOffsetsBase+styleCount*4 > off+chunkSize {
		return nil, fmt.Errorf("string pool offsets are malformed")
	}
	stringsBase := off + stringsStart
	stringsEnd := off + chunkSize
	if stylesStart > 0 {
		stringsEnd = off + stylesStart
	}
	if stringsBase < off || stringsBase > stringsEnd || stringsEnd > off+chunkSize {
		return nil, fmt.Errorf("string pool data is malformed")
	}
	utf8Pool := flags&utf8Flag != 0
	strings := make([]string, 0, stringCount)
	for i := 0; i < stringCount; i++ {
		rel := int(u32(data, stringOffsetsBase+i*4))
		pos := stringsBase + rel
		if pos < stringsBase || pos >= stringsEnd {
			return nil, fmt.Errorf("string offset %d is malformed", i)
		}
		var s string
		var err error
		if utf8Pool {
			s, err = decodeUTF8String(data[pos:stringsEnd])
		} else {
			s, err = decodeUTF16String(data[pos:stringsEnd])
		}
		if err != nil {
			return nil, fmt.Errorf("string %d: %w", i, err)
		}
		strings = append(strings, s)
	}
	styleOffsets := make([]uint32, styleCount)
	for i := 0; i < styleCount; i++ {
		styleOffsets[i] = u32(data, styleOffsetsBase+i*4)
	}
	var styleData []byte
	if styleCount > 0 && stylesStart > 0 {
		styleData = append([]byte(nil), data[off+stylesStart:off+chunkSize]...)
	}
	return &patchStringPool{headerSize: headerSize, chunkSize: chunkSize, styleCount: styleCount, flags: flags, styleOffsets: styleOffsets, styleData: styleData, strings: strings}, nil
}

func (p *patchStringPool) toBytes() ([]byte, error) {
	utf8Pool := p.flags&utf8Flag != 0
	encoded := make([][]byte, 0, len(p.strings))
	stringsBytes := 0
	for _, s := range p.strings {
		var e []byte
		if utf8Pool {
			e = encodeUTF8String(s)
		} else {
			e = encodeUTF16String(s)
		}
		encoded = append(encoded, e)
		stringsBytes += len(e)
	}
	offsetsSize := len(p.strings)*4 + p.styleCount*4
	newStringsStart := 28 + offsetsSize
	paddedStringsBytes := align4(stringsBytes)
	newStylesStart := 0
	if p.styleCount > 0 {
		newStylesStart = newStringsStart + paddedStringsBytes
	}
	newChunkSize := newStringsStart + paddedStringsBytes + len(p.styleData)
	out := bytes.NewBuffer(make([]byte, 0, newChunkSize))
	writeU16(out, uint16(fullChunkStringPool&0xffff))
	writeU16(out, 28)
	writeU32(out, uint32(newChunkSize))
	writeU32(out, uint32(len(p.strings)))
	writeU32(out, uint32(p.styleCount))
	writeU32(out, p.flags)
	writeU32(out, uint32(newStringsStart))
	writeU32(out, uint32(newStylesStart))
	running := 0
	for _, e := range encoded {
		writeU32(out, uint32(running))
		running += len(e)
	}
	for _, off := range p.styleOffsets {
		writeU32(out, off)
	}
	for _, e := range encoded {
		out.Write(e)
	}
	for out.Len()&3 != 0 {
		out.WriteByte(0)
	}
	out.Write(p.styleData)
	return out.Bytes(), nil
}

func encodeUTF8String(s string) []byte {
	utf8Bytes := []byte(s)
	out := bytes.NewBuffer(make([]byte, 0, len(utf8Bytes)+8))
	writeLength8(out, len([]rune(s)))
	writeLength8(out, len(utf8Bytes))
	out.Write(utf8Bytes)
	out.WriteByte(0)
	return out.Bytes()
}

func encodeUTF16String(s string) []byte {
	units := utf16.Encode([]rune(s))
	out := bytes.NewBuffer(make([]byte, 0, len(units)*2+6))
	writeLength16(out, len(units))
	for _, unit := range units {
		writeU16(out, unit)
	}
	writeU16(out, 0)
	return out.Bytes()
}

func writeLength8(out *bytes.Buffer, n int) {
	if n > 0x7f {
		out.WriteByte(byte((n>>8)&0x7f) | 0x80)
		out.WriteByte(byte(n))
	} else {
		out.WriteByte(byte(n))
	}
}

func writeLength16(out *bytes.Buffer, n int) {
	if n > 0x7fff {
		writeU16(out, uint16((n>>16)&0x7fff)|0x8000)
		writeU16(out, uint16(n))
	} else {
		writeU16(out, uint16(n))
	}
}

func align4(n int) int {
	return (n + 3) &^ 3
}

func u16(data []byte, off int) uint16 {
	return binary.LittleEndian.Uint16(data[off:])
}

func u32(data []byte, off int) uint32 {
	return binary.LittleEndian.Uint32(data[off:])
}

func putU16(data []byte, off int, v uint16) {
	binary.LittleEndian.PutUint16(data[off:], v)
}

func putU32(data []byte, off int, v uint32) {
	binary.LittleEndian.PutUint32(data[off:], v)
}

func writeU16(out *bytes.Buffer, v uint16) {
	_ = binary.Write(out, binary.LittleEndian, v)
}

func writeU32(out *bytes.Buffer, v uint32) {
	_ = binary.Write(out, binary.LittleEndian, v)
}
