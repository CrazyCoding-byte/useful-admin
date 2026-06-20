package com.yzx.model.annotation;

import java.lang.annotation.*;

/**
 * 数据权限组注解
 * 
 * @author ruoyi
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {
    
    /**
     * 数据列配置数组
     */
    DataColumn[] value();
    
    /**
     * 条件连接符（默认查询用OR，更新删除用AND）
     */
    String joinStr() default "";
}
