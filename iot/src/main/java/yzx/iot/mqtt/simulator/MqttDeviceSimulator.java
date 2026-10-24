package yzx.iot.mqtt.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final AtomicInteger pendingSeq = new AtomicInteger(-1);

    public static void main(String[] args) throws MqttException, JsonProcessingException {
        MqttClient mqttClient = new MqttClient(BROKER, DEVICE_ID, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setKeepAliveInterval(15);
        // 遗嘱：异常死亡时Broker代发offline(retained覆盖online)
        options.setWill("device/" + DEVICE_ID + "/status",
                "offline".getBytes(StandardCharsets.UTF_8), 1, true);
        mqttClient.connect(options);
        System.out.println("Connected to " + BROKER);

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                System.out.println("[设备] 收到下行 topic=" + topic + " payload=" + payload);
                // 只认平台主动下发的指令；DATA_REPORT_RESP是应答自己的上报，忽略
                if (topic.endsWith("/down/CMD_PUSH")) {
                    int seq = mapper.readTree(payload).get("seqId").asInt();
                    pendingSeq.set(seq);   // 亮牌：只存取，不发送、不阻塞
                    System.out.println("[设备] 指令已收到 seqId=" + seq
                            + "，输入 resp 回复；不回复平台10秒后超时");
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("[设备] 连接丢失: " + cause);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        mqttClient.subscribe("device/" + DEVICE_ID + "/down/#", 1);
        publish(mqttClient, "device/" + DEVICE_ID + "/status", "online", true);
        byte[] raw = "温度=26.5".getBytes(StandardCharsets.UTF_8);
        String body = mapper.writeValueAsString(Map.of(
                "seqId", 1, "data", Base64.getEncoder().encodeToString(raw)));
        publish(mqttClient, "device/" + DEVICE_ID + "/up/DATA_REPORT", body, false);
        System.out.println("[设备]已上线并发送数据上报，命令: resp应答 / crash模拟崩溃 / offline优雅下线");

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String cmd = scanner.nextLine().trim();
            int seq = pendingSeq.get();   // 看牌
            switch (cmd) {
                case "resp" -> {
                    if (seq < 0) {
                        System.out.println("[设备] 当前没有待应答的指令");
                        break;
                    }
                    String respBody = mapper.writeValueAsString(Map.of(
                            "seqId", seq,
                            "data", Base64.getEncoder().encodeToString(
                                    "ok".getBytes(StandardCharsets.UTF_8))));
                    // topic和报文形状与上报完全一致，cmdType换成应答类型
                    publish(mqttClient,
                            "device/" + DEVICE_ID + "/up/CMD_PUSH_RESP", respBody, false);
                    pendingSeq.set(-1);
                    System.out.println("[设备] 已发送应答 seqId=" + seq);
                }
                case "crash" -> {
                    System.out.println("[设备] 模拟崩溃，不告而别，等15~30秒看网关遗嘱日志");
                    mqttClient.disconnectForcibly(0, 0);
                    return;
                }
                case "offline" -> {
                    publish(mqttClient, "device/" + DEVICE_ID + "/status", "offline", true);
                    mqttClient.close();
                    System.out.println("[设备] 已优雅下线");
                    return;
                }
                default -> System.out.println("[设备] 未知命令: " + cmd);
            }
        }
    }

    private static void publish(MqttClient client, String topic, String payload, boolean retained) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(retained);
        client.publish(topic, message);
    }
}
