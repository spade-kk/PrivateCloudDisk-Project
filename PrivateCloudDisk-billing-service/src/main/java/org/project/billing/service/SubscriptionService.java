package org.project.billing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.common.BillingException;
import org.project.billing.config.BillingRabbitMQConfig;
import org.project.billing.mapper.*;
import org.project.billing.model.entity.*;
import org.project.billing.model.message.QuotaUpdateMessage;
import org.project.billing.model.message.SubscriptionChangedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 订阅管理服务
 * 核心业务: 订阅创建、续费、升级、降级、取消、过期处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserSubscriptionMapper userSubscriptionMapper;
    private final SubscriptionPlanMapper subscriptionPlanMapper;
    private final OrderMapper orderMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final RabbitTemplate rabbitTemplate;
    private final BillingEventMapper billingEventMapper;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 订阅计划查询
    // ============================================================

    public SubscriptionPlanEntity getPlanByCode(String planCode) {
        SubscriptionPlanEntity plan = subscriptionPlanMapper.findByPlanCode(planCode);
        if (plan == null) {
            throw BillingException.notFound("订阅计划不存在: " + planCode);
        }
        if (!plan.getIsActive()) {
            throw BillingException.badRequest("该订阅计划已停用: " + planCode);
        }
        return plan;
    }

    public UserSubscriptionEntity getUserSubscription(String userId) {
        return userSubscriptionMapper.findByUserId(userId);
    }

    // ============================================================
    // 创建订阅 (Seata 分布式事务)
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public OrderEntity createSubscription(String userId, String planCode, String billingCycle, String couponCode) {
        // 1. 检查是否已有活跃订阅
        UserSubscriptionEntity existing = userSubscriptionMapper.findByUserIdForUpdate(userId);
        if (existing != null && "ACTIVE".equals(existing.getStatus())) {
            throw BillingException.conflict("用户已有活跃订阅，请使用升级/续费功能");
        }

        // 2. 获取订阅计划
        SubscriptionPlanEntity plan = getPlanByCode(planCode);

        // 3. 计算价格
        BigDecimal originalPrice = getPriceByCycle(plan, billingCycle);
        BigDecimal discountAmount = BigDecimal.ZERO;
        CouponEntity coupon = null;

        // 4. 处理优惠券
        if (couponCode != null && !couponCode.isEmpty()) {
            // 优惠券校验在外部完成，这里只做金额计算
            // 实际应用中需要调用 CouponService.validateAndCalculate()
        }

        BigDecimal payableAmount = originalPrice.subtract(discountAmount);

        // 5. 计算订阅周期
        LocalDateTime startDate = LocalDateTime.now();
        int months = getCycleMonths(billingCycle);
        LocalDateTime endDate = startDate.plusMonths(months);
        LocalDateTime nextBillingDate = endDate;

        // 6. 创建订单
        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderNoGenerator.generateOrderNo());
        order.setUserId(userId);
        order.setOrderType("SUBSCRIPTION");
        order.setPlanId(plan.getId());
        order.setBillingCycle(billingCycle);
        order.setAmountOriginal(originalPrice);
        order.setAmountDiscount(discountAmount);
        order.setAmountPayable(payableAmount);
        order.setAmountPaid(BigDecimal.ZERO);
        order.setCurrency("CNY");
        order.setStatus("PENDING");
        order.setCouponCode(couponCode);
        order.setExpiredAt(LocalDateTime.now().plusMinutes(30));
        order.setRemark("订阅创建: " + plan.getPlanName() + " (" + billingCycle + ")");
        orderMapper.insert(order);

        // 7. 如果是免费版，直接激活
        if (plan.getPriceMonthly().compareTo(BigDecimal.ZERO) == 0
                && plan.getPriceYearly().compareTo(BigDecimal.ZERO) == 0) {
            activateFreeSubscription(userId, plan, order);
        }

        // 8. 记录审计事件
        recordBillingEvent(userId, "ORDER_CREATED", Map.of(
                "orderNo", order.getOrderNo(),
                "planCode", planCode,
                "billingCycle", billingCycle,
                "amount", payableAmount.toString()
        ), "USER:" + userId);

        log.info("创建订阅订单: orderNo={}, userId={}, planCode={}, amount={}",
                order.getOrderNo(), userId, planCode, payableAmount);

        return order;
    }

    /**
     * 激活免费订阅
     */
    @Transactional(rollbackFor = Exception.class)
    public void activateFreeSubscription(String userId, SubscriptionPlanEntity plan, OrderEntity order) {
        // 更新订单状态
        orderMapper.updateStatus(order.getOrderNo(), "COMPLETED");

        // 创建/更新用户订阅
        UserSubscriptionEntity sub = new UserSubscriptionEntity();
        sub.setUserId(userId);
        sub.setPlanId(plan.getId());
        sub.setStatus("ACTIVE");
        sub.setBillingCycle("MONTHLY");
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusYears(100)); // 免费版长期有效
        sub.setAutoRenew(true);
        sub.setNextBillingDate(null);
        userSubscriptionMapper.insert(sub);

        // 发送配额更新消息
        publishQuotaUpdate(userId, plan);

        // 发送订阅变更事件
        publishSubscriptionChanged(userId, "ACTIVATED", null, plan.getPlanCode(), null, plan.getPlanTier());

        recordBillingEvent(userId, "SUBSCRIPTION_CREATED", Map.of(
                "planCode", plan.getPlanCode(),
                "planName", plan.getPlanName()
        ), "SYSTEM");
    }

    // ============================================================
    // 支付成功后激活订阅
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public void activatePaidSubscription(String userId, Long planId, String billingCycle, String orderNo) {
        SubscriptionPlanEntity plan = subscriptionPlanMapper.findById(planId);
        if (plan == null) {
            throw BillingException.notFound("订阅计划不存在: " + planId);
        }

        UserSubscriptionEntity existing = userSubscriptionMapper.findByUserIdForUpdate(userId);
        LocalDateTime startDate = LocalDateTime.now();
        int months = getCycleMonths(billingCycle);
        LocalDateTime endDate = startDate.plusMonths(months);
        LocalDateTime nextBillingDate = endDate;

        if (existing != null) {
            // 已有订阅，更新
            userSubscriptionMapper.updatePlan(userId, planId, billingCycle, startDate, endDate, nextBillingDate);
            userSubscriptionMapper.updateStatus(userId, "ACTIVE");
        } else {
            // 新订阅
            UserSubscriptionEntity sub = new UserSubscriptionEntity();
            sub.setUserId(userId);
            sub.setPlanId(planId);
            sub.setStatus("ACTIVE");
            sub.setBillingCycle(billingCycle);
            sub.setStartDate(startDate);
            sub.setEndDate(endDate);
            sub.setAutoRenew(true);
            sub.setNextBillingDate(nextBillingDate);
            userSubscriptionMapper.insert(sub);
        }

        // 发送配额更新消息
        publishQuotaUpdate(userId, plan);

        // 发送订阅变更事件
        String oldPlanCode = existing != null ?
                subscriptionPlanMapper.findById(existing.getPlanId()).getPlanCode() : null;
        Integer oldPlanTier = existing != null ?
                subscriptionPlanMapper.findById(existing.getPlanId()).getPlanTier() : null;
        String changeType = existing != null ? "UPGRADED" : "ACTIVATED";
        publishSubscriptionChanged(userId, changeType, oldPlanCode, plan.getPlanCode(), oldPlanTier, plan.getPlanTier());

        recordBillingEvent(userId, "SUBSCRIPTION_" + changeType, Map.of(
                "orderNo", orderNo,
                "planCode", plan.getPlanCode(),
                "billingCycle", billingCycle
        ), "SYSTEM");
    }

    // ============================================================
    // 订阅取消
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public void cancelSubscription(String userId) {
        UserSubscriptionEntity sub = userSubscriptionMapper.findByUserIdForUpdate(userId);
        if (sub == null || !"ACTIVE".equals(sub.getStatus())) {
            throw BillingException.badRequest("没有活跃的订阅");
        }

        userSubscriptionMapper.updateStatus(userId, "CANCELLED");

        SubscriptionPlanEntity plan = subscriptionPlanMapper.findById(sub.getPlanId());
        publishSubscriptionChanged(userId, "CANCELLED", plan.getPlanCode(), null, plan.getPlanTier(), null);

        recordBillingEvent(userId, "SUBSCRIPTION_CANCELLED", Map.of(
                "planCode", plan.getPlanCode()
        ), "USER:" + userId);
    }

    // ============================================================
    // 订阅续费
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public void renewSubscription(String userId, String billingCycle) {
        UserSubscriptionEntity sub = userSubscriptionMapper.findByUserIdForUpdate(userId);
        if (sub == null) {
            throw BillingException.badRequest("没有订阅记录");
        }

        SubscriptionPlanEntity plan = subscriptionPlanMapper.findById(sub.getPlanId());
        int months = getCycleMonths(billingCycle);
        LocalDateTime newEndDate = LocalDateTime.now().plusMonths(months);

        userSubscriptionMapper.updatePlan(userId, plan.getId(), billingCycle,
                LocalDateTime.now(), newEndDate, newEndDate);
        userSubscriptionMapper.updateStatus(userId, "ACTIVE");

        publishSubscriptionChanged(userId, "RENEWED", plan.getPlanCode(), plan.getPlanCode(),
                plan.getPlanTier(), plan.getPlanTier());

        recordBillingEvent(userId, "SUBSCRIPTION_RENEWED", Map.of(
                "planCode", plan.getPlanCode(),
                "billingCycle", billingCycle,
                "newEndDate", newEndDate.toString()
        ), "SYSTEM");
    }

    // ============================================================
    // 订阅过期处理
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public void handleExpiredSubscription(String userId) {
        UserSubscriptionEntity sub = userSubscriptionMapper.findByUserIdForUpdate(userId);
        if (sub == null || !"ACTIVE".equals(sub.getStatus())) {
            return;
        }

        userSubscriptionMapper.updateStatus(userId, "GRACE_PERIOD");

        SubscriptionPlanEntity plan = subscriptionPlanMapper.findById(sub.getPlanId());
        publishSubscriptionChanged(userId, "EXPIRED", plan.getPlanCode(), "free", plan.getPlanTier(), 0);

        // 降级到免费版配额
        SubscriptionPlanEntity freePlan = subscriptionPlanMapper.findByPlanCode("free");
        if (freePlan != null) {
            publishQuotaUpdate(userId, freePlan);
        }

        recordBillingEvent(userId, "SUBSCRIPTION_EXPIRED", Map.of(
                "planCode", plan.getPlanCode()
        ), "SYSTEM");
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private BigDecimal getPriceByCycle(SubscriptionPlanEntity plan, String billingCycle) {
        return switch (billingCycle) {
            case "MONTHLY" -> plan.getPriceMonthly();
            case "QUARTERLY" -> plan.getPriceQuarterly();
            case "YEARLY" -> plan.getPriceYearly();
            default -> throw BillingException.badRequest("不支持的计费周期: " + billingCycle);
        };
    }

    private int getCycleMonths(String billingCycle) {
        return switch (billingCycle) {
            case "MONTHLY" -> 1;
            case "QUARTERLY" -> 3;
            case "YEARLY" -> 12;
            default -> throw BillingException.badRequest("不支持的计费周期: " + billingCycle);
        };
    }

    private void publishQuotaUpdate(String userId, SubscriptionPlanEntity plan) {
        QuotaUpdateMessage message = QuotaUpdateMessage.builder()
                .messageId(orderNoGenerator.generateMessageId())
                .userId(userId)
                .planCode(plan.getPlanCode())
                .planTier(plan.getPlanTier())
                .storageLimitBytes(plan.getStorageLimitBytes())
                .maxFileSizeBytes(plan.getMaxFileSizeBytes())
                .operatedAt(LocalDateTime.now())
                .build();
        rabbitTemplate.convertAndSend(BillingRabbitMQConfig.BILLING_EXCHANGE,
                BillingRabbitMQConfig.RK_QUOTA_UPDATE, message);
        log.info("配额更新消息已发送: userId={}, planCode={}", userId, plan.getPlanCode());
    }

    private void publishSubscriptionChanged(String userId, String changeType,
                                            String oldPlanCode, String newPlanCode,
                                            Integer oldPlanTier, Integer newPlanTier) {
        SubscriptionChangedEvent event = SubscriptionChangedEvent.builder()
                .eventId("sub-change:" + userId + ":" + System.currentTimeMillis())
                .userId(userId)
                .changeType(changeType)
                .oldPlanCode(oldPlanCode)
                .newPlanCode(newPlanCode)
                .oldPlanTier(oldPlanTier)
                .newPlanTier(newPlanTier)
                .changedAt(LocalDateTime.now())
                .build();
        rabbitTemplate.convertAndSend(BillingRabbitMQConfig.BILLING_EXCHANGE,
                BillingRabbitMQConfig.RK_SUBSCRIPTION_SYNC, event);
    }

    private void recordBillingEvent(String userId, String eventType, Map<String, String> data, String operator) {
        try {
            BillingEventEntity event = new BillingEventEntity();
            event.setUserId(userId);
            event.setEventType(eventType);
            event.setEventData(objectMapper.writeValueAsString(data));
            event.setOperator(operator);
            billingEventMapper.insert(event);
        } catch (JsonProcessingException e) {
            log.error("记录计费事件失败", e);
        }
    }
}