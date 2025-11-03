package untils

import (
	"crypto/aes"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	"os"
)

// 验证Token是否被篡改，返回解析后的claims
func verifyToken(tokenStr string, pubKey *rsa.PublicKey) (jwt.MapClaims, error) {
	// 解析Token（同时验证签名）
	token, err := jwt.Parse(tokenStr, func(token *jwt.Token) (interface{}, error) {
		// 1. 验证签名算法是否与认证服务一致（如RS256）
		if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("不支持的签名算法: %v", token.Header["alg"])
		}
		// 2. 返回公钥，用于验证签名
		return pubKey, nil
	})

	// 3. 检查解析结果
	if err != nil {
		return nil, fmt.Errorf("Token解析失败（可能被篡改或无效）: %v", err)
	}

	// 4. 检查Token是否有效（包含签名验证结果）
	if !token.Valid {
		return nil, errors.New("Token无效（可能被篡改或已过期）")
	}

	// 5. 提取claims（包含用户信息、过期时间等）
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return nil, errors.New("提取Token信息失败")
	}

	return claims, nil

}

// 解析并验证 Token，同时解密用户信息
func parseAndVerifyToken(tokenStr, aesKey string) (userID, username string, err error) {
	// 1. 验证 RSA 签名
	key, err := loadPublicKey(`E:\studyoauth2\springcloud-oauth2\im\src\publickey.txt`)
	publicKey, err := verifyToken(tokenStr, key)
	if err != nil {
		return "", "", err
	}

	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		return publicKey, nil
	})
	if err != nil || !token.Valid {
		return "", "", fmt.Errorf("Token 无效: %v", err)
	}

	// 2. 提取并解密 Payload 中的敏感字段
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return "", "", fmt.Errorf("解析 Claims 失败")
	}

	additionalInfo, ok := claims["additionalInformation"].(map[string]interface{})
	if !ok {
		return "", "", fmt.Errorf("无 additionalInformation 字段")
	}

	// 解密 u_id
	encryptedUid := additionalInfo["u_id"].(string)
	uid, err := aesDecrypt(encryptedUid, aesKey)
	if err != nil {
		return "", "", fmt.Errorf("解密 u_id 失败: %v", err)
	}

	// 解密 username
	encryptedUsername := additionalInfo["username"].(string)
	uname, err := aesDecrypt(encryptedUsername, aesKey)
	if err != nil {
		return "", "", fmt.Errorf("解密 username 失败: %v", err)
	}

	return uid, uname, nil
}

// aesDecrypt 解密Java端AES加密的内容（AES/ECB/PKCS5Padding + Base64编码）
func aesDecrypt(cipherText, key string) (string, error) {
	// 1. Base64解码密文
	cipherBytes, err := base64.StdEncoding.DecodeString(cipherText)
	if err != nil {
		return "", errors.New("Base64解码失败: " + err.Error())
	}

	// 2. 初始化AES密码器（密钥长度需与Java端一致，如16字节=128位）
	block, err := aes.NewCipher([]byte(key))
	if err != nil {
		return "", errors.New("创建AES密码器失败: " + err.Error())
	}

	// 3. ECB模式解密（与Java端算法一致）
	blockSize := block.BlockSize()
	if len(cipherBytes)%blockSize != 0 {
		return "", errors.New("密文长度不是块大小的整数倍")
	}

	plainBytes := make([]byte, len(cipherBytes))
	for i := 0; i < len(cipherBytes); i += blockSize {
		block.Decrypt(plainBytes[i:i+blockSize], cipherBytes[i:i+blockSize])
	}

	// 4. 去除PKCS5填充
	plainBytes = pkcs5Unpad(plainBytes)
	if plainBytes == nil {
		return "", errors.New("PKCS5解填充失败")
	}

	return string(plainBytes), nil
}

// pkcs5Unpad 去除PKCS5填充
func pkcs5Unpad(data []byte) []byte {
	length := len(data)
	if length == 0 {
		return nil
	}
	padLen := int(data[length-1])
	if padLen > length || padLen == 0 {
		return nil
	}
	return data[:length-padLen]
}

// 从文件加载RSA公钥
func loadPublicKey(path string) (*rsa.PublicKey, error) {
	// 读取公钥文件内容（如publickey.txt）
	pubKeyBytes, err := os.ReadFile(path)
	if err != nil {
		return nil, errors.New("读取公钥文件失败: " + err.Error())
	}

	// 解析PEM格式公钥
	block, _ := pem.Decode(pubKeyBytes)
	if block == nil || block.Type != "PUBLIC KEY" {
		return nil, errors.New("公钥格式错误")
	}

	// 转换为rsa.PublicKey
	pubKey, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return nil, errors.New("解析公钥失败: " + err.Error())
	}

	// 断言为RSA公钥（与认证服务的非对称算法一致）
	rsaPubKey, ok := pubKey.(*rsa.PublicKey)
	if !ok {
		return nil, errors.New("公钥不是RSA类型")
	}
	return rsaPubKey, nil
}
