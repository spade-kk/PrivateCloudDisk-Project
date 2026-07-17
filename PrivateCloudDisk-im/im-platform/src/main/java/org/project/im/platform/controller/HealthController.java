package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.project.im.common.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 * <p>
 * 提供 IM Platform 服务的健康检查端点，
 * 用于 Kubernetes / Docker 的存活探针和就绪探针。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/im")
@Tag(name = "健康检查", description = "服务存活和就绪探针")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<String> health() {
        return Result.success("im-platform is running");
    }

    @GetMapping("/ping")
    @Operation(summary = "Ping 检查")
    public Result<String> ping() {
        return Result.success("pong");
    }
}