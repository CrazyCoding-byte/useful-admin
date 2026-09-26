package yzx.iot.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import yzx.iot.exchange.DeviceMessage;
import yzx.iot.exchange.NettyDeviceExchange;
import yzx.iot.exchange.converter.ProtocolMessageConverter;
import yzx.iot.exchange.converter.TcpMessageConverter;
import yzx.iot.protocol.TcpMessage;

/**
 * @className: ExchangeBridgeHandler
 * @author: yzx
 * @date: 2026/9/25 19:32
 * @Version: 1.0
 * @description:
 * 为什么用`SimpleChannelInboundHandler` ？ 它消费完消息会自动释放`ByteBuf` 引用计数
 * TcpMessage->DeviceMessage
 */
public class ExchangeBridgeHandler extends SimpleChannelInboundHandler<TcpMessage> {
    private final NettyDeviceExchange exchange;
    private final ProtocolMessageConverter<TcpMessage> converter;

    public ExchangeBridgeHandler(NettyDeviceExchange exchange, ProtocolMessageConverter<TcpMessage> tcpMessageConverter) {
        this.exchange = exchange;
        this.converter = tcpMessageConverter;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TcpMessage msg) throws Exception {
        exchange.fireInbound(converter.convert(msg));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // 边界处兜底，异常不能烂在链上 cause.printStackTrace();
        cause.printStackTrace();
        ctx.close();
    }
}
