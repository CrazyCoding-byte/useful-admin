package yzx.iot.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.session.DeviceSession;
import yzx.iot.session.SessionManager;

/**
 * @className: HeartbeatHandler
 * @author: yzx
 * @date: 2026/8/30 15:56
 * @Version: 1.0
 * @description: 心跳处理器 读超时30秒断开连接,心跳请求直接在io线响应,不进业务池
 */
public class HeartbeatHandler extends ChannelDuplexHandler {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                //30秒没收到任何数据,判定为死连接,主动断开
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TcpMessage message = (TcpMessage) msg;
        if (message.getCmdType() == CmdType.HEARTBEAT_REQ) {
            DeviceSession session = SessionManager.INSTANCE.getByChannel(ctx.channel());
            if (session != null) {
                session.setLastHeartbeatTime(System.currentTimeMillis());
            }
            //响应心跳
            TcpMessage heartbeat = new TcpMessage(CmdType.HEARTBEAT_RESP,
                    message.getSeqId(),
                    message.getDeviceId(),
                    null);
            ctx.writeAndFlush(heartbeat);
            return;
        }
        super.channelRead(ctx, msg);
    }
}
