package org.project.plugin.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.RuntimeValidationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** 插件服务只调用 Runtime 的静态校验 API，不在业务 JVM 中执行用户代码。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeValidationClient {
    private final RestClient.Builder restClientBuilder;
    private final PluginProperties properties;

    public RuntimeValidationResponse validate(
            String runtime,
            String source,
            String entrypoint,
            List<String> permissions
    ) {
        String path = switch (runtime) {
            case "PYTHON_3_11" -> "/internal/v1/validation/python";
            case "JAVASCRIPT_ES2022" -> "/internal/v1/validation/javascript";
            default -> throw new PluginApiException(
                    "PLG-RUNTIME-INVALID",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "当前版本不支持该运行时的源码校验"
            );
        };
        try {
            RuntimeValidationResponse response = restClientBuilder.clone()
                    .baseUrl(properties.runtimeUrl())
                    .build()
                    .post()
                    .uri(path)
                    // [SEC-RUNTIME-AUTH-001] Runtime 的内部接口仅接受服务间令牌，禁止把
                    // 未认证的校验请求暴露给同网段其它调用方。
                    .header("X-PCD-Service-Token", properties.internalServiceToken())
                    .body(Map.of(
                            "source", source,
                            "entrypoint", entrypoint,
                            "permissions", permissions
                    ))
                    .retrieve()
                    .body(RuntimeValidationResponse.class);
            if (response == null) {
                throw new PluginApiException(
                        "PLG-VALIDATION-UNAVAILABLE",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "校验服务暂时不可用"
                );
            }
            return response;
        } catch (PluginApiException exception) {
            log.warn(exception.getMessage());
            throw exception;
        } catch (RestClientException exception) {
            log.warn(exception.getMessage());
            throw new PluginApiException(
                    "PLG-VALIDATION-UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "校验服务暂时不可用，请稍后重试"
            );
        }
    }
}
