package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.dto.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 私聊在线状态查询。
 *
 * <p>PRIVATE-CHAT-20260810 [2.3/3.3/7.4]：当前 V2 Protobuf 没有浏览器端上下线事件，
 * 因此不新增未经协议协商的枚举值。IM Server 已通过心跳维护 {@code im:user:{userId}}
 * 的带 TTL 路由映射，本接口只读取该映射并返回最小状态，不读取用户业务表。</p>
 */
@RestController
@RequestMapping("/im/presence")
@RequiredArgsConstructor
@Tag(name = "用户在线状态", description = "查询私聊对端在线状态")
public class PresenceController {

    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping
    @Operation(summary = "批量查询用户在线状态")
    public Result<Map<String, PresenceDTO>> getPresence(
            @RequestParam(name = "userIds") String userIds) {
        Map<String, PresenceDTO> result = new LinkedHashMap<>();
        Arrays.stream(userIds == null ? new String[0] : userIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(100)
                .forEach(userId -> {
                    boolean online = Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                            String.format(ImConstants.REDIS_USER_SERVER, userId)));
                    result.put(userId, new PresenceDTO(online ? "online" : "offline"));
                });
        return Result.success(result);
    }

    public record PresenceDTO(String status) { }
}
