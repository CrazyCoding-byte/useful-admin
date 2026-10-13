package yzx.iot.mqtt;

import lombok.extern.slf4j.Slf4j;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;
import yzx.iot.exchange.converter.ProtocolMessageConverter;
import yzx.iot.protocol.mqtt.MqttMsg;
import yzx.iot.state.DeviceStateManager;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @className: MqttMessageRouter
 * @author: yzx
 * @date: 2026/10/12 17:22
 * @Version: 1.0
 * @description:
 */
@Slf4j
public class MqttMessageRouter {
    private static final String ONLINE = "online";
    private static final String OFFLINE = "offline";
    private final MqttGatewayClient mqttGatewayClient;
    private final DeviceStateManager stateManager;
    private final ProtocolMessageConverter protocolMessageConverter;
    private final Consumer<DeviceExchange> exchangeBinder;
    private final ConcurrentHashMap<String, MqttDeviceExchange> exchanges = new ConcurrentHashMap<>();

    public MqttMessageRouter(MqttGatewayClient mqttGatewayClient, DeviceStateManager deviceStateManager, ProtocolMessageConverter protocolMessageConverter, Consumer<DeviceExchange> exchangeBinder, MqttGatewayClient mqttGatewayClient1, DeviceStateManager deviceStateManager1, ProtocolMessageConverter protocolMessageConverter1, Consumer<DeviceExchange> exchangeBinder1) {
        this.mqttGatewayClient = mqttGatewayClient1;
        this.stateManager = deviceStateManager1;
        this.protocolMessageConverter = protocolMessageConverter1;
        this.exchangeBinder = exchangeBinder1;
    }

    public void route(MqttMsg msg) {
        MqttTopicScheme.Address address = MqttTopicScheme.parse(msg.getTopic());
        switch (address.kind()) {
            case STATUS -> handleStatus(address.deviceId(), msg.getBody());
            case UP -> handleUp(address.deviceId(), msg);
            case DOWN, UNKNOWN -> log.debug("[mqtt]忽略非处理主题:{}", msg.getTopic());
        }
    }

    private void handleStatus(String deviceId, byte[] body) {
        String status = new String(body, StandardCharsets.UTF_8).trim();
        switch (status) {
            case ONLINE -> handleOnline(deviceId);
            case OFFLINE -> handleOffline(deviceId);
            default -> log.warn("[mqtt]未知设备状态payload={} deviceId={}", status, deviceId);
        }
    }

    private void handleOnline(String deviceId) {
        MqttDeviceExchange created = new MqttDeviceExchange(mqttGatewayClient, protocolMessageConverter, deviceId);
        MqttDeviceExchange existing = exchanges.putIfAbsent(deviceId, created);
        if (existing != null) {
            exchangeBinder.accept(existing);
            stateManager.login(deviceId, created);
            log.info("[mqtt]设备上线deviceId={}", deviceId);
        }
    }

    private void handleOffline(String deviceId) {
        MqttDeviceExchange mqttDeviceExchange = exchanges.get(deviceId);
        if (mqttDeviceExchange != null) {
            return;
        }
        mqttDeviceExchange.close();
        stateManager.logout(deviceId, mqttDeviceExchange);
        exchanges.remove(deviceId, mqttDeviceExchange);
        log.info("[mqtt]设备下线deviceId={}", deviceId);
    }

    private void handleUp(String deviceId, MqttMsg msg) {
        MqttDeviceExchange mqttDeviceExchange = exchanges.get(deviceId);
        if (mqttDeviceExchange == null) {
            log.warn("[mqtt] 未上线设备的上行消息被丢弃 deviceId={} topic={}", deviceId, msg.getTopic());
            return;
        }
        DeviceMessage unified = protocolMessageConverter.toInbound(msg);
        mqttDeviceExchange.fireInbound(unified);
    }
}
