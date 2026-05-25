package com.yzx.system.aspect;

import com.yzx.model.exception.ServiceException;
import com.yzx.model.utils.SecurityUtils;
import com.yzx.model.utils.UserUtils;
import com.yzx.system.annotation.RequiresPermission;
import com.yzx.system.service.ISysPermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 权限控制切面
 * 用于拦截标记了@RequiresPermission注解的方法，检查用户是否有相应的权限
 *
 * @author ruoyi
 */
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private ISysPermissionService permissionService;

    /**
     * 定义切点：拦截所有标记了@RequiresPermission注解的方法
     */
    @Pointcut("@annotation(com.yzx.system.annotation.RequiresPermission)")
    public void permissionPointCut() {
    }

    /**
     * 环绕通知：检查权限
     */
    @Around("permissionPointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前用户ID
        Long userId = SecurityUtils.getUserId();

        // 管理员拥有所有权限，直接放行
        if (UserUtils.isAdmin(userId)) {
            return joinPoint.proceed();
        }

        // 获取方法上的注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        // 获取需要的权限
        String requiredPermission = annotation.value();

        // 获取用户拥有的权限
        Set<String> permissions = permissionService.getMenuPermission(userId);

        // 检查用户是否有相应的权限
        if (!permissions.contains(requiredPermission)) {
            throw new ServiceException("无权限操作，请联系管理员");
        }

        // 放行
        return joinPoint.proceed();
    }
}
