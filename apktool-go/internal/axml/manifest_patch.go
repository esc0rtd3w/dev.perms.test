package axml

import (
	"bytes"
	"fmt"
	"regexp"
	"strings"
)

const (
	manifestElement     = "manifest"
	providerElement     = "provider"
	packageAttrName     = "package"
	labelAttrName       = "label"
	authoritiesAttrName = "authorities"

	attrLabelID       = 0x01010001
	attrAuthoritiesID = 0x01010018
)

var (
	manifestPackagePattern     = regexp.MustCompile(`(?is)<manifest\b[^>]*\bpackage\s*=\s*["']([^"']+)["']`)
	applicationLabelPattern    = regexp.MustCompile(`(?is)<application\b[^>]*\bandroid:label\s*=\s*["']([^"']+)["']`)
	providerPattern            = regexp.MustCompile(`(?is)<provider\b[^>]*>`)
	providerAuthoritiesPattern = regexp.MustCompile(`(?is)\bandroid:authorities\s*=\s*["']([^"']+)["']`)
)

type ManifestTextPatchOptions struct {
	Logf func(format string, args ...any)
}

type manifestPatchRequest struct {
	packageName string
	label       string
	authorities []string
}

// PatchManifestFromDecodedText applies the focused text-manifest changes that
// apktool-go currently understands to the preserved binary AndroidManifest.xml.
// This is not a full binary XML compiler yet; it deliberately patches only the
// attributes current decode/repack workflows need for package/app rename workflows.
func PatchManifestFromDecodedText(binaryManifest []byte, decodedText []byte, opts ManifestTextPatchOptions) ([]byte, bool, error) {
	req := manifestPatchRequestFromText(string(decodedText))
	if req.empty() {
		return binaryManifest, false, nil
	}
	if len(binaryManifest) < 12 || u32(binaryManifest, 0) != fullChunkXML {
		return nil, false, fmt.Errorf("AndroidManifest.xml is not Android binary XML")
	}
	xmlSize := int(u32(binaryManifest, 4))
	if xmlSize <= 0 || xmlSize > len(binaryManifest) {
		xmlSize = len(binaryManifest)
	}

	stringPoolOffset := 8
	if stringPoolOffset+28 > len(binaryManifest) || u32(binaryManifest, stringPoolOffset) != fullChunkStringPool {
		return nil, false, fmt.Errorf("binary manifest string pool was not found")
	}
	pool, err := parsePatchStringPool(binaryManifest, stringPoolOffset)
	if err != nil {
		return nil, false, err
	}
	stringPoolEnd := stringPoolOffset + pool.chunkSize
	if stringPoolEnd > len(binaryManifest) {
		return nil, false, fmt.Errorf("binary manifest string pool extends past file data")
	}

	resourceMapOffset := -1
	resourceMapSize := 0
	pos := stringPoolEnd
	if pos+8 <= len(binaryManifest) && u32(binaryManifest, pos) == fullChunkResourceMap {
		resourceMapOffset = pos
		resourceMapSize = int(u32(binaryManifest, pos+4))
		if resourceMapSize < 8 || pos+resourceMapSize > len(binaryManifest) {
			return nil, false, fmt.Errorf("binary manifest resource map is malformed")
		}
		pos += resourceMapSize
	}

	changedPool := false
	packageNameIndex := -1
	packageValueIndex := -1
	labelNameIndex := -1
	labelValueIndex := -1
	authoritiesNameIndex := -1
	authorityValueIndexes := make([]int, 0, len(req.authorities))
	androidNSIndex := pool.indexOf(androidNamespaceURI)

	if req.packageName != "" {
		packageNameIndex, changedPool = pool.indexOrAppend(packageAttrName, changedPool)
		packageValueIndex, changedPool = pool.indexOrAppend(req.packageName, changedPool)
	}
	if req.label != "" && androidNSIndex >= 0 {
		labelNameIndex, changedPool = pool.indexOrAppend(labelAttrName, changedPool)
		labelValueIndex, changedPool = pool.indexOrAppend(req.label, changedPool)
	}
	if len(req.authorities) > 0 && androidNSIndex >= 0 {
		authoritiesNameIndex, changedPool = pool.indexOrAppend(authoritiesAttrName, changedPool)
		for _, authority := range req.authorities {
			idx := -1
			idx, changedPool = pool.indexOrAppend(authority, changedPool)
			authorityValueIndexes = append(authorityValueIndexes, idx)
		}
	}

	var patchedChunks bytes.Buffer
	changedChunks := false
	authorityCursor := 0
	for off := pos; off+8 <= xmlSize; {
		typ := u32(binaryManifest, off)
		size := int(u32(binaryManifest, off+4))
		if size < 8 || off+size > xmlSize {
			return nil, false, fmt.Errorf("malformed XML chunk at offset 0x%x", off)
		}
		chunk := append([]byte(nil), binaryManifest[off:off+size]...)
		if typ == fullChunkStartElement && size >= 36 {
			element := pool.stringAt(int(u32(chunk, 20)))
			switch element {
			case manifestElement:
				if packageNameIndex >= 0 && packageValueIndex >= 0 {
					var patched bool
					chunk, patched, err = patchExistingStringAttribute(chunk, packageNameIndex, 0, packageValueIndex)
					if err != nil {
						return nil, false, err
					}
					if patched {
						changedChunks = true
						logPatch(opts, "patched manifest package=%s", req.packageName)
					}
				}
			case applicationElement:
				if labelNameIndex >= 0 && labelValueIndex >= 0 {
					var patched bool
					chunk, patched, err = patchExistingStringAttribute(chunk, labelNameIndex, attrLabelID, labelValueIndex)
					if err != nil {
						return nil, false, err
					}
					if patched {
						changedChunks = true
						logPatch(opts, "patched application label=%s", req.label)
					}
				}
			case providerElement:
				if authoritiesNameIndex >= 0 && authorityCursor < len(authorityValueIndexes) {
					var patched bool
					chunk, patched, err = patchExistingStringAttribute(chunk, authoritiesNameIndex, attrAuthoritiesID, authorityValueIndexes[authorityCursor])
					if err != nil {
						return nil, false, err
					}
					if patched {
						changedChunks = true
						logPatch(opts, "patched provider authorities=%s", req.authorities[authorityCursor])
					}
					authorityCursor++
				}
			}
		}
		patchedChunks.Write(chunk)
		off += size
	}

	if !changedPool && !changedChunks {
		return binaryManifest, false, nil
	}
	stringPoolBytes := binaryManifest[stringPoolOffset:stringPoolEnd]
	if changedPool {
		stringPoolBytes, err = pool.toBytes()
		if err != nil {
			return nil, false, err
		}
	}
	resourceMapBytes := []byte(nil)
	if resourceMapOffset >= 0 && resourceMapSize > 0 {
		resourceMapBytes = append([]byte(nil), binaryManifest[resourceMapOffset:resourceMapOffset+resourceMapSize]...)
	}
	newSize := 8 + len(stringPoolBytes) + len(resourceMapBytes) + patchedChunks.Len()
	out := bytes.NewBuffer(make([]byte, 0, newSize))
	writeU32(out, fullChunkXML)
	writeU32(out, uint32(newSize))
	out.Write(stringPoolBytes)
	out.Write(resourceMapBytes)
	out.Write(patchedChunks.Bytes())
	return out.Bytes(), true, nil
}

func manifestPatchRequestFromText(xml string) manifestPatchRequest {
	var req manifestPatchRequest
	if m := manifestPackagePattern.FindStringSubmatch(xml); len(m) == 2 {
		req.packageName = strings.TrimSpace(m[1])
	}
	if m := applicationLabelPattern.FindStringSubmatch(xml); len(m) == 2 {
		req.label = strings.TrimSpace(m[1])
	}
	providers := providerPattern.FindAllString(xml, -1)
	for _, provider := range providers {
		if m := providerAuthoritiesPattern.FindStringSubmatch(provider); len(m) == 2 {
			authority := strings.TrimSpace(m[1])
			if authority != "" {
				req.authorities = append(req.authorities, authority)
			}
		}
	}
	return req
}

func (r manifestPatchRequest) empty() bool {
	return r.packageName == "" && r.label == "" && len(r.authorities) == 0
}

func (p *patchStringPool) indexOrAppend(s string, changed bool) (int, bool) {
	if idx := p.indexOf(s); idx >= 0 {
		return idx, changed
	}
	p.strings = append(p.strings, s)
	return len(p.strings) - 1, true
}

func (p *patchStringPool) stringAt(index int) string {
	if p == nil || index < 0 || index >= len(p.strings) {
		return ""
	}
	return p.strings[index]
}

func patchExistingStringAttribute(chunk []byte, nameIndex int, resourceID uint32, valueIndex int) ([]byte, bool, error) {
	chunkSize := int(u32(chunk, 4))
	if chunkSize < 36 || chunkSize > len(chunk) {
		return nil, false, fmt.Errorf("start element chunk is malformed")
	}
	attrStart := int(u16(chunk, 24))
	attrSize := int(u16(chunk, 26))
	attrCount := int(u16(chunk, 28))
	attrBase := 16 + attrStart
	if attrStart < 20 || attrSize < 20 || attrBase+attrCount*attrSize > chunkSize {
		return nil, false, fmt.Errorf("attribute table is malformed")
	}
	changed := false
	out := append([]byte(nil), chunk...)
	for i := 0; i < attrCount; i++ {
		p := attrBase + i*attrSize
		currentName := int(u32(out, p+4))
		if currentName != nameIndex {
			// Some manifests rely on the resource map for framework attribute
			// identity. The focused patcher mainly uses string names, but keep
			// the resource id hook for attributes such as label/authorities.
			if resourceID == 0 {
				continue
			}
		}
		if currentName == nameIndex {
			oldRaw := u32(out, p+8)
			oldData := u32(out, p+16)
			oldType := out[p+15]
			putU32(out, p+8, uint32(valueIndex))
			putU16(out, p+12, 8)
			out[p+14] = 0
			out[p+15] = typeString
			putU32(out, p+16, uint32(valueIndex))
			if oldRaw != uint32(valueIndex) || oldData != uint32(valueIndex) || oldType != typeString {
				changed = true
			}
			return out, changed, nil
		}
	}
	return chunk, false, nil
}

func logPatch(opts ManifestTextPatchOptions, format string, args ...any) {
	if opts.Logf != nil {
		opts.Logf(format, args...)
	}
}
