package com.yzx.common.mqlocalmessage;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 通用本地消息表实体（无MQ依赖，全项目通用）
 */
@Data
@TableName("mq_message")
public class MqMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 全局唯一消息ID
     */
    private String msgId;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 消息/任务内容
     */
    private String content;

    /**
     * 状态 0-待确认 1-已完成 2-失败
     */
    private Integer status;

    /**
     * 交换机（MQ专用，不用MQ可为null）
     */
    private String exchange;

    /**
     * 路由键（MQ专用，不用MQ可为null）
     */
    private String routingKey;

    /**
     * 重试次数
     */
    private Integer retryCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}