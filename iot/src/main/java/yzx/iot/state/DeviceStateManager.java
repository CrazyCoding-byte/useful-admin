package yzx.iot.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import yzx.iot.exchange.DeviceExchange;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @className: DeviceStateManager
 * @author: yzx
 * @date: 2026/10/6 11:01
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Component
public class DeviceStateManager {
    private final ConcurrentHashMap<String, DeviceState> states = new ConcurrentHashMap<>();

    public DeviceState login(String deviceId, DeviceExchange exchange) {
        DeviceState state = states.computeIfAbsent(deviceId, DeviceState::new);
        DeviceExchange oldExchange = state.bindExchange(exchange);
        if (oldExchange != null && !oldExchange.equals(exchange) && oldExchange.isActive()) {
            log.warn("设备重复登录,旧连接被顶替deviceId={}", deviceId);
            oldExchange.close();
        }
        log.info("设备上线 deviceId={} protocol={} online={}", deviceId, exchange.protocol(), states.size());
        return state;
    }

    /**
     *  连线短线注销,必须带"谁断了",不能只给DeviceId
     *  旧连接迟到的死亡事件unbind里的cas被挡在外面
     * @param deviceId
     * @param exchange
     */
    public void logout(String deviceId, DeviceExchange exchange) {
        if (deviceId == null) {
            return;
        }
        DeviceState state = states.get(deviceId);
        if (state == null) {
            return;
        }
        if (!state.unBindExchange(exchange)) {
            return;
        }
        state.failAllPending(new IllegalStateException("设备连接断开:" + deviceId));
        states.compute(deviceId, (id, s) -> (s == state && s.getExchange() == null) ? null : s);
        log.info("设备下线deviceId={} online={}", deviceId, states.size());
    }

    public DeviceState getState(String deviceId) {
        return states.get(deviceId);
    }

    public Collection<DeviceState> getStates() {
        return states.values();
    }

    public int onlineCount() {
        return states.size();
    }

}
