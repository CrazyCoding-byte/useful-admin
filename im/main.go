package main

import (
	"fmt"
	"gorm.io/gorm"
	"local/im/src/config"
	"log/slog"
	"time"
)

type User struct {
	ID      uint
	Name    string
	Profile Profile   `gorm:"foreignKey:UserID"`
	Article []Article `gorm:"foreignKey:UserID"`
}

func (User) TableName() string {
	return "users"
}

type Profile struct {
	ID     uint   `gorm:"column:id;primaryKey;autoIncrement"` // 显式声明自增主键
	UserID uint   `gorm:"column:user_id"`
	Bio    string `gorm:"column:bio"`
}

func (Profile) TableName() string {
	return "profiles"
}

type Article struct {
	ID     uint   `gorm:"column:id;primaryKey;autoIncrement"`
	Title  string `gorm:"column:title"`
	UserID uint   `gorm:"column:user_id"`
	Status uint   `gorm:"column:status"`
}

func (Article) TableName() string {
	return "articles"
}

type Tag struct {
	ID   uint
	Name string
}

func main() {
	dateStr := time.Now().Format("20060102")
	fmt.Println(dateStr)
	// 1. 加载总配置   [是返回值类型]
	cfg, err := config.LoadConfig[config.Config]("E:\\studyoauth2\\springcloud-oauth2\\im\\src\\application.yml")
	if err != nil {
		panic(fmt.Sprintf("加载配置失败：%v", err))
	}

	// 2. 初始化日志
	if err := cfg.Log.InitLogger(); err != nil {
		panic(fmt.Sprintf("日志初始化失败：%v", err))
	}
	// 3. 初始化数据库（获取 *sql.DB 连接）
	_db, err := cfg.Database.InitDatabase()
	// 自动迁移，观察 GORM 如何创建表
	_db.AutoMigrate(&User{}, &Profile{}, &Article{}, &Tag{})
	// 查看生成的 SQL
	var users []User
	statement := _db.Session(&gorm.Session{DryRun: true}).Preload("Profile").Find(&users)
	fmt.Println("生成的 SQL:", statement.Statement.SQL.String())
	// 实际使用
	// 预加载单个关联
	//_db.Preload("Profile").Preload("Article").Find(&users)
	// 为关联表添加条件  注意他搜索的条件是不会过滤
	//_db.Preload("Article", "status = ? and id=?", 1, 1).Find(&users)
	_db.Table("users").
		// 把articles的过滤条件（status=1、user_id=1）移到ON子句
		Joins("left join articles on users.id = articles.user_id and articles.status = ? and users.id = ?", 1, 1).
		// 这里Where只放users表的条件（如果需要过滤用户）
		// Where("users.id = ?", 1).
		Find(&users)
	//_db.Where("id = ?", 1). // 核心：过滤主表 users，只取 id=1 的记录
	//			Preload("Profile").                  // 加载该用户的 Profile
	//			Preload("Article", "status = ?", 1). // 加载该用户状态为1的文章
	//			First(&users)                        // 用 First 而不是 Find，因为只查1条
	fmt.Println(users)
	// 后续业务逻辑...
	slog.Info("应用启动成功")
	fmt.Println("配置加载成功，程序启动中...")
}
