package yzx.iot.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;

/**
 * @className: LoggingFilter
 * @author: yzx
 * @date: 2026/9/26 11:38
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Component
@Order(100)
public class LoggingFilter implements DeviceMessageFilter {

    @Override
    public void doFilter(DeviceExchange exchange, DeviceMessage message, DeviceFilterChain chain) throws Exception {
        long start = System.currentTimeMillis();
        log.info("收到上报 deviceId={} cmd={} seq={}",
                message.getDeviceId(), message.getCmdType(), message.getSeqId());
        chain.doNext(exchange, message); // 放行 log.info( "处理完成 seq={} 耗时={}ms" ,
        log.info("处理完成 seq={} 耗时={}ms",
                message.getSeqId(), System.currentTimeMillis() - start);
    }
}
