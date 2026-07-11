package ziputil

import (
	"bytes"
	"compress/flate"
	"encoding/binary"
	"hash/crc32"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

const (
	MethodStore   = uint16(0)
	MethodDeflate = uint16(8)
)

type Entry struct {
	Name     string
	Source   string
	Data     []byte
	Method   uint16
	Modified time.Time
	Align4   bool
}

type centralEntry struct {
	Entry
	crc              uint32
	compressedSize   uint32
	uncompressedSize uint32
	localOffset      uint32
	extra            []byte
}

// WriteAPK writes a deterministic APK-compatible ZIP. It can force selected
// stored entries, especially resources.arsc, onto a 4-byte boundary, which
// Android 11+ requires for targetSdk 30+ packages.
func WriteAPK(path string, entries []Entry) error {
	sort.SliceStable(entries, func(i, j int) bool { return entries[i].Name < entries[j].Name })
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil && filepath.Dir(path) != "." {
		return err
	}
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer f.Close()
	w := &countingWriter{w: f}
	central := make([]centralEntry, 0, len(entries))
	for _, e := range entries {
		ce, err := writeLocal(w, e)
		if err != nil {
			return err
		}
		central = append(central, ce)
	}
	centralStart := w.n
	for _, ce := range central {
		if err := writeCentral(w, ce); err != nil {
			return err
		}
	}
	centralSize := w.n - centralStart
	return writeEnd(w, len(central), centralSize, centralStart)
}

func writeLocal(w *countingWriter, e Entry) (centralEntry, error) {
	data, err := entryData(e)
	if err != nil {
		return centralEntry{}, err
	}
	if e.Method != MethodStore {
		e.Method = MethodDeflate
	}
	if e.Modified.IsZero() {
		e.Modified = time.Unix(0, 0).UTC()
	}
	name := cleanZipName(e.Name)
	extra := alignmentExtra(w.n, name, nil, e.Align4)
	crc := crc32.ChecksumIEEE(data)
	payload := data
	if e.Method == MethodDeflate {
		var buf bytes.Buffer
		fw, err := flate.NewWriter(&buf, flate.DefaultCompression)
		if err != nil {
			return centralEntry{}, err
		}
		if _, err := fw.Write(data); err != nil {
			fw.Close()
			return centralEntry{}, err
		}
		if err := fw.Close(); err != nil {
			return centralEntry{}, err
		}
		payload = buf.Bytes()
	}
	localOffset := w.n
	dosTime, dosDate := msDosTime(e.Modified)
	writeU32(w, 0x04034b50)
	writeU16(w, 20)
	writeU16(w, 0)
	writeU16(w, e.Method)
	writeU16(w, dosTime)
	writeU16(w, dosDate)
	writeU32(w, crc)
	writeU32(w, uint32(len(payload)))
	writeU32(w, uint32(len(data)))
	writeU16(w, uint16(len(name)))
	writeU16(w, uint16(len(extra)))
	w.Write([]byte(name))
	w.Write(extra)
	w.Write(payload)
	return centralEntry{Entry: e, crc: crc, compressedSize: uint32(len(payload)), uncompressedSize: uint32(len(data)), localOffset: uint32(localOffset), extra: extra}, nil
}

func writeCentral(w *countingWriter, ce centralEntry) error {
	name := cleanZipName(ce.Name)
	dosTime, dosDate := msDosTime(ce.Modified)
	writeU32(w, 0x02014b50)
	writeU16(w, 20)
	writeU16(w, 20)
	writeU16(w, 0)
	writeU16(w, ce.Method)
	writeU16(w, dosTime)
	writeU16(w, dosDate)
	writeU32(w, ce.crc)
	writeU32(w, ce.compressedSize)
	writeU32(w, ce.uncompressedSize)
	writeU16(w, uint16(len(name)))
	writeU16(w, uint16(len(ce.extra)))
	writeU16(w, 0)
	writeU16(w, 0)
	writeU16(w, 0)
	writeU32(w, 0)
	writeU32(w, ce.localOffset)
	w.Write([]byte(name))
	w.Write(ce.extra)
	return nil
}

func writeEnd(w *countingWriter, count int, centralSize, centralStart int64) error {
	writeU32(w, 0x06054b50)
	writeU16(w, 0)
	writeU16(w, 0)
	writeU16(w, uint16(count))
	writeU16(w, uint16(count))
	writeU32(w, uint32(centralSize))
	writeU32(w, uint32(centralStart))
	writeU16(w, 0)
	return nil
}

func entryData(e Entry) ([]byte, error) {
	if e.Data != nil {
		return e.Data, nil
	}
	return os.ReadFile(e.Source)
}

func alignmentExtra(offset int64, name string, existing []byte, align bool) []byte {
	if !align {
		return existing
	}
	base := int(offset) + 30 + len(name) + len(existing)
	pad := (4 - (base % 4)) % 4
	if pad == 0 {
		return existing
	}
	total := pad
	if total < 4 {
		total += 4
	}
	for total%4 != pad {
		total++
	}
	extra := make([]byte, len(existing)+total)
	copy(extra, existing)
	binary.LittleEndian.PutUint16(extra[len(existing):], 0xcafe)
	binary.LittleEndian.PutUint16(extra[len(existing)+2:], uint16(total-4))
	return extra
}

func cleanZipName(name string) string {
	name = filepath.ToSlash(name)
	name = strings.TrimPrefix(name, "/")
	parts := strings.Split(name, "/")
	clean := make([]string, 0, len(parts))
	for _, p := range parts {
		if p == "" || p == "." || p == ".." {
			continue
		}
		clean = append(clean, p)
	}
	return strings.Join(clean, "/")
}

func msDosTime(t time.Time) (uint16, uint16) {
	t = t.Local()
	year, month, day := t.Date()
	hour, min, sec := t.Clock()
	if year < 1980 {
		year, month, day = 1980, 1, 1
		hour, min, sec = 0, 0, 0
	}
	dostime := uint16(hour<<11 | min<<5 | sec/2)
	dosdate := uint16((year-1980)<<9 | int(month)<<5 | day)
	return dostime, dosdate
}

type countingWriter struct {
	w io.Writer
	n int64
}

func (w *countingWriter) Write(p []byte) (int, error) {
	n, err := w.w.Write(p)
	w.n += int64(n)
	return n, err
}

func writeU16(w io.Writer, v uint16) { _ = binary.Write(w, binary.LittleEndian, v) }
func writeU32(w io.Writer, v uint32) { _ = binary.Write(w, binary.LittleEndian, v) }
