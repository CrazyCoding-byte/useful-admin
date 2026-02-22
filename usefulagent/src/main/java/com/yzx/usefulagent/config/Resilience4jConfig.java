package com.yzx.usefulagent.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;

/**
 * @className: Resilience4jConfig
 * @author: yzx
 * @date: 2026/2/22 22:12
 * @Version: 1.0
 * @description:
 */
@Configuration
public class Resilience4jConfig {
    @Resource
    private CircuitBreakerRegistry registry;

    @PostConstruct
    public void init() {
        registry.circuitBreaker("ollamaCircuitBreaker");
    }
}
