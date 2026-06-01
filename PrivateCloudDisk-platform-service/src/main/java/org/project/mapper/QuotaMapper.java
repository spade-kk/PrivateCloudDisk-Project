package org.project.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.project.data.QuotaData;

@Mapper
public interface QuotaMapper {

    /**
     * 根据用户ID查询用户配额
     * @param user_id 用户ID
     * @return QuotaData 用户配额数据
     */
    QuotaData findQuotaByUserId(String user_id);

    /**
     * 插入QuotaData
     * @param quotaData quota数据
     * @return 受变动行数
     */
    int insertQuota(QuotaData quotaData);
    /**
     * 更新QuotaData
     * @param quotaData quota数据
     * @return 受变动行数
     */
    int updateQuota(QuotaData quotaData);
    /**
     * 更新用户Quota网盘已用容量
     * @param used_capacity 已经使用的容量
     * @param user_id 用户ID
     * @return 受变动行数
     */
    int updateQuotaUsedCapacity(Long used_capacity, String user_id);
    /**
     * 更新用户Quota文件数量
     * @param file_count 文件数量
     * @param user_id 用户ID
     * @return 受变动行数
     */
    int updateQuotaFileCount(Integer file_count, String user_id);
    /**
     * 更新用户Quota网盘总容量
     * @param total_capacity 总容量
     * @param user_id 用户ID
     * @return 受变动行数
     */
    int updateQuotaTotalCapacity(Long total_capacity, String user_id);
    /**
     * 删除用户Quota
     * @param user_id 用户ID
     * @return 受变动行数
     */
    int deleteQuotaByUserId(String user_id);
}
