package org.project.billing.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体
 */
@Data
public class CouponEntity {
    private Long id;
    private String couponCode;
    private String couponName;
    private String couponType;
    private BigDecimal discountPercent;
    private BigDecimal fixedAmount;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private String applicablePlans;
    private String applicableUserLevel;
    private Integer totalQuantity;
    private Integer usedQuantity;
    private Integer perUserLimit;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}