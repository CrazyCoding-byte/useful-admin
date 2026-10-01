package yzx.iot.test.func;

/**
 * @className: MyBiFunc
 * @author: yzx
 * @date: 2026/9/26 21:38
 * @Version: 1.0
 * @description:
 */
@FunctionalInterface
public interface MyBiFunc<T, U, R> {
    R apply(T t, U u);
}
