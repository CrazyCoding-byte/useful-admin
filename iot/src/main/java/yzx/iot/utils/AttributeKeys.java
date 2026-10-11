package yzx.iot.utils;

import io.netty.util.AttributeKey;
import lombok.Data;
import yzx.iot.exchange.NettyDeviceExchange;

/**
 * @className: AttributeKeys
 * @author: yzx
 * @date: 2026/8/31 22:58
 * @Version: 1.0
 * @description:
 */
@Data
public class AttributeKeys {
    public static final AttributeKey<NettyDeviceExchange<?>> DEVICE_EXCHANGE = AttributeKey.valueOf("DEVICE_EXCHANGE");
}