import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

const read = path => readFile(new URL(`../${path}`, import.meta.url), 'utf8')
const readIm = path => readFile(new URL(`../../PrivateCloudDisk-im/${path}`, import.meta.url), 'utf8')

test('前端消息枚举必须与权威 Protobuf 编号一致', async () => {
  const [proto, types] = await Promise.all([
    read('src/api/im/proto/im_protocol_v2.proto'),
    read('src/api/im/types.ts'),
  ])
  const expected = {
    TEXT: 1, IMAGE: 2, FILE: 3, VOICE: 4, VIDEO: 5, STICKER: 10,
    LOCATION: 11, REPLY: 12, SYSTEM_NOTICE: 50, READ_RECEIPT: 51,
    MSG_TYPING: 52, CUSTOM: 100,
  }
  for (const [name, value] of Object.entries(expected)) {
    assert.match(proto, new RegExp(`\\b${name}\\s*=\\s*${value}\\s*;`))
    const frontendName = name === 'MSG_TYPING' ? 'TYPING' : name
    assert.match(types, new RegExp(`\\b${frontendName}\\s*=\\s*${value}\\s*,`))
  }
})

test('受保护消息中心路由使用重构页面且好友管理只调用 IM Business 契约', async () => {
  const [router, store, friendApi, addFriend, requests] = await Promise.all([
    read('src/router/index.ts'),
    read('src/stores/messageCenterStore.ts'),
    read('src/api/im/friendApi.ts'),
    read('src/views/im/AddFriendView.vue'),
    read('src/views/im/FriendRequestsView.vue'),
  ])
  assert.match(router, /MessageCenterView\.vue/)
  assert.match(router, /\/im\/add-friend/)
  assert.match(router, /\/im\/friend-requests/)
  assert.match(friendApi, /IM_FRIENDS_BASE\s*=\s*'im\/friends'/)
  assert.doesNotMatch(friendApi, /users\/search/)
  assert.match(addFriend, /searchSpaceUsersApi/)
  assert.match(friendApi, /IM_FRIENDS_BASE}\/requests\/incoming/)
  assert.match(friendApi, /IM_FRIENDS_BASE}\/requests\/pending\/count/)
  assert.doesNotMatch(friendApi, /im\/friend-requests/)
  assert.match(addFriend, /setTimeout\(\(\)=>void search\(\),300\)/)
  assert.match(requests, /acceptFriendRequestApi/)
  assert.match(requests, /cancelFriendRequestApi/)
  assert.match(store, /startFriendRequestPolling/)
  assert.match(store, /scheduledAt/)
  assert.match(store, /sendQueue/)
  assert.match(store, /AbortController/)
})

test('企业消息中心四个职责组件均存在关键交互契约', async () => {
  const [conversation, messages, composer, detail, stickerCatalog] = await Promise.all([
    read('src/components/im/message-center/ConversationList.vue'),
    read('src/components/im/message-center/MessageList.vue'),
    read('src/components/im/message-center/MessageComposer.vue'),
    read('src/components/im/message-center/DetailPanel.vue'),
    read('src/utils/platformStickerCatalog.ts'),
  ])
  assert.match(conversation, /contextmenu/)
  assert.match(conversation, /visibleItems/)
  assert.match(messages, /load-older/)
  assert.match(messages, /content-visibility:auto/)
  assert.match(composer, /compositionstart/)
  assert.match(composer, /handlePaste/)
  assert.match(composer, /openFilePicker/)
  // AUDIT FIX [2.1-2.2,3.1,4.5,5.10] / IM-EMOJI-SESSION-20260810：旧断言要求
  // “尚无好友关系模型”，与本次已落地的好友关系/同步会话机制相冲突。新契约验证无边框
  // 输入、两类表情隔离，以及联系人只打开已由好友接受事务创建的既有会话。
  assert.match(composer, /emoji-picker-element/)
  assert.match(composer, /border:none/)
  assert.match(composer, /MessageType\.STICKER|type:'sticker'/)
  assert.match(stickerCatalog, /@giphy\/js-fetch-api/)
  assert.match(detail, /好友申请接受后会同步创建双方会话/)
  assert.match(detail, /open-friend/)
  assert.doesNotMatch(detail, /start-chat/)
})

test('IM 附件只持久化文件 ID，并在访问时使用短期授权', async () => {
  const [composer, messages, access] = await Promise.all([
    read('src/components/im/message-center/MessageComposer.vue'),
    read('src/components/im/message-center/MessageList.vue'),
    read('src/utils/imAttachmentAccess.ts'),
  ])
  assert.match(composer, /diskFileId:result\.fileId/)
  assert.doesNotMatch(composer, /download_grant|preview_grant/)
  assert.match(messages, /v-observe-preview/)
  assert.match(access, /createDownloadGrantApi/)
  assert.match(access, /fetchPreviewContentBlob/)
  assert.match(access, /releaseDownloadGrantApi/)
  assert.match(access, /cancelDownloadGrantApi/)
})

test('IM 协议 schema 必须使用完整 protobufjs，消息缓存不得直接克隆响应式对象', async () => {
  const [schema, codec, cache] = await Promise.all([
    read('src/api/im/protocol/protoSchema.ts'),
    read('src/api/im/protocol/IMProtocolCodec.ts'),
    read('src/utils/messageCache.ts'),
  ])
  assert.match(schema, /from ['"]protobufjs['"]/) 
  assert.doesNotMatch(schema, /from ['"]protobufjs\/minimal['"]/) 
  assert.match(schema, /protobuf\.Root\(\)/)
  assert.match(codec, /Protobuf deserialization failed: /)
  assert.match(cache, /toRaw\(message\)/)
  assert.match(cache, /toCachedMessage\(/)
  assert.match(cache, /waitForTransaction\(tx\)/)
  assert.doesNotMatch(cache, /const cached: CachedMessage = \{\s*\.\.\.message/)
})

test('V2 WebSocket 编解码与 test-im-client 的 Layer 2/CallPayload 契约一致', async () => {
  const [codec, crypto, client, types, webrtc, useCall] = await Promise.all([
    read('src/api/im/protocol/IMProtocolCodec.ts'),
    read('src/api/im/protocol/IMCryptoCodec.ts'),
    read('src/api/im/ImWebSocketClient.ts'),
    read('src/api/im/types.ts'),
    read('src/api/im/WebRTCService.ts'),
    read('src/composables/useCall.ts'),
  ])
  // test-im-client: Envelope decode 后必须先按 messageType 解密 encryptedPayload。
  assert.match(codec, /deriveLayer2KeyForType\(sessionKeys\.sessionKeyBytes, typeName\)/)
  assert.match(codec, /decryptLayer2\(envelope\.encryptedPayload, layer2Key\)/)
  assert.match(crypto, /13:\s*'VOICE_CALL'/)
  assert.match(crypto, /14:\s*'VIDEO_CALL'/)
  assert.match(crypto, /93:\s*'RECEIPT'/)
  // 后端 MessageTypeDispatcher 的通话 Payload 是 CallPayload，不是旧的 WebRTC schema。
  assert.match(client, /CallPayloadType\(\)/)
  assert.match(client, /toCallPayload\(/)
  assert.doesNotMatch(client, /WebRTCSignalingPayloadType\(\)/)
  // V2 proto 的 ICE 配置命令是 2501；旧 Java legacy 枚举 2601 不得进入 Web 发送链路。
  assert.match(types, /CALL_ICE_SERVERS\s*=\s*2501/)
  assert.doesNotMatch(useCall, /sendSignaling\(2601/)
  assert.doesNotMatch(webrtc, /sendSignaling\(2104/)
})

test('群聊工作区仅使用已定义的 IM Business REST 契约，并保持群消息走 V2 通道', async () => {
  const [router, groups, panel, store, composer, messages] = await Promise.all([
    read('src/router/index.ts'),
    read('src/api/im/groupApi.ts'),
    read('src/components/im/message-center/GroupPanel.vue'),
    read('src/stores/messageCenterStore.ts'),
    read('src/components/im/message-center/MessageComposer.vue'),
    read('src/components/im/message-center/MessageList.vue'),
  ])
  assert.match(router, /\/im\/create-group/)
  assert.match(groups, /const IM_GROUPS_BASE = 'im\/groups'/)
  assert.match(groups, /\$\{IM_GROUPS_BASE\}\/\$\{groupId\}\/members/)
  assert.match(panel, /inviteGroupMembersApi/)
  assert.match(panel, /setGroupMemberRoleApi/)
  assert.match(store, /startGroupPolling/)
  assert.match(store, /openGroupConversation/)
  assert.match(store, /conversationType === ConversationType\.GROUP/)
  assert.match(composer, /mentionCandidates/)
  assert.match(composer, /insertMention/)
  assert.match(messages, /showGroupSender/)
})

test('IM Business 群组端点支持 Snowflake 群 ID，并在解散后拒绝继续发送', async () => {
  const [controller, groupService, messageService, messageDto, groupNoticePublisher] = await Promise.all([
    readIm('im-platform/src/main/java/org/project/im/platform/controller/GroupController.java'),
    readIm('im-platform/src/main/java/org/project/im/platform/service/impl/GroupServiceImpl.java'),
    readIm('im-platform/src/main/java/org/project/im/platform/service/impl/MessageServiceImpl.java'),
    readIm('im-common/src/main/java/org/project/im/common/dto/MessageDTO.java'),
    readIm('im-platform/src/main/java/org/project/im/platform/service/GroupSystemNoticePublisher.java'),
  ])
  assert.match(controller, /@PostMapping\(consumes = MediaType\.APPLICATION_JSON_VALUE\)/)
  assert.match(controller, /@GetMapping\("\/\{groupId\}"\)/)
  assert.match(controller, /@DeleteMapping\("\/\{groupId\}"\)/)
  assert.doesNotMatch(controller, /UUID_REGEX/)
  assert.match(groupService, /ensureConversationForParticipants\(memberId, groupId, ConversationIdGenerator\.GROUP\)/)
  assert.match(messageService, /groupMapper\.selectByGroupId\(receiverId\) == null/)
  // GROUP-CHAT-20260810 [3.21/6]：群聊 receiverId 是 Snowflake groupId，不能恢复 UUID
  // Pattern；这里要求 @NotBlank 与字段相邻，确保 Bean Validation 不会先行拒绝群消息。
  assert.match(messageDto, /@NotBlank\(message = "接收者ID不能为空"\)\s*private String receiverId;/)
  assert.match(groupService, /groupSystemNoticePublisher\.publishAfterCommit/)
  assert.match(groupNoticePublisher, /SYSTEM_NOTICE_VALUE/)
  assert.match(groupNoticePublisher, /afterCommit\(\)/)
})
