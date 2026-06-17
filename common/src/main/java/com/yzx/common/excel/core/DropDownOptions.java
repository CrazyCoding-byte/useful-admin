package com.yzx.common.excel.core;

import cn.hutool.core.util.StrUtil;
import com.yzx.model.Convert;
import com.yzx.model.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 *  * <h1>Excel下拉可选项</h1>
 *  * 注意：为确保下拉框解析正确，传值务必使用createOptionValue()做为值的拼接
 */
public class DropDownOptions {
    /**
     *一级下拉所在列index,从0开始算
     */
    private int index;
    /**
     *二级下拉所在的index,从0开始算,不能与一级相同
     */
    private int nextIndex;
    /**
     *一级下拉所包含的数据
     */
    private List<String> options = new ArrayList<>();

    /**
     *二级下拉所包含的数据map
     *以每一个一级选项值为Key,每个一级选项对应的二级数据为Value
     */
    private Map<String, List<String>> nextOptions = new HashMap<>();
    /**
     * 分隔符
     */
    private static final String DELIMITER = "_";

    public DropDownOptions(int index, List<String> options) {
        this.index = index;
        this.options = options;
    }

    public static String createOptionValue(Object... vars) {
        StringBuilder stringBuilder = new StringBuilder();
        String regex = "^[\\S\\d\\u4e00-\\u9fa5]+$";
        for (int i = 0; i < vars.length; i++) {
            String var = StrUtil.trimToEmpty(Convert.toStr(vars[i]));
            if (!var.matches(regex)) {
                throw new ServiceException("选项数据不符合规则,仅允许使用中英文字符以及数字");
            }
            stringBuilder.append(var);
            if (i < vars.length - 1) {
                //直到最后一个前,都以_作为分割线
                stringBuilder.append(DELIMITER);
            }
        }
        if (stringBuilder.toString().matches("^\\d_*$")) {
            throw new ServiceException("禁止以数字开头");
        }
        return stringBuilder.toString();
    }
}
