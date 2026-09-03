package yzx.iot.spi;

import yzx.iot.exchange.DeviceExchange;

import java.util.function.Consumer;

/**
 * @className: ServerTransport
 * @author: yzx
 * @date: 2026/9/2 13:52
 * @Version: 1.0
 * @description: 服务端传输spi
 *
 */
public interface ServerTransport {
    String name();
    void start(int port, Consumer<DeviceExchange> c);
}
