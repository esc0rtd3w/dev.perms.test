package cli

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/esc0rtd3w/apktool-go/internal/apk"
	"github.com/esc0rtd3w/apktool-go/internal/logging"
)

func Run(args []string, stdout, stderr io.Writer) error {
	if len(args) == 0 {
		printUsage(stdout)
		return nil
	}
	cmd := args[0]
	rest := args[1:]
	switch cmd {
	case "d", "decode":
		return runDecode(rest, stdout, stderr)
	case "b", "build":
		return runBuild(rest, stdout, stderr)
	case "if", "install-framework":
		return runInstallFramework(rest, stdout)
	case "cf", "clean-frameworks":
		return runCleanFrameworks(rest, stdout)
	case "lf", "list-frameworks":
		return runListFrameworks(rest, stdout)
	case "pr", "publicize-resources":
		return runPublicizeResources(rest)
	case "h", "help", "-help", "--help":
		printUsage(stdout)
		return nil
	case "v", "version", "-version", "--version":
		fmt.Fprintln(stdout, apk.ToolVersion)
		return nil
	default:
		printUsage(stderr)
		return fmt.Errorf("unrecognized command: %s", cmd)
	}
}

func loggerForOptions(opts parsedOptions, stdout, stderr io.Writer) (*logging.Logger, error) {
	return logging.New(stdout, stderr, opts.has("quiet"), opts.has("verbose"), opts.value("log"))
}

func runDecode(args []string, stdout, stderr io.Writer) error {
	opts, err := parseOptions(args, decodeSpecs())
	if err != nil {
		return err
	}
	if len(opts.positional) != 1 {
		return fmt.Errorf("decode expects exactly one input APK")
	}
	input := opts.positional[0]
	out := opts.value("output")
	if out == "" {
		out = defaultDecodeDir(input)
	}
	log, err := loggerForOptions(opts, stdout, stderr)
	if err != nil {
		return err
	}
	defer log.Close()
	err = apk.Decode(input, out, apk.DecodeOptions{
		Force:         opts.has("force"),
		NoSrc:         opts.has("no-src"),
		NoRes:         opts.has("no-res"),
		OnlyManifest:  opts.has("only-manifest"),
		NoAssets:      opts.has("no-assets"),
		MatchOriginal: opts.has("match-original"),
		Log:           log,
	})
	if err != nil {
		return err
	}
	if !opts.has("quiet") {
		fmt.Fprintf(stdout, "I: Decoded %s to %s\n", input, out)
	}
	return nil
}

func runBuild(args []string, stdout, stderr io.Writer) error {
	opts, err := parseOptions(args, buildSpecs())
	if err != nil {
		return err
	}
	dir := "."
	if len(opts.positional) > 1 {
		return fmt.Errorf("build expects zero or one decoded APK directory")
	}
	if len(opts.positional) == 1 {
		dir = opts.positional[0]
	}
	out := opts.value("output")
	log, err := loggerForOptions(opts, stdout, stderr)
	if err != nil {
		return err
	}
	defer log.Close()
	err = apk.Build(dir, out, apk.BuildOptions{
		Force:        opts.has("force"),
		NoAPK:        opts.has("no-apk"),
		CopyOriginal: opts.has("copy-original"),
		Debuggable:   opts.has("debuggable"),
		Log:          log,
	})
	if err != nil {
		return err
	}
	if !opts.has("quiet") {
		if out == "" {
			out = filepath.Join(dir, "dist")
		}
		fmt.Fprintf(stdout, "I: Built %s\n", out)
	}
	return nil
}

func runInstallFramework(args []string, stdout io.Writer) error {
	opts, err := parseOptions(args, frameSpecs())
	if err != nil {
		return err
	}
	if len(opts.positional) != 1 {
		return fmt.Errorf("install-framework expects exactly one framework APK")
	}
	if err := apk.InstallFramework(opts.positional[0], opts.value("frame-path"), opts.value("frame-tag")); err != nil {
		return err
	}
	if !opts.has("quiet") {
		fmt.Fprintln(stdout, "I: Framework installed")
	}
	return nil
}

func runCleanFrameworks(args []string, stdout io.Writer) error {
	opts, err := parseOptions(args, frameSpecs())
	if err != nil {
		return err
	}
	if len(opts.positional) != 0 {
		return fmt.Errorf("clean-frameworks does not accept positional arguments")
	}
	if err := apk.CleanFrameworks(opts.value("frame-path")); err != nil {
		return err
	}
	if !opts.has("quiet") {
		fmt.Fprintln(stdout, "I: Framework directory cleaned")
	}
	return nil
}

func runListFrameworks(args []string, stdout io.Writer) error {
	opts, err := parseOptions(args, frameSpecs())
	if err != nil {
		return err
	}
	if len(opts.positional) != 0 {
		return fmt.Errorf("list-frameworks does not accept positional arguments")
	}
	names, err := apk.ListFrameworks(opts.value("frame-path"))
	if err != nil {
		return err
	}
	for _, name := range names {
		fmt.Fprintln(stdout, name)
	}
	return nil
}

func runPublicizeResources(args []string) error {
	opts, err := parseOptions(args, generalSpecs())
	if err != nil {
		return err
	}
	if len(opts.positional) != 1 {
		return fmt.Errorf("publicize-resources expects exactly one resources.arsc file")
	}
	return apk.PublicizeResources(opts.positional[0])
}

func defaultDecodeDir(input string) string {
	base := filepath.Base(input)
	if strings.HasSuffix(strings.ToLower(base), ".apk") {
		return strings.TrimSuffix(base, filepath.Ext(base))
	}
	return base + ".out"
}

func printUsage(w io.Writer) {
	fmt.Fprintln(w, "Apktool "+apk.ToolVersion+" - native compatibility build")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "apktool d|decode [options] <apk-file>")
	fmt.Fprintln(w, "  -f, --force                 Force delete destination directory")
	fmt.Fprintln(w, "  -o, --output <dir>          Output decoded files to <dir>")
	fmt.Fprintln(w, "  -s, --no-src                Do not copy classes*.dex")
	fmt.Fprintln(w, "  -r, --no-res                Do not copy resources.arsc/res")
	fmt.Fprintln(w, "      --only-manifest         Only decode AndroidManifest.xml")
	fmt.Fprintln(w, "      --no-assets             Do not copy assets")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "apktool b|build [options] <apk-dir>")
	fmt.Fprintln(w, "  -f, --force                 Overwrite output")
	fmt.Fprintln(w, "  -o, --output <file>         Output built APK")
	fmt.Fprintln(w, "      --no-apk                Do validation only")
	fmt.Fprintln(w, "      --debuggable            Patch rebuilt binary manifest with android:debuggable=true")
	fmt.Fprintln(w, "      --log <file>            Save detailed apktool-go diagnostics")
	fmt.Fprintln(w, "  -v, --verbose               Include D: debug diagnostics")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "apktool if|install-framework [options] <apk-file>")
	fmt.Fprintln(w, "apktool cf|clean-frameworks [options]")
	fmt.Fprintln(w, "apktool lf|list-frameworks [options]")
	fmt.Fprintln(w, "apktool v|version")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Compatibility note: unsupported apktool flags are accepted where safe, but")
	fmt.Fprintln(w, "the current native implementation focuses first on native decode/repack needs.")
}

func init() {
	_ = os.ErrNotExist
}
