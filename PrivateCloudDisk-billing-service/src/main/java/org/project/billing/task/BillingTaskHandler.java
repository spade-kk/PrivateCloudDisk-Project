package org.project.billing.task;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.mapper.OrderMapper;
import org.project.billing.mapper.UserSubscriptionMapper;
import org.project.billing.model.entity.OrderEntity;
import org.project.billing.model.entity.UserSubscriptionEntity;
import org.project.billing.service.OrderService;
import org.project.billing.service.SubscriptionService;
import org.project.billing.service.UsageService;
import org.project.billing.service.InvoiceService;
import org.project.billing.mapper.InvoiceMapper;
import org.project.billing.model.entity.InvoiceEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计费服务 XXL-Job 定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingTaskHandler {

    private final UserSubscriptionMapper userSubscriptionMapper;
    private final OrderMapper orderMapper;
    private final InvoiceMapper invoiceMapper;
    private final SubscriptionService subscriptionService;
    private final OrderService orderService;
    private final UsageService usageService;
    private final InvoiceService invoiceService;

    // ============================================================
    // 订阅过期检查 (每小时执行一次)
    // ============================================================

    @XxlJob("subscriptionExpiryCheckHandler")
    public void subscriptionExpiryCheckHandler() {
        log.info("=== 订阅过期检查开始 ===");
        try {
            // 1. 处理已过期的订阅
            List<UserSubscriptionEntity> expiredSubs = userSubscriptionMapper
                    .findExpiredSubscriptions(LocalDateTime.now());
            for (UserSubscriptionEntity sub : expiredSubs) {
                try {
                    subscriptionService.handleExpiredSubscription(sub.getUserId());
                    log.info("过期订阅已处理: userId={}", sub.getUserId());
                } catch (Exception e) {
                    log.error("处理过期订阅失败: userId={}", sub.getUserId(), e);
                }
            }

            // 2. 处理即将过期的订阅 (3天内过期，发送提醒)
            List<UserSubscriptionEntity> expiringSubs = userSubscriptionMapper
                    .findExpiringSubscriptions(LocalDateTime.now().plusDays(3));
            for (UserSubscriptionEntity sub : expiringSubs) {
                log.info("订阅即将过期: userId={}, endDate={}", sub.getUserId(), sub.getEndDate());
                // TODO: 发送邮件/短信/站内信提醒用户续费
            }

            XxlJobHelper.handleSuccess("处理过期订阅: " + expiredSubs.size() + " 个, 即将过期: " + expiringSubs.size() + " 个");
        } catch (Exception e) {
            log.error("订阅过期检查异常", e);
            XxlJobHelper.handleFail("订阅过期检查失败: " + e.getMessage());
        }
        log.info("=== 订阅过期检查结束 ===");
    }

    // ============================================================
    // 自动续费处理 (每天执行一次)
    // ============================================================

    @XxlJob("autoRenewHandler")
    public void autoRenewHandler() {
        log.info("=== 自动续费处理开始 ===");
        try {
            List<UserSubscriptionEntity> autoRenewSubs = userSubscriptionMapper
                    .findAutoRenewSubscriptions(LocalDateTime.now());

            for (UserSubscriptionEntity sub : autoRenewSubs) {
                try {
                    subscriptionService.renewSubscription(sub.getUserId(), sub.getBillingCycle());
                    log.info("自动续费完成: userId={}", sub.getUserId());
                } catch (Exception e) {
                    log.error("自动续费失败: userId={}", sub.getUserId(), e);
                }
            }

            XxlJobHelper.handleSuccess("自动续费处理完成: " + autoRenewSubs.size() + " 个");
        } catch (Exception e) {
            log.error("自动续费处理异常", e);
            XxlJobHelper.handleFail("自动续费处理失败: " + e.getMessage());
        }
        log.info("=== 自动续费处理结束 ===");
    }

    // ============================================================
    // 订单过期取消 (每10分钟执行一次)
    // ============================================================

    @XxlJob("orderExpiryHandler")
    public void orderExpiryHandler() {
        log.info("=== 订单过期取消开始 ===");
        try {
            orderService.cancelExpiredOrders();
            XxlJobHelper.handleSuccess("订单过期取消完成");
        } catch (Exception e) {
            log.error("订单过期取消异常", e);
            XxlJobHelper.handleFail("订单过期取消失败: " + e.getMessage());
        }
        log.info("=== 订单过期取消结束 ===");
    }

    // ============================================================
    // 超额计费 (每天执行一次，凌晨2点)
    // ============================================================

    @XxlJob("overageBillingHandler")
    public void overageBillingHandler() {
        log.info("=== 超额计费处理开始 ===");
        try {
            usageService.batchBillOverage();
            XxlJobHelper.handleSuccess("超额计费处理完成");
        } catch (Exception e) {
            log.error("超额计费处理异常", e);
            XxlJobHelper.handleFail("超额计费处理失败: " + e.getMessage());
        }
        log.info("=== 超额计费处理结束 ===");
    }

    // ============================================================
    // 发票自动开具 (每小时执行一次)
    // ============================================================

    @XxlJob("invoiceAutoIssueHandler")
    public void invoiceAutoIssueHandler() {
        log.info("=== 发票自动开具开始 ===");
        try {
            List<InvoiceEntity> pendingInvoices = invoiceMapper.findPendingInvoices();
            for (InvoiceEntity invoice : pendingInvoices) {
                try {
                    invoiceService.issueInvoice(invoice.getInvoiceNo());
                    log.info("发票已开具: invoiceNo={}", invoice.getInvoiceNo());
                } catch (Exception e) {
                    log.error("发票开具失败: invoiceNo={}", invoice.getInvoiceNo(), e);
                }
            }

            XxlJobHelper.handleSuccess("发票开具完成: " + pendingInvoices.size() + " 张");
        } catch (Exception e) {
            log.error("发票自动开具异常", e);
            XxlJobHelper.handleFail("发票自动开具失败: " + e.getMessage());
        }
        log.info("=== 发票自动开具结束 ===");
    }
}