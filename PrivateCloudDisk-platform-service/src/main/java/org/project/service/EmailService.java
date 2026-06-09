package org.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.properties.AppMailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务
 * <p>封装JavaMailSender，提供发送欢迎邮件、验证码邮件的便捷方法。
 *
 * <p>设计说明：
 * <ul>
 *   <li>{@code enabled=false}时仅打印日志，不实际发送，方便开发调试</li>
 *   <li>所有异常向上抛出，由调用方（消费者）统一处理状态记录和消息确认</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final AppMailProperties mailProperties;

    /**
     * 发送欢迎邮件
     *
     * @param toEmail  收件人邮箱
     * @param userName 用户昵称
     */
    public void sendWelcomeEmail(String toEmail, String userName) {
        String subject = mailProperties.getWelcomeSubject();
        String content = buildWelcomeContent(userName);
        sendEmail(toEmail, subject, content, "welcome");
    }

    /**
     * 发送验证码邮件
     *
     * @param toEmail            收件人邮箱
     * @param verificationCode   验证码
     * @param expireSeconds      过期时间（秒）
     * @param purpose            用途（REGISTER / BIND / RESET）
     */
    public void sendVerificationEmail(String toEmail, String verificationCode,
                                      int expireSeconds, String purpose) {
        String subject = mailProperties.getVerifySubject();
        String content = buildVerificationContent(verificationCode, expireSeconds, purpose);
        sendEmail(toEmail, subject, content, "verification");
    }

    /**
     * 核心邮件发送方法
     *
     * @param toEmail   收件人
     * @param subject   主题
     * @param content   内容
     * @param typeLabel 类型标签，用于日志区分
     * @throws MailException 发送失败时抛出
     */
    private void sendEmail(String toEmail, String subject, String content, String typeLabel) {
        if (!mailProperties.isEnabled()) {
            log.info("[邮件服务-{}] mail.enabled=false，开发模式仅打印日志. to={}, subject={}",
                    typeLabel, toEmail, subject);
            log.info("[邮件服务-{}] 邮件内容预览:\n{}", typeLabel, content);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFrom());
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(content);

        log.info("[邮件服务-{}] 开始发送. to={}", typeLabel, toEmail);
        try {
            javaMailSender.send(message);
            log.info("[邮件服务-{}] 发送成功. to={}", typeLabel, toEmail);
        } catch (MailException e) {
            log.error("[邮件服务-{}] 发送失败. to={}, error={}", typeLabel, toEmail, e.getMessage(), e);
            throw e; // 向上抛出，让消费者决定如何处理（进入DLQ）
        }
    }

    // ============ 邮件内容模板方法（简单文本，未来可替换为Thymeleaf/Freemarker）============

    private String buildWelcomeContent(String userName) {
        StringBuilder sb = new StringBuilder();
        sb.append("尊敬的 ").append(userName).append("，您好！\n\n");
        sb.append("欢迎加入私有云网盘！\n\n");
        sb.append("您的账户已成功创建，现在可以：\n");
        sb.append("  · 上传和分享您的文件\n");
        sb.append("  · 创建文件夹组织内容\n");
        sb.append("  · 设置文件分享权限\n\n");
        sb.append("如有疑问，欢迎联系管理员。\n\n");
        sb.append("-- 私有云网盘 团队");
        return sb.toString();
    }

    private String buildVerificationContent(String code, int expireSeconds, String purpose) {
        int minutes = expireSeconds / 60;
        StringBuilder sb = new StringBuilder();
        sb.append("您的验证码：").append(code).append("\n\n");
        sb.append("此验证码用于：").append(formatPurpose(purpose)).append("\n");
        sb.append("有效期：").append(minutes).append(" 分钟\n\n");
        sb.append("如非本人操作，请忽略此邮件。\n\n");
        sb.append("-- 私有云网盘");
        return sb.toString();
    }

    private String formatPurpose(String purpose) {
        if (purpose == null) return "身份验证";
        switch (purpose) {
            case "REGISTER": return "注册账户";
            case "BIND": return "绑定新邮箱";
            case "RESET": return "重置密码";
            default: return purpose;
        }
    }
}
