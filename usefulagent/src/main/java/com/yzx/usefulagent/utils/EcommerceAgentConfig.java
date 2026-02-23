package com.yzx.usefulagent.utils;

import com.yzx.usefulagent.mapper.AgentChatMemoryMapper;
import com.yzx.usefulagent.service.MySQLChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @className: EcommerceAgentConfig
 * @author: yzx
 * @date: 2026/2/22 21:03
 * @Version: 1.0
 * @description:
 */
@Configuration
@RequiredArgsConstructor
public class EcommerceAgentConfig {
    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.model-name}")
    private String ollamaModelName;

    @Value("${agent.system-prompt}")
    private String systemPrompt;
    private final EcommerceTools ecommerceTools;
    private final AgentChatMemoryMapper memoryMapper;

    @Bean
    public ChatLanguageModel ollamaChatModel() {
        return OllamaChatModel.builder().
                baseUrl(ollamaBaseUrl).
                modelName(ollamaModelName).
                temperature(0.3).timeout(Duration.ofSeconds(30)).maxRetries(2).build();
    }

    // 只保留一个核心Agent Bean（删除重复的agentService）
    @Bean
    public EcommerceAgent ecommerceAgent(ChatLanguageModel ollamaChatModel) {
        return AiServices.builder(EcommerceAgent.class).
                chatLanguageModel(ollamaChatModel).//绑定大模型
                tools(ecommerceTools)//绑定工具集
                // 修复：MySQLChatMemory需要传userId和memoryMapper，不是15
                .chatMemoryProvider(userId -> new MySQLChatMemory(String.valueOf(userId), memoryMapper))//绑定记忆
                .systemMessageProvider(id -> systemPrompt).build();
    }

    // Agent接口定义
    public interface EcommerceAgent {
        String chat(@MemoryId String userId, @UserMessage String userMessage);
    }
}
