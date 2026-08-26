package com.saas.billing.common.config;

import com.saas.billing.usage.UsageInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UsageInterceptor usageInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(usageInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/webhooks/**",
                        "/actuator/**",
                        "/error"
                );
    }
}