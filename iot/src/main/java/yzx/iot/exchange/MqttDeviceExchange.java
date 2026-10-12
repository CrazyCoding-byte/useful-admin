package yzx.iot.exchange;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import yzx.iot.exchange.converter.ProtocolMessageConverter;
import yzx.iot.protocol.MqttMsg;

import java.util.function.Consumer;

/**
 * MQTT世界的"话机"：每台设备一个逻辑实例，但不持有物理连接
 * - 所有实例共享同一个到Broker的Paho客户端（一万台设备也只有一条TCP连接）
 * - 每个实例只记住自己的deviceId，出站publish到本设备的down主题
 * - 入站由MqttTransport按deviceId转交对应实例的fireInbound
 */
public class MqttDeviceExchange implements DeviceExchange {

    private final MqttClient sharedClient;
    private final ProtocolMessageConverter<MqttMsg> converter;
    private final String deviceId;

    /** 逻辑在线标记：物理连接在Broker手里看不到，用它表达"未被逻辑踢下线" */
    private volatile boolean active = true;
    private volatile Consumer<DeviceMessage> inboundListener;

    public MqttDeviceExchange(MqttClient sharedClient,
                              ProtocolMessageConverter<MqttMsg> converter,
                              String deviceId) {
        this.sharedClient = sharedClient;
        this.converter = converter;
        this.deviceId = deviceId;
    }

    @Override
    public String deviceId() {
        return deviceId;
    }

    @Override
    public String protocol() {
        return "mqtt";
    }

    /**
     * 出站=publish到本设备的down主题，Broker负责路由到订阅该主题的设备
     * QoS 1：至少送达一次，重复靠设备侧seqId去重
     */
    @Override
    public void sendOutbound(DeviceMessage message) {
        try {
            MqttMsg outbound = converter.toOutbound(message);
            MqttMessage pahoMsg = new MqttMessage(outbound.getBody());
            pahoMsg.setQos(1);
            sharedClient.publish(outbound.getTopic(), pahoMsg);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT下行发布失败 deviceId=" + deviceId, e);
        }
    }

    /** 总机（MqttTransport）把上行消息转给本设备分机时调用 */
    public void fireInbound(DeviceMessage message) {
        Consumer<DeviceMessage> listener = inboundListener;
        if (listener != null) {
            listener.accept(message);
        }
    }

    @Override
    public void onInbound(Consumer<DeviceMessage> listener) {
        this.inboundListener = listener;
    }

    /**
     * 只看得到"网关到Broker"这一段；设备到Broker那段由Broker维护，
     * 设备死活由遗嘱消息驱动logout表达
     */
    @Override
    public boolean isActive() {
        return active && sharedClient.isConnected();
    }

    /** 逻辑踢线：物理连接在Broker上关不掉，只标记本话机失效 */
    @Override
    public void close() {
        active = false;
    }
}
