package com.yzx.common.schedule;

import com.yzx.common.enums.MessageStatusEnum;
import com.yzx.common.mqlocalmessage.MqMessage;
import com.yzx.common.service.IMqMessageService;
import com.yzx.common.service.impl.MqMessageServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @className: MessageRetryService
 * @author: yzx
 * @date: 2026/3/14 19:14
 * @Version: 1.0
 * @description:
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRetryService {
    private final IMqMessageService mqMessageService;
    private final RabbitTemplate rabbitTemplate;
    private static final int MAX_RETRY = 3;
    @Async("messageRetryExecutor")
    public void asyncRetry(MqMessage msg) {
        String msgId = msg.getMsgId();
        try {
            // 只处理 MQ 消息（必须有 exchange）
            if (!StringUtils.hasText(msg.getExchange())) {
                log.warn("消息没有exchange，无法作为MQ消息重试，msgId:{}，将标记为死信", msgId);
                mqMessageService.markAsDead(msgId);
                return;
            }
            rabbitTemplate.convertAndSend(
                    msg.getExchange(),
                    msg.getRoutingKey(),
                    msg.getContent(),
                    m -> {
                        m.getMessageProperties().setCorrelationId(msgId);
                        return m;
                    }
            );
            log.info("MQ消息重试成功，msgId:{}", msgId);
            mqMessageService.updateStatus(msgId, MessageStatusEnum.SUCCESS);
        } catch (Exception e) {
            handleRetryFailure(msg, e);
        }
    }

    private void handleRetryFailure(MqMessage msg, Exception e) {
        String msgId = msg.getMsgId();
        int currentRetry = msg.getRetryCount();

        if (isRetryable(e) && currentRetry + 1 < MAX_RETRY) {
            mqMessageService.incrementRetry(msgId);
            log.warn("消息重试失败（可重试），msgId:{}，当前重试次数:{}", msgId, currentRetry + 1);
        } else {
            mqMessageService.markAsDead(msgId);
            log.error("消息重试失败（不可重试或已达上限），标记为死信，msgId:{}", msgId, e);
        }
    }

    private boolean isRetryable(Exception e) {
        return e instanceof AmqpConnectException || e instanceof java.net.ConnectException;
    }
}
