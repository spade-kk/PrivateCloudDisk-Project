package org.project.control;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.context.SpaceContextHolder;
import org.project.model.dto.InternalAutomationAuthorizeRequest;
import org.project.service.SpaceOperation;
import org.project.service.SpacePermissionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Plugin/Workflow 服务使用的 Platform 实时权限判定。
 *
 * <p>不复制 owner/admin 规则，统一委托既有 SpacePermissionService。该路径只允许
 * 服务网络访问，不得通过公网 Gateway 暴露。</p>
 */
@RestController
@RequestMapping("/business/internal/automation")
@RequiredArgsConstructor
public class InternalAutomationController {
    private final SpacePermissionService spacePermissionService;

    @PostMapping("/authorize")
    public Map<String, Object> authorize(
            @Valid @RequestBody InternalAutomationAuthorizeRequest request
    ) {
        UUID userId = UUID.fromString(request.userId());
        SpaceContextHolder.SpaceContext context =
                spacePermissionService.resolveContext(userId, request.spaceId());
        SpaceContextHolder.set(context);
        try {
            SpaceOperation operation = switch (request.operation()) {
                case "FILE_CONTENT_PREPROCESS" -> SpaceOperation.EDIT;
                case "FILE_READ", "FILE_AVAILABLE_METADATA", "WORKFLOW_EXECUTE" -> SpaceOperation.READ;
                case "FILE_SHARE" -> SpaceOperation.SHARE;
                // 插件生态 Sprint 0：空间插件安装/解绑统一复用现有 MANAGE 权限。
                case "PLUGIN_MANAGE", "WORKFLOW_MANAGE" -> SpaceOperation.MANAGE;
                default -> throw new IllegalArgumentException("不支持的自动化权限操作");
            };
            spacePermissionService.requireOperation(context, operation);
            if (request.fileId() != null && !request.fileId().isBlank()) {
                spacePermissionService.requireFileInCurrentSpace(UUID.fromString(request.fileId()));
            } else if (operation != SpaceOperation.MANAGE
                    && !"WORKFLOW_EXECUTE".equals(request.operation())) {
                throw new IllegalArgumentException("文件级自动化权限校验必须提供 file_id");
            }
            return Map.of(
                    "allowed", true,
                    "space_id", context.spaceId().toString(),
                    "personal_space", context.personalSpace()
            );
        } finally {
            SpaceContextHolder.clear();
        }
    }

    /**
     * [CLOUDFLOW-SEC-004] 返回执行时重新解析的自动化权限快照。
     * Workflow Service 只能将该结果与 DSL 声明权限取交集，禁止把声明字段直接当作 granted。
     */
    @PostMapping("/permissions")
    public Map<String, Object> permissions(
            @Valid @RequestBody InternalAutomationAuthorizeRequest request
    ) {
        UUID userId = UUID.fromString(request.userId());
        SpaceContextHolder.SpaceContext context =
                spacePermissionService.resolveContext(userId, request.spaceId());
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            return Map.of(
                    "space_id", context.spaceId().toString(),
                    "granted_permissions", spacePermissionService.resolveAutomationPermissions(context)
            );
        } finally {
            SpaceContextHolder.clear();
        }
    }
}
