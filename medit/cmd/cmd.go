package cmd

import (
	"bytes"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"unsafe"

	"syscall"

	"github.com/sterrasec/apk-medit/pkg/converter"
	"github.com/sterrasec/apk-medit/pkg/memory"
)

var tids []int
var isAttached = false
var stringPatchTruncate = true

func SetMaxResults(max int) {
	memory.SetMaxResults(max)
}

func SetSkipResults(skip int) {
	memory.SetSkipResults(skip)
}

func SetStringCaseSensitive(enabled bool) {
	memory.SetStringCaseSensitive(enabled)
}

func SetStringPatchTruncate(enabled bool) {
	stringPatchTruncate = enabled
}

type Found struct {
	Addrs       []int    `json:"addrs"`
	Values      []string `json:"values,omitempty"`
	ConverterID string   `json:"converter"`
	DataType    string   `json:"data_type"`
	converter   func(string) ([]byte, error)
}

func newFound(addrs []int, converterID string, dataType string) Found {
	f := Found{
		Addrs:       addrs,
		ConverterID: converterID,
		DataType:    dataType,
	}
	f.restoreConverter()
	return f
}

func newFoundWithValues(addrs []int, values []string, converterID string, dataType string) Found {
	f := newFound(addrs, converterID, dataType)
	f.Values = values
	return f
}

func newFoundWithRepeatedValue(addrs []int, value string, converterID string, dataType string) Found {
	values := make([]string, len(addrs))
	for i := range values {
		values[i] = value
	}
	return newFoundWithValues(addrs, values, converterID, dataType)
}
func newFoundWithStringValues(memPath string, addrs []int, targetVal string, converterID string, dataType string) Found {
	return newFoundWithValues(addrs, readStringValues(memPath, addrs, len([]byte(targetVal)), targetVal), converterID, dataType)
}

func readStringValues(memPath string, addrs []int, byteLen int, fallback string) []string {
	values := make([]string, len(addrs))
	for i := range values {
		values[i] = fallback
	}
	if byteLen <= 0 || len(addrs) == 0 {
		return values
	}
	f, err := os.Open(memPath)
	if err != nil {
		return values
	}
	defer f.Close()
	for i, addr := range addrs {
		buf := make([]byte, byteLen)
		n, err := f.ReadAt(buf, int64(addr))
		if err == nil || n > 0 {
			values[i] = string(buf[:n])
		}
	}
	return values
}

func (f *Found) restoreConverter() {
	if f.ConverterID == "" {
		f.ConverterID = converterIDForDataType(f.DataType)
	}
	f.converter = converterForID(f.ConverterID)
}

func converterIDForDataType(dataType string) string {
	normalized := strings.ToLower(strings.TrimSpace(dataType))
	if strings.Contains(normalized, "hex") || strings.Contains(normalized, "bytes") || strings.HasPrefix(normalized, "file:") {
		return "bytes"
	}
	switch normalized {
	case "utf-8 string", "string":
		return "string"
	case "word":
		return "word"
	case "dword":
		return "dword"
	case "qword":
		return "qword"
	default:
		return ""
	}
}

func converterForID(converterID string) func(string) ([]byte, error) {
	switch converterID {
	case "string":
		return converter.StringToBytes
	case "word":
		return converter.WordToBytes
	case "dword":
		return converter.DwordToBytes
	case "qword":
		return converter.QwordToBytes
	case "bytes":
		return parseHexByteText
	default:
		return converter.StringToBytes
	}
}

func RestoreFoundConverters(founds []Found) []Found {
	for i := range founds {
		founds[i].restoreConverter()
	}
	return founds
}

func Plist() (string, error) {
	cmd := exec.Command("ps", "-e")
	var out bytes.Buffer
	cmd.Stdout = &out
	if err := cmd.Run(); err != nil {
		return "", err
	}

	re := regexp.MustCompile(`\s+`)
	line, err := out.ReadString('\n')
	pids := make(map[string]string)
	for err == nil && len(line) != 0 {
		normalized := strings.TrimSpace(re.ReplaceAllString(string(line), " "))
		s := strings.Split(normalized, " ")
		if len(s) < 2 {
			line, err = out.ReadString('\n')
			continue
		}
		pid := s[1]
		cmd := s[len(s)-1]
		if pid != "PID" && cmd != "" && cmd != "ps" && cmd != "sh" && cmd != "medit" {
			fmt.Printf("Package: %s, PID: %s\n", cmd, pid)
			pids[cmd] = pid
		}
		line, err = out.ReadString('\n')
	}

	current_path, _ := os.Getwd()
	_, package_name := filepath.Split(current_path)
	for cmd, pid := range pids {
		if cmd == package_name {
			fmt.Printf("Target PID has been set to %s.\n", pid)
			return pid, nil
		}
	}

	return "", nil
}

func Attach(pid string) error {
	if pid == "" {
		return errors.New("PID cannot be specified")
	}
	if isAttached {
		fmt.Println("Already attached.")
		return nil
	}

	fmt.Printf("Target PID: %s\n", pid)
	tidDir := fmt.Sprintf("/proc/%s/task", pid)
	if _, err := os.Stat(tidDir); err == nil {
		tidInfo, err := ioutil.ReadDir(tidDir)
		if err != nil {
			return err
		}

		tids = []int{}
		for _, t := range tidInfo {
			tid, _ := strconv.Atoi(t.Name())
			if err := syscall.PtraceAttach(tid); err == nil {
				if err := wait(tid); err != nil {
					fmt.Printf("Failed wait TID: %d, %s\n", tid, err)
					_ = syscall.PtraceDetach(tid)
					continue
				}
				fmt.Printf("Attached TID: %d\n", tid)
				tids = append(tids, tid)
			} else {
				fmt.Printf("attach failed: %s\n", err)
			}
		}
		if len(tids) == 0 {
			return errors.New("attach failed")
		}

		isAttached = true

	} else if os.IsNotExist(err) {
		return errors.New("PID must be an integer that exists")
	} else {
		return err
	}
	return nil
}

func Find(pid string, targetVal string, dataType string) ([]Found, error) {
	founds := []Found{}
	// parse /proc/<pid>/map, and get writable area
	mapsPath := fmt.Sprintf("/proc/%s/maps", pid)
	addrRanges, err := memory.GetWritableAddrRanges(mapsPath)
	// search value in /proc/<pid>/mem
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	if err != nil {
		return nil, err
	}

	if dataType == "all" {
		// String matches are useful for both text and numeric-looking values.
		foundAddrs, err := memory.FindString(memPath, targetVal, addrRanges)
		if err == nil && len(foundAddrs) > 0 {
			founds = append(founds, newFoundWithStringValues(memPath, foundAddrs, targetVal, "string", "UTF-8 string"))
		} else if _, ok := err.(memory.TooManyErr); ok {
			return founds, err
		}

		// Non-numeric text should not be passed through numeric converters.
		if _, err := strconv.ParseInt(targetVal, 0, 64); err != nil {
			return founds, nil
		}
		fmt.Println("------------------------")

		foundAddrs, err = memory.FindWord(memPath, targetVal, addrRanges)
		if err == nil {
			if len(foundAddrs) > 0 {
				founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetVal, "word", "word"))
			}
		} else if _, ok := err.(memory.TooManyErr); ok {
			return founds, err
		}
		fmt.Println("------------------------")

		foundAddrs, err = memory.FindDword(memPath, targetVal, addrRanges)
		if err == nil {
			if len(foundAddrs) > 0 {
				founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetVal, "dword", "dword"))
			}
		} else if _, ok := err.(memory.TooManyErr); ok {
			return founds, err
		}
		fmt.Println("------------------------")

		foundAddrs, err = memory.FindQword(memPath, targetVal, addrRanges)
		if err == nil {
			if len(foundAddrs) > 0 {
				founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetVal, "qword", "qword"))
			}
		} else if _, ok := err.(memory.TooManyErr); ok {
			return founds, err
		}
		return founds, nil

	} else if dataType == "string" {
		foundAddrs, err := memory.FindString(memPath, targetVal, addrRanges)
		if err == nil {
			if len(foundAddrs) > 0 {
				founds = append(founds, newFoundWithStringValues(memPath, foundAddrs, targetVal, "string", "UTF-8 string"))
			}
			return founds, nil
		} else if _, ok := err.(memory.TooManyErr); ok {
			return founds, err
		}

	} else if dataType == "word" {
		foundAddrs, err := memory.FindWord(memPath, targetVal, addrRanges)
		if err == nil {
			if len(foundAddrs) > 0 {
				founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetVal, "word", "word"))
			}
			return founds, nil
		} else if _, ok := err.(memory.TooManyErr); ok {
			return founds, err
		}

	} else if dataType == "dword" {
		foundAddrs, err := memory.FindDword(memPath, targetVal, addrRanges)
		if err == nil {
			if len(foundAddrs) > 0 {
				founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetVal, "dword", "dword"))
			}
			return founds, nil
		} else if _, ok := err.(memory.TooManyErr); ok {
			return founds, err
		}

	} else if dataType == "qword" {
		foundAddrs, err := memory.FindQword(memPath, targetVal, addrRanges)
		if err == nil {
			if len(foundAddrs) > 0 {
				founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetVal, "qword", "qword"))
			}
			return founds, nil
		} else if _, ok := err.(memory.TooManyErr); ok {
			return founds, err
		}
	}

	return nil, errors.New("Error: specified datatype does not exist")
}

type magicSignature struct {
	Extension string
	Label     string
	Hex       string
}

func SearchMagic(pid string, extension string) ([]Found, error) {
	extension = normalizeMagicExtension(extension)
	signatures := signaturesForMagicExtension(extension)
	if len(signatures) == 0 {
		return nil, fmt.Errorf("unsupported file type magic: %s", extension)
	}
	mapsPath := fmt.Sprintf("/proc/%s/maps", pid)
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	addrRanges, err := memory.GetReadableAddrRanges(mapsPath)
	if err != nil {
		return nil, err
	}
	founds := []Found{}
	fmt.Printf("Search File Type .%s...\n", extension)
	for i, sig := range signatures {
		if i > 0 {
			fmt.Println("------------------------")
		}
		targetBytes, err := hex.DecodeString(sig.Hex)
		if err != nil {
			return founds, err
		}
		fmt.Printf("Magic: %s (%s)\n", sig.Label, strings.ToUpper(sig.Hex))
		foundAddrs, err := memory.FindDataInAddrRanges(memPath, targetBytes, addrRanges)
		fmt.Printf("Found: %d!!\n", len(foundAddrs))
		printMaxResultCapNote(len(foundAddrs))
		if len(foundAddrs) < 10 {
			for _, v := range foundAddrs {
				fmt.Printf("Address: 0x%x Value: %s\n", v, sig.Label)
			}
		}
		if len(foundAddrs) > 0 {
			founds = append(founds, newFoundWithRepeatedValue(foundAddrs, sig.Label, "bytes", "file:"+extension))
		}
		if err != nil {
			return founds, err
		}
	}
	return founds, nil
}

func FindBytes(pid string, hexText string) ([]Found, error) {
	targetBytes, err := parseHexByteText(hexText)
	if err != nil {
		return nil, err
	}
	mapsPath := fmt.Sprintf("/proc/%s/maps", pid)
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	addrRanges, err := memory.GetWritableAddrRanges(mapsPath)
	if err != nil {
		return nil, err
	}
	fmt.Println("Search Hex Bytes...")
	targetHex := strings.ToUpper(hex.EncodeToString(targetBytes))
	fmt.Printf("Target Value: %s\n", targetHex)
	foundAddrs, err := memory.FindDataInAddrRanges(memPath, targetBytes, addrRanges)
	fmt.Printf("Found: %d!!\n", len(foundAddrs))
	printMaxResultCapNote(len(foundAddrs))
	if len(foundAddrs) < 10 {
		for _, v := range foundAddrs {
			fmt.Printf("Address: 0x%x Value: %s\n", v, targetHex)
		}
	}
	founds := []Found{}
	if len(foundAddrs) > 0 {
		founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetHex, "bytes", "hex-bytes"))
	}
	return founds, err
}

func FindBytesMasked(pid string, hexText string, maskText string) ([]Found, error) {
	targetBytes, err := parseHexByteText(hexText)
	if err != nil {
		return nil, err
	}
	maskBytes, err := parseHexByteText(maskText)
	if err != nil {
		return nil, err
	}
	if len(maskBytes) != len(targetBytes) {
		return nil, errors.New("payload mask length must match hex byte length")
	}
	mapsPath := fmt.Sprintf("/proc/%s/maps", pid)
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	addrRanges, err := memory.GetWritableAddrRanges(mapsPath)
	if err != nil {
		return nil, err
	}
	fmt.Println("Search Hex Bytes With Mask...")
	targetHex := strings.ToUpper(hex.EncodeToString(targetBytes))
	maskHex := strings.ToUpper(hex.EncodeToString(maskBytes))
	fmt.Printf("Target Value: %s\n", targetHex)
	fmt.Printf("Compare Mask: %s\n", maskHex)
	foundAddrs, err := memory.FindDataInAddrRangesMasked(memPath, targetBytes, maskBytes, addrRanges)
	fmt.Printf("Found: %d!!\n", len(foundAddrs))
	printMaxResultCapNote(len(foundAddrs))
	if len(foundAddrs) < 10 {
		for _, v := range foundAddrs {
			fmt.Printf("Address: 0x%x Value: %s Mask: %s\n", v, targetHex, maskHex)
		}
	}
	founds := []Found{}
	if len(foundAddrs) > 0 {
		founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetHex, "bytes", "hex-bytes-mask"))
	}
	return founds, err
}

func normalizeMagicExtension(extension string) string {
	ext := strings.ToLower(strings.TrimSpace(extension))
	ext = strings.TrimPrefix(ext, ".")
	switch ext {
	case "jpeg", "jpe":
		return "jpg"
	case "apks", "apkm", "xapk", "jar":
		return "zip"
	case "so":
		return "elf"
	default:
		return ext
	}
}

func signaturesForMagicExtension(extension string) []magicSignature {
	switch normalizeMagicExtension(extension) {
	case "png":
		return []magicSignature{{"png", "PNG", "89504E470D0A1A0A"}}
	case "jpg":
		return []magicSignature{{"jpg", "JPEG", "FFD8FF"}}
	case "gif":
		return []magicSignature{{"gif", "GIF87a", "474946383761"}, {"gif", "GIF89a", "474946383961"}}
	case "webp":
		return []magicSignature{{"webp", "WEBP", "57454250"}}
	case "zip":
		return []magicSignature{{"zip", "ZIP local header", "504B0304"}, {"zip", "ZIP empty archive", "504B0506"}, {"zip", "ZIP spanning header", "504B0708"}}
	case "dex":
		return []magicSignature{{"dex", "DEX", "6465780A"}}
	case "elf":
		return []magicSignature{{"elf", "ELF", "7F454C46"}}
	case "ogg":
		return []magicSignature{{"ogg", "OggS", "4F676753"}}
	case "mp3":
		return []magicSignature{{"mp3", "ID3", "494433"}, {"mp3", "MPEG frame", "FFFB"}}
	case "wav":
		return []magicSignature{{"wav", "WAVE", "57415645"}}
	case "mp4":
		return []magicSignature{{"mp4", "ftyp", "66747970"}}
	case "xml":
		return []magicSignature{{"xml", "XML declaration", "3C3F786D6C"}, {"xml", "manifest tag", "3C6D616E6966657374"}}
	case "json":
		return []magicSignature{{"json", "JSON object", "7B22"}, {"json", "JSON array", "5B7B"}}
	case "js":
		return []magicSignature{
			{"js", "JavaScript function", "66756E6374696F6E"},
			{"js", "JavaScript const", "636F6E737420"},
			{"js", "JavaScript let", "6C657420"},
			{"js", "JavaScript var", "76617220"},
			{"js", "JavaScript import", "696D706F727420"},
		}
	default:
		return nil
	}
}

func Filter(pid string, targetVal string, prevFounds []Found) ([]Found, error) {
	founds := []Found{}
	mapsPath := fmt.Sprintf("/proc/%s/maps", pid)
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	writableAddrRanges, err := memory.GetWritableAddrRanges(mapsPath)
	if err != nil {
		return nil, err
	}
	// check if previous result address exists in current memory map
	for i, prevFound := range prevFounds {
		addrRanges := [][2]int{}
		targetBytes, err := prevFound.converter(targetVal)
		if err != nil {
			return founds, err
		}
		targetLength := len(targetBytes)
		fmt.Printf("Check previous results of searching %s...\n", prevFound.DataType)
		fmt.Printf("Target Value: %s(%v)\n", targetVal, targetBytes)
		for _, prevAddr := range prevFound.Addrs {
			for _, writable := range writableAddrRanges {
				if writable[0] < prevAddr && prevAddr < writable[1] {
					addrRanges = append(addrRanges, [2]int{prevAddr, prevAddr + targetLength})
				}
			}
		}
		var foundAddrs []int
		if prevFound.ConverterID == "string" || prevFound.DataType == "UTF-8 string" || prevFound.DataType == "string" {
			foundAddrs, _ = memory.FindString(memPath, targetVal, addrRanges)
		} else {
			foundAddrs, _ = memory.FindDataInAddrRanges(memPath, targetBytes, addrRanges)
			fmt.Printf("Found: %d!!\n", len(foundAddrs))
		}
		if len(foundAddrs) < 10 {
			for _, v := range foundAddrs {
				fmt.Printf("Address: 0x%x\n", v)
			}
		}
		if prevFound.ConverterID == "string" || prevFound.DataType == "UTF-8 string" || prevFound.DataType == "string" {
			founds = append(founds, newFoundWithStringValues(memPath, foundAddrs, targetVal, prevFound.ConverterID, prevFound.DataType))
		} else {
			founds = append(founds, newFoundWithRepeatedValue(foundAddrs, targetVal, prevFound.ConverterID, prevFound.DataType))
		}
		if i != len(prevFounds)-1 {
			fmt.Println("------------------------")
		}
	}
	return founds, nil
}

func normalizeNumericDataType(dataType string) string {
	switch strings.ToLower(strings.TrimSpace(dataType)) {
	case "word", "dword", "qword":
		return strings.ToLower(strings.TrimSpace(dataType))
	default:
		return "dword"
	}
}

func normalizeSnapshotDataType(dataType string) string {
	switch strings.ToLower(strings.TrimSpace(dataType)) {
	case "all", "word", "dword", "qword":
		return strings.ToLower(strings.TrimSpace(dataType))
	default:
		return "dword"
	}
}

func totalFoundAddressCount(founds []Found) int {
	total := 0
	for _, found := range founds {
		total += len(found.Addrs)
	}
	return total
}

func numericWidthForDataType(dataType string) int {
	switch normalizeNumericDataType(dataType) {
	case "word":
		return 2
	case "qword":
		return 8
	default:
		return 4
	}
}

func numericConverterIDForDataType(dataType string) string {
	return normalizeNumericDataType(dataType)
}

func readUintAt(memFile *os.File, addr int, width int) (uint64, error) {
	buf := make([]byte, width)
	if _, err := memFile.ReadAt(buf, int64(addr)); err != nil {
		return 0, err
	}
	switch width {
	case 2:
		return uint64(binary.LittleEndian.Uint16(buf)), nil
	case 4:
		return uint64(binary.LittleEndian.Uint32(buf)), nil
	case 8:
		return binary.LittleEndian.Uint64(buf), nil
	default:
		return 0, errors.New("unsupported numeric width")
	}
}

func numericCompare(current uint64, target uint64, op string) bool {
	switch op {
	case "gt":
		return current > target
	case "lt":
		return current < target
	case "ne":
		return current != target
	default:
		return current == target
	}
}

func isAddrInWritableRanges(addr int, width int, ranges [][2]int) bool {
	for _, r := range ranges {
		if r[0] <= addr && addr+width <= r[1] {
			return true
		}
	}
	return false
}

func printNumericModeHeader(prefix string, dataType string, targetVal string, op string) {
	symbol := "=="
	if op == "gt" {
		symbol = ">"
	} else if op == "lt" {
		symbol = "<"
	}
	fmt.Printf("%s %s...\n", prefix, normalizeNumericDataType(dataType))
	if targetVal != "" {
		fmt.Printf("Target Value: %s %s\n", symbol, targetVal)
	}
}

func printNumericFoundSummary(addrs []int, values []string) {
	fmt.Printf("Found: %d!!\n", len(addrs))
	if memory.SkippedResults() > 0 {
		fmt.Printf("Skipped: %d matching addresses before collecting this range.\n", memory.SkippedResults())
	}
	printMaxResultCapNote(len(addrs))
	if len(addrs) < 10 {
		for i, addr := range addrs {
			if i < len(values) && values[i] != "" {
				fmt.Printf("Address: 0x%x Value: %s\n", addr, values[i])
			} else {
				fmt.Printf("Address: 0x%x\n", addr)
			}
		}
	}
}

func hasReachedMaxResults(count int) bool {
	max := memory.MaxResults()
	return max > 0 && count >= max
}

func printMaxResultCapNote(count int) {
	if hasReachedMaxResults(count) {
		fmt.Printf("Result cap reached at %d addresses. Refine the scan or raise --max-results.\n", memory.MaxResults())
	} else if memory.MaxResults() == 0 {
		fmt.Println("Result cap disabled; state file contains the full result set.")
	}
}

func FindCompare(pid string, targetVal string, dataType string, op string) ([]Found, error) {
	dataType = normalizeNumericDataType(dataType)
	target, err := strconv.ParseUint(targetVal, 10, 64)
	if err != nil {
		return nil, err
	}
	founds, err := scanNumericRange(pid, dataType, op, target, true)
	if err != nil {
		return founds, err
	}
	return founds, nil
}

func Snapshot(pid string, dataType string) ([]Found, error) {
	dataType = normalizeSnapshotDataType(dataType)
	if dataType == "all" {
		allFounds := []Found{}
		for i, numericType := range []string{"word", "dword", "qword"} {
			if i > 0 {
				fmt.Println("------------------------")
			}
			founds, err := scanNumericRange(pid, numericType, "snapshot", 0, false)
			if len(founds) > 0 {
				allFounds = append(allFounds, founds...)
			}
			if err != nil {
				return allFounds, err
			}
			if hasReachedMaxResults(totalFoundAddressCount(allFounds)) {
				break
			}
		}
		return allFounds, nil
	}
	founds, err := scanNumericRange(pid, dataType, "snapshot", 0, false)
	if err != nil {
		return founds, err
	}
	return founds, nil
}

func scanNumericRange(pid string, dataType string, op string, target uint64, hasTarget bool) ([]Found, error) {
	mapsPath := fmt.Sprintf("/proc/%s/maps", pid)
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	addrRanges, err := memory.GetWritableAddrRanges(mapsPath)
	if err != nil {
		return nil, err
	}
	f, err := os.OpenFile(memPath, os.O_RDONLY, 0600)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	width := numericWidthForDataType(dataType)
	addrs := []int{}
	values := []string{}
	if op == "snapshot" {
		fmt.Printf("Snapshot %s...\n", dataType)
	} else {
		printNumericModeHeader("Search", dataType, strconv.FormatUint(target, 10), op)
	}
	bufSize := splitAlignedSize(0x5000000, width)
	if bufSize <= 0 {
		bufSize = width
	}
	buf := make([]byte, bufSize)
	for _, r := range addrRanges {
		beginAddr := r[0]
		endAddr := r[1]
		for chunkBegin := beginAddr; chunkBegin < endAddr; chunkBegin += bufSize {
			chunkEnd := chunkBegin + bufSize
			if chunkEnd > endAddr {
				chunkEnd = endAddr
			}
			chunkLen := chunkEnd - chunkBegin
			if chunkLen < width {
				continue
			}
			readBuf := buf[:chunkLen]
			data := memory.ReadMemory(f, readBuf, chunkBegin, chunkEnd)
			for off := 0; off+width <= len(data); off += width {
				current := readUintFromBytes(data[off:off+width], width)
				if !hasTarget || numericCompare(current, target, op) {
					if !memory.KeepSearchResult() {
						continue
					}
					addrs = append(addrs, chunkBegin+off)
					values = append(values, strconv.FormatUint(current, 10))
				}
				if hasReachedMaxResults(len(addrs)) {
					break
				}
			}
			if hasReachedMaxResults(len(addrs)) {
				break
			}
		}
		if hasReachedMaxResults(len(addrs)) {
			break
		}
	}
	printNumericFoundSummary(addrs, values)
	return []Found{newFoundWithValues(addrs, values, numericConverterIDForDataType(dataType), dataType)}, nil
}

func splitAlignedSize(size int, width int) int {
	if width <= 0 {
		return size
	}
	return size - (size % width)
}

func readUintFromBytes(buf []byte, width int) uint64 {
	switch width {
	case 2:
		return uint64(binary.LittleEndian.Uint16(buf))
	case 4:
		return uint64(binary.LittleEndian.Uint32(buf))
	case 8:
		return binary.LittleEndian.Uint64(buf)
	default:
		return 0
	}
}

func FilterCompare(pid string, targetVal string, prevFounds []Found, op string) ([]Found, error) {
	founds := []Found{}
	usePreviousValues := strings.TrimSpace(targetVal) == ""
	target := uint64(0)
	if !usePreviousValues {
		parsed, err := strconv.ParseUint(targetVal, 10, 64)
		if err != nil {
			return nil, err
		}
		target = parsed
	}
	mapsPath := fmt.Sprintf("/proc/%s/maps", pid)
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	writableAddrRanges, err := memory.GetWritableAddrRanges(mapsPath)
	if err != nil {
		return nil, err
	}
	f, err := os.OpenFile(memPath, os.O_RDONLY, 0600)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	previousTotal := totalFoundAddressCount(prevFounds)
	compared := 0
	missingPrevious := 0
	if previousTotal > 0 {
		fmt.Printf("Previous result count: %d\n", previousTotal)
	}
	for i, prevFound := range prevFounds {
		dataType := normalizeNumericDataType(prevFound.DataType)
		if prevFound.ConverterID == "string" || prevFound.DataType == "UTF-8 string" {
			continue
		}
		width := numericWidthForDataType(dataType)
		if usePreviousValues {
			printNumericPreviousHeader("Check previous results of", dataType, op)
		} else {
			printNumericModeHeader("Check previous results of", dataType, targetVal, op)
		}
		addrs := []int{}
		values := []string{}
		for idx, prevAddr := range prevFound.Addrs {
			if !isAddrInWritableRanges(prevAddr, width, writableAddrRanges) {
				continue
			}
			current, err := readUintAt(f, prevAddr, width)
			if err != nil {
				continue
			}
			compareTarget := target
			if usePreviousValues {
				if idx >= len(prevFound.Values) || strings.TrimSpace(prevFound.Values[idx]) == "" {
					missingPrevious++
					continue
				}
				parsed, err := strconv.ParseUint(strings.TrimSpace(prevFound.Values[idx]), 10, 64)
				if err != nil {
					missingPrevious++
					continue
				}
				compareTarget = parsed
			}
			compared++
			if numericCompare(current, compareTarget, op) {
				addrs = append(addrs, prevAddr)
				values = append(values, strconv.FormatUint(current, 10))
				if hasReachedMaxResults(len(addrs)) {
					break
				}
			}
		}
		printNumericFoundSummary(addrs, values)
		founds = append(founds, newFoundWithValues(addrs, values, numericConverterIDForDataType(dataType), dataType))
		if i != len(prevFounds)-1 {
			fmt.Println("------------------------")
		}
	}
	if usePreviousValues && compared == 0 {
		if missingPrevious > 0 {
			return founds, errors.New("No previous numeric values to compare")
		}
		return founds, errors.New("No numeric previous results")
	}
	if usePreviousValues {
		fmt.Printf("Compared: %d address%s.\n", compared, pluralSuffix(compared))
	}
	if missingPrevious > 0 {
		fmt.Printf("Skipped %d address%s without a previous value.\n", missingPrevious, pluralSuffix(missingPrevious))
	}
	return founds, nil
}

func printNumericPreviousHeader(prefix string, dataType string, op string) {
	symbol := ">"
	if op == "lt" {
		symbol = "<"
	} else if op == "eq" {
		symbol = "=="
	} else if op == "ne" {
		symbol = "!="
	}
	fmt.Printf("%s %s...\n", prefix, normalizeNumericDataType(dataType))
	fmt.Printf("Compare: current %s previous\n", symbol)
}

func pluralSuffix(n int) string {
	if n == 1 {
		return ""
	}
	return "es"
}

func WriteBytesWithoutPtrace(pid string, targetAddr int, hexText string) error {
	targetBytes, err := parseHexByteText(hexText)
	if err != nil {
		return err
	}
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	f, err := os.OpenFile(memPath, os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	defer f.Close()
	if err := memory.WriteMemory(f, targetAddr, targetBytes); err != nil {
		return err
	}
	fmt.Printf("Wrote %d byte%s at 0x%x.\n", len(targetBytes), bytePluralSuffix(len(targetBytes)), targetAddr)
	return nil
}

func WriteBytesWithPtrace(pid string, targetAddr int, hexText string) error {
	targetBytes, err := parseHexByteText(hexText)
	if err != nil {
		return err
	}
	if !isAttached {
		if err := Attach(pid); err != nil {
			return err
		}
	}
	tidInt, _ := strconv.Atoi(pid)
	if err := writeMemoryPtraceSafe(tidInt, targetAddr, targetBytes); err != nil {
		Detach()
		return err
	}
	Detach()
	fmt.Printf("Wrote %d byte%s at 0x%x.\n", len(targetBytes), bytePluralSuffix(len(targetBytes)), targetAddr)
	return nil
}

func WriteFileWithoutPtrace(pid string, targetAddr int, sourcePath string) error {
	targetBytes, err := readImportBytes(sourcePath)
	if err != nil {
		return err
	}
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	f, err := os.OpenFile(memPath, os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	defer f.Close()
	if err := memory.WriteMemory(f, targetAddr, targetBytes); err != nil {
		return err
	}
	fmt.Printf("Wrote %d byte%s from %s at 0x%x.\n", len(targetBytes), bytePluralSuffix(len(targetBytes)), sourcePath, targetAddr)
	return nil
}

func WriteFileWithPtrace(pid string, targetAddr int, sourcePath string) error {
	targetBytes, err := readImportBytes(sourcePath)
	if err != nil {
		return err
	}
	if !isAttached {
		if err := Attach(pid); err != nil {
			return err
		}
	}
	tidInt, _ := strconv.Atoi(pid)
	if err := writeMemoryPtraceSafe(tidInt, targetAddr, targetBytes); err != nil {
		Detach()
		return err
	}
	Detach()
	fmt.Printf("Wrote %d byte%s from %s at 0x%x.\n", len(targetBytes), bytePluralSuffix(len(targetBytes)), sourcePath, targetAddr)
	return nil
}

func readImportBytes(sourcePath string) ([]byte, error) {
	if strings.TrimSpace(sourcePath) == "" {
		return nil, errors.New("source path cannot be empty")
	}
	info, err := os.Stat(sourcePath)
	if err != nil {
		return nil, err
	}
	if info.IsDir() {
		return nil, errors.New("source path is a directory")
	}
	const maxImportBytes = 16 * 1024 * 1024
	if info.Size() > maxImportBytes {
		return nil, fmt.Errorf("source file is too large: %d bytes", info.Size())
	}
	data, err := os.ReadFile(sourcePath)
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, errors.New("source file is empty")
	}
	return data, nil
}

func parseHexByteText(hexText string) ([]byte, error) {
	clean := strings.TrimSpace(hexText)
	clean = strings.ReplaceAll(clean, "0x", "")
	clean = strings.ReplaceAll(clean, "0X", "")
	clean = strings.Map(func(r rune) rune {
		if r >= '0' && r <= '9' {
			return r
		}
		if r >= 'a' && r <= 'f' {
			return r
		}
		if r >= 'A' && r <= 'F' {
			return r
		}
		return -1
	}, clean)
	if clean == "" {
		return nil, errors.New("hex bytes cannot be empty")
	}
	if len(clean)%2 != 0 {
		return nil, errors.New("hex byte text must contain an even number of digits")
	}
	return hex.DecodeString(clean)
}

func bytePluralSuffix(n int) string {
	if n == 1 {
		return ""
	}
	return "s"
}

func PatchWithoutPtrace(pid string, targetVal string, targetAddrs []Found) error {
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	f, err := os.OpenFile(memPath, os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	defer f.Close()

	truncated := false
	for _, found := range targetAddrs {
		for i, targetAddr := range found.Addrs {
			targetBytes, wasTruncated, err := patchBytesForAddress(found, i, targetVal)
			if err != nil {
				return err
			}
			truncated = truncated || wasTruncated
			if err := memory.WriteMemory(f, targetAddr, targetBytes); err != nil {
				return err
			}
		}
	}
	if truncated {
		fmt.Println("String patch value was longer than the original match; truncated to the original byte length.")
	}
	fmt.Println("Successfully patched!")
	return nil
}

func PatchWithPtrace(pid string, targetVal string, targetAddrs []Found) error {
	if !isAttached {
		if err := Attach(pid); err != nil {
			return err
		}
	}
	truncated := false
	for _, found := range targetAddrs {
		for i, targetAddr := range found.Addrs {
			targetBytes, wasTruncated, err := patchBytesForAddress(found, i, targetVal)
			if err != nil {
				return err
			}
			truncated = truncated || wasTruncated
			tidInt, _ := strconv.Atoi(pid)
			if err := writeMemoryPtraceSafe(tidInt, targetAddr, targetBytes); err != nil {
				return err
			}
		}
	}
	Detach()
	if truncated {
		fmt.Println("String patch value was longer than the original match; truncated to the original byte length.")
	}
	fmt.Println("Successfully patched!")
	return nil
}

func patchBytesForAddress(found Found, index int, targetVal string) ([]byte, bool, error) {
	targetBytes, err := found.converter(targetVal)
	if err != nil {
		return nil, false, err
	}
	if !isStringFound(found) || index < 0 || index >= len(found.Values) {
		return targetBytes, false, nil
	}
	if !stringPatchTruncate {
		return targetBytes, false, nil
	}
	limit := len([]byte(found.Values[index]))
	if limit <= 0 {
		return targetBytes, false, nil
	}
	truncated := false
	if len(targetBytes) > limit {
		trimmed := trimStringToByteLimit(targetVal, limit)
		targetBytes, err = found.converter(trimmed)
		if err != nil {
			return nil, false, err
		}
		truncated = true
	}
	if len(targetBytes) < limit {
		padded := make([]byte, limit)
		copy(padded, targetBytes)
		targetBytes = padded
	}
	return targetBytes, truncated, nil
}

func writeMemoryPtraceSafe(pid int, targetAddr int, targetVal []byte) error {
	if len(targetVal) == 0 {
		return nil
	}
	wordSize := int(unsafe.Sizeof(uintptr(0)))
	wordMask := wordSize - 1
	start := targetAddr & ^wordMask
	end := targetAddr + len(targetVal)
	for wordAddr := start; wordAddr < end; wordAddr += wordSize {
		word := make([]byte, wordSize)
		if _, err := syscall.PtracePeekData(pid, uintptr(wordAddr), word); err != nil {
			return err
		}
		for i := 0; i < wordSize; i++ {
			abs := wordAddr + i
			if abs >= targetAddr && abs < end {
				word[i] = targetVal[abs-targetAddr]
			}
		}
		if _, err := syscall.PtracePokeData(pid, uintptr(wordAddr), word); err != nil {
			return err
		}
	}
	return nil
}

func trimStringToByteLimit(value string, maxBytes int) string {
	if maxBytes <= 0 || len(value) == 0 {
		return ""
	}
	used := 0
	end := 0
	for offset, r := range value {
		byteCount := len([]byte(string(r)))
		if used+byteCount > maxBytes {
			break
		}
		used += byteCount
		end = offset + byteCount
	}
	if len([]byte(value)) <= maxBytes {
		return value
	}
	if end == 0 {
		return ""
	}
	return value[:end]
}

func isStringFound(found Found) bool {
	return found.ConverterID == "string" || found.DataType == "UTF-8 string" || found.DataType == "string"
}

func Detach() error {
	if !isAttached {
		fmt.Println("Already detached.")
		return nil
	}

	for _, tid := range tids {
		if err := syscall.PtraceDetach(tid); err != nil {
			return fmt.Errorf("%d detach failed. %s\n", tid, err)
		} else {
			fmt.Printf("Detached TID: %d\n", tid)
		}
	}

	isAttached = false
	return nil
}

func wait(pid int) error {
	var s syscall.WaitStatus

	// sys.WALL = 0x40000000 on Linux(ARM64)
	// Using sys.WALL does not pass test on macOS.
	// https://github.com/golang/go/blob/50bd1c4d4eb4fac8ddeb5f063c099daccfb71b26/src/syscall/zerrors_linux_arm.go#L1203
	wpid, err := syscall.Wait4(pid, &s, 0x40000000, nil)
	if err != nil {
		return err
	}

	if wpid != pid {
		return fmt.Errorf("wait failed: wpid = %d", wpid)
	}
	if !s.Stopped() {
		return fmt.Errorf("wait failed: status is not stopped: ")
	}

	return nil
}

func Dump(pid string, beginAddress int, endAddress int) error {
	memory, err := readMemoryRange(pid, beginAddress, endAddress)
	if err != nil {
		return err
	}
	fmt.Printf("Address range: 0x%x - 0x%x\n", beginAddress, endAddress)
	fmt.Println("--------------------------------------------")
	fmt.Printf("%s", hex.Dump(memory))
	return nil
}

func DumpFile(pid string, beginAddress int, endAddress int, outputPath string) error {
	if strings.TrimSpace(outputPath) == "" {
		return errors.New("output path is empty")
	}
	memory, err := readMemoryRange(pid, beginAddress, endAddress)
	if err != nil {
		return err
	}
	dir := filepath.Dir(outputPath)
	if dir != "." && dir != "" {
		if err := os.MkdirAll(dir, 0777); err != nil {
			return err
		}
	}
	if err := ioutil.WriteFile(outputPath, memory, 0666); err != nil {
		return err
	}
	fmt.Printf("Dump file: %s (%d bytes)\n", outputPath, len(memory))
	return nil
}

func readMemoryRange(pid string, beginAddress int, endAddress int) ([]byte, error) {
	if endAddress <= beginAddress {
		return nil, errors.New("invalid address range")
	}
	memPath := fmt.Sprintf("/proc/%s/mem", pid)
	memFile, err := os.Open(memPath)
	if err != nil {
		return nil, err
	}
	defer memFile.Close()

	memSize := endAddress - beginAddress
	buf := make([]byte, memSize)
	return memory.ReadMemory(memFile, buf, beginAddress, endAddress), nil
}
