package org.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Thymeleaf 邮件模板配置
 * <p>
 * 配置独立的 TemplateEngine 用于邮件模板渲染，
 * 与 Spring Boot 自动配置的 Web 模板引擎分离。
 */
@Configuration
public class ThymeleafMailConfig {

    /**
     * 邮件模板引擎
     * <p>
     * 使用 ClassLoaderTemplateResolver 从 classpath 加载模板文件，
     * 模板路径：templates/mail/
     */
    @Bean(name = "mailTemplateEngine")
    public TemplateEngine mailTemplateEngine() {
        TemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(mailTemplateResolver());
        return templateEngine;
    }

    /**
     * 邮件模板解析器
     */
    private ITemplateResolver mailTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false); // 开发环境关闭缓存，生产环境可开启
        resolver.setCheckExistence(true);
        return resolver;
    }
}
