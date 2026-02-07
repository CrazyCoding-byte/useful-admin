package config

// Config 总配置结构体（对应整个 YAML 配置文件，包含所有子配置）
type Config struct {
	Log      LogConfig      `yaml:"log"`      // 日志配置节点
	Database DatabaseConfig `yaml:"database"` // 数据库配置节点
	AES      AESConfig      `yaml:"aseKey"`   // 对称加密密钥配置（yaml 中节点名为 aseKey）
	MinIO    MinIOConfig    `yaml:"minio"`    // 新增MinIO配置
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
	Endpoint        string `yaml:"endpoint"`
	AccessKeyID     string `yaml:"accessKeyID"`
	SecretAccessKey string `yaml:"secretAccessKey"`
	UseSSL          bool   `yaml:"useSSL"`
	BucketName      string `yaml:"bucketName"`
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
