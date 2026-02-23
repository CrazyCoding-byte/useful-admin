package com.yzx.usefulagent.service;

import com.yzx.usefulagent.utils.EcommerceAgentConfig;
import com.yzx.usefulagent.utils.SensitiveDataMasker;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @className: AgentChatService
 * @author: yzx
 * @date: 2026/2/22 21:44
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Service
public class AgentChatService {
    @Autowired
    private EcommerceAgentConfig.EcommerceAgent ecommerceAgent; // 修复：改用统一的EcommerceAgent
    @Autowired
    private SensitiveDataMasker masker;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChatAuditService auditService;

    // 用户级限流：每分钟最多20条消息
    private static final String USER_RAET_LIMIT_KEY = "agent:rate:user:";

    /**
     * 核心聊天入口：加了限流、熔断、脱敏、审计
     */
    @CircuitBreaker(name = "ollamaCircuitBreaker", fallbackMethod = "chatFallback")
    @RateLimiter(name = "userRateLimiter", fallbackMethod = "rateLimitFallback")
    public String chat(String userId, String userMessage) {
        long startTime = System.currentTimeMillis();
        log.info("[用户对话] 用户ID：{}，消息：{}", userId, userMessage);

        // 1. 用户级限流防刷
        String rateKey = USER_RAET_LIMIT_KEY + userId;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count == 1) {
            redisTemplate.expire(rateKey, 1, TimeUnit.MINUTES);
        }
        if (count > 20) {
            return "你发送消息太快了，请稍后再试~";
        }

        // 2. 敏感数据脱敏，再传给大模型
        String maskedMessage = masker.mask(userMessage);

        // 3. 调用Agent核心引擎
        String reply = ecommerceAgent.chat(userId, maskedMessage);

        // 4. 全链路审计入库（商用必须）
        long costTime = System.currentTimeMillis() - startTime;
        auditService.saveChatLog(userId, userMessage, reply, costTime, "SUCCESS");

        log.info("[Agent回复] 用户ID：{}，回复：{}，耗时：{}ms", userId, reply, costTime);
        return reply;
    }
}
