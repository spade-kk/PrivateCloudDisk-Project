package gitrepo

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"mime"
	"os/exec"
	"path"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"privateclouddisk/git-service/internal/domain"
)

const (
	// [REQ-GIT-UIUX-20260816] 浏览器代码阅读与管理 API 不应吞掉超大文本：超过 2 MiB
	// 只传输前缀，且最多渲染 5000 行；原始下载仍走 Fetch 权限的流式 raw 端点。
	browserPreviewBytes int64 = 2 * 1024 * 1024
	browserPreviewLines       = 5000
)

func validateTreePath(value string) error {
	if value == "" {
		return nil
	}
	cleaned := path.Clean(value)
	if cleaned != value || strings.HasPrefix(cleaned, "/") || cleaned == ".." || strings.HasPrefix(cleaned, "../") || strings.ContainsRune(value, 0) {
		return errors.New("invalid repository path")
	}
	return nil
}

func (m *Manager) ListTree(ctx context.Context, repo domain.Repository, ref, treePath string) ([]domain.TreeEntry, error) {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return nil, err
	}
	if ref == "" {
		ref = repo.DefaultBranch
	}
	if err := ValidateRefName(ref); err != nil {
		return nil, err
	}
	if err := validateTreePath(treePath); err != nil {
		return nil, err
	}
	treeish := ref
	if treePath != "" {
		treeish += ":" + treePath
	}
	output, err := m.run(ctx, repoPath, "ls-tree", "-z", "-l", treeish)
	if err != nil {
		return nil, err
	}
	var entries []domain.TreeEntry
	for _, record := range strings.Split(string(output), "\x00") {
		if record == "" {
			continue
		}
		metadata, name, found := strings.Cut(record, "\t")
		if !found {
			continue
		}
		fields := strings.Fields(metadata)
		if len(fields) != 4 {
			continue
		}
		size, _ := strconv.ParseInt(fields[3], 10, 64)
		entryPath := name
		if treePath != "" {
			entryPath = treePath + "/" + name
		}
		entries = append(entries, domain.TreeEntry{Mode: fields[0], Type: fields[1], Hash: fields[2], Size: size, Name: name, Path: entryPath})
	}
	return entries, nil
}

func (m *Manager) ReadBlob(ctx context.Context, repo domain.Repository, ref, blobPath string) ([]byte, error) {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return nil, err
	}
	if ref == "" {
		ref = repo.DefaultBranch
	}
	if err := ValidateRefName(ref); err != nil {
		return nil, err
	}
	if err := validateTreePath(blobPath); err != nil || blobPath == "" {
		return nil, errors.New("invalid blob path")
	}
	return m.run(ctx, repoPath, "show", ref+":"+blobPath)
}

// ReadBlobPreview 返回可安全嵌入 JSON 的文本预览与文件元数据。
// [REQ-GIT-UIUX-20260816] 保留原 ReadBlob 供 README 等内部文本流程使用；
// 新浏览器接口先读取 object size，再决定是否返回文本，防止把二进制或超限内容塞进 JSON。
func (m *Manager) ReadBlobPreview(ctx context.Context, repo domain.Repository, ref, blobPath string) (domain.BlobPreview, error) {
	repoPath, normalizedRef, err := m.resolveBlob(ctx, repo, ref, blobPath)
	if err != nil {
		return domain.BlobPreview{}, err
	}
	size, err := m.blobSize(ctx, repoPath, normalizedRef, blobPath)
	if err != nil {
		return domain.BlobPreview{}, err
	}
	preview := domain.BlobPreview{
		Path:     blobPath,
		Size:     size,
		MimeType: mimeTypeForPath(blobPath),
	}
	previewLimit := m.cfg.MaxAPIOutputBytes
	if previewLimit > browserPreviewBytes {
		previewLimit = browserPreviewBytes
	}
	if previewLimit < 1 {
		return domain.BlobPreview{}, errors.New("invalid blob preview limit")
	}
	content, err := m.readBlobPrefix(ctx, repoPath, normalizedRef, blobPath, previewLimit)
	if err != nil {
		return domain.BlobPreview{}, err
	}
	preview.Truncated = size > previewLimit
	preview.IsBinary = bytes.IndexByte(content, 0) >= 0 || !utf8.Valid(content) || (!isTextPreviewMIME(preview.MimeType) && !isLikelyTextPath(blobPath))
	if preview.IsBinary {
		// 二进制内容由 raw 流端点处理；预览字节上限不应阻断大图片、PDF 或媒体文件。
		preview.Truncated = false
		return preview, nil
	}
	content, lineTruncated := truncatePreviewLines(content, browserPreviewLines)
	preview.Truncated = preview.Truncated || lineTruncated
	preview.Content = string(content)
	if len(content) > 0 {
		preview.LineCount = bytes.Count(content, []byte("\n")) + 1
	}
	return preview, nil
}

func (m *Manager) readBlobPrefix(ctx context.Context, repoPath, ref, blobPath string, limit int64) ([]byte, error) {
	commandContext, cancel := context.WithTimeout(ctx, m.cfg.GitCommandTimeout)
	defer cancel()
	command := exec.CommandContext(commandContext, m.cfg.GitBinary, "--git-dir="+repoPath, "show", ref+":"+blobPath)
	stdout, err := command.StdoutPipe()
	if err != nil {
		return nil, err
	}
	var stderr bytes.Buffer
	command.Stderr = &limitedWriter{writer: &stderr, remaining: 256 * 1024}
	if err := command.Start(); err != nil {
		return nil, err
	}
	content, readErr := io.ReadAll(io.LimitReader(stdout, limit))
	// 即使浏览器预览达到上限，也要继续消费 stdout，避免 git show 在管道满时阻塞。
	_, drainErr := io.Copy(io.Discard, stdout)
	waitErr := command.Wait()
	if commandContext.Err() != nil {
		return nil, commandContext.Err()
	}
	if readErr != nil {
		return nil, readErr
	}
	if drainErr != nil {
		return nil, drainErr
	}
	if waitErr != nil {
		return nil, fmt.Errorf("git show blob preview: %s: %w", strings.TrimSpace(stderr.String()), waitErr)
	}
	return content, nil
}

func truncatePreviewLines(content []byte, maximum int) ([]byte, bool) {
	if maximum < 1 {
		return nil, len(content) > 0
	}
	lineCount := 0
	for index, value := range content {
		if value != '\n' {
			continue
		}
		lineCount++
		if lineCount >= maximum {
			return content[:index+1], index+1 < len(content)
		}
	}
	return content, false
}

func isTextPreviewMIME(value string) bool {
	base := strings.TrimSpace(strings.Split(value, ";")[0])
	if strings.HasPrefix(base, "text/") {
		return true
	}
	switch base {
	case "application/json", "application/javascript", "application/xml", "application/x-yaml", "application/sql":
		return true
	default:
		return false
	}
}

func isLikelyTextPath(blobPath string) bool {
	switch strings.ToLower(filepath.Ext(blobPath)) {
	case ".go", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".vue", ".java", ".kt", ".rs", ".py", ".rb", ".php", ".cs", ".c", ".h", ".cpp", ".cc", ".hpp", ".swift", ".sh", ".bash", ".zsh", ".fish", ".ps1", ".sql", ".html", ".htm", ".css", ".scss", ".sass", ".less", ".xml", ".yml", ".yaml", ".toml", ".ini", ".cfg", ".conf", ".md", ".mdx", ".txt", ".csv", ".log", ".properties", ".gradle":
		return true
	default:
		return false
	}
}

// RawBlobSize 返回原始对象的大小并校验下载上限；调用方必须已完成 Fetch 权限校验。
func (m *Manager) RawBlobSize(ctx context.Context, repo domain.Repository, ref, blobPath string) (int64, error) {
	repoPath, normalizedRef, err := m.resolveBlob(ctx, repo, ref, blobPath)
	if err != nil {
		return 0, err
	}
	size, err := m.blobSize(ctx, repoPath, normalizedRef, blobPath)
	if err != nil {
		return 0, err
	}
	if size > m.cfg.MaxRawFileBytes {
		return 0, fmt.Errorf("raw file exceeds configured limit (%d bytes)", m.cfg.MaxRawFileBytes)
	}
	return size, nil
}

// StreamRawBlob 直接将 Git Blob 写入调用方，避免图片、PDF 和大文件在 Git Service 内存聚合。
// [REQ-GIT-UIUX-20260816] 原行为先返回 []byte 再由 API 写响应；新行为由调用方先使用
// RawBlobSize 设置 Content-Length，随后将 git show 的 stdout 直通 HTTP 响应，影响范围仅 raw 端点。
func (m *Manager) StreamRawBlob(ctx context.Context, repo domain.Repository, ref, blobPath string, output io.Writer) error {
	repoPath, normalizedRef, err := m.resolveBlob(ctx, repo, ref, blobPath)
	if err != nil {
		return err
	}
	commandContext, cancel := context.WithTimeout(ctx, m.cfg.GitCommandTimeout)
	defer cancel()
	command := exec.CommandContext(commandContext, m.cfg.GitBinary, "--git-dir="+repoPath, "show", normalizedRef+":"+blobPath)
	var stderr bytes.Buffer
	command.Stdout = output
	command.Stderr = &limitedWriter{writer: &stderr, remaining: 256 * 1024}
	if err := command.Run(); err != nil {
		if commandContext.Err() != nil {
			return commandContext.Err()
		}
		return fmt.Errorf("git show raw blob: %s: %w", strings.TrimSpace(stderr.String()), err)
	}
	return nil
}

// Archive 将指定引用以 ZIP 形式流式输出，不经 JSON 或内存聚合。
// [REQ-GIT-UIUX-20260816] GitHub 式“下载 ZIP”只复用 Git 原生 archive，
// 不遍历文件服务目录，因而保持 Git Object 和普通文件空间的资源边界。
func (m *Manager) Archive(ctx context.Context, repo domain.Repository, ref string, output io.Writer) error {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return err
	}
	if ref == "" {
		ref = repo.DefaultBranch
	}
	if err := ValidateRefName(ref); err != nil {
		return err
	}
	// [REQ-GIT-UIUX-20260816] 先验证引用，再开始写 ZIP 响应体。原行为在无效引用时
	// 可能已写入 HTTP 200 的部分流；新行为让 API 可以返回可解析的 4xx 错误，便于客户端重试。
	if _, err := m.run(ctx, repoPath, "rev-parse", "--verify", ref+"^{commit}"); err != nil {
		return fmt.Errorf("invalid archive ref: %w", err)
	}
	commandContext, cancel := context.WithTimeout(ctx, m.cfg.GitCommandTimeout)
	defer cancel()
	command := exec.CommandContext(commandContext, m.cfg.GitBinary,
		"--git-dir="+repoPath, "archive", "--format=zip", "--prefix="+repo.Slug+"/", ref)
	var stderr bytes.Buffer
	command.Stdout = output
	command.Stderr = &limitedWriter{writer: &stderr, remaining: 256 * 1024}
	if err := command.Run(); err != nil {
		if commandContext.Err() != nil {
			return commandContext.Err()
		}
		return fmt.Errorf("git archive: %s: %w", strings.TrimSpace(stderr.String()), err)
	}
	return nil
}

func (m *Manager) resolveBlob(ctx context.Context, repo domain.Repository, ref, blobPath string) (string, string, error) {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return "", "", err
	}
	if ref == "" {
		ref = repo.DefaultBranch
	}
	if err := ValidateRefName(ref); err != nil {
		return "", "", err
	}
	if err := validateTreePath(blobPath); err != nil || blobPath == "" {
		return "", "", errors.New("invalid blob path")
	}
	return repoPath, ref, nil
}

func (m *Manager) blobSize(ctx context.Context, repoPath, ref, blobPath string) (int64, error) {
	output, err := m.run(ctx, repoPath, "cat-file", "-s", ref+":"+blobPath)
	if err != nil {
		return 0, err
	}
	size, err := strconv.ParseInt(strings.TrimSpace(string(output)), 10, 64)
	if err != nil || size < 0 {
		return 0, errors.New("invalid Git blob size")
	}
	return size, nil
}

func mimeTypeForPath(blobPath string) string {
	if value := mime.TypeByExtension(filepath.Ext(blobPath)); value != "" {
		return value
	}
	return "application/octet-stream"
}

func (m *Manager) ReadREADME(ctx context.Context, repo domain.Repository, ref string) (string, string, error) {
	for _, candidate := range []string{"README.md", "README.MD", "README.markdown", "README", "readme.md"} {
		content, err := m.ReadBlob(ctx, repo, ref, candidate)
		if err == nil {
			return candidate, string(content), nil
		}
	}
	return "", "", nil
}

func (m *Manager) ListCommits(ctx context.Context, repo domain.Repository, ref, author, since, until, filePath string, allRefs bool, page, size int) ([]domain.Commit, error) {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return nil, err
	}
	if !allRefs {
		if ref == "" {
			ref = repo.DefaultBranch
		}
		if err := ValidateRefName(ref); err != nil {
			return nil, err
		}
	}
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 100 {
		size = 30
	}
	args := []string{"log"}
	if allRefs {
		args = append(args, "--all")
	} else {
		args = append(args, ref)
	}
	args = append(args, fmt.Sprintf("--max-count=%d", size), fmt.Sprintf("--skip=%d", (page-1)*size),
		"--date=iso-strict", "--format=%H%x1f%T%x1f%P%x1f%an%x1f%ae%x1f%aI%x1f%cn%x1f%cI%x1f%s%x1f%B%x1e")
	if author != "" {
		args = append(args, "--author="+author)
	}
	if since != "" {
		args = append(args, "--since="+since)
	}
	if until != "" {
		args = append(args, "--until="+until)
	}
	if filePath != "" {
		if err := validateTreePath(filePath); err != nil {
			return nil, err
		}
		args = append(args, "--", filePath)
	}
	output, err := m.run(ctx, repoPath, args...)
	if err != nil {
		return nil, err
	}
	return parseCommits(output), nil
}

func parseCommits(output []byte) []domain.Commit {
	var commits []domain.Commit
	for _, record := range strings.Split(string(output), "\x1e") {
		record = strings.Trim(record, "\n")
		if record == "" {
			continue
		}
		fields := strings.SplitN(record, "\x1f", 10)
		if len(fields) != 10 {
			continue
		}
		authoredAt, _ := time.Parse(time.RFC3339, strings.TrimSpace(fields[5]))
		committedAt, _ := time.Parse(time.RFC3339, strings.TrimSpace(fields[7]))
		parents := strings.Fields(fields[2])
		commits = append(commits, domain.Commit{
			Hash: strings.TrimSpace(fields[0]), TreeHash: strings.TrimSpace(fields[1]), Parents: parents,
			AuthorName: fields[3], AuthorEmail: fields[4], AuthoredAt: authoredAt,
			Committer: fields[6], CommittedAt: committedAt, Subject: fields[8], Message: strings.TrimSpace(fields[9]),
		})
	}
	return commits
}

func (m *Manager) SyncCommitIndex(ctx context.Context, repo domain.Repository) error {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return err
	}
	output, err := m.run(ctx, repoPath, "log", "--all", "--max-count=10000", "--date=iso-strict",
		"--format=%H%x1f%T%x1f%P%x1f%an%x1f%ae%x1f%aI%x1f%cn%x1f%cI%x1f%s%x1f%B%x1e")
	if err != nil {
		// Empty repository has no commit index to maintain.
		return m.store.ReplaceCommitIndex(ctx, repo.ID, nil)
	}
	return m.store.ReplaceCommitIndex(ctx, repo.ID, parseCommits(output))
}

func (m *Manager) Diff(ctx context.Context, repo domain.Repository, from, to, filePath string) (string, error) {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return "", err
	}
	if err := ValidateRefName(from); err != nil {
		return "", err
	}
	if err := ValidateRefName(to); err != nil {
		return "", err
	}
	args := []string{"diff", "--no-color", "--unified=3", from, to}
	if filePath != "" {
		if err := validateTreePath(filePath); err != nil {
			return "", err
		}
		args = append(args, "--", filePath)
	}
	output, err := m.run(ctx, repoPath, args...)
	return string(output), err
}

func (m *Manager) Blame(ctx context.Context, repo domain.Repository, ref, filePath string) (string, error) {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return "", err
	}
	if ref == "" {
		ref = repo.DefaultBranch
	}
	if err := ValidateRefName(ref); err != nil {
		return "", err
	}
	if err := validateTreePath(filePath); err != nil || filePath == "" {
		return "", errors.New("invalid blame path")
	}
	output, err := m.run(ctx, repoPath, "blame", "--line-porcelain", ref, "--", filePath)
	return string(output), err
}

// Insights 从 Git 引用和对象树派生仓库展示统计。
// [REQ-GIT-UIUX-20260816] 前端此前只有 objectCount/objectBytes，无法可靠展示提交、贡献者、
// 分支、标签与语言分布；新行为只读 Git 本地缓存和索引，不伪造 Star/Fork 或写入统计快照。
func (m *Manager) Insights(ctx context.Context, repo domain.Repository) (domain.RepositoryInsights, error) {
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return domain.RepositoryInsights{}, err
	}
	result := domain.RepositoryInsights{Languages: []domain.LanguageStat{}, Contributors: []domain.ContributorStat{}, Contributions: []domain.ContributionDay{}}
	refs, err := m.store.ListRefs(ctx, repo.ID, "")
	if err != nil {
		return result, err
	}
	for _, ref := range refs {
		switch ref.Type {
		case "BRANCH":
			result.BranchCount++
		case "TAG":
			result.TagCount++
		}
	}
	if output, err := m.run(ctx, repoPath, "rev-list", "--count", "--all"); err == nil {
		result.CommitCount, _ = strconv.ParseInt(strings.TrimSpace(string(output)), 10, 64)
	}
	if output, err := m.run(ctx, repoPath, "shortlog", "-sne", "--all"); err == nil {
		result.Contributors = parseContributorStats(output)
		result.ContributorCount = len(result.Contributors)
	}
	if output, err := m.run(ctx, repoPath, "log", "--all", "--format=%ad", "--date=short"); err == nil {
		counts := map[string]int64{}
		cutoff := time.Now().AddDate(0, -12, 0).Format("2006-01-02")
		for _, value := range strings.Split(strings.TrimSpace(string(output)), "\n") {
			value = strings.TrimSpace(value)
			if value != "" && value >= cutoff {
				counts[value]++
			}
		}
		for date, count := range counts {
			result.Contributions = append(result.Contributions, domain.ContributionDay{Date: date, Count: count})
		}
		sort.Slice(result.Contributions, func(left, right int) bool { return result.Contributions[left].Date < result.Contributions[right].Date })
	}
	if result.CommitCount == 0 {
		return result, nil
	}
	if output, err := m.runWithOutputLimit(ctx, repoPath, m.cfg.MaxRawFileBytes, "ls-tree", "-r", "-l", repo.DefaultBranch); err == nil {
		result.Languages = parseLanguageStats(output)
	}
	return result, nil
}

func parseContributorStats(output []byte) []domain.ContributorStat {
	items := make([]domain.ContributorStat, 0)
	for _, line := range strings.Split(string(output), "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		countText, identity, found := strings.Cut(line, "\t")
		if !found {
			continue
		}
		count, err := strconv.ParseInt(strings.TrimSpace(countText), 10, 64)
		if err != nil {
			continue
		}
		name, email := identity, ""
		if start := strings.LastIndex(identity, " <"); start >= 0 && strings.HasSuffix(identity, ">") {
			name = identity[:start]
			email = strings.TrimSuffix(identity[start+2:], ">")
		}
		items = append(items, domain.ContributorStat{Name: name, Email: email, Commits: count})
	}
	return items
}

func parseLanguageStats(output []byte) []domain.LanguageStat {
	bytesByLanguage := map[string]int64{}
	for _, line := range strings.Split(string(output), "\n") {
		metadata, fileName, found := strings.Cut(line, "\t")
		if !found {
			continue
		}
		fields := strings.Fields(metadata)
		if len(fields) != 4 || fields[1] != "blob" {
			continue
		}
		size, err := strconv.ParseInt(fields[3], 10, 64)
		if err != nil {
			continue
		}
		if language := languageForPath(fileName); language != "" {
			bytesByLanguage[language] += size
		}
	}
	items := make([]domain.LanguageStat, 0, len(bytesByLanguage))
	for name, size := range bytesByLanguage {
		items = append(items, domain.LanguageStat{Name: name, Bytes: size})
	}
	sort.Slice(items, func(left, right int) bool {
		if items[left].Bytes == items[right].Bytes {
			return items[left].Name < items[right].Name
		}
		return items[left].Bytes > items[right].Bytes
	})
	return items
}

func languageForPath(fileName string) string {
	switch strings.ToLower(filepath.Ext(fileName)) {
	case ".go":
		return "Go"
	case ".ts", ".tsx":
		return "TypeScript"
	case ".js", ".jsx", ".mjs", ".cjs":
		return "JavaScript"
	case ".vue":
		return "Vue"
	case ".java":
		return "Java"
	case ".py":
		return "Python"
	case ".rs":
		return "Rust"
	case ".rb":
		return "Ruby"
	case ".php":
		return "PHP"
	case ".cs":
		return "C#"
	case ".c", ".h":
		return "C"
	case ".cc", ".cpp", ".cxx", ".hpp":
		return "C++"
	case ".json":
		return "JSON"
	case ".yml", ".yaml":
		return "YAML"
	case ".sql":
		return "SQL"
	case ".sh", ".bash", ".zsh":
		return "Shell"
	case ".html", ".htm":
		return "HTML"
	case ".css", ".scss", ".sass", ".less":
		return "CSS"
	case ".md", ".mdx":
		return "Markdown"
	default:
		return ""
	}
}

func (m *Manager) CreateBranch(ctx context.Context, repo domain.Repository, name, startPoint string) error {
	if err := ValidateRefName(name); err != nil {
		return err
	}
	if startPoint == "" {
		startPoint = repo.DefaultBranch
	}
	if err := ValidateRefName(startPoint); err != nil {
		return err
	}
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return err
	}
	hash, err := m.run(ctx, repoPath, "rev-parse", "--verify", startPoint+"^{commit}")
	if err != nil {
		return err
	}
	_, err = m.run(ctx, repoPath, "update-ref", "refs/heads/"+name, strings.TrimSpace(string(hash)), strings.Repeat("0", 40))
	return err
}

func (m *Manager) DeleteBranch(ctx context.Context, repo domain.Repository, name string) error {
	if name == repo.DefaultBranch {
		return errors.New("default branch cannot be deleted")
	}
	if err := ValidateRefName(name); err != nil {
		return err
	}
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return err
	}
	_, err = m.run(ctx, repoPath, "update-ref", "-d", "refs/heads/"+name)
	return err
}

func (m *Manager) CreateTag(ctx context.Context, repo domain.Repository, name, target, message, tagger string) error {
	if err := ValidateRefName(name); err != nil {
		return err
	}
	if target == "" {
		target = repo.DefaultBranch
	}
	if err := ValidateRefName(target); err != nil {
		return err
	}
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return err
	}
	args := []string{"tag"}
	if message != "" {
		args = append(args, "-a", name, "-m", message)
	} else {
		args = append(args, name)
	}
	args = append(args, target)
	commandContext, cancel := context.WithTimeout(ctx, m.cfg.GitCommandTimeout)
	defer cancel()
	_ = tagger // Tag identity is configured by service-level default for deterministic automation.
	output, err := m.run(commandContext, repoPath, args...)
	if err != nil {
		return fmt.Errorf("create tag: %s: %w", strings.TrimSpace(string(output)), err)
	}
	return nil
}

func (m *Manager) DeleteTag(ctx context.Context, repo domain.Repository, name string) error {
	if err := ValidateRefName(name); err != nil {
		return err
	}
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return err
	}
	_, err = m.run(ctx, repoPath, "update-ref", "-d", "refs/tags/"+name)
	return err
}
