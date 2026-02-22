package com.yzx.usefulagent.service;

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

    }
}
