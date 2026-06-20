package transfer

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"

	"github.com/privateclouddisk/cli/api"
	"github.com/privateclouddisk/cli/config"
	"github.com/privateclouddisk/cli/utils"
)

// DownloadManager 下载管理器
type DownloadManager struct {
	cfg *config.Config

	mu       sync.Mutex
	progress *utils.ProgressTracker

	// 并发控制
	workerPool chan struct{}

	// 统计
	downloadedBytes int64
}

// NewDownloadManager 创建下载管理器
func NewDownloadManager(cfg *config.Config) *DownloadManager {
	return &DownloadManager{
		cfg:        cfg,
		workerPool: make(chan struct{}, cfg.DownloadWorkers),
	}
}

// DownloadFile 下载文件
// 流程:
// 1. 获取文件元数据
// 2. 分片下载
// 3. 合并文件
// 4. 校验文件完整性
func (m *DownloadManager) DownloadFile(client *api.Client, fileID, uid, localPath string) error {
	// 1. 获取文件元数据
	fmt.Printf("正在获取文件信息...\n")
	metadata, err := client.GetFileMetadata(fileID, uid)
	if err != nil {
		return fmt.Errorf("获取文件元数据失败: %w", err)
	}

	fileName := metadata.FileName
	fileSize := metadata.FileSize
	totalChunks := metadata.TotalChunks

	if localPath == "" {
		localPath = fileName
	}

	// 如果 localPath 是目录，则在目录下创建文件
	if info, err := os.Stat(localPath); err == nil && info.IsDir() {
		localPath = filepath.Join(localPath, fileName)
	}

	fmt.Printf("文件: %s (%s)\n", fileName, utils.FormatSize(fileSize))
	fmt.Printf("分片数: %d\n", totalChunks)

	// 2. 检查断点续传
	downloadedChunks := make(map[int]bool)
	tempDir := localPath + ".pcddownload"
	os.MkdirAll(tempDir, 0700)
	defer os.RemoveAll(tempDir)

	// 3. 初始化进度条
	m.progress = utils.NewProgressTracker(fileSize, fileName)

	// 4. 分片下载
	chunkSize := fileSize / int64(totalChunks)
	if chunkSize == 0 {
		chunkSize = fileSize
	}

	var wg sync.WaitGroup
	errChan := make(chan error, totalChunks)

	for i := 0; i < totalChunks; i++ {
		wg.Add(1)
		go func(chunkIndex int) {
			defer wg.Done()
			m.workerPool <- struct{}{}
			defer func() { <-m.workerPool }()

			offset := int64(chunkIndex) * chunkSize
			size := chunkSize
			if chunkIndex == totalChunks-1 {
				size = fileSize - offset
			}

			if err := m.downloadChunk(client, metadata, chunkIndex, offset, size, tempDir); err != nil {
				errChan <- fmt.Errorf("分片 %d 下载失败: %w", chunkIndex+1, err)
				return
			}
			downloadedChunks[chunkIndex] = true
		}(i)
	}

	wg.Wait()
	close(errChan)
	m.progress.Finish()

	var errs []error
	for e := range errChan {
		errs = append(errs, e)
	}
	if len(errs) > 0 {
		return fmt.Errorf("下载失败: %d 个分片出错", len(errs))
	}

	// 5. 合并分片
	fmt.Printf("正在合并分片...\n")
	if err := m.mergeChunks(tempDir, localPath, totalChunks); err != nil {
		return fmt.Errorf("合并分片失败: %w", err)
	}

	// 6. 校验文件
	fmt.Printf("正在校验文件...\n")
	checksum, err := utils.ComputeSHA256(localPath)
	if err != nil {
		return fmt.Errorf("校验文件失败: %w", err)
	}
	fmt.Printf("SHA256: %s\n", checksum)

	fmt.Printf("\n下载完成: %s\n", localPath)
	return nil
}

// downloadChunk 下载单个分片
func (m *DownloadManager) downloadChunk(client *api.Client, metadata *api.InternalFileMetadata, chunkIndex int, offset, size int64, tempDir string) error {
	// 构造下载 URL
	// 文件服务通过 gateway 的 /api/v1/file-service 路径访问
	downloadPath := fmt.Sprintf("/api/v1/file-service/download/chunk?file_id=%s&chunk_index=%d&uid=%s",
		metadata.FileID, chunkIndex, metadata.FileID)

	reader, _, err := client.StreamDownload(downloadPath)
	if err != nil {
		// 重试
		for retry := 0; retry < m.cfg.MaxRetries; retry++ {
			time.Sleep(time.Duration(m.cfg.RetryBackoffMs) * time.Millisecond * time.Duration(retry+1))
			fmt.Printf("  重试分片 %d/%d (第 %d 次)...\n", chunkIndex+1, metadata.TotalChunks, retry+1)
			reader, _, err = client.StreamDownload(downloadPath)
			if err == nil {
				break
			}
		}
		if err != nil {
			return err
		}
	}
	defer reader.Close()

	chunkFile := filepath.Join(tempDir, fmt.Sprintf("chunk_%d", chunkIndex))
	out, err := os.Create(chunkFile)
	if err != nil {
		return err
	}
	defer out.Close()

	written, err := io.Copy(out, reader)
	if err != nil {
		return err
	}

	atomic.AddInt64(&m.downloadedBytes, written)
	m.progress.Update(atomic.LoadInt64(&m.downloadedBytes))

	return nil
}

// mergeChunks 合并分片到最终文件
func (m *DownloadManager) mergeChunks(tempDir, outputPath string, totalChunks int) error {
	out, err := os.Create(outputPath)
	if err != nil {
		return err
	}
	defer out.Close()

	for i := 0; i < totalChunks; i++ {
		chunkFile := filepath.Join(tempDir, fmt.Sprintf("chunk_%d", i))
		in, err := os.Open(chunkFile)
		if err != nil {
			return fmt.Errorf("打开分片文件 %d 失败: %w", i, err)
		}

		_, err = io.Copy(out, in)
		in.Close()
		if err != nil {
			return fmt.Errorf("写入分片 %d 失败: %w", i, err)
		}
	}

	return nil
}