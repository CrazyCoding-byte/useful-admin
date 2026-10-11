package yzx.iot.state;

import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @className: DeviceState
 * @author: yzx
 * @date: 2026/10/6 10:57
 * @Version: 1.0
 * @description:
 */
public class DeviceState {

    private final String deviceId;
    // ② 连接插槽：为什么不是volatile普通引用？
    // 因为注销时需要"槽里是我本人我才清空"的条件替换，volatile给不了CAS
    private final AtomicReference<DeviceExchange> exchangeRef = new AtomicReference<>();
    private volatile long loginTime;
    private volatile long lastHeartbeatTime;
    //序列号
    private final AtomicInteger seqGenerator = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, CompletableFuture<DeviceMessage>> pendingRequests = new ConcurrentHashMap<>();
    private final Queue<byte[]> offlineMsgQueue = new LinkedBlockingQueue<>(100);
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();

    public DeviceState(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public DeviceExchange bindExchange(DeviceExchange exchange) {
        DeviceExchange andSet = exchangeRef.getAndSet(exchange);
        long now = System.currentTimeMillis();
        this.lastHeartbeatTime = now;
        this.loginTime = now;
        return andSet;
    }

    public boolean unBindExchange(DeviceExchange exchange) {
        return exchangeRef.compareAndSet(exchange, null);
    }

    public DeviceExchange getExchange() {
        return exchangeRef.get();
    }

    public boolean isOline() {
        DeviceExchange deviceExchange = exchangeRef.get();
        return deviceExchange != null && deviceExchange.isActive();
    }

    public long getLoginTime() {
        return loginTime;
    }

    public long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public void touchHeartbeat() {
        lastHeartbeatTime = System.currentTimeMillis();
    }

    public int nextSeq() {
        return seqGenerator.incrementAndGet();
    }

    //开单:建一张空白取一单,登记入册、把单子交给法令方
    public CompletableFuture<DeviceMessage> addPendingRequest(int seqId) {
        CompletableFuture<DeviceMessage> future = new CompletableFuture<>();
        pendingRequests.put(seqId, future);
        return future;
    }

    //撕单:返回被摘掉的单子:查无此单返回null
    public CompletableFuture<DeviceMessage> removePendingRequest(int seqId) {
        return pendingRequests.remove(seqId);
    }

    /**
     * 短线批量撕单:先逐张填异常(叫醒所有等待的业务线程),再一次性清空登记本
     * complete对已完成的future是安全操作,不用逐个判isDone
     */
    public void failAllPending(Throwable cause) {
        pendingRequests.forEach((seqId, future) -> future.completeExceptionally(cause));
        pendingRequests.clear();
    }

    /**
     * 离线缓存:offer满了会失败,此时摘掉最旧一条再塞
     * @param msg
     */
    public void addOfflineMsg(byte[] msg) {
        if (!offlineMsgQueue.offer(msg)) {
            offlineMsgQueue.poll();
            offlineMsgQueue.offer(msg);
        }
    }

    public void subscribe(String subscription) {
        subscriptions.add(subscription);
    }

    public void unsubscribe(String subscription) {
        subscriptions.remove(subscription);
    }
}
