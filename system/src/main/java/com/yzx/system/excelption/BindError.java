package com.yzx.system.excelption;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @className: BindError
 * @author: yzx
 * @date: 2025/10/10 15:22
 * @Version: 1.0
 * @description:
 */
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
}
