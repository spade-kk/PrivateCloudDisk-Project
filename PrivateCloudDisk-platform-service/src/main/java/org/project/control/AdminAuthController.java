package org.project.control;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.project.model.dto.AdminLoginRequest;
import org.project.model.vo.AdminLoginVO;
import org.project.model.vo.AdminUserVO;
import org.project.control.result.JsonResult;
import org.project.service.AdminAuthService;
import org.project.service.AdminUserService;
import org.project.util.ClientIpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/business/admin/auth")
public class AdminAuthController extends BaseController {

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private AdminUserService adminUserService;

    @PostMapping("/login")
    public JsonResult<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request,
                                          @RequestHeader(value = "X-Admin-Key", required = true) String adminKey,
                                          HttpServletRequest httpRequest) {
        String clientIp = ClientIpUtil.resolveClientIp(httpRequest);
        AdminLoginVO result = adminAuthService.login(request, clientIp, adminKey);
        return new JsonResult<>(OK, result);
    }

    @PostMapping("/logout")
    public JsonResult<Void> logout(@RequestHeader("X-Admin-Id") String adminId,
                                   HttpServletRequest httpRequest) {
        String clientIp = ClientIpUtil.resolveClientIp(httpRequest);
        adminAuthService.logout(UUID.fromString(adminId), clientIp);
        return new JsonResult<>(OK, null);
    }

    @PostMapping("/refresh")
    public JsonResult<AdminLoginVO> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken,
                                                 HttpServletRequest httpRequest) {
        String clientIp = ClientIpUtil.resolveClientIp(httpRequest);
        AdminLoginVO result = adminAuthService.refreshToken(refreshToken, clientIp);
        return new JsonResult<>(OK, result);
    }

    @GetMapping("/me")
    public JsonResult<AdminUserVO> getCurrentAdmin(@RequestHeader("X-Admin-Id") String adminId) {
        AdminUserVO result = adminUserService.getAdminInfo(UUID.fromString(adminId));
        return new JsonResult<>(OK, result);
    }
}