package org.project.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import lombok.extern.slf4j.Slf4j;
import org.project.control.result.JsonResult;
import org.springframework.stereotype.Component;

/**
 * Sentinel 平台服务 Fallback 处理器。
 * <p>
 * 为每个 @SentinelResource 注解的方法提供 fallback 逻辑。
 * 当限流/熔断被触发时，返回友好的错误信息而非直接抛异常。
 *
 * <h3>使用方式</h3>
 * 在 Controller 方法上添加：
 * <pre>{@code
 * @SentinelResource(value = "deleteFiles", blockHandler = "deleteFilesBlockHandler")
 * public JsonResult<Void> deleteFiles(...) { ... }
 * }</pre>
 *
 * <p>
 * 注意：fallback 方法签名必须与原始方法一致，且多一个 BlockException 参数。
 */
@Slf4j
@Component
public class SentinelPlatformFallbackHandler {

    /**
     * 文件删除限流/熔断处理。
     */
    public static JsonResult<Void> deleteFilesBlockHandler(String userId, BlockException ex) {
        log.warn("deleteFiles blocked for userId={}: {}", userId, ex.getMessage());
        return buildBlockResponse(ex, "文件删除操作");
    }

    /**
     * 文件搜索限流/熔断处理。
     */
    public static JsonResult<Void> searchFilesBlockHandler(String keyword, BlockException ex) {
        log.warn("searchFiles blocked for keyword={}: {}", keyword, ex.getMessage());
        return buildBlockResponse(ex, "文件搜索");
    }

    /**
     * 上传会话创建限流处理。
     */
    public static JsonResult<Void> createUploadSessionBlockHandler(String userId, BlockException ex) {
        log.warn("createUploadSession blocked for userId={}: {}", userId, ex.getMessage());
        return buildBlockResponse(ex, "上传会话创建");
    }

    /**
     * 通用 Block 响应构建。
     */
    private static JsonResult<Void> buildBlockResponse(BlockException ex, String operation) {
        if (ex instanceof FlowException) {
            return JsonResult.error(42901, "【" + operation + "】请求过于频繁，系统流量控制已触发");
        } else if (ex instanceof ParamFlowException) {
            return JsonResult.error(42902, "【" + operation + "】操作过于频繁，热点参数限流已触发");
        } else if (ex instanceof DegradeException) {
            return JsonResult.error(50301, "【" + operation + "】服务暂时不可用，系统熔断已触发");
        } else {
            return JsonResult.error(42900, "【" + operation + "】请求被限流");
        }
    }
}