package org.project.billing.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 优惠券领取请求
 */
@Data
public class CouponClaimRequest {
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "优惠券码不能为空")
    private String couponCode;
}