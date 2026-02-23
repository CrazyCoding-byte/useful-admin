package com.yzx.model.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_audit_log")
public class ChatAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String userMessage;
    private String agentReply;
    private Integer costTime;
    private String status;
    private LocalDateTime createTime;
}