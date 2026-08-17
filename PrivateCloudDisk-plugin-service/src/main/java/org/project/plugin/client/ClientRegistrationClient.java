package org.project.plugin.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.ClientBindingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/** 本地插件分发前实时查询客户端注册服务，不在 Plugin Service 复制设备身份状态。 */
@Component
@RequiredArgsConstructor
public class ClientRegistrationClient {
    private final RestClient.Builder restClientBuilder;
    private final PluginProperties properties;

    public ClientBindingResponse requireBinding(String clientId, String userId) {
        try {
            JsonNode response = restClientBuilder.clone()
                    .baseUrl(properties.clientRegistrationUrl())
                    .build()
                    .get()
                    .uri(builder -> builder
                            .path("/client/internal/{clientId}/plugin-binding")
                            .queryParam("user_id", userId)
                            .build(clientId))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = response == null ? null : response.path("data");
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw unauthorized();
            }
            List<String> capabilities = new ArrayList<>();
            data.path("capabilities").forEach(value -> capabilities.add(value.asText()));
            return new ClientBindingResponse(
                    data.path("client_id").asText(),
                    data.path("user_id").asText(),
                    data.path("client_type").asText(),
                    data.path("platform").asText(),
                    data.path("app_version").asText(),
                    List.copyOf(capabilities),
                    data.path("status").asText()
            );
        } catch (PluginApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PluginApiException(
                    "PLG-CLIENT-IDENTITY-UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "暂时无法核验客户端身份，请稍后重试"
            );
        }
    }

    private static PluginApiException unauthorized() {
        return new PluginApiException(
                "PLG-CLIENT-NOT-BOUND",
                HttpStatus.FORBIDDEN,
                "当前客户端未绑定、已吊销或不属于当前账号"
        );
    }
}
