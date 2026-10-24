package yzx.iot.test.mqtttest;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;

/**
 * @className: MqttTest
 * @author: yzx
 * @date: 2026/10/20 15:57
 * @Version: 1.0
 * @description:
 * org.eclipse.paho.client.mqttv3
 * ├── IMqttClient            ← 接口（面向它编程，便于换实现/测试）
 * ├── MqttClient             ← 同步实现（你们在用）
 * ├── MqttAsyncClient        ← 异步实现（唯一真正的实现，MqttClient 内部包着它）
 * ├── IMqttAsyncClient
 * ├── MqttConnectOptions     ← 连接参数
 * ├── MqttMessage            ← 消息体
 * ├── MqttCallback           ← 回调接口（3 个方法）
 * ├── MqttCallbackExtended   ← 增强回调（+ connectComplete）
 * ├── MqttTopic              ← 主题工具（validate 就在这）
 * ├── MqttException          ← 统一异常
 * ├── IMqttToken             ← 操作票据（连接/订阅）
 * ├── IMqttDeliveryToken     ← 投递票据（发布/送达）
 * └── persist/
 *     ├── MemoryPersistence
 *     └── MqttDefaultFilePersistence
 */
public class MqttTest {
    private static final String BROKER = "tcp://127.0.0.1:1883";
    private static final String CLIENT_ID = "test-" + System.currentTimeMillis();

    public static void main(String[] args) throws MqttException, InterruptedException {
        MqttClient mqttClient = new MqttClient(BROKER, CLIENT_ID, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        // 以后连 EMQX 就加这两行：
        // options.setUserName("xxx");
        // options.setPassword("yyy".toCharArray());
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable throwable) {
                System.out.println("connectionLost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
// 注意：这里跑在 Paho 回调线程，不能阻塞
                System.out.println("[test] 收到 topic=" + topic
                        + " payload=" + new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                System.out.println("[test] Broker 已签收 id=" + iMqttDeliveryToken.getMessageId());
            }
        });
        mqttClient.connect(options);
        mqttClient.subscribe("device/+/up/#", 1);
        // 6. 发布一条 QoS1（同步阻塞到 PUBACK）
        MqttMessage msg = new MqttMessage("hello paho".getBytes(StandardCharsets.UTF_8));
        msg.setQos(1);
        msg.setRetained(false);
        mqttClient.publish("device/dev-test-001/up/TEST", msg);

        Thread.sleep(3000);   // 留时间给回调线程打日志

        // 7. 优雅断开
        mqttClient.disconnect();
        mqttClient.close();
    }
}
