package utils

import (
	"crypto/aes"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"

	"github.com/golang-jwt/jwt/v5"
)

// VerifyToken 验证 Token 是否被篡改，返回解析后的 claims。
func VerifyToken(tokenStr string, pubKey *rsa.PublicKey) (jwt.MapClaims, error) {
	// 解析 Token（同时验证签名）
	token, err := jwt.Parse(tokenStr, func(token *jwt.Token) (interface{}, error) {
		// 1. 验证签名算法是否与认证服务一致（如 RS256）
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

	// 4. 检查 Token 是否有效（包含签名验证结果）
	if !token.Valid {
		return nil, errors.New("Token无效（可能被篡改或已过期）")
	}

	// 5. 提取 claims（包含用户信息、过期时间等）
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return nil, errors.New("提取Token信息失败")
	}

	return claims, nil
}

// ParseAndVerifyToken 解析并验证 Token，同时解密用户信息。
//
// 修复点：
//   - 公钥路径不再硬编码绝对路径，而是从可执行文件所在目录向上回溯查找 publickey.txt，
//     适配 Windows/Linux 与不同部署目录；
//   - 同一 Token 只解析一次签名，复用 claims，避免重复 jwt.Parse 带来的不一致风险。
func ParseAndVerifyToken(tokenStr, aesKey string) (userID, username string, err error) {
	// 1. 加载公钥（路径自动解析，不再依赖硬编码绝对路径）
	key, err := LoadPublicKey(resolvePublicKeyPath())
	if err != nil {
		return "", "", err
	}

	// 2. 一次性完成 RSA 签名校验 + claims 提取
	claims, err := VerifyToken(tokenStr, key)
	if err != nil {
		return "", "", err
	}

	// Spring Security OAuth 会把 additionalInformation 合并到 JWT 顶层；
	// 同时兼容旧实现将用户字段放在 additionalInformation 内的格式。
	additionalInfo := map[string]interface{}(claims)
	if nestedInfo, ok := claims["additionalInformation"].(map[string]interface{}); ok {
		additionalInfo = nestedInfo
	}

	// 与 system 资源服务保持一致：u_id 存在时使用 u_id，否则使用 id。
	encryptedUid, ok := encryptedClaim(additionalInfo, "u_id")
	if !ok {
		encryptedUid, ok = encryptedClaim(additionalInfo, "id")
	}
	if !ok {
		return "", "", fmt.Errorf("JWT 中缺少 u_id/id 字段，实际类型: u_id=%T, id=%T", additionalInfo["u_id"], additionalInfo["id"])
	}
	uid, err := AesDecrypt(encryptedUid, aesKey)
	if err != nil {
		return "", "", fmt.Errorf("解密 u_id 失败: %v", err)
	}

	// 解密 username
	// Java auth-server 当前写入的是 userName，兼容旧 Token 的 username。
	encryptedUsername, ok := encryptedClaim(additionalInfo, "userName")
	if !ok {
		encryptedUsername, ok = encryptedClaim(additionalInfo, "username")
	}
	if !ok {
		return "", "", fmt.Errorf("JWT 中缺少 userName/username 字段，实际类型: userName=%T, username=%T", additionalInfo["userName"], additionalInfo["username"])
	}
	uname, err := AesDecrypt(encryptedUsername, aesKey)
	if err != nil {
		return "", "", fmt.Errorf("解密 username 失败: %v", err)
	}

	return uid, uname, nil
}

// encryptedClaim 读取 Java auth-server 写入的 AES 密文。
// 密文必须是字符串，数字 ID 不能当作密文解密。
func encryptedClaim(claims map[string]interface{}, name string) (string, bool) {
	value, exists := claims[name]
	if !exists || value == nil {
		return "", false
	}

	text, ok := value.(string)
	return text, ok && text != ""
}

// resolvePublicKeyPath 解析 publickey.txt 的实际路径。
//
// 查找顺序：
//  1. 环境变量 PUBLIC_KEY_PATH（绝对路径），最高优先级，方便运维注入；
//  2. 可执行文件所在目录及其上层 src/publickey.txt；
//  3. 当前工作目录的 src/publickey.txt。
//
// 失败时返回空字符串，由调用方报错。
func resolvePublicKeyPath() string {
	if p := os.Getenv("PUBLIC_KEY_PATH"); p != "" {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}

	candidates := []string{}
	if exe, err := os.Executable(); err == nil {
		exeDir := filepath.Dir(exe)
		candidates = append(candidates,
			filepath.Join(exeDir, "src", "publickey.txt"),
			filepath.Join(exeDir, "..", "im", "src", "publickey.txt"),
			filepath.Join(exeDir, "..", "src", "publickey.txt"),
		)
	}
	if wd, err := os.Getwd(); err == nil {
		candidates = append(candidates,
			filepath.Join(wd, "src", "publickey.txt"),
			filepath.Join(wd, "im", "src", "publickey.txt"),
		)
	}
	for _, p := range candidates {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	return ""
}

// AesDecrypt 解密 Java 端 AES 加密的内容（AES/ECB/PKCS5Padding + Base64 编码）。
func AesDecrypt(cipherText, key string) (string, error) {
	// 1. Base64 解码密文
	cipherBytes, err := base64.StdEncoding.DecodeString(cipherText)
	if err != nil {
		return "", errors.New("Base64解码失败: " + err.Error())
	}

	// 2. 初始化 AES 密码器（密钥长度需与 Java 端一致，如 16 字节 = 128 位）
	block, err := aes.NewCipher([]byte(key))
	if err != nil {
		return "", errors.New("创建AES密码器失败: " + err.Error())
	}

	// 3. ECB 模式解密（与 Java 端算法一致）
	blockSize := block.BlockSize()
	if len(cipherBytes)%blockSize != 0 {
		return "", errors.New("密文长度不是块大小的整数倍")
	}

	plainBytes := make([]byte, len(cipherBytes))
	for i := 0; i < len(cipherBytes); i += blockSize {
		block.Decrypt(plainBytes[i:i+blockSize], cipherBytes[i:i+blockSize])
	}

	// 4. 去除 PKCS5 填充
	plainBytes = Pkcs5Unpad(plainBytes)
	if plainBytes == nil {
		return "", errors.New("PKCS5解填充失败")
	}

	return string(plainBytes), nil
}

// Pkcs5Unpad 去除 PKCS5 填充。
func Pkcs5Unpad(data []byte) []byte {
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

// LoadPublicKey 从文件加载 RSA 公钥。
// 兼容两种常见格式：
//  1. 标准 PEM —— BEGIN/END 标签独占一行
//  2. "压缩" PEM —— BEGIN/END 标签后没有换行，紧贴 Base64
//
// 后者多见于从 Java 控制台/网页复制粘贴出来的 keystore 导出物，
// 会导致 Go 标准库 pem.Decode 返回 nil，进而被误判为"公钥格式错误"。
func LoadPublicKey(path string) (*rsa.PublicKey, error) {
	pubKeyBytes, err := os.ReadFile(path)
	if err != nil {
		return nil, errors.New("读取公钥文件失败: " + err.Error())
	}

	pubKeyBytes = normalizePEM(pubKeyBytes)

	// 解析 PEM 格式公钥
	block, _ := pem.Decode(pubKeyBytes)
	if block == nil || block.Type != "PUBLIC KEY" {
		return nil, errors.New("公钥格式错误")
	}

	// 转换为 rsa.PublicKey
	pubKey, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return nil, errors.New("解析公钥失败: " + err.Error())
	}

	// 断言为 RSA 公钥（与认证服务的非对称算法一致）
	rsaPubKey, ok := pubKey.(*rsa.PublicKey)
	if !ok {
		return nil, errors.New("公钥不是RSA类型")
	}
	return rsaPubKey, nil
}

// normalizePEM 把"BEGIN/END 标签后无换行"的 PEM 规范化为标准格式。
// 对已经标准的 PEM 是 no-op，不会改动内容。
var (
	pemBeginRE = regexp.MustCompile(`(-----BEGIN [^-]+-----)([^A-Za-z0-9+/=\r\n])?`)
	pemEndRE   = regexp.MustCompile(`([^A-Za-z0-9+/=\r\n])?(-----END [^-]+-----)`)
)

func normalizePEM(data []byte) []byte {
	s := string(data)
	// BEGIN 标签后强制补换行（除非已经有空白字符）
	s = pemBeginRE.ReplaceAllString(s, "$1\n")
	// END 标签前强制补换行
	s = pemEndRE.ReplaceAllString(s, "\n$2")
	return []byte(s)
}
