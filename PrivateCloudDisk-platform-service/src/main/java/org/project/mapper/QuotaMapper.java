package org.project.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.QuotaEntity;

import java.util.List;
import java.util.UUID;

@Mapper
public interface QuotaMapper {

    /**
     * 根据用户ID查询用户配额
     * @param user_id 用户ID
     * @return QuotaEntity 用户配额数据
     */
    QuotaEntity findQuotaByUserId(@Param("user_id") UUID user_id);

    /**
     * 插入QuotaEntity
     * @param quotaData quota数据
     * @return 受变动行数
     */
    int insertQuota(QuotaEntity quotaData);
    /**
     * 更新QuotaEntity
     * @param quotaData quota数据
     * @return 受变动行数
     */
    int updateQuota(QuotaEntity quotaData);
    /**
     * 更新用户Quota网盘已用容量
     * @param used_capacity 已经使用的容量
     * @param user_id 用户ID
     * @return 受变动行数
     */
    int updateQuotaUsedCapacity(@Param("used_capacity") Long used_capacity, @Param("user_id") UUID user_id);
    /**
     * 更新用户Quota文件数量
     * @param file_count 文件数量
     * @param user_id 用户ID
     * @return 受变动行数
     */
    int updateQuotaFileCount(@Param("file_count") Integer file_count, @Param("user_id") UUID user_id);
    /**
     * 更新用户Quota网盘总容量
     * @param total_capacity 总容量
     * @param user_id 用户ID
     * @return 受变动行数
     */
    int updateQuotaTotalCapacity(@Param("total_capacity") Long total_capacity, @Param("user_id") UUID user_id);
    /**
     * 删除用户Quota
     * @param user_id 用户ID
     * @return 受变动行数
     */
    int deleteQuotaByUserId(@Param("user_id") UUID user_id);
    /**
     *
     * @param size
     * @param user_id
     * @return
     */
    int increaseQuotaUsedCapacity(@Param("size") Long size, @Param("user_id") UUID user_id);
    /**
     *
     * @param size
     * @param user_id
     * @return
     */
    int increaseQuotaUsedCapacityByFileSize(@Param("size") Long size, @Param("user_id") UUID user_id);
    /**
     *
     * @param size
     * @param user_id
     * @return
     */
    int decreaseQuotaUsedCapacity(@Param("size") Long size, @Param("user_id") UUID user_id);

    /**
     *
     * @param size
     * @param user_id
     * @return
     */
    int decreaseQuotaUsedCapacityByFileSize(@Param("size") Long size, @Param("user_id") UUID user_id, @Param("version") Integer version);

    // ==================== 预占+提交模式：released 容量操作 ====================

    /**
     * 预占配额容量（released += size）
     * <p>乐观锁：version 必须匹配。事务内先查 quota，再调用此方法。
     * <p>校验：total - (used + released + size) >= 0，否则更新0行。
     *
     * @param size    预占的字节数
     * @param user_id 用户ID
     * @param version 当前乐观锁版本号
     * @return 受影响行数（1=成功，0=容量不足或版本冲突）
     */
    int increaseQuotaReleasedCapacity(@Param("size") Long size, @Param("user_id") UUID user_id, @Param("version") Integer version);

    /**
     * 提交配额预占（released -= size, used += size, file_count += 1）
     * <p>文件正式可用时调用，将预占容量转为已用容量。
     *
     * @param size    文件大小
     * @param user_id 用户ID
     * @param version 当前乐观锁版本号
     * @return 受影响行数
     */
    int commitQuotaReleasedToUsed(@Param("size") Long size, @Param("user_id") UUID user_id, @Param("version") Integer version);

    /**
     * 回滚配额预占（released -= size）
     * <p>合并失败、扫毒失败、上传取消、上传过期时调用，释放预占容量。
     *
     * @param size    释放的字节数
     * @param user_id 用户ID
     * @param version 当前乐观锁版本号
     * @return 受影响行数
     */
    int decreaseQuotaReleasedCapacity(@Param("size") Long size, @Param("user_id") UUID user_id, @Param("version") Integer version);

    /**
     * 查询所有用户ID（用于配额对账）
     * @return 用户ID列表
     */
    List<UUID> findAllUserIds();
}
