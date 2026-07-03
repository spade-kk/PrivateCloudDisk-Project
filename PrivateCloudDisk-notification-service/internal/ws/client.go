// Package ws 提供系统 WebSocket 推送服务。
// 与 IM Server 的 WebSocket 微服务职责不同：
//   - IM Server: 负责私有云平台子模块实时通信，有聊天记录、联系人
//   - 本 WS 服务: 仅负责系统/平台弹窗消息推送，如安全通知、系统公告
package ws

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

const (
	// 写入超时
	writeWait = 10 * time.Second

	// 心跳间隔（客户端 -> 服务端）
	pongWait = 60 * time.Second

	// 心跳间隔（服务端 -> 客户端）
	pingPeriod = 30 * time.Second

	// 最大消息大小
	maxMessageSize = 4096

	// 写缓冲区大小
	writeBufferSize = 16
)

// Client WebSocket 客户端连接
type Client struct {
	hub    *Hub
	conn   *websocket.Conn
	send   chan []byte
	userID string

	mu sync.RWMutex
}

// NewClient 创建客户端
func NewClient(hub *Hub, conn *websocket.Conn, userID string) *Client {
	return &Client{
		hub:    hub,
		conn:   conn,
		send:   make(chan []byte, writeBufferSize),
		userID: userID,
	}
}

// UserID 获取用户 ID
func (c *Client) UserID() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.userID
}

// readPump 从 WebSocket 连接读取消息（心跳）
func (c *Client) readPump() {
	defer func() {
		c.hub.unregister <- c
		c.conn.Close()
	}()

	c.conn.SetReadLimit(maxMessageSize)
	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, message, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseNormalClosure) {
				log.Printf("[WS] 读取错误: userID=%s, error=%v", c.userID, err)
			}
			break
		}

		// 处理客户端消息（心跳回复、确认等）
		c.handleClientMessage(message)
	}
}

// writePump 向 WebSocket 连接写入消息
func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			if !ok {
				// channel 关闭
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				log.Printf("[WS] 写入错误: userID=%s, error=%v", c.userID, err)
				return
			}

		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				log.Printf("[WS] Ping 错误: userID=%s, error=%v", c.userID, err)
				return
			}
		}
	}
}

// handleClientMessage 处理客户端发来的消息
func (c *Client) handleClientMessage(data []byte) {
	var msg struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(data, &msg); err != nil {
		return
	}

	switch msg.Type {
	case "ack":
		// 客户端确认收到消息
		var ack struct {
			Type      string `json:"type"`
			MessageID string `json:"message_id"`
		}
		if err := json.Unmarshal(data, &ack); err == nil && ack.MessageID != "" {
			log.Printf("[WS] 消息确认: userID=%s, msgID=%s", c.userID, ack.MessageID)
		}
	case "ping":
		// 客户端主动心跳
		select {
		case c.send <- []byte(`{"type":"pong"}`):
		default:
		}
	}
}

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true // 允许所有来源（生产环境应限制）
	},
}

// ServeWs 处理 WebSocket 升级请求
func ServeWs(hub *Hub, w http.ResponseWriter, r *http.Request) {
	// 从请求参数获取 userID（生产环境应从 JWT Token 中解析）
	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		http.Error(w, "user_id 参数缺失", http.StatusBadRequest)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[WS] 升级失败: userID=%s, error=%v", userID, err)
		return
	}

	client := NewClient(hub, conn, userID)
	hub.register <- client

	// 推送离线消息（上线后立即推送）
	go hub.pushOfflineMessages(userID)

	// 启动读写协程
	go client.writePump()
	go client.readPump()

	log.Printf("[WS] 新连接: userID=%s", userID)
}