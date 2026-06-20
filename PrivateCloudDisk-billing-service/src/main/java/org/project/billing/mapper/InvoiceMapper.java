package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.InvoiceEntity;

import java.util.List;

@Mapper
public interface InvoiceMapper {

    InvoiceEntity findByInvoiceNo(@Param("invoiceNo") String invoiceNo);

    InvoiceEntity findByOrderId(@Param("orderId") Long orderId);

    int insert(InvoiceEntity invoice);

    int updateStatus(@Param("invoiceNo") String invoiceNo, @Param("status") String status,
                     @Param("fileUrl") String fileUrl, @Param("issuedAt") java.time.LocalDateTime issuedAt);

    List<InvoiceEntity> findByUserId(@Param("userId") String userId);

    List<InvoiceEntity> findPendingInvoices();
}