package yzx.iot.schedule;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @className: DeviceMessageSchedule
 * @author: yzx
 * @date: 2026/10/2 14:26
 * @Version: 1.0
 * @description:
 * 解决设备消息推送无顺序
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iot.scheduler.type", havingValue = "executor", matchIfMissing = true)
public class ExecutorPartitionScheduler implements DeviceMessageScheduler {
    //允许最大并行度,跟设备总数无关
    private static final int PARTITION_COUNT = 8;
    //每个分区是一个单线程+FIFO队列,顺序由队列保证
    private final ExecutorService[] partitions = new ExecutorService[PARTITION_COUNT];

    public ExecutorPartitionScheduler() {
        for (int i = 0; i < PARTITION_COUNT; i++) {
            final int index = i;
            partitions[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "device-worker" + index);
                t.setDaemon(true);
                return t;
            });
        }
    }

    @Override
    public void schedule(String deviceId, Runnable task) {
        partitions[partition(deviceId)].execute(task);
    }

    private int partition(String deviceId) {
        if (deviceId == null) return 0;
        // floorMod 恒非负；Math.abs(Integer.MIN_VALUE) 仍为负会导致数组越界
        return Math.floorMod(deviceId.hashCode(), PARTITION_COUNT);
    }

    @PreDestroy
    public void shutdown() {
        for (ExecutorService partition : partitions) {
            partition.shutdown();
        }
    }
}
