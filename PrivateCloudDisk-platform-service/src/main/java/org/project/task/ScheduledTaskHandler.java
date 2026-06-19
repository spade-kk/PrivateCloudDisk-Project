package org.project.task;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.FileMapper;
import org.project.mapper.TrashTargetMapper;
import org.project.model.entity.FileEntity;
import org.project.model.entity.TrashTargetEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * XXL-Job 定时任务处理器
 *
 * 企业级任务:
 *   - clearExpiredTrashFiles: 自动清理回收站中过期文件
 *   - cleanOrphanUploads: 清理未完成的临时上传会话
 *   - statisticUserQuota: 每日存储配额统计
 */
@Slf4j
@Component
public class ScheduledTaskHandler {

    private final TrashTargetMapper trashTargetMapper;
    private final FileMapper fileMapper;

    public ScheduledTaskHandler(TrashTargetMapper trashTargetMapper, FileMapper fileMapper) {
        this.trashTargetMapper = trashTargetMapper;
        this.fileMapper = fileMapper;
    }

    /**
     * 自动清理回收站中超过 30 天的文件
     * 分片广播模式: 支持分布式分片执行
     */
    @XxlJob("clearExpiredTrashFiles")
    public void clearExpiredTrashFiles() {
        log.info(">>> 开始执行: 清理过期回收站文件");
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        try {
            // 查询超过 30 天的回收站文件
            LocalDateTime threshold = LocalDateTime.now().minusDays(30);
            List<TrashTargetEntity> expiredList = trashTargetMapper.findExpiredBefore(threshold);

            int total = expiredList.size();
            int success = 0;
            int fail = 0;

            for (int i = 0; i < expiredList.size(); i++) {
                // 分片: 只处理属于当前分片的记录
                if (i % shardTotal != shardIndex) {
                    continue;
                }

                TrashTargetEntity trash = expiredList.get(i);
                try {
                    // 删除回收站记录对应的文件
                    FileEntity file = fileMapper.selectByNodeId(trash.getNodeId());
                    if (file != null) {
                        fileMapper.softDelete(file.getFileId());
                    }
                    trashTargetMapper.deleteById(trash.getId());
                    success++;
                } catch (Exception e) {
                    log.error("清理回收站文件失败: nodeId={}", trash.getNodeId(), e);
                    fail++;
                }
            }

            XxlJobHelper.handleSuccess(String.format(
                    "分片 %d/%d 完成: 总计=%d, 成功=%d, 失败=%d",
                    shardIndex, shardTotal, total, success, fail));
        } catch (Exception e) {
            log.error("清理过期回收站文件任务异常", e);
            XxlJobHelper.handleFail("任务执行异常: " + e.getMessage());
        }
    }

    /**
     * 健康检查任务 (每 5 分钟)
     * 检查关键服务依赖是否正常
     */
    @XxlJob("healthCheckTask")
    public void healthCheckTask() {
        try {
            StringBuilder report = new StringBuilder();
            report.append("=== 系统健康检查 ===\n");

            // 数据库连接检查
            try {
                int count = fileMapper.countAll();
                report.append("[OK] 数据库连接正常, 文件总数: ").append(count).append("\n");
            } catch (Exception e) {
                report.append("[FAIL] 数据库连接异常: ").append(e.getMessage()).append("\n");
            }

            report.append("检查时间: ").append(LocalDateTime.now());
            XxlJobHelper.handleSuccess(report.toString());
        } catch (Exception e) {
            log.error("健康检查任务异常", e);
            XxlJobHelper.handleFail(e.getMessage());
        }
    }
}