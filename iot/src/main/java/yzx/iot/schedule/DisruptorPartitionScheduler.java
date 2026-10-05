package yzx.iot.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @className: DisruptorPartitionScheduler
 * @author: yzx
 * @date: 2026/10/4 20:51
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iot.scheduler.type", havingValue = "disruptor")
public class DisruptorPartitionScheduler implements DeviceMessageScheduler {
    @Override
    public void schedule(String deviceId, Runnable task) {

    }
}
