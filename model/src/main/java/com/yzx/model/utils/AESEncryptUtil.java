package com.yzx.model.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * AES 对称加密工具类，用于加密 Token 中的敏感数据
 */
@Component
public class AESEncryptUtil {
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    @Value("${AES.key}")
    private String KEY;

    public String encrypt(String plainText) throws Exception {
        // 增加空值校验，避免密钥未注入时抛出空指针
        if (KEY == null || KEY.isEmpty()) {
            throw new IllegalArgumentException("AES密钥未配置，请检查AES.key配置项");
        }
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 解密字符串
     */
    public String decrypt(String cipherText) throws Exception {
        // 增加空值校验
        if (KEY == null || KEY.isEmpty()) {
            throw new IllegalArgumentException("AES密钥未配置，请检查AES.key配置项");
        }
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
        return new String(decrypted);
    }
}