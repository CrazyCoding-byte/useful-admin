package com.yzx.system.excelption;

import com.yzx.model.AjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @className: BindError
 * @author: yzx
 * @date: 2025/10/10 15:22
 * @Version: 1.0
 * @description:
 */
@Slf4j
@ControllerAdvice
public class BindError {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValueDationException(MethodArgumentNotValidException ex) {
        Map<String, String> errorMap = ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
                fieldError -> fieldError.getField(),
                fieldError -> fieldError.getDefaultMessage()));
        return errorMap;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AjaxResult handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.error("请求体解析失败: {}", ex.getMessage());
        String detail = ex.getMessage();
        if (detail != null) {
            if (detail.contains("JSON parse error")) {
                return AjaxResult.error("请求体JSON格式错误: " + detail.substring(0, Math.min(detail.length(), 200)));
            } else if (detail.contains("unrecognized field")) {
                return AjaxResult.error("请求体包含未知字段");
            } else if (detail.contains("Cannot deserialize")) {
                return AjaxResult.error("请求体字段类型错误: " + detail.substring(0, Math.min(detail.length(), 200)));
            }
        }
        return AjaxResult.error("请求参数解析失败: " + (detail != null ? detail.substring(0, 100) : ""));
    }
}
