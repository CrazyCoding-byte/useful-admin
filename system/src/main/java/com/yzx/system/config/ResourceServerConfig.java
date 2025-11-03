package com.yzx.system.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.UserAuthenticationConverter;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Log4j2
public class ResourceServerConfig extends ResourceServerConfigurerAdapter {

    private static final String RESOURCE_ID = "system-server";
    private static final String PUBLIC_KEY = "publickey.txt";

    @Autowired
    private TokenStore tokenStore; // 注入TokenStore

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.requestMatchers()
                .and().authorizeRequests().antMatchers("/system/user/register").permitAll().anyRequest().authenticated();
    }

    @Bean
    public TokenStore tokenStore(JwtAccessTokenConverter jwtAccessTokenConverter) {
        return new JwtTokenStore(jwtAccessTokenConverter);
    }

    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();

        // 使用自定义的用户信息转换器
        DefaultAccessTokenConverter accessTokenConverter = new DefaultAccessTokenConverter();
        accessTokenConverter.setUserTokenConverter(new CustomUserAuthenticationConverter());

        converter.setAccessTokenConverter(accessTokenConverter);
        converter.setVerifierKey(getPubKey());
        return converter;
    }

    private String getPubKey() {
        ClassPathResource resource = new ClassPathResource(PUBLIC_KEY);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            return br.lines().collect(Collectors.joining("\n"));
        } catch (IOException ioe) {
            log.error("加载公钥失败", ioe);
            return null;
        }
    }

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
        resources
                .resourceId(RESOURCE_ID)
                .stateless(true)
                .tokenServices(tokenServices()); // 使用自定义TokenServices
    }

    @Bean
    public CustomTokenServices tokenServices() {
        CustomTokenServices tokenServices = new CustomTokenServices();
        tokenServices.setTokenStore(tokenStore); // 关键：设置TokenStore
        tokenServices.setSupportRefreshToken(true);
        return tokenServices;
    }

    //自定义用户信息转换器 获取的一般是用户名 所以当我们需要用户信息的principal需要转换
    public static class CustomUserAuthenticationConverter implements UserAuthenticationConverter {

        @Override
        public Map<String, ?> convertUserAuthentication(Authentication authentication) {
            throw new UnsupportedOperationException("Not supported in resource server");
        }

        @Override
        public Authentication extractAuthentication(Map<String, ?> map) {
            //如果有user_details 就吧user_details裝成princpal
            if (map.containsKey("user_details")) {
                Map<String, Object> userDetails = (Map<String, Object>) map.get("user_details");
                Collection<GrantedAuthority> authorities = extractAuthorities(userDetails);
                return new UsernamePasswordAuthenticationToken(
                        userDetails,
                        "N/A",
                        authorities
                );
            }
            //否则就按照默认的转换方式
            // 修改后的回退逻辑：处理authorities的两种类型
            Object principal = map.get(USERNAME);
            Collection<? extends GrantedAuthority> authorities = Collections.emptyList();
            Object authoritiesObj = map.get(AUTHORITIES);

            if (authoritiesObj instanceof String) {
                // 处理逗号分隔的字符串
                authorities = AuthorityUtils.commaSeparatedStringToAuthorityList((String) authoritiesObj);
            } else if (authoritiesObj instanceof Collection) {
                // 处理权限列表
                authorities = ((Collection<?>) authoritiesObj).stream()
                        .map(Object::toString)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }

            return new UsernamePasswordAuthenticationToken(
                    principal,
                    "N/A",
                    authorities
            );
        }

        private Collection<GrantedAuthority> extractAuthorities(Map<String, Object> userDetails) {
            if (userDetails.containsKey("authorities")) {
                List<Map<String, String>> authoritiesList =
                        (List<Map<String, String>>) userDetails.get("authorities");
                return authoritiesList.stream()
                        .map(authMap -> new SimpleGrantedAuthority(authMap.get("authority")))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    //这个地方先调用解系jwt 放入princippal
    public static class CustomTokenServices extends DefaultTokenServices {
        @Override
        public OAuth2Authentication loadAuthentication(String accessTokenValue) {
            OAuth2Authentication authentication = super.loadAuthentication(accessTokenValue);
            if (authentication != null) {
                Object principal = authentication.getPrincipal();

                // 如果Principal是Map且包含user_details，直接使用user_details作为Principal
                if (principal instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> principalMap = (Map<String, Object>) principal;

                    if (principalMap.containsKey("user_details")) {
                        Object userDetails = principalMap.get("user_details");
                        return new OAuth2Authentication(
                                authentication.getOAuth2Request(),
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        authentication.getCredentials(),
                                        authentication.getAuthorities()
                                )
                        );
                    }
                }
            }
            return authentication;
        }
    }
}