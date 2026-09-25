package yzx.iot.exchange.converter;

import yzx.iot.exchange.DeviceMessage;
import yzx.iot.protocol.TcpMessage;

/**
 * @className: TcpMessageConverter
 * @author: yzx
 * @date: 2026/9/2 8:36
 * @Version: 1.0
 * @description:
 */
public class TcpMessageConverter implements ProtocolMessageConverter<TcpMessage> {
    public DeviceMessage toDeviceMessage(TcpMessage tcpMessage) {
        return new DeviceMessage(
                tcpMessage.getCmdType().name(),
                tcpMessage.getSeqId(),
                tcpMessage.getDeviceId(),
                tcpMessage.getPayload()
        );
    }

    @Override
    public String protocol() {
        return "private-ctp";
    }

    @Override
    public DeviceMessage convert(TcpMessage source) {
        return new DeviceMessage(
                source.getCmdType().name(),
                source.getSeqId(),
                source.getDeviceId(),
                source.getPayload()
        );
    }
}
