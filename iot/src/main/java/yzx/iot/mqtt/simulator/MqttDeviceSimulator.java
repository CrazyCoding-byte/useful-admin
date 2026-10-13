package yzx.iot.mqtt.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Scanner;

import static org.apache.logging.log4j.message.MapMessage.MapFormat.JSON;

/**
 * @className: MqttDeviceSimulator
 * @author: yzx
 * @date: 2026/10/13 11:25
 * @Version: 1.0
 * @description:
 */
public class MqttDeviceSimulator {
    private static final String BROKER = "tcp://127.0.0.1:1883";
    private static final String DEVICE_ID = "dev-mqtt-001";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws MqttException, JsonProcessingException {
        MqttClient mqttClient = new MqttClient(BROKER, DEVICE_ID, new MemoryPersistence());
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setKeepAliveInterval(15);
        // 遗嘱：必须在connect前登记。异常死亡时Broker代发到status主题，retained覆盖online
        mqttConnectOptions.setWill("device/" + DEVICE_ID + "/status", "offline".getBytes(StandardCharsets.UTF_8), 1, true);
        mqttClient.connect();
        System.out.println("Connected to " + BROKER);
        mqttClient.setCallback(new MqttCallback() {

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                System.out.println("[设备] 收到下行 topic=" + topic
                        + " payload=" + new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("[设备] 连接丢失: " + cause);
            }

            @Override
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
            }
        });
        //订阅自己的下行通道(#匹配任意指令)
        mqttClient.subscribe("device/" + DEVICE_ID + "/down/#", 1);
        //上线:retaine消息,网关-订阅status就会收到
        publish(mqttClient, "device/" + DEVICE_ID + "/status", "online", true);
        byte[] raw = "温度=26.5".getBytes(StandardCharsets.UTF_8);
        String body = mapper.writeValueAsString(Map.of("seqId", 1, "data", Base64.getEncoder().encodeToString(raw)));
        publish(mqttClient, "device/" + DEVICE_ID + "/up/DATA_REPORT", body, false);
        System.out.println("[设备]已发送数据上报,等待平台应答");
        Scanner scanner = new Scanner(System.in);
        String cmd = scanner.nextLine();
        if ("crash".equalsIgnoreCase(cmd.trim())) {

        }
    }

    private static void publish(MqttClient client, String topic, String payload, boolean retained) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(retained);
        client.publish(topic, message);
    }
}
