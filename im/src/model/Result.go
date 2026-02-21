package model

type Result struct {
	Success int  `json:"success"` //第三个参数是元数据类似于java的注解 go也有反射机制
	Data    User `json:"data"`
}
type PageResult struct {
	Page    int         `json:"page"`  //当前页码
	Size    int         `json:"size"`  //每页条数
	Total   int64       `json:"total"` //总条数
	Pages   int         `json:pages`   //总页数
	Records interface{} `json:records` //数据列表
}
type CursorPageResult struct {
	Records    interface{} `json:"records"`     //当前页数据
	HasMore    bool        `json:"has_more"`    //是否有下一页
	NextCursor int64       `json:"next_cursor"` // 下一页游标（最后一条消息的时间戳）
	Size       int         `json:size`          //每页条数
}

func NewCursorPageResult(records interface{}, hasMore bool, nextCursor int64, size int) *CursorPageResult {
	return &CursorPageResult{
		Records:    records,
		HasMore:    hasMore,
		NextCursor: nextCursor,
		Size:       size,
	}
}
func NewPageResult(page, size int, total int64, records interface{}) *PageResult {
	// 计算总页数
	pages := 0
	if size > 0 {
		pages = int((total + int64(size) - 1) / int64(size))
	}
	return &PageResult{
		Page:    page,
		Size:    size,
		Total:   total,
		Pages:   pages,
		Records: records,
	}
}
