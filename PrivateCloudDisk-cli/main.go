package main

import (
	"fmt"
	"os"

	"github.com/privateclouddisk/cli/cmd"
)

var (
	// 编译时注入的版本信息
	Version   = "dev"
	Commit    = "unknown"
	BuildTime = "unknown"
)

func main() {
	cmd.SetVersion(Version, Commit, BuildTime)

	if err := cmd.Execute(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
}