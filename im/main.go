package main

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

}
