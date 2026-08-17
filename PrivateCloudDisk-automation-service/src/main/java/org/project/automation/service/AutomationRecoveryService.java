package org.project.automation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.automation.model.InboxRow;
import org.project.automation.repository.AutomationInboxMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Inbox Lease 恢复器。
 *
 * <p>消费者进程在接收 MQ 后、写 processed Outbox 前崩溃时，原消息可能已由连接关闭
 * 重投，也可能存在重复发布场景。本恢复器直接使用 Inbox 中的原始 payload 再驱动，
 * 与 Rabbit 重投共同保证恢复，候选提交和 Storage Gate 仍保持幂等。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationRecoveryService {
    private final AutomationInboxMapper inboxMapper;
    private final AutomationExecutionService executionService;

    @Scheduled(fixedDelayString = "${pcd.inbox-recovery-poll-ms:5000}")
    public void recoverExpiredInbox() {
        for (InboxRow row : inboxMapper.findExpired(20)) {
            try {
                /*
                 * 云插件 MVP 恢复修复：
                 * 原行为把所有过期 Inbox 都按 content.ready 解析，file.available 恢复必然失败。
                 * 新行为按持久化事件类型分派，保持两类入口的权限和可写语义互不混淆。
                 */
                ClaimOutcome outcome = switch (row.eventType()) {
                    case "pcd.file.content.ready.v1" ->
                            executionService.processRaw(row.payloadJson());
                    case "pcd.file.available.v1" ->
                            executionService.processAvailableRaw(row.payloadJson());
                    default -> throw new IllegalArgumentException(
                            "不支持恢复的 Automation Inbox 类型: " + row.eventType()
                    );
                };
                log.warn("已恢复过期 Automation Inbox eventId={} outcome={}",
                        row.eventId(), outcome);
            } catch (Exception exception) {
                log.error("恢复 Automation Inbox 失败 eventId={}", row.eventId(), exception);
            }
        }
    }
}
