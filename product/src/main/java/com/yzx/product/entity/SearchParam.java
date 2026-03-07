package com.yzx.product.entity;

import lombok.Data;

/**
 * @className: SearchParam
 * @author: yzx
 * @date: 2026/3/7 12:08
 * @Version: 1.0
 * @description:
 */
@Data
public class SearchParam {
    /**
     * 搜索框关键词（比如：iPhone 15、纯棉T恤）
     * 前端不传则为null/空字符串
     */
    private String keyword;

    /**
     * 选中的分类ID（必须是三级分类ID！）
     * 前端点击分类树最终选中的三级catId，不传则为null
     */
    private Long catalogId;

    /**
     * 分页页码（默认1）
     * 前端翻页时传，比如上一页/下一页
     */
    private Integer pageNum = 1;

    /**
     * 每页条数（默认10，可自定义）
     */
    private Integer pageSize = 10;
}
