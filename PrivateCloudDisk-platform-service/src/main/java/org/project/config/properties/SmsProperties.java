package org.project.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信发送配置属性
 * <p>
 * 配置示例（application.properties）：
 * <pre>
 * app.sms.enabled=false
 * app.sms.base-url=https://sms-provider.example.com/api
 * app.sms.api-key=your_sms_api_key
 * app.sms.sign-name=私有云
 * app.sms.template.welcome=SMS_WELCOME_TEMPLATE_ID
 * app.sms.template.verify=SMS_VERIFY_TEMPLATE_ID
 * app.sms.expire-seconds=300
 * </pre>
 * 
 * <p>
 * 设计说明：使用通用HTTP调用方式，便于后续替换为阿里云、腾讯云、华为云等不同供应商。
 * 开发环境可将enabled设为false，只打印日志不真正发送短信。
 */
@Data
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    /**
     * 是否启用短信发送（开发环境可设为false）
     */
    private boolean enabled = false;

    /**
     * 短信供应商API基础URL
     */
    private String baseUrl = "https://sms-provider.example.com/api";

    /**
     * API访问密钥
     */
    private String apiKey = "";

    /**
     * 短信签名
     */
    private String signName = "私有云";

    /**
     * 验证码有效期（秒）
     */
    private Integer expireSeconds = 300;

    /**
     * 短信模板配置
     */
    private final Template template = new Template();

    @Data
    public static class Template {
        /**
         * 欢迎短信模板ID
         */
        private String welcome = "SMS_WELCOME_TEMPLATE_ID";

        /**
         * 验证码模板ID
         */
        private String verify = "SMS_VERIFY_TEMPLATE_ID";
    }
}
