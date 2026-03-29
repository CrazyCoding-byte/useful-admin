package cn.poile.ucs.auth.handler;

import cn.hutool.core.util.IdUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import cn.poile.ucs.auth.constant.RedisKeyConstant;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 网关扫码登录处理器（WebFlux 响应式 + Java8 兼容）
 */
@Component
public class ScanCodeHandler {

    private final ReactiveStringRedisTemplate redisTemplate;

    public ScanCodeHandler(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ====================== 1. 生成二维码 ======================
    public Mono<ServerResponse> create(ServerRequest request) {
        String scene = IdUtil.simpleUUID();
        String key = RedisKeyConstant.SCAN_SCENE + scene;

        // 生成二维码
        String base64 = QrCodeUtil.generateAsBase64(scene, new QrConfig(300, 300), "png");
        String qrBase64 = "data:image/png;base64," + base64;

        // 存入Redis
        return redisTemplate.opsForValue().set(key, "0", java.time.Duration.ofSeconds(RedisKeyConstant.EXPIRE))
                .flatMap(success -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("code", 200);
                    map.put("scene", scene);
                    map.put("qrCode", qrBase64);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue(map));
                });
    }

    // ====================== 2. 小程序确认扫码 ======================
    public Mono<ServerResponse> confirm(ServerRequest request) {
        String scene = request.queryParam("scene").orElse("");
        String uid = request.queryParam("uid").orElse("");
        String key = RedisKeyConstant.SCAN_SCENE + scene;

        return redisTemplate.opsForValue().get(key)
                .flatMap(status -> {
                    Map<String, Object> result = new HashMap<>();
                    if (!"0".equals(status)) {
                        result.put("code", 400);
                        result.put("msg", "已扫码或已过期");
                        return ServerResponse.ok().bodyValue(result);
                    }
                    // 更新扫码状态
                    return redisTemplate.opsForValue().set(key, uid, java.time.Duration.ofSeconds(RedisKeyConstant.EXPIRE))
                            .flatMap(s -> {
                                Map<String, Object> successMap = new HashMap<>();
                                successMap.put("code", 200);
                                successMap.put("msg", "扫码成功");
                                return ServerResponse.ok().bodyValue(successMap);
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 空值处理（二维码过期）
                    Map<String, Object> emptyMap = new HashMap<>();
                    emptyMap.put("code", 400);
                    emptyMap.put("msg", "二维码已过期");
                    return ServerResponse.ok().bodyValue(emptyMap);
                }));
    }

    // ====================== 3. PC轮询状态 ======================
    public Mono<ServerResponse> status(ServerRequest request) {
        String scene = request.queryParam("scene").orElse("");
        String key = RedisKeyConstant.SCAN_SCENE + scene;

        return redisTemplate.opsForValue().get(key)
                .flatMap(uid -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("code", 200);
                    if ("0".equals(uid)) {
                        map.put("status", "WAITING");
                        map.put("msg", "待扫码");
                    } else {
                        map.put("status", "SUCCESS");
                        map.put("uid", uid);
                        map.put("msg", "扫码成功");
                    }
                    return ServerResponse.ok().bodyValue(map);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    Map<String, Object> expiredMap = new HashMap<>();
                    expiredMap.put("code", 400);
                    expiredMap.put("status", "EXPIRED");
                    expiredMap.put("msg", "二维码已过期");
                    return ServerResponse.ok().bodyValue(expiredMap);
                }));
    }
}