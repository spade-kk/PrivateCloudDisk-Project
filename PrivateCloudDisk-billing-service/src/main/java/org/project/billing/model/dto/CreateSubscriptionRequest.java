package org.project.billing.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建订阅请求
 */
@Data
public class CreateSubscriptionRequest {
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "计划编码不能为空")
    private String planCode;

    @NotBlank(message = "计费周期不能为空")
    private String billingCycle; // MONTHLY / QUARTERLY / YEARLY

    private String couponCode;    // 可选优惠券码

    private Boolean autoRenew = true;
}