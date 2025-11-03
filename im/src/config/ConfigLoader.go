package config

import (
	"fmt"
	"gopkg.in/yaml.v3"
	"os"
)

// LoadConfig 泛型配置加载函数：从 YAML 文件加载配置到指定结构体
// T 可以是 Config（总配置）、LogConfig（单独日志配置）、DatabaseConfig（单独数据库配置）等
func LoadConfig[T any](path string) (*T, error) {
	// 读取文件内容
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件 %s 失败：%w", path, err)
	}

	// 解析 YAML 到目标结构体
	var config T
	if err := yaml.Unmarshal(data, &config); err != nil {
		return nil, fmt.Errorf("解析配置文件 %s 失败：%w", path, err)
	}

	return &config, nil
}
