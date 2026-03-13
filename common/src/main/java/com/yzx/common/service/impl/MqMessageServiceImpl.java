package com.yzx.common.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.common.enums.BizTypeEnum;
import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mapper.MqMessageMapper;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.IMqMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MqMessageServiceImpl extends ServiceImpl<MqMessageMapper, MqMessage> implements IMqMessageService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveMessage(String msgId, BizTypeEnum bizType, String content) {
        MqMessage message = new MqMessage();
        message.setMsgId(msgId);
        message.setBizType(bizType.getCode());
        message.setContent(content);
        message.setStatus(MessageStatusEnum.PENDING.getCode());
        message.setRetryCount(0);
        return this.save(message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveMqMessage(String msgId, BizTypeEnum bizType, String content, String exchange, String routingKey) {
        MqMessage message = new MqMessage();
        message.setMsgId(msgId);
        message.setBizType(bizType.getCode());
        message.setContent(content);
        message.setExchange(exchange);
        message.setRoutingKey(routingKey);
        message.setStatus(MessageStatusEnum.PENDING.getCode());
        message.setRetryCount(0);
        return this.save(message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(String msgId, MessageStatusEnum status) {
        return lambdaUpdate()
                .eq(MqMessage::getMsgId, msgId)
                .set(MqMessage::getStatus, status.getCode())
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean incrementRetry(String msgId) {
        return lambdaUpdate()
                .eq(MqMessage::getMsgId, msgId)
                .setSql("retry_count = retry_count + 1")
                .update();
    }

    @Override
    public List<MqMessage> getFailMessage(int maxRetry) {
        return baseMapper.selectFailMessage(maxRetry);
    }
}