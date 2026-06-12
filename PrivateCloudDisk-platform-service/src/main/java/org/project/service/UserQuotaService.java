package org.project.service;

import org.project.model.entity.QuotaEntity;

import java.util.UUID;

public interface UserQuotaService {
    /**
     *
     * @param user_id
     * @return
     */
    QuotaEntity findUserQuotaInfo(UUID user_id);
    /**
     *
     * @param file_id
     * @param user_id
     */
    void increaseUserUsedQuotaByFileId(UUID file_id, UUID user_id);
    /**
     *
     * @param file_id
     * @param user_id
     */
    void decreaseUserUsedQuotaByFileId(UUID file_id, UUID user_id);
    /**
     *
     * @param size
     * @param user_id
     */
    void increaseUserUsedQuotaBySize(Long size, UUID user_id);
    /**
     *
     * @param size
     * @param user_id
     */
    void decreaseUserUsedQuotaBySize(Long size, UUID user_id);
    /**
     *
     * @param user_id
     * @return
     */
    Long getAvailableUserQuota(UUID user_id);
}
