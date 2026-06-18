package org.project.model.vo;

import lombok.Data;

/**
 * 验证码发送响应 VO。
 *
 * <p>包含 resend_token（不透明 UUID），前端后续重新发送时需携带此 token。
 */
@Data
public class VerificationSendVO {

    /**
     * 重新发送令牌（不透明 UUID），有效期 10 分钟
     */
    private String resendToken;

    /**
     * 令牌过期时间（秒）
     */
    private long expiresIn;

    /**
     * 剩余可重新发送次数
     */
    private int remainingResends;

    public VerificationSendVO(String resendToken, long expiresIn, int remainingResends) {
        this.resendToken = resendToken;
        this.expiresIn = expiresIn;
        this.remainingResends = remainingResends;
    }
}