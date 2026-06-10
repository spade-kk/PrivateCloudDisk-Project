package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.event.AvatarReviewEvent;
import org.project.event.EmailVerificationEvent;
import org.project.event.PhoneVerificationEvent;
import org.project.mapper.UserMapper;
import org.project.model.entity.UserEntity;
import org.project.service.UserEventPublisher;
import org.project.service.UserManageService;
import org.project.service.VerificationCodeService;
import org.project.service.ex.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageServiceImpl implements UserManageService {

    private final UserMapper userMapper;
    private final UserEventPublisher userEventPublisher;
    private final VerificationCodeService verificationCodeService;

    @Value("${file.upload.avatar-dir:/tmp/avatars}")
    private String avatarDir;

    @Override
    public String uploadAvatar(UUID user_id, MultipartFile avatarFile) {
        // 验证文件类型
        String contentType = avatarFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ServiceException("头像文件必须是图片格式");
        }

        // 验证文件大小（最大5MB）
        if (avatarFile.getSize() > 5 * 1024 * 1024) {
            throw new ServiceException("头像文件大小不能超过5MB");
        }

        // 生成文件名
        String fileName = UUID.randomUUID().toString() + getExtension(contentType);
        Path filePath = Paths.get(avatarDir, fileName);

        try {
            // 创建目录
            Files.createDirectories(filePath.getParent());
            
            // 保存文件
            avatarFile.transferTo(filePath.toFile());

            // 更新用户头像路径
            UserEntity user = new UserEntity();
            user.setId(user_id);
            user.setImage_path(filePath.toString());
            userMapper.updateUserInfo(user);

            AvatarReviewEvent event = AvatarReviewEvent.builder()
                    .eventId("avatar-review:" + user_id + ":" + System.currentTimeMillis())
                    .userId(user_id.toString())
                    .avatarPath(filePath.toString())
                    .originalFileName(fileName)
                    .fileSize(avatarFile.getSize())
                    .mimeType(contentType)
                    .createdAt(LocalDateTime.now())
                    .build();
            userEventPublisher.publishAvatarReview(event);

            log.info("用户头像上传成功: userId={}, path={}", user_id, filePath);
            return filePath.toString();

        } catch (IOException e) {
            log.error("保存头像文件失败: userId={}, error={}", user_id, e.getMessage(), e);
            throw new InsertException("保存头像文件失败");
        }
    }

    @Override
    public void changePassword(UUID user_id, String oldPassword, String newPassword) {
        // 查询用户
        UserEntity user = userMapper.findUserById(user_id);
        if (user == null) {
            throw new UserNotFoundException();
        }

        // 验证旧密码
        // TODO: 实现密码验证逻辑
        // if (!passwordEncoder.matches(oldPassword, user.getUser_password())) {
        //     throw new PasswordNotMatchException("旧密码不正确");
        // }
        
        // 更新密码
        // TODO: 实现密码加密
        // String encodedPassword = passwordEncoder.encode(newPassword);
        UserEntity updateUser = new UserEntity();
        updateUser.setId(user_id);
        updateUser.setPassword(newPassword);
        userMapper.updateUserInfo(updateUser);
        log.info("用户密码修改成功: userId={}", user_id);
    }

    @Override
    public void changeEmail(UUID user_id, String newEmail, String verificationCode) {
        // TODO: 验证验证码
        // if (!verifyCode(newEmail, verificationCode)) {
        //     throw new VerificationCodeException("验证码错误");
        // }
        
        // 检查邮箱是否已被使用
        UserEntity existingUser = userMapper.findUserByEmail(newEmail);
        if (existingUser != null) {
            throw new AccountOrPhoneNumberException("邮箱已被使用");
        }

        // 更新邮箱
        UserEntity user = new UserEntity();
        user.setId(user_id);
        user.setEmail(newEmail);
        userMapper.updateUserInfo(user);
        log.info("用户邮箱换绑成功: userId={}, newEmail={}", user_id, newEmail);
    }

    @Override
    public void changePhone(UUID user_id, String newPhone, String verificationCode) {
        // TODO: 验证验证码
        // if (!verifyCode(newPhone, verificationCode)) {
        //     throw new VerificationCodeException("验证码错误");
        // }
        
        // 检查手机号是否已被使用
        UserEntity existingUser = userMapper.findUserByPhone(newPhone);
        if (existingUser != null) {
            throw new PhoneNumberDuplicatedException();
        }

        // 更新手机号
        UserEntity user = new UserEntity();
        user.setId(user_id);
        user.setPhone_number(newPhone);
        userMapper.updateUserInfo(user);
        log.info("用户手机号换绑成功: userId={}, newPhone={}", user_id, newPhone);
    }

    @Override
    public UserEntity getUserInfo(UUID user_id) {
        UserEntity user = userMapper.findUserById(user_id);
        if (user == null) {
            throw new UserNotFoundException();
        }
        return user;
    }

    @Override
    public void updateUserInfo(UUID user_id, String userName) {
        UserEntity user = new UserEntity();
        user.setId(user_id);
        user.setName(userName);
        userMapper.updateUserInfo(user);
        log.info("用户信息更新成功: userId={}", user_id);
    }

    @Override
    public void sendEmailVerificationCode(String email) {
        String code = verificationCodeService.generateCode();
        String eventId = "email-verify:" + email + ":" + System.currentTimeMillis();

        boolean stored = verificationCodeService.storeEmailCode(email, code, 300);
        if (!stored) {
            log.warn("邮箱验证码频率超限，拒绝发送: email={}", email);
            throw new RateLimitExceededException("发送频率超限，请稍后再试");
        }

        EmailVerificationEvent event = EmailVerificationEvent.builder()
                .eventId(eventId)
                .email(email)
                .verificationCode(code)
                .expireSeconds(300)
                .purpose("VERIFY")
                .createdAt(LocalDateTime.now())
                .build();
        userEventPublisher.publishEmailVerification(event);

        log.info("已发送邮箱验证码事件: email={}", email);
    }

    @Override
    public void sendPhoneVerificationCode(String phone) {
        String code = verificationCodeService.generateCode();
        String eventId = "phone-verify:" + phone + ":" + System.currentTimeMillis();

        boolean stored = verificationCodeService.storePhoneCode(phone, code, 300);
        if (!stored) {
            log.warn("手机验证码频率超限，拒绝发送: phone={}", phone);
            throw new RateLimitExceededException("发送频率超限，请稍后再试");
        }

        PhoneVerificationEvent event = PhoneVerificationEvent.builder()
                .eventId(eventId)
                .phone(phone)
                .verificationCode(code)
                .expireSeconds(300)
                .purpose("VERIFY")
                .createdAt(LocalDateTime.now())
                .build();
        userEventPublisher.publishPhoneVerification(event);

        log.info("已发送手机验证码事件: phone={}", phone);
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
