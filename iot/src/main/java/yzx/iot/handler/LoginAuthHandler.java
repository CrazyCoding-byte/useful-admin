package yzx.iot.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import yzx.iot.deviceneum.CmdType;
import yzx.iot.exchange.NettyDeviceExchange;
import yzx.iot.protocol.TcpMessage;
import yzx.iot.session.DeviceSession;
import yzx.iot.session.SessionManager;
import yzx.iot.state.DeviceStateManager;
import yzx.iot.utils.AttributeKeys;

/**
 * @className: LoginAuthHandler
 * @author: yzx
 * @date: 2026/9/1 9:06
 * @Version: 1.0
 * @description:登录认证：只有登录成功后才能进入业务处理器
 */
public class LoginAuthHandler extends ChannelInboundHandlerAdapter {
    private final DeviceStateManager stateManager;

    public LoginAuthHandler(DeviceStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TcpMessage message = (TcpMessage) msg;
        NettyDeviceExchange exchange = ctx.channel().attr(AttributeKeys.DEVICE_EXCHANGE).get();

        //已登录的同一连续又发登录帧=重复登录
        if (exchange.deviceId() != null && message.getCmdType() == CmdType.LOGIN_REQ) {
            sendLoginResp(ctx, message, (byte) 0x02, "重复登录");
            return;
        }

        if (exchange.deviceId() == null) {
            if (message.getCmdType() != CmdType.LOGIN_REQ) {
                ctx.close();
                return;
            }
            String deviceId = message.getDeviceId();
            if (deviceId == null || deviceId.length() < 5) {
                sendLoginResp(ctx, message, (byte) 0x01, "设备ID非法");
                ctx.close();
                return;
            }
            //入注册表
            stateManager.login(deviceId, exchange);
            exchange.setDeviceId(deviceId);
            sendLoginResp(ctx, message, (byte) 0x00, "登录成功");
            return;
        }
        super.channelRead(ctx, msg);
    }

    private void sendLoginResp(ChannelHandlerContext ctx, TcpMessage req, byte code, String msg) {
        byte[] payload = new byte[1 + msg.getBytes().length];
        payload[0] = code;
        System.arraycopy(msg.getBytes(), 0, payload, 1, msg.getBytes().length);
        TcpMessage resp = new TcpMessage(CmdType.LOGIN_RESP, req.getSeqId(), req.getDeviceId(), payload);
        ctx.writeAndFlush(resp);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        SessionManager.INSTANCE.remove(ctx.channel());
        super.channelInactive(ctx);
    }
}
