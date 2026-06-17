package com.yzx.common.excel.core;

import java.util.List;

/**
 * @className: ExcelResult
 * @author: yzx
 * @date: 2026/6/17 17:18
 * @Version: 1.0
 * @description:
 */
public interface ExcelResult<T> {
    /**
     * 对象列表
     */
    List<T> getList();

    /**
     * 错误列表
     */
    List<String> getErrorList();

    /**
     * 导入回执
     */
    String getAnalysis();
}
