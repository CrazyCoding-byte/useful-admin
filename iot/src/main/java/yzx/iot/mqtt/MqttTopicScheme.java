package yzx.iot.mqtt;

/**
 * @className: MqttTopicScheme
 * @author: yzx
 * @date: 2026/10/12 10:27
 * @Version: 1.0
 * @description:
 *
 *  * MQTT主题契约——全工程唯一知道主题长什么样的类（单一事实源）
 *  *
 *  * 约定：
 *  *   device/{deviceId}/status            上下线状态（retained: online/offline，由设备和遗嘱发布）
 *  *   device/{deviceId}/up/{cmdType}      设备上行业务消息
 *  *   device/{deviceId}/down/{cmdType}    平台下行业务消息
 *  *
 *  * 其他类只能调这里的方法，禁止自己拼/切topic字符串。
 *  * 对标TCP世界里"报文头格式只允许Decoder知道"的规矩。
 *
 */
public final class MqttTopicScheme {
    public static final String ROOT = "device";
    public static final String SEG_STATUS = "status";
    public static final String SEG_UP = "up";
    public static final String SEG_DOWN = "down";
    public static final String FILTER_STATUS = ROOT + "/+/" + SEG_STATUS; // device/+/status
public static final String FILTER_UP = ROOT + "/+/" + SEG_UP + "/#";  // device/+/up/#
    private MqttTopicScheme() {
    }

    public enum Kind {
        STATUS, UP, DOWN, UNKNOWN;
    }

    //record是一个类 属性写在类名边上
    public record Address(String deviceId, Kind kind, String cmdType) {

    }

    public static String statusTopic(String deviceId) {
        return ROOT + "/" + deviceId + "/" + SEG_STATUS;
    }

    public static String upTopic(String deviceId, String cmdType) {
        return ROOT + "/" + deviceId + "/" + SEG_UP + "/" + cmdType;
    }

    public static String downTopic(String deviceId, String cmdType) {
        return ROOT + "/" + deviceId + "/" + SEG_DOWN + "/" + cmdType;
    }

    public static Address parse(String topic) {
        if (topic == null) {
            return new Address(null, Kind.UNKNOWN, null);
        }
        String[] p = topic.split("/");
        if (p.length < 3 || !ROOT.equals(p[0])) {
            return new Address(null, Kind.UNKNOWN, null);
        }
        String deviceId = p[1];
        String segment = p[2];
        return switch (segment) {
            case SEG_STATUS -> new Address(deviceId, Kind.STATUS, segment);
            case SEG_UP, SEG_DOWN ->
                    p.length >= 4 ? new Address(deviceId, Kind.UP.equals(segment) ? Kind.UP : Kind.DOWN, p[3]) : new Address(deviceId, Kind.UNKNOWN, null);
            default -> new Address(deviceId, Kind.UNKNOWN, null);
        };
    }
}
