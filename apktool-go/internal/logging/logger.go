package logging

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// Logger keeps apktool-go output close to apktool's familiar I:/W:/E: style
// while adding optional verbose traces for native troubleshooting. It has no
// Android-specific dependencies, so the same code runs on desktop and device.
type Logger struct {
	out     io.Writer
	err     io.Writer
	file    *os.File
	quiet   bool
	verbose bool
	start   time.Time
	mu      sync.Mutex
}

func New(out, err io.Writer, quiet, verbose bool, logPath string) (*Logger, error) {
	if out == nil {
		out = io.Discard
	}
	if err == nil {
		err = io.Discard
	}
	l := &Logger{out: out, err: err, quiet: quiet, verbose: verbose, start: time.Now()}
	if strings.TrimSpace(logPath) != "" {
		if dir := filepath.Dir(logPath); dir != "." && dir != "" {
			if err := os.MkdirAll(dir, 0755); err != nil {
				return nil, fmt.Errorf("create log directory %s: %w", dir, err)
			}
		}
		f, openErr := os.OpenFile(logPath, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0644)
		if openErr != nil {
			return nil, fmt.Errorf("open log file %s: %w", logPath, openErr)
		}
		l.file = f
		l.Debugf("Log file: %s", logPath)
	}
	return l, nil
}

func Nop() *Logger {
	return &Logger{out: io.Discard, err: io.Discard, quiet: true, start: time.Now()}
}

func (l *Logger) Close() error {
	if l == nil || l.file == nil {
		return nil
	}
	l.mu.Lock()
	f := l.file
	l.file = nil
	l.mu.Unlock()
	return f.Close()
}

func (l *Logger) Verbose() bool {
	return l != nil && l.verbose
}

func (l *Logger) Infof(format string, args ...any) {
	if l == nil || l.quiet {
		return
	}
	l.write(l.out, "I", format, args...)
}

func (l *Logger) Warnf(format string, args ...any) {
	if l == nil {
		return
	}
	l.write(l.err, "W", format, args...)
}

func (l *Logger) Errorf(format string, args ...any) {
	if l == nil {
		return
	}
	l.write(l.err, "E", format, args...)
}

func (l *Logger) Debugf(format string, args ...any) {
	if l == nil || !l.verbose {
		return
	}
	l.write(l.out, "D", format, args...)
}

func (l *Logger) DebugDurationf(start time.Time, format string, args ...any) {
	if l == nil || !l.verbose {
		return
	}
	msg := fmt.Sprintf(format, args...)
	l.write(l.out, "D", "%s in %s", msg, time.Since(start).Round(time.Millisecond))
}

func (l *Logger) write(w io.Writer, level, format string, args ...any) {
	line := fmt.Sprintf(format, args...)
	if !strings.HasSuffix(line, "\n") {
		line += "\n"
	}
	text := level + ": " + line
	l.mu.Lock()
	defer l.mu.Unlock()
	_, _ = io.WriteString(w, text)
	if l.file != nil {
		elapsed := time.Since(l.start).Round(time.Millisecond)
		_, _ = fmt.Fprintf(l.file, "%s [%s] %s", elapsed, level, line)
	}
}
