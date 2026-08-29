// router/policy_test.go：回执通知防闭环策略单元测试。
package router

import (
	"testing"

	pb "privateclouddisk/im-router/pkg/proto"
)

func TestShouldNotifyMessageType(t *testing.T) {
	tests := []struct {
		name string
		mt   pb.MessageType
		want bool
	}{
		{"普通聊天消息应回执", pb.MessageType_CHAT_MESSAGE, true},
		{"未指定类型（旧消息兼容）应回执", pb.MessageType_MESSAGE_TYPE_UNSPECIFIED, true},
		{"回执自身不应再回执（防闭环）", pb.MessageType_RECEIPT, false},
		{"错误消息不应回执", pb.MessageType_ERROR_MESSAGE, false},
		{"系统通知不应回执", pb.MessageType_SYSTEM_NOTIFICATION, false},
		{"自定义通知不应回执", pb.MessageType_CUSTOM_NOTIFICATION, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := shouldNotifyMessageType(tt.mt); got != tt.want {
				t.Errorf("shouldNotifyMessageType(%v) = %v，期望 %v", tt.mt, got, tt.want)
			}
		})
	}
}

func TestShouldStoreOfflineMessageType(t *testing.T) {
	if shouldStoreOfflineMessageType(pb.MessageType_CUSTOM_NOTIFICATION) {
		t.Fatal("正在输入等临时通知不得进入离线队列")
	}
	if !shouldStoreOfflineMessageType(pb.MessageType_CHAT_MESSAGE) {
		t.Fatal("普通聊天消息必须支持离线补偿")
	}
}
