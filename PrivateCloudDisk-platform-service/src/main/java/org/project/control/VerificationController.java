package org.project.control;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.control.result.JsonResult;
import org.project.model.dto.VerificationSendRequest;
import org.project.model.vo.VerificationSendVO;
import org.project.service.VerificationCodeService;
import org.project.util.ClientIpUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码控制器 —— 极薄的接口层，只负责参数提取和路由。
 *
 * <p><b>分层原则</b>：
 * <ul>
 *   <li>控制器只做：提取参数、调用业务层、返回 JsonResult</li>
 *   <li>人机验证、邮箱过滤、验证码生成、邮件/短信发送、token 管理 —— 全部在 {@link VerificationCodeService} 中</li>
 *   <li>业务异常由业务层抛出，由 {@link BaseController} 全局异常处理器统一处理</li>
 * </ul>
 *
 * <h3>接口设计</h3>
 *
 * <p><b>1. 首次发送（需人机验证）</b>
 * <pre>
 * POST /business/verification/send
 * Body: { "email"?, "phone"?, "purpose": "REGISTER", "captchaToken": "..." }
 * Response: { "resendToken": "uuid...", "expiresIn": 600, "remainingResends": 8 }
 * </pre>
 *
 * <p><b>2. 重新发送（需 resend token，免人机验证）</b>
 * <pre>
 * POST /business/verification/resend
 * Header: X-Resend-Token: {uuid}
 * Body: { "email"?, "phone"?, "purpose": "REGISTER" }
 * Response: { "resendToken": "same-uuid...", "expiresIn": ..., "remainingResends": N-1 }
 * </pre>
 *
 * <p><b>关键设计</b>：resend 接口<b>不重新颁发 token</b>，返回同一个 token + 更新后的剩余次数。
 * 重新颁发 token 会重置计数器，导致 8 次限制形同虚设。
 */
@Slf4j
@RestController
@RequestMapping("/business/verification")
@RequiredArgsConstructor
public class VerificationController extends BaseController {

    private final VerificationCodeService verificationCodeService;

    /**
     * 首次发送验证码（需人机验证码）。
     * <p>所有业务逻辑（人机验证、过滤、生成、发送、token 创建）均由 VerificationCodeService 处理。
     */
    @PostMapping("/send")
    public JsonResult<VerificationSendVO> sendVerificationCode(
            @Valid @RequestBody VerificationSendRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = ClientIpUtil.resolveClientIp(httpRequest);

        String targetType;
        String target;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            targetType = "email";
            target = request.getEmail().trim().toLowerCase();
        } else {
            targetType = "phone";
            target = request.getPhone().trim();
        }

        VerificationSendVO response = verificationCodeService.sendCode(
                targetType, target, request.getPurpose(),
                request.getCaptchaToken(), request.getCaptchaAction(), clientIp);

        return new JsonResult<>(OK, response);
    }

    /**
     * 重新发送验证码（无需人机验证码，需携带有效的 resend token）。
     * <p>不重新颁发 token，返回同一个 token + 更新后的剩余次数。
     */
    @PostMapping("/resend")
    public JsonResult<VerificationSendVO> resendVerificationCode(
            @Valid @RequestBody VerificationSendRequest request,
            @RequestHeader(value = "X-Resend-Token", required = true) String resendToken,
            HttpServletRequest httpRequest) {

        String clientIp = ClientIpUtil.resolveClientIp(httpRequest);

        String targetType;
        String target;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            targetType = "email";
            target = request.getEmail().trim().toLowerCase();
        } else {
            targetType = "phone";
            target = request.getPhone().trim();
        }

        VerificationSendVO response = verificationCodeService.resendCode(
                targetType, target, request.getPurpose(), resendToken, clientIp);

        return new JsonResult<>(OK, response);
    }
}