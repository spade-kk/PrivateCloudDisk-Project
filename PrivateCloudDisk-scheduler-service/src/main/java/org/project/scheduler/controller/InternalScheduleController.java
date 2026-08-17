package org.project.scheduler.controller;

import jakarta.validation.Valid;
import org.project.scheduler.model.SchedulerModels.CreateScheduleRequest;
import org.project.scheduler.service.SchedulerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 仅供 Workflow Service 调用的调度控制面。 */
@RestController
@RequestMapping("/internal/v1/schedules")
public class InternalScheduleController {
    private final SchedulerService service;

    public InternalScheduleController(SchedulerService service) {
        this.service = service;
    }

    @PostMapping
    public Object create(@Valid @RequestBody CreateScheduleRequest request) {
        return service.create(request);
    }

    @GetMapping("/workflows/{workflowId}")
    public Object list(@PathVariable String workflowId) {
        return service.list(workflowId);
    }

    @PatchMapping("/{scheduleId}")
    public Map<String, Object> setEnabled(
            @PathVariable String scheduleId,
            @RequestParam String userId,
            @RequestParam boolean enabled
    ) {
        service.setEnabled(scheduleId, userId, enabled);
        return Map.of("schedule_id", scheduleId, "enabled", enabled);
    }
}
