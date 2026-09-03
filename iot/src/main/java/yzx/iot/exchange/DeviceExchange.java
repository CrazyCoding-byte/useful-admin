package yzx.iot.exchange;

import java.util.function.Consumer;

/**
 * @className: DeviceExchange
 * @author: yzx
 * @date: 2026/9/3 9:23
 * @Version: 1.0
 * @description:
 */
public interface DeviceExchange {
    /**
     * 设备唯一id
     * @return
     */
    String deviceId();

    /**
     * 底层传输协议类型(仅用于日志统计,业务不依赖)
     * @return
     */
    String protocol();

    /**
     *注册上行消息监听器(设备->平台)
     * 传输层收到消息后,回调这个监听器
     * @param listener
     */
    void onInbound(Consumer<Object> listener);

    /**
     * 发送下行消息(平台->设备)
     */
    void sendOutbound(Object message);

    /**
     * 通道是否存货
     * @return
     */
    boolean isActive();

    /**
     * 关闭通道
     */
    void close();
}
