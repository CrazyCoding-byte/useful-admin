package yzx.iot.session;

import io.netty.channel.Channel;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @className: DeviceSession
 * @author: yzx
 * @date: 2026/8/30 15:51
 * @Version: 1.0
 * @description: 设备会话管理
 */
public class DeviceSession {
    private String deviceId;
    private Channel channel;
    private long loginTime;
    private long lastHeartbeatTime;
    /**离线消息队列,最多缓存100条**/
    private Queue<byte[]> offlineMsgQueue = new LinkedBlockingQueue<>(100);
    /**请求序列号生成器**/
    private AtomicInteger seqGenerator = new AtomicInteger(0);

    public DeviceSession(String deviceId, Channel channel) {
        this.deviceId = deviceId;
        this.channel = channel;
        this.loginTime = System.currentTimeMillis();
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public int nextSeq() {
        return seqGenerator.incrementAndGet();
    }

    public void addofflineMsg(byte[] msg) {
        if (!offlineMsgQueue.offer(msg)) {
            offlineMsgQueue.poll();
            offlineMsgQueue.offer(msg);
        }
    }
}
