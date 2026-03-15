package com.yzx.product.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @className: EsConfig
 * @author: yzx
 * @date: 2025/9/18 13:10
 * @Version: 1.0
 * @description:
 */
@Configuration
public class EsConfig {

    // 从yml读取配置
    @Value("${spring.elasticsearch.rest.uris}")
    private String esUris;

    public static final RequestOptions COMMON_OPTIONS;

    static {
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        COMMON_OPTIONS = builder.build();
    }

    @Bean
    public RestHighLevelClient esRestClient() {
        // 解析yml中的地址，不硬编码
        String[] parts = esUris.replace("http://", "").split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        return new RestHighLevelClient(
                RestClient.builder(new org.apache.http.HttpHost(host, port, "http"))
        );
    }
}
