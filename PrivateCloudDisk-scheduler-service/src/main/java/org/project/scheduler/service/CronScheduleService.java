package org.project.scheduler.service;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** 五段 cron 与时区计算；内部转换为 Spring 六段 cron，不使用系统默认时区。 */
@Service
public class CronScheduleService {
    public LocalDateTime next(String cron, String timezone, LocalDateTime afterUtc) {
        String normalized = normalize(cron);
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime after = afterUtc.atZone(ZoneId.of("UTC")).withZoneSameInstant(zone);
        ZonedDateTime next = CronExpression.parse(normalized).next(after);
        if (next == null) {
            throw new IllegalArgumentException("cron 表达式没有下一次触发时间");
        }
        return next.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }

    public void validate(String cron, String timezone) {
        ZoneId.of(timezone);
        CronExpression.parse(normalize(cron));
    }

    private static String normalize(String cron) {
        if (cron == null) {
            throw new IllegalArgumentException("cron 不能为空");
        }
        String trimmed = cron.trim().replaceAll("\\s+", " ");
        if (trimmed.split(" ").length != 5) {
            throw new IllegalArgumentException("仅支持标准五段 cron 表达式");
        }
        return "0 " + trimmed;
    }
}
