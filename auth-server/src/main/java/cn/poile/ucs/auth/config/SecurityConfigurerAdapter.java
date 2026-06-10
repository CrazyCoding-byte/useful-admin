package cn.poile.ucs.auth.config;

import cn.poile.ucs.auth.auth.provider.MobileCodeAuthenticationProvider;
import cn.poile.ucs.auth.auth.provider.ScanCodeAuthenticationProvider;
import cn.poile.ucs.auth.security.PhoneUserDetailService;
import cn.poile.ucs.auth.security.ScanUserDetailService;
import cn.poile.ucs.auth.security.UserNameUserDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;

/**
 * security web安全配置,spring-cloud-starter-oauth2依赖于security
 *  默认情况下SecurityConfigurerAdapter执行比ResourceServerConfig先
 * @author: yzx
 * @date: 2025/8/21 6:24
 * @Version: 1.0
 * @description:
 */
@Configuration
@EnableWebSecurity
public class SecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

    @Autowired
    private UserNameUserDetailService userDetailsService;

    @Autowired
    private ScanUserDetailService scanUserDetailService;

    @Autowired
    private PhoneUserDetailService phoneUserDetailService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 配置认证管理器
     *
     * @return
     * @throws Exception
     */
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }


    /**
     * 配置密码加密对象（解密时会用到PasswordEncoder的matches判断是否正确）
     * 用户的password和客户端clientSecret用到，所以存的时候存该bean encode过的密码
     *
     * @return
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 这里是对认证管理器的添加配置
     *
     * @param auth
     * @throws Exception
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // 1. 注册自定义Provider（处理手机号验证码）
        auth.authenticationProvider(mobileCodeAuthenticationProvider());
        // 2. 注册DaoAuthenticationProvider（处理密码模式）
        auth.authenticationProvider(daoAuthenticationProvider());
        auth.authenticationProvider(scanCodeAuthenticationProvider());
        // 3. 注册PreAuthenticatedAuthenticationProvider（处理refresh token）
        auth.authenticationProvider(preAuthenticatedAuthenticationProvider());
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers(
                "/user/logout",
                "/wx/*",
                "/wx/*/*",
                "/auth/refresh/*",
                "/auth/tenant/**",
                "/user/jwt",
                "/ucenter/getVerifyCode/**",
                "/captchaImage",
                "/sendSms");
    }

    /**
     *  安全请求配置,这里配置的是security的部分，这里配置全部通过，安全拦截在资源服务的配置文件中配置，
     *  要不然访问未验证的接口将重定向到登录页面，前后端分离的情况下这样并不友好，无权访问接口返回相关错误信息即可
     * @param http
     * @return void
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .formLogin()
                .and()
                // 权限规则：精准放行，其余必须认证
                .authorizeRequests()
                // 放行：登录、验证码、短信、微信、静态资源等
                .antMatchers(
                        "/auth/user/**",
                        "/auth/tenant/**",
                        "/auth/refresh",
                        "/captchaImage",
                        "/sendSms",
                        "/wx/**",
                        "/ucenter/getVerifyCode/**"
                ).permitAll()
                // 放行 OAuth2 核心接口（刷新/获取token）
                .antMatchers("/oauth/token", "/oauth/token_key").permitAll()
                // 校验token接口需要认证（安全规范）
                .antMatchers("/oauth/check_token").authenticated()
                // 其余所有接口：必须登录认证
                .anyRequest().authenticated()
                .and()
                .csrf().disable()
                .cors();
    }


    /**
     * 自定义手机验证码认证提供者
     *
     * @return
     */
    @Bean
    public MobileCodeAuthenticationProvider mobileCodeAuthenticationProvider() {
        MobileCodeAuthenticationProvider provider = new MobileCodeAuthenticationProvider();
        provider.setHideUserNotFoundExceptions(false);
        provider.setUserDetailsService(phoneUserDetailService);
        return provider;
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        //获取用户的密码进行数据的比对DaoAuthenticationProvider
        DaoAuthenticationProvider provider1 = new DaoAuthenticationProvider();
        // 设置userDetailsService
        provider1.setUserDetailsService(userDetailsService);
        // 禁止隐藏用户未找到异常
        provider1.setHideUserNotFoundExceptions(false);
        // 使用BCrypt进行密码的hash
        provider1.setPasswordEncoder(passwordEncoder());
        return provider1;
    }

    /**
     * 自定义扫码认证提供者
     * @return
     */
    @Bean
    public ScanCodeAuthenticationProvider scanCodeAuthenticationProvider() {
        ScanCodeAuthenticationProvider provider = new ScanCodeAuthenticationProvider();
        provider.setHideUserNotFoundExceptions(false);
        provider.setUserDetailsService(scanUserDetailService);
        provider.setStringRedisTemplate(redisTemplate);
        return provider;
    }

    /**
     * PreAuthenticatedAuthenticationProvider - 用于处理 refresh token
     * @return
     */
    @Bean
    public PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider() {
        PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
        provider.setPreAuthenticatedUserDetailsService(new org.springframework.security.core.userdetails.AuthenticationUserDetailsService<org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken>() {
            @Override
            public org.springframework.security.core.userdetails.UserDetails loadUserDetails(org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken token) throws org.springframework.security.core.userdetails.UsernameNotFoundException {
                return userDetailsService.loadUserByUsername(token.getName());
            }
        });
        return provider;
    }

}

