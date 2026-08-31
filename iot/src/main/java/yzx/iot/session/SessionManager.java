package yzx.iot.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @className: SessionManager
 * @author: yzx
 * @date: 2026/8/30 15:51
 * @Version: 1.0
 * @description:
 */
public class SessionManager {
    /**deviceId->会话**/
    private final ConcurrentHashMap<String, DeviceSession> sessions = new ConcurrentHashMap();
    /**channId->deviceId反向银蛇**/
    private final ConcurrentHashMap<String, String> channelToDevice = new ConcurrentHashMap<>();

    public void register(String deviceId, Channel channel) {
        DeviceSession deviceSession = new DeviceSession(deviceId, channel);
        sessions.put(deviceId, deviceSession);
        channelToDevice.put(channel.id().asLongText(), deviceId);
        channel.attr(AttributeKey).set(deviceSession);
    }
}
