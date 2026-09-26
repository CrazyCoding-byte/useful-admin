package yzx.iot.bootstrap;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import yzx.iot.exchange.DeviceExchange;
import yzx.iot.processor.DeviceBusinessProcessor;
import yzx.iot.spi.ServerTransport;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @className: TransportBootStrap
 * @author: yzx
 * @date: 2026/9/25 23:32
 * @Version: 1.0
 * @description:
 */
@Component
public class TransportBootStrap implements ApplicationRunner {
    private final List<ServerTransport> transports;
    private final DeviceBusinessProcessor processor;

    public TransportBootStrap(List<ServerTransport> transports, DeviceBusinessProcessor processor) {
        this.transports = transports;
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Map<String, ServerTransport> transportMap = transports.stream().collect(Collectors.toMap(ServerTransport::name, Function.identity()));

    }

    private void startIfPresent(Map<String, ServerTransport> map, String name, Class<? extends ServerTransport> type) {
        ServerTransport serverTransport = map.get(name);
        if (serverTransport == null) {
            System.out.println("未找到传输层:" + name);
            return;
        }
        int port;
        try {
            port = (int) type.getMethod("getPort").invoke(serverTransport);
        } catch (Exception e) {
            throw new RuntimeException("读取端口配置失败:" + name, e);
        }
        //在这里定义"平台拿到通道之后做什么"——绑定统一的业务处理器
        serverTransport.start(port, (DeviceExchange exchange) -> {
            exchange.onInbound(msg -> processor.handle(exchange, msg));
        });
    }

    @PreDestroy
    public void shutdown() {
        transports.forEach(ServerTransport::stop);
    }
}
