// Package service 提供用户偏好管理服务。
package service

import (
	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/repository"
)

// PreferenceService 偏好管理服务
type PreferenceService struct {
	repo *repository.PreferenceRepo
}

// NewPreferenceService 创建偏好服务
func NewPreferenceService(repo *repository.PreferenceRepo) *PreferenceService {
	return &PreferenceService{repo: repo}
}

// GetPreferences 获取用户所有偏好
func (s *PreferenceService) GetPreferences(userID string) ([]domain.NotificationPreference, error) {
	return s.repo.GetByUserID(userID)
}

// GetChannelPreference 获取用户特定渠道偏好
func (s *PreferenceService) GetChannelPreference(userID, channel string) (*domain.NotificationPreference, error) {
	return s.repo.GetByChannel(userID, channel)
}

// UpdatePreference 更新用户偏好
func (s *PreferenceService) UpdatePreference(pref *domain.NotificationPreference) error {
	return s.repo.Upsert(pref)
}

// SetDND 设置免打扰
func (s *PreferenceService) SetDND(userID, channel string, enabled bool, start, end string) error {
	pref, err := s.repo.GetByChannel(userID, channel)
	if err != nil {
		// 偏好不存在，创建新记录
		return s.repo.Upsert(&domain.NotificationPreference{
			UserID:     userID,
			Channel:    channel,
			Enabled:    true,
			DNDEnabled: enabled,
			DNDStart:   start,
			DNDEnd:     end,
			MaxPerDay:  50,
		})
	}

	pref.DNDEnabled = enabled
	pref.DNDStart = start
	pref.DNDEnd = end
	return s.repo.Upsert(pref)
}

// SetMaxPerDay 设置每日最大推送数
func (s *PreferenceService) SetMaxPerDay(userID, channel string, maxPerDay int) error {
	pref, err := s.repo.GetByChannel(userID, channel)
	if err != nil {
		return s.repo.Upsert(&domain.NotificationPreference{
			UserID:    userID,
			Channel:   channel,
			Enabled:   true,
			MaxPerDay: maxPerDay,
		})
	}

	pref.MaxPerDay = maxPerDay
	return s.repo.Upsert(pref)
}

// ToggleChannel 切换渠道开关
func (s *PreferenceService) ToggleChannel(userID, channel string, enabled bool) error {
	pref, err := s.repo.GetByChannel(userID, channel)
	if err != nil {
		return s.repo.Upsert(&domain.NotificationPreference{
			UserID:  userID,
			Channel: channel,
			Enabled: enabled,
		})
	}

	pref.Enabled = enabled
	return s.repo.Upsert(pref)
}