package com.yzx.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 通用异步线程池（重试任务专用，高性能）
 */
@EnableAsync
@Configuration
public class TaskExecutorConfig {

    @Bean("messageRetryExecutor")
    public Executor messageRetryExecutor() {
        // 核心线程数：根据服务器CPU核数设置
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("msg-retry-");
        // 拒绝策略：由调用者线程执行（即同步执行）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}