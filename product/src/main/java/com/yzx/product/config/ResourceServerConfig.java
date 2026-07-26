package com.yzx.product.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * 商品服务资源服务器配置
 * 负责：校验 JWT Token、解析用户信息、未登录时返回 JSON 401（不再 302 跳登录页）
 */
@Configuration
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Log4j2
public class ResourceServerConfig extends ResourceServerConfigurerAdapter {

    private static final String RESOURCE_ID = "product-server";
    private static final String PUBLIC_KEY = "publickey.txt";

    @Autowired
    private CustomUserAuthenticationConverter customUserAuthenticationConverter;
    @Autowired
    private CustomAuthExceptionEntryPoint customAuthExceptionEntryPoint;

    // 1. 初始化JwtAccessTokenConverter（关联解密转换器）
    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        DefaultAccessTokenConverter accessTokenConverter = new DefaultAccessTokenConverter();

        // 关键：注入带AES解密的用户转换器
        accessTokenConverter.setUserTokenConverter(customUserAuthenticationConverter);
        converter.setAccessTokenConverter(accessTokenConverter);

        // 加载公钥（验证JWT签名）
        String pubKey = getPubKey();
        if (pubKey == null || pubKey.isEmpty()) {
            throw new RuntimeException("公钥加载失败，请检查classpath下的publickey.txt");
        }
        converter.setVerifierKey(pubKey);
        return converter;
    }

    // 2. TokenStore
    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(jwtAccessTokenConverter());
    }

    @Autowired
    private TokenStore tokenStore;

    // 3. HttpSecurity配置：所有请求都需要登录，OPTIONS 预检放行
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests()
                .antMatchers("/actuator/**").permitAll()
                .antMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                .anyRequest().authenticated();
    }

    // 4. ResourceServerSecurity配置
    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
        resources
                .authenticationEntryPoint(customAuthExceptionEntryPoint)
                .resourceId(RESOURCE_ID)
                .stateless(true)
                .tokenServices(tokenServices());
    }

    // 5. TokenServices（确保Token解析时调用解密逻辑）
    @Bean
    public DefaultTokenServices tokenServices() {
        DefaultTokenServices tokenServices = new DefaultTokenServices();
        tokenServices.setTokenStore(tokenStore);
        tokenServices.setSupportRefreshToken(true);
        tokenServices.setTokenEnhancer(jwtAccessTokenConverter());
        return tokenServices;
    }

    // 加载公钥
    private String getPubKey() {
        ClassPathResource resource = new ClassPathResource(PUBLIC_KEY);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String pubKey = br.lines().collect(Collectors.joining("\n"));
            log.info("公钥加载成功，长度：{}", pubKey.length());
            return pubKey;
        } catch (IOException ioe) {
            log.error("加载公钥失败", ioe);
            return null;
        }
    }
}
