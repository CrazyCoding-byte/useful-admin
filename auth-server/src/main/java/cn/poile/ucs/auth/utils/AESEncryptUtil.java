package cn.poile.ucs.auth.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * AES 对称加密工具类，用于加密 Token 中的敏感数据
 */
public class AESEncryptUtil {
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final String KEY = "yangzhixuan944166810@qq.com1150614966@qq.com13735713859"; // 对称加密密钥（需安全存储，如配置中心）

    /**
     * 加密字符串
     */
    public static String encrypt(String plainText) throws Exception {
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
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
        return new String(decrypted);
    }
}