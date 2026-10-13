package yzx.iot.bootstrap;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import yzx.iot.filter.dispatcher.DeviceMessageDispatcher;
import yzx.iot.spi.ServerTransport;
import yzx.iot.bootstrap.transport.TcpTransport;

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
    private final DeviceMessageDispatcher dispatcher;

    public TransportBootStrap(List<ServerTransport> transports, DeviceMessageDispatcher dispatcher) {
        this.transports = transports;
        this.dispatcher = dispatcher;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        transports.forEach(s -> {
            s.start(s.port(), exchange -> exchange.onInbound(msg -> dispatcher.dispatch(exchange, msg)));
        });
    }


    @PreDestroy
    public void shutdown() {
        transports.forEach(ServerTransport::stop);
    }
}
