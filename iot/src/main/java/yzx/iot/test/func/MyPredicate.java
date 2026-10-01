package yzx.iot.test.func;

/**
 * @className: MyPredicate
 * @author: yzx
 * @date: 2026/9/26 21:58
 * @Version: 1.0
 * @description:
 */
@FunctionalInterface
public interface MyPredicate<T> {
    boolean test(T t);
}
