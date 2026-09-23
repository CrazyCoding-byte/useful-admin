package yzx.iot.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.session.DeviceSession;
import yzx.iot.session.SessionManager;

import java.util.concurrent.*;

/**
 * @className: BusinessHandler
 * @author: yzx
 * @date: 2026/8/30 15:56
 * @Version: 1.0
 * @description:
 */
@Slf4j
public class BusinessHandler extends SimpleChannelInboundHandler<TcpMessage> {


    private static final ExecutorService BUSINESS_EXECUTOR = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2, Runtime.getRuntime().availableProcessors() * 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10000), r -> {
        Thread t = new Thread(r, "business-worker-" + r.hashCode());
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     *
     * @param ctx
     * @param tcpMessage
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TcpMessage tcpMessage) throws Exception {
        BUSINESS_EXECUTOR.submit(() -> {
            try {
                processBusiness(ctx, tcpMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void processBusiness(ChannelHandlerContext ctx, TcpMessage tcpMessage) throws Exception {
        DeviceSession session = SessionManager.INSTANCE.getByChannel(ctx.channel());
        if (session == null) return;
        switch (tcpMessage.getCmdType()) {
            //数据上报
            case DATA_REPORT -> {
                handleDataReport(ctx, tcpMessage, session);
                break;
            }
            //指令下发
            case CMD_PUSH_RESP -> {
                handleCmdPushResp(tcpMessage, session);
                break;
            }
            default -> {
                log.warn("receive unknown cmdType:{} deviceId:{}", tcpMessage.getCmdType(), session.getDeviceId());
                break;
            }
        }
    }

    /**
     * 处理数据上报
     * @param ctx
     * @param tcpMessage
     * @param session
     * @throws Exception
     */
    private void handleDataReport(ChannelHandlerContext ctx, TcpMessage tcpMessage, DeviceSession session) throws Exception {
        byte[] payload = tcpMessage.getPayload();
        //todo 实际的业务
        ctx.channel().eventLoop().execute(() -> {
            TcpMessage resp = new TcpMessage(CmdType.DATA_REPORT_RESP,
                    tcpMessage.getSeqId(),
                    tcpMessage.getDeviceId(),
                    new byte[]{0x00} // 成功
            );
            ctx.writeAndFlush(resp);
        });
    }

    /**
     * 处理指令下发响应
     * @param tcpMessage
     * @param session
     * @throws Exception
     */
    private void handleCmdPushResp(TcpMessage tcpMessage, DeviceSession session) throws Exception {
// 根据序列号匹配对应的请求，唤醒等待的Future
        // TODO: 异步请求-响应匹配逻辑
        CompletableFuture<TcpMessage> tcpMessageCompletableFuture = session.removePendingRequest(tcpMessage.getSeqId());
        if (tcpMessageCompletableFuture == null) {
            log.warn(
                    "未找到待响应请求, seqId={}, deviceId={}",
                    tcpMessage.getSeqId(),
                    session.getDeviceId()
            );
            return;
        }
        tcpMessageCompletableFuture.complete(tcpMessage);
    }


}
