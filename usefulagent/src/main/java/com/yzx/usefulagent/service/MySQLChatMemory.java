package com.yzx.usefulagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzx.model.agent.AgentChatMemory;
import com.yzx.usefulagent.mapper.AgentChatMemoryMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static io.lettuce.core.pubsub.PubSubOutput.Type.message;

/**
 * @className: MySQLChatMemory
 * @author: yzx
 * @date: 2026/2/23 12:11
 * @Version: 1.0
 * @description:
 */
@RequiredArgsConstructor
public class MySQLChatMemory implements ChatMemory {
    private final AgentChatMemoryMapper memoryMapper;
    private final String userId;
    private static final int MAX_MESSAGES = 15;

    // 自定义构造器（唯一构造器，避免冲突）
    public MySQLChatMemory(String userId, AgentChatMemoryMapper memoryMapper) {
        this.userId = userId;
        this.memoryMapper = memoryMapper;
    }

    @Override
    public Object id() {
        return userId;
    }

    @Override
    public void add(ChatMessage chatMessage) {
        AgentChatMemory entity = new AgentChatMemory();
        entity.setUserId(userId);
        entity.setRole(chatMessage instanceof UserMessage ? "user" : "assistant");
        entity.setContent(chatMessage.text());
        memoryMapper.insert(entity);
    }

    @Override
    public List<ChatMessage> messages() {
        List<AgentChatMemory> dbList = memoryMapper.selectList(
                new LambdaQueryWrapper<AgentChatMemory>()
                        .eq(AgentChatMemory::getUserId, userId)
                        .orderByAsc(AgentChatMemory::getCreateTime)
                        .last("LIMIT " + MAX_MESSAGES)
        );

        List<ChatMessage> messages = new ArrayList<>();
        for (AgentChatMemory m : dbList) {
            if ("user".equals(m.getRole())) {
                messages.add(UserMessage.from(m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                messages.add(AiMessage.from(m.getContent()));
            }
        }
        return messages;
    }

    @Override
    public void clear() {
        memoryMapper.delete(
                new LambdaQueryWrapper<AgentChatMemory>()
                        .eq(AgentChatMemory::getUserId, userId)
        );
    }
}
