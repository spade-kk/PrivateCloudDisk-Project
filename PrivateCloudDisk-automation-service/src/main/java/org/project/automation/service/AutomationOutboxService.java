package org.project.automation.service;

import lombok.RequiredArgsConstructor;
import org.project.automation.model.OutboxRow;
import org.project.automation.repository.AutomationOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Outbox 领取与状态变更的短事务。 */
@Service
@RequiredArgsConstructor
public class AutomationOutboxService {
    private final AutomationOutboxMapper outboxMapper;

    @Transactional
    public List<OutboxRow> claim(int limit) {
        outboxMapper.recoverPublishing();
        List<OutboxRow> rows = outboxMapper.lockPending(limit);
        rows.forEach(row -> outboxMapper.markPublishing(row.outboxId()));
        return rows;
    }

    @Transactional
    public void markSent(String outboxId) {
        outboxMapper.markSent(outboxId);
    }

    @Transactional
    public void markFailed(String outboxId, Throwable throwable, int retryCount) {
        int delaySeconds = Math.min(1 << Math.min(retryCount, 8), 300);
        outboxMapper.markFailed(
                outboxId,
                ErrorSanitizer.summarize(throwable),
                delaySeconds
        );
    }
}

