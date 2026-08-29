package org.project.im.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.enums.ConversationType;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.platform.util.ConversationIdGenerator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 群组管理操作的系统消息发布器。
 *
 * <p>GROUP-CHAT-20260810 [3.17/4.11/4.12/5.19]：原群管理只改成员和群资料，客户端
 * 没有可持久化、跨端同步的“加入/退出/资料变更”记录。新行为复用既有 SYSTEM_NOTICE 消息、
 * 消息表和 IM Router 推送链路；不创建额外 MQ 拓扑，也不定义未在 V2 Protobuf 中声明的新帧。</p>
 *
 * <p>通知注册为 afterCommit 回调：群组事务失败时绝不产生幽灵系统消息；提交后的通知投递失败
 * 仅记录告警，不回滚已经一致落库的群成员及会话数据，客户端下次历史同步仍可正常工作。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupSystemNoticePublisher {

    private static final String GROUP_EVENT_EXTRA = "{\"payload\":{\"noticeType\":\"GROUP_EVENT\"}}";

    private final MessageService messageService;

    public void publishAfterCommit(String groupId, String actorId, String content) {
        Runnable publish = () -> {
            try {
                messageService.sendMessage(MessageDTO.builder()
                        .conversationId(ConversationIdGenerator.generate(actorId, groupId, ConversationIdGenerator.GROUP))
                        .conversationType(ConversationType.GROUP.getCode())
                        .messageType(IMProtocolV2.IMMessageType.SYSTEM_NOTICE_VALUE)
                        .senderId(actorId)
                        .receiverId(groupId)
                        .content(content)
                        .extra(GROUP_EVENT_EXTRA)
                        .build());
            } catch (RuntimeException exception) {
                log.warn("群系统消息投递失败: groupId={}, actorId={}, reason={}", groupId, actorId, exception.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
    }
}
