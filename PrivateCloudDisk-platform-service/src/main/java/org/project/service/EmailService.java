package org.project.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.properties.AppMailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * 邮件发送服务
 * <p>封装JavaMailSender，提供发送欢迎邮件、验证码邮件的便捷方法。
 * <p>支持HTML富文本邮件，使用Thymeleaf模板引擎渲染企业级邮件样式。
 *
 * <p>设计说明：
 * <ul>
 *   <li>{@code enabled=false}时仅打印日志，不实际发送，方便开发调试</li>
 *   <li>所有异常向上抛出，由调用方（消费者）统一处理状态记录和消息确认</li>
 *   <li>HTML邮件使用Thymeleaf模板，支持动态内容渲染</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final AppMailProperties mailProperties;
    private final TemplateEngine templateEngine;

    /**
     * 发送欢迎邮件（HTML富文本）
     *
     * @param toEmail  收件人邮箱
     * @param userName 用户昵称
     */
    public void sendWelcomeEmail(String toEmail, String userName) {
        String subject = mailProperties.getWelcomeSubject();

        // 构建Thymeleaf上下文
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("loginUrl", mailProperties.getLoginUrl());

        // 渲染HTML模板
        String htmlContent = templateEngine.process("mail/welcome-email", context);

        sendHtmlEmail(toEmail, subject, htmlContent, "welcome");
    }

    /**
     * 发送验证码邮件（HTML富文本）
     *
     * @param toEmail            收件人邮箱
     * @param verificationCode   验证码
     * @param expireSeconds      过期时间（秒）
     * @param purpose            用途（REGISTER / BIND / RESET）
     */
    public void sendVerificationEmail(String toEmail, String verificationCode,
                                      int expireSeconds, String purpose) {
        String subject = mailProperties.getVerifySubject();
        int minutes = expireSeconds / 60;

        // 构建Thymeleaf上下文
        Context context = new Context();
        context.setVariable("verificationCode", verificationCode);
        context.setVariable("expireMinutes", minutes);
        context.setVariable("purposeText", formatPurpose(purpose));
        context.setVariable("purpose", purpose);

        // 渲染HTML模板
        String htmlContent = templateEngine.process("mail/verification-email", context);

        sendHtmlEmail(toEmail, subject, htmlContent, "verification");
    }

    /**
     * 发送纯文本邮件（兼容旧版本或简单场景）
     *
     * @param toEmail   收件人
     * @param subject   主题
     * @param content   纯文本内容
     * @param typeLabel 类型标签，用于日志区分
     * @throws MailException 发送失败时抛出
     */
    public void sendTextEmail(String toEmail, String subject, String content, String typeLabel) {
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

        log.info("[邮件服务-{}] 开始发送纯文本邮件. to={}", typeLabel, toEmail);
        try {
            javaMailSender.send(message);
            log.info("[邮件服务-{}] 纯文本邮件发送成功. to={}", typeLabel, toEmail);
        } catch (MailException e) {
            log.error("[邮件服务-{}] 纯文本邮件发送失败. to={}, error={}", typeLabel, toEmail, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 核心HTML邮件发送方法
     *
     * @param toEmail     收件人
     * @param subject     主题
     * @param htmlContent HTML内容
     * @param typeLabel   类型标签，用于日志区分
     * @throws MailException 发送失败时抛出
     */
    private void sendHtmlEmail(String toEmail, String subject, String htmlContent, String typeLabel) {
        if (!mailProperties.isEnabled()) {
            log.info("[邮件服务-{}] mail.enabled=false，开发模式仅打印日志. to={}, subject={}",
                    typeLabel, toEmail, subject);
            log.info("[邮件服务-{}] HTML邮件内容预览 (前500字符):\n{}",
                    typeLabel, htmlContent.substring(0, Math.min(500, htmlContent.length())));
            return;
        }

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(mailProperties.getFrom(), mailProperties.getFromName());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML格式

            log.info("[邮件服务-{}] 开始发送HTML邮件. to={}", typeLabel, toEmail);
            javaMailSender.send(mimeMessage);
            log.info("[邮件服务-{}] HTML邮件发送成功. to={}", typeLabel, toEmail);

        } catch (UnsupportedEncodingException e) {
            log.error("[邮件服务-{}] 邮件编码不支持. to={}, error={}", typeLabel, toEmail, e.getMessage(), e);
            throw new RuntimeException("邮件编码不支持: " + e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("[邮件服务-{}] HTML邮件构建失败. to={}, error={}", typeLabel, toEmail, e.getMessage(), e);
            throw new RuntimeException("邮件构建失败: " + e.getMessage(), e);
        } catch (MailException e) {
            log.error("[邮件服务-{}] HTML邮件发送失败. to={}, error={}", typeLabel, toEmail, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 格式化用途文本
     */
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
