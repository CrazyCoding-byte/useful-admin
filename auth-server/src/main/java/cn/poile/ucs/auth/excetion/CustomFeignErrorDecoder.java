package cn.poile.ucs.auth.excetion;

import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 自定义Feign异常解码器
 */
@Component
public class CustomFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = "远程调用失败";
        try {
            // 读取响应体的错误信息
            if (response.body() != null) {
                message = Util.toString(response.body().asReader(Util.UTF_8));
            }
        } catch (IOException e) {
            message = "读取远程调用错误信息失败：" + e.getMessage();
        }

        // 根据状态码返回不同的业务异常
        switch (response.status()) {
            case 401:
                return new BusinessException("令牌已过期/无效，请重新登录", 401);
            case 403:
                return new BusinessException("没有权限访问远程服务", 403);
            case 404:
                return new BusinessException("远程服务接口不存在", 404);
            case 500:
                return new BusinessException("远程服务内部错误：" + message, 500);
            default:
                return defaultErrorDecoder.decode(methodKey, response);
        }
    }

    // 自定义业务异常（你也可以用项目中已有的异常类）
    public static class BusinessException extends RuntimeException {
        private int code;

        public BusinessException(String message, int code) {
            super(message);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}