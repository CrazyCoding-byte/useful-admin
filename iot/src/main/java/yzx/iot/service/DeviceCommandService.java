package yzx.iot.service;

import io.netty.channel.ChannelFuture;
import org.springframework.stereotype.Service;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.session.DeviceSession;
import yzx.iot.session.SessionManager;

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

    public CompletableFuture<TcpMessage> sendCommand(String deviceId, byte[] commandData) {
        DeviceSession session = SessionManager.INSTANCE.get(deviceId);
        if (session == null || !session.getChannel().isActive()) {
            CompletableFuture<TcpMessage> future =
                    new CompletableFuture<>();

            future.completeExceptionally(
                    new IllegalStateException("设备离线: " + deviceId)
            );

            return future;
        }

        int seqId = session.nextSeq();

        CompletableFuture<TcpMessage> future =
                session.addPendingRequest(seqId);

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            CompletableFuture<TcpMessage> pending = session.removePendingRequest(seqId);
            if (pending != null && !pending.isDone()) {
                pending.completeExceptionally(
                        new TimeoutException("设备响应超时, seqId=" + seqId + ", deviceId=" + deviceId)
                );
            }
        }, COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        future.whenComplete((resp, ex) -> {
            timeoutTask.cancel(false);
        });
        TcpMessage request = new TcpMessage(
                CmdType.CMD_PUSH,
                seqId,
                deviceId,
                commandData
        );

        ChannelFuture writeFuture = session.getChannel().writeAndFlush(request);
        writeFuture.addListener(f -> {
            if (!f.isSuccess()) {
                CompletableFuture<TcpMessage> pending = session.removePendingRequest(seqId);
                if (pending != null && !pending.isDone()) {
                    pending.completeExceptionally(f.cause());
                }
                timeoutTask.cancel(false);
            }
        });


        return future;
    }
}
