//package yzx.iot.service;
//
//import io.netty.bootstrap.ServerBootstrap;
//import io.netty.channel.*;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.nio.NioIoHandler;
//import io.netty.channel.socket.SocketChannel;
//import io.netty.channel.socket.nio.NioServerSocketChannel;
//import io.netty.handler.timeout.IdleStateHandler;
//import yzx.iot.Codec.TcpFrameDecoder;
//import yzx.iot.Codec.TcpMessageDecoder;
//import yzx.iot.Codec.TcpMessageEncoder;
//import yzx.iot.exchange.NettyDeviceExchange;
//import yzx.iot.handler.*;
//import yzx.iot.processor.DeviceBusinessProcessor;
//import yzx.iot.utils.AttributeKeys;
//
///**
// * @className: NettyBasicServer
// * @author: yzx
// * @date: 2026/8/30 13:53
// * @Version: 1.0
// * @description:
// */
//public class NettyBasicServer {
//    public static void main(String[] args) {
//        //bootstrap
//        MultiThreadIoEventLoopGroup boosGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
//        //workStrap
//        MultiThreadIoEventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
//        DeviceBusinessProcessor processor = new DeviceBusinessProcessor();
//        try {
//            ServerBootstrap serverBootstrap = new ServerBootstrap();
//            serverBootstrap.group(boosGroup, workerGroup)
//                    .channel(NioServerSocketChannel.class)
//                    .option(ChannelOption.SO_BACKLOG, 128)
//                    .childOption(ChannelOption.SO_KEEPALIVE, true)
//                    .childOption(ChannelOption.TCP_NODELAY, true)
//                    .childHandler(new ChannelInitializer<SocketChannel>() {
//                        @Override
//                        protected void initChannel(SocketChannel ch) {
//                            // ① 一条连接 = 一个 Exchange
//                            NettyDeviceExchange exchange = new NettyDeviceExchange(ch);
//                            // ② 注册上行监听器：消息过桥后，最终进到业务处理器
//                            exchange.onInbound(msg -> processor.onMessage(exchange, msg));
//                            // ③ 挂到 channel 属性上，登录 Handler 要用
//                            ch.attr(AttributeKeys.DEVICE_EXCHANGE).set(exchange);
//                            ch.pipeline().addLast("frameDecoder", new TcpFrameDecoder());
//                            ch.pipeline().addLast("messageDecoder", new TcpMessageDecoder());
//                            ch.pipeline().addLast("messageEncoder", new TcpMessageEncoder());
//                            /**
//                             * readerIdleTime  = 30 秒
//                             writerIdleTime  = 0，不检测写空闲
//                             allIdleTime     = 0，不检测总空闲
//                             */
//                            ch.pipeline().addLast("idleState", new IdleStateHandler(30, 0, 0));
//                            ch.pipeline().addLast("flowControl", new FlowControlHandler());
//                            ch.pipeline().addLast("loginAuth", new LoginAuthHandler());
//                            ch.pipeline().addLast("heartbeat", new HeartbeatHandler());
//                            // ③ 原来的 BusinessHandler 删除，换成桥
//                            ch.pipeline().addLast("bridge",new ExchangeBridgeHandler(exchange));
//                        }
//                    });
//            ChannelFuture channelFuture = serverBootstrap.bind(8080).sync();
//            System.out.println(channelFuture.channel().localAddress());
//            channelFuture.channel().closeFuture().sync();
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            boosGroup.shutdownGracefully();
//            workerGroup.shutdownGracefully();
//        }
//    }
//}
