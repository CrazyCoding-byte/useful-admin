package yzx.iot.protocol;

/**
 * @className: MqttMsg
 * @author: yzx
 * @date: 2026/10/11 20:27
 * @Version: 1.0
 * @description:
 *
 *  * MQTT协议私有消息（对标TCP世界的TcpMessage）
 *  * Paho回调里 topic 和 body 是两个参数，这里打包成一个对象，方便Converter统一处理
 *  *
 *  * 主题结构就是MQTT世界的"报文头"：
 *  *   device/{deviceId}/up/{CMD}      设备上行
 *  *   device/{deviceId}/down/{CMD}    平台下行
 *  *   device/{deviceId}/status        在线状态(retained: online/offline)
 *
 */
public class MqttMsg {
    private final String topic;
    private final byte[] body;

    public MqttMsg(String topic, byte[] body) {
        this.topic = topic;
        this.body = body;
    }

    public String getTopic() {
        return topic;
    }

    public byte[] getBody() {
        return body;
    }
}
