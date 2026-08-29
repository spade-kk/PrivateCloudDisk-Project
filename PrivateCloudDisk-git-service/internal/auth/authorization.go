package auth

import (
	"context"
	"errors"
	"fmt"

	"privateclouddisk/git-service/internal/domain"
	"privateclouddisk/git-service/internal/platform"
	"privateclouddisk/git-service/internal/store"
)

type Operation string

const (
	Metadata Operation = "METADATA"
	Fetch    Operation = "FETCH"
	Push     Operation = "PUSH"
	Admin    Operation = "ADMIN"
)

type Authorizer struct {
	platform *platform.Client
	store    *store.Store
}

func NewAuthorizer(platformClient *platform.Client, dataStore *store.Store) *Authorizer {
	return &Authorizer{platform: platformClient, store: dataStore}
}

// Require 合并空间级、仓库级和 Git 资源可见性，始终以 Platform 的 active/resource_type 为总闸门。
// [REQ-GIT-AUDIT-4.1~4.25] 原行为把 allow_public_upload 作为任意认证用户的 Push 许可，
// 会使拥有 PAT 但没有仓库 WRITE 的用户横向越权；新行为只有空间/仓库授予的 WRITE 才可
// 推送。PUBLIC/HIDDEN/PRIVATE 均属于 public resource_type=git 的空间资源，不映射为私人空间。
// 影响范围为 Git HTTP/SSH 协议与管理 API 的授权判断，不改变普通文件公开上传语义。
func (a *Authorizer) Require(ctx context.Context, repo domain.Repository, userID string, operation Operation) (domain.SpaceAuthorization, error) {
	space, err := a.platform.Authorization(ctx, repo.SpaceID, userID)
	if err != nil {
		return space, err
	}
	if !space.Active || space.SpaceID != repo.SpaceID || space.ResourceType != "git" {
		return space, errors.New("space is not an active Git resource")
	}
	level := space.PermissionLevel
	if userID != "" {
		repoLevel, err := a.store.GetPermissionLevel(ctx, repo.ID, userID)
		if err != nil {
			return space, err
		}
		level = maxLevel(level, repoLevel)
		teamPermissions, err := a.store.ListTeamPermissions(ctx, repo.ID)
		if err != nil {
			return space, err
		}
		for _, permission := range teamPermissions {
			membership, err := a.platform.TeamMembership(ctx, permission.SubjectID, userID)
			if err != nil {
				// 团队成员事实源不可用时安全失败，不能把 TEAM 授权误判为匿名/公开权限。
				return space, err
			}
			if membership.Member {
				level = maxLevel(level, permission.Level)
			}
		}
		if userID == repo.OwnerID || userID == space.OwnerID {
			level = domain.PermissionAdmin
		}
	}

	memberRead := userID != "" && atLeast(level, domain.PermissionRead)
	publicReadable := repositoryVisibility(repo) == domain.RepositoryVisibilityPublic &&
		space.AllowPublicBrowse && space.AllowPublicDownload
	allowed := false
	switch operation {
	case Metadata:
		allowed = (repositoryVisibility(repo) == domain.RepositoryVisibilityPublic && space.AllowPublicBrowse) || memberRead
	case Fetch:
		allowed = publicReadable || memberRead
	case Push:
		allowed = userID != "" && atLeast(level, domain.PermissionWrite)
	case Admin:
		allowed = userID != "" && atLeast(level, domain.PermissionAdmin)
	}
	if !allowed {
		return space, fmt.Errorf("permission denied for %s", operation)
	}
	return space, nil
}

func repositoryVisibility(repo domain.Repository) string {
	if repo.Visibility == "" {
		// 兼容 V2 迁移前由旧服务创建的行；数据库迁移完成后会显式写入 PUBLIC。
		return domain.RepositoryVisibilityPublic
	}
	return repo.Visibility
}

// IsConcealed 用于协议层统一执行“未认证 401、认证但无权 404”的隐藏仓库策略，
// 避免通过状态差异泄露 HIDDEN/PRIVATE Git 资源的存在。
func IsConcealed(repo domain.Repository) bool {
	visibility := repositoryVisibility(repo)
	return visibility == domain.RepositoryVisibilityHidden || visibility == domain.RepositoryVisibilityPrivate
}

func atLeast(actual, expected string) bool { return rank(actual) >= rank(expected) }

func maxLevel(left, right string) string {
	if rank(right) > rank(left) {
		return right
	}
	return left
}

func rank(level string) int {
	switch level {
	case domain.PermissionAdmin:
		return 3
	case domain.PermissionWrite:
		return 2
	case domain.PermissionRead:
		return 1
	default:
		return 0
	}
}
