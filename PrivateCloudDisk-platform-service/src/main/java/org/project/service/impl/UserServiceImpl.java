package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.event.UserRegisteredEvent;
import org.project.model.dto.LoginRequest;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.UserEntity;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.UserMapper;
import org.project.security.ApiAbuseProtectionService;
import org.project.security.CaptchaVerifier;
import org.project.service.*;
import org.project.service.ex.*;
import org.project.util.JwtUtil;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 用户服务实现。
 * <p>
 * 密码安全增强：
 * <ol>
 *   <li>支持 PBKDF2-SHA256 预哈希密码（Web 前端 60 万次迭代后传输）</li>
 *   <li>后端使用 BCrypt(12 rounds) 进行二次哈希后存储</li>
 *   <li>自动检测密码格式：64 位十六进制 = 预哈希，否则 = 原始密码</li>
 *   <li>向后兼容旧版 BCrypt 存储的密码</li>
 * </ol>
 * <p>
 * 密码存储格式：
 * <pre>
 * 新用户注册：BCrypt(PBKDF2-SHA256(raw_password))
 * 旧用户登录：BCrypt(raw_password)  [自动迁移]
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final FolderNodeMapper folderNodeMapper;
    private final DirectoryTreeService directoryTreeService;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher userEventPublisher;
    private final VerificationCodeService verificationCodeService;
    private final ApiAbuseProtectionService apiAbuseProtectionService;
    private final CaptchaVerifier captchaVerifier;
    private final JwtUtil jwtUtil;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** PBKDF2-SHA256 预哈希密码格式：严格 64 位十六进制 */
    private static final Pattern PRE_HASHED_PATTERN =
            Pattern.compile("^[a-fA-F0-9]{64}$");

    /**
     * 判断密码是否为 PBKDF2-SHA256 预哈希格式。
     */
    private boolean isPreHashedPassword(String password) {
        return password != null && PRE_HASHED_PATTERN.matcher(password).matches();
    }

    @Override
    public String login(String account, String phoneNumber, String password,
                        String captchaToken, String captchaAction, String clientIp) {
        // 1. 业务层限流：登录失败次数检查（IP 维度 + 账号维度）
        // 构造临时 LoginRequest 用于兼容现有的 ApiAbuseProtectionService
        LoginRequest tempRequest = new LoginRequest();
        tempRequest.setAccount(account);
        tempRequest.setPhone_number(phoneNumber);
        apiAbuseProtectionService.checkLoginStart(tempRequest, clientIp);

        try {
            // 2. 人机验证码校验
            captchaVerifier.verify(
                    captchaToken,
                    captchaAction != null ? captchaAction : "login",
                    clientIp);

            // 3. 账号密码认证
            if (account == null && phoneNumber == null) {
                throw new AccountOrPhoneNumberException();
            }

            UserEntity result;
            if (account != null) {
                result = userMapper.findUserByAccount(account);
            } else {
                result = userMapper.findUserByPhoneNumber(phoneNumber);
            }

            if (result == null) {
                throw new UserNotFoundException();
            }

            if (account != null && phoneNumber != null &&
                    (!result.getAccount().equals(account) ||
                            !result.getPhone_number().equals(phoneNumber))) {
                throw new AccountOrPhoneNumberException("用户账号手机号不匹配");
            }

            if (!passwordMatches(password, result.getPassword())) {
                throw new PasswordNotMatchException();
            }

            // 密码自动迁移
            if (!isBcryptHash(result.getPassword())) {
                String hashedPassword = passwordEncoder.encode(password);
                userMapper.updateUserPassword(result.getId(), hashedPassword);
                log.info("用户密码已从明文迁移至 BCrypt: userId={}", result.getId());
            }

            // 4. 生成 JWT 令牌
            String token = jwtUtil.generateAccessToken(result.getId().toString());

            // 5. 登录成功，清除失败记录
            apiAbuseProtectionService.recordLoginSuccess(tempRequest, clientIp);

            log.info("用户登录成功: userId={}, account={}", result.getId(), result.getAccount());
            return token;

        } catch (ServiceException e) {
            // 登录失败，记录失败次数
            tempRequest = new LoginRequest();
            tempRequest.setAccount(account);
            tempRequest.setPhone_number(phoneNumber);
            apiAbuseProtectionService.recordLoginFailure(tempRequest, clientIp);
            throw e;
        }
    }

    @Override
    public String register(String phoneNumber, String email, String password, String code, String name, String clientIp) {

        // 1. 业务层限流：注册频率检查
        String identity = (phoneNumber != null && !phoneNumber.isBlank())
                ? phoneNumber : email;
        apiAbuseProtectionService.checkRegisterStart(identity, clientIp);

        String targetType = null;
        String target = null;
        try {
            // 2. 确定目标类型和值
            if (phoneNumber != null && !phoneNumber.isBlank()) {
                targetType = "phone";
                target = phoneNumber.trim();
            } else if (email != null && !email.isBlank()) {
                targetType = "email";
                target = email.trim().toLowerCase();
            } else {
                throw new AccountOrPhoneNumberException();
            }

            // 3. 防爆破：检查验证码失败次数（同一 IP 15 分钟内最多 5 次失败）
            verificationCodeService.checkCodeAttempts(targetType, target, "REGISTER", clientIp);

            // 4. 验证验证码（一次性使用，验证后自动删除）
            boolean codeValid = verificationCodeService.verifyCode(targetType, target, "REGISTER", code, clientIp);
            if (!codeValid) {
                throw new VerificationCodeErrorException();
            }

            // 5. 检查手机号/邮箱是否已被注册
            if (phoneNumber != null && !phoneNumber.isBlank()) {
                UserEntity existingByPhone = userMapper.findUserByPhoneNumber(phoneNumber);
                if (existingByPhone != null) {
                    throw new PhoneNumberDuplicatedException();
                }
            }
            if (email != null && !email.isBlank()) {
                UserEntity existingByEmail = userMapper.findUserByEmail(email);
                if (existingByEmail != null) {
                    throw new AccountOrPhoneNumberException("该邮箱已被注册");
                }
            }

            // 6. 创建用户实体
            UserEntity userData = new UserEntity();
            if (phoneNumber != null && !phoneNumber.isBlank()) {
                userData.setPhone_number(phoneNumber);
            }
            if (email != null && !email.isBlank()) {
                userData.setEmail(email);
            }

            userData.setPassword(passwordEncoder.encode(password));
            if (isPreHashedPassword(password)) {
                log.info("用户注册使用 PBKDF2-SHA256 预哈希密码: target={}", target);
            }

            userData.setName(name);

            String elevenDigitsNumber = String.format("%011d", Math.floorMod(SECURE_RANDOM.nextLong(), 100_000_000_000L));
            String account = "pcd_" + elevenDigitsNumber;

            UUID id = UUID.randomUUID();
            userData.setAccount(account);
            userData.setId(id);

            // 7. 插入数据库
            Integer rows = userMapper.insertUser(userData);
            if (rows != 1) {
                throw new InsertException();
            }

            // 8. 创建用户根目录节点
            directoryTreeService.createFolderNode(userData.getId(), null, "#root");

            // 9. 发布用户注册事件（异步发送欢迎邮件/短信）
            UserRegisteredEvent registeredEvent = UserRegisteredEvent.builder()
                    .eventId("user-registered:" + userData.getId() + ":" + System.currentTimeMillis())
                    .userId(userData.getId().toString())
                    .userAccount(userData.getAccount())
                    .userName(userData.getName())
                    .email(userData.getEmail())
                    .phone(userData.getPhone_number())
                    .registeredAt(LocalDateTime.now())
                    .build();
            userEventPublisher.publishUserRegistered(registeredEvent);

            // 10. 注册成功，清除该 IP 的验证码失败计数
            verificationCodeService.clearCodeAttempts(targetType, target, "REGISTER", clientIp);

            log.info("用户注册成功并发布事件: userId={}, account={}, targetType={}, target={}",
                     userData.getId(), userData.getAccount(), targetType, target);

            return userData.getAccount();

        } catch (VerificationCodeErrorException e) {
            // 验证码错误，记录失败次数
            verificationCodeService.recordCodeFailure(targetType, target, "REGISTER",clientIp);
            throw e;
        }
    }

    @Cacheable(cacheNames = "rootFolderNode", key = "#user_id")
    @Override
    public FolderNodeEntity findRootFolderNodeByUserId(UUID user_id) {
        // 检查用户是否存在
        UserEntity userData = userMapper.findUserById(user_id);
        if(userData == null) {
            throw new UserNotFoundException();
        }
        // 查询用户根目录节点
        FolderNodeEntity rootFolderNode = folderNodeMapper.findRootFolderNodeByUserId(user_id);
        if(rootFolderNode == null) {
            throw new NodeNotExistException("根目录节点不存在");
        }
        log.info("已经从数据库中获取用户根目录节点（没有缓存，从数据库中查询）：", rootFolderNode);
        return rootFolderNode;
    }

    @Override
    public UserEntity findUserInfoByUserId(UUID user_id) {
        return userMapper.findUserById(user_id);
    }

    @Override
    public void updateUserPassword(UUID user_id, String user_password, String new_password) {
        // 检查用户是否存在
        UserEntity userData = userMapper.findUserById(user_id);
        if(userData == null) {
            throw new UserNotFoundException();
        }
        // 检查密码匹配情况
        if(!passwordMatches(user_password, userData.getPassword())) {
            throw new PasswordNotMatchException();
        }
        // 更新密码（BCrypt 二次哈希存储）
        Integer rows = userMapper.updateUserPassword(user_id, passwordEncoder.encode(new_password));
        if(rows != 1) {
            throw new UpdateException("更新密码失败");
        }
    }
    @Override
    public void uploadUserAvator(UUID user_id, MultipartFile avator_file) {
        // 检查用户是否存在
        UserEntity userData = userMapper.findUserById(user_id);
        if(userData == null) {
            throw new UserNotFoundException();
        }
        // 上传用户头像
        // 调用持久层函数更新数据
        //
    }

    @Override
    public void updateUserInfo(UUID user_id, String new_email, String new_phone_number, String new_name) {
        // 检查用户是否存在
        UserEntity userData = userMapper.findUserById(user_id);
        if(userData == null) {
            throw new UserNotFoundException();
        }
        userData.setEmail(new_email);
        userData.setPhone_number(new_phone_number);
        userData.setName(new_name);
        // 更新用户信息
        Integer rows =  userMapper.updateUserEntity(userData);
        if(rows != 1) {
            throw new UpdateException("更新用户信息失败");
        }
    }
    @Override
    public void deleteUserByUserId(UUID user_id) {
        // 检查用户是否存在
        UserEntity userData = userMapper.findUserById(user_id);
        if(userData == null) {
            throw new UserNotFoundException();
        }
        // 删除用户
        Integer rows =  userMapper.deleteUserById(user_id);
        if(rows != 1) {
            throw new DeleteException("删除用户失败");
        }
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if(storedPassword == null) {
            return false;
        }
        if(isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    private boolean isBcryptHash(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }
}