package com.yzx.usefulagent.controller;

import com.yzx.usefulagent.utils.EcommerceAgentConfig;
import com.yzx.usefulagent.utils.EcommerceTools;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @className: RawWebSocketController
 * @author: yzx
 * @date: 2026/2/23 12:39
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Component
@ServerEndpoint("/ws/raw/chat/{userId}")
public class RawWebSocketController {
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();
    private static ApplicationContext applicationContext;

    // 注入ApplicationContext（Spring启动时初始化）
    @Autowired
    public void setApplicationContext(ApplicationContext context) {
        RawWebSocketController.applicationContext = context;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        SESSION_MAP.put(userId, session);
        log.info("WebSocket opened for user: {}", userId);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("userId") String userId) {
        log.info("Received message from user {}: {}", userId, message);
        try {
            // 从Spring上下文获取Agent Bean（解决原生WebSocket注入问题）
            EcommerceAgentConfig.EcommerceAgent ecommerceAgent = applicationContext.getBean(EcommerceAgentConfig.EcommerceAgent.class);
            // 调用AI生成回复
            String reply = ecommerceAgent.chat(userId, message);
            // 发送回复给用户
            Session session = SESSION_MAP.get(userId);
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(reply);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
            // 异常兜底回复
            sendErrorReply(userId, "系统繁忙，请稍后再试");
        }
    }

    @OnClose
    public void onClose(@PathParam("userId") String userId, Session session) {
        SESSION_MAP.remove(userId);
        log.info("WebSocket closed for user: {}", userId);
    }

    @OnError
    public void onError(Throwable error, @PathParam("userId") String userId) {
        log.error("WebSocket error for user: {}", userId, error);
        sendErrorReply(userId, "连接异常，请刷新重试");
    }

    // 发送错误回复
    private void sendErrorReply(String userId, String msg) {
        try {
            Session session = SESSION_MAP.get(userId);
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(msg);
            }
        } catch (Exception e) {
            log.error("发送错误回复失败", e);
        }
    }
}