package com.yzx.usefulagent.controller;

import com.yzx.usefulagent.service.AgentChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * @className: StompWebSocketController
 * @author: yzx
 * @date: 2026/2/23 13:25
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class StompWebSocketController {
    private final AgentChatService agentChatService;

    @MessageMapping("/chat/{userId}")
    @SendTo("/topic/agent/{userId}")
    public String chat(@DestinationVariable String userId, String message) {
        log.info("STOMP 收到用户{}消息：{}", userId, message);
        // 调用带熔断/限流/脱敏的核心服务
        return agentChatService.chat(userId, message);
    }
}
