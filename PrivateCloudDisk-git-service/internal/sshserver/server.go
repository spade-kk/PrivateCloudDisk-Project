package sshserver

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"golang.org/x/crypto/ssh"

	"privateclouddisk/git-service/internal/auth"
	"privateclouddisk/git-service/internal/config"
	"privateclouddisk/git-service/internal/domain"
	"privateclouddisk/git-service/internal/gitrepo"
	"privateclouddisk/git-service/internal/security"
	"privateclouddisk/git-service/internal/store"
)

var commandPattern = regexp.MustCompile(`^(git-upload-pack|git-receive-pack) ['\"]?/?([a-z0-9][a-z0-9._-]{0,189})\.git['\"]?$`)

type Server struct {
	cfg        config.Config
	store      *store.Store
	manager    *gitrepo.Manager
	authorizer *auth.Authorizer
	config     *ssh.ServerConfig
	semaphore  chan struct{}
	failures   *security.FailureLimiter
}

func New(cfg config.Config, dataStore *store.Store, manager *gitrepo.Manager, authorizer *auth.Authorizer) (*Server, error) {
	signer, err := loadOrCreateHostKey(cfg.SSHHostKeyPath)
	if err != nil {
		return nil, err
	}
	if cfg.MaxProtocolConcurrent < 1 {
		cfg.MaxProtocolConcurrent = 1
	}
	server := &Server{cfg: cfg, store: dataStore, manager: manager, authorizer: authorizer,
		semaphore: make(chan struct{}, cfg.MaxProtocolConcurrent),
		failures:  security.NewFailureLimiter(cfg.AuthFailureLimit, cfg.AuthFailureWindow, cfg.AuthFailureCooldown)}
	server.config = &ssh.ServerConfig{
		NoClientAuth: false,
		PublicKeyCallback: func(metadata ssh.ConnMetadata, key ssh.PublicKey) (*ssh.Permissions, error) {
			ip := remoteIP(metadata.RemoteAddr())
			if allowed, _ := server.failures.Allow(ip); !allowed {
				server.recordSecurity(context.Background(), nil, nil, "SSH_AUTH_RATE_LIMITED", ip, map[string]any{})
				return nil, fmt.Errorf("SSH authentication temporarily rate limited")
			}
			fingerprint := ssh.FingerprintSHA256(key)
			authContext, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			defer cancel()
			userID, err := dataStore.AuthenticateSSHKey(authContext, fingerprint)
			if err != nil {
				cooldown := server.failures.RecordFailure(ip)
				server.recordSecurity(context.Background(), nil, nil, "SSH_AUTH_FAILED", ip, map[string]any{"cooldown": cooldown > 0})
				return nil, fmt.Errorf("unknown SSH key")
			}
			server.failures.RecordSuccess(ip)
			return &ssh.Permissions{Extensions: map[string]string{"user_id": userID, "fingerprint": fingerprint}}, nil
		},
		ServerVersion: "SSH-2.0-PrivateCloudDisk-Git",
	}
	server.config.AddHostKey(signer)
	return server, nil
}

// ListenAndServe 仅接受 exec channel 中的 upload-pack/receive-pack，拒绝 shell、PTY 和任意命令。
// [REQ-GIT-SSH-5.1~5.4] SSH 与 HTTP 复用同一仓库缓存、Object Store 和授权器。
func (s *Server) ListenAndServe(ctx context.Context) error {
	listener, err := net.Listen("tcp", s.cfg.SSHAddr)
	if err != nil {
		return err
	}
	go func() {
		<-ctx.Done()
		_ = listener.Close()
	}()
	for {
		connection, err := listener.Accept()
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return err
		}
		select {
		case s.semaphore <- struct{}{}:
			go func() {
				defer func() { <-s.semaphore }()
				s.handleConnection(ctx, connection)
			}()
		default:
			// [REQ-GIT-AUDIT-3.14/6.15] 原行为为每个已接收连接创建 goroutine，再在内部
			// 静默丢弃超额连接；新行为在 accept 后立刻释放超额 socket，防止等待握手的
			// goroutine 堆积。Git 客户端可按标准 SSH 失败语义重试。
			_ = connection.Close()
		}
	}
}

func (s *Server) handleConnection(ctx context.Context, connection net.Conn) {
	defer connection.Close()
	// [REQ-GIT-AUDIT-3.12/6.14] 原 SSH 握手没有 deadline，慢速客户端可长期占用
	// 协议并发槽；新行为仅限制握手阶段，认证完成后由 Git 命令超时控制传输生命周期。
	if s.cfg.SSHHandshakeTimeout > 0 {
		_ = connection.SetDeadline(time.Now().Add(s.cfg.SSHHandshakeTimeout))
	}
	serverConnection, channels, requests, err := ssh.NewServerConn(connection, s.config)
	if err != nil {
		return
	}
	_ = connection.SetDeadline(time.Time{})
	defer serverConnection.Close()
	go ssh.DiscardRequests(requests)
	for channelRequest := range channels {
		if channelRequest.ChannelType() != "session" {
			_ = channelRequest.Reject(ssh.UnknownChannelType, "only session channels are supported")
			continue
		}
		channel, requests, err := channelRequest.Accept()
		if err != nil {
			continue
		}
		// [REQ-GIT-AUDIT-3.5/3.14] Git CLI 每条连接只需要一个 exec；原行为允许同一
		// 已认证 socket 并发开启多个 session，绕过连接级并发上限。新行为执行首个合法
		// session 后关闭连接，命令白名单与权限边界保持不变。
		s.handleSession(ctx, serverConnection, channel, requests)
		return
	}
}

func (s *Server) handleSession(parent context.Context, connection *ssh.ServerConn, channel ssh.Channel, requests <-chan *ssh.Request) {
	defer channel.Close()
	for request := range requests {
		if request.Type != "exec" {
			_ = request.Reply(false, nil)
			continue
		}
		var payload struct{ Command string }
		if err := ssh.Unmarshal(request.Payload, &payload); err != nil {
			_ = request.Reply(false, nil)
			return
		}
		matches := commandPattern.FindStringSubmatch(strings.TrimSpace(payload.Command))
		if len(matches) != 3 {
			_ = request.Reply(false, nil)
			_, _ = io.WriteString(channel.Stderr(), "Only Git upload-pack and receive-pack are allowed\n")
			s.recordSecurity(parent, nil, nil, "SSH_COMMAND_REJECTED", remoteIP(connection.RemoteAddr()), map[string]any{"reason": "unsupported_exec"})
			return
		}
		if err := request.Reply(true, nil); err != nil {
			return
		}
		exitCode := s.execute(parent, connection, channel, matches[1], matches[2])
		_, _ = channel.SendRequest("exit-status", false, ssh.Marshal(struct{ Status uint32 }{uint32(exitCode)}))
		return
	}
}

func (s *Server) execute(parent context.Context, connection *ssh.ServerConn, channel ssh.Channel, command, slug string) int {
	ctx, cancel := context.WithTimeout(parent, s.cfg.GitCommandTimeout)
	defer cancel()
	repo, err := s.store.GetRepositoryBySlug(ctx, slug)
	if err != nil {
		_, _ = io.WriteString(channel.Stderr(), "Repository not found\n")
		s.recordSecurity(ctx, nil, nil, "SSH_REPOSITORY_NOT_FOUND", remoteIP(connection.RemoteAddr()), map[string]any{})
		return 1
	}
	if connection.Permissions == nil {
		_, _ = io.WriteString(channel.Stderr(), "Authentication required\n")
		s.recordSecurity(ctx, &repo, nil, "SSH_AUTH_REQUIRED", remoteIP(connection.RemoteAddr()), map[string]any{})
		return 1
	}
	userID := connection.Permissions.Extensions["user_id"]
	operation := auth.Fetch
	if command == "git-receive-pack" {
		operation = auth.Push
	}
	if _, err := s.authorizer.Require(ctx, repo, userID, operation); err != nil {
		// [REQ-GIT-AUDIT-4.2/4.18] 隐藏/私密 Git 资源对已经完成 SSH 认证但没有仓库
		// 授权的用户仍不得泄漏存在性；PUBLIC 仓库则保留明确的权限错误，便于 CLI 诊断。
		if auth.IsConcealed(repo) {
			_, _ = io.WriteString(channel.Stderr(), "Repository not found\n")
		} else {
			_, _ = io.WriteString(channel.Stderr(), "Repository permission denied\n")
		}
		s.recordSecurity(ctx, &repo, &userID, "SSH_PERMISSION_DENIED", remoteIP(connection.RemoteAddr()), map[string]any{"operation": operation})
		return 1
	}
	repoPath, err := s.manager.EnsureLocal(ctx, repo)
	if err != nil {
		_, _ = io.WriteString(channel.Stderr(), "Repository storage unavailable\n")
		actor := userID
		s.recordSecurity(ctx, &repo, &actor, "SSH_STORAGE_UNAVAILABLE", remoteIP(connection.RemoteAddr()), map[string]any{"operation": operation})
		return 1
	}
	// [FIX-GIT-PUSH-RECOVERY-20260816] 与 Smart HTTP 保持一致：上一次 push 可能已经
	// 写入本地 refs，但共享 Object 持久化失败。SSH 客户端下一次 push 同样可能先判定
	// refs 已经一致，因此在 receive-pack 前自动完成 DEGRADED 仓库恢复。
	if operation == auth.Push && repo.Status == "DEGRADED" {
		if err := s.manager.Sync(ctx, repo); err != nil {
			log.Printf("git SSH repository recovery sync failed repo=%s storage=%s: %v", repo.ID, s.cfg.StorageURL, err)
			_, _ = io.WriteString(channel.Stderr(), "Shared object storage unavailable; retry later\n")
			return 1
		}
	}
	var before map[string]string
	if operation == auth.Push {
		before, err = s.manager.SnapshotRefs(ctx, repo)
		if err != nil {
			_, _ = io.WriteString(channel.Stderr(), "Unable to snapshot repository refs\n")
			return 1
		}
	}
	gitCommand := "upload-pack"
	if operation == auth.Push {
		gitCommand = "receive-pack"
	}
	cmd := exec.CommandContext(ctx, s.cfg.GitBinary, gitCommand, repoPath)
	cmd.Stdin, cmd.Stdout, cmd.Stderr = channel, channel, channel.Stderr()
	if err := cmd.Run(); err != nil {
		// Git hook拒绝/非快进等业务结果已通过 Git 协议写回客户端；回滚本地/索引 refs
		// 仅用于防止 receive-pack 在异常中途写入 refs，不把业务拒绝误标记为 DEGRADED。
		if operation == auth.Push {
			if rollbackErr := s.manager.RestoreRefs(ctx, repo, before); rollbackErr != nil {
				log.Printf("git SSH push ref rollback failed repo=%s: %v", repo.ID, rollbackErr)
			}
		}
		actor := userID
		// [REQ-GIT-AUDIT-3.13/4.24] Git 协议会把非快进、保护分支或 hook 拒绝的
		// 具体结果写回 CLI；同时仅记录传输、仓库和操作类别，便于追溯失败而不写入
		// 用户命令原文或凭据。原行为只记录成功操作。
		s.recordSecurity(ctx, &repo, &actor, "SSH_GIT_COMMAND_REJECTED", remoteIP(connection.RemoteAddr()), map[string]any{"operation": operation})
		return 1
	}
	if operation == auth.Push {
		if err := s.manager.Sync(ctx, repo); err != nil {
			log.Printf("git SSH push persistence failed repo=%s storage=%s: %v", repo.ID, s.cfg.StorageURL, err)
			if rollbackErr := s.manager.RestoreRefs(ctx, repo, before); rollbackErr != nil {
				log.Printf("git SSH push ref rollback failed repo=%s: %v", repo.ID, rollbackErr)
			}
			_ = s.store.MarkRepositoryStatus(ctx, repo.ID, "DEGRADED")
			_, _ = io.WriteString(channel.Stderr(), "Shared object persistence failed; retry safely\n")
			actor := userID
			s.recordSecurity(ctx, &repo, &actor, "SSH_OBJECT_PERSISTENCE_FAILED", remoteIP(connection.RemoteAddr()), map[string]any{"operation": operation})
			return 1
		}
		after, _ := s.manager.SnapshotRefs(ctx, repo)
		changed := gitrepo.ChangedRefs(before, after)
		if len(changed) > 0 {
			bindings, _ := s.store.ListWorkflowBindings(ctx, repo.ID)
			payload := map[string]any{
				"specversion": "1.0", "id": fmt.Sprintf("%d-%s", time.Now().UnixNano(), repo.ID),
				"source": "pcd://git-service/repos/" + repo.ID, "type": "pcd.git.push.completed.v1",
				"time": time.Now().UTC().Format(time.RFC3339Nano), "subject": repo.ID,
				"data": map[string]any{"repository_id": repo.ID, "space_id": repo.SpaceID, "actor_id": userID,
					"changed_refs": changed, "workflow_bindings": bindings},
			}
			if err := s.store.InsertOutbox(ctx, repo.ID, "pcd.git.push.completed.v1", s.cfg.EventExchange, "git.push.completed", payload); err != nil {
				log.Printf("insert SSH push outbox failed: %v", err)
			}
		}
		if err := s.manager.UpdateServerInfo(ctx, repo); err != nil {
			// [REQ-GIT-AUDIT-2.50] push 的 Object/refs 已经同步成功后刷新 dumb HTTP
			// 索引；索引刷新失败仅影响兼容静态读取，不能把已成功的 SSH push 回滚。
			log.Printf("git update-server-info failed after SSH push repo=%s: %v", repo.ID, err)
		}
	}
	actor := userID
	_ = s.store.InsertAudit(ctx, repo.ID, &actor, strings.ToUpper(strings.TrimPrefix(command, "git-")),
		remoteIP(connection.RemoteAddr()), "", map[string]any{"transport": "ssh"})
	return 0
}

// recordSecurity 与 HTTP 协议层使用同一张独立安全审计表，不把未知仓库认证失败塞进
// 有 repo 外键语义的业务操作日志。详情只记录事件类别，不记录 PAT、公钥或命令原文。
func (s *Server) recordSecurity(ctx context.Context, repo *domain.Repository, actor *string, operation, ip string, detail any) {
	var repoID *string
	if repo != nil {
		repoID = &repo.ID
	}
	if err := s.store.InsertSecurityAudit(ctx, repoID, actor, operation, ip, detail); err != nil {
		log.Printf("insert SSH Git security audit failed operation=%s: %v", operation, err)
	}
}

func remoteIP(address net.Addr) string {
	host, _, err := net.SplitHostPort(address.String())
	if err == nil {
		return host
	}
	return address.String()
}

func loadOrCreateHostKey(path string) (ssh.Signer, error) {
	if data, err := os.ReadFile(path); err == nil {
		return ssh.ParsePrivateKey(data)
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return nil, err
	}
	_, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, err
	}
	encoded, err := x509.MarshalPKCS8PrivateKey(privateKey)
	if err != nil {
		return nil, err
	}
	pemBytes := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: encoded})
	if err := os.WriteFile(path, pemBytes, 0o600); err != nil {
		return nil, err
	}
	return ssh.NewSignerFromKey(privateKey)
}
