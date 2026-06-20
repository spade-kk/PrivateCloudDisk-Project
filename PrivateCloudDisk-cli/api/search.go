package api

import (
	"fmt"
)

// ============================================================
// 搜索相关 API
// ============================================================

// SearchRequest 搜索请求
type SearchRequest struct {
	Keyword string `json:"keyword"`
	Page    int    `json:"page"`
	Size    int    `json:"size"`
}

// SearchResult 搜索结果
type SearchResult struct {
	Items []SearchItem `json:"items"`
	Total int64        `json:"total"`
	Page  int          `json:"page"`
	Size  int          `json:"size"`
}

// SearchItem 搜索结果项
type SearchItem struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	Type         string `json:"type"`
	Size         int64  `json:"size"`
	UploadedTime string `json:"uploaded_time"`
	NodeID       string `json:"node_id"`
	Highlights   map[string][]string `json:"highlights,omitempty"`
}

// SearchFiles 搜索文件
func (c *Client) SearchFiles(keyword string, page, size int) (*SearchResult, error) {
	path := fmt.Sprintf("/business/files/advanced-search?keyword=%s&page=%d&size=%d", keyword, page, size)
	var result SearchResult
	if err := c.Get(path, &result); err != nil {
		return nil, fmt.Errorf("搜索文件失败: %w", err)
	}
	return &result, nil
}

// ============================================================
// 配额相关 API
// ============================================================

// QuotaInfo 配额信息
type QuotaInfo struct {
	UserID        string `json:"user_id"`
	TotalCapacity int64  `json:"total_capacity"`
	UsedCapacity  int64  `json:"used_capacity"`
	FileCount     int    `json:"file_count"`
	Version       int    `json:"version"`
}

// GetQuota 获取配额信息
func (c *Client) GetQuota() (*QuotaInfo, error) {
	var quota QuotaInfo
	if err := c.Get("/business/quotas/me", &quota); err != nil {
		return nil, fmt.Errorf("获取配额信息失败: %w", err)
	}
	return &quota, nil
}

// ============================================================
// 分享相关 API
// ============================================================

// CreateShareRequest 创建分享请求
type CreateShareRequest struct {
	FileID   string `json:"file_id"`
	Password string `json:"password,omitempty"`
	ExpireH  int    `json:"expire_hours"`
}

// ShareLink 分享链接
type ShareLink struct {
	ShareLinkID string `json:"sharing_link_id"`
	SharePath   string `json:"sharing_link_path"`
	ShareURL    string `json:"share_url"`
}

// CreateShare 创建分享链接
func (c *Client) CreateShare(fileID, password string, expireHours int) (*ShareLink, error) {
	req := CreateShareRequest{
		FileID:   fileID,
		Password: password,
		ExpireH:  expireHours,
	}
	var link ShareLink
	if err := c.Post("/business/shares", req, &link); err != nil {
		return nil, fmt.Errorf("创建分享链接失败: %w", err)
	}
	return &link, nil
}