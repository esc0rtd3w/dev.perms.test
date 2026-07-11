package apk

import (
	"archive/zip"
	"encoding/json"
	"os"
	"path/filepath"
	"time"
)

const MetadataFile = "apktool-go.json"
const YamlFile = "apktool.yml"
const OriginalDir = "original"

type Metadata struct {
	Tool      string          `json:"tool"`
	Version   string          `json:"version"`
	SourceAPK string          `json:"source_apk"`
	DecodedAt string          `json:"decoded_at"`
	Entries   []EntryMetadata `json:"entries"`
}

type EntryMetadata struct {
	Name         string `json:"name"`
	Method       uint16 `json:"method"`
	ModifiedUnix int64  `json:"modified_unix"`
}

func metadataFromZip(path string, files []*zip.File) Metadata {
	entries := make([]EntryMetadata, 0, len(files))
	for _, f := range files {
		if f.FileInfo().IsDir() {
			continue
		}
		entries = append(entries, EntryMetadata{
			Name:         f.Name,
			Method:       f.Method,
			ModifiedUnix: f.Modified.UTC().Unix(),
		})
	}
	return Metadata{
		Tool:      "apktool-go",
		Version:   ToolVersion,
		SourceAPK: filepath.Base(path),
		DecodedAt: time.Now().UTC().Format(time.RFC3339),
		Entries:   entries,
	}
}

func writeMetadata(outDir string, meta Metadata) error {
	b, err := json.MarshalIndent(meta, "", "  ")
	if err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(outDir, MetadataFile), append(b, '\n'), 0644); err != nil {
		return err
	}
	yml := "!!brut.androlib.meta.MetaInfo\n" +
		"apkFileName: " + meta.SourceAPK + "\n" +
		"isFrameworkApk: false\n" +
		"usesFramework:\n  ids: []\n" +
		"sdkInfo: {}\n" +
		"packageInfo: {}\n" +
		"versionInfo: {}\n" +
		"compressionType: false\n"
	return os.WriteFile(filepath.Join(outDir, YamlFile), []byte(yml), 0644)
}

func readMetadata(dir string) (Metadata, error) {
	b, err := os.ReadFile(filepath.Join(dir, MetadataFile))
	if err != nil {
		return Metadata{}, err
	}
	var meta Metadata
	if err := json.Unmarshal(b, &meta); err != nil {
		return Metadata{}, err
	}
	return meta, nil
}
