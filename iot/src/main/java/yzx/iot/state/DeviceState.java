package yzx.iot.state;

import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;

import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
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
    private final AtomicReference<DeviceExchange> exchangeRef = new AtomicReference<>();
    private volatile long lastHeartbeatTime;
    private volatile long seqGenerator;
    private final ConcurrentHashMap<Integer, CompletableFuture<DeviceMessage>> pendingRequests = new ConcurrentHashMap<>();
    private final Queue<byte[]> offlineMsgQueue = new LinkedBlockingQueue<>();
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();

    public DeviceState(String deviceId) {
        this.deviceId = deviceId;
    }

    public DeviceExchange bindExchange(DeviceExchange exchange) {
        DeviceExchange andSet = exchangeRef.getAndSet(exchange);
        if (andSet != null) {
            long now = System.currentTimeMillis();
            this.lastHeartbeatTime = now;
        }
        return andSet;
    }
}
