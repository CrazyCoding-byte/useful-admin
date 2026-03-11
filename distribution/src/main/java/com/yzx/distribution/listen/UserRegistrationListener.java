package com.yzx.distribution.listen;

import com.rabbitmq.client.Channel;
import com.yzx.distribution.service.impl.DistributionServiceimp;
import com.yzx.model.system.UserRegisteredMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @className: UserRegistrationListener
 * @author: yzx
 * @date: 2025/10/10 19:44
 * @Version: 1.0
 * @description:
 */
@Component
@Slf4j
public class UserRegistrationListener {
    @Autowired
    private DistributionServiceimp distributionService;

    @RabbitListener(
            queues = UserRabbitMQConfig.USER_REGISTERED_QUEUE,
            ackMode = "MANUAL"
    )
    public void handleUserRegistration(UserRegisteredMessage message, Channel channel, Message mqMessage) {
        log.info("收到用户注册消息，用户ID: {}", message.getUserId());

        boolean success = false;
        try {
            // 直接从消息中获取邀请人ID，不进行远程调用
            String inviterUserId = null;
            if (message.getInviterInfo() != null) {
                inviterUserId = message.getInviterInfo().getUserId();
                log.info("从消息中获取到邀请人ID: {}", inviterUserId);
            }

            boolean result = distributionService.buildDistributionRelation(
                    Long.valueOf(message.getUserId()),
                    message.getInviteQrCode()
            );

            success = result;

        } catch (Exception e) {
            log.error("处理用户注册消息失败，用户ID: {}", message.getUserId(), e);
            success = false;
        }

        // 手动确认消息
        try {
            if (success) {
                channel.basicAck(mqMessage.getMessageProperties().getDeliveryTag(), false);
                log.info("用户注册消息处理成功，用户ID: {}", message.getUserId());
            } else {
                // 处理失败，拒绝消息（可以根据业务需求决定是否重新入队）
                channel.basicNack(mqMessage.getMessageProperties().getDeliveryTag(), false, false);
                log.warn("用户注册消息处理失败，进入死信队列，用户ID: {}", message.getUserId());
            }
        } catch (IOException e) {
            log.error("确认消息失败，用户ID: {}", message.getUserId(), e);
        }
    }
}
