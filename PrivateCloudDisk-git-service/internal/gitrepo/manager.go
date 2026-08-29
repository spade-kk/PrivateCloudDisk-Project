package gitrepo

import (
	"bufio"
	"bytes"
	"compress/zlib"
	"context"
	"crypto/sha1"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"privateclouddisk/git-service/internal/config"
	"privateclouddisk/git-service/internal/domain"
	storageclient "privateclouddisk/git-service/internal/storage"
	"privateclouddisk/git-service/internal/store"
)

var (
	validRefComponent = regexp.MustCompile(`^[A-Za-z0-9._/-]{1,255}$`)
	validHash         = regexp.MustCompile(`^[0-9a-f]{40}([0-9a-f]{24})?$`)
)

type Manager struct {
	cfg     config.Config
	store   *store.Store
	storage *storageclient.Client
	locks   sync.Map
}

func New(cfg config.Config, dataStore *store.Store, storage *storageclient.Client) *Manager {
	return &Manager{cfg: cfg, store: dataStore, storage: storage}
}

func (m *Manager) repoPath(repo domain.Repository) string {
	return filepath.Join(m.cfg.RepoRoot, repo.ID+".git")
}

func (m *Manager) lock(repoID string) func() {
	value, _ := m.locks.LoadOrStore(repoID, &sync.Mutex{})
	mutex := value.(*sync.Mutex)
	mutex.Lock()
	return mutex.Unlock
}

func (m *Manager) CreateBare(ctx context.Context, repo domain.Repository) error {
	unlock := m.lock(repo.ID)
	defer unlock()
	path := m.repoPath(repo)
	if _, err := os.Stat(filepath.Join(path, "HEAD")); err == nil {
		return nil
	}
	if err := os.MkdirAll(m.cfg.RepoRoot, 0o750); err != nil {
		return err
	}
	if _, err := m.run(ctx, "", "init", "--bare", "--object-format="+repo.HashAlgorithm, path); err != nil {
		return err
	}
	if _, err := m.run(ctx, path, "config", "http.receivepack", "true"); err != nil {
		return err
	}
	if err := m.configureServiceIdentity(ctx, path); err != nil {
		return err
	}
	if _, err := m.run(ctx, path, "symbolic-ref", "HEAD", "refs/heads/"+repo.DefaultBranch); err != nil {
		return err
	}
	return m.writeProtectionHook(ctx, repo)
}

// Fork copies the source repository into a new Git resource directory without
// accepting any path or shell fragment from the client. Both paths are derived
// from UUID repository IDs and the clone is followed by the normal shared-store
// synchronization, so a fork is immediately cloneable from another instance.
func (m *Manager) Fork(ctx context.Context, source, target domain.Repository) error {
	sourcePath, err := m.EnsureLocal(ctx, source)
	if err != nil {
		return err
	}
	unlock := m.lock(target.ID)
	defer unlock()
	targetPath := m.repoPath(target)
	if _, err := os.Stat(filepath.Join(targetPath, "HEAD")); err == nil {
		return errors.New("target repository already exists")
	}
	if err := os.MkdirAll(m.cfg.RepoRoot, 0o750); err != nil {
		return err
	}
	command := exec.CommandContext(ctx, m.cfg.GitBinary, "clone", "--bare", "--no-local", sourcePath, targetPath)
	if output, err := command.CombinedOutput(); err != nil {
		return fmt.Errorf("fork repository: %w: %s", err, strings.TrimSpace(string(output)))
	}
	if err := m.configureServiceIdentity(ctx, targetPath); err != nil {
		return err
	}
	if _, err := m.run(ctx, targetPath, "symbolic-ref", "HEAD", "refs/heads/"+target.DefaultBranch); err != nil {
		return err
	}
	return m.Sync(ctx, target)
}

// EnsureLocal 从全局内容寻址 Object Store 恢复本地 bare repo 缓存。
// 本地仓库只承担协议热路径，StorageProvider 才是跨实例共享的对象事实源。
func (m *Manager) EnsureLocal(ctx context.Context, repo domain.Repository) (string, error) {
	path := m.repoPath(repo)
	if _, err := os.Stat(filepath.Join(path, "HEAD")); err == nil {
		return path, nil
	}
	unlock := m.lock(repo.ID)
	defer unlock()
	if _, err := os.Stat(filepath.Join(path, "HEAD")); err == nil {
		return path, nil
	}
	if err := m.createBareUnlocked(ctx, repo); err != nil {
		return "", err
	}
	objects, err := m.store.ListRepositoryObjects(ctx, repo.ID)
	if err != nil {
		return "", err
	}
	for _, object := range objects {
		destination := filepath.Join(path, "objects", object.Hash[:2], object.Hash[2:])
		if err := m.storage.Download(ctx, repo.HashAlgorithm, object.Hash, destination); err != nil {
			_ = m.store.MarkRepositoryStatus(ctx, repo.ID, "DEGRADED")
			return "", fmt.Errorf("hydrate object %s: %w", object.Hash, err)
		}
	}
	refs, err := m.store.ListRefs(ctx, repo.ID, "")
	if err != nil {
		return "", err
	}
	for _, ref := range refs {
		if _, err := m.run(ctx, path, "update-ref", ref.Name, ref.ObjectHash); err != nil {
			return "", err
		}
	}
	if _, err := m.run(ctx, path, "symbolic-ref", "HEAD", "refs/heads/"+repo.DefaultBranch); err != nil {
		return "", err
	}
	if err := m.writeProtectionHook(ctx, repo); err != nil {
		return "", err
	}
	return path, nil
}

func (m *Manager) createBareUnlocked(ctx context.Context, repo domain.Repository) error {
	path := m.repoPath(repo)
	if err := os.MkdirAll(m.cfg.RepoRoot, 0o750); err != nil {
		return err
	}
	if _, err := m.run(ctx, "", "init", "--bare", "--object-format="+repo.HashAlgorithm, path); err != nil {
		return err
	}
	if _, err := m.run(ctx, path, "config", "http.receivepack", "true"); err != nil {
		return err
	}
	if err := m.configureServiceIdentity(ctx, path); err != nil {
		return err
	}
	_, err := m.run(ctx, path, "symbolic-ref", "HEAD", "refs/heads/"+repo.DefaultBranch)
	return err
}

func (m *Manager) configureServiceIdentity(ctx context.Context, path string) error {
	// 原 bare 仓库没有提交身份，服务端生成 Merge Commit/附注 Tag 会失败；
	// 新行为仅为服务端生成对象设置固定技术身份，不覆盖客户端提交者与作者信息。
	if _, err := m.run(ctx, path, "config", "user.name", "PrivateCloudDisk Git Service"); err != nil {
		return err
	}
	_, err := m.run(ctx, path, "config", "user.email", "git-service@privateclouddisk.local")
	return err
}

func (m *Manager) DeleteLocal(repo domain.Repository) error {
	path := m.repoPath(repo)
	root, err := filepath.Abs(m.cfg.RepoRoot)
	if err != nil {
		return err
	}
	resolved, err := filepath.Abs(path)
	if err != nil {
		return err
	}
	if filepath.Dir(resolved) != root || !strings.HasSuffix(resolved, ".git") {
		return errors.New("refusing to delete path outside Git repository root")
	}
	return os.RemoveAll(resolved)
}

func (m *Manager) SnapshotRefs(ctx context.Context, repo domain.Repository) (map[string]string, error) {
	path, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return nil, err
	}
	output, err := m.run(ctx, path, "for-each-ref", "--format=%(refname) %(objectname)", "refs/heads", "refs/tags")
	if err != nil {
		return nil, err
	}
	return parseRefSnapshot(output), nil
}

func ChangedRefs(before, after map[string]string) map[string]map[string]string {
	result := map[string]map[string]string{}
	for ref, oldHash := range before {
		newHash := after[ref]
		if newHash != oldHash {
			result[ref] = map[string]string{"before": oldHash, "after": newHash}
		}
	}
	for ref, newHash := range after {
		if _, existed := before[ref]; !existed {
			result[ref] = map[string]string{"before": strings.Repeat("0", len(newHash)), "after": newHash}
		}
	}
	return result
}

func (m *Manager) Sync(ctx context.Context, repo domain.Repository) error {
	path, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		_ = m.store.MarkRepositoryStatus(ctx, repo.ID, "DEGRADED")
		return err
	}
	if err := m.store.MarkRepositoryStatus(ctx, repo.ID, "SYNCING"); err != nil {
		return err
	}
	refsMap, err := m.SnapshotRefs(ctx, repo)
	if err != nil {
		_ = m.store.MarkRepositoryStatus(ctx, repo.ID, "DEGRADED")
		return err
	}
	refs := refsFromSnapshot(refsMap)
	if err := m.store.ReplaceRefs(ctx, repo.ID, refs); err != nil {
		_ = m.store.MarkRepositoryStatus(ctx, repo.ID, "DEGRADED")
		return err
	}
	objects, err := m.syncObjects(ctx, repo, path)
	if err != nil {
		_ = m.store.MarkRepositoryStatus(ctx, repo.ID, "DEGRADED")
		return err
	}
	if err := m.store.SyncObjects(ctx, repo.ID, repo.HashAlgorithm, objects); err != nil {
		_ = m.store.MarkRepositoryStatus(ctx, repo.ID, "DEGRADED")
		return err
	}
	if err := m.SyncCommitIndex(ctx, repo); err != nil {
		_ = m.store.MarkRepositoryStatus(ctx, repo.ID, "DEGRADED")
		return err
	}
	return nil
}

// UpdateServerInfo 刷新 dumb HTTP 所需的 info/refs 与 objects/info/packs 索引。
// [REQ-GIT-AUDIT-2.8/2.40/2.50] 原行为只服务 Smart HTTP，push 后静态兼容端点可能
// 返回过期索引；新行为在已成功同步的 push 后调用 Git 原生命令。该索引只属于本地 bare
// 协议缓存，失败不会回滚已成功的 Smart push，调用方仅记录告警以避免误报为持久化失败。
func (m *Manager) UpdateServerInfo(ctx context.Context, repo domain.Repository) error {
	path, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return err
	}
	_, err = m.run(ctx, path, "update-server-info")
	return err
}

// RestoreRefs 回滚一次已经写入本地 bare repo/数据库 refs 的 push。
// 原行为在 receive-pack 成功但共享 Object 持久化失败时只返回 503，导致本地 refs
// 已经领先远端；用户再次执行 git push 会直接得到 Everything up-to-date，无法触发重试。
// 新行为在失败路径恢复本地 refs 和索引 refs，保留已写入的临时对象供 Git 自身复用，
// 让下一次正常 push 能重新进入 receive-pack 和共享对象同步。影响范围仅为当前仓库。
func (m *Manager) RestoreRefs(ctx context.Context, repo domain.Repository, snapshot map[string]string) error {
	unlock := m.lock(repo.ID)
	defer unlock()

	path := m.repoPath(repo)
	if _, err := os.Stat(filepath.Join(path, "HEAD")); err != nil {
		return fmt.Errorf("local repository is unavailable during ref rollback: %w", err)
	}
	output, err := m.run(ctx, path, "for-each-ref", "--format=%(refname) %(objectname)", "refs/heads", "refs/tags")
	if err != nil {
		return fmt.Errorf("snapshot refs for rollback: %w", err)
	}
	current := parseRefSnapshot(output)

	currentNames := make([]string, 0, len(current))
	for name := range current {
		currentNames = append(currentNames, name)
	}
	sort.Strings(currentNames)
	for _, name := range currentNames {
		if _, keep := snapshot[name]; keep {
			continue
		}
		if _, err := m.run(ctx, path, "update-ref", "-d", name); err != nil {
			return fmt.Errorf("delete ref %s during rollback: %w", name, err)
		}
	}

	snapshotNames := make([]string, 0, len(snapshot))
	for name := range snapshot {
		snapshotNames = append(snapshotNames, name)
	}
	sort.Strings(snapshotNames)
	for _, name := range snapshotNames {
		if err := ValidateRefName(name); err != nil || !validHash.MatchString(snapshot[name]) {
			return fmt.Errorf("invalid rollback ref %s", name)
		}
		if _, err := m.run(ctx, path, "update-ref", name, snapshot[name]); err != nil {
			return fmt.Errorf("restore ref %s: %w", name, err)
		}
	}
	if err := m.store.ReplaceRefs(ctx, repo.ID, refsFromSnapshot(snapshot)); err != nil {
		return fmt.Errorf("restore indexed refs: %w", err)
	}
	return nil
}

func refsFromSnapshot(snapshot map[string]string) []domain.Ref {
	names := make([]string, 0, len(snapshot))
	for name := range snapshot {
		names = append(names, name)
	}
	sort.Strings(names)
	refs := make([]domain.Ref, 0, len(names))
	for _, name := range names {
		refType := "OTHER"
		if strings.HasPrefix(name, "refs/heads/") {
			refType = "BRANCH"
		} else if strings.HasPrefix(name, "refs/tags/") {
			refType = "TAG"
		}
		refs = append(refs, domain.Ref{Name: name, ObjectHash: snapshot[name], Type: refType, UpdatedAt: time.Now().UTC()})
	}
	return refs
}

func parseRefSnapshot(output []byte) map[string]string {
	refs := map[string]string{}
	for _, line := range strings.Split(strings.TrimSpace(string(output)), "\n") {
		fields := strings.Fields(line)
		if len(fields) == 2 {
			refs[fields[0]] = fields[1]
		}
	}
	return refs
}

func (m *Manager) syncObjects(ctx context.Context, repo domain.Repository, path string) ([]domain.GitObject, error) {
	hashesOutput, err := m.run(ctx, path, "rev-list", "--objects", "--all")
	if err != nil {
		// Empty repositories have no refs and rev-list returns a non-zero status.
		if len(strings.TrimSpace(string(hashesOutput))) == 0 {
			return nil, nil
		}
		return nil, err
	}
	hashSet := map[string]struct{}{}
	var hashes []string
	for _, line := range strings.Split(strings.TrimSpace(string(hashesOutput)), "\n") {
		fields := strings.Fields(line)
		if len(fields) == 0 || !validHash.MatchString(fields[0]) {
			continue
		}
		if _, exists := hashSet[fields[0]]; !exists {
			hashSet[fields[0]] = struct{}{}
			hashes = append(hashes, fields[0])
		}
	}
	if len(hashes) == 0 {
		return nil, nil
	}
	metadata, err := m.objectMetadata(ctx, path, hashes)
	if err != nil {
		return nil, err
	}
	missing := make([]string, 0, len(hashes))
	objects := make([]domain.GitObject, 0, len(hashes))
	for _, hash := range hashes {
		meta := metadata[hash]
		objects = append(objects, domain.GitObject{
			Hash: hash, Type: meta.objectType, Size: meta.size,
			StoragePath: fmt.Sprintf("git/objects/%s/%s/%s", repo.HashAlgorithm, hash[:2], hash[2:]),
		})
		exists, err := m.storage.Exists(ctx, repo.HashAlgorithm, hash)
		if err != nil {
			return nil, err
		}
		if !exists {
			missing = append(missing, hash)
		}
	}
	if err := m.uploadMissingObjects(ctx, repo, path, missing); err != nil {
		return nil, err
	}
	return objects, nil
}

type objectMeta struct {
	objectType string
	size       int64
}

func (m *Manager) objectMetadata(ctx context.Context, path string, hashes []string) (map[string]objectMeta, error) {
	commandContext, cancel := context.WithTimeout(ctx, m.cfg.GitCommandTimeout)
	defer cancel()
	cmd := exec.CommandContext(commandContext, m.cfg.GitBinary, "--git-dir="+path, "cat-file", "--batch-check=%(objectname) %(objecttype) %(objectsize)")
	cmd.Stdin = strings.NewReader(strings.Join(hashes, "\n") + "\n")
	output, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("git cat-file batch-check: %w", err)
	}
	result := map[string]objectMeta{}
	for _, line := range strings.Split(strings.TrimSpace(string(output)), "\n") {
		fields := strings.Fields(line)
		if len(fields) != 3 {
			continue
		}
		size, err := strconv.ParseInt(fields[2], 10, 64)
		if err != nil || size < 0 || size > m.cfg.MaxObjectBytes {
			return nil, fmt.Errorf("invalid object size for %s", fields[0])
		}
		result[fields[0]] = objectMeta{objectType: fields[1], size: size}
	}
	return result, nil
}

func (m *Manager) uploadMissingObjects(ctx context.Context, repo domain.Repository, path string, hashes []string) error {
	if len(hashes) == 0 {
		return nil
	}
	commandContext, cancel := context.WithTimeout(ctx, m.cfg.GitCommandTimeout)
	defer cancel()
	cmd := exec.CommandContext(commandContext, m.cfg.GitBinary, "--git-dir="+path, "cat-file", "--batch")
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return err
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return err
	}
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	if err := cmd.Start(); err != nil {
		return err
	}
	go func() {
		for _, hash := range hashes {
			_, _ = io.WriteString(stdin, hash+"\n")
		}
		_ = stdin.Close()
	}()
	reader := bufio.NewReaderSize(stdout, 1024*1024)
	for _, expectedHash := range hashes {
		headerLine, err := reader.ReadString('\n')
		if err != nil {
			return fmt.Errorf("read cat-file header: %w", err)
		}
		fields := strings.Fields(headerLine)
		if len(fields) != 3 || fields[0] != expectedHash {
			return fmt.Errorf("unexpected cat-file response for %s", expectedHash)
		}
		size, err := strconv.ParseInt(fields[2], 10, 64)
		if err != nil || size < 0 || size > m.cfg.MaxObjectBytes {
			return fmt.Errorf("invalid Git object size")
		}
		temporary, err := os.CreateTemp(m.cfg.RepoRoot, "pcd-object-*.z")
		if err != nil {
			return err
		}
		temporaryName := temporary.Name()
		zlibWriter := zlib.NewWriter(temporary)
		canonicalHeader := []byte(fmt.Sprintf("%s %d\x00", fields[1], size))
		hasher, err := objectHasher(repo.HashAlgorithm)
		if err != nil {
			zlibWriter.Close()
			temporary.Close()
			os.Remove(temporaryName)
			return err
		}
		_, _ = hasher.Write(canonicalHeader)
		_, _ = zlibWriter.Write(canonicalHeader)
		if _, err := io.CopyN(io.MultiWriter(zlibWriter, hasher), reader, size); err != nil {
			zlibWriter.Close()
			temporary.Close()
			os.Remove(temporaryName)
			return err
		}
		separator, err := reader.ReadByte()
		if err != nil || separator != '\n' {
			zlibWriter.Close()
			temporary.Close()
			os.Remove(temporaryName)
			return errors.New("invalid cat-file object separator")
		}
		if err := zlibWriter.Close(); err != nil {
			temporary.Close()
			os.Remove(temporaryName)
			return err
		}
		if err := temporary.Close(); err != nil {
			os.Remove(temporaryName)
			return err
		}
		calculated := hex.EncodeToString(hasher.Sum(nil))
		if calculated != expectedHash {
			os.Remove(temporaryName)
			return fmt.Errorf("canonical hash mismatch for %s", expectedHash)
		}
		if err := m.storage.PutFile(ctx, repo.HashAlgorithm, expectedHash, temporaryName); err != nil {
			os.Remove(temporaryName)
			return err
		}
		os.Remove(temporaryName)
	}
	if err := cmd.Wait(); err != nil {
		return fmt.Errorf("git cat-file failed: %s: %w", strings.TrimSpace(stderr.String()), err)
	}
	return nil
}

func objectHasher(algorithm string) (interface {
	io.Writer
	Sum([]byte) []byte
}, error) {
	switch algorithm {
	case "sha1":
		return sha1.New(), nil
	case "sha256":
		return sha256.New(), nil
	default:
		return nil, fmt.Errorf("unsupported object algorithm %q", algorithm)
	}
}

func (m *Manager) writeProtectionHook(ctx context.Context, repo domain.Repository) error {
	patterns, err := m.store.ListProtectedPatterns(ctx, repo.ID)
	if err != nil {
		return err
	}
	path := m.repoPath(repo)
	hooksDir := filepath.Join(path, "hooks")
	if err := os.MkdirAll(hooksDir, 0o750); err != nil {
		return err
	}
	patternsFile := filepath.Join(path, "pcd-protected-refs")
	if err := os.WriteFile(patternsFile, []byte(strings.Join(patterns, "\n")+"\n"), 0o640); err != nil {
		return err
	}
	// Hook 不调用 shell 拼接用户输入；模式来自管理员 API 并经 ref 校验。
	hook := `#!/bin/sh
set -eu
patterns_file="$(git rev-parse --git-dir)/pcd-protected-refs"
[ -s "$patterns_file" ] || exit 0
while read old_sha new_sha ref_name; do
  while IFS= read -r pattern; do
    [ -n "$pattern" ] || continue
    case "$ref_name" in
      $pattern) echo "Protected branch requires merge request: $ref_name" >&2; exit 1 ;;
    esac
  done < "$patterns_file"
done
`
	return os.WriteFile(filepath.Join(hooksDir, "pre-receive"), []byte(hook), 0o750)
}

func (m *Manager) RefreshProtectionHook(ctx context.Context, repo domain.Repository) error {
	if _, err := m.EnsureLocal(ctx, repo); err != nil {
		return err
	}
	return m.writeProtectionHook(ctx, repo)
}

func ValidateRefName(name string) error {
	if !validRefComponent.MatchString(name) || strings.Contains(name, "..") || strings.Contains(name, "//") ||
		strings.HasPrefix(name, "/") || strings.HasSuffix(name, "/") || strings.HasSuffix(name, ".lock") {
		return errors.New("invalid Git ref name")
	}
	return nil
}

func (m *Manager) run(ctx context.Context, gitDir string, args ...string) ([]byte, error) {
	return m.runWithOutputLimit(ctx, gitDir, m.cfg.MaxAPIOutputBytes, args...)
}

// runWithOutputLimit 允许下载/预览类只读操作使用与 JSON 管理接口不同的输出上限。
// 原 run 固定 4 MiB 会使合法图片和 PDF 无法预览；
// 新方法不放宽写入和协议对象限制，只为受权限保护的单文件读取提供受控上限。
func (m *Manager) runWithOutputLimit(ctx context.Context, gitDir string, outputLimit int64, args ...string) ([]byte, error) {
	commandContext, cancel := context.WithTimeout(ctx, m.cfg.GitCommandTimeout)
	defer cancel()
	actualArgs := args
	if gitDir != "" {
		actualArgs = append([]string{"--git-dir=" + gitDir}, args...)
	}
	cmd := exec.CommandContext(commandContext, m.cfg.GitBinary, actualArgs...)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &limitedWriter{writer: &stdout, remaining: outputLimit}
	cmd.Stderr = &limitedWriter{writer: &stderr, remaining: 256 * 1024}
	err := cmd.Run()
	if commandContext.Err() != nil {
		return stdout.Bytes(), commandContext.Err()
	}
	if err != nil {
		return stdout.Bytes(), fmt.Errorf("git %s: %s: %w", strings.Join(args, " "), strings.TrimSpace(stderr.String()), err)
	}
	return stdout.Bytes(), nil
}

type limitedWriter struct {
	writer    io.Writer
	remaining int64
}

func (w *limitedWriter) Write(data []byte) (int, error) {
	if int64(len(data)) > w.remaining {
		return 0, errors.New("Git command output exceeds configured limit")
	}
	w.remaining -= int64(len(data))
	return w.writer.Write(data)
}
