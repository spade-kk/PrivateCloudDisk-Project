package org.project.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.project.context.SpaceContextHolder;
import org.project.service.SpaceOperation;
import org.project.service.SpacePermissionService;
import org.project.service.ex.FileNotExistException;
import org.project.service.ex.NodeNotExistException;
import org.project.service.ex.OverstepAuthorityException;
import org.project.service.ex.ServiceException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件业务空间上下文拦截器。
 *
 * <p>需求：空间管理能力全量集成（二、三、五）。
 * 原行为：各接口只接收 file_id/node_id，并直接按当前用户查询。
 * 新行为：在 Controller 执行前统一解析 X-Space-Id、校验成员/权限和路径资源归属；
 * Controller 路径、参数、响应体保持不变。</p>
 *
 * <p>注意：公开分享与内部存储回调拥有独立授权链，不使用调用者当前空间头，
 * 因此不在本拦截器的路径范围内。</p>
 */
@Component
public class SpaceContextInterceptor implements HandlerInterceptor {

    private static final Pattern FILE_PATH =
            Pattern.compile("/(?:files|stars/files|trash/files)/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern NODE_PATH =
            Pattern.compile("/(?:nodes|stars/folders|trash/folders)/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern UPLOAD_PATH =
            Pattern.compile("/uploads/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern SPACE_PATH =
            Pattern.compile("/business/space/([0-9a-fA-F-]{36})(?:/|$)");

    private final SpacePermissionService permissionService;
    private final ObjectMapper objectMapper;

    public SpaceContextInterceptor(
            SpacePermissionService permissionService,
            ObjectMapper objectMapper) {
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {
        try {
            String rawUserId = request.getHeader("X-User-Id");
            /*
             * 空间管理能力全量集成（需求二/七）：
             * 原行为直接 UUID.fromString(null)，缺失网关注入头时会抛出 NPE 并返回 500；
             * 新行为将缺失或空白用户头统一归类为请求格式错误，避免异常泄漏并保持响应稳定。
             */
            if (rawUserId == null || rawUserId.isBlank()) {
                throw new IllegalArgumentException("缺少用户标识");
            }
            UUID userId = UUID.fromString(rawUserId);
            SpaceContextHolder.SpaceContext context = permissionService.resolveContext(
                    userId, request.getHeader("X-Space-Id"));
            SpaceContextHolder.set(context);

            // [SPACE-COLLAB-SEC-02] 路径空间与请求头空间必须一致，避免利用旧头跨空间修改成员/设置。
            Matcher spaceMatcher = SPACE_PATH.matcher(request.getRequestURI());
            if (spaceMatcher.find() && !context.spaceId().equals(UUID.fromString(spaceMatcher.group(1)))) {
                throw new OverstepAuthorityException("空间上下文与目标空间不一致");
            }

            permissionService.requireOperation(context, resolveOperation(request));
            validatePathResource(request);

            request.setAttribute("resolvedSpaceId", context.spaceId().toString());
            request.setAttribute("resolvedSpaceName", context.spaceName());
            response.setHeader("X-Resolved-Space-Id", context.spaceId().toString());
            return true;
        } catch (IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, 40001, "用户或空间标识格式无效");
        } catch (FileNotExistException | NodeNotExistException ex) {
            // 防止通过错误差异枚举其他空间资源，统一返回资源不存在。
            writeError(response, HttpServletResponse.SC_NOT_FOUND, 40401, ex.getMessage());
        } catch (OverstepAuthorityException ex) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, 40301, ex.getMessage());
        } catch (ServiceException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, 40002, ex.getMessage());
        }
        SpaceContextHolder.clear();
        return false;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        // 需求三-4：ThreadLocal 必须在请求结束时清理，避免线程池复用导致跨空间串读。
        SpaceContextHolder.clear();
    }

    private void validatePathResource(HttpServletRequest request) {
        String uri = request.getRequestURI();
        Matcher fileMatcher = FILE_PATH.matcher(uri);
        if (fileMatcher.find()) {
            UUID targetId = UUID.fromString(fileMatcher.group(1));
            // 标签详情接口沿用 /tags/files/{target_id}，实际目标类型由查询参数决定。
            if (uri.contains("/business/tags/files/")
                    && "folder".equalsIgnoreCase(request.getParameter("target_type"))) {
                permissionService.requireNodeInCurrentSpace(targetId);
            } else {
                permissionService.requireFileInCurrentSpace(targetId);
            }
            return;
        }

        Matcher nodeMatcher = NODE_PATH.matcher(uri);
        if (nodeMatcher.find()) {
            permissionService.requireNodeInCurrentSpace(UUID.fromString(nodeMatcher.group(1)));
            return;
        }

        Matcher uploadsMatcher = UPLOAD_PATH.matcher(uri);
        if (uploadsMatcher.find()) {
            permissionService.requireUploadSessionInCurrentSpace(UUID.fromString(uploadsMatcher.group(1)));
        }
    }

    private SpaceOperation resolveOperation(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (uri.contains("/quotas") || uri.contains("/stars")) {
            return SpaceOperation.VIEW;
        }
        if (uri.contains("/shares")) {
            return "GET".equals(method) ? SpaceOperation.VIEW : SpaceOperation.SHARE;
        }
        if (uri.contains("/trash")) {
            if ("GET".equals(method)) return SpaceOperation.VIEW;
            if (uri.endsWith("/restore")) return SpaceOperation.EDIT;
            return SpaceOperation.DELETE;
        }
        if (uri.contains("/tags")) {
            if ("POST".equals(method) && uri.endsWith("/files/batch")) {
                return SpaceOperation.VIEW;
            }
            return "GET".equals(method) ? SpaceOperation.VIEW : SpaceOperation.EDIT;
        }
        if (uri.contains("/uploads")) {
            return "POST".equals(method) ? SpaceOperation.UPLOAD : SpaceOperation.EDIT;
        }
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return SpaceOperation.READ;
        }
        if ("DELETE".equals(method)) {
            return SpaceOperation.DELETE;
        }
        return SpaceOperation.EDIT;
    }

    private void writeError(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
