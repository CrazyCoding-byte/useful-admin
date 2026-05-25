package com.yzx.common.permission.aspect;

import com.yzx.common.permission.helper.DataPermissionHelper;
import com.yzx.model.annotation.DataPermission;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @className: DataPermissionAdvice
 * @author: yzx
 * @date: 2026/5/24 15:10
 * @Version: 1.0
 * @description:
 */
public class DataPermissionAdvice implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        Object target = methodInvocation.getThis();
        Method method = methodInvocation.getMethod();
        Object[] args = methodInvocation.getArguments();
        DataPermissionHelper.setPermissionCache(getAnnotation(target, method));
        try {
            return methodInvocation.proceed();
        } finally {
            DataPermissionHelper.removePermissionCache();
        }
    }

    private DataPermission getAnnotation(Object target, Method method) {
        DataPermission dp = method.getAnnotation(DataPermission.class);
        if (dp != null) return dp;
        Class<?> clazz = target.getClass();
        if (Proxy.isProxyClass(clazz)) {
            clazz = clazz.getInterfaces()[0];
        }
        return clazz.getAnnotation(DataPermission.class);
    }
}
