package yzx.iot.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
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
        Map<String, ServerTransport> collect = transports.stream().collect(Collectors.toMap(ServerTransport::name, Function.identity()));
        
    }
}
