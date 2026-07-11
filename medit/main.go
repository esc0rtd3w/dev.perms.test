package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"

	"github.com/sterrasec/apk-medit/cmd"
)

var appPID string
var addrCache []cmd.Found
var withoutPtrace bool
var specificPID string
var oneShotCommand string
var stateFile string
var batchMode bool
var attachRequested bool
var maxResults int
var skipResults int
var stringCaseSensitive bool
var stringPatchTruncate bool

type batchState struct {
	PID             string      `json:"pid"`
	AttachRequested bool        `json:"attach_requested"`
	Founds          []cmd.Found `json:"founds"`
}

func executor(in string) {
	if err := executeCommand(in); err != nil {
		fmt.Println(err)
		if batchMode {
			os.Exit(1)
		}
	}
}

func executeCommand(in string) error {
	in = strings.TrimSpace(in)
	if in == "ps" {
		if specificPID == "" {
			pid, err := cmd.Plist()
			if err != nil {
				return err
			}
			if pid != "" {
				appPID = pid
			}
		} else {
			fmt.Printf("The Target PID is already set to %s.\n", specificPID)
		}

	} else if strings.HasPrefix(in, "attach") {
		slice := strings.Fields(in)
		var pid string
		if len(slice) > 1 {
			pid = slice[1]
		} else if appPID != "" {
			pid = appPID
		} else {
			return errors.New("PID cannot be specified")
		}
		appPID = pid
		if batchMode {
			attachRequested = true
			fmt.Printf("Attach mode enabled for PID %s.\n", pid)
			return nil
		}
		return cmd.Attach(pid)

	} else if strings.HasPrefix(in, "find-gt") || strings.HasPrefix(in, "find-lt") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 3 {
			return errors.New("find-gt/find-lt require datatype and target value")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		op := "gt"
		if strings.HasPrefix(in, "find-lt") {
			op = "lt"
		}
		foundAddr, err := cmd.FindCompare(appPID, strings.Join(inputSlice[2:], " "), inputSlice[1], op)
		addrCache = cmd.RestoreFoundConverters(foundAddr)
		if err != nil {
			return err
		}

	} else if strings.HasPrefix(in, "find") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 2 {
			return errors.New("Target value cannot be specified")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		dataType := "all"
		targetVal := inputSlice[1]
		if len(inputSlice) >= 3 {
			targetVal = strings.Join(inputSlice[2:], " ")
			dataType = inputSlice[1]
		}
		foundAddr, err := cmd.Find(appPID, targetVal, dataType)
		addrCache = cmd.RestoreFoundConverters(foundAddr)
		if err != nil {
			return err
		}

	} else if strings.HasPrefix(in, "filter-gt") || strings.HasPrefix(in, "filter-lt") || strings.HasPrefix(in, "filter-unchanged") || strings.HasPrefix(in, "filter-changed") {
		if len(addrCache) == 0 {
			return errors.New("No previous results")
		}
		slice := strings.Fields(in)
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		op := "gt"
		if strings.HasPrefix(in, "filter-lt") {
			op = "lt"
		} else if strings.HasPrefix(in, "filter-unchanged") {
			op = "eq"
		} else if strings.HasPrefix(in, "filter-changed") {
			op = "ne"
		}
		targetVal := ""
		if len(slice) > 1 {
			targetVal = strings.Join(slice[1:], " ")
		}
		foundAddr, err := cmd.FilterCompare(appPID, targetVal, cmd.RestoreFoundConverters(addrCache), op)
		addrCache = cmd.RestoreFoundConverters(foundAddr)
		if err != nil {
			return err
		}

	} else if strings.HasPrefix(in, "filter") {
		if len(addrCache) == 0 {
			return errors.New("No previous results")
		}
		slice := strings.Fields(in)
		if len(slice) == 1 {
			return errors.New("Target value cannot be specified")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}

		foundAddr, err := cmd.Filter(appPID, strings.Join(slice[1:], " "), cmd.RestoreFoundConverters(addrCache))
		addrCache = cmd.RestoreFoundConverters(foundAddr)
		if err != nil {
			return err
		}

	} else if strings.HasPrefix(in, "search-magic") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 2 {
			return errors.New("search-magic requires a file extension")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		foundAddr, err := cmd.SearchMagic(appPID, inputSlice[1])
		addrCache = cmd.RestoreFoundConverters(foundAddr)
		if err != nil {
			return err
		}

	} else if strings.HasPrefix(in, "search-bytes-mask") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 3 {
			return errors.New("search-bytes-mask requires hex bytes and mask bytes")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		foundAddr, err := cmd.FindBytesMasked(appPID, inputSlice[1], inputSlice[2])
		addrCache = cmd.RestoreFoundConverters(foundAddr)
		if err != nil {
			return err
		}

	} else if strings.HasPrefix(in, "search-bytes") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 2 {
			return errors.New("search-bytes requires hex bytes")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		foundAddr, err := cmd.FindBytes(appPID, strings.Join(inputSlice[1:], " "))
		addrCache = cmd.RestoreFoundConverters(foundAddr)
		if err != nil {
			return err
		}

	} else if strings.HasPrefix(in, "snapshot") {
		slice := strings.Fields(in)
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		dataType := "dword"
		if len(slice) > 1 {
			dataType = slice[1]
		}
		foundAddr, err := cmd.Snapshot(appPID, dataType)
		addrCache = cmd.RestoreFoundConverters(foundAddr)
		if err != nil {
			return err
		}

	} else if strings.HasPrefix(in, "patch") {
		slice := strings.Fields(in)
		if len(slice) == 1 {
			return errors.New("Target value cannot be specified")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		if len(addrCache) == 0 {
			return errors.New("No previous results")
		}
		targetVal := strings.Join(slice[1:], " ")
		if withoutPtrace {
			return cmd.PatchWithoutPtrace(appPID, targetVal, cmd.RestoreFoundConverters(addrCache))
		}
		return cmd.PatchWithPtrace(appPID, targetVal, cmd.RestoreFoundConverters(addrCache))

	} else if strings.HasPrefix(in, "detach") {
		slice := strings.Fields(in)
		if len(slice) > 1 {
			appPID = slice[1]
		}
		if batchMode {
			attachRequested = false
			fmt.Println("Attach mode disabled.")
			return nil
		}
		return cmd.Detach()

	} else if strings.HasPrefix(in, "write-bytes") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 3 {
			return errors.New("write-bytes requires address and hex bytes")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		address, err := parseAddr(inputSlice[1])
		if err != nil {
			return err
		}
		hexBytes := strings.Join(inputSlice[2:], " ")
		if withoutPtrace {
			return cmd.WriteBytesWithoutPtrace(appPID, address, hexBytes)
		}
		return cmd.WriteBytesWithPtrace(appPID, address, hexBytes)

	} else if strings.HasPrefix(in, "write-file") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 3 {
			return errors.New("write-file requires address and source path")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		address, err := parseAddr(inputSlice[1])
		if err != nil {
			return err
		}
		sourcePath := inputSlice[2]
		if withoutPtrace {
			return cmd.WriteFileWithoutPtrace(appPID, address, sourcePath)
		}
		return cmd.WriteFileWithPtrace(appPID, address, sourcePath)

	} else if strings.HasPrefix(in, "dump-file") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 4 {
			return errors.New("dump-file requires begin address, end address, and output path")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		beginAddr, err := parseAddr(inputSlice[1])
		if err != nil {
			return err
		}
		endAddr, err := parseAddr(inputSlice[2])
		if err != nil {
			return err
		}

		return cmd.DumpFile(appPID, beginAddr, endAddr, inputSlice[3])

	} else if strings.HasPrefix(in, "dump") {
		inputSlice := strings.Fields(in)
		if len(inputSlice) < 3 {
			return errors.New("dump requires begin and end addresses")
		}
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		beginAddr, err := parseAddr(inputSlice[1])
		if err != nil {
			return err
		}
		endAddr, err := parseAddr(inputSlice[2])
		if err != nil {
			return err
		}

		return cmd.Dump(appPID, beginAddr, endAddr)

	} else if in == "clear-state" {
		appPID = ""
		addrCache = []cmd.Found{}
		attachRequested = false
		if stateFile != "" {
			if err := os.Remove(stateFile); err != nil && !os.IsNotExist(err) {
				return err
			}
		}
		fmt.Println("State cleared.")

	} else if in == "exit" {
		fmt.Println("Bye!")
		os.Exit(0)

	} else if in == "" {

	} else {
		return fmt.Errorf("unknown command: %s", in)
	}
	return nil
}

func parseAddr(arg string) (int, error) {
	arg = strings.Replace(arg, "0x", "", 1)
	address, err := strconv.ParseInt(arg, 16, 64)
	if err == nil {
		return int(address), nil
	}
	address, err = strconv.ParseInt(arg, 10, 64)
	if err == nil {
		return int(address), nil
	}
	return 0, err
}

func main() {
	flag.BoolVar(&withoutPtrace, "without-ptrace", false, "Memory modification without ptrace, which is not available in Android 10 and later")
	flag.StringVar(&specificPID, "pid", "", "Attach to a process with this pid")
	flag.StringVar(&oneShotCommand, "command", "", "Run a single command and exit")
	flag.StringVar(&stateFile, "state-file", "", "Persist target PID, attach mode, and search results for one-shot commands")
	flag.IntVar(&maxResults, "max-results", 500000, "Maximum addresses to keep for a scan or snapshot; 0 disables the cap")
	flag.IntVar(&skipResults, "skip-results", 0, "Skip this many matching addresses before collecting scan results")
	flag.BoolVar(&stringCaseSensitive, "case-sensitive", false, "Use case-sensitive UTF-8 string search")
	flag.BoolVar(&stringPatchTruncate, "string-patch-truncate", true, "Truncate and null-pad UTF-8 string patches to the original match byte length")
	flag.Parse()
	maxResults = effectiveMaxResultsForCommand(maxResults, oneShotCommand)
	cmd.SetMaxResults(maxResults)
	cmd.SetSkipResults(skipResults)
	cmd.SetStringCaseSensitive(stringCaseSensitive)
	cmd.SetStringPatchTruncate(stringPatchTruncate)

	// for ptrace attach
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	batchMode = strings.TrimSpace(oneShotCommand) != ""

	if batchMode {
		if err := runBatchCommand(strings.TrimSpace(oneShotCommand)); err != nil {
			fmt.Println(err)
			os.Exit(1)
		}
		return
	}

	if specificPID == "" {
		if pid, err := cmd.Plist(); err != nil {
			log.Fatal(err)
		} else if pid != "" {
			appPID = pid
		}
	} else {
		appPID = specificPID
	}
	addrCache = []cmd.Found{}
	reader := bufio.NewScanner(os.Stdin)
	for {
		fmt.Print("> ")
		if !reader.Scan() {
			break
		}
		executor(reader.Text())
	}
}

func effectiveMaxResultsForCommand(max int, command string) int {
	if max == 0 {
		fields := strings.Fields(strings.TrimSpace(command))
		if len(fields) > 0 && commandKeepsScanState(fields[0]) {
			fmt.Println("Result cap disabled; full comparison state will be written to disk.")
		}
	}
	return max
}

func commandKeepsScanState(command string) bool {
	switch command {
	case "find", "filter", "find-gt", "find-lt", "filter-gt", "filter-lt", "filter-unchanged", "filter-changed", "snapshot", "search-magic", "search-bytes", "search-bytes-mask":
		return true
	default:
		return false
	}
}

func runBatchCommand(command string) error {
	state := batchState{Founds: []cmd.Found{}}
	if stateFile != "" {
		if err := loadBatchState(stateFile, &state); err != nil {
			return err
		}
	}

	appPID = state.PID
	attachRequested = state.AttachRequested
	addrCache = cmd.RestoreFoundConverters(state.Founds)
	if specificPID != "" {
		if state.PID != "" && state.PID != specificPID {
			addrCache = []cmd.Found{}
			attachRequested = false
			fmt.Printf("Process changed from PID %s to %s; previous scan results cleared. Start a new scan.\n", state.PID, specificPID)
			state.PID = specificPID
			state.AttachRequested = false
			state.Founds = []cmd.Found{}
			if stateFile != "" {
				_ = saveBatchState(stateFile, state)
			}
		}
		appPID = specificPID
	}

	temporarilyAttached := false
	if attachRequested && commandNeedsTemporaryAttach(command) && !withoutPtrace {
		if appPID == "" {
			return errors.New("PID cannot be specified")
		}
		if err := cmd.Attach(appPID); err != nil {
			return err
		}
		temporarilyAttached = true
	}
	if temporarilyAttached {
		defer cmd.Detach()
	}

	if err := executeCommand(command); err != nil {
		return err
	}

	if command == "clear-state" {
		return nil
	}

	state.PID = appPID
	state.AttachRequested = attachRequested
	state.Founds = cmd.RestoreFoundConverters(addrCache)
	if stateFile != "" {
		return saveBatchState(stateFile, state)
	}
	return nil
}

func commandNeedsTemporaryAttach(command string) bool {
	fields := strings.Fields(command)
	if len(fields) == 0 {
		return false
	}
	switch fields[0] {
	case "find", "filter", "find-gt", "find-lt", "filter-gt", "filter-lt", "filter-unchanged", "filter-changed", "snapshot", "search-magic", "search-bytes", "search-bytes-mask", "dump", "dump-file", "write-bytes", "write-file":
		return true
	default:
		return false
	}
}

func loadBatchState(path string, state *batchState) error {
	file, err := os.Open(path)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	defer file.Close()
	if err := json.NewDecoder(file).Decode(state); err != nil {
		if err == io.EOF {
			return nil
		}
		return err
	}
	state.Founds = cmd.RestoreFoundConverters(state.Founds)
	return nil
}

func saveBatchState(path string, state batchState) error {
	dir := filepath.Dir(path)
	if dir != "." && dir != "" {
		if err := os.MkdirAll(dir, 0700); err != nil {
			return err
		}
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0600)
	if err != nil {
		return err
	}
	defer file.Close()
	encoder := json.NewEncoder(file)
	encoder.SetIndent("", "  ")
	return encoder.Encode(state)
}
