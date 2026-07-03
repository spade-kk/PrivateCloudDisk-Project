package ws

import (
	"context"
	"encoding/json"
	"log"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/privateclouddisk/notification-service/internal/domain"
)

// Hub 维护活跃的 WebSocket 连接集合
type Hub struct {
	// 注册的客户端
	clients map[string]map[*Client]bool // userID -> clients

	// 注册通道
	register chan *Client

	// 注销通道
	unregister chan *Client

	// 广播通道（按 userID 发送）
	broadcast chan *broadcastMsg

	// Redis 离线消息
	redisClient *redis.Client
	redisPrefix string

	mu sync.RWMutex
}

type broadcastMsg struct {
	UserID        string
	Message       []byte
	CacheStrategy domain.WSCacheStrategy
}

// HubConfig Hub 配置
type HubConfig struct {
	RedisClient *redis.Client
	RedisPrefix string
}

// NewHub 创建 Hub
func NewHub(cfg *HubConfig) *Hub {
	prefix := cfg.RedisPrefix
	if prefix == "" {
		prefix = "ws:offline:"
	}

	return &Hub{
		clients:     make(map[string]map[*Client]bool),
		register:    make(chan *Client, 64),
		unregister:  make(chan *Client, 64),
		broadcast:   make(chan *broadcastMsg, 256),
		redisClient: cfg.RedisClient,
		redisPrefix: prefix,
	}
}

// Run 启动 Hub 主循环
func (h *Hub) Run(ctx context.Context) {
	log.Println("[WS] Hub 已启动")
	defer log.Println("[WS] Hub 已停止")

	for {
		select {
		case client := <-h.register:
			h.registerClient(client)

		case client := <-h.unregister:
			h.unregisterClient(client)

		case msg := <-h.broadcast:
			h.handleBroadcast(msg)

		case <-ctx.Done():
			return
		}
	}
}

// registerClient 注册客户端
func (h *Hub) registerClient(client *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()

	userID := client.UserID()
	if h.clients[userID] == nil {
		h.clients[userID] = make(map[*Client]bool)
	}
	h.clients[userID][client] = true

	// 推送离线消息
	go h.pushOfflineMessages(userID)

	log.Printf("[WS] 客户端上线: userID=%s, 当前在线=%d", userID, len(h.clients[userID]))
}

// unregisterClient 注销客户端
func (h *Hub) unregisterClient(client *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()

	userID := client.UserID()
	if clients, ok := h.clients[userID]; ok {
		if _, exists := clients[client]; exists {
			delete(clients, client)
			close(client.send)
			if len(clients) == 0 {
				delete(h.clients, userID)
			}
		}
	}

	log.Printf("[WS] 客户端下线: userID=%s, 当前在线=%d", userID, len(h.GetClients(userID)))
}

// handleBroadcast 处理广播消息
func (h *Hub) handleBroadcast(msg *broadcastMsg) {
	h.mu.RLock()
	clients, ok := h.clients[msg.UserID]
	h.mu.RUnlock()

	// 用户在线 → 直接推送
	if ok && len(clients) > 0 {
		for client := range clients {
			select {
			case client.send <- msg.Message:
			default:
				// 客户端缓冲区满，跳过
				log.Printf("[WS] 客户端缓冲区满: userID=%s", msg.UserID)
			}
		}
		return
	}

	// 用户离线 → 根据缓存策略处理
	if msg.CacheStrategy == domain.WSCachePersist {
		h.storeOfflineMessage(msg.UserID, msg.Message)
	}
}

// PushMessage 推送消息到指定用户
func (h *Hub) PushMessage(userID string, msg *domain.WSSystemMessage, cacheStrategy domain.WSCacheStrategy) {
	data, err := json.Marshal(msg)
	if err != nil {
		log.Printf("[WS] 序列化消息失败: %v", err)
		return
	}

	h.broadcast <- &broadcastMsg{
		UserID:        userID,
		Message:       data,
		CacheStrategy: cacheStrategy,
	}
}

// storeOfflineMessage 存储离线消息到 Redis
func (h *Hub) storeOfflineMessage(userID string, data []byte) {
	if h.redisClient == nil {
		return
	}

	key := h.redisPrefix + userID
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	// 最多保留 50 条离线消息
	if err := h.redisClient.RPush(ctx, key, data).Err(); err != nil {
		log.Printf("[WS] 存储离线消息失败: userID=%s, error=%v", userID, err)
		return
	}
	// 限制列表长度
	h.redisClient.LTrim(ctx, key, -50, -1)

	// 设置 7 天过期
	h.redisClient.Expire(ctx, key, 7*24*time.Hour)

	log.Printf("[WS] 离线消息已存储: userID=%s", userID)
}

// pushOfflineMessages 推送离线消息给刚上线的用户
func (h *Hub) pushOfflineMessages(userID string) {
	if h.redisClient == nil {
		return
	}

	key := h.redisPrefix + userID
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// 获取所有离线消息
	messages, err := h.redisClient.LRange(ctx, key, 0, -1).Result()
	if err != nil || len(messages) == 0 {
		return
	}

	// 再次确认用户在线
	h.mu.RLock()
	clients, ok := h.clients[userID]
	h.mu.RUnlock()
	if !ok || len(clients) == 0 {
		return
	}

	log.Printf("[WS] 推送离线消息: userID=%s, count=%d", userID, len(messages))

	for _, msg := range messages {
		for client := range clients {
			select {
			case client.send <- []byte(msg):
			default:
			}
		}
	}

	// 删除已推送的离线消息
	h.redisClient.Del(ctx, key)
}

// GetClients 获取用户的所有客户端连接
func (h *Hub) GetClients(userID string) []*Client {
	h.mu.RLock()
	defer h.mu.RUnlock()

	if clients, ok := h.clients[userID]; ok {
		result := make([]*Client, 0, len(clients))
		for c := range clients {
			result = append(result, c)
		}
		return result
	}
	return nil
}

// IsOnline 检查用户是否在线
func (h *Hub) IsOnline(userID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	clients, ok := h.clients[userID]
	return ok && len(clients) > 0
}

// OnlineCount 获取在线用户数
func (h *Hub) OnlineCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}

// TotalConnections 获取总连接数
func (h *Hub) TotalConnections() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	count := 0
	for _, clients := range h.clients {
		count += len(clients)
	}
	return count
}