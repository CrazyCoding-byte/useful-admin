package yzx.iot.bootstrap.transport;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.mqtt.*;
import yzx.iot.protocol.mqtt.MqttMsg;
import yzx.iot.spi.ServerTransport;
import yzx.iot.state.DeviceStateManager;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @className: MqttTransport
 * @author: yzx
 * @date: 2026/10/12 9:59
 * @Version: 1.0
 * @description:
 *
 *  * MQTT传输层装配器：只负责把四个零件拼起来，自己不含任何业务逻辑
 *  *
 *  *   Moquette Broker（端口1883，管设备连接/遗嘱）
 *  *        ▲ 设备连入
 *  *        │ 网关回环
 *  *   MqttGatewayClient（唯一一条到Broker的连接，订阅+发布+重连）
 *  *        │ 入站 MqttMsg
 *  *   MqttMessageRouter（status控制面 / up业务面，管理话机注册表）
 *  *        │ up翻译过桥
 *  *   dispatcher → 过滤器链 → 调度器 → Processor（与TCP完全共用）
 *
 */
@Component
@Slf4j
public class MqttTransport implements ServerTransport {
    @Value("${iot.mqtt.port:1883}")
    private int port;
    private final DeviceStateManager stateManager;
    private final MqttMessageConverter converter = new MqttMessageConverter();
    private Server broker;
    private MqttGatewayClient gatewayClient;
    private final ConcurrentHashMap<String, MqttDeviceExchange> exchanges = new ConcurrentHashMap<>();

    public MqttTransport(final DeviceStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public String name() {

        return "mqtt";
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public void start(int port, Consumer<DeviceExchange> exchangeFactory) {
        startEmbeddedBroker(port);
        try {
            String url = "tcp://127.0.0.1:" + port;
            List<String> filters = List.of(MqttTopicScheme.FILTER_STATUS, MqttTopicScheme.FILTER_UP);
            MqttMessageRouter[] routers = new MqttMessageRouter[1];
            gatewayClient = new MqttGatewayClient(url, filters, (MqttMsg msg) -> routers[0].route(msg));
            MqttMessageRouter mqttMessageRouter = new MqttMessageRouter(gatewayClient, stateManager, converter, exchangeFactory);
            routers[0] = mqttMessageRouter;
            gatewayClient.start();
            log.info("[mqtt]传输层启动,Broker监听端口:{}", port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void stop() {
        if (gatewayClient != null) {
            gatewayClient.stop();
        }
        if (broker != null) {
            broker.stopServer();
        }
    }

    private void startEmbeddedBroker(int port) {
        Properties props = new Properties();
        props.setProperty("host", "0.0.0.0");
        props.setProperty("port", String.valueOf(port));
        props.setProperty("allow_anonymous", "true");
        broker = new Server();
        try {
            broker.startServer(new MemoryConfig(props));
        } catch (Exception e) {
            throw new IllegalStateException("内嵌MQTT Broker启动失败 port=" + port, e);
        }
    }

}
