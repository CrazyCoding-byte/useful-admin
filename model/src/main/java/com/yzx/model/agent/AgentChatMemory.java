package com.yzx.model.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @className: AgentChatMemory
 * @author: yzx
 * @date: 2026/2/22 22:19
 * @Version: 1.0
 * @description:
 */
@Data
@TableName("agent_chat_memory")
public class AgentChatMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}
