package apk

import (
	"archive/zip"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/esc0rtd3w/apktool-go/internal/axml"
	"github.com/esc0rtd3w/apktool-go/internal/logging"
	"github.com/esc0rtd3w/apktool-go/internal/ziputil"
)

type BuildOptions struct {
	Force        bool
	NoAPK        bool
	CopyOriginal bool
	Debuggable   bool
	Log          *logging.Logger
}

func Build(dir, outFile string, opts BuildOptions) error {
	log := opts.Log
	if log == nil {
		log = logging.Nop()
	}
	start := time.Now()
	log.Infof("Using apktool-go %s build", ToolVersion)
	log.Debugf("build input=%s output=%s force=%t no-apk=%t copy-original=%t debuggable=%t", dir, outFile, opts.Force, opts.NoAPK, opts.CopyOriginal, opts.Debuggable)
	if opts.NoAPK {
		log.Debugf("build skipped because --no-apk was set")
		return nil
	}
	meta, err := readMetadata(dir)
	if err != nil {
		return fmt.Errorf("missing %s; decode with this apktool first or keep the metadata file: %w", MetadataFile, err)
	}
	if outFile == "" {
		base := strings.TrimSuffix(meta.SourceAPK, ".apk")
		if base == "" {
			base = filepath.Base(dir)
		}
		outFile = filepath.Join(dir, "dist", base+".apk")
	}
	if _, err := os.Stat(outFile); err == nil && !opts.Force {
		return fmt.Errorf("output exists; use -f to overwrite: %s", outFile)
	}

	entries := make([]ziputil.Entry, 0, len(meta.Entries))
	seen := make(map[string]bool)
	for _, item := range meta.Entries {
		if isSignatureEntry(item.Name) {
			log.Debugf("build skip original signature entry %s", item.Name)
			continue
		}
		path := filepath.Join(dir, filepath.FromSlash(item.Name))
		var entry ziputil.Entry
		var entryReady bool
		if item.Name == "AndroidManifest.xml" {
			manifestEntry, handled, manifestErr := manifestEntryForBuild(dir, item, opts, log)
			if manifestErr != nil {
				return manifestErr
			}
			if handled {
				entry = manifestEntry
				entryReady = true
			}
		}
		if !entryReady {
			st, err := os.Stat(path)
			if err != nil || st.IsDir() {
				log.Debugf("build skip missing original entry %s", item.Name)
				continue
			}
			entry = zipEntryFromFile(item.Name, path, item)
		}
		entries = append(entries, entry)
		seen[item.Name] = true
		log.Debugf("build include %s method=%d", item.Name, entry.Method)
	}

	// Include new raw files that were added after decode. Text AndroidManifest.xml
	// is intentionally not compiled back to binary XML yet; the original binary
	// manifest is used until that compiler path is added.
	err = filepath.WalkDir(dir, func(path string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil || d.IsDir() {
			return walkErr
		}
		rel, err := filepath.Rel(dir, path)
		if err != nil {
			return err
		}
		rel = filepath.ToSlash(rel)
		if isSignatureEntry(rel) {
			log.Debugf("build skip decoded signature entry %s", rel)
			return nil
		}
		if skipBuildFile(rel) || seen[rel] {
			return nil
		}
		st, err := d.Info()
		if err != nil {
			return err
		}
		method := uint16(zip.Deflate)
		if rel == "resources.arsc" || strings.HasSuffix(rel, ".so") || strings.HasSuffix(rel, ".mp3") || strings.HasSuffix(rel, ".ogg") || strings.HasSuffix(rel, ".png") || strings.HasSuffix(rel, ".jpg") || strings.HasSuffix(rel, ".jpeg") || strings.HasSuffix(rel, ".webp") {
			method = zip.Store
		}
		entries = append(entries, zipEntryFromFile(rel, path, EntryMetadata{Name: rel, Method: method, ModifiedUnix: st.ModTime().Unix()}))
		return nil
	})
	if err != nil {
		return err
	}
	if err := ziputil.WriteAPK(outFile, entries); err != nil {
		return err
	}
	log.Infof("Build completed: entries=%d output=%s", len(entries), outFile)
	log.DebugDurationf(start, "build completed entries=%d output=%s", len(entries), outFile)
	return nil
}

func manifestEntryForBuild(dir string, item EntryMetadata, opts BuildOptions, log *logging.Logger) (ziputil.Entry, bool, error) {
	textManifest := filepath.Join(dir, "AndroidManifest.xml")
	rawManifest := filepath.Join(dir, OriginalDir, "AndroidManifest.xml")
	if _, err := os.Stat(rawManifest); err != nil {
		return ziputil.Entry{}, false, nil
	}
	data, err := os.ReadFile(rawManifest)
	if err != nil {
		return ziputil.Entry{}, true, err
	}
	if textData, textErr := os.ReadFile(textManifest); textErr == nil && len(textData) > 0 {
		patched, changed, patchErr := axml.PatchManifestFromDecodedText(data, textData, axml.ManifestTextPatchOptions{
			Logf: log.Debugf,
		})
		if patchErr != nil {
			return ziputil.Entry{}, true, fmt.Errorf("patch binary AndroidManifest.xml from decoded manifest: %w", patchErr)
		}
		if changed {
			data = patched
			log.Debugf("build applied targeted decoded AndroidManifest.xml changes to binary manifest")
		}
	}
	patchDebuggable := opts.Debuggable
	if !patchDebuggable {
		patchDebuggable = decodedManifestRequestsDebuggable(textManifest)
	}
	if patchDebuggable {
		patched, patchErr := axml.PatchDebuggableTrue(data)
		if patchErr != nil {
			return ziputil.Entry{}, true, fmt.Errorf("patch binary AndroidManifest.xml debuggable: %w", patchErr)
		}
		data = patched
		log.Debugf("build patched original binary AndroidManifest.xml with android:debuggable=true")
	} else {
		log.Debugf("build using preserved original binary AndroidManifest.xml with targeted decoded-text patches when present")
	}
	entry := zipEntryFromFile(item.Name, rawManifest, item)
	entry.Source = ""
	entry.Data = data
	return entry, true, nil
}

func decodedManifestRequestsDebuggable(path string) bool {
	b, err := os.ReadFile(path)
	if err != nil || len(b) == 0 {
		return false
	}
	xml := strings.ToLower(string(b))
	return strings.Contains(xml, "android:debuggable=\"true\"") || strings.Contains(xml, "android:debuggable='true'")
}

func zipEntryFromFile(name, path string, item EntryMetadata) ziputil.Entry {
	method := uint16(item.Method)
	if name == "resources.arsc" {
		method = ziputil.MethodStore
	} else if method != zip.Store {
		method = ziputil.MethodDeflate
	}
	return ziputil.Entry{
		Name:     name,
		Source:   path,
		Method:   method,
		Modified: time.Unix(item.ModifiedUnix, 0).UTC(),
		Align4:   name == "resources.arsc",
	}
}

func isSignatureEntry(name string) bool {
	upper := strings.ToUpper(filepath.ToSlash(name))
	if upper == "META-INF/MANIFEST.MF" {
		return true
	}
	if !strings.HasPrefix(upper, "META-INF/") {
		return false
	}
	return strings.HasSuffix(upper, ".SF") || strings.HasSuffix(upper, ".RSA") || strings.HasSuffix(upper, ".DSA") || strings.HasSuffix(upper, ".EC")
}

func skipBuildFile(rel string) bool {
	if rel == MetadataFile || rel == YamlFile || rel == "AndroidManifest.xml" {
		return true
	}
	if strings.HasPrefix(rel, OriginalDir+"/") || strings.HasPrefix(rel, "dist/") || strings.HasPrefix(rel, "build/") {
		return true
	}
	return false
}
