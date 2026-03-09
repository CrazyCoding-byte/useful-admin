package com.yzx.product.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @className: RabbitProductConfig
 * @author: yzx
 * @date: 2026/3/9 9:31
 * @Version: 1.0
 * @description:
 */
@Configuration
public class RabbitProductConfig {
    public static final String PRODUCT_UP_EXCHANGE = "product.up.exchange";
    public static final String PRODUCT_UP_QUEUE = "product.up.queue";
    public static final String PRODUCT_UP_ROUTING_KEY = "product.up";

    @Bean
    public DirectExchange productUpExchange() {
        return new DirectExchange(PRODUCT_UP_EXCHANGE, true, false);
    }

    @Bean
    public Queue productUpQueue() {
        return new Queue(PRODUCT_UP_QUEUE, true, false, false);
    }

    @Bean
    public Binding productUpBinding() {
        return BindingBuilder.bind(productUpQueue()).to(productUpExchange()).with(PRODUCT_UP_ROUTING_KEY);
    }
}
