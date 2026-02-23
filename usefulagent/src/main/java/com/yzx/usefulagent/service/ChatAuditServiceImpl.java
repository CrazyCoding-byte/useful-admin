package com.yzx.usefulagent.service;

import com.yzx.model.agent.ChatAuditLog;
import com.yzx.usefulagent.mapper.ChatAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @className: ChatAuditServiceImpl
 * @author: yzx
 * @date: 2026/2/22 22:11
 * @Version: 1.0
 * @description:
 */
@Service
@RequiredArgsConstructor
public class ChatAuditServiceImpl implements ChatAuditService {
    private final ChatAuditLogMapper auditLogMapper;

    @Override
    public void saveChatLog(String userId, String userMsg, String agentReply, long cost, String status) {
        ChatAuditLog log = new ChatAuditLog();
        log.setUserId(userId);
        log.setUserMessage(userMsg);
        log.setAgentReply(agentReply);
        log.setCostTime((int) cost);
        log.setStatus(status);
        auditLogMapper.insert(log);
    }
}
