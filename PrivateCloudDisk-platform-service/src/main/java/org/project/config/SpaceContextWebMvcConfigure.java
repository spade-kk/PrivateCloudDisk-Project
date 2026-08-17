package org.project.config;

import org.project.interceptor.SpaceContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 空间上下文拦截器注册配置。
 *
 * <p>需求：空间管理能力全量集成（三-4/五）。
 * 仅覆盖需要空间隔离的已登录文件业务；空间 CRUD、公开分享、内部存储回调继续使用各自授权模型。</p>
 */
@Configuration
public class SpaceContextWebMvcConfigure implements WebMvcConfigurer {

    private final SpaceContextInterceptor spaceContextInterceptor;

    public SpaceContextWebMvcConfigure(SpaceContextInterceptor spaceContextInterceptor) {
        this.spaceContextInterceptor = spaceContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(spaceContextInterceptor)
                .addPathPatterns(
                        "/business/files/**",
                        "/business/nodes/**",
                        "/business/uploads/**",
                        "/business/stars/**",
                        "/business/tags/**",
                        "/business/trash/**",
                        "/business/recent/**",
                        "/business/shares/**",
                        // [SPACE-COLLAB-SEC-01] 成员/设置/审批写操作统一复用 X-Space-Id 权限上下文。
                        "/business/space/**",
                        "/business/quotas/**")
                .excludePathPatterns(
                        "/business/public/**",
                        "/business/internal/**",
                        "/business/space/invitations/redeem",
                        // 需求五-10：全量空间配额按成员关系返回，不依赖当前 X-Space-Id。
                        "/business/quotas/space-quotas")
                .order(20);
    }
}
