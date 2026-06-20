package org.project.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.common.BillingException;
import org.project.billing.mapper.InvoiceMapper;
import org.project.billing.mapper.OrderMapper;
import org.project.billing.mapper.BillingEventMapper;
import org.project.billing.model.entity.InvoiceEntity;
import org.project.billing.model.entity.OrderEntity;
import org.project.billing.model.entity.BillingEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 发票管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceMapper invoiceMapper;
    private final OrderMapper orderMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final BillingEventMapper billingEventMapper;

    /**
     * 申请发票
     */
    @Transactional(rollbackFor = Exception.class)
    public InvoiceEntity applyInvoice(String userId, String orderNo, String invoiceTitleType,
                                       String invoiceTitle, String taxNo, String email) {
        OrderEntity order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw BillingException.notFound("订单不存在: " + orderNo);
        }
        if (!order.getUserId().equals(userId)) {
            throw BillingException.badRequest("订单不属于当前用户");
        }
        if (!"PAID".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
            throw BillingException.badRequest("订单未支付完成，无法申请发票");
        }

        // 检查是否已申请过
        InvoiceEntity existing = invoiceMapper.findByOrderId(order.getId());
        if (existing != null) {
            throw BillingException.conflict("该订单已申请过发票: " + existing.getInvoiceNo());
        }

        // 计算税额 (假设增值税率 6%)
        BigDecimal taxRate = new BigDecimal("0.06");
        BigDecimal taxAmount = order.getAmountPaid().multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setInvoiceNo(orderNoGenerator.generateInvoiceNo());
        invoice.setUserId(userId);
        invoice.setOrderId(order.getId());
        invoice.setInvoiceType("ELECTRONIC");
        invoice.setInvoiceTitleType(invoiceTitleType);
        invoice.setInvoiceTitle(invoiceTitle);
        invoice.setTaxNo(taxNo);
        invoice.setInvoiceAmount(order.getAmountPaid());
        invoice.setTaxAmount(taxAmount);
        invoice.setStatus("PENDING");
        invoice.setEmail(email);
        invoiceMapper.insert(invoice);

        log.info("发票申请已提交: invoiceNo={}, orderNo={}, amount={}", invoice.getInvoiceNo(), orderNo, order.getAmountPaid());

        return invoice;
    }

    public List<InvoiceEntity> getUserInvoices(String userId) {
        return invoiceMapper.findByUserId(userId);
    }

    public InvoiceEntity getInvoiceByNo(String invoiceNo) {
        InvoiceEntity invoice = invoiceMapper.findByInvoiceNo(invoiceNo);
        if (invoice == null) {
            throw BillingException.notFound("发票不存在: " + invoiceNo);
        }
        return invoice;
    }

    /**
     * 开发票 (由定时任务或手动触发)
     */
    @Transactional(rollbackFor = Exception.class)
    public void issueInvoice(String invoiceNo) {
        InvoiceEntity invoice = invoiceMapper.findByInvoiceNo(invoiceNo);
        if (invoice == null) {
            throw BillingException.notFound("发票不存在: " + invoiceNo);
        }

        // TODO: 对接第三方电子发票平台 (如百望云、票易通等)
        // 生成PDF发票文件并上传到OSS
        String fileUrl = "https://oss.hellomyservice.xyz/invoices/" + invoiceNo + ".pdf";

        invoiceMapper.updateStatus(invoiceNo, "ISSUED", fileUrl, LocalDateTime.now());

        log.info("发票已开具: invoiceNo={}, fileUrl={}", invoiceNo, fileUrl);
    }
}