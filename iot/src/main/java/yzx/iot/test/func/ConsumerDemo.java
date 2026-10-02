package yzx.iot.test.func;

import java.util.function.Consumer;

/**
 * @className: ConsumerDemo
 * @author: yzx
 * @date: 2026/10/1 23:02
 * @Version: 1.0
 * @description:
 */
public class ConsumerDemo {
    public static void main(String[] args) {
        Consumer<String> consumer = new Consumer<String>() {

            @Override
            public void accept(String s) {
                System.out.println("消费" + s);
            }

        };
        consumer.accept("Hello Consumer");
    }
}
