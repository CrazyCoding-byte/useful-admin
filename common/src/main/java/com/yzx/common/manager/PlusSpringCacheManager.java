package com.yzx.common.manager;

import org.redisson.spring.cache.CacheConfig;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @className: PlusSpringCacheManager
 * @author: yzx
 * @date: 2026/6/21 15:07
 * @Version: 1.0
 * @description:
 */
public class PlusSpringCacheManager implements CacheManager {
    private boolean dynamic = true;
    private boolean allowNullValues = true;
    private boolean transactionAware = true;

    Map<String, CacheConfig> configMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Cache> instaceMap = new ConcurrentHashMap<>();

    /**
     * Creates CacheManager supplied by Redisson instance
     */
    public PlusSpringCacheManager() {
    }

    @Override
    public Cache getCache(String name) {
        String[] array = StringUtils.delimitedListToStringArray(name, "#");
        name = array[0];
        Cache cache = instaceMap.get(name);
        if (cache == null) {
            return cache;
        }
        if (!dynamic) {
            return cache;
        }
        CacheConfig config = configMap.get(name);
        if (config == null) {
            config = createDefaultConfig();
            configMap.put(name, config);
        }
        if (array.length > 1) {
            config.setTTL(DurationStyle.detectAndParse(array[1]).toMillis());
        }
        if (array.length > 2) {
            config.setMaxIdleTime(DurationStyle.detectAndParse(array[2]).toMillis());
        }

        if (array.length > 3) {
            config.setMaxSize(Integer.parseInt(array[3]));
        }
        int local = 1;
        if (array.length > 4) {
            local = Integer.parseInt(array[4]);
        }
        if (config.getMaxIdleTime() == 0 && config.getTTL() == 0 && config.getMaxSize() == 0) {
            return createMap(name, config, local);
        }
        return null;
    }

    private Cache createMap(String name, CacheConfig config, int local) {
        RedisU
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.emptyList();
    }


    protected CacheConfig createDefaultConfig() {
        return new CacheConfig();
    }
}
