package yzx.iot.processor;

import lombok.extern.slf4j.Slf4j;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.DeviceMessage;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.session.DeviceSession;
import yzx.iot.session.SessionManager;

import java.util.concurrent.*;

/**
 * @className: DeviceBusinessProcessor
 * @author: yzx
 * @date: 2026/9/25 19:46
 * @Version: 1.0
 * @description:
 */
@Slf4j
public class DeviceBusinessProcessor {
    private static final ExecutorService BUSINESS_EXECUTOR = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 4,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10000),
            r -> {
                Thread t = new Thread(r, "business-worker");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public void onMessage(DeviceExchange exchange, Object raw) {
        if (!(raw instanceof DeviceMessage message)) {
            log.warn("未知消息类型{}", raw.getClass());
            return;
        }
        //fireInbound 运行在Netty IO线程,这里立刻丢业务池,绝不堵住IO线程
        BUSINESS_EXECUTOR.submit(() -> {
            try {
                processor(exchange, message);
            } catch (Exception e) {
                log.error("业务处理异常 deviceId={}", message.getDeviceId(), e);
            }
        });
    }

    private void processor(DeviceExchange exchange, DeviceMessage message) {
        switch (message.getCmdType()) {
            case "DATA_REPORT" -> handlerDataReport(exchange, message);
            case "CMD_PUSH_RESP" -> handlerCmdPushResp(message);
            default -> log.warn("未知指令 cmdType={} deviceId={}", message.getCmdType(), message.getDeviceId());
        }
    }

    private void handlerDataReport(DeviceExchange exchange, DeviceMessage message) {
        //todo 真实业务:入库,规则引擎
        byte success = 0x00;
        TcpMessage response = new TcpMessage(
                CmdType.DATA_REPORT_RESP,
                message.getSeqId(),
                message.getDeviceId(),
                new byte[]{success}
        );
        exchange.sendOutbound(response);
    }

    private void handlerCmdPushResp(DeviceMessage message) {
        DeviceSession deviceSession = SessionManager.INSTANCE.get(message.getDeviceId());
        if (deviceSession == null) {
            log.warn("响应到达但会话不存在,seqId={}", message.getSeqId());
            return;
        }
        //统一消息要唤醒future,需要转回协议消息,配对只用到seqId
        //所以这里直接用 seqId 完成配对即可(future)里的Tcpmessage
        CompletableFuture<TcpMessage> pending = deviceSession.removePendingRequest(message.getSeqId());
        if (pending != null) {
            log.warn("未找到待响应请求,seqId={},deviceId={}", message.getSeqId(), message.getDeviceId());
            return;
        }
        pending.complete(new TcpMessage(
                CmdType.CMD_PUSH_RESP, message.getSeqId(),
                message.getDeviceId(), message.getPayload()
        ));
    }
}
