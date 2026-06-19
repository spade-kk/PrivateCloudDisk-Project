package org.project.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 文档配置（SpringDoc + Swagger UI）
 * <p>
 * 企业级方案：
 * - 按业务域分组展示 API，避免单一页面过长
 * - JWT Bearer Token 认证方案，支持 Swagger UI 在线调试
 * - 通过 springdoc.api-docs.enabled 控制是否生成 /v3/api-docs
 * - 通过 springdoc.swagger-ui.enabled 控制是否暴露 Swagger UI 页面
 * - 生产环境通过环境变量一键关闭，详见 application-docker.properties
 */
@Configuration
public class OpenApiConfig {

    @Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    // ==================== 全局 OpenAPI 定义 ====================

    @Bean
    public OpenAPI privateCloudDiskOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PrivateCloudDisk API 文档")
                        .description("""
                                ## 私有云盘平台服务 API
                                
                                ### 功能模块
                                - **文件管理** — 文件 CRUD、搜索、收藏、移动/重命名
                                - **目录管理** — 文件夹节点创建、移动、查询
                                - **上传管理** — 分片上传、断点续传
                                - **回收站** — 文件回收、恢复、彻底删除
                                - **用户管理** — 注册、登录、信息修改、密码管理
                                - **配额管理** — 存储空间查询
                                
                                ### 认证方式
                                所有业务接口需在请求头携带 `X-User-Id`（用户 UUID）用于身份识别。
                                管理接口需额外携带 JWT Token。
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("PrivateCloudDisk Team")
                                .email("dev@privateclouddisk.local"))
                        .license(new License()
                                .name("Internal Use Only")
                                .url("https://privateclouddisk.local")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("本地开发环境"),
                        new Server().url("http://platform-service:8081").description("Docker 内部网络")
                ))
                // JWT Bearer 认证方案（供管理接口使用）
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("输入 JWT Token（登录后获取）"))
                        .addSecuritySchemes("X-User-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")
                                .description("用户 UUID，所有业务接口必填")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .addSecurityItem(new SecurityRequirement().addList("X-User-Id"));
    }

    // ==================== API 分组定义 ====================

    /**
     * 文件管理 — 文件 CRUD、搜索、收藏
     */
    @Bean
    public GroupedOpenApi fileApiGroup() {
        return GroupedOpenApi.builder()
                .group("01-文件管理")
                .displayName("文件管理")
                .pathsToMatch(
                        "/business/files/**",
                        "/business/stars/**"
                )
                .build();
    }

    /**
     * 目录管理 — 文件夹节点操作
     */
    @Bean
    public GroupedOpenApi nodeApiGroup() {
        return GroupedOpenApi.builder()
                .group("02-目录管理")
                .displayName("目录管理")
                .pathsToMatch("/business/nodes/**")
                .build();
    }

    /**
     * 上传管理 — 分片上传、会话管理
     */
    @Bean
    public GroupedOpenApi uploadApiGroup() {
        return GroupedOpenApi.builder()
                .group("03-上传管理")
                .displayName("上传管理")
                .pathsToMatch("/business/uploads/**")
                .build();
    }

    /**
     * 回收站 — 文件回收与恢复
     */
    @Bean
    public GroupedOpenApi trashApiGroup() {
        return GroupedOpenApi.builder()
                .group("04-回收站")
                .displayName("回收站")
                .pathsToMatch("/business/trash/**")
                .build();
    }

    /**
     * 用户管理 — 注册、登录、信息修改
     */
    @Bean
    public GroupedOpenApi userApiGroup() {
        return GroupedOpenApi.builder()
                .group("05-用户管理")
                .displayName("用户管理")
                .pathsToMatch("/business/users/**")
                .build();
    }

    /**
     * 配额管理 — 存储空间查询
     */
    @Bean
    public GroupedOpenApi quotaApiGroup() {
        return GroupedOpenApi.builder()
                .group("06-配额管理")
                .displayName("配额管理")
                .pathsToMatch("/business/quotas/**")
                .build();
    }

    /**
     * 内部接口 — 不对外暴露，仅限内部服务调用
     */
    @Bean
    public GroupedOpenApi internalApiGroup() {
        return GroupedOpenApi.builder()
                .group("99-内部接口")
                .displayName("内部接口（仅限内网）")
                .pathsToMatch("/business/internal/**")
                .build();
    }

    @Bean
    public GroupedOpenApi otherApiGroup() {
        return GroupedOpenApi.builder()
                .group("100-其他接口")
                .displayName("其他接口")
                .pathsToMatch("/business/**")
                .build();
    }
}