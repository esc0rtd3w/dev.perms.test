package axml

import (
	"bytes"
	"fmt"
	"math"
	"sort"
)

const (
	xmlType            = 0x0003
	resMapType         = 0x0180
	startNamespaceType = 0x0100
	endNamespaceType   = 0x0101
	startElementType   = 0x0102
	endElementType     = 0x0103
	cdataType          = 0x0104
)

const (
	typeNull       = 0x00
	typeReference  = 0x01
	typeAttribute  = 0x02
	typeString     = 0x03
	typeFloat      = 0x04
	typeDimension  = 0x05
	typeFraction   = 0x06
	typeIntDec     = 0x10
	typeIntHex     = 0x11
	typeIntBool    = 0x12
	typeColorARGB8 = 0x1c
	typeColorRGB8  = 0x1d
	typeColorARGB4 = 0x1e
	typeColorRGB4  = 0x1f
)

// DecodeManifest converts Android's binary XML format into readable XML. It is
// intentionally dependency-free so the tool can be cross-compiled as a small
// Android-native binary.
func DecodeManifest(data []byte) (string, error) {
	if len(data) < 8 {
		return "", fmt.Errorf("short binary XML")
	}
	if le16(data) != xmlType {
		return "", fmt.Errorf("not an Android binary XML document")
	}
	total := int(le32(data[4:]))
	if total <= 0 || total > len(data) {
		return "", fmt.Errorf("invalid binary XML size")
	}
	off := int(le16(data[2:]))
	if off < 8 || off > total {
		return "", fmt.Errorf("invalid binary XML header size")
	}

	pool, consumed, err := parseStringPool(data, off)
	if err != nil {
		return "", err
	}
	off += consumed

	ns := newNamespaceStack()
	var out bytes.Buffer
	out.WriteString("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
	depth := 0

	for off+8 <= total {
		typ := le16(data[off:])
		headerSize := int(le16(data[off+2:]))
		size := int(le32(data[off+4:]))
		if headerSize < 8 || size < headerSize || off+size > total {
			return "", fmt.Errorf("invalid XML chunk at 0x%x", off)
		}

		switch typ {
		case resMapType:
			// Resource IDs are useful when string names are missing. The current
			// This implementation needs manifest preview/repack foundation first, and most
			// manifests keep attribute names in the string pool.
		case startNamespaceType:
			if size >= 24 {
				ns.push(pool.get(le32(data[off+16:])), pool.get(le32(data[off+20:])))
			}
		case endNamespaceType:
			if size >= 24 {
				ns.pop(pool.get(le32(data[off+16:])), pool.get(le32(data[off+20:])))
			}
		case startElementType:
			if size < 36 {
				return "", fmt.Errorf("short start element chunk")
			}
			name := pool.get(le32(data[off+20:]))
			attrStart := int(le16(data[off+24:]))
			attrSize := int(le16(data[off+26:]))
			attrCount := int(le16(data[off+28:]))
			attrsOff := off + 16 + attrStart
			if attrSize <= 0 || attrsOff+attrSize*attrCount > off+size {
				return "", fmt.Errorf("invalid attributes for %s", name)
			}
			writeIndent(&out, depth)
			out.WriteByte('<')
			out.WriteString(qualifyName(pool.get(le32(data[off+16:])), name, ns))

			declarations := ns.pendingDeclarations()
			sort.SliceStable(declarations, func(i, j int) bool { return declarations[i].prefix < declarations[j].prefix })
			for _, d := range declarations {
				if d.prefix == "" {
					fmt.Fprintf(&out, " xmlns=\"%s\"", escapeXML(d.uri))
				} else {
					fmt.Fprintf(&out, " xmlns:%s=\"%s\"", sanitizeName(d.prefix, "ns"), escapeXML(d.uri))
				}
			}
			ns.markDeclarationsWritten()

			for i := 0; i < attrCount; i++ {
				a := attrsOff + i*attrSize
				attrNS := pool.get(le32(data[a:]))
				attrName := pool.get(le32(data[a+4:]))
				raw := pool.get(le32(data[a+8:]))
				dataType := data[a+15]
				valueData := le32(data[a+16:])
				value := formatValue(pool, raw, dataType, valueData)
				fmt.Fprintf(&out, "\n")
				writeIndent(&out, depth+1)
				fmt.Fprintf(&out, "%s=\"%s\"", qualifyName(attrNS, attrName, ns), escapeXML(value))
			}
			out.WriteString(">\n")
			depth++
		case endElementType:
			if size < 24 {
				return "", fmt.Errorf("short end element chunk")
			}
			depth--
			if depth < 0 {
				depth = 0
			}
			name := pool.get(le32(data[off+20:]))
			writeIndent(&out, depth)
			out.WriteString("</")
			out.WriteString(qualifyName(pool.get(le32(data[off+16:])), name, ns))
			out.WriteString(">\n")
		case cdataType:
			if size >= 28 {
				writeIndent(&out, depth)
				out.WriteString(escapeXML(pool.get(le32(data[off+16:]))))
				out.WriteByte('\n')
			}
		}
		off += size
	}
	return out.String(), nil
}

func formatValue(pool *stringPool, raw string, dataType byte, data uint32) string {
	if raw != "" {
		return raw
	}
	switch dataType {
	case typeNull:
		return ""
	case typeReference:
		return fmt.Sprintf("@0x%08x", data)
	case typeAttribute:
		return fmt.Sprintf("?0x%08x", data)
	case typeString:
		return pool.get(data)
	case typeFloat:
		return fmt.Sprintf("%g", float32FromBits(data))
	case typeDimension:
		return fmt.Sprintf("0x%08x", data)
	case typeFraction:
		return fmt.Sprintf("0x%08x", data)
	case typeIntDec:
		return fmt.Sprintf("%d", int32(data))
	case typeIntHex:
		return fmt.Sprintf("0x%08x", data)
	case typeIntBool:
		if data != 0 {
			return "true"
		}
		return "false"
	case typeColorARGB8, typeColorRGB8, typeColorARGB4, typeColorRGB4:
		return fmt.Sprintf("#%08x", data)
	default:
		return fmt.Sprintf("0x%08x", data)
	}
}

func float32FromBits(v uint32) float32 {
	return math.Float32frombits(v)
}
