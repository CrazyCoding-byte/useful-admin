package com.yzx.model.annotation;

import java.lang.annotation.*;

/**
 * 数据列配置注解
 * 
 * @author ruoyi
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataColumn {
    
    /**
     * SpEL表达式中的变量名数组
     * 例如：["deptColumn", "userColumn"]
     */
    String[] key() default {"deptColumn"};
    
    /**
     * 对应的数据库字段名数组
     * 例如：["dept_id", "user_id"]
     */
    String[] value() default {"dept_id"};
    
    /**
     * 权限标识符（拥有此权限的角色不进行该列的过滤）
     */
    String permission() default "";
}
