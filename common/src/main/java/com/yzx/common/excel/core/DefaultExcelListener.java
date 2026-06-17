package com.yzx.common.excel.core;

import cn.hutool.core.util.StrUtil;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import cn.idev.excel.exception.ExcelAnalysisException;
import cn.idev.excel.exception.ExcelDataConvertException;
import com.yzx.common.utils.StreamUtils;
import com.yzx.common.utils.ValidatorUtils;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Map;
import java.util.Set;

/**
 * @className: DefaultExcelListener
 * @author: yzx
 * @date: 2026/6/17 17:23
 * @Version: 1.0
 * @description:
 */
@Slf4j
@NoArgsConstructor
public class DefaultExcelListener<T> extends AnalysisEventListener<T> implements ExcelListener<T> {
    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        String errMsg = null;
        // 1. Excel单元格转换异常 JDK8 去除模式匹配
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException excelDataConvertException = (ExcelDataConvertException) exception;
            // 如果是某一个单元格的转换异常 能获取到具体行号
            Integer rowIndex = excelDataConvertException.getRowIndex();
            Integer columnIndex = excelDataConvertException.getColumnIndex();
            errMsg = StrUtil.format("第{}行-第{}列-表头{}: 解析异常<br/>",
                    rowIndex + 1, columnIndex + 1, headMap.get(columnIndex));
            if (log.isDebugEnabled()) {
                log.error(errMsg);
            }
        }
        // 2. JSR303参数校验异常 同样去除模式匹配
        if (exception instanceof ConstraintViolationException) {
            ConstraintViolationException constraintViolationException = (ConstraintViolationException) exception;
            Set<ConstraintViolation<?>> constraintViolations = constraintViolationException.getConstraintViolations();
            String constraintViolationsMsg = StreamUtils.join(constraintViolations, ConstraintViolation::getMessage, ", ");
            errMsg = StrUtil.format("第{}行数据校验异常: {}", context.readRowHolder().getRowIndex() + 1, constraintViolationsMsg);
            if (log.isDebugEnabled()) {
                log.error(errMsg);
            }
        }
        excelResult.getErrorList().add(errMsg);
        throw new ExcelAnalysisException(errMsg);
    }

    private Boolean isValidate = Boolean.TRUE;
    private Map<Integer, String> headMap;
    private ExcelResult<T> excelResult;

    public DefaultExcelListener(boolean isValidate) {
        this.excelResult = new DefaultExcelResult();
        this.isValidate = isValidate;
    }

    @Override
    public ExcelResult<T> getExcelResult() {
        return excelResult;
    }

    @Override
    public void invoke(T t, AnalysisContext analysisContext) {
        if (isValidate) {
            ValidatorUtils.validate(t);
        }
        excelResult.getList().add(t);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        log.debug("所有数据解析完成!");
    }
}
