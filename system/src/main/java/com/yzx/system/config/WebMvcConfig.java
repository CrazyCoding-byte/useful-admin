package com.yzx.system.config;

import com.yzx.system.interceptor.TenantOAuth2Interceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 注册租户 OAuth2 拦截器
 * 
 * @author ruoyi
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private TenantOAuth2Interceptor tenantOAuth2Interceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册租户 OAuth2 拦截器，拦截所有请求
        registry.addInterceptor(tenantOAuth2Interceptor)
                .addPathPatterns("/**")
                // 排除静态资源和公开接口
                .excludePathPatterns(
                        "/static/**",
                        "/public/**",
                        "/error",
                        "/actuator/**"
                );
    }
}
