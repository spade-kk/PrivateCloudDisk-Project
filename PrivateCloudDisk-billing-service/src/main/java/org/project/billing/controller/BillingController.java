package org.project.billing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.billing.common.ApiResponse;
import org.project.billing.model.dto.CreateSubscriptionRequest;
import org.project.billing.model.entity.OrderEntity;
import org.project.billing.model.entity.SubscriptionPlanEntity;
import org.project.billing.model.entity.UserSubscriptionEntity;
import org.project.billing.model.vo.OrderVO;
import org.project.billing.model.vo.SubscriptionPlanVO;
import org.project.billing.model.vo.UserSubscriptionVO;
import org.project.billing.mapper.SubscriptionPlanMapper;
import org.project.billing.service.SubscriptionService;
import org.project.billing.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final SubscriptionPlanMapper subscriptionPlanMapper;
    private final SubscriptionService subscriptionService;
    private final OrderService orderService;

    // ============================================================
    // 订阅计划
    // ============================================================

    @GetMapping("/plans")
    public ApiResponse<List<SubscriptionPlanVO>> getPlans() {
        List<SubscriptionPlanEntity> plans = subscriptionPlanMapper.findAllActive();
        List<SubscriptionPlanVO> vos = plans.stream().map(p -> SubscriptionPlanVO.builder()
                .id(p.getId())
                .planCode(p.getPlanCode())
                .planName(p.getPlanName())
                .planTier(p.getPlanTier())
                .description(p.getDescription())
                .storageLimitBytes(p.getStorageLimitBytes())
                .storageLimitDisplay(SubscriptionPlanVO.formatBytes(p.getStorageLimitBytes()))
                .maxFileSizeBytes(p.getMaxFileSizeBytes())
                .maxFileSizeDisplay(SubscriptionPlanVO.formatBytes(p.getMaxFileSizeBytes()))
                .maxShareLinks(p.getMaxShareLinks())
                .featuresJson(p.getFeaturesJson())
                .priceMonthly(p.getPriceMonthly())
                .priceYearly(p.getPriceYearly())
                .priceQuarterly(p.getPriceQuarterly())
                .overageUnitPrice(p.getOverageUnitPrice())
                .trialDays(p.getTrialDays())
                .sortOrder(p.getSortOrder())
                .build()).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    // ============================================================
    // 用户订阅
    // ============================================================

    @GetMapping("/subscription/{userId}")
    public ApiResponse<UserSubscriptionVO> getUserSubscription(@PathVariable String userId) {
        UserSubscriptionEntity sub = subscriptionService.getUserSubscription(userId);
        if (sub == null) {
            return ApiResponse.success(null);
        }

        SubscriptionPlanEntity plan = subscriptionPlanMapper.findById(sub.getPlanId());
        long remainingDays = ChronoUnit.DAYS.between(LocalDateTime.now(), sub.getEndDate());

        UserSubscriptionVO vo = UserSubscriptionVO.builder()
                .id(sub.getId())
                .userId(sub.getUserId())
                .planId(sub.getPlanId())
                .planCode(plan != null ? plan.getPlanCode() : null)
                .planName(plan != null ? plan.getPlanName() : null)
                .status(sub.getStatus())
                .billingCycle(sub.getBillingCycle())
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .autoRenew(sub.getAutoRenew())
                .storageLimitBytes(plan != null ? plan.getStorageLimitBytes() : 0L)
                .storageLimitDisplay(plan != null ? SubscriptionPlanVO.formatBytes(plan.getStorageLimitBytes()) : "0")
                .trialDays(plan != null ? plan.getTrialDays() : 0)
                .trialEndedAt(sub.getTrialEndedAt())
                .nextBillingDate(sub.getNextBillingDate())
                .remainingDays(remainingDays)
                .build();

        return ApiResponse.success(vo);
    }

    @PostMapping("/subscription/create")
    public ApiResponse<OrderVO> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        OrderEntity order = subscriptionService.createSubscription(
                request.getUserId(), request.getPlanCode(),
                request.getBillingCycle(), request.getCouponCode());

        OrderVO vo = OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .orderType(order.getOrderType())
                .billingCycle(order.getBillingCycle())
                .amountOriginal(order.getAmountOriginal())
                .amountDiscount(order.getAmountDiscount())
                .amountPayable(order.getAmountPayable())
                .amountPaid(order.getAmountPaid())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .couponCode(order.getCouponCode())
                .expiredAt(order.getExpiredAt())
                .createdAt(order.getCreatedAt())
                .expireSeconds(ChronoUnit.SECONDS.between(LocalDateTime.now(), order.getExpiredAt()))
                .build();

        return ApiResponse.success(vo);
    }

    @PostMapping("/subscription/{userId}/cancel")
    public ApiResponse<Void> cancelSubscription(@PathVariable String userId) {
        subscriptionService.cancelSubscription(userId);
        return ApiResponse.success();
    }

    // ============================================================
    // 订单
    // ============================================================

    @GetMapping("/orders/{userId}")
    public ApiResponse<List<OrderVO>> getUserOrders(@PathVariable String userId) {
        List<OrderEntity> orders = orderService.getUserOrders(userId);
        List<OrderVO> vos = orders.stream().map(o -> {
            SubscriptionPlanEntity plan = o.getPlanId() != null ?
                    subscriptionPlanMapper.findById(o.getPlanId()) : null;
            long expireSeconds = o.getExpiredAt() != null ?
                    ChronoUnit.SECONDS.between(LocalDateTime.now(), o.getExpiredAt()) : 0;
            return OrderVO.builder()
                    .id(o.getId())
                    .orderNo(o.getOrderNo())
                    .userId(o.getUserId())
                    .orderType(o.getOrderType())
                    .planCode(plan != null ? plan.getPlanCode() : null)
                    .planName(plan != null ? plan.getPlanName() : null)
                    .billingCycle(o.getBillingCycle())
                    .amountOriginal(o.getAmountOriginal())
                    .amountDiscount(o.getAmountDiscount())
                    .amountPayable(o.getAmountPayable())
                    .amountPaid(o.getAmountPaid())
                    .currency(o.getCurrency())
                    .status(o.getStatus())
                    .paymentMethod(o.getPaymentMethod())
                    .couponCode(o.getCouponCode())
                    .refundAmount(o.getRefundAmount())
                    .refundReason(o.getRefundReason())
                    .paidAt(o.getPaidAt())
                    .expiredAt(o.getExpiredAt())
                    .createdAt(o.getCreatedAt())
                    .expireSeconds(expireSeconds)
                    .build();
        }).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    @GetMapping("/orders/detail/{orderNo}")
    public ApiResponse<OrderVO> getOrderDetail(@PathVariable String orderNo) {
        OrderEntity o = orderService.getOrderByOrderNo(orderNo);
        SubscriptionPlanEntity plan = o.getPlanId() != null ?
                subscriptionPlanMapper.findById(o.getPlanId()) : null;
        long expireSeconds = o.getExpiredAt() != null ?
                ChronoUnit.SECONDS.between(LocalDateTime.now(), o.getExpiredAt()) : 0;

        OrderVO vo = OrderVO.builder()
                .id(o.getId())
                .orderNo(o.getOrderNo())
                .userId(o.getUserId())
                .orderType(o.getOrderType())
                .planCode(plan != null ? plan.getPlanCode() : null)
                .planName(plan != null ? plan.getPlanName() : null)
                .billingCycle(o.getBillingCycle())
                .amountOriginal(o.getAmountOriginal())
                .amountDiscount(o.getAmountDiscount())
                .amountPayable(o.getAmountPayable())
                .amountPaid(o.getAmountPaid())
                .currency(o.getCurrency())
                .status(o.getStatus())
                .paymentMethod(o.getPaymentMethod())
                .couponCode(o.getCouponCode())
                .refundAmount(o.getRefundAmount())
                .refundReason(o.getRefundReason())
                .paidAt(o.getPaidAt())
                .expiredAt(o.getExpiredAt())
                .createdAt(o.getCreatedAt())
                .expireSeconds(expireSeconds)
                .build();
        return ApiResponse.success(vo);
    }
}