package com.yzx.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @className: RestConfig
 * @author: yzx
 * @date: 2025/9/7 8:40
 * @Version: 1.0
 * @description:
 */
@Configuration
public class RestConfig {
    @Bean
    public RestTemplate restTemplate() {
        // 包装请求工厂，缓存请求体使其可重复读取（核心修复点）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 设置超时时间，避免请求挂起
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        BufferingClientHttpRequestFactory bufferingFactory = new BufferingClientHttpRequestFactory(factory);

        RestTemplate restTemplate = new RestTemplate(bufferingFactory);
        return restTemplate;
    }
}
