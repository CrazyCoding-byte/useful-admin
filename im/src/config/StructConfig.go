package config

import "time"

// Config 总配置结构体（对应整个 YAML 配置文件，包含所有子配置）
type Config struct {
	Log          LogConfig       `yaml:"log"`        // 日志配置节点
	Database     DatabaseConfig  `yaml:"database"`   // 数据库配置节点
	AES          AESConfig       `yaml:"aseKey"`     // 对称加密密钥配置（yaml 中节点名为 aseKey）
	MinIO        MinIOConfig     `yaml:"minio"`      // MinIO配置
	Redis        RedisConfig     `yaml:"redis"`      // Redis配置
	FileConfig   FileConfig      `yaml:"file"`       // 文件配置
	MinioCoreCfg MinioCoreConfig `yaml:"minio-core"` // MinIO 专属配置
}

// DatabaseConfig 数据库配置结构体（对应 YAML 中的 database 节点）
type DatabaseConfig struct {
	Driver   string `yaml:"driver"`   // 数据库驱动（如 "mysql"）
	Host     string `yaml:"host"`     // 数据库主机地址
	Port     int    `yaml:"port"`     // 数据库端口
	Username string `yaml:"username"` // 用户名
	Password string `yaml:"password"` // 密码
	Dbname   string `yaml:"dbname"`   // 数据库名
	Charset  string `yaml:"charset"`  // 字符集（如 "utf8mb4"）
}
type MinIOConfig struct {
	Endpoint        string `yaml:"endpoint"`          // 对应yml: minio.endpoint
	AccessKeyID     string `yaml:"access-key-id"`     // 对应yml: minio.access-key-id（关键！）
	SecretAccessKey string `yaml:"secret-access-key"` // 对应yml: minio.secret-access-key（关键！）
	UseSSL          bool   `yaml:"use-ssl"`           // 对应yml: minio.use-ssl
	BucketName      string `yaml:"bucket-name"`       // 对应yml: minio.bucket-name
}

// LogConfig 日志配置结构体（对应 YAML 中的 log 节点）
type LogConfig struct {
	Path    string `yaml:"path"`     // 日志文件路径（如 "logs/app.log"）
	Level   string `yaml:"level"`    // 日志级别（debug/info/warn/error）
	Format  string `yaml:"format"`   // 日志格式（text/json）
	MaxSize int    `yaml:"max_size"` // 单个文件最大大小（MB）
}

// AESConfig 对称加密配置（对应 YAML 中的 aseKey 节点）
type AESConfig struct {
	Key string `yaml:"key"` // 对称加密密钥
}

type RedisConfig struct {
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	Password string `yaml:"password"`
}

/*
*

	upload-baseUrl: "file-storage" # 文件下载基础 URL
	max-file-size: 200
	max-chunk-size: 5
*/
type FileConfig struct {
	BaseUrl      string `yaml:"upload-baseUrl"`
	MaxFileSize  int64  `yaml:"max-file-size"`
	MaxChunkSize int64  `yaml:"max-chunk-size"`
}

type MinioCoreConfig struct {
	BucketName  string        `yaml:"bucket-name"`
	RedisPrefix string        `yaml:"redis-prefix"`
	ExpireTime  time.Duration `yaml:"expire-time"`
	MaxRetries  int           `yaml:"max-retries"`
	RetryDelay  time.Duration `yaml:"retry-delay"`
}
