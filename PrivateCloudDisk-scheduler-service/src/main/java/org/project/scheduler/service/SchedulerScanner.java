package org.project.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 以短事务持续领取到期计划，单次最多处理 100 条防止长期占用调度线程。 */
@Component
public class SchedulerScanner {
    private static final Logger log = LoggerFactory.getLogger(SchedulerScanner.class);
    private final SchedulerService service;

    public SchedulerScanner(SchedulerService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${pcd.scheduler.scan-delay-ms:1000}")
    public void scan() {
        try {
            for (int index = 0; index < 100 && service.processOneDue(); index++) {
                // 每个计划在独立事务中完成 Gate/Outbox 推进。
            }
        } catch (RuntimeException exception) {
            log.error("Scheduler 扫描到期计划失败", exception);
        }
    }
}
