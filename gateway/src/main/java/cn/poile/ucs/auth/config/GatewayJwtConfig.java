package cn.poile.ucs.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory;

import java.security.KeyPair;

/**
 * 网关 JWT 配置：加载本地 KeyStore 公钥，用于验签和解析 JWT
 */
@Configuration
public class GatewayJwtConfig {

    /**
     * JWT 令牌转换器（核心：用本地 KeyStore 公钥验签）
     */
    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();

        // 加载 KeyStore 文件（对应你的 encrypt.key-store 配置）
        KeyStoreKeyFactory keyStoreKeyFactory = new KeyStoreKeyFactory(new ClassPathResource("xz.keystore"), // 密钥库路径：classpath:/xz.keystore
                "xiaozuokeystore".toCharArray()       // 密钥库密码：secret = xiaozuokeystore
        );
        // 获取密钥对（公钥用于验签）
        KeyPair keyPair = keyStoreKeyFactory.getKeyPair("xzkey",                  // 密钥别名：alias = xzkey
                "xiaozuo".toCharArray()   // 密钥密码：password = xiaozuo
        );
        converter.setKeyPair(keyPair); // 设置密钥对，自动用公钥验签
        return converter;
    }

    /**
     * TokenStore：基于 JWT 的令牌存储（本地解析，无远程依赖）
     */
    @Bean
    public TokenStore tokenStore(JwtAccessTokenConverter jwtAccessTokenConverter) {
        return new JwtTokenStore(jwtAccessTokenConverter);
    }
}