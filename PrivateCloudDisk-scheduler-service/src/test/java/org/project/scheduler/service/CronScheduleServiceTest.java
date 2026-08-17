package org.project.scheduler.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronScheduleServiceTest {
    private final CronScheduleService service = new CronScheduleService();

    @Test
    void calculatesFiveFieldCronInDeclaredTimezone() {
        LocalDateTime next = service.next(
                "0 8 * * 1", "Asia/Shanghai",
                LocalDateTime.of(2026, 7, 26, 0, 0)
        );

        assertThat(next).isAfter(LocalDateTime.of(2026, 7, 26, 0, 0));
        assertThat(next.getHour()).isEqualTo(0);
    }

    @Test
    void rejectsSixFieldCron() {
        assertThatThrownBy(() -> service.validate("0 0 8 * * 1", "UTC"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
