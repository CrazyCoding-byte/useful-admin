package cn.poile.ucs.auth.convert;

import cn.poile.ucs.auth.security.UserNameUserDetailService;
import cn.poile.ucs.auth.utils.AESEncryptUtil;
import com.yzx.model.ucenter.BaseUserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * @className: BaseUserDetails
 * @author: yzx
 * @date: 2025/8/21 6:24
 * @Version: 1.0
 * @description: 自定义JwtAccessToken转换器 这个类可以增强令牌
 */
@Component
@Slf4j
public class JwtAccessToken extends JwtAccessTokenConverter {

    @Bean("keyProp")
    public KeyProperties keyProperties() {
        return new KeyProperties();
    }

    @Resource(name = "keyProp")
    private KeyProperties keyProperties;

    @Autowired
    private UserNameUserDetailService userNameUserDetailService;

    @Autowired
    private CustomUserAuthenticationConverter customUserAuthenticationConverter;

    @PostConstruct
    public void init() {
        // 设置密钥对
        KeyPair keyPair = new KeyStoreKeyFactory(
                keyProperties.getKeyStore().getLocation(),
                keyProperties.getKeyStore().getSecret().toCharArray())
                .getKeyPair(keyProperties.getKeyStore().getAlias(),
                        keyProperties.getKeyStore().getPassword().toCharArray());
        this.setKeyPair(keyPair);
        // 设置自定义的用户认证转换器
        DefaultAccessTokenConverter tokenConverter = (DefaultAccessTokenConverter) getAccessTokenConverter();
        tokenConverter.setUserTokenConverter(customUserAuthenticationConverter);
    }

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken oAuth2AccessToken, OAuth2Authentication oAuth2Authentication) {
        String name = oAuth2Authentication.getName();
        log.debug("jwt token name is :" + name);
        Map<String, Object> map = new LinkedHashMap<>();
        Object principal = oAuth2Authentication.getPrincipal();
        BaseUserDetail baseUserDetail = null;
        if (principal instanceof BaseUserDetail) {
            baseUserDetail = (BaseUserDetail) principal;
        } else {
            UserDetails user = userNameUserDetailService.loadUserByUsername(name);
            baseUserDetail = (BaseUserDetail) user;
        }
        log.debug("ba user detail :" + baseUserDetail);
        DefaultOAuth2AccessToken token = (DefaultOAuth2AccessToken) oAuth2AccessToken;
        try {
            // 对敏感字段进行AES加密
            String encryptedUid = AESEncryptUtil.encrypt(baseUserDetail.getBaseUser().getUserId().toString());
            String encryptedUsername = AESEncryptUtil.encrypt(baseUserDetail.getBaseAuth().getUserName());
            map.put("username", encryptedUsername);
//        map.put("mobile", baseUserDetail.getBaseAuth().getPhoneNumber());
            map.put("u_id", encryptedUid);
        } catch (Exception e) {
            log.error("加密用户信息失败", e);
            throw new RuntimeException("Token 生成失败", e);
        }
        token.setAdditionalInformation(map);
        log.debug("oAuth2AccessToken==========>" + oAuth2AccessToken);
        // 调用父类方法进行签名
        return super.enhance(token, oAuth2Authentication);
    }

    /**
     * 解析token
     *
     * @param value
     * @param map
     * @return
     */
    @Override
    public OAuth2AccessToken extractAccessToken(String value, Map<String, ?> map) {
        OAuth2AccessToken oauth2AccessToken = super.extractAccessToken(value, map);
        return oauth2AccessToken;
    }
}