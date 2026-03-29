package com.yzx.model.utils;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @program: xz-framework-parent
 * @description: oath2.0工具类（适配指定的AESEncryptUtil）
 * @author: Mr.Pan
 * @create: 2020-02-14 17:07
 */
@Component
public class Oauth2Util {
    @Autowired
    private AESEncryptUtil aesEncryptUtil;
    private static final Logger logger = LoggerFactory.getLogger(Oauth2Util.class);

    public UserJwt getUserJwtFromHeader(HttpServletRequest request) {
        Map jwtClaims = Oauth2Util.getJwtClaimsFromHeader(request);
        if (jwtClaims == null) {
            return null;
        }

        // 调用指定的AESEncryptUtil解密ID字段
         String encryptedId = (String) jwtClaims.get("u_id");
         String idStr = decryptWithAesUtil(encryptedId);

        // 核心逻辑保留：类型转换+数字校验
        Integer id = null;
        if (StringUtils.isNotBlank(idStr) && idStr.matches("\\d+")) {
            id = Integer.valueOf(idStr);
        }

        UserJwt userJwt = new UserJwt();
        if (id != null) {
            userJwt.setId(id.longValue());
        }

        // 统一使用指定的AESEncryptUtil解密所有字段
        userJwt.setUsername(decryptWithAesUtil((String) jwtClaims.get("username")));
        userJwt.setUtype(decryptWithAesUtil((String) jwtClaims.get("utype")));
        userJwt.setAvatar(decryptWithAesUtil((String) jwtClaims.get("avatar")));
        userJwt.setRole(decryptWithAesUtil((String) jwtClaims.get("role")));

        return userJwt;
    }

    @Data
    public static class UserJwt {
        private Long id;
        private String username;
        private String avatar;
        private String utype;
        private String role;
    }

    public static Map getJwtClaimsFromHeader(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        //取出头信息
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isEmpty(authorization) || !authorization.contains("Bearer")) {
            return null;
        }
        //从Bearer 后边开始取出token
        String token = authorization.substring(7);
        Map map = null;
        try {
            //解析jwt
            Jwt decode = JwtHelper.decode(token);
            //得到 jwt中的用户信息
            String claims = decode.getClaims();
            //将jwt转为Map
            map = JSON.parseObject(claims, Map.class);
        } catch (Exception e) {
            logger.error("解析JWT失败", e);
            return null;
        }
        return map;
    }

    public boolean checkJwt(String token) {
        try {
            JwtHelper.decode(token);
        } catch (Exception e) {
            logger.error("校验JWT失败", e);
            return false;
        }
        return true;
    }

    /**
     * 适配指定的AESEncryptUtil解密方法，封装异常处理
     * @param cipherText 加密后的Base64字符串
     * @return 解密后的字符串（空值/解密失败返回null）
     */
    private String decryptWithAesUtil(String cipherText) {
        // 空值直接返回，避免解密工具类抛异常
        if (StringUtils.isEmpty(cipherText)) {
            return null;
        }
        try {
            // 调用你提供的AESEncryptUtil静态解密方法
            return aesEncryptUtil.decrypt(cipherText);
        } catch (IllegalArgumentException e) {
            // 密钥未配置的异常，重点日志提示
            logger.error("AES密钥未配置，请检查配置项AES.key", e);
            return null;
        } catch (Exception e) {
            // 其他解密异常（如密文格式错误、密钥不匹配等）
            logger.error("AES解密失败，密文：{}", cipherText, e);
            return null;
        }
    }
}