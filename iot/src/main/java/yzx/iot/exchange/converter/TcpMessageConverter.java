package yzx.iot.exchange.converter;

import yzx.iot.deviceneum.CmdType;
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
    @Override
    public String protocol() {
        return "private-tcp";
    }

    /**
     * 入站：cmdType 从枚举降级为字符串
     * 统一模型不能依赖任何协议特有的枚举类（否则 MQTT 来了怎么办）
     */
    @Override
    public DeviceMessage toInbound(TcpMessage source) {
        return new DeviceMessage(
                source.getCmdType().name(),
                source.getSeqId(),
                source.getDeviceId(),
                source.getPayload()
        );
    }

    /**
     * 出站：字符串还原成枚举
     * 入站时是 name() 存的，这里 valueOf() 必然对得上，不会抛异常
     */
    @Override
    public TcpMessage toOutbound(DeviceMessage source) {
        return new TcpMessage(
                CmdType.valueOf(source.getCmdType()),
                source.getSeqId(),
                source.getDeviceId(),
                source.getPayload()
        );
    }
}
