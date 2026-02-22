package com.yzx.usefulagent.exception;

import com.yzx.model.AjaxResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @className: GlobalExceptionHandler
 * @author: yzx
 * @date: 2026/2/22 20:47
 * @Version: 1.0
 * @description:
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e) {
        e.printStackTrace();
        return AjaxResult.error();
    }
}
