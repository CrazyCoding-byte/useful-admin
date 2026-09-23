package yzx.iot.service;

import yzx.iot.deviceneum.CmdType;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.session.DeviceSession;
import yzx.iot.session.SessionManager;

import java.util.concurrent.CompletableFuture;

/**
 * @className: DeviceCommandService
 * @author: yzx
 * @date: 2026/9/20 18:02
 * @Version: 1.0
 * @description:
 */
public class DeviceCommandService {
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

        TcpMessage request = new TcpMessage(
                CmdType.CMD_PUSH,
                seqId,
                deviceId,
                commandData
        );

        session.getChannel()
                .writeAndFlush(request)
                .addListener(writeFuture -> {
                    if (!writeFuture.isSuccess()) {
                        CompletableFuture<TcpMessage> pending =
                                session.removePendingRequest(seqId);

                        if (pending != null) {
                            pending.completeExceptionally(
                                    writeFuture.cause()
                            );
                        }
                    }
                });

        return future;
    }
}
