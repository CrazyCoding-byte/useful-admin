package com.yzx.usefulagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzx.model.agent.AgentChatMemory;
import com.yzx.usefulagent.mapper.AgentChatMemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @className: ChatHistoryServiceImpl
 * @author: yzx
 * @date: 2026/2/23 12:02
 * @Version: 1.0
 * @description:
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {
    private final AgentChatMemoryMapper memoryMapper;


    @Override
    public List<Map<String, String>> getUserHistory(String userId, int limit) {
        List<AgentChatMemory> memoryList = memoryMapper.selectList(
                new LambdaQueryWrapper<AgentChatMemory>()
                        .eq(AgentChatMemory::getUserId, userId)
                        .orderByAsc(AgentChatMemory::getCreateTime)
                        .last("limit " + limit)
        );

        List<Map<String, String>> historyList = new ArrayList<>();
        for (AgentChatMemory memory : memoryList) {
            Map<String, String> historyMap = new HashMap<>();
            historyMap.put("role", memory.getRole());
            historyMap.put("content", memory.getContent());
            historyList.add(historyMap);
        }

        // 修复：返回组装好的列表，不是空List
        return historyList;
    }
}
