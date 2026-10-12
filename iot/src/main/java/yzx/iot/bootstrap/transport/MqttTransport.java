package yzx.iot.bootstrap.transport;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.mqtt.MqttDeviceExchange;
import yzx.iot.mqtt.MqttMessageConverter;
import yzx.iot.spi.ServerTransport;
import yzx.iot.state.DeviceStateManager;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @className: MqttTransport
 * @author: yzx
 * @date: 2026/10/12 9:59
 * @Version: 1.0
 * @description:
 */
@Component
public class MqttTransport implements ServerTransport {
    @Value("${iot.mqtt.port:1883}")
    private int port;
    private final DeviceStateManager stateManager;
    private final MqttMessageConverter converter = new MqttMessageConverter();
    private Server broker;
    private MqttClient gatewayClient;
    private final ConcurrentHashMap<String, MqttDeviceExchange> exchanges = new ConcurrentHashMap<>();

    public MqttTransport(final DeviceStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public String name() {

        return "mqtt";
    }

    @Override
    public void start(int port, Consumer<DeviceExchange> exchangeFactory) {
        Properties props = new Properties();
        props.setProperty("host", "0.0.0.0");
        props.setProperty("port", String.valueOf(port));
        props.setProperty("allow_anonymous", "true"); // 学习期不鉴权，Moquette留了IAuthenticator以后接
        broker = new Server();
        try {
            broker.startServer(new MemoryConfig(props));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {

        }
    }


    @Override
    public void stop() {

    }

    private void subscribeTopics() throws Exception {
        gatewayClient.subscribe(new String[]{"device/+/status", "device/+/up/#"}, new int[]{1, 1});
    }

    private void dispatch(String topic, byte[] payload, Consumer<DeviceExchange> exChangeFactory) {

    }
}
