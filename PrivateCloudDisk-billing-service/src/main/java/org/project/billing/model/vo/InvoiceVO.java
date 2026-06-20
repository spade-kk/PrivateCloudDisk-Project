package org.project.billing.model.vo;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票 VO
 */
@Data
@Builder
public class InvoiceVO {
    private Long id;
    private String invoiceNo;
    private String userId;
    private Long orderId;
    private String orderNo;
    private String invoiceType;
    private String invoiceTitleType;
    private String invoiceTitle;
    private String taxNo;
    private BigDecimal invoiceAmount;
    private BigDecimal taxAmount;
    private String status;
    private String fileUrl;
    private String email;
    private LocalDateTime issuedAt;
    private LocalDateTime createdAt;
}