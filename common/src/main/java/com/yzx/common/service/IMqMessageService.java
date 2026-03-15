package com.yzx.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.common.enums.BizTypeEnum;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;

import java.util.List;

/**
 * 通用本地消息表服务（无MQ依赖）
 */
public interface IMqMessageService extends IService<MqMessage> {

    /**
     * 保存消息/任务（通用）
     */
    boolean saveMessage(String msgId, BizTypeEnum bizType, String content);

    /**
     * 保存MQ专用消息（带交换机+路由键）
     */
    boolean saveMqMessage(String msgId, BizTypeEnum bizType, String content, String exchange, String routingKey);

    /**
     * 更新消息状态
     */
    boolean updateStatus(String msgId, MessageStatusEnum status);

    /**
     * 增加重试次数
     */
    boolean incrementRetry(String msgId);

    /**
     * 查询失败消息
     */
    List<MqMessage> getFailMessage(int maxRetry);

    void markAsDead(String msgId);
}