package org.project.control;

import org.project.control.result.JsonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sentinel 限流/熔断降级友好提示页面
 * 当请求被 Sentinel 拦截时，会重定向到此端点
 */
@RestController
public class SentinelFallbackController {

    @GetMapping("/blocked")
    public JsonResult<Void> blocked() {
        return JsonResult.error(1429, "请求过于频繁，请稍后再试。系统流量控制已触发。");
    }
}