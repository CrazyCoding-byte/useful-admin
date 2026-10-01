package yzx.iot.test.func;

import java.util.Arrays;
import java.util.List;

/**
 * @className: FunTest
 * @author: yzx
 * @date: 2026/9/26 20:01
 * @Version: 1.0
 * @description:
 */
public class FunTest {
    public static void main(String[] args) {
//        FunTest.execute(() -> {
//            System.out.println("hello");
//        });
//        FunTest.execute(new MyTest() {
//            @Override
//            public void run() {
//                System.out.println("hello");
//            }
//        });
        FunTest.execute("Hello1", s -> System.out.println(s.toUpperCase()));
        FunTest.execute("hello", new MyConsumer<String>() {
            @Override
            public void accpet(String s) {
                System.out.println(s.toUpperCase());
            }
        });
        FunTest.convert("123", str -> {
            return Integer.parseInt(str);
        });
        FunTest.convert("123", new MyFunction<String, Integer>() {

            @Override
            public Integer apply(String s) {
                return Integer.parseInt(s);
            }
        });
        FunTest.convert("123", "234", (str1, str2) -> {
            return Integer.parseInt(str1) + Integer.parseInt(str2);
        });
        FunTest.convert("123", "234", new MyBiFunc<String, String, Integer>() {
            @Override
            public Integer apply(String s, String s2) {
                return Integer.parseInt(s2) + Integer.parseInt(s);
            }
        });
        List<String> list = Arrays.asList("a", "b", "c");
        MyPredicate<String> predicate = new StringLengthPredicate();

    }

    public static void execute(MyTest task) {
        System.out.println("前置逻辑");
        task.run();
        System.out.println("后置逻辑");
    }

    public static <T> void execute(T data, MyConsumer<T> consumer) {
        System.out.println("前置处理");
        consumer.accpet(data);
    }

    public static <T, R> R convert(T data, MyFunction<T, R> myFunction) {
        System.out.println("前置处理");
        return myFunction.apply(data);
    }

    public static <T, U, R> R convert(T data1, U data2, MyBiFunc<T, U, R> myBiFunc) {
        System.out.println("前置处理");
        return myBiFunc.apply(data1, data2);
    }
}
