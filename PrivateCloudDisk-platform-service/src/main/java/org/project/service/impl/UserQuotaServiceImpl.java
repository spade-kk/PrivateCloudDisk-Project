package org.project.service.impl;


import lombok.extern.slf4j.Slf4j;
import org.project.mapper.QuotaMapper;
import org.project.model.entity.QuotaEntity;
import org.project.service.UserQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class UserQuotaServiceImpl implements UserQuotaService {

    @Autowired
    private QuotaMapper quotaMapper;

    @Override
    public QuotaEntity findUserQuotaInfo(UUID user_id) {
        return null;
    }

    @Override
    public void increaseUserUsedQuotaByFileId(UUID file_id, UUID user_id) {

    }

    @Override
    public void decreaseUserUsedQuotaByFileId(UUID file_id, UUID user_id) {

    }
    @Override
    public void increaseUserUsedQuotaBySize(Long size, UUID user_id) {

    }
    @Override
    public void decreaseUserUsedQuotaBySize(Long size, UUID user_id) {

    }
    @Override
    public Long getAvailableUserQuota(UUID user_id) {
        return null;
    }
}
