package com.yzx.common.tenant;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 租户上下文管理
 * 使用 ThreadLocal 存储当前线程的租户ID
 * 
 * @author ruoyi
 */
@Slf4j
public class TenantContext {
    
    private static final ThreadLocal<String> TENANT_HOLDER = new ThreadLocal<>();
    
    /**
     * 获取当前租户ID
     */
    public static String getCurrentTenantId() {
        return TENANT_HOLDER.get();
    }
    
    /**
     * 设置当前租户ID
     */
    public static void setCurrentTenantId(String tenantId) {
        if (tenantId != null) {
            TENANT_HOLDER.set(tenantId);
            log.debug("设置当前租户ID: {}", tenantId);
        }
    }
    
    /**
     * 清除租户ID
     */
    public static void clear() {
        TENANT_HOLDER.remove();
        log.debug("清除租户上下文");
    }
    
    /**
     * 在指定租户中执行
     */
    public static <T> T runWithTenant(String tenantId, Supplier<T> action) {
        String oldTenantId = getCurrentTenantId();
        try {
            setCurrentTenantId(tenantId);
            return action.get();
        } finally {
            if (oldTenantId != null) {
                setCurrentTenantId(oldTenantId);
            } else {
                clear();
            }
        }
    }
    
    /**
     * 忽略租户过滤执行（用于系统级操作）
     */
    public static <T> T ignoreTenant(Supplier<T> action) {
        String oldTenantId = getCurrentTenantId();
        try {
            clear(); // 清除租户ID表示忽略租户
            return action.get();
        } finally {
            if (oldTenantId != null) {
                setCurrentTenantId(oldTenantId);
            }
        }
    }
}
