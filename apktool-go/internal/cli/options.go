package cli

import (
	"fmt"
	"strings"
)

type optionSpec struct {
	canonical  string
	takesValue bool
}

type parsedOptions struct {
	bools      map[string]bool
	values     map[string][]string
	positional []string
}

func parseOptions(args []string, specs map[string]optionSpec) (parsedOptions, error) {
	opts := parsedOptions{
		bools:  make(map[string]bool),
		values: make(map[string][]string),
	}
	for i := 0; i < len(args); i++ {
		arg := args[i]
		if arg == "--" {
			opts.positional = append(opts.positional, args[i+1:]...)
			break
		}
		if !strings.HasPrefix(arg, "-") || arg == "-" {
			opts.positional = append(opts.positional, arg)
			continue
		}

		name := arg
		value := ""
		hasInlineValue := false
		if strings.HasPrefix(arg, "--") {
			if eq := strings.IndexByte(arg, '='); eq >= 0 {
				name = arg[:eq]
				value = arg[eq+1:]
				hasInlineValue = true
			}
		}

		spec, ok := specs[name]
		if !ok {
			// Compatibility mode: accept unknown flags so existing apktool command lines
			// can keep flowing while support is filled in feature by feature.
			opts.bools[strings.TrimLeft(name, "-")] = true
			continue
		}
		key := spec.canonical
		if spec.takesValue {
			if !hasInlineValue {
				if i+1 >= len(args) {
					return opts, fmt.Errorf("missing value for %s", arg)
				}
				i++
				value = args[i]
			}
			opts.values[key] = append(opts.values[key], value)
		} else {
			opts.bools[key] = true
		}
	}
	return opts, nil
}

func (p parsedOptions) has(name string) bool {
	return p.bools[name]
}

func (p parsedOptions) value(name string) string {
	values := p.values[name]
	if len(values) == 0 {
		return ""
	}
	return values[len(values)-1]
}

func generalSpecs() map[string]optionSpec {
	return map[string]optionSpec{
		"-q":        {"quiet", false},
		"--quiet":   {"quiet", false},
		"-v":        {"verbose", false},
		"--verbose": {"verbose", false},
		"--debug":   {"verbose", false},
		"--log":     {"log", true},
	}
}

func mergeSpecs(maps ...map[string]optionSpec) map[string]optionSpec {
	out := make(map[string]optionSpec)
	for _, m := range maps {
		for k, v := range m {
			out[k] = v
		}
	}
	return out
}

func decodeSpecs() map[string]optionSpec {
	return mergeSpecs(generalSpecs(), map[string]optionSpec{
		"-a":                  {"all-src", false},
		"--all-src":           {"all-src", false},
		"-f":                  {"force", false},
		"--force":             {"force", false},
		"-s":                  {"no-src", false},
		"--no-src":            {"no-src", false},
		"-r":                  {"no-res", false},
		"--no-res":            {"no-res", false},
		"--no-debug-info":     {"no-debug-info", false},
		"--only-manifest":     {"only-manifest", false},
		"--keep-broken-res":   {"keep-broken-res", false},
		"--ignore-raw-values": {"ignore-raw-values", false},
		"--match-original":    {"match-original", false},
		"--no-assets":         {"no-assets", false},
		"-o":                  {"output", true},
		"--output":            {"output", true},
		"-p":                  {"frame-path", true},
		"--frame-path":        {"frame-path", true},
		"-t":                  {"frame-tag", true},
		"--frame-tag":         {"frame-tag", true},
		"-j":                  {"jobs", true},
		"--jobs":              {"jobs", true},
		"-l":                  {"lib", true},
		"--lib":               {"lib", true},
		"--api":               {"api", true},
		"--res-resolve-mode":  {"res-resolve-mode", true},
		"--use-aapt1":         {"use-aapt1", false},
		"--use-aapt2":         {"use-aapt2", false},
	})
}

func buildSpecs() map[string]optionSpec {
	return mergeSpecs(generalSpecs(), map[string]optionSpec{
		"-f":              {"force", false},
		"--force":         {"force", false},
		"--no-apk":        {"no-apk", false},
		"--no-crunch":     {"no-crunch", false},
		"--copy-original": {"copy-original", false},
		"--debuggable":    {"debuggable", false},
		"--net-sec-conf":  {"net-sec-conf", false},
		"-o":              {"output", true},
		"--output":        {"output", true},
		"-p":              {"frame-path", true},
		"--frame-path":    {"frame-path", true},
		"-j":              {"jobs", true},
		"--jobs":          {"jobs", true},
		"-l":              {"lib", true},
		"--lib":           {"lib", true},
		"--aapt":          {"aapt", true},
		"--api":           {"api", true},
		"--use-aapt1":     {"use-aapt1", false},
		"--use-aapt2":     {"use-aapt2", false},
	})
}

func frameSpecs() map[string]optionSpec {
	return mergeSpecs(generalSpecs(), map[string]optionSpec{
		"-p":           {"frame-path", true},
		"--frame-path": {"frame-path", true},
		"-t":           {"frame-tag", true},
		"--frame-tag":  {"frame-tag", true},
		"-a":           {"all", false},
		"--all":        {"all", false},
	})
}
