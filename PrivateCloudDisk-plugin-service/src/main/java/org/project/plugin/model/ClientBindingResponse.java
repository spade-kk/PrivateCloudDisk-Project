package org.project.plugin.model;

import java.util.List;

/** 客户端注册服务返回的可信设备绑定投影。 */
public record ClientBindingResponse(
        String clientId,
        String userId,
        String clientType,
        String platform,
        String appVersion,
        List<String> capabilities,
        String status
) {
}
