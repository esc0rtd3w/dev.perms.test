package main

import (
	"fmt"
	"os"

	"github.com/esc0rtd3w/jadx-go/internal/dex"
)

const version = "jadx-go 0.1.0"

func main() {
	if err := dex.Run(os.Args[1:], os.Stdout, os.Stderr, version); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
