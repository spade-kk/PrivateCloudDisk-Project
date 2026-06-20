package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.OrderEntity;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {

    OrderEntity findByOrderNo(@Param("orderNo") String orderNo);

    OrderEntity findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    int insert(OrderEntity order);

    int updateStatus(@Param("orderNo") String orderNo, @Param("status") String status);

    int updatePaymentInfo(@Param("orderNo") String orderNo,
                          @Param("paymentMethod") String paymentMethod,
                          @Param("thirdPartyTradeNo") String thirdPartyTradeNo,
                          @Param("amountPaid") java.math.BigDecimal amountPaid,
                          @Param("paidAt") LocalDateTime paidAt,
                          @Param("status") String status);

    int updateRefundInfo(@Param("orderNo") String orderNo,
                         @Param("refundAmount") java.math.BigDecimal refundAmount,
                         @Param("refundReason") String refundReason,
                         @Param("refundedAt") LocalDateTime refundedAt,
                         @Param("status") String status);

    List<OrderEntity> findByUserId(@Param("userId") String userId);

    List<OrderEntity> findExpiredPendingOrders(@Param("now") LocalDateTime now);

    List<OrderEntity> findPendingRefundOrders();
}