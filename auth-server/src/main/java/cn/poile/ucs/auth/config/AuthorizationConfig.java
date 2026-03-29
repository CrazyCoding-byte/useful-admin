package cn.poile.ucs.auth.config;

import cn.poile.ucs.auth.auth.granter.MobileCodeTokenGranter;
import cn.poile.ucs.auth.auth.granter.ScanCodeTokenGranter;
import cn.poile.ucs.auth.convert.JwtAccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.CompositeTokenGranter;
import org.springframework.security.oauth2.provider.OAuth2RequestFactory;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenGranter;
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeTokenGranter;
import org.springframework.security.oauth2.provider.implicit.ImplicitTokenGranter;
import org.springframework.security.oauth2.provider.password.ResourceOwnerPasswordTokenGranter;
import org.springframework.security.oauth2.provider.refresh.RefreshTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * @className: BaseUserDetails
 * @author: yzx
 * @date: 2025/8/21 6:24
 * @Version: 1.0
 * @description:
 */
@Configuration
@EnableAuthorizationServer
public class AuthorizationConfig extends AuthorizationServerConfigurerAdapter {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JwtAccessToken jwtAccessToken; // 使用自定义的JwtAccessToken

    @Autowired
    @Qualifier("ClientDetailsService")
    private ClientDetailsService clientDetailsService;



    @Bean
    public AuthorizationServerTokenServices tokenServices(TokenStore tokenStore) {
        DefaultTokenServices services = new DefaultTokenServices();
        // 1. 核心依赖注入（必须全！）
        services.setTokenStore(tokenStore);
        services.setClientDetailsService(clientDetailsService); // 客户端信息
        services.setAuthenticationManager(authenticationManager); // 认证管理器
        services.setTokenEnhancer(jwtAccessToken); // 关键：把 Token 转成 JWT 格式

        // 2. 刷新 Token 配置
        services.setSupportRefreshToken(true);
        services.setReuseRefreshToken(true);

        // 3. 过期时间（测试用 10 秒）
        services.setAccessTokenValiditySeconds(10);
        services.setRefreshTokenValiditySeconds(604800); // 7 天

        return services;
    }

    /**
     * 配置 Token Store
     */
    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(jwtAccessToken);
    }

    /**
     * 配置 ClientDetailsService Bean，命名为 "ClientDetailsService"
     * 改为静态方法以避免循环依赖
     */
    @Bean("ClientDetailsService")
    public static ClientDetailsService clientDetailsService(DataSource dataSource) {
        return new JdbcClientDetailsService(dataSource);
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        // 使用JdbcClientDetailsService客户端详情服务
        clients.jdbc(this.dataSource);
    }

    /**
     * 授权服务器端点配置
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) {
        AuthorizationCodeServices authorizationCodeServices = endpoints.getAuthorizationCodeServices();
        List<TokenGranter> tokenGranters = getTokenGranters(
                authorizationCodeServices,
                tokenServices(tokenStore()),
                endpoints.getClientDetailsService(),
                endpoints.getOAuth2RequestFactory());
        tokenGranters.add(endpoints.getTokenGranter());

        endpoints.authenticationManager(authenticationManager)
                // 使用自定义的JwtAccessToken转换器
                .accessTokenConverter(jwtAccessToken)
                .tokenStore(tokenStore())
                .tokenServices(tokenServices(tokenStore()))
                .tokenGranter(new CompositeTokenGranter(tokenGranters));
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security

                .allowFormAuthenticationForClients()
                .tokenKeyAccess("permitAll()")
                .checkTokenAccess("isAuthenticated()");
    }

    /**
     * 创建 grant_type 列表
     */
    private List<TokenGranter> getTokenGranters(AuthorizationCodeServices authorizationCodeServices,
                                                AuthorizationServerTokenServices tokenServices,
                                                ClientDetailsService clientDetailsService,
                                                OAuth2RequestFactory requestFactory) {
        List<TokenGranter> tokenGranters = new ArrayList<>();
        tokenGranters.add(new AuthorizationCodeTokenGranter(tokenServices, authorizationCodeServices, clientDetailsService, requestFactory));
        tokenGranters.add(new RefreshTokenGranter(tokenServices, clientDetailsService, requestFactory));
        ImplicitTokenGranter implicit = new ImplicitTokenGranter(tokenServices, clientDetailsService, requestFactory);
        tokenGranters.add(implicit);
        tokenGranters.add(new ClientCredentialsTokenGranter(tokenServices, clientDetailsService, requestFactory));
        if (authenticationManager != null) {
            tokenGranters.add(new ResourceOwnerPasswordTokenGranter(authenticationManager, tokenServices, clientDetailsService, requestFactory));
        }
        tokenGranters.add(new MobileCodeTokenGranter(authenticationManager, tokenServices, clientDetailsService, requestFactory));
        tokenGranters.add(new ScanCodeTokenGranter(authenticationManager, tokenServices, clientDetailsService, requestFactory));
        return tokenGranters;
    }

}