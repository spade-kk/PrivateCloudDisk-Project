package org.project.scheduler.service;

import org.project.scheduler.model.SchedulerModels.OutboxRow;
import org.project.scheduler.repository.SchedulerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Outbox 多实例互斥领取。 */
@Service
public class OutboxClaimService {
    private final SchedulerMapper mapper;

    public OutboxClaimService(SchedulerMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public OutboxRow claim() {
        String id = mapper.selectOutboxForUpdate();
        if (id == null || mapper.claimOutbox(id) != 1) {
            return null;
        }
        return mapper.findOutbox(id);
    }
}
