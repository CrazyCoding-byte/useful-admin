package yzx.iot.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import yzx.iot.utils.AttributeKeys;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @className: SessionManager
 * @author: yzx
 * @date: 2026/8/30 15:51
 * @Version: 1.0
 * @description:
 */
public enum
SessionManager {
    INSTANCE;
    /**deviceId->会话**/
    private final ConcurrentHashMap<String, DeviceSession> sessions = new ConcurrentHashMap();//全局设备会话表
    /**channId->deviceId反向银蛇**/
    private final ConcurrentHashMap<String, String> channelToDevice = new ConcurrentHashMap<>();//反向索引

    /**
     * 注册会话
     * @param deviceId
     * @param channel
     */
    public void register(String deviceId, Channel channel) {
        DeviceSession deviceSession = new DeviceSession(deviceId, channel);
        sessions.put(deviceId, deviceSession);
        channelToDevice.put(channel.id().asLongText(), deviceId); //解决只有channel的情况
        channel.attr(AttributeKeys.DEVICE_SESSION).set(deviceSession); //连接上下文
    }

    /**
     * 移除会话
     * @param channel
     */
    public void remove(Channel channel) {
        String deviceId = channelToDevice.remove(channel.id().asLongText());
        if (deviceId != null) {
            sessions.remove(deviceId);
        }
    }

    public DeviceSession get(String deviceId) {
        return sessions.get(deviceId);
    }

    public DeviceSession getByChannel(Channel channel) {
        return channel.attr(AttributeKeys.DEVICE_SESSION).get();
    }

    public int onlineCount() {
        return sessions.size();
    }
}
