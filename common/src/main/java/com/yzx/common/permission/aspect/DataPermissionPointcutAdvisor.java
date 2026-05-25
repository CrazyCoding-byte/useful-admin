package com.yzx.common.permission.aspect;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;

/**
 * @className: DataPermissionPointcutAdvisor
 * @author: yzx
 * @date: 2026/5/24 15:06
 * @Version: 1.0
 * @description:
 */
public class DataPermissionPointcutAdvisor extends AbstractPointcutAdvisor {
    private final Advice advice = new DataPermissionAdvice();
    private final Pointcut pointcut = new DataPermissionPointcut();

    @Override
    public Pointcut getPointcut() {
        return this.pointcut;
    }

    @Override
    public Advice getAdvice() {
        return this.advice;
    }
}
