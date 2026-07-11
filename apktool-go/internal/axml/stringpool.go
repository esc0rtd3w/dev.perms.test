package axml

import (
	"encoding/binary"
	"fmt"
	"unicode/utf16"
	"unicode/utf8"
)

const stringPoolType = 0x0001
const utf8Flag = 0x00000100

type stringPool struct {
	strings []string
}

func parseStringPool(data []byte, offset int) (*stringPool, int, error) {
	if len(data) < offset+28 {
		return nil, 0, fmt.Errorf("short string-pool chunk")
	}
	chunkType := le16(data[offset:])
	if chunkType != stringPoolType {
		return nil, 0, fmt.Errorf("chunk 0x%04x is not a string pool", chunkType)
	}
	chunkSize := int(le32(data[offset+4:]))
	if chunkSize <= 0 || offset+chunkSize > len(data) {
		return nil, 0, fmt.Errorf("invalid string-pool size")
	}
	count := int(le32(data[offset+8:]))
	flags := le32(data[offset+16:])
	stringsStart := int(le32(data[offset+20:]))
	offsetsBase := offset + int(le16(data[offset+2:]))
	if offsetsBase+count*4 > offset+chunkSize || stringsStart > chunkSize {
		return nil, 0, fmt.Errorf("invalid string-pool offsets")
	}

	pool := &stringPool{strings: make([]string, count)}
	for i := 0; i < count; i++ {
		rel := int(le32(data[offsetsBase+i*4:]))
		pos := offset + stringsStart + rel
		if pos < offset || pos >= offset+chunkSize {
			return nil, 0, fmt.Errorf("invalid string offset %d", i)
		}
		var s string
		var err error
		if flags&utf8Flag != 0 {
			s, err = decodeUTF8String(data[pos : offset+chunkSize])
		} else {
			s, err = decodeUTF16String(data[pos : offset+chunkSize])
		}
		if err != nil {
			return nil, 0, fmt.Errorf("string %d: %w", i, err)
		}
		pool.strings[i] = s
	}
	return pool, chunkSize, nil
}

func (p *stringPool) get(index uint32) string {
	if p == nil || index == 0xffffffff || int(index) >= len(p.strings) {
		return ""
	}
	return p.strings[index]
}

func decodeUTF8String(data []byte) (string, error) {
	_, n, err := readLength8(data)
	if err != nil {
		return "", err
	}
	byteLen, m, err := readLength8(data[n:])
	if err != nil {
		return "", err
	}
	start := n + m
	end := start + byteLen
	if end > len(data) {
		return "", fmt.Errorf("utf8 string out of bounds")
	}
	// Android stores a NUL terminator after the bytes. It is useful for sanity,
	// but some malformed APKs still decode fine without relying on it.
	return string(data[start:end]), nil
}

func decodeUTF16String(data []byte) (string, error) {
	units, n, err := readLength16(data)
	if err != nil {
		return "", err
	}
	bytesNeeded := units * 2
	if n+bytesNeeded > len(data) {
		return "", fmt.Errorf("utf16 string out of bounds")
	}
	raw := make([]uint16, units)
	for i := 0; i < units; i++ {
		raw[i] = binary.LittleEndian.Uint16(data[n+i*2:])
	}
	return string(utf16.Decode(raw)), nil
}

func readLength8(data []byte) (int, int, error) {
	if len(data) == 0 {
		return 0, 0, fmt.Errorf("short utf8 length")
	}
	first := data[0]
	if first&0x80 == 0 {
		return int(first), 1, nil
	}
	if len(data) < 2 {
		return 0, 0, fmt.Errorf("short utf8 long length")
	}
	return int(first&0x7f)<<8 | int(data[1]), 2, nil
}

func readLength16(data []byte) (int, int, error) {
	if len(data) < 2 {
		return 0, 0, fmt.Errorf("short utf16 length")
	}
	first := le16(data)
	if first&0x8000 == 0 {
		return int(first), 2, nil
	}
	if len(data) < 4 {
		return 0, 0, fmt.Errorf("short utf16 long length")
	}
	second := le16(data[2:])
	return int(first&0x7fff)<<16 | int(second), 4, nil
}

func validXMLName(s string) bool {
	if s == "" {
		return false
	}
	for i, r := range s {
		if i == 0 {
			if !(r == '_' || r == ':' || r >= 'A' && r <= 'Z' || r >= 'a' && r <= 'z' || r >= 0xC0) {
				return false
			}
			continue
		}
		if !(r == '_' || r == ':' || r == '-' || r == '.' || r >= '0' && r <= '9' || r >= 'A' && r <= 'Z' || r >= 'a' && r <= 'z' || r >= 0xC0 || utf8.ValidRune(r)) {
			return false
		}
	}
	return true
}
