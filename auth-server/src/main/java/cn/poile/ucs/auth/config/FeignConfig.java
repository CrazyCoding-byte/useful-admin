package cn.poile.ucs.auth.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * @className: FeignConfig
 * @author: yzx
 * @date: 2025/8/30 6:04
 * @Version: 1.0
 * @description:
 */
@Configuration
public class FeignConfig {

    @Bean("requestInterceptor")
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // 1. 尝试从当前请求上下文获取属性
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                if (request != null) {
                    String authorization = request.getHeader("Authorization");
                    template.header("Authorization", authorization);
                }
            }
        };
    }
}
