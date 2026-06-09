package org.project.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件发送配置属性
 * <p>
 * 配置示例（application.properties）：
 * <pre>
 * app.mail.enabled=true
 * app.mail.from=noreply@yourcompany.com
 * app.mail.from-name=私有云网盘
 * app.mail.welcome-subject=欢迎使用私有云网盘
 * app.mail.verify-subject=您的验证码
 * app.mail.template.welcome-url=https://yourcompany.com/welcome
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {

    /**
     * 是否启用邮件发送（开发环境可设为false，只打印日志）
     */
    private boolean enabled = true;

    /**
     * 发件人邮箱地址
     */
    private String from = "noreply@yourcompany.com";

    /**
     * 发件人显示名称
     */
    private String fromName = "私有云网盘";

    /**
     * 欢迎邮件主题
     */
    private String welcomeSubject = "欢迎使用私有云网盘";

    /**
     * 验证码邮件主题
     */
    private String verifySubject = "您的验证码";

    /**
     * 邮件编码
     */
    private String encoding = "UTF-8";

    /**
     * 登录页面URL（欢迎邮件中的跳转链接）
     */
    private String loginUrl = "http://localhost:5173/login";
}
