package org.project.workflow.service;

import org.project.workflow.model.WorkflowModels.ExecutionRow;
import org.project.workflow.repository.ExecutionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 多实例 Worker 使用行锁和条件更新抢占执行，避免同一实例被重复运行。 */
@Service
public class ExecutionClaimService {
    private final ExecutionMapper mapper;

    public ExecutionClaimService(ExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public ExecutionRow claimNext() {
        String executionId = mapper.selectNextQueuedForUpdate();
        if (executionId == null || mapper.claim(executionId) != 1) {
            return null;
        }
        return mapper.findById(executionId);
    }
}
