package com.yzx.coupon.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @className: RabbitMQConfig
 * @author: yzx
 * @date: 2026/7/23 17:50
 * @Version: 1.0
 * @description:
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue couponObtainQueue() {
        return new Queue("coupon.obtain.queue", true);
    }
}