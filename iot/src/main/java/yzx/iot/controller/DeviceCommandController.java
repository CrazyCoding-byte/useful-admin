package yzx.iot.controller;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import yzx.iot.service.DeviceCommandService;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @className: DeviceCommandController
 * @author: yzx
 * @date: 2026/10/17 22:18
 * @Version: 1.0
 * @description:
 */
@RestController
@RequestMapping("api/device")
public class DeviceCommandController {

    @Autowired
    private DeviceCommandService deviceCommandService;


    @PostMapping("/{deviceId}/command")
    public CompletableFuture<Map<String, Object>> sendCommand(@PathVariable String deviceId, @RequestBody CommandRequest request) {
        byte[] data = request.getData() == null
                ? new byte[0]
                : request.getData().getBytes(StandardCharsets.UTF_8);
        return deviceCommandService.sendCommand(deviceId, data)
                .thenApply(resp -> Map.of(
                        "seqId", resp.getSeqId(),
                        "deviceId", resp.getDeviceId(),
                        "payload", new String(resp.getPayload(), StandardCharsets.UTF_8)));
    }

    @Data
    public static class CommandRequest {
        private String data;
    }
}
