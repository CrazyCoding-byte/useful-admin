package com.yzx.common.utils;

import com.yzx.model.utils.SpringUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.redisson.api.*;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * @className: RedisUtils
 * @author: yzx
 * @date: 2026/6/21 15:48
 * @Version: 1.0
 * @description:
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisUtils {
    private static final RedissonClient CLIENT = SpringUtils.getBean(RedissonClient.class);

    /**
     * 限流
     * @param key  限流key
     * @param rateType 限流类型
     * @param rate  速率
     * @param rateInterval 速率间隔
     * @return -1 表示失败
     */
    public static final long rateLimiter(String key, RateType rateType, int rate, int rateInterval) {
        return rate
    }

    /**
     * 限流
     *
     * @param key          限流key
     * @param rateType     限流类型
     * @param rate         速率
     * @param rateInterval 速率间隔
     * @param timeout      超时时间
     * @return -1 表示失败
     */
    public static final long rateLimiter(String key, RateType rateType, int rate, int rateInterval, int timeout) {
        RRateLimiter rateLimiter = CLIENT.getRateLimiter(key);
        rateLimiter.trySetRate(rateType, rate, Duration.ofSeconds(rateInterval), Duration.ofSeconds(timeout));
        if (rateLimiter.tryAcquire()) {
            return rateLimiter.availablePermits();
        } else {
            return -1;
        }
    }

    /**
     *获取客户端实例
     * @return
     */
    public static RedissonClient getClient() {
        return CLIENT;
    }

    /**
     * 发布通道消息
     *
     * @param channelKey 通道key
     * @param msg        发送数据
     * @param consumer   自定义处理
     */
    public static <T> void publish(String channelKey, T msg, Consumer<T> consumer) {
        RTopic topic = CLIENT.getTopic(channelKey);
        topic.publish(msg);
        consumer.accept(msg);
    }

    /**
     * 发布消息到指定的频道
     *
     * @param channelKey 通道key
     * @param msg        发送数据
     */
    public static <T> void publish(String channelKey, T msg) {
        RTopic topic = CLIENT.getTopic(channelKey);
        topic.publish(msg);
    }

    public static <T> void subscribe(String channelKey, Class<T> clazz, Consumer<T> consumer) {
        RTopic topic = CLIENT.getTopic(channelKey);
        topic.addListener(clazz, (channel, msg) -> consumer.accept(msg));
    }

    public static <T> void setCacheObject(final String key, final T value {
        setCacheObject(key, value, false);
    }

    public static <T> void setCacheObject(final String key, final T value, final boolean isSaveTtl) {
        RBucket<Object> bucket = CLIENT.getBucket(key);
        if (isSaveTtl) {
            try {
                bucket.setAndKeepTTL(value);
            } catch (Exception e) {
                long timeToLive = bucket.remainTimeToLive();
                if (timeToLive == -1) {
                    bucket.set(value);
                } else {
                    bucket.set(value, Duration.ofMillis(timeToLive));
                }
            }
        } else {
            bucket.set(value);
        }
    }
}
