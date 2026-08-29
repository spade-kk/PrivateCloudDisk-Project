package sandbox

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/audit"
	"privateclouddisk/plugin-runtime-service/internal/config"
)

const goodDigest = "sha256:62be2a75d53130c0b76b96a7eddf6fa343777d39a5c49859d266ec7a9e1e5fcf"

// stubDockerScript 生成一个可执行桩：image inspect 时按 marker 输出 RepoDigests，
// 其余调用一律失败；每次 image inspect 调用写一行到 counterPath 便于断言次数（4.7）。
func stubDockerScript(t *testing.T, counterPath, digest string) string {
	t.Helper()
	script := filepath.Join(t.TempDir(), "docker-stub")
	content := "#!/bin/sh\n" +
		"if [ \"$1\" = \"image\" ] && [ \"$2\" = \"inspect\" ]; then\n" +
		"  echo \"[pcd/plugin-sandbox-python@" + digest + "]\"\n" +
		"  echo x >> \"" + counterPath + "\"\n" +
		"  exit 0\n" +
		"fi\n" +
		"echo unexpected-args >&2\n" +
		"exit 1\n"
	if err := os.WriteFile(script, []byte(content), 0o700); err != nil {
		t.Fatal(err)
	}
	return script
}

func digestRunner(t *testing.T, counterPath, digest string) *Runner {
	t.Helper()
	cfg := config.Config{
		RequireSandboxDigest: true,
		SandboxImageDigest:   digest,
		SandboxImage:         "pcd/plugin-sandbox-python:0.1.0",
		DockerBinary:         stubDockerScript(t, counterPath, goodDigest),
	}
	return &Runner{Config: cfg}
}

func counterValue(path string) int {
	data, _ := os.ReadFile(path)
	return len(strings.Fields(string(data)))
}

func TestDigestDisabledShortCircuits(t *testing.T) {
	// 4.1：RequireSandboxDigest=false 直接返回 nil，不调用 docker。
	runner := &Runner{Config: config.Config{RequireSandboxDigest: false}}
	if err := runner.VerifyImageDigest(context.Background()); err != nil {
		t.Fatalf("关闭摘要门禁时应直接通过：%v", err)
	}
}

func TestDigestMatchSetsFlagOnce(t *testing.T) {
	// 4.4/4.7/4.17：匹配时设置 digestChecked，进程内只检查一次。
	counter := filepath.Join(t.TempDir(), "counter")
	runner := digestRunner(t, counter, goodDigest)
	if err := runner.VerifyImageDigest(context.Background()); err != nil {
		t.Fatalf("匹配摘要应通过：%v", err)
	}
	if err := runner.VerifyImageDigest(context.Background()); err != nil {
		t.Fatalf("二次校验仍应通过：%v", err)
	}
	if counterValue(counter) != 1 {
		t.Fatalf("摘要检查应只执行一次，实际 %d 次", counterValue(counter))
	}
}

func TestDigestMismatchRejected(t *testing.T) {
	// 4.5/4.10/4.14：摘要不匹配（或配置为空）返回错误，且不设置成功标志。
	for _, td := range []struct {
		name   string
		digest string
	}{
		{name: "摘要不匹配", digest: "sha256:0000000000000000000000000000000000000000000000000000000000000000"},
		{name: "摘要为空", digest: ""},
	} {
		t.Run(td.name, func(t *testing.T) {
			counter := filepath.Join(t.TempDir(), "counter")
			runner := digestRunner(t, counter, td.digest)
			err := runner.VerifyImageDigest(context.Background())
			if err == nil {
				t.Fatal("摘要不匹配应返回错误")
			}
			if !strings.Contains(err.Error(), "已拒绝启动沙箱") {
				t.Fatalf("错误信息应含拒因：%v", err)
			}
		})
	}
}

func TestDigestInspectFailureAndTimeout(t *testing.T) {
	// 4.6：docker image inspect 执行失败（桩返回非零）→ 错误。
	script := filepath.Join(t.TempDir(), "docker-fail")
	if err := os.WriteFile(script, []byte("#!/bin/sh\nexit 1\n"), 0o700); err != nil {
		t.Fatal(err)
	}
	runner := &Runner{Config: config.Config{
		RequireSandboxDigest: true,
		SandboxImageDigest:   goodDigest,
		DockerBinary:         script,
	}}
	if err := runner.VerifyImageDigest(context.Background()); err == nil {
		t.Fatal("inspect 失败应返回错误")
	}

	// 4.7：父 ctx 已到期 → 命令无法启动，快速返回错误。
	expired, cancel := context.WithTimeout(context.Background(), time.Nanosecond)
	defer cancel()
	time.Sleep(time.Millisecond)
	if err := runner.VerifyImageDigest(expired); err == nil {
		t.Fatal("超时上下文应返回错误")
	}
}

func TestDigestConcurrentOnlyOnce(t *testing.T) {
	// 4.8/4.19：并发调用时只执行一次摘要检查。
	counter := filepath.Join(t.TempDir(), "counter")
	runner := digestRunner(t, counter, goodDigest)
	var wait sync.WaitGroup
	for index := 0; index < 8; index++ {
		wait.Add(1)
		go func() {
			defer wait.Done()
			if err := runner.VerifyImageDigest(context.Background()); err != nil {
				t.Errorf("并发校验失败：%v", err)
			}
		}()
	}
	wait.Wait()
	if got := counterValue(counter); got != 1 {
		t.Fatalf("并发时摘要检查应只执行 1 次，实际 %d", got)
	}
}

// TestRunContainerDigestGate 摘要门禁失败时容器不启动并记录审计（4.15/4.18/4.22/5.24）。
func TestRunContainerDigestGate(t *testing.T) {
	auditPath := filepath.Join(t.TempDir(), "audit.log")
	sink, err := audit.New(auditPath)
	if err != nil {
		t.Fatal(err)
	}
	defer sink.Close()

	script := filepath.Join(t.TempDir(), "docker-mismatch")
	content := "#!/bin/sh\n" +
		"if [ \"$1\" = \"image\" ] && [ \"$2\" = \"inspect\" ]; then\n" +
		"  echo \"[" + goodDigest + "]\"\n" +
		"  exit 0\n" +
		"fi\n" +
		"echo should-not-be-invoked >&2\n" +
		"exit 1\n"
	if err := os.WriteFile(script, []byte(content), 0o700); err != nil {
		t.Fatal(err)
	}
	runner := &Runner{Config: config.Config{
		RequireSandboxDigest: true,
		SandboxImageDigest:   "sha256:deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
		SandboxImage:         "pcd/plugin-sandbox-python:0.1.0",
		DockerBinary:         script,
		WorkRoot:             t.TempDir(),
	}, Audit: sink}

	_, _, err = runner.runContainer(
		context.Background(), "exec_1", 0,
		t.TempDir(), t.TempDir(), t.TempDir(), t.TempDir(), argsEntrypoint,
		"main.py", "main", execLimits{timeout: 30 * time.Second, memoryBytes: 256 * 1024 * 1024},
	)
	if err == nil {
		t.Fatal("摘要门禁失败时容器不应启动")
	}
	auditBytes, _ := os.ReadFile(auditPath)
	if !strings.Contains(string(auditBytes), "container_digest_rejected") {
		t.Fatalf("应记录 container_digest_rejected 审计：%s", auditBytes)
	}
	if strings.Contains(string(auditBytes), "should-not-be-invoked") {
		t.Fatal("摘要门禁失败后不应继续调用 docker run")
	}
}
