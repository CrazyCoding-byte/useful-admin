package cn.poile.ucs.auth.convert;

import cn.poile.ucs.auth.security.UserNameUserDetailService;
import com.yzx.model.ucenter.BaseUserDetail;
import com.yzx.model.utils.AESEncryptUtil;
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
import java.util.Base64;
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
    private AESEncryptUtil aesEncryptUtil;
    @Autowired
    private UserNameUserDetailService userNameUserDetailService;

    @Autowired
    private CustomUserAuthenticationConverter customUserAuthenticationConverter;

    public static void main(String[] args) {
        try {
            byte[] decoded = Base64.getDecoder().decode("0tVToE4Kbib+iEerRoPhiw==");
            log.info("Base64解码成功，长度：{}", decoded.length);
        } catch (IllegalArgumentException e) {
            log.error("Base64解码失败，密文格式错误", e);
        }
    }

    @PostConstruct
    public void init() {
        // 设置密钥对
        KeyPair keyPair = new KeyStoreKeyFactory(keyProperties.getKeyStore().getLocation(), keyProperties.getKeyStore().getSecret().toCharArray()).getKeyPair(keyProperties.getKeyStore().getAlias(), keyProperties.getKeyStore().getPassword().toCharArray());
        this.setKeyPair(keyPair);
        // 设置自定义的用户认证转换器
        DefaultAccessTokenConverter tokenConverter = (DefaultAccessTokenConverter) getAccessTokenConverter();
        tokenConverter.setUserTokenConverter(customUserAuthenticationConverter);
    }

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken oAuth2AccessToken, OAuth2Authentication oAuth2Authentication) {
        String name = oAuth2Authentication.getName();
        log.debug("jwt token name is :" + name);

        // 先调用父类的 enhance 方法，让父类调用 convertUserAuthentication 设置用户信息
        OAuth2AccessToken enhancedToken = super.enhance(oAuth2AccessToken, oAuth2Authentication);

        // 获取 principal
        Object principal = oAuth2Authentication.getPrincipal();
        BaseUserDetail baseUserDetail = null;
        if (principal instanceof BaseUserDetail) {
            baseUserDetail = (BaseUserDetail) principal;
        } else {
            UserDetails user = userNameUserDetailService.loadUserByUsername(name);
            baseUserDetail = (BaseUserDetail) user;
        }
        log.debug("ba user detail :" + baseUserDetail);

        DefaultOAuth2AccessToken token = (DefaultOAuth2AccessToken) enhancedToken;

        // 获取现有的 additional information（包含 convertUserAuthentication 设置的字段）
        Map<String, Object> map = new LinkedHashMap<>(token.getAdditionalInformation());

        try {
            // 对敏感字段进行AES加密
            String encryptedUid = aesEncryptUtil.encrypt(baseUserDetail.getBaseUser().getUserId().toString());

            // 添加额外的字段
            map.put("u_id", encryptedUid);

            // 添加租户ID到Token（多租户支持）
            String tenantId = baseUserDetail.getBaseUser().getTenantId();
            if (tenantId != null && !tenantId.isEmpty()) {
                map.put("tenant_id", tenantId);
                log.debug("JWT Token 中添加租户ID: {}", tenantId);
            } else {
                log.warn("用户 [{}] 的租户ID为空，可能导致多租户功能异常", baseUserDetail.getUsername());
            }
        } catch (Exception e) {
            log.error("加密用户信息失败", e);
            throw new RuntimeException("Token 生成失败", e);
        }

        token.setAdditionalInformation(map);
        log.debug("oAuth2AccessToken==========>" + enhancedToken);

        return token;
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