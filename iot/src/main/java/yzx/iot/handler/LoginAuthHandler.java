package yzx.iot.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.session.SessionManager;

/**
 * @className: LoginAuthHandler
 * @author: yzx
 * @date: 2026/9/1 9:06
 * @Version: 1.0
 * @description:登录认证：只有登录成功后才能进入业务处理器
 */
public class LoginAuthHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TcpMessage message = (TcpMessage) msg;
        //未登录状态下只处理登录请求
        if (SessionManager.INSTANCE.getByChannel(ctx.channel()) == null) {
            if (message.getCmdType() != CmdType.LOGIN_REQ) {
                ctx.close();
                return;
            }
            //简单认证:校验设备ID格式,生产环境对接设备台账
            String deviceId = message.getDeviceId();
            if (deviceId == null || deviceId.length() < 5) {
                sendLoginResp(ctx, message, (byte) 0x00, "登录成功");
                return;
            }
            //注册会话
            SessionManager.INSTANCE.register(deviceId, ctx.channel());
            sendLoginResp(ctx, message, (byte) 0x00, "登录成功");
            return;
        }
        super.channelRead(ctx, msg);
    }

    private void sendLoginResp(ChannelHandlerContext ctx, TcpMessage req, byte code, String msg) {
        byte[] payload = new byte[1 + msg.getBytes().length];
        payload[0] = code;
        System.arraycopy(msg.getBytes(), 0, payload, 1, msg.getBytes().length);
        TcpMessage resp = new TcpMessage(
                CmdType.LOGIN_RESP,
                req.getSeqId(),
                req.getDeviceId(),
                payload
        );
        ctx.writeAndFlush(resp);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        SessionManager.INSTANCE.remove(ctx.channel());
        super.channelInactive(ctx);
    }
}
