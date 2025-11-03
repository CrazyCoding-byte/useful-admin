package model

type Result struct {
	Success int  `json:"success"` //第三个参数是元数据类似于java的注解 go也有反射机制
	Data    User `json:"data"`
}
