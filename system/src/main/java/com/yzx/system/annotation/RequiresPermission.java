package com.yzx.system.annotation;

import java.lang.annotation.*;

/**
 * 权限注解
 * 用于标记需要权限控制的方法
 * 
 * @author ruoyi
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {
    /**
     * 权限代码
     * 例如：system:user:list
     */
    String value();
    
    /**
     * 权限描述
     */
    String description() default "";
}
