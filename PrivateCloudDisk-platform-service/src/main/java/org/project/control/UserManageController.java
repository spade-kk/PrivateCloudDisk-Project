package org.project.control;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.entity.UserEntity;
import org.project.model.vo.UserProfileVO;
import org.project.model.vo.VoMapper;
import org.project.service.UserManageService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户管理控制器
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/business/users")
public class UserManageController extends BaseController {
    
    private final UserManageService userManageService;
    
    /**
     * 上传用户头像
     */
    @PostMapping("/avatar")
    public JsonResult<String> uploadAvatar(
            @RequestParam("avatar") MultipartFile avatarFile,
            @RequestHeader("X-User-Id") String user_id) {
        String avatarPath = userManageService.uploadAvatar(user_id, avatarFile);
        return new JsonResult<>(OK, avatarPath);
    }
    
    /**
     * 修改用户密码
     */
    @PostMapping("/password")
    public JsonResult<Void> changePassword(
            @RequestHeader("X-User-Id") String user_id,
            @RequestParam("oldPassword") @NotBlank String oldPassword,
            @RequestParam("newPassword") @NotBlank String newPassword) {
        userManageService.changePassword(user_id, oldPassword, newPassword);
        return new JsonResult<>(OK);
    }
    
    /**
     * 换绑邮箱
     */
    @PostMapping("/email")
    public JsonResult<Void> changeEmail(
            @RequestHeader("X-User-Id") String user_id,
            @RequestParam("newEmail") @Email String newEmail,
            @RequestParam("verificationCode") @NotBlank String verificationCode) {
        userManageService.changeEmail(user_id, newEmail, verificationCode);
        return new JsonResult<>(OK);
    }
    
    /**
     * 换绑手机号
     */
    @PostMapping("/phone")
    public JsonResult<Void> changePhone(
            @RequestHeader("X-User-Id") String user_id,
            @RequestParam("newPhone") @Pattern(regexp = "^1[3-9]\\d{9}$") String newPhone,
            @RequestParam("verificationCode") @NotBlank String verificationCode) {
        userManageService.changePhone(user_id, newPhone, verificationCode);
        return new JsonResult<>(OK);
    }
    
    /**
     * 获取用户个人信息
     */
    @GetMapping("/info")
    public JsonResult<UserProfileVO> getUserInfo(@RequestHeader("X-User-Id") String user_id) {
        UserEntity user = userManageService.getUserInfo(user_id);
        return new JsonResult<>(OK, VoMapper.toUserProfileVO(user));
    }
    
    /**
     * 更新用户个人信息
     */
    @PutMapping("/info")
    public JsonResult<Void> updateUserInfo(
            @RequestHeader("X-User-Id") String user_id,
            @RequestParam("userName") @NotBlank String userName) {
        userManageService.updateUserInfo(user_id, userName);
        return new JsonResult<>(OK);
    }
    
    /**
     * 发送邮箱验证码
     */
    @PostMapping("/email/verification-code")
    public JsonResult<Void> sendEmailVerificationCode(
            @RequestParam("email") @Email String email) {
        userManageService.sendEmailVerificationCode(email);
        return new JsonResult<>(OK);
    }
    
    /**
     * 发送手机验证码
     */
    @PostMapping("/phone/verification-code")
    public JsonResult<Void> sendPhoneVerificationCode(
            @RequestParam("phone") @Pattern(regexp = "^1[3-9]\\d{9}$") String phone) {
        userManageService.sendPhoneVerificationCode(phone);
        return new JsonResult<>(OK);
    }
}
