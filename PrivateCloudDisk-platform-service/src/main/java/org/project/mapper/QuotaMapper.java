package org.project.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.QuotaEntity;

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
}
