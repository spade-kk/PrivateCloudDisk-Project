package org.project.billing.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户优惠券实体
 */
@Data
public class UserCouponEntity {
    private Long id;
    private String userId;
    private Long couponId;
    private String couponCode;
    private String status;
    private Long orderId;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}