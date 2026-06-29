package com.yzx.common.excel.core;

import cn.idev.excel.write.handler.SheetWriteHandler;
import com.yzx.common.service.DictService;
import com.yzx.model.utils.SpringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @className: ExcelDownHandler
 * @author: yzx
 * @date: 2026/6/20 20:51
 * @Version: 1.0
 * @description:
 */
@Slf4j
public class ExcelDownHandler implements SheetWriteHandler {
    public static void main(String args[]) {
        Set<Integer> set = new HashSet<Integer>();
        set.add(1);
        set.add(100);
        set.add(1000);
        set.add(2);
        set.add(4);
        Iterator<Integer> iterator = set.iterator();
        while (iterator.hasNext()) {
            if (iterator.hasNext()) {
                System.out.println(iterator.next());
            }
        }
    }

    /**
     * Excel表格中的列名英文
     * 仅为了解析列英文，禁止修改
     */
    private static final String EXCEL_COLUMN_NAME = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /**
     * 单选数据Sheet名
     */
    private static final String OPTIONS_SHEET_NAME = "options";

    /**
     * 联动选择数据Sheet名的头
     */
    private static final String LINKED_OPTIONS_SHEET_NAME = "linkedOptions";

    /**
     * 下拉可选项
     */
    private final List<DropDownOptions> dropDownOptions;
    private final DictService dictService;

    /**
     * 当前单选进度
     */
    private int currentOptionsColumnIndex;
    /**
     * 当前联动选择进度
     */
    private int currentLinkedOptionsSheetIndex;

    public ExcelDownHandler(List<DropDownOptions> options) {
        this.dropDownOptions = options;
        this.currentOptionsColumnIndex = 0;
        this.currentLinkedOptionsSheetIndex = 0;
        this.dictService = SpringUtils.getBean(DictService.class);
    }
}
