package com.yzx.system.config;

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
        // 1. 从JWT中提取加密字段（适配授权服务器的字段名：username/u_id/id/authorities/permissions）
        String encryptedUsername = (String) map.get("username");
        String encryptedUserId = (String) map.get("u_id") != null ? (String) map.get("u_id") : (String) map.get("id");
        String encryptedAuthorities = (String) map.get("authorities");
        String encryptedPermissions = (String) map.get("permissions");

        // 2. AES解密核心字段
        String username = decrypt(encryptedUsername);
        Long userId = decryptToLong(encryptedUserId);
        Collection<GrantedAuthority> authorities = decryptToAuthorities(encryptedAuthorities);
        Set<String> permissions = decryptToPermissions(encryptedPermissions);

        // 3. 仅当用户名不为空时，构建完整的BaseUserDetail
        if (!StringUtils.isEmpty(username)) {
            try {
                // 3.1 构建BaseAuth（基础认证信息）
                BaseAuth baseAuth = new BaseAuth();
                baseAuth.setUserName(username); // 解密后的用户名

                // 3.2 构建BaseUser（用户基础信息）
                BaseUser baseUser = new BaseUser();
                if (userId != null) {
                    baseUser.setUserId(userId); // 解密后的用户ID
                }

                // 3.3 构建Spring Security的User对象（用于BaseUserDetail构造）
                User securityUser = new User(
                        username,
                        "N/A", // 密码无需存储，填占位符
                        authorities // 解密后的权限
                );

                // 3.4 正确实例化BaseUserDetail（必须用构造函数）
                BaseUserDetail userDetail = new BaseUserDetail(baseAuth, baseUser, securityUser);
                userDetail.setPermissions(permissions); // 设置权限集合

                // 3.5 返回包含BaseUserDetail的认证信息（SecurityUtils能正确获取）
                return new UsernamePasswordAuthenticationToken(
                        userDetail,
                        "N/A",
                        authorities
                );
            } catch (Exception e) {
                log.error("构建BaseUserDetail失败", e);
            }
        }

        // 兼容逻辑：解密失败时返回基础认证（仅用户名）
        String userNameFallback = (String) map.get("user_name") != null ?
                (String) map.get("user_name") : (String) map.get("username");
        return new UsernamePasswordAuthenticationToken(
                userNameFallback,
                "N/A",
                Collections.emptyList()
        );
    }

    // ========== 工具方法：解密+类型转换 ==========

    /**
     * 通用AES解密（适配AESEncryptUtil的java.util.Base64）
     */
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

    /**
     * 解密为Long类型（用户ID）
     */
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

    /**
     * 解密authorities为GrantedAuthority列表
     */
    private Collection<GrantedAuthority> decryptToAuthorities(String encryptedAuthorities) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (!StringUtils.isEmpty(encryptedAuthorities)) {
            String authJson = decrypt(encryptedAuthorities);
            if (!StringUtils.isEmpty(authJson)) {
                try {
                    // 解析授权服务器加密的authorities JSON数组
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

    /**
     * 解密permissions为Set<String>（适配BaseUserDetail的permissions类型）
     */
    private Set<String> decryptToPermissions(String encryptedPermissions) {
        Set<String> permissions = new HashSet<>();
        if (!StringUtils.isEmpty(encryptedPermissions)) {
            String permJson = decrypt(encryptedPermissions);
            if (!StringUtils.isEmpty(permJson)&&!StringUtils.isEmpty(permJson)) {
                try {
                    List<String> permList = JSON.parseArray(permJson, String.class);
                    permissions = new HashSet<>(permList); // List转Set
                } catch (Exception e) {
                    log.error("解析permissions失败：{}", permJson, e);
                }
            }
        }
        return permissions;
    }
}