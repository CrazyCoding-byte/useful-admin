package com.yzx.common.permission.helper;

import com.yzx.model.annotation.DataPermission;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * @className: DataPermissionHelper
 * @author: yzx
 * @date: 2026/5/24 12:08
 * @Version: 1.0
 * @description:
 */
public class DataPermissionHelper {
    private static final ThreadLocal<DataPermission> PERMISSION_CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> CONTEXT = ThreadLocal.withInitial(() -> new HashMap<>());


    public static DataPermission getPermission() {
        return PERMISSION_CACHE.get();
    }

    public static void setPermissionCache(DataPermission dataPermission) {
        PERMISSION_CACHE.set(dataPermission);
    }

    public static void removePermissionCache() {
        PERMISSION_CACHE.remove();
    }

    public static <T> T getVariable(String key) {
        Map<String, Object> context = CONTEXT.get();
        return (T) context.get(key);
    }

    public static void setVariable(String key, Object value) {
        CONTEXT.get().put(key, value);
    }

    public static Map<String, Object> getContext() {
        return CONTEXT.get();
    }

    public static void clearContext() {
        CONTEXT.remove();
    }
}
