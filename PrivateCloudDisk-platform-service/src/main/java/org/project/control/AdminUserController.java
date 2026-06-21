//package org.project.control;
//
//import jakarta.validation.Valid;
//import org.project.model.dto.AdminUserCreateRequest;
//import org.project.model.vo.AdminUserVO;
//import org.project.control.result.JsonResult;
//import org.project.service.AdminUserService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.UUID;
//
//@RestController
//@RequestMapping("/business/admin/users")
//public class AdminUserController extends BaseController {
//
//    @Autowired
//    private AdminUserService adminUserService;
//
//    @PostMapping
//    public JsonResult<Void> createAdmin(@Valid @RequestBody AdminUserCreateRequest request,
//                                        @RequestHeader("X-Admin-Id") String adminId) {
//        adminUserService.createAdmin(request, UUID.fromString(adminId));
//        return new JsonResult<>(OK);
//    }
//
//    @PutMapping
//    public JsonResult<Void> updateAdmin(@Valid @RequestBody AdminUserCreateRequest request,
//                                        @RequestHeader("X-Admin-Id") String adminId) {
//        adminUserService.updateAdmin(request, UUID.fromString(adminId));
//        return new JsonResult<>(OK);
//    }
//
//    @DeleteMapping("/{adminId}")
//    public JsonResult<Void> deleteAdmin(@PathVariable String adminId,
//                                        @RequestHeader("X-Admin-Id") String operatorId) {
//        adminUserService.deleteAdmin(UUID.fromString(adminId), UUID.fromString(operatorId));
//        return new JsonResult<>(OK);
//    }
//
//    @PutMapping("/{adminId}/reset-password")
//    public JsonResult<Void> resetPassword(@PathVariable String adminId,
//                                          @RequestParam String newPassword,
//                                          @RequestHeader("X-Admin-Id") String operatorId) {
//        adminUserService.resetAdminPassword(UUID.fromString(adminId), newPassword, UUID.fromString(operatorId));
//        return new JsonResult<>(OK);
//    }
//
//    @GetMapping("/{adminId}")
//    public JsonResult<AdminUserVO> getAdminInfo(@PathVariable String adminId) {
//        AdminUserVO result = adminUserService.getAdminInfo(UUID.fromString(adminId));
//        return new JsonResult<>(OK, result);
//    }
//}