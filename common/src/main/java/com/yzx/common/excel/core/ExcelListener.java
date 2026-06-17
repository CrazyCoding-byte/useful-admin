package com.yzx.common.excel.core;

import cn.idev.excel.read.listener.ReadListener;

/**
 * @className: ExcelListener
 * @author: yzx
 * @date: 2026/6/17 17:17
 * @Version: 1.0
 * @description: 导入监听
 */
public interface ExcelListener<T> extends ReadListener<T> {
    ExcelResult<T> getExcelResult();
}
