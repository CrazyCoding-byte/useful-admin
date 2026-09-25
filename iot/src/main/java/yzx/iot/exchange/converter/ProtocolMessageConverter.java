package yzx.iot.exchange.converter;

import yzx.iot.exchange.DeviceMessage;

/**
 * @className: ProtocolMessageConverter
 * @author: yzx
 * @date: 2026/9/25 20:50
 * @Version: 1.0
 * @description:
 */
public interface ProtocolMessageConverter<S> {
    String protocol();

    DeviceMessage convert(S source);
}
