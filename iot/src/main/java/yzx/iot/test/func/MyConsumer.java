package yzx.iot.test.func;

/**
 * @className: MyConsumer
 * @author: yzx
 * @date: 2026/9/26 21:05
 * @Version: 1.0
 * @description:
 */
@FunctionalInterface
public interface MyConsumer<T> {
    void accpet(T t);
}
