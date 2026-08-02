package com.yzx.common.utils;

import com.yzx.model.utils.SpringUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.redisson.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    public static long rateLimiter(String key, RateType rateType, int rate, RateIntervalUnit rateInterval) {
        return rateLimiter(key, rateType, rate, rateInterval);
    }

    /**
     * 限流（老版Redisson trySetRate 参数顺序：rate, rateInterval, rateType, timeout）
     *
     * @param key          限流key
     * @param rateType     限流类型
     * @param rate         速率
     * @param rateInterval 速率间隔
     * @param rateIntervalUnit      超时时间
     * @return -1 表示失败
     */
    public static long rateLimiter(String key, RateType rateType, int rate, int rateInterval, RateIntervalUnit rateIntervalUnit) {
        RRateLimiter rateLimiter = CLIENT.getRateLimiter(key);
        // 老版本 trySetRate 参数顺序：rate, rateInterval, rateType, waitTimeout
        rateLimiter.trySetRate(rateType, rate, rateInterval, rateIntervalUnit);
        if (rateLimiter.tryAcquire()) {
            return rateLimiter.availablePermits();
        } else {
            return -1;
        }
    }

    /**
     * 获取客户端实例
     * @return RedissonClient
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

    /**
     * 订阅频道消息
     */
    public static <T> void subscribe(String channelKey, Class<T> clazz, Consumer<T> consumer) {
        RTopic topic = CLIENT.getTopic(channelKey);
        topic.addListener(clazz, (channel, msg) -> consumer.accept(msg));
    }

    // ===================== String 缓存 =====================
    public static <T> void setCacheObject(final String key, final T value) {
        setCacheObject(key, value, false);
    }

    public static <T> void setCacheObject(final String key, final T value, final boolean isSaveTtl) {
        RBucket<Object> bucket = CLIENT.getBucket(key);
        if (isSaveTtl) {
            long timeToLive = bucket.remainTimeToLive();
            if (timeToLive == -1) {
                bucket.set(value);
            } else {
                bucket.set(value);
            }
        } else {
            bucket.set(value);
        }
    }

    public static <T> void setCacheObject(final String key, final T value, final Duration duration) {
        RBucket<Object> bucket = CLIENT.getBucket(key);
        bucket.set(value, duration.toMillis(), TimeUnit.SECONDS);
    }

    public static <T> boolean setObjectIfAbsent(final String key, final T value, final Duration duration) {
        RBucket<T> bucket = CLIENT.getBucket(key);
        return bucket.setIfExists(value, duration.toMinutes(), TimeUnit.SECONDS);
    }

    public static <T> boolean setObjectIfExists(final String key, final T value, final Duration duration) {
        RBucket<T> bucket = CLIENT.getBucket(key);
        return bucket.setIfExists(value, duration.toMillis(), TimeUnit.SECONDS);
    }

    /**
     * 注册对象监听器
     * <p>
     * key 监听器需开启 `notify-keyspace-events` 等 redis 相关配置
     *
     * @param key      缓存的键值
     * @param listener 监听器配置
     */
    public static <T> void addObjectListener(final String key, final ObjectListener listener) {
        RBucket<T> result = CLIENT.getBucket(key);
        result.addListener(listener);
    }

    /**
     * 设置有效时间
     *
     * @param key     Redis键
     * @param timeout 超时时间 秒
     * @return true=设置成功；false=设置失败
     */
    public static boolean expire(final String key, final long timeout) {
        return expire(key, Duration.ofSeconds(timeout));
    }

    /**
     * 设置有效时间
     *
     * @param key      Redis键
     * @param duration 超时时间
     * @return true=设置成功；false=设置失败
     */
    public static boolean expire(final String key, final Duration duration) {
        RBucket rBucket = CLIENT.getBucket(key);
        return rBucket.expire(duration.toMillis(), TimeUnit.SECONDS);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public static <T> T getCacheObject(final String key) {
        RBucket<T> rBucket = CLIENT.getBucket(key);
        return rBucket.get();
    }

    /**
     * 获得key剩余存活时间
     *
     * @param key 缓存键值
     * @return 剩余存活时间，-1永久，-2不存在
     */
    public static long getTimeToLive(final String key) {
        RBucket<?> rBucket = CLIENT.getBucket(key);
        return rBucket.remainTimeToLive();
    }

    /**
     * 删除单个对象
     *
     * @param key 缓存的键值
     */
    public static boolean deleteObject(final String key) {
        return CLIENT.getBucket(key).delete();
    }

    /**
     * 删除多个key（老版本不推荐大量key批量，无deleteAsync批量）
     *
     * @param collection 多个key集合
     */
    public static void deleteObject(final Collection<?> collection) {
        for (Object obj : collection) {
            String key = String.valueOf(obj);
            CLIENT.getBucket(key).delete();
        }
    }

    /**
     * 检查缓存对象是否存在
     *
     * @param key 缓存的键值
     */
    public static boolean isExistsObject(final String key) {
        return CLIENT.getBucket(key).isExists();
    }

    // ===================== List 缓存 =====================
    public static <T> boolean setCacheList(final String key, final List<T> dataList) {
        RList<T> rList = CLIENT.getList(key);
        return rList.addAll(dataList);
    }

    public static <T> boolean addCacheList(final String key, final T data) {
        RList<T> rList = CLIENT.getList(key);
        return rList.add(data);
    }

    public static <T> void addListListener(final String key, final ObjectListener listener) {
        RList<T> rList = CLIENT.getList(key);
        rList.addListener(listener);
    }

    public static <T> List<T> getCacheList(final String key) {
        RList<T> rList = CLIENT.getList(key);
        return rList.readAll();
    }

    public static <T> List<T> getCacheListRange(final String key, int form, int to) {
        RList<T> rList = CLIENT.getList(key);
        return rList.range(form, to);
    }

    // ===================== Set 缓存 =====================
    public static <T> boolean setCacheSet(final String key, final Set<T> dataSet) {
        RSet<T> rSet = CLIENT.getSet(key);
        return rSet.addAll(dataSet);
    }

    public static <T> boolean addCacheSet(final String key, final T data) {
        RSet<T> rSet = CLIENT.getSet(key);
        return rSet.add(data);
    }

    public static <T> void addSetListener(final String key, final ObjectListener listener) {
        RSet<T> rSet = CLIENT.getSet(key);
        rSet.addListener(listener);
    }

    public static <T> Set<T> getCacheSet(final String key) {
        RSet<T> rSet = CLIENT.getSet(key);
        return rSet.readAll();
    }

    // ===================== Hash(Map) 缓存 =====================
    public static <T> void setCacheMap(final String key, final Map<String, T> dataMap) {
        if (dataMap != null && !dataMap.isEmpty()) {
            RMap<String, T> rMap = CLIENT.getMap(key);
            rMap.putAll(dataMap);
        }
    }

    public static <T> void addMapListener(final String key, final ObjectListener listener) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        rMap.addListener(listener);
    }

    public static <T> Map<String, T> getCacheMap(final String key) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.getAll(rMap.keySet());
    }

    public static <T> Set<String> getCacheMapKeySet(final String key) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.keySet();
    }

    public static <T> void setCacheMapValue(final String key, final String hKey, final T value) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        rMap.put(hKey, value);
    }

    public static <T> T getCacheMapValue(final String key, final String hKey) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.get(hKey);
    }

    public static <T> T delCacheMapValue(final String key, final String hKey) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        return rMap.remove(hKey);
    }

    /**
     * 批量删除hash字段（老版本无RMapAsync，循环删除）
     */
    public static <T> void delMultiCacheMapValue(final String key, final Set<String> hKeys) {
        RMap<String, T> rMap = CLIENT.getMap(key);
        for (String hKey : hKeys) {
            rMap.remove(hKey);
        }
    }

    public static <K, V> Map<K, V> getMultiCacheMapValue(final String key, final Set<K> hKeys) {
        RMap<K, V> rMap = CLIENT.getMap(key);
        return rMap.getAll(hKeys);
    }

    // ===================== 原子计数器 =====================
    public static void setAtomicValue(String key, long value) {
        RAtomicLong atomic = CLIENT.getAtomicLong(key);
        atomic.set(value);
    }

    public static long getAtomicValue(String key) {
        RAtomicLong atomic = CLIENT.getAtomicLong(key);
        return atomic.get();
    }

    public static long incrAtomicValue(String key) {
        RAtomicLong atomic = CLIENT.getAtomicLong(key);
        return atomic.incrementAndGet();
    }

    public static long decrAtomicValue(String key) {
        RAtomicLong atomic = CLIENT.getAtomicLong(key);
        return atomic.decrementAndGet();
    }

    // ===================== Key 扫描操作（移除KeysScanOptions 高版本API） =====================

    /**
     * 模糊匹配key（老版本scan迭代器，防止keys命令阻塞redis）
     * @param pattern 匹配前缀 *xxx* / xxx*
     * @return key集合
     */
    public static Collection<String> keys(final String pattern) {
        RKeys rKeys = CLIENT.getKeys();
        Iterable<String> keysByPattern = rKeys.getKeysByPattern(pattern, 1000);
        List<String> list = new ArrayList<>();
        while (keysByPattern.iterator().hasNext()) {
            list.add(keysByPattern.iterator().next());
        }
        return list;
    }

    /**
     * 根据前缀批量删除key
     * @param pattern key匹配规则
     */
    public static void deleteKeys(final String pattern) {
        CLIENT.getKeys().deleteByPattern(pattern);
    }

    /**
     * 判断key是否存在
     * @param key 缓存key
     * @return true存在
     */
    public static Boolean hasKey(String key) {
        return CLIENT.getKeys().countExists(key) > 0;
    }
}