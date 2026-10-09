package yzx.iot.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import yzx.iot.exchange.DeviceExchange;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
    public static void main(String[] args) {
        HashMap<Integer, String> map = new HashMap<>();
        map.put(1, "1");
        if (map.get(1) == null) {
            map.put(1, "2");
        }
        map.put(1, "3");
    }

    private final ConcurrentHashMap<String, DeviceState> states = new ConcurrentHashMap<String, DeviceState>();

//    public DeviceState login(String deviceId, DeviceExchange exchange) {
    //这个地方的判断问题不是原子的意思是
//        DeviceState state = states.computeIfAbsent(deviceId, DeviceState::new);
//        DeviceExchange oldExchange = state.bindExchange(exchange);
//        if (oldExchange != null && !oldExchange.equals(exchange) && oldExchange.isActive()) {
//            log.warn("设备重复登录,旧连接被顶替deviceId={}", deviceId);
//            oldExchange.close();
//        }
//        log.info("设备上线 deviceId={} protocol={} online={}", deviceId, exchange.protocol(), states.size());
//        return state;
//    }

    /**
     * 登录/重连：建/取行 + 换插槽，必须在同一个compute里完成，
     * 保证"入行"和"换槽"对logout而言是不可分割的一个动作
     */
    public DeviceState login(String deviceId, DeviceExchange exchange) {
        // 用局部变量把锁内发现的旧连接"带出来"，close在锁外做（IO不进锁）
        AtomicReference<DeviceExchange> oldHolder = new AtomicReference<>();
        DeviceState state = states.compute(deviceId, (id, s) -> {
            DeviceState st = (s == null) ? new DeviceState(id) : s;
            oldHolder.set(st.bindExchange(exchange)); // ★ 换插槽也在桶锁内
            return st;
        });
        DeviceExchange oldExchange = oldHolder.get();
        if (oldExchange != null && oldExchange != exchange && oldExchange.isActive()) {
            log.warn("设备重复登录,旧连接被顶替 deviceId={}", deviceId);
            oldExchange.close();
        }
        log.info("设备上线 deviceId={} protocol={} online={}",
                deviceId, exchange.protocol(), states.size());
        return state;
    }

    /**
     *  连线短线注销,必须带"谁断了",不能只给DeviceId
     *  旧连接迟到的死亡事件unbind里的cas被挡在外面
     * @param deviceId
     * @param exchange
     */
//    public void logout(String deviceId, DeviceExchange exchange) {
//        if (deviceId == null) {
//            return;
//        }
//        DeviceState state = states.get(deviceId);
//        if (state == null) {
//            return;
//        }
//        if (!state.unBindExchange(exchange)) {
//            return;
//        }
//        state.failAllPending(new IllegalStateException("设备连接断开:" + deviceId));
//        //compute 如果返回null的话会删除key
//        states.compute(deviceId, (id, s) -> (s == state && s.getExchange() == null) ? null : s);
//        log.info("设备下线deviceId={} online={}", deviceId, states.size());
//    }
    public void logout(String deviceId, DeviceExchange exchange) {
        if (deviceId == null) {
            return;
        }
        states.computeIfPresent(deviceId, (id, s) -> {
            if (!s.unBindExchange(exchange)) {
                return s;
            }
            s.failAllPending(new IllegalStateException("设备连接断开" + deviceId));
            return s.getExchange() == null ? null : s;
        });
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
