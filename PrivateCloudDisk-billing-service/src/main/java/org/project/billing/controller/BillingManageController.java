package org.project.billing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.billing.common.ApiResponse;
import org.project.billing.model.dto.CouponClaimRequest;
import org.project.billing.model.dto.InvoiceApplyRequest;
import org.project.billing.model.dto.RefundRequest;
import org.project.billing.model.entity.*;
import org.project.billing.model.vo.CouponVO;
import org.project.billing.model.vo.InvoiceVO;
import org.project.billing.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingManageController {

    private final CouponService couponService;
    private final InvoiceService invoiceService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    // ============================================================
    // 优惠券
    // ============================================================

    @GetMapping("/coupons")
    public ApiResponse<List<CouponVO>> getActiveCoupons() {
        List<CouponEntity> coupons = couponService.getAllActiveCoupons();
        List<CouponVO> vos = coupons.stream().map(c -> CouponVO.builder()
                .id(c.getId())
                .couponCode(c.getCouponCode())
                .couponName(c.getCouponName())
                .couponType(c.getCouponType())
                .discountPercent(c.getDiscountPercent())
                .fixedAmount(c.getFixedAmount())
                .minOrderAmount(c.getMinOrderAmount())
                .maxDiscountAmount(c.getMaxDiscountAmount())
                .applicablePlans(c.getApplicablePlans())
                .perUserLimit(c.getPerUserLimit())
                .validFrom(c.getValidFrom())
                .validTo(c.getValidTo())
                .isActive(c.getIsActive())
                .build()).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    @GetMapping("/coupons/{userId}")
    public ApiResponse<List<CouponVO>> getUserCoupons(@PathVariable String userId) {
        List<UserCouponEntity> userCoupons = couponService.getUserCoupons(userId);
        List<CouponVO> vos = userCoupons.stream().map(uc -> {
            // 简化处理，实际应关联查询优惠券详情
            return CouponVO.builder()
                    .id(uc.getCouponId())
                    .couponCode(uc.getCouponCode())
                    .status(uc.getStatus())
                    .build();
        }).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    @PostMapping("/coupons/claim")
    public ApiResponse<Void> claimCoupon(@Valid @RequestBody CouponClaimRequest request) {
        couponService.claimCoupon(request.getUserId(), request.getCouponCode());
        return ApiResponse.success();
    }

    // ============================================================
    // 发票
    // ============================================================

    @PostMapping("/invoice/apply")
    public ApiResponse<InvoiceVO> applyInvoice(@Valid @RequestBody InvoiceApplyRequest request,
                                                @RequestParam String userId) {
        InvoiceEntity invoice = invoiceService.applyInvoice(
                userId, request.getOrderNo(), request.getInvoiceTitleType(),
                request.getInvoiceTitle(), request.getTaxNo(), request.getEmail());

        InvoiceVO vo = InvoiceVO.builder()
                .id(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .userId(invoice.getUserId())
                .orderId(invoice.getOrderId())
                .invoiceType(invoice.getInvoiceType())
                .invoiceTitleType(invoice.getInvoiceTitleType())
                .invoiceTitle(invoice.getInvoiceTitle())
                .taxNo(invoice.getTaxNo())
                .invoiceAmount(invoice.getInvoiceAmount())
                .taxAmount(invoice.getTaxAmount())
                .status(invoice.getStatus())
                .fileUrl(invoice.getFileUrl())
                .email(invoice.getEmail())
                .createdAt(invoice.getCreatedAt())
                .build();
        return ApiResponse.success(vo);
    }

    @GetMapping("/invoices/{userId}")
    public ApiResponse<List<InvoiceVO>> getUserInvoices(@PathVariable String userId) {
        List<InvoiceEntity> invoices = invoiceService.getUserInvoices(userId);
        List<InvoiceVO> vos = invoices.stream().map(i -> InvoiceVO.builder()
                .id(i.getId())
                .invoiceNo(i.getInvoiceNo())
                .userId(i.getUserId())
                .orderId(i.getOrderId())
                .invoiceType(i.getInvoiceType())
                .invoiceTitleType(i.getInvoiceTitleType())
                .invoiceTitle(i.getInvoiceTitle())
                .taxNo(i.getTaxNo())
                .invoiceAmount(i.getInvoiceAmount())
                .taxAmount(i.getTaxAmount())
                .status(i.getStatus())
                .fileUrl(i.getFileUrl())
                .email(i.getEmail())
                .issuedAt(i.getIssuedAt())
                .createdAt(i.getCreatedAt())
                .build()).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    @GetMapping("/invoice/detail/{invoiceNo}")
    public ApiResponse<InvoiceVO> getInvoiceDetail(@PathVariable String invoiceNo) {
        InvoiceEntity i = invoiceService.getInvoiceByNo(invoiceNo);
        InvoiceVO vo = InvoiceVO.builder()
                .id(i.getId())
                .invoiceNo(i.getInvoiceNo())
                .userId(i.getUserId())
                .orderId(i.getOrderId())
                .invoiceType(i.getInvoiceType())
                .invoiceTitleType(i.getInvoiceTitleType())
                .invoiceTitle(i.getInvoiceTitle())
                .taxNo(i.getTaxNo())
                .invoiceAmount(i.getInvoiceAmount())
                .taxAmount(i.getTaxAmount())
                .status(i.getStatus())
                .fileUrl(i.getFileUrl())
                .email(i.getEmail())
                .issuedAt(i.getIssuedAt())
                .createdAt(i.getCreatedAt())
                .build();
        return ApiResponse.success(vo);
    }

    // ============================================================
    // 退款
    // ============================================================

    @PostMapping("/refund")
    public ApiResponse<Void> refund(@Valid @RequestBody RefundRequest request) {
        orderService.processRefund(request.getOrderNo(), request.getRefundReason(), request.getOperatorId());
        return ApiResponse.success();
    }
}