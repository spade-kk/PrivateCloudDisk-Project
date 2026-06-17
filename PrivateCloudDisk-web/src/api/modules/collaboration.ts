// ============================================================
// collaboration.ts — 协作/IM 模块 API（向后兼容代理）
// ============================================================
// 将原有 collaboration API 端点代理到新的 imApi 模块，
// 同时扩展群组管理、消息撤回等新能力。
// ============================================================

export {
  getNotificationsApi,
  markNotificationReadApi,
  markAllNotificationsReadApi,
  getFriendsApi,
  searchUsersApi,
  sendFriendRequestApi,
  removeFriendApi,
  getConversationsApi,
  getConversationDetailApi,
  deleteConversationApi,
  toggleConversationTopApi,
  toggleConversationMuteApi,
  getMessageHistoryApi,
  sendMessageApi,
  recallMessageApi,
  markMessageReadApi,
  getMessageByIdApi,
  syncMessagesApi,
  createConversationApi,
  getTotalUnreadCountApi,
  createGroupApi,
  getGroupDetailApi,
  getUserGroupsApi,
  joinGroupApi,
  leaveGroupApi,
  kickMemberApi,
  muteMemberApi,
  unmuteMemberApi,
  toggleMuteAllApi,
  dissolveGroupApi,
  getGroupMembersApi,
  updateGroupAnnouncementApi,
  imHealthCheckApi,
} from '@/api/im/imApi'