package yzx.iot.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * @className: MyAnnotation
 * @author: yzx
 * @date: 2026/9/26 20:58
 * @Version: 1.0
 * @description:
 */
@Target(ElementType.METHOD)
public @interface MyAnnotation {
    String value() default "1";

    int num();

    String[] arr() default {};
}
