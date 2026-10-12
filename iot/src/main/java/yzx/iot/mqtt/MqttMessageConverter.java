package yzx.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import yzx.iot.exchange.DeviceMessage;
import yzx.iot.exchange.converter.ProtocolMessageConverter;
import yzx.iot.protocol.mqtt.MqttMsg;

import java.util.Base64;

/**
 * @className: MqttMessageConverter
 * @author: yzx
 * @date: 2026/10/11 20:42
 * @Version: 1.0
 * @description:
 */
public class MqttMessageConverter implements ProtocolMessageConverter<MqttMsg> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class Payload {
        public int seqId;
        //业务字节用base64包着走文本通道
        public String data;
    }

    @Override
    public String protocol() {
        return "mqtt";
    }

    @Override
    public DeviceMessage toInbound(MqttMsg source) {
        String[] parts = source.getTopic().split("/");
        if (parts.length < 4 || !"up".equals(parts[2])) {
            throw new IllegalArgumentException("非法的上行主题: " + source.getTopic());
        }
        String deviceId = parts[1];
        String cmdType = parts[3];
        Payload payload = parseBody(source.getBody());
        byte[] data = payload.data == null ? new byte[0] : Base64.getDecoder().decode(payload.data);

        return new DeviceMessage(cmdType, payload.seqId, deviceId, data);
    }

    /**
     *
     * @param source
     * @return
     */
    @Override
    public MqttMsg toOutbound(DeviceMessage source) {
        String topic = "device/" + source.getDeviceId() + "/down/" + source.getCmdType();
        Payload payload = new Payload();
        payload.seqId = source.getSeqId();
        payload.data = Base64.getEncoder().encodeToString(source.getPayload());
        try {
            return new MqttMsg(topic, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Mqtt出站消息序列化失败", e);
        }
    }

    private Payload parseBody(byte[] body) {
        try {
            return objectMapper.readValue(body, Payload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("MQTT报文体不是合法json", e);
        }
    }

}
