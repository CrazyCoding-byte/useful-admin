package com.yzx.common.utils;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装类
 * @param <T> 数据类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 每页显示条数
     */
    private Long size;

    /**
     * 当前页码
     */
    private Long current;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 数据列表
     */
    private List<T> records;

    public PageResult() {
    }

    public PageResult(Long total, Long size, Long current, List<T> records) {
        this.total = total;
        this.size = size;
        this.current = current;
        this.records = records;
        // 计算总页数
        this.pages = (total + size - 1) / size;
    }

    public PageResult(Long total, Long size, Long current, Long pages, List<T> records) {
        this.total = total;
        this.size = size;
        this.current = current;
        this.pages = pages;
        this.records = records;
    }

    /**
     * 使用 MyBatis-Plus 的 Page 对象创建 PageResult
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        return new PageResult<>(
                page.getTotal(),
                page.getSize(),
                page.getCurrent(),
                page.getPages(),
                page.getRecords()
        );
    }
}
