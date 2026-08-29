// router/policy.go：回执通知防闭环策略。
//
// 回执通知（RECEIPT）本身没有"发送方"概念：它是由 IM Server 推送送达/失败
// 事件后，Router 生成并推回给消息原发送方的通知。若对通知类消息（系统通知、
// 错误消息、回执自身等）再次生成回执，会形成无限循环。
//
// 因此仅对普通聊天消息（CHAT_MESSAGE）或未指定类型的旧消息
// （MESSAGE_TYPE_UNSPECIFIED，向后兼容）生成回执。
package router

import (
	pb "privateclouddisk/im-router/pkg/proto"
)

// shouldNotifyMessageType 判断该消息类型是否应当产生回执通知。
// 返回 true 仅当类型为普通聊天消息或未指定（旧消息兼容）。
func shouldNotifyMessageType(mt pb.MessageType) bool {
	switch mt {
	case pb.MessageType_CHAT_MESSAGE, pb.MessageType_MESSAGE_TYPE_UNSPECIFIED:
		return true
	default:
		return false
	}
}

// shouldStoreOfflineMessageType 判断消息是否需要离线补偿。
// AUDIT FIX [4.5/6.15/12.4]：正在输入等 CUSTOM_NOTIFICATION 是瞬时状态，
// 原行为若统一落离线队列，会让用户重连后收到已经过期的交互提示。
func shouldStoreOfflineMessageType(mt pb.MessageType) bool {
	return mt != pb.MessageType_CUSTOM_NOTIFICATION
}
