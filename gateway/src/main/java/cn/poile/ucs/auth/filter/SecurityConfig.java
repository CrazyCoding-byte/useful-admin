package cn.poile.ucs.auth.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

/**
 * @author VectorX
 * @version 1.0.0
 * @description 网关安全配置类
 * @date 2024/04/24
 */
// 启用基于WebFlux的安全性配置
@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {
    /**
     * 白名单路径（从配置文件加载，与security-whitelist.properties对应）
     */
    @Value("${security.whitelist:/auth/**,/login,/register,/sms/code}")
    private List<String> whitelist;

    /**
     * 安全拦截配置
     *
     * @param http
     * @return {@link SecurityWebFilterChain}
     */
    @Bean
    public SecurityWebFilterChain webFluxSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange()
                // 白名单路径放行
                .pathMatchers(whitelist.toArray(new String[0])).permitAll()
                // 其他所有请求需要认证
                .anyExchange().authenticated()
                .and()
                // 禁用CSRF（小程序为非浏览器客户端，无需CSRF保护）
                .csrf().disable()
                // 配置JWT认证过滤器（关键：验证Token）
                .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * 自定义JWT认证过滤器：从请求头提取Token并验证
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(tokenStore());
    }

    /**
     * 注入TokenStore（基于JWT，与auth-server的JWT配置一致）
     */
    @Autowired
    private TokenStore tokenStore;
}
