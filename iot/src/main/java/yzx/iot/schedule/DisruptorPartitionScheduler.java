package yzx.iot.schedule;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Disruptor引擎版：承诺与Executor版完全一致（同设备串行、跨设备并行）
 * 区别只在分区内部：加锁链表队列 -> 无锁环形数组
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iot.scheduler.type", havingValue = "disruptor")
public class DisruptorPartitionScheduler implements DeviceMessageScheduler {

    private static final int PARTITION_COUNT = 8;
    /** 环容量：必须是2的幂（定位格子用位与代替取模）；这是"允许落后的深度"，不是线程数 */
    private static final int RING_SIZE = 1024;

    private final DisruptorPartition[] partitions = new DisruptorPartition[PARTITION_COUNT];

    @PostConstruct
    public void init() {
        for (int i = 0; i < PARTITION_COUNT; i++) {
            partitions[i] = new DisruptorPartition(i);
        }
    }

    @Override
    public void schedule(String deviceId, Runnable task) {
        partitions[partition(deviceId)].publish(task);
    }

    private int partition(String deviceId) {
        if (deviceId == null) return 0;
        return Math.floorMod(deviceId.hashCode(), PARTITION_COUNT);
    }

    @PreDestroy
    public void shutdown() {
        for (DisruptorPartition p : partitions) {
            p.shutdown();
        }
    }

    /** 一个分区 = 一个RingBuffer + 一个消费线程 */
    private static class DisruptorPartition {
        private final Disruptor<TaskEvent> disruptor;
        private final RingBuffer<TaskEvent> ringBuffer;

        DisruptorPartition(int index) {
            ThreadFactory threadFactory = r -> {
                Thread t = new Thread(r, "device-disruptor-" + index);
                t.setDaemon(true);
                return t;
            };

            this.disruptor = new Disruptor<>(
                    TaskEvent::new,            // 事件工厂：启动时一次性new出全部空事件，之后零分配
                    RING_SIZE,
                    threadFactory,
                    ProducerType.MULTI,        // 多个Netty IO线程可能同时投递同一分区
                    new BlockingWaitStrategy() // 无消息时挂起省电；有消息的热路径无锁
            );

            // 必须设置：否则事件处理抛异常会杀死该分区消费线程且静默
            this.disruptor.setDefaultExceptionHandler(new ExceptionHandler<TaskEvent>() {
                @Override
                public void handleEventException(Throwable ex, long sequence, TaskEvent event) {
                    log.error("Disruptor消费异常 seq={}", sequence, ex);
                }

                @Override
                public void handleOnStartException(Throwable ex) {
                    log.error("Disruptor启动异常", ex);
                }

                @Override
                public void handleOnShutdownException(Throwable ex) {
                    log.error("Disruptor关闭异常", ex);
                }
            });

            // 单个handler = 单消费线程 = 同分区严格有序；绝不能在这里再submit线程池
            this.disruptor.handleEventsWith((event, sequence, endOfBatch) -> event.runAndClear());

            this.ringBuffer = this.disruptor.start();
        }

        void publish(Runnable task) {
            long sequence = ringBuffer.next();         // ① CAS申请唯一格子（多生产者无锁）
            try {
                ringBuffer.get(sequence).task = task;  // ② 写入预分配格子，不new事件
            } finally {
                ringBuffer.publish(sequence);          // ③ 发布带内存屏障，消费者立即可见
            }
        }

        void shutdown() {
            try {
                disruptor.shutdown(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                disruptor.halt();
            }
        }
    }

    /** 环上的格子内容物：固定数量的对象活整个进程，反复覆写，零GC */
    private static class TaskEvent {
        private Runnable task;

        void runAndClear() {
            Runnable t = task;
            task = null; // 清空引用，防止闭包抓住的exchange/message随环常驻造成泄漏
            t.run();
        }
    }
}
