package com.yzx.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @className: RabbitmqConfig
 * @author: yzx
 * @date: 2025/10/10 18:35
 * @Version: 1.0
 * @description:
 */
@Configuration
public class RabbitMQConfig {

    // 用户注册相关交换机和队列
    public static final String USER_EXCHANGE = "user.exchange";
    public static final String USER_REGISTERED_QUEUE = "user.registered.queue";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";

    // 定义直连交换机
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE, true, false);
    }

    // 定义用户注册队列
    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(USER_REGISTERED_QUEUE, true, false, false);
    }

    // 绑定队列到交换机
    @Bean
    public Binding bindingUserRegistered(Queue userRegisteredQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userRegisteredQueue)
                .to(userExchange)
                .with(USER_REGISTERED_ROUTING_KEY);
    }

    // 配置JSON消息转换器
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}