package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.UsageRecordEntity;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface UsageRecordMapper {

    UsageRecordEntity findByUserIdAndDate(@Param("userId") String userId, @Param("recordDate") LocalDate recordDate);

    int insert(UsageRecordEntity record);

    int upsert(UsageRecordEntity record);

    List<UsageRecordEntity> findUnbilledRecords(@Param("beforeDate") LocalDate beforeDate);

    int markAsBilled(@Param("id") Long id, @Param("billingOrderId") Long billingOrderId);
}