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

    /** 入站：协议私有报文 -> 统一消息 */
    DeviceMessage toInbound(S source);

    /** 出站：统一消息 -> 协议私有报文 */
    S toOutbound(DeviceMessage source);
}
