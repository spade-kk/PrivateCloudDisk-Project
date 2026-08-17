package org.project.context;

import java.util.UUID;

/**
 * 空间请求上下文。
 *
 * <p>需求：空间管理能力全量集成（二、四-4）。
 * 原行为：业务层只能从 user_id 推断资源范围，无法区分同一用户参与的多个空间。
 * 新行为：由统一过滤器解析 X-Space-Id 后写入当前线程，Mapper/Service 可透明读取，
 * 不需要修改既有 Controller URL、请求 DTO 和绝大多数业务方法签名。</p>
 *
 * <p>影响范围：仅当前 HTTP 请求线程；请求完成后必须调用 clear()，防止线程池复用时串空间。</p>
 */
public final class SpaceContextHolder {

    private static final ThreadLocal<SpaceContext> CONTEXT = new ThreadLocal<>();

    private SpaceContextHolder() {
    }

    public static void set(SpaceContext context) {
        CONTEXT.set(context);
    }

    public static SpaceContext get() {
        return CONTEXT.get();
    }

    public static UUID getSpaceId() {
        SpaceContext context = CONTEXT.get();
        return context == null ? null : context.spaceId();
    }

    public static UUID getUserId() {
        SpaceContext context = CONTEXT.get();
        return context == null ? null : context.userId();
    }

    public static boolean isPersonalSpace() {
        SpaceContext context = CONTEXT.get();
        return context != null && context.personalSpace();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * @param explicitSpaceId 是否由客户端显式携带 X-Space-Id
     * @param personalSpace 当前空间是否为默认个人空间；用于兼容历史 NULL 空间数据
     */
    public record SpaceContext(
            UUID spaceId,
            UUID userId,
            String spaceName,
            String role,
            boolean explicitSpaceId,
            boolean personalSpace
    ) {
    }
}
