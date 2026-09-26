package yzx.iot.filter.dispatcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;
import yzx.iot.filter.DeviceFilterChain;
import yzx.iot.filter.DeviceMessageFilter;
import yzx.iot.processor.DeviceBusinessProcessor;

import java.util.List;

/**
 * @className: DeviceMessageDispatcher
 * @author: yzx
 * @date: 2026/9/26 11:25
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Component
public class DeviceMessageDispatcher {
    private final List<DeviceMessageFilter> filters;
    private final DeviceBusinessProcessor processor;

    public DeviceMessageDispatcher(List<DeviceMessageFilter> filters, DeviceBusinessProcessor processor) {
        this.filters = filters;
        this.processor = processor;
    }

    public void dispatch(DeviceExchange exchange, Object raw) {
        if (!(raw instanceof DeviceMessage)) {
            log.warn("非统一消息,丢弃:{}", raw.getClass());
            return;
        }
        try {
            new DeviceFilterChain(filters, processor::onMessage).doNext(exchange, (DeviceMessage) raw);
        } catch (Exception e) {
            log.error("过滤链执行异常 deviceId={}", ((DeviceMessage) raw).getDeviceId(), e);
        }
    }
}
