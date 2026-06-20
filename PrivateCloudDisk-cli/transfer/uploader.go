package transfer

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"github.com/privateclouddisk/cli/api"
	"github.com/privateclouddisk/cli/config"
	"github.com/privateclouddisk/cli/utils"
)

// ChunkTask 单个分片任务
type ChunkTask struct {
	Index       int
	Offset      int64
	Size        int64
	Data        []byte
	Checksum    string
	StoragePath string
	Status      string // pending, uploading, completed, failed
	Retries     int
}

// UploadManager 上传管理器
type UploadManager struct {
	cfg *config.Config

	mu       sync.Mutex
	chunks   []*ChunkTask
	progress *utils.ProgressTracker

	// 并发控制
	workerPool chan struct{}

	// 统计
	uploadedBytes int64
	failedChunks  int32
}

// NewUploadManager 创建上传管理器
func NewUploadManager(cfg *config.Config) *UploadManager {
	return &UploadManager{
		cfg:        cfg,
		workerPool: make(chan struct{}, cfg.MaxConcurrency),
	}
}

// UploadFile 上传文件
// 流程:
// 1. 计算文件 SHA256 和分片信息
// 2. 创建上传会话
// 3. 并发上传分片到文件服务
// 4. 标记分片完成
// 5. 请求合并
// 6. 完成上传
func (m *UploadManager) UploadFile(client *api.Client, localPath, nodeID string) error {
	// 1. 获取文件信息
	fileInfo, err := os.Stat(localPath)
	if err != nil {
		return fmt.Errorf("无法读取文件: %w", err)
	}
	if fileInfo.IsDir() {
		return fmt.Errorf("不支持上传文件夹，请使用 sync 命令同步目录")
	}

	fileName := filepath.Base(localPath)
	fileSize := fileInfo.Size()
	chunkSize := int64(m.cfg.ChunkSizeMB * 1024 * 1024)

	// 计算分片数量
	totalChunks := int((fileSize + chunkSize - 1) / chunkSize)
	if totalChunks == 0 {
		totalChunks = 1
	}

	// 2. 计算文件 SHA256
	fmt.Printf("正在计算文件校验和...\n")
	fileChecksum, err := utils.ComputeSHA256(localPath)
	if err != nil {
		return fmt.Errorf("计算文件校验和失败: %w", err)
	}
	fmt.Printf("SHA256: %s\n", fileChecksum)

	// 3. 创建上传会话
	fmt.Printf("正在创建上传会话...\n")
	fileType := utils.GetFileType(fileName)
	uploadsID, err := client.CreateUploadSession(api.CreateUploadSessionRequest{
		TotalChunks:   totalChunks,
		FileSize:      fileSize,
		FileChecksum:  fileChecksum,
		ChunksMaxSize: int(chunkSize),
		FileName:      fileName,
		FileType:      fileType,
		NodeID:        nodeID,
	})
	if err != nil {
		return fmt.Errorf("创建上传会话失败: %w", err)
	}
	fmt.Printf("上传会话: %s\n", uploadsID)

	// 4. 初始化分片任务
	m.chunks = make([]*ChunkTask, totalChunks)
	for i := 0; i < totalChunks; i++ {
		offset := int64(i) * chunkSize
		size := chunkSize
		if offset+size > fileSize {
			size = fileSize - offset
		}
		m.chunks[i] = &ChunkTask{
			Index:  i,
			Offset: offset,
			Size:   size,
			Status: "pending",
		}
	}

	// 5. 初始化进度条
	m.progress = utils.NewProgressTracker(fileSize, fileName)

	// 6. 并发上传分片到文件服务
	fmt.Printf("正在上传 %d 个分片 (并发数: %d)...\n", totalChunks, m.cfg.MaxConcurrency)

	var wg sync.WaitGroup
	errChan := make(chan error, totalChunks)

	for i := 0; i < totalChunks; i++ {
		wg.Add(1)
		go func(task *ChunkTask) {
			defer wg.Done()
			m.workerPool <- struct{}{}
			defer func() { <-m.workerPool }()

			if err := m.uploadChunk(client, localPath, uploadsID, task); err != nil {
				errChan <- fmt.Errorf("分片 %d 上传失败: %w", task.Index+1, err)
				atomic.AddInt32(&m.failedChunks, 1)
			}
		}(m.chunks[i])
	}

	wg.Wait()
	close(errChan)
	m.progress.Finish()

	// 检查错误
	var errs []error
	for e := range errChan {
		errs = append(errs, e)
	}
	if len(errs) > 0 {
		for _, e := range errs {
			fmt.Fprintf(os.Stderr, "  %v\n", e)
		}
		return fmt.Errorf("上传失败: %d 个分片出错", len(errs))
	}

	// 7. 请求合并
	fmt.Printf("正在合并分片...\n")
	fileID, err := client.MergeChunks(uploadsID)
	if err != nil {
		return fmt.Errorf("合并分片失败: %w", err)
	}
	fmt.Printf("文件 ID: %s\n", fileID)

	// 8. 完成上传
	auth, _ := config.EnsureLoggedIn()
	storagePath := m.chunks[0].StoragePath // 取第一个分片的存储路径前缀
	if err := client.CompleteFileUpload(uploadsID, fileID, storagePath, auth.UserID); err != nil {
		return fmt.Errorf("完成上传失败: %w", err)
	}

	fmt.Printf("\n上传完成: %s -> %s\n", fileName, uploadsID)
	return nil
}

// uploadChunk 上传单个分片
func (m *UploadManager) uploadChunk(client *api.Client, filePath, uploadsID string, task *ChunkTask) error {
	// 读取分片数据
	file, err := os.Open(filePath)
	if err != nil {
		return err
	}
	defer file.Close()

	data := make([]byte, task.Size)
	_, err = file.ReadAt(data, task.Offset)
	if err != nil && err != io.EOF {
		return err
	}
	task.Data = data

	// 计算分片校验和
	hash := sha256.Sum256(data)
	task.Checksum = hex.EncodeToString(hash[:])

	// 上传分片到文件服务 (FastAPI)
	// 注: 实际环境中文件服务 URL 可能不同，这里通过 gateway 透传
	// 分片上传到 /api/v1/upload/chunk 端点
	storagePath := fmt.Sprintf("uploads/%s/chunk_%d", uploadsID, task.Index)
	task.StoragePath = storagePath

	// 构造分片上传路径
	// 文件服务通过 gateway 的 /api/v1/file-service 路径访问
	uploadPath := fmt.Sprintf("/api/v1/file-service/upload/chunk?uploads_id=%s&chunk_index=%d&chunk_hash=%s",
		uploadsID, task.Index, task.Checksum)

	_, err = client.StreamUpload(uploadPath, utils.NewByteReader(data), "application/octet-stream", task.Size)
	if err != nil {
		task.Status = "failed"
		// 重试
		for retry := 0; retry < m.cfg.MaxRetries; retry++ {
			time.Sleep(time.Duration(m.cfg.RetryBackoffMs) * time.Millisecond * time.Duration(retry+1))
			fmt.Printf("  重试分片 %d/%d (第 %d 次)...\n", task.Index+1, len(m.chunks), retry+1)
			_, err = client.StreamUpload(uploadPath, utils.NewByteReader(data), "application/octet-stream", task.Size)
			if err == nil {
				break
			}
		}
		if err != nil {
			return err
		}
	}

	// 标记分片完成
	if err := client.CompleteChunk(uploadsID, task.Index, storagePath); err != nil {
		task.Status = "failed"
		return err
	}

	task.Status = "completed"
	atomic.AddInt64(&m.uploadedBytes, task.Size)
	m.progress.Update(atomic.LoadInt64(&m.uploadedBytes))

	return nil
}

// ResumeUpload 断点续传
func (m *UploadManager) ResumeUpload(client *api.Client, uploadsID, localPath string) error {
	// 获取上传会话状态
	session, err := client.GetUploadSession(uploadsID)
	if err != nil {
		return fmt.Errorf("获取上传会话失败: %w", err)
	}

	fmt.Printf("续传会话: %s, 状态: %s\n", uploadsID, session.Status)

	// 获取已完成的 chunk
	// 这里简化处理，实际需要查询每个 chunk 的状态
	// 然后只上传未完成的 chunk

	_ = session
	return fmt.Errorf("断点续传功能开发中")
}

// ============================================================
// 工具函数
// ============================================================

// sortChunks 按索引排序
func sortChunks(chunks []*ChunkTask) {
	sort.Slice(chunks, func(i, j int) bool {
		return chunks[i].Index < chunks[j].Index
	})
}