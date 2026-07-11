package dex

import (
	"archive/zip"
	"bytes"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

type options struct {
	outDir           string
	input            string
	dexEntry         string
	list             bool
	progress         bool
	binaryInnerNames bool
	zipOut           string
	logPath          string
	includeNestedDex bool
	appendLog        bool
	debugSummary     bool
}

// Run executes the standalone jadx-go command.
func Run(args []string, stdout io.Writer, stderr io.Writer, version string) error {
	opts, help, showVersion, err := parseArgs(args)
	if err != nil {
		return err
	}
	if showVersion {
		fmt.Fprintln(stdout, version)
		return nil
	}
	if help {
		printHelp(stdout)
		return nil
	}
	if opts.input == "" {
		printHelp(stderr)
		return errors.New("missing input APK/DEX")
	}

	var logFile *os.File
	if opts.logPath != "" {
		if err := os.MkdirAll(filepath.Dir(opts.logPath), 0o755); err != nil {
			return err
		}
		flags := os.O_CREATE | os.O_WRONLY | os.O_TRUNC
		if opts.appendLog {
			flags = os.O_CREATE | os.O_WRONLY | os.O_APPEND
		}
		logFile, err = os.OpenFile(opts.logPath, flags, 0o644)
		if err != nil {
			return err
		}
		defer logFile.Close()
		stdout = io.MultiWriter(stdout, logFile)
		stderr = io.MultiWriter(stderr, logFile)
	}

	started := time.Now()
	logf := func(format string, a ...any) {
		fmt.Fprintf(stdout, format, a...)
		if !strings.HasSuffix(format, "\n") {
			fmt.Fprintln(stdout)
		}
	}
	progressf := func(format string, a ...any) {
		if opts.progress {
			logf("PROGRESS "+format, a...)
		}
	}

	if opts.debugSummary {
		logf("jadx-go debug: version=%s input=%s out_dir=%s dex_entry=%s include_nested=%t inner_names=%s zip_out=%s append_log=%t\n",
			version, opts.input, opts.outDir, opts.dexEntry, opts.includeNestedDex, innerNameMode(opts), opts.zipOut, opts.appendLog)
	}
	progressf("phase=load input=%s dex_entry=%s\n", opts.input, opts.dexEntry)
	inputs, err := loadInputs(opts.input, opts.dexEntry, opts.includeNestedDex)
	if err != nil {
		return err
	}
	if len(inputs) == 0 {
		return fmt.Errorf("no DEX files found in %s", opts.input)
	}
	if opts.debugSummary {
		logf("jadx-go debug: dex_inputs=%d\n", len(inputs))
		for i, in := range inputs {
			logf("jadx-go debug: dex_input[%d]=%s bytes=%d\n", i+1, in.Name, len(in.Data))
		}
	}

	parsed := make([]*File, 0, len(inputs))
	for i, in := range inputs {
		progressf("phase=parse dex=%s index=%d total=%d bytes=%d\n", in.Name, i+1, len(inputs), len(in.Data))
		f, err := Parse(in.Name, in.Data)
		if err != nil {
			return fmt.Errorf("%s: %w", in.Name, err)
		}
		if opts.debugSummary {
			logf("jadx-go debug: parsed %s %s\n", in.Name, fileDebugSummary(f))
		}
		parsed = append(parsed, f)
	}

	if opts.list {
		for _, f := range parsed {
			fmt.Fprintf(stdout, "%s\n", f.Name)
			classes := f.Classes()
			for _, cls := range classes {
				fmt.Fprintf(stdout, "  %s\n", cls.Descriptor)
			}
		}
		return nil
	}

	if opts.outDir == "" {
		opts.outDir = defaultOutDir(opts.input)
	}
	if err := os.MkdirAll(opts.outDir, 0o755); err != nil {
		return err
	}
	progressf("phase=write out=%s dex_files=%d\n", opts.outDir, len(parsed))
	lastProgress := time.Time{}
	result, err := WriteJavaSkeletons(opts.outDir, parsed, JavaWriteOptions{
		BinaryInnerNames: opts.binaryInnerNames,
		Progress: func(p JavaProgress) {
			if !opts.progress {
				return
			}
			now := time.Now()
			// Avoid excessive service/status churn while still proving liveness on big APKs.
			if p.ClassIndex == 1 || p.ClassIndex == p.ClassTotal || p.ClassIndex%25 == 0 || now.Sub(lastProgress) > 1500*time.Millisecond {
				lastProgress = now
				progressf("phase=class dex=%s class=%d/%d written=%d descriptor=%s file=%s\n",
					p.DexName, p.ClassIndex, p.ClassTotal, p.WrittenFiles, p.ClassDescriptor, p.JavaPath)
			}
		},
	})
	if err != nil {
		return err
	}
	logf("jadx-go wrote %d Java file(s) from %d dex file(s) to %s\n", result.ClassCount, len(parsed), opts.outDir)
	if opts.debugSummary {
		logf("jadx-go debug summary: %s elapsed_ms=%d\n", result.DebugLine(), time.Since(started).Milliseconds())
	}
	progressf("phase=summary %s elapsed_ms=%d\n", result.DebugLine(), time.Since(started).Milliseconds())
	if result.SkippedCount > 0 {
		logf("jadx-go skipped %d duplicate/problem class file(s); see index.txt\n", result.SkippedCount)
	}
	if opts.zipOut != "" {
		progressf("phase=zip out=%s\n", opts.zipOut)
		if err := zipDirectory(opts.outDir, opts.zipOut); err != nil {
			return fmt.Errorf("zip output failed: %w", err)
		}
		logf("jadx-go zipped output to %s\n", opts.zipOut)
	}
	progressf("phase=done classes=%d dex_files=%d elapsed_ms=%d\n", result.ClassCount, len(parsed), time.Since(started).Milliseconds())
	return nil
}

func parseArgs(args []string) (options, bool, bool, error) {
	opts := options{binaryInnerNames: true}
	help := false
	version := false
	for i := 0; i < len(args); i++ {
		arg := args[i]
		switch arg {
		case "-h", "--help", "help":
			help = true
		case "--version", "version", "-v":
			version = true
		case "-d", "--output-dir", "--output":
			i++
			if i >= len(args) {
				return opts, false, false, fmt.Errorf("%s requires a path", arg)
			}
			opts.outDir = args[i]
		case "--list", "list":
			opts.list = true
		case "--progress":
			opts.progress = true
		case "--include-nested-dex":
			opts.includeNestedDex = true
		case "--append-log":
			opts.appendLog = true
		case "--debug-summary", "--debug-report":
			opts.debugSummary = true
		case "--binary-inner-names", "--binary-names":
			opts.binaryInnerNames = true
		case "--java-inner-names", "--java-names":
			// Accepted for CLI compatibility. Until jadx-go renders nested source
			// declarations, binary names are the only self-consistent Java refs.
			opts.binaryInnerNames = true
		case "--zip-out":
			i++
			if i >= len(args) {
				return opts, false, false, fmt.Errorf("%s requires a path", arg)
			}
			opts.zipOut = args[i]
		case "--dex-entry", "--entry":
			i++
			if i >= len(args) {
				return opts, false, false, fmt.Errorf("%s requires a DEX entry name", arg)
			}
			opts.dexEntry = normalizeDexEntryFilter(args[i])
		case "--log":
			i++
			if i >= len(args) {
				return opts, false, false, fmt.Errorf("%s requires a path", arg)
			}
			opts.logPath = args[i]
		case "--":
			if i+1 < len(args) {
				opts.input = args[i+1]
			}
			i = len(args)
		default:
			if strings.HasPrefix(arg, "-d=") {
				opts.outDir = strings.TrimPrefix(arg, "-d=")
			} else if strings.HasPrefix(arg, "--output-dir=") {
				opts.outDir = strings.TrimPrefix(arg, "--output-dir=")
			} else if strings.HasPrefix(arg, "--output=") {
				opts.outDir = strings.TrimPrefix(arg, "--output=")
			} else if strings.HasPrefix(arg, "--zip-out=") {
				opts.zipOut = strings.TrimPrefix(arg, "--zip-out=")
			} else if strings.HasPrefix(arg, "--log=") {
				opts.logPath = strings.TrimPrefix(arg, "--log=")
			} else if strings.HasPrefix(arg, "--dex-entry=") {
				opts.dexEntry = normalizeDexEntryFilter(strings.TrimPrefix(arg, "--dex-entry="))
			} else if strings.HasPrefix(arg, "--entry=") {
				opts.dexEntry = normalizeDexEntryFilter(strings.TrimPrefix(arg, "--entry="))
			} else if strings.HasPrefix(arg, "-") {
				return opts, false, false, fmt.Errorf("unsupported option: %s", arg)
			} else if opts.input == "" {
				opts.input = arg
			} else {
				return opts, false, false, fmt.Errorf("unexpected extra argument: %s", arg)
			}
		}
	}
	return opts, help, version, nil
}

func innerNameMode(opts options) string {
	if opts.binaryInnerNames {
		return "binary"
	}
	return "java"
}

func fileDebugSummary(f *File) string {
	if f == nil {
		return "classes=0 fields=0 methods=0 code_methods=0 instructions=0"
	}
	classes := f.Classes()
	fields := 0
	methods := 0
	codeMethods := 0
	instructions := 0
	for _, cls := range classes {
		fields += len(cls.StaticFields) + len(cls.Fields)
		for _, m := range cls.Direct {
			methods++
			if m.Code.HasCode {
				codeMethods++
				instructions += len(m.Code.Instructions)
			}
		}
		for _, m := range cls.Virtual {
			methods++
			if m.Code.HasCode {
				codeMethods++
				instructions += len(m.Code.Instructions)
			}
		}
	}
	return fmt.Sprintf("classes=%d fields=%d methods=%d code_methods=%d instructions=%d", len(classes), fields, methods, codeMethods, instructions)
}

func printHelp(w io.Writer) {
	fmt.Fprintln(w, "jadx-go - standalone DEX/APK to best-effort Java output tool")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Usage:")
	fmt.Fprintln(w, "  jadx -d <out-dir> <input.apk|input.dex>")
	fmt.Fprintln(w, "  jadx --list <input.apk|input.dex>")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Options:")
	fmt.Fprintln(w, "  --progress                 print progress lines for long-running callers")
	fmt.Fprintln(w, "  --log <file>               write the same output to a debug log file")
	fmt.Fprintln(w, "  --append-log               append to --log instead of replacing it")
	fmt.Fprintln(w, "  --debug-summary           print parse/write counters for diagnostics")
	fmt.Fprintln(w, "  --zip-out <file.zip>       zip the completed output directory")
	fmt.Fprintln(w, "  --dex-entry <classes.dex>  process only one DEX entry from APK/ZIP input")
	fmt.Fprintln(w, "  --include-nested-dex       also scan nested classes*.dex assets inside APK/ZIP input")
	fmt.Fprintln(w, "  --java-inner-names         accepted alias; output stays binary-safe for now")
	fmt.Fprintln(w, "  --binary-inner-names       render referenced inner/synthetic types with '$' (default/safest)")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Current output recovers simple method bodies and includes decoded Dalvik comments for complex methods.")
}

type inputDex struct {
	Name string
	Data []byte
}

func loadInputs(path string, dexEntry string, includeNestedDex bool) ([]inputDex, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	if isDex(data) {
		name := filepath.Base(path)
		if dexEntry != "" && !dexEntryMatches(name, dexEntry) {
			return nil, fmt.Errorf("selected DEX entry %s not found in raw DEX input %s", dexEntry, name)
		}
		return []inputDex{{Name: name, Data: data}}, nil
	}
	zr, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, fmt.Errorf("input is neither raw DEX nor ZIP/APK: %w", err)
	}
	out := make([]inputDex, 0)
	for _, entry := range zr.File {
		name := strings.TrimPrefix(strings.ReplaceAll(entry.Name, "\\", "/"), "/")
		lower := strings.ToLower(name)
		if !strings.HasSuffix(lower, ".dex") {
			continue
		}
		explicitMatch := dexEntry != "" && dexEntryMatches(name, dexEntry)
		if !explicitMatch && !isDefaultApkDexEntry(name, includeNestedDex) {
			continue
		}
		if dexEntry != "" && !explicitMatch {
			continue
		}
		rc, err := entry.Open()
		if err != nil {
			return nil, err
		}
		b, readErr := io.ReadAll(rc)
		closeErr := rc.Close()
		if readErr != nil {
			return nil, readErr
		}
		if closeErr != nil {
			return nil, closeErr
		}
		if isDex(b) {
			out = append(out, inputDex{Name: name, Data: b})
		}
	}
	sort.Slice(out, func(i, j int) bool { return naturalDexNameLess(out[i].Name, out[j].Name) })
	if dexEntry != "" && len(out) == 0 {
		return nil, fmt.Errorf("selected DEX entry %s was not found", dexEntry)
	}
	return out, nil
}

func isDefaultApkDexEntry(name string, includeNestedDex bool) bool {
	clean := strings.TrimPrefix(strings.ReplaceAll(strings.TrimSpace(name), "\\", "/"), "/")
	if clean == "" {
		return false
	}
	lower := strings.ToLower(clean)
	if !strings.HasSuffix(lower, ".dex") {
		return false
	}
	base := pathBaseSlash(lower)
	if !strings.HasPrefix(base, "classes") {
		return false
	}
	if !includeNestedDex && strings.Contains(lower, "/") {
		return false
	}
	return true
}

func pathBaseSlash(path string) string {
	if idx := strings.LastIndex(path, "/"); idx >= 0 && idx+1 < len(path) {
		return path[idx+1:]
	}
	return path
}

func normalizeDexEntryFilter(entry string) string {
	clean := strings.TrimSpace(strings.ReplaceAll(entry, "\\", "/"))
	clean = strings.TrimPrefix(clean, "/")
	if clean == "" {
		return ""
	}
	if !strings.HasSuffix(strings.ToLower(clean), ".dex") {
		clean += ".dex"
	}
	return clean
}

func dexEntryMatches(name string, filter string) bool {
	name = strings.TrimPrefix(strings.ReplaceAll(strings.TrimSpace(name), "\\", "/"), "/")
	filter = normalizeDexEntryFilter(filter)
	if filter == "" {
		return true
	}
	return name == filter
}

func isDex(data []byte) bool {
	return len(data) >= 8 && string(data[:4]) == "dex\n"
}

func defaultOutDir(input string) string {
	base := filepath.Base(input)
	ext := filepath.Ext(base)
	if ext != "" {
		base = strings.TrimSuffix(base, ext)
	}
	base = sanitizePathSegment(base)
	if base == "" {
		base = "input"
	}
	return base + "-jadx-go-out"
}

func naturalDexNameLess(a, b string) bool {
	ra := dexRank(a)
	rb := dexRank(b)
	if ra != rb {
		return ra < rb
	}
	return a < b
}

func dexRank(name string) int {
	base := strings.ToLower(filepath.Base(name))
	if base == "classes.dex" {
		return 1
	}
	if strings.HasPrefix(base, "classes") && strings.HasSuffix(base, ".dex") {
		mid := strings.TrimSuffix(strings.TrimPrefix(base, "classes"), ".dex")
		var n int
		if _, err := fmt.Sscanf(mid, "%d", &n); err == nil && n > 0 {
			return n
		}
	}
	return 100000
}

func zipDirectory(srcDir, zipPath string) error {
	if srcDir == "" || zipPath == "" {
		return nil
	}
	absZip, _ := filepath.Abs(zipPath)
	if err := os.MkdirAll(filepath.Dir(zipPath), 0o755); err != nil {
		return err
	}
	tmp := zipPath + ".tmp"
	_ = os.Remove(tmp)
	out, err := os.Create(tmp)
	if err != nil {
		return err
	}
	zw := zip.NewWriter(out)
	walkErr := filepath.WalkDir(srcDir, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d == nil || d.IsDir() {
			return nil
		}
		absPath, _ := filepath.Abs(path)
		if absPath == absZip || absPath == absZip+".tmp" {
			return nil
		}
		rel, err := filepath.Rel(srcDir, path)
		if err != nil {
			return err
		}
		rel = filepath.ToSlash(rel)
		info, err := d.Info()
		if err != nil {
			return err
		}
		hdr, err := zip.FileInfoHeader(info)
		if err != nil {
			return err
		}
		hdr.Name = rel
		hdr.Method = zip.Deflate
		w, err := zw.CreateHeader(hdr)
		if err != nil {
			return err
		}
		in, err := os.Open(path)
		if err != nil {
			return err
		}
		_, copyErr := io.Copy(w, in)
		closeErr := in.Close()
		if copyErr != nil {
			return copyErr
		}
		return closeErr
	})
	closeZipErr := zw.Close()
	closeFileErr := out.Close()
	if walkErr != nil {
		_ = os.Remove(tmp)
		return walkErr
	}
	if closeZipErr != nil {
		_ = os.Remove(tmp)
		return closeZipErr
	}
	if closeFileErr != nil {
		_ = os.Remove(tmp)
		return closeFileErr
	}
	if err := os.Rename(tmp, zipPath); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	return nil
}
