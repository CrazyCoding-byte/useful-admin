package yzx.iot.test.func;

/**
 * @className: MyFunction
 * @author: yzx
 * @date: 2026/9/26 21:38
 * @Version: 1.0
 * @description:
 */
@FunctionalInterface
public interface MyFunction<T, R> {
    R apply(T t);
}
