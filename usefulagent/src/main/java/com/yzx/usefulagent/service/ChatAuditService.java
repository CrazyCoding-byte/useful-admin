package com.yzx.usefulagent.service;

/**
 * @className: ChatAuditService
 * @author: yzx
 * @date: 2026/2/22 22:10
 * @Version: 1.0
 * @description:
 */ 
public interface ChatAuditService {
    void saveChatLog(String userId, String userMsg, String agentReply, long cost, String status);
}
