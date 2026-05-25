package com.yzx.common.permission.aspect;

import com.yzx.model.annotation.DataPermission;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @className: DataPermissionPointcut
 * @author: yzx
 * @date: 2026/5/24 15:07
 * @Version: 1.0
 * @description:
 */
public class DataPermissionPointcut extends StaticMethodMatcherPointcut {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (method.isAnnotationPresent(DataPermission.class)) return true;
        Class<?> clazz = targetClass;
        if (Proxy.isProxyClass(clazz)) {
            clazz = clazz.getInterfaces()[0];
        }
        return clazz.isAnnotationPresent(DataPermission.class);
    }
}
