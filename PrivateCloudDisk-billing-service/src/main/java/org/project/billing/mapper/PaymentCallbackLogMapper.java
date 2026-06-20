package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.PaymentCallbackLogEntity;

import java.util.List;

@Mapper
public interface PaymentCallbackLogMapper {

    int insert(PaymentCallbackLogEntity log);

    int updateStatus(@Param("id") Long id, @Param("callbackStatus") String callbackStatus,
                     @Param("errorMessage") String errorMessage);

    int incrementRetryCount(@Param("id") Long id);

    List<PaymentCallbackLogEntity> findFailedLogs(@Param("limit") int limit);
}