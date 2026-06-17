package com.yzx.common.excel.core;

import cn.hutool.core.util.StrUtil;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @className: DefaultExcelResult
 * @author: yzx
 * @date: 2026/6/17 17:28
 * @Version: 1.0
 * @description:
 */
public class DefaultExcelResult<T> implements ExcelResult<T> {
    /**
     * 数据对象list
     */
    @Setter
    private List<T> list;

    @Setter
    private List<String> errorList;

    public DefaultExcelResult() {
        this.list = new ArrayList<>();
        this.errorList = new ArrayList<>();
    }

    @Override
    public List<T> getList() {
        return list;
    }

    @Override
    public List<String> getErrorList() {
        return errorList;
    }

    @Override
    public String getAnalysis() {
        int successCount = list.size();
        int errorCount = errorList.size();
        if (successCount == 0) {
            return "读取失败,未解析到数据";
        } else {
            if (errorCount == 0) {
                return StrUtil.format("恭喜你,全部读取成功{}", successCount);
            } else {
                return "";
            }
        }
    }
}
