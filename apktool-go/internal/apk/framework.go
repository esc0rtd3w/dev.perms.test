package apk

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
)

func DefaultFrameworkDir() string {
	if home, err := os.UserHomeDir(); err == nil && home != "" {
		return filepath.Join(home, ".local", "share", "apktool", "framework")
	}
	return filepath.Join(".", "framework")
}

func InstallFramework(apkPath, frameDir, tag string) error {
	if frameDir == "" {
		frameDir = DefaultFrameworkDir()
	}
	if err := os.MkdirAll(frameDir, 0755); err != nil {
		return err
	}
	name := "1.apk"
	if tag != "" {
		name = "1-" + tag + ".apk"
	}
	in, err := os.Open(apkPath)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(filepath.Join(frameDir, name))
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
}

func CleanFrameworks(frameDir string) error {
	if frameDir == "" {
		frameDir = DefaultFrameworkDir()
	}
	if err := os.RemoveAll(frameDir); err != nil {
		return err
	}
	return os.MkdirAll(frameDir, 0755)
}

func ListFrameworks(frameDir string) ([]string, error) {
	if frameDir == "" {
		frameDir = DefaultFrameworkDir()
	}
	entries, err := os.ReadDir(frameDir)
	if os.IsNotExist(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		if !e.IsDir() {
			names = append(names, e.Name())
		}
	}
	return names, nil
}

func PublicizeResources(path string) error {
	// Placeholder for CLI compatibility. current decode/repack workflows need decode/repack
	// foundation first; resource publicizing will be filled in when the app uses it.
	if _, err := os.Stat(path); err != nil {
		return err
	}
	return fmt.Errorf("publicize-resources is accepted for compatibility but is not implemented yet")
}
