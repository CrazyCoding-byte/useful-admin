package yzx.iot.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import yzx.iot.exchange.DeviceMessage;
import yzx.iot.exchange.NettyDeviceExchange;
import yzx.iot.exchange.converter.TcpMessageConverter;
import yzx.iot.protocol.TcpMessage;

/**
 * @className: ExchangeBridgeHandler
 * @author: yzx
 * @date: 2026/9/25 19:32
 * @Version: 1.0
 * @description:
 * 为什么用`SimpleChannelInboundHandler` ？ 它消费完消息会自动释放`ByteBuf` 引用计数
 */
public class ExchangeBridgeHandler extends SimpleChannelInboundHandler<TcpMessage> {
    private final NettyDeviceExchange exchange;
    private final TcpMessageConverter tcpMessageConverter = new TcpMessageConverter();

    public ExchangeBridgeHandler(NettyDeviceExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TcpMessage msg) throws Exception {
        //1.协议信息翻译统一消息
        DeviceMessage deviceMessage = tcpMessageConverter.toDeviceMessage(msg);
        //2.投递进通道 注意这里不写任何逻辑
        exchange.fireInbound(deviceMessage);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // 边界处兜底，异常不能烂在链上 cause.printStackTrace();
        ctx.close();
    }
}
