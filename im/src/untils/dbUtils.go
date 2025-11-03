package untils

import (
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"time"
)

// 初始化数据库连接（gorm）
func initDB() *gorm.DB {
	db, err := gorm.Open(mysql.Open("user:password@tcp(localhost:3306)/im_db?charset=utf8mb4&parseTime=True&loc=Local"), &gorm.Config{})
	if err != nil {
		panic("数据库连接失败: " + err.Error())
	}
	// 自动迁移表结构
	db.AutoMigrate(&Message{})
	return db
}
