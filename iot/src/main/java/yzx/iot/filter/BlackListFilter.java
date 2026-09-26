package yzx.iot.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;

import java.util.Set;

/**
 * @className: BlackFilter
 * @author: yzx
 * @date: 2026/9/26 11:40
 * @Version: 1.0
 * @description:
 */
@Component
@Slf4j
@Order(200)
public class BlackListFilter implements DeviceMessageFilter {
    private static final Set<String> BLACKLIST = Set.of("banned-device-001");

    @Override
    public void doFilter(DeviceExchange exchange, DeviceMessage msg, DeviceFilterChain chain) throws Exception {
        if (BLACKLIST.contains(msg.getDeviceId())) { // 不调用 chain.doNext() —— 消息在此终结，后面的过滤器和业务都收不到 exchange.close(); return ;
        }
        chain.doNext(exchange, msg);
    }
}
