package org.project.billing.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 流控规则初始化
 * 对计费服务核心接口进行流量控制保护
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.cloud.sentinel.enabled", havingValue = "true", matchIfMissing = true)
public class SentinelRuleConfig {

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 创建订阅接口限流: QPS 50
        FlowRule createSubscriptionRule = new FlowRule();
        createSubscriptionRule.setResource("POST:/api/billing/subscription/create");
        createSubscriptionRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        createSubscriptionRule.setCount(50);
        rules.add(createSubscriptionRule);

        // 支付回调接口限流: QPS 200
        FlowRule paymentCallbackRule = new FlowRule();
        paymentCallbackRule.setResource("POST:/api/billing/callback/alipay");
        paymentCallbackRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        paymentCallbackRule.setCount(200);
        rules.add(paymentCallbackRule);

        // 退款接口限流: QPS 10
        FlowRule refundRule = new FlowRule();
        refundRule.setResource("POST:/api/billing/refund");
        refundRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        refundRule.setCount(10);
        rules.add(refundRule);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel 流控规则已加载: {} 条规则", rules.size());
    }
}