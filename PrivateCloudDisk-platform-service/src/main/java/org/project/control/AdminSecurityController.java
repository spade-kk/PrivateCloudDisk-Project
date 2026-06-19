package org.project.control;

import org.project.control.result.JsonResult;
import org.project.model.entity.IpBlacklistEntity;
import org.project.model.entity.SecurityEventEntity;
import org.project.model.entity.SystemConfigEntity;
import org.project.service.AdminSecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/business/admin/security")
public class AdminSecurityController extends BaseController {

    @Autowired
    private AdminSecurityService adminSecurityService;

    @GetMapping("/events")
    public JsonResult<Map<String, Object>> getSecurityEvents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean handled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        List<SecurityEventEntity> events = adminSecurityService.getSecurityEvents(
                type, severity, handled, startDate, endDate, page, pageSize);
        long total = adminSecurityService.getSecurityEventCount(
                type, severity, handled, startDate, endDate);

        Map<String, Object> result = new HashMap<>();
        result.put("list", events);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return new JsonResult<>(OK, result);
    }

    @PutMapping("/events/{eventId}/handle")
    public JsonResult<Void> handleSecurityEvent(@PathVariable Long eventId,
                                                @RequestParam String resolution,
                                                @RequestHeader("X-Admin-Id") String adminId) {
        adminSecurityService.handleSecurityEvent(eventId, UUID.fromString(adminId), resolution);
        return new JsonResult<>(OK);
    }

    @GetMapping("/ip-blacklist")
    public JsonResult<List<IpBlacklistEntity>> getIpBlacklist() {
        List<IpBlacklistEntity> list = adminSecurityService.getIpBlacklist();
        return new JsonResult<>(OK, list);
    }

    @PostMapping("/ip-blacklist")
    public JsonResult<Void> addIpBlacklist(@RequestParam String ip,
                                           @RequestParam String reason,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiresAt,
                                           @RequestHeader("X-Admin-Id") String adminId) {
        adminSecurityService.addIpBlacklist(ip, reason, UUID.fromString(adminId), expiresAt);
        return new JsonResult<>(OK);
    }

    @DeleteMapping("/ip-blacklist")
    public JsonResult<Void> removeIpBlacklist(@RequestParam String ip) {
        adminSecurityService.removeIpBlacklist(ip);
        return new JsonResult<>(OK);
    }

    @GetMapping("/configs")
    public JsonResult<List<SystemConfigEntity>> getAllConfigs() {
        List<SystemConfigEntity> configs = adminSecurityService.getAllConfigs();
        return new JsonResult<>(OK, configs);
    }

    @PutMapping("/configs")
    public JsonResult<Void> updateConfig(@RequestParam String configKey,
                                         @RequestParam String configValue) {
        adminSecurityService.updateConfig(configKey, configValue);
        return new JsonResult<>(OK);
    }
}