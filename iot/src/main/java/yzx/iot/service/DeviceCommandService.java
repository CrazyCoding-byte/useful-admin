package yzx.iot.service;

import io.netty.channel.ChannelFuture;
import org.springframework.stereotype.Service;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.session.DeviceSession;
import yzx.iot.session.SessionManager;
import yzx.iot.state.DeviceState;
import yzx.iot.state.DeviceStateManager;

import java.util.concurrent.*;

/**
 * @className: DeviceCommandService
 * @author: yzx
 * @date: 2026/9/20 18:02
 * @Version: 1.0
 * @description:
 */
@Service
public class DeviceCommandService {
    private static final long COMMAND_TIMEOUT_MS = 10_000L;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final DeviceStateManager stateManager;

    public DeviceCommandService(DeviceStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public CompletableFuture<DeviceMessage> sendCommand(String deviceId, byte[] commandData) {
        DeviceState state = stateManager.getState(deviceId);
        if (state == null || !state.isOline()) {
            CompletableFuture<DeviceMessage> failed =
                    new CompletableFuture<>();

            failed.completeExceptionally(
                    new IllegalStateException("设备离线: " + deviceId)
            );

            return failed;
        }

        DeviceExchange exchange = state.getExchange();
        int seqId = state.nextSeq();

        CompletableFuture<DeviceMessage> future =
                state.addPendingRequest(seqId);


        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            CompletableFuture<DeviceMessage> pendingRequest = state.removePendingRequest(seqId);
            if (pendingRequest != null && !pendingRequest.isDone()) {
                pendingRequest.completeExceptionally(new TimeoutException("设备响应超时, seqId=" + seqId + ", deviceId=" + deviceId));
            }
        }, COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        future.whenComplete((resp, ex) -> timeoutTask.cancel(false));
        exchange.sendOutbound(new DeviceMessage(CmdType.CMD_PUSH.name(), seqId, deviceId, commandData));

        return future;
    }
}
