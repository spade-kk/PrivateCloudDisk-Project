package org.project.billing.model.vo;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券 VO
 */
@Data
@Builder
public class CouponVO {
    private Long id;
    private String couponCode;
    private String couponName;
    private String couponType;
    private BigDecimal discountPercent;
    private BigDecimal fixedAmount;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private String applicablePlans;
    private Integer perUserLimit;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Boolean isActive;
    private String status; // 用户侧状态: UNUSED/USED/EXPIRED
}