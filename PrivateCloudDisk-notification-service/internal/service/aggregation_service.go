// Package service 提供消息聚合服务。
// 避免短时间内大量推送骚扰用户，在时间窗口内将同类通知聚合为一条。
package service

import (
	"fmt"
	"log"
	"time"

	"github.com/google/uuid"

	"github.com/privateclouddisk/notification-service/internal/config"
	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/repository"
)

// AggregationService 消息聚合服务
type AggregationService struct {
	cfg *config.Config
	repo *repository.AggregationRepo
	notifRepo *repository.NotificationRepo
}

// NewAggregationService 创建聚合服务
func NewAggregationService(
	cfg *config.Config,
	repo *repository.AggregationRepo,
	notifRepo *repository.NotificationRepo,
) *AggregationService {
	return &AggregationService{
		cfg:       cfg,
		repo:      repo,
		notifRepo: notifRepo,
	}
}

// ShouldAggregate 判断是否应该聚合
func (s *AggregationService) ShouldAggregate(notifType string) bool {
	// 以下类型需要聚合
	aggregatableTypes := map[string]bool{
		string(domain.TypeShare):     true,
		string(domain.TypeSystem):    true,
		string(domain.TypeMarketing): true,
		string(domain.TypeReminder):  true,
	}
	return aggregatableTypes[notifType]
}

// AddToWindow 将通知添加到聚合窗口
// 返回聚合窗口 ID（如果创建了新窗口），否则返回空字符串
func (s *AggregationService) AddToWindow(
	userID, channel, notifType string,
	recordID int64,
) (string, error) {
	// 1. 查找当前打开的窗口
	win, err := s.repo.GetOpenWindow(userID, channel, notifType)
	if err != nil {
		// 窗口不存在，创建新窗口
		windowID := uuid.New().String()
		now := time.Now()
		win = &domain.AggregationWindow{
			ID:          windowID,
			UserID:      userID,
			Channel:     channel,
			Type:        notifType,
			Count:       1,
			Status:      "open",
			WindowStart: now,
			WindowEnd:   now.Add(time.Duration(s.cfg.Worker.AggregationWindowSec) * time.Second),
		}

		if err := s.repo.Create(win); err != nil {
			return "", fmt.Errorf("创建聚合窗口失败: %w", err)
		}

		// 更新通知记录
		s.notifRepo.UpdateStatus(recordID, domain.StatusAggregated, "")
		return windowID, nil
	}

	// 2. 窗口已存在，累加计数
	newCount := win.Count + 1
	s.repo.UpdateCount(win.ID, newCount)

	// 3. 检查是否达到最大聚合数
	if newCount >= s.cfg.Worker.MaxAggregationSize {
		s.repo.Close(win.ID, fmt.Sprintf("[%d]", recordID))
		log.Printf("[Aggregation] 窗口已满，触发发送: id=%s, count=%d", win.ID, newCount)
	}

	// 4. 更新通知记录
	s.notifRepo.UpdateStatus(recordID, domain.StatusAggregated, "")

	return win.ID, nil
}

// CloseWindow 关闭聚合窗口
func (s *AggregationService) CloseWindow(windowID string, recordIDs []int64) error {
	// 构造 record IDs JSON
	idsJSON := "["
	for i, id := range recordIDs {
		if i > 0 {
			idsJSON += ","
		}
		idsJSON += fmt.Sprintf("%d", id)
	}
	idsJSON += "]"

	return s.repo.Close(windowID, idsJSON)
}

// GetExpiredWindows 获取过期的聚合窗口
func (s *AggregationService) GetExpiredWindows() ([]domain.AggregationWindow, error) {
	return s.repo.GetExpiredWindows()
}

// MarkSent 标记聚合窗口已发送
func (s *AggregationService) MarkSent(windowID string) error {
	return s.repo.MarkSent(windowID)
}