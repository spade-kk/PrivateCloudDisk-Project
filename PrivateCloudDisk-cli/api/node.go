package api

import (
	"fmt"
	"time"
)

// ============================================================
// 文件/目录节点相关 API
// ============================================================

// FolderNode 文件夹节点
type FolderNode struct {
	NodeID     string `json:"node_id"`
	NodeName   string `json:"node_name"`
	CreateTime string `json:"node_create_time"`
	Status     string `json:"node_status"`
}

// NodeEntry 节点条目（文件或文件夹）
type NodeEntry struct {
	NodeID   string `json:"node_id"`
	NodeType string `json:"node_type"` // FILE 或 FOLDER
	NodeName string `json:"node_name"`
	NodeSize int64  `json:"node_size"`
}

// FileInfo 文件信息
type FileInfo struct {
	ID           string    `json:"id"`
	Name         string    `json:"name"`
	Type         string    `json:"type"`
	Size         int64     `json:"size"`
	UploadedTime time.Time `json:"uploaded_time"`
	NodeID       string    `json:"node_id"`
	TotalChunks  int       `json:"total_chunks"`
}

// CreateFolderRequest 创建文件夹请求
type CreateFolderRequest struct {
	NodeID     string `json:"node_id"`
	FolderName string `json:"folder_name"`
}

// RenameRequest 重命名请求
type RenameRequest struct {
	NewName string `json:"new_node_name,omitempty"`
	NewName2 string `json:"file_new_name,omitempty"`
}

// MoveRequest 移动请求
type MoveRequest struct {
	TargetNodeID string `json:"target_position,omitempty"`
	TargetNodeID2 string `json:"target_node_id,omitempty"`
}

// GetRootNode 获取根目录
func (c *Client) GetRootNode() (*FolderNode, error) {
	var node FolderNode
	if err := c.Get("/business/nodes/root", &node); err != nil {
		return nil, fmt.Errorf("获取根目录失败: %w", err)
	}
	return &node, nil
}

// GetNode 获取指定节点
func (c *Client) GetNode(nodeID string) (*FolderNode, error) {
	var node FolderNode
	if err := c.Get("/business/nodes/"+nodeID, &node); err != nil {
		return nil, fmt.Errorf("获取节点失败: %w", err)
	}
	return &node, nil
}

// ListChildren 列出子节点
func (c *Client) ListChildren(nodeID string) ([]NodeEntry, error) {
	var entries []NodeEntry
	if err := c.Get("/business/nodes/"+nodeID+"/children", &entries); err != nil {
		return nil, fmt.Errorf("列出子节点失败: %w", err)
	}
	return entries, nil
}

// CreateFolder 创建文件夹
func (c *Client) CreateFolder(parentNodeID, folderName string) error {
	req := CreateFolderRequest{
		NodeID:     parentNodeID,
		FolderName: folderName,
	}
	if err := c.Post("/business/nodes/", req, nil); err != nil {
		return fmt.Errorf("创建文件夹失败: %w", err)
	}
	return nil
}

// DeleteNode 删除节点（文件或文件夹）
func (c *Client) DeleteNode(nodeID string) error {
	if err := c.Delete("/business/nodes/"+nodeID, nil); err != nil {
		return fmt.Errorf("删除节点失败: %w", err)
	}
	return nil
}

// RenameNode 重命名节点
func (c *Client) RenameNode(nodeID, newName string) error {
	req := RenameRequest{NewName: newName}
	if err := c.Patch("/business/nodes/"+nodeID+"/name", req, nil); err != nil {
		return fmt.Errorf("重命名节点失败: %w", err)
	}
	return nil
}

// MoveNode 移动节点
func (c *Client) MoveNode(nodeID, targetNodeID string) error {
	req := MoveRequest{TargetNodeID: targetNodeID}
	if err := c.Patch("/business/nodes/"+nodeID+"/position", req, nil); err != nil {
		return fmt.Errorf("移动节点失败: %w", err)
	}
	return nil
}

// GetFileInfo 获取文件信息
func (c *Client) GetFileInfo(fileID string) (*FileInfo, error) {
	var file FileInfo
	if err := c.Get("/business/files/"+fileID, &file); err != nil {
		return nil, fmt.Errorf("获取文件信息失败: %w", err)
	}
	return &file, nil
}

// DeleteFile 删除文件
func (c *Client) DeleteFile(fileID string) error {
	if err := c.Delete("/business/files/"+fileID, nil); err != nil {
		return fmt.Errorf("删除文件失败: %w", err)
	}
	return nil
}

// RenameFile 重命名文件
func (c *Client) RenameFile(fileID, newName string) error {
	req := map[string]string{"file_new_name": newName}
	if err := c.Patch("/business/files/"+fileID+"/name", req, nil); err != nil {
		return fmt.Errorf("重命名文件失败: %w", err)
	}
	return nil
}

// MoveFile 移动文件
func (c *Client) MoveFile(fileID, targetNodeID string) error {
	req := MoveRequest{TargetNodeID2: targetNodeID}
	if err := c.Patch("/business/files/"+fileID+"/position", req, nil); err != nil {
		return fmt.Errorf("移动文件失败: %w", err)
	}
	return nil
}