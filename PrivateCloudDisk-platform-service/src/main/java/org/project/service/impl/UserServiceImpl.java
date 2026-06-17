package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.event.UserRegisteredEvent;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.UserEntity;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.UserMapper;
import org.project.service.DirectoryTreeService;
import org.project.service.UserEventPublisher;
import org.project.service.UserService;
import org.project.service.ex.*;
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
    public UserEntity login(String account, String phone_number, String password) {
        if(account == null && phone_number == null){
            throw new AccountOrPhoneNumberException();
        }
        //调用持久层函数根据用户名获取数据
        UserEntity result;
        if(account != null){
            result = userMapper.findUserByAccount(account);
        }
        else {
            result = userMapper.findUserByPhoneNumber(phone_number);
        }

        if(result == null){
            throw new UserNotFoundException();
        }

        if(account != null && phone_number != null &&
                (!result.getAccount().equals(account) ||
                        !result.getPhone_number().equals(phone_number)))
        {
            throw new AccountOrPhoneNumberException("用户账号手机号不匹配");
        }

        //检查密码匹配情况
        if(!passwordMatches(password, result.getPassword())) {
            throw new PasswordNotMatchException();
        }
        // 密码自动迁移：如果旧密码不是 BCrypt 格式，升级为 BCrypt
        if(!isBcryptHash(result.getPassword())) {
            String hashedPassword = passwordEncoder.encode(password);
            userMapper.updateUserPassword(result.getId(), hashedPassword);
            log.info("用户密码已从明文迁移至 BCrypt: userId={}", result.getId());
        }

        return result;
    }

    @Override
    public String register(String phone_number, String password, String code, String name) {
        if(!code.equals("SXDD998")) {
            throw new VerificationCodeErrorException();
        }

        UserEntity result = userMapper.findUserByPhoneNumber(phone_number);
        if(result != null) {
            throw new PhoneNumberDuplicatedException();
        }
        //创建UserEntity把参数添加进去
        UserEntity userData = new UserEntity();
        userData.setPhone_number(phone_number);

        // 密码存储：BCrypt 二次哈希
        // 如果前端发送的是预哈希密码（64 位十六进制），则存储 BCrypt(PBKDF2-SHA256(raw))
        // 如果前端发送的是原始密码（非 Web 客户端），则存储 BCrypt(raw)
        userData.setPassword(passwordEncoder.encode(password));
        if (isPreHashedPassword(password)) {
            log.info("用户注册使用 PBKDF2-SHA256 预哈希密码: phone={}", phone_number);
        }

        userData.setName(name);

        String elevenDigitsNumber = String.format("%011d", Math.floorMod(SECURE_RANDOM.nextLong(), 100_000_000_000L));
        String account = "pcd_" + elevenDigitsNumber;

        UUID id = UUID.randomUUID();
        userData.setAccount(account);
        userData.setId(id);

        //调用持久层函数插入数据
        Integer rows = userMapper.insertUser(userData);
        if(rows != 1) {
            throw new InsertException();
        }

        // 创建用户根目录节点
        directoryTreeService.createFolderNode(userData.getId(), null, "#root");

        // 发布用户注册事件（异步发送欢迎邮件/短信）
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

        log.info("用户注册成功并发布事件: userId={}, account={}", userData.getId(), userData.getAccount());

        return userData.getAccount();
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