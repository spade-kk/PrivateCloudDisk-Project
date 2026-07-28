package org.project.control;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.project.control.result.JsonResult;
import org.project.model.entity.UserEntity;
import org.project.model.dto.ChangeUserPasswordRequest;
import org.project.model.dto.LoginRequest;
import org.project.model.dto.RegisterUserRequest;
import org.project.model.dto.UpdateUserInfoRequest;
import org.project.model.vo.LoginDeviceVO;
import org.project.model.vo.UserProfileVO;
import org.project.model.vo.VoMapper;
import org.project.service.UserService;
import org.project.util.ClientIpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 处理用户模块的请求。
 * <p>
 * 安全机制说明：
 * <ul>
 *   <li>设备指纹验证、限流、客户端身份识别全部由 <b>Gateway 网关层</b> 统一处理</li>
 *   <li>网关验证通过后，将可信设备信息通过请求头注入下游服务</li>
 *   <li>业务服务通过 X-User-Id、X-Device-Fingerprint 等头获取已验证信息</li>
 *   <li>业务服务仅做业务层限流（登录失败次数、验证码失败次数等）</li>
 * </ul>
 *
 * <p><b>注册接口人机验证说明：</b>
 * <ul>
 *   <li>注册接口本身<b>不需要</b>人机验证码</li>
 *   <li>人机验证在获取验证码时（/business/verification/send）已通过</li>
 *   <li>验证码本身就是第二因子，足以防止自动化攻击</li>
 *   <li>注册接口通过"验证码校验 + 防爆破（IP 级别失败次数限制）"来保护</li>
 * </ul>
 */
@RestController
@RequestMapping("/business/users")
public class UserController extends BaseController {
    @Autowired
    UserService userService;

    /**
     * 处理用户登录的请求。
     * <p>接口层只负责：提取客户端 IP → 调用业务层 → 返回 JsonResult。
     * 人机验证、防滥用检查、账号认证、JWT 生成、成功/失败记录 —— 全部由 {@link UserService#login} 处理。
     */
    @PostMapping("/login")
    public JsonResult<String> login(@Valid @RequestBody LoginRequest loginRequest,
                                     HttpServletRequest request) {
        String clientIp = ClientIpUtil.resolveClientIp(request);
        String token = userService.login(
                loginRequest.getAccount(),
                loginRequest.getPhone_number(),
                loginRequest.getEmail(),
                loginRequest.getPassword(),
                loginRequest.getCaptcha_token(),
                loginRequest.getCaptcha_action(),
                clientIp);
        return new JsonResult<>(OK, token);
    }

    /**
     * 处理用户注册的请求。
     * <p>接口层只负责：提取客户端 IP → 调用业务层 → 返回 JsonResult。
     * 防滥用检查、验证码防爆破、验证码校验、创建用户、清除/记录尝试计数 —— 全部由 {@link UserService#register} 处理。
     */
    @PostMapping("/")
    public JsonResult<String> register(@Valid @RequestBody RegisterUserRequest registerUserRequest,
                                          HttpServletRequest request) {
        String clientIp = ClientIpUtil.resolveClientIp(request);
        String account = userService.register(
                registerUserRequest.getPhone_number(),
                registerUserRequest.getEmail(),
                registerUserRequest.getPassword(),
                registerUserRequest.getCode(),
                registerUserRequest.getName(),
                clientIp);
        return new JsonResult<>(OK, account);
    }

    /**
     * 用户注销
     * @param user_id
     * @return
     */
    @DeleteMapping("/me")
    public JsonResult<Void> deleteUserByUserId(@RequestHeader("X-User-Id") String user_id) {
        userService.deleteUserByUserId(UUID.fromString(user_id));
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
                                                     @Valid @RequestBody UpdateUserInfoRequest updateUserInfoRequest ) {
        userService.updateUserInfo(
                UUID.fromString(user_id),
                updateUserInfoRequest.getNew_email(),
                updateUserInfoRequest.getNew_phone_number(),
                updateUserInfoRequest.getNew_name()
        );
        return new JsonResult<>(OK);
    }

    /**
     * 查询登陆用户信息
     * @param user_id
     * @return JsonResult data UserProfileVO 用户数据
     */
    @GetMapping("/me")
    public JsonResult<UserProfileVO> queryUserInfoByUserId(@RequestHeader("X-User-Id") String user_id) {
        UserEntity userData = userService.findUserInfoByUserId(UUID.fromString(user_id));
        return new JsonResult<>(OK, VoMapper.toUserProfileVO(userData));
    }

    /**
     * 修改用户密码
     * @param user_id 用户Uid
     * @param changeUserPasswordRequest 修改用户密码请求体参数Json对象
     * @return JsonResult data Void
     */
    @PostMapping("/me/password")
    public JsonResult<Void> updateUserPassword(@RequestHeader("X-User-Id") String user_id,
                                               @Valid @RequestBody ChangeUserPasswordRequest changeUserPasswordRequest ) {
        userService.updateUserPassword( UUID.fromString(user_id),
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
        userService.uploadUserAvator(UUID.fromString(user_id), avator_file);
        return new JsonResult<>(OK);
    }

    /**
     * 查询登陆用户设备列表
     * @param user_id
     * @return
     */
    @GetMapping("/me/online-devices")
    public JsonResult<List<LoginDeviceVO>> queryOnlineDevicesByUserId(@RequestHeader("X-User-Id") String user_id) {
        return new JsonResult<>(OK, List.of());
    }
}
