package org.project.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.common.BillingException;
import org.project.billing.mapper.OrderMapper;
import org.project.billing.mapper.SubscriptionPlanMapper;
import org.project.billing.mapper.UsageRecordMapper;
import org.project.billing.mapper.UserSubscriptionMapper;
import org.project.billing.mapper.BillingEventMapper;
import org.project.billing.model.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用量记录与按量计费服务
 * 每日统计用户超额用量，生成超额计费订单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageRecordMapper usageRecordMapper;
    private final UserSubscriptionMapper userSubscriptionMapper;
    private final SubscriptionPlanMapper subscriptionPlanMapper;
    private final OrderMapper orderMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final BillingEventMapper billingEventMapper;

    private static final BigDecimal GB = new BigDecimal(1024 * 1024 * 1024);

    /**
     * 记录或更新用户每日用量
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordDailyUsage(String userId, LocalDate recordDate,
                                  Long storageUsedBytes, Long trafficUsedBytes) {
        UserSubscriptionEntity sub = userSubscriptionMapper.findByUserId(userId);
        SubscriptionPlanEntity plan = null;
        Long storageLimit = 0L;
        BigDecimal overageUnitPrice = BigDecimal.ZERO;

        if (sub != null && "ACTIVE".equals(sub.getStatus())) {
            plan = subscriptionPlanMapper.findById(sub.getPlanId());
            if (plan != null) {
                storageLimit = plan.getStorageLimitBytes();
                overageUnitPrice = plan.getOverageUnitPrice();
            }
        }

        // 计算超额
        long storageOverage = 0;
        if (storageLimit > 0 && storageUsedBytes > storageLimit) {
            storageOverage = storageUsedBytes - storageLimit;
        }

        long trafficOverage = 0;
        // 流量超额 (暂无流量配额, 暂不计算)

        // 计算超额费用: 超额GB * 单价
        BigDecimal overageCost = BigDecimal.ZERO;
        if (storageOverage > 0 && overageUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal overageGB = new BigDecimal(storageOverage).divide(GB, 10, RoundingMode.HALF_UP);
            overageCost = overageGB.multiply(overageUnitPrice).setScale(4, RoundingMode.HALF_UP);
        }

        UsageRecordEntity record = new UsageRecordEntity();
        record.setUserId(userId);
        record.setRecordDate(recordDate);
        record.setStorageUsedBytes(storageUsedBytes);
        record.setStorageLimitBytes(storageLimit);
        record.setStorageOverageBytes(storageOverage);
        record.setTrafficUsedBytes(trafficUsedBytes != null ? trafficUsedBytes : 0L);
        record.setTrafficLimitBytes(0L);
        record.setTrafficOverageBytes(trafficOverage);
        record.setOverageCost(overageCost);
        record.setIsBilled(false);

        usageRecordMapper.upsert(record);

        log.debug("用量记录已更新: userId={}, date={}, storageUsed={}, overage={}, cost={}",
                userId, recordDate, storageUsedBytes, storageOverage, overageCost);
    }

    /**
     * 批量计费：统计未计费的超额记录，生成账单
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchBillOverage() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<UsageRecordEntity> unbilledRecords = usageRecordMapper.findUnbilledRecords(yesterday);

        for (UsageRecordEntity record : unbilledRecords) {
            try {
                billOverageRecord(record);
            } catch (Exception e) {
                log.error("超额计费失败: userId={}, date={}", record.getUserId(), record.getRecordDate(), e);
            }
        }

        log.info("超额计费批量处理完成: 处理 {} 条记录", unbilledRecords.size());
    }

    private void billOverageRecord(UsageRecordEntity record) {
        if (record.getOverageCost().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 创建超额计费订单
        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderNoGenerator.generateOrderNo());
        order.setUserId(record.getUserId());
        order.setOrderType("OVERAGE");
        order.setAmountOriginal(record.getOverageCost());
        order.setAmountDiscount(BigDecimal.ZERO);
        order.setAmountPayable(record.getOverageCost());
        order.setAmountPaid(BigDecimal.ZERO);
        order.setCurrency("CNY");
        order.setStatus("PENDING");
        order.setRemark("超额计费 - " + record.getRecordDate().toString());
        order.setExpiredAt(LocalDateTime.now().plusDays(7));
        orderMapper.insert(order);

        // 标记用量记录已计费
        usageRecordMapper.markAsBilled(record.getId(), order.getId());

        // 记录事件
        BillingEventEntity event = new BillingEventEntity();
        event.setUserId(record.getUserId());
        event.setEventType("OVERAGE_BILLED");
        event.setEventData("{\"orderNo\":\"" + order.getOrderNo() + "\",\"cost\":\"" + record.getOverageCost() + "\"}");
        event.setOperator("SYSTEM");
        billingEventMapper.insert(event);

        log.info("超额计费订单已创建: orderNo={}, userId={}, cost={}",
                order.getOrderNo(), record.getUserId(), record.getOverageCost());
    }

    public UsageRecordEntity getDailyUsage(String userId, LocalDate date) {
        return usageRecordMapper.findByUserIdAndDate(userId, date);
    }
}