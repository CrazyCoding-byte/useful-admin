package yzx.iot.test.func;

import java.util.function.Consumer;

/**
 * @className: User
 * @author: yzx
 * @date: 2026/10/1 23:16
 * @Version: 1.0
 * @description:
 */
public class User {
    private Consumer<String> consumer;

    public void SetConsumer(Consumer<String> consumer) {
        this.consumer = consumer;
    }

    public void doSomething(String msg) {
        System.out.println("逻辑开始");
        if (consumer != null) {
            consumer.accept(msg);
        }
        System.out.println("逻辑结束");
    }

    public static void main(String[] args) {
        User user = new User();
        user.SetConsumer(new Consumer<String>() {

            @Override
            public void accept(String s) {
                System.out.println("【Consumer执行】收到消息：" + s);
            }
        });
        user.doSomething("测试消息");
    }
}
