package yzx.iot.utils;

import io.netty.util.AttributeKey;
import lombok.Data;
import yzx.iot.session.DeviceSession;

/**
 * @className: AttributeKeys
 * @author: yzx
 * @date: 2026/8/31 22:58
 * @Version: 1.0
 * @description:
 */
@Data
public class AttributeKeys {
    public static final AttributeKey<DeviceSession> DEVICE_SESSION = AttributeKey.valueOf("DEVICE_SESSION");
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("DEVICE_ID");
}