package yzx.iot.filter;

import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * @className: DeviceFilterChain
 * @author: yzx
 * @date: 2026/9/25 23:40
 * @Version: 1.0
 * @description:
 */
public class DeviceFilterChain {
    private final List<DeviceMessageFilter> filters;
    private final BiConsumer<DeviceExchange, DeviceMessage> terminal;
    private int cursor = 0;

    public DeviceFilterChain(List<DeviceMessageFilter> filter, BiConsumer<DeviceExchange, DeviceMessage> terminal) {
        this.filters = filter;
        this.terminal = terminal;
    }

    public void doNext(DeviceExchange exchange, DeviceMessage message) throws Exception {
        if (cursor < filters.size()) {
            DeviceMessageFilter filter = filters.get(cursor);
            filter.doFilter(exchange, message, this);
        } else {
            //所有过滤器放行后,消息才到达业务处理器
            terminal.accept(exchange, message);
        }
    }
}
