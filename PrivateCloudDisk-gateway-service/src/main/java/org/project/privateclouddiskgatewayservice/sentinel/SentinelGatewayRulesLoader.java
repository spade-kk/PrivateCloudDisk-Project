package org.project.privateclouddiskgatewayservice.sentinel;

import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.route.RouteDefinitionRouteLocator;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sentinel 网关规则加载器。
 * <p>
 * 支持两种规则来源：
 * <ol>
 *   <li><b>Nacos 动态配置</b>（生产环境推荐）—— 规则存储在 Nacos，实时生效无需重启</li>
 *   <li><b>硬编码默认规则</b>（开发/兜底）—— 当 Nacos 不可用时使用内置规则</li>
 * </ol>
 *
 * <h3>规则分层架构</h3>
 * <pre>
 *                    ┌─────────────────────┐
 *                    │   Nacos 动态规则     │  ← 生产环境，实时生效
 *                    └──────────┬──────────┘
 *                               │ fallback
 *                    ┌──────────▼──────────┐
 *                    │   硬编码默认规则      │  ← 开发/兜底
 *                    └─────────────────────┘
 * </pre>
 */
@Slf4j
@Component
public class SentinelGatewayRulesLoader {

    @Value("${spring.cloud.nacos.discovery.server-addr:}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.discovery.namespace:}")
    private String nacosNamespace;

    @Value("${sentinel.rules.nacos.enabled:false}")
    private boolean nacosRulesEnabled;

    private static final String NACOS_GROUP = "SENTINEL_GATEWAY";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String[] GATEWAY_API_GROUPS = {
            "pcd_storage_api",      // 存储服务 API
            "pcd_business_api",     // 业务服务 API
            "pcd_public_api",       // 公开 API（登录/注册）
            "pcd_admin_api",        // 管理后台 API
            "pcd_internal_api"      // 内部服务间调用
    };

    @PostConstruct
    public void init() {
        if (nacosRulesEnabled && nacosServerAddr != null && !nacosServerAddr.isBlank()) {
            initNacosRules();
            log.info("Sentinel Gateway rules loaded from Nacos (server={})", nacosServerAddr);
        } else {
            initDefaultRules();
            log.info("Sentinel Gateway rules loaded from defaults (Nacos not configured)");
        }
    }

    // ============================================================
    // Nacos 动态规则
    // ============================================================

    private void initNacosRules() {
        // 网关流控规则（API 分组维度）
        for (String apiGroup : GATEWAY_API_GROUPS) {
            String dataId = "sentinel-gateway-flow-" + apiGroup;
            ReadableDataSource<String, List<FlowRule>> flowDs = new NacosDataSource<>(
                    nacosServerAddr, nacosNamespace, NACOS_GROUP, dataId,
                    source -> parseJson(source, new TypeReference<List<FlowRule>>() {})
            );
            FlowRuleManager.register2Property(flowDs.getProperty());
        }

        // 熔断规则
        ReadableDataSource<String, List<DegradeRule>> degradeDs = new NacosDataSource<>(
                nacosServerAddr, nacosNamespace, NACOS_GROUP,
                "sentinel-gateway-degrade",
                source -> parseJson(source, new TypeReference<List<DegradeRule>>() {})
        );
        DegradeRuleManager.register2Property(degradeDs.getProperty());

        // 系统规则
        ReadableDataSource<String, List<SystemRule>> systemDs = new NacosDataSource<>(
                nacosServerAddr, nacosNamespace, NACOS_GROUP,
                "sentinel-gateway-system",
                source -> parseJson(source, new TypeReference<List<SystemRule>>() {})
        );
        SystemRuleManager.register2Property(systemDs.getProperty());
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
    // 硬编码默认规则（开发环境 / 兜底）
    // ============================================================

    private void initDefaultRules() {
        initDefaultFlowRules();
        initDefaultDegradeRules();
        initDefaultSystemRules();
        initDefaultParamFlowRules();
    }

    /**
     * 默认流控规则（QPS 限流）。
     * <p>
     * 规则设计原则：
     * <ul>
     *   <li>公开 API 适当宽松（登录/注册是用户入口）</li>
     *   <li>业务 API 适度限制（防止单用户滥用）</li>
     *   <li>存储 API 严格控制（文件上传/下载是资源密集型操作）</li>
     *   <li>管理 API 宽松（管理员操作频率低）</li>
     * </ul>
     */
    private void initDefaultFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // ========== 公开 API（登录/注册） ==========
        // 登录接口：全局 200 QPS，单机约 100 QPS
        rules.add(buildFlowRule("pcd_public_api", 200, RuleConstant.FLOW_GRADE_QPS));
        // 注册接口：严格限制 20 QPS
        rules.add(buildFlowRule("pcd_public_api_register", 20, RuleConstant.FLOW_GRADE_QPS));

        // ========== 业务 API（文件操作/搜索/分享） ==========
        // 文件操作：全局 300 QPS
        rules.add(buildFlowRule("pcd_business_api", 300, RuleConstant.FLOW_GRADE_QPS));
        // 文件删除（高风险操作）：全局 100 QPS
        rules.add(buildFlowRule("pcd_business_api_delete", 100, RuleConstant.FLOW_GRADE_QPS));
        // 文件搜索：全局 200 QPS
        rules.add(buildFlowRule("pcd_business_api_search", 200, RuleConstant.FLOW_GRADE_QPS));

        // ========== 存储 API（上传/下载） ==========
        // 文件上传：全局 200 QPS（每个分片都是独立请求，实际很高）
        rules.add(buildFlowRule("pcd_storage_api", 500, RuleConstant.FLOW_GRADE_QPS));
        // 文件下载：全局 500 QPS（热门文件可能爆发）
        rules.add(buildFlowRule("pcd_storage_api_download", 500, RuleConstant.FLOW_GRADE_QPS));
        // 大文件操作（Range 分段下载）：全局 300 QPS
        rules.add(buildFlowRule("pcd_storage_api_range", 300, RuleConstant.FLOW_GRADE_QPS));

        // ========== 内部 API（服务间调用） ==========
        // 内部调用：全局 1000 QPS（不限制太死，避免影响服务间通信）
        rules.add(buildFlowRule("pcd_internal_api", 1000, RuleConstant.FLOW_GRADE_QPS));

        FlowRuleManager.loadRules(rules);
        log.info("Default Sentinel flow rules loaded: {} rules", rules.size());
    }

    /**
     * 默认熔断降级规则。
     * <p>
     * 策略：慢调用比例熔断 + 异常比例熔断。
     * 当后端服务响应变慢或异常率升高时，自动熔断保护网关线程池。
     */
    private void initDefaultDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 存储服务熔断：慢调用比例 > 50% 且最小 5 个请求时触发
        DegradeRule storageSlow = new DegradeRule("pcd_storage_api")
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(3000)         // 最大 RT 3000ms
                .setSlowRatioThreshold(0.5)  // 慢调用比例 50%
                .setMinRequestAmount(5)      // 最小请求数
                .setStatIntervalMs(10000)    // 统计窗口 10s
                .setTimeWindow(30);          // 熔断时长 30s
        rules.add(storageSlow);

        // 业务服务熔断：异常比例 > 30% 时触发
        DegradeRule businessException = new DegradeRule("pcd_business_api")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.3)           // 异常比例 30%
                .setMinRequestAmount(10) // 最小请求数
                .setStatIntervalMs(10000)
                .setTimeWindow(20);      // 熔断时长 20s
        rules.add(businessException);

        // 存储服务异常比例熔断：异常 > 20% 时触发
        DegradeRule storageException = new DegradeRule("pcd_storage_api_download")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.2)           // 异常比例 20%
                .setMinRequestAmount(10)
                .setStatIntervalMs(10000)
                .setTimeWindow(30);      // 熔断时长 30s
        rules.add(storageException);

        DegradeRuleManager.loadRules(rules);
        log.info("Default Sentinel degrade rules loaded: {} rules", rules.size());
    }

    /**
     * 默认系统保护规则。
     * <p>
     * 当系统整体负载过高时，自动限流保护系统稳定性。
     * 这些规则是全局的，不区分 API 组。
     */
    private void initDefaultSystemRules() {
        List<SystemRule> rules = new ArrayList<>();

        // LOAD 保护：系统 load1 > 4.0 时触发限流
        SystemRule loadRule = new SystemRule();
        loadRule.setHighestSystemLoad(4.0);
        rules.add(loadRule);

        // CPU 使用率保护：CPU usage > 80% 时触发限流
        SystemRule cpuRule = new SystemRule();
        cpuRule.setHighestCpuUsage(0.8);
        rules.add(cpuRule);

        // 平均 RT 保护：平均 RT > 3000ms 时触发限流
        SystemRule rtRule = new SystemRule();
        rtRule.setAvgRt(3000);
        rules.add(rtRule);

        // QPS 保护：入口 QPS > 2000 时触发限流
        SystemRule qpsRule = new SystemRule();
        qpsRule.setQps(2000);
        rules.add(qpsRule);

        SystemRuleManager.loadRules(rules);
        log.info("Default Sentinel system rules loaded: {} rules", rules.size());
    }

    /**
     * 默认热点参数限流规则。
     * <p>
     * 针对特定热点参数（如文件ID、用户ID）进行精细化限流，
     * 防止单个热门文件或恶意用户耗尽资源。
     */
    private void initDefaultParamFlowRules() {
        List<ParamFlowRule> rules = new ArrayList<>();

        // 文件下载热点限流：对 fileId 参数限流
        // 单文件下载 QPS > 50 时触发（防止热门文件爆发性下载）
        ParamFlowRule fileDownloadRule = new ParamFlowRule("pcd_storage_api_download")
                .setParamIdx(0)                       // 第 0 个参数（fileId）
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(50);                        // 单文件 QPS 上限
        // 特定热点文件可设置例外值
        // 例如：系统公告文件（fileId=xxx）可设置更高 QPS
        fileDownloadRule.setParamFlowItemList(List.of(
                ParamFlowItem.newItem("system_notice_default", 200, String.class.getName())
        ));
        rules.add(fileDownloadRule);

        // 文件删除热点限流：对 userId 参数限流
        // 单用户删除 QPS > 10 时触发（防止恶意批量删除）
        ParamFlowRule fileDeleteRule = new ParamFlowRule("pcd_business_api_delete")
                .setParamIdx(0)                       // 第 0 个参数（userId）
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(10);                        // 单用户删除 QPS 上限
        rules.add(fileDeleteRule);

        // 上传会话创建热点限流：对 userId 参数限流
        // 单用户上传会话创建 QPS > 5 时触发
        ParamFlowRule uploadSessionRule = new ParamFlowRule("pcd_business_api_upload_session")
                .setParamIdx(0)                       // 第 0 个参数（userId）
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(5);                         // 单用户上传会话 QPS 上限
        rules.add(uploadSessionRule);

        ParamFlowRuleManager.loadRules(rules);
        log.info("Default Sentinel param-flow rules loaded: {} rules", rules.size());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private FlowRule buildFlowRule(String resource, int count, int grade) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(grade);
        rule.setCount(count);
        rule.setLimitApp("default");
        return rule;
    }
}