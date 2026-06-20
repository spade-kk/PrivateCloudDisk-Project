package org.project.billing.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票实体
 */
@Data
public class InvoiceEntity {
    private Long id;
    private String invoiceNo;
    private String userId;
    private Long orderId;
    private String invoiceType;
    private String invoiceTitleType;
    private String invoiceTitle;
    private String taxNo;
    private BigDecimal invoiceAmount;
    private BigDecimal taxAmount;
    private String status;
    private String fileUrl;
    private String email;
    private String remark;
    private LocalDateTime issuedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}