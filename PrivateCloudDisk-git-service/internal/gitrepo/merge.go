package gitrepo

import (
	"context"
	"errors"
	"os"
	"os/exec"
	"strings"

	"privateclouddisk/git-service/internal/domain"
)

// Merge 使用 Git 自带 merge-base/merge-tree/commit-tree，避免自行实现易错的三方合并。
// [REQ-GIT-MR-8.3] 支持 fast-forward 和显式双亲 merge commit；冲突时不更新目标 ref。
func (m *Manager) Merge(ctx context.Context, repo domain.Repository, source, target, strategy, actor string) (string, error) {
	if err := ValidateRefName(source); err != nil {
		return "", err
	}
	if err := ValidateRefName(target); err != nil {
		return "", err
	}
	repoPath, err := m.EnsureLocal(ctx, repo)
	if err != nil {
		return "", err
	}
	sourceHash, err := m.run(ctx, repoPath, "rev-parse", "--verify", "refs/heads/"+source+"^{commit}")
	if err != nil {
		return "", err
	}
	targetHash, err := m.run(ctx, repoPath, "rev-parse", "--verify", "refs/heads/"+target+"^{commit}")
	if err != nil {
		return "", err
	}
	sourceCommit := strings.TrimSpace(string(sourceHash))
	targetCommit := strings.TrimSpace(string(targetHash))

	commandContext, cancel := context.WithTimeout(ctx, m.cfg.GitCommandTimeout)
	defer cancel()
	ancestor := exec.CommandContext(commandContext, m.cfg.GitBinary, "--git-dir="+repoPath,
		"merge-base", "--is-ancestor", targetCommit, sourceCommit).Run() == nil
	if ancestor || strategy == "FAST_FORWARD" {
		if !ancestor {
			return "", errors.New("merge is not fast-forward")
		}
		if _, err := m.run(ctx, repoPath, "update-ref", "refs/heads/"+target, sourceCommit, targetCommit); err != nil {
			return "", err
		}
		return sourceCommit, nil
	}

	tree, err := m.run(ctx, repoPath, "merge-tree", "--write-tree", targetCommit, sourceCommit)
	if err != nil {
		return "", errors.New("merge conflict: " + err.Error())
	}
	treeHash := strings.Fields(string(tree))
	if len(treeHash) == 0 {
		return "", errors.New("merge-tree did not produce a tree")
	}
	cmd := exec.CommandContext(ctx, m.cfg.GitBinary, "--git-dir="+repoPath, "commit-tree", treeHash[0],
		"-p", targetCommit, "-p", sourceCommit, "-m", "Merge branch '"+source+"' into '"+target+"'")
	cmd.Env = append(os.Environ(),
		"GIT_AUTHOR_NAME="+actor, "GIT_AUTHOR_EMAIL=git-service@privateclouddisk.local",
		"GIT_COMMITTER_NAME="+actor, "GIT_COMMITTER_EMAIL=git-service@privateclouddisk.local")
	commitOutput, err := cmd.Output()
	if err != nil {
		return "", err
	}
	mergeCommit := strings.TrimSpace(string(commitOutput))
	if _, err := m.run(ctx, repoPath, "update-ref", "refs/heads/"+target, mergeCommit, targetCommit); err != nil {
		return "", err
	}
	return mergeCommit, nil
}
