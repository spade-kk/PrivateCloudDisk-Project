package org.project.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.UserEntity;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.UserMapper;
import org.project.service.DirectoryTreeService;
import org.project.service.UserService;
import org.project.service.ex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FolderNodeMapper folderNodeMapper;
    @Autowired
    private DirectoryTreeService directoryTreeService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        if(!isBcryptHash(result.getPassword())) {
            String hashedPassword = passwordEncoder.encode(password);
            userMapper.updateUserPassword(result.getId(), hashedPassword);
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
        userData.setPassword(passwordEncoder.encode(password));
        userData.setName(name);

        String elevenDigitsNumber = String.format("%011d", Math.floorMod(SECURE_RANDOM.nextLong(), 100_000_000_000L));
        String account = "pcd_" + elevenDigitsNumber;

        String id = UUID.randomUUID().toString();
        userData.setAccount(account);
        userData.setId(id);

        //调用持久层函数插入数据
        Integer rows = userMapper.insertUser(userData);
        if(rows != 1) {
            throw new InsertException();
        }

        // 创建用户根目录节点
        directoryTreeService.createFolderNode(userData.getId(), null, "#root");

        return userData.getAccount();
    }


    @Cacheable(cacheNames = "rootFolderNode", key = "#user_id")
    @Override
    public FolderNodeEntity findRootFolderNodeByUserId(String user_id) {
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
    public UserEntity findUserInfoByUserId(String user_id) {
        return userMapper.findUserById(user_id);
    }

    @Override
    public void updateUserPassword(String user_id, String user_password, String new_password) {
        // 检查用户是否存在
        UserEntity userData = userMapper.findUserById(user_id);
        if(userData == null) {
            throw new UserNotFoundException();
        }
        // 检查密码匹配情况
        if(!passwordMatches(user_password, userData.getPassword())) {
            throw new PasswordNotMatchException();
        }
        // 更新密码
        // 调用持久层函数更新数据
        Integer rows = userMapper.updateUserPassword(user_id, passwordEncoder.encode(new_password));
        if(rows != 1) {
            throw new UpdateException("更新密码失败");
        }
    }
    @Override
    public void uploadUserAvator(String user_id, MultipartFile avator_file) {
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
    public void updateUserInfo(String user_id, String new_email, String new_phone_number, String new_name) {
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
    public void deleteUserByUserId(String user_id) {
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
