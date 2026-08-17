package org.project.plugin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.project.plugin.model.ApiResponse;
import org.project.plugin.model.PluginRatingRequest;
import org.project.plugin.service.PluginMarketplaceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 插件市场公开 API；列表只返回审核通过的不可变版本。 */
@Validated
@RestController
@RequestMapping("/plugins/marketplace")
@RequiredArgsConstructor
public class PluginMarketplaceController {
    private final PluginMarketplaceService service;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.list(type, category, query, page, size), requestId(request)
        );
    }

    @PostMapping("/{pluginId}/submit")
    public ApiResponse<?> submit(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        service.submit(pluginId, userId);
        return ApiResponse.ok(Map.of("review_status", "PENDING"), requestId(request));
    }

    @PostMapping("/{pluginId}/ratings")
    public ApiResponse<?> rate(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody PluginRatingRequest body,
            HttpServletRequest request
    ) {
        service.rate(pluginId, userId, body);
        return ApiResponse.ok(Map.of("saved", true), requestId(request));
    }

    @GetMapping("/{pluginId}/ratings")
    public ApiResponse<?> ratings(
            @PathVariable String pluginId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.ratings(pluginId, page, size), requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
