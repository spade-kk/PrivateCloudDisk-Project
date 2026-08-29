package org.project.im.platform.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 主业务服务用户目录客户端。
 *
 * <p>USER-DIRECTORY-20260810：IM 只保存好友、群组和消息关系，不再直接访问
 * {@code pcd_user_info_table}。用户公开资料、账号搜索和用户是否存在统一由
 * platform-service 的公共用户目录接口负责，空间协作、公开空间和 IM 共用该边界。</p>
 *
 * <p>查询失败按“资料不可用”处理，调用方保留 userId 作为降级展示值；创建好友/群成员
 * 等需要确认用户存在的写操作则由调用方使用 {@link #exists(String, String)} 做失败关闭校验。</p>
 */
@Slf4j
@Component
public class PlatformUserDirectoryClient {

    private static final int OK = 200;

    private final RestClient restClient;

    public PlatformUserDirectoryClient(
            RestClient.Builder restClientBuilder,
            @Value("${platform-service.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public boolean exists(String userId, String viewerId) {
        return findPublicProfile(userId, viewerId).isPresent();
    }

    public Optional<PublicProfile> findPublicProfile(String userId, String viewerId) {
        if (isBlank(userId) || isBlank(viewerId)) return Optional.empty();
        try {
            PlatformResponse<PublicProfile> response = restClient.get()
                    .uri("/business/users/{userId}/profile", userId)
                    .header("X-User-Id", viewerId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return response != null && response.code() == OK && response.data() != null
                    ? Optional.of(response.data()) : Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("主业务用户目录查询失败: userId={}, viewerId={}, reason={}", userId, viewerId, exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 公共用户搜索仅返回主业务服务的最小公开资料；分页由主业务服务统一实现。
     */
    public List<PublicProfile> searchPublicProfiles(String keyword, String viewerId, int page, int size) {
        if (isBlank(keyword) || isBlank(viewerId)) return List.of();
        try {
            PlatformResponse<List<PublicProfile>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/business/users/search")
                            .queryParam("q", keyword.trim())
                            .queryParam("page", Math.max(1, page))
                            .queryParam("size", Math.min(100, Math.max(1, size)))
                            .build())
                    .header("X-User-Id", viewerId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return response != null && response.code() == OK && response.data() != null
                    ? response.data() : List.of();
        } catch (RuntimeException exception) {
            log.warn("主业务用户目录搜索失败: viewerId={}, keyword={}, reason={}", viewerId, keyword, exception.getMessage());
            return List.of();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PublicProfile(String userId, String username, String account, String avatarPath) { }

    private record PlatformResponse<T>(Integer code, String message, T data) { }
}
