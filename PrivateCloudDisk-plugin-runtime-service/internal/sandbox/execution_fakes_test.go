package sandbox

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/broker"
	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/uds"
)

// repoPluginsDir 返回 testdata/plugins 夹具目录（相对本包编译路径）。
func repoPluginsDir() string {
	return filepath.Join("..", "..", "testdata", "plugins")
}

// fixtureZipBytes 把一个夹具脚本打包成受约束 .pcdpkg（manifest.yaml + src/<script>），
// 事件 pcd.file.content.ready.v1、函数 main、预激活读写权限（覆盖 Execute 集成用例）。
func fixtureZipBytes(t *testing.T, name string) []byte {
	t.Helper()
	return multiFixtureZipBytes(t, name)
}

// multiFixtureZipBytes 把多个夹具脚本打进同一个 .pcdpkg（同一事件的多入口链）。
func multiFixtureZipBytes(t *testing.T, names ...string) []byte {
	t.Helper()
	srcFiles := map[string][]byte{}
	events := ""
	index := 1
	for _, name := range names {
		path := filepath.Join(repoPluginsDir(), name)
		content, err := os.ReadFile(path)
		if err != nil {
			t.Fatalf("读取夹具 %s: %v", name, err)
		}
		srcFiles["src/"+name] = content
		events += fmt.Sprintf(`  - event: %s
    module: src/%s
    function: main
    priority: %d
    permissions:
      - file.content.read_staging
      - file.content.write_pre_activation
`, EventContentReady, name, 10*index)
		index++
	}
	manifest := fmt.Sprintf(`manifest_version: 1
plugin:
  id: %s
  name: fixture-plugin
  type: CLOUD_PLUGIN
  version: 1.0.0
runtime:
  language: python
  version: "3.11"
permissions:
  - file.content.read_staging
  - file.content.write_pre_activation
entrypoints:
  events:
%s`, fixtureManifestID("fixture", 1), events)
	return pcdpkgBytes(t, manifest, srcFiles)
}

// zipFiles 把一组磁盘文件打进内存 .pcdpkg zip（保留相对路径），供部分用例复用。
func zipFiles(t *testing.T, paths ...string) []byte {
	t.Helper()
	buffer := &bytes.Buffer{}
	writer := zip.NewWriter(buffer)
	for _, path := range paths {
		content, err := os.ReadFile(path)
		if err != nil {
			t.Fatalf("读取夹具 %s: %v", path, err)
		}
		header := &zip.FileHeader{Name: filepath.Base(path), Method: zip.Deflate}
		header.SetMode(0o400)
		entry, err := writer.CreateHeader(header)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write(content); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

// fakeBroker 记录调用并允许按函数注入行为（需求六 6.8/7.5）。
type fakeBroker struct {
	mu sync.Mutex
	// 默认：Exchange 返回固定 Lease；Download / DownloadActive 写入哨兵内容；Upload 返回固定 Candidate。
	exchangeFn          func(ctx context.Context, gateID, executionID, ref string, ttl int) (broker.ExchangedLease, error)
	downloadFn          func(ctx context.Context, gateID, executionID, lease, destination string) error
	downloadActiveFn    func(ctx context.Context, fileID, executionID, actorUserID, spaceID, destination string) error
	uploadFn            func(ctx context.Context, gateID, executionID, lease, source string) (broker.Candidate, error)
	exchangeCalls       int
	downloadCalls       int
	downloadActiveCalls int
	uploadCalls         int
	uploadSources       []string
	uploadContents      [][]byte
}

// fakeCapabilityInvoker keeps ordinary Runner logic tests on the real UDS
// session path without requiring an HTTP Capability Hub or Docker. Integration
// tests replace it with their socket-aware relay.
type fakeCapabilityInvoker struct {
	result uds.InvocationResult
	err    error
}

func (f fakeCapabilityInvoker) Invoke(_ context.Context, _ uds.Invocation) (uds.InvocationResult, error) {
	return f.result, f.err
}

func (b *fakeBroker) Exchange(ctx context.Context, gateID, executionID, ref string, ttl int) (broker.ExchangedLease, error) {
	b.mu.Lock()
	b.exchangeCalls++
	b.mu.Unlock()
	if b.exchangeFn != nil {
		return b.exchangeFn(ctx, gateID, executionID, ref, ttl)
	}
	return broker.ExchangedLease{ExecutionLease: "lease-" + executionID, ExpiresAt: "2030-01-01T00:00:00Z"}, nil
}

func (b *fakeBroker) Download(ctx context.Context, gateID, executionID, lease, destination string) error {
	b.mu.Lock()
	b.downloadCalls++
	b.mu.Unlock()
	if b.downloadFn != nil {
		return b.downloadFn(ctx, gateID, executionID, lease, destination)
	}
	return os.WriteFile(destination, []byte("preprocessed-content"), 0o400)
}

func (b *fakeBroker) DownloadActive(ctx context.Context, fileID, executionID, actorUserID, spaceID, destination string) error {
	b.mu.Lock()
	b.downloadActiveCalls++
	b.mu.Unlock()
	if b.downloadActiveFn != nil {
		return b.downloadActiveFn(ctx, fileID, executionID, actorUserID, spaceID, destination)
	}
	return os.WriteFile(destination, []byte("active-content"), 0o400)
}

func (b *fakeBroker) Upload(ctx context.Context, gateID, executionID, lease, source string) (broker.Candidate, error) {
	b.mu.Lock()
	b.uploadCalls++
	b.uploadSources = append(b.uploadSources, source)
	content, readErr := os.ReadFile(source)
	if readErr == nil {
		b.uploadContents = append(b.uploadContents, content)
	}
	b.mu.Unlock()
	if b.uploadFn != nil {
		return b.uploadFn(ctx, gateID, executionID, lease, source)
	}
	size := int64(0)
	if stat, err := os.Stat(source); err == nil {
		size = stat.Size()
	}
	return broker.Candidate{
		ID: "candidate-1", Checksum: strings.Repeat("a1", 32), Size: size,
	}, nil
}

func (b *fakeBroker) calls() (int, int, int, int) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.exchangeCalls, b.downloadCalls, b.downloadActiveCalls, b.uploadCalls
}

// fakePackages 记录插件包下载调用（需求七 7.7）。
type fakePackages struct {
	mu            sync.Mutex
	zips          map[string][]byte
	downloadFn    func(ctx context.Context, versionID, destination string) error
	downloadCalls map[string]int
}

func (p *fakePackages) DownloadPcdpkg(ctx context.Context, versionID, destination string) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.downloadCalls == nil {
		p.downloadCalls = map[string]int{}
	}
	p.downloadCalls[versionID]++
	if p.downloadFn != nil {
		return p.downloadFn(ctx, versionID, destination)
	}
	content, ok := p.zips[versionID]
	if !ok {
		return fmt.Errorf("插件包 %q 未注册", versionID)
	}
	if err := os.MkdirAll(filepath.Dir(destination), 0o700); err != nil {
		return err
	}
	return os.WriteFile(destination, content, 0o600)
}

func (p *fakePackages) calls() map[string]int {
	p.mu.Lock()
	defer p.mu.Unlock()
	out := map[string]int{}
	for key, value := range p.downloadCalls {
		out[key] = value
	}
	return out
}

// fileSHA256 供 upload 候选校验与假响应构造使用。
func fileSHA256(path string) string {
	file, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer file.Close()
	digest := sha256.New()
	if _, err := io.Copy(digest, file); err != nil {
		return ""
	}
	return hex.EncodeToString(digest.Sum(nil))
}

// newTestRunner 构造注入 fakes 的最小 Runner（用于 mock 逻辑测试）。
func newTestRunner(t *testing.T, cfg config.Config, brokerClient *fakeBroker, packages *fakePackages) *Runner {
	t.Helper()
	if cfg.WorkRoot == "" {
		cfg.WorkRoot = t.TempDir()
	}
	if cfg.SocketRoot == "" {
		// Darwin/Linux Unix-domain paths are intentionally short; placing test
		// sockets in /tmp mirrors production's short /run/pcd/plugins root and
		// keeps the execution work-root cleanup assertion independent.
		socketRoot, err := os.MkdirTemp("/tmp", "pcd-runtime-uds-")
		if err != nil {
			t.Fatalf("创建 Runtime UDS 测试目录失败：%v", err)
		}
		cfg.SocketRoot = socketRoot
		t.Cleanup(func() { _ = os.RemoveAll(socketRoot) })
	}
	if cfg.SocketMaxFrameBytes == 0 {
		cfg.SocketMaxFrameBytes = 64 * 1024
	}
	if cfg.SocketMaxConnections == 0 {
		cfg.SocketMaxConnections = 8
	}
	if cfg.SocketRequestsPerSec == 0 {
		cfg.SocketRequestsPerSec = 1000
	}
	if cfg.SocketRequestBurst == 0 {
		cfg.SocketRequestBurst = 1000
	}
	if cfg.SocketRequestTimeout == 0 {
		cfg.SocketRequestTimeout = 5 * time.Second
	}
	if cfg.ExecutionTimeout <= 0 {
		cfg.ExecutionTimeout = 30 * time.Second
	}
	if cfg.PackageMaxBytes == 0 {
		cfg.PackageMaxBytes = 10 * 1024 * 1024
	}
	if cfg.CandidateMaxBytes == 0 {
		cfg.CandidateMaxBytes = 10 * 1024 * 1024
	}
	if cfg.LogLimitBytes == 0 {
		cfg.LogLimitBytes = 100 * 1024
	}
	if packages == nil {
		packages = &fakePackages{zips: map[string][]byte{}}
	}
	if brokerClient == nil {
		brokerClient = &fakeBroker{}
	}
	sessions, err := uds.NewManager(uds.Config{
		RootDir: cfg.SocketRoot, GroupID: -1, MaxFrameBytes: cfg.SocketMaxFrameBytes,
		MaxConnectionsPerPeer: cfg.SocketMaxConnections, RequestsPerSecond: cfg.SocketRequestsPerSec,
		RequestBurst: cfg.SocketRequestBurst, RequestTimeout: cfg.SocketRequestTimeout,
	}, fakeCapabilityInvoker{})
	if err != nil {
		t.Fatalf("创建 Runtime UDS 测试会话管理器失败：%v", err)
	}
	t.Cleanup(func() { _ = sessions.Close() })
	return &Runner{Config: cfg, Packages: packages, Broker: brokerClient, Sessions: sessions}
}
