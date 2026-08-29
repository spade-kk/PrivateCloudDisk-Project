package domain

import "time"

const (
	PermissionNone  = "NONE"
	PermissionRead  = "READ"
	PermissionWrite = "WRITE"
	PermissionAdmin = "ADMIN"

	// RepositoryVisibility 是 Git 资源自身的可见性，不能用空间类型替代。
	// [REQ-GIT-AUDIT-4.1/4.2] 原行为只借用公开空间的 browse/download 开关，无法区分
	// 可被匿名发现的公开仓库与仅允许已授权成员按地址访问的隐藏/私密仓库；新行为保持
	// Space 仍为 resource_type=git 的公开资源容器，并把 Git 协议的发现与拉取策略收敛
	// 到仓库资源。影响范围为 HTTP/SSH 授权和仓库管理 API，不改变文件/团队/私人空间语义。
	RepositoryVisibilityPublic  = "PUBLIC"
	RepositoryVisibilityHidden  = "HIDDEN"
	RepositoryVisibilityPrivate = "PRIVATE"
)

type Repository struct {
	ID            string     `json:"repoId"`
	SpaceID       string     `json:"spaceId"`
	OwnerID       string     `json:"ownerId"`
	Name          string     `json:"name"`
	Slug          string     `json:"slug"`
	Description   string     `json:"description"`
	Visibility    string     `json:"visibility"`
	DefaultBranch string     `json:"defaultBranch"`
	HashAlgorithm string     `json:"hashAlgorithm"`
	Status        string     `json:"status"`
	ObjectCount   int64      `json:"objectCount"`
	ObjectBytes   int64      `json:"objectBytes"`
	CreatedAt     time.Time  `json:"createdAt"`
	UpdatedAt     time.Time  `json:"updatedAt"`
	DeletedAt     *time.Time `json:"-"`
	HTTPCloneURL  string     `json:"httpCloneUrl,omitempty"`
	SSHCloneURL   string     `json:"sshCloneUrl,omitempty"`
	Starred       bool       `json:"starred"`
	StarCount     int64      `json:"starCount"`
	ForkCount     int64      `json:"forkCount"`
}

type RepositorySocialStats struct {
	Starred   bool  `json:"starred"`
	StarCount int64 `json:"starCount"`
	ForkCount int64 `json:"forkCount"`
}

type RepositoryStar struct {
	ID        int64     `json:"id"`
	RepoID    string    `json:"repoId"`
	UserID    string    `json:"userId"`
	CreatedAt time.Time `json:"createdAt"`
}

type RepositoryFork struct {
	ID           int64     `json:"id"`
	RepoID       string    `json:"repoId"`
	ForkedRepoID string    `json:"forkedRepoId"`
	UserID       string    `json:"userId"`
	CreatedAt    time.Time `json:"createdAt"`
}

type SpaceAuthorization struct {
	Active              bool   `json:"active"`
	SpaceID             string `json:"space_id"`
	OwnerID             string `json:"owner_id"`
	ResourceType        string `json:"resource_type"`
	PermissionLevel     string `json:"permission_level"`
	AllowPublicBrowse   bool   `json:"allow_public_browse"`
	AllowPublicDownload bool   `json:"allow_public_download"`
	AllowPublicUpload   bool   `json:"allow_public_upload"`
}

// TeamMembership 是 Platform 对现有团队/企业空间成员关系的最小投影。
type TeamMembership struct {
	Member bool   `json:"member"`
	TeamID string `json:"teamId"`
	UserID string `json:"userId"`
	Role   string `json:"role,omitempty"`
}

type Ref struct {
	Name       string    `json:"name"`
	ObjectHash string    `json:"objectHash"`
	Type       string    `json:"type"`
	Protected  bool      `json:"protected"`
	UpdatedAt  time.Time `json:"updatedAt"`
}

type GitObject struct {
	Hash        string `json:"hash"`
	Type        string `json:"type"`
	Size        int64  `json:"size"`
	StoragePath string `json:"storagePath"`
}

type TreeEntry struct {
	Mode string `json:"mode"`
	Type string `json:"type"`
	Hash string `json:"hash"`
	Size int64  `json:"size,omitempty"`
	Name string `json:"name"`
	Path string `json:"path"`
}

// BlobPreview 是 Git 浏览器的安全文本预览投影。
// [REQ-GIT-UIUX-20260816] 原行为把任意 Git Object 强制转换为 JSON 字符串，
// 会损坏二进制内容且在大文件上超出管理 API 限制；新行为明确返回类型和截断状态，
// 原始字节仅通过受 Fetch 权限保护的 raw 端点读取。
type BlobPreview struct {
	Path      string `json:"path"`
	Content   string `json:"content"`
	Size      int64  `json:"size"`
	MimeType  string `json:"mimeType"`
	IsBinary  bool   `json:"isBinary"`
	Truncated bool   `json:"truncated"`
	LineCount int    `json:"lineCount"`
}

// RepositoryInsights 提供公开仓库页面需要的真实派生统计，避免前端展示虚构 Star/Fork 指标。
// 统计来自当前 Git 引用和对象树，不写入业务表，因此不会改变 Git 协议或仓库事实源。
type RepositoryInsights struct {
	CommitCount      int64             `json:"commitCount"`
	ContributorCount int               `json:"contributorCount"`
	BranchCount      int               `json:"branchCount"`
	TagCount         int               `json:"tagCount"`
	Languages        []LanguageStat    `json:"languages"`
	Contributors     []ContributorStat `json:"contributors"`
	Contributions    []ContributionDay `json:"contributions"`
}

type ContributionDay struct {
	Date  string `json:"date"`
	Count int64  `json:"count"`
}

type LanguageStat struct {
	Name  string `json:"name"`
	Bytes int64  `json:"bytes"`
}

type ContributorStat struct {
	Name    string `json:"name"`
	Email   string `json:"email"`
	Commits int64  `json:"commits"`
}

type Commit struct {
	Hash        string    `json:"hash"`
	TreeHash    string    `json:"treeHash"`
	Parents     []string  `json:"parents"`
	AuthorName  string    `json:"authorName"`
	AuthorEmail string    `json:"authorEmail"`
	AuthoredAt  time.Time `json:"authoredAt"`
	Committer   string    `json:"committerName"`
	CommittedAt time.Time `json:"committedAt"`
	Subject     string    `json:"subject"`
	Message     string    `json:"message"`
}

type Permission struct {
	ID        int64     `json:"id"`
	RepoID    string    `json:"repoId"`
	SubjectID string    `json:"subjectId"`
	Type      string    `json:"subjectType"`
	Level     string    `json:"permissionLevel"`
	CreatedAt time.Time `json:"createdAt"`
}

type MergeRequest struct {
	ID             string     `json:"mergeRequestId"`
	RepoID         string     `json:"repoId"`
	Number         int64      `json:"number"`
	Title          string     `json:"title"`
	Description    string     `json:"description"`
	SourceBranch   string     `json:"sourceBranch"`
	TargetBranch   string     `json:"targetBranch"`
	AuthorID       string     `json:"authorId"`
	Status         string     `json:"status"`
	ApprovalStatus string     `json:"approvalStatus"`
	MergeStrategy  string     `json:"mergeStrategy"`
	MergedBy       *string    `json:"mergedBy,omitempty"`
	MergedAt       *time.Time `json:"mergedAt,omitempty"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
}

// MergeRequestComment 是 MR 详情页的可审计讨论记录；作者资料仍由 Platform 用户目录解析，
// Git Service 只保存不可变的用户 ID 引用，避免跨服务复制用户表。
type MergeRequestComment struct {
	ID        string    `json:"commentId"`
	MRID      string    `json:"mergeRequestId"`
	AuthorID  string    `json:"authorId"`
	Body      string    `json:"body"`
	CreatedAt time.Time `json:"createdAt"`
}

type Webhook struct {
	ID        string    `json:"webhookId"`
	RepoID    string    `json:"repoId"`
	URL       string    `json:"url"`
	Events    []string  `json:"events"`
	Active    bool      `json:"active"`
	CreatedAt time.Time `json:"createdAt"`
}

type WorkflowBinding struct {
	ID         string   `json:"bindingId"`
	RepoID     string   `json:"repoId"`
	WorkflowID string   `json:"workflowId"`
	RefPattern string   `json:"refPattern"`
	Events     []string `json:"events"`
	Enabled    bool     `json:"enabled"`
}

type AuditEntry struct {
	ID        int64     `json:"id"`
	RepoID    string    `json:"repoId"`
	ActorID   *string   `json:"actorId,omitempty"`
	Operation string    `json:"operation"`
	IP        string    `json:"clientIp"`
	Detail    string    `json:"detail"`
	CreatedAt time.Time `json:"createdAt"`
}

type OutboxEvent struct {
	ID         string
	Aggregate  string
	EventType  string
	Exchange   string
	RoutingKey string
	Payload    []byte
	Attempts   int
}
