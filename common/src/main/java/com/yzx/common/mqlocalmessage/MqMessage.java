package com.yzx.common.mqlocalmessage;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mq_message")
public class MqMessage {
    @TableId("message_id")
    private String msgId;

    private String appName;          // 新增

    private String content;

    @TableField("to_exchange")       // 若字段已改名
    private String exchange;

    @TableField("routing_key")
    private String routingKey;

    @TableField("class_type")        // 对应业务类型
    private String bizType;

    @TableField("message_status")
    private Integer status;

    @TableField("retry_count")       // 新增
    private Integer retryCount;

    @TableField("last_retry_time")   // 新增
    private LocalDateTime lastRetryTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}