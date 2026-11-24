package com.secondserve.config;

import com.secondserve.service.AppContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the authentication/role interceptor. It is deliberately a SEPARATE class from AppConfig: AppConfig creates the
 * AppContext bean, so injecting AppContext into AppConfig itself would be a circular dependency.
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {
    private final AppContext ctx;

    public WebSecurityConfig(AppContext ctx) { this.ctx = ctx; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(ctx)).addPathPatterns("/api/**");
    }
}
