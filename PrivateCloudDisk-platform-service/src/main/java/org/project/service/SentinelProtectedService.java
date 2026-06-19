package org.project.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.project.model.vo.FileVO;
import org.project.service.ex.FileNotExistException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Sentinel 流量控制 + 熔断降级示例 Service
 *
 * 企业级用法:
 *   - @SentinelResource 注解: 声明资源并配置降级逻辑
 *   - value: 资源名称 (显示在 Sentinel Dashboard)
 *   - blockHandler: 限流/熔断时的降级方法
 *   - fallback: 业务异常时的降级方法
 *
 * 在 Sentinel Dashboard 中可动态配置:
 *   - 流控规则: QPS 阈值、线程数阈值
 *   - 熔断规则: 慢调用比例、异常比例、异常数
 *   - 热点规则: 对特定参数进行精细化限流
 */
@Slf4j
@Service
public class SentinelProtectedService {

    /**
     * 高频文件查询接口 — 需要限流保护
     *
     * @SentinelResource 注解会在方法调用前检查流控规则
     * 如果被限流，会调用 blockHandler 指定的降级方法
     */
    @SentinelResource(
            value = "getFileList",
            blockHandler = "getFileListBlockHandler",
            fallback = "getFileListFallback"
    )
    public List<FileVO> getFileList(String userId, String parentId) {
        // 模拟文件列表查询
        log.debug("查询文件列表: userId={}, parentId={}", userId, parentId);

        // 实际业务逻辑...
        return Collections.emptyList();
    }

    /**
     * 限流/熔断降级方法 (blockHandler)
     * 当被 Sentinel 拦截时调用
     */
    public List<FileVO> getFileListBlockHandler(String userId, String parentId, BlockException ex) {
        log.warn("文件列表查询被限流: userId={}, parentId={}, rule={}", userId, parentId, ex.getRule());
        return Collections.emptyList(); // 返回空列表或提示用户稍后再试
    }

    /**
     * 业务异常降级方法 (fallback)
     * 当业务逻辑抛出异常时调用
     */
    public List<FileVO> getFileListFallback(String userId, String parentId, Throwable ex) {
        log.error("文件列表查询业务异常: userId={}, parentId={}", userId, parentId, ex);
        return Collections.emptyList();
    }

    /**
     * 文件上传 — 热点参数限流
     * 例如: 限制单个用户每秒最多上传 5 个文件
     */
    @SentinelResource(
            value = "uploadFile",
            blockHandler = "uploadFileBlockHandler"
    )
    public String uploadFile(String userId, String fileName, long fileSize) {
        log.debug("文件上传: userId={}, fileName={}, size={}", userId, fileName, fileSize);
        return "upload-success";
    }

    public String uploadFileBlockHandler(String userId, String fileName, long fileSize, BlockException ex) {
        log.warn("文件上传被限流: userId={}, fileName={}", userId, fileName);
        throw new RuntimeException("上传过于频繁，请稍后再试");
    }
}