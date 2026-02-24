package cn.poile.ucs.auth.utils;

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
    // 1. 静态变量（保留，作为实际使用的密钥）
    private static String KEY;

    // 2. 非静态的setter方法，用于接收Spring注入的配置值
    //    @Value注解放到setter方法上，注入实例级别的值后赋值给静态变量
    @Value("${AES.key}")
    public void setKey(String key) {
        AESEncryptUtil.KEY = key; // 将实例变量的值赋值给静态变量
    }

    /**
     * 加密字符串
     */
    public static String encrypt(String plainText) throws Exception {
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
    public static String decrypt(String cipherText) throws Exception {
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