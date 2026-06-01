package org.project.control;

import jakarta.validation.Valid;
import org.project.control.result.JsonResult;
import org.project.data.UserData;
import org.project.model.dto.ChangeUserPasswordRequest;
import org.project.model.dto.LoginRequest;
import org.project.model.dto.registerUserRequest;
import org.project.model.dto.updateUserInfoRequest;
import org.project.service.UserService;
import org.project.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 处理用户模块的请求
 */
@RestController
@RequestMapping("/business/users")
public class UserController extends BaseController {
    @Autowired
    UserService userService;
    @Autowired
    JwtUtil jwtUtil;

    /**
     * 处理用户登录的请求
     * @param loginRequest 登陆请求体参数Json对象
     * @return JsonResult data String 登陆通行凭证令牌 JWT Token
     */
    @PostMapping("/login")
    public JsonResult<String> login( @Valid @RequestBody LoginRequest loginRequest )
    {
        //调用业务层函数
        UserData userData = userService.login(loginRequest.getAccount(), loginRequest.getPhone_number(), loginRequest.getPassword());
        String token = jwtUtil.generateAccessToken(userData.getId());
        //请求登录操作结果成功！ OK
        return new JsonResult<String>(OK, token);
    }

    /**
     * 处理用户注册的请求
     * @param registerUserRequest 注册请求体参数Json对象
     * @return JsonResult data String
     */
    @PostMapping("/")
    public JsonResult<String> register( @Valid @RequestBody registerUserRequest registerUserRequest )
    {
        String account = userService.register(
                registerUserRequest.getPhone_number(),
                registerUserRequest.getPassword(),
                registerUserRequest.getCode(),
                registerUserRequest.getName());

        return new JsonResult<String>(OK, account);
    }

    /**
     * 用户注销
     * @param user_id
     * @return
     */
    @DeleteMapping("/me")
    public JsonResult<Void> deleteUserByUserId(@RequestHeader("X-User-Id") String user_id) {
        userService.deleteUserByUserId(user_id);
        return new JsonResult<>(OK);
    }

    /**
     * 修改用户信息
     * @param user_id 用户Uid
     * @param updateUserInfoRequest 更新用户信息请求体参数Json对象
     * @return JsonResult data Void
     */
    @PatchMapping("/me")
    public JsonResult<Void> updateUserInfoByUserId( @RequestHeader("X-User-Id") String user_id,
                                                     @Valid @RequestBody updateUserInfoRequest updateUserInfoRequest ) {
        userService.updateUserInfo(
                user_id,
                updateUserInfoRequest.getNew_email(),
                updateUserInfoRequest.getNew_phone_number(),
                updateUserInfoRequest.getNew_name()
        );
        return new JsonResult<>(OK);
    }

    /**
     * 查询登陆用户信息
     * @param user_id
     * @return JsonResult data UserData 用户数据
     */
    @GetMapping("/me")
    public JsonResult<UserData> queryUserInfoByUserId(@RequestHeader("X-User-Id") String user_id) {
        UserData userData = userService.findUserInfoByUserId(user_id);
        return new JsonResult<>(OK, userData);
    }

    /**
     * 修改用户密码
     * @param user_id 用户Uid
     * @param changeUserPasswordRequest 修改用户密码请求体参数Json对象
     * @return JsonResult data Void
     */
    @PostMapping("/me/password")
    public JsonResult<Void> updateUserPassword(@RequestHeader("X-User-Id") String user_id,
                                               @RequestBody ChangeUserPasswordRequest changeUserPasswordRequest ) {
        userService.updateUserPassword( user_id,
                changeUserPasswordRequest.getUser_password(),
                changeUserPasswordRequest.getNew_password());
        return new JsonResult<>(OK);
    }
    /**
     * 上传用户头像
     * @param user_id
     * @param avator_file
     * @return JsonResult data Void
     */
    @PutMapping("/me/avatar")
    public JsonResult<Void> uploadAvatarByUserId (@RequestHeader("X-User-Id") String user_id,
                                                  MultipartFile avator_file ) {
        userService.uploadUserAvator(user_id, avator_file);
        return new JsonResult<>(OK);
    }

    /**
     * 查询登陆用户设备列表
     * @param user_id
     * @return
     */
    @GetMapping("/me/online-devices")
    public JsonResult<Void> queryOnlineDevicesByUserId(@RequestHeader("X-User-Id") String user_id) {
        return new JsonResult<>(OK);
    }
}
