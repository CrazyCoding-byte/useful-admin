package yzx.iot.schedule;

/**
 * @className: DeviceMessageScheduler
 * @author: yzx
 * @date: 2026/10/4 20:43
 * @Version: 1.0
 * @description:
 */
public interface DeviceMessageScheduler {
    void schedule (String deviceId, Runnable task) ;
}
