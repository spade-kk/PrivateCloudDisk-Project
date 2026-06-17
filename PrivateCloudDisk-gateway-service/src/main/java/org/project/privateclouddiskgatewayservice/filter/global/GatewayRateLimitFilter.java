package org.project.privateclouddiskgatewayservice.filter.global;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.config.properties.GatewayRateLimitProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayRateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String method = exchange.getRequest().getMethod().name();
        String path = normalizePath(exchange.getRequest().getURI().getPath());
        List<GatewayRateLimitProperties.Rule> matchedRules = properties.getRules()
                .stream()
                .filter(rule -> matches(rule, method, path))
                .toList();

        if (matchedRules.isEmpty()) {
            return chain.filter(exchange);
        }

        return Flux.fromIterable(matchedRules)
                .concatMap(rule -> hitRule(exchange, rule))
                .next()
                .flatMap(rule -> tooManyRequests(exchange, rule))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<GatewayRateLimitProperties.Rule> hitRule(ServerWebExchange exchange,
                                                          GatewayRateLimitProperties.Rule rule) {
        String identity = identity(exchange, rule.getKeyType());
        if (!StringUtils.hasText(identity)) {
            identity = "anonymous:" + clientIp(exchange);
        }
        String key = "pcd:gateway:rate-limit:" + rule.getName() + ":" + sha256(identity);
        Duration window = rule.getWindow();

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    Mono<Long> counted = Mono.just(count);
                    if (count == 1L) {
                        counted = redisTemplate.expire(key, window).thenReturn(count);
                    }
                    return counted;
                })
                .flatMap(count -> {
                    if (count > rule.getLimit()) {
                        log.warn("Gateway rate limit exceeded rule={}, count={}, limit={}, path={}",
                                rule.getName(), count, rule.getLimit(), exchange.getRequest().getURI().getPath());
                        return Mono.just(rule);
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.error("Gateway rate limiter failed rule={}", rule.getName(), e);
                    return properties.isFailOpen()
                            ? Mono.empty()
                            : Mono.just(rule);
                });
    }

    private boolean matches(GatewayRateLimitProperties.Rule rule, String method, String path) {
        if (!rule.isEnabled()
                || !StringUtils.hasText(rule.getName())
                || !StringUtils.hasText(rule.getPathPattern())
                || rule.getLimit() <= 0
                || rule.getWindow() == null
                || rule.getWindow().toMillis() <= 0) {
            return false;
        }
        boolean methodMatches = "*".equals(rule.getMethod())
                || rule.getMethod().equalsIgnoreCase(method);
        return methodMatches
                && pathMatcher.match(normalizePath(rule.getPathPattern()), path);
    }

    private String identity(ServerWebExchange exchange, GatewayRateLimitProperties.KeyType keyType) {
        return switch (keyType) {
            case IP -> "ip:" + clientIp(exchange);
            case USER -> {
                String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                yield StringUtils.hasText(userId) ? "user:" + userId : "";
            }
            case USER_OR_IP -> {
                String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                yield StringUtils.hasText(userId) ? "user:" + userId : "ip:" + clientIp(exchange);
            }
            case FINGERPRINT -> {
                String fp = exchange.getRequest().getHeaders().getFirst("X-Device-Fingerprint");
                yield StringUtils.hasText(fp) ? "fp:" + fp : "";
            }
            case FINGERPRINT_OR_IP -> {
                String fp = exchange.getRequest().getHeaders().getFirst("X-Device-Fingerprint");
                yield StringUtils.hasText(fp) ? "fp:" + fp : "ip:" + clientIp(exchange);
            }
        };
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, GatewayRateLimitProperties.Rule rule) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(Math.max(1L, rule.getWindow().toSeconds())));
        String body = String.format(
                "{\"code\":429,\"message\":\"Too many requests. Please retry later.\",\"rule\":\"%s\",\"timestamp\":\"%s\"}",
                rule.getName(),
                LocalDateTime.now()
        );
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
