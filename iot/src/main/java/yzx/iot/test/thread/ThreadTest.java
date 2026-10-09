package yzx.iot.test.thread;

import java.util.concurrent.*;

/**
 * @className: ThreadTest
 * @author: yzx
 * @date: 2026/10/9 16:18
 * @Version: 1.0
 * @description:
 */
public class ThreadTest {
    public static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) throws InterruptedException {
        testScheduledFuture();
    }

    public void testScheduleFuture() throws InterruptedException {
        //延迟delay时间后执行一次任务
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            System.out.println("3s到了,执行超市逻辑");
        }, 3, TimeUnit.SECONDS);
//        Thread.sleep(2000);
//        boolean cancel = task.cancel(false);
//        System.out.println("是否取消成功:" + cancel);
        Thread.sleep(4000);
        scheduler.shutdownNow();
    }

    public static void testScheduledFuture() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("固定速率执行" + System.currentTimeMillis());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    public static void testScheduleWithFixedDelay() {
        scheduler.scheduleWithFixedDelay(() -> {
            System.out.println("固定延迟队列" + System.currentTimeMillis());
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    public static void testCompletableFuture() throws InterruptedException {
        //supplyAsync 有返回值 异步
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return "设备应答成功";
        });
        //whenComplete:成功/异常都会执行
        future.whenComplete((resp, ex) -> {
            if (ex != null) {
                System.out.println("任务异常:" + ex.getMessage());
            } else {
                System.out.println("收到应答:" + resp);
            }
        });
        Thread.sleep(1500);
    }

    public static void testCompletableFutureThenApply() {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            return "原始报文";
        }).thenApply(msg -> {
            //转换:解析报文
            return msg + "->解析完成";
        }).thenAccept(parsedMsg->{
            System.out.println("业务处理:"+parsedMsg);
        }).exceptionally(ex->{
            System.out.println("发生异常:"+ex.getMessage());
        })
    }
}
