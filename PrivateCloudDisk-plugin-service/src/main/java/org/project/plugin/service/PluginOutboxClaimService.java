package org.project.plugin.service;

import lombok.RequiredArgsConstructor;
import org.project.plugin.model.PluginOutboxRow;
import org.project.plugin.repository.PluginManagementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 插件 Outbox 多实例互斥领取。 */
@Service
@RequiredArgsConstructor
public class PluginOutboxClaimService {
    private final PluginManagementMapper mapper;

    @Transactional
    public PluginOutboxRow claim() {
        String id = mapper.selectOutboxForUpdate();
        if (id == null || mapper.claimOutbox(id) != 1) {
            return null;
        }
        return mapper.findOutbox(id);
    }
}
