package yzx.iot.filter;

import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;

/**
 * @className: DeviceMessageFilter
 * @author: yzx
 * @date: 2026/9/25 23:38
 * @Version: 1.0
 * @description:
 */
public interface DeviceMessageFilter {
    void doFilter(DeviceExchange exchange, DeviceMessage msg, DeviceFilterChain chain) throws Exception;
}
