package yzx.iot.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import yzx.iot.Codec.TcpFrameDecoder;
import yzx.iot.Codec.TcpMessageDecoder;
import yzx.iot.Codec.TcpMessageEncoder;
import yzx.iot.handler.BusinessHandler;
import yzx.iot.handler.FlowControlHandler;
import yzx.iot.handler.HeartbeatHandler;
import yzx.iot.handler.LoginAuthHandler;

/**
 * @className: NettyBasicServer
 * @author: yzx
 * @date: 2026/8/30 13:53
 * @Version: 1.0
 * @description:
 */
public class NettyBasicServer {
    public static void main(String[] args) {
        //bootstrap
        MultiThreadIoEventLoopGroup boosGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        //workStrap
        MultiThreadIoEventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(boosGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast("frameDecoder", new TcpFrameDecoder());
                            ch.pipeline().addLast("messageDecoder", new TcpMessageDecoder());
                            ch.pipeline().addLast("messageEncoder", new TcpMessageEncoder());
                            ch.pipeline().addLast("idleState", new IdleStateHandler(30, 0, 0));
                            ch.pipeline().addLast("flowControl", new FlowControlHandler());
                            ch.pipeline().addLast("loginAuth", new LoginAuthHandler());
                            ch.pipeline().addLast("heartbeat", new HeartbeatHandler());
                            ch.pipeline().addLast("business", new BusinessHandler());
                        }
                    });
            ChannelFuture channelFuture = serverBootstrap.bind(8080).sync();
            System.out.println(channelFuture.channel().localAddress());
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            boosGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
