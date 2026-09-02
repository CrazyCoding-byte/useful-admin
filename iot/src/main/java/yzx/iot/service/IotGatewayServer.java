package yzx.iot.service;

import io.netty.channel.MultiThreadIoEventLoopGroup;

/**
 * @className: IotGatewayServer
 * @author: yzx
 * @date: 2026/9/1 10:47
 * @Version: 1.0
 * @description:
 */
public class IotGatewayServer {
    private final int port;
    private MultiThreadIoEventLoopGroup bossGroup;
    private MultiThreadIoEventLoopGroup workerGroup;

    public IotGatewayServer(int port) {
        this.port = port;
    }

    public void start() {

    }
}
