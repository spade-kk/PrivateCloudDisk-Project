//package org.project.control;
//
//import org.project.control.result.JsonResult;
//import org.project.model.entity.AdminAuditLogEntity;
//import org.project.service.AdminAuditLogService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.format.annotation.DateTimeFormat;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//@RestController
//@RequestMapping("/business/admin/audit-logs")
//public class AdminAuditLogController extends BaseController {
//
//    @Autowired
//    private AdminAuditLogService adminAuditLogService;
//
//    @GetMapping
//    public JsonResult<Map<String, Object>> getAuditLogs(
//            @RequestParam(required = false) String adminId,
//            @RequestParam(required = false) String action,
//            @RequestParam(required = false) String resource,
//            @RequestParam(required = false) String status,
//            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
//            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
//            @RequestParam(defaultValue = "1") int page,
//            @RequestParam(defaultValue = "20") int pageSize) {
//        UUID adminUuid = adminId != null ? UUID.fromString(adminId) : null;
//        List<AdminAuditLogEntity> logs = adminAuditLogService.getLogs(
//                adminUuid, action, resource, status, startDate, endDate, page, pageSize);
//        long total = adminAuditLogService.getLogCount(
//                adminUuid, action, resource, status, startDate, endDate);
//
//        Map<String, Object> result = new HashMap<>();
//        result.put("list", logs);
//        result.put("total", total);
//        result.put("page", page);
//        result.put("pageSize", pageSize);
//        return new JsonResult<>(OK, null);
//    }
//}