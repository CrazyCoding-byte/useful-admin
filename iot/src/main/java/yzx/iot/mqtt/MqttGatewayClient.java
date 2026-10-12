package yzx.iot.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import yzx.iot.protocol.mqtt.MqttMsg;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @className: MqttGatewayClient
 * @author: yzx
 * @date: 2026/10/12 11:48
 * @Version: 1.0
 * @description:
 */
@Slf4j
public class MqttGatewayClient {
    private final String brokerUrl;
    private final List<String> subscriptions;
    private final Consumer<MqttMsg> inboundSink;
    private MqttClient client;

    public MqttGatewayClient(String brokerUrl, List<String> subscriptions, Consumer<MqttMsg> inboundSink) {
        this.brokerUrl = brokerUrl;
        this.subscriptions = subscriptions;
        this.inboundSink = inboundSink;
    }

    public void start() {
        try {
            MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setCleanSession(true);
            mqttConnectOptions.setAutomaticReconnect(true);
            mqttConnectOptions.setConnectionTimeout(10);
            mqttConnectOptions.setKeepAliveInterval(30);
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    try {
                        resubscribe();
                        log.info("[mqtt] 网关已连接Broker uri={} reconnect={}", serverURI, reconnect);
                    } catch (Exception e) {
                        log.error("[mqtt]连接后订阅失败", e);
                    }
                }

                @Override
                public void connectionLost(Throwable throwable) {
                    log.warn("[mqtt]与broker连接断开,等待自动重连", throwable);
                }

                @Override
                public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                     inboundSink.accept(new MqttMsg(s,mqttMessage.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("MQTT gateway client failed to start" + brokerUrl, e);
        }
    }


    public CompletableFuture<Void> publish(String topic, byte[] payload, int qos) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (client == null || !client.isConnected()) {
            future.completeExceptionally(new IllegalStateException("MQTT gateway client failed to start"));
            return future;
        }
        try {
            MqttMessage mqttMessage = new MqttMessage(payload);
            mqttMessage.setQos(qos);
            client.publish(topic, mqttMessage);// QoS1：Paho阻塞到PUBACK，抛异常即Broker拒收
            future.complete(null);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    private void resubscribe() throws Exception {
        String[] topics = subscriptions.toArray(new String[0]);
        int[] qoa = new int[subscriptions.size()];
        Arrays.fill(qoa, 1);
        client.subscribe(topics, qoa);
    }

    public void stop() {
        try {
            if (client != null) {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            }
        } catch (Exception e) {
            log.warn("[mqtt]网关客户端关闭异常");
        }
    }
}
