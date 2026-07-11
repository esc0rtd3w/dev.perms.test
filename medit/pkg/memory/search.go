package memory

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"sync"

	"github.com/sterrasec/apk-medit/pkg/converter"
)

var splitSize = 0x5000000
var maxResults = 500000
var skipResults int
var skippedResults int
var stringCaseSensitive bool

func SetMaxResults(max int) {
	if max < 0 {
		max = 0
	}
	maxResults = max
}

func MaxResults() int {
	return maxResults
}

func SetSkipResults(skip int) {
	if skip < 0 {
		skip = 0
	}
	skipResults = skip
	skippedResults = 0
}

func SkipResults() int {
	return skipResults + skippedResults
}

func SkippedResults() int {
	return skippedResults
}

func KeepSearchResult() bool {
	if skipResults > 0 {
		skipResults--
		skippedResults++
		return false
	}
	return true
}

func SetStringCaseSensitive(enabled bool) {
	stringCaseSensitive = enabled
}

var bufferPool = sync.Pool{
	New: func() interface{} {
		return make([]byte, splitSize)
	},
}

func GetWritableAddrRanges(mapsPath string) ([][2]int, error) {
	addrRanges := [][2]int{}
	ignorePaths := []string{"/vendor/lib64/", "/system/lib64/", "/system/bin/", "/system/framework/", "/data/dalvik-cache/"}
	file, err := os.OpenFile(mapsPath, os.O_RDONLY, 0600)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		meminfo := strings.Fields(line)
		addrRange := meminfo[0]
		permission := meminfo[1]
		if permission[0] == 'r' && permission[1] == 'w' && permission[3] != 's' {
			ignoreFlag := false
			if len(meminfo) >= 6 {
				filePath := meminfo[5]
				for _, ignorePath := range ignorePaths {
					if strings.HasPrefix(filePath, ignorePath) {
						ignoreFlag = true
						break
					}
				}
			}

			if !ignoreFlag {
				addrs := strings.Split(addrRange, "-")
				beginAddr, _ := strconv.ParseInt(addrs[0], 16, 64)
				endAddr, _ := strconv.ParseInt(addrs[1], 16, 64)
				addrRanges = append(addrRanges, [2]int{int(beginAddr), int(endAddr)})
			}
		}
	}
	return addrRanges, nil
}

func GetReadableAddrRanges(mapsPath string) ([][2]int, error) {
	addrRanges := [][2]int{}
	ignorePaths := []string{"/vendor/lib64/", "/system/lib64/", "/system/bin/", "/system/framework/", "/data/dalvik-cache/"}
	file, err := os.OpenFile(mapsPath, os.O_RDONLY, 0600)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		meminfo := strings.Fields(line)
		if len(meminfo) < 2 {
			continue
		}
		addrRange := meminfo[0]
		permission := meminfo[1]
		if len(permission) < 4 || permission[0] != 'r' || permission[3] == 's' {
			continue
		}
		ignoreFlag := false
		if len(meminfo) >= 6 {
			filePath := meminfo[5]
			for _, ignorePath := range ignorePaths {
				if strings.HasPrefix(filePath, ignorePath) {
					ignoreFlag = true
					break
				}
			}
		}
		if ignoreFlag {
			continue
		}
		addrs := strings.Split(addrRange, "-")
		beginAddr, _ := strconv.ParseInt(addrs[0], 16, 64)
		endAddr, _ := strconv.ParseInt(addrs[1], 16, 64)
		addrRanges = append(addrRanges, [2]int{int(beginAddr), int(endAddr)})
	}
	return addrRanges, nil
}

type Err struct {
	err error
}

func (e *Err) Error() string {
	return fmt.Sprint(e.err)
}

type ParseErr struct {
	*Err
}

type TooManyErr struct {
	*Err
}

func FindDataInAddrRanges(memPath string, targetBytes []byte, addrRanges [][2]int) ([]int, error) {
	foundAddrs := []int{}
	f, err := os.OpenFile(memPath, os.O_RDONLY, 0600)
	defer f.Close()

	searchLength := len(targetBytes)
	for _, s := range addrRanges {
		beginAddr := s[0]
		endAddr := s[1]
		memSize := endAddr - beginAddr
		if err != nil {
			fmt.Println(err)
		}
		for i := 0; i < (memSize/splitSize)+1; i++ {
			// target memory is too big to read all of it, so split it and then search in memory
			splitIndex := (i + 1) * splitSize
			splittedBeginAddr := beginAddr + i*splitSize
			splittedEndAddr := endAddr
			if splitIndex < memSize {
				splittedEndAddr = beginAddr + splitIndex
			}
			b := bufferPool.Get().([]byte)[:(splittedEndAddr - splittedBeginAddr)]
			ReadMemory(f, b, splittedBeginAddr, splittedEndAddr)
			findDataInSplittedMemory(&b, targetBytes, searchLength, splittedBeginAddr, 0, &foundAddrs)
			bufferPool.Put(b)
			if maxResults > 0 && len(foundAddrs) >= maxResults {
				return foundAddrs, nil
			}
		}
	}
	return foundAddrs, nil
}

func findDataInSplittedMemory(memory *[]byte, targetBytes []byte, searchLength int, beginAddr int, offset int, results *[]int) {
	if maxResults > 0 && len(*results) >= maxResults {
		return
	}
	// Use bytes.Index for the search step, but iterate instead of recursing so
	// dense result sets do not overflow the Go stack.
	for offset < len(*memory) {
		index := bytes.Index((*memory)[offset:], targetBytes)
		if index == -1 {
			return
		}
		resultAddr := beginAddr + index + offset
		if KeepSearchResult() {
			*results = append(*results, resultAddr)
			if maxResults > 0 && len(*results) >= maxResults {
				return
			}
		}
		offset += index + searchLength
	}
}

func FindDataInAddrRangesMasked(memPath string, targetBytes []byte, compareMask []byte, addrRanges [][2]int) ([]int, error) {
	foundAddrs := []int{}
	if len(targetBytes) == 0 {
		return foundAddrs, nil
	}
	if len(compareMask) != len(targetBytes) {
		return foundAddrs, errors.New("target and mask byte counts differ")
	}
	anchorStart, anchorBytes := longestFixedMaskRun(targetBytes, compareMask)
	if len(anchorBytes) == 0 {
		return foundAddrs, errors.New("mask must leave at least one fixed byte")
	}

	f, err := os.OpenFile(memPath, os.O_RDONLY, 0600)
	if err != nil {
		return foundAddrs, err
	}
	defer f.Close()

	searchLength := len(targetBytes)
	for _, s := range addrRanges {
		beginAddr := s[0]
		endAddr := s[1]
		memSize := endAddr - beginAddr
		for i := 0; i < (memSize/splitSize)+1; i++ {
			splitIndex := (i + 1) * splitSize
			splittedBeginAddr := beginAddr + i*splitSize
			splittedEndAddr := endAddr
			if splitIndex < memSize {
				splittedEndAddr = beginAddr + splitIndex
			}
			b := bufferPool.Get().([]byte)[:(splittedEndAddr - splittedBeginAddr)]
			ReadMemory(f, b, splittedBeginAddr, splittedEndAddr)
			findMaskedDataInSplittedMemory(&b, targetBytes, compareMask, anchorStart, anchorBytes, searchLength, splittedBeginAddr, 0, &foundAddrs)
			bufferPool.Put(b)
			if maxResults > 0 && len(foundAddrs) >= maxResults {
				return foundAddrs, nil
			}
		}
	}
	return foundAddrs, nil
}

func longestFixedMaskRun(targetBytes []byte, compareMask []byte) (int, []byte) {
	bestStart := -1
	bestLength := 0
	currentStart := -1
	currentLength := 0
	for i, maskByte := range compareMask {
		fixed := maskByte != 0
		if fixed {
			if currentStart < 0 {
				currentStart = i
				currentLength = 0
			}
			currentLength++
			if currentLength > bestLength {
				bestStart = currentStart
				bestLength = currentLength
			}
			continue
		}
		currentStart = -1
		currentLength = 0
	}
	if bestStart < 0 || bestLength <= 0 {
		return 0, nil
	}
	return bestStart, targetBytes[bestStart : bestStart+bestLength]
}

func findMaskedDataInSplittedMemory(memory *[]byte, targetBytes []byte, compareMask []byte, anchorStart int, anchorBytes []byte, searchLength int, beginAddr int, offset int, results *[]int) {
	if maxResults > 0 && len(*results) >= maxResults {
		return
	}
	for offset < len(*memory) {
		index := bytes.Index((*memory)[offset:], anchorBytes)
		if index == -1 {
			return
		}
		anchorIndex := offset + index
		candidate := anchorIndex - anchorStart
		if candidate >= 0 && candidate+searchLength <= len(*memory) && maskedBytesEqual((*memory)[candidate:candidate+searchLength], targetBytes, compareMask) {
			resultAddr := beginAddr + candidate
			if KeepSearchResult() {
				*results = append(*results, resultAddr)
				if maxResults > 0 && len(*results) >= maxResults {
					return
				}
			}
		}
		offset = anchorIndex + 1
	}
}

func maskedBytesEqual(candidate []byte, targetBytes []byte, compareMask []byte) bool {
	for i, target := range targetBytes {
		if compareMask[i] != 0 && candidate[i] != target {
			return false
		}
	}
	return true
}

func asciiLowerCopy(src []byte) []byte {
	dst := make([]byte, len(src))
	for i, b := range src {
		if b >= 'A' && b <= 'Z' {
			dst[i] = b + ('a' - 'A')
		} else {
			dst[i] = b
		}
	}
	return dst
}

func FindDataInAddrRangesCaseInsensitive(memPath string, targetBytes []byte, addrRanges [][2]int) ([]int, error) {
	foundAddrs := []int{}
	f, err := os.OpenFile(memPath, os.O_RDONLY, 0600)
	defer f.Close()

	if err != nil {
		return foundAddrs, err
	}
	targetLower := asciiLowerCopy(targetBytes)
	searchLength := len(targetLower)
	for _, s := range addrRanges {
		beginAddr := s[0]
		endAddr := s[1]
		memSize := endAddr - beginAddr
		for i := 0; i < (memSize/splitSize)+1; i++ {
			splitIndex := (i + 1) * splitSize
			splittedBeginAddr := beginAddr + i*splitSize
			splittedEndAddr := endAddr
			if splitIndex < memSize {
				splittedEndAddr = beginAddr + splitIndex
			}
			b := bufferPool.Get().([]byte)[:(splittedEndAddr - splittedBeginAddr)]
			ReadMemory(f, b, splittedBeginAddr, splittedEndAddr)
			lower := asciiLowerCopy(b)
			findDataInSplittedMemory(&lower, targetLower, searchLength, splittedBeginAddr, 0, &foundAddrs)
			bufferPool.Put(b)
			if maxResults > 0 && len(foundAddrs) >= maxResults {
				return foundAddrs, nil
			}
		}
	}
	return foundAddrs, nil
}

func FindString(memPath string, targetVal string, addrRanges [][2]int) ([]int, error) {
	if stringCaseSensitive {
		fmt.Println("Search UTF-8 String (case-sensitive)...")
	} else {
		fmt.Println("Search UTF-8 String (case-insensitive)...")
	}
	targetBytes, _ := converter.StringToBytes(targetVal)
	fmt.Printf("Target Value: %s(%v)\n", targetVal, targetBytes)
	var foundAddrs []int
	var err error
	if stringCaseSensitive {
		foundAddrs, err = FindDataInAddrRanges(memPath, targetBytes, addrRanges)
	} else {
		foundAddrs, err = FindDataInAddrRangesCaseInsensitive(memPath, targetBytes, addrRanges)
	}
	fmt.Printf("Found: %d!!\n", len(foundAddrs))
	if SkippedResults() > 0 {
		fmt.Printf("Skipped: %d matching addresses before collecting this range.\n", SkippedResults())
	}
	if maxResults > 0 && len(foundAddrs) >= maxResults {
		fmt.Printf("Result cap reached at %d addresses. Refine the scan or raise --max-results.\n", maxResults)
	}
	if len(foundAddrs) < 10 {
		for _, v := range foundAddrs {
			fmt.Printf("Address: 0x%x\n", v)
		}
	}
	return foundAddrs, err
}

func FindWord(memPath string, targetVal string, addrRanges [][2]int) ([]int, error) {
	fmt.Println("Search Word...")
	targetBytes, err := converter.WordToBytes(targetVal)
	if err != nil {
		fmt.Printf("parsing %s: value out of range\n", targetVal)
		return nil, ParseErr{&Err{errors.New("Error: value out of range")}}
	}
	fmt.Printf("Target Value: %s(%v)\n", targetVal, targetBytes)
	foundAddrs, err := FindDataInAddrRanges(memPath, targetBytes, addrRanges)
	fmt.Printf("Found: %d!!\n", len(foundAddrs))
	if SkippedResults() > 0 {
		fmt.Printf("Skipped: %d matching addresses before collecting this range.\n", SkippedResults())
	}
	if maxResults > 0 && len(foundAddrs) >= maxResults {
		fmt.Printf("Result cap reached at %d addresses. Refine the scan or raise --max-results.\n", maxResults)
	}
	if len(foundAddrs) < 10 {
		for _, v := range foundAddrs {
			fmt.Printf("Address: 0x%x\n", v)
		}
	}
	return foundAddrs, err
}

func FindDword(memPath string, targetVal string, addrRanges [][2]int) ([]int, error) {
	fmt.Println("Search Double Word...")
	targetBytes, err := converter.DwordToBytes(targetVal)
	if err != nil {
		fmt.Printf("parsing %s: value out of range\n", targetVal)
		return nil, ParseErr{&Err{errors.New("Error: value out of range")}}
	}
	fmt.Printf("Target Value: %s(%v)\n", targetVal, targetBytes)
	foundAddrs, err := FindDataInAddrRanges(memPath, targetBytes, addrRanges)
	fmt.Printf("Found: %d!!\n", len(foundAddrs))
	if SkippedResults() > 0 {
		fmt.Printf("Skipped: %d matching addresses before collecting this range.\n", SkippedResults())
	}
	if maxResults > 0 && len(foundAddrs) >= maxResults {
		fmt.Printf("Result cap reached at %d addresses. Refine the scan or raise --max-results.\n", maxResults)
	}
	if len(foundAddrs) < 10 {
		for _, v := range foundAddrs {
			fmt.Printf("Address: 0x%x\n", v)
		}
	}
	return foundAddrs, err
}

func FindQword(memPath string, targetVal string, addrRanges [][2]int) ([]int, error) {
	fmt.Println("Search Quad Word...")
	targetBytes, err := converter.QwordToBytes(targetVal)
	if err != nil {
		fmt.Printf("parsing %s: value out of range\n", targetVal)
		return nil, ParseErr{&Err{errors.New("Error: value out of range")}}
	}
	fmt.Printf("Target Value: %s(%v)\n", targetVal, targetBytes)
	foundAddrs, err := FindDataInAddrRanges(memPath, targetBytes, addrRanges)
	fmt.Printf("Found: %d!!\n", len(foundAddrs))
	if SkippedResults() > 0 {
		fmt.Printf("Skipped: %d matching addresses before collecting this range.\n", SkippedResults())
	}
	if maxResults > 0 && len(foundAddrs) >= maxResults {
		fmt.Printf("Result cap reached at %d addresses. Refine the scan or raise --max-results.\n", maxResults)
	}
	if len(foundAddrs) < 10 {
		for _, v := range foundAddrs {
			fmt.Printf("Address: 0x%x\n", v)
		}
	}
	return foundAddrs, err
}
