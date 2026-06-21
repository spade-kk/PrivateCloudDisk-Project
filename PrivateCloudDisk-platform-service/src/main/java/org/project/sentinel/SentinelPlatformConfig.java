package org.project.sentinel;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowItem;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sentinel 平台服务配置。
 * <p>
 * 在 platform-service 中启用 Sentinel 的：
 * <ul>
 *   <li>@SentinelResource 注解支持（方法级流控、熔断、热点参数限流）</li>
 *   <li>Nacos 动态规则 / 硬编码默认规则</li>
 *   <li>系统保护规则（LOAD/CPU/RT）</li>
 * </ul>
 *
 * <h3>与 ApiAbuseProtectionService 的职责划分</h3>
 * <table>
 *   <tr><th>组件</th><th>职责</th><th>示例</th></tr>
 *   <tr><td>ApiAbuseProtectionService</td><td>业务滥用检测</td><td>登录失败锁定、手机号注册频率</td></tr>
 *   <tr><td>Sentinel @SentinelResource</td><td>系统保护</td><td>接口 QPS 上限、熔断降级、热点参数限流</td></tr>
 * </table>
 */
@Slf4j
@Configuration
public class SentinelPlatformConfig {

    @Value("${spring.cloud.nacos.discovery.server-addr:}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.discovery.namespace:}")
    private String nacosNamespace;

    @Value("${sentinel.rules.nacos.enabled:false}")
    private boolean nacosRulesEnabled;

    private static final String NACOS_GROUP = "SENTINEL_PLATFORM";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 启用 @SentinelResource 注解的 AOP 切面。
     * 这是 Sentinel 注解生效的必要条件。
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        log.info("SentinelResourceAspect registered for platform-service");
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void init() {
        if (nacosRulesEnabled && nacosServerAddr != null && !nacosServerAddr.isBlank()) {
            initNacosRules();
            log.info("Sentinel Platform rules loaded from Nacos (server={})", nacosServerAddr);
        } else {
            initDefaultRules();
            log.info("Sentinel Platform rules loaded from defaults (Nacos not configured)");
        }
    }

    // ============================================================
    // Nacos 动态规则
    // ============================================================

    private void initNacosRules() {
        // 流控规则
        ReadableDataSource<String, List<FlowRule>> flowDs = new NacosDataSource<>(
                nacosServerAddr, nacosNamespace, NACOS_GROUP,
                "sentinel-platform-flow",
                source -> parseJson(source, new TypeReference<List<FlowRule>>() {})
        );
        FlowRuleManager.register2Property(flowDs.getProperty());

        // 熔断规则
        ReadableDataSource<String, List<DegradeRule>> degradeDs = new NacosDataSource<>(
                nacosServerAddr, nacosNamespace, NACOS_GROUP,
                "sentinel-platform-degrade",
                source -> parseJson(source, new TypeReference<List<DegradeRule>>() {})
        );
        DegradeRuleManager.register2Property(degradeDs.getProperty());

        // 系统规则
        ReadableDataSource<String, List<SystemRule>> systemDs = new NacosDataSource<>(
                nacosServerAddr, nacosNamespace, NACOS_GROUP,
                "sentinel-platform-system",
                source -> parseJson(source, new TypeReference<List<SystemRule>>() {})
        );
        SystemRuleManager.register2Property(systemDs.getProperty());

        // 热点参数规则
        ReadableDataSource<String, List<ParamFlowRule>> paramFlowDs = new NacosDataSource<>(
                nacosServerAddr, nacosNamespace, NACOS_GROUP,
                "sentinel-platform-param-flow",
                source -> parseJson(source, new TypeReference<List<ParamFlowRule>>() {})
        );
        ParamFlowRuleManager.register2Property(paramFlowDs.getProperty());
    }

    private <T> List<T> parseJson(String source, TypeReference<List<T>> typeRef) {
        try {
            return OBJECT_MAPPER.readValue(source, typeRef);
        } catch (Exception e) {
            log.error("Failed to parse Sentinel rule from JSON", e);
            return Collections.emptyList();
        }
    }

    // ============================================================
    // 硬编码默认规则
    // ============================================================

    private void initDefaultRules() {
        initDefaultFlowRules();
        initDefaultDegradeRules();
        initDefaultSystemRules();
        initDefaultParamFlowRules();
    }

    /**
     * 默认流控规则（QPS 限流）。
     */
    private void initDefaultFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 文件删除：全局 100 QPS（防止恶意批量删除）
        rules.add(buildFlowRule("deleteFiles", 100, RuleConstant.FLOW_GRADE_QPS));
        // 文件删除（单用户）：每用户 10 QPS
        rules.add(buildFlowRule("deleteFilesByUser", 10, RuleConstant.FLOW_GRADE_QPS));

        // 文件搜索：全局 200 QPS
        rules.add(buildFlowRule("searchFiles", 200, RuleConstant.FLOW_GRADE_QPS));
        // 文件全文搜索：全局 50 QPS（ES 密集型）
        rules.add(buildFlowRule("fulltextSearch", 50, RuleConstant.FLOW_GRADE_QPS));

        // 上传会话创建：全局 100 QPS
        rules.add(buildFlowRule("createUploadSession", 100, RuleConstant.FLOW_GRADE_QPS));
        // 上传会话完成：全局 100 QPS
        rules.add(buildFlowRule("completeUploadSession", 100, RuleConstant.FLOW_GRADE_QPS));

        // 分享创建：全局 50 QPS
        rules.add(buildFlowRule("createShare", 50, RuleConstant.FLOW_GRADE_QPS));
        // 分享访问：全局 200 QPS
        rules.add(buildFlowRule("accessShare", 200, RuleConstant.FLOW_GRADE_QPS));

        // 内部接口（存储服务调用）：全局 500 QPS
        rules.add(buildFlowRule("internalGetFileMetadata", 500, RuleConstant.FLOW_GRADE_QPS));
        rules.add(buildFlowRule("internalGetDownloadMetadata", 500, RuleConstant.FLOW_GRADE_QPS));

        FlowRuleManager.loadRules(rules);
        log.info("Default platform flow rules loaded: {} rules", rules.size());
    }

    /**
     * 默认熔断降级规则。
     */
    private void initDefaultDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 文件搜索熔断：慢调用比例 > 50% 时熔断
        DegradeRule searchSlow = new DegradeRule("searchFiles")
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(2000)              // 最大 RT 2000ms
                .setSlowRatioThreshold(0.5)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000)
                .setTimeWindow(20);          // 熔断时长 20s
        rules.add(searchSlow);

        // 文件删除异常熔断：异常比例 > 30%
        DegradeRule deleteException = new DegradeRule("deleteFiles")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.3)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000)
                .setTimeWindow(30);          // 熔断时长 30s
        rules.add(deleteException);

        DegradeRuleManager.loadRules(rules);
        log.info("Default platform degrade rules loaded: {} rules", rules.size());
    }

    /**
     * 默认系统保护规则。
     */
    private void initDefaultSystemRules() {
        List<SystemRule> rules = new ArrayList<>();

        SystemRule loadRule = new SystemRule();
        loadRule.setHighestSystemLoad(3.0);
        rules.add(loadRule);

        SystemRule cpuRule = new SystemRule();
        cpuRule.setHighestCpuUsage(0.75);
        rules.add(cpuRule);

        SystemRule rtRule = new SystemRule();
        rtRule.setAvgRt(2000);
        rules.add(rtRule);

        SystemRule qpsRule = new SystemRule();
        qpsRule.setQps(1000);
        rules.add(qpsRule);

        SystemRuleManager.loadRules(rules);
        log.info("Default platform system rules loaded: {} rules", rules.size());
    }

    /**
     * 默认热点参数限流规则。
     * <p>
     * 针对文件 ID、用户 ID 等热点参数做精细化限流。
     */
    private void initDefaultParamFlowRules() {
        List<ParamFlowRule> rules = new ArrayList<>();

        // 文件删除功能：对 userId 参数限流
        // 单用户删除 QPS > 10 时触发限流
        ParamFlowRule deleteByUser = new ParamFlowRule("deleteFilesByUser")
                .setParamIdx(0)              // 第一个参数：userId
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(10);               // 单用户删除 QPS 上限
        rules.add(deleteByUser);

        // 文件搜索：对 keyword 参数限流
        // 防止热点搜索词压垮 Elasticsearch
        ParamFlowRule searchKeyword = new ParamFlowRule("searchFiles")
                .setParamIdx(0)              // 第一个参数：keyword
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(100);              // 单关键词搜索 QPS 上限
        rules.add(searchKeyword);

        // 上传会话创建：对 userId 参数限流
        ParamFlowRule uploadByUser = new ParamFlowRule("createUploadSession")
                .setParamIdx(0)              // 第一个参数：userId
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(5);                // 单用户上传会话 QPS 上限
        rules.add(uploadByUser);

        ParamFlowRuleManager.loadRules(rules);
        log.info("Default platform param-flow rules loaded: {} rules", rules.size());
    }

    private FlowRule buildFlowRule(String resource, int count, int grade) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(grade);
        rule.setCount(count);
        rule.setLimitApp("default");
        return rule;
    }
}