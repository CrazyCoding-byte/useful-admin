package yzx.iot.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import yzx.iot.Codec.TcpFrameDecoder;
import yzx.iot.Codec.TcpMessageDecoder;
import yzx.iot.Codec.TcpMessageEncoder;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.exchange.NettyDeviceExchange;
import yzx.iot.exchange.converter.TcpMessageConverter;
import yzx.iot.handler.ExchangeBridgeHandler;
import yzx.iot.handler.FlowControlHandler;
import yzx.iot.handler.HeartbeatHandler;
import yzx.iot.handler.LoginAuthHandler;
import yzx.iot.spi.ServerTransport;
import yzx.iot.utils.AttributeKeys;

import java.util.function.Consumer;

/**
 * @className: TcpTransport
 * @author: yzx
 * @date: 2026/9/25 22:55
 * @Version: 1.0
 * @description:
 */
@Component
@Getter
public class TcpTransport implements ServerTransport {
    @Getter
    @Value("${iot.tcp.port:8080}")
    private int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final TcpMessageConverter tcpMessageConverter = new TcpMessageConverter();

    @Override
    public String name() {
        return "private-tcp";
    }

    @Override
    public void start(int port, Consumer<DeviceExchange> exchangeFactory) {
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true).childOption(ChannelOption.TCP_NODELAY, true).childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
// 1. 创建这条连接的通道
                    NettyDeviceExchange exchange = new NettyDeviceExchange(ch);
                    ch.attr(AttributeKeys.DEVICE_EXCHANGE).set(exchange);
                    // 2. 装配 TCP 私有管线
                    ch.pipeline().addLast("frameDecoder", new TcpFrameDecoder());
                    ch.pipeline().addLast("messageDecoder", new TcpMessageDecoder());
                    ch.pipeline().addLast("messageEncoder", new TcpMessageEncoder());
                    ch.pipeline().addLast("idleState", new IdleStateHandler(30, 0, 0));
                    ch.pipeline().addLast("flowControl", new FlowControlHandler());
                    ch.pipeline().addLast("loginAuth", new LoginAuthHandler());
                    ch.pipeline().addLast("heartbeat", new HeartbeatHandler());
                    ch.pipeline().addLast("bridge", new ExchangeBridgeHandler(exchange, tcpMessageConverter)); // 3. 交给平台：平台决定消息送给谁，传输层不关心 exchangeFactory.accept(exchange);
                }
            });
            serverChannel = serverBootstrap.bind(port).sync().channel();
            System.out.println("[" + name() + "] 传输层启动，监听端口: " + port);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("传输层启动失败: " + name(), e);
        }
    }

    @Override
    public void stop() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }

}
