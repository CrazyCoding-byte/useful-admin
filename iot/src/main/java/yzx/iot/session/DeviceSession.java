package yzx.iot.session;

import io.netty.channel.Channel;
import lombok.Data;
import yzx.iot.protocol.TcpMessage;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
/**
 * @className: DeviceSession
 * @author: yzx
 * @date: 2026/8/30 15:51
 * @Version: 1.0
 * @description: 设备会话管理
 */
@Data
public class DeviceSession {
    private String deviceId;
    private Channel channel;
    private long loginTime;
    private long lastHeartbeatTime;
    /**离线消息队列,最多缓存100条**/
    private Queue<byte[]> offlineMsgQueue = new LinkedBlockingQueue<>(100);
    /**请求序列号生成器**/
    private AtomicInteger seqGenerator = new AtomicInteger(0);
    /**
     * seqId->等待设备响应的future
     * 
     */
    private final ConcurrentHashMap<Integer,CompletableFuture<TcpMessage>> pendingRequests=new ConcurrentHashMap<>();

    public DeviceSession(String deviceId, Channel channel) {
        this.deviceId = deviceId;
        this.channel = channel;
        this.loginTime = System.currentTimeMillis();
        this.lastHeartbeatTime = System.currentTimeMillis();
    }
    public CompletableFuture<TcpMessage> addPendingRequest(int seqId){
        CompletableFuture<TcpMessage> future=new CompletableFuture<>();
        pendingRequests.put(seqId,future);
        return future;
    }
  
    public CompletableFuture<TcpMessage> removePendingRequest(int seqId){
        return pendingRequests.remove(seqId);
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
