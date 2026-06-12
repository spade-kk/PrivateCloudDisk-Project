import { del, get, patch, post } from '@/utils/request'

export function getNotificationsApi() {
  return get('business/collaboration/notifications', {}, { silent: true })
}

export function markNotificationReadApi(id) {
  return patch(`business/collaboration/notifications/${id}/read`, {}, { silent: true })
}

export function markAllNotificationsReadApi() {
  return patch('business/collaboration/notifications/read-all', {}, { silent: true })
}

export function getFriendsApi() {
  return get('business/collaboration/friends', {}, { silent: true })
}

export function searchUsersApi(keyword) {
  return get('business/collaboration/users/search', { keyword }, { silent: true })
}

export function sendFriendRequestApi(account, remark = '') {
  return post('business/collaboration/friend-requests', { account, remark }, { silent: true })
}

export function removeFriendApi(friendId) {
  return del(`business/collaboration/friends/${friendId}`, {}, { silent: true })
}

export function getConversationsApi() {
  return get('business/collaboration/conversations', {}, { silent: true })
}

export function getConversationMessagesApi(conversationId, params = {}) {
  return get(`business/collaboration/conversations/${conversationId}/messages`, params, { silent: true })
}

export function sendChatMessageApi(conversationId, payload) {
  return post(`business/collaboration/conversations/${conversationId}/messages`, payload, { silent: true })
}

export function createDirectConversationApi(friendId) {
  return post('business/collaboration/conversations/direct', { friend_id: friendId }, { silent: true })
}
