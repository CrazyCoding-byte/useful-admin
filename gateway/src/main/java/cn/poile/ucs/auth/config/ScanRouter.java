package cn.poile.ucs.auth.config;

import cn.poile.ucs.auth.handler.ScanCodeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * @className: RouterFunctionConfiguration
 * @author: yzx
 * @date: 2026/3/27 15:44
 * @Version: 1.0
 * @description:
 */
@Configuration
public class ScanRouter {

    @Bean
    public RouterFunction<ServerResponse> scanRoute(ScanCodeHandler handler) {
        return RouterFunctions.route()
                // 生成二维码
                .GET("/gateway/scan/create", RequestPredicates.accept(MediaType.ALL), handler::create)
                // 小程序确认
                .POST("/gateway/scan/confirm", RequestPredicates.accept(MediaType.ALL), handler::confirm)
                // 轮询状态
                .GET("/gateway/scan/status", RequestPredicates.accept(MediaType.ALL), handler::status)
                .build();
    }
}
