package apk

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/esc0rtd3w/apktool-go/internal/axml"
	"github.com/esc0rtd3w/apktool-go/internal/logging"
)

const ToolVersion = "3.0.2-go-0.5"

type DecodeOptions struct {
	Force         bool
	NoSrc         bool
	NoRes         bool
	OnlyManifest  bool
	NoAssets      bool
	MatchOriginal bool
	Log           *logging.Logger
}

func Decode(apkPath, outDir string, opts DecodeOptions) error {
	log := opts.Log
	if log == nil {
		log = logging.Nop()
	}
	start := time.Now()
	log.Infof("Using apktool-go %s decode", ToolVersion)
	log.Debugf("decode input=%s output=%s force=%t no-src=%t no-res=%t only-manifest=%t no-assets=%t", apkPath, outDir, opts.Force, opts.NoSrc, opts.NoRes, opts.OnlyManifest, opts.NoAssets)
	r, err := zip.OpenReader(apkPath)
	if err != nil {
		return err
	}
	defer r.Close()

	if st, err := os.Stat(outDir); err == nil && st.IsDir() {
		if !opts.Force {
			return fmt.Errorf("destination directory already exists: %s", outDir)
		}
		if err := os.RemoveAll(outDir); err != nil {
			return err
		}
	}
	if err := os.MkdirAll(outDir, 0755); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Join(outDir, OriginalDir), 0755); err != nil {
		return err
	}

	meta := metadataFromZip(apkPath, r.File)
	extracted := 0
	skipped := 0
	decodedManifest := false
	for _, f := range r.File {
		if f.FileInfo().IsDir() || shouldSkipDecode(f.Name, opts) {
			skipped++
			log.Debugf("decode skip %s", f.Name)
			continue
		}
		if err := extractFile(f, filepath.Join(outDir, f.Name)); err != nil {
			return err
		}
		extracted++
		log.Debugf("decode extract %s method=%d size=%d", f.Name, f.Method, f.UncompressedSize64)
		if f.Name == "AndroidManifest.xml" {
			raw, err := readZipFile(f)
			if err != nil {
				return err
			}
			if err := os.WriteFile(filepath.Join(outDir, OriginalDir, "AndroidManifest.xml"), raw, 0644); err != nil {
				return err
			}
			decoded, err := axml.DecodeManifest(raw)
			if err == nil {
				if err := os.WriteFile(filepath.Join(outDir, "AndroidManifest.xml"), []byte(decoded), 0644); err != nil {
					return err
				}
				decodedManifest = true
				log.Debugf("decode binary manifest -> text XML bytes=%d", len(decoded))
			} else {
				// Keep the raw binary manifest when decoding fails rather than
				// silently creating a misleading text file.
				log.Warnf("manifest decode failed, kept binary manifest: %v", err)
			}
		}
	}
	if err := writeMetadata(outDir, meta); err != nil {
		return err
	}
	log.Infof("Decode completed: entries=%d extracted=%d skipped=%d manifestText=%t", len(meta.Entries), extracted, skipped, decodedManifest)
	log.DebugDurationf(start, "decode completed entries=%d extracted=%d skipped=%d manifestText=%t", len(meta.Entries), extracted, skipped, decodedManifest)
	return nil
}

func shouldSkipDecode(name string, opts DecodeOptions) bool {
	if opts.OnlyManifest && name != "AndroidManifest.xml" {
		return true
	}
	if opts.NoSrc && (strings.HasPrefix(name, "classes") && strings.HasSuffix(name, ".dex")) {
		return true
	}
	if opts.NoRes && (name == "resources.arsc" || strings.HasPrefix(name, "res/")) {
		return true
	}
	if opts.NoAssets && strings.HasPrefix(name, "assets/") {
		return true
	}
	return false
}

func extractFile(f *zip.File, outPath string) error {
	clean, err := safeOutputPath(outPath)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(clean), 0755); err != nil {
		return err
	}
	in, err := f.Open()
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(clean, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, f.FileInfo().Mode())
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
}

func readZipFile(f *zip.File) ([]byte, error) {
	rc, err := f.Open()
	if err != nil {
		return nil, err
	}
	defer rc.Close()
	return io.ReadAll(rc)
}

func safeOutputPath(path string) (string, error) {
	clean := filepath.Clean(path)
	if strings.Contains(clean, ".."+string(filepath.Separator)) {
		return "", fmt.Errorf("unsafe path: %s", path)
	}
	return clean, nil
}
