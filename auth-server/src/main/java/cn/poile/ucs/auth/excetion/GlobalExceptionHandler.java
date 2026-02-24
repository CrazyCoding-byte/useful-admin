package cn.poile.ucs.auth.excetion;

import com.yzx.model.AjaxResult;
import com.yzx.model.exception.CustomException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * @className: GlobalExceptionHandler
 * @author: yzx
 * @date: 2026/2/24 21:30
 * @Version: 1.0
 * @description:
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 处理Feign调用的业务异常
     */
    @ExceptionHandler(CustomFeignErrorDecoder.BusinessException.class)
    public AjaxResult handleFeignBusinessException(CustomFeignErrorDecoder.BusinessException e) {
        log.error("Feign远程调用异常：", e);
        return AjaxResult.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理其他Feign异常
     */
    @ExceptionHandler(FeignException.class)
    public AjaxResult handleFeignException(FeignException e) {
        log.error("Feign远程调用异常：", e);
        return AjaxResult.error("远程服务调用失败：" + e.getMessage());
    }

    @ExceptionHandler(CustomException.class)
    public AjaxResult handleCustomException(CustomException e) {
        return AjaxResult.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e) {
        return AjaxResult.error("服务器内部错误:" + e.getMessage());
    }
}
