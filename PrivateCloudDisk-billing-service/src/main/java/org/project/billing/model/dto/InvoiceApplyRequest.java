package org.project.billing.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 申请发票请求
 */
@Data
public class InvoiceApplyRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotBlank(message = "抬头类型不能为空")
    private String invoiceTitleType; // PERSONAL / ENTERPRISE

    @NotBlank(message = "发票抬头不能为空")
    private String invoiceTitle;

    private String taxNo;

    private String email;
}