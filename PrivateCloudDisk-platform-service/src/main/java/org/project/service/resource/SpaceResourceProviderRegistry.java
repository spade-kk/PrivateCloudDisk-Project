package org.project.service.resource;

import org.project.service.ex.InsertException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** [REQ-GIT-SPACE-2.3] Provider 注册表，禁止业务层通过 if/else 复制资源初始化逻辑。 */
@Component
public class SpaceResourceProviderRegistry {
    private final Map<String, SpaceResourceProvider> providers;

    public SpaceResourceProviderRegistry(List<SpaceResourceProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.resourceType().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public SpaceResourceProvider require(String resourceType) {
        String normalized = resourceType == null || resourceType.isBlank()
                ? "file" : resourceType.trim().toLowerCase(Locale.ROOT);
        SpaceResourceProvider provider = providers.get(normalized);
        if (provider == null) throw new InsertException("暂不支持的空间资源类型: " + normalized);
        return provider;
    }
}
