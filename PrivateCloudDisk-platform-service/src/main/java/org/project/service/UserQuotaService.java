package org.project.service;

import org.project.model.entity.QuotaEntity;

import java.util.UUID;

/**
 * 用户配额服务
 * <p>
 * 采用「预占 + 提交」模式解决异步上传与配额强一致性矛盾：
 * <pre>
 *   available = total - (used + released)
 *
 *   上传会话创建 → preCommitQuota（released += fileSize）
 *   文件可获得     → commitQuota    （released -= fileSize, used += fileSize）
 *   合并失败/扫毒失败/取消/超时 → rollbackQuota（released -= fileSize）
 * </pre>
 */
public interface UserQuotaService {

    /**
     * 查询用户配额信息
     */
    QuotaEntity findUserQuotaInfo(UUID user_id);

    /**
     * 获取用户可用容量（available = total - used - released）
     */
    Long getAvailableUserQuota(UUID user_id);

    // ==================== 预占+提交模式 ====================

    /**
     * 预占配额容量（released += fileSize）
     * <p>上传会话创建时调用。校验 available >= fileSize，否则抛出 QuotaExceededException。
     * <p>使用乐观锁重试机制，最多重试 3 次。
     *
     * @param user_id  用户ID
     * @param fileSize 文件大小（字节）
     * @throws org.project.exception.QuotaExceededException 配额不足
     */
    void preCommitQuota(UUID user_id, long fileSize);

    /**
     * 提交配额预占（released -= fileSize, used += fileSize, file_count += 1）
     * <p>文件正式可获得时调用（合并+扫毒完成）。
     * <p>幂等：通过事件ID去重。
     *
     * @param user_id  用户ID
     * @param fileSize 文件大小（字节）
     */
    void commitQuota(UUID user_id, long fileSize);

    /**
     * 回滚配额预占（released -= fileSize）
     * <p>合并失败、扫毒失败、上传取消、上传超时时调用。
     * <p>幂等：通过事件ID去重。
     *
     * @param user_id  用户ID
     * @param fileSize 文件大小（字节）
     */
    void rollbackQuota(UUID user_id, long fileSize);

    /**
     * 增加已用容量（兼容旧接口）
     * @param file_id
     * @param user_id
     */
    void increaseUserUsedQuotaByFileId(UUID file_id, UUID user_id);

    /**
     * 根据文件ID减少已用容量（兼容旧接口）
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
}