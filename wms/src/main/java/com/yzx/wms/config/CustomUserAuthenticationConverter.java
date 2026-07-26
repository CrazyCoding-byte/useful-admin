package com.yzx.wms.config;

import com.alibaba.fastjson.JSON;
import com.yzx.model.ucenter.BaseAuth;
import com.yzx.model.ucenter.BaseUser;
import com.yzx.model.ucenter.BaseUserDetail;
import com.yzx.model.utils.AESEncryptUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.provider.token.UserAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CustomUserAuthenticationConverter implements UserAuthenticationConverter {
    // 注入和授权服务器一致的AES解密工具
    @Autowired
    private AESEncryptUtil aesEncryptUtil;

    /**
     * 资源服务器无需实现此方法（授权服务器专用）
     */
    @Override
    public Map<String, ?> convertUserAuthentication(Authentication authentication) {
        throw new UnsupportedOperationException("资源服务器不支持此方法");
    }

    /**
     * 核心：解析JWT并解密，构建正确的BaseUserDetail
     */
    @Override
    public Authentication extractAuthentication(Map<String, ?> map) {
        String encryptedUsername = (String) map.get("userName");
        if (encryptedUsername == null) {
            encryptedUsername = (String) map.get("username");
        }
        String encryptedUserId = (String) map.get("u_id") != null ? (String) map.get("u_id") : (String) map.get("id");
        String encryptedAuthorities = (String) map.get("authorities");
        String encryptedPermissions = (String) map.get("permissions");

        String tenantId = (String) map.get("tenant_id");

        String username = decrypt(encryptedUsername);
        Long userId = decryptToLong(encryptedUserId);
        Collection<GrantedAuthority> authorities = decryptToAuthorities(encryptedAuthorities);
        Set<String> permissions = decryptToPermissions(encryptedPermissions);

        if (!StringUtils.isEmpty(username)) {
            try {
                BaseAuth baseAuth = new BaseAuth();
                baseAuth.setUserName(username);

                BaseUser baseUser = new BaseUser();
                if (userId != null) {
                    baseUser.setUserId(userId);
                }
                if (tenantId != null && !tenantId.isEmpty()) {
                    baseUser.setTenantId(tenantId);
                    log.debug("从 JWT Token 中解析到租户ID: {}", tenantId);
                } else {
                    log.warn("JWT Token 中未包含租户ID，用户: {}", username);
                }

                User securityUser = new User(
                        username,
                        "N/A",
                        authorities
                );

                BaseUserDetail userDetail = new BaseUserDetail(baseAuth, baseUser, securityUser);
                userDetail.setPermissions(permissions);

                return new UsernamePasswordAuthenticationToken(
                        userDetail,
                        "N/A",
                        authorities
                );
            } catch (Exception e) {
                log.error("构建BaseUserDetail失败", e);
            }
        }

        String userNameFallback = (String) map.get("user_name") != null ?
                (String) map.get("user_name") : (String) map.get("username");
        return new UsernamePasswordAuthenticationToken(
                userNameFallback,
                "N/A",
                Collections.emptyList()
        );
    }

    private String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            return aesEncryptUtil.decrypt(cipherText);
        } catch (IllegalArgumentException e) {
            log.error("AES密钥未配置：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("AES解密失败，密文：{}", cipherText, e);
            return null;
        }
    }

    private Long decryptToLong(String cipherText) {
        String plainText = decrypt(cipherText);
        if (plainText == null || !plainText.matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(plainText);
        } catch (NumberFormatException e) {
            log.error("解密后转换Long失败：{}", plainText, e);
            return null;
        }
    }

    private Collection<GrantedAuthority> decryptToAuthorities(String encryptedAuthorities) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (!StringUtils.isEmpty(encryptedAuthorities)) {
            String authJson = decrypt(encryptedAuthorities);
            if (!StringUtils.isEmpty(authJson)) {
                try {
                    List<Map> rawAuthList = JSON.parseArray(authJson, Map.class);
                    if (rawAuthList == null || rawAuthList.isEmpty()) {
                        return authorities;
                    }
                    List<Map<String, String>> authList = rawAuthList.stream()
                            .map(rawMap -> {
                                Map<String, String> stringMap = new HashMap<>();
                                rawMap.forEach((k, v) -> {
                                    if (k != null && v != null) {
                                        stringMap.put(String.valueOf(k), String.valueOf(v));
                                    }
                                });
                                return stringMap;
                            })
                            .collect(Collectors.toList());
                    authorities = authList.stream()
                            .map(authMap -> new SimpleGrantedAuthority(authMap.get("authority")))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    log.error("解析authorities失败：{}", authJson, e);
                }
            }
        }
        return authorities;
    }

    private Set<String> decryptToPermissions(String encryptedPermissions) {
        Set<String> permissions = new HashSet<>();
        if (!StringUtils.isEmpty(encryptedPermissions)) {
            String permJson = decrypt(encryptedPermissions);
            if (!StringUtils.isEmpty(permJson)) {
                try {
                    List<String> permList = JSON.parseArray(permJson, String.class);
                    permissions = new HashSet<>(permList);
                } catch (Exception e) {
                    log.error("解析permissions失败：{}", permJson, e);
                }
            }
        }
        return permissions;
    }
}
