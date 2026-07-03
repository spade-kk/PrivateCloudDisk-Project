// Package service 提供通知服务的核心业务逻辑。
package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"time"

	"github.com/privateclouddisk/notification-service/internal/channel"
	"github.com/privateclouddisk/notification-service/internal/config"
	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/repository"
)

// ChannelSender 渠道发送接口（避免循环依赖）
type ChannelSender interface {
	Send(ctx context.Context, channelName string, recipient string, msg *channel.Message) (*channel.SendResult, error)
	Dispatch(ctx context.Context, channelName string, msg *channel.Message,
		deviceTokens []string, email string, phone string) (*channel.SendResult, error)
}

// NotificationService 通知核心服务
type NotificationService struct {
	cfg              *config.Config
	templateRepo     *repository.TemplateRepo
	notificationRepo *repository.NotificationRepo
	deliveryLogRepo  *repository.DeliveryLogRepo
	preferenceRepo   *repository.PreferenceRepo
	deviceRepo       *repository.DeviceRepo
	aggregationRepo  *repository.AggregationRepo
	channelSender    ChannelSender
	wsHub            WSHubPublisher // WS Hub 推送接口
}

// WSHubPublisher WS Hub 推送接口（避免循环依赖）
type WSHubPublisher interface {
	PushMessage(userID string, msg *domain.WSSystemMessage, cacheStrategy domain.WSCacheStrategy)
}

// NewNotificationService 创建通知服务
func NewNotificationService(
	cfg *config.Config,
	templateRepo *repository.TemplateRepo,
	notificationRepo *repository.NotificationRepo,
	deliveryLogRepo *repository.DeliveryLogRepo,
	preferenceRepo *repository.PreferenceRepo,
	deviceRepo *repository.DeviceRepo,
	aggregationRepo *repository.AggregationRepo,
	channelSender ChannelSender,
	wsHub WSHubPublisher,
) *NotificationService {
	return &NotificationService{
		cfg:              cfg,
		templateRepo:     templateRepo,
		notificationRepo: notificationRepo,
		deliveryLogRepo:  deliveryLogRepo,
		preferenceRepo:   preferenceRepo,
		deviceRepo:       deviceRepo,
		aggregationRepo:  aggregationRepo,
		channelSender:    channelSender,
		wsHub:            wsHub,
	}
}

// ProcessEvent 处理通知事件（核心入口）
func (s *NotificationService) ProcessEvent(event *domain.NotificationEvent) error {
	log.Printf("[Notification] 处理事件: eventID=%s, type=%s, userID=%s, channels=%v",
		event.EventID, event.EventType, event.UserID, event.Channels)

	// 1. 确定发送渠道
	channels := s.determineChannels(event)

	// 2. 检查用户偏好（免打扰、渠道开关、每日限额）
	channels = s.filterByPreferences(event.UserID, channels)

	if len(channels) == 0 {
		log.Printf("[Notification] 无可用渠道，跳过: eventID=%s", event.EventID)
		return nil
	}

	// 3. 对每个渠道执行发送
	var lastErr error
	for _, ch := range channels {
		if err := s.sendViaChannel(event, ch); err != nil {
			log.Printf("[Notification] 渠道 %s 发送失败: eventID=%s, error=%v", ch, event.EventID, err)
			lastErr = err
		}
	}

	return lastErr
}

// determineChannels 确定发送渠道
func (s *NotificationService) determineChannels(event *domain.NotificationEvent) []string {
	// 如果事件明确指定了渠道，优先使用
	if len(event.Channels) > 0 {
		return event.Channels
	}

	// 根据事件类型自动选择渠道
	switch event.EventType {
	case "user_registered":
		channels := []string{}
		if event.Email != "" {
			channels = append(channels, string(domain.ChannelEmail))
		}
		if event.Phone != "" {
			channels = append(channels, string(domain.ChannelSMS))
		}
		return channels
	case "email_verification":
		return []string{string(domain.ChannelEmail)}
	case "phone_verification":
		return []string{string(domain.ChannelSMS)}
	case "share_notify":
		return []string{string(domain.ChannelPush)}
	case "system_notify":
		return []string{string(domain.ChannelPush), string(domain.ChannelEmail)}
	default:
		return []string{string(domain.ChannelPush)}
	}
}

// filterByPreferences 根据用户偏好过滤渠道
func (s *NotificationService) filterByPreferences(userID string, channels []string) []string {
	if userID == "" {
		return channels
	}

	prefs, err := s.preferenceRepo.GetByUserID(userID)
	if err != nil {
		log.Printf("[Notification] 获取用户偏好失败，使用默认: userID=%s, error=%v", userID, err)
		return channels
	}

	// 构建偏好映射
	prefMap := make(map[string]*domain.NotificationPreference)
	for i := range prefs {
		prefMap[prefs[i].Channel] = &prefs[i]
	}

	var filtered []string
	now := time.Now()
	currentTime := now.Format("15:04")

	for _, ch := range channels {
		pref, ok := prefMap[ch]

		// 无偏好配置，默认允许
		if !ok {
			filtered = append(filtered, ch)
			continue
		}

		// 渠道被禁用
		if !pref.Enabled {
			log.Printf("[Notification] 渠道 %s 已禁用: userID=%s", ch, userID)
			continue
		}

		// 免打扰检查
		if pref.DNDEnabled && s.isInDNDPeriod(pref, currentTime) {
			log.Printf("[Notification] 免打扰时段: userID=%s, channel=%s, time=%s", userID, ch, currentTime)
			continue
		}

		// 每日限额检查
		if pref.MaxPerDay > 0 {
			dailyCount, err := s.notificationRepo.GetDailyCount(userID, ch)
			if err == nil && dailyCount >= pref.MaxPerDay {
				log.Printf("[Notification] 已达每日限额: userID=%s, channel=%s, count=%d/%d",
					userID, ch, dailyCount, pref.MaxPerDay)
				continue
			}
		}

		filtered = append(filtered, ch)
	}

	return filtered
}

// isInDNDPeriod 检查是否在免打扰时段
func (s *NotificationService) isInDNDPeriod(pref *domain.NotificationPreference, currentTime string) bool {
	if pref.DNDStart == "" || pref.DNDEnd == "" {
		return false
	}

	// 跨天免打扰 (如 22:00 - 07:00)
	if pref.DNDStart > pref.DNDEnd {
		return currentTime >= pref.DNDStart || currentTime <= pref.DNDEnd
	}

	// 同天免打扰 (如 13:00 - 14:00)
	return currentTime >= pref.DNDStart && currentTime <= pref.DNDEnd
}

// sendViaChannel 通过指定渠道发送通知
func (s *NotificationService) sendViaChannel(event *domain.NotificationEvent, channelName string) error {
	// 1. 幂等检查
	exists, err := s.notificationRepo.ExistsByEventID(event.EventID, channelName)
	if err != nil {
		return fmt.Errorf("幂等检查失败: %w", err)
	}
	if exists {
		log.Printf("[Notification] 事件已处理，跳过: eventID=%s, channel=%s", event.EventID, channelName)
		return nil
	}

	// 2. 获取模板（根据 TemplateSource 分发）
	lang := event.TemplateLang
	if lang == "" {
		lang = "zh-CN"
	}
	templateCode := event.TemplateCode
	if templateCode == "" {
		templateCode = s.getDefaultTemplateCode(event.EventType)
	}

	var template *domain.Template
	switch event.TemplateSource {
	case string(domain.TemplateSourceRaw):
		// 从事件原始模板构造
		if event.RawTemplate != nil {
			template = &domain.Template{
				Title:    event.RawTemplate.Title,
				Body:     event.RawTemplate.Body,
				HTMLBody: event.RawTemplate.HTMLBody,
			}
		}
	case string(domain.TemplateSourceFile):
		// 从嵌入模板文件加载
		template, err = s.templateRepo.GetByCodeFromFile(templateCode, channelName, lang)
		if err != nil {
			log.Printf("[Notification] 从文件加载模板失败，回退到数据库: eventID=%s, channel=%s, error=%v",
				event.EventID, channelName, err)
			template, err = s.templateRepo.GetByCodeFallback(templateCode, channelName, lang)
		}
	default:
		// 默认从数据库模板表加载
		template, err = s.templateRepo.GetByCodeFallback(templateCode, channelName, lang)
	}

	if err != nil {
		log.Printf("[Notification] 获取模板失败，使用原始内容: eventID=%s, channel=%s, error=%v",
			event.EventID, channelName, err)
		template = nil
	}

	// 3. 渲染内容（包括 HTML body）
	title, body, htmlBody := s.renderContent(event, template)

	// 4. 确定接收者
	recipient := s.determineRecipient(event, channelName)

	// 5. 创建通知记录
	record := &domain.NotificationRecord{
		EventID:      event.EventID,
		UserID:       event.UserID,
		Channel:      channelName,
		Type:         event.EventType,
		Title:        title,
		Body:         body,
		Recipient:    recipient,
		TemplateCode: event.TemplateCode,
		Status:       domain.StatusPending,
		Priority:     event.Priority,
		MaxRetries:   s.cfg.Worker.RetryMaxAttempts,
	}
	recordID, err := s.notificationRepo.Insert(record)
	if err != nil {
		return fmt.Errorf("创建通知记录失败: %w", err)
	}

	// 6. 发送（通过 ChannelManager 实际发送）
	startTime := time.Now()
	sendErr := s.dispatchToChannel(event, channelName, title, body, htmlBody, recipient, recordID)

	// 7. 记录送达日志
	durationMs := time.Since(startTime).Milliseconds()
	deliveryStatus := domain.StatusSent
	errMsg := ""
	if sendErr != nil {
		deliveryStatus = domain.StatusFailed
		errMsg = sendErr.Error()
		s.notificationRepo.MarkFailed(recordID, errMsg)
	} else {
		s.notificationRepo.MarkSent(recordID)
	}

	deliveryLog := &domain.DeliveryLog{
		NotificationID: recordID,
		EventID:        event.EventID,
		Channel:        channelName,
		Status:         deliveryStatus,
		ErrorMsg:       errMsg,
		DurationMs:     durationMs,
	}
	s.deliveryLogRepo.Insert(deliveryLog)

	return sendErr
}

// dispatchToChannel 分发到具体渠道（通过 ChannelManager 实际发送）
func (s *NotificationService) dispatchToChannel(
	event *domain.NotificationEvent, channelName, title, body, htmlBody, recipient string, recordID int64,
) error {
	// WS 渠道：通过 Hub 推送（不经过传统 channel sender）
	if channelName == "ws" {
		return s.dispatchToWS(event, title, body, recordID)
	}

	if s.channelSender == nil {
		log.Printf("[Notification] ChannelSender 未初始化，跳过实际发送: recordID=%d", recordID)
		return nil
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	msg := &channel.Message{
		Title:    title,
		Body:     body,
		HTMLBody: htmlBody,
		Data:     event.PushData,
		Priority: event.Priority,
	}

	result, err := s.channelSender.Dispatch(ctx, channelName, msg,
		event.DeviceTokens, event.Email, event.Phone)

	if err != nil {
		log.Printf("[Notification] 渠道 %s 发送失败: recordID=%d, error=%v", channelName, recordID, err)
		return err
	}

	if result != nil && !result.Success {
		log.Printf("[Notification] 渠道 %s 发送失败: recordID=%d, error=%v", channelName, recordID, result.Error)
		return result.Error
	}

	log.Printf("[Notification] 渠道 %s 发送成功: recordID=%d, msgID=%s", channelName, recordID, result.MessageID)
	return nil
}

// dispatchToWS 通过 WebSocket 系统推送
func (s *NotificationService) dispatchToWS(
	event *domain.NotificationEvent, title, body string, recordID int64,
) error {
	if s.wsHub == nil {
		log.Printf("[Notification] WS Hub 未初始化，跳过 WS 推送: recordID=%d", recordID)
		return nil
	}

	wsMsg := &domain.WSSystemMessage{
		ID:        event.EventID,
		Type:      event.EventType,
		Title:     title,
		Body:      body,
		Priority:  event.Priority,
		Data:      event.PushData,
		Timestamp: time.Now().Unix(),
	}

	cacheStrategy := domain.WSCachePersist
	switch event.WSCacheStrategy {
	case "none":
		cacheStrategy = domain.WSCacheNone
	case "persist":
		cacheStrategy = domain.WSCachePersist
	}

	s.wsHub.PushMessage(event.UserID, wsMsg, cacheStrategy)
	log.Printf("[Notification] WS 推送完成: recordID=%d, userID=%s, cache=%s", recordID, event.UserID, event.WSCacheStrategy)
	return nil
}

// getDefaultTemplateCode 根据事件类型返回默认模板编码
func (s *NotificationService) getDefaultTemplateCode(eventType string) string {
	switch eventType {
	case "user_registered":
		return "welcome_email"
	case "email_verification":
		return "verification_email"
	case "phone_verification":
		return "sms_verification"
	case "password_reset":
		return "password_reset_email"
	case "share_notify":
		return "share_notification"
	case "system_notify":
		return "system_notification"
	default:
		return ""
	}
}

// renderContent 渲染模板内容（返回 title, body, htmlBody）
func (s *NotificationService) renderContent(event *domain.NotificationEvent, template *domain.Template) (string, string, string) {
	if template == nil {
		return event.PushTitle, event.PushBody, ""
	}

	// 合并默认变量（如当前年份、支持邮箱等）
	variables := s.buildDefaultVariables(event)
	if event.Variables != nil {
		for k, v := range event.Variables {
			variables[k] = v
		}
	}

	title := template.Title
	body := template.Body
	htmlBody := template.HTMLBody

	// 简单变量替换
	for k, v := range variables {
		placeholder := fmt.Sprintf("{{.%s}}", k)
		value := fmt.Sprintf("%v", v)
		title = replaceAll(title, placeholder, value)
		body = replaceAll(body, placeholder, value)
		if htmlBody != "" {
			htmlBody = replaceAll(htmlBody, placeholder, value)
		}
	}

	// 如果没有替换成功，使用事件原始内容
	if title == "" || title == template.Title {
		if event.PushTitle != "" {
			title = event.PushTitle
		}
	}
	if body == "" || body == template.Body {
		if event.PushBody != "" {
			body = event.PushBody
		}
	}

	return title, body, htmlBody
}

// buildDefaultVariables 构建默认变量（如当前年份、支持邮箱等）
func (s *NotificationService) buildDefaultVariables(event *domain.NotificationEvent) map[string]interface{} {
	frontendURL := s.cfg.Email.FrontendURL
	if frontendURL == "" {
		frontendURL = "https://privateclouddisk.com"
	}

	defaults := map[string]interface{}{
		"CurrentYear":  time.Now().Format("2006"),
		"SupportEmail": s.cfg.Email.FromAddr,
		"HelpUrl":      frontendURL + "/help",
		"LoginUrl":     frontendURL + "/login",
	}

	// 如果事件中已有用户信息，设置默认值
	if event.UserID != "" {
		defaults["Username"] = event.UserID
	}
	if event.Email != "" {
		defaults["Email"] = event.Email
	}
	if event.Phone != "" {
		defaults["Phone"] = event.Phone
	}

	return defaults
}

// determineRecipient 确定接收者标识
func (s *NotificationService) determineRecipient(event *domain.NotificationEvent, channel string) string {
	switch channel {
	case string(domain.ChannelEmail):
		return event.Email
	case string(domain.ChannelSMS):
		return event.Phone
	case string(domain.ChannelWS):
		return event.UserID
	case string(domain.ChannelPush), string(domain.ChannelAPNs), string(domain.ChannelFCM):
		if len(event.DeviceTokens) > 0 {
			data, _ := json.Marshal(event.DeviceTokens)
			return string(data)
		}
		return ""
	default:
		return ""
	}
}

// replaceAll 替换所有匹配
func replaceAll(s, old, new string) string {
	result := s
	for i := 0; i < len(s); i++ {
		if i+len(old) <= len(result) && result[i:i+len(old)] == old {
			result = result[:i] + new + result[i+len(old):]
			i += len(new) - 1
		}
	}
	return result
}

// GetHistory 查询通知历史
func (s *NotificationService) GetHistory(userID string, offset, limit int) ([]domain.NotificationRecord, int, error) {
	return s.notificationRepo.GetHistory(userID, offset, limit)
}

// GetRecord 查询单条通知
func (s *NotificationService) GetRecord(id int64) (*domain.NotificationRecord, error) {
	return s.notificationRepo.GetByID(id)
}

// ProcessAggregation 处理聚合窗口到期
func (s *NotificationService) ProcessAggregation(ctx context.Context) error {
	windows, err := s.aggregationRepo.GetExpiredWindows()
	if err != nil {
		return fmt.Errorf("获取过期聚合窗口失败: %w", err)
	}

	for _, win := range windows {
		log.Printf("[Aggregation] 处理聚合窗口: id=%s, userID=%s, count=%d", win.ID, win.UserID, win.Count)

		// 获取聚合的通知
		records, err := s.notificationRepo.GetPendingAggregated(win.ID)
		if err != nil {
			log.Printf("[Aggregation] 获取聚合通知失败: id=%s, error=%v", win.ID, err)
			continue
		}

		if len(records) == 0 {
			s.aggregationRepo.MarkSent(win.ID)
			continue
		}

		// 构造聚合消息
		title := fmt.Sprintf("您有 %d 条新通知", len(records))
		_ = s.buildAggregationBody(records)

		// 发送聚合通知
		// TODO: 调用 ChannelManager 发送聚合消息
		log.Printf("[Aggregation] 聚合通知已发送: id=%s, title=%s, count=%d", win.ID, title, len(records))

		// 更新所有通知状态
		for _, record := range records {
			s.notificationRepo.UpdateStatus(record.ID, domain.StatusSent, "")
		}

		s.aggregationRepo.MarkSent(win.ID)
	}

	return nil
}

// buildAggregationBody 构建聚合消息正文
func (s *NotificationService) buildAggregationBody(records []domain.NotificationRecord) string {
	body := "您收到了以下通知：\n"
	for i, record := range records {
		if i >= 5 {
			body += fmt.Sprintf("...还有 %d 条通知", len(records)-5)
			break
		}
		body += fmt.Sprintf("• %s\n", record.Title)
	}
	return body
}