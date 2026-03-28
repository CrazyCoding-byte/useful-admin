package cn.poile.ucs.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * @className: RedisConfig
 * @author: yzx
 * @date: 2026/3/27 18:16
 * @Version: 1.0
 * @description:
 */
@Configuration
public class RedisConfig {
    @Bean
    public ReactiveRedisTemplate<String,String> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory){
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
    }
}
