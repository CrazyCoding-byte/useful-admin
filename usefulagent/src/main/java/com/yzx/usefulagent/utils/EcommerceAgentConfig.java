package com.yzx.usefulagent.utils;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @className: EcommerceAgentConfig
 * @author: yzx
 * @date: 2026/2/22 21:03
 * @Version: 1.0
 * @description:
 */
@Configuration
public class EcommerceAgentConfig {
    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.model-name}")
    private String ollamaModelName;

    @Value("${agent.system-prompt}")
    private String systemPrompt;

    @Bean
    public ChatLanguageModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .temperature(0.3) // 商用调低随机性，避免胡说八道
                .timeout(java.time.Duration.ofSeconds(30))
                .maxRetries(2) // 内置重试
                .build();
    }

    // 单例Agent服务，整个服务只用这一个实例
    @Bean
    public AgentService agentService(ChatLanguageModel ollamaChatModel, EcommerceTools ecommerceTools) {
        return AiServices.builder(AgentService.class)
                .chatLanguageModel(ollamaChatModel)
                .tools(ecommerceTools)
                .chatMemoryProvider(userId -> new MySQLChatMemory(userId, 15)) // 记住最近15轮对话
                .systemMessageProvider(id -> systemPrompt) // 话术写在配置文件里，运营能改
                .build();
    }

    // Agent接口定义
    public interface AgentService {
        String chat(@MemoryId String userId, @UserMessage String userMessage);
    }
}
