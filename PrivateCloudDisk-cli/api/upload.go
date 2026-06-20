package api

import (
	"fmt"
)

// ============================================================
// 上传相关 API
// ============================================================

// CreateUploadSessionRequest 创建上传会话请求
type CreateUploadSessionRequest struct {
	TotalChunks   int    `json:"total_chunks"`
	FileSize      int64  `json:"file_size"`
	FileChecksum  string `json:"file_checksum"`
	ChunksMaxSize int    `json:"chunks_max_size"`
	FileName      string `json:"file_name"`
	FileType      string `json:"file_type"`
	NodeID        string `json:"node_id"`
}

// UploadSessionInfo 上传会话信息
type UploadSessionInfo struct {
	UploadsID      string `json:"uploads_id"`
	UserID         string `json:"user_id"`
	FileName       string `json:"file_name"`
	StartingTime   string `json:"starting_time"`
	EndingTime     string `json:"endding_time"`
	FileSize       int64  `json:"file_size"`
	ChunksMaxSize  int    `json:"chunks_max_size"`
	TotalChunks    int    `json:"total_chunks"`
	FileChecksum   string `json:"file_checksum"`
	FileType       string `json:"file_type"`
	NodeID         string `json:"node_id"`
	Status         string `json:"status"`
}

// InternalFileMetadata 内部文件元数据
type InternalFileMetadata struct {
	FileID      string `json:"id"`
	FileName    string `json:"name"`
	FileSize    int64  `json:"size"`
	FileType    string `json:"type"`
	TotalChunks int    `json:"total_chunks"`
	StoragePath string `json:"chunk_storage_path"`
}

// CreateUploadSession 创建上传会话
func (c *Client) CreateUploadSession(req CreateUploadSessionRequest) (string, error) {
	var uploadsID string
	if err := c.Post("/business/uploads/", req, &uploadsID); err != nil {
		return "", fmt.Errorf("创建上传会话失败: %w", err)
	}
	return uploadsID, nil
}

// CompleteChunk 标记分片上传完成
func (c *Client) CompleteChunk(uploadsID string, chunkIndex int, storagePath string) error {
	path := fmt.Sprintf("/business/internal/storage/uploads/%s/chunks/%d/complete", uploadsID, chunkIndex)
	url := path + "?storage_path=" + storagePath
	if err := c.Post(url, nil, nil); err != nil {
		return fmt.Errorf("标记分片完成失败: %w", err)
	}
	return nil
}

// MergeChunks 请求合并分片
func (c *Client) MergeChunks(uploadsID string) (string, error) {
	var fileID string
	if err := c.Post("/business/internal/storage/uploads/"+uploadsID+"/merging", nil, &fileID); err != nil {
		return "", fmt.Errorf("合并分片失败: %w", err)
	}
	return fileID, nil
}

// CompleteFileUpload 完成文件上传
func (c *Client) CompleteFileUpload(uploadsID, fileID, fileStoragePath, uid string) error {
	path := fmt.Sprintf("/business/internal/storage/files?uploads_id=%s&file_storage_path=%s&file_id=%s&uid=%s",
		uploadsID, fileStoragePath, fileID, uid)
	if err := c.Post(path, nil, nil); err != nil {
		return fmt.Errorf("完成文件上传失败: %w", err)
	}
	return nil
}

// GetFileMetadata 获取文件元数据（内部接口）
func (c *Client) GetFileMetadata(fileID, uid string) (*InternalFileMetadata, error) {
	var metadata InternalFileMetadata
	path := fmt.Sprintf("/business/internal/storage/files/%s?uid=%s", fileID, uid)
	if err := c.Get(path, &metadata); err != nil {
		return nil, fmt.Errorf("获取文件元数据失败: %w", err)
	}
	return &metadata, nil
}

// GetUploadSession 获取上传会话信息
func (c *Client) GetUploadSession(uploadsID string) (*UploadSessionInfo, error) {
	var info UploadSessionInfo
	if err := c.Get("/business/internal/storage/uploads/"+uploadsID, &info); err != nil {
		return nil, fmt.Errorf("获取上传会话信息失败: %w", err)
	}
	return &info, nil
}